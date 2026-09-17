package com.ia.project.dynamicstudyplanner.ga.tactical;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.domain.retention.SubjectRetentionState;
import com.ia.project.dynamicstudyplanner.domain.tactical.StudyMethodology;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyBlock;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TimeSlot;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.SpacedRepetitionRepairer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rede de segurança para a refatoração de {@link SpacedRepetitionRepairer} (etapa 04b).
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * {@code repair} tinha <b>complexidade cognitiva 21</b> (limiar 15) — a maior do repositório —, com
 * quatro níveis de aninhamento, e a classe estava a <b>4,9 % de cobertura</b>. A complexidade
 * cognitiva pesa o aninhamento, e não só a contagem de caminhos: três {@code if} encaixados custam
 * muito mais para ler do que três em sequência.
 *
 * <h2>Dublê em vez de motor real</h2>
 *
 * O reparador depende de {@link RetentionAlgorithm} apenas para uma pergunta:
 * "esta disciplina precisa de revisão hoje?". Os testes instalam um dublê que responde a partir de
 * um conjunto fixo, para que cada caso seja escolhido em vez de perseguido através da curva de
 * Ebbinghaus.
 */
@DisplayName("SpacedRepetitionRepairer: caracterizacao antes da refatoracao")
class SpacedRepetitionRepairerTest {

    private static final Subject PORTUGUES = new Subject("Portugues", 10, 3);
    private static final Subject MATEMATICA = new Subject("Matematica", 12, 5);
    private static final LocalDate DIA = LocalDate.of(2026, 3, 2);

    /** Responde "revisão obrigatória" apenas para as disciplinas informadas. */
    private static RetentionAlgorithm exigeRevisaoPara(Set<Subject> disciplinas) {
        return new RetentionAlgorithm() {
            @Override
            public double calculateRetentionProbability(SubjectRetentionState state, LocalDate targetDate) {
                return 1.0;
            }

            @Override
            public boolean isReviewMandatory(Subject subject, SubjectRetentionState state, LocalDate targetDate) {
                return disciplinas.contains(subject);
            }

            @Override
            public SubjectRetentionState processReview(SubjectRetentionState currentState,
                                                       LocalDate reviewDate, int performanceGrade) {
                return currentState;
            }
        };
    }

    private static TimeSlot faixa(int hora) {
        LocalDateTime de = DIA.atTime(hora, 0);
        return new TimeSlot(de, de.plusHours(1));
    }

    private static EvolutionContext contexto(Map<Subject, Double> importancias, RetentionProfile perfil) {
        return EvolutionContext.builder()
                .importanceScores(importancias)
                .minimumDaysPerSubject(Map.of())
                .planStartDate(DIA)
                .retentionProfile(perfil)
                // Obrigatorios pelo construtor passo a passo (ADR-0004); irrelevantes para o reparo.
                .planningHorizonDays(30)
                .hoursPerStudyDay(4)
                .maxDailyCognitiveLoad(10)
                .build();
    }

    @Test
    @DisplayName("perfil de retencao nulo devolve o proprio plano, sem tocar em nada")
    void perfilNuloDevolveOPlano() {
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        agenda.put(faixa(9), new TacticalStudyBlock(PORTUGUES, StudyMethodology.PASSIVE_READING, 60));
        TacticalStudyPlan plano = new TacticalStudyPlan(agenda);

        SpacedRepetitionRepairer reparador = new SpacedRepetitionRepairer(exigeRevisaoPara(Set.of(PORTUGUES)));

        assertThat(reparador.repair(plano, contexto(Map.of(PORTUGUES, 1.0), null)))
                .as("sem perfil de retencao nao ha o que reparar; devolve a MESMA instancia")
                .isSameAs(plano);
    }

    @Test
    @DisplayName("nenhuma revisao obrigatoria deixa a agenda intacta")
    void semRevisaoObrigatoriaAAgendaFicaIntacta() {
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        agenda.put(faixa(9), new TacticalStudyBlock(PORTUGUES, StudyMethodology.PASSIVE_READING, 60));
        agenda.put(faixa(11), new TacticalStudyBlock(MATEMATICA, StudyMethodology.PRACTICE_EXAM, 60));
        TacticalStudyPlan plano = new TacticalStudyPlan(agenda);

        SpacedRepetitionRepairer reparador = new SpacedRepetitionRepairer(exigeRevisaoPara(Set.of()));

        TacticalStudyPlan reparado = reparador.repair(plano,
                contexto(Map.of(PORTUGUES, 1.0, MATEMATICA, 1.0), new RetentionProfile(Map.of())));

        assertThat(reparado.getSchedule()).isEqualTo(agenda);
    }

    @Test
    @DisplayName("revisao obrigatoria ja presente nao provoca nova insercao")
    void revisaoJaPresenteNaoDuplica() {
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        agenda.put(faixa(9), new TacticalStudyBlock(PORTUGUES, StudyMethodology.SPACED_REPETITION_REVIEW, 60));
        agenda.put(faixa(11), new TacticalStudyBlock(MATEMATICA, StudyMethodology.PRACTICE_EXAM, 60));
        TacticalStudyPlan plano = new TacticalStudyPlan(agenda);

        SpacedRepetitionRepairer reparador = new SpacedRepetitionRepairer(exigeRevisaoPara(Set.of(PORTUGUES)));

        TacticalStudyPlan reparado = reparador.repair(plano,
                contexto(Map.of(PORTUGUES, 1.0), new RetentionProfile(Map.of())));

        assertThat(reparado.getSchedule()).isEqualTo(agenda);
    }

    @Test
    @DisplayName("revisao faltante sobrescreve o bloco de menor pontuacao, preservando o tamanho da agenda")
    void revisaoFaltanteSobrescreveOBlocoMaisFraco() {
        // Pontuacao = cognitiveLoad da disciplina x multiplicador de retencao da metodologia.
        //   9h  Portugues(3) x PASSIVE_READING(0.5) = 1.5  <- o mais fraco, sera sobrescrito
        //   11h Matematica(5) x PRACTICE_EXAM(2.0)  = 10.0
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        agenda.put(faixa(9), new TacticalStudyBlock(PORTUGUES, StudyMethodology.PASSIVE_READING, 60));
        agenda.put(faixa(11), new TacticalStudyBlock(MATEMATICA, StudyMethodology.PRACTICE_EXAM, 60));
        TacticalStudyPlan plano = new TacticalStudyPlan(agenda);

        SpacedRepetitionRepairer reparador = new SpacedRepetitionRepairer(exigeRevisaoPara(Set.of(MATEMATICA)));

        TacticalStudyPlan reparado = reparador.repair(plano,
                contexto(Map.of(MATEMATICA, 1.0), new RetentionProfile(Map.of())));

        assertThat(reparado.getSchedule())
                .as("reparo sobrescreve, nao acrescenta: a agenda mantem o tamanho")
                .hasSize(2);
        assertThat(reparado.getSchedule().get(faixa(9)))
                .as("o bloco mais fraco virou a revisao obrigatoria")
                .isEqualTo(new TacticalStudyBlock(MATEMATICA, StudyMethodology.SPACED_REPETITION_REVIEW, 60));
        assertThat(reparado.getSchedule().get(faixa(11)))
                .as("o bloco forte foi preservado")
                .isEqualTo(agenda.get(faixa(11)));
    }

    @Test
    @DisplayName("agenda vazia com revisao obrigatoria continua vazia — nao ha bloco para sobrescrever")
    void agendaVaziaContinuaVazia() {
        TacticalStudyPlan plano = new TacticalStudyPlan(Map.of());

        SpacedRepetitionRepairer reparador = new SpacedRepetitionRepairer(exigeRevisaoPara(Set.of(PORTUGUES)));

        TacticalStudyPlan reparado = reparador.repair(plano,
                contexto(Map.of(PORTUGUES, 1.0), new RetentionProfile(Map.of())));

        assertThat(reparado.getSchedule())
                .as("comportamento medido: sem bloco alvo, a revisao obrigatoria simplesmente nao entra")
                .isEmpty();
    }

    @Test
    @DisplayName("duas revisoes obrigatorias sobrescrevem dois blocos distintos")
    void duasRevisoesSobrescrevemDoisBlocos() {
        Map<TimeSlot, TacticalStudyBlock> agenda = new LinkedHashMap<>();
        agenda.put(faixa(9), new TacticalStudyBlock(PORTUGUES, StudyMethodology.PASSIVE_READING, 60));
        agenda.put(faixa(11), new TacticalStudyBlock(PORTUGUES, StudyMethodology.VIDEO_LECTURE, 60));
        agenda.put(faixa(14), new TacticalStudyBlock(MATEMATICA, StudyMethodology.PRACTICE_EXAM, 60));
        TacticalStudyPlan plano = new TacticalStudyPlan(agenda);

        SpacedRepetitionRepairer reparador =
                new SpacedRepetitionRepairer(exigeRevisaoPara(Set.of(PORTUGUES, MATEMATICA)));

        TacticalStudyPlan reparado = reparador.repair(plano,
                contexto(new LinkedHashMap<>(Map.of(PORTUGUES, 1.0, MATEMATICA, 1.0)),
                        new RetentionProfile(Map.of())));

        assertThat(reparado.getSchedule()).hasSize(3);
        assertThat(reparado.getSchedule().values())
                .filteredOn(b -> b.methodology() == StudyMethodology.SPACED_REPETITION_REVIEW)
                .as("as duas disciplinas obrigatorias ganharam revisao")
                .hasSize(2);
    }
}
