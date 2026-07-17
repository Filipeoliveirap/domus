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
 * Rate limiting geral por IP, em duas camadas independentes (ambas no Redis):
 * <ul>
 *   <li><b>Global</b> — teto generoso em TODOS os endpoints, contra abuso/scraping;
 *   <li><b>Auth</b> — teto apertado nas rotas sensíveis (login, cadastro, reset), contra
 *       força bruta e enumeração.
 * </ul>
 *
 * <p>Algoritmo: janela fixa por minuto. A chave inclui o minuto atual
 * ({@code rl:<escopo>:<ip>:<minuto>}); {@code INCR} é atômico no Redis, então não há
 * corrida. Estourou → HTTP 429 + {@code Retry-After}, sem chegar ao controller.
 *
 * <p>Roda antes da autenticação: floods anônimos são barrados barato. Complementa (não
 * substitui) o {@link LoginAttemptService}, que é anti-força-bruta por conta (e-mail).
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
            "/igrejas/registrar"
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

    /** Incrementa o contador da janela atual e diz se ultrapassou o limite. */
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
     * IP de origem. Por padrão usa o IP do socket. Só confia em headers quando há um proxy
     * confiável na frente (trust-forwarded-for=true) — confiar sem proxy permitiria forjar o
     * IP e escapar/poluir o limite.
     *
     * <p>Atrás da Cloudflare, prefere {@code CF-Connecting-IP}: é o IP real do cliente, sempre,
     * sem a ambiguidade do {@code X-Forwarded-For} (que pode conter itens forjados pelo cliente
     * antes de chegar na Cloudflare). Cai no XFF só se o CF não vier.
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
