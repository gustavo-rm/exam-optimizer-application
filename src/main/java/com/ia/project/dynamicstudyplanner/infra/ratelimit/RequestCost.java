package com.ia.project.dynamicstudyplanner.infra.ratelimit;

/**
 * Quanto um pedido de otimização custa, em fichas — a correção do achado E4.
 *
 * <h2>O problema: o limite contava chamadas, e o custo varia 119 vezes</h2>
 *
 * Até a etapa 06b o limite de taxa consumia <b>uma</b> ficha por requisição, qualquer que fosse ela.
 * O contrato, porém, aceita pedidos com custos muito diferentes:
 *
 * <pre>
 *   pedido típico (15 disciplinas, 100 gerações, população 50) ...... 619.200 planos/hora
 *   pedido pesado (24 disciplinas, 1000 gerações, população 500) ....   5.183 planos/hora
 *                                                                      ------ 119x
 * </pre>
 *
 * A consequência medida: <b>17 clientes obedecendo o limite de 5 por minuto saturavam a máquina</b>
 * com pedidos pesados, enquanto um cliente de pedidos típicos era barrado depois de gastar 1/119 do
 * mesmo recurso. Não era abuso — era a política funcionando como escrita.
 *
 * <h2>A função de custo, e de onde vieram os números</h2>
 *
 * Não é estimativa. O custo foi medido numa grade de 16 pontos cobrindo todo o espaço que o contrato
 * aceita (5 a 40 disciplinas, 10 a 1000 gerações, 10 a 500 de população), com medianas de 5 a 11
 * execuções:
 *
 * <pre>
 *   disc   ger   pop   tempo    disc   ger   pop   tempo
 *      5   100    50     9 ms     15   100    10     1 ms
 *     15   100    50    12 ms     15   100   100    22 ms
 *     24   100    50    11 ms     15   100   250    43 ms
 *     40   100    50    15 ms     15   100   500    84 ms
 *     15    10    50     1 ms      5  1000   500   443 ms
 *     15   250    50    20 ms     24  1000   500  1069 ms
 *     15   500    50    40 ms     40  1000   500  1538 ms
 *     15  1000    50    80 ms     24    10    10     0 ms
 * </pre>
 *
 * Dois fatos saltam da grade:
 *
 * <ul>
 *   <li><b>{@code gerações × população} domina e é linear.</b> Multiplicar esse produto por 100
 *       (de 5 000 para 500 000) multiplica o tempo por ~89.</li>
 *   <li><b>Disciplinas entram linearmente, sobre uma base fixa.</b> A 1000 gerações e população 500:
 *       5 disciplinas custam 443 ms, 24 custam 1069 e 40 custam 1538 — uma reta de inclinação
 *       0,313 ms por disciplina e intercepto 2,87 ms, por unidade de trabalho.</li>
 * </ul>
 *
 * O modelo que sai daí, em milissegundos:
 *
 * <pre>
 *   unidades = (gerações × população) / 5.000
 *   custo_ms = 4,441 + unidades × (2,866 + 0,313 × disciplinas)
 * </pre>
 *
 * A parcela fixa de 4,441 ms é o que a requisição gasta fora do laço evolutivo — montagem do
 * contexto e população inicial —, e é ela que faz um pedido minúsculo não custar zero.
 *
 * <p><b>Ajuste verificado contra a grade:</b> erro abaixo de 5 % em todo pedido a partir de 3
 * unidades de trabalho. Abaixo disso o modelo cobra mais que o medido, o que é intencional: o piso
 * de uma ficha impede que uma enxurrada de pedidos triviais passe de graça.
 *
 * <h2>Por que as constantes serem de uma máquina não é problema</h2>
 *
 * Os milissegundos foram medidos num contêiner de 4 núcleos. Numa máquina mais rápida, <b>todos</b>
 * encolhem na mesma proporção — e o que a função devolve é uma <b>razão</b>, dividida pelo custo do
 * pedido típico. A razão entre um pedido pesado e um típico é uma propriedade do algoritmo, não do
 * relógio.
 */
public final class RequestCost {

    /** Produto {@code gerações × população} do pedido de referência (100 × 50). */
    private static final double UNIDADE_DE_TRABALHO = 5_000.0;

    /** Custo que não escala com o tamanho do problema: contexto e população inicial. */
    private static final double SOBRECARGA_FIXA_MS = 4.441;

    /** Custo por unidade de trabalho, independente do número de disciplinas. */
    private static final double BASE_POR_UNIDADE_MS = 2.866;

    /** Custo adicional por disciplina, por unidade de trabalho. */
    private static final double POR_DISCIPLINA_MS = 0.313;

    /** Custo do pedido de referência: 15 disciplinas, 100 gerações, população 50. */
    private static final double PEDIDO_TIPICO_MS = 12.0;

    private RequestCost() {
    }

    /**
     * Fichas que este pedido consome do balde do cliente.
     *
     * @param disciplinas  total de disciplinas, somando conhecimentos gerais e eixos específicos
     * @param geracoes     ciclos evolutivos pedidos
     * @param populacao    soluções por geração
     * @return o custo, sempre pelo menos 1
     */
    public static int fichas(int disciplinas, int geracoes, int populacao) {
        double unidades = (geracoes * (double) populacao) / UNIDADE_DE_TRABALHO;
        double custoMs = SOBRECARGA_FIXA_MS
                + unidades * (BASE_POR_UNIDADE_MS + POR_DISCIPLINA_MS * disciplinas);
        return Math.max(1, (int) Math.round(custoMs / PEDIDO_TIPICO_MS));
    }

    /**
     * Custo do pedido mais caro que o contrato aceita — o teto usado para dimensionar a capacidade
     * do balde e para cobrar um corpo que não pôde ser lido.
     *
     * <p>São 40 disciplinas com 1000 gerações e população 500. Não são 50 disciplinas porque o piso
     * de dias por disciplina soma mais do que o ano tem, e a regra de negócio recusa antes de gastar
     * CPU (422 em 24 ms).
     */
    public static int fichasDoPedidoMaisCaro() {
        return fichas(40, 1000, 500);
    }
}
