package com.ia.project.dynamicstudyplanner.benchmark.instance;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;

import java.time.LocalDate;

/**
 * A single, fully specified benchmark problem instance.
 * <p>
 * Instances are deterministic: {@link InstanceLibrary} builds them from fixed seeds, so the same
 * instance is byte-identical across runs and machines. This is what makes the comparison between
 * the production GA and the baselines meaningful — every planner sees exactly the same problem.
 *
 * @param id              Short stable identifier used in reports and CSV output.
 * @param description     Human-readable summary of what this instance is meant to represent.
 * @param exam            The exam structure (subjects, weights, deadline).
 * @param profile         The student profile (availability, knowledge gaps, psychological state).
 * @param planStartDate   Fixed anchor date for schedule generation. Never {@code LocalDate.now()},
 *                        so that business metrics are reproducible on any day.
 * @param totalStudyDays  The "ideal" day budget handed to the optimizer (the GA's decision budget).
 * @param numGenerations  GA generations, mirroring what the API client would send.
 * @param populationSize  GA population size, mirroring what the API client would send.
 */
public record BenchmarkInstance(
        String id,
        String description,
        Exam exam,
        StudentProfile profile,
        LocalDate planStartDate,
        int totalStudyDays,
        int numGenerations,
        int populationSize
) {

    /** Number of subjects across both the general-knowledge and specific-knowledge sections. */
    public int subjectCount() {
        return exam.getAllSubjects().size();
    }

    /** Calendar days between the plan start and the exam date. */
    public long horizonDays() {
        return java.time.temporal.ChronoUnit.DAYS.between(planStartDate, exam.getExamDate());
    }
}
