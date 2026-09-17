package com.ia.project.dynamicstudyplanner.api.controller;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O invólucro que deixa o corpo ser lido mais de uma vez.
 *
 * <h2>Por que esta classe tem teste próprio</h2>
 *
 * Ela é invisível no fluxo normal e catastrófica quando errada: se o corpo não for reposto, o
 * desserializador recebe vazio e <b>toda requisição da API vira 400</b>, sem nenhum sintoma que
 * aponte para o filtro. O achado E4 só pôde ser corrigido porque o filtro passou a ler o corpo, e
 * esta é a peça que torna isso seguro.
 */
@DisplayName("Corpo em cache: legivel quantas vezes for preciso, e limitado em tamanho")
class CachedBodyHttpServletRequestTest {

    private static final String CORPO = "{\"gaConfig\":{\"numGenerations\":100,\"populationSize\":50}}";

    private static MockHttpServletRequest requisicaoCom(String corpo) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/optimizer/generate");
        request.setContent(corpo.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    @DisplayName("o mesmo corpo pode ser lido duas vezes")
    void oMesmoCorpoPodeSerLidoDuasVezes() throws IOException {
        CachedBodyHttpServletRequest envolvida =
                new CachedBodyHttpServletRequest(requisicaoCom(CORPO), 1024);

        String primeira = new String(envolvida.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String segunda = new String(envolvida.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(primeira).isEqualTo(CORPO);
        assertThat(segunda)
                .as("a segunda leitura e a do controlador; sem ela a API inteira devolveria 400")
                .isEqualTo(CORPO);
        assertThat(envolvida.corpoTruncado()).isFalse();
    }

    @Test
    @DisplayName("o leitor de texto ve o mesmo conteudo do fluxo de bytes")
    void oLeitorDeTextoVeOMesmoConteudo() throws IOException {
        CachedBodyHttpServletRequest envolvida =
                new CachedBodyHttpServletRequest(requisicaoCom(CORPO), 1024);

        try (BufferedReader leitor = envolvida.getReader()) {
            assertThat(leitor.readLine()).isEqualTo(CORPO);
        }
        assertThat(new String(envolvida.corpo(), StandardCharsets.UTF_8)).isEqualTo(CORPO);
    }

    @Test
    @DisplayName("o fluxo relata quando terminou")
    void oFluxoRelataQuandoTerminou() throws IOException {
        CachedBodyHttpServletRequest envolvida =
                new CachedBodyHttpServletRequest(requisicaoCom("ab"), 1024);
        ServletInputStream fluxo = envolvida.getInputStream();

        assertThat(fluxo.isReady()).isTrue();
        assertThat(fluxo.isFinished()).isFalse();
        assertThat(fluxo.read()).isEqualTo('a');
        assertThat(fluxo.isFinished()).isFalse();
        assertThat(fluxo.read()).isEqualTo('b');
        assertThat(fluxo.isFinished()).isTrue();
        assertThat(fluxo.read()).isEqualTo(-1);
    }

    @Test
    @DisplayName("leitura assincrona nao e suportada, e diz isso em vez de falhar em silencio")
    void leituraAssincronaNaoEhSuportada() throws IOException {
        // O corpo ja esta inteiro em memoria: um ReadListener nunca teria o que notificar. Falhar
        // alto e melhor que aceitar o registro e nunca chamar de volta.
        ServletInputStream fluxo =
                new CachedBodyHttpServletRequest(requisicaoCom(CORPO), 1024).getInputStream();

        assertThatThrownBy(() -> fluxo.setReadListener(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("corpo acima do teto e descartado, e o descarte e anunciado")
    void corpoAcimaDoTetoEhDescartado() throws IOException {
        // Guardar um corpo de tamanho arbitrario seria trocar um problema por outro: bastaria
        // enviar um corpo enorme para consumir memoria da replica antes de qualquer validacao.
        CachedBodyHttpServletRequest envolvida =
                new CachedBodyHttpServletRequest(requisicaoCom(CORPO), 10);

        assertThat(envolvida.corpoTruncado())
                .as("quem chama precisa SABER que nao ha corpo, para nao confundir com corpo vazio")
                .isTrue();
        assertThat(envolvida.corpo()).isEmpty();
        assertThat(envolvida.getInputStream().readAllBytes()).isEmpty();
    }

    @Test
    @DisplayName("corpo exatamente no teto e guardado")
    void corpoExatamenteNoTetoEhGuardado() throws IOException {
        // Fronteira: o teto e inclusivo. Um erro de um byte aqui descartaria corpos legitimos.
        String corpo = "1234567890";
        CachedBodyHttpServletRequest envolvida =
                new CachedBodyHttpServletRequest(requisicaoCom(corpo), corpo.length());

        assertThat(envolvida.corpoTruncado()).isFalse();
        assertThat(new String(envolvida.corpo(), StandardCharsets.UTF_8)).isEqualTo(corpo);
    }
}
