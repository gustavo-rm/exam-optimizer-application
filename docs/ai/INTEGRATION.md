# INTEGRATION — the Core as seen from exam-optimizer-application

This repository is the **provider** of the interface; `sinapse-platform`
(https://github.com/gustavo-rm/sinapse-platform) is the consumer. This file says what is provided
and how it is protected. It does not restate the README: where the README already documents a
detail, the row points to it. Labels are against `origin/main` at `9104c1b`.

## Provided interface

| Item | Value | Status |
|---|---|---|
| Endpoint | `POST /plans`, only under Spring profile `baseline-core` | READ(plan/PlanProtocol.java:20,30@9104c1b) |
| Contract version | `1.0` (`PlanRequest.VERSION`) | READ(coreapi/contract/PlanRequest.java:78@9104c1b) |
| Request / response types | `PlanRequest` / `PlanResponse` records in `coreapi/contract/`, duplicated in the platform | READ(README.md:513-519@9104c1b) |
| Reference JSON | `src/test/resources/contract/plan-request-v1.0.json`, `plan-response-v1.0.json` | READ(@9104c1b) |
| Not in OpenAPI | `/plans` is hidden from the published OpenAPI document on purpose | READ(README.md:385-391@9104c1b) |
| Security | none: `permitAll`, no auth, no rate limit; private network only | READ(baseline/BaselinePlanSecurityConfig.java:88@9104c1b; README.md "Deployment: private network only") |

Rules for the contract package and the reference JSON are in [CLAUDE.md §2](../../CLAUDE.md) and
README "Core contract (v1.0)"; they are not repeated here.

**How the byte-for-byte identity with the platform is protected.**

- Records ↔ reference JSON on this side: `coreapi/CoreContractGoldenTest` (round trip, union of keys,
  component sweep). OBSERVED(9104c1b, `./mvnw test -Dtest=CoreContractGoldenTest` → 8 tests, 0 failures)
- Reference JSON here ↔ reference JSON in the platform: **process only**. No test in this repository
  reads the platform's copy. The platform's side of the protection is in its own
  `docs/ai/INTEGRATION.md` "Reference contract JSON" (READ at other-repo@1006475).

## algorithmParams — index

`algorithmParams` is an open `Map<String, Object>` in v1.0, so a key is not a contract change.
Accepted values, defaults, error answers and whether the response carries the effective value, for
all four keys: README "Choosing the engine per request", subsection "The closed set: the four keys
the Core applies". OBSERVED(EOA-13 branch, executed over HTTP for each engine). `importance` and
`precedence` do not apply to `greedy-baseline`: it neither reads nor validates them.

| Key | Read in | Status |
|---|---|---|
| `engine` | `plan/PlanEngineSelector.java:98` | READ(@9104c1b); HTTP cases OBSERVED, see below |
| `importance` | `sinapse/importance/ImportanceStrategies.java:86` | READ(@9104c1b) |
| `precedence` | `plan/PrecedencePolicies.java:50` | READ(@9104c1b) |
| `provenance` | `plan/PrerequisiteProvenance.java:62` | READ(@9104c1b) |
| `generations`, `population-size`, `mutation-rate` | nowhere in `src/main`; ignored, name logged at WARN | OBSERVED(EOA-13 branch, `HiperparametrosDoCoreHttpTest`); deliberate: the Core owns the GA hyperparameters, DECISION(project owner, 2026-10-08, ADR-0009) |

`importance`, `precedence` and `provenance` change the **problem**, not the search: they are
experimental factors. READ(README.md:226-243; CLAUDE.md §1b@9104c1b)

`engine` over HTTP — OBSERVED(9104c1b, `./mvnw test -Dtest=PlanEngineSelectionHttpTest,PlanEngineSelectorTest`
→ 11 tests, 0 failures):

- over HTTP: key absent → 200 with `greedy-baseline`; `"greedy-baseline"` named → same plan as absent;
  `""` → 422 `unknown-engine`; `null` → 400 `malformed-body` (the `Map.copyOf` at
  `PlanRequest.java:87` refuses null before the selector runs);
- unit level only (`PlanEngineSelectorTest`): unknown name and non-string value are refused with
  `PlanRejectedException`. Their codes `unknown-engine` / `unusable-engine` are READ from
  `PlanEngineSelector.java`, not asserted by a test.

**Effective values in the response.** The response does not echo `algorithmParams`. It does record
the effective condition in `fitness`: `engine` (all engines), `prerequisite-provenance` (all
engines), `importance-strategy` and `precedence-policy` (`ga` and `ga-timeline` only, defaults
included; they do not apply to the greedy engine, which reports neither). OBSERVED(EOA-13 branch,
over HTTP, with and without the keys, for each engine). The GA's search parameters are not in the
response except `metadata.generations`; they are logged at INFO per request.

**Build identity.** `fitness.build` is the commit SHA the Core was built from (`-dirty` suffix for
a dirty tree, `unknown` without git), stamped by the selector next to `fitness.engine`.
`metadata.coreVersion` does not identify the build (STATE.md K7). OBSERVED(EOA-13 branch,
`VersaoDoBuildTest`).

## Error types

Every error is an RFC 7807 problem with type `https://api.dynamicstudyplanner.com/errors/<code>`.
READ(api/exception/ProblemDetails.java:34@9104c1b)

- Classification 400 vs 422: [ADR-0005](../adr/0005-criterio-de-classificacao-de-erro.md).
- Advices and statuses: README "Error Handling" (README.md:399-419); code in `api/exception/`.
- 422 codes: the string passed to `new PlanRejectedException("<code>", …)` across `src/main`
  (`plan/PlanRejectedException.java`); `grep -rn 'new PlanRejectedException("' src/main` lists them.
- A plan breaking its own output invariants is a 500, never returned. READ(README.md:633-637@9104c1b)

## Determinism and reproducibility

- Guaranteed: same body, same `randomSeed`, same engine ⇒ same plan, on any thread.
  OBSERVED(9104c1b, `./mvnw test -Dtest=PlanEngineDeterminismTest,GaResultadoInalteradoTest` →
  16 tests, 0 failures). Rules that keep it: CLAUDE.md §3 and §5.
- `metadata.elapsedMillis` is a **reserved constant, always 0**, in every engine, and it does not
  measure time. It stays 0 by owner decision DT-2 of 2026-10-08
  ([ADR-0008](../adr/0008-elapsed-millis-reservado.md)): a measured duration would make two runs of
  the same seeded request differ. Whoever needs a duration measures it outside the Core: the
  platform times its call, the Core's harness times the engine call (`MeasurementHarness`).
  DECISION(project owner, 2026-10-08); constant READ(GeneticPlanEngine.java:84,
  TimelinePlanEngine.java:102, GreedyBaselineScheduler.java:99@40e6061).
- `metadata.generations` is the real count for the GA engines and 0 for greedy.
  READ(GeneticPlanEngine.java:72-73, GreedyBaselineScheduler.java:78@9104c1b)
- Core version: `metadata.coreVersion`, resolved from `baseline.core.version=@project.version@`.
  OBSERVED(9104c1b, built `target/classes/application-baseline-core.properties` → `2.0.1`). It does
  not identify a commit; `fitness.build` does (STATE.md K7).
- The tactical gene order depends on `HashMap` iteration order; numbers are tied to JDK 21.
  READ(CLAUDE.md §5b@9104c1b)

## Known deviations — what a consumer must NOT assume

- That any engine plans the real catalogue fully: `ga-timeline` and `ga` refuse most real-catalogue
  instances with 422 `plan-would-be-empty` (STATE.md K2, K3).
- That the `offending` id of a `ga-timeline` `plan-would-be-empty` names the cause: it is always the
  first topic of the request. READ(sinapse/TimelinePlanEngine.java:183@9104c1b)
- That `generations`/`population-size`/`mutation-rate` sent in `algorithmParams` change anything (K6,
  ADR-0009).
- That `importance` or `precedence` sent to `greedy-baseline` are validated or applied: they are
  neither, and an invalid value there is answered `200`.
- That `elapsedMillis` measures time: it is a reserved constant 0 (ADR-0008, STATE.md K5). Nor that
  `coreVersion` identifies a build: read `fitness.build` (K7).
- That a missing `engine` key and `"engine": null` mean the same thing: null is a 400.
- That a partial plan is an error: not enough availability yields a declared partial plan, a prefix
  of the study order, with `partial` and `topics-unscheduled-ids` in `fitness`. READ(README.md:245-250@9104c1b)
- That `/plans` exists without profile `baseline-core` (K10).

## Instances shared with the platform

`src/test/resources/instances/`: three synthetic `PlanRequest`s (`apertado`, `medio`, `folgado`) and
a README, copied byte for byte from `sinapse-platform@cbb5529`. READ(docs/DIAGNOSTICO_ESCALA_REAL.md:52-58@9104c1b);
SHA-256 of all three match the README table: OBSERVED(9104c1b, `sha256sum src/test/resources/instances/*.json`).

`src/test/resources/instances/reproducers/` (commit `b9f62b3`): two minimal requests produced by
the EOA-10 `ddmin` reduction in `diagnostic/RealCatalogDiagnosticTest`. `ga-timeline-empty-minimal.json`
(one 150-min topic, one 70-min window) is refused by `ga-timeline` and by greedy;
`…-feasible.json` (two topics) is accepted by greedy and still refused by `ga-timeline` in 10/10 seeds. READ(docs/DIAGNOSTICO_ESCALA_REAL.md:318-319@9104c1b). Do not edit them.

## Contract change protocol

PROPOSAL — nobody has decided this procedure; the README rule it builds on is READ(README.md:533-537@9104c1b):

1. Open a request in [HANDOFF.md](./HANDOFF.md) first, naming the reference JSON and fields affected.
2. Bump `PlanRequest.VERSION`; update both reference JSON files identically in both repositories.
3. Provider (this repository) lands first, consumer second, within one logical change.
4. Never edit a reference JSON to make a test pass (CLAUDE.md §2).

## What the consumer assumes about this repository

Not restated here: `sinapse-platform` `docs/ai/INTEGRATION.md` "Assumptions about the Core" (READ at
other-repo@1006475). Where one of those assumptions is contradicted by this repository, the row there
names the STATE.md entry here that says so.

## What this repository assumes about its consumer

unknown. UNVERIFIED(not established in this task)
