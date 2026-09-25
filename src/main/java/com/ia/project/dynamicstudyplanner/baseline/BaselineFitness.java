package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.plan.PlacedSession;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteReport;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code fitness} map: only terms this scheduler actually computes.
 *
 * <h2>Nothing here is invented</h2>
 *
 * {@code fitness} is opaque to the platform — it is read off the wire and never interpreted
 * ({@code docs/CORE_CONTRACT_SURVEY.md} §2.2) — which makes it the easiest field in the contract to
 * fill with plausible-looking numbers. The reference document
 * {@code src/test/resources/contract/plan-response-v1.0.json} shows a genetic run's terms
 * ({@code weighted-score}, {@code coverage}, {@code overload-penalty}, {@code hard-violations}), and
 * <b>none of them appears here</b>: this scheduler computes no weighted score, evaluates no coverage
 * objective and applies no penalty. Reporting them would be describing a run that did not happen.
 *
 * <p>Every key below is a count or a ratio the allocation produced. {@code strategy} names which
 * scheduler answered, so a stored plan can be attributed to a condition of the experiment months
 * later — that attribution is the reason this map is filled at all.
 *
 * <p>Key order is insertion order here, but the contract record copies the map with
 * {@code Map.copyOf}, whose iteration order is its own. The order of the keys is therefore not part
 * of what this side promises; the set of keys and their values are.
 */
public final class BaselineFitness {

    /** The value of {@code strategy}: which condition of the experiment produced this plan. */
    public static final String STRATEGY = "greedy-baseline";

    private BaselineFitness() {
    }

    /**
     * Sums the allocation into the reported terms.
     *
     * @param topicsTotal      how many topics the request carried
     * @param sessions         the sessions that were placed; never empty
     * @param availableMinutes usable minutes after clipping to the horizon; positive, because a plan
     *                         with no minutes is refused before this is called
     * @param hardEdgesApplied how many {@code HARD} constraints the ordering had to satisfy
     * @param prerequisites    what the prerequisite stage saw and did, reported in the same keys
     *                         the genetic path uses so the two conditions compare without a
     *                         translation step
     * @return the map to report, read-only
     */
    static Map<String, Object> of(int topicsTotal, List<PlacedSession> sessions,
            long availableMinutes, int hardEdgesApplied, PrerequisiteReport prerequisites) {

        long study = sessions.stream().filter(session -> session.kind() == SessionKind.STUDY).count();
        long revision = sessions.size() - study;
        long scheduledMinutes = sessions.stream()
                .mapToLong(PlacedSession::durationMinutes)
                .sum();

        Map<String, Object> fitness = new LinkedHashMap<>();
        fitness.put("strategy", STRATEGY);
        fitness.put("topics-total", topicsTotal);
        fitness.put("topics-scheduled", study);
        fitness.put("topics-unscheduled", topicsTotal - study);
        fitness.put("partial", study < topicsTotal);
        fitness.put("study-sessions", study);
        fitness.put("revision-sessions", revision);
        fitness.put("scheduled-minutes", scheduledMinutes);
        fitness.put("available-minutes", availableMinutes);
        fitness.put("availability-utilisation", ratio(scheduledMinutes, availableMinutes));
        fitness.put("hard-edges-applied", hardEdgesApplied);
        fitness.put(PrerequisiteProvenance.FITNESS_KEY, prerequisites.provenance().id());
        fitness.put("prerequisite-edges-hard", prerequisites.hardEdges());
        fitness.put("prerequisite-edges-soft", prerequisites.softEdges());
        fitness.put("soft-prerequisite-inversions", prerequisites.inversionsAfter());
        // No before-repair key: this engine runs no repair, and a key reporting "the repair removed
        // none" would claim one ran. An absent key is the same statement the disabled fitness terms
        // make on the other path.
        // Named, not counted. A partial plan that says "three topics did not fit" leaves the
        // platform unable to tell the student WHICH three; the scheduled set is a prefix of the
        // study order, so these are exactly the tail nobody reaches.
        fitness.put("topics-unscheduled-ids", prerequisites.unscheduled());
        return Collections.unmodifiableMap(fitness);
    }

    /**
     * A ratio rounded to four decimals.
     *
     * <p>Rounded so that the reported number is short and identical on every run, rather than a
     * seventeen-digit expansion that differs the moment the arithmetic is reassociated.
     */
    private static double ratio(long part, long whole) {
        return Math.round((double) part / whole * 10_000d) / 10_000d;
    }
}
