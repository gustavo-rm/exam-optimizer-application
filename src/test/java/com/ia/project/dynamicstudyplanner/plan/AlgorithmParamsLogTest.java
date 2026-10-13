package com.ia.project.dynamicstudyplanner.plan;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticEngineParameters;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticPlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.TimelineEngineParameters;
import com.ia.project.dynamicstudyplanner.sinapse.TimelinePlanEngine;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineSearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

/**
 * O que {@link AlgorithmParamsLog} registra, e o que ele não pode registrar.
 *
 * <p>Os parâmetros efetivos vêm de um orçamento escolhido aqui (7 gerações, 11 indivíduos), e não do
 * de produção, para que a asserção prove que o valor é LIDO do orçamento e não escrito em algum lugar.
 */
@DisplayName("algorithmParams: chaves ignoradas e parametros efetivos no log")
class AlgorithmParamsLogTest {

    private static final GeneticSearchBudget BUDGET = new GeneticSearchBudget(7, 11);

    private final AlgorithmParamsLog paramsLog = new AlgorithmParamsLog(List.of(
            new GeneticEngineParameters(BUDGET),
            new TimelineEngineParameters(BUDGET)));

    private Logger logger;
    private ListAppender<ILoggingEvent> coletor;

    @BeforeEach
    void anexarColetor() {
        logger = (Logger) LoggerFactory.getLogger(AlgorithmParamsLog.class);
        coletor = new ListAppender<>();
        coletor.start();
        logger.addAppender(coletor);
        logger.setLevel(Level.TRACE);
    }

    @AfterEach
    void removerColetor() {
        logger.detachAppender(coletor);
        coletor.stop();
        logger.setLevel(null);
    }

    private static PlanRequest withParams(Map<String, Object> params) {
        PlanRequest base = PlanRequests.builder().build();
        return new PlanRequest(base.contractVersion(), base.horizon(), base.availability(),
                base.goals(), base.topics(), base.prerequisites(), base.history(),
                params, base.randomSeed());
    }

    private List<ILoggingEvent> eventos(Level nivel) {
        return coletor.list.stream().filter(evento -> evento.getLevel() == nivel).toList();
    }

    @Test
    @DisplayName("o conjunto fechado e exatamente as quatro chaves que o codigo le")
    void oConjuntoFechadoEhAsQuatroChavesLidas() {
        assertThat(AlgorithmParamsLog.IMPORTANCE_PARAM).isEqualTo(ImportanceStrategies.PARAM);
        assertThat(AlgorithmParamsLog.APPLIED_KEYS).isEqualTo(Set.of(
                PlanEngineSelector.ENGINE_PARAM, ImportanceStrategies.PARAM,
                PrecedencePolicy.PARAM, EdgeProvenanceFilter.PARAM));
    }

    @Test
    @DisplayName("as tres chaves que a plataforma envia saem no WARN pelo nome, nunca pelo valor")
    void chavesIgnoradasSaemPeloNome() {
        PlanRequest request = withParams(Map.of("generations", 400, "population-size", 120,
                "mutation-rate", 0.0517, PlanEngineSelector.ENGINE_PARAM, GeneticPlanEngine.ID));

        paramsLog.record(request, GeneticPlanEngine.ID);

        assertThat(paramsLog.ignoredKeys(request))
                .containsExactly("generations", "mutation-rate", "population-size");
        List<ILoggingEvent> warns = eventos(Level.WARN);
        assertThat(warns).hasSize(1);
        String aviso = warns.get(0).getFormattedMessage();
        assertThat(aviso).contains("generations", "mutation-rate", "population-size")
                .as("os valores sao entrada de quem chama e nao vao para o agregador")
                .doesNotContain("400", "120", "0.0517");
    }

    @Test
    @DisplayName("so as chaves aplicadas: nenhum WARN, e o INFO continua")
    void semChaveIgnoradaNaoHaWarn() {
        paramsLog.record(withParams(Map.of(PlanEngineSelector.ENGINE_PARAM, GeneticPlanEngine.ID,
                PrecedencePolicy.PARAM, "weighted")), GeneticPlanEngine.ID);

        assertThat(eventos(Level.WARN)).isEmpty();
        assertThat(eventos(Level.INFO)).hasSize(1);
    }

    @Test
    @DisplayName("o INFO traz o motor e os parametros lidos do orcamento e das constantes da busca")
    void oInfoTrazOsParametrosEfetivos() {
        paramsLog.record(withParams(Map.of()), GeneticPlanEngine.ID);

        String info = eventos(Level.INFO).get(0).getFormattedMessage();
        assertThat(info).contains(GeneticPlanEngine.ID, "generations=7", "population-size=11",
                "mutation-rate=" + DefaultGeneticAlgorithmFactory.MUTATION_RATE);
    }

    @Test
    @DisplayName("ga: orcamento configurado e as taxas da fabrica, nada vindo da requisicao")
    void parametrosEfetivosDoGa() {
        assertThat(paramsLog.effectiveParameters(GeneticPlanEngine.ID)).containsExactly(
                entry("generations", 7),
                entry("population-size", 11),
                entry("crossover-rate", DefaultGeneticAlgorithmFactory.CROSSOVER_RATE),
                entry("mutation-rate", DefaultGeneticAlgorithmFactory.MUTATION_RATE),
                entry("elitism", DefaultGeneticAlgorithmFactory.ELITISM),
                entry("stagnation-patience", DefaultGeneticAlgorithmFactory.STAGNATION_PATIENCE),
                entry("hypermutation-rate", DefaultGeneticAlgorithmFactory.HYPERMUTATION_RATE));
    }

    @Test
    @DisplayName("ga-timeline: o mesmo orcamento e as taxas da busca de linha do tempo")
    void parametrosEfetivosDaLinhaDoTempo() {
        assertThat(paramsLog.effectiveParameters(TimelinePlanEngine.ID)).containsExactly(
                entry("generations", 7),
                entry("population-size", 11),
                entry("crossover-rate", TimelineSearch.CROSSOVER_RATE),
                entry("mutation-rate", TimelineSearch.MUTATION_RATE));
    }

    @Test
    @DisplayName("o guloso nao tem parametro de busca: o INFO diz isso com um mapa vazio")
    void oGulosoNaoTemParametroDeBusca() {
        paramsLog.record(withParams(Map.of()), GreedyBaselineEngine.ID);

        assertThat(paramsLog.effectiveParameters(GreedyBaselineEngine.ID)).isEmpty();
        assertThat(eventos(Level.INFO).get(0).getFormattedMessage())
                .contains(GreedyBaselineEngine.ID, "{}");
    }

    @Test
    @DisplayName("dois conjuntos de parametros para o mesmo motor impedem a subida")
    void parametrosDuplicadosFalham() {
        assertThatThrownBy(() -> new AlgorithmParamsLog(List.of(
                new GeneticEngineParameters(BUDGET), new GeneticEngineParameters(BUDGET))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("o seletor registra as duas linhas, e o plano e o mesmo do motor chamado direto")
    void oSeletorRegistraSemMudarOPlano() {
        PlanRequest request = withParams(Map.of("generations", 400));

        PlanResponse viaSeletor = PlanEngines.selector().plan(request);
        PlanResponse direto = PlanEngines.greedy().plan(request);

        assertThat(eventos(Level.WARN)).hasSize(1);
        assertThat(eventos(Level.INFO)).hasSize(1);
        assertThat(viaSeletor.sessions()).isEqualTo(direto.sessions());
        assertThat(viaSeletor.metadata()).isEqualTo(direto.metadata());
    }
}
