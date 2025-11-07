package com.ia.project.dynamicstudyplanner.service.scheduler.strategy;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.time.LocalDate;
import java.util.Map;
import java.util.HashMap;

/**
 * Encapsulates all the state and data needed for an allocation strategy to make a decision.
 * This avoids polluting method signatures with numerous parameters.
 */
public record AllocationContext(
        int availableHoursToday,
        Map<Subject, Double> hoursToSchedulePerSubject,
        Map<Subject, LocalDate> lastStudiedDateMap
) {
    public AllocationContext(int availableHoursToday, Map<Subject, Double> hoursToSchedulePerSubject, Map<Subject, LocalDate> lastStudiedDateMap) {
        this.availableHoursToday = availableHoursToday;
        this.hoursToSchedulePerSubject = new HashMap<>(hoursToSchedulePerSubject);
        this.lastStudiedDateMap = new HashMap<>(lastStudiedDateMap);
    }

    @Override
    public Map<Subject, Double> hoursToSchedulePerSubject() {
        return new HashMap<>(this.hoursToSchedulePerSubject);
    }

    @Override
    public Map<Subject, LocalDate> lastStudiedDateMap() {
        return new HashMap<>(this.lastStudiedDateMap);
    }
}
