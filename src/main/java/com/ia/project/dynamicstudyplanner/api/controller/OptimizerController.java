package com.ia.project.dynamicstudyplanner.api.controller;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationRequest;
import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResponse;
import com.ia.project.dynamicstudyplanner.api.mapper.ExamMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.OptimizationResultMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.StudentProfileMapper;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.service.StudyOptimizerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/optimizer")
public class OptimizerController {

    private final StudyOptimizerService optimizerService;
    private final ExamMapper examMapper;
    private final StudentProfileMapper studentProfileMapper;
    private final OptimizationResultMapper optimizationResultMapper;

    public OptimizerController(StudyOptimizerService optimizerService, ExamMapper examMapper,
                               StudentProfileMapper studentProfileMapper, OptimizationResultMapper optimizationResultMapper) {
        this.optimizerService = optimizerService;
        this.examMapper = examMapper;
        this.studentProfileMapper = studentProfileMapper;
        this.optimizationResultMapper = optimizationResultMapper;
    }

    @PostMapping("/generate-plan")
    public ResponseEntity<OptimizationResponse> generateStudyPlan(@Valid @RequestBody OptimizationRequest request) {
        // 1. Mapear DTOs para o Domínio usando mappers especializados
        Exam exam = examMapper.toDomain(request.exam());
        StudentProfile profile = studentProfileMapper.toDomain(request.studentProfile(), exam.getAllSubjects());

        // 2. Chamar o serviço
        OptimizationResult result = optimizerService.optimize(
                exam,
                profile,
                request.gaConfig().totalStudyDays(),
                request.gaConfig().numGenerations(),
                request.gaConfig().populationSize()
        );

        // 3. Mapear o resultado do Domínio de volta para um DTO de resposta
        return ResponseEntity.ok(optimizationResultMapper.toResponse(result));
    }
}
