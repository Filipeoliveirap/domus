package com.domus.api.modules.foto.DTOs;

import com.domus.api.modules.foto.Foto;

import java.util.UUID;

/** Resposta do envio: só o suficiente para o front montar a URL de leitura. */
public record FotoResponse(UUID id) {

    public static FotoResponse from(Foto foto) {
        return new FotoResponse(foto.getId());
    }
}
