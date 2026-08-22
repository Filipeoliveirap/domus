package com.domus.api.modules.evento.convite.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Devolvido por GET /convites/{token} — NUNCA inclui lista de inscritos, e-mail/telefone de
 *  quem convidou, nem qualquer outra Pessoa além do convidante. */
public record ConvitePublicoResponse(
        UUID eventoId,
        String titulo,
        String descricao,
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        String localNome,
        String localEndereco,
        UUID fotoId,
        String igrejaNome,
        UUID igrejaLogoFotoId,
        String convidadoPorNome,
        UUID convidadoPorFotoId,
        Integer vagasRestantes,
        BigDecimal preco,
        List<CampoPersonalizadoResponse> campos
) {}
