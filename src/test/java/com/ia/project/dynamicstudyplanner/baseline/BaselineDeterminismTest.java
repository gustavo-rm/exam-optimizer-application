package com.ia.project.dynamicstudyplanner.baseline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same request produces the same plan, byte for byte, on any thread.
 *
 * <h2>Why the application's own mapper</h2>
 *
 * "The same plan" is a claim about what goes on the wire, and the wire shape is decided as much by the
 * {@code ObjectMapper} as by the records. A {@code new ObjectMapper()} here would compare two
 * serialisations that production never produces. The scheduler is taken from the context for the same
 * reason: the {@code coreVersion} it reports comes from the profile's properties, so this also proves
 * the wiring resolves.
 *
 * <p>Byte equality is a stricter claim than equal objects: it would catch a difference in the order of
 * two sessions that happened to have equal fields, and it is the property the platform's stored
 * snapshot depends on. The key order of the opaque {@code fitness} map comes from
 * {@code Map.copyOf}, which is stable within a run, so it does not weaken the comparison here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(BaselineCore.PROFILE)
@DisplayName("Determinism of the greedy baseline")
class BaselineDeterminismTest {

    /** Threads run at once. More than two, because two can agree by luck on a race. */
    private static final int THREADS = 4;

    @Autowired
    private GreedyBaselineScheduler scheduler;

    @Autowired
    private ObjectMapper objectMapper;

    /** A request that exercises revision sessions and a truncated plan, not just the simple path. */
    private static PlanRequest richRequest() {
        return BaselineRequests.builder()
                .withHistory(List.of(
                        BaselineRequests.studied(BaselineRequests.TOPIC_1, "2026-07-01T10:00:00Z",
                                RecallRating.GOOD, RecallRating.AGAIN),
                        BaselineRequests.studied(BaselineRequests.TOPIC_3, "2026-06-15T10:00:00Z",
                                RecallRating.HARD)))
                .build();
    }

    @Test
    @DisplayName("four threads scheduling one request produce one identical plan")
    void fourThreadsProduceOneIdenticalPlan() throws Exception {
        PlanRequest request = richRequest();
        Callable<String> run = () -> objectMapper.writeValueAsString(scheduler.schedule(request));

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<String> plans = new ArrayList<>();
        try {
            for (Future<String> answer : pool.invokeAll(List.of(run, run, run, run))) {
                plans.add(answer.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(plans).hasSize(THREADS);
        assertThat(plans)
                .as("every thread serialised the same document, byte for byte")
                .containsOnly(plans.get(0));
        assertThat(plans.get(0))
                .as("and so does a fifth run on the calling thread")
                .isEqualTo(objectMapper.writeValueAsString(scheduler.schedule(request)));
    }

    @Test
    @DisplayName("a second scheduler instance agrees, so nothing is carried between requests")
    void aSecondInstanceAgrees() throws Exception {
        PlanRequest request = richRequest();

        String fromContext = objectMapper.writeValueAsString(scheduler.schedule(request));
        String fromFresh = objectMapper.writeValueAsString(
                new GreedyBaselineScheduler("2.0.1").schedule(request));

        assertThat(fromFresh).isEqualTo(fromContext);
    }

    @Test
    @DisplayName("the plan does not depend on the order the request's collections arrived in")
    void orderOfTheRequestCollectionsDoesNotMatter() throws Exception {
        PlanRequest request = richRequest();
        PlanRequest shuffled = new PlanRequest(request.contractVersion(), request.horizon(),
                request.availability().reversed(), request.goals().reversed(),
                request.topics().reversed(), request.prerequisites().reversed(),
                request.history().reversed(), request.algorithmParams(), request.randomSeed());

        assertThat(objectMapper.writeValueAsString(scheduler.schedule(shuffled)))
                .as("topics, goals, edges, history and windows are all sorted explicitly")
                .isEqualTo(objectMapper.writeValueAsString(scheduler.schedule(request)));
    }
}
