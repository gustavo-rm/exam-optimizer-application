package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.domain.StudyBlock;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.SubjectRetentionState;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.service.StudyScheduleGenerator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.CognitiveLoadBalancingStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.InterleavedCriticalStrategy;
import com.ia.project.dynamicstudyplanner.service.scheduler.strategy.ReviewFocusedStrategy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a macro {@link StudyPlan} into the comparable metrics of {@link PlanMetrics}.
 * <p>
 * Every metric is computed with production classes — {@code FitnessEvaluator},
 * {@code StudyScheduleGenerator}, {@code CognitiveLoadCalculator}, {@code HybridRetentionEngine} —
 * so the numbers describe what the shipped system would actually do with each plan, not a
 * reimplementation of it. No production class is modified or subclassed.
 */
public final class MetricsCalculator {

    /**
     * Recall grade fed to the SM-2 update for every simulated study session. Grade 4 is a successful
     * recall that leaves the easiness factor unchanged at its 2.5 default, so the simulated interval
     * growth is driven purely by how often and how late a subject is studied — which is exactly the
     * property being compared between planners.
     */
    private static final int ASSUMED_RECALL_GRADE = 4;

    /**
     * Production threshold below which {@code HybridRetentionEngine} makes a review mandatory.
     * <p>
     * Read from the engine rather than duplicated, so the business metric and the production rule
     * cannot drift apart. It was a hardcoded 0.85 here while the engine also hardcoded 0.85; the
     * engine's value has since been corrected to {@code e^-1} for consistency with its SM-2 half
     * (docs/revisao-ag/01-auditoria-fitness.md Apendice C), and this metric follows it.
     */
    private static final double RETENTION_WINDOW_THRESHOLD =
            HybridRetentionEngine.MANDATORY_REVIEW_THRESHOLD;

    private final FitnessEvaluator fitnessEvaluator;
    private final StudyScheduleGenerator scheduleGenerator = new StudyScheduleGenerator();
    private final CognitiveLoadCalculator cognitiveLoadCalculator = new CognitiveLoadCalculator();
    private final HybridRetentionEngine retentionEngine = new HybridRetentionEngine();

    public MetricsCalculator(FitnessEvaluator fitnessEvaluator) {
        this.fitnessEvaluator = fitnessEvaluator;
    }

    /**
     * Measures one plan.
     *
     * @param instance      the instance the plan was produced for
     * @param context       the evolution context used for fitness scoring
     * @param plan          the macro plan under measurement
     * @param elapsedMillis wall-clock time the planner took, measured by the harness
     */
    public PlanMetrics measure(BenchmarkInstance instance, EvolutionContext context,
                               StudyPlan plan, long elapsedMillis) {

        double fitness = fitnessEvaluator.evaluate(plan, context);
        int maxDailyLoad = cognitiveLoadCalculator.calculate(instance.profile(), instance.exam());

        // Schedule 1: exactly the chain DynamicStudyPlannerService composes in production. This is
        // what the student would receive, so retention and delivered-hours are measured on it.
        AllocationStrategy productionChain = new ReviewFocusedStrategy(
                new CognitiveLoadBalancingStrategy(new InterleavedCriticalStrategy(), maxDailyLoad));
        ScheduleResult delivered = scheduleGenerator.generate(
                plan, instance.profile(), instance.exam(), instance.planStartDate(), productionChain);

        // Schedule 2: the same chain with the load-balancing pruner removed. Needed because the
        // pruner drops blocks until the budget is met, which would report zero overload for every
        // planner and hide the difference. This variant shows the load the macro plan really implies.
        AllocationStrategy unprunedChain = new ReviewFocusedStrategy(new InterleavedCriticalStrategy());
        ScheduleResult unpruned = scheduleGenerator.generate(
                plan, instance.profile(), instance.exam(), instance.planStartDate(), unprunedChain);

        return new PlanMetrics(
                fitness,
                elapsedMillis,
                meanRetentionAtExam(delivered, instance),
                pctInRetentionWindow(delivered, instance),
                countOverloadDays(unpruned, maxDailyLoad),
                scheduledHoursRatio(delivered),
                topSubjectShare(plan),
                delivered.status()
        );
    }

    // ------------------------------------------------------------------
    // Retention
    // ------------------------------------------------------------------

    /**
     * Replays the delivered schedule chronologically through the production retention engine and
     * returns each subject's predicted recall on the exam date.
     * <p>
     * Each study day for a subject counts as one SM-2 review, so a subject studied often and late
     * ends with a long stability interval and a recent review date; one studied early and abandoned
     * decays. Subjects that never appear in the schedule score 0.
     */
    private Map<Subject, Double> retentionBySubject(ScheduleResult result, BenchmarkInstance instance) {
        Map<Subject, SubjectRetentionState> states = new HashMap<>();

        List<LocalDate> orderedDates = result.schedule().keySet().stream().sorted().toList();
        for (LocalDate date : orderedDates) {
            for (StudyBlock block : result.schedule().get(date)) {
                SubjectRetentionState current = states.get(block.subject());
                if (current == null) {
                    current = new SubjectRetentionState(date);
                }
                states.put(block.subject(), retentionEngine.processReview(current, date, ASSUMED_RECALL_GRADE));
            }
        }

        LocalDate examDate = instance.exam().getExamDate();
        Map<Subject, Double> retention = new HashMap<>();
        for (Subject subject : instance.exam().getAllSubjects()) {
            // A null state yields 0.0 from the production engine, which is the intended reading:
            // a subject that was never studied is not retained.
            retention.put(subject, retentionEngine.calculateRetentionProbability(states.get(subject), examDate));
        }
        return retention;
    }

    private double meanRetentionAtExam(ScheduleResult result, BenchmarkInstance instance) {
        return retentionBySubject(result, instance).values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
    }

    private double pctInRetentionWindow(ScheduleResult result, BenchmarkInstance instance) {
        Map<Subject, Double> retention = retentionBySubject(result, instance);
        if (retention.isEmpty()) {
            return 0.0;
        }
        long inside = retention.values().stream()
                .filter(r -> r >= RETENTION_WINDOW_THRESHOLD)
                .count();
        return inside / (double) retention.size();
    }

    // ------------------------------------------------------------------
    // Cognitive load and delivery
    // ------------------------------------------------------------------

    /**
     * Counts days whose summed {@code hours * cognitiveLoad} exceeds the student's daily budget —
     * the same arithmetic {@code CognitiveLoadBalancingStrategy} uses when it decides to prune.
     */
    private int countOverloadDays(ScheduleResult result, int maxDailyLoad) {
        int violations = 0;
        for (List<StudyBlock> blocks : result.schedule().values()) {
            int load = blocks.stream()
                    .mapToInt(b -> b.hours() * b.subject().cognitiveLoad())
                    .sum();
            if (load > maxDailyLoad) {
                violations++;
            }
        }
        return violations;
    }

    /** Delivered study hours over requested study hours; below 1.0 means the plan was truncated. */
    private double scheduledHoursRatio(ScheduleResult result) {
        if (result.requiredHours() <= 0.0) {
            return 0.0;
        }
        double scheduled = result.schedule().values().stream()
                .flatMap(List::stream)
                .mapToInt(StudyBlock::hours)
                .sum();
        return scheduled / result.requiredHours();
    }

    /** Fraction of the day budget held by the single largest allocation. */
    private double topSubjectShare(StudyPlan plan) {
        int total = plan.getTotalDays();
        if (total <= 0) {
            return 0.0;
        }
        int max = plan.getDaysPerSubject().values().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        return max / (double) total;
    }
}
