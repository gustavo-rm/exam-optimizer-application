package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * As invariantes de saída do protocolo, exigidas de <b>todo</b> motor registrado.
 *
 * <h2>Um teste parametrizado, e não um por motor</h2>
 *
 * As doze propriedades são do <b>protocolo</b>, não de um algoritmo: a plataforma checa oito delas e
 * recusa a resposta com {@code CoreProtocolException}, e as outras quatro ninguém checa depois daqui.
 * Escrever um teste por motor deixaria as duas listas divergirem — a experiência de repositório é que
 * a cópia mais nova recebe a asserção nova e a antiga não —, e o motor que entrasse depois nasceria
 * sem nenhuma.
 *
 * <p>Parametrizar pelo motor inverte o padrão: um motor novo aparece na fonte de argumentos e
 * <b>herda a suíte inteira</b>. Um motor que quebre qualquer propriedade reprova aqui, com o nome do
 * motor na mensagem.
 *
 * <p>As asserções são as de {@link PlanInvariantAssertions}, derivadas do levantamento e escritas
 * independentemente de {@link PlanOutputInvariants} — chamar a checagem de produção provaria apenas
 * que ela concorda consigo mesma.
 *
 * <h2>Cobertura por caso, não só por motor</h2>
 *
 * Cada motor roda contra o mesmo conjunto de requisições: a nominal, uma com pré-requisito duro
 * relevante, uma com histórico, uma com disponibilidade apertada o suficiente para truncar o plano, e
 * uma com uma única janela. O plano parcial é o caso interessante — é onde a propriedade "o conjunto
 * agendado é fechado sob pré-requisitos" pode falhar sem que nada mais pareça errado.
 */
@DisplayName("Motores de plano: as invariantes de saida valem para todos")
class PlanEngineInvariantTest {

    /** Cada motor registrado, com o nome que aparece na mensagem de falha. */
    private static Stream<PlanEngines.Case> engines() {
        return PlanEngines.all().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("o plano nominal satisfaz as doze invariantes e a contiguidade")
    void oPlanoNominalSatisfazTodasAsInvariantes(PlanEngines.Case engine) {
        PlanRequest request = PlanRequests.builder().build();
        PlanResponse response = engine.plan(request);

        PlanInvariantAssertions.assertEveryInvariant(request, response);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("com pre-requisito duro, a ordem entregue o respeita")
    void comPreRequisitoDuroAOrdemORespeita(PlanEngines.Case engine) {
        PlanRequest request = PlanRequests.builder()
                .withPrerequisites(List.of(
                        PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                        PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_4)))
                .build();

        PlanInvariantAssertions.assertEveryInvariant(request, engine.plan(request));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("com historico de revisoes, as invariantes continuam valendo")
    void comHistoricoAsInvariantesContinuamValendo(PlanEngines.Case engine) {
        PlanRequest request = PlanRequests.builder()
                .withHistory(List.of(
                        PlanRequests.studied(PlanRequests.TOPIC_1, "2026-08-20T20:00:00Z",
                                com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating.GOOD),
                        PlanRequests.studied(PlanRequests.TOPIC_3, "2026-08-28T20:00:00Z",
                                com.ia.project.dynamicstudyplanner.coreapi.contract.RecallRating.AGAIN)))
                .build();

        PlanInvariantAssertions.assertEveryInvariant(request, engine.plan(request));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("plano parcial: o que cabe continua fechado sob pre-requisitos")
    void planoParcialContinuaFechadoSobPreRequisitos(PlanEngines.Case engine) {
        // Uma janela de duas horas para 378 minutos de conteudo: o plano tem de truncar. E o caso em
        // que "pular para o que ainda cabe" produziria um calendario mais cheio e invalido.
        PlanRequest request = PlanRequests.builder()
                .withAvailability(List.of(
                        PlanRequests.slot("2026-09-01T19:00:00Z", "2026-09-01T21:00:00Z")))
                .withPrerequisites(List.of(
                        PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                        PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_3),
                        PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_4)))
                .build();

        PlanResponse response = engine.plan(request);

        PlanInvariantAssertions.assertEveryInvariant(request, response);
        assertThat(response.sessions())
                .as("%s: a janela nao cabe o edital inteiro, entao o plano tem de ser um prefixo",
                        engine)
                .hasSizeLessThan(8);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("engines")
    @DisplayName("o motor declara a si mesmo no mapa de fitness")
    void oMotorDeclaraASiMesmo(PlanEngines.Case engine) {
        PlanResponse response = engine.plan(PlanRequests.builder().build());

        assertThat(response.fitness())
                .as("%s: um plano guardado sem atribuicao nao serve de dado experimental", engine)
                .containsEntry(PlanEngine.FITNESS_ENGINE_KEY, engine.id());
    }
}
