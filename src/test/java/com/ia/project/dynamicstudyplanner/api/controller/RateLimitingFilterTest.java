package com.ia.project.dynamicstudyplanner.api.controller;

import com.ia.project.dynamicstudyplanner.api.exception.RateLimitExceededException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private RateLimitingFilter filter;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        // Capacidade 2, recarga de 2 a cada minuto. O resolvedor vai sem proxy confiavel
        // declarado, que e o padrao seguro: o endereco da conexao e a unica fonte considerada.
        filter = new RateLimitingFilter(2, 2, 1, handlerExceptionResolver,
                new ClientIpResolver(""));
    }

    @Test
    void doFilterInternal_ShouldAllowRequestsUnderLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/optimizer/generate");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);
        verifyNoInteractions(handlerExceptionResolver);
    }

    @Test
    void doFilterInternal_ShouldBlockRequestsOverLimit() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/optimizer/generate");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 1st request - allowed
        filter.doFilterInternal(request, response, filterChain);
        // 2nd request - allowed
        filter.doFilterInternal(request, response, filterChain);
        // 3rd request - blocked
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(2)).doFilter(request, response);
        verify(handlerExceptionResolver, times(1)).resolveException(
                eq(request), eq(response), isNull(), any(RateLimitExceededException.class));
    }

    @Test
    void doFilterInternal_ShouldNotLimitOtherEndpoints() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/other");
        request.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // All 5 requests should pass since we're only limiting /api/v1/optimizer/
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verifyNoInteractions(handlerExceptionResolver);
    }
}
