package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobResultSerializer;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobStore;
import com.ia.project.dynamicstudyplanner.infra.jobs.OptimizationJob;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Aceita um pedido de otimização, devolve um identificador na hora e executa depois.
 *
 * <h2>Por que isto existe (achado E6)</h2>
 *
 * O endpoint original é assíncrono <b>por dentro</b> — a thread do Tomcat é liberada, e isso foi
 * medido: 30 pedidos em voo ocupavam 1 das 200 threads do conector. Mas é síncrono <b>para o
 * cliente</b>: a conexão HTTP fica aberta até o plano ficar pronto, o que no pior pedido aceito
 * significa 2,4 a 3,4 segundos.
 *
 * <p>Três limites vinham disso:
 *
 * <ul>
 *   <li><b>Prazos de infraestrutura.</b> Balanceadores e <i>gateways</i> têm limites próprios,
 *       tipicamente 30 ou 60 s. Um pedido pesado atrás de fila estoura qualquer um deles, e o
 *       cliente perde o trabalho sem saber se ele chegou a acontecer.</li>
 *   <li><b>Trabalho pago e descartado.</b> Quando o prazo estourava, a otimização em andamento
 *       continuava consumindo CPU para uma conexão que já tinha ido embora.</li>
 *   <li><b>Pico virava erro.</b> Sem onde guardar o pedido, não havia como transformar pico em
 *       latência; sobra só recusar.</li>
 * </ul>
 *
 * <h2>Como o fluxo novo resolve</h2>
 *
 * O pedido é aceito em milissegundos (<b>202 Accepted</b> com um identificador), a otimização roda
 * no pool, e o cliente busca o resultado quando quiser. A conexão HTTP dura o tempo de gravar um
 * registro, não o tempo de calcular um plano.
 *
 * <p><b>O controle de admissão continua na frente, e ficou mais barato.</b> Se a fila do pool estiver
 * cheia, {@code TaskRejectedException} é lançada <b>no envio</b>, na thread da requisição, antes de
 * qualquer CPU de otimização — e vira 503 com {@code Retry-After}. Recusar passou a custar
 * milissegundos em vez de 30 segundos de espera.
 */
@Service
public class OptimizationJobService {

    private static final Logger log = LoggerFactory.getLogger(OptimizationJobService.class);

    private final GenerateStudyPlanUseCaseHolder useCase;
    private final ThreadPoolTaskExecutor executor;
    private final JobStore jobStore;
    private final JobResultSerializer serializer;

    private final Counter aceitos;
    private final Counter concluidos;
    private final Counter falhos;
    private final Timer duracaoTotal;

    public OptimizationJobService(GenerateStudyPlanUseCaseHolder useCase,
                                  @Qualifier("optimizerTaskExecutor") ThreadPoolTaskExecutor executor,
                                  JobStore jobStore, JobResultSerializer serializer,
                                  MeterRegistry registry) {
        this.useCase = useCase;
        this.executor = executor;
        this.jobStore = jobStore;
        this.serializer = serializer;
        this.aceitos = Counter.builder("dynamicstudyplanner.jobs.accepted")
                .description("Pedidos de otimizacao aceitos para processamento posterior")
                .register(registry);
        this.concluidos = Counter.builder("dynamicstudyplanner.jobs.completed")
                .description("Trabalhos concluidos com plano gerado").register(registry);
        this.falhos = Counter.builder("dynamicstudyplanner.jobs.failed")
                .description("Trabalhos que terminaram sem plano").register(registry);
        this.duracaoTotal = Timer.builder("dynamicstudyplanner.jobs.duration")
                .description("Da aceitacao ate o fim, incluindo o tempo na fila")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /**
     * Aceita o pedido e devolve o identificador. <b>Retorna em milissegundos.</b>
     *
     * <p>A gravação do registro vem <b>antes</b> do envio ao pool, de propósito: se a ordem fosse a
     * inversa, o trabalho poderia começar, terminar e tentar gravar o resultado antes de o registro
     * existir. Gravando primeiro, o pior caso é um registro PENDING órfão, que expira sozinho.
     *
     * @throws TaskRejectedException quando a fila está cheia — o controle de admissão, tratado
     *         como 503 com {@code Retry-After}
     */
    public String submeter(Exam exam, StudentProfile profile, int totalStudyDays,
                           int numGenerations, int populationSize) {
        String id = UUID.randomUUID().toString();
        jobStore.save(OptimizationJob.aceito(id, Instant.now()));
        // Envio EXPLICITO ao executor, e nao @Async neste metodo.
        //
        // A primeira versao marcava executar() com @Async e o chamava daqui. Nao funcionou, e a
        // medicao pegou: o envio levava os mesmos 1,5 s do caminho sincrono. A causa e classica —
        // @Async age por proxy, e uma chamada de um metodo do bean para outro do MESMO bean passa
        // por dentro, sem tocar no proxy. O trabalho rodava na propria thread da requisicao.
        //
        // Enviar explicitamente resolve e, de quebra, torna o controle de admissao visivel: e este
        // execute() que lanca TaskRejectedException quando a fila esta cheia, na thread da
        // requisicao, antes de qualquer CPU de otimizacao.
        executor.execute(() -> executar(id, exam, profile, totalStudyDays,
                numGenerations, populationSize));
        aceitos.increment();
        log.info("Job {} accepted for asynchronous optimization", id);
        return id;
    }

    public Optional<OptimizationJob> consultar(String id) {
        return jobStore.find(id);
    }

    /**
     * Executa a otimização numa thread do pool.
     *
     * <p>Nenhuma exceção escapa: um trabalho que falha vira um registro FAILED que o cliente
     * consegue ler. Deixar a exceção subir apenas encheria o log do executor e deixaria o cliente
     * consultando um registro eternamente RUNNING — o pior dos dois mundos.
     */
    void executar(String id, Exam exam, StudentProfile profile, int totalStudyDays,
                  int numGenerations, int populationSize) {
        Instant inicio = Instant.now();
        jobStore.find(id).ifPresent(job -> jobStore.save(job.iniciado(inicio)));
        try {
            var resultado = useCase.executar(exam, profile, totalStudyDays,
                    numGenerations, populationSize);

            registrar(id, job -> job.concluido(Instant.now(), serializer.serializar(resultado)));
            concluidos.increment();
        } catch (Exception e) {
            // A mensagem de uma violacao de REGRA DE NEGOCIO vai inteira para o cliente, porque e
            // exatamente o que ele precisa saber para corrigir o pedido — e porque o caminho
            // sincrono ja faz isso, devolvendo 422 com o texto da DomainException. Um mesmo pedido
            // recusado pelos dois caminhos tem que dar a mesma explicacao; a primeira versao desta
            // etapa devolvia aqui um generico "could not be completed", e o teste do fluxo pegou.
            //
            // Excecoes INESPERADAS continuam viram mensagem generica, pelo mesmo motivo do tratador
            // global: nao vazar detalhe interno de uma falha que ninguem previu.
            boolean deDominio = e instanceof DomainException
                    || e instanceof IllegalArgumentException
                    || e instanceof IllegalStateException;
            registrar(id, job -> job.falhou(Instant.now(), e.getClass().getSimpleName(),
                    deDominio ? e.getMessage() : "The optimization could not be completed."));
            falhos.increment();
            log.warn("Job {} failed: {}", id, e.toString());
        } finally {
            duracaoTotal.record(java.time.Duration.between(inicio, Instant.now()));
        }
    }

    private void registrar(String id, java.util.function.UnaryOperator<OptimizationJob> transicao) {
        jobStore.find(id).ifPresent(job -> jobStore.save(transicao.apply(job)));
    }

    /**
     * Pequeno invólucro sobre o caso de uso.
     *
     * <p>Existe porque {@code GenerateStudyPlanUseCase} devolve {@code CompletableFuture} e é ele
     * próprio {@code @Async}: chamá-lo daqui enfileiraria uma <b>segunda</b> tarefa no mesmo pool,
     * ocupando duas vagas para um trabalho — e, com a fila cheia, uma tarefa já em execução seria
     * recusada no meio. O invólucro chama o cálculo de forma direta, na thread que já foi alocada.
     */
    @Service
    public static class GenerateStudyPlanUseCaseHolder {

        private final StudyOptimizerService optimizerService;
        private final StudyScheduleGenerator scheduleGenerator;
        private final com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator loadCalculator;

        public GenerateStudyPlanUseCaseHolder(
                StudyOptimizerService optimizerService,
                StudyScheduleGenerator scheduleGenerator,
                com.ia.project.dynamicstudyplanner.service.calculation.CognitiveLoadCalculator loadCalculator) {
            this.optimizerService = optimizerService;
            this.scheduleGenerator = scheduleGenerator;
            this.loadCalculator = loadCalculator;
        }

        public com.ia.project.dynamicstudyplanner.domain.FullPlannerResult executar(
                Exam exam, StudentProfile profile, int totalStudyDays,
                int numGenerations, int populationSize) {
            var otimizacao = optimizerService.optimize(exam, profile, totalStudyDays,
                    numGenerations, populationSize);
            var estrategia = com.ia.project.dynamicstudyplanner.service.scheduler.strategy.AllocationChains
                    .production(loadCalculator.calculate(profile, exam));
            var cronograma = scheduleGenerator.generate(otimizacao.plan(), profile, exam,
                    java.time.LocalDate.now(), estrategia);
            return new com.ia.project.dynamicstudyplanner.domain.FullPlannerResult(otimizacao, cronograma);
        }
    }
}
