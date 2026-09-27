/**
 * Allocation-level planners, inherited from the genetic-algorithm review — <b>not</b> in the engine
 * matrix.
 *
 * <h2>What they are</h2>
 *
 * Each one distributes a budget of sessions across planning items and stops there: the output is a
 * macro {@code StudyPlan}, a count per item, with no calendar. They differ only in their
 * distribution policy, which is what made them comparable with each other and with the macro
 * chromosome the genetic algorithm used to be measured on.
 *
 * <h2>Why they are not measured against the engines</h2>
 *
 * An engine produces a whole {@code PlanResponse} — placed sessions, inside declared windows,
 * ordered by prerequisites. Scoring one of these against an engine would compare <b>two different
 * budgets</b>: {@code BenchmarkInstance.totalStudyDays()} is the harness's own figure, deliberately
 * not a copy of the private one {@code GeneticPlanEngine} derives, and the two have no reason to
 * agree. The comparison would be measuring the budgets, not the planners.
 *
 * <p>They survived the EOA-9 removal because they are already item-based and carry no dependency on
 * the concurso domain — verified one by one, not assumed. The only trace of it left was prose: the
 * word <i>edital</i> in two comments and a reference to a regression test that no longer exists,
 * both corrected rather than carried forward.
 *
 * <p>They are kept as material for an allocation question, not for the engine comparison. If nothing
 * asks that question, deleting the package costs nothing — which is a decision, not a cleanup.
 */
package com.ia.project.dynamicstudyplanner.benchmark.strategy;
