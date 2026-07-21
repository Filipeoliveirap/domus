package com.domus.api.modules.pessoa.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ConcederAcessoRequestDTO(
        @NotNull(message = "O membro é obrigatório")
        UUID pessoaId,

        @NotBlank(message = "O perfil é obrigatório")
        String role,

        // Opcional: só é usado quando o membro ainda não tem e-mail cadastrado.
        // Nesse caso o e-mail é gravado no membro antes de disparar o convite.
        @Email(message = "E-mail inválido")
        String email
) {}
