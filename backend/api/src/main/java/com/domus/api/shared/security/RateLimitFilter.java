package com.domus.api.shared.security;

import com.domus.api.shared.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Rate limiting por IP, janela fixa no Redis, em duas camadas: global (todos os endpoints)
 * e auth (rotas sensíveis). Estoura → HTTP 429 + Retry-After. Roda antes da autenticação.
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PREFIXO_GLOBAL = "rl:global:";
    private static final String PREFIXO_AUTH = "rl:auth:";
    private static final Duration JANELA = Duration.ofSeconds(60);

    /** Rotas sensíveis que recebem o limite apertado de auth (além do global). */
    private static final List<String> ROTAS_AUTH = List.of(
            "/auth/login",
            "/auth/google/login",
            "/auth/google/registrar",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/igrejas/registrar",
            // Código de vínculo não expira: tentativa de adivinhação merece limite estrito.
            "/igrejas-vinculadas/entrar"
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final int limiteGlobal;
    private final int limiteAuth;
    private final boolean trustForwardedFor;

    public RateLimitFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.ratelimit.global-por-minuto:100}") int limiteGlobal,
            @Value("${app.ratelimit.auth-por-minuto:10}") int limiteAuth,
            @Value("${app.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.limiteGlobal = limiteGlobal;
        this.limiteAuth = limiteAuth;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Preflight de CORS não é tráfego real do usuário; não conta.
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip = resolverIp(request);
        long minuto = Instant.now().getEpochSecond() / 60;

        // Camada global: todos os endpoints.
        if (excedeu(PREFIXO_GLOBAL, ip, minuto, limiteGlobal)) {
            responder429(request, response);
            return;
        }

        // Camada auth: só rotas sensíveis.
        if (ehRotaAuth(request.getRequestURI()) && excedeu(PREFIXO_AUTH, ip, minuto, limiteAuth)) {
            responder429(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean excedeu(String prefixo, String ip, long minuto, int limite) {
        String chave = prefixo + ip + ":" + minuto;
        Long contador = redisTemplate.opsForValue().increment(chave);
        if (contador != null && contador == 1L) {
            redisTemplate.expire(chave, JANELA);
        }
        return contador != null && contador > limite;
    }

    private boolean ehRotaAuth(String uri) {
        return ROTAS_AUTH.stream().anyMatch(uri::startsWith);
    }

    /**
     * Por padrão usa o IP do socket. Só confia em headers com proxy confiável
     * (trust-forwarded-for=true) — sem isso, qualquer um forjaria o IP.
     * Atrás da Cloudflare, prefere CF-Connecting-IP (sempre é o IP real do cliente).
     */
    private String resolverIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // Primeiro IP da lista = cliente original.
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void responder429(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long segundosParaReset = 60 - (Instant.now().getEpochSecond() % 60);

        log.warn("Rate limit excedido. ip={}, path={}", resolverIp(request), request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(segundosParaReset));

        ErrorResponse corpo = ErrorResponse.of(429, "RATE_LIMIT_EXCEDIDO",
                "Muitas requisições em pouco tempo. Aguarde um instante e tente novamente.");
        response.getWriter().write(objectMapper.writeValueAsString(corpo));
    }
}
