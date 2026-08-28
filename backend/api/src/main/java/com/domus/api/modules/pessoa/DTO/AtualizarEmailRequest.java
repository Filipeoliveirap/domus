package com.domus.api.modules.pessoa.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Usado só pra dar o PRIMEIRO e-mail a uma Pessoa que ainda não tem — ver
 *  {@code PessoaService.definirEmailInicial}. Não serve pra trocar um e-mail já
 *  existente (mesma regra de imutabilidade do e-mail em {@code PessoaController.atualizarMe}). */
public record AtualizarEmailRequest(
        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres.")
        String email
) {}
