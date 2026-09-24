package com.ia.project.dynamicstudyplanner.domain.retention;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the overall retention history for a student across all subjects.
 */
public class RetentionProfile {

    private final Map<PlanningItem, ItemRetentionState> retentionStates;

    public RetentionProfile(Map<PlanningItem, ItemRetentionState> initialStates) {
        this.retentionStates = new ConcurrentHashMap<>();
        if (initialStates != null) {
            this.retentionStates.putAll(initialStates);
        }
    }

    public ItemRetentionState getState(PlanningItem item) {
        return retentionStates.get(item);
    }

    public void updateState(PlanningItem item, ItemRetentionState newState) {
        this.retentionStates.put(item, newState);
    }

    public Map<PlanningItem, ItemRetentionState> getAllStates() {
        return Collections.unmodifiableMap(retentionStates);
    }
}
