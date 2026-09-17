package com.ia.project.dynamicstudyplanner.service;

import com.ia.project.dynamicstudyplanner.domain.Chronotype;
import com.ia.project.dynamicstudyplanner.domain.StudentProfile;
import com.ia.project.dynamicstudyplanner.domain.StudentState;
import com.ia.project.dynamicstudyplanner.domain.exam.Exam;
import com.ia.project.dynamicstudyplanner.domain.exam.Subject;
import com.ia.project.dynamicstudyplanner.domain.exception.DomainException;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobResultSerializer;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobStatus;
import com.ia.project.dynamicstudyplanner.infra.jobs.JobStore;
import com.ia.project.dynamicstudyplanner.infra.jobs.LocalJobStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Trava o tratamento de erro do trabalhador assíncrono e o controle de admissão.
 *
 * <h2>Por que o tratamento de erro importa tanto aqui</h2>
 *
 * No caminho síncrono, uma exceção sobe pelo tratador global e o cliente recebe um código HTTP com
 * explicação. No caminho assíncrono não há ninguém esperando: se a exceção escapar, ela só enche o
 * log do executor, e o cliente fica consultando um registro <b>eternamente RUNNING</b> — sem
 * resultado e sem explicação, que é o pior dos dois mundos.
 *
 * <p>A distinção entre erro de regra e erro inesperado também importa. Um pedido recusado por regra
 * de negócio precisa dizer <b>qual</b> regra, para o cliente corrigir; uma falha imprevista não pode
 * vazar detalhe interno. O caminho síncrono já fazia essa distinção; este teste garante que o
 * assíncrono dá a mesma resposta para o mesmo pedido.
 */
@DisplayName("Trabalhador assincrono: nenhum trabalho fica preso, e o motivo certo chega ao cliente")
class OptimizationJobServiceTest {

    private static Exam exame() {
        return new Exam("Concurso", LocalDate.now().plusDays(200), 100.0,
                List.of(new Subject("Portugues", 20, 3)), List.of());
    }

    private static StudentProfile perfil(Exam exam) {
        Map<Subject, Double> lacunas = new HashMap<>();
        exam.getAllSubjects().forEach(s -> lacunas.put(s, 3.0));
        Map<DayOfWeek, Integer> disponibilidade = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            disponibilidade.put(d, 4);
        }
        return new StudentProfile("Aluno", lacunas, disponibilidade,
                new StudentState(3.0, 3.0, 3.0, Chronotype.INTERMEDIATE));
    }

    /** Executor que roda a tarefa na thread da chamada, para o teste ser determinístico. */
    private static ThreadPoolTaskExecutor executorDireto() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(10);
        executor.initialize();
        return executor;
    }

    private static OptimizationJobService servicoQueLanca(JobStore store, RuntimeException erro) {
        var holder = new OptimizationJobService.GenerateStudyPlanUseCaseHolder(null, null, null) {
            @Override
            public com.ia.project.dynamicstudyplanner.domain.FullPlannerResult executar(
                    Exam exam, StudentProfile profile, int totalStudyDays,
                    int numGenerations, int populationSize) {
                throw erro;
            }
        };
        JobResultSerializer serializer = resultado -> "{}";
        return new OptimizationJobService(holder, executorDireto(), store, serializer,
                new SimpleMeterRegistry());
    }

    private static void aguardar(JobStore store, String id) {
        long limite = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            if (store.find(id).filter(j -> j.terminado()).isPresent()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("o trabalho " + id + " nao terminou");
    }

    @Test
    @DisplayName("violacao de regra de negocio chega ao cliente com o texto da regra")
    void violacaoDeRegraChegaComOTexto() {
        JobStore store = new LocalJobStore(Duration.ofMinutes(10), 100);
        Exam exam = exame();
        String id = servicoQueLanca(store,
                new DomainException("Total minimum study days required (30) exceeds total available days (10)."))
                .submeter(exam, perfil(exam), 10, 50, 20);

        aguardar(store, id);

        assertThat(store.find(id)).get().satisfies(job -> {
            assertThat(job.status()).isEqualTo(JobStatus.FAILED);
            assertThat(job.errorDetail())
                    .as("o cliente precisa saber QUAL regra recusou o pedido, para corrigi-lo")
                    .contains("exceeds total available days");
            assertThat(job.resultJson()).isNull();
        });
    }

    @Test
    @DisplayName("falha inesperada nao vaza detalhe interno")
    void falhaInesperadaNaoVazaDetalheInterno() {
        JobStore store = new LocalJobStore(Duration.ofMinutes(10), 100);
        Exam exam = exame();
        String id = servicoQueLanca(store,
                new NullPointerException("Cannot invoke \"Foo.bar()\" because \"this.baz\" is null"))
                .submeter(exam, perfil(exam), 100, 50, 20);

        aguardar(store, id);

        assertThat(store.find(id)).get().satisfies(job -> {
            assertThat(job.status()).isEqualTo(JobStatus.FAILED);
            assertThat(job.errorDetail())
                    .as("mensagem generica: uma falha imprevista nao pode revelar estrutura interna")
                    .isEqualTo("The optimization could not be completed.")
                    .doesNotContain("this.baz");
            // O TIPO fica registrado, porque quem opera precisa dele — e ele nao revela dado.
            assertThat(job.errorType()).isEqualTo("NullPointerException");
        });
    }

    @Test
    @DisplayName("fila cheia recusa no ENVIO, antes de gastar CPU de otimizacao")
    void filaCheiaRecusaNoEnvio() {
        // O controle de admissao. A excecao sobe na thread da requisicao e vira 503 com
        // Retry-After — custa milissegundos, e nao os 30 s que o cliente esperava antes.
        ThreadPoolTaskExecutor lotado = new ThreadPoolTaskExecutor();
        lotado.setCorePoolSize(1);
        lotado.setMaxPoolSize(1);
        lotado.setQueueCapacity(1);
        lotado.initialize();
        lotado.shutdown(); // recusa tudo a partir daqui

        JobStore store = new LocalJobStore(Duration.ofMinutes(10), 100);
        var holder = new OptimizationJobService.GenerateStudyPlanUseCaseHolder(null, null, null);
        OptimizationJobService servico = new OptimizationJobService(
                holder, lotado, store, resultado -> "{}", new SimpleMeterRegistry());
        Exam exam = exame();

        assertThatThrownBy(() -> servico.submeter(exam, perfil(exam), 100, 50, 20))
                .isInstanceOf(TaskRejectedException.class);
    }
}
