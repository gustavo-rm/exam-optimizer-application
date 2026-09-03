package com.ia.project.dynamicstudyplanner.config;

import com.ia.project.dynamicstudyplanner.config.logging.MdcTaskDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for asynchronous processing.
 * Configures a dedicated thread pool for heavy, CPU-bound genetic algorithm tasks,
 * preventing exhaustion of the main Tomcat servlet thread pool.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${optimizer.thread-pool-size:0}")
    private int configuredPoolSize;

    @Value("${optimizer.queue-capacity:32}")
    private int queueCapacity;

    /**
     * Cria o executor das otimizações, dimensionado pelo número de núcleos da máquina.
     *
     * <h2>Por que uma thread por núcleo, e não metade</h2>
     *
     * O trabalho é ligado a CPU: uma otimização não espera disco nem rede, então uma thread só
     * libera o núcleo quando termina. Para carga assim o ponto ótimo fica em torno de uma thread
     * por núcleo — abaixo disso sobra núcleo ocioso, acima disso as threads se revezam no mesmo
     * núcleo e pagam troca de contexto sem ganhar capacidade.
     *
     * <p>Isso foi <b>medido</b> na etapa 05b, a 16 requisições simultâneas, medianas de 3 baterias
     * alternadas entre as configurações para não confundir diferença com deriva da máquina:
     *
     * <pre>
     *   pool = 2 (o padrão anterior, núcleos/2) ... 142 req/s
     *   pool = 4 (núcleos) ........................ 170 req/s
     *   pool = 6 .................................. 155 req/s
     *   pool = 8 (o valor fixo em arquivo) ........ 138 req/s
     * </pre>
     *
     * <p>O padrão anterior era {@code núcleos / 2}, herdado de quando a avaliação de fitness era
     * sempre paralela: cada requisição já ocupava vários núcleos por dentro, e um pool grande só
     * aumentava a disputa. Com o limiar do achado F3, uma requisição de população típica passou a
     * ser monothread, e o pool voltou a ser o que de fato limita a concorrência.
     *
     * <p>{@code availableProcessors()} respeita o limite de CPU do contêiner desde o JDK 10, então
     * o número lido é a fatia realmente disponível, não os núcleos da máquina hospedeira.
     */
    @Bean(name = "optimizerTaskExecutor")
    public Executor optimizerTaskExecutor() {
        int availableCores = Runtime.getRuntime().availableProcessors();
        int workerThreads = configuredPoolSize > 0 ? configuredPoolSize : availableCores;
        log.info("Configuring optimizerTaskExecutor with {} worker threads ({} cores available, "
                + "configured override: {})", workerThreads, availableCores,
                configuredPoolSize > 0 ? configuredPoolSize : "none");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workerThreads);
        executor.setMaxPoolSize(workerThreads);
        // Fila limitada: quando enche, a tarefa e recusada na hora em vez de acumular sem teto.
        // A recusa chega ao cliente como 503 com Retry-After (InfrastructureErrorAdvice), e nao
        // mais como 500 — ver o achado E2. A PROFUNDIDADE da fila e uma decisao com aritmetica,
        // documentada em optimizer.queue-capacity: uma fila mais funda do que o prazo consegue
        // drenar so produz trabalho descartado (achado E5).
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("Optimizer-Async-");

        // Ensure correlation IDs (traceId) are copied from the HTTP thread to the Async thread
        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.initialize();
        return executor;
    }
}
