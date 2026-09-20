package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which topics get a {@code REVISION} session, by one stated rule.
 *
 * <h2>The rule, in one sentence</h2>
 *
 * A topic gets exactly one {@code REVISION} session, of half its estimated minutes rounded up, when
 * the request carries a history entry for it whose most recent recall rating is {@code AGAIN} or
 * {@code HARD} and whose {@code lastStudiedAt} is at least {@value #STALE_AFTER_DAYS} days before the
 * first day of the horizon; every other topic gets none.
 *
 * <h2>The rule is flat on purpose</h2>
 *
 * The most recent rating, not a trend. A fixed threshold, not a decay curve. One revision, not a
 * schedule of them. Placed directly after its own study session, with no spacing model. Each of those
 * is a modelling decision worth making — and each is a decision the genetic algorithm exists to make,
 * against a fitness function, with the retention engine this repository already has. A baseline that
 * anticipated them would stop being the control condition it is here to be. What this component owes
 * the experiment is predictability: given a request, a reader can work out the revision sessions by
 * hand.
 *
 * <h2>Design assumption: no history entry means "not studied", and the 90-day window is why</h2>
 *
 * <b>The absence of history is ambiguous in the platform, and the two readings want opposite
 * treatments.</b> {@code history} only covers the platform's configured window —
 * {@code sinapse.planning.generation.history-window}, 90 days
 * ({@code docs/CORE_CONTRACT_SURVEY.md} §4). A topic with no entry was either never studied, which
 * calls for no revision, or was last studied more than 90 days ago, which is the strongest case for
 * one. Nothing in the request distinguishes them.
 *
 * <p><b>The choice made here: treat the absence as "not studied", and schedule no revision.</b> Not
 * because it is more likely, but because the cost of being wrong is bounded and falls the safe way. A
 * topic with no entry still gets a full {@code STUDY} session of its whole
 * {@code estimatedMinutes} — which is <i>more</i> time than the half-length revision it would have
 * received under the other reading. So a topic genuinely last studied 91 days ago is not skipped; it
 * is planned as new ground, which for material that has been decaying for three months is close to
 * what it needs anyway. The opposite choice has no such floor: it would spend the student's scarce
 * hours revising topics they have never seen, and the {@code REVISION} label would tell them to
 * expect familiar material.
 *
 * <p>This is an assumption, not a finding. It is asserted by {@code RevisionPolicyTest}, so changing
 * it is a visible decision rather than a silent drift, and the rating-and-recency rule is the first
 * thing to revisit if the platform ever sends the age of the window along with the history.
 *
 * <h2>The reference instant comes from the request</h2>
 *
 * "Old" is measured against the first day of the horizon, read at {@code 00:00Z}, and never against
 * {@code Instant.now()}. Wall-clock time on the decision path would make two runs of the same request
 * differ, which is the one thing this scheduler may not do.
 */
final class RevisionPolicy {

    /** How old {@code lastStudiedAt} has to be, in days, for a revision to be scheduled. */
    static final int STALE_AFTER_DAYS = 14;

    /** History by topic. Only ever read by key; the first entry for a topic wins. */
    private final Map<UUID, PlanRequest.TopicHistory> byTopic;

    /** First instant of the horizon. The only notion of "now" this scheduler has. */
    private final Instant reference;

    private RevisionPolicy(Map<UUID, PlanRequest.TopicHistory> byTopic, Instant reference) {
        this.byTopic = byTopic;
        this.reference = reference;
    }

    /**
     * Reads the history of one request.
     *
     * <p>A repeated {@code topicId} keeps the first entry. The contract does not forbid a repeat and
     * carries no way to tell two entries apart, so one of them has to win; taking the first makes the
     * outcome a function of the document rather than of a merge rule nobody asked for.
     *
     * @param request the request, already accepted by {@link PlanRequestGuard}
     * @return the policy for this request
     */
    static RevisionPolicy of(PlanRequest request) {
        Map<UUID, PlanRequest.TopicHistory> byTopic = new LinkedHashMap<>();
        request.history().forEach(entry -> byTopic.putIfAbsent(entry.topicId(), entry));
        return new RevisionPolicy(byTopic, HorizonBounds.of(request.horizon()).from());
    }

    /** Whether this topic earns a revision session. See the class comment for the whole rule. */
    boolean needsRevision(UUID topicId) {
        PlanRequest.TopicHistory history = byTopic.get(topicId);
        if (history == null) {
            return false;
        }
        return studiedLongAgo(history) && recalledBadly(history);
    }

    /** How long a revision lasts: half the study estimate, rounded up, never less than a minute. */
    static int revisionMinutes(int estimatedMinutes) {
        return Math.max(1, (estimatedMinutes + 1) / 2);
    }

    private boolean studiedLongAgo(PlanRequest.TopicHistory history) {
        Instant lastStudiedAt = history.lastStudiedAt();
        return lastStudiedAt != null
                && !lastStudiedAt.isAfter(reference.minus(STALE_AFTER_DAYS, ChronoUnit.DAYS));
    }

    /**
     * Whether the student's most recent judgement of this topic was a poor one.
     *
     * <p>{@code recallRatings} is oldest first ({@code docs/CORE_CONTRACT_SURVEY.md} §2.1), so the
     * last element is the most recent. An entry with no ratings at all says nothing about recall and
     * earns no revision — it is the platform's "studied, but no session closed with a recorded
     * duration" state.
     */
    private static boolean recalledBadly(PlanRequest.TopicHistory history) {
        List<RecallRating> ratings = history.recallRatings();
        if (ratings.isEmpty()) {
            return false;
        }
        RecallRating latest = ratings.get(ratings.size() - 1);
        return latest == RecallRating.AGAIN || latest == RecallRating.HARD;
    }
}
