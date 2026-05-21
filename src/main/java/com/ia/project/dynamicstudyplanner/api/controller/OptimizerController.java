package com.ia.project.dynamicstudyplanner.api.controller;

import com.ia.project.dynamicstudyplanner.api.dto.OptimizationRequest;
import com.ia.project.dynamicstudyplanner.api.dto.PlannerResponseDto;
import com.ia.project.dynamicstudyplanner.api.mapper.ExamMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.FullPlannerResultMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.StudentProfileMapper;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.service.DynamicStudyPlannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/optimizer")
@Tag(name = "Study Plan Optimizer", description = "Endpoints for generating computationally optimized study plans using AI.")
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
    @Operation(summary = "Generate an optimized study plan", description = "Runs a Genetic Algorithm to find the optimal allocation of study days, then generates a day-by-day tactical schedule.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully generated the optimized study plan.", content = {
                    @Content(mediaType = "application/json", schema = @Schema(implementation = PlannerResponseDto.class))
            }),
            @ApiResponse(responseCode = "400", description = "Bad Request. Validation failed for the input payload.", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            }),
            @ApiResponse(responseCode = "408", description = "Request Timeout. The algorithm took too long to compute.", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            }),
            @ApiResponse(responseCode = "422", description = "Unprocessable Entity. The business logic failed (e.g., impossible constraints).", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            }),
            @ApiResponse(responseCode = "500", description = "Internal Server Error. An unexpected exception occurred.", content = {
                    @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            })
    })
    public CompletableFuture<ResponseEntity<PlannerResponseDto>> generateFullStudyPlan(@Valid @RequestBody OptimizationRequest request) {
        // 1. Map from API DTOs to Domain Objects
        Exam exam = examMapper.toDomain(request.exam());
        StudentProfile profile = studentProfileMapper.toDomain(request.studentProfile(), exam.getAllSubjects());

        // 2. Call the high-level orchestrator service asynchronously
        return plannerService.generateFullStudyPlan(
                exam,
                profile,
                request.gaConfig().totalStudyDays(),
                request.gaConfig().numGenerations(),
                request.gaConfig().populationSize()
        )
        .orTimeout(30, TimeUnit.SECONDS) // Hard limit to prevent stuck threads
        .thenApply(result -> ResponseEntity.ok(resultMapper.toResponse(result))); // 3. Map to DTO
    }
}
