package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO da rota `GET /cobrancas/id/{id}` — pública pela mesma garantia de posse por UUID
 * que já vale para `/cobrancas/{id}/pagar` e `/cobrancas/{id}/status` (ver javadoc de
 * {@code CobrancaController}). Usada pela página de checkout (`/eventos/{eventoId}/
 * pagamento/{cobrancaId}`) para montar o cabeçalho com contexto do evento — por isso
 * carrega {@code eventoId}/{@code inicioEmEvento} além do que {@link CobrancaPublicaDTO}
 * já tinha.
 */
public record CobrancaCheckoutDTO(
    UUID id,
    UUID eventoId,
    String tituloEvento,
    LocalDateTime inicioEmEvento,
    String nomePagador,
    BigDecimal valor,
    String status,
    Instant expiraEm
) {}
