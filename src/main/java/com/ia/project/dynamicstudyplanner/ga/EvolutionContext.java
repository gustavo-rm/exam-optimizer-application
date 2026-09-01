package com.ia.project.dynamicstudyplanner.ga;

import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.engagement.EngagementProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulates all the contextual information required for a single evolution step.
 * This avoids "parameter drilling" by bundling related parameters into one object.
 * <p>
 * The fitness terms that consume these fields, their formulas and the reasoning behind their
 * weights are documented in {@code docs/revisao-ag/05-fitness-function.md}. Adding a field here
 * because a new fitness term needs it means that document has to be updated too.
 *
 * @param importanceScores        Raw personalised importance per subject, in the exam's own scoring
 *                                units. Kept because the scheduler and the baselines still rank by
 *                                it; the fitness itself uses {@link #normalizedImportance}.
 * @param normalizedImportance    Importance projected onto the simplex, so the values sum to 1.
 *                                See {@link #normalize}.
 * @param retentionWeights        Importance tempered by {@link #RETENTION_TEMPERING} and
 *                                renormalised. Flatter than {@link #normalizedImportance}; see
 *                                {@link #temper} for why retention is not weighted by exam value.
 * @param minimumDaysPerSubject   Coverage floor per subject, from {@code BaselineCalculator}.
 * @param studentState            Self-reported stress, fatigue and motivation. Enters the fitness
 *                                indirectly, through the daily cognitive-load budget.
 * @param fitnessEvaluator        The configured fitness pipeline.
 * @param retentionProfile        Review history. Empty in the macro path.
 * @param planStartDate           First day of the plan.
 * @param engagementProfile       Behavioural history. Baseline in the macro path.
 * @param planningHorizonDays     Calendar days between the plan start and the exam. Drives the
 *                                spacing estimate in the retention objective.
 * @param hoursPerStudyDay        Study hours a single plan day represents, derived from the
 *                                student's weekly availability.
 * @param maxDailyCognitiveLoad   Sustainable daily load budget from {@code CognitiveLoadCalculator}.
 *                                Already reflects the student's psychological state.
 */
public record EvolutionContext(
        Map<Subject, Double> importanceScores,
        Map<Subject, Double> normalizedImportance,
        Map<Subject, Double> retentionWeights,
        Map<Subject, Integer> minimumDaysPerSubject,
        StudentState studentState,
        FitnessEvaluator fitnessEvaluator,
        RetentionProfile retentionProfile,
        LocalDate planStartDate,
        EngagementProfile engagementProfile,
        int planningHorizonDays,
        int hoursPerStudyDay,
        int maxDailyCognitiveLoad
) {

    /**
     * Builds a context, deriving the normalised importance once so the fitness does not recompute
     * it on every evaluation — the evaluator runs up to half a million times per request.
     */
    public static EvolutionContext of(
            Map<Subject, Double> importanceScores,
            Map<Subject, Integer> minimumDaysPerSubject,
            StudentState studentState,
            FitnessEvaluator fitnessEvaluator,
            RetentionProfile retentionProfile,
            LocalDate planStartDate,
            EngagementProfile engagementProfile,
            int planningHorizonDays,
            int hoursPerStudyDay,
            int maxDailyCognitiveLoad
    ) {
        Map<Subject, Double> normalized = normalize(importanceScores);
        return new EvolutionContext(
                importanceScores,
                normalized,
                temper(normalized),
                minimumDaysPerSubject,
                studentState,
                fitnessEvaluator,
                retentionProfile,
                planStartDate,
                engagementProfile,
                planningHorizonDays,
                hoursPerStudyDay,
                maxDailyCognitiveLoad
        );
    }

    /**
     * Projects raw importance onto the unit simplex: every value is divided by the total, so the
     * weights sum to 1 and become dimensionless.
     * <p>
     * This is the fix for the dominance problem measured in
     * {@code docs/revisao-ag/01-auditoria-fitness.md} §2.1.4. Raw importance is
     * {@code questionCount x axisWeight x knowledgeGap}, and the API's own validation limits allow
     * two subjects in the same sum to differ by a factor of 250,000. Under a plain sum that makes
     * the allocation winner-take-all: the marginal gain of the dominant subject stays above every
     * other subject's for the entire budget, and only the minimum-days floor keeps the rest of the
     * syllabus alive. Normalising removes the payload's ability to set the scale and makes fitness
     * values comparable between different exams, which is what allows the benchmark harness to
     * track quality over time at all.
     *
     * @param raw importance per subject, in the exam's scoring units
     * @return importance summing to 1; a uniform distribution when every input is zero or absent,
     *         which happens only for a degenerate payload in which no subject scores any points
     */
    public static Map<Subject, Double> normalize(Map<Subject, Double> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        double total = raw.values().stream()
                .mapToDouble(v -> Math.max(0.0, v == null ? 0.0 : v))
                .sum();

        Map<Subject, Double> normalized = new HashMap<>(raw.size());
        if (total <= 0.0) {
            double uniform = 1.0 / raw.size();
            for (Subject subject : raw.keySet()) {
                normalized.put(subject, uniform);
            }
            return Collections.unmodifiableMap(normalized);
        }

        for (Map.Entry<Subject, Double> entry : raw.entrySet()) {
            double value = entry.getValue() == null ? 0.0 : Math.max(0.0, entry.getValue());
            normalized.put(entry.getKey(), value / total);
        }
        return Collections.unmodifiableMap(normalized);
    }

    /**
     * Exponent applied to the normalised importance to obtain the retention weights.
     * <p>
     * {@code 0.5} is the tempered midpoint between uniform weighting ({@code 0}) and full
     * exam-value weighting ({@code 1}). Under it, a subject worth 100x another on the exam is
     * weighted 10x for retention purposes.
     */
    public static final double RETENTION_TEMPERING = 0.5;

    /**
     * Flattens the importance distribution for the retention objective.
     * <p>
     * <b>Why retention is not weighted by exam value.</b> O1 asks "how many exam points can this plan
     * earn?", which is correctly weighted by what each subject is worth. O3 asks a different
     * question: "how much of what was studied survives to exam day?" The cost of forgetting is not
     * proportional to exam value — forgetting a low-weight subject wastes the study days already
     * spent on it, and those days cost the student the same regardless of the subject's weight. So
     * the natural weighting for retention is flatter than the weighting for score.
     * <p>
     * <b>What it fixes.</b> While both objectives used the identical weight, they agreed to starve
     * the same subjects, and nothing in the fitness pushed back. The controlled sweep in
     * {@code docs/revisao-ag/06-regime-alta-carga.md} showed the consequence: above an effective
     * importance dispersion of roughly 30:1, the correlation between fitness and predicted retention
     * turns negative and deepens with dispersion — maximising the fitness made retention worse.
     * Tempering gives the retention term a weighting of its own, so it can dissent.
     */
    public static Map<Subject, Double> temper(Map<Subject, Double> normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return Map.of();
        }

        Map<Subject, Double> tempered = new HashMap<>(normalized.size());
        double total = 0.0;
        for (Map.Entry<Subject, Double> entry : normalized.entrySet()) {
            double value = Math.pow(Math.max(0.0, entry.getValue()), RETENTION_TEMPERING);
            tempered.put(entry.getKey(), value);
            total += value;
        }

        if (total <= 0.0) {
            double uniform = 1.0 / normalized.size();
            normalized.keySet().forEach(s -> tempered.put(s, uniform));
            return Collections.unmodifiableMap(tempered);
        }

        final double sum = total;
        tempered.replaceAll((subject, value) -> value / sum);
        return Collections.unmodifiableMap(tempered);
    }

}
