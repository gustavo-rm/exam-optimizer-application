package com.ia.project.dynamicstudyplanner.infra.jobs;

/** Estados por que passa um pedido de otimização aceito para processamento posterior. */
public enum JobStatus {

    /** Aceito e na fila. Ainda não começou. */
    PENDING,

    /** Uma thread do pool está executando a otimização. */
    RUNNING,

    /** Terminou com plano gerado. O resultado está disponível. */
    SUCCEEDED,

    /** Terminou sem plano. O motivo está no campo de erro. */
    FAILED
}
