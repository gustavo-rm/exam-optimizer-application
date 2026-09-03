package com.ia.project.dynamicstudyplanner.benchmark.instance;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exam.ThematicAxis;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds the synthetic benchmark instances.
 * <p>
 * Every instance is generated from a fixed seed, so the library is deterministic across runs,
 * machines and JVM versions. The instances deliberately span the dimensions that the audit
 * (docs/revisao-ag/01-auditoria-fitness.md) flagged as behaviour-changing:
 * <ul>
 *   <li><b>Number of subjects</b> — 6 to 40, which changes how much the minimum-days floor
 *       consumes of the total budget.</li>
 *   <li><b>Time pressure</b> — from comfortable surplus to hard deficit, which decides whether
 *       {@code StudyScheduleGenerator} has to compress the plan.</li>
 *   <li><b>Weight dispersion</b> — near-uniform versus three orders of magnitude, which is the
 *       axis along which the audit predicted winner-take-all degeneracy (§2.1.4).</li>
 *   <li><b>Planning horizon</b> — 40 to 400 days, which decides how much forgetting matters.</li>
 * </ul>
 * <p>
 * All instances are validated as feasible: {@code StudyPlanFactory} throws when the sum of the
 * minimum days exceeds the total budget, so {@link #withFeasibleBudget} raises the budget when
 * the requested one would be rejected. The adjustment is recorded in the instance description.
 */
public final class InstanceLibrary {

    /**
     * Fixed anchor for every instance. Using a constant rather than {@code LocalDate.now()} keeps
     * the schedule-derived business metrics reproducible regardless of when the harness runs.
     * It is a Monday, so weekly availability patterns line up predictably.
     */
    public static final LocalDate ANCHOR = LocalDate.of(2026, 1, 5);

    private static final ImportanceCalculator IMPORTANCE = new ImportanceCalculator();
    private static final BaselineCalculator BASELINE = new BaselineCalculator(IMPORTANCE);

    private InstanceLibrary() {
    }

    /**
     * The full instance set used by the report harness.
     *
     * @return eight deterministic instances, ordered from smallest to most demanding.
     */
    public static List<BenchmarkInstance> all() {
        return List.of(
                smallComfortable(),
                mediumTypical(),
                largeTightDeadline(),
                extremeWeightDispersion(),
                balancedWeights(),
                shortSprint(),
                longMarathon(),
                scaleStress()
        );
    }

    /**
     * The subset used by the automated regression test: everything except the scale-stress
     * instance, which is too slow for a unit-test budget.
     *
     * @return seven instances that together run in a few seconds per repetition.
     */
    public static List<BenchmarkInstance> fastSubset() {
        List<BenchmarkInstance> fast = new ArrayList<>(all());
        fast.removeIf(i -> i.id().equals("I8-escala"));
        return List.copyOf(fast);
    }

    // ------------------------------------------------------------------
    // Instances
    // ------------------------------------------------------------------

    private static BenchmarkInstance smallComfortable() {
        return build("I1-pequeno-folgado",
                "6 disciplinas, 120 dias de horizonte, 21h/semana. Tempo sobra: cenario onde "
                        + "praticamente qualquer plano cabe no calendario.",
                101L, 2, 4, 120, hours(3, 3, 3, 3, 3, 3, 3),
                new StudentState(2.0, 2.0, 4.0, Chronotype.INTERMEDIATE),
                80, 100, 50, 1.0);
    }

    private static BenchmarkInstance mediumTypical() {
        return build("I2-medio-tipico",
                "12 disciplinas, 180 dias, 25h/semana. Perfil tipico de concurso de medio porte.",
                102L, 4, 8, 180, hours(4, 4, 4, 4, 3, 3, 3),
                new StudentState(3.0, 3.0, 3.0, Chronotype.MORNING_LARK),
                140, 100, 50, 1.0);
    }

    private static BenchmarkInstance largeTightDeadline() {
        return build("I3-grande-apertado",
                "25 disciplinas, 150 dias, 14h/semana. Deficit de tempo severo: o agendador "
                        + "tatico tera de comprimir o plano.",
                103L, 8, 17, 150, hours(2, 2, 2, 2, 2, 2, 2),
                new StudentState(4.5, 4.0, 2.0, Chronotype.NIGHT_OWL),
                200, 100, 50, 1.0);
    }

    private static BenchmarkInstance extremeWeightDispersion() {
        return build("I4-pesos-extremos",
                "10 disciplinas com pesos de eixo cobrindo 3 ordens de grandeza (0.1 a 100). "
                        + "Instancia desenhada para expor a degeneracao winner-take-all da auditoria.",
                104L, 3, 7, 200, hours(4, 4, 4, 4, 4, 5, 5),
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE),
                160, 100, 50, 1000.0);
    }

    private static BenchmarkInstance balancedWeights() {
        return build("I5-pesos-balanceados",
                "10 disciplinas com pesos quase uniformes (razao ~2:1). Controle experimental "
                        + "para I4: mesma escala, dispersao de peso oposta.",
                105L, 3, 7, 200, hours(4, 4, 4, 4, 4, 5, 5),
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE),
                160, 100, 50, 2.0);
    }

    private static BenchmarkInstance shortSprint() {
        return build("I6-horizonte-curto",
                "8 disciplinas, 40 dias, 31h/semana. Reta final: pouco tempo, muita carga diaria.",
                106L, 3, 5, 40, hours(4, 4, 4, 4, 4, 6, 5),
                new StudentState(4.0, 3.5, 4.0, Chronotype.MORNING_LARK),
                50, 100, 50, 1.0);
    }

    private static BenchmarkInstance longMarathon() {
        return build("I7-horizonte-longo",
                "15 disciplinas, 400 dias, 10h/semana. Maratona: e o cenario em que o "
                        + "esquecimento deveria pesar mais na qualidade do plano.",
                107L, 5, 10, 400, hours(1, 1, 1, 1, 2, 2, 2),
                new StudentState(2.5, 2.5, 3.5, Chronotype.NIGHT_OWL),
                260, 100, 50, 1.0);
    }

    private static BenchmarkInstance scaleStress() {
        return build("I8-escala",
                "40 disciplinas, 300 dias, 35h/semana, populacao 200 x 400 geracoes. "
                        + "Instancia de estresse: mede o custo do AG na configuracao mais pesada.",
                108L, 12, 28, 300, hours(5, 5, 5, 5, 5, 5, 5),
                new StudentState(3.0, 3.5, 3.0, Chronotype.INTERMEDIATE),
                280, 400, 200, 1.0);
    }

    // ------------------------------------------------------------------
    // Construction helpers
    // ------------------------------------------------------------------

    /**
     * Assembles one instance and guarantees it is solvable.
     *
     * @param id                 stable identifier
     * @param description        human-readable summary
     * @param seed               RNG seed; fixes question counts, cognitive loads and knowledge gaps
     * @param generalSubjects    number of general-knowledge subjects
     * @param specificSubjects   number of specific-knowledge subjects, spread across 3 axes
     * @param horizonDays        calendar days between {@link #ANCHOR} and the exam date
     * @param weeklyAvailability hours available per weekday
     * @param state              psychological state
     * @param desiredStudyDays   requested day budget; raised if the minimum-days floor exceeds it
     * @param numGenerations     GA generations
     * @param populationSize     GA population size
     * @param weightSpread       ratio between the largest and smallest thematic-axis weight
     */
    private static BenchmarkInstance build(
            String id, String description, long seed,
            int generalSubjects, int specificSubjects, int horizonDays,
            Map<DayOfWeek, Integer> weeklyAvailability, StudentState state,
            int desiredStudyDays, int numGenerations, int populationSize,
            double weightSpread
    ) {
        Random rng = new Random(seed);

        List<Subject> general = new ArrayList<>();
        for (int i = 0; i < generalSubjects; i++) {
            general.add(new Subject("GK-" + id + "-" + i, 5 + rng.nextInt(16), 1 + rng.nextInt(5)));
        }

        List<ThematicAxis> axes = buildAxes(id, rng, specificSubjects, weightSpread);

        Exam exam = new Exam(
                "Exame " + id,
                ANCHOR.plusDays(horizonDays),
                100.0,
                general,
                axes
        );

        Map<Subject, Double> gaps = new HashMap<>();
        for (Subject subject : exam.getAllSubjects()) {
            // Knowledge gap on the 1.0 (strong) .. 5.0 (weak) scale validated by StudentProfileDto.
            gaps.put(subject, 1.0 + rng.nextDouble() * 4.0);
        }

        StudentProfile profile = new StudentProfile("Aluno " + id, gaps, weeklyAvailability, state);

        int studyDays = withFeasibleBudget(exam, profile, desiredStudyDays);
        String note = studyDays == desiredStudyDays
                ? description
                : description + " (orcamento elevado de " + desiredStudyDays + " para " + studyDays
                        + " dias para respeitar o piso de dias minimos)";

        return new BenchmarkInstance(id, note, exam, profile, ANCHOR,
                studyDays, numGenerations, populationSize);
    }

    /**
     * Distributes the specific-knowledge subjects across three thematic axes whose weights span
     * {@code weightSpread}, geometrically. A spread of 1000 puts weights at 0.1 / 3.16 / 100,
     * reproducing the payload-driven dispersion the audit quantified.
     */
    private static List<ThematicAxis> buildAxes(String id, Random rng, int specificSubjects, double weightSpread) {
        int axisCount = 3;
        double minWeight = Math.max(0.1, 3.0 / Math.sqrt(weightSpread));

        List<List<Subject>> buckets = new ArrayList<>();
        for (int a = 0; a < axisCount; a++) {
            buckets.add(new ArrayList<>());
        }
        for (int i = 0; i < specificSubjects; i++) {
            buckets.get(i % axisCount)
                    .add(new Subject("SP-" + id + "-" + i, 5 + rng.nextInt(16), 1 + rng.nextInt(5)));
        }

        List<ThematicAxis> axes = new ArrayList<>();
        for (int a = 0; a < axisCount; a++) {
            if (buckets.get(a).isEmpty()) {
                continue;
            }
            // Geometric progression from minWeight up to minWeight * weightSpread.
            double weight = minWeight * Math.pow(weightSpread, a / (double) (axisCount - 1));
            axes.add(new ThematicAxis(a + 1, "Eixo " + (a + 1), weight, buckets.get(a)));
        }
        return axes;
    }

    /**
     * Raises the day budget when the minimum-days floor computed by {@link BaselineCalculator}
     * would exceed it, which {@code StudyPlanFactory} rejects with a {@code DomainException}
     * (reclassified in etapa 03d — see ADR-0005; it used to be an {@code IllegalArgumentException}).
     * A 25% headroom above the floor leaves the optimizer real decisions to make; without it the
     * instance would be trivially determined by the constraint alone.
     */
    private static int withFeasibleBudget(Exam exam, StudentProfile profile, int desiredStudyDays) {
        int floor = BASELINE.calculateMinimumDays(exam, profile).values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int required = (int) Math.ceil(floor * 1.25);
        return Math.max(desiredStudyDays, required);
    }

    private static Map<DayOfWeek, Integer> hours(int mon, int tue, int wed, int thu, int fri, int sat, int sun) {
        Map<DayOfWeek, Integer> map = new EnumMap<>(DayOfWeek.class);
        map.put(DayOfWeek.MONDAY, mon);
        map.put(DayOfWeek.TUESDAY, tue);
        map.put(DayOfWeek.WEDNESDAY, wed);
        map.put(DayOfWeek.THURSDAY, thu);
        map.put(DayOfWeek.FRIDAY, fri);
        map.put(DayOfWeek.SATURDAY, sat);
        map.put(DayOfWeek.SUNDAY, sun);
        return map;
    }
}
