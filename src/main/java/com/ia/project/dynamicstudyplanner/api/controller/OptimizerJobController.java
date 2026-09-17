package com.ia.project.dynamicstudyplanner.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.api.dto.JobAcceptedDto;
import com.ia.project.dynamicstudyplanner.api.dto.JobStatusDto;
import com.ia.project.dynamicstudyplanner.api.dto.OptimizationRequest;
import com.ia.project.dynamicstudyplanner.api.dto.PlannerResponseDto;
import com.ia.project.dynamicstudyplanner.api.exception.JobNotFoundException;
import com.ia.project.dynamicstudyplanner.api.mapper.ExamMapper;
import com.ia.project.dynamicstudyplanner.api.mapper.StudentProfileMapper;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobStatus;
import com.ia.project.dynamicstudyplanner.infra.jobs.OptimizationJob;
import com.ia.project.dynamicstudyplanner.service.OptimizationJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Fluxo assíncrono: envia o pedido, recebe um identificador, busca o resultado depois.
 *
 * <h2>Por que este caminho existe ao lado de {@code POST /generate} (achado E6)</h2>
 *
 * O endpoint original mantém a conexão HTTP aberta até o plano ficar pronto — 2,4 a 3,4 segundos no
 * pior pedido aceito, e mais que isso quando há fila. Balanceadores e <i>gateways</i> têm prazos
 * próprios, e um pedido pesado atrás de fila estoura qualquer um deles; quando isso acontece, a
 * otimização em andamento continua gastando CPU para uma conexão que já foi embora.
 *
 * <p>Aqui o envio termina em milissegundos e a espera é do cliente, no tempo dele.
 *
 * <h2>O caminho síncrono continua funcionando, e isso é deliberado</h2>
 *
 * {@code POST /generate} é o contrato publicado, documentado no README e coberto por testes. Trocá-lo
 * por este quebraria todo cliente existente de uma vez. Os dois convivem: quem faz um pedido pequeno
 * (a maioria — 25 ms no payload típico) continua no caminho direto; quem faz pedido pesado, ou quem
 * está atrás de um <i>gateway</i> com prazo curto, usa este.
 *
 * <h2>Consultar o resultado exige estado compartilhado</h2>
 *
 * São duas requisições HTTP distintas, e nada garante que caiam na mesma réplica. Com registro
 * local, a consulta teria chance 1/N de acertar a réplica que conhece o identificador. Por isso o
 * registro de trabalhos usa a mesma chave de configuração do limite de taxa
 * ({@code api.shared-state.redis.enabled}) — as duas peças sobem juntas, e o modo local se anuncia
 * no log.
 */
@RestController
@RequestMapping("/api/v1/optimizer/jobs")
@Tag(name = "Study Plan Optimizer (asynchronous)",
        description = "Submit an optimization request and collect the result later.")
public class OptimizerJobController {

    private final OptimizationJobService jobService;
    private final ObjectMapper objectMapper;
    private final ExamMapper examMapper;
    private final StudentProfileMapper studentProfileMapper;

    public OptimizerJobController(OptimizationJobService jobService, ObjectMapper objectMapper,
                                  ExamMapper examMapper, StudentProfileMapper studentProfileMapper) {
        this.jobService = jobService;
        this.objectMapper = objectMapper;
        this.examMapper = examMapper;
        this.studentProfileMapper = studentProfileMapper;
    }

    @PostMapping
    @Operation(summary = "Submit an optimization request",
            description = "Accepts the request, returns an identifier immediately and runs the "
                    + "genetic algorithm in the background. Poll the returned URL for the result.")
    @ApiResponses({
            @ApiResponse(responseCode = "202",
                    description = "Accepted. The Location header and statusUrl point to the job.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JobAcceptedDto.class))),
            @ApiResponse(responseCode = "400",
                    description = "Bad Request. The payload failed validation or could not be parsed.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429",
                    description = "Too Many Requests. The per-client rate limit was exceeded.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503",
                    description = "Service Unavailable. The queue is full; retry after the number "
                            + "of seconds in the Retry-After header.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<JobAcceptedDto> submeter(@Valid @RequestBody OptimizationRequest request) {
        // O mapeamento DTO -> dominio acontece AQUI, e nao no servico: o servico nao pode conhecer
        // a camada de API sem criar um ciclo entre modulos (ver JobResultSerializer).
        Exam exam = examMapper.toDomain(request.exam());
        StudentProfile profile = studentProfileMapper.toDomain(
                request.studentProfile(), exam.getAllSubjects());

        String id = jobService.submeter(exam, profile,
                request.gaConfig().totalStudyDays(),
                request.gaConfig().numGenerations(),
                request.gaConfig().populationSize());
        URI url = URI.create("/api/v1/optimizer/jobs/" + id);
        return ResponseEntity.accepted()
                .location(url)
                .body(new JobAcceptedDto(id, JobStatus.PENDING.name(), url.toString()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Read the status and, once ready, the result of a job")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "The job exists. The result field is present once SUCCEEDED.",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = JobStatusDto.class))),
            @ApiResponse(responseCode = "404",
                    description = "No job with this identifier — never existed, or already expired.",
                    content = @Content(mediaType = "application/problem+json",
                            schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<JobStatusDto> consultar(@PathVariable String id) {
        OptimizationJob job = jobService.consultar(id)
                .orElseThrow(() -> new JobNotFoundException(id));
        return ResponseEntity.ok(paraDto(job));
    }

    private JobStatusDto paraDto(OptimizationJob job) {
        PlannerResponseDto resultado = null;
        if (job.status() == JobStatus.SUCCEEDED && job.resultJson() != null) {
            try {
                resultado = objectMapper.readValue(job.resultJson(), PlannerResponseDto.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Resultado do trabalho " + job.id() + " ilegivel", e);
            }
        }
        String erro = job.errorDetail() == null ? null
                : job.errorType() + ": " + job.errorDetail();
        return new JobStatusDto(job.id(), job.status().name(), job.submittedAt(),
                job.startedAt(), job.finishedAt(), resultado, erro);
    }
}
