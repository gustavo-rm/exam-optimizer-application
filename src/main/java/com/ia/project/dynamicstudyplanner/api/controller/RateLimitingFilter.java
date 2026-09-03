package com.ia.project.dynamicstudyplanner.api.controller;

import com.ia.project.dynamicstudyplanner.api.exception.RateLimitExceededException;
import com.ia.project.dynamicstudyplanner.infra.ratelimit.RateLimitBuckets;
import io.github.bucket4j.Bucket;
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
            ClientIpResolver clientIpResolver) {
        this.buckets = buckets;
        this.handlerExceptionResolver = handlerExceptionResolver;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only rate limit the optimizer endpoints
        if (request.getRequestURI().startsWith("/api/v1/optimizer/")) {
            // Chave do balde: endereco COMPLETO, para nao juntar clientes distintos num mesmo
            // balde. O mascaramento e aplicado so no que sai para log (ver ClientIpResolver).
            String clientIp = clientIpResolver.resolve(request);
            Bucket bucket = buckets.resolve(clientIp);

            if (!bucket.tryConsume(1)) {
                // A mensagem desta excecao e registrada em log pelo InfrastructureErrorAdvice, entao
                // carrega o endereco ja mascarado: rede o suficiente para reconhecer abuso, sem
                // identificar o assinante (achado S1).
                handlerExceptionResolver.resolveException(request, response, null,
                        new RateLimitExceededException("Rate limit exceeded for client "
                                + ClientIpResolver.maskForLogging(clientIp)));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // A resolucao do endereco do cliente saiu daqui na etapa 02b e virou ClientIpResolver.
    // A versao anterior confiava no X-Forwarded-For enviado pelo cliente, o que permitia a qualquer
    // chamador trocar de balde a cada requisicao e anular o limite (achado S12).
}
