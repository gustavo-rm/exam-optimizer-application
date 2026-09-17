package com.ia.project.dynamicstudyplanner.infra.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava a função de custo contra a grade que a calibrou.
 *
 * <h2>Por que testar um modelo contra a medição que o gerou</h2>
 *
 * Parece circular, e não é. O modelo tem quatro constantes ajustadas sobre três pontos; os outros
 * treze da grade são <b>verificação independente</b>: nada garantia que uma reta ajustada em
 * instâncias grandes acertasse as pequenas, nem que o produto {@code gerações × população} fosse
 * mesmo o fator dominante. Este teste é o que impede alguém de mexer nas constantes "para arredondar"
 * e desalinhar o preço do custo real sem perceber.
 *
 * <h2>A tolerância, e por que ela tem duas parcelas</h2>
 *
 * O desvio admitido é <b>0,5 ficha mais 15 % do custo real</b>, e a separação é deliberada:
 *
 * <ul>
 *   <li><b>0,5 ficha</b> é a quantização. Fichas são inteiras, então um pedido que custa 3,3 vira 3
 *       ou 4 — meia ficha de erro é inerente à unidade, não ao modelo. Num pedido de 3 fichas isso
 *       parece 15 %; num de 90, é ruído.</li>
 *   <li><b>15 %</b> é a folga para o modelo em si, que ajusta quatro constantes sobre uma máquina
 *       específica.</li>
 * </ul>
 *
 * <p>Somar as duas numa tolerância única esconderia qual das duas cresceu, se alguém mexesse nas
 * constantes.
 */
@DisplayName("Custo de um pedido: o preco acompanha o trabalho, nao a contagem de chamadas")
class RequestCostTest {

    /** Custo medido do pedido de referência, em milissegundos. */
    private static final double TIPICO_MS = 12.0;

    @Test
    @DisplayName("o pedido de referencia custa exatamente uma ficha")
    void oPedidoDeReferenciaCustaUmaFicha() {
        assertThat(RequestCost.fichas(15, 100, 50)).isEqualTo(1);
    }

    @Test
    @DisplayName("nenhum pedido custa menos que uma ficha")
    void nenhumPedidoCustaMenosQueUmaFicha() {
        // Sem piso, uma enxurrada de pedidos minusculos passaria de graca — e cada um ainda paga
        // conexao, validacao e serializacao.
        assertThat(RequestCost.fichas(1, 10, 10)).isEqualTo(1);
        assertThat(RequestCost.fichas(0, 0, 0)).isEqualTo(1);
    }

    @Test
    @DisplayName("o teto declarado bate com o pedido mais caro que o contrato aceita")
    void oTetoBateComOPedidoMaisCaro() {
        // 40 disciplinas, e nao 50: com 50 o piso de dias por disciplina soma mais do que o ano
        // tem, e a regra de negocio recusa com 422 em 24 ms, antes de gastar CPU.
        assertThat(RequestCost.fichasDoPedidoMaisCaro())
                .isEqualTo(RequestCost.fichas(40, 1000, 500));
    }

    @Test
    @DisplayName("o espalhamento entre o tipico e o mais caro reflete os 119x medidos")
    void oEspalhamentoRefleteAMedicao() {
        int tipico = RequestCost.fichas(15, 100, 50);
        int maisCaro = RequestCost.fichasDoPedidoMaisCaro();

        // A etapa 06 mediu 119x entre o tipico e o pesado de 24 disciplinas em planos/hora; o de 40
        // disciplinas e mais caro ainda. O que importa aqui e a ORDEM DE GRANDEZA: antes desta
        // correcao os dois custavam a mesma ficha.
        assertThat(maisCaro / (double) tipico).isBetween(80.0, 200.0);
    }

    @ParameterizedTest(name = "{0} disc, {1} ger, pop {2} — medido {3} ms")
    @CsvSource({
            // disciplinas, geracoes, populacao, tempo medido em ms (grade da etapa 06b)
            "5,   1000, 500, 443",
            "24,  1000, 500, 1069",
            "40,  1000, 500, 1538",
            "15,  1000, 50,  80",
            "15,  100,  500, 84",
            "15,  100,  250, 43",
            "15,  500,  50,  40",
            "15,  100,  100, 22"
    })
    @DisplayName("o preco acompanha o custo medido, dentro de 15 %")
    void oPrecoAcompanhaOCustoMedido(int disciplinas, int geracoes, int populacao, int medidoMs) {
        int fichas = RequestCost.fichas(disciplinas, geracoes, populacao);
        double custoRealEmFichas = medidoMs / TIPICO_MS;

        double toleranciaDeQuantizacao = 0.5;
        double toleranciaDoModelo = 0.15 * custoRealEmFichas;

        assertThat(Math.abs(fichas - custoRealEmFichas))
                .as("preco %d fichas contra custo real de %.2f fichas (%d ms medidos); "
                        + "admitido %.2f (0,5 de quantizacao + 15 %% de modelo)",
                        fichas, custoRealEmFichas, medidoMs,
                        toleranciaDeQuantizacao + toleranciaDoModelo)
                .isLessThanOrEqualTo(toleranciaDeQuantizacao + toleranciaDoModelo);
    }

    @Test
    @DisplayName("dobrar geracoes ou populacao dobra o preco — o produto e o que manda")
    void oProdutoGeracoesPopulacaoEhOQueManda() {
        // A grade mostrou que os dois fatores entram pelo produto: 1000x50 e 100x500 custaram
        // 80 e 84 ms. O preco tem que refletir isso, senao seria trivial escolher o eixo barato.
        assertThat(RequestCost.fichas(15, 1000, 50))
                .isEqualTo(RequestCost.fichas(15, 100, 500));
    }

    @Test
    @DisplayName("mais disciplinas custam mais, com o mesmo trabalho evolutivo")
    void maisDisciplinasCustamMais() {
        int poucas = RequestCost.fichas(5, 1000, 500);
        int medias = RequestCost.fichas(24, 1000, 500);
        int muitas = RequestCost.fichas(40, 1000, 500);

        assertThat(poucas).isLessThan(medias);
        assertThat(medias).isLessThan(muitas);
        // Medido: 443, 1069 e 1538 ms — uma reta, nao uma proporcao direta.
        assertThat(muitas / (double) poucas).isBetween(3.0, 4.0);
    }
}
