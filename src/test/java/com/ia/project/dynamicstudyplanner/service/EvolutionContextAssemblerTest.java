package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessEvaluator;
import com.ia.project.dynamicstudyplanner.service.calculation.BaselineCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator;
import com.ia.project.dynamicstudyplanner.service.calculation.ImportanceCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O montador do contexto, testado sem subir o algoritmo genético.
 *
 * <h2>O que este arquivo demonstra sobre o achado E8</h2>
 *
 * Até a etapa 03e, montar o contexto era um método <b>privado</b> de {@code StudyOptimizerService}.
 * Verificar que o horizonte de planejamento ou a carga diária estavam certos exigia rodar uma
 * otimização inteira e inspecionar o plano no fim — um teste caro, lento e indireto, que falharia
 * por dezenas de razões sem relação com o que se queria verificar.
 *
 * <p>Com a responsabilidade separada, os mesmos valores são verificáveis diretamente. É o argumento
 * do E8 em forma executável: responsabilidades misturadas não são só desconfortáveis de ler, elas
 * <b>impedem que cada uma seja verificada por si</b>.
 */
@DisplayName("EvolutionContextAssembler: o que a evolucao precisa saber")
class EvolutionContextAssemblerTest {

    private static final LocalDate HOJE = LocalDate.now();

    private final ImportanceCalculator importanceCalculator = new ImportanceCalculator();
    private final EvolutionContextAssembler assembler = new EvolutionContextAssembler(
            new BaselineCalculator(importanceCalculator),
            importanceCalculator,
            new CognitiveLoadCalculator(),
            new FitnessEvaluator(List.of(), List.of(), List.of()));

    private static Exam exame(int diasAteAProva) {
        return new Exam("Concurso", HOJE.plusDays(diasAteAProva), 100.0,
                List.of(new Subject("Portugues", 20, 3), new Subject("Matematica", 15, 4)),
                List.of());
    }

    private static StudentProfile perfil(Exam exame, int horasPorDia) {
        Map<Subject, Double> lacunas = new HashMap<>();
        for (Subject disciplina : exame.getGeneralKnowledgeSubjects()) {
            lacunas.put(disciplina, 3.0);
        }
        Map<DayOfWeek, Integer> disponibilidade = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek dia : DayOfWeek.values()) {
            disponibilidade.put(dia, horasPorDia);
        }
        return new StudentProfile("Aluno", lacunas, disponibilidade,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }

    @Test
    @DisplayName("o contexto sai completo: nenhum campo fica nulo por esquecimento")
    void oContextoSaiCompleto() {
        Exam exame = exame(200);
        EvolutionContext contexto = assembler.assemble(exame, perfil(exame, 3));

        // O construtor passo a passo (ADR-0004) permite omitir campos, o que e proposital para o
        // caminho tatico — mas no caminho de producao nenhum deles pode faltar.
        assertThat(contexto.importanceScores()).isNotEmpty();
        assertThat(contexto.normalizedImportance()).isNotEmpty();
        assertThat(contexto.retentionWeights()).isNotEmpty();
        assertThat(contexto.minimumDaysPerSubject()).isNotEmpty();
        assertThat(contexto.studentState()).isNotNull();
        assertThat(contexto.fitnessEvaluator()).isNotNull();
        assertThat(contexto.retentionProfile()).isNotNull();
        assertThat(contexto.engagementProfile()).isNotNull();
        assertThat(contexto.planStartDate()).isNotNull();
    }

    @Test
    @DisplayName("o horizonte de planejamento e a distancia ate a prova, e nunca menor que 1")
    void oHorizonteEADistanciaAteAProva() {
        assertThat(assembler.assemble(exame(200), perfil(exame(200), 3)).planningHorizonDays())
                .isEqualTo(200);

        // Prova hoje ou no passado: o horizonte e limitado a 1 em vez de virar zero ou negativo,
        // porque ele DIVIDE a estimativa de espacamento do termo de retencao.
        Exam provaHoje = exame(0);
        assertThat(assembler.assemble(provaHoje, perfil(provaHoje, 3)).planningHorizonDays())
                .as("horizonte zero produziria divisao por zero na fitness de retencao")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("as horas por dia de estudo saem da disponibilidade semanal, arredondadas para cima")
    void asHorasPorDiaSaemDaDisponibilidadeSemanal() {
        Exam exame = exame(200);

        // 7 dias x 3h = 21h por semana; 21/7 = 3 exatos.
        assertThat(assembler.assemble(exame, perfil(exame, 3)).hoursPerStudyDay())
                .isEqualTo(3);

        // 7 dias x 1h = 7h; 7/7 = 1.
        assertThat(assembler.assemble(exame, perfil(exame, 1)).hoursPerStudyDay())
                .as("nunca zero: um dia de estudo com zero horas nao e um dia de estudo")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("o mesmo edital e perfil produzem o mesmo contexto")
    void oMesmoPedidoProduzOMesmoContexto() {
        Exam exame = exame(200);
        StudentProfile perfil = perfil(exame, 3);

        EvolutionContext primeiro = assembler.assemble(exame, perfil);
        EvolutionContext segundo = assembler.assemble(exame, perfil);

        assertThat(segundo.importanceScores()).isEqualTo(primeiro.importanceScores());
        assertThat(segundo.minimumDaysPerSubject()).isEqualTo(primeiro.minimumDaysPerSubject());
        assertThat(segundo.planningHorizonDays()).isEqualTo(primeiro.planningHorizonDays());
        assertThat(segundo.maxDailyCognitiveLoad()).isEqualTo(primeiro.maxDailyCognitiveLoad());
    }
}
