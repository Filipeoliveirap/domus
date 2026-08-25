package com.domus.api.modules.pagamento.cobranca.DTOs;

/**
 * Resposta de {@code POST /cobrancas/{id}/pagar}. {@code mpPaymentId} identifica o
 * pagamento criado no Mercado Pago — a confirmação definitiva (marcar a
 * {@code CobrancaEvento} como PAGO) NÃO acontece aqui, e sim de forma assíncrona pelo
 * webhook (Task 10). Este endpoint só inicia o pagamento.
 */
public record PagarCobrancaResponse(String mpPaymentId) {}
