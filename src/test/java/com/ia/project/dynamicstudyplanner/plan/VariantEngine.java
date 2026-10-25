package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;

/**
 * Uma cópia de teste de um motor de produção, com a colocação escolhida por {@link PlacementVariant}.
 *
 * <p>Em {@link PlacementVariant#BASE} a cópia tem de devolver a <b>mesma</b> resposta do motor de
 * produção, byte a byte no hash canônico; a medição confere isso em toda célula antes de olhar P.
 */
public interface VariantEngine {

    /** @return o id do motor de produção que esta cópia reproduz */
    String id();

    /**
     * Planeja.
     *
     * @param request o pedido
     * @return a resposta e a leitura de prefixo
     * @throws PlanRejectedException quando o plano sairia vazio, como no motor de produção
     */
    VariantPlan plan(PlanRequest request);
}
