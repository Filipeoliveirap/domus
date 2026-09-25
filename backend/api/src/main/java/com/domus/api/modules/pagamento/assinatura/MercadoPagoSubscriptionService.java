package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.modules.pagamento.assinatura.dto.AssinaturaResponse;
import com.domus.api.modules.pagamento.assinatura.dto.CriarAssinaturaRequest;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class MercadoPagoSubscriptionService {

    private final MercadoPagoClient mercadoPagoClient;

    public MercadoPagoSubscriptionService(MercadoPagoClient mercadoPagoClient) {
        this.mercadoPagoClient = mercadoPagoClient;
    }

    public AssinaturaResponse criarAssinaturaTrial(Igreja igreja, CriarAssinaturaRequest request) {
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

        Map<?, ?> response = mercadoPagoClient.criarAssinaturaPreapproval("DOMUS_MP_TOKEN", body);
        String id = (String) response.get("id");
        String status = (String) response.get("status");

        return new AssinaturaResponse(id, status);
    }
}
