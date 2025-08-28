package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResponse;
import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResultDto;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import org.springframework.stereotype.Component;

/**
 * Maps the final {@link OptimizationResult} domain object to its corresponding
 * {@link OptimizationResponse} DTO for the API layer.
 */
@Component
public class OptimizationResultMapper {

    private final StudyPlanMapper studyPlanMapper;

    public OptimizationResultMapper(StudyPlanMapper studyPlanMapper) {
        this.studyPlanMapper = studyPlanMapper;
    }

    /**
     * Maps an OptimizationResult domain object to a full, top-level OptimizationResponse DTO.
     *
     * @param result The domain entity containing the results of the GA execution.
     * @return The resulting {@code OptimizationResponse} ready to be sent to the client.
     */
    public OptimizationResponse toResponse(OptimizationResult result) {
        if (result == null) {
            return null;
        }
        OptimizationResultDto resultDto = new OptimizationResultDto(
                studyPlanMapper.toDto(result.plan()),
                result.fitness(),
                result.generationsRun(),
                result.executionTimeMillis()
        );
        return new OptimizationResponse("Plan generated successfully", resultDto);
    }
}
