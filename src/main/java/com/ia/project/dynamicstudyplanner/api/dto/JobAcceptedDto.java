package com.ia.project.dynamicstudyplanner.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta ao envio de um pedido de otimização — devolvida em <b>202 Accepted</b>.
 *
 * <p>O {@code statusUrl} vem no corpo além do cabeçalho {@code Location} porque um cliente que já
 * desserializa JSON não deveria precisar ler cabeçalhos para saber o passo seguinte.
 */
@Schema(description = "Confirmation that the optimization request was accepted for processing.")
public record JobAcceptedDto(

        @Schema(description = "Identifier of the accepted job.",
                example = "3f2b1c44-9a7e-4c1f-8f0d-2a5b6c7d8e9f")
        String id,

        @Schema(description = "Current status of the job.", example = "PENDING")
        String status,

        @Schema(description = "Where to poll for the result.",
                example = "/api/v1/optimizer/jobs/3f2b1c44-9a7e-4c1f-8f0d-2a5b6c7d8e9f")
        String statusUrl
) {}
