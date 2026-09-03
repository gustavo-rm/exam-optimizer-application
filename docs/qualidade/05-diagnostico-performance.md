# Etapa 05 — Diagnóstico de performance

- **Data:** 2026-09-03
- **Commit analisado:** `ab7666d` (topo de `release/v4.0`)
- **Natureza:** **diagnóstico**. Nenhuma linha de código foi alterada — verificado com
  `git status` limpo ao final. Todo o instrumental de medição foi escrito **fora do repositório**.
- **Ambiente de medição:** 4 núcleos, 16 GB de RAM, OpenJDK 21.0.10, Spring Boot 3.5.16

---

## Por que esta etapa vem depois das outras

A ordem foi deliberada, e o resultado justifica a espera. Duas das medições abaixo só fazem sentido
sobre o código já reorganizado:

- A **decomposição por fase** do algoritmo genético (§6.2) foi feita decorando as estratégias de
  seleção, cruzamento e mutação. Isso só é possível porque elas são injetadas por interface — e
  porque `EvolutionContextAssembler` passou a ser público e construível fora do Spring, na etapa
  03e. Antes, montar o contexto exigia copiar `prepareContext` à mão.
- O achado **F5** (`getTotalDays` recalculado) fica visível porque `StudyPlan` é imutável. Se ainda
  houvesse mutação em algum ponto, a resposta a "posso memoizar isto?" seria "não sei".

Ajustar performance antes disso teria mascarado problema estrutural com otimização local — que é
exatamente o que a instrução mandava evitar.

### Ferramentas usadas

Nenhuma está configurada no repositório. Todas foram executadas para este diagnóstico:

| Ferramenta | Para quê |
|---|---|
| **JFR** (*Java Flight Recorder*, o gravador de eventos que acompanha a JVM) | amostragem de CPU, alocação e pausas de coleta de lixo sobre a aplicação real |
| Decoradores cronometrados | tempo por fase do AG, sem tocar no código de produção |
| `curl` + `xargs -P` | latência e vazão HTTP sob concorrência |
| Micro-medições em laço | custo unitário de operações repetidas milhões de vezes |

**Método.** Toda medição de tempo foi precedida de **aquecimento** — repetições descartadas para o
compilador JIT (*Just-In-Time*, que traduz o bytecode em código de máquina só depois de ver o método
executar muitas vezes) terminar de otimizar os caminhos quentes. Sem isso, as primeiras execuções
medem o interpretador, não o programa. Os valores relatados são **medianas** de 5 a 20 repetições,
com mínimo e máximo quando a dispersão importa.

---

## 1. Onde o tempo é gasto no caminho mais usado

O sistema tem **um único endpoint**: `POST /api/v1/optimizer/generate`. Não há segundo caminho a
perfilar.

### 1.1 Latência ponta a ponta, sobre a aplicação real

Medido contra o `.jar` empacotado, com o limite de requisições afrouxado para não interferir:

| Cenário | Mediana | p90 | Resposta |
|---|---:|---:|---:|
| 15 disciplinas, 100 gerações, 50 indivíduos (típico) | **49 ms** | 58 ms | 37 KB |
| 24 disciplinas, 1000 gerações, 500 indivíduos (pior admitido pelo DTO) | **2 731 ms** | 2 749 ms | 37 KB |

### 1.2 A decomposição do tempo

Medindo as etapas separadamente, com a fonte de aleatoriedade **de produção**:

| Etapa | Típico | Pior admitido | Participação |
|---|---:|---:|---:|
| **Algoritmo genético** | 44 ms | **2 313 ms** | **~85 %** |
| Geração do cronograma diário | 4 ms | 3 ms | ~0,1 % |
| Serialização da resposta | 1 ms | 1 ms | ~0,04 % |
| *Não atribuído* (Tomcat, leitura do pedido, mapeamento DTO, despacho assíncrono) | — | ~413 ms | ~15 % |

**O algoritmo genético é o caminho.** Cronograma e serialização somados não chegam a 0,2 % do tempo
do pior caso. Qualquer otimização fora do AG mexe em menos de um sexto do total.

> **Limitação declarada.** Os ~413 ms não atribuídos não puderam ser decompostos de fora. A
> aplicação **tem** um cronômetro Micrometer (`dynamicstudyplanner.optimization.duration`) e expõe
> `/actuator/prometheus`, mas `SecurityConfig` libera apenas `/api/v1/**` e a Swagger UI — o
> endpoint responde **403**. A métrica existe e não pode ser lida. Registrado como achado **F9**.

### 1.3 O perfil de CPU: o AG não gasta o tempo onde parece

2 419 amostras de CPU coletadas pelo JFR durante a carga. Agrupando pelo **frame de topo** — o método
onde a CPU de fato estava, e não apenas quem chamou quem:

| Fatia | O que é |
|---:|---|
| **12,2 %** | `sun.security.provider.SecureRandom.engineNextBytes` |
| **11,1 %** | `sun.security.provider.NativePRNG$RandomIO.implNextBytes` |
| 10,1 % | `java.util.HashMap.putVal` |
| 7,2 % | `java.util.HashMap.getNode` |
| 5,3 % | `Collections$UnmodifiableMap$UnmodifiableEntrySet$1.hasNext` |
| 4,9 % | `java.util.HashMap$HashIterator.nextNode` |
| 3,0 % | `sun.security.provider.DigestBase.engineDigest` |
| 3,0 % | `java.util.HashMap.keysToArray` |
| 2,6 % | `sun.security.provider.NativePRNG.engineNextBytes` |
| 2,4 % | `TournamentSelection.select` — **o primeiro método do projeto na lista** |
| 2,3 % | `Subject.equals` |
| 2,1 % | `sun.security.provider.SecureRandom.updateState` |

Somando por família:

- **~31 % em geração criptográfica de números aleatórios** (`SecureRandom` e o `NativePRNG` por
  trás dele);
- **~22 % em operações de `HashMap`** (inserção, busca, redimensionamento, iteração);
- **~11 % nos invólucros de coleção imutável** (`Collections.unmodifiableMap` e afins);
- **menos de 3 % no método de maior peso escrito neste projeto.**

Os três achados de maior impacto desta etapa saem daí.

---

## 2. Consultas a banco de dados

**Não se aplica: o sistema não tem banco de dados.** Isto não é presunção — foi verificado:

| Verificação | Resultado |
|---|---|
| Dependências de persistência no `pom.xml` (JPA, Hibernate, JDBC, R2DBC, Mongo, Redis, Cassandra, Elasticsearch, Flyway, Liquibase, driver de banco) | **0 ocorrências** |
| `@Entity`, `@Repository`, `@Table`, `@Query`, `JpaRepository`, `CrudRepository`, `EntityManager`, `DataSource`, `JdbcTemplate` em `src/main` | **0 arquivos** |

O `README.md` declara a arquitetura sem estado, e o inventário da etapa 00 registrou o mesmo. Logo:

- **N+1** — não existe consulta, logo não existe consulta por item de lista;
- **Índice ausente** — não existe índice a faltar;
- **Consulta trazendo dado demais** — não existe consulta.

O análogo mais próximo, e o que este relatório cobre no lugar, é o **recálculo em laço sobre dados
que não mudam** — o padrão N+1 aplicado a computação em vez de a I/O. Está na §4, achados **F4** e
**F5**.

---

## 3. Chamadas síncronas bloqueantes

### 3.1 Não há chamada externa

| Verificação | Resultado |
|---|---|
| `RestTemplate`, `WebClient`, `HttpClient`, `FeignClient`, `OkHttp`, `RestClient`, `URLConnection`, `Socket` em `src/main` | **0 arquivos** |

Não há serviço externo a chamar, em série ou em paralelo. Todo o trabalho é CPU local.

### 3.2 O caminho assíncrono está correto

O controlador devolve `CompletableFuture` e **não bloqueia**:

```java
return plannerUseCase.generateFullStudyPlan(...)
        .orTimeout(30, TimeUnit.SECONDS)
        .thenApply(result -> ResponseEntity.ok(resultMapper.toResponse(result)));
```

Busca por `.get()`, `.join()`, `Thread.sleep` e `await()` sobre futuros em `src/main`: **nenhuma
ocorrência**. A thread do Tomcat é liberada durante o cálculo, como deveria.

### 3.3 O achado real: a concorrência não compra nada

Carga contra a aplicação real, payload típico, 32 requisições por nível:

| Concorrência | Mediana | p90 | Vazão |
|---:|---:|---:|---:|
| 1 | 34 ms | 38 ms | 24,9 req/s |
| 2 | 71 ms | 74 ms | 25,9 req/s |
| 4 | 156 ms | 174 ms | 24,7 req/s |
| 8 | 278 ms | 301 ms | 26,6 req/s |
| 16 | 536 ms | 652 ms | 26,4 req/s |

**A vazão é plana em ~25 req/s do começo ao fim, enquanto a latência cresce linearmente.** É a
assinatura de um sistema já saturado com **uma** requisição: as quatro CPUs já estão ocupadas por um
pedido só, porque a avaliação de fitness é paralela por dentro. Concorrência adicional apenas divide
o mesmo bolo.

Isso tem duas consequências práticas, e ambas viram achado:

**O executor está dimensionado para 8 threads numa máquina de 4 núcleos.**

```properties
optimizer.thread-pool-size=8      # application.properties:54
```

```java
// AsyncConfig.java — o padrão, quando a propriedade não é informada
Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
log.info("Configuring optimizerTaskExecutor with {} cores", workerThreads);
```

O comentário do código diz *"ideal thread count is near the core count"* e a mensagem de log chama o
número de "cores" — mas o valor é uma constante de arquivo, não uma leitura da máquina. Na
inicialização a aplicação registrou literalmente *"Configuring optimizerTaskExecutor with 8 cores"*
num contêiner de 4. → **F7**

**A paralelização interna compete com a paralelização entre requisições.** A/B medido, mesma carga,
mudando apenas o paralelismo do `ForkJoinPool` comum — que é o pool que `parallelStream()` usa:

| | Fitness paralela (padrão) | Fitness sequencial |
|---|---:|---:|
| Latência, 1 requisição | **34 ms** | 43 ms |
| Vazão, 8 concorrentes | 26,6 req/s | **30,4 req/s** |

Paralelizar a fitness deixa **uma** requisição 21 % mais rápida e o **servidor** 13 % menos
produtivo. É a troca clássica de `parallelStream()` dentro de um caminho de requisição: o pool é
global e compartilhado, então o ganho de um pedido é tirado dos outros. → **F6**

---

## 4. Cache não aproveitado

**Não existe cache de computação no sistema.** O Caffeine que aparece no `pom.xml` é usado
exclusivamente pelo `RateLimitingFilter` para contar requisições por endereço — não guarda nenhum
resultado de cálculo.

Duas recomputações foram medidas, ambas com entradas constantes durante toda a execução:

### 4.1 `LearningModel.requiredSessions(subject, horizon)` — F4

```java
// RetentionObjective.java:74, dentro do laço por disciplina
double required = LearningModel.requiredSessions(subject, horizon);
```

```java
// LearningModel.java:77 — função pura, sem estado
public static double requiredSessions(Subject subject, int planningHorizonDays) {
    double horizon = Math.max(1.0, planningHorizonDays);
    return Math.max(1.0, horizon / timeConstantDays(subject));
}
```

Depende apenas da carga cognitiva da disciplina e do horizonte — **os dois fixos durante a
execução inteira**. É chamada uma vez por disciplina, por indivíduo, por geração.

| | Valor medido |
|---|---:|
| Custo por chamada | 17,29 ns |
| Chamadas no pior caso (500 × 1000 × 24) | **12 012 000** |
| Resultados distintos possíveis | **24** |
| Custo total | **208 ms** — ~9 % do tempo do AG |

### 4.2 `StudyPlan.getTotalDays()` — F5

```java
public int getTotalDays() {
    return this.daysPerSubject.values().stream().mapToInt(Integer::intValue).sum();
}
```

`StudyPlan` é **imutável**: o mapa é embrulhado em `Collections.unmodifiableMap` no construtor e o
campo é `final`. O total, portanto, nunca muda depois de o objeto existir — e é recalculado por
varredura completa a cada chamada. Os dois operadores de cruzamento o chamam a cada filho gerado.

| | Valor medido |
|---|---:|
| Custo por chamada (24 disciplinas) | 126,4 ns |
| Chamadas no pior caso | 500 500 |
| Custo total | **63 ms** — ~2,7 % do tempo do AG |

### 4.3 Cache de resposta: não se aplica

Não há repetição plausível de requisição. Cada pedido carrega um edital e um perfil de estudante
próprios, e o AG é estocástico — a mesma entrada nem sequer produz a mesma saída, já que a semente
não é informada pelo cliente (pendência **P3** de `docs/revisao-ag/04-robustez.md`). Cachear a
resposta exigiria antes tornar o resultado determinístico, o que é decisão de produto, não de
performance.

---

## 5. Serialização

**Não é gargalo.** Medido sobre o objeto de resposta real, 200 repetições:

| Operação | Tamanho | Mediana |
|---|---:|---:|
| Escrita (serialização) | 107 792 B | **0,59 ms** |
| Leitura (desserialização) | 107 792 B | 1,04 ms |

Isso é aproximadamente 180 MB/s de escrita — desempenho normal do Jackson, sem indício de
configuração ruim.

Na resposta HTTP real o corpo tem **37 KB**, não 107 KB, porque a camada de mapeamento converte o
objeto de domínio num DTO mais enxuto antes de serializar. Ou seja: o payload que sai pela rede é
**um terço** do objeto interno, e mesmo o objeto interno completo custa menos de 1 ms.

Para dimensionar: a serialização é **0,04 %** do pior caso e **2 %** do caso típico. Mesmo eliminada
por completo, a diferença seria imperceptível.

---

## 6. O motor de planejamento

### 6.1 Como o tempo escala

**Por gerações** — linear, como esperado (15 disciplinas, 50 indivíduos):

| Gerações | 10 | 50 | 100 | 250 | 500 | 1000 |
|---|---:|---:|---:|---:|---:|---:|
| Mediana | 2 ms | 9 ms | 15 ms | 38 ms | 70 ms | 136 ms |

**Por tamanho da população** — linear (15 disciplinas, 100 gerações):

| População | 10 | 25 | 50 | 100 | 250 | 500 |
|---|---:|---:|---:|---:|---:|---:|
| Mediana | 7 ms | 10 ms | 15 ms | 23 ms | 55 ms | 109 ms |

**Por disciplinas** — sublinear (1000 gerações, 200 indivíduos, para os custos fixos por geração não
dominarem):

| Disciplinas | 5 | 10 | 20 | 30 | 40 |
|---|---:|---:|---:|---:|---:|
| Mediana | 257 ms | 348 ms | 504 ms | 721 ms | 864 ms |
| Por disciplina | 51,4 ms | 34,8 ms | 25,2 ms | 24,0 ms | 21,6 ms |

Oito vezes mais disciplinas custam 3,4 vezes mais tempo: há um termo constante grande por geração
(cópia da população, ordenação, seleção) que se amortiza conforme o problema cresce.

### 6.2 A fronteira real não é o tempo

O DTO admite até **50 disciplinas**, **1000 gerações** e **500 indivíduos**. O pior caso que o AG
consegue de fato executar leva **~2,3 s**, contra um limite de **30 s** no controlador — uma folga
de **13×**.

Mas boa parte do espaço de entrada declarado **nunca chega ao AG**:

| Disciplinas | 365 dias (máximo do DTO) |
|---:|---|
| 40 | 28 ms |
| **45** | **rejeitado**: piso de dias mínimos (406) excede os dias disponíveis (365) |
| **50** | **rejeitado**: piso (462) excede 365 |

A partir de ~44 disciplinas de dificuldade média, o piso `15 × n` de `BaselineCalculator` torna
qualquer pedido inviável e a requisição volta com **422** antes de a evolução começar. **O limite
prático do sistema é a regra de negócio, não a CPU** — e isso é a pendência **P5**, já registrada em
`docs/revisao-ag/04-robustez.md`, aparecendo agora pelo lado da performance.

### 6.3 Decomposição por fase, e o que está paralelizado

Cronometrando cada operador genético por decoração (4 núcleos):

| Cenário | Parede | Seleção | **Cruzamento** | Mutação | Fitness (soma de CPU) | Threads na fitness |
|---|---:|---:|---:|---:|---:|---:|
| 15d / 100g / 50p | 37 ms | 0 ms | 16 ms | 1 ms | 15 ms | 4 |
| 15d / 500g / 50p | 143 ms | 4 ms | 58 ms | 6 ms | 79 ms | 4 |
| 15d / 100g / 250p | 99 ms | 4 ms | 46 ms | 4 ms | 58 ms | 4 |
| **24d / 1000g / 500p** | **1 614 ms** | 97 ms | **816 ms** | 61 ms | 1 214 ms | 4 |

Respondendo diretamente à pergunta da tarefa:

**A fitness já é paralela.** `Population.calculateFitness` usa `individuals.parallelStream()`, e a
medição confirma **4 threads distintas** executando avaliações.

**Seleção, cruzamento e mutação são sequenciais.** O laço `createOffspring` em
`GeneticAlgorithm.evolvePopulation` gera um filho por vez, numa única thread. Somados, são
**974 ms** dos 1 614 ms do pior caso — e o cruzamento sozinho responde por **816 ms**, metade do
tempo total de parede.

O perfil de CPU concorda: `WeightedAverageCrossover.crossover` e `createWeightedAverageGenes` são,
juntos, o maior consumidor entre os métodos do projeto.

### 6.4 O ganho da paralelização existente é pequeno, e piora com mais threads

Mesma carga, variando apenas o paralelismo do pool comum (mediana de 9 execuções):

| Threads | Mediana | Ganho sobre 1 thread |
|---:|---:|---:|
| 1 | 1 516 ms | — |
| **2** | **1 263 ms** | **1,20×** |
| 4 | 1 319 ms | 1,15× |

**Quatro threads são piores que duas.** Com ~60 % do trabalho sequencial (o laço de descendentes), a
lei de Amdahl limita o ganho máximo a ~1,6× — e o custo de coordenação do `ForkJoinPool` come o
resto. Paralelizar a fitness, que é a parte que já está paralela, não tem mais o que dar. → **F3**

### 6.5 O maior achado: o AG gasta um terço do tempo gerando aleatoriedade criptográfica

```java
// RandomProvider.java:14
private static Random instance = new SecureRandom();
```

`SecureRandom` é um gerador **criptográfico**: cada número passa por SHA-1 e por leitura de entropia
do sistema operacional, porque o requisito é ser imprevisível para um adversário. Um algoritmo
genético não tem adversário — precisa de números bem distribuídos, não imprevisíveis.

O gerador é consultado em **15 pontos** do código de produção, dentro dos laços mais quentes:
sorteio de cruzamento, ponto de corte, disciplina a ajustar no reparo, torneio de seleção, decisão
de mutação.

A/B com a mesma carga, trocando apenas a fonte de aleatoriedade:

| Cenário | `SecureRandom` (produção) | `java.util.Random` | Redução |
|---|---:|---:|---:|
| 15 disc. / 100 ger. / 50 pop. | 35 ms | **16 ms** | **54 %** |
| 24 disc. / 1000 ger. / 500 pop. | 2 299 ms | **1 365 ms** | **41 %** |

Confere com o perfil de CPU, que atribuiu ~31 % das amostras às classes de `sun.security.provider`.

**E há um agravante que vale mais do que o número.** Todo teste e todo benchmark do repositório
substitui a fonte antes de medir:

```java
// benchmark/strategy/ProductionGeneticAlgorithm.java:125
RandomProvider.setInstance(new Random(seed));
```

A classe cujo Javadoc diz existir para medir *"every production component as shipped"* troca, na
primeira linha, o componente que responde por 41 % do tempo de produção. **Todos os números de tempo
publicados em `docs/revisao-ag/` foram medidos numa configuração ~40 % mais rápida do que a que roda
de verdade.** → **F1**

---

## 7. Lista priorizada por impacto medido

Ordenada por **tempo de CPU que o achado explica**, com o número que o sustenta. Nenhum item entrou
por suposição.

### Nível 1 — explicam dezenas de por cento do tempo

| # | Achado | Localização | Impacto medido |
|---|---|---|---|
| **F1** | **`SecureRandom` como fonte de aleatoriedade do AG.** Gerador criptográfico usado para sorteio de simulação, consultado em 15 pontos dos laços quentes | `util/RandomProvider.java:14` | **41 % do pior caso** (2 299 → 1 365 ms) e **54 % do caso típico** (35 → 16 ms). Perfil de CPU: **~31 %** das amostras em `sun.security.provider`. Agravante: os benchmarks trocam a fonte antes de medir, então nenhum número publicado reflete produção |
| **F2** | **Cruzamento sequencial e caro.** `createOffspring` gera um filho por vez, numa thread; `WeightedAverageCrossover` é o método mais quente do projeto | `ga/GeneticAlgorithm.java:78-82`; `ga/strategy/crossover/WeightedAverageCrossover.java` | **816 ms de 1 614 ms** no pior caso — **51 % do tempo de parede**, inteiramente monothread |
| **F3** | **Paralelismo no lugar errado.** A fitness, que já é paralela, rende apenas 1,20×; e 4 threads são **piores** que 2 | `ga/Population.java:116` | 1 thread 1 516 ms · 2 threads **1 263 ms** · 4 threads 1 319 ms. Teto de Amdahl ~1,6× com 60 % sequencial |

### Nível 2 — explicam poucos por cento cada, e são baratos de resolver

| # | Achado | Localização | Impacto medido |
|---|---|---|---|
| **F4** | **`requiredSessions` recalculada 12 milhões de vezes** para 24 resultados distintos, com entradas constantes na execução inteira | `ga/fitness/objective/RetentionObjective.java:74`; `objective/LearningModel.java:77` | 17,29 ns × 12 012 000 = **208 ms**, ~9 % do tempo do AG |
| **F5** | **`getTotalDays()` recalcula a soma completa** de um objeto imutável, a cada filho gerado | `domain/StudyPlan.java:47` | 126,4 ns × 500 500 = **63 ms**, ~2,7 % do tempo do AG |
| **F6** | **`parallelStream()` no caminho de requisição** troca vazão do servidor por latência de um pedido | `ga/Population.java:116` | Fitness sequencial: −21 % de latência isolada, **+13 % de vazão** com 8 concorrentes (26,6 → 30,4 req/s) |
| **F7** | **Executor com 8 threads em máquina de 4 núcleos.** Valor fixo em arquivo, com log dizendo "cores" | `application.properties:54`; `config/AsyncConfig.java:33-37` | Vazão plana em ~25 req/s de 1 a 16 concorrentes; latência cresce linear (34 → 536 ms). O pool não aumenta capacidade, só enfileira |

### Nível 3 — custo estrutural difuso, sem correção óbvia

| # | Achado | Localização | Impacto medido |
|---|---|---|---|
| **F8** | **Invólucros de coleção imutável nos laços quentes.** `StudyPlan` embrulha o mapa em `Collections.unmodifiableMap`, e todo objetivo de fitness itera esse mapa por indivíduo, por geração | `domain/StudyPlan.java:29` | Perfil: **~11 %** das amostras de CPU nos invólucros. Micro-medição: iterar 24 entradas custa **426 ns** envolto contra **210 ns** no `HashMap` cru (**+103 %**). A imutabilidade é uma garantia real — o custo é o preço dela, e a decisão é de projeto |
| **F9** | **A métrica de tempo existe e não pode ser lida.** `dynamicstudyplanner.optimization.duration` é publicada e `/actuator/prometheus` está na lista de exposição, mas `SecurityConfig` só libera `/api/v1/**` e a Swagger UI | `config/SecurityConfig.java:71-78`; `application.properties` | `/actuator/prometheus` responde **403**. Foi por isso que os ~413 ms da §1.2 não puderam ser decompostos de fora |

### O que **não** é gargalo — verificado, não presumido

| Item | Medição |
|---|---|
| **Serialização** | 0,59 ms para 107 KB. **0,04 %** do pior caso |
| **Geração do cronograma** | 3–4 ms, independente do tamanho do AG. **0,1 %** do pior caso |
| **Coleta de lixo** | 312 pausas somando **1 054 ms em 341 s** de gravação — **0,3 %** do tempo. Maior pausa: 16 ms |
| **Bloqueio de thread** | Nenhum `.get()`, `.join()`, `sleep` ou `await` sobre futuros em `src/main`. O controlador é assíncrono de ponta a ponta |
| **Banco de dados** | Não existe — 0 dependências, 0 anotações |
| **Chamada externa** | Não existe — 0 clientes HTTP |

---

## Resumo em uma tela

| Item pedido | Resultado | Veredito |
|---|---|---|
| **1. Profiling do caminho mais usado** | 49 ms típico, 2 731 ms pior. AG = **85 %**; cronograma + serialização < 0,2 % | ✅ medido |
| **2. Consultas a banco** | **Não se aplica** — 0 dependências de persistência, 0 anotações. O análogo (recálculo em laço) está em F4/F5 | ➖ inexistente |
| **3. Chamadas bloqueantes** | Sem chamada externa; controlador não bloqueia. O achado real é o pool de 8 em 4 núcleos e a vazão plana | ⚠️ F6, F7 |
| **4. Cache não aproveitado** | Nenhum cache de computação. 12 M recálculos para 24 resultados (208 ms) + soma refeita em objeto imutável (63 ms) | ⚠️ F4, F5 |
| **5. Serialização** | 0,59 ms / 107 KB | ✅ limpo |
| **6. AG: tempo e paralelização** | 2,3 s no pior caso contra 30 s de limite. Fitness **é** paralela (4 threads) e rende só 1,20×; seleção/cruzamento/mutação **não** são, e valem 974 ms | ⚠️ F2, F3 |
| **7. Lista priorizada** | 9 achados, todos com número de profiling | — |

**O achado que muda tudo é o F1.** Um terço da CPU do algoritmo genético é gasto gerando
aleatoriedade criptográfica que ele não precisa, e trocar a fonte corta 41 % do pior caso e 54 % do
caso típico — sem alterar nenhuma decisão do algoritmo, apenas a qualidade estatística exigida dos
sorteios. Os achados F2 e F3, juntos, dizem onde estaria o próximo ganho: o cruzamento é metade do
tempo de parede e roda numa thread só, enquanto o esforço de paralelização atual está aplicado à
parte que já rende pouco.

**Nenhum desses números aparecia antes porque ninguém os havia medido.** O repositório tem um
cronômetro de otimização publicado que não pode ser lido (F9) e um conjunto de benchmarks que
substitui, antes de medir, justamente o componente mais caro (F1).
