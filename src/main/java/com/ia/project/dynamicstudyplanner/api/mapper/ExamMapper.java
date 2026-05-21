package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.ExamDto;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import org.springframework.stereotype.Component;

/**
 * Maps between the {@link Exam} domain object and its corresponding {@link ExamDto}.
 * This mapper orchestrates the mapping of the entire exam structure, delegating
 * the conversion of nested objects (Subjects and ThematicAxes) to their respective mappers.
 */
@Component
public class ExamMapper implements Mapper<ExamDto, Exam> {

    private final SubjectMapper subjectMapper;
    private final ThematicAxisMapper thematicAxisMapper;

    public ExamMapper(SubjectMapper subjectMapper, ThematicAxisMapper thematicAxisMapper) {
        this.subjectMapper = subjectMapper;
        this.thematicAxisMapper = thematicAxisMapper;
    }

    /**
     * Maps an Exam domain object to its DTO representation.
     *
     * @param exam The domain entity to be mapped.
     * @return The resulting {@code ExamDto}.
     */
    @Override
    public ExamDto toDto(Exam exam) {
        if (exam == null) {
            return null;
        }
        return new ExamDto(
                exam.getName(),
                exam.getExamDate(),
                exam.getGeneralKnowledgeTotalScore(),
                exam.getGeneralKnowledgeSubjects().stream().map(subjectMapper::toDto).toList(),
                exam.getSpecificKnowledgeAxes().stream().map(thematicAxisMapper::toDto).toList()
        );
    }

    /**
     * Maps an ExamDto to its domain entity representation.
     *
     * @param dto The DTO to be mapped.
     * @return The resulting {@code Exam} domain object.
     */
    @Override
    public Exam toDomain(ExamDto dto) {
        if (dto == null) {
            return null;
        }
        return new Exam(
                dto.name(),
                dto.examDate(),
                dto.generalKnowledgeTotalScore(),
                dto.generalKnowledgeSubjects().stream().map(subjectMapper::toDomain).toList(),
                dto.specificKnowledgeAxes().stream().map(thematicAxisMapper::toDomain).toList()
        );
    }
}
