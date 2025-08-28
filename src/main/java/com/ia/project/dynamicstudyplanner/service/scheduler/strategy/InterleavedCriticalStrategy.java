package com.ia.project.dynamicstudyplanner.service.scheduler.strategy;

import com.ia.project.dynamicstudyplanner.domain.StudyBlock;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An allocation strategy that focuses on the most critical subjects but interleaves them
 * to avoid monotony and improve long-term retention.
 */
public class InterleavedCriticalStrategy implements AllocationStrategy {
    private static final int INTERLEAVING_FOCUS_SIZE = 3; // Estuda as 3 matérias mais críticas do dia

    @Override
    public List<StudyBlock> allocateHours(AllocationContext context) {
        List<StudyBlock> dailyBlocks = new ArrayList<>();

        // 1. Get the top N most critical subjects for today
        List<Subject> topSubjects = context.hoursToSchedulePerSubject().entrySet().stream()
                .filter(entry -> entry.getValue() > 0.5)
                .sorted(Map.Entry.<Subject, Double>comparingByValue().reversed())
                .limit(INTERLEAVING_FOCUS_SIZE)
                .map(Map.Entry::getKey)
                .toList();

        if (topSubjects.isEmpty()) {
            return dailyBlocks;
        }

        // 2. Allocate hours in a round-robin fashion among the top subjects
        int currentSubjectIndex = 0;
        for (int i = 0; i < context.availableHoursToday(); i++) {
            Subject subjectToStudy = topSubjects.get(currentSubjectIndex);

            dailyBlocks.add(new StudyBlock(subjectToStudy, 1));
            context.hoursToSchedulePerSubject().computeIfPresent(subjectToStudy, (s, hours) -> hours - 1);

            // Move to the next subject in the rotation
            currentSubjectIndex = (currentSubjectIndex + 1) % topSubjects.size();
        }

        return dailyBlocks;
    }
}
