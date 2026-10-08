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
| Depends on | D4, EOA-13 (STATE.md) |
| Status | open |

Today the README indexes the four keys the Core reads (`engine`, `importance`, `precedence`,
`provenance`) and states that `generations`, `population-size`, `mutation-rate` are ignored.
READ(README.md:616-627@9104c1b). The content of SP-8 is UNVERIFIED(not read in sinapse-platform).

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

None recorded. `sinapse-platform` has no `docs/ai/` yet, and it was not read in this task.
UNVERIFIED(planner, 2026-10-08)

## Cross-repository log

Newest first, at most 20 entries; older ones leave (git keeps them).

| Date | Change | Interface affected | Action required |
|---|---|---|---|
| 2026-10-08 | PR #33 merged: engine selection pinned over HTTP; README documents `algorithmParams.engine` and indexes the four read keys | `algorithmParams` (documentation only) | platform: see EOA-H1 |
| 2026-10-05 | PR #32 merged: EOA-10 real-scale diagnosis; `ga` and `ga-timeline` refuse most real-catalogue requests | `POST /plans` behaviour (no shape change) | platform: do not rely on GA engines for the real catalogue (INTEGRATION.md "Known deviations") |
| 2026-10-05 | Commit `e9e09d1`: three instances copied from `sinapse-platform@cbb5529` | test inputs | see EOA-H2 |
