package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.plan.EdgeProvenanceFilter;
import com.ia.project.dynamicstudyplanner.plan.PlanProtocol;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicies;
import com.ia.project.dynamicstudyplanner.plan.PrecedencePolicy;
import com.ia.project.dynamicstudyplanner.plan.PrerequisiteProvenance;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategies;
import com.ia.project.dynamicstudyplanner.sinapse.importance.ImportanceStrategy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * As três escolhas que uma requisição faz sobre <b>qual condição do experimento</b> ela roda.
 *
 * <h2>Por que as três viajam juntas</h2>
 *
 * Proveniência, importância e política de precedência são a mesma espécie de coisa: cada uma é um eixo
 * do fatorial, chega por {@code algorithmParams}, recusa um valor desconhecido com {@code 422} em vez
 * de normalizar, e é ecoada em {@code fitness} para que uma execução registrada seja reconstruível.
 * Lidas em conjunto, descrevem a célula; lidas separadamente, não descrevem nada.
 *
 * <p>O motivo imediato é mais prosaico e vale dizer: o construtor de {@link GeneticPlanEngine} estava
 * em <b>oito</b> parâmetros, o limite que o Checkstyle deste repositório impõe, e a política de
 * precedência seria o nono. Agrupar o que já era um conceito é a mudança que mantém o limite fazendo
 * o seu trabalho em vez de ser argumentado para baixo — o mesmo movimento que criou
 * {@link GeneticSearchBudget} quando o construtor chegou a nove pela primeira vez.
 */
@Component
@Profile(PlanProtocol.PROFILE)
public class RequestConditions {

    private final ImportanceStrategies importanceStrategies;
    private final PrerequisiteProvenance provenance;
    private final PrecedencePolicies precedencePolicies;

    /**
     * @param importanceStrategies de onde vem o peso do termo dominante da fitness
     * @param provenance           quais arestas de pré-requisito esta execução pode ver
     * @param precedencePolicies   se as preferências de ordem são reparadas ou precificadas
     */
    public RequestConditions(ImportanceStrategies importanceStrategies,
            PrerequisiteProvenance provenance, PrecedencePolicies precedencePolicies) {

        this.importanceStrategies = importanceStrategies;
        this.provenance = provenance;
        this.precedencePolicies = precedencePolicies;
    }

    /** @return a estratégia de importância desta requisição */
    public ImportanceStrategy importance(PlanRequest request) {
        return importanceStrategies.resolve(request);
    }

    /** @return a condição de ablação de arestas desta requisição */
    public EdgeProvenanceFilter provenance(PlanRequest request) {
        return provenance.resolve(request);
    }

    /**
     * @param request       a requisição
     * @param engineDefault o padrão deste motor; ver {@link PrecedencePolicies} para por que o padrão
     *                      é por motor e não global
     * @return a política de precedência desta requisição
     */
    public PrecedencePolicy precedence(PlanRequest request, PrecedencePolicy engineDefault) {
        return precedencePolicies.resolve(request, engineDefault);
    }
}
