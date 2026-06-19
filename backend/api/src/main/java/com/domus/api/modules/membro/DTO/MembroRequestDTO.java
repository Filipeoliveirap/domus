package com.domus.api.modules.membro.DTO;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record MembroRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 255, message = "nome do usuário deve ter no máximo 255 caracteres")
        String nome,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Telefone é obrigatório")
        @Pattern(regexp = "^\\d{11}$", message = "O telefone deve conter exatamente 11 dígitos numéricos.")
        @Size(min = 11)
        String telefone,

        @NotNull(message = "A data de nascimento é obrigatória.")
        @Past(message = "A data de nascimento deve ser uma data no passado.")
        LocalDate dataNacimento,

        String endereco,

        @NotBlank(message = "O status do membro é obrigatório")
        String status,

        String observacoes

) {
}
