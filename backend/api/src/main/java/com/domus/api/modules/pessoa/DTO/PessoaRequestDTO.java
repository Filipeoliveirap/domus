package com.domus.api.modules.pessoa.DTO;

import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Vinculo;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record PessoaRequestDTO(
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

        @NotNull(message = "O vínculo da pessoa com a igreja é obrigatório")
        Vinculo vinculo,

        EstadoCivil estadoCivil,
        @Size(max = 255)
        String ministerio,

        String observacoes,

        @Past(message = "A data de batismo deve ser uma data no passado.")
        LocalDate dataBatismo,

        /** Id da foto já enviada via {@code POST /fotos}; {@code null} = sem foto. */
        UUID fotoId
) {}