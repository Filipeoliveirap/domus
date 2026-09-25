package com.domus.api.modules.postagem.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CriarComentarioRequest(
        @NotBlank(message = "Conteúdo do comentário é obrigatório")
        String conteudo,
        UUID paiComentarioId
) {}
