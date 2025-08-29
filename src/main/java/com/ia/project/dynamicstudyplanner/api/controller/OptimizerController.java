package com.ia.project.dynamicstudyplanner.api.controller;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationRequest;
import com.ia.project.dynamicstudyplanner.api.dto.PlannerResponseDto;
import com.ia.project.dynamicstudyplanner.api.mapper.ExamMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.FullPlannerResultMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.StudentProfileMapper;
import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.service.DynamicStudyPlannerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimizer")
public class OptimizerController {

    private final DynamicStudyPlannerService plannerService;
    private final ExamMapper examMapper;
    private final StudentProfileMapper studentProfileMapper;
    private final FullPlannerResultMapper resultMapper;

    public OptimizerController(DynamicStudyPlannerService plannerService, ExamMapper examMapper,
                               StudentProfileMapper studentProfileMapper, FullPlannerResultMapper resultMapper) {
        this.plannerService = plannerService;
        this.examMapper = examMapper;
        this.studentProfileMapper = studentProfileMapper;
        this.resultMapper = resultMapper;
    }

    /**
     * The primary endpoint to generate a complete, personalized, and optimized study plan.
     * It orchestrates both the strategic (GA) and tactical (daily schedule) planning phases.
     *
     * @param request The request body containing the exam, student profile, and GA config.
     * @return A ResponseEntity containing the full PlannerResponseDto with the generated plan.
     */
    @PostMapping("/generate")
    public ResponseEntity<PlannerResponseDto> generateFullStudyPlan(@Valid @RequestBody OptimizationRequest request) {
        // 1. Map from API DTOs to Domain Objects
        Exam exam = examMapper.toDomain(request.exam());
        StudentProfile profile = studentProfileMapper.toDomain(request.studentProfile(), exam.getAllSubjects());

        // 2. Call the high-level orchestrator service
        FullPlannerResult result = plannerService.generateFullStudyPlan(
                exam,
                profile,
                request.gaConfig().totalStudyDays(),
                request.gaConfig().numGenerations(),
                request.gaConfig().populationSize()
        );

        // 3. Map the full domain result back to a response DTO and return
        return ResponseEntity.ok(resultMapper.toResponse(result));
    }
}
