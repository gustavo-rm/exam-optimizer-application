package com.ia.project.dynamicstudyplanner.sinapse.importance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.plan.PlanRequests;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * As duas estratégias produzem valores na mesma escala normalizada, pelo mesmo procedimento.
 *
 * <h2>Por que isto precisa de teste próprio</h2>
 *
 * As escalas <b>brutas</b> são incomparáveis de propósito: prioridade vai de 1 a 5, centralidade vai
 * de 1 a n. Se as duas condições produzissem fitness em escalas diferentes, os números das duas
 * execuções não se comparariam — e comparar as duas é a razão de ambas existirem.
 *
 * <p>O que as torna comparáveis é haver <b>um único procedimento de normalização</b>:
 * {@code EvolutionContext.normalize} projeta o que chegar no simplex unitário. As estratégias
 * devolvem valor bruto e não normalizam nada; se uma delas normalizasse por conta própria, a outra
 * passaria a ser medida contra outra escala e nada acusaria.
 *
 * <p>Este arquivo é parametrizado por estratégia, então uma estratégia nova herda as asserções em
 * vez de nascer sem nenhuma.
 */
@DisplayName("Importancia: as duas estrategias na mesma escala normalizada")
class ImportanceScaleTest {

    private static Stream<ImportanceStrategy> strategies() {
        return Stream.of(new GoalPriorityImportance(), new PrerequisiteCentralityImportance());
    }

    /** Um edital com arestas HARD, para a centralidade ter o que medir. */
    private static PlanRequest request() {
        return PlanRequests.builder()
                .withPrerequisites(List.of(
                        PlanRequests.hard(PlanRequests.TOPIC_1, PlanRequests.TOPIC_2),
                        PlanRequests.hard(PlanRequests.TOPIC_2, PlanRequests.TOPIC_3),
                        PlanRequests.hard(PlanRequests.TOPIC_3, PlanRequests.TOPIC_4)))
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    @DisplayName("o valor bruto e positivo para todo topico: nenhum nasce invisivel")
    void oValorBrutoEhPositivoParaTodoTopico(ImportanceStrategy strategy) {
        Map<PlanningItem, Double> raw = strategy.importanceOf(request());

        assertThat(raw).hasSize(request().topics().size());
        assertThat(raw.values())
                .as("%s: importancia zero deixaria o topico agendado e invisivel para a fitness",
                        strategy.id())
                .allSatisfy(value -> assertThat(value).isPositive());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    @DisplayName("depois do procedimento unico de normalizacao, os pesos somam 1")
    void depoisDaNormalizacaoOsPesosSomam1(ImportanceStrategy strategy) {
        Map<PlanningItem, Double> normalized =
                EvolutionContext.normalize(strategy.importanceOf(request()));

        assertThat(normalized.values().stream().mapToDouble(Double::doubleValue).sum())
                .as("%s: sem isso os fitness das duas condicoes nao sao comparaveis", strategy.id())
                .isCloseTo(1.0, within(1e-12));
        assertThat(normalized.values())
                .allSatisfy(weight -> assertThat(weight).isBetween(0.0, 1.0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    @DisplayName("a estrategia NAO normaliza por conta propria: o bruto e mesmo bruto")
    void aEstrategiaNaoNormalizaPorContaPropria(ImportanceStrategy strategy) {
        // A contraprova de que o procedimento e unico. Se uma estrategia ja devolvesse valores
        // somando 1, ela teria embutido a normalizacao — e a outra passaria a ser medida contra uma
        // escala diferente sem que nada acusasse.
        double rawSum = strategy.importanceOf(request()).values().stream()
                .mapToDouble(Double::doubleValue).sum();

        assertThat(rawSum)
                .as("%s: normalizar aqui e o unico jeito de as duas escalas divergirem", strategy.id())
                .isGreaterThan(1.0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    @DisplayName("a normalizacao e invariante a escala: dobrar o bruto nao muda o peso")
    void aNormalizacaoEhInvarianteAEscala(ImportanceStrategy strategy) {
        Map<PlanningItem, Double> raw = strategy.importanceOf(request());
        Map<PlanningItem, Double> doubled = new java.util.LinkedHashMap<>();
        raw.forEach((item, value) -> doubled.put(item, value * 2.0));

        Map<PlanningItem, Double> fromRaw = EvolutionContext.normalize(raw);
        Map<PlanningItem, Double> fromDoubled = EvolutionContext.normalize(doubled);

        // E o que permite que uma estrategia conte de 1 a 5 e a outra de 1 a n sem que a unidade
        // importe: so a PROPORCAO entre topicos sobrevive a normalizacao.
        fromRaw.forEach((item, weight) -> assertThat(fromDoubled.get(item))
                .as("%s: %s", strategy.id(), item.id())
                .isCloseTo(weight, within(1e-12)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strategies")
    @DisplayName("os itens sao os mesmos nas duas estrategias, na ordem de chegada")
    void osItensSaoOsMesmosNaOrdemDeChegada(ImportanceStrategy strategy) {
        assertThat(strategy.importanceOf(request()).keySet())
                .as("%s: a ordem do mapa entra na soma de ponto flutuante de normalize", strategy.id())
                .containsExactlyElementsOf(
                        new GoalPriorityImportance().importanceOf(request()).keySet());
    }
}
