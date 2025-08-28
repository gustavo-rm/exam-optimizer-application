package examoptimizer.service.scheduler.strategy;

import examoptimizer.domain.StudyBlock;
import examoptimizer.domain.exam.Subject;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for a strategy that allocates study hours for a single day.
 * Each implementation of this interface will represent a different method of scheduling
 * (e.g., focusing on critical subjects, interleaving, etc.).
 */
public interface AllocationStrategy {

    /**
     * Allocates hours for a given day based on the provided context.
     * @param context The context object containing all necessary state for the decision.
     * @return A list of StudyBlock objects for the day.
     */
    List<StudyBlock> allocateHours(AllocationContext context);
}
