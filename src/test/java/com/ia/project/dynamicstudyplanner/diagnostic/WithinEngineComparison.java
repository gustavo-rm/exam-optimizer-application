package com.ia.project.dynamicstudyplanner.diagnostic;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.PlanningItem;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.sinapse.SearchVitality;
import com.ia.project.dynamicstudyplanner.sinapse.TopicPlanningItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Passo 3b: três soluções sob a função de UM motor — nunca a de um motor contra a de outro.
 *
 * <ul>
 *   <li>{@code p_e}: o que a busca do motor achou, lido pelo {@link SearchReplay};</li>
 *   <li>{@code p_g}: o plano do guloso, codificado na representação do motor;</li>
 *   <li>{@code p_0}: o plano vazio na mesma representação.</li>
 * </ul>
 *
 * <h2>As duas codificações de {@code p_g}</h2>
 *
 * <b>{@code ga}</b>: o cromossomo é macro — sessões por item, sem calendário. O plano do guloso vira
 * o vetor "quantas sessões o guloso deu a cada tópico", com zero para os que ele não agendou. Não
 * passa por piso nem por reparo: é o plano do guloso, e a restrição de piso o precifica como
 * precificaria qualquer outro vetor.
 *
 * <p><b>{@code ga-timeline}</b>: o cromossomo já é o calendário. O plano do guloso é reconstruído
 * por {@code PlanScoring.rebuild}, o mesmo caminho que o harness EOA-M usa, sem reparo.
 *
 * <p>{@code F} é {@link FitnessBreakdown#selectionScore()}: o número pelo qual a seleção do motor
 * ordena. No caminho macro ele é igual ao agregado; no tático, é o bruto quando negativo (G15).
 */
final class WithinEngineComparison {

    /** Rótulos das três soluções. */
    static final String ENGINE_SOLUTION = "p_e";
    static final String GREEDY_SOLUTION = "p_g";
    static final String EMPTY_SOLUTION = "p_0";

    private final SearchReplay replay;
    private final EngineProbe probe;

    WithinEngineComparison(SearchReplay replay, EngineProbe probe) {
        this.replay = replay;
        this.probe = probe;
    }

    /** Uma solução pontuada. */
    record Scored(String solution, int topics, int sessions, FitnessBreakdown breakdown) {
    }

    /**
     * As três soluções sob a função do {@code ga}, e a conferência do replay.
     *
     * @param request o pedido com motor {@code ga} e a semente
     * @param engine  a execução real do motor, para conferir o replay
     * @param greedy  a execução do guloso na mesma instância
     */
    List<Scored> ga(PlanRequest request, EngineProbe.Outcome engine, EngineProbe.Outcome greedy) {
        SearchReplay.Replayed replayed = replay.ga(request);
        verify(engine, replayed);
        EvolutionContext context = replay.context(request);
        List<Scored> scored = new ArrayList<>();
        scored.add(macro(ENGINE_SOLUTION, replayed.fittest(), context));
        scored.add(macro(GREEDY_SOLUTION, encodeMacro(request, context, greedy), context));
        scored.add(macro(EMPTY_SOLUTION, encodeMacro(request, context, null), context));
        return scored;
    }

    /** As três soluções sob a função do {@code ga-timeline}, e a conferência do replay. */
    List<Scored> timeline(PlanRequest request, EngineProbe.Outcome engine,
            EngineProbe.Outcome greedy) {
        SearchReplay.Replayed replayed = replay.timeline(request);
        verify(engine, replayed);
        EvolutionContext context = replay.context(request);
        TacticalStudyPlan greedyPlan = greedy.response() == null
                ? new TacticalStudyPlan(Map.of())
                : probe.scoring().rebuild(request, greedy.response());
        List<Scored> scored = new ArrayList<>();
        scored.add(tactical(ENGINE_SOLUTION, (TacticalStudyPlan) replayed.fittest(), context));
        scored.add(tactical(GREEDY_SOLUTION, greedyPlan, context));
        scored.add(tactical(EMPTY_SOLUTION, new TacticalStudyPlan(Map.of()), context));
        return scored;
    }

    /**
     * O replay é a busca do motor, ou o passo não vale nada.
     *
     * <p>Aceito: {@code search-final-best} publicado tem de ser bit a bit o do replay. Recusado pelo
     * {@code ga-timeline}: o replay tem de ter terminado num melhor indivíduo vazio, que é a condição
     * de {@code plan-would-be-empty} naquele motor. Uma recusa do {@code ga} não tem o que conferir:
     * ela nasce na colocação, depois da busca, e o cromossomo macro nunca é vazio.
     */
    static void verify(EngineProbe.Outcome engine, SearchReplay.Replayed replayed) {
        if (engine.wasAccepted()) {
            Object published = engine.response().fitness().get(SearchVitality.FINAL_KEY);
            double expected = ((Number) published).doubleValue();
            if (Double.compare(expected, replayed.vitality().finalBest()) != 0) {
                throw new IllegalStateException("O replay divergiu do motor " + engine.engine()
                        + " na semente " + engine.seed() + ": publicado " + expected
                        + ", replay " + replayed.vitality().finalBest());
            }
            return;
        }
        if (replayed.fittest() instanceof TacticalStudyPlan tactical
                && !tactical.getSchedule().isEmpty()) {
            throw new IllegalStateException("O motor recusou (" + engine.state()
                    + ") e o replay terminou num plano não vazio, semente " + engine.seed());
        }
    }

    private static Scored macro(String solution, StudyPlan plan, EvolutionContext context) {
        int topics = 0;
        for (int sessions : plan.getDaysPerItem().values()) {
            topics += sessions > 0 ? 1 : 0;
        }
        return new Scored(solution, topics, plan.getTotalDays(),
                context.fitnessEvaluator().explain(plan, context));
    }

    private static Scored tactical(String solution, TacticalStudyPlan plan,
            EvolutionContext context) {
        int topics = (int) plan.getSchedule().values().stream().map(block -> block.item())
                .distinct().count();
        return new Scored(solution, topics, plan.getSchedule().size(),
                context.fitnessEvaluator().explain(plan, context));
    }

    /**
     * O plano do guloso como vetor macro: sessões por item, na ordem dos itens do contexto. Com
     * {@code greedy} nulo, o vetor vazio.
     */
    private static StudyPlan encodeMacro(PlanRequest request, EvolutionContext context,
            EngineProbe.Outcome greedy) {
        Map<UUID, Integer> perTopic = new LinkedHashMap<>();
        if (greedy != null && greedy.response() != null) {
            for (PlanResponse.ScheduledSession session : greedy.response().sessions()) {
                perTopic.merge(session.topicId(), 1, Integer::sum);
            }
        }
        Map<PlanningItem, UUID> ids = TopicPlanningItems.topicIdsByItem(request.topics());
        Map<PlanningItem, Integer> days = new LinkedHashMap<>();
        for (PlanningItem item : context.importanceScores().keySet()) {
            days.put(item, perTopic.getOrDefault(ids.get(item), 0));
        }
        return new StudyPlan(days);
    }

    static String header() {
        return "instance,engine,seed,solution,n_topics,n_sessions,F_selection,aggregate,raw_score,"
                + "term,term_kind,term_value,term_weight,term_contribution";
    }

    /** Uma linha por termo, com os escalares da solução repetidos: formato longo. */
    static List<String> rows(String instance, String engine, long seed, Scored scored) {
        List<String> rows = new ArrayList<>();
        FitnessBreakdown b = scored.breakdown();
        String prefix = String.join(",", instance, engine, Long.toString(seed), scored.solution(),
                Integer.toString(scored.topics()), Integer.toString(scored.sessions()),
                Csv.num(b.selectionScore()), Csv.num(b.aggregate()), Csv.num(b.rawScore()));
        for (FitnessBreakdown.Term term : b.terms()) {
            rows.add(String.join(",", prefix, term.name(), term.kind().name(),
                    Csv.num(term.value()), Csv.num(term.weight()),
                    Csv.num(term.weightedContribution())));
        }
        return rows;
    }
}
