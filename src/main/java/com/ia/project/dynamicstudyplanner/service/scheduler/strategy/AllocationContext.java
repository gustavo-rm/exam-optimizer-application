package examoptimizer.service.scheduler.strategy;

import examoptimizer.domain.exam.Subject;

import java.time.LocalDate;
import java.util.Map;

/**
 * Encapsulates all the state and data needed for an allocation strategy to make a decision.
 * This avoids polluting method signatures with numerous parameters.
 */
public record AllocationContext(
        int availableHoursToday,
        Map<Subject, Double> hoursToSchedulePerSubject,
        Map<Subject, LocalDate> lastStudiedDateMap
) {}
