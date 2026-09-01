package com.ia.project.dynamicstudyplanner.ga.fitness.objective;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

/**
 * The learning-curve parameters shared by the fitness objectives.
 * <p>
 * Both the mastery term and the retention term are built on the same time constant, and they must
 * stay consistent: a subject that takes longer to learn also holds a longer stable memory trace, so
 * the same {@code tau} governs how fast mastery saturates and how long a memory survives before it
 * needs refreshing. Keeping the parameter in one place is what makes that consistency structural
 * instead of a coincidence.
 * <p>
 * Reference documentation, including the citations behind each choice:
 * {@code docs/revisao-ag/05-fitness-function.md}.
 */
public final class LearningModel {

    /**
     * Study days for an average-difficulty subject to reach ~63% mastery, i.e. one time constant.
     * Twice that, about 20 days, reaches ~86%. Chosen to sit on the same scale as the 15-day
     * ceiling {@code BaselineCalculator} uses for its hardest-subject floor, so the coverage
     * constraint and the objectives describe study effort in comparable units.
     */
    private static final double TAU_AT_AVERAGE_LOAD = 10.0;

    /**
     * The cognitive load treated as average. Matches {@code CognitiveLoadCalculator}'s
     * {@code AVERAGE_LOAD_FACTOR} so "average difficulty" means the same thing across the system.
     */
    private static final double AVERAGE_COGNITIVE_LOAD = 3.0;

    private LearningModel() {
    }

    /**
     * Time constant for a subject, in study days.
     * <p>
     * Grows linearly with {@code Subject.cognitiveLoad}: an easy subject (load 1) has tau ~3.3 days,
     * an average one 10, the hardest (load 5) ~16.7. Serves two roles:
     * <ul>
     *   <li><b>Mastery:</b> {@code 1 - exp(-d/tau)} — days to learn the material.</li>
     *   <li><b>Retention:</b> the stability {@code S} in Ebbinghaus's {@code R = exp(-t/S)} — days
     *       a memory survives before recall drops to {@code e^-1}.</li>
     * </ul>
     */
    public static double timeConstantDays(Subject subject) {
        int load = Math.max(1, subject.cognitiveLoad());
        return TAU_AT_AVERAGE_LOAD * load / AVERAGE_COGNITIVE_LOAD;
    }

    /**
     * Fraction of the subject mastered after {@code days} of study, in [0,1).
     * Exponential approach to an asymptote: fast early gains, saturating as the subject is learned.
     */
    public static double mastery(Subject subject, int days) {
        if (days <= 0) {
            return 0.0;
        }
        return 1.0 - Math.exp(-days / timeConstantDays(subject));
    }

    /**
     * Study sessions a subject needs, spread across the horizon, to keep recall from falling below
     * the forgetting threshold before the exam.
     * <p>
     * Derived from Ebbinghaus directly: recall drops to {@code e^-1} after one stability interval,
     * so sessions must not be spaced further apart than {@code tau}. Over a horizon of {@code H}
     * days that means {@code H / tau} sessions. This is the same instant at which
     * {@code HybridRetentionEngine} now declares a review mandatory, so the macro objective and the
     * tactical engine use one definition of "overdue".
     *
     * @param subject           the subject
     * @param planningHorizonDays calendar days from the plan start to the exam
     * @return sessions required, never below 1
     */
    public static double requiredSessions(Subject subject, int planningHorizonDays) {
        double horizon = Math.max(1.0, planningHorizonDays);
        return Math.max(1.0, horizon / timeConstantDays(subject));
    }
}
