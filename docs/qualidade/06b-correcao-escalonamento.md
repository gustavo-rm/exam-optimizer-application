# 06b — Correção de escalonamento

- **Data:** 2026-09-04
- **Branch:** `release/v4.0`
- **Base:** [`06-diagnostico-escalonamento.md`](./06-diagnostico-escalonamento.md) — 10 achados (E1–E10)
- **Commits:** `a9829ad` (E3) · `ebb9b3e` (E1) · `8719aa2` (E2, E5) · `38fc207` (E6) · `268a05e` (E7–E10)
- **Corrigidos:** 9 de 10. **Aberto com análise:** E4 (limite por custo — decisão de produto)

---

## Resumo em uma tela

| Achado | O que era | O que é agora | Prova |
|---|---|---|---|
| **E1** | Limite de taxa na memória de cada réplica: **10 aceitos** onde a política diz 5 | Baldes num Redis compartilhado: **5 aceitos, 7 recusados** | dois processos reais |
| **E2** | Fila cheia → **66 respostas 500** (55 %) e **99 registros de ERROR** | **0 respostas 500**, 84 × `503` com `Retry-After`, **0 ERROR** | 120 pedidos simultâneos |
| **E3** | Uma fonte de aleatoriedade `static` para o processo: **+70 %** de tempo com 4 threads | Uma por thread: **−34 %** em processo | 7 baterias pareadas |
| **E5** | Fila de 50 contra prazo de 30 s: 7 vagas de trabalho descartado | Fila de 32, com a conta escrita e travada em teste | aritmética verificada |
| **E6** | Cliente segurava a conexão **1 527 ms** no pedido pesado | `202 Accepted` em **24 ms**; resultado consultado depois | 2 réplicas, envio numa e leitura na outra |
| **E7** | Rastreamento a 100 % sem exportador: *spans* descartados | Amostragem de 10 %, configurável | configuração declarada |
| **E8** | Threads do conector invisíveis | `tomcat_threads_busy` publicada | scrape contra o jar |
| **E9** | Resposta de 36,3 KB sem compressão | **2,4 KB** — razão de **15,4×** | medido no fio |
| **E10** | 2 linhas de INFO por requisição, **449 MB/h** por réplica saturada | 0 linhas de INFO por requisição | teste trava o nível |
| **E4** | Limite conta requisições, não custo (espalhamento de 119×) | **Aberto**, com desenho proposto (§8) | — |

| | Antes | Depois |
|---|---:|---:|
| Testes | 222 | **261** (+39) |
| Cobertura (BUNDLE) | 91,46 % instr. / 73,85 % ramos | **92,18 % / 74,54 %** |
| Suíte | verde | **verde**, rodada após cada mudança |

---

## 0. O que mudou de verdade, e o que não mudou

**A entrega central desta etapa não é desempenho — é correção sob replicação e comportamento sob
sobrecarga.** Vale dizer isso na frente porque dois números da tabela acima são fáceis de ler como
promessa de velocidade, e não são:

- **E3 rendeu 34 % em processo e não se reproduziu no HTTP** (§3). O número honesto está lá.
- **Duas réplicas na mesma máquina de 4 núcleos entregam menos que uma**, não mais (§7). O ganho de
  replicar aparece quando as réplicas estão em máquinas diferentes; o que esta etapa entregou é
  poder replicar **sem quebrar o limite de taxa e sem perder trabalhos**.

O que mudou de fato: o serviço passou a poder rodar replicado sem defeito silencioso, a recusar
carga excedente em milissegundos com o código HTTP certo, e a aceitar trabalho pesado sem prender o
cliente.

---

## 1. E1 — O limite de taxa saiu da memória local

### O defeito

`RateLimitingFilter` construía o próprio cache Caffeine. Cada réplica tinha **seu** mapa de baldes,
e um cliente distribuído pelo balanceador entre N réplicas recebia N baldes independentes.

O agravante era o silêncio: nenhum log, métrica ou teste acusava. O serviço seguia respondendo 200 —
só o limite deixara de valer, desfazendo o endurecimento da etapa 02 sem tocar numa linha do filtro.

### A correção

O filtro passou a depender de um porto, `RateLimitBuckets`, com dois adaptadores:

| Adaptador | Onde ficam os baldes | Serve para |
|---|---|---|
| `LocalRateLimitBuckets` | memória do processo | uma réplica |
| `RedisRateLimitBuckets` | Redis compartilhado, com *compare-and-swap* | **qualquer número de réplicas** |

**A correção central não é o adaptador Redis. É que a escolha deixou de ser silenciosa.** O modo
local registra, na inicialização, exatamente o que quebra:

```
WARN  Limite de taxa: baldes LOCAIS a este processo. Correto com UMA replica.
      Com N replicas o limite de 5 por 1 min vira N x 5 — cada replica conta separado.
      Para replicar, defina api.shared-state.redis.enabled=true.
```

E publica `dynamicstudyplanner.shared.state.replicavel` (0 = não replicável). Duas réplicas
reportando 0 **são o defeito acontecendo** — condição que um alarme consegue detectar.

### A prova, com dois processos reais

Mesmo experimento do diagnóstico: duas réplicas em portas distintas, limite de 5 por minuto, o mesmo
cliente alternando entre elas.

| | Antes | Depois |
|---|---:|---:|
| Aceitos | **10** | **5** |
| Bloqueados | 2 | 7 |
| Política declarada | 5 | 5 |

```
pedido  1 -> :8082 -> 200      pedido  1 -> :8082 -> 200
pedido  2 -> :8081 -> 200      pedido  2 -> :8081 -> 200
...                            ...
pedido  5 -> :8082 -> 200      pedido  5 -> :8082 -> 200
pedido  6 -> :8081 -> 200  ←   pedido  6 -> :8081 -> 429  ←
pedido 10 -> :8081 -> 200      pedido 10 -> :8081 -> 429
```

### O que o Redis faz quando cai

A chamada falha e a exceção sobe, virando **503 com `Retry-After`**. É deliberado: o limite de taxa
é um controle de segurança, e um controle que se desliga sozinho quando a infraestrutura tem
problema é pior que nenhum — vira exatamente a janela que um abuso espera. Deixar passar
transformaria uma indisponibilidade do Redis em **ausência total de limite em todas as réplicas ao
mesmo tempo**.

---

## 2. E6 — O cliente parou de segurar a conexão

### O que existia, e por que não bastava

O endpoint original já é assíncrono **por dentro** — a etapa 06 mediu 30 pedidos em voo ocupando 1
das 200 threads do Tomcat. Mas é síncrono **para o cliente**: a conexão fica aberta até o plano
ficar pronto.

### O que foi construído

| | |
|---|---|
| `POST /api/v1/optimizer/jobs` | aceita, devolve **202** com `Location` e identificador |
| `GET /api/v1/optimizer/jobs/{id}` | estado atual e, quando pronto, o plano |

**O caminho síncrono continua funcionando, e isso é deliberado.** `POST /generate` é o contrato
publicado, documentado no README e coberto por testes; trocá-lo quebraria todo cliente existente de
uma vez. O retrato do OpenAPI confirma que ele ficou **byte a byte idêntico** — a mudança do
contrato é puramente aditiva (2 caminhos, 2 esquemas).

### A medição

Pedido pesado (24 disciplinas, 1000 gerações, população 500):

| | Antes | Depois |
|---|---:|---:|
| Tempo de conexão aberta | **1 527 ms** | **24 ms** |

Sob 120 envios simultâneos, comparando os dois caminhos **já com o E2 corrigido**:

| | Caminho síncrono | Caminho assíncrono |
|---|---:|---:|
| Aceitos | 36, mediana **15,67 s** | 36, mediana **0,144 s** |
| Recusados (503) | 84, mediana 1,59 s | 84, mediana **0,249 s** |
| **Pior tempo de resposta** | **25,09 s** | **0,65 s** |

Ninguém espera. Um pico deixou de virar dezenas de segundos de espera.

### Consultar exige estado compartilhado — e por um motivo pior que o do E1

São **duas requisições HTTP distintas**, e nada garante que caiam na mesma réplica. Com registro
local, a consulta teria chance **1/N** de acertar a réplica que conhece o identificador; as outras
responderiam **404 para um trabalho que existe**. Isso quebraria a funcionalidade, não só um limite —
e de forma intermitente, que é a pior maneira de quebrar.

Por isso o registro usa a **mesma** chave de configuração dos baldes: as duas peças sobem juntas, e
não há como ligar metade. Verificado com duas réplicas reais — enviado em `:8081`, resultado lido em
`:8082`.

### Consultar não consome o limite de taxa

O limite protege a capacidade de **cálculo**. Cobrar as consultas do mesmo balde faria o cliente
gastar o limite dele só para descobrir se o trabalho que já pagou terminou. O filtro passou a
ignorar requisições `GET` sob `/api/v1/optimizer/`.

### Três defeitos que a medição e os testes pegaram

Vale registrar, porque cada um teria passado despercebido:

| Defeito | Como apareceu |
|---|---|
| `@Async` num método chamado de outro método do **mesmo bean** — a autoinvocação passa por fora do proxy, e o trabalho rodava na thread da requisição | **a medição**: o envio levava os mesmos 1,5 s do caminho síncrono |
| Ciclo `service → api → service`, porque o serviço chamava os mapeadores da API | **`ModuleBoundaryTest`** reprovou. Resolvido invertendo a dependência: o porto `JobResultSerializer` mora em `infra/jobs`, a implementação em `api/mapper` |
| Violação de regra de negócio mascarada atrás de um genérico *"could not be completed"*, enquanto o caminho síncrono devolvia 422 com o texto da regra | **o teste do fluxo**: o mesmo pedido recusado pelos dois caminhos precisa dar a mesma explicação |

---

## 3. E3 — Uma fonte de aleatoriedade por thread

### O defeito e a correção

`RandomProvider` guardava **uma** instância `static` para o processo inteiro, consultada em 15
pontos dos laços quentes por todas as requisições ao mesmo tempo. `Random` é seguro para uso
concorrente — sincroniza a semente por *compare-and-swap* — então não havia corrupção. Havia
desperdício: sob concorrência, o "refaz" do CAS é trabalho jogado fora.

A fonte passou a ser **por thread**. A reprodutibilidade continua intacta — e é por isso que não se
usou `ThreadLocalRandom`, que não aceita semente. As 9 assinaturas de referência da etapa 05b foram
conferidas depois da mudança: idênticas.

### O ganho em processo

Vazão concorrente, os dois builds medidos em par, medianas de 7 baterias de 20 otimizações por
thread:

| Threads | Antes | Depois | Ganho |
|---:|---:|---:|---:|
| 1 | 224 ms | 220 ms | ~0 |
| 2 | 240 ms | 226 ms | −6 % |
| **4** | **374 ms** | **247 ms** | **−34 %** |
| 8 | 741 ms | 479 ms | −35 % |

### O ganho que **não** apareceu ponta a ponta

Isto precisa ser dito com a mesma clareza. Medindo pela porta HTTP, com o payload típico, dois
pares alternando os builds:

| Concorrência | Sem E3 | Com E3 |
|---:|---:|---:|
| 1 | 38,7 req/s | 42,9 req/s |
| 2 | 70,6 req/s | 75,0 req/s |
| 4 | 107,8 req/s | **121,9 req/s** |
| 8 | 141,9 req/s | 144,1 req/s |
| 16 | **206,5 req/s** | 194,6 req/s |
| 16 (outro par) | 187,9 req/s | **198,3 req/s** |

**A direção favorece a correção na maioria dos níveis, mas o tamanho fica dentro do ruído, e a
16 concorrentes os dois pares discordam entre si.** O ganho de 34 % medido em processo **não se
reproduz** end-to-end.

A explicação é a decomposição que a própria etapa 05b mediu: o algoritmo genético responde por ~51 %
da requisição; o resto é HTTP, JSON e geração do cronograma. E dentro do algoritmo, a disputa pela
semente só morde quando várias otimizações de **fitness sequencial** rodam ao mesmo tempo — no
payload pesado, uma requisição sozinha já ocupa os quatro núcleos, e o gargalo passa a ser CPU, não
a semente.

A correção fica, porque o custo dela é zero e o ganho cresce com o número de núcleos. Mas **quem ler
esta etapa não deve esperar 34 % de vazão a mais.**

---

## 4. E2 e E5 — Sobrecarga deixou de ser erro de servidor

### E2: o código HTTP certo

O `ThreadPoolTaskExecutor` recusa quando a fila enche, e isso é o comportamento **certo** — é o que
impede o processo de aceitar trabalho até morrer. O problema era como a recusa chegava ao cliente:
sem tratador dedicado, `TaskRejectedException` caía no genérico e virava **500**, registrada em
nível ERROR como *"Unexpected error occurred"*.

Com 120 pedidos pesados simultâneos, mesma bancada da etapa 06:

| Resposta | Antes | Depois |
|---|---:|---:|
| **500** Internal Server Error | **66** (55 %) | **0** |
| **408** Request Timeout | 33 (27,5 %) | **0** |
| **503** + `Retry-After` | 0 | **84** (70 %), mediana 1,59 s |
| **200** OK | 21 (17,5 %), mediana 19,08 s | **36** (30 %), mediana 15,67 s |
| Registros de nível **ERROR** | **99** | **0** |

**Três coisas melhoraram ao mesmo tempo:**

- As requisições **bem-sucedidas subiram 71 %** (21 → 36). Recusar cedo libera a máquina para
  terminar trabalho, em vez de se debater com pedidos que vão estourar o prazo.
- A latência mediana das bem-sucedidas caiu de 19,08 s para 15,67 s.
- Quem é recusado sabe disso em **1,59 s**, e não depois de esperar 30 s por um 408.

Por que o código importa:

| | 500 | 503 + `Retry-After` |
|---|---|---|
| Balanceador entende | "esta réplica está quebrada" — pode tirá-la do rodízio e concentrar a carga nas outras, em cascata | "esta réplica está cheia" |
| Cliente entende | "algo deu errado" — muitas bibliotecas repetem em 5xx sem recuo, amplificando a carga | "volte em N segundos" |
| Painel de SLO lê | taxa de erro de 55 %, e alguém acordado para procurar um defeito que não existe | capacidade esgotada, com contador próprio |

### E5: a fila e o prazo passaram a se conhecer

A fila tinha 50 vagas; o prazo era o padrão do Tomcat, 30 s, nunca declarado. Drenar 50 pedidos
pesados a 1,44 req/s leva **35 s**, então as últimas ~7 vagas só produziam trabalho descartado — o
cliente esperava 30 s, recebia 408, e a otimização dele chegava a **começar**, gastando CPU para uma
conexão que já tinha ido embora. Aparecia mesmo em carga igual à capacidade nominal: com 54
simultâneos, 18 receberam 408.

Agora os dois são declarados, e a conta está escrita ao lado deles:

```
D × (2,8 s / 4 threads) + 2,8 s ≤ 30 s   ⇒   D ≤ 38
optimizer.queue-capacity = 32            (com margem)
spring.mvc.async.request-timeout = 30s   (declarado, não herdado)
```

`AsyncConfigDimensionamentoTest` **refaz a conta lendo o valor real do `application.properties`**:
mudar um dos números sem refazer a conta reprova no teste, em vez de reaparecer como 408 em produção.

---

## 5. Item 3 — Pool de conexões, agora que ele existe

No diagnóstico este item **não se aplicava**: não havia banco. Ao introduzir Redis, ele passou a se
aplicar — e a resposta do Lettuce é diferente da de um pool JDBC.

**O Lettuce multiplexa.** Uma conexão TCP carrega comandos de muitas threads ao mesmo tempo, porque
o protocolo do Redis é pipelinado e as respostas voltam em ordem. Diferente de JDBC, onde a conexão
fica presa à transação e por isso se usa um pool, aqui não há o que agrupar.

Medido, com duas réplicas:

| Situação | Conexões ao Redis |
|---|---:|
| Em repouso (2 réplicas + `redis-cli`) | **3** |
| Sob 60 requisições simultâneas | **3** |
| Depois da carga | **3** |

**1 conexão por réplica, constante sob carga.** O número cresce com **réplicas**, não com
requisições.

Isso importa porque é o erro clássico de escala: um pool de 10 conexões por réplica é razoável com 2
réplicas e some com o orçamento de conexões muito antes do esperado. Com `maxclients` de 10 000, o
teto aqui é de **milhares de réplicas**; com pool de 10 por réplica, seria de mil.

O mesmo raciocínio está escrito em `SharedStateConfig` como aviso para quando um banco relacional
existir: **o dimensionamento correto do pool depende do número de réplicas**, porque o limite do
servidor é global. É a mesma classe de erro do E1 — uma decisão de capacidade tomada localmente
sobre um recurso global.

---

## 6. Item 5 — Observabilidade

As três perguntas que precisam ser respondíveis sem acesso à máquina, e o que faltava:

### Está lento?

| Sinal | Estado |
|---|---|
| `http_server_requests_seconds` por rota e status | já existia |
| `dynamicstudyplanner_optimization_duration` | já existia; legível desde a etapa 05b |
| `dynamicstudyplanner_jobs_duration` (aceitação → fim, **incluindo fila**) | **novo** — é o que o cliente sente |

### Está errando?

| Sinal | Estado |
|---|---|
| `dynamicstudyplanner_overload_rejected` | **novo** — recusa por capacidade contada **à parte** dos erros de verdade. Sem isso, sobrecarga e defeito se confundem: o E2 media 55 % de taxa de erro para um serviço que estava apenas cheio |
| `dynamicstudyplanner_shared_state_replicavel` | **novo** — duas réplicas reportando 0 são o defeito E1 acontecendo |
| `dynamicstudyplanner_jobs_failed` | **novo** |

### Está cheio?

| Sinal | Estado |
|---|---|
| `dynamicstudyplanner_optimizer_queue_saturation` | **novo** — razão pronta de 0 a 1. As métricas soltas já existiam, mas só quem soubesse que a capacidade é a soma de `queued_tasks` e `queue_remaining_tasks` obteria a razão — aritmética repetida em cada painel e em cada alarme. **Publicar pronto é a diferença entre um alarme que alguém escreve e um que alguém não escreve** |
| `tomcat_threads_busy` / `config_max` | **E8 corrigido** — `server.tomcat.mbeanregistry.enabled=true`. Sem a chave, o Micrometer publicava só `tomcat_sessions_*`: seis séries, todas zero num serviço que não usa sessão |
| `jvm_memory_used`, `executor_*` | já existiam |

### E7 e E10 — custo de observar

**E7:** a amostragem de rastreamento estava em 1,0 — 100 % das requisições instrumentadas — sem
**nenhum** exportador no classpath: os *spans* eram criados e descartados. Passou a 10 %,
configurável por `TRACING_SAMPLING`. O `traceId` continua no log, e a correlação das linhas de uma
requisição — que é o que se usa hoje — não muda.

**E10:** as duas linhas de INFO por requisição descreviam resultado de cálculo interno — úteis para
depurar o algoritmo, inúteis para operar o serviço. A 172 req/s eram **449 MB/hora** por réplica
saturada. Viraram DEBUG, e o teste agora trava que **nada de INFO cresce com o tráfego**.

**E9:** compressão ligada. Medido no fio, contra o jar em execução:

| | Valor |
|---|---:|
| Resposta sem compressão | 36,3 KB |
| Com `gzip` | **2,4 KB** |
| Razão | **15,4×** |
| Egresso por réplica saturada | 21,4 GB/h → **1,4 GB/h** |

---

## 7. Item 6 — Teste de carga antes/depois

### O componente que quebrava primeiro melhorou

O diagnóstico apontou **E1** como o primeiro a quebrar — "na segunda réplica, antes de qualquer
crescimento de tráfego". A verificação é direta e está na §1: **10 aceitos → 5 aceitos**, com a
política declarando 5.

### Vazão numa réplica

Varredura com o mesmo harness (32 amostras por nível, aquecimento descartado). **Três estados**,
para que fique claro o que cada etapa entregou:

| Concorrência | Pré-05b | Etapa 06 (entrada desta etapa) | Etapa 06b |
|---:|---:|---:|---:|
| 1 | 20,5 req/s | 40,3 req/s | 40,3 req/s |
| 2 | 23,9 req/s | 65,5 req/s | 72,7 req/s |
| 4 | 20,4 req/s | 98,8 req/s | 107,7 req/s |
| 8 | 22,3 req/s | 110,3 req/s | 129,4 req/s |
| 16 | 30,0 req/s | 158,0 req/s | 174,1 req/s |

**O salto grande — de vazão plana em ~25 req/s para escalonamento com a concorrência — foi da etapa
05b, e já está reportado lá.** O que esta etapa acrescentou, da segunda para a terceira coluna, é
modesto e majoritariamente ruído: a §3 mostra que o único candidato a ganho de vazão (E3) não se
reproduz de forma consistente no HTTP.

Isto está escrito assim de propósito. As correções desta etapa são de **correção sob replicação** e
de **comportamento sob sobrecarga** — não de vazão. Apresentar a primeira coluna ao lado da terceira
sem essa separação atribuiria a esta etapa um ganho que foi de outra.

### Duas réplicas na mesma máquina rendem menos, não mais

| Concorrência | 1 réplica | 2 réplicas |
|---:|---:|---:|
| 4 | 107,7 req/s | 60,1 req/s |
| 8 | 129,4 req/s | 80,5 req/s |
| 16 | **174,1 req/s** | **90,4 req/s** |

**Isto não é falha da correção — é aritmética.** Duas JVMs numa máquina de 4 núcleos dividem os
mesmos 4 núcleos e ainda pagam duas vezes o custo fixo do processo. Replicar adiciona capacidade
quando as réplicas estão em **máquinas diferentes**; o que esta etapa entregou é poder replicar
**sem quebrar o limite de taxa e sem perder trabalhos**, que era o impedimento.

Medir "2 réplicas contra 1" num único contêiner responde a pergunta errada. A pergunta certa — *o
limite continua valendo e o trabalho continua encontrável quando há mais de uma réplica?* — foi
respondida com sim, nas §1 e §2.

---

## 8. E4 — Aberto, com desenho e o motivo de não ter sido feito

O limite de taxa conta **requisições**, não custo. O contrato aceita, do mais barato ao mais caro:

| | Planos/hora por réplica | Custo relativo |
|---|---:|---:|
| Pedido típico (15 disc., 100 ger., pop. 50) | 619 200 | 1× |
| Pedido pesado (24 disc., 1000 ger., pop. 500) | 5 183 | **119×** |

Dezessete clientes obedecendo o limite de 5 por minuto saturam a máquina com pedidos pesados.

**O desenho está claro:** cobrar do balde um número de fichas proporcional a
`gerações × população`, com o pedido típico valendo 1 e o pesado valendo ~100, e a capacidade
expressa em fichas em vez de chamadas. O `bucket4j` já suporta consumir N fichas — é uma linha.

**Por que não foi feito nesta etapa:** isso muda a **política pública** do serviço, não a mecânica.
Hoje um cliente pode fazer 5 requisições por minuto, quaisquer que sejam. Depois, poderia fazer 500
típicas ou 5 pesadas. Isso é melhor, mas é uma decisão de produto sobre o que se promete a quem
consome a API — e a etapa não pediu mudança de política. Fica registrado com a análise e o custo
medido, para ser decidido em vez de ser decidido por omissão.

**Pendência P20.**

---

## 9. Configuração nova, em um lugar só

| Chave | Padrão | O que faz |
|---|---|---|
| `api.shared-state.redis.enabled` | `false` | liga baldes **e** registro de trabalhos compartilhados. **Única chave para replicar** |
| `api.shared-state.redis.host` / `.port` / `.timeout-ms` | `localhost` / `6379` / `2000` | endereço do Redis |
| `api.jobs.ttl-minutes` | `60` | prazo de vida do registro de um trabalho |
| `api.jobs.max-records` | `10000` | teto de registros no modo local |
| `optimizer.queue-capacity` | `32` | profundidade da fila — **calculada contra o prazo** |
| `spring.mvc.async.request-timeout` | `30s` | prazo, agora declarado |
| `api.overload.retry-after-seconds` | `30` | valor do `Retry-After` nas respostas 503 |
| `management.tracing.sampling.probability` | `0.1` | amostragem de rastreamento |
| `server.compression.enabled` | `true` | compressão das respostas |
| `server.tomcat.mbeanregistry.enabled` | `true` | métricas de thread do conector |

**O padrão continua sendo uma instalação de uma réplica sem Redis**, porque desenvolvimento e
instalações pequenas não devem exigir infraestrutura. A auto-configuração de Redis do Spring Boot
foi **excluída de propósito**: ela criaria sempre uma fábrica de conexões para `localhost:6379` e um
indicador de saúde que reportaria DOWN, fazendo `/actuator/health` responder 503 justamente no
cenário em que Redis não é necessário.

---

## 10. Testes acrescentados

| Arquivo | O que trava | Testes |
|---|---|---:|
| `infra/ratelimit/RateLimitBucketsCompartilhadoTest` | local dobra o limite com 2 réplicas (contraprova); compartilhado soma no mesmo balde | 3 |
| `infra/jobs/JobStoreCompartilhadoTest` | local não atravessa réplicas (contraprova); compartilhado atravessa; marcas de tempo sobrevivem à serialização | 5 |
| `api/FluxoAssincronoTest` | 202 com `Location`; resultado completo; 404 sem distinguir inexistente de expirado; validação antes da fila; consulta não consome limite | 6 |
| `service/OptimizationJobServiceTest` | regra de negócio chega com o texto; falha inesperada não vaza detalhe interno; fila cheia recusa no envio | 3 |
| `api/exception/SobrecargaContratoTest` | 503 e não 500; `Retry-After`; contador próprio; Redis fora também é 503 | 5 |
| `config/ObservabilidadeMinimaTest` | latência, taxa de erro, saturação, replicabilidade, ciclo de vida dos trabalhos, e as chaves de E7/E8/E9 | 8 |
| `config/SharedStateConfigTest` | os quatro modos de armazenamento e a métrica que os torna visíveis | 6 |
| `config/AsyncConfigDimensionamentoTest` | fila cabe no prazo; medidor de saturação nos dois ramos | +3 (3 → 6) |

### Sobre o Redis dos testes

Os testes de estado compartilhado sobem um **redis-server real, em processo** (`embedded-redis`,
porta sorteada), e não um dublê nem o Redis da máquina.

- **Servidor real, não dublê**, porque o que se quer verificar é justamente que **duas pontas
  distintas enxergam o mesmo estado**. Um dublê em memória entregue às duas pontas provaria apenas
  que o dublê é compartilhado — a propriedade que interessa seria assumida, não verificada.
- **Embutido, não o da máquina**, porque depender de um Redis instalado tornaria o teste condicional:
  pulado em qualquer máquina sem ele, integração contínua incluída. **Um teste que não roda não
  protege nada** — e, de quebra, o piso de cobertura teria de ser rebaixado para acomodar a ausência.

Verificado: a suíte roda **verde com 0 testes pulados** e o Redis da máquina desligado.

---

## 11. O que ficou aberto

| # | Pendência | Onde | Por quê |
|---|---|---|---|
| **P17** | Pool próprio para a avaliação de fitness | `ga/Population.java` | os dois níveis de paralelismo precisam ser dimensionados em conjunto |
| **P18** | Cromossomo como vetor indexado | `ga/GeneticAlgorithm.java` | muda o tipo de domínio no sistema inteiro e altera o resultado |
| **P19** | Credencial real para o coletor de métricas | `config/SecurityConfig.java` | decisão de implantação |
| **P20** | Limite de taxa por **custo**, não por contagem (E4) | §8 | muda a política pública da API — decisão de produto |
