package com.domus.api.modules.visitante.DTOs;

import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VisitanteRequest(
        @NotBlank @Size(max = 255) String nome,
        @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter 10 ou 11 dígitos numéricos.")
        String telefone,
        @Valid EnderecoDTO endereco,
        Sexo sexo,
        EstadoCivil estadoCivil,
        LocalDate dataNascimento,
        Boolean temFilhos,
        @Min(value = 0, message = "A quantidade de filhos não pode ser negativa.")
        @Max(value = 30, message = "A quantidade de filhos informada é implausível.")
        Integer quantidadeFilhos,
        @Size(max = 5000, message = "As observações devem ter no máximo 5000 caracteres.")
        String observacoes
) {
    public VisitanteRequest {
        if (temFilhos == null) temFilhos = false;
    }
}
