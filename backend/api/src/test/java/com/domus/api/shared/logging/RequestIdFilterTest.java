package com.domus.api.shared.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RequestIdFilterTest {

    private final RequestIdFilter filtro = new RequestIdFilter();

    @Test
    void geraRequestId_poeNoMdcENoHeader_eLimpaAoFim() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Request-Id")).thenReturn(null);

        // Captura o request_id que estava no MDC DURANTE a requisição.
        AtomicReference<String> durante = new AtomicReference<>();
        doAnswer(inv -> {
            durante.set(MDC.get(RequestIdFilter.REQUEST_ID));
            return null;
        }).when(chain).doFilter(request, response);

        filtro.doFilter(request, response, chain);

        assertThat(durante.get()).isNotBlank();
        verify(response).setHeader(eq("X-Request-Id"), eq(durante.get()));
        // Depois da requisição o MDC deve estar limpo (thread reutilizada).
        assertThat(MDC.get(RequestIdFilter.REQUEST_ID)).isNull();
    }

    @Test
    void honraRequestIdVindoDoCliente() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getHeader("X-Request-Id")).thenReturn("id-externo-123");

        AtomicReference<String> durante = new AtomicReference<>();
        doAnswer(inv -> {
            durante.set(MDC.get(RequestIdFilter.REQUEST_ID));
            return null;
        }).when(chain).doFilter(request, response);

        filtro.doFilter(request, response, chain);

        assertThat(durante.get()).isEqualTo("id-externo-123");
        verify(response).setHeader("X-Request-Id", "id-externo-123");
    }
}
