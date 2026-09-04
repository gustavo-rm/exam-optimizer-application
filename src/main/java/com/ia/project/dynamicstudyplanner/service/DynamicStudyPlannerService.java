package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationChains;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationStrategy;
import com.ia.project.dynamicstudyplanner.usecase.GenerateStudyPlanUseCase;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * A high-level orchestrator service that acts as the primary facade for the application's core logic.
 * This service is the main entry point for generating a complete study plan.
 */
@Service
public class DynamicStudyPlannerService implements GenerateStudyPlanUseCase {

    private final StudyOptimizerService optimizerService;
    private final StudyScheduleGenerator scheduleGenerator;
    private final CognitiveLoadCalculator cognitiveLoadCalculator;

    public DynamicStudyPlannerService(StudyOptimizerService optimizerService,
            StudyScheduleGenerator scheduleGenerator, CognitiveLoadCalculator cognitiveLoadCalculator) {
        this.optimizerService = optimizerService;
        this.scheduleGenerator = scheduleGenerator;
        this.cognitiveLoadCalculator = cognitiveLoadCalculator;
    }

    /**
     * Generates a full, end-to-end study plan asynchronously, from strategic optimization to a tactical daily schedule.
     * Executes entirely on a background thread pool specifically tuned for CPU-bound tasks.
     *
     * @param exam The complete {@link Exam} object defining the test structure.
     * @param profile The {@link StudentProfile} object containing the student's personal data.
     * @param totalStudyDays The total number of "ideal" days for the GA to allocate.
     * @param numGenerations The number of generations for the GA to run.
     * @param populationSize The population size for the GA.
     * @return A {@code CompletableFuture} wrapping the {@code FullPlannerResult}.
     */
    @Async("optimizerTaskExecutor")
    public CompletableFuture<FullPlannerResult> generateFullStudyPlan(
            Exam exam,
            StudentProfile profile,
            int totalStudyDays,
            int numGenerations,
            int populationSize
    ) {
        // --- Step 1: Strategic Optimization ---
        OptimizationResult optimizationResult = optimizerService.optimize(
                exam, profile, totalStudyDays, numGenerations, populationSize
        );

        // --- Step 2: Tactical Scheduling ---
        // A composicao da cadeia vive em AllocationChains, e nao aqui, para que os benchmarks e os
        // testes usem exatamente a mesma definicao em vez de replica-la (achado E2 da etapa 03).
        AllocationStrategy finalStrategy = AllocationChains.production(
                cognitiveLoadCalculator.calculate(profile, exam));

        ScheduleResult scheduleResult = scheduleGenerator.generate(
                optimizationResult.plan(), profile, exam, LocalDate.now(), finalStrategy
        );

        return CompletableFuture.completedFuture(new FullPlannerResult(optimizationResult, scheduleResult));
    }
}
