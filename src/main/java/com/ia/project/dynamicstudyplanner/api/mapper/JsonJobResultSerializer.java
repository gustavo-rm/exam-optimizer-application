package com.ia.project.dynamicstudyplanner.api.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobResultSerializer;
import org.springframework.stereotype.Component;

/**
 * Implementação do porto {@code JobResultSerializer}, do lado da API.
 *
 * <p>Mora aqui porque é aqui que vivem os mapeadores e o formato de resposta. O módulo de serviço
 * depende só da interface, então a dependência atravessa a fronteira em um sentido só — ver a nota
 * em {@link JobResultSerializer}.
 */
@Component
public class JsonJobResultSerializer implements JobResultSerializer {

    private final FullPlannerResultMapper resultMapper;
    private final ObjectMapper objectMapper;

    public JsonJobResultSerializer(FullPlannerResultMapper resultMapper, ObjectMapper objectMapper) {
        this.resultMapper = resultMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public String serializar(FullPlannerResult resultado) {
        try {
            return objectMapper.writeValueAsString(resultMapper.toResponse(resultado));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o resultado do trabalho", e);
        }
    }
}
