package com.ia.project.dynamicstudyplanner.usecase;

import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;

import java.util.concurrent.CompletableFuture;

/**
 * Port representing the primary use case of the application:
 * Generating a complete, optimized study plan.
 */
public interface GenerateStudyPlanUseCase {
    CompletableFuture<FullPlannerResult> generateFullStudyPlan(Exam exam, StudentProfile profile,
            int totalStudyDays, int numGenerations, int populationSize);
}
