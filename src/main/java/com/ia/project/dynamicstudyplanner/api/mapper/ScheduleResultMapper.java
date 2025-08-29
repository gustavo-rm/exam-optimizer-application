package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.ScheduleResultDto;
import com.ia.project.dynamicstudyplanner.api.dto.StudyBlockDto;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps between the {@link ScheduleResult} domain object and its corresponding {@link ScheduleResultDto}.
 */
@Component
public class ScheduleResultMapper {

    private final StudyBlockMapper studyBlockMapper;

    public ScheduleResultMapper(StudyBlockMapper studyBlockMapper) {
        this.studyBlockMapper = studyBlockMapper;
    }

    /**
     * Maps a ScheduleResult domain object to its DTO representation.
     *
     * @param result The domain entity containing the full tactical schedule and its analysis.
     * @return The resulting {@code ScheduleResultDto}.
     */
    public ScheduleResultDto toDto(ScheduleResult result) {
        if (result == null) {
            return null;
        }

        // Convert the schedule map's value from List<StudyBlock> to List<StudyBlockDto>
        Map<LocalDate, List<StudyBlockDto>> scheduleDto = result.schedule().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(studyBlockMapper::toDto).toList()
                ));

        return new ScheduleResultDto(
                scheduleDto,
                result.status(),
                result.requiredHours(),
                result.availableHours()
        );
    }
}
