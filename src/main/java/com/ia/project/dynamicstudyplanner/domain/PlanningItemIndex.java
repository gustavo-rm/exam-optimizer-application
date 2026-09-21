package com.ia.project.dynamicstudyplanner.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A ordem canônica dos genes de um cromossomo: qual item de planejamento ocupa cada posição.
 *
 * <h2>Por que o índice existe (pendência P18)</h2>
 *
 * O cromossomo já foi um {@code Map<?, Integer>}. Ler ou escrever <b>um</b> gene custava um cálculo
 * de hash, um desembrulho de {@code Integer} e, na escrita, a alocação de um nó de mapa — medido em
 * ~72 ns por gene sobre 474 mil recombinações no pior caso admitido pela API. Como o conjunto de
 * itens é <b>fixo durante toda a otimização</b>, nada disso precisa ser pago: basta acordar uma vez
 * qual item fica em cada posição e trabalhar com {@code int[]}.
 *
 * <p>É isso que este objeto guarda. Ele é criado <b>uma vez por otimização</b> e compartilhado por
 * todos os indivíduos de todas as gerações. Um {@code int[]} alinhado a ele é um plano de estudos
 * completo, e dois vetores alinhados ao mesmo índice podem ser recombinados posição a posição, sem
 * consultar item nenhum.
 *
 * <h2>Por que a ordem é declarada, e não a de um mapa</h2>
 *
 * A ordem dos genes importa: ela decide onde cai o ponto de corte do cruzamento de ponto único e a
 * que item corresponde cada sorteio do reparo. Ela é a ordem em que o cliente apresentou os itens —
 * declarada e estável — e não a ordem de iteração de um {@code HashMap}, que o contrato de
 * {@code java.util.Map} não especifica e que uma atualização de JDK pode mudar. Sem isso, o plano
 * entregue ao aluno dependeria de um detalhe interno da biblioteca padrão.
 *
 * <h2>Esta classe substituiu {@code SubjectIndex}</h2>
 *
 * Mesma responsabilidade, mesma ordem estável, mesmos métodos — só que sobre {@link PlanningItem}
 * em vez de sobre a disciplina do concurso. A troca é de <b>nome do que um gene significa</b>, não
 * de comportamento: a aritmética, a ordem e o número de genes são os mesmos, e as assinaturas de
 * referência do algoritmo foram conferidas idênticas antes e depois.
 *
 * <h2>Contrato</h2>
 *
 * Imutável e seguro para uso concorrente. As posições vão de {@code 0} a {@code size() - 1} na
 * ordem em que os itens foram apresentados; um item repetido na entrada ocupa uma única posição, a
 * primeira em que apareceu.
 */
public final class PlanningItemIndex {

    /** Índice vazio, para planos sem item algum. */
    private static final PlanningItemIndex VAZIO = new PlanningItemIndex(new PlanningItem[0], Map.of());

    private final PlanningItem[] itens;

    /** Posição de cada item. Só é consultado fora do caminho quente. */
    private final Map<PlanningItem, Integer> posicoes;

    private PlanningItemIndex(PlanningItem[] itens, Map<PlanningItem, Integer> posicoes) {
        this.itens = itens;
        this.posicoes = posicoes;
    }

    /**
     * Cria um índice na ordem em que os itens aparecem.
     *
     * @param itens os itens do plano, na ordem que passa a ser a ordem dos genes;
     *                    {@code null} ou vazio produz o índice vazio
     * @return o índice canônico correspondente
     */
    public static PlanningItemIndex of(Collection<PlanningItem> itens) {
        if (itens == null || itens.isEmpty()) {
            return VAZIO;
        }

        PlanningItem[] emOrdem = new PlanningItem[itens.size()];
        Map<PlanningItem, Integer> posicoes = new HashMap<>(itens.size() * 2);
        int proxima = 0;
        for (PlanningItem item : itens) {
            if (item == null || posicoes.containsKey(item)) {
                continue;
            }
            emOrdem[proxima] = item;
            posicoes.put(item, proxima);
            proxima++;
        }

        if (proxima == 0) {
            return VAZIO;
        }
        if (proxima < emOrdem.length) {
            // Houve repeticao ou nulo na entrada: encolhe para o tamanho real.
            PlanningItem[] ajustado = new PlanningItem[proxima];
            System.arraycopy(emOrdem, 0, ajustado, 0, proxima);
            emOrdem = ajustado;
        }
        return new PlanningItemIndex(emOrdem, Collections.unmodifiableMap(posicoes));
    }

    /** @return quantos genes um cromossomo alinhado a este índice tem */
    public int size() {
        return itens.length;
    }

    /**
     * @param posicao posição do gene, de {@code 0} a {@code size() - 1}
     * @return o item que ocupa essa posição
     */
    public PlanningItem item(int posicao) {
        return itens[posicao];
    }

    /**
     * @param item o item procurado
     * @return a posição dela, ou {@code -1} se não fizer parte deste índice
     */
    public int positionOf(PlanningItem item) {
        Integer posicao = posicoes.get(item);
        return posicao == null ? -1 : posicao;
    }

    /** @return os itens na ordem canônica, como lista somente leitura */
    public List<PlanningItem> items() {
        return List.of(itens);
    }

    /**
     * Projeta um mapa por item no vetor alinhado a este índice.
     *
     * <p>É o que tira as buscas com hash do caminho quente: pisos de dias mínimos, importâncias
     * normalizadas e pesos de retenção são consultados uma vez por gene, por indivíduo, por
     * geração. Projetados uma vez na construção do contexto, viram leitura de posição de vetor.
     *
     * @param valores mapa por item; {@code null} devolve o vetor todo com o padrão
     * @param padrao  valor para o item ausente do mapa
     * @return vetor de tamanho {@link #size()}, alinhado a este índice
     */
    public int[] projectInts(Map<PlanningItem, Integer> valores, int padrao) {
        int[] vetor = new int[itens.length];
        for (int i = 0; i < itens.length; i++) {
            Integer valor = valores == null ? null : valores.get(itens[i]);
            vetor[i] = valor == null ? padrao : valor;
        }
        return vetor;
    }

    /**
     * Projeta um mapa de valores reais no vetor alinhado a este índice.
     *
     * @param valores mapa por item; {@code null} devolve o vetor todo com o padrão
     * @param padrao  valor para o item ausente do mapa
     * @return vetor de tamanho {@link #size()}, alinhado a este índice
     */
    public double[] projectDoubles(Map<PlanningItem, Double> valores, double padrao) {
        double[] vetor = new double[itens.length];
        for (int i = 0; i < itens.length; i++) {
            Double valor = valores == null ? null : valores.get(itens[i]);
            vetor[i] = valor == null ? padrao : valor;
        }
        return vetor;
    }

    /**
     * Reconstrói o mapa por item a partir de um vetor alinhado.
     *
     * <p><b>Fora do caminho quente.</b> É a conversão que a fronteira usa para devolver o plano ao
     * cliente; dentro da evolução, trabalhe com o vetor.
     *
     * @param dias vetor alinhado a este índice
     * @return mapa na ordem canônica, preservada por {@link LinkedHashMap}
     */
    public Map<PlanningItem, Integer> toMap(int[] dias) {
        Map<PlanningItem, Integer> mapa = new LinkedHashMap<>(itens.length * 2);
        for (int i = 0; i < itens.length; i++) {
            mapa.put(itens[i], dias[i]);
        }
        return mapa;
    }
}
