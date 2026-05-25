package com.ia.project.dynamicstudyplanner.domain.retention;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the overall retention history for a student across all subjects.
 */
public class RetentionProfile {

    private final Map<Subject, SubjectRetentionState> retentionStates;

    public RetentionProfile(Map<Subject, SubjectRetentionState> initialStates) {
        this.retentionStates = new ConcurrentHashMap<>();
        if (initialStates != null) {
            this.retentionStates.putAll(initialStates);
        }
    }

    public SubjectRetentionState getState(Subject subject) {
        return retentionStates.get(subject);
    }

    public void updateState(Subject subject, SubjectRetentionState newState) {
        this.retentionStates.put(subject, newState);
    }

    public Map<Subject, SubjectRetentionState> getAllStates() {
        return Collections.unmodifiableMap(retentionStates);
    }
}
