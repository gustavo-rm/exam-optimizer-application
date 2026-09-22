package com.ia.project.dynamicstudyplanner.baseline;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.plan.PlanEngine;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Registers the greedy baseline as an engine of {@code POST /plans}.
 *
 * <h2>Why this is a separate class from the scheduler</h2>
 *
 * {@link GreedyBaselineScheduler} predates the engine abstraction and is tested directly, on its own
 * method, by {@code GreedyBaselineSchedulerTest}. Making it implement {@link PlanEngine} would have
 * been fewer files and would have put the protocol's interface on the class the baseline's own unit
 * tests construct by hand, coupling those tests to a seam they have no interest in. This adapter is
 * two methods and keeps the scheduler answerable on its own terms.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class GreedyBaselineEngine implements PlanEngine {

    /** The id of the first condition of the experiment. Matches {@code BaselineFitness.STRATEGY}. */
    public static final String ID = BaselineFitness.STRATEGY;

    private final GreedyBaselineScheduler scheduler;

    public GreedyBaselineEngine(GreedyBaselineScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PlanResponse plan(PlanRequest request) {
        return scheduler.schedule(request);
    }
}
