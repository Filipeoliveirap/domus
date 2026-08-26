package com.domus.api.modules.pagamento;

import com.domus.api.modules.pagamento.webhook.MercadoPagoWebhookService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Poll ativo de confirmação de pagamento, disparado logo após {@code POST
 * /cobrancas/{id}/pagar} criar o pagamento no Mercado Pago — corre em paralelo ao webhook
 * (Task 10), não no lugar dele. Motivação (2026-08-26): o webhook do Mercado Pago pode
 * demorar bem mais que a espera razoável de alguém olhando a tela de "confirmando
 * pagamento" (testado ao vivo: um Pix aprovado no banco do pagador levou minutos até o
 * webhook chegar). Consultar a API do MP diretamente, a cada poucos segundos, dá uma
 * segunda via de confirmação — quem responder primeiro (webhook ou poll) confirma; a outra
 * chamada vira no-op graças à guarda de idempotência em
 * {@link MercadoPagoWebhookService#confirmarPagamento}.
 *
 * <p>Falha aqui (rede instável, Mercado Pago fora do ar) nunca perde o pagamento — é só o
 * "atalho": o webhook continua sendo o caminho garantido, este poll é só otimização de
 * latência percebida pelo usuário.
 */
@Service
public class PagamentoPollingService {

    private static final Logger log = LoggerFactory.getLogger(PagamentoPollingService.class);

    /** ~1 minuto de tentativas (20 x 3s) — cobre a maioria dos casos reais sem segurar
     *  thread ocupada indefinidamente; depois disso, só o webhook resolve. */
    private static final int MAX_TENTATIVAS = 20;
    private static final long INTERVALO_MS = 3000;

    /** Status do Mercado Pago que ainda podem virar "approved" — continuar tentando. */
    private static final java.util.Set<String> STATUS_AINDA_EM_ABERTO =
        java.util.Set.of("pending", "in_process");

    private final MercadoPagoClient mercadoPagoClient;
    private final MercadoPagoWebhookService webhookService;

    public PagamentoPollingService(MercadoPagoClient mercadoPagoClient, MercadoPagoWebhookService webhookService) {
        this.mercadoPagoClient = mercadoPagoClient;
        this.webhookService = webhookService;
    }

    @Async("pagamentoPollingExecutor")
    public void pollarConfirmacao(UUID igrejaId, String cobrancaId, String mpPaymentId) {
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                Thread.sleep(INTERVALO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            try {
                var info = mercadoPagoClient.buscarInformacoesPagamento(igrejaId, mpPaymentId);
                if (!STATUS_AINDA_EM_ABERTO.contains(info.status())) {
                    webhookService.confirmarPagamento(cobrancaId, mpPaymentId, info.status());
                    return;
                }
            } catch (RuntimeException e) {
                log.warn("Poll de confirmação de pagamento falhou nesta tentativa, seguindo. "
                    + "cobrancaId={} mpPaymentId={} tentativa={}", cobrancaId, mpPaymentId, tentativa, e);
            }
        }

        log.info("Poll de confirmação de pagamento esgotou as tentativas sem resolver — "
            + "webhook continua sendo o caminho garantido. cobrancaId={} mpPaymentId={}", cobrancaId, mpPaymentId);
    }
}
