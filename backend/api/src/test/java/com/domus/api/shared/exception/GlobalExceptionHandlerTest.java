package com.domus.api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    /** Método anotado só pra gerar uma ConstraintViolationException real via validação de parâmetro
     * (é assim que @Validated + @Size em @RequestParam falha na prática, diferente de @Valid em @RequestBody). */
    private static class MetodoValidado {
        void buscar(@jakarta.validation.constraints.Size(max = 3, message = "Muito longo.") String q) {}
    }

    @Test
    void constraintViolation_vira400ComCampoEMensagem() {
        var validatorFactory = Validation.buildDefaultValidatorFactory();
        var executableValidator = validatorFactory.getValidator().forExecutables();
        MetodoValidado alvo = new MetodoValidado();
        java.lang.reflect.Method metodo;
        try {
            metodo = MetodoValidado.class.getDeclaredMethod("buscar", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        Set<jakarta.validation.ConstraintViolation<MetodoValidado>> violacoes =
                executableValidator.validateParameters(alvo, metodo, new Object[]{"texto muito maior que três"});
        ConstraintViolationException ex = new ConstraintViolationException(violacoes);

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/visitantes");

        ResponseEntity<ErrorResponse> resposta = handler.handleConstraintViolation(ex, request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(400);
        assertThat(resposta.getBody().campos()).containsKey("q");
        assertThat(resposta.getBody().campos().get("q")).isEqualTo("Muito longo.");
    }

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
