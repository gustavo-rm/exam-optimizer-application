package com.ia.project.dynamicstudyplanner.service.scheduler.tactical;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HybridHeuristicSchedulerTest {

    private final HybridHeuristicScheduler scheduler = new HybridHeuristicScheduler();

    @Test
    void shouldRespectBufferZones() {
        // Arrange
        Subject math = new Subject("Math", 10, 3);
        Map<Subject, Integer> macroPlan = Map.of(math, 2); // 2 hours = 120 mins

        // Provide exactly a 2-hour window
        LocalDateTime start = LocalDateTime.of(2023, 10, 10, 10, 0);
        AvailabilityWindow window = new AvailabilityWindow(start, start.plusHours(2), 5.0);

        // Act
        TacticalStudyPlan plan = scheduler.schedule(macroPlan, List.of(window), false);

        // Assert: The scheduler should NOT fill the full 120 minutes because of the 15% buffer zone constraint.
        // It will only pack what fits into 102 minutes (120 * 0.85).
        long totalScheduledMinutes = plan.getSchedule().values().stream()
                .mapToLong(TacticalStudyBlock::durationMinutes)
                .sum();

        assertThat(totalScheduledMinutes).isLessThanOrEqualTo((long)(120 * 0.85));
    }

    @Test
    void shouldPrioritizeActiveRecallInEmergencyMode() {
        // Arrange
        Subject history = new Subject("History", 10, 2);
        Map<Subject, Integer> macroPlan = Map.of(history, 4); // 4 hours

        LocalDateTime start = LocalDateTime.of(2023, 10, 10, 8, 0);
        AvailabilityWindow window = new AvailabilityWindow(start, start.plusHours(10), 5.0);

        // Act
        TacticalStudyPlan plan = scheduler.schedule(macroPlan, List.of(window), true);

        // Assert
        // In emergency mode, passive reading must be strictly 0.
        boolean hasPassiveReading = plan.getSchedule().values().stream()
                .anyMatch(block -> block.methodology() == StudyMethodology.PASSIVE_READING);

        assertThat(hasPassiveReading).isFalse();
    }

    @Test
    void shouldGenerateNonOverlappingTimeSlots() {
        // Arrange
        Subject physics = new Subject("Physics", 10, 5);
        Map<Subject, Integer> macroPlan = Map.of(physics, 1);

        LocalDateTime start = LocalDateTime.of(2023, 10, 10, 9, 0);
        AvailabilityWindow window1 = new AvailabilityWindow(start, start.plusHours(1), 5.0);
        AvailabilityWindow window2 = new AvailabilityWindow(start.plusHours(2), start.plusHours(3), 5.0);

        // Act
        TacticalStudyPlan plan = scheduler.schedule(macroPlan, List.of(window1, window2), false);

        // Assert
        List<TimeSlot> assignedSlots = plan.getSchedule().keySet().stream().toList();

        for (int i = 0; i < assignedSlots.size(); i++) {
            for (int j = i + 1; j < assignedSlots.size(); j++) {
                TimeSlot slotA = assignedSlots.get(i);
                TimeSlot slotB = assignedSlots.get(j);

                // Assert no overlap: A ends before B starts, or A starts after B ends
                boolean noOverlap = !slotA.endTime().isAfter(slotB.startTime()) ||
                                    !slotA.startTime().isBefore(slotB.endTime());

                assertThat(noOverlap).isTrue();
            }
        }
    }
}
