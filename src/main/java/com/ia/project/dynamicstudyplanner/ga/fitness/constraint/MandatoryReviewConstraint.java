package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Penalises a plan for every item that was due a spaced-repetition review and did not get one.
 *
 * <h2>Tactical path only</h2>
 *
 * It short-circuits to severity zero for a plain {@code StudyPlan}, so it is neutral in the macro
 * path <b>by design</b> and not by omission: the macro chromosome is a session count per item with
 * no calendar, so it cannot express "this session is a review" and therefore cannot violate this.
 * Retention reaches the macro fitness through {@code RetentionObjective} instead, which is graded.
 * Same reasoning as {@code SoftPrerequisiteOrderConstraint}; see {@code CLAUDE.md} §1b.
 *
 * <h2>Why the severity is graded, and what it cost while it was binary (G14)</h2>
 *
 * It used to return {@code false} on the first item that missed a mandatory review, which the
 * default {@link ConstraintValidator#violationSeverity} turned into a flat severity of 1. At a
 * penalty weight of {@code 0.50} that subtracted half a point from <b>every</b> plan that missed
 * <b>any</b> review — a plan that reviewed 24 of 25 topics paid exactly what a plan that reviewed
 * none paid.
 *
 * <p>That is not a style objection, it was measured. Across the 720 runs of
 * {@code docs/revisao-ag/09-medicao-baseline-vs-v1.md}, the binary severity read 1,0 in
 * <b>720 of 720</b>, the three objectives can attain at most {@code 0,5769}, and the aggregate
 * therefore clamped to zero in <b>94,6%</b> of them. The published fitness could not tell two
 * materially different plans apart.
 *
 * <p>On the macro path that was invisible, because this term short-circuits there and the search
 * never saw it. It becomes decisive the moment a search evolves <b>tactical</b> plans, which is what
 * the timeline chromosome does: every candidate would score exactly zero, tournament selection would
 * be choosing at random, and the experiment would report "no difference" for a reason that has
 * nothing to do with the representation under test.
 *
 * <p>So the severity is now the <b>fraction of due reviews that were missed</b>, the same shape
 * {@link MinimumDaysConstraint} uses. A plan missing one review of twenty scores 0,05 and a plan
 * missing all twenty scores 1,0, which is the gradient a search needs to walk back to feasibility
 * instead of only learning that it is somewhere outside it.
 *
 * <p><b>The weights did not change.</b> Only the magnitude this term reports did, so no
 * renormalisation follows and every objective weight is where it was.
 */
@Component
public class MandatoryReviewConstraint implements ConstraintValidator {

    private final RetentionAlgorithm retentionAlgorithm;

    public MandatoryReviewConstraint(RetentionAlgorithm retentionAlgorithm) {
        this.retentionAlgorithm = retentionAlgorithm;
    }

    @Override
    public boolean isValid(StudyPlan plan, EvolutionContext context) {
        // Delegates so that "valid" and "severity zero" cannot drift apart into two answers.
        return violationSeverity(plan, context) == 0.0;
    }

    /**
     * Due reviews that were missed, over due reviews.
     *
     * @param plan    the plan to judge
     * @param context the context, for the retention state and the items in scope
     * @return 0 when every due review is scheduled, 1 when none is, and the fraction in between;
     *         0 for a macro plan and 0 when nothing is due, which are different facts with the same
     *         severity because neither is a violation
     */
    @Override
    public double violationSeverity(StudyPlan plan, EvolutionContext context) {
        if (!(plan instanceof TacticalStudyPlan tactical) || context.retentionProfile() == null) {
            return 0.0;
        }

        Set<PlanningItem> reviewed = reviewedItems(tactical);
        int due = 0;
        int missed = 0;
        for (PlanningItem item : context.importanceScores().keySet()) {
            if (isDue(item, context)) {
                due++;
                if (!reviewed.contains(item)) {
                    missed++;
                }
            }
        }

        // Integer division guarded, and the ratio is over DUE reviews rather than over every item:
        // an item that needs no review must not dilute the severity of one that was skipped.
        return due == 0 ? 0.0 : missed / (double) due;
    }

    /** Whether this item is due a review by the plan's start date. */
    private boolean isDue(PlanningItem item, EvolutionContext context) {
        return retentionAlgorithm.isReviewMandatory(item,
                context.retentionProfile().getState(item), context.planStartDate());
    }

    /**
     * Items carrying at least one review block.
     *
     * <p>A {@code HashSet} is safe here: it is read only through {@code contains}, so its iteration
     * order never reaches a count or a sum. Compare {@code EvolutionContext.normalize}, where map
     * order does reach a floating-point sum and a hash-ordered map is a defect.
     */
    private static Set<PlanningItem> reviewedItems(TacticalStudyPlan plan) {
        Set<PlanningItem> reviewed = new HashSet<>();
        for (TacticalStudyBlock block : plan.getSchedule().values()) {
            if (block.methodology() == StudyMethodology.SPACED_REPETITION_REVIEW) {
                reviewed.add(block.item());
            }
        }
        return reviewed;
    }
}
