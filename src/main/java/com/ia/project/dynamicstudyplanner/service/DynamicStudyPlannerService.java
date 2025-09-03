package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.*;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.CognitiveLoadBalancingStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.InterleavedCriticalStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.ReviewFocusedStrategy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * A high-level orchestrator service that acts as the primary facade for the application's core logic.
 * This service is the main entry point for generating a complete study plan.
 */
@Service
public final class DynamicStudyPlannerService {

    private final StudyOptimizerService optimizerService;
    private final StudyScheduleGenerator scheduleGenerator;
    private final CognitiveLoadCalculator cognitiveLoadCalculator;

    public DynamicStudyPlannerService(StudyOptimizerService optimizerService, StudyScheduleGenerator scheduleGenerator, CognitiveLoadCalculator cognitiveLoadCalculator) {
        this.optimizerService = optimizerService;
        this.scheduleGenerator = scheduleGenerator;
        this.cognitiveLoadCalculator = cognitiveLoadCalculator;
    }

    /**
     * Generates a full, end-to-end study plan, from strategic optimization to a tactical daily schedule.
     *
     * @param exam The complete {@link Exam} object defining the test structure.
     * @param profile The {@link StudentProfile} object containing the student's personal data.
     * @param totalStudyDays The total number of "ideal" days for the GA to allocate.
     * @param numGenerations The number of generations for the GA to run.
     * @param populationSize The population size for the GA.
     * @return A {@code FullPlannerResult} containing both the strategic and tactical planning results.
     */
    public FullPlannerResult generateFullStudyPlan(
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
        // 1. The base strategy decides WHICH subjects to study today (the most critical ones)
        // 2. The load balancing strategy decides HOW to organize these subjects
        // 3. The review strategy decides whether a REVIEW should come first

        AllocationStrategy finalStrategy = new ReviewFocusedStrategy(
                new CognitiveLoadBalancingStrategy(
                        new InterleavedCriticalStrategy(),
                        cognitiveLoadCalculator.calculate(profile, exam)
                )
        );

        ScheduleResult scheduleResult = scheduleGenerator.generate(
                optimizationResult.plan(), profile, exam, LocalDate.now(), finalStrategy
        );

        return new FullPlannerResult(optimizationResult, scheduleResult);
    }
}