# Diagnóstico do Algoritmo Genético — SINAPSE / Exam Optimizer

**Objetivo deste documento:** construir um mapa fiel do estado atual da camada de otimização
(Algoritmo Genético) do repositório, sem avaliar qualidade e sem propor alterações.
Nenhum código foi modificado nesta etapa.

**Commit de referência:** `814f9d8` (`Merge pull request #7 from gustavo-rm/feature/student-state-its-optimization-...`)
**Branch de trabalho:** `claude/sinapse-ag-diagnostico-8t1ez7`
**Data do levantamento:** 2026-08-30
**Escopo varrido:** todo `src/main`, `src/test`, `pom.xml`, `src/main/resources/application.properties` e os 7 documentos `.md` da raiz.

---

## 0. Sumário do que foi encontrado (leitura rápida)

| Item | Situação encontrada |
|---|---|
| Motor do AG | Existe e está ativo: `ga/GeneticAlgorithm.java`, opera sobre `StudyPlan` (macro) |
| Encoding do cromossomo | `Map<Subject, Integer>` — dias por matéria. **Sem ordem/sequência temporal** |
| Fitness ativa no caminho macro | 1 objetivo (`ScoreGainObjective`), 1 constraint efetiva (`MinimumDaysConstraint`), 1 penalidade efetiva (`FatigueAndSustainabilityPenalty`) |
| Componentes de fitness inertes no caminho macro | `MandatoryReviewConstraint` e `DropoutRiskPenalty` (guardas `instanceof TacticalStudyPlan` nunca satisfeitas) |
| Ebbinghaus na fitness | Não entra na fitness do AG macro. Existe em `HybridRetentionEngine`, consumido apenas por código inerte/não-cabeado, e em `ReviewFocusedStrategy` (pós-AG) |
| Carga cognitiva (Sweller) na fitness | Ausente na fitness. Existe em `CognitiveLoadCalculator` + `CognitiveLoadBalancingStrategy`, ambos pós-AG |
| Pré-requisitos (Ausubel) | **Nenhuma implementação em todo o repositório.** Nenhum campo, classe, termo ou teste de pré-requisito/sequenciamento |
| Segunda camada de AG (tático) | Interfaces e 2 operadores escritos, **nenhum instanciado ou cabeado** — andaime morto |
| Testes/benchmarks do AG | 2 arquivos de teste tocam o AG (`IndividualTest`, `TransferMutationTest`). Benchmark ou comparação com baseline mais simples: **nenhum encontrado** |
| Biblioteca de otimização externa | Nenhuma. AG 100% escrito à mão |

---

## 1. Localização e estrutura dos arquivos do AG

### 1.1 Estrutura de diretórios

Todo o motor evolutivo vive em `src/main/java/com/ia/project/dynamicstudyplanner/ga/`
(1.929 linhas em 35 arquivos):

```
ga/
├── GeneticAlgorithm.java             (152 L)  motor: elitismo + laço de reprodução + hipermutação
├── GeneticAlgorithmBuilder.java      (161 L)  Builder; carrega os defaults de hiperparâmetros
├── Individual.java                   ( 72 L)  wrapper cromossomo + fitness em cache
├── Population.java                   (117 L)  coleção de indivíduos; avaliação em parallelStream
├── EvolutionContext.java             ( 19 L)  record com todo o contexto da evolução
├── config/
│   ├── GeneticAlgorithmFactory.java  ( 17 L)  interface
│   └── DefaultGeneticAlgorithmFactory.java (45 L)  ONDE OS HIPERPARÂMETROS REAIS SÃO FIXADOS
├── factory/
│   └── StudyPlanFactory.java         ( 69 L)  geração de cromossomo aleatório válido
├── generator/
│   ├── PopulationGenerator.java      ( 22 L)  interface
│   └── DefaultPopulationGenerator.java (39 L) inicialização da população
├── fitness/
│   ├── FitnessEvaluator.java         ( 67 L)  PIPELINE DA FITNESS (soma ponderada + penalidades)
│   ├── objective/
│   │   ├── FitnessObjective.java     ( 12 L)
│   │   └── ScoreGainObjective.java   ( 45 L)  único objetivo existente
│   ├── constraint/
│   │   ├── ConstraintValidator.java  ( 11 L)
│   │   ├── MinimumDaysConstraint.java( 17 L)
│   │   └── MandatoryReviewConstraint.java (52 L)
│   └── penalty/
│       ├── FitnessPenalty.java       ( 16 L)
│       ├── FatigueAndSustainabilityPenalty.java (61 L)
│       └── DropoutRiskPenalty.java   ( 53 L)
├── strategy/
│   ├── selection/{SelectionStrategy, TournamentSelection}.java
│   ├── crossover/{CrossoverStrategy, HybridCrossover, WeightedAverageCrossover, RepairingCrossover}.java
│   └── mutation/{MutationStrategy, AbstractMutationStrategy, CreepMutation, TransferMutation}.java
└── tactical/                          ANDAIME NÃO CABEADO (ver 1.9)
    ├── repair/{ChromosomeRepairer, SpacedRepetitionRepairer}.java
    └── strategy/crossover/{TacticalCrossoverStrategy, DayBoundaryCrossover}.java
        strategy/mutation/{TacticalMutationStrategy, MethodologyMutation}.java
```

Orquestração fora do pacote `ga/`:

- `service/StudyOptimizerService.java` — fachada; monta o `EvolutionContext`, gera a população
  inicial, roda o laço de gerações e empacota o resultado.
- `service/DynamicStudyPlannerService.java` — chama o AG e depois a camada tática (não-AG).
- `api/controller/OptimizerController.java` — entrada HTTP; recebe os 3 hiperparâmetros
  variáveis via corpo da requisição.

### 1.2 Encoding do cromossomo

`domain/StudyPlan.java:16-30`

```java
public class StudyPlan {
    private final Map<Subject, Integer> daysPerSubject;
    public StudyPlan(Map<Subject, Integer> daysPerSubject) {
        this.daysPerSubject = daysPerSubject == null ? Map.of() : Collections.unmodifiableMap(daysPerSubject);
    }
```

- **Genótipo:** um mapa `Subject -> Integer` (número de dias de estudo alocados àquela matéria).
- **Loci:** as matérias (`exam.getAllSubjects()`); **não há índice posicional nem ordem**.
  Como `Map` (implementação `HashMap` nos operadores), a iteração não tem ordem estável garantida.
- **Invariante mantida pelos operadores:** `Σ daysPerSubject.values() == totalDays` (parâmetro da
  requisição) e `daysPerSubject.get(s) >= minimumDaysPerSubject.get(s)`.
- `Individual` (`ga/Individual.java:21-37`) embrulha o `StudyPlan` e um `double fitness`
  inicializado em `-1.0` ("ainda não calculado"), com `compareTo` em ordem decrescente de fitness.

> **Consequência estrutural registrada, sem juízo de valor:** o cromossomo é um vetor de
> quantidades agregadas. Não codifica *quando* nem *em que ordem* cada matéria é estudada.
> Qualquer noção de sequenciamento (pré-requisitos) é, por construção, inexprimível neste encoding.

Existe um segundo encoding, `domain/tactical/TacticalStudyPlan.java:14-21`, que estende
`StudyPlan` e mapeia `TimeSlot -> TacticalStudyBlock` (matéria + metodologia + duração). Ele
**não é produzido por nenhum AG** no código atual (ver 1.9).

### 1.3 Inicialização da população

`ga/generator/DefaultPopulationGenerator.java:27-38` → `ga/factory/StudyPlanFactory.java:40-69`

```java
for (int i = 0; i < populationSize; i++) {
    population.addIndividual(new Individual(
        planFactory.createRandomPlan(allSubjects, totalDays, context.minimumDaysPerSubject())));
}
population.calculateFitness(context);
```

`createRandomPlan` faz três passos:
1. **Checagem de viabilidade:** se `Σ minimumDaysPerSubject > totalDays`, lança
   `IllegalArgumentException`.
2. **Piso:** cada matéria recebe `minimumDaysPerSubject.getOrDefault(subject, 1)`.
3. **Distribuição:** os `remainingDays = totalDays - totalMinimumDays` restantes são
   distribuídos um a um, cada um para uma matéria sorteada uniformemente
   (`random.nextInt(subjects.size())`).

Não há semeadura heurística, nem *seeding* com soluções conhecidas, nem controle explícito
de diversidade inicial.

### 1.4 Seleção

`ga/strategy/selection/TournamentSelection.java`

- **Torneio** com `tournamentSize = 3` fixado no construtor sem argumentos
  (linha 24-26), que é o construtor que o Spring usa (`@Component`).
- Amostragem **com reposição** (`population.getIndividual(random.nextInt(size))` em laço, sem
  exclusão de repetidos), vencedor por maior `getFitness()`.
- Um construtor alternativo `TournamentSelection(int)` existe (linha 43) mas nunca é chamado
  em produção.

### 1.5 Crossover

Três estratégias, compostas:

**`HybridCrossover`** (bean efetivamente injetado, via `@Qualifier("hybridCrossover")`):
```java
// HybridCrossover.java:24-37
if (random.nextDouble() > crossoverRate) {                 // não cruza
    return parent1.getFitness() > parent2.getFitness() ? new Individual(parent1.getPlan())
                                                       : new Individual(parent2.getPlan());
}
if (random.nextDouble() < exploitationChance) {             // exploitationChance = 0.75 (linha 21)
    return exploitationStrategy.crossover(parent1, parent2, 1.0, context);  // WeightedAverage
} else {
    return explorationStrategy.crossover(parent1, parent2, 1.0, context);   // Repairing
}
```

**`WeightedAverageCrossover`** (exploração local / refinamento) — média dos genes ponderada
pela **fitness bruta** dos pais:
```java
// WeightedAverageCrossover.java:50-61
double weight1 = parent1.getFitness();
double weight2 = parent2.getFitness();
double totalFitness = weight1 + weight2;
if (totalFitness == 0) totalFitness = 1;
childValue = (p1Value * weight1 + p2Value * weight2) / totalFitness;
childGenes.put(subject, (int) Math.round(childValue));
```

**`RepairingCrossover`** (exploração global) — corte de ponto único sobre
`new ArrayList<>(parent1Genes.keySet())`, com ponto de corte
`random.nextInt(subjects.size() - 1) + 1`.

Ambos terminam com o mesmo método `repairChildGenes(...)` (duplicado literalmente nas duas
classes, `WeightedAverageCrossover.java:72-92` e `RepairingCrossover.java:71-92`): adiciona ou
remove dias em matérias sorteadas até que a soma volte a `parent1.getPlan().getTotalDays()`,
respeitando `minimumDaysPerSubject` na remoção e retirando da lista de candidatas as matérias
que já estão no piso.

### 1.6 Mutação

`ga/strategy/mutation/AbstractMutationStrategy.java:43-71` define o fluxo comum:

```java
if (Math.random() > mutationRate) return individual;          // linha 49
Map<Subject, Integer> mutatedGenes = new HashMap<>(individual.getPlan().getDaysPerSubject());
List<Subject> subjects = new ArrayList<>(mutatedGenes.keySet());
if (subjects.size() < 2) return individual;
boolean mutationSuccessful = performMutation(mutatedGenes, subjects, context);
if (!mutationSuccessful) return individual;
return new Individual(new StudyPlan(mutatedGenes));
```

Duas implementações:

- **`CreepMutation`** (`@Component("creepMutation")` — **é a usada em produção**): sorteia
  `creepValue ∈ [-maxCreepDistance, +maxCreepDistance]` (`maxCreepDistance = 3` por padrão,
  linha 35), aplica `+creepValue` a uma matéria e `-creepValue` a outra. Se `creepValue == 0`,
  força `1`. Aborta (retorna `false`) se qualquer das duas violar o mínimo.
- **`TransferMutation`** (`@Component("transferMutation")` — **bean existe mas nunca é
  injetado**): transfere exatamente 1 dia de uma matéria para outra.

Ambas preservam a soma total de dias por construção.

### 1.7 Elitismo

`ga/GeneticAlgorithm.java:70-86`

```java
Population newPopulation = new Population(currentPopulation.getSize());
if (elitism) {
    newPopulation.addIndividual(currentPopulation.getFittest());   // 1 elite, sem cópia
}
int startIndex = elitism ? 1 : 0;
for (int i = startIndex; i < currentPopulation.getSize(); i++) {
    newPopulation.addIndividual(createOffspring(currentPopulation, context, currentMutationRate));
}
newPopulation.calculateFitness(context);
```

- Tamanho da elite: **1**, hardcoded (não parametrizável).
- O elite é inserido **por referência** e sua fitness é **recalculada** junto com o resto em
  `newPopulation.calculateFitness(context)`.

### 1.8 Critério de parada

**Não existe critério de convergência.** O laço é de contagem fixa:

`service/StudyOptimizerService.java:145-166`
```java
for (int i = 0; i < numGenerations; i++) {
    population = ga.evolvePopulation(population, context);
    // apenas logging a cada 5 gerações, se TRACE estiver habilitado
}
```

`numGenerations` vem da requisição HTTP. Não há parada antecipada por estagnação, por limiar de
fitness, por diversidade mínima ou por orçamento de tempo dentro do AG.

O único limite temporal é externo ao AG:
`OptimizerController.java:85` → `.orTimeout(30, TimeUnit.SECONDS)`.

Existe um **mecanismo adaptativo de estagnação que não interrompe** — apenas eleva a taxa de
mutação (`ga/GeneticAlgorithm.java:114-152`):

```java
private void trackStagnation(double currentBestFitness) {
    if (currentBestFitness > bestFitnessSoFar) { ...reset... }
    else { generationsWithoutImprovement++; }
    if (generationsWithoutImprovement >= stagnationPatience && !inHypermutationMode) {
        inHypermutationMode = true;
        hypermutationCounter = 5;                 // duração hardcoded: 5 gerações
        generationsWithoutImprovement = 0;
    }
}
```

Observação factual: `trackStagnation` é chamada com a fitness do **melhor da população atual**,
*antes* de evoluir; e `GeneticAlgorithm` guarda estado mutável de estagnação
(`generationsWithoutImprovement`, `bestFitnessSoFar`, `inHypermutationMode`) — a instância é
criada nova a cada chamada de `gaFactory.create()` dentro de `optimize()`.

### 1.9 Segunda camada de AG (tático) — presente porém não cabeada

Arquivos existentes em `ga/tactical/`:

| Arquivo | Anotação Spring | Instanciado / injetado em algum lugar? |
|---|---|---|
| `ChromosomeRepairer` (interface) | — | — |
| `SpacedRepetitionRepairer` | `@Component` | **Não.** Bean criado, nunca injetado |
| `TacticalCrossoverStrategy` (interface) | — | — |
| `DayBoundaryCrossover` | nenhuma | **Não.** Nunca instanciado |
| `TacticalMutationStrategy` (interface) | — | — |
| `MethodologyMutation` | nenhuma | **Não.** Nunca instanciado |

Verificação (grep por usos fora do próprio pacote): nenhuma ocorrência em `src/main` ou
`src/test`. Não existe um motor de AG que consuma `TacticalCrossoverStrategy` /
`TacticalMutationStrategy` — `GeneticAlgorithm` só conhece `CrossoverStrategy` e
`MutationStrategy` (assinaturas sobre `Individual`). A camada tática efetivamente executada é
determinística e não-evolutiva: `StudyScheduleGenerator` + as `AllocationStrategy`
(`service/scheduler/strategy/`).

`service/scheduler/tactical/HybridHeuristicScheduler.java` (`@Service`, produz um
`TacticalStudyPlan`) também **não é injetado em nenhum lugar** — só é exercitado por seu teste.

---

## 2. Transcrição literal da função de fitness

### 2.1 O pipeline

`ga/fitness/FitnessEvaluator.java:39-66` — transcrição literal:

```java
public double evaluate(StudyPlan plan, EvolutionContext context) {
    // 1. Calculate weighted sum of rewards
    double totalReward = 0.0;
    for (FitnessObjective objective : objectives) {
        totalReward += objective.calculateReward(plan, context) * objective.getWeight();
    }

    // 2. Apply hard constraints (if violated, drastically reduce fitness)
    boolean meetsAllConstraints = true;
    for (ConstraintValidator constraint : constraints) {
        if (!constraint.isValid(plan, context)) {
            meetsAllConstraints = false;
            break;
        }
    }

    if (!meetsAllConstraints) {
        totalReward *= 0.5; // standard constraint violation penalty
    }

    // 3. Apply soft penalties (multiplicative)
    double finalFitness = totalReward;
    for (FitnessPenalty penalty : penalties) {
        finalFitness *= penalty.calculatePenaltyFactor(plan, context);
    }

    return finalFitness;
}
```

As três listas são injetadas pelo Spring com **todos** os beans de cada tipo:
- `objectives = [ScoreGainObjective]`
- `constraints = [MinimumDaysConstraint, MandatoryReviewConstraint]`
- `penalties = [DropoutRiskPenalty, FatigueAndSustainabilityPenalty]`

### 2.2 Termo 1 — `ScoreGainObjective` (único objetivo)

`ga/fitness/objective/ScoreGainObjective.java:20-44`, literal:

```java
@Override
public double calculateReward(StudyPlan plan, EvolutionContext context) {
    double score = 0.0;
    Map<Subject, Double> importanceScores = context.importanceScores();

    for (Map.Entry<Subject, Integer> entry : plan.getDaysPerSubject().entrySet()) {
        Subject subject = entry.getKey();
        int days = entry.getValue();

        double importance = importanceScores.getOrDefault(subject, 0.0);
        if (importance == 0.0) {
            log.warn("The subject '{}' does not have an importance score.", subject.name());
        }

        // A logarithmic function models the diminishing returns of studying.
        double knowledge = Math.log(1.0 + days);
        score += knowledge * importance;
    }
    return score;
}

@Override
public double getWeight() {
    return 1.0; // Base weight
}
```

Peso: **1.0** (constante, retornada pelo método; não há configuração externa).

`importance_s` é pré-calculada fora do AG, em
`service/calculation/ImportanceCalculator.java:34-42`:

```java
double baseImportance = exam.calculateBaseImportance(subject);
double finalImportance = profile.applyKnowledgeGapFactor(subject, baseImportance);
```

com (`domain/exam/Exam.java:93-110`):
- matéria de conhecimentos gerais: `baseImportance = questionCount × (generalKnowledgeTotalScore / totalGKQuestions)`
- matéria de conhecimentos específicos: `baseImportance = questionCount × axisWeight` (peso do eixo temático)
- matéria órfã (não achada em nenhum eixo): `0.0` + `log.warn`

e (`domain/StudentProfile.java:82-84`): `finalImportance = baseImportance × knowledgeGaps.getOrDefault(subject, 1.0)`.

### 2.3 Termo 2 — `MinimumDaysConstraint` (constraint dura, ativa)

`ga/fitness/constraint/MinimumDaysConstraint.java:13-16`:
```java
@Override
public boolean isValid(StudyPlan plan, EvolutionContext context) {
    return plan.meetsMinimumConstraints(context.minimumDaysPerSubject());
}
```
`domain/StudyPlan.java:60-69`: itera as entradas **do próprio plano** e retorna `false` na
primeira matéria com `allocatedDays < minimumDaysPerSubject.getOrDefault(key, 1)`.

Efeito na fitness: multiplicador **0.5** (constante literal em `FitnessEvaluator.java:56`).

Origem dos mínimos: `service/calculation/BaselineCalculator.java`, com constantes
`MAX_MINIMUM_DAYS = 15` e `MIN_REQUIRED_DAYS = 1` (linhas 27-28) e a fórmula
`MIN + (normalizedScore × (MAX - MIN))` (linha 86-88).

### 2.4 Termo 3 — `MandatoryReviewConstraint` (constraint dura, **inerte no caminho macro**)

`ga/fitness/constraint/MandatoryReviewConstraint.java:28-51`:
```java
public boolean isValid(StudyPlan plan, EvolutionContext context) {
    if (!(plan instanceof TacticalStudyPlan tacticalPlan) || context.retentionProfile() == null) {
        return true; // Not applicable for macro-plans without retention tracking
    }
    ...
    for (Subject subject : context.importanceScores().keySet()) {
        boolean mandatory = retentionAlgorithm.isReviewMandatory(subject,
                context.retentionProfile().getState(subject), context.planStartDate());
        if (mandatory && !reviewedSubjects.contains(subject)) return false;
    }
    return true;
}
```
No caminho macro, `plan` é sempre um `StudyPlan` puro (produzido por `StudyPlanFactory`,
`WeightedAverageCrossover`, `RepairingCrossover` e `AbstractMutationStrategy`, todos com
`new StudyPlan(...)`). Logo o `instanceof` falha e o método **sempre retorna `true`**.

### 2.5 Termo 4 — `DropoutRiskPenalty` (penalidade, **inerte no caminho macro**)

`ga/fitness/penalty/DropoutRiskPenalty.java:23-52`, literal:
```java
if (!(plan instanceof TacticalStudyPlan tacticalPlan) || context.engagementProfile() == null) {
    return 1.0; // Not applicable for macro-plans
}
double riskScore = riskPredictor.calculateRiskScore(tacticalPlan, context.engagementProfile(), context.studentState());
if (riskScore < 0.4) return 1.0;
double totalCognitiveLoad = tacticalPlan.calculateTotalCognitiveLoad();
boolean isRecoverySchedule = totalCognitiveLoad < 20.0;
if (isRecoverySchedule) return 1.0;
double penalty = riskScore * 0.8;         // Max 80% reduction
return Math.max(0.1, 1.0 - penalty);
```
Constantes: limiar de risco `0.4`, limiar de "cronograma de recuperação" `20.0`, fator `0.8`,
piso `0.1`. Mesmo motivo do 2.4: no macro sempre retorna `1.0`.

### 2.6 Termo 5 — `FatigueAndSustainabilityPenalty` (penalidade, **a única ativa no macro**)

`ga/fitness/penalty/FatigueAndSustainabilityPenalty.java:23-60`, literal:
```java
StudentState state = context.studentState();
if (state == null) return 1.0;

if (plan instanceof TacticalStudyPlan tacticalPlan) {
    return fatigueModel.calculateBurnoutRisk(tacticalPlan, state);   // ramo nunca alcançado no macro
}

// --- Fallback for Macro (Days-based) Plans ---
int totalDays = plan.getTotalDays();

double stressFactor     = 1.0 - ((state.stressLevel()     - 1.0) / 8.0); // 1.0 -> 1.0, 5.0 -> 0.5
double fatigueFactor    = 1.0 - ((state.fatigueLevel()    - 1.0) / 8.0); // 1.0 -> 1.0, 5.0 -> 0.5
double motivationFactor = 0.5 + ((state.motivationLevel() - 1.0) / 8.0); // 1.0 -> 0.5, 5.0 -> 1.0

double sustainabilityFactor = stressFactor * fatigueFactor * motivationFactor;

if (totalDays > 10) {
    double penalty = 1.0 - sustainabilityFactor;
    penalty = Math.max(0.0, Math.min(0.5, penalty));   // Cap penalty between 0.0 and 0.5
    return 1.0 - penalty;                              // Multiplier: 0.5 to 1.0
}
return 1.0;
```
Constantes: divisor `8.0` (três vezes), offset `0.5` na motivação, limiar `totalDays > 10`,
teto de penalidade `0.5`.

### 2.7 Fórmula completa, como está no código

**Forma genérica do pipeline:**

```
fitness(plan) = [ Σ_{o ∈ objectives} reward_o(plan) · w_o ]
              · (violouAlgumaConstraint ? 0.5 : 1.0)
              · Π_{p ∈ penalties} penaltyFactor_p(plan)
```

**Instanciada com os beans existentes:**

```
fitness(plan) = [ Σ_{s ∈ plan.daysPerSubject} ln(1 + dias_s) · importancia_s ] · 1.0
              · (∃s: dias_s < min_s  ?  0.5 : 1.0)                    [MinimumDaysConstraint]
              · (planoTatico? ... : 1.0)                              [MandatoryReviewConstraint → sempre 1.0 no macro]
              · (planoTatico? ... : 1.0)                              [DropoutRiskPenalty → sempre 1.0 no macro]
              · F_fadiga(plan, state)                                 [FatigueAndSustainabilityPenalty]

onde  importancia_s = baseImportance_s(edital) × knowledgeGapFactor_s(aluno)

      F_fadiga = 1.0                                                  se state == null ou totalDays ≤ 10
               = 1.0 − clamp( 1 − S , 0.0 , 0.5 )                     caso contrário
      S        = (1 − (stress−1)/8) · (1 − (fadiga−1)/8) · (0.5 + (motivação−1)/8)
```

**Fórmula efetiva executada hoje pelo AG macro** (removendo os ramos comprovadamente inertes):

```
fitness(plan) = ( Σ_s ln(1 + dias_s) · importancia_s ) · c · F_fadiga
                  com c ∈ {0.5, 1.0} e F_fadiga ∈ [0.5, 1.0]
```

Note-se que `totalDays` é constante em toda a população (invariante preservada pelos
operadores) e `state` é o mesmo para todos os indivíduos: portanto **`F_fadiga` é um fator
constante idêntico para todos os indivíduos da mesma execução**, e não diferencia soluções.
Isto é uma constatação estrutural do código, não uma avaliação.

### 2.8 Tabela de todos os pesos e constantes da fitness

| Constante | Valor | Arquivo:linha | Configurável? |
|---|---|---|---|
| Peso de `ScoreGainObjective` | `1.0` | `ScoreGainObjective.java:43` | Não (literal no `getWeight()`) |
| Forma de retorno decrescente | `Math.log(1.0 + days)` | `ScoreGainObjective.java:35` | Não |
| Importância default de matéria ausente | `0.0` | `ScoreGainObjective.java:29` | Não |
| Multiplicador de violação de constraint | `0.5` | `FitnessEvaluator.java:56` | Não |
| Mínimo default por matéria (checagem) | `1` | `StudyPlan.java:63` | Não |
| `MAX_MINIMUM_DAYS` | `15` | `BaselineCalculator.java:27` | Não |
| `MIN_REQUIRED_DAYS` | `1` | `BaselineCalculator.java:28` | Não |
| Limiar de risco de evasão | `0.4` | `DropoutRiskPenalty.java:31` | Não |
| Limiar "cronograma de recuperação" | `20.0` | `DropoutRiskPenalty.java:41` | Não |
| Fator de penalidade por evasão | `0.8` | `DropoutRiskPenalty.java:49` | Não |
| Piso da penalidade por evasão | `0.1` | `DropoutRiskPenalty.java:50` | Não |
| Divisor de stress/fadiga/motivação | `8.0` | `FatigueAndSustainabilityPenalty.java:43-45` | Não |
| Limiar de dias para ativar penalidade | `> 10` | `FatigueAndSustainabilityPenalty.java:51` | Não |
| Teto da penalidade de sustentabilidade | `0.5` | `FatigueAndSustainabilityPenalty.java:55` | Não |
| `MANDATORY_REVIEW_THRESHOLD` (Ebbinghaus) | `0.85` | `HybridRetentionEngine.java:18` | Não |
| `BURNOUT_THRESHOLD` | `50.0` | `FatigueAndEnergyModel.java:24` | Não |
| `FATIGUE_CARRYOVER_RATE` | `0.2` | `FatigueAndEnergyModel.java:26` | Não |
| `AVERAGE_LOAD_FACTOR` | `3.0` | `CognitiveLoadCalculator.java:19` | Não |

Nenhuma dessas constantes é lida de `application.properties`, de variável de ambiente ou do
payload da API.

---

## 3. Mapeamento termo da fitness ↔ conceito teórico

Legenda da coluna "documentado":
**Explícito** = há comentário/Javadoc no próprio arquivo nomeando o conceito;
**Inferível** = a intenção só se deduz do nome da classe ou de documento externo (README/`.md` da raiz);
**Ausente** = nenhuma menção em lugar algum.

| # | Termo da fitness | Conceito que pretende operacionalizar | Documentado? | Evidência textual |
|---|---|---|---|---|
| 1 | `ScoreGainObjective` — fator `importancia_s` | **Peso de edital** (nº de questões × valor da questão / peso do eixo temático) | **Explícito** (parcial) | `Exam.java:87-92`: *"Calculates the objective base importance of a subject based purely on the exam's scoring rules"*. O conceito "peso de edital" é nomeado no Javadoc de `Exam` (*"as defined in the official announcement ('edital')"*, linha 16) |
| 2 | `ScoreGainObjective` — fator `knowledgeGapFactor` embutido em `importancia_s` | **Aprendizagem significativa (Ausubel)** na leitura do README — priorizar onde há lacuna de conhecimento ("subsunçores"). **Não** é sequenciamento por pré-requisitos | **Inferível apenas** — e por documento externo | Nada no código Java menciona Ausubel. `ImportanceCalculator.java:15-20` fala em *"Weighted Scoring Model"* e *"subjective need to study it"*, sem citar teoria. A ligação a Ausubel existe só em `README.md:22` |
| 3 | `ScoreGainObjective` — forma `ln(1 + dias)` | **Retornos decrescentes do estudo** (curva de aprendizagem genérica). Não é curva de esquecimento | **Explícito** | `ScoreGainObjective.java:34`: *"A logarithmic function models the diminishing returns of studying."* Nenhuma teoria nomeada |
| 4 | `MinimumDaysConstraint` (×0.5) | **Piso de cobertura por matéria**, derivado de "dificuldade percebida" (`BaselineCalculator`). Não corresponde a nenhum dos três fundamentos | **Explícito** para a mecânica, **Ausente** para a teoria | `MinimumDaysConstraint.java:7-9`: *"Validates that the study plan meets the minimum day requirements"*. `BaselineCalculator.java:11-17` fala em *"perceived difficulty"* sem referência teórica |
| 5 | `MandatoryReviewConstraint` (×0.5 se violada) | **Curva de esquecimento de Ebbinghaus / repetição espaçada** | **Explícito**, mas apenas na dependência | O próprio arquivo diz *"fails to include mandatory spaced repetition reviews"* (linha 16) sem citar Ebbinghaus; a citação está em `RetentionAlgorithm.java:15` (*"Uses the Ebbinghaus Forgetting Curve formula"*) e `HybridRetentionEngine.java:12,31`. **Registrar: este termo é inerte no caminho macro (§2.4), logo Ebbinghaus não influencia a fitness executada** |
| 6 | `DropoutRiskPenalty` | **Risco de evasão / engajamento** — quarto conceito, fora dos três fundamentos declarados. Usa `calculateTotalCognitiveLoad()` como proxy de intensidade | **Explícito** para engajamento, **Inferível** para carga cognitiva | `DropoutRiskPenalty.java:10-12`: *"Penalizes schedules that exacerbate the risk of a student abandoning the platform."* Nenhuma menção a Sweller. **Inerte no caminho macro (§2.5)** |
| 7 | `FatigueAndSustainabilityPenalty` (ramo tático) | **Burnout / energia**, via `FatigueAndEnergyModel` (curvas bifásicas por cronotipo) | **Explícito** para fadiga, **Ausente** para Sweller | `FatigueAndEnergyModel.java:15-19`. Não usa `Subject.cognitiveLoad` como orçamento diário; usa carga emocional + energia requerida. **Ramo nunca alcançado no macro** |
| 8 | `FatigueAndSustainabilityPenalty` (ramo macro — o que roda) | **Sustentabilidade psicológica** a partir de `StudentState` (estresse/fadiga/motivação). **Não** é carga cognitiva de Sweller | **Inferível** | Comentários no arquivo (linhas 49-56) descrevem só a mecânica: *"If the plan is very long, a low sustainability factor hits harder."* Nenhuma teoria nomeada |

### 3.1 Onde estão, de fato, os três fundamentos declarados

| Fundamento | Onde o repositório o implementa | Está dentro da fitness do AG? |
|---|---|---|
| **Ebbinghaus / repetição espaçada** | (a) `service/calculation/retention/HybridRetentionEngine.java` — `R = e^(−t/S)` (linha 33) e limiar `0.85`; consumido por `MandatoryReviewConstraint` (inerte) e `SpacedRepetitionRepairer` (não cabeado). (b) `service/scheduler/strategy/ReviewFocusedStrategy.java` — reserva a 1ª hora do dia para a matéria estudada há mais tempo; roda **depois** do AG, em `DynamicStudyPlannerService.java:64-69` | **Não.** Nem via constraint (inerte), nem via objetivo (não existe objetivo de retenção) |
| **Sweller / carga cognitiva** | (a) `service/calculation/CognitiveLoadCalculator.java` — orçamento diário `MAX_DAILY_COGNITIVE_LOAD`. (b) `service/scheduler/strategy/CognitiveLoadBalancingStrategy.java` — intercalação por dois ponteiros + poda `pruneToFitDailyLoadLimit`. Ambos rodam **depois** do AG | **Não.** Nenhum termo da fitness lê `Subject.cognitiveLoad` no caminho macro |
| **Ausubel / pré-requisitos e sequenciamento** | **Nenhuma implementação encontrada em nenhum ponto do repositório.** Busca por `prereq`, `pré-requisito`, `pre-requisito`, `ausubel`, `sequenc`, `subsumer` em `*.java`, `*.md`, `*.properties`: zero ocorrências em código; a única menção é `README.md:22`, que reinterpreta Ausubel como "cruzar peso do edital com lacunas declaradas" — o que corresponde ao termo #2 da tabela acima, não a sequenciamento | **Não.** E é estruturalmente inexprimível: o cromossomo `Map<Subject,Integer>` não tem ordem (§1.2) |

---

## 4. Hiperparâmetros do AG e onde estão definidos

### 4.1 Definidos por requisição HTTP (os únicos ajustáveis em runtime)

`api/dto/GaConfigDto.java` → `OptimizerController.java:81-83` → `StudyOptimizerService.optimize(...)`

| Hiperparâmetro | Faixa validada | Onde |
|---|---|---|
| `totalStudyDays` | `@Min(1) @Max(365)` | `GaConfigDto.java:16-18` |
| `numGenerations` | `@Min(10) @Max(1000)` | `GaConfigDto.java:20-22` |
| `populationSize` | `@Min(10) @Max(500)` | `GaConfigDto.java:24-26` |

Não há valores default no servidor: os três campos são primitivos `int` do record e o cliente
precisa enviá-los (`@NotNull @Valid` sobre o `gaConfig` em `OptimizationRequest.java:24-26`).

### 4.2 Hardcoded em `DefaultGeneticAlgorithmFactory` (o que realmente vale em produção)

`ga/config/DefaultGeneticAlgorithmFactory.java:33-44`, literal:

```java
return new GeneticAlgorithmBuilder()
        .withSelectionStrategy(selectionStrategy)
        .withCrossoverStrategy(crossoverStrategy)      // @Qualifier("hybridCrossover")
        .withMutationStrategy(mutationStrategy)        // @Qualifier("creepMutation")
        .withElitism(true)
        .withCrossoverRate(0.95)
        .withMutationRate(0.05)
        .withStagnationPatience(25)
        .withHypermutationRate(0.20)
        .build();
```

| Hiperparâmetro | Valor efetivo | Default do Builder (sobrescrito) |
|---|---|---|
| Taxa de crossover | **0.95** | `0.9` (`GeneticAlgorithmBuilder.java:22`) |
| Taxa de mutação base | **0.05** | `0.05` (linha 23) |
| Elitismo | **true**, tamanho 1 fixo | `true` (linha 24) |
| Paciência para estagnação | **25 gerações** | `30` (linha 27) |
| Taxa de hipermutação | **0.20** | `0.25` (linha 28) |

### 4.3 Hardcoded dentro dos operadores

| Hiperparâmetro | Valor | Arquivo:linha |
|---|---|---|
| Tamanho do torneio | `3` | `TournamentSelection.java:25` (construtor no-arg, o usado pelo Spring) |
| Chance de exploitation vs exploration | `0.75` | `HybridCrossover.java:21` |
| Distância máxima do creep | `3` | `CreepMutation.java:35` |
| Duração da hipermutação | `5` gerações | `GeneticAlgorithm.java:129` |
| Tamanho da elite | `1` (implícito no `startIndex`) | `GeneticAlgorithm.java:74,78` |

### 4.4 Configuração externa (`application.properties` / env)

`src/main/resources/application.properties` — **nenhuma propriedade do AG**. As únicas
propriedades relacionadas ao custo do AG são de infraestrutura:

```properties
optimizer.thread-pool-size=8          # consumido por AsyncConfig.java:25
api.rate-limit.capacity=5
api.rate-limit.refill-tokens=5
api.rate-limit.refill-duration-minutes=1
```

Não há nenhum `@ConfigurationProperties`, `@Value` ou leitura de variável de ambiente para
taxas, pesos, tamanho de torneio, elitismo ou paciência. Grep confirmado: o único `@Value` do
projeto é `optimizer.thread-pool-size` em `AsyncConfig.java:25`.

### 4.5 Critério de convergência

**Não existe.** Ver §1.8. O `stagnationPatience = 25` **não** encerra a execução — apenas
dispara 5 gerações de hipermutação a 0.20.

---

## 5. Testes automatizados, benchmarks e comparação empírica

### 5.1 Inventário completo de `src/test` (12 arquivos)

| Arquivo | Linhas | Toca o AG? | O que verifica |
|---|---|---|---|
| `ga/IndividualTest.java` | 68 | **Sim** | 2 testes: fitness sem penalidade = `17.917` para `ln(1+5)×10`; e com violação de mínimo = `3.465` (metade de `6.93`). Monta o `FitnessEvaluator` à mão com objetivo + 2 penalidades + 1 constraint |
| `ga/strategy/mutation/TransferMutationTest.java` | 44 | **Sim** | 1 teste de invariante: `TransferMutation` preserva o total de dias. Testa a estratégia **que não é usada em produção** (a de produção é `CreepMutation`) |
| `service/calculation/BaselineCalculatorTest.java` | 51 | Indireto | Mínimos por matéria |
| `service/calculation/ImportanceCalculatorTest.java` | 40 | Indireto | Score de importância |
| `service/calculation/CognitiveLoadCalculatorTest.java` | 37 | Não | Orçamento diário de carga |
| `service/scheduler/tactical/HybridHeuristicSchedulerTest.java` | 92 | Não | Scheduler tático (não cabeado em produção) |
| `api/controller/OptimizerControllerTest.java` | 87 | Não | Camada HTTP |
| `api/controller/RateLimitingFilterTest.java` | 86 | Não | Rate limiting |
| `domain/StudyPlanTest.java` | 50 | Indireto | Invariantes do cromossomo |
| `domain/StudentProfileTest.java` | 69 | Não | Domínio |
| `domain/exam/ExamTest.java` | 89 | Não | Cálculo de importância base |
| `DynamicStudyPlannerApplicationTests.java` | 13 | Não | `contextLoads` |

### 5.2 O que **não** existe

- **Teste de convergência do AG:** nenhum. Não há teste que rode `StudyOptimizerService.optimize()`
  fim-a-fim e verifique que a fitness final > fitness inicial.
- **Teste de qualquer operador de crossover:** nenhum. `HybridCrossover`,
  `WeightedAverageCrossover` e `RepairingCrossover` têm cobertura zero.
- **Teste de `CreepMutation`** (a mutação efetivamente usada): nenhum.
- **Teste de `TournamentSelection`, `Population`, `GeneticAlgorithm`, `StudyPlanFactory`,
  `DefaultPopulationGenerator`, `GeneticAlgorithmBuilder`:** nenhum.
- **Teste do mecanismo de hipermutação/estagnação:** nenhum.
- **Teste dos componentes de fitness `MandatoryReviewConstraint`, `DropoutRiskPenalty`,
  `FatigueAndSustainabilityPenalty` isoladamente:** nenhum (só são exercitados indiretamente por
  `IndividualTest`, e nos dois casos retornando `1.0`).
- **Teste de `HybridRetentionEngine`** (curva de Ebbinghaus / SM-2): nenhum.
- **Teste de `FatigueAndEnergyModel`** (curvas bifásicas): nenhum.
- **Testes property-based / invariantes com N amostras:** nenhum (o único teste de invariante é
  uma execução única em `TransferMutationTest`).
- **Benchmarks (JMH ou equivalente):** **nenhum encontrado.** Nenhuma dependência JMH no
  `pom.xml`, nenhum arquivo com `benchmark` no nome, nenhum diretório `jmh`.
- **Comparação empírica contra alternativa mais simples** (guloso, heurística de proporcional
  ao peso do edital, programação linear, random search, etc.): **nenhum encontrado.** Não há
  baseline implementado, nem harness de comparação, nem dados/resultados versionados.
- **CI:** **nenhum encontrado.** Não existe `.github/` no repositório; nenhum arquivo de
  workflow ou pipeline de qualquer provedor.

### 5.3 Testes que existem apenas como documento, não como código

`TESTING_STRATEGY_ARCHITECTURE.md` descreve em detalhe uma bateria que **não está
implementada**: testes property-based dos operadores, testes de convergência estatística
("rodar o AG 50 vezes e assertar que a melhor fitness final > inicial em 95% das execuções"),
testes de constraint do repairer, benchmarks JMH da `FitnessEvaluator.evaluate()` e execução
determinística por seed fixa via `@SpringBootTest`. Nenhum desses itens tem contrapartida em
`src/test`. O documento `SCHEDULING_ALGORITHMS_EVALUATION.md` compara AG contra Bin Packing e
CSP solvers **em prosa argumentativa**, sem nenhuma medição, dado ou experimento.

---

## 6. Dependências externas relevantes

### 6.1 Bibliotecas de otimização

**Nenhuma.** O AG é integralmente escrito à mão. Não há Jenetics, ECJ, MOEA Framework,
Apache Commons Math (`GeneticAlgorithm`), OptaPlanner/Timefold, Choco, OR-Tools ou
qualquer outra biblioteca de otimização/metaheurística no `pom.xml`.

### 6.2 Geração de números aleatórios

Três fontes de aleatoriedade **independentes e não coordenadas** convivem no caminho do AG:

| Fonte | Onde é usada | Semeável? |
|---|---|---|
| `util/RandomProvider.getInstance()` — singleton estático inicializado com `new SecureRandom()` (`RandomProvider.java:14`), com `setInstance(Random)` "for testing purposes only" (linha 26) | `TournamentSelection:19`, `HybridCrossover:17`, `WeightedAverageCrossover:15`, `RepairingCrossover:16`, `StudyPlanFactory:20`, `DayBoundaryCrossover:19`, `MethodologyMutation:19`, `RandomizedInterleavedStrategy:15` | Sim, via `setInstance` |
| `Math.random()` | `AbstractMutationStrategy.java:49` — **o sorteio que decide se cada indivíduo sofre mutação** | **Não** (usa um `Random` interno do JDK, inacessível) |
| `java.util.concurrent.ThreadLocalRandom.current()` | `AbstractMutationStrategy.java:100` (`randomSubject`) e `CreepMutation.java:72` (`creepValue`) | **Não** (não aceita seed) |

Existe ainda um quarto ponto: `config/RandomConfig.java` declara um `@Bean Random randomProvider()`
retornando `new SecureRandom()` — **esse bean nunca é injetado em lugar nenhum** (grep confirmado).

### 6.3 Stack e versões (`pom.xml`)

| Dependência | Versão | Origem |
|---|---|---|
| Spring Boot (parent) | `3.5.5` | declarada |
| `spring-boot-starter-web` | herdada de 3.5.5 | — |
| `spring-boot-starter-actuator` | herdada | — |
| `spring-boot-starter-security` | herdada | — |
| `spring-boot-starter-validation` | herdada | — |
| `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ) | herdada | escopo `test` |
| `spring-security-test` | herdada | escopo `test` |
| `micrometer-registry-prometheus` | herdada | `runtime` |
| `micrometer-tracing-bridge-brave` | herdada | — |
| `logstash-logback-encoder` | `7.4` | fixada |
| `springdoc-openapi-starter-webmvc-ui` | `2.5.0` | fixada |
| `jakarta.validation-api` | `3.1.1` | fixada |
| `bucket4j-core` | `8.10.1` | fixada |
| `caffeine` | herdada de 3.5.5 | — |
| `lombok` | herdada | `optional` |
| `spring-boot-devtools` | herdada | `runtime`, `optional` |

**Java / build:**
- `<properties><java.version>21</java.version>` (`pom.xml:30`) e `README.md:35,72` dizem Java 21.
- Mas o `maven-compiler-plugin` está configurado com `<source>24</source><target>24</target>`
  e `<compilerArgs>--enable-preview</compilerArgs>` (`pom.xml:118-120`).
- Wrapper Maven presente (`mvnw`, `.mvn/wrapper/maven-wrapper.properties`).

**Concorrência relevante para o AG:** `Population.calculateFitness` usa
`individuals.parallelStream()` (`Population.java:112`), ou seja, avaliação de fitness no
`ForkJoinPool.commonPool()`, aninhada dentro do `optimizerTaskExecutor`
(`AsyncConfig.java:31-53`, pool de 8 threads por `optimizer.thread-pool-size=8`).

---

## 7. Riscos e pontos obscuros identificados

Lista descritiva do que parece frágil, mágico ou não documentado. **Sem propostas de solução.**

### 7.1 Descolamento entre o discurso teórico e a fitness executada

- **R1 — Ausubel/pré-requisitos não existe.** O contexto do projeto afirma que a fitness deve
  incorporar sequenciamento por pré-requisitos. Não há, em nenhum arquivo do repositório,
  qualquer representação de pré-requisito, dependência entre matérias ou ordem de estudo.
  O `README.md:22` reinterpreta Ausubel como "peso do edital × lacunas declaradas", que é uma
  operacionalização diferente da descrita no contexto da tarefa.
- **R2 — O encoding torna R1 inexprimível.** `Map<Subject, Integer>` não tem ordem. Mesmo que
  um termo de pré-requisito fosse adicionado à fitness, ele não teria sobre o que operar no
  cromossomo macro.
- **R3 — Ebbinghaus não influencia a fitness.** `MandatoryReviewConstraint` retorna `true`
  incondicionalmente no caminho macro (guarda `instanceof TacticalStudyPlan`). Nenhum indivíduo
  jamais é penalizado por ausência de revisão espaçada durante a evolução.
- **R4 — Carga cognitiva (Sweller) não influencia a fitness.** `Subject.cognitiveLoad` não é
  lido por nenhum termo ativo. A poda por orçamento diário acontece em
  `CognitiveLoadBalancingStrategy`, **depois** da evolução, quando o AG já escolheu o plano.
- **R5 — Otimizar X e entregar Y.** O AG otimiza o plano macro sob uma fitness que ignora
  revisão e carga; a camada tática seguinte (`ReviewFocusedStrategy` + `CognitiveLoadBalancingStrategy`)
  pode **remover blocos** (`pruneToFitDailyLoadLimit`, `StudyScheduleGenerator.java:96` com
  `reductionFactor`). O plano entregue pode divergir do plano cuja fitness foi maximizada, sem
  que essa divergência realimente a otimização.

### 7.2 Termos de fitness inertes ou degenerados

- **R6 — Dois dos cinco componentes de fitness são no-ops.** `MandatoryReviewConstraint` e
  `DropoutRiskPenalty` retornam sempre o valor neutro. Estão registrados como beans, aparecem
  nas listas injetadas, custam ciclos e dão a impressão de um pipeline multiobjetivo com 5
  termos quando efetivamente há 3.
- **R7 — A única penalidade ativa é constante dentro de uma execução.**
  `FatigueAndSustainabilityPenalty` (ramo macro) depende só de `StudentState` e de `totalDays`.
  `StudentState` é fixo por requisição e `totalDays` é invariante em toda a população. Logo
  `F_fadiga` é o mesmo número para todos os indivíduos e **não altera a ordenação relativa**
  produzida pela seleção por torneio — funciona como um reescalonamento global. O único efeito
  observável é sobre valores absolutos (e sobre os pesos de `WeightedAverageCrossover`).
- **R8 — Fitness efetivamente monoobjetivo.** Removidos os inertes e o fator constante, o que
  guia a busca é `Σ ln(1+dias_s) · importancia_s` sujeito a um multiplicador binário `0.5`.
  A documentação (`FitnessEvaluator.java:16-20`, `NUMERICAL_STABILITY_AUDIT.md`) descreve um
  MOOP por soma ponderada; o comportamento observável é de um único objetivo.
- **R9 — `StudentState` pode ser `null` sem sinalização.** `StudentProfile` tem um construtor
  que passa `state = null` (`StudentProfile.java:33-35`). Nesse caso `F_fadiga = 1.0`
  silenciosamente. Não há validação de faixa (1..5) em `StudentState` — é um record sem
  compact constructor.

### 7.3 Constantes mágicas sem justificativa registrada

- **R10** — O divisor `8.0` em `FatigueAndSustainabilityPenalty` para variáveis numa escala
  declarada de 1 a 5. O comentário explica o *resultado* (`5.0 -> 0.5`), não a *escolha*.
- **R11** — O limiar `totalDays > 10` para ativar a penalidade de sustentabilidade. Nenhuma
  justificativa. Abaixo dele a penalidade é ignorada completamente.
- **R12** — `20.0` como "carga cognitiva de um cronograma de recuperação" em
  `DropoutRiskPenalty.java:41`, contradito pelo comentário imediatamente acima
  (linha 40: *"Assume an average safe load is ~30.0 for a week (heuristic)"*) — os dois números
  não batem e nenhum é explicado.
- **R13** — `MANDATORY_REVIEW_THRESHOLD = 0.85` (probabilidade de retenção abaixo da qual a
  revisão vira obrigatória): número alto e sem fonte citada.
- **R14** — `BURNOUT_THRESHOLD = 50.0` e `FATIGUE_CARRYOVER_RATE = 0.2`, com unidades não
  definidas em lugar nenhum (o que "50" mede?).
- **R15** — `AVERAGE_LOAD_FACTOR = 3.0` em `CognitiveLoadCalculator`, e as cadeias de
  modificadores `1.1 - ((x-1)/4.0)*0.3`, `1.0 - ((x-1)/4.0)*0.2`, etc. (linhas 61-71): seis
  constantes empilhadas multiplicativamente, cada uma com um comentário que só restata o
  cálculo.
- **R16** — Os quatro multiplicadores por metodologia em `StudyMethodology` (ex.:
  `PRACTICE_EXAM(4.0, 2.0, 4.5, 5.0)`): 20 números sem nenhuma referência ou derivação.
- **R17** — `exploitationChance = 0.75` (`HybridCrossover`), `tournamentSize = 3`,
  `maxCreepDistance = 3`, `hypermutationCounter = 5`: nenhum documentado ou calibrado.

### 7.4 Reprodutibilidade e aleatoriedade

- **R18 — Impossível fixar seed do AG.** A decisão *"este indivíduo sofre mutação?"* usa
  `Math.random()` (`AbstractMutationStrategy.java:49`) e a escolha de matérias/valor do creep usa
  `ThreadLocalRandom` (`AbstractMutationStrategy.java:100`, `CreepMutation.java:72`). Nenhum dos
  dois é semeável nem passa pelo `RandomProvider`. A execução determinística prometida em
  `TESTING_STRATEGY_ARCHITECTURE.md` §2 não é alcançável com o código atual.
- **R19 — Três fontes de RNG paralelas + um bean órfão.** `RandomProvider` (singleton estático),
  `Math.random()`, `ThreadLocalRandom` e o `@Bean Random` de `RandomConfig` que ninguém injeta.
  Não há documento explicando qual é a fonte canônica.
- **R20 — `RandomProvider.setInstance` é estático e mutável globalmente.** Um teste que fixe a
  seed altera o estado de toda a JVM, incluindo execuções concorrentes.
- **R21 — `SecureRandom` num laço quente.** `StudyPlanFactory` e os operadores de crossover
  consomem `SecureRandom` a cada gene, dentro de um laço de até 500 indivíduos × 1000 gerações.
  Nenhuma nota registra essa escolha.
- **R22 — Iteração sobre `HashMap` em operadores genéticos.**
  `RepairingCrossover.performSinglePointCrossover` monta
  `new ArrayList<>(parent1Genes.keySet())` e corta essa lista — a ordem depende do hash das
  matérias, não de qualquer semântica. O "ponto de corte" é, na prática, arbitrário e não
  reprodutível entre execuções/JVMs.

### 7.5 Lógica que parece contradizer o próprio comentário

- **R23 — `BaselineCalculator.calculatePerceivedDifficulty` se cancela algebricamente:**
  ```java
  double objectiveWeight = importanceScores.getOrDefault(subject, 0.0) / knowledgeGapFactor;
  return objectiveWeight * knowledgeGapFactor;   // == importanceScores.get(subject)
  ```
  O comentário afirma *"A subject is perceived as difficult if it is important for the exam AND
  the student is weak in it"*, mas o valor retornado é **exatamente** o score de importância
  personalizado — o fator de lacuna entra e sai. Além disso há divisão por
  `knowledgeGapFactor`, que não tem validação de zero.
- **R24 — `WeightedAverageCrossover` com `totalFitness == 0`.** O guard
  `if (totalFitness == 0) totalFitness = 1;` produz `childValue = 0` para **todas** as matérias
  (numerador também é zero), e o reparo subsequente redistribui `totalDays` inteiramente ao
  acaso. Um crossover "de refinamento" degenera em reinicialização aleatória silenciosamente.
- **R25 — Fitness bruta usada como peso de mistura.** `WeightedAverageCrossover` pondera pelos
  valores absolutos de fitness, não por ranking nem por fitness normalizada. Como a fitness é
  uma soma de `ln·importância` em escala arbitrária (depende do valor total do edital), o viés
  em favor do pai mais apto varia com a escala do edital, não com a diferença real de qualidade.
- **R26 — `RepairingCrossover` assume conjuntos de chaves idênticos.**
  `childGenes.put(subject, parent2Genes.get(subject))` sem `getOrDefault`; se os pais tiverem
  chaves diferentes, `null` é inserido e o `Integer` é desempacotado adiante (NPE em
  `repairChildGenes` → `mapToInt`).
- **R27 — `StudyPlanFactory` pode furar `totalDays`.** `totalMinimumDays` é a soma dos valores
  do mapa `minimumDaysPerSubject`, mas o piso aplicado por matéria é
  `getOrDefault(subject, 1)`. Se alguma matéria não estiver no mapa, ela recebe 1 dia que não foi
  contabilizado, e a soma final excede `totalDays`.
- **R28 — `MandatoryReviewConstraint` seria "sempre violada" se algum dia recebesse um plano
  tático.** `StudyOptimizerService.prepareContext` injeta um
  `new RetentionProfile(Map.of())` explicitamente rotulado *"Dummy retention profile"*
  (`StudyOptimizerService.java:127-128`). Com esse perfil, `getState(subject)` retorna `null`
  para toda matéria, e `HybridRetentionEngine.isReviewMandatory` retorna `true` quando o estado
  é `null` (linha 39). Ou seja: no dia em que o `instanceof` passar a valer, o multiplicador
  `0.5` passa a incidir sobre **todos** os indivíduos indistintamente.
- **R29 — Idem para `EngagementProfile`.** `EngagementProfile.baseline()` (aluno perfeito) é
  injetado como *"Dummy engagement profile"* (`StudyOptimizerService.java:130-131`), o que
  força `riskScore` baixo e neutraliza `DropoutRiskPenalty` mesmo no caso tático.
- **R30 — Duplicação literal de `repairChildGenes`.** O mesmo método de 21 linhas existe
  idêntico em `WeightedAverageCrossover.java:72-92` e `RepairingCrossover.java:71-92`. Duas
  cópias que precisam evoluir juntas, sem nada que garanta isso.

### 7.6 Motor, ciclo evolutivo e parada

- **R31 — Sem critério de parada por convergência.** Custo e qualidade são função de um
  `numGenerations` escolhido pelo cliente da API (10..1000). Não há feedback: uma execução que
  convergiu na geração 30 continua até 1000; uma que ainda melhora na 1000 é cortada.
- **R32 — Timeout de 30 s decide o resultado.** `orTimeout(30, TimeUnit.SECONDS)` no controller
  aborta a `CompletableFuture`, mas a thread do `optimizerTaskExecutor` **continua rodando** o
  laço até o fim (nada checa interrupção dentro de `runEvolution`). Com fila de 50 e pool de 8,
  requisições canceladas continuam consumindo CPU.
- **R33 — `trackStagnation` é chamada antes da evolução, sobre a população de entrada.** O
  contador de "gerações sem melhora" mede a população que ainda não foi evoluída naquele passo;
  o acoplamento entre o índice de geração e o estado de estagnação não está documentado.
- **R34 — Hipermutação sem reforço de diversidade.** Ao entrar em modo de hipermutação, apenas
  a taxa sobe (0.05 → 0.20) por 5 gerações; a `CreepMutation` continua sendo um ajuste ±3 dias
  entre duas matérias. Não há reinicialização parcial nem injeção de indivíduos aleatórios.
- **R35 — Elite inserida por referência.** `newPopulation.addIndividual(currentPopulation.getFittest())`
  compartilha o objeto `Individual` entre gerações; sua fitness é reescrita por
  `newPopulation.calculateFitness`. Como os operadores nunca mutam um `Individual` no lugar, hoje
  isso é inofensivo, mas é um invariante não declarado e não testado.
- **R36 — Recalcular a fitness do elite a cada geração.** Com `populationSize` de até 500 e
  1000 gerações, é uma avaliação redundante por geração; o `double fitness` de `Individual` é
  descrito como cache no comentário (linha 27) mas nunca é usado como tal.
- **R37 — `parallelStream` no `commonPool` aninhado no `optimizerTaskExecutor`.**
  `Population.calculateFitness` usa o pool comum do ForkJoin, compartilhado por toda a JVM,
  dentro de um pool `@Async` já dimensionado. Sob concorrência, o paralelismo real e a
  contenção não são previsíveis a partir de `optimizer.thread-pool-size`.
- **R38 — `Population.getFittest()` retorna `null` em população vazia** (`Population.java:60`),
  e `GeneticAlgorithm.evolvePopulation` chama `currentPopulation.getFittest().getFitness()` na
  primeira linha (linha 67) sem checagem.
- **R39 — `TournamentSelection` amostra com reposição**, então um mesmo indivíduo pode ocupar
  os 3 lugares do torneio. Não é necessariamente indesejado, mas não está documentado como
  decisão.

### 7.7 Código-andaime e divergência entre documentação e implementação

- **R40 — Todo o pacote `ga/tactical/` é código morto.** `DayBoundaryCrossover`,
  `MethodologyMutation` e `SpacedRepetitionRepairer` não são instanciados nem injetados.
  `GA_TACTICAL_SCHEDULING_ARCHITECTURE.md` descreve esse AG tático como a arquitetura do sistema;
  ele não existe em execução.
- **R41 — `HybridHeuristicScheduler` (`@Service`) não é injetado em lugar nenhum.** O único
  consumidor é seu próprio teste. O agendamento real usa `StudyScheduleGenerator` +
  `AllocationStrategy`, um caminho diferente.
- **R42 — Beans e classes órfãos adicionais:** `TransferMutation` (bean nomeado, nunca injetado —
  e é o único operador de mutação com teste), `CriticalFirstStrategy` e
  `RandomizedInterleavedStrategy` (nunca referenciadas), `RandomConfig.randomProvider()` (bean
  nunca injetado), construtor `TournamentSelection(int)` (nunca chamado).
- **R43 — `NUMERICAL_STABILITY_AUDIT.md` documenta recomendações como se fossem estado atual em
  algumas leituras.** Ele lista explicitamente "Normalização de objetivos", "penalidades aditivas
  em vez de multiplicativas" e "piso de fitness `Math.max(1.0, score)`" como *recomendações*;
  nenhuma está implementada. Um leitor apressado do conjunto de `.md` da raiz pode concluir o
  contrário.
- **R44 — `TESTING_STRATEGY_ARCHITECTURE.md` descreve uma suíte inexistente** (ver §5.3).
- **R45 — `SCHEDULING_ALGORITHMS_EVALUATION.md` rotula o AG híbrido como "Recommended" sem
  nenhum dado.** A comparação contra Bin Packing e CSP é inteiramente argumentativa. Não há
  medição, implementação de baseline ou experimento que sustente a escolha.
- **R46 — `DefaultPopulationGenerator` instancia `new StudyPlanFactory()` no construtor**
  (linha 23), fora do container, contrariando o padrão de DI usado no resto do pacote.
- **R47 — `Individual.java` carrega imports e um `Logger` não utilizados** (`Subject`, `Map`,
  `log`), resquício de uma versão anterior em que a fitness era calculada dentro da classe.

### 7.8 Build, versionamento e observabilidade

- **R48 — Divergência de versão do Java.** `pom.xml` declara `java.version=21` e o README diz
  Java 21, mas o `maven-compiler-plugin` compila com `source/target 24` e `--enable-preview`.
  Não há nota explicando qual é a alvo real.
- **R49 — Sem CI.** Não existe `.github/` nem qualquer pipeline. Nada garante que a suíte
  (mesmo mínima) rode antes de um merge.
- **R50 — Métricas do AG são só de contorno.** `StudyOptimizerService` registra um `Counter` de
  execuções e um `Timer` de duração. Não há métrica de fitness final, fitness inicial, número de
  gerações até estagnação, taxa de violação de constraint ou diversidade — ou seja, não há como
  observar empiricamente o comportamento do otimizador em produção.
- **R51 — Log de progresso do AG está em `TRACE` e condicionado.**
  `StudyOptimizerService.java:150`: `if (i % 5 == 0 && log.isTraceEnabled())`. Em qualquer nível
  normal, a evolução é uma caixa-preta; só o `log.info` final imprime a melhor fitness.

---

## Apêndice A — Caminho de execução, ponta a ponta

```
POST /api/v1/optimizer/generate
  └─ OptimizerController.generateFullStudyPlan            (orTimeout 30 s)
     └─ DynamicStudyPlannerService.generateFullStudyPlan  (@Async "optimizerTaskExecutor")
        ├─ [1] StudyOptimizerService.optimize(exam, profile, totalDays, numGenerations, populationSize)
        │      ├─ prepareContext()
        │      │    ├─ BaselineCalculator.calculateMinimumDays()        → minimumDaysPerSubject
        │      │    ├─ ImportanceCalculator.calculatePersonalizedImportance() → importanceScores
        │      │    ├─ new RetentionProfile(Map.of())                   ← "dummy"
        │      │    └─ EngagementProfile.baseline()                     ← "dummy"
        │      ├─ gaFactory.create()                                    ← hiperparâmetros hardcoded
        │      ├─ populationGenerator.generate()                        ← StudyPlanFactory aleatório
        │      └─ for (i = 0; i < numGenerations; i++)                  ← sem critério de parada
        │           └─ ga.evolvePopulation()
        │                ├─ trackStagnation() / determineCurrentMutationRate()
        │                ├─ elitismo (1 indivíduo)
        │                ├─ TournamentSelection ×2 → HybridCrossover → CreepMutation
        │                └─ Population.calculateFitness()  (parallelStream)
        │                     └─ FitnessEvaluator.evaluate()  ← §2
        └─ [2] StudyScheduleGenerator.generate(...)                     ← NÃO evolutivo
               └─ ReviewFocusedStrategy( CognitiveLoadBalancingStrategy( InterleavedCriticalStrategy ) )
                    ↑ Ebbinghaus (revisão)   ↑ Sweller (carga)  — ambos APÓS a otimização
```

## Apêndice B — Índice de arquivos citados

| Papel | Arquivo |
|---|---|
| Motor evolutivo | `src/main/java/.../ga/GeneticAlgorithm.java` |
| Hiperparâmetros efetivos | `src/main/java/.../ga/config/DefaultGeneticAlgorithmFactory.java` |
| Defaults do builder | `src/main/java/.../ga/GeneticAlgorithmBuilder.java` |
| Pipeline de fitness | `src/main/java/.../ga/fitness/FitnessEvaluator.java` |
| Único objetivo | `src/main/java/.../ga/fitness/objective/ScoreGainObjective.java` |
| Constraints | `src/main/java/.../ga/fitness/constraint/{MinimumDays,MandatoryReview}Constraint.java` |
| Penalidades | `src/main/java/.../ga/fitness/penalty/{FatigueAndSustainability,DropoutRisk}Penalty.java` |
| Cromossomo macro | `src/main/java/.../domain/StudyPlan.java` |
| Cromossomo tático (não evoluído) | `src/main/java/.../domain/tactical/TacticalStudyPlan.java` |
| Ebbinghaus | `src/main/java/.../service/calculation/retention/HybridRetentionEngine.java` |
| Carga cognitiva | `src/main/java/.../service/calculation/CognitiveLoadCalculator.java` |
| Orquestração do AG | `src/main/java/.../service/StudyOptimizerService.java` |
| Entrada de hiperparâmetros | `src/main/java/.../api/dto/GaConfigDto.java` |
| Config externa | `src/main/resources/application.properties` |
| Testes que tocam o AG | `src/test/java/.../ga/IndividualTest.java`, `src/test/java/.../ga/strategy/mutation/TransferMutationTest.java` |
