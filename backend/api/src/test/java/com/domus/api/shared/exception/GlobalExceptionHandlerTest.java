package com.domus.api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void conflitoNegocio_vira409ComCodigoEMensagem() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/eventos/x/presenca/marcar-todos");

        ConflitoNegocioException ex = new ConflitoNegocioException(
                "PRESENCA_NAO_HABILITADA", "Este evento não controla presença.");

        ResponseEntity<ErrorResponse> resposta = handler.handleConflitoNegocio(ex, request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().error()).isEqualTo("PRESENCA_NAO_HABILITADA");
        assertThat(resposta.getBody().message()).isEqualTo("Este evento não controla presença.");
    }
}
