package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationLibrary;
import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationPoint;
import com.ia.project.dynamicstudyplanner.benchmark.instance.EdgeStrengthMix;
import com.ia.project.dynamicstudyplanner.benchmark.instance.EffortMapping;
import com.ia.project.dynamicstudyplanner.benchmark.instance.ImportanceSpread;
import com.ia.project.dynamicstudyplanner.benchmark.metric.CurationMetrics;
import com.ia.project.dynamicstudyplanner.benchmark.metric.CurationRow;
import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.EdgeStrength;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.baseline.GreedyBaselineEngine;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O teste rápido da medição de curadoria (EOA-1): o gerador tem a forma prometida, cada eixo mexe só
 * no que diz mexer, e a régua nova reprova quando deve.
 *
 * <p>Não repete a medição — {@link CurationAxesMain} roda 7 020 linhas em ~40 min. Roda uma célula
 * de uma disciplina, que é a mais barata, e o resto é sobre a biblioteca, sem motor.
 */
@SpringBootTest
@ActiveProfiles(PlanProtocol.PROFILE)
@DisplayName("Medição dos eixos de curadoria")
class CurationAxesTest {

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

    private static CurationPoint center(int subjects) {
        return new CurationPoint(subjects, 1, CurationDesign.CENTER_IMPORTANCE,
                CurationDesign.CENTER_STRENGTH, CurationDesign.CENTER_EFFORT, 1.0);
    }

    private MeasurementHarness harness() {
        return new MeasurementHarness(selector, new PlanScoring(composition, retention),
                importanceStrategies, budget);
    }

    @Test
    @DisplayName("a estrutura é a mesma em todo nível dos eixos que não são tamanho nem sorteio")
    void osEixosNaoMudamAEstrutura() {
        CurationPoint center = center(2);
        List<String> reference = pairs(CurationLibrary.build(center));
        List<UUID> topics = ids(CurationLibrary.build(center));

        List<CurationPoint> variants = new ArrayList<>();
        for (ImportanceSpread level : ImportanceSpread.values()) {
            variants.add(center.with(level));
        }
        for (EdgeStrengthMix level : EdgeStrengthMix.values()) {
            variants.add(center.with(level));
        }
        for (EffortMapping level : EffortMapping.values()) {
            variants.add(center.with(level));
        }
        List<PlanRequest.AvailabilitySlot> calendar =
                CurationLibrary.build(center).benchmark().request().availability();

        // O calendario do aluno e o mesmo em todo nivel, inclusive nos de esforco: a anotacao muda o
        // que se pede, nao as horas que ele tem.
        assertThat(variants).allSatisfy(variant -> {
            CurationInstance instance = CurationLibrary.build(variant);
            assertThat(pairs(instance)).as(variant.id()).isEqualTo(reference);
            assertThat(ids(instance)).as(variant.id()).isEqualTo(topics);
            assertThat(instance.benchmark().request().availability()).as(variant.id())
                    .isEqualTo(calendar);
        });
        CurationInstance tighter = CurationLibrary.build(center.withTightness(0.7));
        assertThat(pairs(tighter)).isEqualTo(reference);
        assertThat(tighter.benchmark().request().availability().size())
                .isLessThan(calendar.size());
        assertThat(pairs(CurationLibrary.build(center.withReplicate(2))))
                .as("outro sorteio, outra estrutura").isNotEqualTo(reference);
        assertThat(pairs(CurationLibrary.build(center))).as("determinística").isEqualTo(reference);
    }

    @Test
    @DisplayName("as arestas e as faixas têm a proporção do catálogo de exemplo")
    void aFormaEhADoCatalogo() {
        for (int subjects = 1; subjects <= 3; subjects++) {
            CurationInstance curated = CurationLibrary.build(center(subjects));
            List<PlanRequest.Topic> topics = curated.benchmark().request().topics();

            assertThat(curated.edges()).isEqualTo(35 * subjects);
            assertThat(curated.hardEdges()).isEqualTo(24 * subjects);
            assertThat(curated.backwardEdges()).isEqualTo(2 * subjects);
            assertThat(topics.stream().collect(Collectors.groupingBy(PlanRequest.Topic::effortTier,
                    Collectors.counting())))
                    .isEqualTo(Map.of("SHORT", 4L * subjects, "STANDARD", 21L * subjects,
                            "LONG", 8L * subjects, "EXTENDED", 2L * subjects));
            assertThat(topics).allSatisfy(topic -> assertThat(topic.estimatedMinutes())
                    .isEqualTo(minutesOf(topic.effortTier())));

            assertThat(CurationLibrary.build(center(subjects).with(EdgeStrengthMix.ALL_SOFT))
                    .hardEdges()).isZero();
            assertThat(CurationLibrary.build(center(subjects).with(EdgeStrengthMix.ALL_HARD))
                    .hardEdges()).isEqualTo(35 * subjects);
        }
        assertThat(CurationLibrary.build(center(2).with(EffortMapping.UNIFORM))
                .benchmark().request().topics())
                .allSatisfy(topic -> {
                    assertThat(topic.effortTier()).isEqualTo("STANDARD");
                    assertThat(topic.estimatedMinutes()).isEqualTo(50);
                });
    }

    @Test
    @DisplayName("controle negativo: o guloso não distingue faixas que valem os mesmos minutos")
    void oGulosoNaoLeAFaixa() {
        // UNIFORM manda tudo STANDARD; FLAT manda as faixas do catálogo, todas a 50 minutos. O guloso
        // aloca por estimatedMinutes e nunca le effortTier: o plano tem de ser o mesmo, sessao por
        // sessao. Se divergir, ou o guloso passou a ler a faixa, ou o gerador mudou mais que a faixa.
        CurationPoint uniform = center(2).with(EffortMapping.UNIFORM);
        Condition greedy = new Condition(GreedyBaselineEngine.ID, EdgeProvenanceFilter.ALL);

        String withoutTiers = hashOf(uniform, greedy);
        String withTiers = hashOf(uniform.with(EffortMapping.FLAT), greedy);

        assertThat(withTiers).isEqualTo(withoutTiers);
    }

    @Test
    @DisplayName("uma célula roda inteira, alinha o CSV, e a régua nova passa sob all-hard")
    void umaCelulaRodaEAlinha() {
        List<String> terms = new ArrayList<>();
        composition.objectives().forEach(objective -> terms.add(objective.name()));
        composition.constraints().forEach(constraint -> terms.add(constraint.name()));
        CurationInstance instance =
                CurationLibrary.build(center(1).with(EdgeStrengthMix.ALL_HARD));

        List<CurationRow> rows =
                CurationAxesMain.measureCell(harness(), importanceStrategies, "size", instance);

        assertThat(rows).hasSize(Condition.ENGINES.size() * MeasurementHarness.SEEDS.length);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.cells(terms)).hasSameSizeAs(CurationRow.header(terms));
            assertThat(row.metrics().importanceCv()).as("uma meta, dispersão zero").isZero();
        });
    }

    @Test
    @DisplayName("a régua de ordem sobre todas as arestas enxerga uma inversão plantada")
    void aReguaDeOrdemReprovaQuandoDeve() {
        CurationInstance instance = CurationLibrary.build(center(1));
        Condition greedy = new Condition(GreedyBaselineEngine.ID, EdgeProvenanceFilter.ALL);
        PlanResponse valid = harness().measured(instance.benchmark(), greedy,
                MeasurementHarness.SEEDS[0]).response();
        List<UUID> scheduled = valid.sessions().stream()
                .map(PlanResponse.ScheduledSession::topicId).toList();
        PlanRequest.PrerequisiteEdge hard = instance.benchmark().request().prerequisites().stream()
                .filter(edge -> edge.strength() == EdgeStrength.HARD)
                .filter(edge -> scheduled.contains(edge.prerequisiteTopicId())
                        && scheduled.contains(edge.dependentTopicId()))
                .findFirst().orElseThrow();

        CurationMetrics before = CurationMetrics.of(instance, valid, List.of(1.0));
        CurationMetrics after = CurationMetrics.of(instance, swapped(valid, hard), List.of(1.0));

        assertThat(before.orderInversions()).as("o guloso respeita as HARD").isLessThanOrEqualTo(
                before.orderPairsScheduled());
        assertThat(after.orderInversions()).as("a troca planta exatamente uma inversão a mais")
                .isGreaterThan(before.orderInversions());
        assertThat(after.planHash()).isNotEqualTo(before.planHash());
    }

    private String hashOf(CurationPoint point, Condition condition) {
        CurationInstance instance = CurationLibrary.build(point);
        PlanResponse response = harness().measured(instance.benchmark(), condition,
                MeasurementHarness.SEEDS[0]).response();
        return CurationMetrics.of(instance, response, List.of(1.0)).planHash();
    }

    /** Troca os inícios do pré-requisito e do dependente de uma aresta: o dependente vem antes. */
    private static PlanResponse swapped(PlanResponse response, PlanRequest.PrerequisiteEdge edge) {
        List<PlanResponse.ScheduledSession> sessions = new ArrayList<>(response.sessions());
        int prerequisite = firstOf(sessions, edge.prerequisiteTopicId());
        int dependent = firstOf(sessions, edge.dependentTopicId());
        PlanResponse.ScheduledSession early = sessions.get(prerequisite);
        PlanResponse.ScheduledSession late = sessions.get(dependent);
        sessions.set(prerequisite, new PlanResponse.ScheduledSession(early.topicId(), early.kind(),
                late.scheduledStart(), early.durationMinutes(), early.sequenceIndex()));
        sessions.set(dependent, new PlanResponse.ScheduledSession(late.topicId(), late.kind(),
                early.scheduledStart(), late.durationMinutes(), late.sequenceIndex()));
        return new PlanResponse(response.contractVersion(), sessions, response.fitness(),
                response.metadata());
    }

    private static int firstOf(List<PlanResponse.ScheduledSession> sessions, UUID topic) {
        for (int index = 0; index < sessions.size(); index++) {
            if (sessions.get(index).topicId().equals(topic)) {
                return index;
            }
        }
        throw new IllegalStateException("topic " + topic + " was not scheduled");
    }

    private static int minutesOf(String tier) {
        return switch (tier) {
            case "SHORT" -> 25;
            case "STANDARD" -> 50;
            case "LONG" -> 90;
            case "EXTENDED" -> 150;
            default -> throw new IllegalArgumentException(tier);
        };
    }

    private static List<String> pairs(CurationInstance instance) {
        return instance.benchmark().request().prerequisites().stream()
                .map(edge -> edge.prerequisiteTopicId() + ">" + edge.dependentTopicId())
                .toList();
    }

    private static List<UUID> ids(CurationInstance instance) {
        return instance.benchmark().request().topics().stream().map(PlanRequest.Topic::id).toList();
    }
}
