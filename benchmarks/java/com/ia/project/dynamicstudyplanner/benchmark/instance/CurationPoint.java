package com.ia.project.dynamicstudyplanner.benchmark.instance;

import java.util.Locale;

/**
 * As coordenadas de uma instância da medição de curadoria (EOA-1).
 *
 * <h2>O que entra na estrutura e o que não entra</h2>
 *
 * Só {@code subjects} e {@code replicate} decidem os tópicos, as faixas latentes e as arestas. Os
 * outros quatro eixos mudam <b>como</b> a mesma estrutura é enviada — prioridade das metas,
 * classificação das arestas, tabela de minutos, calendário —, nunca quais tópicos e quais pares
 * existem. É isso que torna a comparação entre níveis pareada de verdade: duas linhas que diferem
 * num eixo têm os mesmos identificadores de tópico e as mesmas arestas.
 *
 * @param subjects   quantas disciplinas, de {@value CurationLibrary#TOPICS_PER_SUBJECT} tópicos cada
 * @param replicate  qual sorteio da estrutura, a unidade de replicação entre instâncias
 * @param importance a dispersão das prioridades das metas
 * @param strength   quantas das arestas são {@code HARD}
 * @param effort     a anotação de faixa e a tabela de minutos
 * @param tightness  minutos disponíveis ÷ minutos demandados <b>nominais</b> (tabela da plataforma
 *                   sobre as faixas latentes) — ver {@code CurationLibrary}
 */
public record CurationPoint(
        int subjects,
        int replicate,
        ImportanceSpread importance,
        EdgeStrengthMix strength,
        EffortMapping effort,
        double tightness) {

    /** @return quantos tópicos a instância tem */
    public int topics() {
        return subjects * CurationLibrary.TOPICS_PER_SUBJECT;
    }

    /** @return {@code s2-r1-close-curated-mix-tiers-platform-a1.0}, derivado das coordenadas */
    public String id() {
        return String.format(Locale.ROOT, "s%d-r%d-%s-%s-%s-a%.1f", subjects, replicate,
                importance.id(), strength.id(), effort.id(), tightness);
    }

    /** @return o mesmo ponto com outra dispersão de importância */
    public CurationPoint with(ImportanceSpread level) {
        return new CurationPoint(subjects, replicate, level, strength, effort, tightness);
    }

    /** @return o mesmo ponto com outra classificação de arestas */
    public CurationPoint with(EdgeStrengthMix level) {
        return new CurationPoint(subjects, replicate, importance, level, effort, tightness);
    }

    /** @return o mesmo ponto com outra anotação de esforço */
    public CurationPoint with(EffortMapping level) {
        return new CurationPoint(subjects, replicate, importance, strength, level, tightness);
    }

    /** @return o mesmo ponto com outro número de disciplinas */
    public CurationPoint withSubjects(int count) {
        return new CurationPoint(count, replicate, importance, strength, effort, tightness);
    }

    /** @return o mesmo ponto com outra folga */
    public CurationPoint withTightness(double level) {
        return new CurationPoint(subjects, replicate, importance, strength, effort, level);
    }

    /** @return o mesmo ponto com outro sorteio da estrutura */
    public CurationPoint withReplicate(int level) {
        return new CurationPoint(subjects, level, importance, strength, effort, tightness);
    }
}
