package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How much each subject is pushing, reduced to the two numbers the ordering reads.
 *
 * <h2>Two goals for the same subject</h2>
 *
 * The contract does not forbid it, and the goal's own identifier is not sent
 * ({@code docs/CORE_CONTRACT_SURVEY.md} §2.1), so two entries for one subject are indistinguishable
 * here. They are merged by taking the <b>highest</b> priority and the <b>earliest</b> target date.
 * Both operations are commutative, which is the property that matters: the result does not depend on
 * the order the goals arrived in, so it cannot become a hidden input to the plan.
 *
 * <h2>A subject with no goal</h2>
 *
 * Priority {@link #NO_GOAL} and no target date. The contract documents priority as 1 to 5, so zero
 * sorts below every declared goal, and a missing date sorts last. A topic can reach this scheduler
 * without a goal for its subject: edges are sent for topics outside the planned subjects, and
 * {@code subjectId} is structurally nullable on both records.
 *
 * <p>Both maps are {@code LinkedHashMap} and are only ever read by key. They tolerate a null
 * {@code subjectId}, which {@code Map.copyOf} would not, and their insertion order is the order of
 * {@code goals} — deterministic, though nothing here iterates them.
 *
 * @param priorityBySubject highest declared priority per subject
 * @param deadlineBySubject earliest declared target date per subject
 */
record GoalPressure(Map<UUID, Integer> priorityBySubject, Map<UUID, LocalDate> deadlineBySubject) {

    /** The priority a topic gets when no goal names its subject. Below every documented value. */
    static final int NO_GOAL = 0;

    /**
     * Reduces the goals of one request.
     *
     * @param goals the goals as sent, possibly with repeated subjects
     * @return the merged pressure, read-only
     */
    static GoalPressure of(List<PlanRequest.Goal> goals) {
        Map<UUID, Integer> priorities = new LinkedHashMap<>();
        Map<UUID, LocalDate> deadlines = new LinkedHashMap<>();
        for (PlanRequest.Goal goal : goals) {
            priorities.merge(goal.subjectId(), goal.priority(), Math::max);
            if (goal.targetDate() != null) {
                deadlines.merge(goal.subjectId(), goal.targetDate(), GoalPressure::earlier);
            }
        }
        return new GoalPressure(Collections.unmodifiableMap(priorities),
                Collections.unmodifiableMap(deadlines));
    }

    private static LocalDate earlier(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    /** The priority pushing on this subject, or {@link #NO_GOAL} when no goal names it. */
    int priorityOf(UUID subjectId) {
        return priorityBySubject.getOrDefault(subjectId, NO_GOAL);
    }

    /** The date the student wants this subject finished by, or {@code null} when none was sent. */
    LocalDate deadlineOf(UUID subjectId) {
        return deadlineBySubject.get(subjectId);
    }
}
