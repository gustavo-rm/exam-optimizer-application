# 06 — Diagnóstico de escalonamento

- **Data:** 2026-09-03
- **Branch:** `release/v4.0`
- **Base:** [`00-inventario.md`](./00-inventario.md) a [`05b-correcao-performance.md`](./05b-correcao-performance.md)
- **Escopo:** o que impede o sistema de crescer — em réplicas e em carga
- **Natureza:** diagnóstico. **Nenhum código foi alterado.**

---

## Resumo em uma tela

| Pergunta da etapa | Resposta curta | Achado |
|---|---|---|
| 1. Roda com múltiplas réplicas? | **Não** — o limite de taxa vive na memória de cada réplica. Demonstrado: 2 réplicas aceitaram **10** requisições onde a política declara 5 | **E1** |
| 2. Pool de conexões de banco adequado? | **Não se aplica** — não há banco. 0 dependências, 0 anotações em 123 arquivos, 0 métricas de pool | — |
| 3. Processamento pesado bloqueia a requisição? | A thread do Tomcat **é liberada** (medido: 30 pedidos em voo, 1 thread ocupada). Mas o cliente espera de ponta a ponta — não há fila de trabalhos | **E6** |
| 4. Falha graciosamente sob sobrecarga? | **Não.** Com 120 pedidos pesados simultâneos: **55 % receberam HTTP 500** | **E2**, **E4**, **E5** |
| 5. Observabilidade suficiente? | Boa base — logs estruturados, `traceId` atravessando o `@Async`, profundidade da fila exposta. Mas os *spans* de rastreamento são descartados e a saturação do conector é invisível | **E7**, **E8** |
| 6. O que quebra primeiro? | O limite de taxa, **na segunda réplica** — antes de qualquer crescimento de tráfego | **E1** |

**Dez achados: E1 a E10.** O mais grave não precisa de 10× nem de 100× de tráfego para aparecer: ele
aparece no instante em que a segunda réplica sobe.

### Uma leitura importante do conjunto

As etapas 01 a 05b deixaram o serviço com um núcleo sólido: sem estado de sessão, sem escrita em
disco, motor 50 % mais rápido, servidor escalando com a concorrência. **O que falta não está no
núcleo — está nas bordas.** Os três achados mais graves são todos de borda: uma decisão de
capacidade guardada no lugar errado (E1), uma condição de carga reportada como defeito (E2) e uma
variável estática compartilhada entre requisições (E3). Nenhum deles exige rearquitetura; todos
exigem decidir **onde mora o estado** e **o que significa "estou cheio"**.

---

## 0. Como isto foi medido

Todos os números vêm de execução, não de leitura de código. A bancada:

| O que | Como |
|---|---|
| Aplicação | o `.jar` de produção (`mvn package`), 4 núcleos, heap ergonômico de 3,42 GB |
| Pedido **típico** | 15 disciplinas, 100 gerações, população 50 — o payload usado desde a etapa 05 |
| Pedido **pesado** | 24 disciplinas, 1000 gerações, população 500 — o mais caro que o contrato **aceita** |
| Métricas | `/actuator/prometheus`, legível desde a correção do F9 na etapa 05b |
| Múltiplas réplicas | dois processos reais em portas distintas, não simulação |

**O pedido mais caro que o contrato aceita não é o maior que ele permite.** O DTO admite até 50
disciplinas, mas o piso de dias por disciplina soma mais do que o ano tem. A resposta medida:

```
422 Unprocessable Content, em 24 ms
"Total minimum study days required (462) exceeds total available days (365)."
```

O teto real de custo está em 24 disciplinas. Isso é uma defesa que já existe, e vale registrar como
tal: o pedido é recusado **antes** de gastar CPU.

---

## 1. Múltiplas réplicas: o que quebra

### 1.1 O que **não** quebra — verificado

*Stateless* (sem estado) significa que a réplica não guarda, entre requisições, nada que outra
réplica precise saber. Verificado item a item:

| Verificação | Resultado |
|---|---:|
| `HttpSession`, `@SessionAttributes`, `@SessionScope` | **0 ocorrências** |
| Escrita em disco local (`new File`, `FileWriter`, `Files.write`, `FileOutputStream`) | **0 ocorrências** |
| Campos `static` não-`final` de domínio | **0** (a única exceção é tratada em **E3**) |
| Estado mutável do algoritmo genético | **por requisição** — `gaFactory.create()` devolve uma instância nova a cada otimização; os contadores de estagnação e hipermutação nascem e morrem com o pedido |
| Contadores do Micrometer | por instância, **como deve ser** — o Prometheus agrega réplicas por rótulo |
| Tempo de inicialização | **3,76 s** — rápido o bastante para autoescalonamento reagir |

O núcleo do serviço está pronto para replicar.

### 1.2 E1 — O limite de taxa vive na memória local de cada réplica

```java
// RateLimitingFilter.java — construtor
this.cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterAccess(Duration.ofMinutes(60))
        .build();
```

Cada réplica tem **seu próprio** mapa de baldes. Um cliente distribuído pelo balanceador entre N
réplicas recebe N baldes independentes — e o limite efetivo vira **N × capacidade**.

**Isto não foi deduzido. Foi executado**: dois processos reais, portas 8081 e 8082, limite de 5 por
minuto, o mesmo cliente alternando entre as duas:

```
pedido  1 -> :8082 -> 200      pedido  7 -> :8082 -> 200
pedido  2 -> :8081 -> 200      pedido  8 -> :8081 -> 200
pedido  3 -> :8082 -> 200      pedido  9 -> :8082 -> 200
pedido  4 -> :8081 -> 200      pedido 10 -> :8081 -> 200
pedido  5 -> :8082 -> 200      pedido 11 -> :8082 -> 429
pedido  6 -> :8081 -> 200      pedido 12 -> :8081 -> 429

aceitos: 10    bloqueados: 2    (a política declara 5 por minuto)
```

**O dobro de réplicas dobrou o limite.** Com 10 réplicas o limite declarado de 5 vira 50.

Três agravantes:

- **É regressão de segurança, não só de escala.** A etapa 02 endureceu este limite de propósito
  (achados S12 e seguintes), inclusive contra a burla por cabeçalho forjado. Replicar desfaz o
  trabalho sem tocar em uma linha do filtro.
- **É silencioso.** Nenhum log, nenhuma métrica e nenhum teste acusa. O sistema segue respondendo
  200; só o limite deixou de valer.
- **É a única peça de estado compartilhado que o sistema tem.** Não existe cache de resultado, nem
  sessão, nem nada mais a externalizar. O trabalho é ter **um** lugar compartilhado, não migrar uma
  camada inteira.

→ **E1. Severidade: alta. Quebra na segunda réplica, antes de qualquer crescimento de tráfego.**

### 1.3 E3 — Uma fonte de aleatoriedade estática para todas as requisições

```java
// RandomProvider.java
private static Random instance = new Random();
```

Um objeto `Random`, `static`, consultado em 15 pontos dos laços quentes do algoritmo — por **todas**
as requisições ao mesmo tempo. `Random` é seguro para uso concorrente, então não há corrupção: cada
`nextInt` disputa a mesma semente por *compare-and-swap* (CAS — a thread lê o valor, calcula o
próximo e só grava se ninguém tiver mudado no meio; se mudou, refaz). Sob concorrência, esse "refaz"
vira trabalho perdido.

Medido: quatro threads rodando otimizações completas, comparando a fonte única de produção contra
uma instância por thread. Medianas de 5 baterias:

| Threads | Fonte única (produção) | Uma por thread | Custo da fonte única |
|---:|---:|---:|---:|
| 1 | 233 ms | 238 ms | −2 % (ruído) |
| 2 | 269 ms | 256 ms | +5 % |
| **4** | **393 ms** | **231 ms** | **+70 %** |
| 8 | 741 ms | 457 ms | +62 % |

Repare na coluna do meio: com uma fonte por thread, **4 threads fazem 4× o trabalho no mesmo tempo
que 1 thread** (231 ms contra 238 ms) — escalonamento praticamente linear. A fonte única destrói
isso: o mesmo trabalho passa de 233 para 393 ms.

**A réplica está entregando ~59 % da capacidade que o hardware permite**, e a perda cresce
exatamente com o paralelismo que o pool de 4 threads existe para explorar. Com uma réplica de 8 ou
16 núcleos, a perda seria maior.

O impedimento não é técnico, é de contrato: a semente fixa é o que sustenta
`GaResultadoInalteradoTest` e as 9 assinaturas de referência da etapa 05b. Qualquer correção precisa
preservar reprodutibilidade **por requisição** — o que é possível (uma fonte semeada por otimização,
em vez de uma global), mas é decisão de projeto, não ajuste.

→ **E3. Severidade: alta. Não quebra correção; corta 41 % da capacidade de cada réplica, hoje.**

---

## 2. Pool de conexões de banco

**Não se aplica.** A verificação, reconferida nesta etapa:

| Verificação | Resultado |
|---|---:|
| Dependências de persistência no `pom.xml` (JPA, JDBC, Hibernate, jOOQ, MyBatis, drivers, Flyway, Liquibase, Mongo, Redis) | **0** |
| `@Entity`, `@Repository`, `@Table`, `@Query`, `@Transactional`, `DataSource`, `EntityManager`, `JdbcTemplate` em 123 arquivos | **0** |
| Chaves `spring.datasource`, `spring.jpa`, `spring.data` | **0** |
| Séries `hikaricp_*`, `jdbc_*`, `r2dbc_*` no `/actuator/prometheus` | **0** de 155 |

**O análogo existe e é onde a pergunta se aplica de verdade.** O recurso escasso que este serviço
reparte entre requisições não são conexões, é CPU — e o pool que o governa é o
`optimizerTaskExecutor`. Ele já é dimensionado pela máquina desde a etapa 05b (achado F7). O que
falta nele é o comportamento sob saturação, tratado nos achados **E2**, **E4** e **E5**.

Vale antecipar uma armadilha para quando o banco existir: **o dimensionamento correto do pool de
conexões depende do número de réplicas**, porque o limite de conexões do servidor de banco é
global. Um pool de 10 conexões é razoável com 2 réplicas e derruba um Postgres padrão
(`max_connections = 100`) com 12. É a mesma classe de erro do E1 — uma decisão de capacidade tomada
localmente sobre um recurso global.

---

## 3. Processamento pesado: assíncrono por dentro, síncrono para o cliente

### 3.1 A thread do Tomcat **é** liberada — medido

O controlador devolve `CompletableFuture<ResponseEntity<...>>` e o serviço é
`@Async("optimizerTaskExecutor")`. Isso deveria devolver a thread do conector ao pool enquanto o
cálculo roda. Foi verificado com as métricas do Tomcat ligadas:

| Estado | `tomcat_threads_busy` | `executor_active_threads` | `executor_queued_tasks` |
|---|---:|---:|---:|
| Em repouso | 1 | 0 | 0 |
| **30 pedidos pesados em voo** | **1** | 4 | 22 |

Trinta requisições em andamento ocupam **uma** thread do Tomcat — a da própria coleta de métricas.
O mecanismo funciona como anunciado, e isso é um acerto de projeto que merece registro: **o
conector, com 200 threads, nunca é o gargalo.** O gargalo é sempre o pool do otimizador.

### 3.2 E6 — Mas o cliente espera de ponta a ponta

Assíncrono aqui significa "não prende thread do servidor". **Não** significa "o cliente não espera".
A conexão HTTP fica aberta até o plano ficar pronto: não há identificador de trabalho, não há
consulta de status, não há fila durável. Consequências medidas:

| | Pedido típico | Pedido pesado |
|---|---:|---:|
| Latência de uma requisição isolada | 25 ms | **2,4 – 3,4 s** |
| Vazão sustentada por réplica | 172 req/s | **1,44 req/s** |
| Planos por hora por réplica | 619 200 | **5 183** |

Três limites decorrem disso:

- **O cliente segura a conexão por segundos.** Balanceadores e *gateways* têm prazos próprios
  (30 s ou 60 s é comum); um pedido pesado atrás de fila estoura qualquer um deles.
- **Trabalho perdido não é retomado.** Quando o prazo estoura, a otimização em andamento é
  descartada — a CPU já foi gasta e ninguém recebe o resultado (§4.2).
- **Não há como absorver pico.** Uma fila durável transformaria pico em latência; hoje pico vira
  erro.

→ **E6. Severidade: média. Não quebra agora; é o teto de arquitetura para carga pesada sustentada.**

---

## 4. Backpressure e sobrecarga: o achado mais duro

### 4.1 E2 — Sob sobrecarga, o sistema devolve 500

**Experimento:** 120 pedidos pesados disparados simultaneamente contra uma réplica (pool de 4,
fila de 50 — capacidade total 54).

| Status | Quantidade | % | Mediana | Máximo |
|---|---:|---:|---:|---:|
| **500** Internal Server Error | **66** | **55 %** | 0,18 s | 0,40 s |
| **408** Request Timeout | 33 | 27,5 % | 30,13 s | 30,22 s |
| 200 OK | 21 | 17,5 % | 19,08 s | 30,02 s |

**Mais da metade da carga recebeu erro de servidor.** O corpo:

```json
{
  "type": "https://api.dynamicstudyplanner.com/errors/internal-server-error",
  "title": "Internal Server Error",
  "status": 500,
  "detail": "An unexpected internal error occurred."
}
```

A causa é conhecida e verificada no log: **66 `TaskRejectedException`** — a fila de 50 encheu e o
`ThreadPoolTaskExecutor` aplicou sua política padrão (`AbortPolicy`), que lança exceção. Não há
`@ExceptionHandler` para ela, então ela cai no tratador genérico.

O problema não é a rejeição. **Rejeitar é o comportamento certo** — é isso que impede o processo de
aceitar trabalho até morrer. O problema é *como* a rejeição é comunicada:

| | Hoje | O que a situação pede |
|---|---|---|
| Status HTTP | **500** Internal Server Error | **503** Service Unavailable |
| Cabeçalho `Retry-After` | ausente | presente, dizendo quando voltar |
| Nível de log | **ERROR**, "Unexpected error occurred" | WARN, "capacidade esgotada" |
| Semântica para o balanceador | "esta réplica está quebrada" | "esta réplica está cheia" |
| Semântica para o cliente | "algo deu errado, não sei o quê" | "tente de novo em N segundos" |

As consequências práticas de trocar 503 por 500:

- **Um balanceador não faz *backpressure* com 500.** Ele pode tirar a réplica do balanceamento por
  suspeitar de defeito — concentrando a carga nas que sobraram e derrubando-as em cascata.
- **Clientes reagem errado.** Muitas bibliotecas repetem automaticamente em 5xx sem recuo — o que
  amplifica exatamente a carga que causou a rejeição.
- **O alarme mente.** Foram **99 registros de nível ERROR** numa condição de capacidade. Um painel
  de SLO lê isso como taxa de erro de 55 %, e quem for acordado vai procurar um defeito que não
  existe. A informação certa — "cheguei ao limite" — está disponível
  (`executor_queue_remaining_tasks` chegou a 0) e não é usada.

→ **E2. Severidade: crítica. Já acontece hoje, com uma réplica, sem crescimento nenhum.**

### 4.2 E5 — A fila é mais funda do que o prazo consegue drenar

O pool tem fila de 50. O prazo do Tomcat para requisição assíncrona é **30 s** — e é o padrão do
contêiner, não uma escolha: `spring.mvc.async.request-timeout` não aparece em
`application.properties`.

```
drenar 50 pedidos pesados a 1,44 req/s ....... 35 s
prazo do Tomcat .............................. 30 s
posição na fila a partir da qual o 408 e certo: 43
```

**As últimas 7 vagas da fila só produzem trabalho descartado.** Quem cai nelas espera 30 s, ocupa
uma vaga que poderia ter recusado outro pedido cedo, e recebe 408. Pior: quando a vez dele chega, a
otimização **começa** e consome CPU — trabalho para uma conexão que já foi embora.

Isso apareceu mesmo em carga igual à capacidade nominal: no teste de 54 pedidos simultâneos (exatos
4 + 50), **18 receberam 408**. A capacidade anunciada pela configuração não é a capacidade real.

→ **E5. Severidade: alta. A fila e o prazo foram escolhidos sem relação um com o outro.**

### 4.3 E4 — O limite de taxa conta requisições; o custo varia 119 vezes

O contrato aceita, do mais barato ao mais caro que passa pela regra de negócio:

| | Planos/hora por réplica | Custo relativo |
|---|---:|---:|
| Pedido típico (15 disc., 100 ger., pop. 50) | 619 200 | 1× |
| Pedido pesado (24 disc., 1000 ger., pop. 500) | 5 183 | **119×** |

O `RateLimitingFilter` conta **requisições**, não custo. Um cliente que sempre manda o pedido mais
caro consome 119 vezes mais CPU que outro, dentro do mesmo limite. A aritmética:

```
capacidade da maquina com pedidos pesados ..... 1,44 req/s = 86 req/min
limite por cliente ............................ 5 req/min
clientes necessarios para saturar a replica ... 86 / 5 ~= 17
```

**Dezessete clientes obedecendo o limite saturam a máquina.** Não é abuso — é uso conforme a
política. E com o E1 em cena, N réplicas multiplicam a permissão sem multiplicar a capacidade por
cliente.

O contrato já tem tetos (`@Max` em gerações e população) e a regra de negócio já corta o caso
extremo com 422. O que falta é o limite ser **função do custo pedido**, não da contagem de chamadas.

→ **E4. Severidade: alta. É o vetor pelo qual carga legítima vira indisponibilidade.**

---

## 5. Observabilidade

### 5.1 O que existe, e é bom

| Recurso | Estado | Evidência |
|---|---|---|
| Logs estruturados (JSON) | **sim** | logstash-logback; 390 bytes/linha, campos `level`, `logger`, `thread`, `traceId`, `spanId` |
| `traceId` atravessa a fronteira `@Async` | **sim** | mesmo `traceId` na thread `http-nio-...` e em `Optimizer-Async-1` — o `MdcTaskDecorator` funciona |
| Métrica de negócio | **sim** | `dynamicstudyplanner_optimization_duration` e `_runs`, legíveis desde a etapa 05b |
| Profundidade da fila | **sim** | `executor_queued_tasks`, `executor_queue_remaining_tasks`, `executor_active_threads` |
| Status HTTP por rota | **sim** | `http_server_requests_seconds_count{status,uri,outcome}` |
| JVM (heap, GC, threads, classes) | **sim** | 155 séries no total |
| Sonda de saúde para orquestrador | **sim** | `/actuator/health` público desde a etapa 05b |

**O sinal que detectaria a sobrecarga do E2 já é publicado.** `executor_queue_remaining_tasks`
chegando a 0 é a definição operacional de "estou cheio". Ninguém o observa porque não há painel nem
alarme — mas o dado está lá.

### 5.2 E7 — O rastreamento é gerado e jogado fora

`micrometer-tracing-bridge-brave` está no `pom.xml`, e `management.tracing.sampling.probability=1.0`
manda instrumentar **100 %** das requisições. Mas:

```
busca por exportador (zipkin, otlp, opentelemetry-exporter, reporter): 0 resultados
```

Não há para onde os *spans* irem. O sistema paga a instrumentação de todas as requisições e
descarta o resultado. O `traceId` no log é útil — dá para juntar as linhas de uma requisição — mas
isso é correlação de log, não rastreamento distribuído.

Duas consequências ao crescer:

- **Amostrar 100 % não escala.** É a configuração certa para desenvolvimento e cara em produção.
  Com 10× ou 100× de tráfego, o custo é pago em cada requisição por um dado que ninguém lê.
- **O valor do rastreamento só aparece com mais de um serviço.** Hoje há um. Ligar um coletor agora
  é preparação; a decisão sobre amostragem é que precisa ser tomada antes do tráfego crescer.

→ **E7. Severidade: baixa hoje, média a 10×.**

### 5.3 E8 — A saturação do conector é invisível

As métricas do Tomcat expostas por padrão são só de sessão:

```
tomcat_sessions_active_current, tomcat_sessions_created_total, ... (6 series, todas zero)
```

`tomcat_threads_busy_threads` e `tomcat_threads_config_max_threads` **não aparecem** — exigem
`server.tomcat.mbeanregistry.enabled=true`, que não está configurado. Foi preciso ligar a chave
manualmente para produzir a medição da §3.1.

Hoje isso não esconde nada (§3.1 mostrou 1 thread ocupada de 200). Mas é justamente a métrica que
diria se o conector virou gargalo — e é a que estará faltando no dia em que isso acontecer.

→ **E8. Severidade: baixa. Custo de correção: uma linha de configuração.**

### 5.4 O que não existe em nenhum lugar

- **Nenhum painel.** Nenhum arquivo de dashboard, nenhuma configuração de Grafana.
- **Nenhum alarme.** Nenhuma regra de alerta, nenhum limiar declarado.
- **Nenhum SLO.** Não há latência-alvo nem taxa de erro aceitável escrita em lugar nenhum.

Sem um limiar declarado, "detectar antes de virar incidente" não tem como acontecer: a informação
existe, mas ninguém está olhando, e nada avisa. Isso é consistente com o inventário (R2): **não há
implantação**. Não é omissão de operação — é trabalho que ainda não começou.

---

## 6. Projeção: 10× e 100×

### 6.1 A premissa, declarada

**Não existe implantação e não existe número de usuários hoje** (achado R2 do inventário). Uma
projeção sobre "10× os usuários de hoje" multiplicaria zero. Então a projeção é feita ao contrário:
a partir da capacidade medida, quantos usuários cabem — e o que arrebenta primeiro em cada faixa.

Premissa de uso, explícita para poder ser contestada: **um estudante gera 2 planos por mês**, com
o perfil do pedido típico. Uma réplica de 4 núcleos entrega 619 200 planos/hora ⇒ ~446 milhões por
mês ⇒ **223 milhões de estudantes**, se a carga fosse perfeitamente distribuída.

Esse número é irreal e é justamente o ponto: **nenhum dos limites deste sistema é a capacidade de
cálculo.** Todos são de forma, não de força.

### 6.2 O que quebra, em ordem de chegada

| Marco | O que acontece | Achado |
|---|---|---|
| **2ª réplica** | O limite de taxa dobra sozinho. Nenhum aviso | **E1** |
| **Qualquer pico > 54 simultâneos** | 55 % da carga vira HTTP 500 e 99 registros de ERROR | **E2** |
| **17 clientes pesados** | A réplica satura obedecendo a própria política de limite | **E4** |
| **Fila > 43** | As últimas 7 vagas viram CPU gasta e 408 | **E5** |
| **10× do tráfego típico** | Egresso: 21,4 GB/h por réplica saturada, sem compressão. Log: 449 MB/h | **E9**, **E10** |
| **100×** | Amostragem de rastreamento a 100 % passa a custar em cada requisição, para um dado descartado | **E7** |
| **Nunca (memória)** | Pico medido: **339 MB** com a fila cheia, contra 3,42 GB de heap. Não é o limite | — |

Duas coisas que **não** quebram merecem registro, porque contrariam a intuição:

**Memória não é o limite.** Com a capacidade inteira ocupada (4 otimizações ativas + 50 na fila), o
pico de heap foi 339 MB — 10 % do disponível. Em repouso, 37 MB.

**O conector não é o limite.** 30 pedidos em voo ocuparam 1 das 200 threads do Tomcat.

### 6.3 E9 — Resposta de 36 KB sem compressão

`server.compression` não aparece na configuração, e a resposta não traz `Content-Encoding`.

| | Valor |
|---|---:|
| Tamanho da resposta | 36,3 KB |
| Comprimida (gzip nível 6) | **2,5 KB** |
| Razão | **14,6×** |
| Egresso por réplica saturada | **21,4 GB/h** → 1,5 GB/h |

**99 % do corpo é o `scheduleResult`** — o calendário dia a dia, 40,3 KB de 40,6 KB. É repetitivo por
natureza, e é exatamente o que a compressão faz bem. Egresso é o item de custo que cresce linearmente
com o tráfego, sem teto.

→ **E9. Severidade: média a 10×. Custo de correção: duas linhas de configuração.**

### 6.4 E10 — Volume de log

Duas linhas de INFO por requisição bem-sucedida, 390 bytes cada:

```
INFO  DefaultPopulationGenerator  Initial Population created. Best fitness: 0.8367...
INFO  StudyOptimizerService       Evolution complete after 100 generations. Final best fitness: 0.85
```

A 172 req/s: **449 MB/hora por réplica saturada**, ~10,5 GB/dia. As duas linhas descrevem o
resultado de um cálculo interno — informação de depuração, não de operação. Nada nelas ajuda a
diagnosticar um problema de escala; o que ajudaria (profundidade de fila, rejeições) não é
registrado.

→ **E10. Severidade: baixa a 10×, média a 100×.**

---

## 7. Lista priorizada — pelo que quebra primeiro

Ordenada pelo **momento em que o achado passa a doer**, não pelo esforço de corrigir. Dois deles já
doem hoje, sem crescimento nenhum.

### Nível 1 — já quebrado, ou quebra na primeira tentativa de crescer

| # | Achado | Quando quebra | Impacto medido |
|---|---|---|---|
| **E1** | **Limite de taxa na memória local de cada réplica.** `RateLimitingFilter.cache` é um Caffeine por instância | **Na 2ª réplica** | Demonstrado com dois processos reais: **10 requisições aceitas onde a política declara 5**. Com N réplicas, N × 5. Silencioso: nenhum log, métrica ou teste acusa. Desfaz o endurecimento da etapa 02 |
| **E2** | **Sobrecarga devolve 500, não 503.** `TaskRejectedException` sem tratador dedicado | **Hoje**, em qualquer pico acima de 54 simultâneos | 120 pedidos pesados: **66 (55 %) receberam 500** em 0,18 s; **99 registros de nível ERROR** para uma condição de capacidade. Sem `Retry-After`. Balanceador não recua; cliente repete e amplifica |
| **E3** | **Uma fonte de aleatoriedade estática para todas as requisições.** `RandomProvider.instance` | **Hoje**, proporcional ao paralelismo | 4 threads: **+70 %** de tempo contra uma fonte por thread (393 ms × 231 ms). A réplica entrega **~59 %** da capacidade do hardware. Piora com mais núcleos |

### Nível 2 — quebra sob carga real, antes de 10×

| # | Achado | Quando quebra | Impacto medido |
|---|---|---|---|
| **E4** | **O limite conta requisições, não custo.** Espalhamento de 119× dentro do contrato | Com ~17 clientes usando o pedido pesado | 619 200 planos/h (típico) contra 5 183 (pesado). 86 req/min de capacidade ÷ 5 req/min por cliente ≈ **17 clientes obedientes saturam a réplica** |
| **E5** | **Fila de 50 mais funda que o prazo de 30 s.** Os dois números foram escolhidos sem relação | Sempre que a fila passa de 43 | Drenar 50 pesados leva **35 s** contra prazo de **30 s**: as últimas **7 vagas** só produzem trabalho descartado. Mesmo com 54 simultâneos (a capacidade nominal), **18 receberam 408** |
| **E6** | **Requisição síncrona de ponta a ponta.** Sem identificador de trabalho nem fila durável | Carga pesada sustentada | Pedido pesado: **2,4–3,4 s** de conexão aberta, **1,44 req/s** por réplica. Prazo estourado descarta trabalho já pago em CPU |

### Nível 3 — custo e cegueira ao crescer

| # | Achado | Quando quebra | Impacto medido |
|---|---|---|---|
| **E9** | **Resposta de 36 KB sem compressão** | 10× | **21,4 GB/h** por réplica saturada contra 1,5 GB/h comprimido — razão de **14,6×**. Egresso cresce linearmente, sem teto |
| **E7** | **Rastreamento gerado e descartado.** Bridge sem exportador, amostragem em 100 % | 10× a 100× | **0 exportadores** no classpath. Toda requisição paga instrumentação por um *span* que ninguém lê |
| **E10** | **2 linhas de INFO por requisição** com resultado de cálculo interno | 100× | **449 MB/h** por réplica saturada (~10,5 GB/dia). Nenhuma das duas ajuda a diagnosticar escala |
| **E8** | **Métricas de thread do Tomcat desligadas** | Quando o conector virar gargalo | Só 6 séries `tomcat_sessions_*`, todas zero. `tomcat_threads_busy` exige `server.tomcat.mbeanregistry.enabled=true` |

### O que **não** é problema de escala — verificado, não presumido

| Item | Medição |
|---|---|
| Estado de sessão / disco local | **0 ocorrências** de `HttpSession`, `@SessionScope`, escrita em arquivo |
| Estado mutável do algoritmo | **Por requisição** — `gaFactory.create()` a cada otimização |
| Pool de conexões de banco | **Não existe banco**: 0 dependências, 0 anotações em 123 arquivos, 0 séries `hikaricp_*` |
| Memória | Pico de **339 MB** com a capacidade cheia, contra 3,42 GB de heap |
| Thread do conector | **1 de 200 ocupada** com 30 pedidos em voo — o `@Async` funciona como anunciado |
| Tempo de inicialização | **3,76 s** — compatível com autoescalonamento |
| Pedido de 50 disciplinas | Cortado pela regra de negócio: **422 em 24 ms** (462 dias exigidos contra 365 disponíveis), antes de gastar CPU |

---

## 8. Uma observação sobre a ordem das etapas

A instrução desta etapa dizia que escalonamento depende de estrutura, testes e performance
resolvidos. O diagnóstico confirma isso de um jeito específico e vale registrar:

**A etapa 05b mudou qual é o gargalo, e portanto qual seria a correção certa aqui.** Antes dela, a
vazão era plana em ~25 req/s e cada requisição já ocupava os quatro núcleos por dentro — um
diagnóstico de escalonamento naquele estado teria concluído que o serviço não escala
horizontalmente por saturar sozinho, e teria proposto reescrever o motor. Depois dela, a vazão
escala com a concorrência (172 req/s a 16 simultâneos), e o que resta são **três problemas de
borda**: onde mora o estado (E1), o que significa "estou cheio" (E2) e uma variável estática
compartilhada (E3).

Nenhum dos três exige tocar no algoritmo. Diagnosticar escalonamento antes teria produzido a
resposta certa para a pergunta errada.
