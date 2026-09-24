package com.ia.project.dynamicstudyplanner.sinapse.importance;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PlanRejectedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Chooses which meaning {@code importance} has for a request, and guarantees the answer says which.
 *
 * <h2>The same mechanism as the engine choice, on purpose</h2>
 *
 * Selected per request from {@code algorithmParams.importance}, defaulting to
 * {@code plan.fitness.sinapse.importance-strategy}. {@code algorithmParams} is
 * {@code Map<String, Object>} in the contract, so carrying the choice there invents no field.
 *
 * <p>Per request, and not per deployment, for the reason the engine choice is: the two are
 * conditions of one comparison, and conditions that need two deploys cannot be compared without also
 * comparing the deploys. Switching strategy therefore requires <b>no recompile and no redeploy</b>.
 *
 * <h2>An unknown strategy is refused, not defaulted</h2>
 *
 * Falling back would run one meaning of {@code importance} while the caller recorded another — and
 * this is the input to the term carrying half the fitness, so the resulting measurements would be
 * wrong in a way nothing downstream could detect. Same reasoning as {@code PlanEngineSelector}, and
 * the same refusal shape.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class ImportanceStrategies {

    /** The {@code algorithmParams} key carrying the caller's choice. */
    public static final String PARAM = "importance";

    private final Map<String, ImportanceStrategy> byId;
    private final String defaultStrategyId;

    /**
     * @param strategies        every registered strategy, injected by Spring
     * @param defaultStrategyId the id to use when the request names none. There is no code default:
     *                          a deployment that cannot say what {@code importance} means fails to
     *                          start rather than answering with a meaning nobody chose
     */
    public ImportanceStrategies(List<ImportanceStrategy> strategies,
            @Value("${plan.fitness.sinapse.importance-strategy}") String defaultStrategyId) {

        Map<String, ImportanceStrategy> registry = new TreeMap<>();
        for (ImportanceStrategy strategy : strategies) {
            ImportanceStrategy clash = registry.put(strategy.id(), strategy);
            if (clash != null) {
                throw new IllegalStateException("Two importance strategies answer to the id '"
                        + strategy.id() + "': " + clash.getClass().getName() + " and "
                        + strategy.getClass().getName() + ". The id is how a stored plan is "
                        + "attributed to a condition of the comparison, so it has to be unique.");
            }
        }
        // Map.copyOf e seguro aqui: este registro e lido por get e por registeredIds(), que ordena.
        // A ordem de iteracao embaralhada por execucao da JVM, que Map.copyOf produz, nao alcanca
        // aritmetica nenhuma. Ver SinapseAdapterIsolationTest para a regra e para o que deu errado
        // quando um mapa POR ITEM usou Map.copyOf.
        this.byId = Map.copyOf(registry);
        this.defaultStrategyId = defaultStrategyId;

        if (!byId.containsKey(defaultStrategyId)) {
            throw new IllegalStateException("plan.fitness.sinapse.importance-strategy is '"
                    + defaultStrategyId + "', which no registered strategy answers to. Registered: "
                    + registeredIds() + ". Failing at startup rather than on the first request.");
        }
    }

    /**
     * The strategy this request runs on.
     *
     * @param request the request as the platform sent it
     * @return the chosen strategy
     * @throws PlanRejectedException when the request names a strategy that does not exist
     */
    public ImportanceStrategy resolve(PlanRequest request) {
        Object requested = request.algorithmParams().get(PARAM);
        if (requested == null) {
            return byId.get(defaultStrategyId);
        }
        if (!(requested instanceof String name)) {
            throw new PlanRejectedException("unusable-importance-strategy",
                    "algorithmParams." + PARAM + " must be a string naming one of the registered "
                            + "importance strategies: " + registeredIds() + ".",
                    List.of(String.valueOf(requested)));
        }
        ImportanceStrategy strategy = byId.get(name);
        if (strategy == null) {
            throw new PlanRejectedException("unknown-importance-strategy",
                    "No importance strategy answers to '" + name + "'. Registered: "
                            + registeredIds() + ". The request is refused rather than run on the "
                            + "default, because importance feeds the term carrying half the fitness "
                            + "and a plan recorded under the wrong meaning is worse than no plan.",
                    List.of(name));
        }
        return strategy;
    }

    /** @return the registered ids, sorted, for an error message a caller can act on */
    public List<String> registeredIds() {
        return byId.keySet().stream().sorted(Comparator.naturalOrder()).toList();
    }
}
