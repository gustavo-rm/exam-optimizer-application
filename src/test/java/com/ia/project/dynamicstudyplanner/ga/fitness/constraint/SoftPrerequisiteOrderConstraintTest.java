package com.ia.project.dynamicstudyplanner.ga.fitness.constraint;

import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessWeights;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The formula declared on {@code SoftPrerequisiteOrderConstraint}, asserted term by term.
 *
 * <p>The Javadoc there states a formula in four cases and a normalisation. A Javadoc formula that
 * nothing executes is a comment; each case below is one of them, and the last two tests are the
 * properties the formula exists to have — {@code [0,1]} whatever the syllabus size, and silence on
 * a plan that has no calendar.
 */
@DisplayName("Soft prerequisite order: the severity formula")
class SoftPrerequisiteOrderConstraintTest {

    private static final Offset<Double> TOLERANCE = Offset.offset(1e-9);

    private static final PlanningItem A = new PlanningItem("A", "A", 3);
    private static final PlanningItem B = new PlanningItem("B", "B", 3);
    private static final PlanningItem C = new PlanningItem("C", "C", 3);

    private static final LocalDateTime DAY_ONE = LocalDateTime.of(2026, 9, 1, 8, 0);

    private final SoftPrerequisiteOrderConstraint constraint = new SoftPrerequisiteOrderConstraint();

    @Test
    @DisplayName("no preferences: nothing to violate, severity zero")
    void noPreferencesIsZero() {
        TacticalStudyPlan plan = plan(Map.of(A, 0L, B, 10L));

        assertThat(constraint.violationSeverity(plan, context(Map.of()))).isZero();
        assertThat(constraint.isValid(plan, context(Map.of()))).isTrue();
    }

    @Test
    @DisplayName("a preference that is honoured costs nothing")
    void anHonouredPreferenceIsZero() {
        // A at minute 0, B at minute 10, preference "A before B".
        TacticalStudyPlan plan = plan(Map.of(A, 0L, B, 10L));

        assertThat(constraint.violationSeverity(plan, context(prefer(B, A)))).isZero();
    }

    @Test
    @DisplayName("gravity is the inversion's distance as a fraction of the plan's span")
    void gravityIsTheDistanceOverTheSpan() {
        // Span is 0..100. B is at 40 and its preferred prerequisite A is at 100, so the inversion
        // is 60 minutes over a span of 100: 0.6. One preference, so severity is that same 0.6.
        TacticalStudyPlan plan = plan(Map.of(C, 0L, B, 40L, A, 100L));

        assertThat(constraint.violationSeverity(plan, context(prefer(B, A))))
                .isCloseTo(0.6, TOLERANCE);
    }

    @Test
    @DisplayName("a scheduled dependent whose prerequisite is absent costs the maximum")
    void anAbsentPrerequisiteIsTheWorstCase() {
        // B is in the plan, A never is: the student was handed B with A nowhere in the plan, which
        // is the worst outcome this preference can describe.
        TacticalStudyPlan plan = plan(Map.of(B, 0L, C, 50L));

        assertThat(constraint.violationSeverity(plan, context(prefer(B, A))))
                .isCloseTo(1.0, TOLERANCE);
    }

    @Test
    @DisplayName("an unscheduled dependent costs nothing: no order was imposed on it")
    void anUnscheduledDependentIsZero() {
        TacticalStudyPlan plan = plan(Map.of(A, 0L, C, 50L));

        assertThat(constraint.violationSeverity(plan, context(prefer(B, A))))
                .as("B is not in the plan, so nothing about B's order can have gone wrong")
                .isZero();
    }

    @Test
    @DisplayName("severity is the mean over the preferences, so it stays in [0,1]")
    void severityIsTheMeanOverPreferences() {
        // Two preferences, one fully violated (absent prerequisite) and one honoured: 1.0 and 0.0
        // over two preferences is 0.5. Without the denominator this would be 1.0, and a syllabus
        // would score worse for carrying more edges.
        TacticalStudyPlan plan = plan(Map.of(B, 0L, C, 50L));
        Map<PlanningItem, Set<PlanningItem>> preferences = new LinkedHashMap<>();
        preferences.put(B, new LinkedHashSet<>(Set.of(A)));
        preferences.put(C, new LinkedHashSet<>(Set.of(B)));

        assertThat(constraint.violationSeverity(plan, context(preferences)))
                .isCloseTo(0.5, TOLERANCE);
    }

    @Test
    @DisplayName("a plan with no calendar is silent: this term does not steer the search")
    void aMacroPlanIsSilent() {
        // The macro chromosome is a count of sessions per item with no order, so it cannot express
        // the violation and must not be charged for one. This is the v1 boundary, not an oversight:
        // precedence is imposed after the search and priced afterwards.
        StudyPlan macro = new StudyPlan(Map.of(A, 5, B, 5));

        assertThat(constraint.violationSeverity(macro, context(prefer(B, A)))).isZero();
    }

    @Test
    @DisplayName("a preference is subtracted at its own weight, below any objective")
    void isSubtractedAtThePreferenceWeight() {
        assertThat(constraint.getPenaltyWeight())
                .as("a violated preference must never cost as much as a violated requirement")
                .isEqualTo(FitnessWeights.SOFT_PREREQUISITE_ORDER)
                .isLessThan(FitnessWeights.CONSTRAINT_VIOLATION)
                .isLessThan(FitnessWeights.COGNITIVE_LOAD);
    }

    /** "{@code prerequisite} would ideally come before {@code dependent}". */
    private static Map<PlanningItem, Set<PlanningItem>> prefer(PlanningItem dependent,
            PlanningItem prerequisite) {

        Map<PlanningItem, Set<PlanningItem>> preferences = new LinkedHashMap<>();
        preferences.put(dependent, new LinkedHashSet<>(Set.of(prerequisite)));
        return preferences;
    }

    /** One 10-minute block per item, at the given minute offset from day one. */
    private static TacticalStudyPlan plan(Map<PlanningItem, Long> startMinutes) {
        Map<TimeSlot, TacticalStudyBlock> schedule = new LinkedHashMap<>();
        startMinutes.forEach((item, minute) -> {
            LocalDateTime start = DAY_ONE.plusMinutes(minute);
            schedule.put(new TimeSlot(start, start.plusMinutes(10)),
                    new TacticalStudyBlock(item, StudyMethodology.ACTIVE_RECALL, 10));
        });
        return new TacticalStudyPlan(schedule);
    }

    private static EvolutionContext context(Map<PlanningItem, Set<PlanningItem>> preferences) {
        return EvolutionContext.builder()
                .importanceScores(Map.of(A, 1.0, B, 1.0, C, 1.0))
                .minimumDaysPerItem(Map.of(A, 1, B, 1, C, 1))
                .softPrerequisitesPerItem(preferences)
                .planningHorizonDays(30)
                .hoursPerStudyDay(4)
                .maxDailyCognitiveLoad(20)
                .build();
    }
}
