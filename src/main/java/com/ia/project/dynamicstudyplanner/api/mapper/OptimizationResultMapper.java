package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResponse; // This can be removed now
import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResultDto;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import org.springframework.stereotype.Component;

/**
 * Maps the strategic {@link OptimizationResult} domain object to its corresponding
 * {@link OptimizationResultDto} for the API layer.
 */
@Component
public class OptimizationResultMapper {

    private final StudyPlanMapper studyPlanMapper;

    public OptimizationResultMapper(StudyPlanMapper studyPlanMapper) {
        this.studyPlanMapper = studyPlanMapper;
    }

    /**
     * Maps an OptimizationResult domain object to its dedicated DTO representation.
     * This is used by higher-level mappers to build a complete API response.
     *
     * @param result The domain entity containing the results of the GA execution.
     * @return The resulting {@code OptimizationResultDto} containing the strategic plan details.
     */
    public OptimizationResultDto toDto(OptimizationResult result) {
        if (result == null) {
            return null;
        }
        return new OptimizationResultDto(
                studyPlanMapper.toDto(result.plan()),
                result.fitness(),
                result.generationsRun(),
                result.executionTimeMillis()
        );
    }

    /**
     * DEPRECATED: This method is no longer ideal as it assumes a specific top-level response structure.
     * It's better to use the more focused toDto method and let a higher-level mapper build the final response.
     * Kept here for reference but can be removed.
     *
     * @param result The domain entity containing the results of the GA execution.
     * @return The resulting {@code OptimizationResponse} ready to be sent to the client.
     */
    @Deprecated
    public OptimizationResponse toResponse(OptimizationResult result) {
        return new OptimizationResponse("Plan generated successfully", toDto(result));
    }
}