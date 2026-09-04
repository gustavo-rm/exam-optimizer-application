package com.ia.project.dynamicstudyplanner.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Trava o acesso aos endpoints de infraestrutura.
 *
 * <h2>Por que este arquivo existe</h2>
 *
 * A configuração de segurança terminava em {@code anyRequest().authenticated()} sem declarar
 * mecanismo de autenticação nenhum. O resultado não era "protegido": era <b>inalcançável</b> — 403
 * permanente, inclusive para quem tivesse a senha. Isso produziu dois defeitos registrados em
 * etapas diferentes, S5 (a sonda de saúde de um orquestrador receberia 403 e reiniciaria um
 * processo saudável) e F9 (a métrica de tempo da otimização era publicada e não podia ser lida).
 *
 * <p>Os dois eram invisíveis para a suíte porque nenhum teste consultava esses caminhos. É o tipo
 * de defeito que só aparece em produção, e só depois que alguém depende dele.
 */
@SpringBootTest
// Sem isto o Spring Boot desliga a exportacao de metricas em teste, e /actuator/prometheus nem
// chega a existir no contexto — o teste passaria a medir a ausencia do endpoint, nao a regra de
// seguranca que ele deveria travar.
@AutoConfigureObservability
@TestPropertySource(properties = {
        "spring.security.user.name=metricas",
        "spring.security.user.password=senha-de-teste"
})
@DisplayName("Actuator: saude publica, metricas autenticadas — e as duas alcancaveis")
class ActuatorAcessivelTest {

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("a sonda de saude responde sem credencial")
    void aSondaDeSaudeRespondeSemCredencial() throws Exception {
        // Uma sonda de Kubernetes nao carrega credencial. Se este teste voltar a reprovar com 403,
        // o efeito em producao e reinicio em laco de um processo saudavel.
        mvc().perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a metrica de tempo da otimizacao e legivel com credencial")
    void aMetricaDeTempoEhLegivelComCredencial() throws Exception {
        mvc().perform(get("/actuator/prometheus").with(basica()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "dynamicstudyplanner_optimization_duration")));
    }

    @Test
    @DisplayName("a metrica continua fechada para quem nao apresenta credencial")
    void aMetricaContinuaFechadaSemCredencial() throws Exception {
        // Contraprova: sem ela, este arquivo passaria mesmo que a correcao tivesse aberto o
        // endpoint de metricas para qualquer um — que seria trocar um defeito por outro pior.
        mvc().perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor basica() {
        return httpBasic("metricas", "senha-de-teste");
    }
}
