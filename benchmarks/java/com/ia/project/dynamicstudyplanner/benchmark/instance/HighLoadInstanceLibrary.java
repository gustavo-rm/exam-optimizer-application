package com.ia.project.dynamicstudyplanner.benchmark.instance;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exam.ThematicAxis;
import com.ia.project.dynamicstudyplanner.ga.fitness.objective.LearningModel;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * A controlled family of instances that sweeps one variable: the <b>demand ratio</b>
 *
 * <pre>
 *   R = (SUM_s requiredSessions(s)) / totalStudyDays
 * </pre>
 *
 * where {@code requiredSessions(s) = horizon / tau(s)} is what {@code RetentionObjective} needs per
 * subject to keep it above the forgetting threshold. {@code R &lt; 1} means the budget can cover the
 * whole syllabus; {@code R = 4} means it can cover a quarter of it.
 * <p>
 * Built for {@code docs/revisao-ag/06-regime-alta-carga.md}. The original library varies many things
 * at once, which is right for a benchmark but useless for locating a boundary: {@code I7} sits at
 * {@code R ≈ 2.3} with the fitness/retention correlation neutralised, and {@code I8} at
 * {@code R ≈ 4.3} with it still at −0.88. Everything else differs between the two, so nothing could
 * be attributed to the ratio. Here <b>only the budget moves</b> — same subjects, same weights, same
 * loads, same horizon, same availability — so any change in behaviour is the ratio's doing.
 */
public final class HighLoadInstanceLibrary {

    /** Target ratios, from comfortable to severely starved. */
    private static final double[] TARGET_RATIOS = {0.5, 0.75, 1.0, 1.5, 2.0, 3.0, 4.0, 5.0};

    private static final int SUBJECT_COUNT = 20;
    private static final int HORIZON_DAYS = 300;
    private static final long SEED = 606_060L;

    private static final ImportanceCalculator IMPORTANCE = new ImportanceCalculator();
    private static final BaselineCalculator BASELINE = new BaselineCalculator(IMPORTANCE);

    private HighLoadInstanceLibrary() {
    }

    /**
     * One instance per target ratio, all sharing the same exam and student.
     *
     * @return eight instances, ordered from the most comfortable budget to the tightest
     */
    public static List<BenchmarkInstance> all() {
        Exam exam = sharedExam();
        StudentProfile profile = sharedProfile(exam);

        double demand = totalRequiredSessions(exam);
        int floor = BASELINE.calculateMinimumDays(exam, profile).values().stream()
                .mapToInt(Integer::intValue).sum();

        List<BenchmarkInstance> instances = new ArrayList<>();
        for (double target : TARGET_RATIOS) {
            int budget = (int) Math.round(demand / target);

            // The minimum-days floor is a hard lower bound on the budget; a target ratio that would
            // need less than the floor is unreachable. Raising the budget lowers the achieved ratio,
            // which is recorded in the description rather than hidden.
            int feasibleBudget = Math.max(budget, floor);
            double achieved = demand / feasibleBudget;

            String note = feasibleBudget == budget
                    ? String.format("Razao demanda/orcamento alvo %.2f, atingida %.2f.", target, achieved)
                    : String.format("Razao alvo %.2f inatingivel: exigiria orcamento de %d dias contra "
                            + "um piso de dias minimos de %d. Orcamento elevado ao piso; razao atingida %.2f.",
                            target, budget, floor, achieved);

            instances.add(new BenchmarkInstance(
                    String.format("R%.2f", achieved).replace('.', '_'),
                    note + " 20 disciplinas, horizonte de 300 dias, 21h/semana. Unico fator que varia "
                            + "entre as instancias desta familia e o orcamento de dias.",
                    exam, profile, InstanceLibrary.ANCHOR,
                    feasibleBudget, 100, 50));
        }
        return List.copyOf(instances);
    }

    /**
     * A second controlled sweep, this time on <b>subject count</b> with the demand ratio held
     * roughly constant at 2.0.
     * <p>
     * Needed because the budget sweep in {@link #all()} falsified the hypothesis it was built to
     * test: the retention metric saturates at 100% for every planner at every ratio from 0.5 to 5.0,
     * so the demand ratio is not what drives the fitness/retention correlation. The instances that do
     * show a correlation ({@code I3}, {@code I4}, {@code I8}) differ from this family mainly in how
     * many subjects compete for the calendar — and the tactical scheduler
     * {@code InterleavedCriticalStrategy} only studies the three most critical subjects per day, so
     * subject count plausibly decides how many subjects the schedule can keep fresh at all.
     * <p>
     * Budget scales with subject count to hold the ratio fixed, so the only variable is {@code n}.
     *
     * @return seven instances from 5 to 40 subjects
     */
    public static List<BenchmarkInstance> bySubjectCount() {
        int[] counts = {5, 10, 15, 20, 25, 30, 40};
        List<BenchmarkInstance> instances = new ArrayList<>();

        for (int n : counts) {
            Exam exam = examWith(n);
            StudentProfile profile = sharedProfile(exam);

            double demand = totalRequiredSessions(exam);
            int floor = BASELINE.calculateMinimumDays(exam, profile).values().stream()
                    .mapToInt(Integer::intValue).sum();
            int budget = Math.max((int) Math.round(demand / 2.0), floor);

            instances.add(new BenchmarkInstance(
                    String.format("N%02d", n),
                    String.format("%d disciplinas, horizonte de 300 dias, 21h/semana, orcamento de "
                            + "%d dias (razao demanda/orcamento %.2f). Unico fator que varia nesta "
                            + "familia e o numero de disciplinas.", n, budget, demand / budget),
                    exam, profile, InstanceLibrary.ANCHOR, budget, 100, 50));
        }
        return List.copyOf(instances);
    }

    /**
     * A third controlled sweep, on the <b>dispersion of the thematic-axis weights</b>, with subject
     * count, horizon, budget and availability all fixed.
     * <p>
     * The two sweeps above both failed to reproduce the negative correlation, so neither budget
     * tightness nor subject count explains it. Weight dispersion is the remaining candidate: it is
     * what most distinguishes {@code I4-pesos-extremos}, the instance with the strongest surviving
     * negative correlation, from everything else.
     *
     * @return seven instances with axis weights spanning ratios from 1:1 to 1000:1
     */
    public static List<BenchmarkInstance> byWeightDispersion() {
        double[] spreads = {1.0, 3.0, 10.0, 30.0, 100.0, 300.0, 1000.0};
        List<BenchmarkInstance> instances = new ArrayList<>();

        for (double spread : spreads) {
            Exam exam = examWith(SUBJECT_COUNT, spread);
            StudentProfile profile = sharedProfile(exam);

            double demand = totalRequiredSessions(exam);
            int floor = BASELINE.calculateMinimumDays(exam, profile).values().stream()
                    .mapToInt(Integer::intValue).sum();
            int budget = Math.max((int) Math.round(demand / 2.0), floor);

            instances.add(new BenchmarkInstance(
                    String.format("W%04d", (int) spread),
                    String.format("Dispersao de peso dos eixos %.0f:1, 20 disciplinas, horizonte de "
                            + "300 dias, orcamento de %d dias. Unico fator que varia nesta familia e "
                            + "a dispersao dos pesos.", spread, budget),
                    exam, profile, InstanceLibrary.ANCHOR, budget, 100, 50));
        }
        return List.copyOf(instances);
    }

    /**
     * Sessions the retention objective asks for across the whole syllabus, at this horizon.
     * This is the numerator of the ratio and is constant across the family.
     */
    public static double totalRequiredSessions(Exam exam) {
        return exam.getAllSubjects().stream()
                .mapToDouble(s -> LearningModel.requiredSessions(s, HORIZON_DAYS))
                .sum();
    }

    /**
     * Ratio between the largest and the smallest normalised importance in an instance.
     * <p>
     * This is the <b>effective</b> dispersion the fitness actually sees. It is not the same as the
     * thematic-axis weight ratio: importance is {@code questionCount x axisWeight x knowledgeGap}, so
     * an exam with uniform axis weights can still present a wide spread through the other two
     * factors. Reporting it makes instances from different libraries comparable on the one axis that
     * turned out to matter.
     */
    public static double importanceDispersion(BenchmarkInstance instance) {
        Map<Subject, Double> importance =
                IMPORTANCE.calculatePersonalizedImportance(instance.exam(), instance.profile());
        double max = importance.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = importance.values().stream().mapToDouble(Double::doubleValue)
                .filter(v -> v > 0).min().orElse(0);
        return min <= 0 ? Double.POSITIVE_INFINITY : max / min;
    }

    /** The demand ratio of any instance, whichever library it came from. */
    public static double demandRatio(BenchmarkInstance instance) {
        double demand = instance.exam().getAllSubjects().stream()
                .mapToDouble(s -> LearningModel.requiredSessions(s, (int) instance.horizonDays()))
                .sum();
        return demand / instance.totalStudyDays();
    }

    // ------------------------------------------------------------------

    /**
     * One exam shared by every instance in the family: 20 subjects across three thematic axes, with
     * question counts and cognitive loads spread so that importances and time constants vary
     * realistically rather than degenerately.
     */
    private static Exam sharedExam() {
        return examWith(SUBJECT_COUNT);
    }

    /** The same exam shape at an arbitrary subject count, for the subject-count sweep. */
    private static Exam examWith(int subjectCount) {
        return examWith(subjectCount, 3.0);
    }

    /** The exam shape with an explicit ratio between the largest and smallest axis weight. */
    private static Exam examWith(int subjectCount, double weightSpread) {
        Random rng = new Random(SEED);
        int generalCount = Math.max(1, subjectCount / 3);

        List<Subject> general = new ArrayList<>();
        for (int i = 0; i < generalCount; i++) {
            general.add(new Subject("HL-GK-" + i, 5 + rng.nextInt(16), 1 + rng.nextInt(5)));
        }

        List<List<Subject>> buckets = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < subjectCount - generalCount; i++) {
            buckets.get(i % 3).add(new Subject("HL-SP-" + i, 5 + rng.nextInt(16), 1 + rng.nextInt(5)));
        }

        // Geometric progression from minWeight to minWeight * weightSpread across the three axes.
        List<ThematicAxis> axes = new ArrayList<>();
        double minWeight = Math.max(0.1, 3.0 / Math.sqrt(weightSpread));
        for (int a = 0; a < 3; a++) {
            if (!buckets.get(a).isEmpty()) {
                double weight = minWeight * Math.pow(weightSpread, a / 2.0);
                axes.add(new ThematicAxis(a + 1, "Eixo " + (a + 1), weight, buckets.get(a)));
            }
        }

        return new Exam("Exame alta carga " + subjectCount, InstanceLibrary.ANCHOR.plusDays(HORIZON_DAYS),
                100.0, general, axes);
    }

    private static StudentProfile sharedProfile(Exam exam) {
        Random rng = new Random(SEED + 1);
        Map<Subject, Double> gaps = new HashMap<>();
        for (Subject subject : exam.getAllSubjects()) {
            gaps.put(subject, 1.0 + rng.nextDouble() * 4.0);
        }

        Map<DayOfWeek, Integer> availability = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            availability.put(day, 3);
        }

        return new StudentProfile("Aluno alta carga", gaps, availability,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }
}
