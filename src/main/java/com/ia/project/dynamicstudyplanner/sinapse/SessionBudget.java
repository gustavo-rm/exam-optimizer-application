package com.ia.project.dynamicstudyplanner.sinapse;

import com.ia.project.dynamicstudyplanner.coreapi.contract.PlanRequest;
import com.ia.project.dynamicstudyplanner.ga.EvolutionContext;
import com.ia.project.dynamicstudyplanner.plan.AvailabilityAllocator;

/**
 * Quantas sessões um plano tem para distribuir — <b>uma derivação, compartilhada pelos dois motores
 * genéticos</b>.
 *
 * <h2>Por que é compartilhada, e não copiada</h2>
 *
 * Estava privada em {@code GeneticPlanEngine}. Com dois motores genéticos — a v1, que evolui a
 * alocação, e a v2, que evolui a ordem — uma segunda cópia da fórmula seria a diferença mais fácil de
 * introduzir sem perceber, e a mais difícil de ver depois: os dois motores receberiam orçamentos
 * diferentes e a comparação entre eles mediria isso junto com o que está sob teste.
 *
 * <p>O experimento exige que as condições difiram numa coisa. Então a fórmula mora aqui, e os dois a
 * chamam. A mudança é uma extração sem alteração de aritmética; {@code GaResultadoInalteradoTest} e a
 * matriz de medição confirmam que a v1 não se moveu.
 *
 * <h2>Por que é derivado e não configurado</h2>
 *
 * O cromossomo aloca um total fixo, e algo tem de dizer qual é. É o número de sessões de comprimento
 * médio que a disponibilidade cabe: minutos utilizáveis divididos pela média de
 * {@code estimatedMinutes}. Maior que isso e a busca otimizaria um plano que o calendário não cabe, e
 * a colocação truncaria a maior parte — o algoritmo estaria pontuando uma ficção. Menor e deixaria o
 * tempo declarado do aluno sem uso.
 *
 * <p>Com piso na <b>soma dos pisos por tópico</b>, que {@link SinapseMinimumDays} deriva de
 * {@code estimatedMinutes} sobre o dia de estudo medido. Um orçamento abaixo dessa soma torna a
 * população inicial insatisfazível, e {@code StudyPlanFactory} o recusaria com um {@code 422} que
 * nomeia a causa errada — o pedido pareceria inviável quando é o orçamento que é pequeno demais.
 */
public final class SessionBudget {

    private SessionBudget() {
    }

    /**
     * O orçamento de sessões deste pedido.
     *
     * @param request o pedido, para a disponibilidade e as durações estimadas
     * @param context o contexto, para os pisos por item
     * @return sessões a distribuir, nunca abaixo da soma dos pisos
     */
    public static int of(PlanRequest request, EvolutionContext context) {
        long minutes = AvailabilityAllocator.over(request).availableMinutes();
        double meanLength = request.topics().stream()
                .mapToInt(PlanRequest.Topic::estimatedMinutes)
                .average()
                .orElse(1.0);
        int affordable = (int) Math.floor(minutes / Math.max(1.0, meanLength));
        return Math.max(SinapseMinimumDays.totalFloor(context.minimumDaysPerItem()), affordable);
    }
}
