package com.ia.project.dynamicstudyplanner.api.mapper;

import com.ia.project.dynamicstudyplanner.api.dto.ExamDto;
import com.ia.project.dynamicstudyplanner.api.dto.OptimizationResultDto;
import com.ia.project.dynamicstudyplanner.api.dto.PlannerResponseDto;
import com.ia.project.dynamicstudyplanner.api.dto.ScheduleResultDto;
import com.ia.project.dynamicstudyplanner.api.dto.StudentProfileDto;
import com.ia.project.dynamicstudyplanner.api.dto.StudentStateDto;
import com.ia.project.dynamicstudyplanner.api.dto.StudyPlanDto;
import com.ia.project.dynamicstudyplanner.api.dto.SubjectDto;
import com.ia.project.dynamicstudyplanner.api.dto.ThematicAxisDto;
import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.FullPlannerResult;
import com.ia.project.dynamicstudyplanner.domain.OptimizationResult;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudyBlock;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exam.ThematicAxis;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleResult;
import com.ia.project.dynamicstudyplanner.domain.schedule.ScheduleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os oito mapeadores que traduzem entre o contrato HTTP e o domínio.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * O pacote {@code api.mapper} tinha <b>0% de cobertura de ramos</b> antes da etapa 01b
 * ({@code docs/qualidade/01-diagnostico-testes.md} §1.1). São estes objetos que montam o corpo que o
 * aluno recebe: um campo renomeado, um tipo trocado ou um nulo inesperado passaria pela suíte
 * inteira sem uma falha.
 *
 * <h2>Ida e volta, e não só ida</h2>
 *
 * Onde o mapeador declara as duas direções, o teste é de ida e volta: converter para DTO e de volta
 * para o domínio precisa devolver um objeto igual ao original. Isso pega a classe inteira de erro em
 * que dois campos do mesmo tipo são trocados de lugar — {@code questionCount} e
 * {@code cognitiveLoad}, por exemplo —, que uma asserção campo a campo escrita a partir do próprio
 * mapeador não pega, porque repetiria o mesmo engano.
 */
@DisplayName("Mapeadores entre o contrato HTTP e o dominio")
class MapperRoundTripTest {

    private static final LocalDate DATA_DA_PROVA = LocalDate.of(2026, 12, 1);

    private final SubjectMapper subjectMapper = new SubjectMapper();
    private final ThematicAxisMapper thematicAxisMapper = new ThematicAxisMapper(subjectMapper);
    private final ExamMapper examMapper = new ExamMapper(subjectMapper, thematicAxisMapper);
    private final StudentProfileMapper studentProfileMapper = new StudentProfileMapper();
    private final StudyPlanMapper studyPlanMapper = new StudyPlanMapper();
    private final StudyBlockMapper studyBlockMapper = new StudyBlockMapper();
    private final ScheduleResultMapper scheduleResultMapper = new ScheduleResultMapper(studyBlockMapper);
    private final OptimizationResultMapper optimizationResultMapper =
            new OptimizationResultMapper(studyPlanMapper);
    private final FullPlannerResultMapper fullPlannerResultMapper =
            new FullPlannerResultMapper(optimizationResultMapper, scheduleResultMapper);

    @Nested
    @DisplayName("Entrada: do contrato HTTP para o dominio")
    class Entrada {

        @Test
        @DisplayName("SubjectMapper preserva os tres campos, na ordem certa, nas duas direcoes")
        void subjectMapperFazIdaEVolta() {
            // questionCount (20) e cognitiveLoad (4) sao ambos int e sao valores distintos de
            // proposito: trocados de lugar, a ida e volta deixa de fechar.
            SubjectDto original = new SubjectDto("Direito Penal", 20, 4);

            Subject dominio = subjectMapper.toDomain(original);
            SubjectDto voltou = subjectMapper.toDto(dominio);

            assertThat(dominio.name()).isEqualTo("Direito Penal");
            assertThat(dominio.questionCount()).isEqualTo(20);
            assertThat(dominio.cognitiveLoad()).isEqualTo(4);
            assertThat(voltou).isEqualTo(original);
        }

        @Test
        @DisplayName("ThematicAxisMapper preserva o eixo e as disciplinas aninhadas")
        void thematicAxisMapperFazIdaEVolta() {
            ThematicAxisDto original = new ThematicAxisDto(7, "Eixo Juridico", 2.5, List.of(
                    new SubjectDto("Constitucional", 30, 5),
                    new SubjectDto("Administrativo", 25, 3)));

            ThematicAxis dominio = thematicAxisMapper.toDomain(original);

            assertThat(dominio.id()).isEqualTo(7);
            assertThat(dominio.name()).isEqualTo("Eixo Juridico");
            assertThat(dominio.weight()).isEqualTo(2.5);
            assertThat(dominio.subjects()).extracting(Subject::name)
                    .containsExactly("Constitucional", "Administrativo");
            assertThat(thematicAxisMapper.toDto(dominio)).isEqualTo(original);
        }

        @Test
        @DisplayName("ExamMapper preserva a estrutura completa do edital nas duas direcoes")
        void examMapperFazIdaEVolta() {
            ExamDto original = new ExamDto(
                    "Concurso 2026", DATA_DA_PROVA, 40.0,
                    List.of(new SubjectDto("Portugues", 20, 2)),
                    List.of(new ThematicAxisDto(1, "Eixo TI", 1.5,
                            List.of(new SubjectDto("Informatica", 25, 4)))));

            Exam dominio = examMapper.toDomain(original);

            assertThat(dominio.getName()).isEqualTo("Concurso 2026");
            assertThat(dominio.getExamDate()).isEqualTo(DATA_DA_PROVA);
            assertThat(dominio.getGeneralKnowledgeTotalScore()).isEqualTo(40.0);
            assertThat(dominio.getAllSubjects()).extracting(Subject::name)
                    .as("getAllSubjects reune conhecimentos gerais e especificos")
                    .containsExactlyInAnyOrder("Portugues", "Informatica");
            assertThat(examMapper.toDto(dominio)).isEqualTo(original);
        }

        @Test
        @DisplayName("ExamMapper devolve null para entrada null, nos dois sentidos")
        void examMapperTrataNull() {
            // Ramo explicito no codigo de producao, e ate agora nunca executado.
            assertThat(examMapper.toDomain(null)).isNull();
            assertThat(examMapper.toDto(null)).isNull();
        }

        @Test
        @DisplayName("StudentProfileMapper liga as lacunas declaradas as disciplinas do edital")
        void studentProfileMapperLigaLacunasAsDisciplinas() {
            Subject portugues = new Subject("Portugues", 20, 2);
            Subject informatica = new Subject("Informatica", 25, 4);

            StudentProfileDto dto = new StudentProfileDto(
                    "Aluno", Map.of("Portugues", 2.0, "Informatica", 4.5),
                    Map.of(DayOfWeek.MONDAY, 3, DayOfWeek.SATURDAY, 6),
                    new StudentStateDto(3.0, 2.0, 4.0, Chronotype.NIGHT_OWL));

            StudentProfile perfil = studentProfileMapper.toDomain(dto, List.of(portugues, informatica));

            assertThat(perfil.getName()).isEqualTo("Aluno");
            assertThat(perfil.getKnowledgeGapFactor(portugues))
                    .as("a lacuna declarada por nome precisa chegar na disciplina certa")
                    .isEqualTo(2.0);
            assertThat(perfil.getKnowledgeGapFactor(informatica)).isEqualTo(4.5);
            assertThat(perfil.getTotalWeeklyHours()).isEqualTo(9);
        }

        @Test
        @DisplayName("StudentProfileMapper usa INTERMEDIATE quando o cronotipo vem ausente")
        void cronotipoAusenteViraIntermediate() {
            Subject portugues = new Subject("Portugues", 20, 2);
            StudentProfileDto dto = new StudentProfileDto(
                    "Aluno", Map.of("Portugues", 2.0), Map.of(DayOfWeek.MONDAY, 3),
                    new StudentStateDto(3.0, 2.0, 4.0, null));

            StudentProfile perfil = studentProfileMapper.toDomain(dto, List.of(portugues));

            assertThat(perfil.getState()).isNotNull();
            assertThat(perfil.getState().chronotype())
                    .as("ramo de fallback do cronotipo, nunca executado antes da etapa 01b")
                    .isEqualTo(Chronotype.INTERMEDIATE);
        }

        @Test
        @DisplayName("StudentProfileMapper aceita perfil sem bloco de estado")
        void estadoAusenteEhAceito() {
            Subject portugues = new Subject("Portugues", 20, 2);
            StudentProfileDto dto = new StudentProfileDto(
                    "Aluno", Map.of("Portugues", 2.0), Map.of(DayOfWeek.MONDAY, 3), null);

            StudentProfile perfil = studentProfileMapper.toDomain(dto, List.of(portugues));

            assertThat(perfil.getState())
                    .as("o campo state e opcional no contrato: sua ausencia nao pode quebrar o mapeamento")
                    .isNull();
        }

        @Test
        @DisplayName("COMPORTAMENTO ATUAL: lacuna que cita disciplina fora do edital vira chave nula")
        void lacunaParaDisciplinaInexistenteViraChaveNula() {
            // Este teste fixa o comportamento de HOJE, que nao e obviamente o desejado.
            //
            // StudentProfileMapper resolve o nome da disciplina com subjectMap.get(nome). Um nome
            // que nao esta no edital devolve null, e null vira chave do mapa de lacunas em vez de
            // ser rejeitado. Nenhuma validacao do contrato impede isso: knowledgeGaps e um mapa de
            // String livre, sem referencia cruzada com as disciplinas declaradas.
            //
            // Nao esta sendo corrigido aqui porque a correcao muda codigo de producao e o contrato
            // de erro da API (provavelmente um 400 novo), o que e decisao de estrutura, nao de
            // teste. Registrado como pendencia P5 em docs/qualidade/01b-correcao-testes.md.
            Subject portugues = new Subject("Portugues", 20, 2);
            StudentProfileDto dto = new StudentProfileDto(
                    "Aluno", Map.of("Disciplina Que Nao Existe", 3.0),
                    Map.of(DayOfWeek.MONDAY, 3), null);

            StudentProfile perfil = studentProfileMapper.toDomain(dto, List.of(portugues));

            assertThat(perfil.getKnowledgeGapFactor(portugues))
                    .as("a disciplina real fica sem lacuna declarada e cai no fator padrao")
                    .isEqualTo(1.0);
        }

        @Test
        @DisplayName("COMPORTAMENTO ATUAL: duas lacunas invalidas colidem na mesma chave nula")
        void duasLacunasInvalidasColidem() {
            // Consequencia da anterior: Collectors.toMap recusa chaves duplicadas, entao duas
            // disciplinas inexistentes produzem IllegalStateException — que o
            // GlobalExceptionHandler traduz em 500, nao em 400. O aluno recebe "erro interno"
            // por um erro que e dele. Mesma pendencia P5.
            Subject portugues = new Subject("Portugues", 20, 2);
            StudentProfileDto dto = new StudentProfileDto(
                    "Aluno", Map.of("Nao Existe A", 3.0, "Nao Existe B", 4.0),
                    Map.of(DayOfWeek.MONDAY, 3), null);

            assertThatThrownBy(() -> studentProfileMapper.toDomain(dto, List.of(portugues)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("Saida: do dominio para o corpo da resposta")
    class Saida {

        private final Subject portugues = new Subject("Portugues", 20, 2);
        private final Subject informatica = new Subject("Informatica", 25, 4);

        @Test
        @DisplayName("StudyPlanMapper converte disciplinas em nomes, preservando os dias")
        void studyPlanMapperUsaNomesComoChave() {
            StudyPlan plano = new StudyPlan(Map.of(portugues, 30, informatica, 45));

            StudyPlanDto dto = studyPlanMapper.toDto(plano);

            assertThat(dto.daysPerSubject())
                    .as("o cliente recebe nomes, nao objetos de dominio")
                    .containsExactlyInAnyOrderEntriesOf(Map.of("Portugues", 30, "Informatica", 45));
        }

        @Test
        @DisplayName("StudyPlanMapper recusa a direcao inversa, que nao faz sentido no dominio")
        void studyPlanMapperRecusaVolta() {
            assertThatThrownBy(() -> studyPlanMapper.toDomain(new StudyPlanDto(Map.of())))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("not supported");
        }

        @Test
        @DisplayName("StudyBlockMapper reduz o bloco ao nome da disciplina e as horas")
        void studyBlockMapperReduzAoEssencial() {
            assertThat(studyBlockMapper.toDto(new StudyBlock(portugues, 3)))
                    .isEqualTo(new com.ia.project.dynamicstudyplanner.api.dto.StudyBlockDto("Portugues", 3));
            assertThat(studyBlockMapper.toDto(null)).isNull();
        }

        @Test
        @DisplayName("ScheduleResultMapper preserva datas, status e horas do cronograma")
        void scheduleResultMapperPreservaOCronograma() {
            LocalDate dia1 = LocalDate.of(2026, 9, 10);
            LocalDate dia2 = LocalDate.of(2026, 9, 11);
            ScheduleResult resultado = new ScheduleResult(
                    Map.of(dia1, List.of(new StudyBlock(portugues, 2)),
                            dia2, List.of(new StudyBlock(informatica, 3), new StudyBlock(portugues, 1))),
                    ScheduleStatus.WARNING_TIME_DEFICIT, 120.0, 90.0);

            ScheduleResultDto dto = scheduleResultMapper.toDto(resultado);

            assertThat(dto.status()).isEqualTo(ScheduleStatus.WARNING_TIME_DEFICIT);
            assertThat(dto.requiredHours()).isEqualTo(120.0);
            assertThat(dto.availableHours()).isEqualTo(90.0);
            assertThat(dto.schedule()).containsOnlyKeys(dia1, dia2);
            assertThat(dto.schedule().get(dia2))
                    .as("a ordem dos blocos dentro do dia e parte do que o aluno le")
                    .extracting(com.ia.project.dynamicstudyplanner.api.dto.StudyBlockDto::subjectName)
                    .containsExactly("Informatica", "Portugues");
            assertThat(scheduleResultMapper.toDto(null)).isNull();
        }

        @Test
        @DisplayName("OptimizationResultMapper preserva fitness, geracoes e tempo")
        void optimizationResultMapperPreservaMetadados() {
            OptimizationResult resultado = new OptimizationResult(
                    new StudyPlan(Map.of(portugues, 10)), 0.7321, 50, 1234L);

            OptimizationResultDto dto = optimizationResultMapper.toDto(resultado);

            assertThat(dto.fitness()).isEqualTo(0.7321);
            assertThat(dto.generationsRun()).isEqualTo(50);
            assertThat(dto.executionTimeMillis()).isEqualTo(1234L);
            assertThat(dto.plan().daysPerSubject()).containsEntry("Portugues", 10);
            assertThat(optimizationResultMapper.toDto(null)).isNull();
        }

        @Test
        @DisplayName("FullPlannerResultMapper monta o corpo final com a mensagem de sucesso")
        void fullPlannerResultMapperMontaOCorpoFinal() {
            FullPlannerResult resultado = new FullPlannerResult(
                    new OptimizationResult(new StudyPlan(Map.of(portugues, 10)), 0.5, 30, 100L),
                    new ScheduleResult(Map.of(LocalDate.of(2026, 9, 10),
                            List.of(new StudyBlock(portugues, 2))),
                            ScheduleStatus.SUCCESS_IDEAL_PLAN, 20.0, 20.0));

            PlannerResponseDto resposta = fullPlannerResultMapper.toResponse(resultado);

            assertThat(resposta.message())
                    .as("mensagem fixa do contrato de sucesso")
                    .isEqualTo("Full study plan generated successfully.");
            assertThat(resposta.optimizationResult()).isNotNull();
            assertThat(resposta.scheduleResult()).isNotNull();
            assertThat(fullPlannerResultMapper.toResponse(null)).isNull();
        }
    }
}
