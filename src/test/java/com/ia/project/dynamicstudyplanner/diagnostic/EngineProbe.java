package com.ia.project.dynamicstudyplanner.diagnostic;

import com.ia.project.dynamicstudyplanner.benchmark.metric.PlanScoring;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.plan.PlanEngineSelector;
import com.ia.project.dynamicstudyplanner.plan.PlanInvariantViolationException;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import com.ia.project.dynamicstudyplanner.sinapse.GeneticSearchBudget;
import com.ia.project.dynamicstudyplanner.sinapse.RequestConditions;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Executa um motor pelo seletor de produção e mede o resultado.
 *
 * <p>O motor é escolhido pelo <b>nome</b>, em {@code algorithmParams.engine}, e chamado por
 * {@link PlanEngineSelector#plan} — a mesma chamada que {@code PlanController} faz. Uma recusa com
 * {@code 422} vira uma linha com o código da recusa; uma violação de invariante, uma linha com a
 * mensagem. Nenhuma das duas aborta a medição: o diagnóstico quer contá-las.
 *
 * <p>Todo plano aceito é pontuado pelo avaliador a posteriori do harness de medição,
 * {@link PlanScoring}, com a composição de produção injetada. O número é o objetivo do genético
 * relatado por completude, nunca uma comparação entre motores.
 */
final class EngineProbe {

    /** Estado de uma execução aceita. */
    static final String ACCEPTED = "accepted";

    private final PlanEngineSelector selector;
    private final PlanScoring scoring;
    private final RequestConditions conditions;
    private final GeneticSearchBudget budget;

    EngineProbe(PlanEngineSelector selector, PlanScoring scoring, RequestConditions conditions,
            GeneticSearchBudget budget) {
        this.selector = selector;
        this.scoring = scoring;
        this.conditions = conditions;
        this.budget = budget;
    }

    PlanScoring scoring() {
        return scoring;
    }

    RequestConditions conditions() {
        return conditions;
    }

    GeneticSearchBudget budget() {
        return budget;
    }

    /**
     * Uma execução, com o relógio do harness em volta da chamada ao seletor.
     *
     * @param instance o rótulo da instância, para a linha do CSV
     * @param request  o pedido já com motor e semente
     */
    Outcome run(String instance, PlanRequest request) {
        String engine = String.valueOf(request.algorithmParams().get(PlanEngineSelector.ENGINE_PARAM));
        long capacity = DiagnosticInstances.capacity(request);
        long started = System.nanoTime();
        try {
            PlanResponse response = selector.plan(request);
            double wallMillis = (System.nanoTime() - started) / 1e6;
            return accepted(instance, engine, request, response, capacity, wallMillis);
        } catch (PlanRejectedException rejected) {
            double wallMillis = (System.nanoTime() - started) / 1e6;
            return Outcome.failed(instance, request, rejected.reason(), "",
                    rejected.getMessage() + " offending=" + rejected.offending(),
                    budgetFor(engine), wallMillis);
        } catch (PlanInvariantViolationException violation) {
            double wallMillis = (System.nanoTime() - started) / 1e6;
            return Outcome.failed(instance, request, "plan-invariant-violation",
                    violation.getMessage(), "", budgetFor(engine), wallMillis);
        }
    }

    /** O plano aceito, reconstruído e repontuado como o harness EOA-M faz. */
    private Outcome accepted(String instance, String engine, PlanRequest request,
            PlanResponse response, long capacity, double wallMillis) {

        EvolutionContext context = scoring.contextFor(request, conditions.provenance(request),
                conditions.importance(request));
        FitnessBreakdown posthoc = scoring.score(scoring.rebuild(request, response), context);
        List<PlanResponse.ScheduledSession> sessions = response.sessions();
        long minutes = sessions.stream().mapToLong(PlanResponse.ScheduledSession::durationMinutes).sum();
        int topics = (int) sessions.stream().map(PlanResponse.ScheduledSession::topicId)
                .distinct().count();
        return new Outcome(instance, engine, request.randomSeed(), ACCEPTED, "", "", topics,
                sessions.size(), minutes, capacity, term(posthoc, "syllabusMastery"),
                posthoc.aggregate(), posthoc.rawScore(), response.metadata().generations(),
                wallMillis, response);
    }

    private int budgetFor(String engine) {
        return "greedy-baseline".equals(engine) ? 0 : budget.generations();
    }

    /** O valor normalizado de um termo, ou {@code NaN} quando o termo não está na composição. */
    static double term(FitnessBreakdown breakdown, String name) {
        return breakdown.terms().stream()
                .filter(t -> t.name().equals(name))
                .mapToDouble(FitnessBreakdown.Term::value)
                .findFirst()
                .orElse(Double.NaN);
    }

    /**
     * Uma execução medida.
     *
     * @param topics       tópicos distintos com ao menos uma sessão
     * @param mastery      {@code syllabusMastery} do avaliador a posteriori: cobertura ponderada
     *                     pela importância; {@code NaN} quando não houve plano
     * @param posthoc      agregado do avaliador a posteriori; {@code NaN} quando não houve plano
     * @param posthocRaw   o mesmo antes do limite {@code [0,1]}, porque o limite empata em zero
     *                     planos que o bruto ainda ordena
     * @param generations  gerações efetivas: as que a resposta declara, ou o orçamento configurado
     *                     quando a resposta foi recusada depois da busca
     * @param response     a resposta, ou {@code null} quando recusada
     */
    record Outcome(String instance, String engine, long seed, String state, String violation,
            String message, int topics, int sessions, long minutes, long capacity, double mastery,
            double posthoc, double posthocRaw, int generations, double wallMillis,
            PlanResponse response) {

        static Outcome failed(String instance, PlanRequest request, String state,
                String violation, String message, int generations, double wallMillis) {
            String engine = String.valueOf(
                    request.algorithmParams().get(PlanEngineSelector.ENGINE_PARAM));
            return new Outcome(instance, engine, request.randomSeed(), state, violation, message,
                    0, 0, 0, DiagnosticInstances.capacity(request), Double.NaN, Double.NaN,
                    Double.NaN, generations, wallMillis, null);
        }

        boolean wasAccepted() {
            return ACCEPTED.equals(state);
        }

        double utilisation() {
            return capacity == 0 ? 0.0 : minutes / (double) capacity;
        }

        /** Tópicos agendados, para codificar o plano em outra representação. */
        List<UUID> topicIds() {
            return response == null ? List.of()
                    : response.sessions().stream().map(PlanResponse.ScheduledSession::topicId)
                            .distinct().toList();
        }

        static String header() {
            return "instance,engine,seed,state,invariant_violation,n_topics,n_sessions,"
                    + "scheduled_minutes,capacity,util,posthoc_syllabus_mastery,posthoc_ga_objective,"
                    + "posthoc_raw_score,generations,wall_ms,message";
        }

        String csv() {
            return String.join(",", instance, engine, Long.toString(seed), state,
                    Csv.quote(violation), Integer.toString(topics), Integer.toString(sessions),
                    Long.toString(minutes), Long.toString(capacity), Csv.num(utilisation()),
                    Csv.num(mastery), Csv.num(posthoc), Csv.num(posthocRaw),
                    Integer.toString(generations),
                    String.format(Locale.ROOT, "%.1f", wallMillis), Csv.quote(message));
        }
    }
}
