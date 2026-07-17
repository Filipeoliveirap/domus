package com.domus.api.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    StringRedisTemplate redis;
    ValueOperations<String, String> valueOps;
    ObjectMapper objectMapper;
    HttpServletRequest request;
    HttpServletResponse response;
    FilterChain chain;
    StringWriter corpoResposta;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        // Espelha o ObjectMapper do Spring Boot (registra o suporte a java.time).
        objectMapper = new ObjectMapper().findAndRegisterModules();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(request.getRemoteAddr()).thenReturn("1.2.3.4");
        corpoResposta = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(corpoResposta));
    }

    /** Faz o INCR devolver um valor conforme o prefixo da chave (global vs auth). */
    private void incrementaPara(String prefixo, long valor) {
        when(valueOps.increment(startsWith(prefixo))).thenReturn(valor);
    }

    private RateLimitFilter filtro(int global, int auth, boolean trustFwd) {
        return new RateLimitFilter(redis, objectMapper, global, auth, trustFwd);
    }

    @Test
    void abaixoDoLimite_deixaPassar() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        incrementaPara("rl:global:", 1L);

        filtro(100, 10, false).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void acimaDoLimiteGlobal_bloqueiaCom429() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        incrementaPara("rl:global:", 101L);

        filtro(100, 10, false).doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(response).setHeader(eq(HttpHeaders.RETRY_AFTER), anyString());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rotaAuth_aplicaLimiteApertado() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/login");
        incrementaPara("rl:global:", 1L);   // global ok
        incrementaPara("rl:auth:", 11L);     // auth estourado

        filtro(100, 10, false).doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rotaNaoAuth_naoContaNoBaldeDeAuth() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        incrementaPara("rl:global:", 1L);

        filtro(100, 10, false).doFilter(request, response, chain);

        verify(valueOps, never()).increment(startsWith("rl:auth:"));
        verify(chain).doFilter(request, response);
    }

    @Test
    void preflightOptions_naoEhContado() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");

        filtro(100, 10, false).doFilter(request, response, chain);

        verify(valueOps, never()).increment(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void trustForwardedFor_usaPrimeiroIpDoHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/membros");
        when(request.getHeader("X-Forwarded-For")).thenReturn("9.9.9.9, 10.0.0.1");
        incrementaPara("rl:global:", 1L);

        filtro(100, 10, true).doFilter(request, response, chain);

        verify(valueOps).increment(startsWith("rl:global:9.9.9.9:"));
    }
}
