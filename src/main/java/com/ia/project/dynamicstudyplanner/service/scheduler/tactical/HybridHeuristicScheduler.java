package com.ia.project.dynamicstudyplanner.service.scheduler.tactical;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridHeuristicScheduler implements TacticalScheduler {

    private static final double BUFFER_ZONE_PERCENTAGE = 0.15; // Leave 15% of windows open for chaos/interruptions

    @Override
    public TacticalStudyPlan schedule(Map<Subject, Integer> macroPlan, List<AvailabilityWindow> windows, boolean emergencyMode) {
        Map<TimeSlot, TacticalStudyBlock> schedule = new HashMap<>();

        // 1. Sort windows by energy (Highest energy first)
        List<AvailabilityWindow> sortedWindows = new ArrayList<>(windows);
        sortedWindows.sort(Comparator.comparingDouble(AvailabilityWindow::expectedEnergyLevel).reversed());

        // 2. Generate a pool of blocks needed from the macro plan
        List<TacticalStudyBlock> pendingBlocks = generateBlockPool(macroPlan, emergencyMode);

        // 3. Sort blocks by required intensity (Highest required energy first)
        pendingBlocks.sort(Comparator.comparingDouble(TacticalStudyBlock::calculateRequiredEnergy).reversed());

        // 4. Greedy Packing: Match high-intensity blocks to high-energy windows
        for (AvailabilityWindow window : sortedWindows) {
            long windowCapacity = window.getDurationMinutes();
            long usableCapacity = (long) (windowCapacity * (1.0 - BUFFER_ZONE_PERCENTAGE)); // Enforce buffer zone

            long currentFilled = 0;

            // Note: In a full implementation, we would split windows into smaller specific TimeSlots.
            // For this skeleton, we represent the entire usable chunk as a single slot for demonstration.
            TimeSlot slot = new TimeSlot(window.startTime(), window.startTime().plusMinutes(usableCapacity));

            for (int i = 0; i < pendingBlocks.size(); i++) {
                TacticalStudyBlock block = pendingBlocks.get(i);

                if (currentFilled + block.durationMinutes() <= usableCapacity) {
                    // Fits!
                    // In a true bin-packing implementation, we would slice the window into multiple TimeSlots
                    // and assign blocks. Here we just assign the first block that fits to represent the logic.
                    schedule.put(slot, block);
                    currentFilled += block.durationMinutes();
                    pendingBlocks.remove(i);
                    break; // Move to next window (skeleton logic - normally we'd keep packing this window)
                }
            }
        }

        return new TacticalStudyPlan(schedule);
    }

    private List<TacticalStudyBlock> generateBlockPool(Map<Subject, Integer> macroPlan, boolean emergencyMode) {
        List<TacticalStudyBlock> blocks = new ArrayList<>();
        // Translate "Hours per Subject" into specific methodology blocks.
        // e.g., 2 hours of Math -> 1 hour Active Recall, 1 hour Practice Exam
        for (Map.Entry<Subject, Integer> entry : macroPlan.entrySet()) {
            Subject subject = entry.getKey();
            int hours = entry.getValue();

            if (emergencyMode) {
                // In emergency mode, skip passive reading entirely.
                blocks.add(new TacticalStudyBlock(subject, StudyMethodology.ACTIVE_RECALL, hours * 60L));
            } else {
                // Standard mode: mix methodologies
                long activeMins = (long) (hours * 60 * 0.7);
                long passiveMins = (hours * 60L) - activeMins;

                if (activeMins > 0) blocks.add(new TacticalStudyBlock(subject, StudyMethodology.ACTIVE_RECALL, activeMins));
                if (passiveMins > 0) blocks.add(new TacticalStudyBlock(subject, StudyMethodology.PASSIVE_READING, passiveMins));
            }
        }
        return blocks;
    }
}
