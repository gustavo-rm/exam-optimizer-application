package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResultDto;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Maps the strategic {@link OptimizationResult} domain object to its corresponding
 * {@link OptimizationResultDto} for the API layer.
 */
@Component
public class OptimizationResultMapper {

    private final StudyPlanMapper studyPlanMapper;

    /**
     * The build version, reported as {@code coreVersion}.
     *
     * <p>Comes from {@code application.properties}, where Maven's resource filtering substitutes
     * {@code project.version}. There is no default: a deployment that cannot say which build
     * answered fails to start, rather than answering with a version string somebody typed.
     */
    private final String coreVersion;

    public OptimizationResultMapper(StudyPlanMapper studyPlanMapper,
            @Value("${app.core.version}") String coreVersion) {
        this.studyPlanMapper = studyPlanMapper;
        this.coreVersion = coreVersion;
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
                result.executionTimeMillis(),
                coreVersion
        );
    }

}
