package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.ga.config.DefaultGeneticAlgorithmFactory;
import com.ia.project.dynamicstudyplanner.plan.EngineParameters;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Os parâmetros com que o motor {@code ga} roda: o orçamento configurado e as taxas da fábrica.
 *
 * <p>Nada aqui é escolhido por requisição. {@code generations} e {@code population-size} vêm de
 * {@code plan.engine.ga.*}; as taxas são constantes de {@link DefaultGeneticAlgorithmFactory}, lidas
 * de lá e não repetidas, para que este log não possa divergir do que a busca usa.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class GeneticEngineParameters implements EngineParameters {

    private final Map<String, Object> effective;

    public GeneticEngineParameters(GeneticSearchBudget budget) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("generations", budget.generations());
        values.put("population-size", budget.populationSize());
        values.put("crossover-rate", DefaultGeneticAlgorithmFactory.CROSSOVER_RATE);
        values.put("mutation-rate", DefaultGeneticAlgorithmFactory.MUTATION_RATE);
        values.put("elitism", DefaultGeneticAlgorithmFactory.ELITISM);
        values.put("stagnation-patience", DefaultGeneticAlgorithmFactory.STAGNATION_PATIENCE);
        values.put("hypermutation-rate", DefaultGeneticAlgorithmFactory.HYPERMUTATION_RATE);
        this.effective = Collections.unmodifiableMap(values);
    }

    @Override
    public String engineId() {
        return GeneticPlanEngine.ID;
    }

    @Override
    public Map<String, Object> effective() {
        return effective;
    }
}
