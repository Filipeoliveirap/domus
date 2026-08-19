package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.shared.DTO.PagedResponse;

/** {@code totalPessoas}/{@code vagas}/{@code vagasRestantes} contam TODAS as confirmadas — só {@code inscritos} filtra por busca. */
public record ListaInscritosResponse(
        long totalPessoas,
        Integer vagas,
        Integer vagasRestantes,
        PagedResponse<InscritoResponse> inscritos
) {}
