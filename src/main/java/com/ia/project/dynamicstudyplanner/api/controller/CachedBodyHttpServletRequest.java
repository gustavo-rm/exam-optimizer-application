package com.ia.project.dynamicstudyplanner.api.controller;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Requisição cujo corpo pode ser lido mais de uma vez.
 *
 * <h2>Por que isto é necessário (achado E4)</h2>
 *
 * O limite de taxa passou a cobrar por <b>custo</b>, e o custo de um pedido está no corpo dele —
 * gerações, população e número de disciplinas. Mas o corpo de uma requisição HTTP é um fluxo que se
 * lê <b>uma vez</b>: se o filtro o consumisse para calcular o preço, o controlador receberia um
 * corpo vazio.
 *
 * <p>Este invólucro lê o corpo para memória uma vez e devolve um fluxo novo a cada chamada. É o
 * padrão usual para inspecionar corpo em filtro, e a alternativa — mover o limite para depois da
 * desserialização — seria pior: o trabalho de interpretar o JSON aconteceria <b>antes</b> de o
 * limite decidir se o pedido é aceito, que é exatamente o que um limite de taxa existe para evitar.
 *
 * <h2>O corpo é limitado, e isso importa</h2>
 *
 * Guardar em memória um corpo de tamanho arbitrário seria trocar um problema por outro: bastaria
 * enviar um corpo enorme para consumir memória da réplica antes de qualquer validação. O limite é
 * aplicado na leitura, e um corpo que o exceda é <b>truncado</b> — o chamador saberá disso porque
 * {@link #corpoTruncado()} responde {@code true}, e o filtro trata esse caso cobrando o pedido mais
 * caro possível.
 *
 * <p>O teto é folgado em relação ao maior pedido legítimo: o payload de 50 disciplinas — o maior que
 * o DTO admite — ocupa cerca de 4 KB.
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] corpo;
    private final boolean truncado;

    public CachedBodyHttpServletRequest(HttpServletRequest request, int limiteBytes) throws IOException {
        super(request);
        byte[] lido = request.getInputStream().readNBytes(limiteBytes + 1);
        if (lido.length > limiteBytes) {
            this.corpo = new byte[0];
            this.truncado = true;
        } else {
            this.corpo = lido;
            this.truncado = false;
        }
    }

    /** {@code true} quando o corpo passou do teto e não foi guardado. */
    public boolean corpoTruncado() {
        return truncado;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream fonte = new ByteArrayInputStream(corpo);
        return new ServletInputStream() {
            @Override
            public int read() {
                return fonte.read();
            }

            @Override
            public boolean isFinished() {
                return fonte.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Leitura assincrona nao se aplica: o corpo ja esta inteiro em memoria.
                throw new UnsupportedOperationException("corpo ja lido para memoria");
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    /** O corpo lido, para quem precisa inspecioná-lo sem consumir o fluxo. */
    public byte[] corpo() {
        return corpo;
    }
}
