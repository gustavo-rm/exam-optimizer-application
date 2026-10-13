# HANDOFF — requests between exam-optimizer-application and sinapse-platform

This repository writes only here. A request to `sinapse-platform` is opened below; that repository
answers in its own `docs/ai/HANDOFF.md`, citing the ID. A request from it is answered below, citing
its ID and the SHA at which it was read. IDs opened here use the prefix `EOA-H`.

Status values: `open` | `acknowledged` | `done`.

## Open requests to the other repository

### EOA-H1 — README `algorithmParams` section becomes the source for the platform's SP-8 item 3b

| Field | Value |
|---|---|
| Date | 2026-10-08 (registered here; raised earlier by the planner: UNVERIFIED(planner, 2026-10-08)) |
| What changes or is needed | After EOA-13, the `algorithmParams` section of this README is the single source the platform's SP-8 item 3b should reference instead of restating |
| Why | One fact, one place: the keys the Core reads, and their effect, are decided here |
| Interface affected | `PlanRequest.algorithmParams` (open map; no contract version change) |
| What the platform must do | Reference README "Choosing the engine per request" (and its successor after EOA-13) from SP-8 item 3b instead of restating it |
| Depends on | D4 (decided 2026-10-08, ADR-0009), EOA-13 (STATE.md) |
| Status | acknowledged |

Acknowledged by the platform in its `docs/ai/HANDOFF.md` "Responses" (other-repo@1006475). Since EOA-13 the README section documents all four keys the Core applies, with values, defaults,
errors and the effective value in the response, and states that the GA hyperparameters belong to
the Core. The anchor is unchanged ("Choosing the engine per request"). The content of SP-8 is
UNVERIFIED(not read in sinapse-platform).

### EOA-H2 — instances are byte copies of the platform's

| Field | Value |
|---|---|
| Date | 2026-10-08 (registered here; copy made in commit `e9e09d1` on 2026-10-05) |
| What changes or is needed | If the platform regenerates the instances in its repository, they must be copied again here byte for byte and the expected SHA-256 values updated |
| Why | EOA-10 results are tied to those exact bytes |
| Interface affected | `src/test/resources/instances/*.json` and their README (copied from `sinapse-platform@cbb5529`) |
| What the platform must do | Announce a regeneration in its `docs/ai/HANDOFF.md`, with the new commit SHA |
| Depends on | nothing |
| Status | acknowledged |

Where the expected SHA-256 values live here: the table in `src/test/resources/instances/README.md`
and the truncated table in `docs/DIAGNOSTICO_ESCALA_REAL.md` §2. No test in this repository asserts
them. READ(@9104c1b). Current files match: OBSERVED(9104c1b, `sha256sum src/test/resources/instances/*.json`).

## Responses to requests from the other repository

Requests are read in `sinapse-platform`'s `docs/ai/HANDOFF.md`, at the SHA in each row.

| Their ID | Read at | Response | Resolved by | Status |
|---|---|---|---|---|
| SP-H1 | other-repo@1006475 | (a) Executed, not only read: `generations`, `population-size` and `mutation-rate` sent in `algorithmParams` do not change the plan (`HiperparametrosDoCoreHttpTest`: minimum and maximum of each key give the same sessions, `fitness` and `metadata`; a positive control with a one-generation Core budget does change the plan). They stay ignored by decision D4 (ADR-0009) and each request now logs their names at WARN. (b) The response already reports the effective `importance` (`fitness.importance-strategy`) and `precedence` (`fitness.precedence-policy`) for `ga` and `ga-timeline`, defaults included, and `provenance` (`fitness.prerequisite-provenance`) for every engine; `importance` and `precedence` do not apply to `greedy-baseline`, so nothing is reported there. Values, defaults and errors: README "Choosing the engine per request" | EOA-13 Part B, items 0 and 0b (#36) | done |
| SP-H2 | other-repo@1006475 | Decided by the project owner on 2026-10-08 (DT-2, [ADR-0008](../adr/0008-elapsed-millis-reservado.md)): the Core will **not** fill `metadata.elapsedMillis`. It stays a reserved constant 0, so the response stays reproducible byte for byte and the contract does not change. The platform measures the call itself, in a new column (its SP-8 item 3c) | EOA-13 Part A (documentation only); done on the platform side when SP-8 item 3c is delivered | acknowledged |
| SP-H3 | other-repo@1006475 | Diagnosed in EOA-10 (`docs/DIAGNOSTICO_ESCALA_REAL.md`); the repair is EOA-12, blocked on D1 and D2 (STATE.md). Until then only `greedy-baseline` plans the real catalogue (STATE.md K2, K3) | EOA-12 | acknowledged |

## Cross-repository log

Newest first, at most 20 entries; older ones leave (git keeps them).

| Date | Change | Interface affected | Action required |
|---|---|---|---|
| 2026-10-08 | EOA-13 answers the platform's SP-H1 (done: executed), SP-H2 (acknowledged, DT-2) and SP-H3 (acknowledged, EOA-12), read at other-repo@1006475 | none (answers only) | platform: close SP-H1 on its side when this PR merges |
| 2026-10-08 | EOA-13: `fitness.build` carries the Core's build commit; the ignored `algorithmParams` keys are logged by name; README documents the four applied keys; D4 and DT decided (ADR-0009, ADR-0008) | `PlanResponse.fitness` (open map, no contract change) | platform: may read `fitness.build` to know which Core build produced a plan; `metadata.coreVersion` does not say it |
| 2026-10-08 | PR #33 merged: engine selection pinned over HTTP; README documents `algorithmParams.engine` and indexes the four read keys | `algorithmParams` (documentation only) | platform: see EOA-H1 |
| 2026-10-05 | PR #32 merged: EOA-10 real-scale diagnosis; `ga` and `ga-timeline` refuse most real-catalogue requests | `POST /plans` behaviour (no shape change) | platform: do not rely on GA engines for the real catalogue (INTEGRATION.md "Known deviations") |
| 2026-10-05 | Commit `e9e09d1`: three instances copied from `sinapse-platform@cbb5529` | test inputs | see EOA-H2 |
