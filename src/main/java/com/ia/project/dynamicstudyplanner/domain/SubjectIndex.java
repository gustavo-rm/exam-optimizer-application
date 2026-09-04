package com.ia.project.dynamicstudyplanner.domain;

import com.ia.project.dynamicstudyplanner.domain.exam.Subject;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ordem canônica dos genes de um cromossomo: qual disciplina ocupa cada posição do vetor.
 *
 * <h2>Por que esta classe existe (pendência P18)</h2>
 *
 * O cromossomo era um {@code Map<Subject, Integer>}. Ler ou escrever <b>um</b> gene custava um
 * cálculo de hash, um desembrulho de {@code Integer} e, na escrita, a alocação de um nó de mapa —
 * medido em ~72 ns por gene sobre 474 mil recombinações no pior caso admitido pela API. Como o
 * conjunto de disciplinas é <b>fixo durante toda a otimização</b>, nada disso precisava ser pago:
 * basta acordar uma vez qual disciplina fica em cada posição e trabalhar com {@code int[]}.
 *
 * <p>É isso que este objeto guarda. Ele é criado <b>uma vez por otimização</b> e compartilhado por
 * todos os indivíduos de todas as gerações. Um {@code int[]} alinhado a ele é um plano de estudos
 * completo, e dois vetores alinhados ao mesmo índice podem ser recombinados posição a posição, sem
 * consultar disciplina nenhuma.
 *
 * <h2>O que mudou no resultado — e por que a mudança é uma melhora</h2>
 *
 * A ordem dos genes importa: ela decide onde cai o ponto de corte do cruzamento de ponto único e a
 * que disciplina corresponde cada sorteio do reparo. Antes, essa ordem era a <b>ordem de iteração
 * de um {@code HashMap}</b> — determinística na prática, mas <b>não especificada</b> pelo contrato
 * de {@code java.util.Map} e livre para mudar numa atualização de JDK. Ou seja: o plano entregue ao
 * aluno dependia de um detalhe interno da biblioteca padrão, e a assinatura dourada travada em
 * {@code GaResultadoInalteradoTest} podia mudar sozinha sem que uma linha do projeto mudasse.
 *
 * <p>Agora a ordem é a do edital — a mesma lista de disciplinas que o cliente enviou, na ordem em
 * que enviou. É declarada, estável e nossa. A troca alterou os planos produzidos <b>uma vez</b>, e
 * em troca eles pararam de depender de algo que não controlamos.
 *
 * <h2>Contrato</h2>
 *
 * Imutável e seguro para uso concorrente. As posições vão de {@code 0} a {@code size() - 1} na
 * ordem em que as disciplinas foram apresentadas; disciplinas repetidas na entrada ocupam uma única
 * posição, a primeira em que apareceram.
 */
public final class SubjectIndex {

    /** Índice vazio, para planos sem disciplina alguma. */
    private static final SubjectIndex VAZIO = new SubjectIndex(new Subject[0], Map.of());

    private final Subject[] disciplinas;

    /** Posição de cada disciplina. Só é consultado fora do caminho quente. */
    private final Map<Subject, Integer> posicoes;

    private SubjectIndex(Subject[] disciplinas, Map<Subject, Integer> posicoes) {
        this.disciplinas = disciplinas;
        this.posicoes = posicoes;
    }

    /**
     * Cria um índice na ordem em que as disciplinas aparecem.
     *
     * @param disciplinas as disciplinas do edital, na ordem que passa a ser a ordem dos genes;
     *                    {@code null} ou vazio produz o índice vazio
     * @return o índice canônico correspondente
     */
    public static SubjectIndex of(Collection<Subject> disciplinas) {
        if (disciplinas == null || disciplinas.isEmpty()) {
            return VAZIO;
        }

        Subject[] emOrdem = new Subject[disciplinas.size()];
        Map<Subject, Integer> posicoes = new HashMap<>(disciplinas.size() * 2);
        int proxima = 0;
        for (Subject disciplina : disciplinas) {
            if (disciplina == null || posicoes.containsKey(disciplina)) {
                continue;
            }
            emOrdem[proxima] = disciplina;
            posicoes.put(disciplina, proxima);
            proxima++;
        }

        if (proxima == 0) {
            return VAZIO;
        }
        if (proxima < emOrdem.length) {
            // Houve repetição ou nulo na entrada: encolhe para o tamanho real.
            Subject[] ajustado = new Subject[proxima];
            System.arraycopy(emOrdem, 0, ajustado, 0, proxima);
            emOrdem = ajustado;
        }
        return new SubjectIndex(emOrdem, Collections.unmodifiableMap(posicoes));
    }

    /** @return quantos genes um cromossomo alinhado a este índice tem */
    public int size() {
        return disciplinas.length;
    }

    /**
     * @param posicao posição do gene, de {@code 0} a {@code size() - 1}
     * @return a disciplina que ocupa essa posição
     */
    public Subject subject(int posicao) {
        return disciplinas[posicao];
    }

    /**
     * @param disciplina a disciplina procurada
     * @return a posição dela, ou {@code -1} se não fizer parte deste índice
     */
    public int positionOf(Subject disciplina) {
        Integer posicao = posicoes.get(disciplina);
        return posicao == null ? -1 : posicao;
    }

    /** @return as disciplinas na ordem canônica, como lista somente leitura */
    public List<Subject> subjects() {
        return List.of(disciplinas);
    }

    /**
     * Projeta um mapa por disciplina no vetor alinhado a este índice.
     *
     * <p>É o que tira as buscas com hash do caminho quente: pisos de dias mínimos, importâncias
     * normalizadas e pesos de retenção são consultados uma vez por gene, por indivíduo, por
     * geração. Projetados uma vez na construção do contexto, viram leitura de posição de vetor.
     *
     * @param valores mapa por disciplina; {@code null} devolve o vetor todo com o padrão
     * @param padrao  valor para a disciplina ausente do mapa
     * @return vetor de tamanho {@link #size()}, alinhado a este índice
     */
    public int[] projectInts(Map<Subject, Integer> valores, int padrao) {
        int[] vetor = new int[disciplinas.length];
        for (int i = 0; i < disciplinas.length; i++) {
            Integer valor = valores == null ? null : valores.get(disciplinas[i]);
            vetor[i] = valor == null ? padrao : valor;
        }
        return vetor;
    }

    /**
     * Projeta um mapa de valores reais no vetor alinhado a este índice.
     *
     * @param valores mapa por disciplina; {@code null} devolve o vetor todo com o padrão
     * @param padrao  valor para a disciplina ausente do mapa
     * @return vetor de tamanho {@link #size()}, alinhado a este índice
     */
    public double[] projectDoubles(Map<Subject, Double> valores, double padrao) {
        double[] vetor = new double[disciplinas.length];
        for (int i = 0; i < disciplinas.length; i++) {
            Double valor = valores == null ? null : valores.get(disciplinas[i]);
            vetor[i] = valor == null ? padrao : valor;
        }
        return vetor;
    }

    /**
     * Reconstrói o mapa por disciplina a partir de um vetor alinhado.
     *
     * <p><b>Fora do caminho quente.</b> É a conversão que a fronteira usa para devolver o plano ao
     * cliente; dentro da evolução, trabalhe com o vetor.
     *
     * @param dias vetor alinhado a este índice
     * @return mapa na ordem canônica, preservada por {@link LinkedHashMap}
     */
    public Map<Subject, Integer> toMap(int[] dias) {
        Map<Subject, Integer> mapa = new LinkedHashMap<>(disciplinas.length * 2);
        for (int i = 0; i < disciplinas.length; i++) {
            mapa.put(disciplinas[i], dias[i]);
        }
        return mapa;
    }
}
