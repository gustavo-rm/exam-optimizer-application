package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;

/**
 * The measured outcome of one planner on one instance, for one repetition.
 *
 * @param fitness            Value from the production {@code FitnessEvaluator}. Comparable across
 *                           planners on the same instance; <b>not</b> comparable across instances,
 *                           because the fitness has no normalised unit (auditoria §3.1).
 * @param elapsedMillis      Wall-clock time to produce the macro plan. Excludes metric computation.
 * @param meanRetentionAtExam Mean predicted recall probability across subjects on the exam date,
 *                           from the production {@code HybridRetentionEngine}. Range 0..1.
 * @param pctInRetentionWindow Fraction of subjects whose predicted recall on the exam date is at or
 *                           above the production {@code MANDATORY_REVIEW_THRESHOLD} of 0.85 — i.e.
 *                           the share of the syllabus still "inside the ideal retention window"
 *                           when the student sits the exam. Range 0..1.
 * @param cognitiveOverloadDays Number of scheduled days whose total cognitive load exceeds the
 *                           budget from the production {@code CognitiveLoadCalculator}, measured on
 *                           a schedule built <b>without</b> the load-balancing pruner so the raw
 *                           load implied by the macro plan is visible.
 * @param scheduledHoursRatio Hours that survive into the delivered schedule divided by hours the
 *                           macro plan asked for. Below 1.0 means the tactical layer silently
 *                           dropped study time.
 * @param topSubjectShare    Share of the day budget consumed by the single largest subject. Exposes
 *                           the winner-take-all concentration the audit predicted (§2.1.4).
 * @param scheduleStatus     Production status enum for the generated schedule.
 */
public record PlanMetrics(
        double fitness,
        long elapsedMillis,
        double meanRetentionAtExam,
        double pctInRetentionWindow,
        int cognitiveOverloadDays,
        double scheduledHoursRatio,
        double topSubjectShare,
        ScheduleStatus scheduleStatus
) {
}
