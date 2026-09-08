package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.domus.api.modules.pagamento.MercadoPagoApi.InformacoesPagamento;
import com.domus.api.modules.pagamento.webhook.MercadoPagoWebhookService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Foco: {@code reconferirAgora} — a reconferência síncrona que o {@code GET /cobrancas/{id}/status}
 * dispara pra fechar o buraco entre o fim do poll assíncrono (~1min) e o webhook chegar.
 */
class PagamentoPollingServiceTest {

    MercadoPagoClient mercadoPagoClient;
    MercadoPagoWebhookService webhookService;
    PagamentoPollingService service;

    UUID igrejaId = UUID.randomUUID();
    String cobrancaId = UUID.randomUUID().toString();
    String mpPaymentId = "mp-payment-123";

    @BeforeEach
    void setup() {
        mercadoPagoClient = mock(MercadoPagoClient.class);
        webhookService = mock(MercadoPagoWebhookService.class);
        service = new PagamentoPollingService(mercadoPagoClient, webhookService);
    }

    @Test
    void confirmaQuandoMercadoPagoJaAprovou() {
        when(mercadoPagoClient.buscarInformacoesPagamento(igrejaId, mpPaymentId))
            .thenReturn(new InformacoesPagamento(cobrancaId, "approved"));

        service.reconferirAgora(igrejaId, cobrancaId, mpPaymentId);

        verify(webhookService).confirmarPagamento(cobrancaId, mpPaymentId, "approved");
    }

    @Test
    void naoConfirmaEnquantoPagamentoAindaEstaEmAberto() {
        when(mercadoPagoClient.buscarInformacoesPagamento(igrejaId, mpPaymentId))
            .thenReturn(new InformacoesPagamento(cobrancaId, "pending"));

        service.reconferirAgora(igrejaId, cobrancaId, mpPaymentId);

        verify(webhookService, never()).confirmarPagamento(any(), any(), any());
    }

    @Test
    void repassaStatusTerminalNaoAprovadoParaOWebhookService() {
        // rejected/cancelled não "confirmam", mas o confirmarPagamento é o ponto único que
        // trata cada status do MP (inclusive liberar a cobrança pra nova tentativa) — a
        // reconferência não deve decidir isso sozinha, só repassar.
        when(mercadoPagoClient.buscarInformacoesPagamento(igrejaId, mpPaymentId))
            .thenReturn(new InformacoesPagamento(cobrancaId, "rejected"));

        service.reconferirAgora(igrejaId, cobrancaId, mpPaymentId);

        verify(webhookService).confirmarPagamento(cobrancaId, mpPaymentId, "rejected");
    }

    @Test
    void engoleFalhaDoMercadoPagoSemPropagar() {
        when(mercadoPagoClient.buscarInformacoesPagamento(eq(igrejaId), eq(mpPaymentId)))
            .thenThrow(new RuntimeException("Mercado Pago fora do ar"));

        assertThatCode(() -> service.reconferirAgora(igrejaId, cobrancaId, mpPaymentId))
            .doesNotThrowAnyException();

        verify(webhookService, never()).confirmarPagamento(any(), any(), any());
    }
}
