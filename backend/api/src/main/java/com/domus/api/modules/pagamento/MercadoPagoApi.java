package com.domus.api.modules.pagamento;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoApi.class);

    private final RestClient restClient = RestClient.create();

    // MPApiException não expõe o corpo do erro no getMessage() ("Api error. Check response
    // for details") — só em getApiResponse().getContent(). Sem logar isso, todo erro do
    // Mercado Pago (cartão recusado, token expirado, campo inválido) vira uma
    // IllegalStateException opaca e o motivo real fica invisível nos logs.
    private static void logarDetalheSeApiException(String contexto, Exception e) {
        if (e instanceof MPApiException apiException) {
            log.error("{}: {} (status {})", contexto,
                apiException.getApiResponse() != null ? apiException.getApiResponse().getContent() : "sem corpo",
                apiException.getStatusCode());
        }
    }

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
            logarDetalheSeApiException("Falha ao criar pagamento no Mercado Pago", e);
            throw new IllegalStateException("Falha ao criar pagamento no Mercado Pago", e);
        }
    }

    /**
     * Cria o pagamento a partir dos dados TOKENIZADOS pelo Payment Brick no navegador do
     * pagador (Task 14). Diferente de {@link #criarPagamento}, aqui o cartão já foi
     * tokenizado no cliente — o Domus nunca vê número de cartão, só o {@code token}
     * gerado pelo SDK JS do Mercado Pago. Campos ({@code token}, {@code paymentMethodId},
     * {@code installments}, {@code payer.email}) confirmados no jar real
     * ({@code sdk-java-2.1.16.jar}, via {@code jar xf} + {@code javap
     * com/mercadopago/client/payment/PaymentCreateRequest.class} e
     * {@code PaymentPayerRequest.class}) — todos existem como campos do builder, exatamente
     * o que o Payment Brick devolve em {@code onSubmit({ formData })}
     * ({@code formData.token}, {@code formData.payment_method_id},
     * {@code formData.installments}, {@code formData.payer.email}).
     *
     * <p>PIX não usa {@code token} nem {@code installments} (só
     * {@code paymentMethodId = "pix"}) — os dois campos aceitam {@code null} no builder do
     * SDK sem quebrar (campos de instância, não primitivos), então o mesmo método serve
     * pros dois meios de pagamento que o Brick oferece.
     */
    /**
     * {@code qrCode}/{@code qrCodeBase64} só vêm preenchidos quando {@code paymentMethodId}
     * é {@code "pix"} — cartão resolve na hora (aprovado/recusado) e não tem QR nenhum. Vêm
     * de {@code Payment.getPointOfInteraction().getTransactionData()}, confirmado no jar
     * real (mesmo processo de inspeção via {@code jar xf} + {@code javap} usado no resto
     * desta classe) — {@code PaymentPointOfInteraction.getTransactionData()} e
     * {@code PaymentTransactionData.getQrCode()}/{@code getQrCodeBase64()} existem com essa
     * assinatura exata.
     */
    public record ResultadoPagamento(String mpPaymentId, String status, String statusDetail, String qrCode,
                                      String qrCodeBase64, Instant expiraEmPix) {}

    /** Prazo real de validade do QR/copia-e-cola Pix — independente do prazo da
     *  {@code CobrancaEvento} em si (que pode ser de até 48h pra link compartilhado, ver
     *  {@code CobrancaEventoService.PRAZO_LINK_COMPARTILHADO}). Achado ao vivo (2026-08-27):
     *  sem mandar {@code date_of_expiration} explícito, o Mercado Pago aplicava o próprio
     *  padrão dele — e o front, sem saber a validade real, mostrava o prazo da cobrança
     *  inteira (até 48h) como se fosse o tempo pra pagar o Pix, um contador sem sentido. */
    private static final int MINUTOS_EXPIRACAO_PIX = 30;

    public ResultadoPagamento criarPagamentoTokenizado(String accessToken, String externalReference, BigDecimal valor,
                                            String token, String paymentMethodId, Integer installments,
                                            String payerEmail, String issuerId) {
        try {
            PaymentClient client = new PaymentClient();
            OffsetDateTime expiracaoPix = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(MINUTOS_EXPIRACAO_PIX);
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                .transactionAmount(valor)
                .description("Inscrição em evento — Domus")
                .externalReference(externalReference)
                .token(token)
                .paymentMethodId(paymentMethodId)
                .installments(installments)
                .issuerId(issuerId)
                .payer(PaymentPayerRequest.builder().email(payerEmail).build())
                // "pix" é o único paymentMethodId que o Brick manda sem token — os demais
                // (cartão) ignoram este campo, mas só definir quando é Pix evita qualquer
                // efeito colateral não documentado em outro meio de pagamento.
                .dateOfExpiration("pix".equals(paymentMethodId) ? expiracaoPix : null)
                .build();
            MPRequestOptions options = MPRequestOptions.builder()
                .accessToken(accessToken)
                .build();
            var pagamento = client.create(request, options);
            var transactionData = pagamento.getPointOfInteraction() != null
                ? pagamento.getPointOfInteraction().getTransactionData()
                : null;
            return new ResultadoPagamento(
                String.valueOf(pagamento.getId()),
                pagamento.getStatus(),
                pagamento.getStatusDetail(),
                transactionData != null ? transactionData.getQrCode() : null,
                transactionData != null ? transactionData.getQrCodeBase64() : null,
                "pix".equals(paymentMethodId) ? expiracaoPix.toInstant() : null
            );
        } catch (Exception e) {
            logarDetalheSeApiException("Falha ao criar pagamento tokenizado no Mercado Pago", e);
            throw new IllegalStateException("Falha ao criar pagamento tokenizado no Mercado Pago", e);
        }
    }

    /**
     * Par {@code (externalReference, status)} de um pagamento no Mercado Pago. O
     * {@code status} é o que decide, no webhook, se a cobrança pode ser marcada como PAGO
     * (Critical 2, revisão final de branch) — valores documentados do SDK: {@code approved},
     * {@code pending}, {@code in_process}, {@code rejected}, {@code cancelled},
     * {@code refunded}, {@code charged_back}.
     */
    public record InformacoesPagamento(String externalReference, String status) {}

    /**
     * Busca o pagamento pelo id no Mercado Pago e devolve o {@code external_reference}
     * (setado por {@link #criarPagamento}, é o id da nossa {@code CobrancaEvento}) junto
     * do {@code status} real do pagamento. Usado pelo webhook, que só manda {@code data.id}
     * — não o external_reference nem o status direto no payload.
     *
     * <p><b>Por que {@code RestClient} direto, e não {@code PaymentClient.get(Long,
     * MPRequestOptions)} do SDK oficial (achado testando o fluxo end-to-end, 2026-08-26):</b>
     * embora {@code PaymentClient.get(Long, MPRequestOptions)} exista com essa assinatura no
     * jar real ({@code sdk-java-2.1.16.jar}), a chamada devolve 401 "Must provide your
     * access_token to proceed" mesmo com um {@code accessToken} válido (confirmado: o MESMO
     * token funciona em {@link #criarPagamentoTokenizado}, via {@code PaymentClient.create}) —
     * o SDK não anexa o header de autorização no {@code GET}, mesmo bug de classe do
     * {@code OauthClient} já documentado em {@code MercadoPagoOAuthClient}. Por isso este
     * método fala direto com {@code GET /v1/payments/{id}} da API do Mercado Pago.
     *
     * <p><b>Critical 2 (revisão final de branch):</b> antes desta correção, o webhook
     * confirmava a cobrança como PAGO incondicionalmente, sem checar o status — PIX pendente,
     * cartão recusado ou pagamento cancelado confirmavam a cobrança do mesmo jeito que um
     * pagamento aprovado. Este método passou a expor o status pra o chamador decidir.
     */
    public InformacoesPagamento buscarInformacoesPagamento(String accessToken, String mpPaymentId) {
        try {
            var pagamento = restClient.get()
                .uri("https://api.mercadopago.com/v1/payments/{id}", mpPaymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(RespostaPagamentoMercadoPago.class);
            if (pagamento == null) {
                throw new IllegalStateException("Resposta vazia do Mercado Pago ao consultar pagamento");
            }
            if ("rejected".equals(pagamento.status())) {
                // `status` sozinho não diz o motivo — o Mercado Pago só expõe isso em
                // `status_detail` (ex.: cc_rejected_insufficient_amount,
                // cc_rejected_bad_filled_security_code, cc_rejected_high_risk). Sem logar
                // aqui, toda recusa vira "recusado" genérico, sem pista nenhuma pra
                // diagnosticar se é problema do cartão, do ambiente de teste, ou nosso.
                log.info("Pagamento recusado pelo Mercado Pago. mpPaymentId={} statusDetail={}",
                    mpPaymentId, pagamento.statusDetail());
            }
            return new InformacoesPagamento(pagamento.externalReference(), pagamento.status());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao consultar pagamento no Mercado Pago", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RespostaPagamentoMercadoPago(
        @JsonProperty("external_reference") String externalReference,
        String status,
        @JsonProperty("status_detail") String statusDetail,
        @JsonProperty("point_of_interaction") PontoDeInteracao pointOfInteraction,
        @JsonProperty("date_of_expiration") String dateOfExpiration
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PontoDeInteracao(@JsonProperty("transaction_data") DadosTransacaoPix transactionData) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DadosTransacaoPix(
        @JsonProperty("qr_code") String qrCode,
        @JsonProperty("qr_code_base64") String qrCodeBase64
    ) {}

    /** {@code qrCode}/{@code qrCodeBase64} nulos quando o pagamento em andamento é cartão,
     *  não Pix — cartão nunca tem {@code point_of_interaction} na resposta do Mercado Pago.
     *  {@code expiraEm} é a validade real deste Pix específico (ver {@link #MINUTOS_EXPIRACAO_PIX}),
     *  não o prazo da cobrança inteira. */
    public record QrCodePix(String qrCode, String qrCodeBase64, Instant expiraEm) {}

    /**
     * Recupera o QR/copia-e-cola de um pagamento Pix JÁ CRIADO — pro caso de a pessoa dar
     * reload na tela de checkout enquanto o Pix está pendente (achado ao vivo, 2026-08-27):
     * antes disso não existia jeito de mostrar o QR de novo, porque ele só é devolvido pelo
     * Mercado Pago no momento de {@link #criarPagamentoTokenizado}, nunca de novo depois.
     * Mesma chamada {@code GET /v1/payments/{id}} de {@link #buscarInformacoesPagamento},
     * só que também lendo {@code point_of_interaction.transaction_data}.
     */
    public QrCodePix buscarQrCodePix(String accessToken, String mpPaymentId) {
        try {
            var pagamento = restClient.get()
                .uri("https://api.mercadopago.com/v1/payments/{id}", mpPaymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(RespostaPagamentoMercadoPago.class);
            if (pagamento == null) {
                throw new IllegalStateException("Resposta vazia do Mercado Pago ao consultar pagamento");
            }
            var dados = pagamento.pointOfInteraction() != null ? pagamento.pointOfInteraction().transactionData() : null;
            return new QrCodePix(
                dados != null ? dados.qrCode() : null,
                dados != null ? dados.qrCodeBase64() : null,
                pagamento.dateOfExpiration() != null
                    ? java.time.OffsetDateTime.parse(pagamento.dateOfExpiration()).toInstant()
                    : null
            );
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao consultar QR Pix no Mercado Pago", e);
        }
    }

    /**
     * Sempre com {@code amount} explícito — nunca um "estorno cheio" sem valor (2026-08-27):
     * cancelar uma inscrição que já tinha recebido um estorno parcial antes (reajuste de
     * preço pra baixo) e mandar estornar sem valor tentaria devolver o total original de
     * novo, e o Mercado Pago recusa por falta de saldo. Todo chamador calcula o quanto
     * ainda falta devolver ({@link com.domus.api.modules.pagamento.cobranca.CobrancaEvento
     * #valorRestanteParaEstornar()}) e manda exatamente isso.
     *
     * <p>Por {@code RestClient} direto, não {@code PaymentRefundClient.refund(Long,
     * MPRequestOptions)} do SDK — mesmo bug de classe já documentado em
     * {@link #buscarInformacoesPagamento}: confirmado ao vivo (2026-08-26) testando um
     * cancelamento de inscrição paga, o SDK devolve 401 {@code "authorization value not
     * present"} mesmo com {@code accessToken} válido (o mesmo token funciona em
     * {@link #criarPagamentoTokenizado}) — {@code MPRequestOptions} não propaga o header
     * {@code Authorization} nesta versão (2.1.16) em várias chamadas do SDK.</p>
     */
    public void estornarParcial(String accessToken, String mpPaymentId, BigDecimal valor) {
        try {
            restClient.post()
                .uri("https://api.mercadopago.com/v1/payments/{id}/refunds", mpPaymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("amount", valor))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao estornar parcialmente pagamento no Mercado Pago", e);
        }
    }

    /**
     * Cancela uma tentativa de pagamento ainda pendente (Pix escaneado mas nunca pago, ou
     * cartão em análise) — achado ao vivo (2026-08-27): sem isto, uma tentativa presa (QR
     * antigo que já não serve, ou o meio errado escolhido) travava a cobrança em
     * {@code COBRANCA_JA_EM_PROCESSAMENTO} até expirar sozinha, sem forma de tentar de novo
     * na hora. {@code PUT /v1/payments/{id}} com {@code status=cancelled} é o único jeito
     * documentado do Mercado Pago pra isso — {@code PaymentClient} do SDK não expõe um
     * método de cancelamento com essa assinatura (só {@code cancel(String)}, que tem o
     * mesmo bug de header de autorização ausente já documentado em
     * {@link #buscarInformacoesPagamento} e {@link #buscarQrCodePix} — por isso, mesma
     * solução: RestClient direto).
     *
     * <p>Best-effort: um pagamento que já saiu de pending/in_process (aprovado, recusado,
     * ou já cancelado) recusa o PUT — não é erro nosso, só ignora e segue, porque o objetivo
     * real (liberar a cobrança pra nova tentativa) já está garantido pelo chamador.</p>
     */
    public void cancelarPagamento(String accessToken, String mpPaymentId) {
        try {
            restClient.put()
                .uri("https://api.mercadopago.com/v1/payments/{id}", mpPaymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(java.util.Map.of("status", "cancelled"))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.info("Não foi possível cancelar a tentativa de pagamento no Mercado Pago "
                + "(pode já ter saído de pending/in_process) — seguindo mesmo assim. mpPaymentId={}",
                mpPaymentId, e);
        }
    }
}
