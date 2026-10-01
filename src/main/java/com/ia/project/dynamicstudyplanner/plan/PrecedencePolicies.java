package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolve a política de precedência de uma requisição.
 *
 * <h2>O padrão é por MOTOR, e não global — e isso é deliberado</h2>
 *
 * Cada motor passa o seu próprio padrão: o macro pede {@code LEXICOGRAPHIC} e o de linha do tempo
 * pede {@code WEIGHTED}, que é o comportamento que cada um já tinha. Um padrão global obrigaria um
 * dos dois a mudar de comportamento, e <b>os dois são resultados publicados</b> — o macro com reparo é
 * o grupo de controle de {@code docs/revisao-ag/09} e {@code /10}, e a linha do tempo com preço é o
 * tratamento medido em {@code /10} e varrido em {@code /11}.
 *
 * <p>Mudar qualquer um deles por efeito colateral de um refactor contaminaria a condição de controle,
 * e os números publicados deixariam de reproduzir. A assimetria do padrão é o que garante que a
 * introdução do eixo seja <b>inerte</b> até alguém pedir uma célula nova.
 *
 * <h2>Um identificador desconhecido é recusado, não normalizado</h2>
 *
 * Mesmo motivo de {@link PlanEngineSelector} e {@link PrerequisiteProvenance}: cair no padrão rodaria
 * a condição A enquanto quem chamou registrou a condição B, e nada rio abaixo teria como detectar.
 *
 * <h2>O guloso não participa deste eixo</h2>
 *
 * Ele não repara nem precifica — não avalia fitness nenhuma —, e que não repare é decisão registrada
 * ({@code CLAUDE.md} §1b, travada por
 * {@code GreedyBaselineSchedulerTest.softEdgesDoNotConstrainTheOrder}). Ele ignora o parâmetro, como
 * já ignora {@code importance}.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class PrecedencePolicies {

    /**
     * A política desta requisição.
     *
     * @param request       a requisição
     * @param engineDefault o que usar quando a requisição não nomeia nenhuma; ver o comentário da
     *                      classe para por que ele é do motor e não global
     * @return a política a aplicar
     * @throws PlanRejectedException com {@code 422} quando o identificador não é de nenhuma política
     */
    public PrecedencePolicy resolve(PlanRequest request, PrecedencePolicy engineDefault) {
        Object requested = request.algorithmParams().get(PrecedencePolicy.PARAM);
        if (requested == null) {
            return engineDefault;
        }
        if (!(requested instanceof String name)) {
            throw new PlanRejectedException("unusable-precedence-policy",
                    "algorithmParams." + PrecedencePolicy.PARAM + " must be a string naming one of "
                            + PrecedencePolicy.ids() + ".",
                    List.of(String.valueOf(requested)));
        }
        PrecedencePolicy policy = PrecedencePolicy.byId(name);
        if (policy == null) {
            throw new PlanRejectedException("unknown-precedence-policy",
                    "No precedence policy answers to '" + name + "'. Known policies: "
                            + PrecedencePolicy.ids() + ". The request is refused rather than run on "
                            + "the default, because a plan recorded under the wrong condition is "
                            + "worse than no plan.",
                    List.of(name));
        }
        return policy;
    }
}
