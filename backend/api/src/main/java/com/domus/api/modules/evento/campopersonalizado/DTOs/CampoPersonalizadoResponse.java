package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEvento;
import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;

import java.util.List;
import java.util.UUID;

public record CampoPersonalizadoResponse(
        UUID id,
        String label,
        String placeholder,
        TipoCampoPersonalizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean visivelAoPublico,
        int ordem
) {
    public static CampoPersonalizadoResponse from(CampoPersonalizadoEvento c) {
        return new CampoPersonalizadoResponse(
                c.getId(), c.getLabel(), c.getPlaceholder(), c.getTipo(),
                c.getOpcoesComoLista(), c.isObrigatorio(), c.isVisivelAoPublico(), c.getOrdem()
        );
    }
}
