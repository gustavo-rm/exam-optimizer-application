package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mesma requisição, mesma semente, mesmo motor: mesmo plano — inclusive em outra thread.
 *
 * <h2>Por que duas threads, e não duas chamadas</h2>
 *
 * {@code RandomProvider} guarda um {@code Random} <b>por thread</b>. Duas chamadas seguidas na mesma
 * thread compartilham essa fonte, então provariam pouco: um motor que esquecesse de semear passaria,
 * porque a segunda chamada herdaria a fonte que a primeira deixou. Em threads distintas cada uma
 * começa com a fonte inicial da sua própria thread, e só um motor que instala a semente que recebeu
 * produz o mesmo plano nas duas.
 *
 * <p>A contraprova do outro lado é a restauração: o endpoint roda num pool, e uma semente deixada
 * instalada faria a requisição seguinte naquela thread reproduzir o plano desta. Por isso a terceira
 * asserção — depois de planejar com semente, uma execução <b>sem</b> semear na mesma thread não
 * herda nada dela.
 *
 * <h2>Sementes diferentes têm de divergir</h2>
 *
 * Sem essa contraprova, um motor que ignorasse a aleatoriedade por completo passaria no teste de
 * reprodutibilidade. É exigida só do motor genético: o guloso é determinístico por construção e não
 * lê semente alguma, o que é a propriedade dele — e é verificada como tal.
 */
@DisplayName("Motores de plano: reprodutibilidade")
class PlanEngineDeterminismTest {

    private static Stream<PlanEngines.Case> engines() {
        return PlanEngines.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("o plano e identico em duas threads diferentes")
    void oPlanoEhIdenticoEmDuasThreads(PlanEngines.Case engine) throws Exception {
        PlanRequest request = PlanRequests.builder().build();

        List<String> assinaturas = emDuasThreads(
                () -> signature(engine.plan(request)),
                () -> signature(engine.plan(request)));

        assertThat(assinaturas.get(1))
                .as("%s: a mesma requisicao semeada tem de dar o mesmo plano em qualquer thread",
                        engine)
                .isEqualTo(assinaturas.get(0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("a semente nao vaza para a execucao seguinte na mesma thread")
    void aSementeNaoVazaParaAExecucaoSeguinte(PlanEngines.Case engine) throws Exception {
        PlanRequest semeada = PlanRequests.builder().withSeed(999_777L).build();
        PlanRequest outra = PlanRequests.builder().withSeed(111_222L).build();

        // Numa thread limpa: planeja a "outra" sozinha. Noutra: planeja a semeada primeiro e a
        // "outra" depois. Se a semente de 999777 ficasse instalada, a segunda leitura divergiria.
        List<String> assinaturas = emDuasThreads(
                () -> signature(engine.plan(outra)),
                () -> {
                    engine.plan(semeada);
                    return signature(engine.plan(outra));
                });

        assertThat(assinaturas.get(1))
                .as("%s: a fonte tem de ser restaurada, senao o pool contamina a requisicao seguinte",
                        engine)
                .isEqualTo(assinaturas.get(0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("a semente chega a busca: ela desempata otimos simetricos")
    void aSementeChegaABusca(PlanEngines.Case engine) {
        // Todos os tópicos em STANDARD e prioridades de meta iguais por disciplina: dois deles ficam
        // INDISTINGUÍVEIS para a fitness — mesma faixa, mesma importância —, e a alocação ótima é
        // degenerada. Qual dos dois recebe a sessão extra só pode ser decidido pelo sorteio, então
        // duas sementes que produzam o mesmo plano aqui provam que a semente não chegou à busca.
        String comUma = signature(engine.plan(comFolga().withSeed(1L).build()));
        String comOutra = signature(engine.plan(comFolga().withSeed(2L).build()));

        if (engine.id().equals(GeneticPlanEngine.ID)) {
            assertThat(comOutra)
                    .as("um AG que ignorasse a semente passaria no teste de reprodutibilidade")
                    .isNotEqualTo(comUma);
        } else {
            assertThat(comOutra)
                    .as("o guloso e deterministico por construcao: a semente nao o afeta")
                    .isEqualTo(comUma);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("com otimo unico, sementes diferentes convergem para o mesmo plano")
    void comOtimoUnicoSementesConvergem(PlanEngines.Case engine) {
        // A contraparte do teste acima, e a razao de ele precisar de uma instancia degenerada.
        // Com faixas de esforco distintas o otimo deixa de ser simetrico, e a busca o encontra a
        // partir de qualquer semente. Isso NAO e defeito de semeadura: e a propriedade que se quer
        // de uma busca — o resultado e do problema, nao do sorteio. Fica travado para que ninguem
        // "corrija" a convergencia mais tarde tomando-a por um bug.
        String comUma = signature(engine.plan(comFaixasDistintas().withSeed(1L).build()));
        String comOutra = signature(engine.plan(comFaixasDistintas().withSeed(2L).build()));

        assertThat(comOutra)
                .as("%s: com otimo unico o plano e do problema, nao da semente", engine)
                .isEqualTo(comUma);
    }

    /**
     * Uma requisição com folga real no calendário.
     *
     * <h2>Por que a contraprova precisa de folga, e o que isso revela</h2>
     *
     * Na requisição nominal a disponibilidade são 420 minutos e o primeiro passe do edital custa
     * 378. O orçamento de sessões do cromossomo é o número de sessões de duração média que caibam,
     * com piso de uma por tópico — e nesse caso os dois batem em 4. <b>O cromossomo fica sem grau de
     * liberdade algum</b>: existe uma única alocação viável, e toda semente a encontra.
     *
     * <p>Isso não é um defeito nem do algoritmo nem da semeadura, e vale registrar como propriedade:
     * <b>quando o calendário quase não cobre o conteúdo, os dois motores necessariamente concordam</b>,
     * porque não há escolha a fazer. A diferença entre as duas condições do experimento só aparece
     * onde há folga para distribuir. Uma contraprova rodada sobre a requisição nominal passaria
     * vacuamente com qualquer implementação, inclusive uma que ignorasse a semente.
     *
     * <p>Dez janelas de três horas contra os mesmos 378 minutos de primeiro passe dão ~19 sessões
     * para 4 tópicos: as revisões passam a ser distribuíveis, e é aí que a busca decide algo.
     */
    private static PlanRequests.Builder comFolga() {
        List<PlanRequest.AvailabilitySlot> generosa = new java.util.ArrayList<>();
        for (int day = 1; day <= 10; day++) {
            String date = String.format("2026-09-%02d", day);
            generosa.add(PlanRequests.slot(date + "T09:00:00Z", date + "T12:00:00Z"));
        }
        return PlanRequests.builder().withAvailability(List.copyOf(generosa));
    }

    /**
     * A mesma folga, com faixas de esforço distintas por tópico.
     *
     * <p>Faixas diferentes e prioridades diferentes tiram a simetria: existe um único ótimo, e a
     * busca o encontra a partir de qualquer semente. É a instância em que a convergência é a
     * resposta certa, e é por isso que a prova de que a semente chega à busca precisa da outra.
     */
    private static PlanRequests.Builder comFaixasDistintas() {
        return comFolga().withTopics(List.of(
                PlanRequests.topic(PlanRequests.TOPIC_1, PlanRequests.SUBJECT_FIRST,
                        1, 30, "SHORT"),
                PlanRequests.topic(PlanRequests.TOPIC_2, PlanRequests.SUBJECT_FIRST,
                        2, 60, "STANDARD"),
                PlanRequests.topic(PlanRequests.TOPIC_3, PlanRequests.SUBJECT_SECOND,
                        1, 108, "LONG"),
                PlanRequests.topic(PlanRequests.TOPIC_4, PlanRequests.SUBJECT_SECOND,
                        2, 180, "EXTENDED")));
    }

    /** Roda duas tarefas em duas threads distintas e devolve os resultados na ordem dada. */
    private static List<String> emDuasThreads(Callable<String> first, Callable<String> second)
            throws Exception {

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> one = pool.submit(first);
            Future<String> two = pool.submit(second);
            return List.of(one.get(), two.get());
        } finally {
            pool.shutdownNow();
        }
    }

    /** O plano inteiro como texto: tópico, tipo, início, duração e índice de cada sessão. */
    private static String signature(PlanResponse response) {
        StringBuilder signature = new StringBuilder();
        for (PlanResponse.ScheduledSession session : response.sessions()) {
            signature.append(session.sequenceIndex()).append(':')
                    .append(session.topicId()).append(':')
                    .append(session.kind()).append(':')
                    .append(session.scheduledStart()).append(':')
                    .append(session.durationMinutes()).append('|');
        }
        return signature.toString();
    }
}
