package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.modules.pagamento.assinatura.dto.AssinaturaResponse;
import com.domus.api.modules.pagamento.assinatura.dto.CriarAssinaturaRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class MercadoPagoSubscriptionService {

    private final MercadoPagoClient mercadoPagoClient;
    private final String saasAccessToken;

    public MercadoPagoSubscriptionService(
            MercadoPagoClient mercadoPagoClient,
            @Value("${app.pagamento.saas.access-token:}") String saasAccessToken) {
        this.mercadoPagoClient = mercadoPagoClient;
        this.saasAccessToken = (saasAccessToken != null && !saasAccessToken.isBlank()) ? saasAccessToken : "DOMUS_MP_TOKEN";
    }

    public AssinaturaResponse criarAssinaturaTrial(Igreja igreja, CriarAssinaturaRequest request) {
        // Em dev/teste com token simulado: autoriza trial sem depender da rede externa do Mercado Pago
        if (request.cardTokenId() != null && request.cardTokenId().startsWith("tok_dev_")) {
            return new AssinaturaResponse("preapp_dev_" + java.util.UUID.randomUUID().toString().substring(0, 8), "authorized");
        }

        String startDate = OffsetDateTime.now().plusDays(14).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> body = Map.of(
            "payer_email", request.payerEmail(),
            "back_url", "https://domus.app.br/configuracoes/assinatura",
            "reason", "Assinatura Domus - Plano " + igreja.getPlano().getNomeExibicao(),
            "auto_recurring", Map.of(
                "frequency", 1,
                "frequency_type", "months",
                "transaction_amount", igreja.getPlano().getValorMensal(),
                "currency_id", "BRL",
                "start_date", startDate
            ),
            "card_token_id", request.cardTokenId(),
            "status", "authorized"
        );

        Map<?, ?> response = mercadoPagoClient.criarAssinaturaPreapproval(saasAccessToken, body);
        String id = (String) response.get("id");
        String status = (String) response.get("status");

        return new AssinaturaResponse(id, status);
    }

    public void alterarPlano(String preapprovalId, PlanoAssinatura novoPlano) {
        if (preapprovalId == null || preapprovalId.isBlank() || preapprovalId.startsWith("preapp_dev_")) {
            return;
        }

        Map<String, Object> body = Map.of(
            "reason", "Assinatura Domus - Plano " + novoPlano.getNomeExibicao(),
            "auto_recurring", Map.of(
                "transaction_amount", novoPlano.getValorMensal()
            )
        );

        mercadoPagoClient.atualizarAssinaturaPreapproval(saasAccessToken, preapprovalId, body);
    }

    public void atualizarCartao(String preapprovalId, String cardTokenId) {
        if (preapprovalId == null || preapprovalId.isBlank() || preapprovalId.startsWith("preapp_dev_")) {
            return;
        }

        Map<String, Object> body = Map.of(
            "card_token_id", cardTokenId
        );

        mercadoPagoClient.atualizarAssinaturaPreapproval(saasAccessToken, preapprovalId, body);
    }

    public void cancelarAssinatura(String preapprovalId) {
        if (preapprovalId == null || preapprovalId.isBlank() || preapprovalId.startsWith("preapp_dev_")) {
            return;
        }

        Map<String, Object> body = Map.of(
            "status", "cancelled"
        );

        mercadoPagoClient.atualizarAssinaturaPreapproval(saasAccessToken, preapprovalId, body);
    }

    public void reativarAssinatura(String preapprovalId) {
        if (preapprovalId == null || preapprovalId.isBlank() || preapprovalId.startsWith("preapp_dev_")) {
            return;
        }

        Map<String, Object> body = Map.of(
            "status", "authorized"
        );

        mercadoPagoClient.atualizarAssinaturaPreapproval(saasAccessToken, preapprovalId, body);
    }

    public String consultarStatus(String preapprovalId) {
        if (preapprovalId == null || preapprovalId.isBlank()) {
            return null;
        }
        if (preapprovalId.startsWith("preapp_dev_")) {
            return "authorized";
        }

        Map<?, ?> resp = mercadoPagoClient.consultarAssinaturaPreapproval(saasAccessToken, preapprovalId);
        if (resp != null && resp.get("status") instanceof String status) {
            return status;
        }
        return null;
    }
}
