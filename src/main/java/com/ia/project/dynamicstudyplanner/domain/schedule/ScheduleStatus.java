package examoptimizer.domain.schedule;

/**
 * Represents the outcome status of the schedule generation process.
 */
public enum ScheduleStatus {
    /** The schedule was successfully generated with the ideal plan. */
    SUCCESS_IDEAL_PLAN,
    /** The student has more time available than required. The schedule was generated with leisure. */
    SUCCESS_WITH_SURPLUS_TIME,
    /** The student does not have enough time. The plan was proportionally reduced to fit the available time. */
    WARNING_TIME_DEFICIT
}
