package com.ia.project.dynamicstudyplanner.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Estado de um trabalho, e o resultado quando ele existe.
 *
 * <p>Campos nulos são omitidos: enquanto o trabalho não termina, não há resultado nem erro a
 * mostrar, e um corpo cheio de {@code null} só dá trabalho a quem lê.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Status of an optimization job, including the result once it is ready.")
public record JobStatusDto(

        @Schema(description = "Job identifier.") String id,

        @Schema(description = "PENDING, RUNNING, SUCCEEDED or FAILED.", example = "SUCCEEDED")
        String status,

        @Schema(description = "When the request was accepted.") Instant submittedAt,

        @Schema(description = "When it left the queue and started running. Absent while PENDING.")
        Instant startedAt,

        @Schema(description = "When it finished. Absent while not finished.") Instant finishedAt,

        @Schema(description = "The generated plan. Present only when SUCCEEDED.")
        PlannerResponseDto result,

        @Schema(description = "Why it failed. Present only when FAILED.") String error
) {}
