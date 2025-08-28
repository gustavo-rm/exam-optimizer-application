package com.ia.project.dynamicstudyplanner.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudyBlock;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationContext;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationStrategy;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;

/**
 * Generates a realistic, day-by-day study schedule by orchestrating a series of steps:
 * 1. Performing a viability analysis of the ideal plan against available time.
 * 2. Adjusting the plan if necessary.
 * 3. Iterating through the days and delegating hour allocation to a chosen strategy.
 */
public class StudyScheduleGenerator {

    /**
     * Creates a realistic, day-by-day study schedule. This is the main public method
     * that orchestrates the entire generation process.
     *
     * @param plan The optimized StudyPlan from the GA.
     * @param profile The student's profile, containing weekly availability.
     * @param exam The Exam object, containing the final deadline.
     * @param startDate The date to start the study plan.
     * @param hoursPerStudyDay The conversion factor from "study days" to hours.
     * @param allocationStrategy The chosen strategy for allocating daily study hours.
     * @return A ScheduleResult containing the schedule and the outcome status.
     */
    public ScheduleResult generate(
            StudyPlan plan,
            StudentProfile profile,
            Exam exam,
            LocalDate startDate,
            int hoursPerStudyDay,
            AllocationStrategy allocationStrategy
    ) {
        // --- 1. VIABILITY ANALYSIS & PLAN ADJUSTMENT ---
        ScheduleContext context = prepareScheduleContext(plan, profile, startDate, exam.examDate(), hoursPerStudyDay);

        // --- 2. DAILY SCHEDULE GENERATION LOOP ---
        Map<LocalDate, List<StudyBlock>> schedule = buildSchedule(profile, exam.examDate(), startDate, context, allocationStrategy);

        // --- 3. RETURN FINAL RESULT ---
        return new ScheduleResult(schedule, context.status(), context.requiredHours(), context.availableHours());
    }

    /**
     * A private record to hold the context for the schedule generation after the
     * initial viability analysis.
     */
    private record ScheduleContext(
            Map<Subject, Double> hoursToSchedule,
            ScheduleStatus status,
            double requiredHours,
            double availableHours
    ) {}

    /**
     * Performs the viability analysis by comparing required hours vs. available hours
     * and adjusts the study plan if there is a time deficit.
     *
     * @return A ScheduleContext object containing the adjusted plan and status.
     */
    private ScheduleContext prepareScheduleContext(
            StudyPlan plan, StudentProfile profile, LocalDate startDate, LocalDate examDate, int hoursPerStudyDay
    ) {
        double availableHours = calculateTotalAvailableHours(profile, startDate, examDate);
        double requiredHours = plan.daysPerSubject().values().stream()
                .mapToDouble(days -> days * hoursPerStudyDay)
                .sum();

        Map<Subject, Double> hoursToSchedulePerSubject;
        ScheduleStatus status;

        if (availableHours >= requiredHours) {
            status = (availableHours > requiredHours) ?
                    ScheduleStatus.SUCCESS_WITH_SURPLUS_TIME : ScheduleStatus.SUCCESS_IDEAL_PLAN;
            hoursToSchedulePerSubject = plan.daysPerSubject().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> (double) (e.getValue() * hoursPerStudyDay)));
        } else {
            status = ScheduleStatus.WARNING_TIME_DEFICIT;
            double reductionFactor = availableHours / requiredHours;
            hoursToSchedulePerSubject = plan.daysPerSubject().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> (e.getValue() * hoursPerStudyDay) * reductionFactor));
        }
        return new ScheduleContext(hoursToSchedulePerSubject, status, requiredHours, availableHours);
    }

    /**
     * The main loop that iterates from the start date to the exam date, building the schedule day by day.
     *
     * @param profile The student's profile.
     * @param examDate The deadline.
     * @param startDate The first day of the schedule.
     * @param context The prepared schedule context with the adjusted plan.
     * @param allocationStrategy The strategy for daily hour allocation.
     * @return The generated schedule as a map of dates to study blocks.
     */
    private Map<LocalDate, List<StudyBlock>> buildSchedule(
            StudentProfile profile, LocalDate examDate, LocalDate startDate, ScheduleContext context, AllocationStrategy allocationStrategy
    ) {
        Map<LocalDate, List<StudyBlock>> schedule = new LinkedHashMap<>();
        LocalDate currentDate = startDate;
        Map<Subject, LocalDate> lastStudiedDateMap = new HashMap<>();

        while (currentDate.isBefore(examDate) && context.hoursToSchedule().values().stream().anyMatch(h -> h > 0.5)) {
            int availableHoursToday = profile.weeklyAvailability().getOrDefault(currentDate.getDayOfWeek(), 0);

            if (availableHoursToday > 0) {
                // Delegate the allocation logic to the chosen strategy
                List<StudyBlock> dailyBlocks = allocationStrategy.allocateHours(
                        new AllocationContext(availableHoursToday, context.hoursToSchedule(), lastStudiedDateMap)
                );

                // If any blocks were created, consolidate them and update the state
                if (!dailyBlocks.isEmpty()) {
                    List<StudyBlock> consolidatedBlocks = consolidateBlocks(dailyBlocks);
                    schedule.put(currentDate, consolidatedBlocks);
                    LocalDate finalCurrentDate = currentDate;
                    consolidatedBlocks.forEach(block -> lastStudiedDateMap.put(block.subject(), finalCurrentDate));
                }
            }
            currentDate = currentDate.plusDays(1);
        }
        return schedule;
    }

    /**
     * Consolidates a list of 1-hour study blocks into larger blocks for better readability.
     * For example, [ (Math, 1), (Math, 1) ] becomes [ (Math, 2) ].
     *
     * @param dailyBlocks A list of potentially fragmented study blocks.
     * @return A list of consolidated study blocks.
     */
    private List<StudyBlock> consolidateBlocks(List<StudyBlock> dailyBlocks) {
        return new ArrayList<>(dailyBlocks.stream()
                .collect(Collectors.groupingBy(StudyBlock::subject, Collectors.summingInt(StudyBlock::hours)))
                .entrySet().stream()
                .map(entry -> new StudyBlock(entry.getKey(), entry.getValue()))
                .toList());
    }

    /**
     * Calculates the total study hours a student has available between a start and end date.
     *
     * @param profile The student's profile containing their weekly availability.
     * @param start The first day of the period.
     * @param end The last day of the period.
     * @return The total number of available hours.
     */
    private double calculateTotalAvailableHours(StudentProfile profile, LocalDate start, LocalDate end) {
        if (start.isAfter(end)) return 0;
        long totalDays = DAYS.between(start, end);
        double totalHours = 0;
        for (long i = 0; i < totalDays; i++) {
            LocalDate currentDate = start.plusDays(i);
            totalHours += profile.weeklyAvailability().getOrDefault(currentDate.getDayOfWeek(), 0);
        }
        return totalHours;
    }
}