package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationInstance;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.coreapi.contract.SessionKind;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * O que a medição de curadoria (EOA-1) acrescenta ao grupo (b), e por quê cada número existe.
 *
 * <h2>Uma régua que não depende do nível que está sendo medido</h2>
 *
 * {@code soft_inversions} conta só as arestas {@code SOFT}, então muda de significado entre os níveis
 * do eixo de força: sob {@code all-hard} ela é zero porque não há aresta {@code SOFT}, não porque o
 * plano respeite alguma coisa. Comparar a coluna entre níveis compararia denominadores. As colunas
 * {@code order_inversions_*} contam sobre <b>todas</b> as arestas do pedido, de qualquer força, com a
 * mesma definição nos três níveis — a pergunta "o plano estuda o dependente antes do pré-requisito?"
 * feita da mesma maneira, classifique-se a aresta como for.
 *
 * <p>A definição é do plano, não da ordem de estudo: uma aresta está invertida quando os dois tópicos
 * foram agendados e a primeira sessão do dependente começa antes da primeira sessão do pré-requisito.
 * Por isso ela difere de {@code soft_inversions}, que o Core conta sobre a ordem inteira, inclusive
 * tópicos que não couberam. A leitura forte de {@code HARD} ({@code Invariants}) implica esta, então sob
 * {@code all-hard} ela tem de ser zero — e é verificada, não suposta.
 *
 * <p>{@code dependents_without_prerequisite} é a consequência mais crua de classificar uma aresta como
 * preferência: o dependente entrou no plano e o pré-requisito não entrou. Sob {@code HARD} é violação
 * de invariante e a linha nem existiria.
 *
 * <h2>O hash do plano</h2>
 *
 * Um resumo das sessões (tópico, tipo, início, duração, na ordem de {@code sequenceIndex}). Duas linhas
 * com o mesmo hash têm o mesmo cronograma. É o que permite afirmar "o nível não mudou o plano" sem
 * depender de uma métrica escolhida: se o hash é igual, nenhuma métrica pode diferir.
 *
 * @param planHash                       16 hexadecimais do SHA-256 das sessões
 * @param orderInversions                arestas, de qualquer força, invertidas no plano
 * @param orderPairsScheduled            arestas com os dois tópicos agendados: o denominador
 * @param dependentsWithoutPrerequisite  arestas cujo dependente entrou e o pré-requisito não
 * @param studySessions                  sessões {@code STUDY}
 * @param revisionSessions               sessões {@code REVISION}
 * @param scheduledByTier                tópicos agendados por faixa <b>latente</b>, na ordem de
 *                                       {@code EffortMapping.TIERS}
 * @param importanceCv                   coeficiente de variação da importância por tópico
 * @param minutesCv                      coeficiente de variação de {@code estimatedMinutes}
 */
public record CurationMetrics(
        String planHash,
        int orderInversions,
        int orderPairsScheduled,
        int dependentsWithoutPrerequisite,
        int studySessions,
        int revisionSessions,
        int[] scheduledByTier,
        double importanceCv,
        double minutesCv) {

    /**
     * Lê as métricas de uma execução.
     *
     * @param instance   a instância, para as arestas e a faixa latente
     * @param response   a resposta
     * @param importance a importância por tópico como a estratégia de produção a resolveu
     * @return as métricas
     */
    public static CurationMetrics of(CurationInstance instance, PlanResponse response,
            Collection<Double> importance) {

        PlanRequest request = instance.benchmark().request();
        Map<UUID, Instant> firstStart = firstStarts(response);
        int inverted = 0;
        int pairs = 0;
        int orphaned = 0;
        for (PlanRequest.PrerequisiteEdge edge : request.prerequisites()) {
            Instant prerequisite = firstStart.get(edge.prerequisiteTopicId());
            Instant dependent = firstStart.get(edge.dependentTopicId());
            if (dependent == null) {
                continue;
            }
            if (prerequisite == null) {
                orphaned++;
                continue;
            }
            pairs++;
            if (dependent.isBefore(prerequisite)) {
                inverted++;
            }
        }
        int[] byTier = new int[4];
        firstStart.keySet().forEach(id -> byTier[instance.latentTier().get(id)]++);
        int study = (int) response.sessions().stream()
                .filter(session -> session.kind() == SessionKind.STUDY).count();
        return new CurationMetrics(hash(response), inverted, pairs, orphaned, study,
                response.sessions().size() - study, byTier, coefficientOfVariation(importance),
                coefficientOfVariation(request.topics().stream()
                        .map(topic -> (double) topic.estimatedMinutes()).toList()));
    }

    /**
     * Desvio-padrão populacional sobre a média.
     *
     * <p>Escolhido por ser <b>invariante de escala</b>: o Core normaliza a importância no simplex antes
     * de qualquer termo lê-la, então a mesma dispersão relativa tem de dar o mesmo número com 35 ou 105
     * tópicos, com prioridades {@code 4/3} ou {@code 8/6}. Desvio-padrão bruto não teria essa
     * propriedade; o da importância normalizada encolheria com o número de tópicos.
     *
     * @param values os valores
     * @return o coeficiente, 0 quando todos são iguais ou não há valor
     */
    public static double coefficientOfVariation(Collection<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        if (mean == 0.0) {
            return 0.0;
        }
        double variance = values.stream()
                .mapToDouble(value -> (value - mean) * (value - mean)).average().orElse(0.0);
        return Math.sqrt(variance) / mean;
    }

    private static Map<UUID, Instant> firstStarts(PlanResponse response) {
        Map<UUID, Instant> first = new HashMap<>();
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            first.merge(session.topicId(), session.scheduledStart(),
                    (a, b) -> b.isBefore(a) ? b : a);
        }
        return first;
    }

    /** As sessões, e só elas, resumidas. O {@code fitness} fica de fora: não é cronograma. */
    private static String hash(PlanResponse response) {
        StringBuilder text = new StringBuilder();
        response.sessions().stream()
                .sorted(java.util.Comparator.comparingInt(PlanResponse.ScheduledSession::sequenceIndex))
                .forEach(session -> text.append(session.topicId()).append('|')
                        .append(session.kind()).append('|')
                        .append(session.scheduledStart()).append('|')
                        .append(session.durationMinutes()).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is mandatory in every JDK", impossible);
        }
    }
}
