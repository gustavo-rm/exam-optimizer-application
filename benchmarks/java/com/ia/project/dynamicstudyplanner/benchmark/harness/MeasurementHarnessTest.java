package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.BenchmarkInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.InstanceLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.metric.Invariants;
import com.ia.project.dynamicstudyplanner.benchmark.metric.MeasurementRow;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.retention.RetentionAlgorithm;
import com.ia.project.dynamicstudyplanner.ga.fitness.FitnessComposition;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste rápido do instrumento: ele roda, ele reprova quando deve, e a biblioteca é determinística.
 *
 * <h2>O que este teste é e o que ele não é</h2>
 *
 * A medição completa — 24 instâncias, 6 condições, 5 sementes — é manual, roda por
 * {@link MeasurementMain} e leva alguns segundos. Este teste não a repete. Ele pega <b>duas</b>
 * instâncias de extremos opostos do fatorial e verifica que o aparelho funciona: que a matriz roda
 * sem abortar, que as invariantes do grupo (a) valem, que o CSV alinha cabeçalho e células.
 *
 * <p>A razão de existir é que o harness ficou desligado do build na EOA-4b e apodreceu em silêncio —
 * dez arquivos importando tipos removidos, e nada dizia nada, porque nada os compilava. Compilar já
 * resolve metade disso; a outra metade é que um instrumento que ninguém executa não é um instrumento
 * verificado, e uma mudança em produção pode quebrar a leitura sem quebrar a compilação.
 *
 * <h2>Sabotagem: uma checagem que nunca reprova não verifica nada</h2>
 *
 * {@link #inversaoRigidaEhDetectada()} pega uma resposta válida e adianta a última sessão para antes
 * da primeira, criando de propósito uma inversão {@code HARD}. Se a checagem continuar dizendo
 * "válido", ela não está checando — e todas as 720 linhas da medição teriam sido escritas por um
 * aparelho cego.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("Harness de medição")
class MeasurementHarnessTest {

    @Autowired
    private PlanEngineSelector selector;

    @Autowired
    @Qualifier("sinapseFitnessComposition")
    private FitnessComposition composition;

    @Autowired
    private RetentionAlgorithm retention;

    @Autowired
    private ImportanceStrategies importanceStrategies;

    @Autowired
    private GeneticSearchBudget budget;

    /** As duas pontas do fatorial: a menor e mais folgada, e a maior, mais densa e mais apertada. */
    private static List<BenchmarkInstance> corners() {
        return List.of(
                InstanceLibrary.build(10, 0.4, 1.5, false),
                InstanceLibrary.build(25, 1.2, 0.7, true));
    }

    private MeasurementHarness harness() {
        return new MeasurementHarness(selector, new PlanScoring(composition, retention),
                importanceStrategies, budget);
    }

    @Test
    @DisplayName("roda a matriz inteira nas duas pontas do fatorial sem abortar")
    void aMatrizRodaSemAbortar() {
        List<MeasurementRow> rows = new ArrayList<>();
        for (BenchmarkInstance instance : corners()) {
            for (Condition condition : Condition.matrix()) {
                rows.add(harness().measure(instance, condition, MeasurementHarness.SEEDS[0]));
            }
        }

        assertThat(rows).hasSize(corners().size() * Condition.matrix().size());
        // Cada motor registrado aparece, e é o seletor quem carimba o nome: uma linha atribuída ao
        // motor errado invalidaria a comparação sem quebrar nada.
        assertThat(rows.stream().map(MeasurementRow::engine).distinct().sorted().toList())
                .isEqualTo(Condition.ENGINES.stream().sorted().toList());
    }

    @Test
    @DisplayName("o guloso não relata reparo de inversões e o genético relata")
    void oRelatoDeReparoDistingueOsDoisMotores() {
        BenchmarkInstance instance = corners().get(1);
        for (Condition condition : Condition.matrix()) {
            MeasurementRow row = harness().measure(instance, condition,
                    MeasurementHarness.SEEDS[0]);
            Integer removed = row.outcome().inversionsRemoved();
            if ("ga".equals(row.engine())) {
                assertThat(removed).as("o genético repara e diz quanto").isNotNull();
            } else {
                // Ausente, não zero. "Nenhuma removida" e "nenhum reparo rodou" são fatos
                // diferentes, e o baseline é o segundo — ver CLAUDE.md §1b.
                assertThat(removed).as("o guloso não repara e não relata").isNull();
            }
        }
    }

    @Test
    @DisplayName("cabeçalho e células do CSV têm o mesmo comprimento")
    void oCsvAlinhaCabecalhoECelulas() {
        List<String> terms = new ArrayList<>();
        composition.objectives().forEach(objective -> terms.add(objective.name()));
        composition.constraints().forEach(constraint -> terms.add(constraint.name()));

        MeasurementRow row = harness().measure(corners().get(0),
                Condition.matrix().get(0), MeasurementHarness.SEEDS[0]);

        assertThat(row.cells(terms)).hasSameSizeAs(MeasurementRow.header(terms));
    }

    @Test
    @DisplayName("uma inversão rígida plantada é detectada")
    void inversaoRigidaEhDetectada() {
        BenchmarkInstance instance = corners().get(1);
        Condition condition = new Condition("ga", EdgeProvenanceFilter.ALL);
        PlanRequest request = condition.applyTo(instance.request(),
                MeasurementHarness.SEEDS[0]);
        PlanResponse valid = selector.plan(request);

        assertThat(Invariants.hardInversions(request, valid, EdgeProvenanceFilter.ALL))
                .as("o motor não produz inversão rígida")
                .isEmpty();

        assertThat(Invariants.hardInversions(request, sabotaged(valid), EdgeProvenanceFilter.ALL))
                .as("a checagem enxerga a inversão plantada")
                .isNotEmpty();
    }

    @Test
    @DisplayName("a biblioteca de instâncias é determinística e tem 24 células")
    void aBibliotecaEhDeterministica() {
        List<BenchmarkInstance> first = InstanceLibrary.all();
        List<BenchmarkInstance> second = InstanceLibrary.all();

        assertThat(first).hasSize(24);
        assertThat(first).isEqualTo(second);
        assertThat(first.stream().map(BenchmarkInstance::id).distinct().count()).isEqualTo(24L);
        // Toda instância tem pelo menos uma aresta de cada força: uma sem SOFT não distinguiria um
        // motor que repara de um que não repara, e uma sem HARD não exercitaria a ordenação.
        assertThat(first).allSatisfy(instance ->
                assertThat(instance.request().prerequisites()).hasSizeGreaterThanOrEqualTo(2));
    }

    /**
     * Move a última sessão para o instante da primeira, criando uma inversão rígida.
     *
     * <p>A última sessão de uma ordem topológica depende — direta ou transitivamente — de algo que a
     * precede, então adiantá-la para o começo do plano viola a precedência por construção.
     */
    private static PlanResponse sabotaged(PlanResponse response) {
        List<PlanResponse.ScheduledSession> sessions =
                new ArrayList<>(response.sessions());
        int last = sessions.size() - 1;
        PlanResponse.ScheduledSession tail = sessions.get(last);
        sessions.set(last, new PlanResponse.ScheduledSession(tail.topicId(), tail.kind(),
                sessions.get(0).scheduledStart(), tail.durationMinutes(), tail.sequenceIndex()));
        return new PlanResponse(response.contractVersion(), sessions, response.fitness(),
                response.metadata());
    }
}
