/**
 * The greedy baseline scheduler: the experimental control condition, not a mock.
 *
 * <h2>What this module is</h2>
 *
 * A deterministic greedy scheduler that answers {@code POST /plans} with a {@link
 * com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse}. It respects hard prerequisites,
 * orders by goal pressure and fills the student's availability windows in one forward pass. It runs
 * no search, keeps no population and evaluates no generations.
 *
 * <p><b>It is the experimental baseline, not a stand-in for the genetic algorithm.</b> A claim that
 * the genetic algorithm adds value is only meaningful against a simple scheduler that already
 * respects the same constraints, and this is that scheduler. It is a condition of the experiment and
 * is expected to stay in the codebase after the genetic algorithm is wired to this contract — it is
 * not scaffolding to delete.
 *
 * <h2>What it reads from elsewhere</h2>
 *
 * Two things, both read-only: the contract records in {@code coreapi/contract} and the error-body
 * builder in {@code api/exception}.
 *
 * <p>This section used to say "nothing, and the exam-optimiser path behaves exactly as it did",
 * because this module was added alongside that path. EOA-4b removed the path, and {@code /plans} is
 * now the only endpoint the service serves.
 *
 * <h2>Determinism is a requirement here, not an aspiration</h2>
 *
 * The same {@code PlanRequest} must produce the same plan, byte for byte, on any thread and in any
 * process. Three rules follow, and all three are checked by tests:
 *
 * <ul>
 *   <li>no iteration over a {@code HashMap} or {@code HashSet} reaches the output — every collection
 *       whose order can be observed is a {@code LinkedHashMap}, a {@code TreeMap} with an explicit
 *       comparator, or a list sorted explicitly;</li>
 *   <li>no {@code Instant.now()} or {@code LocalDate.now()} on the decision path — the reference
 *       instant comes from {@code horizon.start} of the request itself;</li>
 *   <li>no {@code RandomProvider}, and no other source of randomness. {@code randomSeed} is echoed
 *       back and never consumed.</li>
 * </ul>
 *
 * <p>{@code metadata.elapsedMillis} is reported as {@code 0} for the same reason: a measured
 * duration would make two runs of the same request differ. The platform reads the field off the wire
 * and drops it ({@code docs/CORE_CONTRACT_SURVEY.md} §2.2), so nothing is lost by not measuring.
 *
 * <h2>The algorithm, in order</h2>
 *
 * <ol>
 *   <li>{@link com.ia.project.dynamicstudyplanner.baseline.PlanRequestGuard} refuses a request that
 *       cannot be planned at all, with {@code 422} and the offending items named;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.baseline.StudyOrder} topologically sorts the
 *       topics over the {@code HARD} edges, breaking ties by goal pressure, then curricular
 *       position, then topic UUID as text — a total order, so no collection's iteration order can
 *       leak into the result;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.baseline.AvailabilityAllocator} lays the sessions
 *       into the availability windows in one forward pass, never overlapping and never backtracking;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.baseline.RevisionPolicy} adds at most one
 *       {@code REVISION} session per topic, by one stated rule;</li>
 *   <li>{@link com.ia.project.dynamicstudyplanner.baseline.PlanOutputInvariants} asserts the twelve
 *       properties of the answer <b>before</b> it is returned — the eight the platform checks and
 *       the four it does not.</li>
 * </ol>
 *
 * <h2>Deployment: this service must not be reachable from the internet</h2>
 *
 * {@code /api/v1/**} is {@code permitAll} and {@code /plans} is {@code permitAll} too. There is no
 * authentication of any kind in front of either. The service therefore has to live on a private
 * network, behind the platform, and that requirement is written in the README rather than only here.
 *
 * <p>The ten-line summary of the algorithm and the statement that this component is the
 * experimental baseline and not a mock are in {@code docs/BASELINE_CORE.md}.
 */
package com.ia.project.dynamicstudyplanner.baseline;
