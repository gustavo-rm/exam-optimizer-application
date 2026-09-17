package com.ia.project.dynamicstudyplanner.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Contabiliza as execuções da otimização: quantas rodaram e quanto tempo levaram.
 *
 * <h2>Por que isto saiu de {@code StudyOptimizerService}</h2>
 *
 * É o terceiro dos quatro assuntos que o achado <b>E8</b> encontrou misturados. Observabilidade
 * muda por motivos próprios — publicar uma métrica nova, mudar o nome de uma série, acrescentar uma
 * <i>tag</i> — que nada têm a ver com o algoritmo genético. Enquanto os dois moravam juntos, o
 * histórico do arquivo não distinguia "mudei como a evolução roda" de "mudei o que o painel mostra".
 *
 * <h2>Por que a medição é uma só</h2>
 *
 * O tempo aparece em dois destinos: o {@code Timer} do Micrometer, que alimenta o monitoramento, e o
 * campo {@code executionTimeMillis} do resultado, que volta ao cliente na resposta da API. São o
 * mesmo tempo — e por isso {@link #recordRun} o mede <b>uma vez</b> e entrega os dois, em vez de
 * deixar o chamador cronometrar por fora e arriscar dois números divergentes para a mesma coisa.
 *
 * <p>A ordem preservada da versão anterior: o contador incrementa <b>antes</b> da execução (uma
 * tentativa é uma tentativa, tenha ela sucesso ou não) e o cronômetro registra em {@code finally}
 * (uma falha demorada também é informação de desempenho).
 */
@Component
public class OptimizationMetrics {

    private final Counter runsCounter;
    private final Timer durationTimer;

    public OptimizationMetrics(MeterRegistry meterRegistry) {
        this.runsCounter = Counter.builder("dynamicstudyplanner.optimization.runs")
                .description("Total number of study plan optimization runs executed")
                .register(meterRegistry);

        this.durationTimer = Timer.builder("dynamicstudyplanner.optimization.duration")
                .description("Time taken to execute the full genetic algorithm optimization")
                .register(meterRegistry);
    }

    /**
     * Executa uma otimização sob medição, devolvendo o resultado dela junto da duração.
     *
     * @param execucao o trabalho a medir; a exceção que ele lançar é propagada intacta, e o tempo
     *                 decorrido até a falha ainda assim vai para o {@code Timer}
     */
    public <T> Timed<T> recordRun(Supplier<T> execucao) {
        runsCounter.increment();
        long inicio = System.nanoTime();
        try {
            T resultado = execucao.get();
            return new Timed<>(resultado, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio));
        } finally {
            durationTimer.record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
        }
    }

    /** Um resultado e o tempo que ele levou para ser produzido, em milissegundos. */
    public record Timed<T>(T resultado, long duracaoMs) {
    }
}
