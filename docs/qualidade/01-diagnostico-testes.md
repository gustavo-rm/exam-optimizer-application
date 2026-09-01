# Etapa 01 — Diagnóstico de testes

**Escopo:** só testes. Segurança, estrutura, escrita, performance e escalonamento vêm depois, uma
etapa de cada vez.

**Esta etapa não altera código e não implementa nada.** O objetivo é um diagnóstico confiável antes
de decidir onde investir esforço de teste. Toda medição foi feita por linha de comando, sem
modificar nenhum arquivo do repositório — inclusive o `pom.xml`.

| | |
|---|---|
| **Data** | 2026-09-01 |
| **Commit** | `d1eafcb` (etapa 00) sobre `2bc2cde` |
| **Branch** | `release/v4.0` |
| **Base** | [`00-inventario.md`](./00-inventario.md) |

**Lembrete da etapa 00:** o repositório tem **um** serviço implantável, não vários. Onde a tarefa
pede "por serviço", a resposta é única e o detalhamento útil vem por módulo interno (pacote).

### Vocabulário usado neste documento

Cada termo é explicado aqui na primeira ocorrência, conforme o padrão de escrita adotado em
`docs/revisao-ag/README.md`.

- **Cobertura de instruções** — fração das instruções de *bytecode* (o código compilado que a JVM
  executa) que a suíte percorre pelo menos uma vez.
- **Cobertura de ramos** — fração dos desvios condicionais (`if`, `switch`, `&&`, ternários)
  percorridos **nas duas direções**. É sempre a métrica mais exigente e a mais informativa das duas.
- **Teste frágil ou instável (*flaky*)** — teste que passa ou falha para o mesmo código, sem que
  nada tenha mudado, por depender de tempo, ordem de execução, aleatoriedade ou estado externo.
- ***Mock*** (objeto simulado) — substituto controlado de uma dependência, usado para isolar a
  classe sob teste.
- ***Fixture*** — os dados de entrada montados para um teste.
- **Teste de contrato** — teste que verifica se o formato de dados trocado entre dois lados (dois
  serviços, ou uma API e seus clientes) continua compatível.
- **Teste ponta a ponta (*end-to-end*, e2e)** — exercita o sistema inteiro pela mesma porta que o
  usuário real usa, sem substituir peças por simulações.
- ***Gate*** — verificação que **bloqueia** a integração de uma mudança quando falha. O oposto de
  informativa, que apenas relata e deixa passar.

---

## 1. Cobertura real

Não existe relatório de cobertura configurado no repositório — o `pom.xml` não declara JaCoCo nem
equivalente. A medição abaixo foi obtida invocando o JaCoCo pela linha de comando
(`org.jacoco:jacoco-maven-plugin:0.8.13`), sem adicioná-lo ao projeto.

```bash
mvn -o org.jacoco:jacoco-maven-plugin:0.8.13:prepare-agent test \
       org.jacoco:jacoco-maven-plugin:0.8.13:report
```

**Suíte: 73 testes, 0 falhas, 0 erros, 0 ignorados.** Cobertura medida sobre `src/main` — o código de
`benchmarks/` compila em `target/test-classes` e não é instrumentado.

| Métrica | Cobertas | Total | **Cobertura** |
|---|---:|---:|---:|
| Instruções | 4.421 | 6.593 | **67,1%** |
| **Ramos** | 256 | 484 | **52,9%** |
| Linhas | 954 | 1.394 | **68,4%** |

O número reproduziu exatamente entre duas execuções independentes.

### 1.1 O agregado esconde duas populações

Os 67,1% são a média de duas realidades opostas. O motor do algoritmo genético e as calculadoras de
domínio estão bem cobertos; a borda do sistema — a que fala com o usuário — está quase descoberta.

**Bem cobertos (≥ 90% de instruções):**

| Pacote | Instruções | Ramos |
|---|---:|---:|
| `service.calculation` | 99,6% | 87,5% |
| `ga.fitness` | 99,2% | 83,3% |
| `ga.fitness.objective` | 96,1% | 71,4% |
| `ga.strategy.mutation` | 96,1% | 88,9% |
| `config` | 94,3% | 50,0% |
| `domain` | 93,1% | 72,7% |
| `ga.strategy.crossover` | 91,5% | 77,5% |
| `ga.config`, `ga.factory`, `ga.generator`, `service.scheduler.tactical`, `domain.schedule`, `util` | 100% | 91,7%–100% |

**Quase descobertos (≤ 25% de instruções):**

| Pacote | Instruções | Ramos |
|---|---:|---:|
| `ga.tactical.strategy.crossover` | 0,0% | 0,0% |
| `ga.tactical.strategy.mutation` | 0,0% | 0,0% |
| `service.calculation.fatigue` | 1,3% | 0,0% |
| `service.calculation.engagement` | 3,4% | 0,0% |
| `ga.tactical.repair` | 4,9% | 0,0% |
| `api.mapper` | 12,7% | **0,0%** |
| `config.logging` | 17,6% | 0,0% |
| `api.exception` | 22,0% | 50,0% |

### 1.2 Os métodos que nunca executam

Esta é a medição mais importante do documento. O relatório do JaCoCo em nível de método mostra, com
precisão, **qual código de produção nenhum dos 73 testes chega a executar**:

| Método | Instruções cobertas | Situação |
|---|---:|---|
| `OptimizerController.generateFullStudyPlan` | **0 / 35** | **nunca executado** |
| `DynamicStudyPlannerService.generateFullStudyPlan` | **0 / 41** | **nunca executado** |
| `GlobalExceptionHandler.handleTimeoutException` | 0 / 31 | nunca executado |
| `GlobalExceptionHandler.handleDomainException` | 0 / 33 | nunca executado |
| `GlobalExceptionHandler.handleRateLimitExceededException` | 0 / 32 | nunca executado |
| `GlobalExceptionHandler.handleIllegalArgumentException` | 0 / 33 | nunca executado |
| `GlobalExceptionHandler.handleAllUncaughtException` | 0 / 31 | nunca executado |
| `GlobalExceptionHandler.handleConstraintViolationException` | 0 / 42 | nunca executado |
| `GlobalExceptionHandler.handleAuthenticationException` | 0 / 32 | nunca executado |
| `GlobalExceptionHandler.handleAccessDeniedException` | 0 / 32 | nunca executado |
| `FatigueAndEnergyModel.calculateBurnoutRisk` | 0 / 132 | nunca executado |
| `FatigueAndEnergyModel.calculateBiphasicCurve` | 0 / 60 | nunca executado |
| `FatigueAndEnergyModel.getExpectedEnergyLevel` | 0 / 35 | nunca executado |
| `DropoutRiskPredictor.calculateRiskScore` | 0 / 85 | nunca executado |
| `HybridRetentionEngine.isReviewMandatory` | 0 / 17 | nunca executado |
| `Population.getWorst` | 0 / 16 | nunca executado |
| `Population.getAverageFitness` | 0 / 15 | nunca executado |
| `StudyOptimizerService.runEvolution` | 39 / 103 | parcial |
| `MandatoryReviewConstraint.isValid` | 11 / 71 | parcial (só a guarda de entrada) |
| `EvolutionContext.normalize` | 59 / 88 | parcial |
| `EvolutionContext.temper` | 61 / 76 | parcial |

Das nove funções do `GlobalExceptionHandler`, **oito nunca executam**. A única que roda é a de erro
de validação.

---

## 2. Qualidade dos testes, não só quantidade

### 2.1 Testes que só verificam que "não lança exceção"

Foram encontrados **três**, e eles não são equivalentes entre si — vale distinguir.

**a) `GaEdgeCasesTest.tinyGaConfigurationStillRuns` — é o caso puro.**

```java
assertThatCode(() -> optimizer().optimize(exam, profile, 60, 1, 2))
        .doesNotThrowAnyException();
```

Roda o algoritmo genético na configuração mínima (1 geração, população 2) e não afirma **nada** sobre
o plano produzido: nem que respeita o orçamento, nem que aloca as três disciplinas, nem que a
*fitness* está em `[0,1]`. Um algoritmo que devolvesse um plano vazio passaria.

**b) `PrerequisiteSequencingDiagnosticTest.productionPipelineSatisfiesTheAssertion` — é o padrão
disfarçado.**

```java
assertThat(productionPipeline()).isNotNull();
```

A intenção real é boa: garantir que o construtor do `FitnessEvaluator`, que valida se os pesos dos
objetivos somam 1,0, não rejeita a composição de produção. Mas a asserção escrita verifica que um
construtor devolveu um objeto — coisa que em Java é impossível falhar sem lançar exceção. O teste
funciona por efeito colateral. `assertThatCode(...).doesNotThrowAnyException()` diria o que ele
realmente verifica.

**c) `DynamicStudyPlannerApplicationTests.contextLoads` — vazio, mas *não* inútil.**

```java
@SpringBootTest
class DynamicStudyPlannerApplicationTests {
    @Test void contextLoads() {}
}
```

Zero asserções. Ainda assim é o **único** teste do repositório que exercita a fiação real do Spring:
como `FitnessEvaluator` é um `@Component` cujo construtor valida a soma dos pesos, subir o contexto
falharia se a composição de *beans* de produção ficasse desbalanceada. Ele cobre um risco real, por
acidente de construção. Vale registrar isso antes que alguém o classifique como ruído e o remova.

### 2.2 Testes cujas asserções são frouxas demais para prender o comportamento

As três asserções de `HybridHeuristicSchedulerTest` foram medidas com uma sonda executada fora do
repositório, contra as classes compiladas, para saber **o que elas realmente comparam**:

| Teste | O que o cenário produz de fato | O que o teste afirma |
|---|---|---|
| `shouldRespectBufferZones` | 1 bloco, **36 minutos** | `total <= 102` — folga de 66 minutos; passaria com 0, 36, 80 ou 102 |
| `shouldPrioritizeActiveRecallInEmergencyMode` | 1 bloco, `ACTIVE_RECALL` | "não existe `PASSIVE_READING`" — um cronograma vazio também passa |
| `shouldGenerateNonOverlappingTimeSlots` | 2 blocos → **1 par comparado** | não-sobreposição, verificada em exatamente um par |

A lógica de não-sobreposição do terceiro teste está **correta** (`A.fim <= B.início || A.início >=
B.fim` é a definição certa) e o laço aninhado **não** é vazio — foram medidos 2 blocos, portanto 1
par. A fragilidade não é vacuidade: é que o cenário é mínimo demais para exercitar o que o nome
promete. O primeiro é o mais frouxo dos três — asserção de limite superior com 66 minutos de folga
não prende a "zona de proteção de 15%" que o comentário descreve.

### 2.3 Redundância × caminhos críticos descobertos

Há concentração clara de esforço em poucos pontos:

| Alvo | Ocorrências em testes |
|---|---:|
| `optimize(` | 11 |
| `getTotalDays()` | 7 — em 3 classes de teste distintas |
| `calculateMinimumDays` | 4 |
| `meetsMinimumConstraints` | 3 |
| `new Subject("Math", ...)` | 17 ocorrências, em 9 arquivos |

`StudyPlan.getTotalDays()` — um método de uma linha que soma valores de um mapa — é verificado por
`StudyPlanTest`, `TransferMutationTest` e `GaEdgeCasesTest`. No mesmo repositório,
`DynamicStudyPlannerService.generateFullStudyPlan`, que orquestra o produto inteiro, tem zero.

**A distorção mais cara não é a repetição, é a alocação.** `HybridHeuristicScheduler` tem **100% de
cobertura de instruções e 3 testes dedicados** — e **nenhum consumidor em produção** (é a pendência
G5, ABERTA, registrada em `docs/revisao-ag/README.md` e na seção 3.3 do inventário). Ou seja, a
suíte investe três testes numa classe que nenhum usuário alcança, enquanto o orquestrador do único
endpoint do produto não tem nenhum.

### 2.4 A fiação de produção é reproduzida à mão em três lugares

O conjunto de componentes da *fitness* é montado manualmente, com o comentário *"The same component
set Spring wires in production"*, em três arquivos independentes:

- `IndividualTest.productionPipeline()`
- `PrerequisiteSequencingDiagnosticTest.productionPipeline()`
- `benchmarks/…/metric/MetricsCalculator` (que replica também a cadeia de estratégias de
  `DynamicStudyPlannerService`)

Nenhum deles usa a fiação real. A equivalência entre as três cópias e o que o Spring monta é
afirmada por comentário, não verificada por teste — com a única exceção parcial do `contextLoads`
descrito em 2.1(c), que detecta desequilíbrio de pesos mas não divergência de composição.

### 2.5 O que está bem feito — e deve ser preservado

O diagnóstico seria desonesto se listasse só o que falta. Três testes têm qualidade acima da média
do repositório:

- **`IndividualTest`** calcula os valores esperados **à mão**, no comentário, a partir da fórmula
  documentada (`0,480068`, `0,014248`), em vez de copiar o resultado de uma execução. Um teste assim
  falha quando a fórmula muda — que é exatamente o objetivo.
- **`GeneticAlgorithmVsBaselinesTest`** documenta a calibração do seu próprio limiar de 2%: piso de
  ruído medido (0,286%), poder de detecção (o pior déficit já registrado, 6,18%) e por que não é mais
  apertado. E afirma a lista de instâncias deficientes por **igualdade exata**, de modo a falhar
  também quando o sistema *melhora* sem que o ganho seja registrado.
- **`GaEdgeCasesTest.producedPlansAreAlwaysFeasible`** varre 5 sementes e afirma invariantes
  (orçamento respeitado, piso de dias mínimos atendido) em vez de valores pontuais.

---

## 3. Testes frágeis ou instáveis

### 3.1 O que foi executado

A suíte completa foi executada **10 vezes**: 5 na ordem padrão, conforme pedido, e mais 5 com a
ordem de execução embaralhada (`-Dsurefire.runOrder=random`). A segunda bateria foi acrescentada
porque o repositório tem estado global mutável entre testes (seção 3.3), e ordem fixa não o
exercita.

Os relatórios XML do Surefire foram comparados **caso a caso**, não só pelo total.

| Bateria | Execuções | Casos por execução | Resultado |
|---|---:|---:|---|
| Ordem padrão | 5 | 73 | 73 PASS em todas |
| Ordem aleatória | 5 | 73 | 73 PASS em todas |

**Nenhum caso de teste apresentou resultado inconsistente entre as 10 execuções.** A união dos casos
observados é de 73 em todas — nenhum teste apareceu ou desapareceu entre execuções.

### 3.2 O limite desse resultado — leia antes de citá-lo

**Dez execuções verdes provam menos do que parecem**, porque boa parte da suíte é determinística por
construção e as 10 execuções são, para essa parte, 10 cópias do mesmo cálculo.

O `BenchmarkHarness` usa `long seed = BASE_SEED + r`, com `BASE_SEED` constante. Portanto
`GeneticAlgorithmVsBaselinesTest` — 5 dos 73 testes, e o mais caro da suíte — produz resultado
**bit a bit idêntico** em toda execução. Repeti-lo não é amostragem.

A parte da suíte que realmente exercita aleatoriedade a cada execução é `GaEdgeCasesTest`, e nem toda
ela. A classificação medida:

| Teste de `GaEdgeCasesTest` | Semente | Roda o AG |
|---|---|---|
| `sameSeedProducesIdenticalPlan` | fixa | sim |
| `differentSeedsProduceDifferentPlans` | fixa | sim |
| `producedPlansAreAlwaysFeasible` | fixa (5 sementes) | sim |
| `budgetBelowMinimumDaysFloorFails` | **sem semente** (`SecureRandom`) | sim |
| `singleSubjectProducesValidPlan` | **sem semente** | sim |
| `mutationWithSingleSubjectIsANoOp` | **sem semente** | sim |
| `zeroAvailabilityCurrentlyYieldsAnEmptySchedule` | **sem semente** | sim |
| `examDateInThePastCurrentlyYieldsAnEmptySchedule` | **sem semente** | sim |
| `scalesToManySubjects` | **sem semente** | sim |
| `tinyGaConfigurationStillRuns` | **sem semente** | sim |

Sete testes rodam o algoritmo genético com `SecureRandom`, sem semente fixa. Para **esses**, as 10
execuções são amostragem real, e 10 amostras limpas é evidência razoável — não prova. Para o resto,
o verde é tautológico.

### 3.3 Fragilidades estruturais que 10 execuções não revelariam

Nenhuma delas falhou nas 10 execuções. Estão aqui porque são mecanismos capazes de produzir
instabilidade futura, e a etapa de testes deve saber que existem.

**F1 — `RandomProvider` é estado estático global mutável.**

```java
public class RandomProvider {
    private static Random instance = new SecureRandom();
    public static void setInstance(Random random) { instance = random; }
}
```

Os testes gravam nele (`RandomProvider.setInstance(new Random(20260830L))`) e o
`ProductionGeneticAlgorithm` dos benchmarks também. Hoje isso é contido por disciplina: o
`GaEdgeCasesTest` tem um `@AfterEach` que restaura `SecureRandom`, com um comentário que declara o
motivo — *"leaving a fixed seed installed would leak into other tests, since RandomProvider holds
global static state"*. A contenção depende de todo teste futuro lembrar da mesma disciplina. O
próprio `BenchmarkHarness` documenta que não é seguro para execução paralela. Habilitar paralelismo
no Surefire quebraria a suíte de forma difícil de diagnosticar.

**F2 — Asserção de tempo de parede em `scalesToManySubjects`.**

```java
assertThat(elapsedMillis).isLessThan(10_000L);
```

Medido em ~150 ms na máquina da revisão, com limite de 10 s — 66× de folga, e o comentário explica
que a intenção é pegar mudança acidental de complexidade, não variação entre máquinas. É o desenho
mais defensável possível para esse tipo de asserção, mas continua sendo uma asserção de tempo,
sensível a máquina compartilhada ou congelamento de coletor de lixo. É o candidato número um a
falha esporádica quando este projeto entrar em CI.

**F3 — Oito testes, em quatro arquivos, dependem da data em que rodam.** `LocalDate.now()` aparece como data da prova
em `CognitiveLoadCalculatorTest`, `BaselineCalculatorTest`, `ImportanceCalculatorTest` (prova
**hoje**, zero dias de horizonte) e em `ExamTest` (`now().plusDays(30)`). Os benchmarks resolveram
esse problema — `InstanceLibrary` usa âncora fixa, com comentário explícito de *"Never
`LocalDate.now()`"* — mas a suíte de `src/test` não seguiu. São testes que podem mudar de
comportamento com a passagem do tempo sem que nenhuma linha mude.

---

## 4. Tipos de teste presentes e ausentes

Classificação dos 17 arquivos de teste, por anotação e por conteúdo:

| Tipo | Quantos | Onde |
|---|---:|---|
| **Unitário puro** (sem framework) | 12 arquivos, 61 testes | `IndividualTest`, `GaEdgeCasesTest`, `TransferMutationTest`, `PrerequisiteSequencingDiagnosticTest`, `HybridHeuristicSchedulerTest`, `CognitiveLoadCalculatorTest`, `ImportanceCalculatorTest`, `ExamTest`, `StudyPlanTest`, `StudentProfileTest`, e em `benchmarks/`: `CorrelationAggregateTest`, `SpearmanTest` |
| **Unitário com *mock*** (Mockito) | 2 arquivos, 4 testes | `BaselineCalculatorTest`, `RateLimitingFilterTest` |
| **Integração com contexto Spring** | 2 arquivos, 3 testes | `OptimizerControllerTest` (`@SpringBootTest` + MockMvc), `DynamicStudyPlannerApplicationTests` |
| **Regressão algorítmica / benchmark** | 1 arquivo, 5 testes | `GeneticAlgorithmVsBaselinesTest` |
| **Contrato** | **0** | — |
| **Ponta a ponta** | **0** | — |
| **Carga / desempenho** | **0** | — |
| **Baseado em propriedades** | **0** | — |

### 4.1 Onde a ausência é mais arriscada

O exemplo que a tarefa cita — *"serviço que fala com serviço externo sem teste de contrato"* — **não
se aplica a este repositório**. O inventário estabeleceu que não há comunicação entre serviços: zero
`RestTemplate`, `WebClient`, `Feign`, `Kafka`, `Rabbit`, `JMS`, `DataSource`. Não há contrato entre
serviços a proteger, e um teste de contrato entre serviços seria trabalho sem objeto.

**O contrato que existe e está desprotegido é outro: o contrato HTTP público.** É a única fronteira
do sistema, é consumida por clientes que o repositório não controla, e:

- **não existe especificação OpenAPI versionada** — o contrato só existe em tempo de execução,
  gerado pelo springdoc, portanto nenhuma mudança de contrato aparece em *diff*;
- **nenhum teste jamais recebe uma resposta 200** desse endpoint (seção 5.1), logo o formato do
  corpo de resposta — o `PlannerResponseDto` que o cliente consome — **não é verificado por nada**;
- **os 8 mapeadores de `api.mapper`, que produzem esse corpo, têm 0% de cobertura de ramos.**

Uma renomeação de campo, uma mudança de tipo ou um campo que passe a vir nulo atravessaria a suíte
inteira sem uma falha. Esse é o risco de contrato real do projeto, e ele é **interno ao serviço
único** — não entre serviços.

Sobre **ponta a ponta**: o único fluxo do produto tem exatamente um passo HTTP e nenhuma dependência
externa, então um teste de integração que suba o contexto e faça um `POST` real com carga válida já
é, na prática, o teste ponta a ponta deste sistema. A ausência não exige ferramental novo
(Testcontainers, Selenium): exige uma requisição bem-sucedida.

Sobre **carga**: `scalesToManySubjects` cobre parcialmente o eixo de escala algorítmica (200
disciplinas). O que não existe é qualquer teste de concorrência — o `optimizerTaskExecutor` tem
pool de 8 e fila limitada a 50, com política de rejeição rápida, e **nada exercita o comportamento
quando a fila enche**. Fica registrado para a etapa de performance/escalonamento, não para esta.

---

## 5. Caminhos críticos de negócio sem nenhum teste

Ordenados pelo impacto sobre o aluno — o usuário final que recebe o plano de estudos.

### 5.1 O fluxo de sucesso inteiro. Nenhum teste chega nele.

**Este é o achado central deste diagnóstico.**

`OptimizerControllerTest` tem dois testes. Ambos enviam cargas **inválidas** e esperam `400`:
`populationSize` acima do máximo, e `name` do exame em branco. Ambos são rejeitados pela validação
do Jakarta Bean Validation **antes** de o corpo do método do controller executar.

A consequência está medida no JaCoCo:

```
OptimizerController.generateFullStudyPlan          0 / 35 instruções   nunca executado
DynamicStudyPlannerService.generateFullStudyPlan   0 / 41 instruções   nunca executado
```

Portanto **nada nesta suíte verifica**:

| Etapa do fluxo | Classe | Cobertura |
|---|---|---|
| Tradução do pedido para o domínio | `ExamMapper`, `StudentProfileMapper` | 11,1% / 4,3% — 0% de ramos |
| Orquestração das duas fases | `DynamicStudyPlannerService.generateFullStudyPlan` | **0%** |
| Composição da cadeia de estratégias de agendamento | mesma função | **0%** |
| Execução assíncrona no `optimizerTaskExecutor` | `AsyncConfig` + `@Async` | não exercitada |
| Tradução do resultado para a resposta | `FullPlannerResultMapper` e os 6 mapeadores restantes | 12,7% — **0% de ramos** |
| Serialização do corpo de resposta | `PlannerResponseDto` e DTOs de saída | 34,5%, vários em 0% |

O produto poderia devolver `200` com um corpo vazio, com campos nulos, ou com o cronograma de outro
aluno, e os 73 testes continuariam verdes.

### 5.2 O comportamento sob timeout — o caso que o aluno mais sente

O controller aplica `.orTimeout(30, TimeUnit.SECONDS)` e o `README.md` promete `408 Request Timeout`.
`GlobalExceptionHandler.handleTimeoutException` tem **0 / 31 instruções**. O caminho que o aluno
percorre quando o algoritmo demora demais — justamente o cenário de carga alta, quando mais gente
está usando — nunca foi executado por um teste.

### 5.3 O resto do tratamento de erros

Oito das nove funções do `GlobalExceptionHandler` nunca executam. Isso inclui:

- `handleDomainException` — o `422` das restrições impossíveis (por exemplo, orçamento abaixo do
  piso de dias mínimos, situação que `GaEdgeCasesTest` prova ser alcançável);
- `handleRateLimitExceededException` — o `429`. O `RateLimitingFilterTest` verifica que o filtro
  **chama** o resolvedor de exceções, com um *mock*; **ninguém verifica que o cliente recebe um 429**
  com corpo `application/problem+json`;
- `handleAllUncaughtException` — a rede de segurança do `500`.

Em todos os casos, o formato RFC 7807 que o `README.md` documenta como contrato de erro não é
verificado em nenhuma resposta real, exceto no `400` de validação.

### 5.4 A regra de negócio do rate limiting em produção

O `RateLimitingFilterTest` é um bom teste unitário, mas usa `request.setRemoteAddr(...)`. O caminho
realmente usado atrás de um balanceador de carga é o cabeçalho `X-Forwarded-For` — o repositório
configura `server.forward-headers-strategy=framework` exatamente para isso. `getClientIP()` tem um
ramo dedicado a esse cabeçalho e ele **não é exercitado** (o filtro tem 3 de 8 ramos descobertos).
Registrado aqui como lacuna de teste; a questão de confiança nesse cabeçalho é R15 do inventário e
pertence à etapa de segurança.

### 5.5 O que parece lacuna crítica mas não é

Honestidade sobre a lista acima exige separar o que está descoberto **e é alcançável** do que está
descoberto **porque nada o alcança**:

| Código sem cobertura | Por que **não** é caminho crítico |
|---|---|
| `FatigueAndEnergyModel` (0%, 227 instruções) | Só é chamado por `FatigueAndSustainabilityPenalty`, que o `FitnessEvaluator` documenta como retornando 1,0 no caminho macro — ela só teria dados sobre um `TacticalStudyPlan`, que nada produz |
| `DropoutRiskPredictor.calculateRiskScore` (0/85) | Mesma situação, via `DropoutRiskPenalty` |
| `MandatoryReviewConstraint.isValid` (11/71) | A guarda de entrada retorna `true` de imediato salvo para `TacticalStudyPlan`. **Inerte em produção, não só em teste** |
| `HybridRetentionEngine.isReviewMandatory` (0/17) | Só alcançável pela constraint acima |
| `ga.tactical.*`, `CriticalFirstStrategy`, `RandomizedInterleavedStrategy` (0%) | Sem consumidor — pendências G5 e G11, ABERTAS |

São, somados, mais de 400 instruções de cobertura zero que **não representam risco para o aluno
hoje**. Investir teste aí antes de cobrir o fluxo de sucesso seria subir o número agregado sem
reduzir risco. A decisão sobre esse código é de estrutura (o que fazer com a camada tática), não de
testes.

---

## 6. Como os testes rodam hoje

**Só localmente, por invocação manual. Nunca rodaram em CI, em nenhum momento da história do
repositório.**

Verificado em duas frentes independentes:

1. **No repositório:** não existe diretório `.github/`, nenhum arquivo `.yml` ou `.yaml` em lugar
   nenhum, nenhum `Jenkinsfile`, nenhuma configuração de CI de qualquer fornecedor.
2. **Na API do GitHub**, consultando `gustavo-rm/exam-optimizer-application`:

   | Consulta | Resultado |
   |---|---|
   | Workflows registrados | `total_count: 0` |
   | Execuções de workflow (histórico completo) | `total_count: 0` |

**Os testes não são *gate* — não existe *gate*.** Não há verificação automática a bloquear, porque
não há verificação automática. A branch `main` tem proteção que exige pull request (a tentativa de
push direto é recusada com *"Changes must be made through a pull request"*), mas essa proteção exige
**revisão humana**, não aprovação de testes: sem workflows, não existe *status check* que o GitHub
pudesse exigir.

Consequências práticas para as decisões da próxima etapa:

- A suíte roda quando alguém digita `./mvnw test`. Não há registro de que tenha rodado antes de
  cada um dos merges já feitos.
- A cobertura de 67,1% / 52,9% **não é observada por ninguém de forma contínua** — este documento é
  a primeira vez que o número existe.
- As sete medições de `benchmarks/` que produzem os números publicados em `docs/revisao-ag/` rodam
  por invocação manual de `main()`, com *classpath* montado à mão. Apenas
  `GeneticAlgorithmVsBaselinesTest`, `SpearmanTest` e `CorrelationAggregateTest` entram na suíte
  automática.
- O tempo da suíte é favorável a virar *gate*: **cerca de 12 a 18 segundos** por execução completa
  com dependências já em cache, medido nas 10 execuções desta etapa.

---

## 7. Lacunas priorizadas por risco

Ordenação pedida: **caminho crítico sem teste > teste instável mascarando problema real > cobertura
baixa em código de baixo risco > estilo e organização**.

### Nível 1 — Caminho crítico de negócio sem nenhum teste

| # | Lacuna | Evidência | O que quebra sem ser notado |
|---|---|---|---|
| **T1** | **O fluxo de sucesso não é exercitado por nenhum teste.** Nenhuma requisição chega a receber `200` | `OptimizerController.generateFullStudyPlan` 0/35; `DynamicStudyPlannerService.generateFullStudyPlan` 0/41 | Tudo entre receber o pedido e devolver o plano: orquestração, assincronia, composição das estratégias |
| **T2** | **Os 8 mapeadores têm 0% de cobertura de ramos.** São eles que montam o corpo que o cliente lê | `api.mapper` 12,7% instruções, **0,0% ramos** | Campo renomeado, tipo trocado, nulo inesperado no plano entregue ao aluno |
| **T3** | **O `408` de timeout nunca foi executado** | `handleTimeoutException` 0/31 | O comportamento sob carga alta — exatamente quando mais alunos estão esperando |
| **T4** | **`422`, `429` e `500` nunca foram executados como resposta HTTP** | `handleDomainException` 0/33, `handleRateLimitExceededException` 0/32, `handleAllUncaughtException` 0/31 | O contrato de erro RFC 7807 que o README promete |
| **T5** | **O contrato HTTP público não tem especificação versionada nem teste de formato** | sem arquivo OpenAPI; nenhum teste inspeciona corpo de sucesso | Quebra de compatibilidade com clientes, invisível em *diff* e invisível na suíte |

### Nível 2 — Instabilidade capaz de mascarar problema real

Nenhum teste instável foi observado em 10 execuções. Os itens abaixo são **mecanismos** de
instabilidade, não instabilidade medida — e o T6 é o mais sério dos três porque **já hoje** faz um
teste verde valer menos do que parece.

| # | Lacuna | Evidência | Por que importa |
|---|---|---|---|
| **T6** | **A repetição da suíte não é evidência para a parte determinística.** `BASE_SEED + r` fixa as sementes; `GeneticAlgorithmVsBaselinesTest` é bit a bit idêntico a cada execução | `BenchmarkHarness:53,121` | Qualquer afirmação futura de "rodamos N vezes, está estável" precisa dizer para quais testes isso vale |
| **T7** | **`RandomProvider` é estado estático global mutável, gravado por testes e por benchmarks** | `util/RandomProvider.java`; `@AfterEach` em `GaEdgeCasesTest`; aviso no `BenchmarkHarness:46` | Um teste futuro que fixe semente sem restaurar contamina os demais; paralelismo no Surefire quebra a suíte |
| **T8** | **Asserção de tempo de parede (`< 10 s`) e 8 testes (em 4 arquivos) dependentes de `LocalDate.now()`** | `GaEdgeCasesTest:294-296`; `CognitiveLoadCalculatorTest:26`, `BaselineCalculatorTest:33`, `ImportanceCalculatorTest:26`, `ExamTest` (5×) | Primeiros candidatos a falhar de forma intermitente no dia em que a suíte entrar em CI |

### Nível 3 — Cobertura baixa em código de baixo risco

| # | Lacuna | Evidência | Por que é nível 3 |
|---|---|---|---|
| **T9** | `FatigueAndEnergyModel` e `DropoutRiskPredictor` sem cobertura (0% e 3,4%, 312 instruções somadas) | seção 5.5 | Alcançáveis só por penalidades que retornam 1,0 no caminho macro |
| **T10** | `MandatoryReviewConstraint` e `HybridRetentionEngine.isReviewMandatory` só na guarda de entrada | seção 5.5 | Inertes em produção, não só em teste |
| **T11** | Camada tática (`ga.tactical.*`) e duas estratégias sem cobertura e sem consumidor | G5 e G11, ABERTAS | Decisão de estrutura, não de teste: cobrir código que ninguém chama não reduz risco |
| **T12** | `Population.getWorst` e `getAverageFitness` nunca executados | 0/16 e 0/15 | Métodos de diagnóstico, fora do laço de decisão |

### Nível 4 — Estilo e organização

| # | Lacuna | Evidência |
|---|---|---|
| **T13** | `tinyGaConfigurationStillRuns` não afirma nada sobre o resultado | `GaEdgeCasesTest:338-345` |
| **T14** | `productionPipelineSatisfiesTheAssertion` expressa "não lança" como `isNotNull()` | `PrerequisiteSequencingDiagnosticTest:165` |
| **T15** | As 3 asserções de `HybridHeuristicSchedulerTest` têm folga grande demais (36 min medidos contra limite de 102) | seção 2.2 |
| **T16** | A fiação de produção é reproduzida à mão em 3 arquivos, sem teste que verifique a equivalência com o que o Spring monta | seção 2.4 |
| **T17** | Três classes de teste verificam `getTotalDays()`; 17 ocorrências de `new Subject("Math", …)` em 9 arquivos, sem fábrica de *fixtures* compartilhada | seção 2.3 |
| **T18** | Nenhuma ferramenta de cobertura configurada: o número desta etapa não é observável de forma contínua | `pom.xml` |

---

## Resumo em uma tela

- **Cobertura medida (não estimada): 67,1% de instruções, 52,9% de ramos, 68,4% de linhas**, sobre
  `src/main`. Nenhum relatório está configurado no repositório — o número foi obtido por invocação
  externa do JaCoCo e não é observado por ninguém hoje.
- **73 testes, 10 execuções, 0 resultados inconsistentes** — 5 na ordem padrão e 5 com ordem
  embaralhada, comparadas caso a caso. **Mas** boa parte da suíte é determinística por construção
  (sementes fixas), então o verde vale para os 7 testes que de fato sorteiam, não para toda a suíte.
- **O achado central: o fluxo de sucesso do produto nunca é executado por nenhum teste.** Os dois
  testes do controller enviam cargas inválidas e param na validação; o corpo do endpoint e o
  orquestrador têm **0 instruções cobertas**. O corpo de resposta que o aluno recebe não é
  verificado por nada, e os mapeadores que o montam têm **0% de cobertura de ramos**.
- **Oito das nove funções de tratamento de erro nunca executam**, incluindo o `408` de timeout, o
  `422` de regra de negócio e o `429` de rate limiting.
- **Os testes nunca rodaram em CI** — 0 workflows e 0 execuções em toda a história do repositório,
  confirmado pela API do GitHub. Não são *gate* porque não existe *gate*. A suíte leva 12–18 s.
- **Não há teste de contrato entre serviços a fazer** — não há serviços. O contrato desprotegido é o
  HTTP público, sem especificação versionada e sem nenhuma resposta de sucesso verificada.
- **Mais de 400 instruções de cobertura zero não são risco**: são código que nenhum caminho de
  produção alcança (camada tática, modelos de fadiga e evasão). Cobri-las subiria o número agregado
  sem reduzir risco algum.
