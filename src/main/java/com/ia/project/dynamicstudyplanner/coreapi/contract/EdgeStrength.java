package com.ia.project.dynamicstudyplanner.coreapi.contract;

/**
 * How binding a prerequisite is.
 *
 * <p>Each gets a different mechanism: a hard edge is a constraint, a soft one is a preference.
 * Receiving only the pair of topics would leave this side unable to tell them apart, and treating
 * every edge as hard makes most real curricula infeasible.
 */
public enum EdgeStrength {

    /** Must be studied before. A violation makes the plan invalid. */
    HARD,

    /** Better studied before. A violation costs, and is sometimes worth paying. */
    SOFT
}
