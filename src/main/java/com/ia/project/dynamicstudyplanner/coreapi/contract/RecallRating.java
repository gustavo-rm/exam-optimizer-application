package com.ia.project.dynamicstudyplanner.coreapi.contract;

/**
 * The student's own judgement of how well they recalled a topic.
 *
 * <p>Four ordinal levels, sent as a trajectory rather than as a latest value: the sequence over time
 * is what the optimiser can use to modulate spacing.
 *
 * <p>It is self-report. It measures adherence and perception, not retention.
 */
public enum RecallRating {

    /** Could not recall it. */
    AGAIN,

    /** Recalled it with difficulty. */
    HARD,

    /** Recalled it. */
    GOOD,

    /** Recalled it without effort. */
    EASY
}
