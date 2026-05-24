package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.StudyPlanDto;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps between the {@link StudyPlan} domain object and its corresponding
 * {@link StudyPlanDto}.
 */
@Component
public class StudyPlanMapper implements Mapper<StudyPlanDto, StudyPlan> {

    /**
     * Maps a StudyPlan domain object to its DTO representation.
     * <p>
     * This conversion is crucial for a JSON-friendly API, as it transforms the
     * {@code Map<Subject, Integer>} into a {@code Map<String, Integer>}, using the subject's name
     * as the key.
     *
     * @param plan The domain entity to be mapped.
     * @return The resulting {@code StudyPlanDto}.
     */
    @Override
    public StudyPlanDto toDto(StudyPlan plan) {
        if (plan == null) {
            return null;
        }
        Map<String, Integer> daysPerSubjectName = plan.getDaysPerSubject().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().name(),
                        Map.Entry::getValue,
                        (oldValue, newValue) -> newValue
                ));
        return new StudyPlanDto(daysPerSubjectName);
    }

    /**
     * Maps a StudyPlanDto to its domain entity representation.
     * <p>
     * Note: A direct mapping from DTO to a full StudyPlan domain object is not
     * typically required, as the StudyPlan is a result of the optimization process,
     * not an input. This method is included for completeness of the Mapper interface.
     *
     * @param dto The DTO to be mapped.
     * @return The resulting {@code StudyPlan}.
     * @throws UnsupportedOperationException as this direction is not implemented.
     */
    @Override
    public StudyPlan toDomain(StudyPlanDto dto) {
        // This direction is not needed because a StudyPlan is an output of the system, not an input.
        // If it were ever needed, it would require a list of all Subject objects to map back from names.
        throw new UnsupportedOperationException("Mapping from StudyPlanDto to StudyPlan is not supported.");
    }
}
