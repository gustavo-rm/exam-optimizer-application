# CLAUDE.md

Guidance for Claude Code sessions working in this repository. Sessions do not share context, so
read this before touching anything. Every number below was read from the files it cites — if a
number here disagrees with the file, the file wins and this document is the bug.

## 1. Build and quality gates

`./mvnw verify` is the gate: Checkstyle (`validate`), the test suite, then the JaCoCo check
(`verify`). CI runs the same command, so a green local `verify` is a green CI.

**Checkstyle** — `config/checkstyle/checkstyle.xml`, at `violationSeverity=error` and
`includeTestSourceDirectory=true`: test code is checked like production code.

| Rule | Max |
|---|---|
| `CyclomaticComplexity` | **10** |
| `MethodLength` | 80 |
| `LineLength` | 120 |
| `ParameterNumber` | 8 |
| `NestedIfDepth` | 3 |

A method over complexity 10 is split into named helpers, not annotated away. Record components do
not count toward `ParameterNumber` — a 9-component record passes.

**JaCoCo** — `pom.xml`, execution `check-coverage-floor`, one rule on the `BUNDLE` element:

| Counter | Minimum |
|---|---|
| `INSTRUCTION` | **0.9325** |
| `BRANCH` | **0.7861** |

A ratchet, not a target: flush against a past measurement, so any regression fails the build.
JaCoCo **truncates** the ratio before comparing — read the real figure from
`target/site/jacoco/jacoco.csv`, not a rounded print-out.

### The prohibition

**Lowering a coverage floor, relaxing a Checkstyle rule, or adding `@SuppressWarnings` to make the
build pass is forbidden.** Write the missing test, or split the method, or stop and report. If new
code genuinely adds uncovered branches, the fix is a test that exercises them — a real one, pinning
behaviour that matters. If you believe a floor must move, do not move it: stop and say why.

## 2. The Core contract package (`coreapi/contract`)

Mirrors `br.com.sinapse.platform.coreclient.contract` from `sinapse-platform`. Records and enums
only; it imports nothing from this codebase and nothing here imports it.

**It is not a literal mirror, and the difference is load-bearing.** All **10** records carry
`@JsonInclude(JsonInclude.Include.NON_NULL)`, which the originals do not have: the platform gets the
same effect from `spring.jackson.default-property-inclusion: non_null` globally, while this
application sets no `spring.jackson` key, so Jackson's `ALWAYS` default would write a null field
where the reference document omits the key. **Removing the annotation breaks wire compatibility.**
It repeats per type because `@JsonInclude` does not reach a nested record.

`src/test/resources/contract/plan-request-v1.0.json` and `plan-response-v1.0.json` are
**byte-identical** to the copies in `sinapse-platform`, which runs a twin test against the same two
files. **Never edit them to make a test pass.** A failure there means the two repositories have
diverged; fix the records, or report. Changing the shape means bumping `PlanRequest.VERSION` and
updating both repositories in the same logical change.

`Topic.effortTier` is a `String`, deliberately, not an enum. Validating the closed set
(`SHORT`, `STANDARD`, `LONG`, `EXTENDED`) belongs to the adapter.

## 3. Determinism

`util/RandomProvider` is the only source of randomness **in the genetic algorithm** — one seedable
`java.util.Random` per thread. Tests fix it with `RandomProvider.setInstance(new Random(seed))`, and
the `support/RandomProviderIsolation` JUnit extension (auto-registered for every test via
`META-INF/services` and `junit-platform.properties`) restores it afterwards. Do not introduce
`Math.random()`, `ThreadLocalRandom` (not seedable) or a bare `new Random()` into GA code.

Two things outside the GA are not covered by that rule and should not be "fixed": job ids come from
`UUID.randomUUID()` in `service/OptimizationJobService`, and `config/RandomConfig` declares a
`SecureRandom` bean that **nothing injects** — dead configuration, not the GA's source.

**The offspring loop in `ga/GeneticAlgorithm.evolvePopulation` is sequential on purpose.**
Parallelising it has been tried and measured: two runs with the same seed stop agreeing, because
which thread draws which number depends on scheduling. `GaResultadoInalteradoTest` fails **2 of its
4 tests**, and the gain is not even consistent (see the comment in the method). `Population
.calculateFitness` is sequential for the same class of reason.

**No class under `ga/fitness` may consume `RandomProvider`.** Fitness must be a pure function of
plan and context; a draw there makes the same plan score differently on re-evaluation. Verify with
`grep -rn "RandomProvider" src/main/java/com/ia/project/dynamicstudyplanner/ga/fitness/` — it must
return nothing.

## 4. Do not touch without an explicit instruction

`ga/strategy/mutation/CreepMutation`, `ga/strategy/mutation/TransferMutation`,
`ga/strategy/crossover/WeightedAverageCrossover`, `ga/strategy/selection/TournamentSelection`,
`ga/Population`, and the evolution loop in `ga/GeneticAlgorithm`.

They are already agnostic of the planning unit, in two different ways. The three mutation/crossover
operators work on `int[]` gene vectors aligned to a `SubjectIndex`, so they never name a subject.
`TournamentSelection`, `Population` and the evolution loop never touch genes at all — they move
whole `Individual`s around by fitness. Changing the planning unit from subject to topic is a change
to what `SubjectIndex` indexes; none of these six needs to change, and changing them is how the
migration acquires a regression.

Note: `TransferMutation` is a `@Component("transferMutation")` but is **not** on the production
path — `ga/config/DefaultGeneticAlgorithmFactory` injects `@Qualifier("creepMutation")`. It is still
covered by a test; leave it alone.

## 5. `GaResultadoInalteradoTest`

Four tests. It pins that the GA is **reproducible**: with the seed fixed to `20260903L`, the same
input yields the same plan, subject by subject, and the same fitness to the last bit — plus the
counter-proof that different seeds diverge, and that the day budget is respected exactly. It is
stricter than it looks: any change to the **order** or **count** of random draws fails it, even one
that changes no arithmetic.

**There is no stored reference signature in this repository, and none should be added.** The test
computes a signature in-process and compares two seeded runs against each other — deliberately, so
that no one can make it pass by rewriting a recorded number. The rule that follows is the same
either way: **a divergence here is a finding, not noise.** Do not change the seed, loosen an
assertion, or weaken the comparison to get green. Diagnose it, and if the behaviour change is
intended, say so explicitly in the change that causes it.

## 6. Language

Set by `docs/adr/0007-idioma-do-codigo-e-da-documentacao.md`. The rule in one line: **what a machine
reads, or what leaves through the API, is in English; what only a person reads is in Portuguese.**

| Element | Language |
|---|---|
| Class, method, variable, constant, package names | English |
| Exception messages that reach the client | English |
| Log keys and metric names | English |
| **Javadoc and code comments** | **Portuguese** |
| `@DisplayName` and test method names | Portuguese |
| `docs/` | Portuguese |
| Commit messages (as practised on `main`) | Portuguese subject, English Conventional-Commit prefix |

`README.md` is in English. Nothing is renamed or translated in bulk; the convention applies to new
code and to code you are already changing for another reason.

## 7. Branches and commits

Branch: `type/version/description` — e.g. `feature/1.0/core-contract-mirror`, `docs/1.0/claude-md`.
Release branches are `release/vN.0`.

Commits follow **Conventional Commits**: `type(scope): subject` — types in use on `main` are `feat`,
`fix`, `perf`, `docs`, `test`; scopes like `ga`, `coreapi`, `escala`, `qualidade`. Explain *why* in
the body; this repository's commit messages carry the reasoning.

Neither convention is enforced by tooling: there is no `CONTRIBUTING.md`, no commit-lint and no PR
template, and the "Contributing" section of `README.md` still shows an older, different style. Follow
this file, not that section.
