package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Map;

/**
 * Encapsulates all the contextual information required for a single evolution step.
 * This avoids "parameter drilling" by bundling related parameters into one object.
 */
public record EvolutionContext(
        Map<Subject, Double> importanceScores,
        Map<Subject, Integer> minimumDaysPerSubject,
        com.ia.project.dynamicstudyplanner.domain.StudentState studentState
) {}
