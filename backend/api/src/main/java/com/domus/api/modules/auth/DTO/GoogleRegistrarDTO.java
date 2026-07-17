package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de igreja via Google. Nome/e-mail do dono NÃO entram aqui: vêm sempre do
 * ID token validado (fonte da verdade da identidade). Só carregamos os dados da igreja,
 * que o Google não fornece.
 */
public record GoogleRegistrarDTO(
        @NotBlank(message = "idToken é obrigatório")
        String idToken,

        @NotBlank(message = "Nome da igreja é obrigatório")
        @Size(max = 255, message = "Nome da igreja deve ter no máximo 255 caracteres")
        String nomeIgreja,

        @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
        String cnpj,

        @NotBlank(message = "Telefone para contato é obrigatório")
        @Pattern(regexp = "^\\d{10,11}$", message = "Telefone inválido. Informe DDD + número (10 ou 11 dígitos)")
        String telefoneContato
) {}
