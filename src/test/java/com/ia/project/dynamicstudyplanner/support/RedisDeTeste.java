package com.ia.project.dynamicstudyplanner.support;

import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Um Redis de verdade, em processo, para os testes de estado compartilhado.
 *
 * <h2>Por que um servidor real e não um dublê</h2>
 *
 * O achado E1 é sobre <b>duas réplicas verem o mesmo estado</b>. Um dublê em memória, entregue às
 * duas pontas do teste, provaria apenas que o dublê é compartilhado — a propriedade que interessa
 * seria assumida, não verificada. Com um servidor real, o teste exercita o
 * <i>compare-and-swap</i> do bucket4j sobre o protocolo do Redis, que é onde estaria um erro de
 * concorrência.
 *
 * <h2>Por que embutido e não o Redis da máquina</h2>
 *
 * Depender de um Redis instalado tornaria o teste condicional: ele passaria a ser pulado em
 * qualquer máquina sem Redis, incluindo integração contínua — e um teste que não roda não protege
 * nada. Pior: o piso de cobertura teria de ser rebaixado para acomodar a ausência dele.
 *
 * <p>A porta é sorteada pelo sistema operacional a cada execução, para que duas execuções
 * simultâneas não colidam.
 */
public final class RedisDeTeste implements AutoCloseable {

    private final RedisServer servidor;
    private final int porta;

    private RedisDeTeste(RedisServer servidor, int porta) {
        this.servidor = servidor;
        this.porta = porta;
    }

    public static RedisDeTeste iniciar() throws IOException {
        int porta = portaLivre();
        RedisServer servidor = RedisServer.newRedisServer()
                .port(porta)
                .setting("save \"\"")
                .setting("appendonly no")
                .build();
        servidor.start();
        return new RedisDeTeste(servidor, porta);
    }

    public int porta() {
        return porta;
    }

    public String uri() {
        return "redis://localhost:" + porta;
    }

    @Override
    public void close() throws IOException {
        servidor.stop();
    }

    private static int portaLivre() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
