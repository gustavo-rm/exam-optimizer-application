package com.ia.project.dynamicstudyplanner.service.scheduler.strategy;

import com.ia.project.dynamicstudyplanner.domain.StudyBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A meta-strategy that decorates another AllocationStrategy to balance cognitive load.
 * It first receives a proposed schedule for the day and then refines it by:
 * 1. Reordering study blocks to alternate between high and low intensity subjects.
 * 2. Pruning the schedule if the total daily cognitive load exceeds a defined threshold.
 */
public class CognitiveLoadBalancingStrategy implements AllocationStrategy {

    private final AllocationStrategy baseStrategy;
    private final int maxDailyCognitiveLoad;

    /**
     * Constructs a CognitiveLoadBalancingStrategy.
     *
     * @param baseStrategy The underlying strategy to generate the initial daily plan.
     * @param maxDailyCognitiveLoad The maximum cognitive load allowed for any single day.
     */
    public CognitiveLoadBalancingStrategy(AllocationStrategy baseStrategy, int maxDailyCognitiveLoad) {
        this.baseStrategy = baseStrategy;
        this.maxDailyCognitiveLoad = maxDailyCognitiveLoad;
    }

    @Override
    public List<StudyBlock> allocateHours(AllocationContext context) {
        // 1. Get the initial schedule from the base strategy
        List<StudyBlock> initialBlocks = baseStrategy.allocateHours(context);

        if (initialBlocks.isEmpty() || initialBlocks.size() == 1) {
            return initialBlocks; // Interleaving is not necessary for 0 or 1 block
        }

        // 2. Reorder the blocks for optimal cognitive balance
        List<StudyBlock> reorderedBlocks = reorderForBalance(initialBlocks);

        // 3. Limit the total cognitive load for the day
        return pruneToFitDailyLoadLimit(reorderedBlocks);
    }

    /**
     * Reorders study blocks using an efficient "Two Pointers Interleaving" algorithm.
     * This method sorts the blocks by cognitive load and then builds a new list by
     * alternating between the hardest and easiest subjects, creating a balanced study flow.
     *
     * @param blocks The initial, potentially unbalanced list of study blocks for the day.
     * @return A reordered list of study blocks designed to reduce cognitive strain.
     */
    private List<StudyBlock> reorderForBalance(List<StudyBlock> blocks) {
        // Step 1: Sort blocks from easiest (lowest load) to hardest (highest load).
        List<StudyBlock> sortedByLoad = blocks.stream()
                .sorted(Comparator.comparingInt(block -> block.subject().cognitiveLoad()))
                .toList();

        // Step 2: Initialize the final list and the two pointers.
        List<StudyBlock> interleavedList = new ArrayList<>();
        int left = 0;  // Pointer to the easiest block.
        int right = sortedByLoad.size() - 1; // Pointer to the hardest block.

        // Step 3: Build the interleaved list until the pointers meet.
        while (left < right) {
            // Add the hardest available block, then the easiest.
            interleavedList.add(sortedByLoad.get(right));
            interleavedList.add(sortedByLoad.get(left));

            // Move pointers towards the center.
            right--;
            left++;
        }

        // Step 4: If there is a middle element (odd number of blocks), add it to the end.
        if (left == right) {
            interleavedList.add(sortedByLoad.get(left));
        }

        return interleavedList;
    }

    /**
     * Removes study blocks from the end of the day's schedule if the total
     * cognitive load exceeds the maximum allowed threshold.
     *
     * @param blocks The reordered list of study blocks.
     * @return A final list of study blocks that respects the daily load limit.
     */
    private List<StudyBlock> pruneToFitDailyLoadLimit(List<StudyBlock> blocks) {
        int currentLoad = 0;
        List<StudyBlock> finalBlocks = new ArrayList<>();
        for (StudyBlock block : blocks) {
            int blockLoad = block.hours() * block.subject().cognitiveLoad();
            if (currentLoad + blockLoad <= maxDailyCognitiveLoad) {
                finalBlocks.add(block);
                currentLoad += blockLoad;
            } else {
                break;
            }
        }
        return finalBlocks;
    }
}
