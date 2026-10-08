package com.ia.project.dynamicstudyplanner.plan;

import java.util.Map;

/**
 * Os parâmetros de busca com que um motor <b>de fato</b> roda, para o log de cada requisição.
 *
 * <h2>Por que existe</h2>
 *
 * O dono do projeto decidiu em 08/10/2026 que os hiperparâmetros do AG são propriedade do Core
 * (D4, Variante 1; {@code docs/adr/0009-hiperparametros-sao-do-core.md}). A plataforma continua
 * enviando {@code generations}, {@code population-size} e {@code mutation-rate} em
 * {@code algorithmParams}, e o Core continua sem aplicá-los. O que muda é que isso deixa de ser
 * silencioso: {@link AlgorithmParamsLog} registra as chaves ignoradas e, ao lado delas, os valores que
 * rodaram — lidos daqui.
 *
 * <h2>Por que a interface mora em {@code plan} e as implementações não</h2>
 *
 * Os valores vivem em {@code sinapse} e em {@code ga}, que já dependem de {@code plan}. Importá-los
 * daqui criaria um ciclo entre módulos, que {@code ModuleBoundaryTest} reprova. A interface fica com
 * quem a consome, como pede a mensagem daquele teste.
 *
 * <p>Um motor sem parâmetro de busca, como o guloso, simplesmente não registra implementação.
 */
public interface EngineParameters {

    /** @return o id do motor a que estes parâmetros pertencem, como em {@link PlanEngine#id()} */
    String engineId();

    /**
     * @return nome do parâmetro, no estilo das chaves de {@code algorithmParams}, para o valor
     *         efetivo; em ordem estável, para que duas linhas de log se comparem a olho
     */
    Map<String, Object> effective();
}
