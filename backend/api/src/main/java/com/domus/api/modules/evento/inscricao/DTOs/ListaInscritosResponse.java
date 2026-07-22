package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.List;

public record ListaInscritosResponse(
        long totalPessoas,
        Integer vagas,
        Integer vagasRestantes,
        List<InscritoResponse> inscritos
) {}
