package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Torna visível, por requisição, o que o Core fez com {@code algorithmParams}.
 *
 * <h2>Por que existe</h2>
 *
 * D4, decidida pelo dono em 08/10/2026: os hiperparâmetros do AG são do Core (Variante 1,
 * {@code docs/adr/0009-hiperparametros-sao-do-core.md}). {@code algorithmParams} continua aceito,
 * porque o contrato o prevê, e as chaves fora do conjunto fechado continuam sem efeito — o item 0 do
 * EOA-13 confirmou isso por execução. Até aqui, porém, o descarte era silencioso, e quem enviava
 * {@code generations: 400} não tinha como saber que rodaram 60.
 *
 * <h2>Duas linhas</h2>
 *
 * <ul>
 *   <li>{@code WARN}, só quando há o que avisar: os <b>nomes</b> das chaves recebidas que não estão
 *       em {@link #APPLIED_KEYS}. Nunca os valores — são entrada de quem chama, e entrada de quem
 *       chama não vai para o agregador de logs ({@code LogPrivacyTest});</li>
 *   <li>{@code INFO}, sempre: o motor escolhido e os parâmetros de busca com que ele roda, lidos de
 *       {@link EngineParameters}. Um motor sem implementação registrada, como o guloso, não tem
 *       parâmetro de busca, e a linha diz isso com um mapa vazio.</li>
 * </ul>
 *
 * <p>Só registra; não altera a requisição, a resposta nem o plano.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class AlgorithmParamsLog {

    private static final Logger log = LoggerFactory.getLogger(AlgorithmParamsLog.class);

    /**
     * A chave de importância. Repetida aqui como texto porque a constante original,
     * {@code ImportanceStrategies.PARAM}, mora em {@code sinapse}, e importá-la criaria um ciclo entre
     * módulos. {@code AlgorithmParamsLogTest} trava que as duas são iguais.
     */
    public static final String IMPORTANCE_PARAM = "importance";

    /** O conjunto fechado de chaves de {@code algorithmParams} que o Core aplica. */
    public static final Set<String> APPLIED_KEYS = Set.of(PlanEngineSelector.ENGINE_PARAM,
            IMPORTANCE_PARAM, PrecedencePolicy.PARAM, EdgeProvenanceFilter.PARAM);

    private final Map<String, Map<String, Object>> byEngine;

    /**
     * @param parameters os parâmetros efetivos de cada motor que os tem; dois para o mesmo motor
     *                   são erro de montagem e impedem a subida
     */
    public AlgorithmParamsLog(List<EngineParameters> parameters) {
        this.byEngine = parameters.stream().collect(Collectors.toUnmodifiableMap(
                EngineParameters::engineId, EngineParameters::effective));
    }

    /**
     * Registra as duas linhas desta requisição.
     *
     * @param request  a requisição como chegou
     * @param engineId o motor que vai respondê-la
     */
    public void record(PlanRequest request, String engineId) {
        List<String> ignored = ignoredKeys(request);
        if (!ignored.isEmpty()) {
            log.warn("algorithmParams keys not applied by the Core and ignored: {}. Applied keys: {}. "
                    + "Search parameters are owned by the Core (ADR 0009).", ignored, appliedKeys());
        }
        log.info("Plan runs on engine {} with effective search parameters {}", engineId,
                effectiveParameters(engineId));
    }

    /**
     * @param request a requisição
     * @return os nomes das chaves recebidas fora de {@link #APPLIED_KEYS}, em ordem alfabética
     */
    public List<String> ignoredKeys(PlanRequest request) {
        return request.algorithmParams().keySet().stream()
                .filter(key -> !APPLIED_KEYS.contains(key))
                .sorted()
                .toList();
    }

    /**
     * @param engineId o id de um motor
     * @return os parâmetros de busca com que ele roda; vazio para um motor sem busca
     */
    public Map<String, Object> effectiveParameters(String engineId) {
        return byEngine.getOrDefault(engineId, Map.of());
    }

    private static List<String> appliedKeys() {
        return APPLIED_KEYS.stream().sorted().toList();
    }
}
