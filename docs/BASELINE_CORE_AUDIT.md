# Baseline Core scheduler — audit

Measurement of commit `6f0e40d` ("Add the greedy baseline scheduler behind `POST /plans`"), merged
into `main` by PR #19, against a list of criteria it was **not** written from. This audit measures;
it does not fix, and it proposes no patch.

**Headline: no criterion in category (a) failed.** Every output invariant on the list — including the
five `RestSinapseCore.validated` does not check — is guaranteed by construction, asserted at runtime
before the response leaves, and negative-tested. The stop rule was therefore not triggered and all
sixteen items were audited.

Audited on `main` at `14184eb`, working tree clean. Paths are relative to the repository root;
`baseline/` abbreviates `src/main/java/com/ia/project/dynamicstudyplanner/baseline/` and
`test/baseline/` abbreviates `src/test/java/com/ia/project/dynamicstudyplanner/baseline/`.

---

## A. Determinism

| # | Item | Verdict | Evidence |
|---|---|---|---|
| A1 | No `HashMap`/`HashSet` iteration reaches the output | **PASS** | `grep -rn "HashMap\|HashSet\|groupingBy\|toMap\|Collectors.toSet" baseline/` returns only `LinkedHashMap` (`GoalPressure.java:49-50`, `BaselineFitness.java:58`, `RevisionPolicy.java:89`) and `LinkedHashSet` (`PlanRequestGuard.java:80`, `HardPrerequisiteGraph.java:144`). Every map/set whose order is observable is `LinkedHashMap`/`LinkedHashSet` populated from list iteration, or `TreeMap`/`TreeSet` over the explicit comparator `HardPrerequisiteGraph.BY_TEXT` (`StudyOrder.java:122,130`, `PlanOutputInvariants.java:109,174`). Pinned by a source-scanning test: `test/baseline/BaselineDeterminismRulesTest.java:68` "no iteration over an unordered collection can reach the output". |
| A2 | No clock read on the decision path | **PASS** | `grep -rn "Instant\.now\|LocalDate\.now\|LocalDateTime\.now\|System\.currentTimeMillis\|System\.nanoTime\|Clock\." baseline/` returns only two prose mentions (`package-info.java:33`, `RevisionPolicy.java:59`) and no call. The reference instant is derived from the request: `RevisionPolicy.of` takes `HorizonBounds.of(request.horizon()).from()` (`RevisionPolicy.java:91`). `elapsedMillis` is the constant `0` for the same reason (`GreedyBaselineScheduler.java:87`). Pinned by `BaselineDeterminismRulesTest.java:82`. |
| A3 | No use of `RandomProvider` | **PASS** | `grep -rn "RandomProvider\|Math\.random\|ThreadLocalRandom\|new Random\|UUID\.randomUUID\|SecureRandom" baseline/` returns one prose mention (`package-info.java:35`) and no call. Pinned by `BaselineDeterminismRulesTest.java:95`, which scans for the same tokens. |
| A4 | Topological sort has a **total** tie-break | **PASS** | `StudyOrder.comparator` (`StudyOrder.java:110-119`) is lexicographic over four keys: goal priority descending, target date `nullsLast`, `Topic::position`, then `Topic::id` under `HardPrerequisiteGraph.BY_TEXT`. Identifiers are unique per request — `PlanRequestGuard.checkTopics` refuses a duplicate id (`PlanRequestGuard.java:88-91`) — so the fourth key makes the order total and no tie can reach the `PriorityQueue`'s internal layout. The class comment states this at `StudyOrder.java:48-52`. Stronger than the criterion, which asked only for position then id. |
| A5 | Same request, two threads → identical plan | **PASS** | `test/baseline/BaselineDeterminismTest.java:66` `fourThreadsProduceOneIdenticalPlan`: four `Callable`s on a fixed pool of 4 serialise the response and assert `containsOnly(plans.get(0))` — byte-for-byte on the serialised document, not field-wise — plus a fifth run on the calling thread (lines 70-87). Two further tests: `aSecondInstanceAgrees` (line 91, a fresh `GreedyBaselineScheduler` agrees, so nothing is carried between requests) and `orderOfTheRequestCollectionsDoesNotMatter` (line 103). |

---

## B. Output invariants

`PlanOutputInvariants.check` runs on every answer, at `GreedyBaselineScheduler.java:136`, between
constructing the response and returning it. A violation raises `PlanInvariantViolationException` →
**500**, never 422 (`BaselinePlanController.java:105-116`), because it means this module has a defect.

### The eight `validated` enforces

| # | Invariant | Guaranteed in code | Asserted in test |
|---|---|---|---|
| 1 | Body not empty | **PASS** — `schedule` returns a constructed `PlanResponse` or throws; no path returns `null` (`GreedyBaselineScheduler.java:110-138`) | **PASS** — `BaselinePlanEndpointTest.java:104` asserts 200 with a parseable body |
| 2 | `contractVersion` matches | **PASS** — set from `PlanRequest.VERSION` (`GreedyBaselineScheduler.java:129`), re-checked `PlanOutputInvariants.java:75` | **PASS** — `PlanOutputInvariantsTest.java:88` |
| 3 | At least one session | **PASS** — empty allocation raises 422 `plan-would-be-empty` (`GreedyBaselineScheduler.java:120-126`), re-checked `PlanOutputInvariants.java:78` | **PASS** — `PlanOutputInvariantsTest.java:95`; `GreedyBaselineSchedulerRejectionTest.java:284` |
| 4 | Metadata names a core version | **PASS** — `PlanOutputInvariants.java:81-83` (non-null, non-blank) | **PASS** — `PlanOutputInvariantsTest.java:101,108,115` (three cases) |
| 5 | Seed echoed | **PASS** — `request.randomSeed()` copied at `GreedyBaselineScheduler.java:133`, re-checked `PlanOutputInvariants.java:84` | **PASS** — `PlanOutputInvariantsTest.java:122` |
| 6 | Every session has topic, kind, start | **PASS** — `PlanOutputInvariants.java:122-123` | **PASS** — `PlanOutputInvariantsTest.java:129,136,144` |
| 7 | Every duration positive | **PASS** — `PlanRequestGuard.checkDurations` refuses `estimatedMinutes <= 0` up front (`PlanRequestGuard.java:101-110`); `RevisionPolicy.revisionMinutes` floors at 1 (`RevisionPolicy.java:105`); re-checked `PlanOutputInvariants.java:124` | **PASS** — `PlanOutputInvariantsTest.java:152`; `GreedyBaselineSchedulerRejectionTest.java:132` |
| 8 | No repeated `sequenceIndex` | **PASS** — index is a count over the finished list (`GreedyBaselineScheduler.java:195-199`), re-checked `PlanOutputInvariants.java:100` | **PASS** — `PlanOutputInvariantsTest.java:159` |

### The five `validated` does not check — the ones that matter

| # | Invariant | Guaranteed in code | Asserted in test |
|---|---|---|---|
| 9 | `scheduledStart` inside the horizon | **PASS** — windows are clipped to `HorizonBounds` before any placement (`AvailabilityAllocator.java:69-82`), so a session starts at or after `horizon.from` and strictly before `horizon.until`; re-checked `PlanOutputInvariants.java:127` | **PASS** — `PlanOutputInvariantsTest.java:177,184` (before the open, after the close); `GreedyBaselineSchedulerTest.java:474` (clipping) |
| 10 | `scheduledStart` inside an availability window | **PASS**, and **stronger than the criterion**: `PlanOutputInvariants.fitsAWindow` (`:140-147`) requires the **whole block** — start *and* end — to lie inside one window as it was sent, not merely the start. By construction a block is placed only when `finish` is not after the current window's close (`AvailabilityAllocator.java:96-101`) and is never split across two windows | **PASS** — `PlanOutputInvariantsTest.java:191` (start in no window) and `:198` (starts inside a window, runs past its close); `GreedyBaselineSchedulerTest.java:524` (never split across adjacent windows) |
| 11 | Sessions do not overlap | **PASS** — the allocator's cursor is monotonic (`AvailabilityAllocator.java:96-101`, never reset backwards), so each session starts where the last ended or later; re-checked `PlanOutputInvariants.checkChronology` (`:150-162`) | **PASS** — `PlanOutputInvariantsTest.java:205`; `GreedyBaselineSchedulerTest.java:500` (overlapping input windows produce no overlapping sessions) |
| 12 | `topicId` was sent | **PASS** — sessions are built only from topics of the study order, which is built from `request.topics()` (`StudyOrder.java:67-89`); re-checked `PlanOutputInvariants.java:125` | **PASS** — `PlanOutputInvariantsTest.java:214` |
| 13 | `sequenceIndex` contiguous | **PASS** — `IntStream.range(0, placed.size())` (`GreedyBaselineScheduler.java:196`); re-checked `PlanOutputInvariants.java:103` asserting `first == 0 && last == count - 1` | **PASS** — `PlanOutputInvariantsTest.java:221` (distinct but not contiguous), `:230` (contiguous but not from zero) |

Two invariants beyond the list are also enforced: no session of a dependent topic before its hard
prerequisites, and the prerequisite being present in the plan at all
(`PlanOutputInvariants.checkPrerequisites`, `:173-199`; tested `PlanOutputInvariantsTest.java:239,248`).
The invariants are additionally asserted over *generated* plans, not only hand-built ones, through
`test/baseline/PlanInvariantAssertions.java`, called from `GreedyBaselineSchedulerTest.java:64,72,87,103`
and `BaselinePlanEndpointTest.java:116`.

---

## C. Contract

| # | Item | Verdict | Evidence |
|---|---|---|---|
| C6 | Consumes `coreapi/contract`, or redeclared its own DTO? | **PASS — consumes** | `BaselinePlanController.java:4-5` imports `coreapi.contract.PlanRequest` / `PlanResponse` and the handler signature is `PlanResponse plan(@RequestBody PlanRequest request)` (`:77`). No DTO is declared in `baseline/`: the package contains no record mirroring a contract type (`PlacedSession` is an internal placement tuple, not a wire type). |
| C7 | Serialised shape equals `plan-response-v1.0.json`, null fields omitted | **PASS** | `BaselinePlanEndpointTest.java:129` `theAnswerHasTheReferenceShape` compares the shape of the real HTTP response against the reference document, serialised by the application's own `ObjectMapper`. `:140` `omitsANullFieldRatherThanWritingIt` asserts the body contains no `null`, then proves the mechanism by serialising a `PlanResponse` with null `metadata` and asserting the key disappears — i.e. it pins `@JsonInclude(NON_NULL)` on the contract records, the divergence `CLAUDE.md` §2 warns against removing. |
| C8 | `randomSeed` echoed; `coreVersion` from the build, not a literal | **PASS** | Seed: copied at `GreedyBaselineScheduler.java:133`, asserted equal to the request's at `PlanOutputInvariants.java:84`, negative-tested at `PlanOutputInvariantsTest.java:122`. Version: injected with `@Value("${baseline.core.version}")` and **no default** (`GreedyBaselineScheduler.java:98`), from `application-baseline-core.properties:23` `baseline.core.version=@project.version@`. Filtering verified empirically — after `./mvnw verify`, `target/classes/application-baseline-core.properties` reads `baseline.core.version=2.0.1`. An unfiltered value would be caught: `BaselinePlanEndpointTest.java:120-124` asserts `isNotBlank().doesNotContain("@").matches("\\d+\\.\\d+.*")`. |

---

## D. Decisions deliberately left out of the records

| # | Item | Verdict | Evidence |
|---|---|---|---|
| D9 | `effortTier`: validated, or silent default? | **PASS (neither — never read, and said so)** | `grep -rn "effortTier" baseline/` returns matches in Javadoc only; no code reads the field. Allocation is by `estimatedMinutes` (`GreedyBaselineScheduler.java:170,179`). The decision is stated in three places — `GreedyBaselineScheduler.java:56-64`, `PlanRequestGuard.java:21-28`, `docs/BASELINE_CORE.md` §"What is refused, and what is not" — including when to revisit it ("if a future version starts reading the band, that is the moment to reject a value outside the closed set"). Pinned by `GreedyBaselineSchedulerTest.java:640` "an effortTier outside the closed set is accepted and changes nothing". There is no silent default because there is no read. |
| D10 | Absent `recallRatings`/`lastStudiedAt`: declared or assumed? | **PASS — declared** | `RevisionPolicy.java:33-54` is a titled section, "Design assumption: no history entry means 'not studied', and the 90-day window is why", which names the ambiguity, states the choice, and argues the cost is bounded (an absent-history topic still gets a full `STUDY` block, which is longer than the half-length revision the other reading would give). Restated in `docs/BASELINE_CORE.md:66-71` under "Stated assumptions". Asserted by `GreedyBaselineSchedulerTest.java:442` "a topic absent from history gets no revision: absence is read as not studied", plus `:414` (entry with no ratings) and `:424` (entry with no `lastStudiedAt`). |

---

## E. Declared robustness

| # | Item | Verdict | Evidence |
|---|---|---|---|
| E11 | Cycle in `HARD` edges: 422 naming the topics, no infinite loop | **PASS** | Kahn's algorithm places each topic at most once and stops when the ready queue empties (`StudyOrder.java:80-84`); leftovers mean a cycle (`:85-87`). `HardPrerequisiteGraph.cycleWithin` (`:129-155`) extracts one concrete cycle by walking prerequisites, guarded by `while (visited.add(current))` — it terminates on the first repeat over a finite residue. The 422 names the chain: `StudyOrder.java:160-165`, with the ids in `PlanRejectedException.offending`. Tested: `GreedyBaselineSchedulerRejectionTest.java:212` (two-topic cycle, topics named), `:228` (self-loop), `:239` (only the cycle is named, not what it blocks), `:254` "detection terminates rather than looping on the cycle"; end-to-end at `BaselinePlanEndpointTest.java:162`. |
| E12 | Insufficient availability: partial plan **declared**, unallocated topics **named** | **FAIL (partial)** | Declared: yes. `fitness` carries `topics-total`, `topics-scheduled`, `topics-unscheduled` and a boolean `partial` (`BaselineFitness.java:60-63`), documented at `GreedyBaselineScheduler.java:40-54` and `docs/BASELINE_CORE.md:121-126`, tested at `GreedyBaselineSchedulerTest.java:541`. **Named: no.** `grep -n 'fitness.put' BaselineFitness.java` shows eleven keys, none carrying topic identifiers; nothing else in the response lists the dropped topics. The set is recoverable by the consumer — the plan is a prefix of the study order, so the unscheduled topics are exactly `request.topics()` minus the topic ids appearing in `sessions` — but the response does not itemise them and no document says the consumer is expected to derive them. |

---

## F. Isolation and quality

| # | Item | Verdict | Evidence |
|---|---|---|---|
| F13 | Without the profile: boots as before, `/plans` → 404 | **PASS** | `BaselinePlanController` and `GreedyBaselineScheduler` are `@Profile(BaselineCore.PROFILE)` (`:53`, `:80`). `test/baseline/BaselinePlanAbsentTest.java:44` asserts `POST /plans` answers 404, `:54` that neither bean exists, `:75` that "the optimizer endpoint behaves exactly as it did". `:65` pins the deliberate asymmetry: the security chain for `/plans` is *not* profile-gated, so the 404 is honest rather than a 401 masking a missing handler. |
| F14 | `/api/v1/optimizer/*` intact; `GaResultadoInalteradoTest` green and unchanged | **PASS** | `git diff --stat 6f0e40d^ 6f0e40d -- src/main/.../api src/main/.../ga src/main/.../service src/main/.../config` → empty; PR #19 added only `baseline/`, `coreapi` was already there. `git diff 6f0e40d^ HEAD -- .../GaResultadoInalteradoTest.java` → empty; the file's last change is `9db2505`, long before PR #19. `git diff --stat 6f0e40d^ 6f0e40d -- src/test/resources/contract/` → empty, so `openapi-snapshot.json` is untouched — consistent with `/plans` being `@Hidden` (`BaselinePlanController.java:54`) and pinned by `BaselinePlanEndpointTest.java:221`. The test is green in the run below. **Signature caveat:** `GaResultadoInalteradoTest` stores no reference signature to compare against (`CLAUDE.md` §5) — it compares two seeded in-process runs — so "signatures unchanged" is established here by the file being byte-identical to its pre-PR-#19 state, not by re-deriving a recorded value. |
| F15 | Coverage floor lowered, Checkstyle relaxed, `@SuppressWarnings` added? | **PASS — none of the three** | `git diff --stat 6f0e40d^ 6f0e40d -- pom.xml config/` → **empty output**: PR #19 changed neither the POM nor the Checkstyle configuration. `git diff 6f0e40d^ 6f0e40d \| grep "^+.*@SuppressWarnings"` → no match. No suppression file exists (`find . -name "*suppress*"` → nothing). Floors on `main` today are still `INSTRUCTION 0.9325` / `BRANCH 0.7861` (`pom.xml:314,338`), the values recorded in `CLAUDE.md` §1. The build was made to pass by writing code that fits the gates, not by moving them. |
| F16 | Private-network requirement in the README, not only in a code comment | **PASS** | `README.md:359` is a dedicated section, "⚠️ Deployment: private network only / Implantação: somente rede privada", stating it in English (`:361-366`) and Portuguese (`:371`). Cross-referenced from the Security section at `README.md:227`. Also in `docs/BASELINE_CORE.md:191` and in `BaselinePlanSecurityConfig.java:16`, so the comment is a restatement, not the only home. |

---

## G. `./mvnw verify` on `main`

Run on `main` at `14184eb`:

```
[INFO] Tests run: 421, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
[INFO] Total time:  47.450 s
```

Checkstyle reported no violations and the JaCoCo `check-coverage-floor` rule passed. Coverage read
from `target/site/jacoco/jacoco.csv`:

| Counter | Covered / total | Ratio | Floor | Headroom |
|---|---|---|---|---|
| `INSTRUCTION` | 10252 / 10832 | **0.94645** | 0.9325 | +0.0140 |
| `BRANCH` | 679 / 816 | **0.83211** | 0.7861 | +0.0460 |

Both rose relative to the floors; the baseline module arrived with its own tests rather than
consuming the ratchet's slack.

---

## Findings

### (a) Output invariant not guaranteed

**None.** All thirteen invariants on the list are guaranteed by construction *and* re-asserted at
runtime before the response leaves *and* covered by a negative test. Two of them are enforced more
strictly than the criterion asked: availability containment covers the whole block rather than only
its start, and `sequenceIndex` is contiguous rather than merely distinct. This is the category the
stop rule was written for, and it is empty — so the remaining items were all audited.

### (b) Invariant guaranteed but not tested

**None.** Every invariant with a guarantee in code has a corresponding negative test, listed in §B.

### (c) Decision taken in silence that should be declared

1. **The unallocated topics are not itemised in the response** (E12). Partiality is declared —
   `partial`, `topics-scheduled`, `topics-unscheduled` — but *which* topics were dropped is left for
   the consumer to derive by set difference against `request.topics()`, and nothing in the code or in
   `docs/BASELINE_CORE.md` says that derivation is the intended contract. A consumer that trusts
   `fitness` alone learns how many topics it lost, not which. This is the one item on the list that
   the implementation does not meet as written.
2. **The 14-day staleness threshold has no stated basis.** `RevisionPolicy.STALE_AFTER_DAYS = 14`
   (`RevisionPolicy.java:65`) decides which topics get a `REVISION` block. It is stated in three
   places (`RevisionPolicy.java:20`, `docs/BASELINE_CORE.md:26`, `:66-71`) and never justified — no
   citation, no measurement, and none of the "THIS IS AN ASSUMPTION, NOT A FINDING" framing this
   codebase uses elsewhere for exactly this kind of number (compare the platform's effort-tier
   minutes, `docs/CORE_CONTRACT_SURVEY.md` §1.8, and `LearningModel.TAU_AT_AVERAGE_LOAD`, which
   carries its reasoning). The surrounding Javadoc argues at length for the rule being *flat* — one
   revision, latest rating, no decay curve — and that argument is sound; it just never reaches the
   constant itself. A reader cannot tell whether 14 was reasoned about or picked.

### (d) Cosmetic

1. **`RevisionPolicy.java:52` cites a test that does not exist.** "It is asserted by
   `RevisionPolicyTest`" — `find src/test -name "RevisionPolicyTest*"` returns nothing, and the
   baseline package has eleven test files, none of them that one. The assumption *is* asserted, at
   `GreedyBaselineSchedulerTest.java:442`, so the claim is true and only the name is wrong. The other
   two test names cited from `baseline/` Javadoc (`BaselinePlanSecurityPostureTest`,
   `GreedyBaselineSchedulerTest`) do exist.
2. **`availableMinutes()` double-counts overlapping windows.** `AvailabilityAllocator.java:117-121`
   sums windows as sent, so two that overlap are counted twice, inflating `available-minutes` and
   deflating `availability-utilisation`. It is documented on the method (`:107-116`) and argued as
   deliberate — the platform sorts and expands availability day by day, so overlap does not occur in
   its traffic, and merging windows for one reported number would rewrite the request. It affects a
   reported figure only, never allocation: the cursor already refuses to place a block twice in the
   same minute. No test pins the behaviour either way.

---

## Items not audited

None. The stop rule did not trigger, and A1–A5, B (all thirteen), C6–C8, D9–D10, E11–E12, F13–F16
and G were all measured.
