package com.domus.api.modules.pagamento.cobranca.DTOs;

/**
 * Corpo de {@code POST /cobrancas/{id}/pagar} (Task 14) — o payload que o Payment Brick
 * devolve em {@code onSubmit({ formData })} no front, repassado quase igual pro backend.
 * {@code token} e {@code installments} vêm nulos quando o meio escolhido é PIX (o Brick
 * não tokeniza cartão nesse caso); {@code paymentMethodId} vem sempre (ex.: {@code "pix"},
 * {@code "visa"}, {@code "master"}).
 */
public record PagarCobrancaRequest(
    String token,
    String paymentMethodId,
    Integer installments,
    String payerEmail
) {}
