package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.PlannerResponseDto;
import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;
import org.springframework.stereotype.Component;

/**
 * The top-level mapper that assembles the final API response {@link PlannerResponseDto}
 * from the complete domain result {@link FullPlannerResult}.
 * <p>
 * This class acts as an orchestrator, delegating the mapping of the strategic and tactical
 * parts of the result to their specialized mappers.
 */
@Component
public class FullPlannerResultMapper {

    private final OptimizationResultMapper optimizationResultMapper;
    private final ScheduleResultMapper scheduleResultMapper;

    public FullPlannerResultMapper(OptimizationResultMapper optimizationResultMapper,
            ScheduleResultMapper scheduleResultMapper) {
        this.optimizationResultMapper = optimizationResultMapper;
        this.scheduleResultMapper = scheduleResultMapper;
    }

    /**
     * Maps a FullPlannerResult domain object to a full, top-level PlannerResponseDto.
     *
     * @param result The complete domain result containing both the GA optimization and the daily schedule.
     * @return The resulting {@code PlannerResponseDto} ready to be sent to the client.
     */
    public PlannerResponseDto toResponse(FullPlannerResult result) {
        if (result == null) {
            return null;
        }

        return new PlannerResponseDto(
                "Full study plan generated successfully.",
                optimizationResultMapper.toDto(result.optimizationResult()),
                scheduleResultMapper.toDto(result.scheduleResult())
        );
    }
}
