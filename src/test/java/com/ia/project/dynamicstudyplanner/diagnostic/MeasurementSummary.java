package com.ia.project.dynamicstudyplanner.diagnostic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * As tabelas do EOA-12b, calculadas das linhas da matriz com a estatística fixada antes de rodar
 * ({@code docs/MEDICAO_ALOCADOR.md} §4): medianas com mínimo e máximo, diferenças pareadas por
 * semente, fração de sementes e n efetivo. Sem teste de significância.
 */
final class MeasurementSummary {

    /** Como cada motor rodou com P, pela classificação do E1. Toda tabela carrega a coluna. */
    static final String CLASSIFICATION = "COMPLETO";

    private MeasurementSummary() {
    }

    /**
     * Uma execução da matriz.
     *
     * @param variant      {@code base} ou {@code P}
     * @param outcome      as métricas
     * @param sessionsHash hash de {@code sessions}, vazio numa recusa
     * @param counts       a classificação dos ausentes
     */
    record Run(String variant, EngineProbe.Outcome outcome, String sessionsHash,
            AbsenceClassifier.Counts counts) {

        boolean accepted() {
            return outcome.wasAccepted();
        }

        /** O resultado, para o n efetivo: o plano, ou o código da recusa. */
        String result() {
            return accepted() ? sessionsHash : "refused:" + outcome.state();
        }
    }

    static String cellHeader() {
        return "instance,engine,variant,classification,accepted,refused,n_effective,invariants_pass,"
                + "topics_median,topics_min,topics_max,sessions_median,minutes_median,"
                + "util_median,syllabus_mastery_median,ga_objective_median,raw_score_median";
    }

    /** Uma célula: as 10 sementes de uma instância, um motor e uma variante. */
    static String cell(List<Run> runs, int invariantsPass) {
        List<Run> accepted = runs.stream().filter(Run::accepted).toList();
        EngineProbe.Outcome first = runs.get(0).outcome();
        return String.join(",", first.instance(), first.engine(), runs.get(0).variant(), CLASSIFICATION,
                Integer.toString(accepted.size()), Integer.toString(runs.size() - accepted.size()),
                Integer.toString(effective(runs)), Integer.toString(invariantsPass),
                num(median(runs, run -> run.outcome().topics())), num(min(runs, run -> run.outcome().topics())),
                num(max(runs, run -> run.outcome().topics())), num(median(runs, run -> run.outcome().sessions())),
                num(median(runs, run -> run.outcome().minutes())),
                num(median(accepted, run -> run.outcome().utilisation())),
                num(median(accepted, run -> run.outcome().mastery())),
                num(median(accepted, run -> run.outcome().posthoc())),
                num(median(accepted, run -> run.outcome().posthocRaw())));
    }

    static String pairHeader() {
        return "instance,engine,classification,n_effective_base,n_effective_p,n_effective_pairs,"
                + "d_topics_median,d_topics_min,d_topics_max,frac_seeds_p_more_topics,"
                + "d_sessions_median,d_minutes_median,pairs_both_accepted,"
                + "d_util_median,d_util_min,d_util_max,d_syllabus_mastery_median,d_syllabus_mastery_min,"
                + "d_syllabus_mastery_max,d_ga_objective_median,d_raw_score_median,d_raw_score_min,"
                + "d_raw_score_max";
    }

    /** P contra a base, pareado por semente: as duas listas estão na mesma ordem de semente. */
    static String pair(List<Run> base, List<Run> skipAhead) {
        List<Double> topics = differences(base, skipAhead, run -> run.outcome().topics(), false);
        List<Double> sessions = differences(base, skipAhead, run -> run.outcome().sessions(), false);
        List<Double> minutes = differences(base, skipAhead, run -> run.outcome().minutes(), false);
        List<Double> util = differences(base, skipAhead, run -> run.outcome().utilisation(), true);
        List<Double> mastery = differences(base, skipAhead, run -> run.outcome().mastery(), true);
        List<Double> objective = differences(base, skipAhead, run -> run.outcome().posthoc(), true);
        List<Double> raw = differences(base, skipAhead, run -> run.outcome().posthocRaw(), true);
        long more = topics.stream().filter(d -> d > 0).count();
        EngineProbe.Outcome first = base.get(0).outcome();
        return String.join(",", first.instance(), first.engine(), CLASSIFICATION,
                Integer.toString(effective(base)), Integer.toString(effective(skipAhead)),
                Integer.toString(effectivePairs(base, skipAhead)),
                num(median(topics)), num(min(topics)), num(max(topics)),
                String.format(Locale.ROOT, "%d/%d", more, topics.size()),
                num(median(sessions)), num(median(minutes)), Integer.toString(util.size()),
                num(median(util)), num(min(util)), num(max(util)),
                num(median(mastery)), num(min(mastery)), num(max(mastery)),
                num(median(objective)), num(median(raw)), num(min(raw)), num(max(raw)));
    }

    static String absenceHeader() {
        return "instance,engine,variant,classification,responses,"
                + "absent,absent_A,absent_B,absent_C_root,absent_C_dep,absent_D,A_scheduled,"
                + "frac_A_plus_B,frac_C";
    }

    /** A distribuição de A, B, C e D numa célula, só nas aceitas ou só nas recusas. */
    static String absence(List<Run> runs, String responses) {
        int[] sum = new int[7];
        for (Run run : runs) {
            AbsenceClassifier.Counts counts = run.counts();
            int[] values = {counts.absent(), counts.a(), counts.b(), counts.cRoot(), counts.cDep(),
                counts.d(), counts.aScheduled()};
            for (int index = 0; index < sum.length; index++) {
                sum[index] += values[index];
            }
        }
        EngineProbe.Outcome first = runs.get(0).outcome();
        double total = sum[0];
        return String.join(",", first.instance(), first.engine(), runs.get(0).variant(), CLASSIFICATION,
                responses + "=" + runs.size(), Integer.toString(sum[0]), Integer.toString(sum[1]),
                Integer.toString(sum[2]), Integer.toString(sum[3]), Integer.toString(sum[4]),
                Integer.toString(sum[5]), Integer.toString(sum[6]),
                total == 0 ? "n/a" : num((sum[1] + sum[2]) / total),
                total == 0 ? "n/a" : num((sum[3] + sum[4]) / total));
    }

    /** Resultados distintos nas sementes: cada plano aceito distinto, e as recusas de mesmo código. */
    static int effective(List<Run> runs) {
        Set<String> distinct = new HashSet<>();
        runs.forEach(run -> distinct.add(run.result()));
        return distinct.size();
    }

    private static int effectivePairs(List<Run> base, List<Run> skipAhead) {
        Set<String> distinct = new HashSet<>();
        for (int index = 0; index < base.size(); index++) {
            distinct.add(base.get(index).result() + "|" + skipAhead.get(index).result());
        }
        return distinct.size();
    }

    /**
     * P menos base, semente a semente.
     *
     * @param bothAccepted se só os pares com os dois lados aceitos entram (métricas do avaliador)
     */
    private static List<Double> differences(List<Run> base, List<Run> skipAhead,
            ToDoubleFunction<Run> metric, boolean bothAccepted) {

        List<Double> differences = new ArrayList<>();
        for (int index = 0; index < base.size(); index++) {
            Run before = base.get(index);
            Run after = skipAhead.get(index);
            if (!bothAccepted || before.accepted() && after.accepted()) {
                differences.add(metric.applyAsDouble(after) - metric.applyAsDouble(before));
            }
        }
        return differences;
    }

    private static double median(List<Run> runs, ToDoubleFunction<Run> metric) {
        return median(runs.stream().map(run -> metric.applyAsDouble(run)).toList());
    }

    private static double min(List<Run> runs, ToDoubleFunction<Run> metric) {
        return min(runs.stream().map(run -> metric.applyAsDouble(run)).toList());
    }

    private static double max(List<Run> runs, ToDoubleFunction<Run> metric) {
        return max(runs.stream().map(run -> metric.applyAsDouble(run)).toList());
    }

    static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
    }

    private static double min(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }

    private static double max(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }

    private static String num(double value) {
        return Csv.num(value);
    }
}
