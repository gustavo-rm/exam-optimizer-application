package com.ia.project.dynamicstudyplanner.service.scheduler.tactical;

import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.List;
import java.util.Map;

/**
 * Interface for generating the tactical micro-plan.
 * It takes the macro-plan (days/hours per subject) from the GA and packs it into actual real-world availability windows.
 */
public interface TacticalScheduler {
    /**
     * @param macroPlan The output from the GA (e.g., Subject -> Total Hours).
     * @param windows The student's specific availability windows for the week.
     * @param emergencyMode If true, overrides standard spaced-repetition logic.
     * @return A detailed tactical schedule.
     */
    TacticalStudyPlan schedule(Map<Subject, Integer> macroPlan, List<AvailabilityWindow> windows, boolean emergencyMode);
}
