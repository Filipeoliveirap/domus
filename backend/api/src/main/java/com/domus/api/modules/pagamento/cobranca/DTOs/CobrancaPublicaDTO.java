package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO da rota pública {@code GET /cobrancas/{token}} — sem autenticação, por isso
 * carrega estritamente o necessário para montar a tela de checkout: nem telefone, nem
 * e-mail, nem qualquer outro campo de Pessoa/AcompanhanteInscricao além do nome.
 */
public record CobrancaPublicaDTO(
    String tituloEvento,
    String nomePagador,
    BigDecimal valor,
    String status,
    Instant expiraEm
) {}
