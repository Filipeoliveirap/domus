package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.shared.DTO.PagedResponse;

/**
 * {@code totalPessoas}/{@code vagas}/{@code vagasRestantes} contam TODAS as confirmadas,
 * nunca afetados por {@code busca} — só {@code inscritos} pagina e filtra.
 */
public record ListaInscritosResponse(
        long totalPessoas,
        Integer vagas,
        Integer vagasRestantes,
        PagedResponse<InscritoResponse> inscritos
) {}
