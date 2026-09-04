package com.ia.project.dynamicstudyplanner.infra.jobs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * O registro de um pedido de otimização aceito para processamento posterior.
 *
 * <p>É um valor imutável: cada transição de estado produz um registro novo, gravado por cima do
 * anterior. Isso evita a classe inteira de defeitos em que duas réplicas alteram campos diferentes
 * do mesmo registro e uma sobrescreve o trabalho da outra.
 *
 * <p>O resultado é guardado como <b>texto JSON já serializado</b>, e não como objeto. Assim o
 * armazenamento não precisa conhecer os tipos do domínio, e um resultado gravado por uma réplica é
 * legível por outra sem depender de as duas terem a mesma versão das classes — que é exatamente o
 * cenário de uma implantação gradual, com réplicas novas e antigas convivendo.
 *
 * @param id           identificador sorteado na aceitação
 * @param status       estado atual
 * @param submittedAt  quando o pedido foi aceito
 * @param startedAt    quando saiu da fila e começou a rodar; nulo enquanto PENDING
 * @param finishedAt   quando terminou; nulo enquanto não terminou
 * @param resultJson   o corpo da resposta, já serializado; nulo enquanto não há resultado
 * @param errorType    classe do erro, quando FAILED
 * @param errorDetail  mensagem segura para o cliente, quando FAILED
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OptimizationJob(
        String id,
        JobStatus status,
        Instant submittedAt,
        Instant startedAt,
        Instant finishedAt,
        String resultJson,
        String errorType,
        String errorDetail
) {

    public static OptimizationJob aceito(String id, Instant agora) {
        return new OptimizationJob(id, JobStatus.PENDING, agora, null, null, null, null, null);
    }

    public OptimizationJob iniciado(Instant agora) {
        return new OptimizationJob(id, JobStatus.RUNNING, submittedAt, agora, null, null, null, null);
    }

    public OptimizationJob concluido(Instant agora, String resultado) {
        return new OptimizationJob(id, JobStatus.SUCCEEDED, submittedAt, startedAt, agora,
                resultado, null, null);
    }

    public OptimizationJob falhou(Instant agora, String tipo, String detalhe) {
        return new OptimizationJob(id, JobStatus.FAILED, submittedAt, startedAt, agora,
                null, tipo, detalhe);
    }

    public boolean terminado() {
        return status == JobStatus.SUCCEEDED || status == JobStatus.FAILED;
    }
}
