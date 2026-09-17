package com.ia.project.dynamicstudyplanner.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Regressão da segunda metade do achado S12, medida de ponta a ponta pela cadeia real de filtros.
 *
 * <h2>Por que este teste é separado do {@code ClientIpResolverTest}</h2>
 *
 * O {@code ClientIpResolverTest} verifica a lógica de resolução isoladamente, e ela estava correta
 * mesmo assim o limite continuava burlável. A causa estava fora do resolvedor: com
 * {@code server.forward-headers-strategy=framework}, o Spring registra o
 * {@code ForwardedHeaderFilter}, que roda antes dos filtros da aplicação e <b>reescreve</b>
 * {@code getRemoteAddr()} com o {@code X-Forwarded-For} do cliente. O resolvedor recebia um endereço
 * de conexão já adulterado e não tinha como saber.
 *
 * <p>Só um teste que atravesse a cadeia inteira pega esse tipo de defeito. Este atravessa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "api.rate-limit.capacity=3",
        "api.rate-limit.refill-tokens=3",
        "api.rate-limit.refill-duration-minutes=1"
})
@DisplayName("S12 ponta a ponta: cabecalho forjado nao burla o limite")
class ForwardedHeaderConfigTest {

    private static final String CARGA_INVALIDA =
            "{\"exam\":null,\"studentProfile\":null,\"gaConfig\":null}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("o ForwardedHeaderFilter nao esta registrado com a configuracao padrao")
    void oForwardedHeaderFilterNaoEstaRegistrado() {
        boolean registrado = context.getBeansOfType(FilterRegistrationBean.class).values().stream()
                .anyMatch(reg -> reg.getFilter() != null
                        && reg.getFilter().getClass().getSimpleName().equals("ForwardedHeaderFilter"));

        assertThat(registrado)
                .as("com server.forward-headers-strategy=none o filtro nao entra na cadeia, "
                        + "e getRemoteAddr() volta a ser o endereco real da conexao")
                .isFalse();
    }

    @Test
    @DisplayName("seis requisicoes com X-Forwarded-For diferente caem no mesmo balde")
    void cabecalhoVariandoNaoBurlaOLimite() throws Exception {
        int bloqueadas = 0;

        for (int i = 1; i <= 6; i++) {
            int status = mockMvc.perform(post("/api/v1/optimizer/generate")
                            .header("X-Forwarded-For", "203.0.113." + i)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(CARGA_INVALIDA))
                    .andReturn().getResponse().getStatus();
            if (status == 429) {
                bloqueadas++;
            }
        }

        // Capacidade 3: as tres primeiras passam pela validacao (400) e as tres seguintes sao
        // bloqueadas. Antes da correcao completa, este laco produzia seis 400 e zero bloqueios.
        assertThat(bloqueadas)
                .as("variar o cabecalho a cada requisicao nao pode render balde novo")
                .isEqualTo(3);
    }
}
