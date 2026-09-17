# Etapa 00 — Inventário do repositório

**Escopo:** mapeamento fiel do estado atual de todo o repositório do SINAPSE, como base para as
etapas seguintes da varredura de qualidade (testes, segurança, estrutura, escrita/manutenibilidade,
performance, escalonamento).

**Esta etapa não avalia qualidade e não propõe correções.** Onde aparece um número ou um fato
incômodo, ele está registrado como observação, não como defeito a corrigir. A avaliação é trabalho
das etapas seguintes.

| | |
|---|---|
| **Data do levantamento** | 2026-09-01 |
| **Commit inspecionado** | `2bc2cde` — *Merge pull request #8 from gustavo-rm/claude/sinapse-ag-diagnostico-8t1ez7* |
| **Branch** | `release/v4.0` |
| **Método** | Leitura do código e da documentação; build e suíte de testes executados de fato neste ambiente; medições de cobertura e de dependências desatualizadas rodadas por linha de comando, **sem alterar nenhum arquivo do repositório** |

---

## Nota de escopo: o repositório tem um serviço, não vários

A tarefa pede o inventário dos "bounded contexts / microsserviços" do produto. O levantamento
encontrou uma realidade diferente da premissa, e o inventário é apresentado a partir do que existe.

**O repositório contém exatamente um artefato implantável:** uma aplicação Spring Boot de módulo
único (um só `pom.xml`, um só `main`, um só processo), que expõe **um único endpoint HTTP**. Não há
segundo serviço, não há chamada de rede entre componentes, não há fila de mensagens, não há banco de
dados. Toda a comunicação interna é chamada de método Java dentro do mesmo processo.

O termo *bounded context* (contexto delimitado — a fronteira dentro da qual um modelo de domínio tem
significado único e consistente, no vocabulário de Domain-Driven Design) aparece no `README.md`, na
seção "Code Quality", com esta redação: *"bounded contexts mapped to packages"* — contextos
delimitados mapeados em pacotes. Ou seja, **o próprio repositório usa "bounded context" no sentido de
pacote Java interno, não de serviço implantável separadamente.**

Por isso este documento tem duas camadas:

- A **seção 1** inventaria o serviço único, do jeito que a tarefa pede para cada serviço: stack,
  build, teste, implantação.
- A **seção 1.2** inventaria os **módulos internos** (os pacotes de topo), que são o análogo mais
  próximo de "bounded context" neste repositório, para que as etapas seguintes tenham granularidade
  de trabalho.

Se em algum momento existiu — ou está planejado — um recorte em microsserviços, ele não está neste
repositório e não foi encontrado em nenhum documento. As etapas seguintes devem trabalhar com um
monólito modular.

---

## 1. Serviços e módulos

### 1.1 O serviço

| Item | Valor observado |
|---|---|
| **Nome do produto** | SINAPSE (usado apenas na documentação em `docs/revisao-ag/`) |
| **Nome do artefato** | `DynamicStudyPlanner` — `com.ia.project:DynamicStudyPlanner` |
| **Nome da aplicação em runtime** | `Dynamic Study Planner` (`spring.application.name`) |
| **Versão no `pom.xml`** | `2.0.1` |
| **Linguagem** | Java |
| **Versão da linguagem compilada** | **21** — confirmado no bytecode gerado (`major version: 65`, `minor version: 0`) |
| **JDK disponível no ambiente** | OpenJDK 21.0.10 |
| **Framework** | Spring Boot 3.5.5 (herdado de `spring-boot-starter-parent`) |
| **Gerenciador de dependências** | Maven, com Maven Wrapper (`./mvnw`, wrapper 3.3.2, distribuição Apache Maven 3.9.11) |
| **Estilo arquitetural** | API REST *stateless* (sem estado entre requisições), monólito de módulo único |
| **Persistência** | Nenhuma. Sem banco de dados, sem cache distribuído, sem estado entre requisições |
| **Endpoint de negócio** | `POST /api/v1/optimizer/generate` — único |
| **Endpoints de infraestrutura** | `/actuator/health`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui.html` |
| **Tamanho** | 6.229 linhas em `src/main/java` (115 classes), 1.386 em `src/test/java` (14 classes), 4.525 em `benchmarks/java` (29 classes) |

### 1.2 Módulos internos (os "contextos" do repositório)

Todos vivem sob `src/main/java/com/ia/project/dynamicstudyplanner/`. Não são implantáveis
separadamente; são fronteiras de pacote.

| Módulo | Classes | Linhas | Responsabilidade observada |
|---|---:|---:|---|
| `ga/` | 39 | 2.609 | Motor de algoritmo genético (AG): população, indivíduo, operadores de seleção/cruzamento/mutação, função de *fitness* (a nota que o algoritmo atribui a cada plano candidato), objetivos, restrições e penalidades |
| `service/` | 19 | 1.456 | Orquestração, calculadoras de domínio (carga cognitiva, importância, linha de base), motor de retenção, estratégias de alocação diária |
| `api/` | 28 | 1.175 | Controller, DTOs (objetos de transporte de dados entre a API e o mundo externo), mapeadores DTO↔domínio, tratamento global de exceções, filtro de *rate limiting* |
| `domain/` | 22 | 740 | Modelos puros de domínio: `Exam`, `Subject`, `StudentProfile`, `StudyPlan`, além dos submodelos `retention`, `engagement`, `tactical`, `schedule` |
| `config/` | 5 | 192 | Configuração global: segurança, execução assíncrona, OpenAPI, fonte de aleatoriedade, propagação de contexto de log |
| `usecase/` | 1 | 15 | Uma única interface (*porta*, no vocabulário de arquitetura hexagonal): `GenerateStudyPlanUseCase` |
| `util/` | 1 | 29 | `RandomProvider` |

Além de `src/`, o repositório tem um terceiro corpo de código:

| Diretório | Classes | Linhas | Situação |
|---|---:|---:|---|
| `benchmarks/java/` | 29 | 4.525 | Módulo de medição. Anexado como **raiz de código-fonte de teste** pelo `build-helper-maven-plugin`, portanto compila em `target/test-classes` e **não entra no jar da aplicação**. O `pom.xml` e o `benchmarks/README.md` documentam essa separação explicitamente |

### 1.3 Como é construído, testado e implantado hoje

**Construção.** Maven, um único módulo, sem perfis de build customizados.

```bash
./mvnw clean package          # gera target/DynamicStudyPlanner-2.0.1.jar
```

Três plugins configurados no `pom.xml`:

| Plugin | Versão | Papel |
|---|---|---|
| `build-helper-maven-plugin` | 3.6.0 | Anexa `benchmarks/java` como raiz de fonte de teste |
| `maven-compiler-plugin` | herdada do parent (3.14.0 em execução) | Processador de anotações do Lombok; declara `source`/`target` 24 e `--enable-preview` |
| `spring-boot-maven-plugin` | herdada do parent (3.5.5) | Repacota o jar executável, excluindo o Lombok |

**Verificado neste ambiente:** o build funciona e a compilação sai em **Java 21**, não 24. O parent
do Spring Boot define `maven.compiler.release=${java.version}` e `java.version` está fixado em `21`
nas `<properties>` do `pom.xml`. Quando `release` está definido, o `javac` ignora `source` e
`target`. O log confirma (`release 21`) e o bytecode também (`major version: 65`, `minor version: 0`
— minor 0 significa que **nenhum recurso de pré-visualização foi ativado**, apesar do
`--enable-preview`). Registrado na seção 5 como R3.

**Teste.**

```bash
./mvnw test
```

Executado neste ambiente no commit `2bc2cde`: **73 testes, 0 falhas, 0 erros, 0 ignorados**, em
aproximadamente 30 segundos (incluindo o download inicial das dependências). A suíte cobre
`src/test/java` **e** os testes que vivem dentro de `benchmarks/java`
(`GeneticAlgorithmVsBaselinesTest`, `SpearmanTest`, `CorrelationAggregateTest`), porque o diretório
de benchmarks é raiz de fonte de teste.

Fora da suíte normal, há sete programas de medição com `main()` em `benchmarks/`, rodados à mão pela
linha de comando montada com `dependency:build-classpath`. Os comandos estão em
`docs/revisao-ag/README.md`, seção "Como rodar as medições", e em `benchmarks/README.md`. Eles
escrevem os cinco CSVs de `benchmarks/results/`.

**Implantação.** **Não existe.** O levantamento não encontrou:

- nenhum diretório `.github/` e nenhum workflow de CI/CD (integração e entrega contínuas);
- nenhum `Dockerfile`, `docker-compose.yml`, manifesto Kubernetes, chart Helm ou arquivo Terraform;
- nenhum `Jenkinsfile` ou configuração equivalente;
- nenhum arquivo `.yml`/`.yaml` em todo o repositório.

O `README.md` reconhece isso em duas seções. Em "Future Improvements": *"Containerize the application
with Docker"* e *"Implement CI/CD pipelines (e.g., GitHub Actions)"*. Em "Deployment": *"Currently,
the application runs via Maven wrapper locally"*. O procedimento de implantação documentado é rodar
`java -jar` no artefato gerado.

---

## 2. Ferramental por serviço

Como há um só serviço, esta seção é única.

### 2.1 Cobertura de testes

**Não há relatório de cobertura configurado no repositório.** O `pom.xml` não declara JaCoCo nem
qualquer outra ferramenta de cobertura; não há limiar mínimo, não há publicação de relatório, não há
verificação em build.

Para que as etapas seguintes tenham um ponto de partida numérico, a cobertura foi medida uma vez
neste ambiente com o JaCoCo invocado pela linha de comando (`org.jacoco:jacoco-maven-plugin:0.8.13`),
**sem alterar o `pom.xml` nem qualquer outro arquivo do repositório**. Medida apenas sobre
`src/main` — o código dos benchmarks compila em `target/test-classes` e não é instrumentado.

*Instruções* é a métrica de instruções de bytecode executadas; *ramos* é a métrica de desvios
condicionais (`if`, `switch`, operadores ternários) percorridos em ambas as direções. Ramos é sempre
a mais exigente das duas.

**Total: 67,1% de instruções, 52,9% de ramos.**

Por pacote, ordenado do menos ao mais coberto:

| Pacote | Instruções | Ramos |
|---|---:|---:|
| `ga.tactical.strategy.crossover` | 0,0% | 0,0% |
| `ga.tactical.strategy.mutation` | 0,0% | 0,0% |
| `service.calculation.fatigue` | 1,3% | 0,0% |
| `service.calculation.engagement` | 3,4% | 0,0% |
| `ga.tactical.repair` | 4,9% | 0,0% |
| `api.mapper` | 12,7% | 0,0% |
| `config.logging` | 17,6% | 0,0% |
| `api.exception` | 22,0% | 50,0% |
| `api.dto` | 34,5% | — |
| (raiz do pacote) | 37,5% | — |
| `ga.fitness.penalty` | 41,7% | 42,9% |
| `domain.exception` | 44,4% | — |
| `ga.fitness.constraint` | 54,8% | 37,5% |
| `domain.retention` | 65,1% | 50,0% |
| `api.controller` | 67,7% | 62,5% |
| `service.scheduler.strategy` | 68,7% | 61,9% |
| `ga.strategy.selection` | 71,9% | 62,5% |
| `domain.engagement` | 73,2% | 50,0% |
| `domain.exam` | 76,1% | 66,7% |
| `service.calculation.retention` | 77,1% | 50,0% |
| `domain.tactical` | 80,2% | 75,0% |
| `ga` | 82,5% | 60,9% |
| `service` | 83,0% | 83,3% |
| `ga.strategy.crossover` | 91,5% | 77,5% |
| `domain` | 93,1% | 72,7% |
| `config` | 94,3% | 50,0% |
| `ga.fitness.objective` | 96,1% | 71,4% |
| `ga.strategy.mutation` | 96,1% | 88,9% |
| `ga.fitness` | 99,2% | 83,3% |
| `service.calculation` | 99,6% | 87,5% |
| `ga.config`, `ga.factory`, `ga.generator`, `service.scheduler.tactical`, `domain.schedule`, `util` | 100,0% | 91,7%–100% |

Classes com cobertura zero ou quase zero e volume relevante de código (candidatas a inspeção na
etapa de testes):

| Classe | Instruções | Linhas |
|---|---:|---:|
| `service.calculation.fatigue.FatigueAndEnergyModel` | 1,3% | 47 |
| `ga.tactical.repair.SpacedRepetitionRepairer` | 4,9% | 24 |
| `ga.tactical.strategy.crossover.DayBoundaryCrossover` | 0,0% | 23 |
| `service.calculation.engagement.DropoutRiskPredictor` | 3,4% | 22 |
| `service.scheduler.strategy.RandomizedInterleavedStrategy` | 0,0% | 17 |
| `service.scheduler.strategy.CriticalFirstStrategy` | 0,0% | 12 |
| `ga.tactical.strategy.mutation.MethodologyMutation` | 0,0% | 11 |
| Os oito mapeadores de `api.mapper` | 4,3%–18,2% | 3–20 cada |

**Observação de leitura:** o número agregado de 67,1% mistura duas situações diferentes. O núcleo do
algoritmo genético e as calculadoras de domínio estão bem cobertos (90–100%); a periferia — camada
tática, mapeadores, modelos de fadiga e evasão — está próxima de zero. Parte disso é código que
**nenhum caminho de produção alcança** (ver seção 3.3), o que muda o significado da cobertura baixa.
A distinção entre "não testado" e "não usado" é trabalho da etapa de testes.

### 2.2 Lint, formatação e análise estática

**Nenhuma ferramenta configurada.** O levantamento procurou e não encontrou, em nenhum lugar do
repositório:

| Ferramenta | Situação |
|---|---|
| Checkstyle | ausente |
| Spotless / google-java-format / Palantir format | ausente |
| PMD | ausente |
| SpotBugs / FindBugs | ausente |
| Error Prone | ausente |
| SonarQube / SonarCloud | ausente (nenhum `sonar-project.properties`, nenhuma propriedade `sonar.*` no `pom.xml`) |
| `.editorconfig` | ausente |
| `maven-enforcer-plugin` | ausente — confirmado pelo aviso emitido na execução: *"Project does not define required minimum version of Maven"* |

O `README.md` registra a ausência com franqueza, na seção "Code Quality": *"Linting and Formatting:
(Assuming IDE defaults, further configuration via Checkstyle or Spotless is recommended)"* e
*"Static Analysis: (Can be integrated via SonarQube or similar tools in future CI pipelines)"*.

A única verificação automática que roda hoje é a suíte de testes JUnit, e só quando alguém a executa
manualmente — não há gatilho automático.

### 2.3 Versões de linguagem e runtime

| Item | Declarado | Efetivo |
|---|---|---|
| `java.version` (propriedade Maven) | 21 | 21 |
| `maven.compiler.source` / `target` | **24** (no `maven-compiler-plugin`) | **ignorados** — `release=21` do parent prevalece |
| `--enable-preview` | declarado em `compilerArgs` | **sem efeito observável** — bytecode com `minor version: 0` |
| Spring Boot | 3.5.5 | 3.5.5 |
| Maven Wrapper | 3.9.11 | — |
| Maven do ambiente | — | 3.9.x (`/opt/maven`) |

### 2.4 Dependências desatualizadas

**Não há nenhuma ferramenta de verificação de dependências configurada no repositório:** sem
Dependabot (não há `.github/dependabot.yml`), sem Renovate, sem OWASP Dependency-Check, sem
`versions-maven-plugin` declarado, sem varredura de vulnerabilidades em qualquer forma. Portanto,
**nenhuma vulnerabilidade está sinalizada hoje por ferramenta existente** — não porque não existam,
mas porque nada procura.

Para dar um ponto de partida, o `versions-maven-plugin` foi executado uma vez por linha de comando,
sem ser adicionado ao `pom.xml`. Resultado das **dependências declaradas diretamente**, ignorando
versões de pré-lançamento (`-M`, `-RC`, `-alpha`, `-beta`):

| Dependência | Em uso | Última estável |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-parent` (e todos os *starters*) | 3.5.5 | **4.1.1** |
| `org.springframework.security:spring-security-test` | 6.5.3 | 7.1.1 |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.5.0 | **3.1.0** |
| `net.logstash.logback:logstash-logback-encoder` | 7.4 | **9.0** |
| `io.micrometer:micrometer-registry-prometheus` | 1.15.3 | 1.17.1 |
| `io.micrometer:micrometer-tracing-bridge-brave` | 1.5.3 | 1.7.1 |
| `org.projectlombok:lombok` | 1.18.38 | 1.18.46 |
| `com.github.ben-manes.caffeine:caffeine` | 3.2.2 | 3.2.4 |
| `jakarta.validation:jakarta.validation-api` | 3.1.1 | 3.1.1 (a 4.0.0 só existe como milestone) |
| `com.bucket4j:bucket4j-core` | 8.10.1 | 8.10.1 — atual |
| `org.codehaus.mojo:build-helper-maven-plugin` | 3.6.0 | 3.6.0 — atual |

As três defasagens de maior salto são **Spring Boot 3.5 → 4.1** (uma linha maior inteira),
**springdoc 2.5 → 3.1** e **logstash-logback-encoder 7.4 → 9.0**. Nenhuma delas foi investigada
quanto a impacto ou a CVEs (identificadores públicos de vulnerabilidade) nesta etapa — é trabalho da
etapa de segurança.

---

## 3. Mapa de comunicação

### 3.1 Comunicação externa

**Não há comunicação entre serviços, de nenhum tipo.** O levantamento procurou por `RestTemplate`,
`WebClient`, `@FeignClient`, `KafkaTemplate`, `RabbitTemplate`, `JmsTemplate`, `HttpClient`,
`URLConnection`, `DataSource` e `JpaRepository` em todo o código de produção e de benchmark: **zero
ocorrências**. Não há chamada síncrona (REST, gRPC), não há chamada assíncrona (fila, tópico,
evento), não há banco de dados.

A única fronteira de rede do sistema é a entrada:

```
Cliente HTTP
    │
    │  POST /api/v1/optimizer/generate   (síncrono para o cliente; JSON)
    ▼
RateLimitingFilter  ──▶  429 Too Many Requests (se o balde do IP estiver vazio)
    │
    ▼
Spring Security  (permitAll em /api/v1/**)
    │
    ▼
OptimizerController
```

### 3.2 Fluxo interno (chamadas de método, mesmo processo)

O único fluxo de produção do sistema, do controller ao resultado:

```
OptimizerController.generateFullStudyPlan()
    │
    ├─ ExamMapper / StudentProfileMapper           DTO ──▶ domínio
    │
    └─ GenerateStudyPlanUseCase  (interface / porta)
           │
           └─ DynamicStudyPlannerService  @Async("optimizerTaskExecutor")
                  │                        devolve CompletableFuture; timeout rígido de 30 s
                  │                        aplicado no controller com .orTimeout(30, SECONDS)
                  │
                  ├─ [Passo 1 — otimização estratégica]
                  │     StudyOptimizerService.optimize()
                  │        ├─ BaselineCalculator
                  │        ├─ ImportanceCalculator
                  │        ├─ CognitiveLoadCalculator      (instanciada com `new`, não injetada)
                  │        ├─ GeneticAlgorithmFactory ──▶ DefaultGeneticAlgorithmFactory
                  │        ├─ PopulationGenerator    ──▶ DefaultPopulationGenerator
                  │        └─ FitnessEvaluator
                  │              ├─ List<FitnessObjective>   ScoreGain (0,50) · Retention (0,30) · CognitiveLoad (0,20)
                  │              │      └─ RetentionObjective ──▶ LearningModel ──▶ HybridRetentionEngine
                  │              │      └─ CognitiveLoadObjective ──▶ FatigueAndSustainabilityPenalty ──▶ FatigueAndEnergyModel
                  │              ├─ List<ConstraintValidator>  MinimumDays · MandatoryReview   (subtraídos)
                  │              └─ List<FitnessPenalty>       Dropout · FatigueAndSustainability  (multiplicados)
                  │
                  └─ [Passo 2 — agendamento tático diário]
                        StudyScheduleGenerator.generate()
                           └─ ReviewFocusedStrategy
                                 └─ CognitiveLoadBalancingStrategy
                                       └─ InterleavedCriticalStrategy
    │
    └─ FullPlannerResultMapper                     domínio ──▶ DTO ──▶ JSON
```

**Assincronia:** existe, mas é interna ao processo. `@Async("optimizerTaskExecutor")` desloca o
trabalho de CPU do pool de threads HTTP do Tomcat para um pool dedicado (`AsyncConfig`:
`corePoolSize = maxPoolSize = 8` por `optimizer.thread-pool-size`, fila limitada a 50, prefixo de
thread `Optimizer-Async-`). O `MdcTaskDecorator` copia `traceId` e `spanId` para a thread assíncrona.
Para o cliente HTTP, a chamada continua sendo uma requisição síncrona que espera a resposta.

### 3.3 Código instanciado pelo Spring mas fora do caminho de execução

Levantamento de consumidores por busca textual em todo `src/main`. Registrado aqui porque muda a
leitura de qualquer métrica de cobertura ou de complexidade nas etapas seguintes:

| Classe | Consumidores em `src/main` | Situação |
|---|---|---|
| `HybridHeuristicScheduler` | 0 | camada tática — `docs/revisao-ag` a registra como G5 (ABERTO) |
| `SpacedRepetitionRepairer` | 0 | idem |
| `DayBoundaryCrossover` | 0 | idem |
| `MethodologyMutation` | 0 | idem |
| `CriticalFirstStrategy` | 0 | registrada como G11 (ABERTO) — sem anotação Spring, sem consumidor |
| `RandomizedInterleavedStrategy` | 0 | idem G11 |
| `DropoutRiskPenalty` | 0 por referência direta | **é injetada** — o `FitnessEvaluator` recebe `List<FitnessPenalty>` e o Spring resolve por tipo |
| `FatigueAndSustainabilityPenalty` | 1 (`CognitiveLoadObjective`) | injetada da mesma forma **e** referenciada diretamente |

Sobre as duas penalidades: o Javadoc do `FitnessEvaluator` documenta que, no caminho macro, **todas
retornam 1,0 por construção** — só teriam dados para agir sobre um `TacticalStudyPlan`, que nada
produz hoje. Ou seja, o produto das penalidades é a identidade e o gancho existe para preservar o
contrato da camada tática. Isso está escrito no código, não é dedução deste inventário.

---

## 4. Documentação existente

O repositório tem **23 arquivos Markdown**, em três grupos com maturidades muito diferentes.

### 4.1 Documentação de produto (raiz)

| Documento | Linhas | Último commit | Situação aparente |
|---|---:|---|---|
| `README.md` | 237 | 2026-08-31 | **Atual no essencial.** Foi revisado na etapa 06 da revisão do AG, que removeu a atribuição incorreta a Ausubel e passou a apontar para `docs/revisao-ag/05-fitness-function.md`. Três divergências pontuais estão listadas na seção 5 |

### 4.2 Documentos de arquitetura da camada tática / ITS (raiz)

Sete documentos, todos de **25–26 de maio de 2026** — **três meses mais antigos** que a revisão do
algoritmo genético (30–31 de agosto de 2026), que reescreveu boa parte do motor.

| Documento | Linhas | Último commit |
|---|---:|---|
| `GA_TACTICAL_SCHEDULING_ARCHITECTURE.md` | 48 | 2026-05-25 |
| `RETENTION_ENGINE_ARCHITECTURE.md` | 47 | 2026-05-25 |
| `SCHEDULING_ALGORITHMS_EVALUATION.md` | 42 | 2026-05-25 |
| `DROPOUT_RISK_PREDICTOR_ARCHITECTURE.md` | 32 | 2026-05-25 |
| `FATIGUE_AND_ENERGY_MODEL_ARCHITECTURE.md` | 44 | 2026-05-25 |
| `NUMERICAL_STABILITY_AUDIT.md` | 50 | 2026-05-26 |
| `TESTING_STRATEGY_ARCHITECTURE.md` | 50 | 2026-05-26 |

**Quão desatualizados parecem.** Estes sete não são descrições do sistema construído: são documentos
**prospectivos**, escritos no tempo futuro e no modo de recomendação. Eles descrevem a transição do
planejador para um *ITS* (Intelligent Tutoring System — sistema tutor inteligente), com frases como
*"As the system evolves from allocating generic study days to a tactical, time-slot-based ITS…"* e
seções tituladas *"Recommendation for ITS"* e *"(Recommended)"*.

O problema de leitura é que eles ficam na raiz do repositório, com nomes de documento de arquitetura
de referência (`..._ARCHITECTURE.md`, `..._AUDIT.md`), sem nenhuma marca de que descrevem um estado
**planejado e não construído**. Três exemplos concretos da distância entre o que dizem e o que existe:

- `TESTING_STRATEGY_ARCHITECTURE.md` prescreve testes determinísticos para `FatigueAndEnergyModel`,
  `HybridRetentionEngine`, `DropoutRiskPredictor` e `FitnessEvaluator`, e testes baseados em
  propriedades para os operadores. Hoje: `FatigueAndEnergyModel` tem 1,3% de cobertura,
  `DropoutRiskPredictor` tem 3,4%, e não há nenhum teste baseado em propriedades no repositório.
- `GA_TACTICAL_SCHEDULING_ARCHITECTURE.md` propõe substituir o cromossomo `Map<Subject, Integer>` por
  uma linha do tempo estruturada. Hoje o cromossomo macro continua sendo `Map<Subject, Integer>` — o
  que `docs/revisao-ag/` confirma e usa como premissa de várias conclusões.
- `NUMERICAL_STABILITY_AUDIT.md` descreve como "estado atual" recompensas em `Math.log(1.0 + days)` e
  penalidades multiplicativas. Ambos mudaram: o commit `d21c7fa` substituiu `ln(1+d)` por uma curva de
  maestria com teto, e o `FitnessEvaluator` hoje **subtrai** violações de restrição em vez de
  multiplicá-las, com o Javadoc explicando por quê.

### 4.3 Documentação de engenharia (`docs/revisao-ag/`)

Treze documentos produzidos entre 30 e 31 de agosto de 2026, mais o `README.md` do diretório, que
funciona como índice e como fonte única de status.

| Documento | O que registra |
|---|---|
| `README.md` | Índice, números canônicos, status das pendências G1–G13, pendências de decisão humana, padrão de escrita, comandos de medição |
| `00-diagnostico.md` | Mapeamento do AG sem juízo de valor; 51 riscos R1–R51 |
| `01-auditoria-fitness.md` | Auditoria de cada termo da fitness |
| `02-formulacao.md` | Soma ponderada × multiobjetivo × Pareto |
| `03-validacao.md` | AG × baselines; harness, 6 estratégias, 8 instâncias |
| `04-robustez.md` | Determinismo, variância entre sementes, hiperparâmetros, casos de borda; pendências P1–P7 |
| `05-fitness-function.md` | Documentação de referência permanente da função de fitness |
| `06-decisao-ausubel.md` | Decisão de não implementar sequenciamento por pré-requisitos |
| `06-regime-alta-carga.md` | Fronteira do regime de alta carga |
| `06-limite-troca-pesos.md` | Troca pontuação × retenção; pendência de negócio aberta |
| `06-verificacao-pos-rodada.md` | Verificação independente; origem de G1–G11 |
| `07-correcao-metrica-e-ausubel.md` | Métrica corrigida, baseline remedida |
| `08-saturacao-e-amostragem.md` | Estado atual: saturação, heterogeneidade, decisão de não expandir a amostra |
| `PR-release-4.0.md` | Descrição do PR da release 4.0 |

**Situação:** é a documentação mais atual e mais rigorosa do repositório, escrita em português, com
padrão de escrita declarado (`README.md`, seção "Padrão de escrita destes relatórios") que esta
varredura segue. Está sincronizada com o código: o commit mais recente do repositório é o merge da
linha de trabalho que a produziu. Rastreia treze pendências, **oito resolvidas e cinco abertas**
(G5, G6, G11, G12, G13), além de quatro pendências de decisão humana.

`benchmarks/README.md` documenta o módulo de medição, o mecanismo de isolamento e os comandos.
Também parece atual.

### 4.4 O que não existe

- **Nenhum ADR** (Architecture Decision Record — registro curto e datado de uma decisão
  arquitetural, seu contexto e suas consequências). Não há diretório `adr/`, `decisions/` ou
  equivalente. As decisões arquiteturais que estão registradas — soma ponderada em vez de Pareto,
  não implementar Ausubel, não expandir a amostra — vivem dentro dos relatórios de
  `docs/revisao-ag/`, no formato de relatório, não de ADR.
- **Nenhum `CONTRIBUTING.md`**, apesar de o `README.md` ter uma seção "Contributing" com o fluxo de
  fork e pull request.
- **Nenhum `CHANGELOG.md`**.
- **Nenhum arquivo de especificação OpenAPI versionado.** O contrato só existe em runtime, gerado
  pelo springdoc em `/v3/api-docs`.
- **Nenhum `CLAUDE.md`** ou arquivo equivalente de instruções de repositório.

---

## 5. Riscos e pontos frágeis observados de relance

Registro de coisas notadas durante o mapeamento, **sem investigação**. O objetivo é que as etapas
seguintes não repitam o trabalho de descoberta. Cada item aponta a etapa que deve tratá-lo. **Nenhum
destes itens foi avaliado quanto a severidade real.**

### Build e toolchain

| # | Observação | Onde | Etapa |
|---|---|---|---|
| **R1** | Não há CI/CD. Nenhum workflow, nenhum gatilho automático. A suíte de 73 testes só roda quando alguém digita `./mvnw test` | raiz | estrutura |
| **R2** | Não há containerização. Sem `Dockerfile`, sem manifesto de orquestração. O procedimento de implantação documentado é `java -jar` | raiz | estrutura / escalonamento |
| **R3** | `maven-compiler-plugin` declara `source`/`target` **24** e `--enable-preview`, mas o build sai em **21** — `maven.compiler.release=21`, herdado do parent via `java.version`, prevalece e anula os três. Configuração inerte que declara uma intenção que não acontece | `pom.xml:146-148` | estrutura |
| **R4** | `cp.txt` (10,9 KB) está **versionado no repositório**. É um classpath gerado por `dependency:build-classpath`, cheio de caminhos absolutos de uma máquina específica (`/root/.m2/repository/...`). Não está no `.gitignore` | `cp.txt` | estrutura |
| **R5** | Versão do `pom.xml` é **2.0.1**, enquanto a branch é `release/v4.0`, os documentos falam em "release 4.0" e a próxima release é v4.1/v4.5. O `README.md` instrui a rodar `DynamicStudyPlanner-2.0.1.jar` em dois lugares | `pom.xml:13`, `README.md:137,235` | estrutura |
| **R6** | `spring-boot-devtools` está declarado como dependência (escopo `runtime`, `optional`). Ferramenta de desenvolvimento presente na árvore de dependências de um artefato sem separação de perfil | `pom.xml` | segurança / estrutura |
| **R7** | Não há `maven-enforcer-plugin`; o build não fixa versão mínima de Maven nem de JDK. O aviso é emitido pelo próprio Maven na execução | `pom.xml` | estrutura |

### Testes e verificação

| # | Observação | Onde | Etapa |
|---|---|---|---|
| **R8** | Nenhuma ferramenta de cobertura configurada. Baseline medido nesta etapa: **67,1% de instruções, 52,9% de ramos** — sem limiar, sem relatório publicado, sem gatilho | `pom.xml` | testes |
| **R9** | Sete classes com cobertura entre 0% e 5% e volume não trivial (`FatigueAndEnergyModel` 47 linhas, `SpacedRepetitionRepairer` 24, `DayBoundaryCrossover` 23, `DropoutRiskPredictor` 22, e as estratégias táticas). Parte disso é código não alcançado por nenhum caminho de produção — a distinção importa | seção 2.1 | testes |
| **R10** | Os oito mapeadores de `api.mapper` estão entre 4,3% e 18,2% de cobertura e **0% de ramos**. São o ponto de tradução entre o contrato externo e o domínio | `api/mapper/` | testes |
| **R11** | Nenhum teste baseado em propriedades, apesar de `TESTING_STRATEGY_ARCHITECTURE.md` prescrever exatamente isso para os operadores genéticos | `src/test/` | testes |
| **R12** | As sete medições de `benchmarks/` que geram os números publicados rodam por invocação manual de `main()` com classpath montado à mão. Só `GeneticAlgorithmVsBaselinesTest`, `SpearmanTest` e `CorrelationAggregateTest` entram na suíte automática | `benchmarks/` | testes |

### Segurança

| # | Observação | Onde | Etapa |
|---|---|---|---|
| **R13** | `SecurityConfig` aplica `permitAll()` a todo `/api/v1/**` e à Swagger UI, com CSRF desabilitado. É uma escolha declarada e justificada no `README.md` ("open optimization engine"), registrada aqui só para que a etapa de segurança a confirme contra a intenção atual do produto | `config/SecurityConfig.java` | segurança |
| **R14** | Os endpoints do Actuator (`/actuator/health`, `/actuator/prometheus`) não têm regra própria e caem em `anyRequest().authenticated()`. Como não há `UserDetailsService` configurado, o Spring Boot gera um usuário `user` com senha aleatória a cada inicialização — visível no log da suíte: *"Using generated security password: …"*, seguido do próprio aviso do framework de que essa configuração é só para desenvolvimento | `config/SecurityConfig.java` | segurança |
| **R15** | `RateLimitingFilter.getClientIP()` confia no primeiro valor do cabeçalho `X-Forwarded-For` sem validar a origem. Um cliente que envie o cabeçalho diretamente escolhe a própria chave de balde | `api/controller/RateLimitingFilter.java` | segurança |
| **R16** | O estado do rate limiting é um cache Caffeine **em memória, por instância** (`maximumSize` 10.000, expiração por inatividade de 60 min). Com N réplicas, o limite efetivo por IP é N × 5 requisições/min | `api/controller/RateLimitingFilter.java` | segurança / escalonamento |
| **R17** | Zero ferramental de varredura de dependências (sem Dependabot, Renovate, OWASP Dependency-Check ou equivalente). Nenhuma vulnerabilidade está sinalizada hoje porque nada procura | repositório | segurança |
| **R18** | Defasagem de uma linha maior no framework principal: **Spring Boot 3.5.5 → 4.1.1**. Também `springdoc 2.5.0 → 3.1.0` e `logstash-logback-encoder 7.4 → 9.0`. Nenhuma investigada quanto a CVEs ou a impacto de migração | `pom.xml` | segurança |
| **R19** | `management.tracing.sampling.probability=1.0` — 100% das requisições amostradas para *tracing*, sem distinção de perfil | `application.properties` | performance |

### Estrutura e manutenibilidade

| # | Observação | Onde | Etapa |
|---|---|---|---|
| **R20** | Seis classes com zero consumidores em `src/main` (camada tática: `HybridHeuristicScheduler`, `SpacedRepetitionRepairer`, `DayBoundaryCrossover`, `MethodologyMutation`; estratégias: `CriticalFirstStrategy`, `RandomizedInterleavedStrategy`). Já rastreadas como **G5** e **G11**, ambas ABERTAS, em `docs/revisao-ag/README.md` | seção 3.3 | estrutura |
| **R21** | `StudyOptimizerService` instancia `CognitiveLoadCalculator` com `new` em declaração de campo, enquanto `DynamicStudyPlannerService` recebe a mesma classe por injeção. Dois modos de obter a mesma dependência no mesmo fluxo | `service/StudyOptimizerService.java:36-37` | estrutura |
| **R22** | O mesmo arquivo usa nomes de classe totalmente qualificados em declarações de campo e parâmetros de construtor (`com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator`) em vez de `import` | `service/StudyOptimizerService.java` | escrita |
| **R23** | Sete documentos `*_ARCHITECTURE.md` / `*_AUDIT.md` na raiz descrevem um estado **planejado**, não construído, sem nenhuma marca disso. Três divergências concretas contra o código atual estão na seção 4.2 | raiz | escrita |
| **R24** | O `README.md` afirma *"Strict adherence to Domain-Driven Design principles with bounded contexts mapped to packages"* e classifica a camada tática como parte da arquitetura, sem mencionar que ela não é alcançada por nenhum caminho de execução | `README.md` | escrita |
| **R25** | O `README.md` declara `spring.profiles.active` com valor padrão `dev` — e `application.properties` de fato fixa `dev` como perfil ativo, sem `application-prod.properties` ou equivalente. Só existe um arquivo de configuração para todos os ambientes | `application.properties` | estrutura |
| **R26** | O comentário do `logback-spring.xml` diz que em dev/test o padrão deveria ser log legível por humanos e em produção JSON — e então, na frase seguinte, opta por JSON em todos os casos. A configuração não distingue perfis | `logback-spring.xml` | escrita |
| **R27** | Não há contrato OpenAPI versionado; a especificação só existe em runtime. Qualquer mudança de contrato é invisível no diff | `api/` | estrutura |
| **R28** | Não existem ADRs. Decisões arquiteturais reais estão registradas, mas dentro de relatórios longos de `docs/revisao-ag/`, sem índice próprio de decisões | `docs/` | escrita |

### Já rastreado por `docs/revisao-ag/` — não redescobrir

Cinco pendências abertas e quatro decisões pendentes já estão documentadas, com localização e
severidade, em `docs/revisao-ag/README.md`. As etapas seguintes devem **partir delas**, não
redescobri-las:

| # | Pendência | Situação |
|---|---|---|
| **G5** | Camada tática sem consumidor, sem teste (~400 linhas) | ABERTO — fora do caminho de produção |
| **G6** | `TacticalStudyPlan.getDaysPerSubject()` conta dias distintos, não planejados | ABERTO — inofensivo hoje, armadilha futura |
| **G11** | `CriticalFirstStrategy` e `RandomizedInterleavedStrategy` sem consumidor | ABERTO — cosmético |
| **G12** | `I8-escala` mantém ρ = −0,880; a temperagem dos pesos não a moveu. Principal fonte da heterogeneidade I² = 58% | **PENDENTE — no caminho de produção**, caracterizado mas não explicado |
| **G13** | A divisão uniforme vence o AG na janela de retenção em `I3` (96,0% × 76,6%) e `I4` (100,0% × 81,4%) | **PENDENTE — decisão de negócio**, exige definir a promessa de cobertura do produto |
| **P1–P7** | Correções estruturais listadas na etapa 04 (robustez) como pendência, não implementadas | ABERTO — `docs/revisao-ag/04-robustez.md` |

Também vale registrar que `docs/revisao-ag/00-diagnostico.md` catalogou **51 riscos (R1–R51)** do
algoritmo genético. **A numeração `R1–R28` deste documento é independente daquela** e trata do
repositório como um todo, não do AG. Ao citar um risco entre etapas, sempre qualifique a origem —
"R14 do inventário" ou "R14 do diagnóstico do AG".

---

## Resumo em uma tela

- **Um** serviço implantável: Spring Boot 3.5.5, Java 21, Maven, sem estado, sem banco, **um**
  endpoint de negócio.
- **Zero** comunicação entre serviços. Tudo é chamada de método no mesmo processo. A assincronia é
  interna (`@Async` + pool dedicado), não distribuída.
- **Zero** CI/CD, **zero** containerização, **zero** lint, **zero** análise estática, **zero**
  varredura de dependências.
- **73 testes, todos passando.** Cobertura medida nesta etapa: **67,1% de instruções, 52,9% de
  ramos** — alta no núcleo do algoritmo, próxima de zero na periferia.
- **Documentação em três velocidades:** `docs/revisao-ag/` é recente e rigorosa; o `README.md` está
  atual no essencial; os sete documentos de arquitetura da raiz são projeções de maio de 2026 que
  descrevem um sistema que não foi construído.
- **Cinco pendências técnicas abertas e quatro decisões pendentes** já rastreadas em
  `docs/revisao-ag/README.md`, duas delas no caminho de produção (G12, G13).
