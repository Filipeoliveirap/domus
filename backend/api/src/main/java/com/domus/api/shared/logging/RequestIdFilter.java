package com.domus.api.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Carimba um {@code request_id} único no MDC (o "post-it" por thread) no começo de cada
 * requisição, para que TODA linha de log daquela requisição saia com esse id — permitindo
 * correlacionar logs entre si e ligar um erro do Sentry ao seu conjunto de logs.
 *
 * <p>Roda como filtro mais externo (ordem mais alta): assim o {@code request_id} já existe
 * quando qualquer outro filtro (rate limit, segurança) loga. Limpa o MDC no {@code finally}
 * porque a thread é reutilizada entre requisições — deixar contexto vazado seria um bug.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID = "request_id";
    private static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Honra um id vindo do cliente/proxy (rastreio distribuído); senão gera um novo.
        String requestId = request.getHeader(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(REQUEST_ID, requestId);
        response.setHeader(HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Thread reutilizada: limpa TODO o contexto (inclui usuario_id/igreja_id).
            MDC.clear();
        }
    }
}
