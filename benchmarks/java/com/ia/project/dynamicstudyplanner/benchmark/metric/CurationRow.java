package com.ia.project.dynamicstudyplanner.benchmark.metric;

import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationInstance;
import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma linha da medição de curadoria (EOA-1): as coordenadas dos eixos novos, a linha de
 * {@link MeasurementRow} inteira, e as métricas de {@link CurationMetrics}.
 *
 * <h2>A linha publicada não muda</h2>
 *
 * As colunas de {@code MeasurementRow} vêm no meio, intactas e na mesma ordem, como a varredura de peso
 * já faz com {@code soft_weight}. Quem lê {@code measurement.csv} lê o miolo desta linha sem
 * aprender nada novo, e nenhuma coluna publicada ganha um segundo significado.
 *
 * @param role     o papel do ponto no desenho: {@code center} ou o eixo que ele varia
 * @param instance a instância, com as coordenadas
 * @param base     a linha de {@code MeasurementRow} desta execução
 * @param metrics  o que esta medição acrescenta
 */
public record CurationRow(String role, CurationInstance instance, MeasurementRow base,
        CurationMetrics metrics) {

    private static final List<String> LEADING = List.of(
            "design_role", "point_id", "subjects", "replicate",
            "importance_level", "importance_cv",
            "strength_level", "edges_total", "edges_hard", "edges_backward", "edges_cross",
            "effort_level", "effort_minutes", "minutes_cv");

    private static final List<String> TRAILING = List.of(
            "plan_hash", "order_inversions_all_edges", "order_pairs_scheduled",
            "dependents_without_prerequisite", "sessions_study", "sessions_revision",
            "scheduled_short", "scheduled_standard", "scheduled_long", "scheduled_extended");

    /**
     * @param termNames os termos da composição, como em {@link MeasurementRow#header(List)}
     * @return o cabeçalho
     */
    public static List<String> header(List<String> termNames) {
        List<String> header = new ArrayList<>(LEADING);
        header.addAll(MeasurementRow.header(termNames));
        header.addAll(TRAILING);
        return List.copyOf(header);
    }

    /**
     * @param termNames a mesma lista de {@link #header(List)}
     * @return as células, alinhadas ao cabeçalho
     */
    public List<String> cells(List<String> termNames) {
        CurationPoint point = instance.point();
        List<String> cells = new ArrayList<>();
        cells.add(role);
        cells.add(point.id());
        cells.add(Integer.toString(point.subjects()));
        cells.add(Integer.toString(point.replicate()));
        cells.add(point.importance().id());
        cells.add(MeasurementRow.number(metrics.importanceCv()));
        cells.add(point.strength().id());
        cells.add(Integer.toString(instance.edges()));
        cells.add(Integer.toString(instance.hardEdges()));
        cells.add(Integer.toString(instance.backwardEdges()));
        cells.add(Integer.toString(instance.crossEdges()));
        cells.add(point.effort().id());
        cells.add(point.effort().table());
        cells.add(MeasurementRow.number(metrics.minutesCv()));
        cells.addAll(base.cells(termNames));
        cells.add(metrics.planHash());
        cells.add(Integer.toString(metrics.orderInversions()));
        cells.add(Integer.toString(metrics.orderPairsScheduled()));
        cells.add(Integer.toString(metrics.dependentsWithoutPrerequisite()));
        cells.add(Integer.toString(metrics.studySessions()));
        cells.add(Integer.toString(metrics.revisionSessions()));
        for (int tier : metrics.scheduledByTier()) {
            cells.add(Integer.toString(tier));
        }
        return cells;
    }
}
