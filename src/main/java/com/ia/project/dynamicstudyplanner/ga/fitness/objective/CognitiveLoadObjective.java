package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import org.springframework.stereotype.Component;


/**
 * O4 — how sustainable the plan's daily mental effort is (Sweller).
 *
 * <pre>
 *   expectedDailyLoad = hoursPerStudyDay * SUM_s (days_s / totalDays) * cognitiveLoad(s)
 *   O4(plan)          = 1 - clamp((expectedDailyLoad - budget) / budget, 0, 1)
 * </pre>
 *
 * Bounded in [0,1]: 1 while the plan's expected daily load stays inside the student's budget,
 * falling linearly to 0 once it reaches twice the budget. Full rationale, weight and citations:
 * {@code docs/revisao-ag/05-fitness-function.md}.
 *
 * <h2>What it models</h2>
 *
 * Cognitive load theory holds that working memory has a hard capacity limit, and that instruction
 * exceeding it produces no learning regardless of how much time is spent. {@code Subject.cognitiveLoad}
 * is the intrinsic-load proxy the API already collects (1-5), and {@code CognitiveLoadCalculator}
 * turns the student's availability and psychological state into a sustainable daily budget. This
 * objective compares the two: a plan weighted towards high-load material implies harder average
 * days, and is penalised in proportion to how far past the budget it lands.
 *
 * <h2>Why the student's state enters here rather than as a separate penalty</h2>
 *
 * The budget from {@code CognitiveLoadCalculator} already folds in stress, fatigue and motivation.
 * Routing the state through the budget makes it <em>discriminate between plans</em>: a stressed
 * student gets a smaller budget, so heavy plans lose more fitness than light ones. The previous
 * {@code FatigueAndSustainabilityPenalty} multiplied every individual by the same constant, which
 * docs/revisao-ag/01-auditoria-fitness.md Apendice B proved could not change the ranking of any two
 * plans — it was arithmetic with no effect on the search.
 *
 * <h2>Honest limits</h2>
 *
 * Sweller's construct is about load within a single learning episode, and this term can only speak
 * about the <em>expected</em> daily load implied by the mix of subjects, because the macro
 * chromosome has no calendar (docs/revisao-ag/01-auditoria-fitness.md §3.3). A plan can satisfy this
 * objective and still produce individual overloaded days. Enforcing a real per-day cap needs a
 * time-indexed encoding; until then the tactical layer's
 * {@code CognitiveLoadBalancingStrategy} remains the only per-day guard, and it enforces the cap by
 * dropping blocks rather than by planning around them.
 */
@Component
public class CognitiveLoadObjective implements FitnessObjective {

    /**
     * Multiple of the budget at which the objective bottoms out at zero.
     * <p>
     * Two means "twice the sustainable daily load is as bad as this term can say". Beyond that the
     * plan is already scoring zero here and further overload is indistinguishable — a deliberate
     * floor, so that one catastrophic term cannot swamp the other objectives in the weighted sum.
     */
    private static final double OVERLOAD_RATIO_AT_ZERO = 1.0;

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        int totalDays = plan.getTotalDays();
        int budget = context.maxDailyCognitiveLoad();
        if (totalDays <= 0 || budget <= 0) {
            return 1.0;
        }

        // forEach, e nao entrySet: ver a nota sobre o custo do involucro em StudyPlan.
        double[] weightedLoad = {0.0};
        plan.getDaysPerSubject().forEach((subject, days) ->
                weightedLoad[0] += days * (double) subject.cognitiveLoad());

        double expectedDailyLoad = context.hoursPerStudyDay() * weightedLoad[0] / totalDays;
        double overload = (expectedDailyLoad - budget) / budget;

        return 1.0 - Math.clamp(overload, 0.0, OVERLOAD_RATIO_AT_ZERO);
    }

    @Override
    public double getWeight() {
        return FitnessWeights.COGNITIVE_LOAD;
    }
}
