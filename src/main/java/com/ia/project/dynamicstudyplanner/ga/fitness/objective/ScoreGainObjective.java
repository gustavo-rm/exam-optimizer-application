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
 */
@Component
public class ScoreGainObjective implements FitnessObjective {

    private static final Logger log = LoggerFactory.getLogger(ScoreGainObjective.class);

    @Override
    public double calculateReward(StudyPlan plan, EvolutionContext context) {
        double score = 0.0;
        Map<Subject, Double> importanceScores = context.importanceScores();

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
