package com.ia.project.dynamicstudyplanner.coreapi.contract;

/**
 * Where an edge came from.
 *
 * <p>It crosses the wire so that the ablation experiment is possible without adding instrumentation
 * afterwards: does the algorithm do better with curated edges, with derived ones, or with none? A
 * run that does not know which edges were which cannot answer that.
 */
public enum EdgeProvenance {

    /** Asserted by a human curator. */
    CURATED,

    /** Seeded from the curricular ordering of a subject. */
    TEXTBOOK_ORDER,

    /** Produced by automatic derivation. */
    DERIVED
}
