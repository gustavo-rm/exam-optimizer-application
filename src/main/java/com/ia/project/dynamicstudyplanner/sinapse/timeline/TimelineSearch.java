package com.ia.project.dynamicstudyplanner.sinapse.timeline;

import com.ia.project.dynamicstudyplanner.domain.tactical.TacticalStudyPlan;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.ga.Individual;
import com.ia.project.dynamicstudyplanner.ga.Population;
import com.ia.project.dynamicstudyplanner.ga.strategy.selection.SelectionStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.repair.ChromosomeRepairer;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.crossover.TacticalCrossoverStrategy;
import com.ia.project.dynamicstudyplanner.ga.tactical.strategy.mutation.TacticalMutationStrategy;

import java.util.List;

/**
 * O laço de evolução da v2: uma população de cromossomos táticos, selecionada, recombinada, mutada e
 * reparada.
 *
 * <h2>Por que um laço novo em vez de {@code GeneticAlgorithm}</h2>
 *
 * O laço macro move {@code Individual}s por aptidão e nunca toca genes — é por isso que
 * {@code CLAUDE.md} §4 diz que ele não precisa mudar quando a unidade de planejamento muda. Mas os
 * <b>operadores</b> que ele chama são tipados em {@code int[]} alinhado a um
 * {@code PlanningItemIndex}: {@code CrossoverStrategy} e {@code MutationStrategy} recebem
 * {@code Individual} e devolvem {@code Individual}, e os três operadores concretos leem vetores de
 * genes. O cromossomo tático não é um vetor de genes, e sim um mapa de slots.
 *
 * <p>Então este laço existe ao lado do outro, e <b>nenhuma classe da lista de não-mexer foi
 * tocada</b>: {@code TournamentSelection} é reusada como está, porque ela só compara aptidões.
 *
 * <h2>A ordem das etapas, e por que o reparo de calendário é o último</h2>
 *
 * <ol>
 *   <li>seleção por torneio, a mesma da v1;</li>
 *   <li>{@code DayBoundaryCrossover} — recombina dias inteiros de dois pais;</li>
 *   <li>{@code BlockSwapMutation} — o operador de <b>ordem</b>, que é a hipótese da v2;</li>
 *   <li>{@code MethodologyMutation} — o eixo de intensidade;</li>
 *   <li>{@code SpacedRepetitionRepairer} — devolve as revisões obrigatórias que os operadores
 *       tenham atropelado;</li>
 *   <li>{@link TimelineRepairer} — ordem topológica e reempacotamento do calendário.</li>
 * </ol>
 *
 * <p>O reparo de calendário vem <b>por último</b> porque é ele que restabelece as invariantes de
 * saída, e qualquer etapa depois dele poderia quebrá-las de novo. O reparo de retenção vem antes dele
 * porque sobrescreve blocos e muda durações, e o reempacotamento precisa ver o multiconjunto final.
 *
 * <h2>Elitismo, e o que deliberadamente não tem</h2>
 *
 * O melhor indivíduo passa intacto para a geração seguinte, como no laço macro. <b>Não há
 * hipermutação</b>: o laço macro a usa contra estagnação, com paciência configurada, e acrescentá-la
 * aqui sem medir se a v2 estagna seria importar um mecanismo por simetria. Fica como possível
 * trabalho futuro, medido em vez de suposto.
 */
public final class TimelineSearch {

    /** As mesmas taxas do laço macro, para que a comparação não inclua a diferença de taxas. */
    public static final double CROSSOVER_RATE = 0.95;

    /** Idem. Ver {@code DefaultGeneticAlgorithmFactory}. */
    public static final double MUTATION_RATE = 0.05;

    private final SelectionStrategy selection;
    private final TacticalCrossoverStrategy crossover;
    private final TacticalMutationStrategy orderMutation;
    private final TacticalMutationStrategy methodologyMutation;
    private final ChromosomeRepairer retentionRepairer;
    private final TimelineRepairer timelineRepairer;

    /**
     * @param selection            seleção, reusada do caminho macro
     * @param crossover            recombinação por fronteira de dia
     * @param orderMutation        o operador de ordem
     * @param methodologyMutation  o operador de intensidade
     * @param retentionRepairer    devolve revisões obrigatórias perdidas
     * @param timelineRepairer     ordem topológica e reempacotamento; roda por último
     */
    public TimelineSearch(SelectionStrategy selection, TacticalCrossoverStrategy crossover,
            TacticalMutationStrategy orderMutation, TacticalMutationStrategy methodologyMutation,
            ChromosomeRepairer retentionRepairer, TimelineRepairer timelineRepairer) {

        this.selection = selection;
        this.crossover = crossover;
        this.orderMutation = orderMutation;
        this.methodologyMutation = methodologyMutation;
        this.retentionRepairer = retentionRepairer;
        this.timelineRepairer = timelineRepairer;
    }

    /**
     * Evolui e devolve o cromossomo mais apto.
     *
     * @param seeds       a população inicial, já reparada
     * @param context     o contexto
     * @param generations quantas gerações evoluir
     * @return o plano tático mais apto da última geração
     */
    public TacticalStudyPlan run(List<TacticalStudyPlan> seeds, EvolutionContext context,
            int generations) {

        Population population = new Population(seeds.size());
        seeds.forEach(plan -> population.addIndividual(new Individual(plan)));
        population.calculateFitness(context);

        Population current = population;
        for (int generation = 0; generation < generations; generation++) {
            current = evolve(current, context);
        }
        return (TacticalStudyPlan) current.getFittest().getPlan();
    }

    /**
     * Uma geração.
     *
     * <p>Sequencial de propósito, pela mesma razão que o laço macro: qual thread saca qual número
     * depende do escalonamento, e duas execuções com a mesma semente deixariam de concordar. Ver a
     * nota em {@code GeneticAlgorithm.evolvePopulation} e {@code CLAUDE.md} §3.
     */
    private Population evolve(Population population, EvolutionContext context) {
        Population next = new Population(population.getSize());
        next.addIndividual(new Individual(population.getFittest().getPlan()));

        while (next.getSize() < population.getSize()) {
            next.addIndividual(new Individual(offspring(population, context)));
        }

        next.calculateFitness(context);
        return next;
    }

    /** Um descendente: recombinação, as duas mutações, e os dois reparos na ordem que importa. */
    private TacticalStudyPlan offspring(Population population, EvolutionContext context) {
        TacticalStudyPlan first = (TacticalStudyPlan) selection.select(population).getPlan();
        TacticalStudyPlan second = (TacticalStudyPlan) selection.select(population).getPlan();

        TacticalStudyPlan child = crossover.crossover(first, second, CROSSOVER_RATE, context);
        child = orderMutation.mutate(child, MUTATION_RATE, context);
        child = methodologyMutation.mutate(child, MUTATION_RATE, context);
        child = retentionRepairer.repair(child, context);
        return timelineRepairer.repair(child, context);
    }
}
