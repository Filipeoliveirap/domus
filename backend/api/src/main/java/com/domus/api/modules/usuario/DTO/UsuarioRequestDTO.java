package com.domus.api.modules.usuario.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 255, message = "nome do usuário deve ter no máximo 255 caracteres")
        String nomeUsuario,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String emailUsuario,

        @NotBlank(message = "Senha é obrigatória")
        @Size(min = 8, message = "Mínimo 8 caracteres")
        String senhaUsuario,

        @NotBlank(message = "Perfil é obrigatório")
        String role
) {
}
