package com.ia.project.dynamicstudyplanner.plan;

import java.util.List;

/**
 * A request this scheduler understood and refuses to plan. Answered with HTTP {@code 422}.
 *
 * <h2>Why 422 and not 400</h2>
 *
 * Every condition signalled by this exception is <b>syntactically valid</b> — the JSON parsed, every
 * field had the declared type, the contract version matched. What fails is the instruction: a cycle
 * in the hard prerequisites has no valid ordering, a topic that claims no minutes cannot occupy a
 * block, an inverted availability window is not a window. That is exactly the distinction RFC 9110
 * draws between {@code 400} and {@code 422}, and the same criterion ADR-0005 applies elsewhere in
 * this codebase.
 *
 * <h2>Why the offending items travel with it</h2>
 *
 * A {@code 422} that only says "this cannot be planned" forces whoever called to bisect their own
 * payload. Each rejection therefore names what it tripped over — the topics in the cycle, the topic
 * with the impossible duration, the index of the broken availability slot — and the controller writes
 * that list into the error body under {@code offending}.
 *
 * <p>The values named are curriculum identifiers and array indices. No self-assessment, no
 * psychological state and no student name is ever put in an error body or a log line, which is the
 * standing rule of this repository ({@code security/LogPrivacyTest}).
 */
public class PlanRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Last segment of the RFC 7807 {@code type} URI, so a caller can branch on the category. */
    private final String reason;

    /** What the rejection tripped over, as text. Never empty. */
    private final List<String> offending;

    /**
     * @param reason    kebab-case category, for example {@code "hard-prerequisite-cycle"}
     * @param detail    one sentence, for a human, saying what cannot be planned and why
     * @param offending the items the rejection names; copied defensively
     */
    public PlanRejectedException(String reason, String detail, List<String> offending) {
        super(detail);
        this.reason = reason;
        this.offending = List.copyOf(offending);
    }

    public String reason() {
        return reason;
    }

    public List<String> offending() {
        return offending;
    }
}
