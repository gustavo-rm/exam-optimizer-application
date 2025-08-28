package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.SubjectDto;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.springframework.stereotype.Component;

@Component
public class SubjectMapper implements Mapper<SubjectDto, Subject> {

    @Override
    public SubjectDto toDto(Subject subject) {
        return new SubjectDto(subject.name(), subject.questionCount());
    }

    @Override
    public Subject toDomain(SubjectDto dto) {
        return new Subject(dto.name(), dto.questionCount());
    }
}
