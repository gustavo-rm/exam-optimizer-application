package com.ia.project.dynamicstudyplanner.ga.fitness.penalty;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.domain.fatigue.FatigueAlgorithm;
import org.springframework.stereotype.Component;
import com.ia.project.dynamicstudyplanner.domain.StudentState;

/**
 * Penalizes plans that are unsustainable given the student's psychological and physical state.
 * It integrates the FatigueAlgorithm contract to determine burnout risks.
 * <p>
 * <b>Tactical path only.</b> The macro branch is deliberately neutral: the student's state now
 * reaches the optimizer through the daily budget that {@code CognitiveLoadObjective} scores against,
 * where it can actually distinguish one plan from another. See
 * {@code docs/revisao-ag/05-fitness-function.md} §3.4 and the comment in
 * {@link #calculatePenaltyFactor}.
 */
@Component
public class FatigueAndSustainabilityPenalty implements FitnessPenalty {

    private final FatigueAlgorithm fatigueModel;

    public FatigueAndSustainabilityPenalty(FatigueAlgorithm fatigueModel) {
        this.fatigueModel = fatigueModel;
    }

    @Override
    public double calculatePenaltyFactor(StudyPlan plan, EvolutionContext context) {
        StudentState state = context.studentState();
        if (state == null) {
            return 1.0; // No penalty if state is unknown
        }

        // Note: For full integration, the GA must be evolved to operate on TacticalStudyPlan.
        // This is a bridge implementation. If the context contains a tactical plan, we evaluate it accurately.
        // If not, we fall back to a generic baseline penalty derived from state.

        if (plan instanceof TacticalStudyPlan tacticalPlan) {
            return fatigueModel.calculateBurnoutRisk(tacticalPlan, state);
        }

        // --- Macro (days-based) plans: deliberately neutral ---
        // This branch used to compute a sustainability factor from stress, fatigue and motivation.
        // docs/revisao-ag/01-auditoria-fitness.md Apendice B proved it could not affect any decision:
        // it depended only on the student state and the total day budget, both identical for every
        // individual in a run, so it was a positive constant that left tournament comparisons,
        // weighted-average crossover and stagnation detection untouched. Its only effect was to
        // rescale the number reported to the client.
        //
        // The signal itself was worth keeping, so it moved somewhere it can discriminate: the
        // student's state feeds CognitiveLoadCalculator's daily budget, which CognitiveLoadObjective
        // compares each plan against. A stressed student now gets a smaller budget, and heavy plans
        // lose more fitness than light ones — which is what the term was always meant to express.
        return 1.0;
    }
}
