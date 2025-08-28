package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.ThematicAxisDto;
import com.ia.project.dynamicstudyplanner.domain.ThematicAxis;
import org.springframework.stereotype.Component;

@Component
public class ThematicAxisMapper implements Mapper<ThematicAxisDto, ThematicAxis> {

    private final SubjectMapper subjectMapper;

    public ThematicAxisMapper(SubjectMapper subjectMapper) {
        this.subjectMapper = subjectMapper;
    }

    @Override
    public ThematicAxisDto toDto(ThematicAxis axis) {
        return new ThematicAxisDto(
                axis.id(),
                axis.name(),
                axis.weight(),
                axis.subjects().stream().map(subjectMapper::toDto).toList()
        );
    }

    @Override
    public ThematicAxis toDomain(ThematicAxisDto dto) {
        return new ThematicAxis(
                dto.id(),
                dto.name(),
                dto.weight(),
                dto.subjects().stream().map(subjectMapper::toDomain).toList()
        );
    }
}
