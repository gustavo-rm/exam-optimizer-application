package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.api.exception.ProblemDetails;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /plans}: the Core protocol endpoint the platform already calls.
 *
 * <h2>Only under the {@code baseline-core} profile</h2>
 *
 * Without the profile this bean is not created and the path has no handler, so the application boots
 * exactly as it did before this module existed and {@code /plans} answers {@code 404}. Making the
 * {@code 404} honest is the reason {@link BaselinePlanSecurityConfig} is <b>not</b> gated by the
 * profile; see the comment there.
 *
 * <h2>Hidden from the published OpenAPI document</h2>
 *
 * {@code /plans} is not part of this service's public API — it is the protocol
 * {@code sinapse-platform} speaks to it, versioned by {@code PlanRequest.VERSION} and pinned by the
 * reference documents in {@code src/test/resources/contract/}, not by the OpenAPI snapshot. Publishing
 * it would put a second, weaker description of the same contract in circulation and would change
 * {@code contract/openapi-snapshot.json} the moment anyone ran with the profile on. The contract is
 * documented in {@code docs/CORE_CONTRACT_SURVEY.md} and {@code docs/BASELINE_CORE.md}.
 *
 * <h2>Errors are handled here, not by the application's advices</h2>
 *
 * A handler declared on the controller wins over any {@code @RestControllerAdvice}, which is what this
 * module needs: {@code RequestErrorAdvice} maps {@code IllegalArgumentException} to {@code 400} and
 * {@code BusinessRuleErrorAdvice} only knows {@code DomainException}, so a rejection raised here would
 * otherwise arrive as the wrong status or as a bare {@code 500}. Keeping the two handlers on the
 * controller also keeps this module from adding an advice that would apply to every existing endpoint.
 *
 * <p>The bodies are built by the application's own {@link ProblemDetails}, so a refusal from
 * {@code /plans} has the same RFC 7807 shape as a refusal from {@code /api/v1/**} — one error contract
 * in the service, not two. Only identifiers and array indices are ever named; no self-assessment and
 * no psychological state reaches an error body or a log line, which is this repository's standing rule.
 */
@RestController
@Profile(PlanProtocol.PROFILE)
@Hidden
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);

    /** RFC 7807 extension naming what a refusal tripped over. */
    private static final String OFFENDING_PROPERTY = "offending";

    private final PlanEngineSelector engines;

    public PlanController(PlanEngineSelector engines) {
        this.engines = engines;
    }

    /**
     * Plans one request.
     *
     * @param request the snapshot the platform assembled
     * @return the plan, in the shape {@code docs/CORE_CONTRACT_SURVEY.md} §2.2 describes
     */
    @PostMapping(path = PlanProtocol.PLANS_PATH,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public PlanResponse plan(@RequestBody PlanRequest request) {
        return engines.plan(request);
    }

    /**
     * A request that was understood and cannot be planned. {@code 422}, with the offenders named.
     *
     * <p>{@code WARN}, never {@code ERROR}: refusing an unplannable request is the service working.
     */
    @ExceptionHandler(PlanRejectedException.class)
    public ResponseEntity<ProblemDetail> onRejected(PlanRejectedException exception,
            HttpServletRequest request) {

        log.warn("Plan refused on path {}: {} {}", request.getRequestURI(),
                exception.reason(), exception.offending());

        ProblemDetail problem = ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY,
                exception.reason(), exception.getMessage(), request);
        problem.setProperty(OFFENDING_PROPERTY, exception.offending());
        return ResponseEntity.unprocessableEntity().body(problem);
    }

    /**
     * This scheduler produced a plan that breaks its own invariants. {@code 500}.
     *
     * <p>{@code ERROR}, because unlike every other outcome of this endpoint it means the code is
     * wrong. The detail says which invariant broke; see {@link PlanOutputInvariants}.
     */
    @ExceptionHandler(PlanInvariantViolationException.class)
    public ResponseEntity<ProblemDetail> onInvariantViolation(
            PlanInvariantViolationException exception, HttpServletRequest request) {

        log.error("Plan broke an output invariant on path {}: {}",
                request.getRequestURI(), exception.getMessage());

        return ResponseEntity.internalServerError().body(ProblemDetails.of(
                HttpStatus.INTERNAL_SERVER_ERROR, "plan-invariant-violation",
                "The engine produced a plan that violates its own output invariants and refused "
                        + "to return it.", request));
    }
}
