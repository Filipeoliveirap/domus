package com.domus.api.modules.igreja.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CadastroCongregacaoRequest(
    @NotBlank(message = "Código de convite é obrigatório")
    String codigoConvite,

    @NotBlank(message = "Nome da congregação é obrigatório")
    @Size(max = 255, message = "Nome da congregação deve ter no máximo 255 caracteres")
    String nome,

    @NotBlank(message = "E-mail do administrador é obrigatório")
    @Email(message = "E-mail inválido")
    String emailAdmin,

    @NotBlank(message = "Nome do administrador é obrigatório")
    String nomeAdmin,

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres")
    String senha
) {}
