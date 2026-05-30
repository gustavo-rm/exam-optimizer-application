package com.ia.project.dynamicstudyplanner.service.scheduler.strategy;

import com.ia.project.dynamicstudyplanner.domain.StudyBlock;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.*;
import java.util.stream.Collectors;

/**
 * An allocation strategy that randomizes the order of subjects each day.
 * This maximizes variety and forces the brain to constantly switch contexts,
 * which can be a powerful learning technique.
 */
public class RandomizedInterleavedStrategy implements AllocationStrategy {
    private final Random random = com.ia.project.dynamicstudyplanner.util.RandomProvider.getInstance();

    @Override
    public List<StudyBlock> allocateHours(AllocationContext context) {
        List<StudyBlock> dailyBlocks = new ArrayList<>();

        // 1. Get all subjects that still need study and shuffle them
        List<Subject> subjectsToStudyToday = context.hoursToSchedulePerSubject().entrySet().stream()
                .filter(entry -> entry.getValue() > 0.5)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        Collections.shuffle(subjectsToStudyToday, random);

        if (subjectsToStudyToday.isEmpty()) {
            return dailyBlocks;
        }

        // 2. Allocate hours in a round-robin fashion among the shuffled subjects
        int currentSubjectIndex = 0;
        for (int i = 0; i < context.availableHoursToday(); i++) {
            Subject subjectToStudy = subjectsToStudyToday.get(currentSubjectIndex);

            dailyBlocks.add(new StudyBlock(subjectToStudy, 1));
            context.hoursToSchedulePerSubject().computeIfPresent(subjectToStudy, (s, hours) -> hours - 1);

            currentSubjectIndex = (currentSubjectIndex + 1) % subjectsToStudyToday.size();
        }

        return dailyBlocks;
    }
}
