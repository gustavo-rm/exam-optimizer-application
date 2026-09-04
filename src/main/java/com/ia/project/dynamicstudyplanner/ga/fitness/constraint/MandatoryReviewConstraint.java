package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * Checks if a generated study plan fails to include mandatory spaced repetition reviews.
 * <p>
 * <b>Tactical path only.</b> It short-circuits to valid for a plain {@code StudyPlan}, so it is
 * neutral in the macro path by design. Retention reaches the macro fitness through
 * {@code RetentionObjective} instead, which is graded rather than binary. See
 * {@code docs/revisao-ag/05-fitness-function.md} §2 and §3.3.
 */
@Component
public class MandatoryReviewConstraint implements ConstraintValidator {

    private final RetentionAlgorithm retentionAlgorithm;

    public MandatoryReviewConstraint(RetentionAlgorithm retentionAlgorithm) {
        this.retentionAlgorithm = retentionAlgorithm;
    }

    @Override
    public boolean isValid(StudyPlan plan, EvolutionContext context) {
        if (!(plan instanceof TacticalStudyPlan tacticalPlan) || context.retentionProfile() == null) {
            return true; // Not applicable for macro-plans without retention tracking
        }

        Set<Subject> reviewedSubjects = new HashSet<>();

        // Find what subjects actually have spaced repetition scheduled in this plan
        for (TacticalStudyBlock block : tacticalPlan.getSchedule().values()) {
            if (block.methodology() == StudyMethodology.SPACED_REPETITION_REVIEW) {
                reviewedSubjects.add(block.subject());
            }
        }

        // Check if any subject required a review but was missing from the set
        for (Subject subject : context.importanceScores().keySet()) {
            boolean mandatory = retentionAlgorithm.isReviewMandatory(subject,
                    context.retentionProfile().getState(subject), context.planStartDate());
            if (mandatory && !reviewedSubjects.contains(subject)) {
                return false; // Constraint Violated: A mandatory review was missed
            }
        }

        return true; // All mandatory reviews are present
    }
}
