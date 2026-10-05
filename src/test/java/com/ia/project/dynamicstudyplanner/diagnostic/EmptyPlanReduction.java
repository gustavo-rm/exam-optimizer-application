package com.ia.project.dynamicstudyplanner.diagnostic;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.domain.FitnessBreakdown;
import com.ia.project.dynamicstudyplanner.domain.StudyPlan;
import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Passo 6: reduz um pedido ao menor que ainda leva o {@code ga-timeline} a
 * {@code plan-would-be-empty}, e lê a trajetória do melhor indivíduo nele.
 *
 * <h2>O critério de "ainda reproduz"</h2>
 *
 * Fixado antes da redução: o erro ocorre em <b>pelo menos {@code required}</b> das sementes 1 a 10,
 * onde {@code required} é quantas sementes o reproduziam na instância de partida. Com partida
 * 10/10, todo passo da redução preserva o determinismo inteiro, e não só "às vezes".
 *
 * <h2>A redução</h2>
 *
 * Bisseção à moda de {@code ddmin}: tenta remover blocos de metade, depois de um quarto, até um
 * elemento por vez; aceita toda remoção que preserva o critério. Primeiro tópicos (com as arestas
 * que os tocam), depois janelas, depois a meta; repete até nenhuma remoção passar.
 *
 * <h2>Duas reduções, porque a primeira degenera</h2>
 *
 * A redução pedida — "ainda dá o erro" — chega a um pedido em que <b>nada cabe</b>, e ali a recusa
 * é correta: não separa o defeito do caso legítimo. A segunda, com {@code requireFeasible}, só
 * aceita uma remoção se o guloso continuar <b>aceitando</b> o pedido, isto é, se existir plano não
 * vazio. O que sobra é o menor pedido em que o {@code ga-timeline} recusa um problema que tem
 * solução.
 */
final class EmptyPlanReduction {

    static final String TIMELINE = "ga-timeline";
    static final String EMPTY = "plan-would-be-empty";
    static final String GREEDY = "greedy-baseline";

    private final EngineProbe probe;
    private final List<Long> seeds;
    private final int required;
    private final boolean requireFeasible;
    private final List<String> log = new ArrayList<>();

    EmptyPlanReduction(EngineProbe probe, List<Long> seeds, int required, boolean requireFeasible) {
        this.probe = probe;
        this.seeds = seeds;
        this.required = required;
        this.requireFeasible = requireFeasible;
    }

    List<String> log() {
        return log;
    }

    /** Quantas sementes levam o {@code ga-timeline} a {@code plan-would-be-empty}. */
    int reproductions(PlanRequest request) {
        int count = 0;
        for (long seed : seeds) {
            EngineProbe.Outcome outcome = probe.run("reduction",
                    DiagnosticInstances.forEngine(request, TIMELINE, seed));
            count += EMPTY.equals(outcome.state()) ? 1 : 0;
        }
        return count;
    }

    /** Se o guloso aceita o pedido: a prova de que existe plano não vazio. */
    boolean greedyAccepts(PlanRequest request) {
        return probe.run("reduction", DiagnosticInstances.forEngine(request, GREEDY,
                request.randomSeed())).wasAccepted();
    }

    private boolean reproduces(PlanRequest request) {
        if (requireFeasible && !greedyAccepts(request)) {
            return false;
        }
        return reproductions(request) >= required;
    }

    /** O menor pedido que ainda satisfaz o critério. */
    PlanRequest reduce(PlanRequest start) {
        PlanRequest current = start;
        int before;
        do {
            before = size(current);
            current = reduceTopics(current);
            current = reduceWindows(current);
            current = reduceGoals(current);
        } while (size(current) < before);
        return current;
    }

    private static int size(PlanRequest request) {
        return request.topics().size() + request.availability().size() + request.goals().size();
    }

    private PlanRequest reduceTopics(PlanRequest request) {
        List<UUID> ids = request.topics().stream().map(PlanRequest.Topic::id).toList();
        Function<List<UUID>, PlanRequest> build = kept ->
                DiagnosticInstances.retainTopics(request, new LinkedHashSet<>(kept));
        return build.apply(minimise(ids, build, "topics"));
    }

    private PlanRequest reduceWindows(PlanRequest request) {
        Function<List<PlanRequest.AvailabilitySlot>, PlanRequest> build = kept ->
                DiagnosticInstances.withAvailability(request, kept);
        return build.apply(minimise(request.availability(), build, "windows"));
    }

    private PlanRequest reduceGoals(PlanRequest request) {
        if (request.goals().isEmpty()) {
            return request;
        }
        PlanRequest without = DiagnosticInstances.withGoals(request, List.of());
        if (reproduces(without)) {
            log.add("goals: removidas todas (" + request.goals().size() + ")");
            return without;
        }
        log.add("goals: remover a meta deixa de reproduzir; mantida");
        return request;
    }

    /** {@code ddmin} simplificado: nunca devolve lista vazia. */
    private <T> List<T> minimise(List<T> elements, Function<List<T>, PlanRequest> build,
            String label) {
        List<T> current = new ArrayList<>(elements);
        int chunk = Math.max(1, current.size() / 2);
        while (true) {
            boolean removed = false;
            int start = 0;
            while (start < current.size()) {
                List<T> candidate = new ArrayList<>(current);
                candidate.subList(start, Math.min(current.size(), start + chunk)).clear();
                if (!candidate.isEmpty() && reproduces(build.apply(candidate))) {
                    log.add(String.format(Locale.ROOT, "%s: %d -> %d (bloco %d)", label,
                            current.size(), candidate.size(), chunk));
                    current = candidate;
                    removed = true;
                } else {
                    start += chunk;
                }
            }
            if (!removed && chunk == 1) {
                return current;
            }
            chunk = removed ? chunk : Math.max(1, chunk / 2);
        }
    }

    static String traceHeader() {
        return "seed,generation,fittest_sessions,fittest_topics,F_selection,aggregate,raw_score,"
                + initialHeader() + ",terms";
    }

    static String initialHeader() {
        return "initial_size,initial_empty,initial_F_nan,initial_F_of_empty,"
                + "initial_best_F_nonempty,initial_getFittest_is_empty";
    }

    /**
     * A trajetória do melhor indivíduo: uma linha por geração, de 0 ao orçamento.
     *
     * @param replay  o replay da busca
     * @param request o pedido mínimo, já com motor e semente
     */
    static List<String> trace(SearchReplay replay, PlanRequest request, int generations) {
        List<String> rows = new ArrayList<>();
        EvolutionContext context = replay.context(request);
        String initial = initialPopulation(replay.timeline(request, 0).initial(), context);
        for (int generation = 0; generation <= generations; generation++) {
            TacticalStudyPlan fittest =
                    (TacticalStudyPlan) replay.timeline(request, generation).fittest();
            FitnessBreakdown b = context.fitnessEvaluator().explain(fittest, context);
            int topics = (int) fittest.getSchedule().values().stream().map(block -> block.item())
                    .distinct().count();
            rows.add(String.join(",", Long.toString(request.randomSeed()),
                    Integer.toString(generation), Integer.toString(fittest.getSchedule().size()),
                    Integer.toString(topics), Csv.num(b.selectionScore()), Csv.num(b.aggregate()),
                    Csv.num(b.rawScore()), initial, Csv.quote(terms(b))));
        }
        return rows;
    }

    /**
     * A geração zero: quantos vazios, quantos com {@code F} {@code NaN}, o {@code F} de um vazio, o
     * melhor {@code F} finito entre os não vazios, e se o melhor indivíduo pela regra de
     * {@code Population.getFittest} — {@code Comparator.comparingDouble} sobre a aptidão — é vazio.
     */
    static String initialPopulation(List<StudyPlan> initial, EvolutionContext context) {
        Population population = new Population(initial.size());
        initial.forEach(plan -> population.addIndividual(new Individual(plan)));
        population.calculateFitness(context);
        int empty = 0;
        int nan = 0;
        double ofEmpty = Double.NaN;
        double bestFilled = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < population.getSize(); index++) {
            Individual individual = population.getIndividual(index);
            double f = individual.getFitness();
            nan += Double.isNaN(f) ? 1 : 0;
            if (isEmpty(individual.getPlan())) {
                empty++;
                ofEmpty = f;
            } else if (!Double.isNaN(f)) {
                bestFilled = Math.max(bestFilled, f);
            }
        }
        return String.join(",", Integer.toString(initial.size()), Integer.toString(empty),
                Integer.toString(nan), empty == 0 ? "none" : Csv.num(ofEmpty), Csv.num(bestFilled),
                Boolean.toString(isEmpty(population.getFittest().getPlan())));
    }

    private static boolean isEmpty(StudyPlan plan) {
        return ((TacticalStudyPlan) plan).getSchedule().isEmpty();
    }

    /** A decomposição como texto: {@code nome=contribuição;...}. */
    static String terms(FitnessBreakdown breakdown) {
        return breakdown.terms().stream()
                .map(t -> t.name() + "=" + String.format(Locale.ROOT, "%.6f", t.weightedContribution()))
                .collect(Collectors.joining(";"));
    }
}
