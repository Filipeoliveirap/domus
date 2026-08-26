package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO da rota pública {@code GET /cobrancas/{token}} — sem autenticação, por isso
 * carrega estritamente o necessário para montar a tela de checkout: nem telefone, nem
 * e-mail, nem qualquer outro campo de Pessoa/AcompanhanteInscricao além do nome.
 *
 * <p>{@code id} (Task 14) é o identificador que a página pública usa para chamar
 * {@code POST /cobrancas/{id}/pagar} depois de resolver o {@code token} da URL — o
 * pagamento em si é sempre por id, nunca por token (ver nota em {@code CobrancaController}).
 */
public record CobrancaPublicaDTO(
    UUID id,
    String tituloEvento,
    String nomePagador,
    BigDecimal valor,
    String status,
    Instant expiraEm,
    /** {@code true} quando já existe uma tentativa de pagamento em voo (mpPaymentId
     *  gravado, esperando o webhook confirmar) — mesma lógica de {@link CobrancaCheckoutDTO}. */
    boolean pagamentoEmAndamento
) {}
