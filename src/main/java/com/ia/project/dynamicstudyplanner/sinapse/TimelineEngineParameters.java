package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.plan.EngineParameters;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.sinapse.timeline.TimelineSearch;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Os parâmetros com que o motor {@code ga-timeline} roda: o mesmo orçamento da v1 e as taxas de
 * {@link TimelineSearch}, lidas de lá.
 *
 * <p>Só o que {@link TimelineSearch} expõe como valor. O elitismo de um indivíduo está escrito no
 * laço, não é parâmetro, e hipermutação não há; listá-los aqui como configuração afirmaria um botão
 * que não existe.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class TimelineEngineParameters implements EngineParameters {

    private final Map<String, Object> effective;

    public TimelineEngineParameters(GeneticSearchBudget budget) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("generations", budget.generations());
        values.put("population-size", budget.populationSize());
        values.put("crossover-rate", TimelineSearch.CROSSOVER_RATE);
        values.put("mutation-rate", TimelineSearch.MUTATION_RATE);
        this.effective = Collections.unmodifiableMap(values);
    }

    @Override
    public String engineId() {
        return TimelinePlanEngine.ID;
    }

    @Override
    public Map<String, Object> effective() {
        return effective;
    }
}
