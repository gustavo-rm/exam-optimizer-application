package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One run, as one line of the output — and the declaration of what that line means.
 *
 * <h2>One row per (instance, engine, provenance, seed)</h2>
 *
 * Nothing in this file averages anything. A row is a single run, and every aggregation happens in
 * the report, where the method that produced it can be stated next to the number. Writing means
 * here would bake an aggregation into the data and make it impossible to check later which runs
 * went into one.
 *
 * <h2>The four groups, and why they are not interchangeable</h2>
 *
 * The columns arrive in group order — identity, invariants, outcome, cost, objective — and the
 * order is the argument. <b>The outcome columns are the ones a conclusion may rest on.</b> They are
 * properties of the schedule that someone who had never seen the fitness function could compute.
 * The objective columns are last, and they are prefixed {@code ga_objective_} rather than
 * {@code fitness_}, because that is what they are: the quantity the genetic engine maximises and
 * the greedy scheduler has never been shown. They are reported for completeness — a claim about
 * the search is unfalsifiable without them — and never as the comparison between the two engines.
 *
 * <h2>The term columns come from the composition</h2>
 *
 * {@link #header(List)} takes the term names and {@link #cells(List)} reads the same list back out
 * of the breakdown by name, so a term added to the composition adds a column in both places or in
 * neither. A term the composition does not declare is written as an empty cell, never as a zero:
 * a term that did not run is not a term that scored nothing.
 *
 * @param instance    which instance ran, with its factorial coordinates
 * @param engine      the engine id, as {@code PlanEngineSelector} stamped it
 * @param provenance  the ablation condition the run saw the graph under
 * @param importance  the importance strategy the run used
 * @param seed        the replication unit: distinct seeds on the same instance
 * @param hardEdges   {@code HARD} edges that were constraints under this condition
 * @param softEdges   {@code SOFT} edges that were preferences under this condition
 * @param outcome     group (b)
 * @param cost        group (c)
 * @param objective   group (d)
 */
public record MeasurementRow(
        BenchmarkInstance instance,
        String engine,
        String provenance,
        String importance,
        long seed,
        int hardEdges,
        int softEdges,
        OutcomeMetrics outcome,
        CostMetrics cost,
        FitnessBreakdown objective) {

    /** The columns before the per-term ones, in the order {@link #cells(List)} writes them. */
    private static final List<String> FIXED = List.of(
            // identity
            "instance_id", "topics", "density", "tightness", "horizon_days",
            "demanded_minutes", "available_minutes",
            "engine", "provenance", "importance", "seed",
            "prerequisite_edges_hard", "prerequisite_edges_soft",
            // group (a) — validity, not quality. A row only exists when both are true.
            "invariants_ok", "reproducible",
            // group (b) — outcome
            "soft_inversions", "inversions_removed", "topics_scheduled", "topics_unscheduled",
            "scheduled_minutes", "utilisation", "load_ceiling_bound", "load_excess_ratio",
            "first_quarter_share", "peak_over_mean",
            // group (c) — cost
            "generations", "population_size", "evaluations", "elapsed_micros",
            // group (d) — the GA's objective, reported for completeness
            "ga_objective_aggregate", "ga_objective_raw", "ga_objective_bounded",
            "ga_objective_penalty_factor");

    /**
     * The header.
     *
     * @param termNames the composition's terms, in composition order
     * @return the fixed columns followed by three per term
     */
    public static List<String> header(List<String> termNames) {
        List<String> header = new ArrayList<>(FIXED);
        for (String name : termNames) {
            header.add("ga_term_" + name + "_value");
            header.add("ga_term_" + name + "_weight");
            header.add("ga_term_" + name + "_weighted");
        }
        return List.copyOf(header);
    }

    /**
     * This run, as cells aligned to {@link #header(List)}.
     *
     * <p>{@code invariants_ok} and {@code reproducible} are written {@code true} unconditionally,
     * which looks like a tautology and is not: the harness refuses to build a row for a run that
     * failed either check, so the columns record that the check ran and passed for this row rather
     * than reporting a verdict that varies. A false there would mean the abort was bypassed.
     *
     * @param termNames the same list passed to {@link #header(List)}
     * @return the cells, already escaped for CSV
     */
    public List<String> cells(List<String> termNames) {
        List<String> cells = new ArrayList<>();
        cells.add(instance.id());
        cells.add(Integer.toString(instance.topics()));
        cells.add(number(instance.density()));
        cells.add(number(instance.tightness()));
        cells.add(Integer.toString(instance.horizonDays()));
        cells.add(Long.toString(instance.demandedMinutes()));
        cells.add(Long.toString(instance.availableMinutes()));
        cells.add(engine);
        cells.add(provenance);
        cells.add(importance);
        cells.add(Long.toString(seed));
        cells.add(Integer.toString(hardEdges));
        cells.add(Integer.toString(softEdges));
        cells.add("true");
        cells.add("true");
        outcomeCells(cells);
        costCells(cells);
        objectiveCells(cells, termNames);
        return cells;
    }

    private void outcomeCells(List<String> cells) {
        cells.add(Integer.toString(outcome.softInversions()));
        // Empty, not zero: the greedy baseline runs no repair, and "removed none" is a different
        // statement from "no repair was attempted".
        cells.add(outcome.inversionsRemoved() == null
                ? "" : Integer.toString(outcome.inversionsRemoved()));
        cells.add(Integer.toString(outcome.topicsScheduled()));
        cells.add(Integer.toString(outcome.topicsUnscheduled()));
        cells.add(Long.toString(outcome.scheduledMinutes()));
        cells.add(number(outcome.utilisation()));
        cells.add(Boolean.toString(outcome.loadCeilingBound()));
        cells.add(number(outcome.loadExcessRatio()));
        cells.add(number(outcome.firstQuarterShare()));
        cells.add(number(outcome.peakOverMean()));
    }

    private void costCells(List<String> cells) {
        cells.add(Integer.toString(cost.generations()));
        cells.add(Integer.toString(cost.populationSize()));
        cells.add(Long.toString(cost.evaluations()));
        cells.add(Long.toString(cost.elapsedMicros()));
    }

    private void objectiveCells(List<String> cells, List<String> termNames) {
        cells.add(number(objective.aggregate()));
        cells.add(number(objective.rawScore()));
        cells.add(number(objective.boundedScore()));
        cells.add(number(objective.penaltyFactor()));
        for (String name : termNames) {
            FitnessBreakdown.Term term = objective.terms().stream()
                    .filter(candidate -> candidate.name().equals(name))
                    .findFirst()
                    .orElse(null);
            if (term == null) {
                cells.add("");
                cells.add("");
                cells.add("");
            } else {
                cells.add(number(term.value()));
                cells.add(number(term.weight()));
                cells.add(number(term.weightedContribution()));
            }
        }
    }

    /**
     * A number with a dot for a decimal separator and six places.
     *
     * <p>{@link Locale#ROOT} is not decoration: a machine with a comma locale would write
     * {@code 0,8134}, which a CSV reader splits into two columns and a spreadsheet reads as a
     * different number. Six places because the fitness terms differ in the fifth on plans that are
     * genuinely different.
     */
    public static String number(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
