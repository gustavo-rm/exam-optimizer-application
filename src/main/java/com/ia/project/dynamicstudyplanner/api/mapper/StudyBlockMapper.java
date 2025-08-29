package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.StudyBlockDto;
import com.ia.project.dynamicstudyplanner.domain.StudyBlock;
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link StudyBlock} domain object and its corresponding {@link StudyBlockDto}.
 */
@Component
public class StudyBlockMapper {

    /**
     * Maps a StudyBlock domain object to its DTO representation.
     * This converts the complex Subject object into a simple subject name string
     * for a clean JSON output.
     *
     * @param block The domain entity to be mapped.
     * @return The resulting {@code StudyBlockDto}.
     */
    public StudyBlockDto toDto(StudyBlock block) {
        if (block == null) {
            return null;
        }
        return new StudyBlockDto(block.subject().name(), block.hours());
    }
}
