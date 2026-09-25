package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.util.List;

/**
 * What the prerequisite stage did to this plan, in the shape {@code fitness} reports it.
 *
 * <h2>Why it is one record rather than four arguments</h2>
 *
 * The four figures are read together or not at all. "Two inversions remain" means nothing without
 * "out of nine applicable preferences", and neither means anything without the provenance condition
 * that decided which edges were applicable in the first place — a run under {@code curated} and a
 * run under {@code all} can report the same residue over completely different graphs.
 *
 * @param provenance       the condition this run saw the graph under
 * @param hardEdges        how many {@code HARD} edges became ordering constraints
 * @param softEdges        how many {@code SOFT} edges became priced preferences
 * @param inversionsBefore preferences the study order inverted before repair, or {@code null} when
 *                         no repair ran. Null rather than "equal to after", because "the repair
 *                         removed none" and "no repair was attempted" are different facts and the
 *                         greedy baseline is the second one. A key that did not run does not get
 *                         reported, which is the same rule the disabled fitness terms follow
 * @param inversionsAfter  preferences inverted in the plan that was produced; what the genetic
 *                         path's fitness term prices, and what the baseline is measured on
 * @param unscheduled      topics the calendar could not hold, named rather than counted
 */
public record PrerequisiteReport(EdgeProvenanceFilter provenance, int hardEdges, int softEdges,
        Integer inversionsBefore, int inversionsAfter, List<String> unscheduled) {

    /** Defensive copy: this ends up in a response map that must not change under a reader. */
    public PrerequisiteReport {
        unscheduled = List.copyOf(unscheduled);
    }

    /** The tail of the order nobody reaches. */
    private static List<String> unscheduledOf(List<PlanRequest.Topic> order, int scheduled) {
        return order.stream().skip(scheduled).map(topic -> topic.id().toString()).toList();
    }

    /**
     * Sums up one run's prerequisite stage.
     *
     * @param provenance the condition that ran
     * @param hard       the applied hard constraints
     * @param soft       the applicable soft preferences
     * @param repair     what the repair pass managed to remove
     * @param order      the study order, repaired, in placement order
     * @param scheduled  how many topics of that order the calendar held
     * @return the report
     */
    public static PrerequisiteReport repaired(EdgeProvenanceFilter provenance,
            HardPrerequisiteGraph hard, SoftPrerequisiteEdges soft,
            PrerequisiteOrderRepairer.Result repair, List<PlanRequest.Topic> order, int scheduled) {

        // The scheduled set is a PREFIX of the order — placement stops at the first topic that does
        // not fit rather than skipping ahead — so everything from that point on is what the student
        // does not get. Naming them is the difference between a declared partial plan and a
        // silently incomplete one.
        List<String> unscheduled = unscheduledOf(order, scheduled);

        return new PrerequisiteReport(provenance, hard.appliedEdgeCount(), soft.size(),
                repair.inversionsBefore(), repair.inversionsAfter(), unscheduled);
    }

    /**
     * Sums up a run that applied no repair, and says so by omission.
     *
     * <p>The greedy baseline orders by goal pressure and curricular position and states no opinion
     * about preferences — {@code GreedyBaselineSchedulerTest.softEdgesDoNotConstrainTheOrder} pins
     * that, deliberately. Teaching it to repair would have made the two engines differ by one thing
     * less and the comparison between them measure two changes at once, so it is measured instead.
     *
     * @param provenance the condition that ran
     * @param hard       the applied hard constraints
     * @param soft       the applicable soft preferences
     * @param order      the study order, in placement order
     * @param scheduled  how many topics of that order the calendar held
     * @return the report, with no before-repair figure
     */
    public static PrerequisiteReport measured(EdgeProvenanceFilter provenance,
            HardPrerequisiteGraph hard, SoftPrerequisiteEdges soft,
            List<PlanRequest.Topic> order, int scheduled) {

        return new PrerequisiteReport(provenance, hard.appliedEdgeCount(), soft.size(), null,
                PrerequisiteOrderRepairer.inversionsIn(order, soft), unscheduledOf(order, scheduled));
    }

    /** @return whether a repair pass ran at all */
    public boolean wasRepaired() {
        return inversionsBefore != null;
    }
}
