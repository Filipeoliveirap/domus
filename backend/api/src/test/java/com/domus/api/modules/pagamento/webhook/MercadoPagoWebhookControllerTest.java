package com.domus.api.modules.pagamento.webhook;

import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.MercadoPagoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookControllerTest {

    MercadoPagoAssinaturaValidator validator;
    MercadoPagoWebhookService service;
    MercadoPagoClient mercadoPagoClient;
    MercadoPagoWebhookController controller;

    @BeforeEach
    void setup() {
        validator = mock(MercadoPagoAssinaturaValidator.class);
        service = mock(MercadoPagoWebhookService.class);
        mercadoPagoClient = mock(MercadoPagoClient.class);
        controller = new MercadoPagoWebhookController(validator, service, mercadoPagoClient);
    }

    @Test
    void ignoraSilenciosamenteAssinaturaInvalida() {
        when(validator.valida(any(), any(), any())).thenReturn(false);

        var resposta = controller.webhook("ts=1,v1=forjado", "req-1", "999", "payment", "mp-user-1");

        assertOk(resposta);
        verifyNoInteractions(service, mercadoPagoClient);
    }

    @Test
    void resolveExternalReferencePeloMpUserIdEConfirma() {
        when(validator.valida(any(), any(), any())).thenReturn(true);
        when(mercadoPagoClient.buscarExternalReferencePorMpUserId("mp-user-1", "999"))
            .thenReturn("cobranca-abc");

        var resposta = controller.webhook("ts=1,v1=hash", "req-1", "999", "payment", "mp-user-1");

        assertOk(resposta);
        verify(service).confirmarPagamento("cobranca-abc", "999");
    }

    @Test
    void ignoraQuandoUserIdNaoVemNoWebhook() {
        when(validator.valida(any(), any(), any())).thenReturn(true);

        var resposta = controller.webhook("ts=1,v1=hash", "req-1", "999", "payment", null);

        assertOk(resposta);
        verifyNoInteractions(mercadoPagoClient);
        verify(service, never()).confirmarPagamento(any(), any());
    }

    @Test
    void ignoraTiposDiferentesDePayment() {
        when(validator.valida(any(), any(), any())).thenReturn(true);

        var resposta = controller.webhook("ts=1,v1=hash", "req-1", "999", "merchant_order", "mp-user-1");

        assertOk(resposta);
        verifyNoInteractions(service, mercadoPagoClient);
    }

    @Test
    void devolve200MesmoQuandoConsultaAoMercadoPagoFalha() {
        when(validator.valida(any(), any(), any())).thenReturn(true);
        when(mercadoPagoClient.buscarExternalReferencePorMpUserId(any(), any()))
            .thenThrow(new RuntimeException("falha de rede simulada"));

        var resposta = controller.webhook("ts=1,v1=hash", "req-1", "999", "payment", "mp-user-1");

        assertOk(resposta);
    }

    @Test
    void ignoraSilenciosamenteQuandoDataIdVemNulo() {
        var resposta = controller.webhook("ts=1,v1=hash", "req-1", null, "payment", "mp-user-1");

        assertOk(resposta);
        verifyNoInteractions(validator, service, mercadoPagoClient);
    }

    @Test
    void ignoraSilenciosamenteQuandoTypeVemEmBranco() {
        var resposta = controller.webhook("ts=1,v1=hash", "req-1", "999", "  ", "mp-user-1");

        assertOk(resposta);
        verifyNoInteractions(validator, service, mercadoPagoClient);
    }

    @Test
    void ignoraSilenciosamenteQuandoAssinaturaVemNula() {
        var resposta = controller.webhook(null, "req-1", "999", "payment", "mp-user-1");

        assertOk(resposta);
        verifyNoInteractions(validator, service, mercadoPagoClient);
    }

    @Test
    void ignoraSilenciosamenteQuandoRequestIdVemNulo() {
        var resposta = controller.webhook("ts=1,v1=hash", null, "999", "payment", "mp-user-1");

        assertOk(resposta);
        verifyNoInteractions(validator, service, mercadoPagoClient);
    }

    private void assertOk(org.springframework.http.ResponseEntity<Void> resposta) {
        org.assertj.core.api.Assertions.assertThat(resposta.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
