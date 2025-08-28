package examoptimizer.service.scheduler.strategy;

import examoptimizer.domain.StudyBlock;
import examoptimizer.domain.exam.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An allocation strategy that always prioritizes the subject with the most
 * remaining study hours. This creates a focused, but potentially monotonous, schedule.
 */
public class CriticalFirstStrategy implements AllocationStrategy {

    @Override
    public List<StudyBlock> allocateHours(AllocationContext context) {
        List<StudyBlock> dailyBlocks = new ArrayList<>();
        for (int i = 0; i < context.availableHoursToday(); i++) {
            // Find the subject with the most remaining hours
            Optional<Subject> nextSubjectToStudy = context.hoursToSchedulePerSubject().entrySet().stream()
                    .filter(entry -> entry.getValue() > 0.5)
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey);

            if (nextSubjectToStudy.isPresent()) {
                Subject subject = nextSubjectToStudy.get();
                dailyBlocks.add(new StudyBlock(subject, 1));
                context.hoursToSchedulePerSubject().computeIfPresent(subject, (s, hours) -> hours - 1);
            } else {
                break; // No subjects left to study
            }
        }
        return dailyBlocks;
    }
}
