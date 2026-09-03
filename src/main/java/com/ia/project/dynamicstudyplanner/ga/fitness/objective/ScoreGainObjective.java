package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;

/**
 * O1 — expected score gain on the exam, weighted by each subject's share of the syllabus.
 *
 * <pre>
 *   O1(plan) = SUM_s  normalizedImportance(s) * mastery(s, days_s)
 *   mastery(s, d) = 1 - exp(-d / tau(s))          [LearningModel]
 * </pre>
 *
 * Since the normalised importances sum to 1 and every mastery term lies in [0,1), the objective is
 * bounded in [0,1). Full rationale, weight and citations:
 * {@code docs/revisao-ag/05-fitness-function.md}.
 *
 * <h2>Why a bounded curve replaced ln(1 + d)</h2>
 *
 * The previous form, {@code ln(1 + days)}, captured diminishing returns but is unbounded: it claims
 * knowledge grows without limit as days accumulate, with no mastery ceiling. That is not what the
 * learning-curve literature describes — practice curves approach an asymptote — and it had a
 * concrete consequence measured in docs/revisao-ag/01-auditoria-fitness.md §2.1.2 and §2.1.4:
 * without saturation, the marginal gain of a high-importance subject never falls far enough for a
 * low-importance one to compete, so the optimum concentrated the budget on very few subjects.
 * An exponential approach to an asymptote fixes both problems at once — it saturates, so extra days
 * on an already-mastered subject stop paying, which is what pushes the allocation to spread.
 *
 * <h2>Why tau scales with intrinsic cognitive load</h2>
 *
 * {@code tau} is the number of study days that brings a subject to roughly 63% mastery. Harder
 * material takes longer, so tau grows with {@code Subject.cognitiveLoad}. It lives in
 * {@link LearningModel}, shared with the retention objective so that "how long this subject takes
 * to learn" and "how long its memory survives" cannot drift apart. This is the first point at which
 * {@code cognitiveLoad} influences the optimizer at all: before this change, no active fitness
 * component read it (auditoria §2.5.1).
 */
@Component
public class ScoreGainObjective implements FitnessObjective {

    private static final Logger log = LoggerFactory.getLogger(ScoreGainObjective.class);

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        Map<Subject, Double> importanceScores = context.normalizedImportance();

        // forEach, e nao entrySet: ver a nota sobre o custo do involucro em StudyPlan. O acumulador
        // precisa de um vetor de um elemento porque a lambda nao pode escrever numa variavel local.
        double[] score = {0.0};
        plan.getDaysPerSubject().forEach((subject, days) -> {
            double importance = importanceScores.getOrDefault(subject, 0.0);
            if (importance == 0.0) {
                log.warn("The subject '{}' does not have an importance score.", subject.name());
            }
            score[0] += importance * LearningModel.mastery(subject, days);
        });
        return score[0];
    }

    /**
     * Weight of this objective in the aggregated fitness.
     * <p>
     * The value and its justification live in {@code FitnessWeights}; see
     * {@code docs/revisao-ag/05-fitness-function.md} for the reasoning behind the split.
     */
    @Override
    public double getWeight() {
        return FitnessWeights.SYLLABUS_MASTERY;
    }
}
