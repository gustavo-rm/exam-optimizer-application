package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Objective to maximize the expected score gain based on allocated study days.
 * <p>
 * Full rationale, formula and weight justification: {@code docs/revisao-ag/05-fitness-function.md}.
 * <p>
 * Importance is read from {@link EvolutionContext#normalizedImportance()} rather than the raw
 * scores. Raw importance carries the exam's own units, and the API's validation limits let two
 * subjects in the same sum differ by up to 250,000x, which made the allocation winner-take-all
 * (docs/revisao-ag/01-auditoria-fitness.md §2.1.4). Normalising also makes the objective's scale
 * independent of the exam, so several objectives can be summed without one drowning the others.
 */
@Component
public class ScoreGainObjective implements FitnessObjective {

    private static final Logger log = LoggerFactory.getLogger(ScoreGainObjective.class);

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

            // A logarithmic function models the diminishing returns of studying.
            double knowledge = Math.log(1.0 + days);
            score += knowledge * importance;
        }
        return score;
    }

    @Override
    public double getWeight() {
        return 1.0; // Base weight
    }
}
