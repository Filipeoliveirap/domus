package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizado;
import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;

import java.util.UUID;

public record RespostaResponse(
        UUID campoId,
        String label,
        TipoCampoPersonalizado tipo,
        String valor
) {
    public static RespostaResponse from(RespostaCampoPersonalizado r) {
        return new RespostaResponse(r.getCampo().getId(), r.getCampo().getLabel(), r.getCampo().getTipo(), r.getValor());
    }
}
