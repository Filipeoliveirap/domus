package com.domus.api.modules.anexo.dto;

import com.domus.api.modules.anexo.Anexo;

import java.util.UUID;

/** Resposta após upload de anexo. */
public record AnexoUploadResponse(
    UUID id,
    String tipo,
    Long bytes,
    String nomeOriginal,
    String url
) {
    public static AnexoUploadResponse from(Anexo anexo) {
        return new AnexoUploadResponse(
            anexo.getId(),
            anexo.getTipo(),
            anexo.getBytes(),
            anexo.getNomeOriginal(),
            "/anexos/" + anexo.getId()
        );
    }
}
