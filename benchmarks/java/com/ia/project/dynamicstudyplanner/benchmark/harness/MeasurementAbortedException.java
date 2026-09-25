package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;

import java.util.List;

/**
 * The measurement stopped, because something happened that is a finding rather than a data point.
 *
 * <h2>Why this is not a logged warning and a skipped row</h2>
 *
 * Two things can go wrong that invalidate the whole run rather than one cell of it:
 *
 * <ul>
 *   <li>a <b>{@code HARD} inversion</b>, which means the property EOA-7 exists to guarantee does not
 *       hold — its acceptance criterion was not actually satisfied, and no comparison between the
 *       engines means anything until it is;</li>
 *   <li>a <b>reproducibility failure</b>, which means the numbers are not the numbers: a second run
 *       of the same seeded request produced a different plan, so nothing measured here can be
 *       repeated by anyone.</li>
 * </ul>
 *
 * Neither degrades a measurement gracefully. Skipping the offending row and carrying on would
 * produce a CSV that looks complete, a report that reads normally, and a defect that nobody sees. So
 * the harness stops, and it stops carrying <b>the instance and the seed that reproduce it</b>,
 * because a defect report without a reproduction is a rumour.
 */
public class MeasurementAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param instance  the instance that was running
     * @param condition the condition that was running
     * @param seed      the seed that reproduces it
     * @param reason    what happened, in one line
     * @param detail    the specific violations or differences found
     */
    public MeasurementAbortedException(BenchmarkInstance instance, Condition condition, long seed,
            String reason, List<String> detail) {

        super(message(instance, condition, seed, reason, detail));
    }

    private static String message(BenchmarkInstance instance, Condition condition, long seed,
            String reason, List<String> detail) {

        StringBuilder message = new StringBuilder()
                .append("Measurement aborted: ").append(reason)
                .append(System.lineSeparator())
                .append("  reproduce with: instance=").append(instance.id())
                .append(" engine=").append(condition.engine())
                .append(" provenance=").append(condition.provenance().id())
                .append(" importance=").append(Condition.IMPORTANCE)
                .append(" seed=").append(seed)
                .append(System.lineSeparator());
        detail.forEach(line -> message.append("  ").append(line).append(System.lineSeparator()));
        return message.toString();
    }
}
