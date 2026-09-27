package com.ia.project.dynamicstudyplanner.benchmark.instance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeProvenance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The measured instances: an explicit factorial in the topic domain, generated deterministically.
 *
 * <h2>The factorial, and why these four axes</h2>
 *
 * <pre>
 *   topics      {10, 25}              how much there is to plan
 *   density     {0.4, 1.2}            prerequisite edges per topic — how constrained the order is
 *   tightness   {0.7, 1.0, 1.5}       available minutes / first-pass minutes demanded
 *   spread      {compact, sparse}     the same minutes on consecutive days, or every other day
 * </pre>
 *
 * 2 x 2 x 3 x 2 = <b>24 instances</b>. The axes are crossed fully rather than varied one at a time,
 * because the question is where the engines differ, and "they differ only when the calendar is
 * tight <i>and</i> the graph is dense" is an interaction a one-at-a-time sweep cannot see.
 *
 * <p><b>Tightness carries three levels and size carries two</b>, deliberately. A bigger syllabus
 * makes every engine slower without changing what either one decides; a calendar that cannot hold
 * the syllabus forces both to choose what to drop, which is where an ordering stage can differ from
 * another at all. 0.7 cannot fit the first passes, 1.0 fits them exactly, 1.5 leaves room for
 * revisions — and that room is what gives the search degrees of freedom to spend.
 *
 * <p><b>{@code spread} is not a size axis in disguise.</b> It holds the available minutes fixed and
 * doubles the calendar they are spread over, which halves the hours per horizon day. That is the one
 * input {@code SinapseLoadBudget} derives the daily ceiling from, so the sparse half of the
 * factorial is where the load term can bind at all.
 *
 * <h2>Determinism</h2>
 *
 * Everything is a pure function of {@link #GENERATOR_SEED} and the coordinates: topic identifiers,
 * lengths, effort tiers, and which pairs get an edge. Regenerating the library on another machine or
 * another day produces identical requests, which is what lets a measurement be repeated rather than
 * merely re-run. The seed is fixed here, in the repository, and changing it invalidates every
 * recorded number — so it is written down rather than passed in.
 *
 * <h2>Three subjects, at three priorities — not one</h2>
 *
 * The default importance strategy is {@code goal-priority}, which reads
 * {@code goals[].priority} and gives <b>every topic of a subject the same weight</b>. A library with
 * one subject would hand every topic the same importance and flatten {@code syllabusMastery} — half
 * the fitness — into a constant, leaving the genetic engine nothing to discriminate on in the term
 * that dominates it. Measuring the search on instances where its main signal is constant would
 * understate it, and it would understate it invisibly.
 *
 * <p>So topics are dealt round-robin across {@value #SUBJECTS} subjects whose goals declare
 * {@link #PRIORITIES}. This also gives the greedy baseline its first ordering criterion something to
 * order by, which is the comparison's own precondition: both engines have to be able to express a
 * preference before a difference between their preferences means anything.
 *
 * <h2>Two properties the generator guarantees</h2>
 *
 * <b>Every {@code HARD} graph is acyclic, under every provenance subset.</b> Hard edges only ever
 * point from a lower arrival index to a higher one, so no subset of them can close a cycle. An
 * instance refused for a cycle would measure the refusal path, not the schedulers.
 *
 * <p><b>Every window can hold the longest topic.</b> Windows are {@value #WINDOW_MINUTES} minutes and
 * topics are capped at {@value #MAX_TOPIC_MINUTES}, because a topic longer than every window fits
 * nowhere and the instance collapses to an empty-plan refusal regardless of engine.
 */
public final class InstanceLibrary {

    /** Fixes the whole library. Changing it invalidates every recorded measurement. */
    public static final long GENERATOR_SEED = 20260925L;

    /** Minutes of availability on each day that has any. */
    public static final int WINDOW_MINUTES = 240;

    /** Longest topic the generator emits; must not exceed {@link #WINDOW_MINUTES}. */
    public static final int MAX_TOPIC_MINUTES = 180;

    /** Shortest topic the generator emits. */
    public static final int MIN_TOPIC_MINUTES = 30;

    /** How many subjects the topics are dealt across. */
    public static final int SUBJECTS = 3;

    /** The goal priority of each subject, in subject order. Distinct, so importance varies. */
    public static final int[] PRIORITIES = {5, 3, 1};

    /** Factorial level: how many topics are in scope. */
    public static final int[] TOPICS = {10, 25};

    /** Factorial level: prerequisite edges per topic. */
    public static final double[] DENSITY = {0.4, 1.2};

    /** Factorial level: available minutes over first-pass minutes demanded. */
    public static final double[] TIGHTNESS = {0.7, 1.0, 1.5};

    /** Factorial level: {@code false} packs the days together, {@code true} skips every other day. */
    public static final boolean[] SPREAD = {false, true};

    /** First day of every instance's horizon, so two instances differ only by their coordinates. */
    private static final LocalDate START = LocalDate.parse("2026-10-05");

    /** The closed set of {@code effortTier}, cycled so every instance carries all four bands. */
    private static final String[] TIERS = {"SHORT", "STANDARD", "LONG", "EXTENDED"};

    /** Cycled over the edges so each provenance condition removes a different slice. */
    private static final EdgeProvenance[] PROVENANCES = {
        EdgeProvenance.CURATED, EdgeProvenance.TEXTBOOK_ORDER, EdgeProvenance.DERIVED};

    private InstanceLibrary() {
    }

    /** @return the full factorial, in a fixed order */
    public static List<BenchmarkInstance> all() {
        List<BenchmarkInstance> instances = new ArrayList<>();
        for (int topics : TOPICS) {
            for (double density : DENSITY) {
                for (double tightness : TIGHTNESS) {
                    for (boolean spread : SPREAD) {
                        instances.add(build(topics, density, tightness, spread));
                    }
                }
            }
        }
        return List.copyOf(instances);
    }

    /**
     * Builds one instance.
     *
     * @param topics    how many topics
     * @param density   edges per topic
     * @param tightness available over demanded minutes
     * @param spread    whether the available days are consecutive or every other day
     * @return the instance, with its coordinates attached
     */
    public static BenchmarkInstance build(int topics, double density, double tightness,
            boolean spread) {

        // One stream per instance, derived from the library seed and the coordinates, so that adding
        // a level to one axis cannot shift the topics of an instance on another.
        Random random = new Random(GENERATOR_SEED
                + 1000L * topics + 100L * Math.round(density * 10) + Math.round(tightness * 10)
                + (spread ? 7L : 0L));

        List<PlanRequest.Topic> topicList = topics(topics, random);
        long demanded = topicList.stream().mapToLong(PlanRequest.Topic::estimatedMinutes).sum();

        long targetMinutes = Math.round(tightness * demanded);
        int days = Math.max(1, (int) Math.ceil(targetMinutes / (double) WINDOW_MINUTES));
        int horizonDays = spread ? days * 2 - 1 : days;
        LocalDate end = START.plusDays(horizonDays - 1L);

        PlanRequest request = new PlanRequest(PlanRequest.VERSION,
                new PlanRequest.Horizon(START, end),
                availability(days, spread),
                goals(end),
                topicList,
                edges(topicList, density, random),
                List.of(), Map.of(), 0L);

        return new BenchmarkInstance(id(topics, density, tightness, spread), topics, density,
                tightness, horizonDays, request, demanded, (long) days * WINDOW_MINUTES);
    }

    /** {@code t10-d0.4-a0.7-compact} — readable, and derived from the coordinates it names. */
    private static String id(int topics, double density, double tightness, boolean spread) {
        return "t%d-d%.1f-a%.1f-%s".formatted(topics, density, tightness,
                spread ? "sparse" : "compact");
    }

    /**
     * One goal per subject, at the declared priority, all due on the horizon's last day.
     *
     * <p>The target dates are identical on purpose. {@code StudyOrder} reads the date as a tie-break
     * <i>within</i> a priority level, so distinct dates would vary a second ordering criterion at the
     * same time as the first and make the greedy baseline's order attributable to neither.
     */
    private static List<PlanRequest.Goal> goals(LocalDate end) {
        List<PlanRequest.Goal> goals = new ArrayList<>(SUBJECTS);
        for (int subject = 0; subject < SUBJECTS; subject++) {
            goals.add(new PlanRequest.Goal(subjectId(subject), end, PRIORITIES[subject]));
        }
        return goals;
    }

    /**
     * The topics, dealt round-robin across the subjects.
     *
     * <p>{@code position} counts within the subject, which is what the field means — it is the
     * curricular position, and {@code StudyOrder} relies on it being unique inside one subject.
     */
    private static List<PlanRequest.Topic> topics(int count, Random random) {
        List<PlanRequest.Topic> topics = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int minutes = MIN_TOPIC_MINUTES
                    + random.nextInt(MAX_TOPIC_MINUTES - MIN_TOPIC_MINUTES + 1);
            topics.add(new PlanRequest.Topic(topicId(index), subjectId(index % SUBJECTS),
                    index / SUBJECTS + 1, TIERS[index % TIERS.length], minutes));
        }
        return topics;
    }

    /**
     * {@code round(density * topics)} edges, alternating {@code HARD} and {@code SOFT}.
     *
     * <p>A hard edge always points from a lower arrival index to a higher one, which is what makes
     * every provenance subset acyclic. A soft edge points <b>backwards</b> half the time: a
     * preference that agrees with the order it is measured against can never be inverted, and an
     * instance where nothing can be violated cannot tell a repairing engine from one that does not
     * repair.
     *
     * <p>Provenance cycles through all three values so the {@code curated} condition keeps roughly a
     * third of the edges, {@code curated-textbook} two thirds and {@code all} everything — the
     * cumulative ladder the ablation needs.
     */
    private static List<PlanRequest.PrerequisiteEdge> edges(List<PlanRequest.Topic> topics,
            double density, Random random) {

        int wanted = (int) Math.round(density * topics.size());
        List<PlanRequest.PrerequisiteEdge> edges = new ArrayList<>(wanted);
        for (int index = 0; index < wanted; index++) {
            int low = random.nextInt(topics.size() - 1);
            int high = low + 1 + random.nextInt(topics.size() - low - 1);
            boolean hard = index % 2 == 0;
            boolean forward = hard || index % 4 == 1;
            edges.add(new PlanRequest.PrerequisiteEdge(
                    topics.get(forward ? low : high).id(),
                    topics.get(forward ? high : low).id(),
                    hard ? EdgeStrength.HARD : EdgeStrength.SOFT,
                    PROVENANCES[index % PROVENANCES.length]));
        }
        return edges;
    }

    private static List<PlanRequest.AvailabilitySlot> availability(int days, boolean spread) {
        List<PlanRequest.AvailabilitySlot> slots = new ArrayList<>(days);
        for (int day = 0; day < days; day++) {
            Instant from = START.plusDays((long) day * (spread ? 2 : 1))
                    .atTime(LocalTime.of(8, 0)).toInstant(ZoneOffset.UTC);
            slots.add(new PlanRequest.AvailabilitySlot(from,
                    from.plusSeconds(WINDOW_MINUTES * 60L)));
        }
        return slots;
    }

    /** Fixed, readable identifiers: nothing here may come from {@code UUID.randomUUID()}. */
    private static UUID topicId(int index) {
        return new UUID(0xb000000000004000L, 0x8000000000000000L | index);
    }

    private static UUID subjectId(int subject) {
        return new UUID(0x5000000000004000L, 0x8000000000000000L | subject);
    }
}
