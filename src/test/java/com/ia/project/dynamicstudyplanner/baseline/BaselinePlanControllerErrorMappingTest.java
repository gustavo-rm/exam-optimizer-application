package com.ia.project.dynamicstudyplanner.baseline;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How the controller turns this module's two exceptions into HTTP answers.
 *
 * <h2>Why the 500 path is tested here rather than through the endpoint</h2>
 *
 * A {@code PlanInvariantViolationException} means the scheduler has a defect, and the scheduler does
 * not have one — so the branch is unreachable through {@code POST /plans} today. Reaching it over HTTP
 * would mean substituting a deliberately broken scheduler into the context, which would test the
 * substitution. Calling the handler is the smaller, more honest test: what is in question is the
 * mapping, not the route, and the route is covered by {@code BaselinePlanEndpointTest}.
 */
@DisplayName("Baseline plan controller: error mapping")
class BaselinePlanControllerErrorMappingTest {

    private final BaselinePlanController controller =
            new BaselinePlanController(new GreedyBaselineScheduler("2.0.1"));

    private static MockHttpServletRequest postToPlans() {
        return new MockHttpServletRequest("POST", BaselineCore.PLANS_PATH);
    }

    @Test
    @DisplayName("a refusal becomes 422, with the category in the type and the offenders in the body")
    void aRefusalBecomes422() {
        ResponseEntity<ProblemDetail> answer = controller.onRejected(
                new PlanRejectedException("hard-prerequisite-cycle",
                        "The HARD prerequisites contain a cycle.", List.of("first", "second")),
                postToPlans());

        assertThat(answer.getStatusCode().value()).isEqualTo(422);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().getType().toString())
                .isEqualTo("https://api.dynamicstudyplanner.com/errors/hard-prerequisite-cycle");
        assertThat(answer.getBody().getDetail())
                .as("the detail says which rule barred the plan, as it does everywhere else here")
                .isEqualTo("The HARD prerequisites contain a cycle.");
        assertThat(answer.getBody().getProperties())
                .containsEntry("offending", List.of("first", "second"))
                .containsKey("timestamp");
        assertThat(answer.getBody().getInstance())
                .hasToString(BaselineCore.PLANS_PATH);
    }

    @Test
    @DisplayName("an invariant violation becomes 500, and its internals stay in the log")
    void anInvariantViolationBecomes500() {
        ResponseEntity<ProblemDetail> answer = controller.onInvariantViolation(
                new PlanInvariantViolationException("session 3 runs into session 4"),
                postToPlans());

        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getBody()).isNotNull();
        assertThat(answer.getBody().getType().toString())
                .endsWith("/baseline-invariant-violation");
        assertThat(answer.getBody().getDetail())
                .as("which session broke goes to the log, not to the caller: the caller can do "
                        + "nothing about a defect in this scheduler")
                .doesNotContain("session 3")
                .contains("violates its own output invariants");
    }
}
