# CONTEXT — exam-optimizer-application (the SINAPSE "Core")

Stable entry point for an agent. Read this, then [STATE.md](./STATE.md). Status labels follow the
protocol in [CLAUDE.md](../../CLAUDE.md#ai-context-protocol). Baseline of every label below:
`origin/main` at `9104c1b`.

## Purpose

- SINAPSE is a study-planning product. It has two repositories: `sinapse-platform` (the platform's
  Spring Boot backend) and this one, the "Core": the optimiser the platform calls over HTTP.
  DECISION(stated by project owner)
- They are two separate repositories, not a monorepo; the contract is duplicated on purpose, with no
  shared Maven module. DECISION(stated by project owner); READ(README.md:513-524@9104c1b)
- This repository exists to serve SINAPSE. The exam ("concurso") product does not continue as a line
  of its own; its code path was removed in EOA-4b. DECISION(stated by project owner);
  READ(commit 3214e2d, CLAUDE.md §1b@9104c1b)

## Architecture

Do not re-derive it: [CLAUDE.md §1b](../../CLAUDE.md) explains the service after EOA-4b,
[docs/CODE_MAP.md](../CODE_MAP.md) maps the code, and the README sections
"Project Structure" and "Core scheduler" describe the packages and the engines. In short:

- One endpoint, `POST /plans`, existing only under the Spring profile `baseline-core`.
  READ(plan/PlanProtocol.java:20,30@9104c1b)
- Flow: `plan/PlanController` → `plan/PlanEngineSelector` picks an engine from
  `algorithmParams.engine` (default `plan.engine.default`) → the engine plans and runs
  `PlanOutputInvariants.check` → the selector stamps `fitness.engine` → `PlanResponse`.
  READ(PlanEngineSelector.java:98,129@9104c1b; README.md:576-577@9104c1b)
- Three engines, all synchronous on the request thread:

  | id | class | doc |
  |---|---|---|
  | `greedy-baseline` (default) | `baseline/GreedyBaselineEngine` | [docs/BASELINE_CORE.md](../BASELINE_CORE.md) |
  | `ga` | `sinapse/GeneticPlanEngine` | [docs/SINAPSE_ADAPTER.md](../SINAPSE_ADAPTER.md) |
  | `ga-timeline` | `sinapse/TimelinePlanEngine` | [docs/revisao-ag/10-medicao-v2-linha-do-tempo.md](../revisao-ag/10-medicao-v2-linha-do-tempo.md) |

  READ(README.md:563-569@9104c1b)
- Top-level packages under `src/main/java/com/ia/project/dynamicstudyplanner/`: `api`, `baseline`,
  `config`, `coreapi`, `domain`, `ga`, `plan`, `service`, `sinapse`, `util`. Roles: README
  "Project Structure". `coreapi/contract` is the contract mirror (see [INTEGRATION.md](./INTEGRATION.md)).
  READ(ls @9104c1b)
- No database, no authentication, no rate limiting. READ(README.md:421,445-451@9104c1b;
  config/SecurityConfig.java, baseline/BaselinePlanSecurityConfig.java:88@9104c1b)
- Stateless between requests: no static mutable collection in `src/main`; the only per-thread state
  is `RandomProvider`'s `ThreadLocal<Random>`, which the GA engines reseed from `randomSeed` on each
  request and restore afterwards. READ(grep over src/main; RandomProvider.java:97, GeneticPlanEngine.java:131-135@9104c1b). Scaling by replicas behind a
  load balancer: UNVERIFIED(planner, 2026-10-08) — nothing here deploys it.

## Stack and commands

| Item | Value | Status |
|---|---|---|
| Java | 21 | READ(pom.xml:30@9104c1b) |
| Spring Boot parent | 3.5.16 | READ(pom.xml:8@9104c1b) |
| Artifact / version | `com.ia.project:DynamicStudyPlanner:2.0.1` | READ(pom.xml:11-13@9104c1b) |
| Build | Maven wrapper `./mvnw` | READ(repo root@9104c1b) |
| Gate (= CI) | `./mvnw verify`: Checkstyle at `validate`, tests, JaCoCo check | READ(.github/workflows/ci.yml:59@9104c1b) |
| Tests only | `./mvnw test` (runs neither gate) | READ(README.md:423-436@9104c1b) |
| Profile for `/plans` | `baseline-core` (default active profile is `dev`, which serves no endpoint) | READ(application.properties:2@9104c1b) |
| Port | 8080 (Spring default; no `server.port` key) | READ(README.md:374@9104c1b) |
| GA budget | `plan.engine.ga.generations=60`, `population-size=40` | READ(application-baseline-core.properties:59-60@9104c1b) |

Quality gates — values read from the build files, not from memory. They never go down and are never
suppressed (CLAUDE.md §1, "The prohibition"). DECISION(stated by project owner)

| Gate | Value | Status |
|---|---|---|
| JaCoCo `INSTRUCTION` COVEREDRATIO min | 0.9325 | READ(pom.xml:304-306@9104c1b) |
| JaCoCo `BRANCH` COVEREDRATIO min | 0.7861 | READ(pom.xml:328-330@9104c1b) |
| Checkstyle `CyclomaticComplexity` max | 10 | READ(config/checkstyle/checkstyle.xml:72-73@9104c1b) |
| Checkstyle `MethodLength` / `LineLength` / `ParameterNumber` / `NestedIfDepth` | 80 / 120 / 8 / 3 | READ(checkstyle.xml:34,76,80,83@9104c1b) |
| Checkstyle severity, test sources included | `error`, `true` | READ(pom.xml:194-195@9104c1b) |

## Repository map

| Path | What an agent needs to know |
|---|---|
| `src/main/java/.../plan/` | endpoint, selector, guards, output invariants, request-condition parsers |
| `src/main/java/.../coreapi/contract/` | contract mirror — records and enums only (CLAUDE.md §2) |
| `src/main/java/.../sinapse/`, `ga/`, `baseline/` | the two genetic engines + GA core; the greedy engine |
| `src/main/resources/application-baseline-core.properties` | engine default, GA budget, fitness and prerequisite keys |
| `src/test/resources/contract/` | reference contract JSON — never edited |
| `src/test/resources/instances/` | three synthetic real-catalog requests + `reproducers/` (see INTEGRATION.md) |
| `benchmarks/` | measurement harness, test source root (CLAUDE.md §5b, [benchmarks/README.md](../../benchmarks/README.md)) |
| `docs/adr/` | ADRs, Portuguese, `NNNN-slug.md`, indexed in [docs/adr/README.md](../adr/README.md) |
| `docs/revisao-ag/`, `docs/qualidade/` | GA review and quality sweep reports, each with a README index |
| `docs/DIAGNOSTICO_ESCALA_REAL.md` (+ `.csv`, `docs/diagnostico-escala-real/`) | EOA-10 real-scale diagnosis |
| `docs/ai/` | this layer |

Root-level `*_ARCHITECTURE.md`, `*_AUDIT.md` and `*_EVALUATION.md` files were not reviewed for this
layer; do not treat them as current. UNVERIFIED(not read, 2026-10-08)

## Conventions

- Language: code identifiers, API messages, log keys, metric names in English; Javadoc, comments,
  test names and `docs/` (team documents) in Portuguese. DECISION([ADR-0007](../adr/0007-idioma-do-codigo-e-da-documentacao.md))
- Commits, PRs and `docs/ai/` in English. DECISION(stated by project owner, 2026-10-08). This
  conflicts with the commit row of CLAUDE.md §6; see STATE.md K1.
- Conventional Commits `type(scope): subject`, body explains *why*. READ(CLAUDE.md §7@9104c1b)
- Branch name `type/version/description`, e.g. `docs/1.0/ai-context-layer`. READ(CLAUDE.md §7@9104c1b)
- PR body written by hand and checked against `git diff --stat`. DECISION(stated by project owner)
- Before pushing: `./mvnw verify` green locally. READ(CLAUDE.md §1@9104c1b)

## The other repository

`sinapse-platform` — https://github.com/gustavo-rm/sinapse-platform — the consumer of `POST /plans`.
This repository offers it the v1.0 contract and expects nothing that is verified (see
[INTEGRATION.md](./INTEGRATION.md)). URL READ(docs/DIAGNOSTICO_ESCALA_REAL.md:53@9104c1b).

## Where to find what

| Question | File |
|---|---|
| What is being worked on, what is broken, what is undecided? | [STATE.md](./STATE.md) |
| What does the platform get from `/plans`, and how does the contract change? | [INTEGRATION.md](./INTEGRATION.md) |
| What has been asked of / by the other repository? | [HANDOFF.md](./HANDOFF.md) |
| Rules for build, determinism, untouchable classes, measurement | [CLAUDE.md](../../CLAUDE.md) §1–§7 |
| Accepted `algorithmParams` keys and HTTP answers | [README.md](../../README.md) "Choosing the engine per request" |
| Why a past architectural choice was made | [docs/adr/](../adr/README.md) |
| GA review history and measured results | [docs/revisao-ag/README.md](../revisao-ag/README.md) |
| Why the GA fails on the real catalogue | [docs/DIAGNOSTICO_ESCALA_REAL.md](../DIAGNOSTICO_ESCALA_REAL.md) §1 |
| Contract component-by-component | [docs/CORE_CONTRACT_SURVEY.md](../CORE_CONTRACT_SURVEY.md) |

## Files that are never edited

The single list; each row names where the rule comes from.

| Path | Rule | Source |
|---|---|---|
| `src/test/resources/contract/*.json` | never edited, not even to make a test pass; byte-identical to the platform's copies, and a failure there means the repositories diverged | CLAUDE.md §2 |
| `src/test/resources/instances/*.json` and its `README.md` | byte-for-byte copies of `sinapse-platform@cbb5529`, SHA-256 listed in that README and in `docs/DIAGNOSTICO_ESCALA_REAL.md` §2 (EOA-10); updated only by a new copy whose SHA-256 is checked, announced by the platform | HANDOFF.md EOA-H2 |
| `src/test/resources/instances/reproducers/` | the EOA-10 `ddmin` reproducers; not edited | INTEGRATION.md "Instances shared with the platform" |
| `ga/strategy/mutation/CreepMutation`, `ga/strategy/mutation/TransferMutation`, `ga/strategy/crossover/WeightedAverageCrossover`, `ga/strategy/selection/TournamentSelection`, `ga/Population`, the evolution loop in `ga/GeneticAlgorithm` | not touched without an explicit instruction | CLAUDE.md §4 |
| `GaResultadoInalteradoTest` | never weakened (seed, assertion, comparison); a divergence is a finding | CLAUDE.md §5 |

## Non-negotiables

- Never lower a coverage floor, relax a Checkstyle rule or add `@SuppressWarnings` (CLAUDE.md §1).
- No randomness outside `util/RandomProvider`; none at all in `ga/fitness` (CLAUDE.md §3).
- The files in "Files that are never edited" above.
- Diagnose before fixing: no engine change without a verified diagnosis. DECISION(stated by project owner)
- No secrets, tokens, student data or personal data in docs or examples; instances are synthetic.
- Never record a proposal as a decision.

## Decisions recorded here

`docs/adr/` exists, so new decisions go there as ADRs in its format. The DECISION lines above
(two repositories, concurso line discontinued, gates never lowered, diagnose before fixing, English
for commits/PRs/agent docs) were stated by the project owner and have no ADR yet; see STATE.md D-AI1.
