# Code Map — verified against the source

**Scope.** A read-only map of this service, produced to support two planned changes: a new
`POST /plans` endpoint speaking the sinapse-platform contract, and a later move of the planning
unit from subject to topic. Nothing was refactored or fixed; findings are recorded only.

**Baseline.** Working tree at commit `cc8e46f` (`Merge pull request #10 from gustavo-rm/release/v5.0`),
branch `claude/youthful-darwin-7y0m1v`. Every path below was opened; every reference claim below
was produced by a command whose text is shown.

**Reading the divergences.** Lines beginning with `DIVERGENCE:` mark a point where the prior
reading analysis did not match the code. They are stated, not corrected.

---

## 1. Production path

The call chain for the one path a client can reach today
(`POST /api/v1/optimizer/generate` → GA → day-by-day schedule):

| Class | Real path | What it does |
|---|---|---|
| `DynamicStudyPlannerService` | `src/main/java/com/ia/project/dynamicstudyplanner/service/DynamicStudyPlannerService.java` | The façade and only implementation of `GenerateStudyPlanUseCase`: runs the strategic optimization, composes the production allocation chain, then calls the schedule generator, all on the optimizer thread pool (`@Async("optimizerTaskExecutor")`, line 47). |
| `GeneticAlgorithm` | `ga/GeneticAlgorithm.java` | Evolves one generation — elitism, then a deliberately sequential offspring loop (selection → crossover → mutation) — and tracks stagnation to switch into a hypermutation rate for 5 generations. |
| `Population` | `ga/Population.java` | Holds one generation's `Individual` list and exposes fittest/worst/average; `calculateFitness` is a plain sequential loop (the old `parallelStream` and its threshold were removed). |
| `StudyPlan` | `domain/StudyPlan.java` | The chromosome: an immutable allocation of study days, stored as an `int[]` aligned to a shared `SubjectIndex`, with `getDaysPerSubject()` rebuilding the map only at the boundary. |
| `SubjectIndex` | `domain/SubjectIndex.java` | The canonical gene order — which subject sits at which array position — built once per optimization from the exam's own subject list and shared by every individual. |
| `GeneVectors` | `ga/GeneVectors.java` | Per-subject constants (minimum days, normalized importance, retention weight, required sessions) projected once onto that order so the hot loop reads array slots instead of maps; also holds `requireCovers(plan)`. |
| `EvolutionContext` | `ga/EvolutionContext.java` | The record bundling everything one evolution step needs (importances, floors, student state, evaluator, horizon, hours/day, daily load budget, `GeneVectors`), built through a step-by-step `Builder`; also owns `normalize`, `temper` and `RETENTION_TEMPERING = 0.5` (line 341). |
| `LearningModel` | `ga/fitness/objective/LearningModel.java` | The shared learning-curve math: `timeConstantDays` (tau scaled by cognitive load), `mastery` (`1 - e^(-d/tau)`) and `requiredSessions` (`horizon / tau`), used by both the mastery and retention objectives. |
| `RandomProvider` | `util/RandomProvider.java` | The single static access point to GA randomness — one `java.util.Random` **per thread** via `ThreadLocal`, seedable with `setInstance` for reproducible runs. |

Supporting classes on the same path, for completeness:
`service/StudyOptimizerService.java` (assembles context, builds the GA, runs the generation loop,
records metrics), `service/EvolutionContextAssembler.java` (builds the context and passes
`exam.getAllSubjects()` as the gene order), `ga/config/DefaultGeneticAlgorithmFactory.java`
(elitism on, crossover 0.95, mutation 0.05, stagnation patience 25, hypermutation 0.20),
`ga/generator/DefaultPopulationGenerator.java`, `ga/factory/StudyPlanFactory.java`,
`ga/Individual.java`, `ga/GeneticAlgorithmBuilder.java`.

No divergence on this item: all nine classes exist, at the paths above, and all nine are reached
by the production request.

---

## 2. Fitness — every class under `ga/fitness`

Source root: `src/main/java/com/ia/project/dynamicstudyplanner/ga/fitness/`.

### Aggregation

| Class | Path | Declared weight | Input fields it depends on | Uses `RandomProvider`? |
|---|---|---|---|---|
| `FitnessEvaluator` | `fitness/FitnessEvaluator.java` | none (it applies the others' weights) | the three injected lists: `List<FitnessObjective>`, `List<FitnessPenalty>`, `List<ConstraintValidator>` | **No** |
| `FitnessWeights` | `fitness/FitnessWeights.java` | declares all of them: `SYLLABUS_MASTERY = 0.50` (line 31), `RETENTION = 0.30` (41), `COGNITIVE_LOAD = 0.20` (52), `CONSTRAINT_VIOLATION = 0.50` (62) | none — constants only | **No** |

`FitnessEvaluator.evaluate` is: weighted sum of objectives, minus `penaltyWeight × severity` per
constraint, clamped to `[0,1]`, then multiplied by every penalty factor. Its constructor asserts
the objective weights sum to 1.0 within `1e-9` and throws `IllegalStateException` otherwise.

### Objectives (`fitness/objective/`)

| Class | Path | Declared weight | Input fields it depends on | Uses `RandomProvider`? |
|---|---|---|---|---|
| `FitnessObjective` | `objective/FitnessObjective.java` | none (interface: `calculateReward`, `getWeight`) | — | **No** |
| `ScoreGainObjective` (O1) | `objective/ScoreGainObjective.java` | `FitnessWeights.SYLLABUS_MASTERY` = **0.50** | `context.geneVectors()` → `normalizedImportance(i)` and the gene order; `plan.daysAt(order, i)`; `Subject.cognitiveLoad()` via `LearningModel.mastery` | **No** |
| `RetentionObjective` (O3) | `objective/RetentionObjective.java` | `FitnessWeights.RETENTION` = **0.30** | `context.geneVectors()` → `retentionWeight(i)`, `requiredSessions(i)` (itself derived from `planningHorizonDays` and `Subject.cognitiveLoad()`); `plan.daysAt(order, i)` | **No** |
| `CognitiveLoadObjective` (O4) | `objective/CognitiveLoadObjective.java` | `FitnessWeights.COGNITIVE_LOAD` = **0.20** | `plan.getTotalDays()`, `context.maxDailyCognitiveLoad()`, `context.hoursPerStudyDay()`, `Subject.cognitiveLoad()` per gene | **No** |
| `LearningModel` | `objective/LearningModel.java` | none (static math, not a registered objective — it is not a Spring bean and implements no interface) | `Subject.cognitiveLoad()`, `planningHorizonDays` | **No** |

The three objectives sum to exactly 1.00.

### Constraints (`fitness/constraint/`) — subtracted, not multiplied

| Class | Path | Declared weight | Input fields it depends on | Uses `RandomProvider`? |
|---|---|---|---|---|
| `ConstraintValidator` | `constraint/ConstraintValidator.java` | interface default `getPenaltyWeight()` = `FitnessWeights.CONSTRAINT_VIOLATION` = **0.50**; default `violationSeverity` is binary 0/1 | — | **No** |
| `MinimumDaysConstraint` | `constraint/MinimumDaysConstraint.java` | inherits **0.50**; overrides `violationSeverity` to graded `missingDays / totalFloor`, capped at 1 | `context.minimumDaysPerSubject()`, `plan.getDaysForSubject(...)` | **No** |
| `MandatoryReviewConstraint` | `constraint/MandatoryReviewConstraint.java` | inherits **0.50**; keeps the binary default severity | `context.retentionProfile()`, `context.importanceScores()` keys, `context.planStartDate()`, injected `RetentionAlgorithm`; reads `TacticalStudyPlan.getSchedule()` | **No** |

`MandatoryReviewConstraint` returns `true` (valid) immediately unless the plan is a
`TacticalStudyPlan` **and** a retention profile is present — neither holds on the production path,
so it contributes zero there.

### Penalties (`fitness/penalty/`) — multiplicative

| Class | Path | Declared weight | Input fields it depends on | Uses `RandomProvider`? |
|---|---|---|---|---|
| `FitnessPenalty` | `penalty/FitnessPenalty.java` | **none** — the interface has only `calculatePenaltyFactor`, no weight accessor | — | **No** |
| `DropoutRiskPenalty` | `penalty/DropoutRiskPenalty.java` | **none**; hard-coded constants instead (risk cutoff 0.4, recovery load < 20.0, max 80% reduction, floor 0.1) | `context.engagementProfile()`, `context.studentState()`, `TacticalStudyPlan.calculateTotalCognitiveLoad()`, injected `DropoutRiskAlgorithm` | **No** |
| `FatigueAndSustainabilityPenalty` | `penalty/FatigueAndSustainabilityPenalty.java` | **none** | `context.studentState()`, injected `FatigueAlgorithm`, and the plan only when it is a `TacticalStudyPlan` | **No** |

Both penalties return `1.0` on the macro path by design, so the product is the identity there.

**Evidence for the `RandomProvider` column** — the command and its full output:

```
$ grep -rn "RandomProvider\|Random" src/main/java/com/ia/project/dynamicstudyplanner/ga/fitness/
(no match in ga/fitness)
```

A repo-wide `grep -rn "RandomProvider" src/main --include=*.java` returns matches only in
`util/RandomProvider.java` itself and in ten operator classes:
`ga/strategy/selection/TournamentSelection`, `ga/strategy/crossover/{HybridCrossover,
RepairingCrossover, WeightedAverageCrossover, ChildGeneRepair}`,
`ga/strategy/mutation/{AbstractMutationStrategy, CreepMutation}`, `ga/factory/StudyPlanFactory`,
and the two tactical operators `ga/tactical/strategy/crossover/DayBoundaryCrossover` and
`ga/tactical/strategy/mutation/MethodologyMutation`. No fitness class appears.

> **DIVERGENCE:** the request assumes every class under `ga/fitness` has a *declared weight*. Only
> the three objectives and the two constraints do. `FitnessPenalty` and both its implementations
> declare no weight at all — they are pure multipliers with inline constants — and `FitnessEvaluator`,
> `FitnessWeights`, `FitnessObjective` and `LearningModel` have none either. Any topic-level
> reweighting has to treat the penalty branch as a separate mechanism, not a fourth weight.

---

## 3. Tactical scheduling

| Class | Real path | What it does |
|---|---|---|
| `StudyScheduleGenerator` | `service/StudyScheduleGenerator.java` | Turns the GA's day allocation into a real calendar: converts days to hours, runs a viability analysis against the student's availability up to the exam date (emitting `SUCCESS_IDEAL_PLAN` / `SUCCESS_WITH_SURPLUS_TIME` / `WARNING_TIME_DEFICIT`, scaling the plan down proportionally on a deficit), then walks day by day delegating each day's hours to an `AllocationStrategy`. |
| `AllocationChains.production(int)` | `service/scheduler/strategy/AllocationChains.java` | The single definition of the decorator chain the student actually gets: `ReviewFocusedStrategy(CognitiveLoadBalancingStrategy(InterleavedCriticalStrategy(), maxDailyCognitiveLoad))`. A sibling `productionWithoutLoadPruning()` exists for benchmark measurement only. |
| `TacticalStudyPlan` | `domain/tactical/TacticalStudyPlan.java` | A second chromosome shape: extends `StudyPlan` and maps `TimeSlot → TacticalStudyBlock`, deriving the days-per-subject view by counting distinct days; adds `calculateTotalCognitiveLoad()`. |
| `TimeSlot` | `domain/tactical/TimeSlot.java` | A `record(LocalDateTime startTime, LocalDateTime endTime)` with `getDurationMinutes()`; used as the map key (locus) so blocks cannot overlap. |
| `TacticalStudyBlock` | `domain/tactical/TacticalStudyBlock.java` | A `record(Subject, StudyMethodology, long durationMinutes)` with `calculateRequiredEnergy()` and `calculateEmotionalLoad()`; the gene of the tactical representation. |

> **DIVERGENCE:** grouping these five under one heading implies one subsystem. They are two
> disjoint ones. `StudyScheduleGenerator` + `AllocationChains.production` produce
> `ScheduleResult(Map<LocalDate, List<StudyBlock>>, status, requiredHours, availableHours)` using
> `domain/StudyBlock` — a `record(Subject, int hours)`. They never construct, read or import
> `TacticalStudyPlan`, `TimeSlot` or `TacticalStudyBlock`. The `domain/tactical` types are reached
> only from the unused tactical layer and from the three fitness components that short-circuit on
> the macro path (see §4). Nothing in the production request ever builds a `TacticalStudyPlan`.

---

## 4. Outside the production path

**Question:** are `ga/tactical/**` and `service/scheduler/tactical/**` (including
`HybridHeuristicScheduler`, `ChromosomeRepairer`, `SpacedRepetitionRepairer`) referenced by any
`src/main` class outside their own package?

**Answer: no.** Confirmed.

Commands and results:

```
$ grep -rn "ga\.tactical" src/main --include=*.java
ga/tactical/repair/ChromosomeRepairer.java:1:package com.ia...ga.tactical.repair;
ga/tactical/repair/SpacedRepetitionRepairer.java:1:package com.ia...ga.tactical.repair;
ga/tactical/strategy/crossover/DayBoundaryCrossover.java:1:package com.ia...ga.tactical.strategy.crossover;
ga/tactical/strategy/crossover/TacticalCrossoverStrategy.java:1:package com.ia...ga.tactical.strategy.crossover;
ga/tactical/strategy/mutation/MethodologyMutation.java:1:package com.ia...ga.tactical.strategy.mutation;
ga/tactical/strategy/mutation/TacticalMutationStrategy.java:1:package com.ia...ga.tactical.strategy.mutation;

$ grep -rn "scheduler\.tactical" src/main --include=*.java
service/scheduler/tactical/HybridHeuristicScheduler.java:1:package com.ia...service.scheduler.tactical;
service/scheduler/tactical/TacticalScheduler.java:1:package com.ia...service.scheduler.tactical;
```

Every hit is the package declaration of the file itself — there is not a single `import` of either
package anywhere in `src/main`.

Per-class simple-name sweep, excluding the two declaring package trees:

```
$ for c in ChromosomeRepairer SpacedRepetitionRepairer DayBoundaryCrossover \
           TacticalCrossoverStrategy MethodologyMutation TacticalMutationStrategy \
           HybridHeuristicScheduler TacticalScheduler; do
    echo "--- $c"
    grep -rn --include=*.java -w "$c" src/main \
      | grep -v "/ga/tactical/" | grep -v "/service/scheduler/tactical/" \
      || echo "    (no references outside ga/tactical + service/scheduler/tactical)"
  done
```

All eight printed `(no references outside ga/tactical + service/scheduler/tactical)`.

Two qualifications that matter before anything is deleted or moved:

- **Two of them are Spring beans and are therefore instantiated at startup**, even with no caller:
  `SpacedRepetitionRepairer` is `@Component` (line 28) and `HybridHeuristicScheduler` is `@Service`
  (line 17). `SpacedRepetitionRepairer` constructor-injects `RetentionAlgorithm`. "Unreferenced"
  here means no call site, not absent from the context.
- **Only tests reach them.** `grep -rln "ga\.tactical\|scheduler\.tactical" src/test benchmarks`
  returns exactly three files: `src/test/java/.../ga/tactical/SpacedRepetitionRepairerTest.java`,
  `src/test/java/.../ga/tactical/DayBoundaryCrossoverTest.java`, and
  `src/test/java/.../service/scheduler/tactical/HybridHeuristicSchedulerTest.java`.

The tactical *domain* types are a separate matter: `grep -rln "domain.tactical" src/main` shows
`domain/tactical/**` is also imported by `ga/fitness/constraint/MandatoryReviewConstraint`,
`ga/fitness/penalty/DropoutRiskPenalty`, `ga/fitness/penalty/FatigueAndSustainabilityPenalty`,
`service/calculation/fatigue/FatigueAndEnergyModel` and
`service/calculation/engagement/DropoutRiskPredictor` — all of which are live beans that short-circuit
because the plan they receive is never a `TacticalStudyPlan`.

---

## 5. API

### Every endpoint the application exposes today

| Verb | Route | Request DTO | Response DTO | Declared in |
|---|---|---|---|---|
| `POST` | `/api/v1/optimizer/generate` | `OptimizationRequest` (`@Valid @RequestBody`) | `CompletableFuture<ResponseEntity<PlannerResponseDto>>`, 30 s `orTimeout`; errors as `application/problem+json` (`ProblemDetail`) | `api/controller/OptimizerController.java:54` |
| `POST` | `/api/v1/optimizer/jobs` | `OptimizationRequest` (`@Valid @RequestBody`) | `202 Accepted` + `Location` header, body `JobAcceptedDto(id, status, statusUrl)` | `api/controller/OptimizerJobController.java:80` |
| `GET` | `/api/v1/optimizer/jobs/{id}` | `@PathVariable String id` (no body) | `JobStatusDto(id, status, submittedAt, startedAt, finishedAt, result, error)`; `404` via `JobNotFoundException` | `api/controller/OptimizerJobController.java:120` |

`grep -rn "@GetMapping\|@PostMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping\|@RequestMapping\|@RestController\|@Controller" src/main --include=*.java`
returns only these two controllers plus the three `@RestControllerAdvice` classes
(`RequestErrorAdvice`, `BusinessRuleErrorAdvice`, `InfrastructureErrorAdvice`) — there is no third
controller.

Also served, but not written as controllers here:

- `GET /v3/api-docs`, `GET /swagger-ui/**`, `GET /swagger-ui.html` — springdoc, configured by
  `config/OpenApiConfig.java`.
- `GET /actuator/health` (+ `/actuator/health/**`) and `GET /actuator/prometheus` — the only two
  exposed, per `src/main/resources/application.properties:95`
  (`management.endpoints.web.exposure.include=health,prometheus`).

Request DTO tree (all in `api/dto/`): `OptimizationRequest{exam: ExamDto, studentProfile:
StudentProfileDto, gaConfig: GaConfigDto}`; `ExamDto{name, examDate, generalKnowledgeTotalScore,
generalKnowledgeSubjects: List<SubjectDto>, specificKnowledgeAxes: List<ThematicAxisDto>}`;
`ThematicAxisDto{id, name, weight, subjects: List<SubjectDto>}`; `SubjectDto{name, questionCount
(1–500), cognitiveLoad (1–5)}`; `GaConfigDto{totalStudyDays (1–365), numGenerations (10–1000),
populationSize (10–500)}`.

Response DTO tree: `PlannerResponseDto{message, optimizationResult: OptimizationResultDto,
scheduleResult: ScheduleResultDto}`; `OptimizationResultDto{plan: StudyPlanDto, fitness,
generationsRun, executionTimeMillis}`; `StudyPlanDto{daysPerSubject: Map<String,Integer>}`;
`ScheduleResultDto{schedule: Map<LocalDate, List<StudyBlockDto>>, status, requiredHours,
availableHours}`; `StudyBlockDto{subjectName, hours}`.

Note for the `POST /plans` work: the rate limiter
(`api/controller/RateLimitingFilter.java:85`) only prices requests whose URI
`startsWith("/api/v1/optimizer/")` and whose method is not `GET`. A new route outside that prefix
is currently **not** rate-limited.

### `openapi-snapshot.json`

- **Location:** `src/test/resources/contract/openapi-snapshot.json` — 700 lines, `openapi: 3.1.0`.
  It records exactly the three business paths above and 15 schemas (`ExamDto`, `GaConfigDto`,
  `JobAcceptedDto`, `JobStatusDto`, `OptimizationRequest`, `OptimizationResultDto`,
  `PlannerResponseDto`, `ProblemDetail`, `ScheduleResultDto`, `StudentProfileDto`,
  `StudentStateDto`, `StudyBlockDto`, `StudyPlanDto`, `SubjectDto`, `ThematicAxisDto`).
- **How it is generated:** *not* by a build plugin — there is no OpenAPI generation goal in
  `pom.xml`. It is a committed snapshot, refreshed by hand. When the test below fails it writes the
  current document to `target/openapi-atual.json` and its failure message instructs the author to
  copy that file over the snapshot **in the same commit** so the contract change shows up in the
  diff (`contract/OpenApiContractTest.java:97-115`).
- **How it is verified:** `src/test/java/com/ia/project/dynamicstudyplanner/contract/OpenApiContractTest.java`,
  a `@SpringBootTest @AutoConfigureMockMvc` class with four tests — (1) `GET /v3/api-docs` returns
  200; (2) the live document, normalized by sorting keys into a `TreeMap`, equals the normalized
  snapshot; (3) the path set is exactly the three routes, with `generate` POST-only, `jobs`
  POST-only and `jobs/{id}` GET-only, and the listed schemas and response codes are present;
  (4) the DoS-guard limits (365 / 1000 / 500 / 500 / 5) are published in the schema. It runs inside
  `./mvnw verify`, which is what `.github/workflows/ci.yml` executes on push and PR to `main` and
  `release/**`.

---

## 6. Configuration

### `SecurityConfig` — `src/main/java/com/ia/project/dynamicstudyplanner/config/SecurityConfig.java`

`permitAll()` is granted to, in order (lines 104–111):

1. `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`
2. `/api/v1/**` — i.e. **all three business endpoints are unauthenticated**
3. `/actuator/health`, `/actuator/health/**`

Everything else is `anyRequest().authenticated()` (line 117) behind
`httpBasic(Customizer.withDefaults())` (line 123) — which in practice means `/actuator/prometheus`.
CSRF is disabled. When `api.security.require-https` is true (default **false**), the chain adds
`requiresSecure()` and an HSTS header with `api.security.hsts-max-age-seconds` (default 31 536 000).
The class's own Javadoc states plainly that there is no authentication of any kind on the API, here
or delegated, and that the default-off HTTPS flag is meant to be turned on together with
`api.trusted-proxies`. A `POST /plans` added under a new prefix would fall into
`anyRequest().authenticated()` and return 401/403 unless a matcher is added.

### `optimizerTaskExecutor` and `@Async`

- Defined in `config/AsyncConfig.java:58-84` (`@Configuration @EnableAsync`), as a
  `ThreadPoolTaskExecutor` with core = max = `optimizer.thread-pool-size` if > 0 else
  `Runtime.getRuntime().availableProcessors()`, queue capacity `optimizer.queue-capacity` (default
  32), thread prefix `Optimizer-Async-`, and an `MdcTaskDecorator` so the trace id crosses threads.
  The same class registers the gauge `dynamicstudyplanner.optimizer.queue.saturation`.
- **`@Async` is used in exactly one place in `src/main`:**
  `service/DynamicStudyPlannerService.java:47` — `@Async("optimizerTaskExecutor")` on
  `generateFullStudyPlan`. (`grep -rn "@Async" src/main --include=*.java`; other hits are
  `@EnableAsync` in `AsyncConfig` and prose in comments.)
- `service/OptimizationJobService.java` deliberately does **not** use `@Async`: it takes the
  executor by `@Qualifier("optimizerTaskExecutor")` (line 72) and submits explicitly, because
  `@Async` is proxy-based and a self-invocation inside the same bean would run inline (comment at
  lines 106–110).

### `config/RandomConfig`

`src/main/java/com/ia/project/dynamicstudyplanner/config/RandomConfig.java` — a `@Configuration`
declaring one bean, `public Random randomProvider()` (line 18), returning `new SecureRandom()`.

**Who injects it: nobody.**

```
$ grep -rn "randomProvider\|RandomConfig" src benchmarks --include=*.java
src/test/java/.../support/RandomProviderIsolation.java:47:    private static final String KEY = "randomProviderBefore";
src/main/java/.../config/RandomConfig.java:15:public class RandomConfig {
src/main/java/.../config/RandomConfig.java:18:    public Random randomProvider() {

$ grep -rn -E "\bRandom\s+[a-zA-Z_]" src/main --include=*.java
# only: RandomProvider.getInstance()/setInstance() signatures, RandomConfig itself, and one comment
```

The single test hit is an unrelated string constant. No constructor parameter, field or
`@Autowired` point of type `java.util.Random` exists anywhere in `src/main`.

> **DIVERGENCE:** the question "who injects this bean — check all of `src/main`" presupposes an
> injector. There is none. `RandomConfig` is dead configuration: the GA takes its randomness from
> the static `util/RandomProvider`, which returns a per-thread `java.util.Random` and never consults
> Spring. The bean's Javadoc ("use SecureRandom in production... swap to a fixed-seed Random in
> tests") describes a design the code does not implement — and `RandomProvider`'s own Javadoc
> records that `SecureRandom` was deliberately abandoned because it accounted for ~31% of GA CPU time.

---

## 7. Tests

### `GaResultadoInalteradoTest` — `src/test/java/com/ia/project/dynamicstudyplanner/ga/GaResultadoInalteradoTest.java`

Four `@Test` methods, all wiring the optimizer by hand (no Spring context), with
`SEMENTE = 20260903L`:

1. `mesmaSementeProduzOMesmoPlano` — 12 subjects, 60 generations, population 40; asserts identical
   signature, bit-identical fitness and identical `generationsRun` across two seeded runs.
2. `aReprodutibilidadeValeParaVariosTamanhos` — the same for 5, 12 and 24 subjects at 40 generations
   / population 30.
3. `sementesDiferentesProduzemPlanosDiferentes` — the counter-proof: seeds `1L` and `2L` must
   produce different signatures.
4. `oOrcamentoEhRespeitado` — the day allocation sums to exactly 365 at 5, 12 and 24 subjects.

**How comparison works:** the private helper `assinatura(OptimizationResult)` (lines 127–133) builds
a string `"D0=105|D1=84|…"` from `plan().getDaysPerSubject()`, sorted by subject name so map
iteration order cannot affect it. Each run is preceded by
`RandomProvider.setInstance(new Random(SEMENTE))` (line 138). The comparison is always
**run-against-run, in the same JVM**.

> **DIVERGENCE:** there are **no reference signatures recorded in this test, or anywhere in the
> repository.** The test explicitly rejects golden values — its Javadoc (lines 69–75) says
> hard-coded numbers "would have to be rewritten on every legitimate algorithm change, and the
> temptation would be to rewrite them without looking", so it verifies reproducibility instead and
> leaves stability between commits to the diff. The "9 reference signatures" that
> `util/RandomProvider.java:85` and `ga/GeneticAlgorithm.java:88` mention are 3 seeds × 3 instance
> sizes captured on the measurement bench; `docs/qualidade/05b-correcao-performance.md:75` states
> in so many words: *"As assinaturas moram na bancada de medição, fora do repositório."* Any future
> change cannot re-check them from this checkout — only the four property assertions above run in CI.

### `RandomProviderIsolation` — `src/test/java/com/ia/project/dynamicstudyplanner/support/RandomProviderIsolation.java`

A JUnit 5 extension implementing `BeforeEachCallback` and `AfterEachCallback`. `beforeEach` stashes
`RandomProvider.getInstance()` in the extension store; `afterEach` removes it and reinstalls it,
falling back to `new Random()` — deliberately `java.util.Random`, not `SecureRandom`, to match the
production default. It is registered **automatically for every test on the test classpath**, with no
annotation required, by the pair:

- `src/test/resources/META-INF/services/org.junit.jupiter.api.extension.Extension`, whose single
  line is `com.ia.project.dynamicstudyplanner.support.RandomProviderIsolation`;
- `src/test/resources/junit-platform.properties`, which sets
  `junit.jupiter.extensions.autodetection.enabled=true` and
  `junit.jupiter.execution.parallel.enabled=false`.

Since `RandomProvider` became thread-local, before/after run on the test thread, so the extension is
also safe under parallel execution — though the properties file still pins the suite to sequential.

---

## 8. `StudentProfileDto`

Path: `src/main/java/com/ia/project/dynamicstudyplanner/api/dto/StudentProfileDto.java` — a record
with four components:

| Field | Type | Validation | Required? |
|---|---|---|---|
| `name` | `String` | `@NotBlank`, `@Size(max = 100)` | **yes** |
| `knowledgeGaps` | `Map<String, Double>` | `@NotEmpty`, `@Size(max = 100)`; keys `@NotBlank @Size(max=100)`, values `@NotNull @DecimalMin("1.0") @DecimalMax("5.0")` | **yes** |
| `weeklyAvailability` | `Map<DayOfWeek, Integer>` | `@NotEmpty`, `@Size(max = 7)`; keys `@NotNull`, values `@NotNull @Min(0) @Max(24)` | **yes** |
| `state` | `StudentStateDto` | `@jakarta.validation.Valid` only | **no** |

> **DIVERGENCE:** `state` is **not** a required field. It carries `@Valid` but no `@NotNull`, so
> a payload omitting it validates, and `StudentProfileMapper.toDomain` (lines 60–74) leaves
> `StudentState` as `null` when it is absent. Within `state`, `stressLevel`, `fatigueLevel` and
> `motivationLevel` are each `@NotNull` 1.0–5.0, but `chronotype` is optional and defaults to
> `Chronotype.INTERMEDIATE` in the mapper.

`knowledgeGaps` carries an extra cross-field rule the schema cannot express: every key must name a
subject present in the submitted exam, enforced in `StudentProfileMapper` (which throws
`UnknownSubjectException` → 400).

### Which fitness classes consume each field

| Field | Path into the fitness pipeline | Fitness classes that actually read it |
|---|---|---|
| `name` | `StudentProfile.getName()` — used for logging/response only | **none** |
| `knowledgeGaps` | `ImportanceCalculator.calculatePersonalizedImportance` (base exam importance × gap factor) → `EvolutionContext.importanceScores` → `normalize()` → `normalizedImportance`, and → `temper()` (exponent `RETENTION_TEMPERING = 0.5`) → `retentionWeights`; both projected into `GeneVectors`. Separately `BaselineCalculator.calculateMinimumDays` → `minimumDaysPerSubject`. Also feeds `CognitiveLoadCalculator`'s fatigue factor via `getAverageKnowledgeGap()`. | `ScoreGainObjective` (via `normalizedImportance`), `RetentionObjective` (via `retentionWeight`), `MinimumDaysConstraint` (via the floors), `MandatoryReviewConstraint` (iterates `importanceScores.keySet()`), `CognitiveLoadObjective` (indirectly, through the budget) |
| `weeklyAvailability` | `StudentProfile.getTotalWeeklyHours()` → `hoursPerStudyDay = ceil(total/7)` and → `CognitiveLoadCalculator.calculate` base capacity → `maxDailyCognitiveLoad` | `CognitiveLoadObjective` (both `hoursPerStudyDay()` and `maxDailyCognitiveLoad()`) |
| `state` | `EvolutionContextAssembler` line 130 sets `.studentState(profile.getState())`; the same state also shapes `CognitiveLoadCalculator`'s stress/fatigue/motivation modifiers, and therefore the daily budget | `CognitiveLoadObjective` (indirectly, via the budget — the only place the state can discriminate between plans on the macro path); `FatigueAndSustainabilityPenalty` reads `context.studentState()` but returns `1.0` for a non-tactical plan; `DropoutRiskPenalty` passes it to the risk predictor, also only for a tactical plan |

Useful for the topic migration: no fitness class reads `StudentProfileDto` directly. Everything
arrives through `EvolutionContext` / `GeneVectors`, which are keyed by `Subject`. Changing the
planning unit to topic means changing what `SubjectIndex` indexes; the objectives themselves read
only array positions.

---

## 9. Root `*_ARCHITECTURE.md`

Five files match `*_ARCHITECTURE.md` at the repository root (`ls *_ARCHITECTURE.md`):

| File | Implemented, or design proposal? |
|---|---|
| `DROPOUT_RISK_PREDICTOR_ARCHITECTURE.md` | **Mixed, mostly proposal.** §2's scoring heuristic has a real counterpart: `service/calculation/engagement/DropoutRiskPredictor` (a `@Service` implementing `DropoutRiskAlgorithm`) and `ga/fitness/penalty/DropoutRiskPenalty`. But §3 ("Recovery Days", "Motivational Wins", "Schedule Triage") is unimplemented, §5 (ML) is explicitly forward-looking, the doc's stated formula `Risk = 0.4*ConsistencyDrop + 0.6*Fatigue` does not match the code, and the doc's "flags high risk (e.g., > 0.7)" does not match the penalty's actual 0.4 cutoff. The predictor also needs telemetry (`recentCompletionRate`, `consecutiveFailedDays`, `hasExpressedFrustration`) that no endpoint collects — production passes `EngagementProfile.baseline()`. |
| `FATIGUE_AND_ENERGY_MODEL_ARCHITECTURE.md` | **Mixed; §2 is implemented, §3 is not true today.** `service/calculation/fatigue/FatigueAndEnergyModel` really does implement the described dimensions: chronotype peak hours (lark 9/15, owl 14/22, intermediate 10/16), an energy multiplier clamped to 0.5–1.5, 20% interday fatigue carryover, and a burnout threshold of 50.0 with acute/chronic cliffs. Two mismatches: the doc says sine waves, the code uses a cosine-shaped contribution around each peak hour; and §3's claim that the model "replaces the simplistic math in `FatigueAndSustainabilityPenalty`" holds only for a `TacticalStudyPlan`, which production never builds — the macro branch returns `1.0` and says so. §4 (ML) is a proposal. |
| `GA_TACTICAL_SCHEDULING_ARCHITECTURE.md` | **Design proposal.** It is written throughout as a recommendation ("Recommended", "we must", "should be designed"). Several named pieces exist as classes — `ChromosomeRepairer`, `SpacedRepetitionRepairer`, `DayBoundaryCrossover`, `MethodologyMutation`, and the `TimeSlot → StudyBlock` chromosome as `TacticalStudyPlan` — but there is **no tactical GA**: nothing in `src/main` evolves a `TacticalStudyPlan`, and per §4 none of these classes is referenced from outside its own package. The §3 mutations "Subject Swap" and "Intensity" and the §4 "Methodology Inheritance Crossover" have no implementation at all. |
| `RETENTION_ENGINE_ARCHITECTURE.md` | **Mixed.** §2C and §3 are implemented: `service/calculation/retention/HybridRetentionEngine` (a `@Service` implementing `RetentionAlgorithm`) has SM-2 interval/easiness updates with the 1.3 floor and `R = exp(-t/S)`. §4A's `MandatoryReviewConstraint` exists but is neutral on the macro path, and its threshold is `e^-1 ≈ 0.368`, not the 80% the doc names. §4B's repairer exists but is called by nothing (§4). §5 (load-modified stability) and §6 (ML/half-life regression) are proposals. Note the macro path passes an empty `RetentionProfile`, so no review is ever computed in production. |
| `TESTING_STRATEGY_ARCHITECTURE.md` | **Design proposal, and partly contradicted by the code.** §1B property-based testing: no such library in `pom.xml` (no jqwik). §1D "run the GA 50 times and assert statistical boundaries": no such test exists. §3: no JMH and no Gatling/JMeter in the build. Most importantly, §2 prescribes "replace all bare `new Random()` calls with a globally injected `RandomProvider` bean… In Production: `SecureRandom`… In Testing: `@SpringBootTest` injects a fixed-seed provider" — this is exactly the design of the dead `config/RandomConfig` bean from §6. The implementation went the other way: a static, per-thread `java.util.Random` seeded directly by `RandomProvider.setInstance`, with `SecureRandom` removed for cost. Only §1A (deterministic component tests) matches what is in `src/test`. |

Two other root documents are audits or evaluations and do **not** match the `*_ARCHITECTURE.md`
pattern, so they are outside this item: `NUMERICAL_STABILITY_AUDIT.md` and
`SCHEDULING_ALGORITHMS_EVALUATION.md`.

---

## Divergence summary

1. **§2** — not every class under `ga/fitness` declares a weight: both `FitnessPenalty`
   implementations have none and use inline constants.
2. **§3** — `StudyScheduleGenerator` / `AllocationChains.production` and
   `TacticalStudyPlan` / `TimeSlot` / `TacticalStudyBlock` are two disjoint subsystems; the former
   emits `domain/StudyBlock` keyed by `LocalDate` and never touches the latter.
3. **§6** — nothing injects `config/RandomConfig`'s `Random` bean; it is dead configuration, and
   the GA uses the static `util/RandomProvider` instead.
4. **§7** — `GaResultadoInalteradoTest` stores no reference signatures; it compares two seeded runs
   in-process, and the "9 reference signatures" live on the measurement bench, outside this
   repository.
5. **§8** — `StudentProfileDto.state` is optional (`@Valid` without `@NotNull`), as is
   `state.chronotype` (mapper defaults it to `INTERMEDIATE`).
