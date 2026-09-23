package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.postagem.TipoPostagem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CriarPostagemRequest(
        @NotNull(message = "Tipo da postagem é obrigatório")
        TipoPostagem tipo,

        boolean oficial,

        @Size(max = 150, message = "Título pode ter no máximo 150 caracteres")
        String titulo,

        @NotBlank(message = "Conteúdo da postagem é obrigatório")
        String conteudo,

        UUID fotoId,

        @Size(max = 100, message = "Referência de versículo pode ter no máximo 100 caracteres")
        String versiculoRef,

        boolean fixado
) {}
