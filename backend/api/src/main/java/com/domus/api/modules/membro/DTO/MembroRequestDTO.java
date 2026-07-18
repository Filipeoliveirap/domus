package com.domus.api.modules.membro.DTO;

import com.domus.api.modules.membro.EstadoCivil;
import com.domus.api.modules.membro.StatusMembro;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record MembroRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @Email(message = "E-mail inválido")
        String email,

        @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter 10 ou 11 dígitos numéricos.")
        String telefone,

        @Past(message = "A data de nascimento deve ser uma data no passado.")
        LocalDate dataNascimento,

        @jakarta.validation.Valid
        EnderecoDTO endereco,

        @NotNull(message = "O status do membro é obrigatório")
        StatusMembro status,

        EstadoCivil estadoCivil,
        @Size(max = 255)
        String ministerio,

        String observacoes
) {}