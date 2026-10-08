# STATE — exam-optimizer-application

Reviewed: 2026-10-08 UTC at `9104c1b` (origin/main).

Present only; history is `git log` and the PR descriptions. Edit only your own row in **Work**.
Task IDs come from the project owner's planning document, which lives outside this repository; this
table is now the source of their status. UNVERIFIED(planner, 2026-10-08) for anything about that
document's contents.

## Work

| ID | Title | Status | PR | Depends on | Notes |
|---|---|---|---|---|---|
| EOA-10 | Real-catalogue scale diagnosis | done | #32 (merge `abd6676`) | — | [docs/DIAGNOSTICO_ESCALA_REAL.md](../DIAGNOSTICO_ESCALA_REAL.md). Merged: OBSERVED(9104c1b, `git log --merges`) |
| EOA-11b | Engine-selection tests and docs | done | #33 (merge `21c8e41`) | — | README documents `algorithmParams.engine` and indexes all four read keys. Follow-up #34 (`9104c1b`) put `ga-timeline` in `PlanEngines.all()`. OBSERVED(9104c1b, `git log --merges`) |
| EOA-13 | `elapsedMillis` (DT-2) and honest hyperparameters (D4) | done | (number added in a second commit) | #33 (merged), D4 and DT (decided 2026-10-08) | Item 0 executed: the three GA keys are ignored (`HiperparametrosDoCoreHttpTest`). 0b: effective `importance`/`precedence` already in `fitness` for the GA engines; they do not apply to greedy, nothing stamped. 0c: `fitness.build`. Part A documentation only |
| EOA-12 | GA repair at real scale | blocked | — | D1, D2 | Inputs: EOA-10 §8 and §10. Prompt still a skeleton: UNVERIFIED(planner, 2026-10-08) |
| EOA-9b | 2×2 factorial for the thesis | pending | — | EOA-12 | — |
| AI-1 | AI context layer (`docs/ai/`, CLAUDE.md protocol) | done | #35 (merge `40e6061`) | — | ID assigned here; the planner had none. Merged: OBSERVED(`git log --merges`) |

## Open decisions

Owner of every row: project owner.

| ID | Question | Blocks | Options | Recommendation |
|---|---|---|---|---|
| DT | Should the Core report a real duration in `metadata.elapsedMillis`? | — | DT-1, DT-2, DT-3 | **Decided** by the project owner, 2026-10-08: DT-2, the Core keeps 0 and the platform measures the call. DT-1 and DT-3 discarded. [ADR-0008](../adr/0008-elapsed-millis-reservado.md) |
| D4 | Who owns the GA hyperparameters: the Core (`plan.engine.ga.*`) or the caller (`algorithmParams`)? | — | Variant 1, Variant 2 | **Decided** by the project owner, 2026-10-08: Variant 1, the Core owns them. Variant 2 discarded. [ADR-0009](../adr/0009-hiperparametros-sao-do-core.md) |
| D1 | What proves the GA was repaired? | EOA-12 | invariants as gate; comparative quality as gate | Invariants as the gate, comparative quality only as a measured hypothesis — PROPOSAL(planner) |
| D2 | What may the repair touch? | EOA-12 | search side (selection, operators, placement); objective side (fitness terms) | none recorded. EOA-10 §10 states its first three hypotheses touch fitness, selection or placement and defers to D2: READ(docs/DIAGNOSTICO_ESCALA_REAL.md:365-366@9104c1b) |
| D3 | What does the student see when not every topic fits? | nothing | — | none recorded. Today the response names the casualties in `fitness.topics-unscheduled-ids`: READ(README.md:245-250@9104c1b) |
| D-AI1 | Should the owner decisions in CONTEXT.md "Decisions recorded here" become ADRs in `docs/adr/`? | nothing | ADR per decision; leave as DECISION lines | ADRs, in the existing Portuguese format — PROPOSAL |

## Known issues and limitations

| ID | Description | Status | Evidence | Workaround | Resolved by |
|---|---|---|---|---|---|
| K1 | CLAUDE.md §6 says commit subjects are Portuguese; the owner now wants English, and commits since 2026-10-05 are English | OBSERVED(9104c1b, `git log --no-merges -14`); DECISION(stated by project owner) | CLAUDE.md §6 table, last row | Follow the owner: English | owner edits CLAUDE.md §6 (this task may not rewrite it) |
| K2 | `ga-timeline` refuses 30/30 real-catalogue runs with `plan-would-be-empty`: `F(empty plan)=NaN` from `dailyLoadBudget`, and `Population.getFittest` ranks `NaN` above every number | READ(docs/DIAGNOSTICO_ESCALA_REAL.md:21-23@9104c1b), labelled [observado] there; code path READ(DailyLoadBudgetObjective.java:105-106, Population.java:63-65@9104c1b) | EOA-10 §8, §10 H-A | use `greedy-baseline` | EOA-12 |
| K3 | `ga` refuses 23/30 real-catalogue runs; its chromosome holds all 35 topics, the plan shrinks after the search, at placement | READ(docs/DIAGNOSTICO_ESCALA_REAL.md:24-26@9104c1b), [observado] there | EOA-10 §8b | use `greedy-baseline` | EOA-12 |
| K4 | Placement is all-or-nothing per window and stops at the first topic that does not fit; the same allocator limits the greedy engine (2, 15, 33 topics on the three profiles). That this is *the* cause | mechanism READ(plan/AvailabilityAllocator.java:112-127, sinapse/SessionPlacement.java:115-117@9104c1b); cause HYPOTHESIS (EOA-10 §1.4) | EOA-10 §3, §10 | — | EOA-12 (D2) |
| K5 | `metadata.elapsedMillis` is a reserved constant 0 in all three engines and does not measure time; it stays 0 by owner decision DT-2 | READ(GeneticPlanEngine.java:84, TimelinePlanEngine.java:102, GreedyBaselineScheduler.java:99@40e6061); DECISION(project owner, 2026-10-08, [ADR-0008](../adr/0008-elapsed-millis-reservado.md)) | README "metadata, field by field"; class Javadoc of each engine | measure outside the Core: the platform times its call, the harness times the engine call | deliberate (ADR-0008) |
| K6 | `algorithmParams.generations`, `population-size`, `mutation-rate` are ignored; the GA runs `plan.engine.ga.*` (60 generations × 40). Deliberate since D4 | OBSERVED(EOA-13 branch, `HiperparametrosDoCoreHttpTest`: same plan at min and max of each key, positive control changes it); DECISION(project owner, 2026-10-08, ADR-0009) | README "The GA's search parameters belong to the Core" | the ignored names are logged at WARN, the effective values at INFO | deliberate (ADR-0009) |
| K7 | **Closed.** `metadata.coreVersion` is still the Maven version and does not identify the build; `fitness.build` now carries the commit SHA (`-dirty` suffix if the tree was dirty, `unknown` without git) | OBSERVED(EOA-13 branch, `VersaoDoBuildTest`: `fitness.build` equals the generated `git.properties`) | README "The GA's search parameters belong to the Core"; `plan/BuildIdentity` | read `fitness.build` | EOA-13 (option (a), `git-commit-id-maven-plugin`) |
| K8 | **Closed.** The README heading now says "three engines behind `POST /plans`", matching the table under it | READ(README.md, EOA-13 branch) | — | — | EOA-13 |
| K9 | **Closed.** README now documents the values of `algorithmParams.precedence` (`lexicographic`, `weighted`) and their per-engine default | OBSERVED(EOA-13 branch, executed over HTTP) | README "The closed set: the four keys the Core applies" | — | EOA-13 |
| K10 | Without profile `baseline-core` the app serves no endpoint; the default active profile is `dev` | READ(application.properties:2@9104c1b; CLAUDE.md §1b) | — | set `spring.profiles.active` to include `baseline-core` | deliberate (CLAUDE.md §1b) |
| K11 | No authentication and no rate limiting on `/plans`; private network required | READ(baseline/BaselinePlanSecurityConfig.java:88@9104c1b; README.md:449-450) | README "Deployment: private network only" | network isolation | deliberate |
| K12 | `cp.txt` at the root is a Maven classpath dump committed by accident | READ(cp.txt, commit 12d75fb@9104c1b) | — | ignore it | unassigned |

## Unknowns

- Effect of `importance`, `precedence`, `provenance` on the real catalogue (hypothesis HK): EOA-10
  does not measure it; it only lists the four keys as read. READ(docs/DIAGNOSTICO_ESCALA_REAL.md:82@9104c1b)
- The platform audit figures before EOA-10 (`ga` 2–4 topics, `ga-timeline` 422
  `plan-would-be-empty`, greedy 33 sessions). UNVERIFIED(sinapse-platform audit, 2026-10-05)
- Production topology (replicas behind a load balancer): nothing in this repository deploys it.
  UNVERIFIED(planner, 2026-10-08)
- What the platform assumes about the Core: not restated here. See `sinapse-platform`
  `docs/ai/INTEGRATION.md` "Assumptions about the Core" (READ at other-repo@1006475).
