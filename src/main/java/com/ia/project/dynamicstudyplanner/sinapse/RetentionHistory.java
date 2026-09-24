package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.domain.retention.ItemRetentionState;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the student already knows, reconstructed from a ninety-day window.
 *
 * <h2>The ambiguity this class has to answer</h2>
 *
 * The platform's history covers <b>ninety days</b>. So a topic missing from {@code history} means one
 * of two things and the payload cannot tell them apart: never studied, or last studied more than
 * ninety days ago. The distinction matters because the answer feeds {@code RetentionObjective} and
 * {@code MandatoryReviewConstraint}, and those read a forgetting curve.
 *
 * <h2>Project assumption: absence is treated as new ground</h2>
 *
 * A topic with no usable history gets <b>no retention state</b>, which
 * {@code HybridRetentionEngine.isReviewMandatory} answers with {@code true} — study it.
 *
 * <p>The alternative is to assume "more than ninety days ago", and that requires <b>inventing a
 * {@code lastStudiedAt}</b>. The retention probability is {@code exp(-daysSince / stability)}: a
 * fabricated date is not a small inaccuracy there, it is the term's most sensitive input chosen by
 * us rather than observed. Treating absence as new ground invents nothing and errs in the cheaper
 * direction — re-teaching material the student already knows costs time, while skipping material
 * they never saw costs the exam. The asymmetry is the argument; it is not that "never" is more
 * likely.
 *
 * <p>Recorded in {@code docs/SINAPSE_ADAPTER.md} §3 and pinned by {@code SinapseAssumptionsTest}.
 *
 * <h2>A topic present with nothing recorded is the same case</h2>
 *
 * The contract distinguishes a topic absent from {@code history} from one present with a null
 * {@code lastStudiedAt} — the second had a session that never closed with a recorded duration. Both
 * arrive here without a date, so both are new ground. The states differ in the platform; they do not
 * differ in what this side can compute from them.
 *
 * <h2>How the state is rebuilt: the project's own SM-2, replayed</h2>
 *
 * The ratings are folded through {@code RetentionAlgorithm.processReview}, the recurrence the
 * tactical engine already uses, rather than through arithmetic invented here. The fold is exact
 * despite having only one date: {@code processReview} uses {@code reviewDate} only as the date it
 * stores, so replaying every rating at {@code lastStudiedAt} produces the same repetition count,
 * easiness factor and interval as replaying them on their real dates would, and stores the one date
 * the payload does carry.
 */
public final class RetentionHistory {

    private RetentionHistory() {
    }

    /**
     * Builds the retention profile the evolution context carries.
     *
     * @param topics  the topics as they arrived, for the item each history entry belongs to
     * @param history the history as it arrived
     * @param model   the recurrence to replay the ratings through
     * @return a profile holding a state only for topics with a usable last-studied date
     */
    public static RetentionProfile of(List<PlanRequest.Topic> topics,
            List<PlanRequest.TopicHistory> history, RetentionAlgorithm model) {

        Map<UUID, PlanningItem> itemsByTopic = new LinkedHashMap<>();
        topics.forEach(topic -> itemsByTopic.put(topic.id(), TopicPlanningItems.toItem(topic)));

        Map<PlanningItem, ItemRetentionState> states = new LinkedHashMap<>();
        for (PlanRequest.TopicHistory entry : history) {
            PlanningItem item = itemsByTopic.get(entry.topicId());
            if (item == null || entry.lastStudiedAt() == null) {
                // A history entry for a topic outside this plan, or one with nothing usable
                // recorded. Neither produces a state; see the class comment.
                continue;
            }
            states.put(item, rebuild(entry, model));
        }
        return new RetentionProfile(states);
    }

    /** Folds one topic's ratings through the SM-2 recurrence, ending at its last studied date. */
    private static ItemRetentionState rebuild(PlanRequest.TopicHistory entry,
            RetentionAlgorithm model) {

        LocalDate lastStudied = LocalDate.ofInstant(entry.lastStudiedAt(), ZoneOffset.UTC);
        ItemRetentionState state = new ItemRetentionState(lastStudied);
        for (RecallRating rating : entry.recallRatings()) {
            state = model.processReview(state, lastStudied, gradeOf(rating));
        }
        return state;
    }

    /**
     * The four ratings as SM-2 performance grades.
     *
     * <pre>
     *   AGAIN -> 1     HARD -> 2     GOOD -> 4     EASY -> 5
     * </pre>
     *
     * <p>Chosen so that the recurrence keeps its own semantics rather than to spread the four values
     * evenly. {@code processReview} treats {@code grade >= 3} as successful recall, which puts
     * {@code AGAIN} and {@code HARD} on the failing side. Its easiness update is
     * {@code EF + (0.1 - (5 - q)(0.08 + (5 - q) 0.02))}, which is exactly zero at {@code q = 4} and
     * positive only at {@code q = 5} — so {@code GOOD} leaves the topic as easy as it was and only
     * {@code EASY} makes it easier. Mapping {@code EASY} to 4 would have made an easy recall
     * indistinguishable from an unremarkable one.
     *
     * <p>A fifth assumption of the adapter, recorded with the others.
     */
    private static int gradeOf(RecallRating rating) {
        return switch (rating) {
            case AGAIN -> 1;
            case HARD -> 2;
            case GOOD -> 4;
            case EASY -> 5;
        };
    }
}
