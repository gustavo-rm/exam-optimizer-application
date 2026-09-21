package com.ia.project.dynamicstudyplanner.domain;

import java.util.List;

/**
 * A fitness de um plano, decomposta nos termos que a produziram.
 *
 * <h2>O que isto resolve (GAP-07)</h2>
 *
 * A fitness saía como um {@code double} único. Um número sem unidade não responde "por que este
 * plano": não dá para dizer se um plano ganhou por cobrir mais edital, por reter melhor, ou apenas
 * por violar menos uma restrição. A função objetivo <b>já</b> tem termos com pesos declarados
 * ({@code ga/fitness/FitnessWeights}); o que faltava era publicá-los.
 *
 * <p>Os nomes em {@link Term#name()} são <b>estáveis</b>: viram chave de API e de análise da tese,
 * então renomear um é uma quebra de contrato, não uma refatoração.
 *
 * <h2>A aritmética, exatamente como o avaliador a faz</h2>
 *
 * <pre>
 *   rawScore      = SOMA dos weightedContribution de todos os termos
 *   boundedScore  = clamp(rawScore, 0, 1)
 *   penaltyFactor = PRODUTO dos fatores multiplicativos
 *   aggregate     = boundedScore * penaltyFactor
 * </pre>
 *
 * <p><b>{@code rawScore} não é necessariamente igual a {@code aggregate}, e isso é a verdade do
 * código, não uma imprecisão desta decomposição.</b> Dois passos ficam entre eles. O
 * {@code clamp} morde quando as violações de restrição levam a soma abaixo de zero — um plano
 * inviável não fica com fitness negativa, fica com zero. E as penalidades multiplicam. No caminho
 * macro elas devolvem {@code 1.0} por construção, então o produto é a identidade e ali
 * {@code aggregate == boundedScore}; mas escrever a igualdade como se valesse sempre esconderia o
 * passo que a camada tática usa.
 *
 * <p>Quem quiser verificar a composição confere a cadeia inteira, não um atalho.
 *
 * <h2>Aditivo e multiplicativo são separados de propósito</h2>
 *
 * Um {@link Term} entra por soma e tem peso; uma {@link Penalty} entra por produto e não tem. Dar
 * um campo "peso" a uma penalidade obrigaria a inventar um número que não existe no cálculo. Duas
 * listas dizem a verdade sobre dois mecanismos diferentes.
 *
 * @param terms         os termos aditivos ativos nesta execução: objetivos (contribuição positiva)
 *                      e restrições (negativa), na ordem em que o avaliador os aplicou
 * @param penalties     os fatores multiplicativos ativos, pelos nomes das classes
 * @param rawScore      a soma das contribuições ponderadas, antes do limite
 * @param boundedScore  {@code rawScore} limitado a {@code [0,1]}
 * @param penaltyFactor o produto dos fatores; {@code 1.0} quando nenhuma penalidade incide
 * @param aggregate     a fitness final — o mesmo {@code double} que o avaliador devolve
 */
public record FitnessBreakdown(
        List<Term> terms,
        List<Penalty> penalties,
        double rawScore,
        double boundedScore,
        double penaltyFactor,
        double aggregate
) {

    /** Cópias defensivas: um detalhamento que mudasse depois de publicado não explicaria nada. */
    public FitnessBreakdown {
        terms = List.copyOf(terms);
        penalties = List.copyOf(penalties);
    }

    /** Como um termo entra na soma. */
    public enum TermKind {

        /** Objetivo: contribui positivamente, com o peso declarado. */
        OBJECTIVE,

        /** Restrição: subtrai peso vezes severidade da violação. */
        CONSTRAINT
    }

    /**
     * Um termo aditivo da fitness.
     *
     * @param name                 nome estável do termo; chave de API e de análise
     * @param kind                 se soma ou subtrai
     * @param value                o valor normalizado em {@code [0,1]} — recompensa para um
     *                             objetivo, severidade da violação para uma restrição
     * @param weight               o peso declarado do termo
     * @param weightedContribution o que este termo de fato somou ao {@code rawScore}; já negativo
     *                             para uma restrição, para que a soma seja simples
     */
    public record Term(String name, TermKind kind, double value, double weight,
                       double weightedContribution) {
    }

    /**
     * Um fator multiplicativo.
     *
     * @param name   nome da classe da penalidade
     * @param factor o fator aplicado; {@code 1.0} significa "não incidiu"
     */
    public record Penalty(String name, double factor) {
    }
}
