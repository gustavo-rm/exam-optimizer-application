# Baseline Core scheduler

**This component is the experimental baseline, not a mock.**

A deterministic greedy scheduler that answers `POST /plans` with a `PlanResponse`. A claim that the
genetic algorithm adds value only means something against a simple scheduler that already respects
the same constraints, and this is that scheduler: it is a condition of the experiment, expected to
stay in the codebase after the genetic algorithm is wired to the same contract, and not scaffolding
to delete. The genetic algorithm is untouched by this module.

## The algorithm, in ten lines

1. Refuse what cannot be planned — absent or inverted horizon, no topics, a duplicate or
   unidentified topic, a topic estimating no minutes, no or broken availability windows — with
   **422** naming the offenders.
2. Reduce the prerequisite edges to constraints: `HARD` only, both endpoints among the topics sent,
   duplicates collapsed. `SOFT` edges are preferences and are ignored.
3. Topologically sort the topics over those constraints (Kahn), so a dependent topic is never
   considered before every topic it hard-requires has been placed.
4. Among the topics that are simultaneously legal next, choose by goal priority (higher first),
   then target date (nearer first, none last), then curricular position (lower first), then topic
   identifier as text. The four are read in sequence, not weighed — the order is total, so no
   collection's iteration order can reach the result.
5. Give a topic one `REVISION` block, half its estimated minutes rounded up, when the request has a
   history entry for it whose most recent recall rating is `AGAIN` or `HARD` and whose
   `lastStudiedAt` is at least 14 days before the first day of the horizon.
6. Clip the availability windows to the horizon read in UTC, drop the empty results and sort them by
   opening instant.
7. Lay the blocks in with one forward-only cursor: place at the cursor if the block fits whole in
   the current window, otherwise move to the next window. Never backtrack, never split a block
   across two windows.
8. Place each topic whole or not at all, and stop at the first topic that does not fit — the plan is
   a **prefix** of the study order, so the scheduled set stays closed under prerequisites.
9. Number the finished list 0, 1, 2, …: `sequenceIndex` is chronological, unique and contiguous
   because the cursor only ever moved forward.
10. Assert all twelve output invariants, then answer — echoing `randomSeed` exactly, reporting the
    build version as `coreVersion` and a `fitness` map of only the terms actually computed, with
    `"strategy": "greedy-baseline"`.

## Determinism

Required, not desired. The same request produces the same plan, byte for byte, on any thread.

* No iteration over a `HashMap` or `HashSet` reaches the output: every observable collection is a
  `LinkedHashMap`, a `TreeMap`/`TreeSet` over an explicit comparator, or a list sorted explicitly.
* No `Instant.now()` or `LocalDate.now()` on the decision path. The reference instant is
  `horizon.start` at `00:00Z`, which comes from the request.
* No `RandomProvider` and no other source of randomness. `randomSeed` is echoed and never consumed.
* `metadata.elapsedMillis` is `0`, because a measured duration would make two runs differ.
  `metadata.generations` is `0`, because none ran. The platform reads both off the wire and drops
  them (`docs/CORE_CONTRACT_SURVEY.md` §2.2).

`BaselineDeterminismTest` compares four concurrent runs byte for byte;
`BaselineDeterminismRulesTest` bans the three constructs in the package's source, so a latent
difference cannot be introduced without failing the build.

## Stated assumptions

**The horizon is read in UTC.** `horizon` is a pair of `LocalDate` and `availability` is a list of
absolute `Instant`s; comparing them needs a zone, and no zone crosses the wire — the platform
resolves the student's zone when it expands their weekly routine and keeps it. So the first day
starts at `00:00Z` and the last ends at `24:00Z`. The consequence is bounded and deliberate: for a
student far from UTC a window at the edge of the horizon can be clipped, and the plan loses that
sliver rather than emitting a session the platform's own horizon does not cover.

**An absent history entry is read as "not studied", so it earns no revision.** The platform's
`history` only covers a 90-day window (`docs/CORE_CONTRACT_SURVEY.md` §4), so a topic with no entry
was *either* never studied — which calls for no revision — *or* last studied more than 90 days ago,
which is the strongest case for one. Nothing in the request distinguishes them, and the two readings
want opposite treatments. The absence is read as "not studied" because the cost of being wrong is
bounded and falls the safe way: the topic still gets a full `STUDY` block of its whole
`estimatedMinutes`, which is *more* time than the half-length revision the other reading would have
given. A topic genuinely last studied 91 days ago is therefore planned as new ground, not skipped.
The opposite choice has no such floor — it would spend scarce hours revising material the student
has never seen, under a label promising familiarity. `GreedyBaselineSchedulerTest` asserts the
choice, so changing it is visible rather than silent.

**The revision rule is flat on purpose.** The most recent rating, not a trend; a fixed threshold,
not a decay curve; one revision, not a schedule of them; placed directly after its own study block,
with no spacing model. Each of those is a judgement the genetic algorithm exists to make against a
fitness function, with the retention engine this repository already has. A baseline that anticipated
them would stop being the control condition.

## What is refused, and what is not

Every refusal is **422**: the document parsed, the contract version matched, and the instructions
cannot be carried out — the distinction RFC 9110 draws, and the criterion ADR-0005 applies elsewhere
here. The body is RFC 7807, built by the application's own `ProblemDetails`, with the category in
`type` and the offending items in an `offending` extension.

| `type` slug | Condition |
|---|---|
| `unusable-horizon` | horizon absent, half-declared, or ending before it starts |
| `no-topics` | nothing to plan |
| `unidentified-topic` | a topic with no identifier |
| `duplicate-topic` | the same topic sent twice, with possibly different durations |
| `unusable-topic-duration` | a topic estimating zero or fewer minutes |
| `no-availability` | nowhere to put a session |
| `unusable-availability-slot` | a window that closes before it opens, or is missing an instant |
| `hard-prerequisite-cycle` | the `HARD` edges contain a cycle |
| `plan-would-be-empty` | windows exist, but not even the first topic fits |

**A cycle is detected, named and refused — never broken.** Kahn's algorithm cannot loop: it places
each topic at most once and stops when nothing is ready, and the leftover topics are exactly those
still waiting on a prerequisite that will never be placed. One concrete cycle is then extracted and
named in the body, prerequisite first. Breaking it here would produce a plan that silently
contradicts a curated curriculum edge, with the student studying a topic before its prerequisite and
nothing anywhere saying so. A cycle in `HARD` edges is a defect in the curriculum graph, and the
only party that can fix it is the one that curated it.

**`effortTier` outside `SHORT, STANDARD, LONG, EXTENDED` is *not* refused, because this scheduler
never reads it.** Allocation is by `estimatedMinutes`, which the platform has already derived from
the band and scaled by the student's own effort factor (`docs/CORE_CONTRACT_SURVEY.md` §1.8). The
contract carries the band as a `String`, so a fifth value arrives here without a deserialisation
error; it is carried past, unread, and changes nothing about the plan. Validating it would refuse a
request over a field the answer does not depend on. If a future version of this scheduler starts
reading the band, that is the moment to reject an unknown value with **422** naming it — not before.
`GreedyBaselineSchedulerTest` pins the current behaviour by planning the same request twice, once
with a band that does not exist.

**Not enough availability is a declared partial plan, never a silent one.** Allocation stops at the
first topic that does not fit and leaves out every topic after it in the study order, so the
scheduled set is a prefix of a topological order and therefore closed under prerequisites — skipping
ahead to whatever still fitted would produce a fuller calendar and an invalid one. A topic is placed
whole or not at all: if its revision block does not fit, its study block is given back too. The
answer says so in `fitness` (`topics-scheduled`, `topics-unscheduled`, `partial`). If *nothing*
fits, the request is refused with `plan-would-be-empty` instead, because an empty schedule is one of
the eight things the platform rejects outright.

## The twelve output invariants

Asserted before the response is returned, by `PlanOutputInvariants`. Eight are the checks
`RestSinapseCore.validated` applies (`docs/CORE_CONTRACT_SURVEY.md` §3): a non-empty body, a
matching `contractVersion`, at least one session, metadata naming a core version, the seed echoed,
every session carrying a topic and a kind and a start, every duration positive, no repeated
`sequenceIndex`.

Four are the ones the same survey says `validated` does **not** check, which is why they are
enforced here — nobody downstream catches them:

* `scheduledStart` inside the horizon;
* the whole session inside one availability window that was sent;
* sessions not overlapping one another;
* `topicId` among the topics that were sent.

Two more on top: `sequenceIndex` contiguous rather than merely distinct, and no session of a
dependent topic before the sessions of its hard prerequisites.

A violation is **500**, not 422: everything a caller can get wrong is refused earlier, so a failure
here means this module is wrong. All of them follow from the construction, which is exactly why they
are asserted — they are the properties a future change to the allocator would break silently.

## `fitness`: only what it computes

`fitness` is opaque to the platform, which makes it the easiest field in the contract to fill with
plausible numbers. The reference document `plan-response-v1.0.json` shows a genetic run's terms
(`weighted-score`, `coverage`, `overload-penalty`, `hard-violations`) and **none of them appears
here**: this scheduler computes no weighted score, evaluates no coverage objective and applies no
penalty. Reporting them would describe a run that did not happen.

What it reports: `strategy` (`"greedy-baseline"`), `topics-total`, `topics-scheduled`,
`topics-unscheduled`, `partial`, `study-sessions`, `revision-sessions`, `scheduled-minutes`,
`available-minutes`, `availability-utilisation`, `hard-edges-applied`. `strategy` is what lets a
stored plan be attributed to a condition of the experiment months later, which is the reason the map
is filled at all.

## The profile, and the 404 without it

The controller and the scheduler are `@Profile("baseline-core")`. Without the profile no bean of the
package is created, `POST /plans` has no handler, and the application boots exactly as it did
before — `BaselinePlanAbsentTest` asserts all three on the application's default context.

The **security chain is deliberately not gated by the profile**. `SecurityConfig` ends in
`anyRequest().authenticated()` with HTTP Basic, so without an unconditional chain a request to
`/plans` with the profile off would be answered `401` by the filter chain before the dispatcher could
report that nothing handles the path — telling the caller their credentials are wrong when the truth
is that the endpoint is not there. `permitAll` on a path with no handler exposes nothing: it grants
access to a `404`.

`/plans` is also hidden from the published OpenAPI document. It is not this service's public API —
it is the Core protocol, versioned by `PlanRequest.VERSION` and pinned by the reference documents in
`src/test/resources/contract/`. Publishing it would put a second, weaker description of the same
contract in circulation and would change `contract/openapi-snapshot.json` the moment anyone ran with
the profile on.

`baseline.core.version` lives in `src/main/resources/application-baseline-core.properties`, where
Maven's resource filtering substitutes `project.version`. There is no default in the code: a
deployment that cannot say which build answered fails at startup rather than answering with an
invented version string.

## Deployment: private network only

`/api/v1/**` is `permitAll` and `/plans` is `permitAll` too. **There is no authentication of any kind
in front of either** — none in this repository and none delegated to an external component. Anything
that can reach this service can post a study snapshot to it, containing the student's history and
goals, and read a plan back.

**The service must therefore not be reachable from the internet. It belongs on a private network,
behind the platform, which is the only party meant to call it.** The requirement is repeated in the
README, in both languages, because it is a deployment constraint and whoever deploys this reads the
README.

Apart from being public, `/plans` is held to no different standard than the rest: CSRF disabled, as
for the rest of this stateless API, and the `api.security.require-https` posture mirrored, so an
operator who declares a TLS proxy in front gets the same refusal of cleartext and the same HSTS
header on `/plans` as everywhere else. `BaselinePlanSecurityPostureTest` compares the two paths
request by request, so they cannot drift apart unnoticed.

## Where the code is

`src/main/java/com/ia/project/dynamicstudyplanner/baseline/`. It reads only two things from outside
itself: the contract records in `coreapi/contract` and the error-body builder in `api/exception`. No
existing class is modified.

| Class | Role |
|---|---|
| `BaselinePlanController` | `POST /plans`, and the two error mappings |
| `BaselinePlanSecurityConfig` | the filter chain that makes `/plans` public |
| `GreedyBaselineScheduler` | the pass, end to end |
| `PlanRequestGuard` | what is refused with 422 |
| `HardPrerequisiteGraph` | which edges become constraints |
| `StudyOrder` | the topological sort and the four tie-breaks |
| `GoalPressure` | priority and target date per subject |
| `RevisionPolicy` | which topics earn a revision |
| `HorizonBounds` | the horizon as an interval of instants |
| `AvailabilityAllocator` | the forward-only cursor |
| `BaselineFitness` | the reported terms |
| `PlanOutputInvariants` | the twelve assertions |
