# 🧠 Dynamic Study Planner API

> An optimization engine for study plans built with Artificial Intelligence (Genetic Algorithms) and founded on Learning Science Theories.

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-DDD%20%7C%20Stateless-blueviolet?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

## 📖 About the Project

The **Dynamic Study Planner** solves the chronic problem of static and generic ("one-size-fits-all") study plans.

The system operates through a **Stateless RESTful API**. It receives a "snapshot" of a student — the
horizon, the availability windows, the topics with their effort tiers, the prerequisite graph, the
goals and the study history — and returns a scheduled plan: which topic to study in which window,
prerequisites first.

The objective function (Fitness) is not arbitrary: two of its three terms are derived from established
results in learning science, and the derivation, the weights and — importantly — the limits of each
approximation are documented in
[`docs/revisao-ag/05-fitness-function.md`](docs/revisao-ag/05-fitness-function.md).

> **One path since EOA-4b.** This service used to expose a second, older API of its own —
> `/api/v1/optimizer/**`, which planned a Brazilian public-exam syllabus from a self-assessed student
> profile. It was removed: the product line does not continue, and the sections below describe only
> what remains. Where a design decision only makes sense against what was there before, the older
> behaviour is named in the past tense rather than deleted.

## 🔬 Theoretical Foundation

Every fitness term is normalised to `[0,1]` and combined by a weighted sum whose weights sum to 1.

1. **Syllabus coverage weighted by importance** — *(weight 0.50)*. Each topic contributes in
   proportion to how important it is, multiplied by how much of it the plan can actually teach,
   modelled as an exponential approach to a mastery ceiling. Where *importance* comes from is a
   choice made per request — see the section on the heaviest term below. It is not attributed to any
   learning theory, and in particular it is *not* an implementation of Ausubel's meaningful learning
   (see below).
2. **The Forgetting Curve (Hermann Ebbinghaus)** — *(weight 0.30)*. Derived directly from
   `R = e^(−t/S)`: recall falls to `e⁻¹` after one stability interval, so a subject needs roughly
   `horizon / τ` sessions to stay above the forgetting threshold until exam day. The fitness scores
   the importance-weighted fraction of the syllabus that gets them.
   **This is a mean-field approximation, not spaced repetition proper** — the macro chromosome has no
   calendar, so the term knows *how many* sessions a subject gets, not *when*.
3. **Cognitive Load Theory (John Sweller)** — *(weight 0.20)*. `PlanningItem.difficultyBand` is the
   intrinsic-load proxy; `sinapse.SinapseLoadBudget` turns availability and mean difficulty into a
   sustainable daily budget, and the fitness penalises plans whose expected daily load exceeds it.
   **This bounds the *expected* daily load, not the load of a single learning episode**, which is
   what Sweller's construct is actually about.

## 🧩 The objective function

The fitness composition is **declared explicitly**, in `sinapse/SinapseFitnessConfig`, and not
assembled by the container from every `FitnessObjective` bean it can find. That is the difference
between a composition someone wrote and one that happens to be whatever is on the classpath — and a
term whose inputs are absent does not fail, it returns its neutral value and keeps reporting a
number.

| Term | Kind | Weight |
|---|---|---|
| `syllabusMastery` (O1) | weighted summand | **0.50** |
| `retention` (O2) | weighted summand | **0.30** |
| `dailyLoadBudget` | weighted summand | **0.20**, switchable |
| | | sum **1.00** |
| `minimum-days` | graded severity, subtracted | λ 0.50 |
| `mandatory-review` | graded severity, subtracted | λ 0.50 |
| `SoftPrerequisiteOrderConstraint` | graded severity, subtracted | λ **0.10** |

With `plan.fitness.sinapse.daily-load-budget=false` the load term leaves and the remaining two
**renormalise automatically** to 0.625 / 0.375, so "no load ceiling" is an executable, comparable
condition rather than a code change.

The soft-prerequisite term is subtracted at **a tenth** of the other two, and the difference is the
point: the contract separates `HARD` from `SOFT` and says a hard edge is a rule while a soft edge is
a preference. A violated preference must cost less than the smallest objective can pay back (0.20)
and far less than a violated requirement (0.50), or the two strengths would differ in name only. See
[Prerequisites](#-prerequisites-hard-orders-soft-is-priced) below.

There are **no multiplicative penalties**. `dropout-risk` and `fatigue-sustainability` existed while
the older path did, read a self-declared psychological state that this contract does not carry, and
were removed with it in EOA-4b — see the next section. Removing them changed no weight: both were
factors applied after the clamp, not summands, so the three objective weights already summed to 1.

### The SINAPSE path does not model fatigue or dropout risk — for lack of data, not for lack of relevance

The removed API required `stressLevel`, `fatigueLevel`, `motivationLevel` and `chronotype`. The
platform collects none of them. Collecting self-declared psychological state bound to an identity is a
decision under Brazil's LGPD — with minors in the secondary-school pilot — and it is **not an
engineering decision to make by filling in a field**. Both terms are therefore absent from the SINAPSE
composition — and, since EOA-4b, no longer exist as code at all. `FitnessBreakdown` lists **only the
active terms**: a disabled term does not appear with a zero, it does not appear. Reporting
`"fatigue-penalty": 1.0` would be true arithmetic and a false statement — it would say the function looked at fatigue and found nothing wrong, when the
function has no fatigue data at all.

### The heaviest term changed its nature, and that is a product fact, not a refactor

`syllabusMastery` carries **0.50 — half the fitness** — and computes `importance x mastery(days)`.
On the removed concurso product, `importance` was the subject's value on the exam
(`questionCount x thematic-axis weight`): external, objective, written in the published syllabus.
**The SINAPSE domain has no such input.** `PlanRequest` carries no question count, no axis weight and
no syllabus, so the substitute changes what the dominant term *means*.

Two strategies are implemented, selected per request via `algorithmParams.importance` and defaulting
to `plan.fitness.sinapse.importance-strategy`:

| id | Source | Nature |
|---|---|---|
| **`goal-priority`** *(default)* | `goals[].priority`, propagated to the subject's topics | **self-declared by the student** |
| `prerequisite-centrality` | how many topics transitively depend on a topic (`HARD` edges) | **objective, structural** |

**The default is in tension with the platform's decision L1** (ADR 0012, *"the student declares
nothing"*), which avoided self-declared input deliberately — and it is now the input to half the
fitness. That tension is not resolved and is not hidden: it is precisely why the second strategy
exists. `prerequisite-centrality` is objective and consistent with L1, and turns a graph that today
only *constrains* the plan into a *signal* about it — but it is a hypothesis, and nothing here yet
measures whether it plans better. Both are implemented so the comparison is possible at all.

Raw scales differ on purpose (1..5 against 1..n); one shared normalisation step puts both on the
unit simplex, so the two conditions produce comparable fitness values. **The strategy that ran is
always echoed in `fitness["importance-strategy"]`** — without it a recorded run cannot be
reconstructed, because nothing would say what `importance` meant in that execution. Details in
[`docs/SINAPSE_ADAPTER.md`](docs/SINAPSE_ADAPTER.md) §5.

### O termo de maior peso mudou de natureza

`syllabusMastery` carrega **0,50 — metade do fitness**. No produto de concurso, que saiu em EOA-4b,
`importance` era o valor da disciplina na prova (`questionCount x peso do eixo`): externo e objetivo, está no edital. O
domínio SINAPSE **não tem esse insumo**, então o substituto muda o que o termo dominante significa.

Duas estratégias, escolhidas por requisição: **`goal-priority`** (padrão) lê `goals[].priority` —
**autodeclarada pelo aluno**, o que contraria a decisão L1 (ADR 0012, "o aluno não informa nada"); e
`prerequisite-centrality`, que deriva da estrutura do currículo — **objetiva**, coerente com L1, e
ainda **hipótese não validada**. A tensão com L1 existe, alimenta metade do fitness, e está
registrada aqui de propósito. A estratégia que rodou é sempre ecoada em
`fitness["importance-strategy"]`.

### The load term survives under a different name, because it is a different term

`cognitiveLoad` becomes **`dailyLoadBudget`** on the SINAPSE path. It lost two of its three inputs —
the self-assessed knowledge gap and the psychological state, both **removed from the formula rather
than defaulted** — so keeping the name, and Sweller's citation with it, would describe a model this
is not. An archived run has to be self-describing a year later.

It is also **re-scaled**: the concurso constants are calibrated for difficulty on 1..5, while
`effortTier` gives 1..4. Four scale defects were found and fixed, the worst of which made the term
bind *more* with a generous calendar than a tight one — the rounding was being measured, not the
load. The details are in [`docs/SINAPSE_ADAPTER.md`](docs/SINAPSE_ADAPTER.md) §2.5.

And it is **measured rather than assumed to matter**. Every plan reports whether the ceiling bound
and by how much (`objective.dailyLoadBudget.binding`, `.excess-ratio`, `.ceiling`). Across a spread
of syllabus difficulty mixes the ceiling binds in **4 of 7 instances (57%)**, with the excess rising
monotonically with difficulty — so the term discriminates rather than decorating. Had the rate been
near zero, the right move would have been to switch it off and take the renormalised weights.

> **Fitness values recorded before EOA-4b are not comparable with values recorded after it.** The day
> floor that feeds `minimum-days` — subtracted at λ 0.50, the heaviest single weight in the aggregate —
> used to be gap-scaled importance normalised by its maximum and capped at 15 days; it is now derived
> from `estimatedMinutes` over the student's measured study day. Two different quantities on two
> different scales ([`docs/SINAPSE_ADAPTER.md`](docs/SINAPSE_ADAPTER.md) §2.6).

### O caminho SINAPSE não modela fadiga nem risco de evasão — por ausência de dado, não por irrelevância

A API removida exigia `stressLevel`, `fatigueLevel`, `motivationLevel` e `chronotype`. A
plataforma não coleta nenhum deles. Coletar estado psicológico autodeclarado vinculado à identidade é
decisão sob a LGPD — com menores no piloto de ensino médio — e **não é decisão de engenharia**. Os
dois termos ficam fora da composição SINAPSE, e o `FitnessBreakdown` lista **apenas os termos
ativos**: termo desativado não aparece com valor zero, ele não aparece. Um termo inerte que aparenta
estar ativo é pior que um termo ausente, porque o ausente é limitação documentada e o inerte é
alegação — e uma que favorece o sistema.

O termo de carga permanece com outro nome — **`dailyLoadBudget`** —, orçamento reconstruído só de
disponibilidade e dificuldade média, com os fatores ausentes **removidos da fórmula em vez de
defaultados**, e constantes re-escaladas de 1..5 para 1..4. A taxa medida de "o teto foi vinculante"
é **4 de 7 instâncias (57%)**, o que o qualifica como termo que discrimina e não como decoração.
Valores de fitness registrados antes de EOA-4b **não são comparáveis** com os de depois: o piso de
dias mínimos passou a vir de outra fonte, em outra escala.

### What this system does **not** do

**Ausubel's meaningful learning is not implemented in the fitness.** The macro chromosome is an
`int[]` of days aligned to a canonical order — a count with no calendar — so precedence between topics
is **still not expressible inside the objective function**, and the genetic search still cannot see
order. What changed is what happens after it: precedence is imposed by the tactical stage and priced
afterwards, which is the v1 design described in the next section. Earlier versions of this README
attributed Ausubel to the knowledge-gap multiplier; that attribution was incorrect and has been
removed. The reasoning, the two options considered and the decision are recorded in
[`docs/revisao-ag/06-decisao-ausubel.md`](docs/revisao-ag/06-decisao-ausubel.md).

## 🔗 Prerequisites: `HARD` orders, `SOFT` is priced

The platform sends `prerequisites[]` with a strength and a provenance, and the two fields are used
for two different things.

**`HARD` is a rule.** It decides which orders exist: `SinapseStudyOrder` sorts topologically over the
hard graph, placement walks that order forward and truncates at the first topic that does not fit —
so the scheduled set is always a *prefix* of a topological order and therefore closed under
prerequisites. A cycle among hard edges is refused with `422`, naming the cycle.
`PlanOutputInvariants` refuses any plan that breaks the property, and
`PrerequisiteOrderingTest` asserts the stronger form over **generated** chains, diamonds and layered
graphs: no session of a dependent begins before *every* session of its hard prerequisites has ended.

**`SOFT` is a preference.** It decides what an order costs, never which orders exist. Every order
stays available, an inversion is repaired when it can be and priced when it cannot:

* `PrerequisiteOrderRepairer` moves a prerequisite earlier, one improving move at a time, keeping
  only moves that stay topological over the hard graph and strictly reduce the inversion count. It
  terminates because that count is a non-negative integer that strictly drops.
* `SoftPrerequisiteOrderConstraint` prices whatever remains, as
  `Σ gravity / |preferences|`, where gravity is the inversion's distance as a fraction of the plan's
  span — 1 when the dependent is scheduled and its prerequisite is nowhere in the plan, 0 when the
  dependent is not scheduled at all. The formula is on the class; each of its cases is a test.

> **The soft term does not steer the search, and that is the design rather than a gap.** During
> evolution the fitness sees the macro chromosome, which has no calendar and therefore cannot
> violate an ordering preference. The term only bites on the *placed* plan. A v1 whose search
> already saw order would not be a control group for the timeline-chromosome v2 — it would be a
> worse v2.

**The greedy baseline does not repair.** It orders by goal pressure and curricular position and
states no opinion about preferences, which is exactly the control it exists to be; teaching it the
genetic path's repair would leave the two engines differing by one thing less and the comparison
between them measuring two changes at once. It reports the inversions it produced instead, and
omits the `-before-repair` key because no repair ran.

### Provenance: three conditions, one key

`EdgeProvenance` is on the wire so that the ablation is possible without adding instrumentation
later (ADR 0006). `plan.prerequisites.provenance`, overridable per request with
`algorithmParams.provenance`, selects which edges a run may see:

| id | Edges admitted |
|---|---|
| `curated` | `CURATED` |
| `curated-textbook` | `CURATED` + `TEXTBOOK_ORDER` |
| `all` *(default)* | every edge, `DERIVED` included |

The conditions are **cumulative on purpose**, so a difference between two of them is attributable to
the edges the wider one added. The filter applies to *every* consumer of the graph — both engines
and the `prerequisite-centrality` importance strategy — because a condition in which the scheduler
ignores a derived edge while the importance term still counts it is not one condition, it is two. An
unknown id is refused with `422` rather than falling back, and the condition that ran is echoed in
`fitness["prerequisite-provenance"]`: a stored plan that cannot name its arm is unattributable.

### What a partial plan now says

Not enough availability still produces a *declared* partial plan, and it now names the casualties:
`topics-unscheduled-ids` lists the topics the calendar could not hold. They are exactly the tail of
the study order, because the scheduled set is a prefix of it. A count says a partial plan happened;
the names say to whom.

## 🚀 Features

* **Evolutionary Optimization:** Uses a Genetic Algorithm built from scratch (with Tournament Selection, Creep Mutation, and Hybrid Crossover) to find the best allocation of effort over months of study.
* **Dynamic Scheduling:** Converts the macro allocation into concrete sessions placed in the student's declared availability windows, prerequisites first and forward only.
* **Self-Calibrating:** The caller inputs no technical metric — no generation count, no population size, no daily effort ceiling. `SinapseLoadBudget` derives the sustainable daily limit from the declared availability and the mean difficulty of the topics in scope.
* **Resilient & Stateless Architecture:** Designed for the cloud. It keeps no state, so a fresh snapshot produces a fully re-optimized plan ("re-planning from scratch"), ensuring total adaptability over time.

## 💻 Tech Stack

* **Language:** Java 21
* **Framework:** Spring Boot 3.5.x
* **Security & Validation:** Spring Security (stateless config), Spring Boot Validation (Jakarta Bean Validation API 3.1)
* **API Documentation:** Springdoc OpenAPI (Swagger UI)
* **Observability:** Spring Boot Actuator, Micrometer (Prometheus, Brave Tracing)
* **Logging:** SLF4J, Logback with Logstash Logback Encoder (Structured JSON logging)
* **Testing:** JUnit 5, Mockito, Spring Boot Test
* **Build Tool:** Maven

## 🏗️ Architecture

This project was built prioritizing a clean and extensible design:

* **Architectural Style:** Stateless REST API with one endpoint, `POST /plans`, behind two interchangeable engines.
* **Domain-Driven Design (DDD):** Pure domain models isolated from framework rules. Entities and Value Objects handle business logic.
* **Design Patterns:**
  * **Strategy:** Genetic operators, and the per-request choice of engine and of importance source.
  * **Factory:** Decouples object creation (e.g., GeneticAlgorithmFactory, StudyPlanFactory).
  * **Dependency Injection (IoC):** Spring wires the engines and the fitness composition.
* **Synchronous by design:** `POST /plans` answers on the request thread. The asynchronous job flow — a thread pool, a bounded queue, a job store and a `GET` to poll the result — belonged to the removed API and went with it.

## 📂 Project Structure

The directory organization reflects a clear separation of responsibilities:

```text
src/main/java/com/ia/project/dynamicstudyplanner/
├── api/exception/                   # Error-handling advices and the RFC 7807 body builder
├── baseline/                        # The greedy baseline engine — the experiment's control condition
├── config/                          # Global config (Security, OpenAPI, logging)
├── coreapi/contract/                # Mirror of the platform's Core contract: records and enums only
├── domain/                          # Pure domain models (entities, value objects, domain exceptions)
├── ga/                              # Genetic algorithm engine (factories, strategies, context, individual, population)
├── plan/                            # POST /plans: the protocol, the guard, the engine selector, the output invariants
├── service/calculation/retention/   # The spaced-repetition recurrence the fitness and the placement read
├── sinapse/                         # The adapter: PlanRequest in, chromosome out, PlanResponse back
└── util/                            # RandomProvider — the single seedable source of randomness
```

## ✅ Requirements

* **Java:** Version 21
* **Maven:** 3.8+ (or use the provided Maven Wrapper `./mvnw`)

## ⚙️ Environment Variables

The application can be configured via `application.properties` or environment variables:

| Variable / Property | Default Value | Description |
|----------------------|---------------|-------------|
| `spring.profiles.active` | `dev` | Active Spring profile. `baseline-core` must be among them for `POST /plans` to exist. |
| `plan.engine.default` | `greedy-baseline` | Engine used when `algorithmParams.engine` is absent. |
| `plan.engine.ga.generations` | `60` | Generations the genetic engine runs. |
| `plan.engine.ga.population-size` | `40` | Population size of the genetic engine. |
| `plan.fitness.sinapse.daily-load-budget` | `true` | Whether the load ceiling is part of the fitness. |
| `plan.fitness.sinapse.importance-strategy` | `goal-priority` | Default source of `importance`. |
| `plan.prerequisites.provenance` | `all` | Which prerequisite edges a run may see: `curated`, `curated-textbook` or `all`. |

The rate-limit and thread-pool keys are gone: the Bucket4j filter priced a request by its exam
syllabus and its `gaConfig`, and the executor served the asynchronous job flow. Both belonged to the
removed API. `POST /plans` was never covered by the rate limiter, so nothing that protected it was
lost — but it is also **not** rate limited, which the private-network requirement below assumes.

#### Deployment-dependent security settings

These three describe **one single deployment assumption** and must be changed together. The defaults
are the safe ones — they assume no reverse proxy in front of the application.

| Variable / Property | Default Value | Description |
|----------------------|---------------|-------------|
| `server.forward-headers-strategy` | `none` | Whether Spring trusts `X-Forwarded-*`. **Do not set to `framework` without a real proxy in front** — see below. |
| `api.security.require-https` | `false` | Requires HTTPS and emits HSTS. Depends on `X-Forwarded-Proto`, so only meaningful behind a known proxy. |
| `api.security.hsts-max-age-seconds` | `31536000` | HSTS max-age, used only when the above is on. |

> **Why `server.forward-headers-strategy` defaults to `none`.** With `framework`, Spring registers
> `ForwardedHeaderFilter`, which rewrites the request's scheme and address from client-supplied
> `X-Forwarded-*` headers **before any application filter runs**. With no proxy in front, that lets
> the caller declare its own request was HTTPS — which is the very decision
> `api.security.require-https` makes. Switch to `framework` **only** together with a real proxy in
> front.
>
> A fourth key, `api.trusted-proxies`, sat here until EOA-4b. It was read by the client-address
> resolver that keyed the rate-limit buckets — finding S12 in
> `docs/qualidade/02b-correcao-seguranca.md` — and both went with the path they protected.
>
> TLS termination itself is infrastructure's responsibility and cannot be done by this application.
> `application.properties` carries the same warning next to each key.

## 🛠️ Installation & Running the Project

### Local Execution (Maven)

1. Clone the repository:
   ```bash
   git clone https://github.com/gustavo-rm/exam-optimizer-application.git
   cd exam-optimizer-application
   ```

2. Build the project:
   ```bash
   ./mvnw clean install
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

   Alternatively, you can run the generated `.jar` file:
   ```bash
   java -jar target/DynamicStudyPlanner-2.0.1.jar
   ```

The application will start on port `8080` by default.

## 📚 API Documentation

Once the application is running, you can access the Swagger UI to interact with the API:

* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **OpenAPI Docs:** `http://localhost:8080/v3/api-docs`

### Main Endpoint

**`POST /plans`** — and it is the only one.

It is **deliberately absent from the published OpenAPI document**, so Swagger UI shows no path. It is
not this service's public API: it is the protocol `sinapse-platform` speaks to it, versioned by
`PlanRequest.VERSION` and pinned by the reference documents in `src/test/resources/contract/`.
Publishing it would put a second, weaker description of the same contract in circulation. See
[Core contract (v1.0)](#-core-contract-v10) below and `docs/CORE_CONTRACT_SURVEY.md`.

**Request body:** a `PlanRequest` — horizon, availability windows, goals, topics, prerequisite edges,
study history, `algorithmParams` and `randomSeed`.

**Response:** a `PlanResponse` — the scheduled sessions, the fitness breakdown of the terms that
actually ran, and execution metadata.

**Error Handling:**
Every error is returned as a standardized **RFC 7807 Problem Detail**. Handling is split across three
ordered `@RestControllerAdvice` classes, separated by the *nature of the cause* rather than by status
code:

| Advice | Covers | Typical statuses |
|---|---|---|
| `RequestErrorAdvice` | The request itself is not acceptable: malformed JSON, wrong type, unknown route, unsupported verb, failed validation | `400`, `404`, `405`, `415` |
| `BusinessRuleErrorAdvice` | The request is well-formed, but the domain cannot fulfil it | `422` |
| `InfrastructureErrorAdvice` | Security and the `500` safety net | `401`, `403`, `500` |

`PlanController` declares two handlers of its own, which win over the advices: a refusal it
understands but cannot plan is `422` with the offenders named, and a plan that breaks its own output
invariants is `500`. Both bodies are built by the same `ProblemDetails`, so there is one error
contract in the service and not two.

The distinction between `400` and `422` follows RFC 9110: a `400` is fixed by changing *how* you send
the request, a `422` by changing *what* you are asking for. The criterion used to classify each check
is recorded in [ADR-0005](./docs/adr/0005-criterio-de-classificacao-de-erro.md).

## 🗄️ Database

This application is **completely stateless** and does not use a database. It processes the input payload, runs the optimization algorithms in memory, and returns the result in real-time. This design choice maximizes horizontal scalability and simplifies deployment.

## 🧪 Testing

The project uses JUnit 5, Mockito and AssertJ.

```bash
./mvnw test      # test suite only
./mvnw verify    # style check + tests + coverage floor — what CI runs
```

`verify` is the meaningful command: it runs **Checkstyle** (`config/checkstyle/checkstyle.xml`) in
the `validate` phase, then the suite, then the **JaCoCo coverage floor**. `test` alone runs neither
gate.

The floor is a ratchet set flush against a past measurement — **0.9325 instructions and 0.7861
branches** — so losing a single covered instruction fails the build. Adding covered code does *not*
raise it automatically: read the new numbers from `target/site/jacoco/jacoco.csv` and bump both
values in `pom.xml`, where the reasoning is documented alongside the rule.

Continuous integration runs `verify` on every push and pull request
(`.github/workflows/ci.yml`). Note that the workflow reports but does not yet *block* merges — that
requires marking the job as a required status check in the branch protection settings.

## 🔒 Security

* **Stateless:** The API is stateless and does not maintain sessions.
* **Public Access:** `POST /plans` is `permitAll()` on a filter chain of its own, and so is Swagger UI. `/api/v1/**` is still `permitAll()` although nothing handles it since EOA-4b — the rule is kept so that removing it stays a deliberate posture change rather than a side effect of this removal. CSRF is disabled.
* **No authentication anywhere.** Because nothing authenticates `/plans`, **this service must run on a private network, behind the platform** — see [Core scheduler](#-core-scheduler-two-engines-behind-post-plans-baseline-core-profile) for the requirement in both languages.
* **No rate limiting.** The Bucket4j filter priced a request by its exam syllabus and its `gaConfig` and guarded only `/api/v1/optimizer/**`; it left with that path in EOA-4b. `/plans` was never behind it, so no protection was lost — and none exists. Add one before this service is reachable by anything but the platform.
* **Input Validation:** `PlanRequestGuard` and `HorizonBounds` refuse, before any search runs, a request whose horizon, availability, topics or prerequisite graph cannot produce a plan — with `422` naming what it tripped over. `EffortTierBands` refuses a tier outside the contract's closed set.

## ⚡ Performance and Scalability

* **Synchronous processing:** `POST /plans` runs the search on the request thread and answers on it. The dedicated `@Async` thread pool, its bounded queue and the 30-second request timeout served the removed asynchronous job flow and went with it — **the CPU cost of a search now lands on a Tomcat worker**, which the search budget (`plan.engine.ga.*`) has to be sized against.
* **Statelessness:** The absence of a database or session state allows the application to be horizontally scaled indefinitely. Since EOA-4b there is no shared state at all between replicas — the Redis-backed job store and rate-limit buckets left with the path that used them.
* **Caching:** High-frequency access patterns within the Genetic Algorithm (e.g., retrieving fittest individuals) are cached internally to avoid redundant computations.

## 📊 Logging and Monitoring

* **Observability:** Exposes `/actuator/health` and `/actuator/prometheus` endpoints for monitoring.
* **Metrics:** Uses Micrometer for HTTP and JVM metrics. **There is no timer on the search itself** — `dynamicstudyplanner.optimization.duration` instrumented the removed service and has no replacement, so "is the engine slow?" is currently answerable only through overall request latency.
* **Distributed Tracing:** Micrometer Tracing (Brave bridge) is integrated. With the asynchronous flow gone, a request runs on one thread and the trace context needs no propagation across a pool.
* **Structured Logging:** Configured to output JSON logs via Logback (`logstash-logback-encoder`), making it enterprise-ready for ingestion by ELK stacks or Datadog.

## 🔮 Future Improvements

* Containerize the application with Docker and provide a `docker-compose.yml`.
* Implement CI/CD pipelines (e.g., GitHub Actions) for automated testing and building.
* Add integration with an external monitoring stack (Grafana dashboards for Prometheus metrics).

## 🤝 Contributing

Contributions are welcome! If you would like to contribute:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

## 👨‍💻 Author

Gustavo Malacarne (Software Engineer) - dynamic-study-planner
* Software Engineer focused on building scalable applications, backend architectures and AI-driven solutions. 
* Master’s degree in Computer Science with specialization in Artificial Intelligence, Machine Learning and software engineering practices. 
* Passionate about technology, research and developing innovative systems that combine performance, maintainability and intelligent decision-making.

## 🧹 Code Quality

* **Linting and formatting:** Checkstyle, configured in `config/checkstyle/checkstyle.xml` with the
  conventions the codebase already follows (4-space indent, 120-column lines, no star imports, no
  brace-less `if`). It runs in the `validate` phase, so a style violation fails in seconds rather
  than at the end of the build. `.editorconfig` mirrors the same rules for editors.
* **Coverage floor:** JaCoCo `check` fails the build when coverage regresses. The floors sit at
  0.9325 / 0.7861, so any drop is caught — see `pom.xml` for how to move them.
* **Architectural boundaries:** enforced by test, not by convention — `arquitetura/ModuleBoundaryTest`
  fails on a dependency cycle between top-level modules and on any framework import inside `domain`.
* **API contract:** `contract/OpenApiContractTest` compares the generated OpenAPI spec against a
  committed snapshot, so the published contract cannot drift unnoticed. Since EOA-4b that snapshot
  publishes **zero paths** — `/plans` is `@Hidden` on purpose — which still catches an endpoint
  appearing by accident. The Core contract itself is pinned by `coreapi/CoreContractGoldenTest`
  against the reference documents, not by this snapshot.
* **Static analysis:** not configured. PMD and SonarQube were run manually during the quality review
  (`docs/qualidade/04-diagnostico-escrita.md`) but are not part of the build.
* **Architectural decisions:** recorded as ADRs in [`docs/adr/`](./docs/adr/).

## 🔗 Core contract (v1.0)

This service speaks the `sinapse-platform` Core contract: the platform sends a `PlanRequest` to
`POST /plans` and reads back a `PlanResponse`.

**The contract is duplicated on purpose.** There is no shared Maven module and none is planned.
Each repository keeps its own copy of the records — here, `coreapi/contract/` — and both validate
their serialisation against reference documents that are byte-for-byte identical on the two sides:

| File | Role |
|---|---|
| `src/test/resources/contract/plan-request-v1.0.json` | Reference document of the request |
| `src/test/resources/contract/plan-response-v1.0.json` | Reference document of the response |
| `docs/CORE_CONTRACT_SURVEY.md` | Component-by-component transcription of the platform's records |

**The reference documents are the source of truth**, not either side's Java. They are what
`coreapi/CoreContractGoldenTest` pins the wire shape against, and the platform runs a twin test
(`br.com.sinapse.platform.coreclient.CoreContractGoldenTest`) reading the same two files. The trade
is deliberate and worth stating: publishing an artifact would make the compiler catch a divergence,
and without one the build catches it instead — one test run later, but with nothing to version,
publish and keep in step across two release cycles.

**Changing the shape requires a version bump and both repositories in the same logical PR.** Adding,
removing, renaming or retyping any component means: bump `PlanRequest.VERSION`, update both
reference documents, and land the change on both sides together. The platform compares the version
it receives against its own and refuses a mismatch, so a one-sided change does not degrade quietly —
it stops working.

Two details that look like tidying and are not:

* **`Topic.effortTier` is a `String`, never an enum.** The platform sends `effortTier().name()` and
  declares no `EffortTier` type in its contract package. An enum here would make this service reject,
  at deserialisation, a band the platform considers valid — and the platform would report that as
  `CORE_UNAVAILABLE`, pointing at the network instead of at the contract. Validating the closed set
  (`SHORT`, `STANDARD`, `LONG`, `EXTENDED`) belongs to the adapter.
* **Null fields are omitted, and that is pinned per record.** The platform sets
  `spring.jackson.default-property-inclusion: non_null` globally; this service sets no
  `spring.jackson` key at all, so Jackson's `ALWAYS` default would write `"lastStudiedAt": null`
  where the reference document omits the key. Each record in `coreapi/contract/` therefore carries
  `@JsonInclude(NON_NULL)` — the local equivalent of that one configuration line, kept per record
  rather than set globally so that it travels with the contract types instead of with this
  application's configuration. The platform's records carry no such annotation, so a literal
  mirroring would delete it; the golden test fails naming the exact field if anyone does.
* **`coreapi/contract/` holds records and enums only.** It depends on nothing else in this codebase
  and nothing else depends on it, which is what lets it stay a faithful mirror rather than drifting
  into the local domain model. Mapping to and from `PlanningItem`, `StudyPlan` and the rest is the
  adapter's job (`sinapse/`).

## 🧭 Core scheduler: two engines behind `POST /plans` (`baseline-core` profile)

`POST /plans` has **two implementations behind one interface** (`plan.PlanEngine`), which are the two
conditions of the experiment:

| `algorithmParams.engine` | Engine | Stage | Notes |
|---|---|---|---|
| `greedy-baseline` | `baseline.GreedyBaselineEngine` | EOA-2 | deterministic greedy pass; the default |
| `ga` | `sinapse.GeneticPlanEngine` | EOA-5 | the genetic algorithm on the SINAPSE contract |

The engine is chosen **per request**, from `algorithmParams` — a `Map<String, Object>` in the
contract, so nothing was invented to carry it. Without the key, `plan.engine.default` decides. Per
request rather than per deployment because two conditions that need two deploys cannot be compared
without also comparing the deploys. An unknown engine is refused with `422` listing the ones that
exist, never silently replaced by the default.

**The answer always declares which engine ran**, under `fitness.engine`. The selector stamps it after
the engine returns, so an engine can neither mislabel its own output nor forget to label it.

Both engines satisfy the same output invariants — the eight `RestSinapseCore.validated` applies, the
four it does not (horizon, availability window, non-overlap, known `topicId`) and contiguous
`sequenceIndex` — enforced by one test parameterised over every registered engine
(`PlanEngineInvariantTest`), so a new engine inherits the whole suite.

The greedy engine is described in [`docs/BASELINE_CORE.md`](./docs/BASELINE_CORE.md); the genetic
engine's adapter, its project assumptions and the parameters still to be calibrated are in
[`docs/SINAPSE_ADAPTER.md`](./docs/SINAPSE_ADAPTER.md).

**The baseline is the experimental baseline, not a mock.** A claim that the genetic algorithm adds
value only means something against a simple scheduler that already respects the same constraints. It
stays here now that the genetic algorithm is wired to this contract.

> One property worth knowing before designing the comparison: when availability barely covers the
> syllabus, the two engines **necessarily agree** — there is a single feasible allocation and the
> chromosome has no degree of freedom. The difference between the conditions only appears where there
> is slack to distribute (`docs/SINAPSE_ADAPTER.md` §3.8).

* It is active **only** under the Spring profile `baseline-core`. Without the profile no bean of the
  module is created and `POST /plans` answers `404` — which, since EOA-4b removed the other path,
  means **the application then serves no endpoint at all** beyond the actuator. The gate is kept as
  it is because removing it is a deployment decision, not part of a removal; whoever runs this must
  set the profile.
* Determinism is a requirement: the same `PlanRequest` produces the same plan byte for byte, on any
  thread. No unordered collection reaches the output, no wall-clock reading is taken on the decision
  path, and `randomSeed` is echoed rather than consumed.
* Requests it understands and cannot plan are refused with `422`, naming what they tripped over —
  including a cycle in the `HARD` edges, which it reports and never breaks.
* Not enough availability produces a **declared partial plan** — a prefix of the study order, so the
  scheduled set stays closed under prerequisites — with `topics-scheduled`, `topics-unscheduled` and
  `partial` in `fitness`.

### ⚠️ Deployment: private network only / Implantação: somente rede privada

**EN —** `/plans` is `permitAll`, and so is the `/api/v1/**` prefix that no longer has a handler.
**There is no authentication of any kind in front of it**, neither in this repository nor delegated
to an external component.
Anything that can reach this service can post a study snapshot to it — the student's history, goals
and availability — and read a plan back. **This service must therefore not be reachable from the
internet. It belongs on a private network, behind the `sinapse-platform`, which is the only party
meant to call it.** Apart from being public, `/plans` is held to no different standard than the rest
of the service: the same absence of authentication, CSRF disabled as for the rest of this stateless
API, and the same `api.security.require-https` posture, so a declared TLS proxy in front refuses
cleartext and emits HSTS on `/plans` exactly as it does elsewhere.

**PT —** `/plans` é `permitAll`, e o prefixo `/api/v1/**` — que desde EOA-4b não tem manipulador
nenhum — também é. **Não existe autenticação de nenhum tipo na frente dele**, nem neste repositório
nem delegada a um componente externo.
Qualquer um que alcance este serviço consegue enviar a ele um retrato de estudo — histórico, metas e
disponibilidade do estudante — e ler um plano de volta. **Portanto este serviço não pode ficar
alcançável pela internet. Ele tem de viver em rede privada, atrás da `sinapse-platform`, que é a
única parte que deveria chamá-lo.** Fora o acesso público, `/plans` não fica sujeito a um padrão
diferente do resto do serviço: a mesma ausência de autenticação, CSRF desligado como no restante
desta API sem estado, e a mesma postura de `api.security.require-https` — com proxy TLS declarado na
frente, a requisição em claro é recusada e o HSTS é emitido em `/plans` como em qualquer outro
caminho.

## 🚀 Deployment

Currently, the application runs via Maven wrapper locally. For production deployment:
1. Package the application: `./mvnw clean package`
2. Run the generated artifact: `java -jar target/DynamicStudyPlanner-2.0.1.jar`

(Future state: Dockerfile generation to deploy to container orchestrators like Kubernetes or AWS ECS).
