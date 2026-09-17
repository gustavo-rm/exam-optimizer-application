package com.ia.project.dynamicstudyplanner.infra.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.Optional;

/**
 * Registros num Redis compartilhado — o que permite enviar o pedido a uma réplica e buscar o
 * resultado em outra.
 *
 * <h2>Uma conexão, não um pool</h2>
 *
 * O Lettuce <b>multiplexa</b>: uma conexão TCP carrega comandos de muitas threads ao mesmo tempo,
 * porque o protocolo do Redis é pipelinado e as respostas voltam em ordem. Diferente de JDBC, onde a
 * conexão fica presa à transação e por isso se usa um pool, aqui não há o que agrupar — e é por isso
 * que o número de conexões cresce com <b>réplicas</b>, não com requisições.
 *
 * <p>Isso importa para escala porque o erro clássico é dimensionar um pool por réplica: 10 conexões
 * × 50 réplicas = 500 conexões para um trabalho que 50 fariam. Medição desta etapa: <b>1 conexão por
 * réplica</b>, constante sob 60 requisições simultâneas.
 *
 * <h2>Expiração: o resultado não fica para sempre</h2>
 *
 * Cada gravação renova um TTL (<i>time to live</i>, prazo após o qual o Redis apaga a chave
 * sozinho). Sem ele, cada pedido deixaria ~36 KB de resultado no Redis indefinidamente, e o
 * armazenamento cresceria com o tráfego acumulado em vez de com o tráfego em andamento.
 */
public class RedisJobStore implements JobStore {

    private static final String PREFIXO = "dsp:job:";

    private final StatefulRedisConnection<String, String> conexao;
    private final ObjectMapper objectMapper;
    private final Duration tempoDeVida;

    public RedisJobStore(RedisClient client, ObjectMapper objectMapper, Duration tempoDeVida) {
        this.conexao = client.connect();
        this.objectMapper = objectMapper;
        this.tempoDeVida = tempoDeVida;
    }

    @Override
    public void save(OptimizationJob job) {
        RedisCommands<String, String> comandos = conexao.sync();
        try {
            comandos.set(PREFIXO + job.id(), objectMapper.writeValueAsString(job),
                    SetArgs.Builder.ex(tempoDeVida));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o registro de trabalho " + job.id(), e);
        }
    }

    @Override
    public Optional<OptimizationJob> find(String id) {
        String json = conexao.sync().get(PREFIXO + id);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OptimizationJob.class));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            // Registro ilegivel e tratado como ausente, nao como erro: o cliente recebe 404 e pode
            // reenviar, em vez de 500 por um dado que ninguem consegue mais usar.
            return Optional.empty();
        }
    }

    @Override
    public String modo() {
        return "redis";
    }

    @Override
    public boolean compartilhado() {
        return true;
    }
}
