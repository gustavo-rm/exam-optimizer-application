package com.ia.project.dynamicstudyplanner.coreapi.contract;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What this optimiser produces, in the shape the platform reads.
 *
 * <p><strong>Mirror of the platform's record, not an adaptation of it.</strong> The authoritative
 * declaration lives in {@code sinapse-platform}, at
 * {@code br.com.sinapse.platform.coreclient.contract.PlanResponse}, and is transcribed component by
 * component in {@code docs/CORE_CONTRACT_SURVEY.md} §2.2.
 *
 * <p>The platform validates what comes back, and the checks it applies are listed in
 * {@code docs/CORE_CONTRACT_SURVEY.md} §3. Four of them constrain what this side may emit: the
 * schedule may not be empty, {@code metadata.coreVersion} may not be null or blank,
 * {@code metadata.randomSeed} must echo the seed that was sent, every session needs a topic, a kind
 * and a start, every duration must be positive, and no {@code sequenceIndex} may repeat. Failing
 * any of them raises {@code CoreProtocolException} on the platform, not a retry. Enforcing them is
 * the adapter's job — this record holds shape only.
 *
 * <p><b>A inclusão de nulos é anotada por tipo</b>, e não herdada da configuração global. O motivo
 * está em {@link PlanRequest}: esta aplicação não fixa {@code default-property-inclusion}, a
 * plataforma fixa, e sem a anotação os dois lados escreveriam documentos diferentes.
 *
 * @param contractVersion version of the contract this answer was written against
 * @param sessions        the schedule, in the order it was sequenced
 * @param fitness         the metrics the plan was judged by, as reported. Opaque to the platform
 * @param metadata        what ran, with what seed, for how long
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PlanResponse(
        String contractVersion,
        List<ScheduledSession> sessions,
        Map<String, Object> fitness,
        ExecutionMetadata metadata) {

    /** Defensive copies. */
    public PlanResponse {
        sessions = sessions == null ? List.of() : List.copyOf(sessions);
        fitness = fitness == null ? Map.of() : Map.copyOf(fitness);
    }

    /**
     * One session placed on the calendar.
     *
     * @param topicId         topic to be studied
     * @param kind            new ground or going back over it
     * @param scheduledStart  when it starts
     * @param durationMinutes how long it allows
     * @param sequenceIndex   its position in the plan, unique within the plan
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScheduledSession(
            UUID topicId,
            SessionKind kind,
            Instant scheduledStart,
            int durationMinutes,
            int sequenceIndex) {
    }

    /**
     * What the run was.
     *
     * <p>The platform reads {@code generations} and {@code elapsedMillis} off the wire and drops
     * them: neither is validated nor persisted there. They are part of the shape all the same, and
     * removing them would be a contract change.
     *
     * @param coreVersion   version of the optimiser that produced the plan
     * @param randomSeed    seed it ran with, echoed back so that the record can be checked against
     *                      what was sent rather than assumed
     * @param generations   how many generations the algorithm ran
     * @param elapsedMillis how long it took
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExecutionMetadata(
            String coreVersion,
            long randomSeed,
            int generations,
            long elapsedMillis) {
    }
}
