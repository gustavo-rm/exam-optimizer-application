package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.tactical.AvailabilityWindow;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionProfile;
import com.ia.project.dynamicstudyplanner.plan.PlanEngines;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.sinapse.importance.GoalPriorityImportance;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import com.ia.project.dynamicstudyplanner.service.calculation.retention.HybridRetentionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * As suposições de projeto do adaptador, travadas uma a uma.
 *
 * <h2>Por que um arquivo só para elas</h2>
 *
 * Cada uma resolve uma ambiguidade que o contrato não resolve, e cada uma poderia ter sido resolvida
 * de outro jeito defensável. Declaradas em {@code docs/SINAPSE_ADAPTER.md} §3 e travadas aqui: o
 * documento diz o que foi escolhido e por quê, e este arquivo garante que o código ainda faz isso.
 * Uma suposição só em prosa é uma suposição que já mudou sem ninguém saber.
 */
@DisplayName("Adaptador SINAPSE: as suposicoes declaradas")
class SinapseAssumptionsTest {

    private static final HybridRetentionEngine RETENTION = new HybridRetentionEngine();

    @Nested
    @DisplayName("effortTier fora do conjunto fechado")
    class EffortTier {

        @Test
        @DisplayName("e recusado com 422 nomeando o valor recebido, nao caindo num padrao")
        void eRecusadoNomeandoOValor() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(PlanRequests.topic(PlanRequests.TOPIC_1,
                            PlanRequests.SUBJECT_FIRST, 1, 30, "GIGANTIC")))
                    .build();

            assertThatThrownBy(() -> PlanEngines.genetic().plan(request))
                    .isInstanceOf(PlanRejectedException.class)
                    .hasMessageContaining("SHORT")
                    .hasMessageContaining("EXTENDED")
                    .extracting(thrown -> ((PlanRejectedException) thrown).offending())
                    .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                    .as("o chamador precisa saber QUAL valor veio, senao tem de bisseccionar o payload")
                    .anySatisfy(offender -> assertThat(offender).contains("GIGANTIC"));
        }

        @Test
        @DisplayName("nulo tambem e recusado, e nao tratado como ausencia benigna")
        void nuloTambemERecusado() {
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(PlanRequests.topic(PlanRequests.TOPIC_1,
                            PlanRequests.SUBJECT_FIRST, 1, 30, null)))
                    .build();

            assertThatThrownBy(() -> PlanEngines.genetic().plan(request))
                    .isInstanceOf(PlanRejectedException.class);
        }

        @Test
        @DisplayName("as quatro faixas mapeiam para 1..4, em ordem")
        void asQuatroFaixasMapeiamEmOrdem() {
            assertThat(EffortTierBands.bandOf("SHORT")).isEqualTo(1);
            assertThat(EffortTierBands.bandOf("STANDARD")).isEqualTo(2);
            assertThat(EffortTierBands.bandOf("LONG")).isEqualTo(3);
            assertThat(EffortTierBands.bandOf("EXTENDED")).isEqualTo(4);
        }

        @Test
        @DisplayName("o motor guloso NAO recusa a faixa desconhecida: ele nao a le")
        void oGulosoNaoRecusa() {
            // A assimetria e deliberada, nao uma inconsistencia: o guloso aloca por
            // estimatedMinutes e nunca consulta a faixa, entao recusar a requisicao seria recusa-la
            // por um campo de que a resposta nao depende.
            PlanRequest request = PlanRequests.builder()
                    .withTopics(List.of(PlanRequests.topic(PlanRequests.TOPIC_1,
                            PlanRequests.SUBJECT_FIRST, 1, 30, "GIGANTIC")))
                    .build();

            assertThat(PlanEngines.greedy().plan(request).sessions()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("historico ausente da janela de noventa dias")
    class HistoricoAusente {

        @Test
        @DisplayName("topico sem entrada nenhuma nao ganha estado: e terreno novo")
        void semEntradaNaoGanhaEstado() {
            RetentionProfile profile = RetentionHistory.of(
                    PlanRequests.builder().build().topics(), List.of(), RETENTION);

            assertThat(profile.getAllStates())
                    .as("ausencia e ambigua entre 'nunca' e 'ha mais de 90 dias'; inventar uma data "
                            + "alimentaria exp(-dias/estabilidade) com um numero nosso")
                    .isEmpty();
        }

        @Test
        @DisplayName("topico presente sem lastStudiedAt tambem nao ganha estado")
        void presenteSemDataTambemNao() {
            PlanRequest request = PlanRequests.builder()
                    .withHistory(List.of(PlanRequests.studied(PlanRequests.TOPIC_1, null)))
                    .build();

            assertThat(RetentionHistory.of(request.topics(), request.history(), RETENTION)
                    .getAllStates())
                    .as("os dois estados diferem na plataforma; nao diferem no que este lado pode "
                            + "calcular a partir deles")
                    .isEmpty();
        }

        @Test
        @DisplayName("topico com data ganha estado, e a data e a que veio")
        void comDataGanhaEstado() {
            PlanRequest request = PlanRequests.builder()
                    .withHistory(List.of(PlanRequests.studied(
                            PlanRequests.TOPIC_1, "2026-08-20T20:00:00Z", RecallRating.GOOD)))
                    .build();

            RetentionProfile profile =
                    RetentionHistory.of(request.topics(), request.history(), RETENTION);
            PlanningItem first = TopicPlanningItems.toItem(request.topics().get(0));

            assertThat(profile.getState(first)).isNotNull();
            assertThat(profile.getState(first).getLastReviewDate())
                    .as("a unica data que o payload carrega e a que fica guardada")
                    .isEqualTo(java.time.LocalDate.parse("2026-08-20"));
        }

        @Test
        @DisplayName("terreno novo produz uma sessao de ESTUDO, nao de revisao")
        void terrenoNovoProduzEstudo() {
            // A consequencia observavel da suposicao: o erro cai do lado de reensinar, nunca do
            // lado de pular conteudo que o aluno talvez nunca tenha visto.
            PlanResponse response = PlanEngines.genetic()
                    .plan(PlanRequests.builder().withHistory(List.of()).build());

            assertThat(response.sessions())
                    .filteredOn(session -> session.sequenceIndex() == 0)
                    .allSatisfy(session -> assertThat(session.kind()).isEqualTo(SessionKind.STUDY));
        }
    }

    @Nested
    @DisplayName("topico cuja disciplina nao aparece em goals")
    class TopicoSemMeta {

        @Test
        @DisplayName("e planejado na prioridade minima, nem excluido nem recusado")
        void ePlanejadoNaPrioridadeMinima() {
            PlanRequest request = PlanRequests.builder().withGoals(List.of(
                    PlanRequests.goal(PlanRequests.SUBJECT_FIRST, "2026-09-20", 5))).build();

            Map<PlanningItem, Double> importance =
                    GoalPriorityImportance.of(request.topics(), request.goals());
            PlanningItem doSegundoAssunto = TopicPlanningItems.toItem(request.topics().get(2));

            assertThat(importance.get(doSegundoAssunto))
                    .as("zero deixaria o topico agendado e invisivel para todo termo da fitness")
                    .isEqualTo(GoalPriorityImportance.PRIORITY_WITHOUT_GOAL);
        }

        @Test
        @DisplayName("duas metas para a mesma disciplina colapsam na maior prioridade")
        void duasMetasColapsamNaMaior() {
            PlanRequest request = PlanRequests.builder().withGoals(List.of(
                    PlanRequests.goal(PlanRequests.SUBJECT_FIRST, "2026-09-20", 2),
                    PlanRequests.goal(PlanRequests.SUBJECT_FIRST, "2026-09-25", 5))).build();

            Map<PlanningItem, Double> importance =
                    GoalPriorityImportance.of(request.topics(), request.goals());

            assertThat(importance.get(TopicPlanningItems.toItem(request.topics().get(0))))
                    .as("o contrato nao envia identificador de meta, entao duas entradas para uma "
                            + "disciplina sao indistinguiveis; o maximo nunca rebaixa o que o aluno "
                            + "expressou")
                    .isEqualTo(5.0);
        }
    }

    @Nested
    @DisplayName("piso de sessoes por topico")
    class PisoDeSessoes {

        @Test
        @DisplayName("vem de estimatedMinutes sobre o dia de estudo medido, sem constante nenhuma")
        void vemDeEstimatedMinutesSobreODiaMedido() {
            // Tres janelas de 3h em tres dias distintos => dia de estudo de 180 minutos.
            List<AvailabilityWindow> windows = AvailabilityWindows.of(List.of(
                    PlanRequests.slot("2026-09-01T09:00:00Z", "2026-09-01T12:00:00Z"),
                    PlanRequests.slot("2026-09-02T09:00:00Z", "2026-09-02T12:00:00Z"),
                    PlanRequests.slot("2026-09-03T09:00:00Z", "2026-09-03T12:00:00Z")));

            assertThat(SinapseMinimumDays.minutesPerStudyDay(windows)).isEqualTo(180.0);
            assertThat(SinapseMinimumDays.sessionsFor(30, 180.0))
                    .as("um topico curto cabe numa sentada")
                    .isEqualTo(1);
            assertThat(SinapseMinimumDays.sessionsFor(180, 180.0))
                    .as("exatamente um dia de estudo continua sendo uma sentada")
                    .isEqualTo(1);
            assertThat(SinapseMinimumDays.sessionsFor(181, 180.0))
                    .as("um minuto a mais ja exige a segunda")
                    .isEqualTo(2);
            assertThat(SinapseMinimumDays.sessionsFor(540, 180.0)).isEqualTo(3);
        }

        @Test
        @DisplayName("topicos de tamanhos diferentes recebem pisos diferentes")
        void tamanhosDiferentesRecebemPisosDiferentes() {
            // O piso era um 1 fixo para todo topico. Era defensavel como definicao e jogava fora
            // estimatedMinutes, que e o unico numero calibrado de um topico: um topico de 180
            // minutos e um de 30 nao precisam do mesmo esforco minimo, e o payload diz isso.
            PlanRequest request = PlanRequests.builder()
                    .withAvailability(List.of(
                            PlanRequests.slot("2026-09-01T09:00:00Z", "2026-09-01T10:00:00Z")))
                    .build();
            Map<PlanningItem, Integer> floor = SinapseMinimumDays.of(
                    request.topics(), AvailabilityWindows.of(request.availability()));

            // Dia de estudo de 60 minutos: 30min -> 1, 60min -> 1, 108min -> 2, 180min -> 3.
            assertThat(floor.get(TopicPlanningItems.toItem(request.topics().get(0)))).isEqualTo(1);
            assertThat(floor.get(TopicPlanningItems.toItem(request.topics().get(2)))).isEqualTo(2);
            assertThat(floor.get(TopicPlanningItems.toItem(request.topics().get(3)))).isEqualTo(3);
            assertThat(SinapseMinimumDays.totalFloor(floor)).isEqualTo(7);
        }

        @Test
        @DisplayName("o denominador sao dias COM disponibilidade, nao dias do horizonte")
        void oDenominadorSaoDiasComDisponibilidade() {
            // Duas janelas de 3h no mesmo dia sao um dia de estudo de 6h, nao dois de 3h. E duas em
            // dias distintos, com o mesmo total, sao dois de 3h. Dividir pelos dias do horizonte
            // encurtaria o dia de estudo e inflaria todo piso de quem estuda tres noites por semana.
            List<AvailabilityWindow> mesmoDia = AvailabilityWindows.of(List.of(
                    PlanRequests.slot("2026-09-01T09:00:00Z", "2026-09-01T12:00:00Z"),
                    PlanRequests.slot("2026-09-01T14:00:00Z", "2026-09-01T17:00:00Z")));
            List<AvailabilityWindow> doisDias = AvailabilityWindows.of(List.of(
                    PlanRequests.slot("2026-09-01T09:00:00Z", "2026-09-01T12:00:00Z"),
                    PlanRequests.slot("2026-09-08T09:00:00Z", "2026-09-08T12:00:00Z")));

            assertThat(SinapseMinimumDays.minutesPerStudyDay(mesmoDia)).isEqualTo(360.0);
            assertThat(SinapseMinimumDays.minutesPerStudyDay(doisDias)).isEqualTo(180.0);
        }

        @Test
        @DisplayName("sem disponibilidade, o piso nao explode: cai no minimo absoluto")
        void semDisponibilidadeCaiNoMinimo() {
            assertThat(SinapseMinimumDays.minutesPerStudyDay(List.of())).isEqualTo(1.0);
            assertThat(SinapseMinimumDays.sessionsFor(0, 1.0))
                    .isEqualTo(SinapseMinimumDays.ABSOLUTE_FLOOR);
        }
    }
}
