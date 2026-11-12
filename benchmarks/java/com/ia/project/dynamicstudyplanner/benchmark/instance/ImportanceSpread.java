package com.ia.project.dynamicstudyplanner.benchmark.instance;

import java.util.Arrays;

/**
 * Eixo {@code importance} da medição de curadoria (EOA-1): quanto as prioridades das metas se
 * afastam umas das outras.
 *
 * <h2>Os níveis saem do que a plataforma pode gerar, não de uma escala paralela</h2>
 *
 * Na plataforma, {@code PlanRequest.Goal.priority} é um inteiro de 1 a 5, uma meta por disciplina, e
 * uma meta criada sem prioridade recebe 3 ({@code GoalRequest}, {@code @Schema(defaultValue = "3")}).
 * No Core, {@code GoalPriorityImportance} propaga a prioridade da meta a todo tópico da disciplina e
 * nada mais. Então a única dispersão de importância que o Core pode receber hoje é a dispersão entre
 * as prioridades das metas, e os três níveis abaixo são três pontos desse espaço:
 *
 * <pre>
 *   FLAT     todas as metas em 3        dispersão zero; o que acontece se ninguém mexe no padrão
 *   CLOSE    a primeira em 4, as outras em 3   prioridades próximas
 *   EXTREME  a primeira em 5, as outras em 1   as pontas da escala
 * </pre>
 *
 * <p>Com uma disciplina só, os três níveis coincidem: uma meta não tem dispersão, seja qual for a
 * prioridade. É o regime do catálogo real de hoje (as três instâncias de
 * {@code src/test/resources/instances} têm uma meta, prioridade 2) e é por isso que a coluna gravada é
 * a dispersão <b>efetiva</b>, e não o nome do nível.
 */
public enum ImportanceSpread {

    /** Todas as metas na prioridade padrão da plataforma. */
    FLAT("flat", 3, 3),

    /** Uma meta um ponto acima das outras. */
    CLOSE("close", 4, 3),

    /** Uma meta no topo da escala e as outras no fundo. */
    EXTREME("extreme", 5, 1);

    private final String id;
    private final int first;
    private final int others;

    ImportanceSpread(String id, int first, int others) {
        this.id = id;
        this.first = first;
        this.others = others;
    }

    /** @return o nome gravado no CSV */
    public String id() {
        return id;
    }

    /**
     * As prioridades das metas, na ordem das disciplinas.
     *
     * @param subjects quantas disciplinas (uma meta cada)
     * @return a prioridade de cada meta
     */
    public int[] priorities(int subjects) {
        int[] priorities = new int[subjects];
        Arrays.fill(priorities, others);
        priorities[0] = first;
        return priorities;
    }
}
