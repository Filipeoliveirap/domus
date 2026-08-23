package com.domus.api.modules.pagamento;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentRefundClient;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Chamada HTTP real ao Mercado Pago, via SDK oficial ({@code com.mercadopago:sdk-java},
 * versão 2.1.16). Isolado num componente próprio (em vez de dentro de
 * {@link MercadoPagoClient}) só pra poder mockar a borda de I/O nos testes de
 * {@code MercadoPagoClientTest} sem precisar de rede.
 *
 * <p><b>Nota sobre a Task 4 (OAuth) vs. esta task:</b> na Task 4, o fluxo de OAuth
 * ({@code com.mercadopago.client.oauth.OauthClient}) se mostrou quebrado na v2.1.16 (não
 * manda {@code client_id} no corpo) e teve que ser reescrito com {@code RestClient} direto
 * na API REST. Aqui, no entanto, {@code MercadoPagoConfig}, {@code PaymentClient} e
 * {@code PaymentRefundClient} — as classes que este wrapper usa — foram conferidas
 * abrindo o jar real (
 * {@code ~/.m2/repository/com/mercadopago/sdk-java/2.1.16/sdk-java-2.1.16.jar}) com
 * {@code jar xf} + {@code javap}: TODAS existem com exatamente a assinatura usada abaixo
 * ({@code MercadoPagoConfig.setAccessToken(String)}, {@code PaymentClient.create(
 * PaymentCreateRequest)}, {@code PaymentCreateRequest.builder().transactionAmount(...)
 * .description(...).externalReference(...).build()}, {@code PaymentRefundClient.refund(
 * Long)}). Ou seja, ao contrário da Task 4, o SDK oficial funciona de verdade aqui — não
 * há motivo para reescrever via REST direto nesta classe.
 */
@Component
public class MercadoPagoApi {

    public String criarPagamento(String accessToken, String externalReference, BigDecimal valor) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            PaymentClient client = new PaymentClient();
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                .transactionAmount(valor)
                .description("Inscrição em evento — Domus")
                .externalReference(externalReference)
                .build();
            var pagamento = client.create(request);
            return String.valueOf(pagamento.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar pagamento no Mercado Pago", e);
        }
    }

    public void estornar(String accessToken, String mpPaymentId) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            PaymentRefundClient client = new PaymentRefundClient();
            client.refund(Long.parseLong(mpPaymentId));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao estornar pagamento no Mercado Pago", e);
        }
    }
}
