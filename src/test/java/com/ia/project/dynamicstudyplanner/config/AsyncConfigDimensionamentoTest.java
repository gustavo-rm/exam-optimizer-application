package com.ia.project.dynamicstudyplanner.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava o dimensionamento do pool que executa as otimizações.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * O achado F7 da etapa 05 era um número fixo em arquivo de configuração —
 * {@code optimizer.thread-pool-size=8} — sob o comentário "Available core count", numa máquina de
 * 4 núcleos. A aplicação registrava no log, literalmente, <i>"Configuring optimizerTaskExecutor
 * with 8 cores"</i>. O valor não vinha da máquina; só parecia vir.
 *
 * <p>Esse tipo de defeito não aparece em teste de comportamento: a aplicação funciona igual, só
 * mais devagar sob concorrência (medido: 138 req/s com o pool de 8 contra 170 req/s com 4, a 16
 * requisições simultâneas). O que o pega é uma verificação de que o número <b>é mesmo derivado</b>
 * do ambiente quando ninguém manda o contrário.
 */
@DisplayName("AsyncConfig: o pool e dimensionado pela maquina, nao por numero fixo")
class AsyncConfigDimensionamentoTest {

    @Test
    @DisplayName("sem configuracao explicita, o pool tem uma thread por nucleo disponivel")
    void semConfiguracaoExplicitaUsaOsNucleosDisponiveis() {
        ThreadPoolTaskExecutor executor = executorCom(0, 32);

        try {
            assertThat(executor.getCorePoolSize())
                    .as("o tamanho tem que sair de availableProcessors(), nao de uma constante")
                    .isEqualTo(Runtime.getRuntime().availableProcessors());
            assertThat(executor.getMaxPoolSize())
                    .as("pool fixo: trabalho ligado a CPU nao ganha nada com threads extras sob pico")
                    .isEqualTo(executor.getCorePoolSize());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("uma configuracao explicita vence a leitura da maquina")
    void configuracaoExplicitaVence() {
        // A porta de escape continua existindo — o que muda e que ela deixou de ser o padrao.
        // Vale para limitar o consumo de CPU de proposito, quando o processo divide a maquina.
        ThreadPoolTaskExecutor executor = executorCom(3, 32);

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(3);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("a fila e limitada — sobrecarga falha rapido em vez de acumular memoria")
    void aFilaEhLimitada() {
        ThreadPoolTaskExecutor executor = executorCom(0, 32);

        try {
            assertThat(executor.getQueueCapacity())
                    .as("fila ilimitada transformaria pico de carga em consumo de memoria sem teto")
                    .isEqualTo(32);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("a fila configurada cabe dentro do prazo da requisicao (achado E5)")
    void aFilaCabeDentroDoPrazo() {
        // Esta e a relacao que a etapa 06 encontrou quebrada: a fila tinha 50 vagas e o prazo era o
        // padrao do Tomcat, 30 s, sem nenhuma relacao entre os dois. Drenar 50 pedidos pesados
        // levava 35 s, entao as ultimas ~7 vagas so produziam trabalho descartado — o cliente
        // esperava 30 s, recebia 408, e a otimizacao dele chegava a COMECAR, gastando CPU para uma
        // conexao que ja tinha ido embora.
        //
        // O teste refaz a conta com os valores reais de configuracao, para que mudar um dos dois
        // numeros sem refazer a conta reprove aqui em vez de reaparecer como 408 em producao.
        int filaConfigurada = valorDaPropriedade("optimizer.queue-capacity", 32);
        int prazoSegundos = 30;                 // spring.mvc.async.request-timeout
        double custoDoPedidoPesado = 2.8;       // medido na etapa 06, 24 disciplinas / 1000 ger / 500 pop
        int threads = Runtime.getRuntime().availableProcessors();

        double esperaDaUltimaVaga = filaConfigurada * (custoDoPedidoPesado / threads);
        double tempoTotalDaUltimaVaga = esperaDaUltimaVaga + custoDoPedidoPesado;

        assertThat(tempoTotalDaUltimaVaga)
                .as("a ultima vaga da fila espera %.1f s e ainda precisa de %.1f s para executar: "
                        + "%.1f s no total, contra um prazo de %d s. Acima disso, as vagas do fim "
                        + "so produzem 408 e CPU desperdicada.",
                        esperaDaUltimaVaga, custoDoPedidoPesado, tempoTotalDaUltimaVaga, prazoSegundos)
                .isLessThan(prazoSegundos);
    }

    /** Le a propriedade do application.properties, para o teste conferir o valor que vale de fato. */
    private static int valorDaPropriedade(String chave, int padrao) {
        try (java.io.InputStream in = AsyncConfigDimensionamentoTest.class
                .getResourceAsStream("/application.properties")) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            return Integer.parseInt(p.getProperty(chave, String.valueOf(padrao)).trim());
        } catch (Exception e) {
            throw new IllegalStateException("nao foi possivel ler " + chave, e);
        }
    }

    /** Monta a configuração como o Spring monta, injetando a propriedade pelo campo. */
    private static ThreadPoolTaskExecutor executorCom(int propriedade, int fila) {
        AsyncConfig config = new AsyncConfig();
        ReflectionTestUtils.setField(config, "configuredPoolSize", propriedade);
        ReflectionTestUtils.setField(config, "queueCapacity", fila);
        Executor executor = config.optimizerTaskExecutor();

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        return (ThreadPoolTaskExecutor) executor;
    }
}
