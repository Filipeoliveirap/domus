package com.domus.api.modules.pagamento;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.core.MPRequestOptions;
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
 * na API REST. Aqui, no entanto, {@code PaymentClient} e {@code PaymentRefundClient} — as
 * classes que este wrapper usa — foram conferidas abrindo o jar real (
 * {@code ~/.m2/repository/com/mercadopago/sdk-java/2.1.16/sdk-java-2.1.16.jar}) com
 * {@code jar xf} + {@code javap}: todas existem com exatamente a assinatura usada abaixo.
 *
 * <p><b>Por que {@code MPRequestOptions} por chamada, e não {@code MercadoPagoConfig.
 * setAccessToken(...)} global (revisão pós-Task 8):</b> {@code MercadoPagoConfig} guarda o
 * access token num campo {@code static} sem sincronização — confirmado via {@code javap
 * com/mercadopago/MercadoPagoConfig.class} (getter/setter estáticos, sem
 * {@code synchronized}). Com múltiplas igrejas processando pagamentos ao mesmo tempo (o
 * app roda em threads concorrentes do Tomcat), duas chamadas simultâneas a
 * {@code setAccessToken} + {@code create}/{@code refund} podiam entrelaçar e vazar o token
 * de uma igreja para a chamada de outra — o tipo exato de vazamento cross-tenant que este
 * projeto trata como grave (dinheiro real de igreja indo pra credencial errada). A
 * inspeção do jar (abaixo) confirmou que {@code PaymentClient.create} e
 * {@code PaymentRefundClient.refund} têm overloads que recebem um
 * {@code com.mercadopago.core.MPRequestOptions} — que carrega o próprio
 * {@code accessToken} como campo de instância, sem tocar em nenhum estado estático:
 *
 * <pre>
 * javap com/mercadopago/client/payment/PaymentClient.class
 * // create(PaymentCreateRequest, MPRequestOptions)
 * javap com/mercadopago/client/payment/PaymentRefundClient.class
 * // refund(Long, MPRequestOptions)
 * javap com/mercadopago/core/MPRequestOptions.class
 * javap 'com/mercadopago/core/MPRequestOptions$MPRequestOptionsBuilder.class'
 * // builder().accessToken(String)...build() — getAccessToken()/setAccessToken() de instância
 * </pre>
 *
 * Por isso este wrapper nunca chama {@code MercadoPagoConfig.setAccessToken}: o token é
 * passado só por {@code MPRequestOptions}, isolado por chamada — resolve o vazamento na
 * raiz, sem precisar serializar chamadas concorrentes.
 */
@Component
public class MercadoPagoApi {

    public String criarPagamento(String accessToken, String externalReference, BigDecimal valor) {
        try {
            PaymentClient client = new PaymentClient();
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                .transactionAmount(valor)
                .description("Inscrição em evento — Domus")
                .externalReference(externalReference)
                .build();
            MPRequestOptions options = MPRequestOptions.builder()
                .accessToken(accessToken)
                .build();
            var pagamento = client.create(request, options);
            return String.valueOf(pagamento.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar pagamento no Mercado Pago", e);
        }
    }

    /**
     * Busca o pagamento pelo id no Mercado Pago e devolve o {@code external_reference}
     * que foi setado por {@link #criarPagamento} (é o id da nossa {@code CobrancaEvento}).
     * Usado pelo webhook, que só manda {@code data.id} — não o external_reference direto.
     *
     * <p>{@code PaymentClient.get(Long, MPRequestOptions)} foi confirmado no jar real
     * ({@code sdk-java-2.1.16.jar}, via {@code jar xf} + {@code javap}
     * {@code com/mercadopago/client/payment/PaymentClient.class}) — existe com essa
     * assinatura exata, e {@code Payment.getExternalReference()} também existe (javap em
     * {@code com/mercadopago/resources/payment/Payment.class}).
     */
    public String buscarExternalReference(String accessToken, String mpPaymentId) {
        try {
            PaymentClient client = new PaymentClient();
            MPRequestOptions options = MPRequestOptions.builder()
                .accessToken(accessToken)
                .build();
            var pagamento = client.get(Long.parseLong(mpPaymentId), options);
            return pagamento.getExternalReference();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao consultar pagamento no Mercado Pago", e);
        }
    }

    public void estornar(String accessToken, String mpPaymentId) {
        try {
            PaymentRefundClient client = new PaymentRefundClient();
            MPRequestOptions options = MPRequestOptions.builder()
                .accessToken(accessToken)
                .build();
            client.refund(Long.parseLong(mpPaymentId), options);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao estornar pagamento no Mercado Pago", e);
        }
    }
}
