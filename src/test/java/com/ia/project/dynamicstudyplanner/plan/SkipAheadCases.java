package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.SUBJECT_FIRST;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_1;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_2;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_3;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.TOPIC_4;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.goal;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.hard;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.slot;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.studied;
import static com.ia.project.dynamicstudyplanner.plan.PlanRequests.topic;

/**
 * Os casos pequenos e determinísticos da opção P (EOA-12b), os mesmos para os três motores.
 *
 * <p>Uma matéria só, sem histórico salvo onde dito, e as posições curriculares na ordem desejada:
 * assim a ordem do guloso é a das posições, e os testes dos dois genéticos passam a mesma ordem
 * explicitamente. Duas janelas de 60 minutos, em dias diferentes, salvo onde dito.
 *
 * <ul>
 *   <li>(a) {@link #neverFitsInTheMiddle}: 30, <b>90</b>, 30 minutos. O de 90 nunca cabe.</li>
 *   <li>(b) {@link #skippedHasHardDependent}: 30, <b>90</b>, 30 (dependente {@code HARD} do de 90),
 *       30.</li>
 *   <li>(c) {@link #failureKeepsTheWindow}: 40, <b>70</b>, 20. O de 20 tem de usar os 20 minutos que
 *       sobraram na primeira janela, e não ir para a segunda.</li>
 *   <li>(d) {@link #everythingFits}: 30, 30, 30.</li>
 * </ul>
 */
public final class SkipAheadCases {

    /** Primeira janela: 2026-09-01, das 19h às 20h. */
    public static final Instant FIRST_WINDOW = Instant.parse("2026-09-01T19:00:00Z");

    /** Segunda janela: 2026-09-03, das 19h às 20h. */
    public static final Instant SECOND_WINDOW = Instant.parse("2026-09-03T19:00:00Z");

    private static final List<PlanRequest.AvailabilitySlot> TWO_HOURS = List.of(
            slot("2026-09-01T19:00:00Z", "2026-09-01T20:00:00Z"),
            slot("2026-09-03T19:00:00Z", "2026-09-03T20:00:00Z"));

    private static final List<PlanRequest.AvailabilitySlot> ONE_HOUR = List.of(
            slot("2026-09-01T19:00:00Z", "2026-09-01T20:00:00Z"));

    private SkipAheadCases() {
    }

    /** (a) Um tópico que nunca cabe no meio da ordem. */
    public static PlanRequest neverFitsInTheMiddle() {
        return request(TWO_HOURS, List.of(
                topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                topic(TOPIC_2, SUBJECT_FIRST, 2, 90),
                topic(TOPIC_3, SUBJECT_FIRST, 3, 30)), List.of());
    }

    /** (b) O tópico pulado tem um dependente {@code HARD}; um quarto tópico independente cabe. */
    public static PlanRequest skippedHasHardDependent() {
        return request(TWO_HOURS, List.of(
                topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                topic(TOPIC_2, SUBJECT_FIRST, 2, 90),
                topic(TOPIC_3, SUBJECT_FIRST, 3, 30),
                topic(TOPIC_4, SUBJECT_FIRST, 4, 30)), List.of(hard(TOPIC_2, TOPIC_3)));
    }

    /** (c) Uma falha não consome janela. */
    public static PlanRequest failureKeepsTheWindow() {
        return request(TWO_HOURS, List.of(
                topic(TOPIC_1, SUBJECT_FIRST, 1, 40),
                topic(TOPIC_2, SUBJECT_FIRST, 2, 70),
                topic(TOPIC_3, SUBJECT_FIRST, 3, 20)), List.of());
    }

    /**
     * (c) no guloso, para a unidade de tentativa dele: estudo e revisão juntos ou nenhum.
     *
     * <p>Uma janela de 60 minutos. 10 minutos; depois 50 com revisão de 25 concedida pelo histórico
     * (estudado há mais de 14 dias e lembrado com {@code AGAIN}); depois 10. O estudo de 50 cabe e a
     * revisão não: sob P o estudo é devolvido, e o de 10 seguinte começa onde o de 50 começaria.
     */
    public static PlanRequest revisionDoesNotFit() {
        return PlanRequests.builder()
                .withAvailability(ONE_HOUR)
                .withGoals(List.of(goal(SUBJECT_FIRST, "2026-09-20", 5)))
                .withTopics(List.of(
                        topic(TOPIC_1, SUBJECT_FIRST, 1, 10),
                        topic(TOPIC_2, SUBJECT_FIRST, 2, 50),
                        topic(TOPIC_3, SUBJECT_FIRST, 3, 10)))
                .withPrerequisites(List.of())
                .withHistory(List.of(studied(TOPIC_2, "2026-08-01T00:00:00Z", RecallRating.AGAIN)))
                .build();
    }

    /** (d) Tudo cabe. */
    public static PlanRequest everythingFits() {
        return request(TWO_HOURS, List.of(
                topic(TOPIC_1, SUBJECT_FIRST, 1, 30),
                topic(TOPIC_2, SUBJECT_FIRST, 2, 30),
                topic(TOPIC_3, SUBJECT_FIRST, 3, 30)), List.of(hard(TOPIC_1, TOPIC_3)));
    }

    /**
     * Uma janela de 60 minutos, para a revisão discricionária dos dois genéticos: 50 minutos com
     * duas sessões (estudo de 50 e revisão de 25), depois 10.
     */
    public static PlanRequest oneHour(int firstMinutes, int secondMinutes) {
        return request(ONE_HOUR, List.of(
                topic(TOPIC_1, SUBJECT_FIRST, 1, firstMinutes),
                topic(TOPIC_2, SUBJECT_FIRST, 2, secondMinutes)), List.of());
    }

    /** @return os tópicos do pedido, na ordem de posição, que é a ordem de colocação dos casos */
    public static List<UUID> ids(PlanRequest request) {
        return request.topics().stream().map(PlanRequest.Topic::id).toList();
    }

    private static PlanRequest request(List<PlanRequest.AvailabilitySlot> availability,
            List<PlanRequest.Topic> topics, List<PlanRequest.PrerequisiteEdge> edges) {

        return PlanRequests.builder()
                .withAvailability(availability)
                .withGoals(List.of(goal(SUBJECT_FIRST, "2026-09-20", 5)))
                .withTopics(topics)
                .withPrerequisites(edges)
                .build();
    }
}
