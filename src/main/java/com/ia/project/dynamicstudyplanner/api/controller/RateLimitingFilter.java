package com.ia.project.dynamicstudyplanner.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ia.project.dynamicstudyplanner.api.dto.GaConfigDto;
import com.ia.project.dynamicstudyplanner.api.exception.RateLimitExceededException;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.RateLimitBuckets;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.RequestCost;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

/**
 * Filter that applies Bucket4j rate limiting to the expensive optimizer endpoints.
 * It uses a Caffeine cache to track IP addresses.
 *
 * This filter intercepts requests before the controller, ensuring DoS protection.
 * If a request is rate-limited, it throws a RateLimitExceededException that is
 * handled by the HandlerExceptionResolver to trigger the @RestControllerAdvice.
 */
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitBuckets buckets;
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;
    private final int limiteDeCorpoBytes;
    private final DistributionSummary fichasCobradas;

    /** Piso cobrado quando o corpo não permite calcular o preço. Ver {@code custoEmFichas}. */
    private static final int CUSTO_DE_CORPO_ILEGIVEL = 1;

    /**
     * O filtro deixou de construir o próprio armazenamento na etapa 06b (achado E1).
     *
     * <p>Antes, o construtor montava um cache Caffeine local. Isso tornava o filtro correto com uma
     * réplica e <b>silenciosamente incorreto</b> com mais de uma: cada processo contava separado, e
     * o limite efetivo virava N × o configurado. Agora o armazenamento vem de fora, e
     * {@code SharedStateConfig} decide — e <b>anuncia no log</b> — se ele é local ou compartilhado.
     *
     * <p>A lógica do filtro não mudou: resolver o cliente, consumir um token, recusar se não houver.
     * O que mudou foi de onde vem o balde.
     */
    public RateLimitingFilter(
            RateLimitBuckets buckets,
            HandlerExceptionResolver handlerExceptionResolver,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper,
            @Value("${api.rate-limit.max-body-bytes:262144}") int limiteDeCorpoBytes,
            MeterRegistry registry) {
        this.buckets = buckets;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
        this.limiteDeCorpoBytes = limiteDeCorpoBytes;
        this.fichasCobradas = DistributionSummary
                .builder("dynamicstudyplanner.ratelimit.cost")
                .description("Fichas cobradas por pedido — 1 e o pedido tipico de referencia")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // O limite protege a CAPACIDADE DE CALCULO, entao vale para quem PEDE trabalho, nao para
        // quem consulta o resultado. Desde a etapa 06b existe o fluxo assincrono, em que o cliente
        // busca o resultado por GET: cobrar essas consultas do mesmo balde faria o cliente gastar
        // seu limite justamente para descobrir se o trabalho que ele ja pagou terminou — e a
        // consulta custa uma leitura, nao uma otimizacao.
        if (!request.getRequestURI().startsWith("/api/v1/optimizer/")
                || "GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // O corpo precisa ser lido para saber o PRECO do pedido, e precisa continuar disponivel
        // para o controlador desserializar. Ver CachedBodyHttpServletRequest.
        CachedBodyHttpServletRequest requisicao =
                new CachedBodyHttpServletRequest(request, limiteDeCorpoBytes);
        int fichas = custoEmFichas(requisicao);
        fichasCobradas.record(fichas);

        // Chave do balde: endereco COMPLETO, para nao juntar clientes distintos num mesmo
        // balde. O mascaramento e aplicado so no que sai para log (ver ClientIpResolver).
        String clientIp = clientIpResolver.resolve(requisicao);
        Bucket bucket = buckets.resolve(clientIp);

        if (!bucket.tryConsume(fichas)) {
            // A mensagem desta excecao e registrada em log pelo InfrastructureErrorAdvice, entao
            // carrega o endereco ja mascarado: rede o suficiente para reconhecer abuso, sem
            // identificar o assinante (achado S1).
            handlerExceptionResolver.resolveException(requisicao, response, null,
                    new RateLimitExceededException("Rate limit exceeded for client "
                            + ClientIpResolver.maskForLogging(clientIp) + " (cost " + fichas + ")"));
            return;
        }

        filterChain.doFilter(requisicao, response);
    }

    /**
     * Preço do pedido, em fichas.
     *
     * <h2>O que acontece quando o corpo não pode ser lido</h2>
     *
     * Duas situações: o corpo passou do teto de tamanho, ou não é um JSON com os campos esperados.
     * Nos dois casos o filtro cobra o <b>mínimo</b>, uma ficha.
     *
     * <p>Isso parece a direção insegura e não é, porque <b>um pedido que não pode ser precificado
     * também não pode ser executado</b>. O mesmo Jackson que falha aqui falha na desserialização do
     * controlador, e um {@code gaConfig} ausente ou fora dos limites é recusado pela validação: nos
     * dois casos a resposta é 400 e <b>nenhuma otimização roda</b>. O custo real de um corpo
     * deformado é o de uma tentativa de leitura — que é exatamente o que o piso de uma ficha cobre.
     *
     * <p>Não há brecha aqui: para burlar o preço seria preciso um corpo que este cálculo não
     * consegue ler <b>e</b> que a desserialização aceita. Como os dois usam o mesmo interpretador,
     * esse corpo não existe.
     *
     * <p>Cobrar o máximo, ao contrário, puniria com 129 fichas — um quinto do balde — quem tem um
     * defeito de integração, por um pedido que ao servidor custou uma tentativa de parse.
     *
     * <p>O que <b>não</b> acontece aqui é rejeitar por conteúdo: decidir se o pedido é válido é
     * trabalho da validação, não do preço. Este método só responde "quanto custa".
     */
    private int custoEmFichas(CachedBodyHttpServletRequest requisicao) {
        if (requisicao.corpoTruncado()) {
            return CUSTO_DE_CORPO_ILEGIVEL;
        }
        try {
            JsonNode raiz = objectMapper.readTree(requisicao.corpo());
            if (raiz == null || raiz.isMissingNode()) {
                return CUSTO_DE_CORPO_ILEGIVEL;
            }
            JsonNode ga = raiz.path("gaConfig");
            JsonNode exame = raiz.path("exam");
            if (ga.isMissingNode() || !ga.hasNonNull("numGenerations")
                    || !ga.hasNonNull("populationSize")) {
                return CUSTO_DE_CORPO_ILEGIVEL;
            }
            int geracoes = ga.path("numGenerations").asInt();
            int populacao = ga.path("populationSize").asInt();
            if (foraDoContrato(geracoes, populacao)) {
                return CUSTO_DE_CORPO_ILEGIVEL;
            }
            return RequestCost.fichas(contarDisciplinas(exame), geracoes, populacao);
        } catch (IOException e) {
            return CUSTO_DE_CORPO_ILEGIVEL;
        }
    }

    /**
     * {@code true} quando os parâmetros estão fora do que o contrato aceita.
     *
     * <p>Um pedido assim será recusado com <b>400</b> pela validação, sem rodar otimização nenhuma —
     * então ele custa o piso, e não o que os números pediriam. Sem esta verificação, mandar
     * {@code populationSize: 1000000} drenaria o balde inteiro por um pedido que jamais executaria,
     * e o cliente receberia 429 no lugar do 400 que explica o erro dele.
     *
     * <p>Os limites vêm das constantes de {@link GaConfigDto}, as mesmas que alimentam as anotações
     * de validação: não há como o preço e o contrato divergirem.
     */
    private static boolean foraDoContrato(int geracoes, int populacao) {
        return geracoes < GaConfigDto.MIN_GENERATIONS || geracoes > GaConfigDto.MAX_GENERATIONS
                || populacao < GaConfigDto.MIN_POPULATION || populacao > GaConfigDto.MAX_POPULATION;
    }

    /** Disciplinas do edital: conhecimentos gerais mais as de cada eixo específico. */
    private static int contarDisciplinas(JsonNode exame) {
        int total = exame.path("generalKnowledgeSubjects").size();
        for (JsonNode eixo : exame.path("specificKnowledgeAxes")) {
            total += eixo.path("subjects").size();
        }
        return total;
    }

    // A resolucao do endereco do cliente saiu daqui na etapa 02b e virou ClientIpResolver.
    // A versao anterior confiava no X-Forwarded-For enviado pelo cliente, o que permitia a qualquer
    // chamador trocar de balde a cada requisicao e anular o limite (achado S12).
}
