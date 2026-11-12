package com.ia.project.dynamicstudyplanner.benchmark.instance;

/**
 * Eixo força da aresta da medição de curadoria (EOA-1): quantas das <b>mesmas</b> arestas são
 * {@code HARD}.
 *
 * <h2>Esta ablação reclassifica; a de proveniência remove</h2>
 *
 * A matriz publicada já varia {@code provenance}, e ela <b>tira arestas</b> do grafo: sob
 * {@code curated} as arestas {@code TEXTBOOK_ORDER} e {@code DERIVED} não existem para o motor. Este
 * eixo não tira nenhuma. O conjunto de pares (pré-requisito, dependente) é idêntico nos três níveis,
 * aresta por aresta; o que muda é só a coluna {@code strength}. É o que isola a pergunta da ADR 0014:
 * a mesma estrutura, classificada de dois jeitos, produz planos diferentes?
 *
 * <pre>
 *   ALL_SOFT  nenhuma HARD     o curador não classificou, e tudo virou preferência
 *   CURATED   24/35 HARD       a proporção do catálogo de exemplo MED-ANAT, só arestas a favor da posição
 *   ALL_HARD  todas HARD       o curador não classificou, e tudo virou requisito
 * </pre>
 *
 * <p>Os dois extremos são os dois contrafactuais de "não curar a força": não há terceiro valor no
 * contrato ({@code EdgeStrength} tem dois). Todos os níveis são acíclicos porque o gerador garante que
 * o conjunto <b>inteiro</b> de arestas é um DAG, não só a parte {@code HARD} dele.
 */
public enum EdgeStrengthMix {

    /** Nenhuma aresta {@code HARD}. */
    ALL_SOFT("all-soft"),

    /** A proporção do catálogo de exemplo, como um curador a escreveu. */
    CURATED("curated-mix"),

    /** Toda aresta {@code HARD}. */
    ALL_HARD("all-hard");

    private final String id;

    EdgeStrengthMix(String id) {
        this.id = id;
    }

    /** @return o nome gravado no CSV */
    public String id() {
        return id;
    }
}
