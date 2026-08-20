package com.domus.api.modules.pessoa.DTO;

import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.pessoa.Vinculo;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record PessoaRequestDTO(
        @NotBlank(message = "Nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
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

        /** Nulável de propósito: pessoas já cadastradas não têm valor e não dá pra
         * inventar um dado sobre gente real. Serve só pra restringir inscrição em
         * evento (ex.: "encontro de mulheres"), não pra descrever identidade. */
        Sexo sexo,

        @Size(max = 255)
        String cargo,

        @Size(max = 5000, message = "As observações devem ter no máximo 5000 caracteres")
        String observacoes,

        @Past(message = "A data de batismo deve ser uma data no passado.")
        LocalDate dataBatismo,

        /** Id da foto já enviada via {@code POST /fotos}; {@code null} = sem foto. */
        UUID fotoId
) {}