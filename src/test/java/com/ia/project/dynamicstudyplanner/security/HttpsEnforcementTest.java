package com.ia.project.dynamicstudyplanner.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Enforcement defensivo de TLS (achado S11), e a fronteira do que este repositório pode resolver.
 *
 * <h2>O que é e o que não é responsabilidade deste código</h2>
 *
 * <b>Terminar TLS não é.</b> A aplicação não tem, e não deveria ter, o certificado: isso é
 * infraestrutura. O que é responsabilidade daqui é não servir HTTP em silêncio quando o operador
 * declarou que existe um proxy TLS na frente — recusar a requisição em claro e emitir HSTS.
 *
 * <h2>Por que o padrão é desligado</h2>
 *
 * Ligar sem proxy real quebraria todo acesso local e, pior, faria a decisão depender do cabeçalho
 * {@code X-Forwarded-Proto}, que só vale vindo de um proxy confiável — o mesmo problema do S12. Por
 * isso {@code api.security.require-https} e {@code api.trusted-proxies} descrevem a mesma premissa
 * de implantação e devem ser ligadas juntas.
 */
@DisplayName("S11: enforcement defensivo de TLS")
class HttpsEnforcementTest {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "api.rate-limit.capacity=100",
            "api.rate-limit.refill-tokens=100"
    })
    @DisplayName("Padrao (desligado): desenvolvimento local continua funcionando")
    class PadraoDesligado {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("requisicao em claro e aceita, e nao ha HSTS")
        void requisicaoEmClaroEhAceita() throws Exception {
            MvcResult resultado = mockMvc.perform(post("/api/v1/optimizer/generate")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

            assertThat(resultado.getResponse().getStatus())
                    .as("com a chave desligada, a requisicao segue o fluxo normal e cai na validacao")
                    .isEqualTo(400);
            assertThat(resultado.getResponse().getHeader("Strict-Transport-Security"))
                    .as("HSTS sobre HTTP puro nao faria sentido e nao e emitido")
                    .isNull();
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @TestPropertySource(properties = {
            "api.rate-limit.capacity=100",
            "api.rate-limit.refill-tokens=100",
            "api.security.require-https=true",
            "api.security.hsts-max-age-seconds=31536000"
    })
    @DisplayName("Ligado: postura de producao atras de proxy TLS")
    class Ligado {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("requisicao em claro e recusada, com redirecionamento para https")
        void requisicaoEmClaroEhRecusada() throws Exception {
            MvcResult resultado = mockMvc.perform(post("/api/v1/optimizer/generate")
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();

            assertThat(resultado.getResponse().getStatus())
                    .as("a requisicao nao pode ser processada em claro")
                    .isIn(301, 302, 307, 308);
            assertThat(resultado.getResponse().getRedirectedUrl())
                    .as("o redirecionamento aponta para o mesmo recurso sobre TLS")
                    .startsWith("https://");
        }

        @Test
        @DisplayName("requisicao segura e aceita e recebe HSTS")
        void requisicaoSeguraRecebeHsts() throws Exception {
            MvcResult resultado = mockMvc.perform(post("/api/v1/optimizer/generate")
                            .secure(true)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn();

            assertThat(resultado.getResponse().getStatus())
                    .as("sobre TLS, a requisicao segue o fluxo normal")
                    .isEqualTo(400);
            assertThat(resultado.getResponse().getHeader("Strict-Transport-Security"))
                    .as("HSTS instrui o navegador a nunca mais usar HTTP puro neste dominio")
                    .isNotNull()
                    .contains("max-age=31536000")
                    .contains("includeSubDomains");
        }
    }
}
