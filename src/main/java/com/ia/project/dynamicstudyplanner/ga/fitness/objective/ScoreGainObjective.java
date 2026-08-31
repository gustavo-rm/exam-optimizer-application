package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * O1 — expected score gain on the exam, weighted by each subject's share of the syllabus.
 *
 * <pre>
 *   O1(plan) = SUM_s  normalizedImportance(s) * mastery(s, days_s)
 *   mastery(s, d) = 1 - exp(-d / tau(s))
 *   tau(s) = TAU_AT_AVERAGE_LOAD * cognitiveLoad(s) / AVERAGE_COGNITIVE_LOAD
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
 * material takes longer, so tau grows with {@code Subject.cognitiveLoad}, anchored so that a subject
 * of average difficulty (load 3, the same average {@code CognitiveLoadCalculator} assumes) needs
 * about 10 days. This is the first point at which {@code cognitiveLoad} influences the optimizer at
 * all: before this change, no active fitness component read it (auditoria §2.5.1).
 */
@Component
public class ScoreGainObjective implements FitnessObjective {

    private static final Logger log = LoggerFactory.getLogger(ScoreGainObjective.class);

    /**
     * Study days for an average-difficulty subject to reach ~63% mastery (one time constant).
     * Twice that, about 20 days, reaches ~86%. Chosen to be consistent with the 15-day ceiling
     * {@code BaselineCalculator} already uses for its hardest-subject floor, so the two components
     * describe study effort on the same scale.
     */
    private static final double TAU_AT_AVERAGE_LOAD = 10.0;

    /**
     * The cognitive load treated as average. Matches {@code CognitiveLoadCalculator}'s
     * {@code AVERAGE_LOAD_FACTOR}, so "average difficulty" means the same thing in both places.
     */
    private static final double AVERAGE_COGNITIVE_LOAD = 3.0;

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        double score = 0.0;
        Map<Subject, Double> importanceScores = context.normalizedImportance();

        for (Map.Entry<Subject, Integer> entry : plan.getDaysPerSubject().entrySet()) {
            Subject subject = entry.getKey();
            int days = entry.getValue();

            double importance = importanceScores.getOrDefault(subject, 0.0);
            if (importance == 0.0) {
                log.warn("The subject '{}' does not have an importance score.", subject.name());
            }

            score += importance * mastery(subject, days);
        }
        return score;
    }

    /**
     * Fraction of the subject mastered after {@code days} of study, in [0,1).
     * <p>
     * Exponential approach to an asymptote: fast early gains, saturating as the subject is learned.
     */
    private static double mastery(Subject subject, int days) {
        if (days <= 0) {
            return 0.0;
        }
        return 1.0 - Math.exp(-days / timeConstant(subject));
    }

    /** Days to reach ~63% mastery, growing with the subject's intrinsic difficulty. */
    private static double timeConstant(Subject subject) {
        int load = Math.max(1, subject.cognitiveLoad());
        return TAU_AT_AVERAGE_LOAD * load / AVERAGE_COGNITIVE_LOAD;
    }

    /**
     * Weight of this objective in the aggregated fitness.
     * <p>
     * The value and its justification live in {@code FitnessWeights}; see
     * {@code docs/revisao-ag/05-fitness-function.md} for the reasoning behind the split.
     */
    @Override
    public double getWeight() {
        return com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights.SYLLABUS_MASTERY;
    }
}
