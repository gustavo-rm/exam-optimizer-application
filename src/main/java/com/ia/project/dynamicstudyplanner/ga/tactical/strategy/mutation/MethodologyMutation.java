package com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation;

import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;

import java.util.HashMap;
import java.util.Map;
import com.ia.project.dynamicstudyplanner.util.RandomProvider;

/**
 * A mutation strategy that changes the study methodology of a block (e.g., from reading to active recall).
 * This allows the algorithm to find the optimal intensity for a given time slot.
 */
public class MethodologyMutation implements TacticalMutationStrategy {


    @Override
    public TacticalStudyPlan mutate(TacticalStudyPlan plan, double mutationRate, EvolutionContext context) {
        Map<TimeSlot, TacticalStudyBlock> newSchedule = new HashMap<>(plan.getSchedule());

        for (Map.Entry<TimeSlot, TacticalStudyBlock> entry : newSchedule.entrySet()) {
            if (RandomProvider.getInstance().nextDouble() < mutationRate) {
                TacticalStudyBlock block = entry.getValue();
                StudyMethodology[] methodologies = StudyMethodology.values();
                StudyMethodology newMethod = methodologies[RandomProvider.getInstance().nextInt(methodologies.length)];

                // Do not mutate into Spaced Repetition randomly, that is controlled by the RetentionEngine
                if (newMethod != StudyMethodology.SPACED_REPETITION_REVIEW) {
                    newSchedule.put(entry.getKey(), new TacticalStudyBlock(block.subject(), newMethod, block.durationMinutes()));
                }
            }
        }

        return new TacticalStudyPlan(newSchedule);
    }
}
