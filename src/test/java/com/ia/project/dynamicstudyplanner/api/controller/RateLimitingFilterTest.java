package com.ia.project.dynamicstudyplanner.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.api.dto.GaConfigDto;
import com.ia.project.dynamicstudyplanner.api.exception.RateLimitExceededException;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.LocalRateLimitBuckets;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.RequestCost;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * O filtro de limite de taxa — agora cobrando por <b>custo</b>, não por chamada (achado E4).
 *
 * <h2>O que mudou, e por que o teste precisou mudar junto</h2>
 *
 * Até a etapa 06b o filtro consumia uma ficha por requisição e nunca olhava o corpo. Agora ele lê o
 * corpo para calcular o preço, o que traz duas obrigações novas que estes testes verificam:
 *
 * <ul>
 *   <li>o preço tem que <b>acompanhar o tamanho do problema pedido</b>, e não a contagem de chamadas;</li>
 *   <li>o corpo tem que <b>continuar legível</b> pelo controlador — se o filtro consumisse o fluxo,
 *       toda requisição chegaria vazia ao desserializador, e a API inteira quebraria.</li>
 * </ul>
 *
 * <p>O armazenamento é o local, de propósito: aqui se verifica a lógica do filtro, que é a mesma nos
 * dois armazenamentos. Que o modo compartilhado faz o limite valer entre réplicas é verificado por
 * {@code RateLimitBucketsCompartilhadoTest}, contra um Redis de verdade.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Limite de taxa: o balde conta custo, nao chamadas")
class RateLimitingFilterTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filtroComCapacidade(int fichas) {
        return new RateLimitingFilter(
                new LocalRateLimitBuckets(() -> BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(fichas)
                                .refillIntervally(fichas, Duration.ofMinutes(1))
                                .build())
                        .build()),
                handlerExceptionResolver,
                // Sem proxy confiavel declarado: o endereco da conexao e a unica fonte considerada.
                new ClientIpResolver(""),
                JSON,
                262_144,
                new SimpleMeterRegistry());
    }

    /** Requisição com um corpo de verdade — o filtro agora depende dele para precificar. */
    private static MockHttpServletRequest pedido(String ip, int disciplinas, int geracoes, int populacao) {
        StringBuilder subjects = new StringBuilder();
        for (int i = 0; i < disciplinas; i++) {
            subjects.append(i > 0 ? "," : "")
                    .append("{\"name\":\"D").append(i).append("\",\"questionCount\":10,\"cognitiveLoad\":3}");
        }
        String corpo = "{\"exam\":{\"generalKnowledgeSubjects\":[" + subjects + "],"
                + "\"specificKnowledgeAxes\":[]},"
                + "\"gaConfig\":{\"totalStudyDays\":365,\"numGenerations\":" + geracoes
                + ",\"populationSize\":" + populacao + "}}";
        return comCorpo(ip, corpo);
    }

    private static MockHttpServletRequest comCorpo(String ip, String corpo) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/optimizer/generate");
        request.setRequestURI("/api/v1/optimizer/generate");
        request.setRemoteAddr(ip);
        request.setContent(corpo.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    @Test
    @DisplayName("o corpo continua legivel depois do filtro — sem isto, a API inteira quebraria")
    void oCorpoContinuaLegivelDepoisDoFiltro() throws ServletException, IOException {
        MockHttpServletRequest request = pedido("10.0.0.1", 15, 100, 50);
        String original = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);

        final String[] lido = new String[1];
        FilterChain cadeia = (req, res) ->
                lido[0] = new String(((HttpServletRequest) req).getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);

        filtroComCapacidade(650).doFilterInternal(request, new MockHttpServletResponse(), cadeia);

        assertThat(lido[0])
                .as("o filtro le o corpo para precificar; se nao o repuser, o desserializador "
                        + "recebe vazio e toda requisicao vira 400")
                .isEqualTo(original);
    }

    @Test
    @DisplayName("um pedido tipico custa 1 ficha; o balde de 650 aguenta centenas deles")
    void pedidoTipicoCustaUmaFicha() throws ServletException, IOException {
        RateLimitingFilter filtro = filtroComCapacidade(650);
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 100; i++) {
            filtro.doFilterInternal(pedido("10.0.0.2", 15, 100, 50), response, filterChain);
        }

        verify(filterChain, times(100)).doFilter(any(ServletRequest.class), eq(response));
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("um pedido caro esgota o balde em poucas chamadas — e essa e a correcao do E4")
    void pedidoCaroEsgotaOBaldeEmPoucasChamadas() throws ServletException, IOException {
        // Antes, este pedido custava o mesmo que o tipico: uma ficha. Era por isso que 17 clientes
        // obedientes saturavam a maquina.
        int custo = RequestCost.fichas(40, 1000, 500);
        assertThat(custo).as("o pedido mais caro aceito custa muito mais que o tipico").isGreaterThan(100);

        RateLimitingFilter filtro = filtroComCapacidade(650);
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 6; i++) {
            filtro.doFilterInternal(pedido("10.0.0.3", 40, 1000, 500), response, filterChain);
        }

        // 650 / 129 = 5 pedidos, e o sexto e recusado.
        verify(filterChain, times(5)).doFilter(any(ServletRequest.class), eq(response));
        verify(handlerExceptionResolver, times(1)).resolveException(
                any(), eq(response), isNull(), any(RateLimitExceededException.class));
    }

    @Test
    @DisplayName("corpo ilegivel custa o piso — porque tambem nao roda otimizacao nenhuma")
    void corpoIlegivelCustaOPiso() throws ServletException, IOException {
        // A primeira versao desta correcao cobrava o MAXIMO aqui, achando que o contrario seria uma
        // brecha. Nao e: para burlar o preco seria preciso um corpo que este calculo nao le E que a
        // desserializacao aceita — e as duas usam o mesmo Jackson, entao esse corpo nao existe. Um
        // corpo deformado vira 400 sem rodar o algoritmo, e custa ao servidor uma tentativa de
        // parse. Cobrar 129 fichas por isso puniria quem tem defeito de integracao.
        RateLimitingFilter filtro = filtroComCapacidade(20);
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 20; i++) {
            filtro.doFilterInternal(comCorpo("10.0.0.4", "isto nao e json"), response, filterChain);
        }

        verify(filterChain, times(20)).doFilter(any(ServletRequest.class), eq(response));
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("corpo acima do teto nao e guardado em memoria")
    void corpoAcimaDoTetoNaoEhGuardadoEmMemoria() throws ServletException, IOException {
        // Guardar um corpo de tamanho arbitrario seria trocar um problema por outro: bastaria
        // enviar um corpo enorme para consumir memoria da replica antes de qualquer validacao.
        RateLimitingFilter filtroComTetoBaixo = new RateLimitingFilter(
                new LocalRateLimitBuckets(() -> BucketConfiguration.builder()
                        .addLimit(Bandwidth.builder().capacity(650)
                                .refillIntervally(650, Duration.ofMinutes(1)).build())
                        .build()),
                handlerExceptionResolver, new ClientIpResolver(""), JSON,
                100, new SimpleMeterRegistry());
        MockHttpServletResponse response = new MockHttpServletResponse();

        final int[] tamanhoRecebido = new int[1];
        FilterChain cadeia = (req, res) -> tamanhoRecebido[0] =
                ((HttpServletRequest) req).getInputStream().readAllBytes().length;

        filtroComTetoBaixo.doFilterInternal(
                pedido("10.0.0.5", 15, 100, 50), response, cadeia);

        assertThat(tamanhoRecebido[0])
                .as("acima do teto o corpo nao e retido; a validacao recusa o pedido adiante")
                .isZero();
    }

    @Test
    @DisplayName("pedido fora do contrato custa o piso, e recebe 400 em vez de 429")
    void pedidoForaDoContratoCustaOPiso() throws ServletException, IOException {
        // populationSize acima do teto do contrato: a validacao devolve 400 sem rodar otimizacao.
        // Sem esta regra, o preco calculado sobre 1.000.000 drenaria o balde inteiro, e o cliente
        // receberia 429 no lugar do 400 que explica o erro dele.
        RateLimitingFilter filtro = filtroComCapacidade(20);
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < 20; i++) {
            filtro.doFilterInternal(pedido("10.0.0.8", 15, 100, 1_000_000), response, filterChain);
        }

        verify(filterChain, times(20)).doFilter(any(ServletRequest.class), eq(response));
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("os limites do preco sao os mesmos do contrato, nao uma copia deles")
    void osLimitesDoPrecoSaoOsDoContrato() throws ServletException, IOException {
        // O pedido no TETO do contrato tem que ser precificado de verdade; o primeiro valor acima
        // dele, nao. Se alguem mexer no teto do DTO e o filtro nao acompanhar, este par diverge.
        RateLimitingFilter filtro = filtroComCapacidade(650);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // No teto: 129 fichas cada, cabem 5 no balde de 650.
        for (int i = 0; i < 6; i++) {
            filtro.doFilterInternal(
                    pedido("10.0.0.9", 40, GaConfigDto.MAX_GENERATIONS, GaConfigDto.MAX_POPULATION),
                    response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(any(ServletRequest.class), eq(response));
        verify(handlerExceptionResolver, times(1)).resolveException(
                any(), eq(response), isNull(), any(RateLimitExceededException.class));
    }

    @Test
    @DisplayName("consulta por GET nao consome ficha nenhuma")
    void consultaPorGetNaoConsomeFicha() throws ServletException, IOException {
        // O limite protege a capacidade de CALCULO. Cobrar as consultas faria o cliente gastar o
        // limite dele so para descobrir se o trabalho que ja pagou terminou.
        MockHttpServletRequest consulta = new MockHttpServletRequest("GET", "/api/v1/optimizer/jobs/abc");
        consulta.setRequestURI("/api/v1/optimizer/jobs/abc");
        consulta.setRemoteAddr("10.0.0.6");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RateLimitingFilter filtro = filtroComCapacidade(1);

        for (int i = 0; i < 20; i++) {
            filtro.doFilterInternal(consulta, response, filterChain);
        }

        verify(filterChain, times(20)).doFilter(consulta, response);
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    @DisplayName("outros caminhos nao passam pelo limite")
    void outrosCaminhosNaoPassamPeloLimite() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/other");
        request.setRequestURI("/api/v1/other");
        request.setRemoteAddr("10.0.0.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RateLimitingFilter filtro = filtroComCapacidade(1);

        for (int i = 0; i < 5; i++) {
            filtro.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verifyNoInteractions(handlerExceptionResolver);
    }
}
