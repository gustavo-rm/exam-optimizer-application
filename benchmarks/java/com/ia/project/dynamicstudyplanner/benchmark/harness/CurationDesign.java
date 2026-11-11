package com.ia.project.dynamicstudyplanner.benchmark.harness;

import com.ia.project.dynamicstudyplanner.benchmark.instance.CurationPoint;
import com.ia.project.dynamicstudyplanner.benchmark.instance.EdgeStrengthMix;
import com.ia.project.dynamicstudyplanner.benchmark.instance.EffortMapping;
import com.ia.project.dynamicstudyplanner.benchmark.instance.ImportanceSpread;

import java.util.ArrayList;
import java.util.List;

/**
 * A matriz da medição de curadoria (EOA-1), e o critério pelo qual ela não é o produto cartesiano.
 *
 * <h2>O produto inteiro, e por que não ele</h2>
 *
 * <pre>
 *   subjects 3 × importance 3 × strength 3 × effort 4 × tightness 3 × replicate 3
 *     = 972 instâncias × 3 motores × 5 sementes = 14 580 linhas
 * </pre>
 *
 * Medido antes de desenhar: uma linha da v2 custa ~0,55 s com 35 tópicos, ~1,0 s com 70 e ~1,5 s com
 * 105 (duas chamadas por linha); o guloso e a v1 somam menos de 5% disso. O produto inteiro levaria
 * ~100 min. Dá para rodar — e a redução abaixo não é por tempo, é porque metade do produto mede
 * combinações que não respondem pergunta nenhuma desta tarefa: com uma disciplina o eixo de
 * importância é degenerado por construção (uma meta, dispersão zero), e o cruzamento completo de
 * tamanho com os três eixos curados mede a interação de quatro fatores que ninguém perguntou.
 *
 * <h2>O desenho: um fatorial no tamanho do piloto, e o tamanho variado sobre ele</h2>
 *
 * <ul>
 *   <li><b>Bloco F</b> — fatorial completo dos três eixos curados, {@code 3 × 3 × 4 = 36} pontos, em
 *       {@value #PILOT_SUBJECTS} disciplinas (70 tópicos): o piloto terá mais de uma disciplina, e duas
 *       é o menor número em que o eixo de importância existe. Completo e não um-de-cada-vez porque a
 *       pergunta é <i>em que regime</i> uma distinção paga, e "a força da aresta só importa quando as
 *       prioridades divergem" é uma interação que uma varredura um-de-cada-vez não enxerga.</li>
 *   <li><b>Bloco S</b> — 1 e 3 disciplinas (35 e 105 tópicos), cada eixo curado variado um de cada vez
 *       em volta do centro: 8 pontos por tamanho. Responde se o que o bloco F achou continua valendo
 *       no tamanho de hoje (o catálogo de exemplo) e no de três disciplinas.</li>
 * </ul>
 *
 * Os dois blocos cruzam inteiros com {@link #TIGHTNESS}, {@link #REPLICATES}, os três motores e as
 * cinco sementes: {@code (36 + 16) × 3 × 3 × 3 × 5 = 7 020} linhas, ~40 min estimados.
 *
 * <h2>O centro</h2>
 *
 * {@code close} / {@code curated-mix} / {@code tiers-platform}: prioridades próximas (o padrão da
 * plataforma, 3, com uma meta acima), a proporção {@code HARD}/{@code SOFT} do catálogo, e a tabela de
 * minutos que a plataforma usa hoje. É o ponto mais parecido com o que o piloto mandaria.
 */
public final class CurationDesign {

    /** Disciplinas do bloco F: o menor piloto multidisciplina. */
    public static final int PILOT_SUBJECTS = 2;

    /** Disciplinas do bloco S. */
    public static final List<Integer> SIZE_SUBJECTS = List.of(1, 3);

    /** As folgas de {@code InstanceLibrary}, mantidas para que o regime seja legível contra 09–12. */
    public static final List<Double> TIGHTNESS = List.of(0.7, 1.0, 1.5);

    /** Sorteios da estrutura: a replicação entre instâncias, além da entre sementes. */
    public static final List<Integer> REPLICATES = List.of(1, 2, 3);

    /** O centro do desenho. */
    public static final ImportanceSpread CENTER_IMPORTANCE = ImportanceSpread.CLOSE;

    /** O centro do desenho. */
    public static final EdgeStrengthMix CENTER_STRENGTH = EdgeStrengthMix.CURATED;

    /** O centro do desenho. */
    public static final EffortMapping CENTER_EFFORT = EffortMapping.PLATFORM;

    /**
     * Um ponto da matriz e o papel dele.
     *
     * @param role  {@code factorial} no bloco F; no bloco S, {@code size} para o centro e
     *              {@code size-importance}, {@code size-strength} ou {@code size-effort} para o eixo
     *              que o ponto varia
     * @param point as coordenadas
     */
    public record Cell(String role, CurationPoint point) {
    }

    private CurationDesign() {
    }

    /** @return todas as células, sorteio por fora, depois folga, depois os pontos */
    public static List<Cell> cells() {
        List<Cell> cells = new ArrayList<>();
        for (int replicate : REPLICATES) {
            for (double tightness : TIGHTNESS) {
                CurationPoint center = new CurationPoint(PILOT_SUBJECTS, replicate,
                        CENTER_IMPORTANCE, CENTER_STRENGTH, CENTER_EFFORT, tightness);
                cells.addAll(factorial(center));
                for (int subjects : SIZE_SUBJECTS) {
                    cells.addAll(oneAtATime(center.withSubjects(subjects)));
                }
            }
        }
        return List.copyOf(cells);
    }

    /** Bloco F: os 36 pontos em volta de um centro. */
    static List<Cell> factorial(CurationPoint center) {
        List<Cell> cells = new ArrayList<>();
        for (ImportanceSpread importance : ImportanceSpread.values()) {
            for (EdgeStrengthMix strength : EdgeStrengthMix.values()) {
                for (EffortMapping effort : EffortMapping.values()) {
                    cells.add(new Cell("factorial",
                            center.with(importance).with(strength).with(effort)));
                }
            }
        }
        return cells;
    }

    /** Bloco S: o centro e cada eixo curado variado sozinho, 8 pontos. */
    static List<Cell> oneAtATime(CurationPoint center) {
        List<Cell> cells = new ArrayList<>();
        cells.add(new Cell("size", center));
        for (ImportanceSpread importance : ImportanceSpread.values()) {
            if (importance != center.importance()) {
                cells.add(new Cell("size-importance", center.with(importance)));
            }
        }
        for (EdgeStrengthMix strength : EdgeStrengthMix.values()) {
            if (strength != center.strength()) {
                cells.add(new Cell("size-strength", center.with(strength)));
            }
        }
        for (EffortMapping effort : EffortMapping.values()) {
            if (effort != center.effort()) {
                cells.add(new Cell("size-effort", center.with(effort)));
            }
        }
        return cells;
    }
}
