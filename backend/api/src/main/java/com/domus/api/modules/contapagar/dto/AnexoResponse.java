package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.anexo.Anexo;

import java.util.UUID;

/** Anexo embutido na resposta de conta. */
public record AnexoResponse(
    UUID id,
    String tipo,
    Long bytes,
    String nomeOriginal
) {
    public static AnexoResponse from(Anexo anexo) {
        if (anexo == null) return null;
        return new AnexoResponse(anexo.getId(), anexo.getTipo(), anexo.getBytes(), anexo.getNomeOriginal());
    }
}
