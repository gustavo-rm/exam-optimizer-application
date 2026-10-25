package com.ia.project.dynamicstudyplanner.plan;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanResponse;

import java.util.List;

/**
 * A resposta de uma cópia de motor, e o que o código de produção teria nomeado como não agendado.
 *
 * <p>{@code prefixReading} é a lista que {@code PrerequisiteReport} monta hoje, supondo que o plano é
 * um prefixo da ordem ({@code skip(scheduled)}). Sob P essa suposição cai, e a regra P9 de
 * {@code docs/MEDICAO_ALOCADOR.md} manda a resposta nomear os ausentes de fato. A medição compara as
 * duas para dizer quantas respostas sairiam com a lista errada sem ajuste. Vazia numa recusa.
 *
 * @param response      a resposta, ou {@code null} numa recusa
 * @param prefixReading o que a leitura de prefixo teria nomeado
 */
public record VariantPlan(PlanResponse response, List<String> prefixReading) {

    public VariantPlan {
        prefixReading = List.copyOf(prefixReading);
    }
}
