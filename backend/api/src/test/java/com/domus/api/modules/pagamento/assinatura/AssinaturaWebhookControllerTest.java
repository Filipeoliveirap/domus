package com.domus.api.modules.pagamento.assinatura;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssinaturaWebhookControllerTest {

    private AssinaturaWebhookService webhookService;
    private AssinaturaWebhookController controller;

    @BeforeEach
    void setup() {
        webhookService = mock(AssinaturaWebhookService.class);
        controller = new AssinaturaWebhookController(webhookService);
    }

    @Test
    void deveProcessarWebhookDeAssinaturaComSucesso() {
        Map<String, Object> payload = Map.of(
            "type", "subscription_preapproval",
            "data", Map.of("id", "preapp_123", "status", "authorized")
        );

        ResponseEntity<Void> response = controller.receberWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(webhookService).processarWebhookAssinatura("preapp_123", "authorized");
    }

    @Test
    void deveProcessarWebhookDeAssinaturaMesmoSemStatusNoPayload() {
        Map<String, Object> payload = Map.of(
            "type", "subscription_preapproval",
            "action", "updated",
            "data", Map.of("id", "preapp_123")
        );

        ResponseEntity<Void> response = controller.receberWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(webhookService).processarWebhookAssinatura("preapp_123", null);
    }

    @Test
    void deveIgnorarWebhookComTipoDiferenteDeSubscriptionPreapproval() {
        Map<String, Object> payload = Map.of(
            "type", "payment",
            "data", Map.of("id", "pay_123", "status", "approved")
        );

        ResponseEntity<Void> response = controller.receberWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(webhookService);
    }

    @Test
    void deveIgnorarWebhookQuandoDataVierNuloOuSemId() {
        Map<String, Object> payload = Map.of(
            "type", "subscription_preapproval"
        );

        ResponseEntity<Void> response = controller.receberWebhook(payload);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verifyNoInteractions(webhookService);
    }
}
