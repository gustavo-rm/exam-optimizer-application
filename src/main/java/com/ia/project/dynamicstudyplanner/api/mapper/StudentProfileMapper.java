package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.StudentProfileDto;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StudentProfileMapper {

    public StudentProfile toDomain(StudentProfileDto dto, List<Subject> allSubjects) {
        Map<String, Subject> subjectMap = allSubjects.stream()
                .collect(Collectors.toMap(Subject::name, Function.identity()));

        Map<Subject, Double> knowledgeGaps = dto.knowledgeGaps().entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> subjectMap.get(entry.getKey()),
                        Map.Entry::getValue
                ));

        return new StudentProfile(dto.name(), knowledgeGaps, dto.weeklyAvailability());
    }
}
