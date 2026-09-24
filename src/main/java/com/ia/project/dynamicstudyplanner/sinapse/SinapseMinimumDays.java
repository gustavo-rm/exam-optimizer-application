package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The floor of sessions each topic needs, derived from the payload and nothing else.
 *
 * <h2>The derivation</h2>
 *
 * <pre>
 *   minutesPerStudyDay = total available minutes / number of distinct days that have availability
 *   floor(topic)       = max(1, ceil(estimatedMinutes / minutesPerStudyDay))
 * </pre>
 *
 * Both inputs are real platform data. {@code estimatedMinutes} is the one quantitative field on a
 * topic the platform has already scaled to this student
 * ({@code docs/CORE_CONTRACT_SURVEY.md} §1.8), and the study day's length is measured from the
 * windows the student declared.
 *
 * <h2>Why no "minutes per study day" constant</h2>
 *
 * Because this path does not need one, and introducing one would be the exact trade this adapter
 * exists to refuse: swapping a self-declared input for a fresh assumption. The denominator is
 * <b>distinct days that actually have availability</b>, not calendar days of the horizon — the
 * question the floor answers is "how many sittings does this topic take?", and a day the student
 * declared nothing is not a sitting. Dividing by horizon days would shorten the apparent study day
 * and inflate every floor for a student who studies three evenings a week.
 *
 * <p>The earlier version of this class was a flat {@code 1} for every topic. That was defensible as
 * a definition — a topic in scope needs at least one session — but it threw away
 * {@code estimatedMinutes}, which is the only calibrated number on a topic. A 180-minute topic and a
 * 30-minute topic do not need the same minimum effort, and the payload says so.
 *
 * <h2>The floor is not comparable across paths, and that is load-bearing</h2>
 *
 * It feeds {@code MinimumDaysConstraint} — subtracted at λ 0.50, the heaviest single weight in the
 * aggregate — and the floor of the initial population. The concurso path derives its floor from
 * gap-scaled importance normalised by its maximum, capped at 15 days. <b>The two are different
 * quantities on different scales, so a fitness value from one path cannot be compared with one from
 * the other.</b> Only plans from the same path are comparable, which is what the experiment
 * compares. Recorded in {@code docs/SINAPSE_ADAPTER.md} §2.5.
 */
public final class SinapseMinimumDays {

    /** Every topic in scope needs at least one sitting, whatever the arithmetic says. */
    public static final int ABSOLUTE_FLOOR = 1;

    private SinapseMinimumDays() {
    }

    /**
     * The floor per planning item.
     *
     * @param topics  the topics as they arrived
     * @param windows the availability, already converted
     * @return item {@literal ->} minimum sessions, in the topics' arrival order
     */
    public static Map<PlanningItem, Integer> of(List<PlanRequest.Topic> topics,
            List<AvailabilityWindow> windows) {

        double minutesPerStudyDay = minutesPerStudyDay(windows);
        Map<PlanningItem, Integer> floor = new LinkedHashMap<>();
        for (PlanRequest.Topic topic : topics) {
            floor.put(TopicPlanningItems.toItem(topic),
                    sessionsFor(topic.estimatedMinutes(), minutesPerStudyDay));
        }
        // unmodifiableMap over LinkedHashMap, never Map.copyOf: see GoalPriorityImportance.
        return Collections.unmodifiableMap(floor);
    }

    /**
     * How long a study day is, measured from the windows.
     *
     * @param windows the availability
     * @return average minutes per day that has any availability; {@code 1.0} when there is none, so
     *         that the division below cannot produce an infinite floor
     */
    public static double minutesPerStudyDay(List<AvailabilityWindow> windows) {
        if (windows.isEmpty()) {
            return 1.0;
        }
        long minutes = windows.stream().mapToLong(AvailabilityWindow::getDurationMinutes).sum();
        TreeSet<LocalDate> days = new TreeSet<>();
        windows.forEach(window -> days.add(window.startTime().toLocalDate()));
        return Math.max(1.0, (double) minutes / Math.max(1, days.size()));
    }

    /** Sittings a topic of this length takes, at this study-day length. */
    public static int sessionsFor(int estimatedMinutes, double minutesPerStudyDay) {
        return Math.max(ABSOLUTE_FLOOR,
                (int) Math.ceil(estimatedMinutes / Math.max(1.0, minutesPerStudyDay)));
    }

    /** The sum of the floors: the smallest session budget the chromosome can satisfy. */
    public static int totalFloor(Map<PlanningItem, Integer> floor) {
        return floor.values().stream().mapToInt(Integer::intValue).sum();
    }
}
