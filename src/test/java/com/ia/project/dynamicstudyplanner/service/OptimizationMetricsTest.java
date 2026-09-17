package com.ia.project.dynamicstudyplanner.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O contrato de medição que saiu de {@code StudyOptimizerService} na etapa 03e (achado E8).
 *
 * <h2>Por que estas garantias merecem teste próprio agora</h2>
 *
 * Enquanto a contagem morava dentro do serviço, ela era um bloco {@code try/finally} no meio de um
 * método de 40 linhas — <b>e nenhum teste a exercitava</b>. As três garantias abaixo eram verdadeiras
 * por acidente de escrita, não por contrato verificado, e qualquer reorganização do método poderia
 * tê-las perdido em silêncio:
 *
 * <ul>
 *   <li>o contador incrementa por <b>tentativa</b>, não por sucesso;</li>
 *   <li>o cronômetro registra <b>também</b> quando a execução falha — uma falha lenta é informação
 *       de desempenho, e ignorá-la distorceria a média para baixo justamente nos piores casos;</li>
 *   <li>a duração devolvida ao cliente e a registrada no monitoramento vêm da <b>mesma medição</b>.</li>
 * </ul>
 *
 * <p>Separar a responsabilidade é o que tornou possível testá-la sem montar um algoritmo genético
 * inteiro. Este arquivo é a evidência prática do que o achado E8 argumentava.
 */
@DisplayName("OptimizationMetrics: contagem e cronometragem das otimizacoes")
class OptimizationMetricsTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final OptimizationMetrics metrics = new OptimizationMetrics(registro);

    private double execucoesContadas() {
        return registro.get("dynamicstudyplanner.optimization.runs").counter().count();
    }

    private long amostrasDeTempo() {
        return registro.get("dynamicstudyplanner.optimization.duration").timer().count();
    }

    @Test
    @DisplayName("devolve o resultado da execucao e conta uma rodada")
    void devolveOResultadoEContaARodada() {
        OptimizationMetrics.Timed<String> medido = metrics.recordRun(() -> "plano");

        assertThat(medido.resultado())
                .as("a medicao nao pode alterar o que a execucao produziu")
                .isEqualTo("plano");
        assertThat(execucoesContadas()).isEqualTo(1.0);
        assertThat(amostrasDeTempo()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a execucao que falha tambem e contada e cronometrada")
    void falhaTambemEContadaECronometrada() {
        // O caso que mais importa para monitoramento: se so o sucesso fosse medido, uma degradacao
        // que faz tudo estourar o prazo apareceria como QUEDA no volume e MELHORA na duracao media.
        assertThatThrownBy(() -> metrics.recordRun(() -> {
            throw new IllegalStateException("o AG explodiu");
        }))
                .as("a excecao original precisa chegar intacta a quem chamou")
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("o AG explodiu");

        assertThat(execucoesContadas())
                .as("o contador mede tentativas, nao sucessos")
                .isEqualTo(1.0);
        assertThat(amostrasDeTempo())
                .as("uma falha lenta e informacao de desempenho e nao pode sumir da amostra")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("a duracao devolvida e a mesma que foi registrada no monitoramento")
    void aDuracaoDevolvidaEAMesmaQueFoiRegistrada() {
        // Uma execucao com duracao observavel, para que os dois numeros possam ser comparados sem
        // depender de resolucao de relogio.
        OptimizationMetrics.Timed<String> medido = metrics.recordRun(() -> {
            long ate = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(25);
            while (System.nanoTime() < ate) {
                Thread.onSpinWait();
            }
            return "ok";
        });

        double registrada = registro.get("dynamicstudyplanner.optimization.duration")
                .timer().totalTime(TimeUnit.MILLISECONDS);

        assertThat(medido.duracaoMs())
                .as("a duracao devolvida ao cliente tem que refletir o tempo real gasto")
                .isGreaterThanOrEqualTo(25L);
        assertThat(registrada)
                .as("as duas leituras vem da mesma medicao: o cronometro fecha depois, entao e >= "
                        + "a devolvida, mas nunca de outra ordem de grandeza")
                .isGreaterThanOrEqualTo(medido.duracaoMs())
                .isLessThan(medido.duracaoMs() + 1000);
    }
}
