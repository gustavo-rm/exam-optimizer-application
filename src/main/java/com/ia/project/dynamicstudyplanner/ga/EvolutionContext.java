package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Map;
import java.util.HashMap;

/**
 * Encapsulates all the contextual information required for a single evolution step.
 * This avoids "parameter drilling" by bundling related parameters into one object.
 */
public record EvolutionContext(
        Map<Subject, Double> importanceScores,
        Map<Subject, Integer> minimumDaysPerSubject
) {
    public EvolutionContext(Map<Subject, Double> importanceScores, Map<Subject, Integer> minimumDaysPerSubject) {
        this.importanceScores = new HashMap<>(importanceScores);
        this.minimumDaysPerSubject = new HashMap<>(minimumDaysPerSubject);
    }

    @Override
    public Map<Subject, Double> importanceScores() {
        return new HashMap<>(this.importanceScores);
    }

    @Override
    public Map<Subject, Integer> minimumDaysPerSubject() {
        return new HashMap<>(this.minimumDaysPerSubject);
    }
}
