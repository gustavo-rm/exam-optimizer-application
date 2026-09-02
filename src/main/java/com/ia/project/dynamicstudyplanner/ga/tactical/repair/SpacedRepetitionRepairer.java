package com.ia.project.dynamicstudyplanner.ga.tactical.repair;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * A repair heuristic that forcibly overwrites existing blocks with mandatory review blocks
 * if the crossover/mutation algorithms produced a plan that failed the MandatoryReviewConstraint.
 */
@Component
public class SpacedRepetitionRepairer implements ChromosomeRepairer {

    private final RetentionAlgorithm retentionAlgorithm;

    public SpacedRepetitionRepairer(RetentionAlgorithm retentionAlgorithm) {
        this.retentionAlgorithm = retentionAlgorithm;
    }

    @Override
    public TacticalStudyPlan repair(TacticalStudyPlan plan, EvolutionContext context) {
        if (context.retentionProfile() == null) {
            return plan;
        }

        Map<TimeSlot, TacticalStudyBlock> newSchedule = new HashMap<>(plan.getSchedule());

        for (Subject subject : context.importanceScores().keySet()) {
            boolean mandatory = retentionAlgorithm.isReviewMandatory(subject, context.retentionProfile().getState(subject), context.planStartDate());

            if (mandatory) {
                // Check if it exists
                boolean found = newSchedule.values().stream()
                        .anyMatch(b -> b.subject().equals(subject) && b.methodology() == StudyMethodology.SPACED_REPETITION_REVIEW);

                if (!found) {
                    // Repair Strategy: Find the lowest ROI block and overwrite it with the mandatory review
                    TimeSlot targetSlot = null;
                    double lowestScore = Double.MAX_VALUE;

                    for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : newSchedule.entrySet()) {
                        // Very simplified heuristic for finding a "weak" block to replace
                        double score = entry.getValue().subject().cognitiveLoad() * entry.getValue().methodology().getExpectedRetentionMultiplier();
                        if (score < lowestScore) {
                            lowestScore = score;
                            targetSlot = entry.getKey();
                        }
                    }

                    if (targetSlot != null) {
                        newSchedule.put(targetSlot, new TacticalStudyBlock(subject, StudyMethodology.SPACED_REPETITION_REVIEW, targetSlot.getDurationMinutes()));
                    }
                }
            }
        }

        return new TacticalStudyPlan(newSchedule);
    }
}
