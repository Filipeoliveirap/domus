package com.domus.api.modules.pagamento.webhook;

import com.domus.api.modules.pagamento.MercadoPagoClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recebe as notificações de pagamento do Mercado Pago. O corpo do webhook manda só
 * {@code type: "payment"} e {@code data.id} — o id do pagamento no Mercado Pago, não o
 * {@code external_reference} (id da nossa {@link com.domus.api.modules.pagamento.cobranca.CobrancaEvento}).
 * Pra descobrir o external_reference é preciso consultar {@code GET /v1/payments/{id}} no
 * Mercado Pago, o que exige um access token — e o webhook, ao contrário da criação do
 * pagamento, não sabe de antemão de qual igreja é esse pagamento.
 *
 * <p><b>Como resolvemos isso (ver {@link #buscarExternalReference}):</b> o formato padrão
 * de webhook do Mercado Pago (tanto o clássico "IPN" quanto o v2 atual) inclui, junto do
 * {@code type}/{@code topic} e {@code data.id}, um campo {@code user_id} — o id da conta MP
 * que gerou a notificação (é passado tanto como query string quanto no corpo JSON,
 * dependendo da versão/canal configurado no painel do MP). Usamos esse {@code user_id} pra
 * achar a {@code ContaPagamentoIgreja} da igreja dona da conta (por {@code mp_user_id}),
 * pegar o access token dela, e só então consultar o pagamento.
 *
 * <p><b>Nível de confiança:</b> este ambiente de implementação não tem acesso à internet
 * pra confirmar contra a documentação oficial atual do Mercado Pago — a suposição de que
 * {@code user_id} vem no payload/query do webhook é baseada no formato documentado e
 * estável há anos do Mercado Pago (é, inclusive, o mesmo campo usado pelo próprio SDK
 * oficial em exemplos de verificação de webhook), não em algo confirmado nesta sessão via
 * chamada real. Está implementado como parâmetro opcional (não quebra o "sempre 200" do
 * webhook se um dia vier ausente) e logado quando falta, pra facilitar diagnosticar em
 * produção caso a suposição esteja errada.
 */
@RestController
@RequestMapping("/pagamentos/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoAssinaturaValidator validator;
    private final MercadoPagoWebhookService service;
    private final MercadoPagoClient mercadoPagoClient;

    public MercadoPagoWebhookController(MercadoPagoAssinaturaValidator validator,
                                         MercadoPagoWebhookService service,
                                         MercadoPagoClient mercadoPagoClient) {
        this.validator = validator;
        this.service = service;
        this.mercadoPagoClient = mercadoPagoClient;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
        @RequestHeader("x-signature") String assinatura,
        @RequestHeader("x-request-id") String requestId,
        @RequestParam("data.id") String dataId,
        @RequestParam String type,
        @RequestParam(value = "user_id", required = false) String userId
    ) {
        // O Mercado Pago SEMPRE espera 200 — mesmo em rejeição, só loga e ignora,
        // pra não entrar em reenvio infinito do provedor.
        if (!validator.valida(assinatura, dataId, requestId)) {
            log.warn("Webhook do Mercado Pago com assinatura inválida, ignorado. requestId={}", requestId);
            return ResponseEntity.ok().build();
        }

        if ("payment".equals(type)) {
            try {
                String externalReference = buscarExternalReference(dataId, userId);
                if (externalReference != null) {
                    service.confirmarPagamento(externalReference, dataId);
                } else {
                    log.warn("Webhook do Mercado Pago sem external_reference resolvido, ignorado. "
                        + "dataId={} userId={}", dataId, userId);
                }
            } catch (Exception e) {
                // Nunca deixa uma falha de consulta ao MP (rede, conta não encontrada, etc.)
                // vazar como 5xx — o MP reenviaria infinitamente. Só loga pra investigar.
                log.error("Falha ao processar webhook de pagamento do Mercado Pago. dataId={} userId={}",
                    dataId, userId, e);
            }
        }

        return ResponseEntity.ok().build();
    }

    private String buscarExternalReference(String mpPaymentId, String userId) {
        if (userId == null || userId.isBlank()) {
            log.warn("Webhook do Mercado Pago sem user_id — não é possível resolver a conta "
                + "dona do pagamento. dataId={}", mpPaymentId);
            return null;
        }
        return mercadoPagoClient.buscarExternalReferencePorMpUserId(userId, mpPaymentId);
    }
}
