package com.domus.api.modules.visitante.DTOs;

import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VisitanteRequest(
        @NotBlank @Size(max = 255) String nome,
        String telefone,
        @Valid EnderecoDTO endereco,
        Sexo sexo,
        EstadoCivil estadoCivil,
        LocalDate dataNascimento,
        Boolean temFilhos,
        Integer quantidadeFilhos,
        String observacoes
) {
    public VisitanteRequest {
        if (temFilhos == null) temFilhos = false;
    }
}
