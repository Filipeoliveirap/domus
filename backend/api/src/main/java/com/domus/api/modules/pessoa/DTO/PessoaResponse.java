package com.domus.api.modules.pessoa.DTO;

import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.pessoa.Vinculo;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record PessoaResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        LocalDate dataNascimento,
        EnderecoDTO endereco,
        Vinculo vinculo,
        EstadoCivil estadoCivil,
        Sexo sexo,
        String cargo,
        UUID fotoId,
        String observacoes,
        LocalDateTime createdAt,
        LocalDate dataBatismo,
        String avisoTelefoneDuplicado,
        boolean arquivada
) {
    public static PessoaResponse from(Pessoa m) {
        return from(m, null);
    }

    public static PessoaResponse from(Pessoa m, String avisoTelefoneDuplicado) {
        return from(m, avisoTelefoneDuplicado, true);
    }

    public static PessoaResponse from(Pessoa m, String avisoTelefoneDuplicado,
                                      boolean incluirDadosSensiveis) {
        return new PessoaResponse(
                m.getId(), m.getNome(), m.getEmail(), m.getTelefone(),
                m.getDataNascimento(),
                incluirDadosSensiveis ? enderecoDe(m.getEndereco()) : null,
                m.getVinculo(),
                m.getEstadoCivil(), m.getSexo(), m.getCargo(),
                m.getFoto() != null ? m.getFoto().getId() : null,
                incluirDadosSensiveis ? m.getObservacoes() : null,
                m.getCreatedAt(),
                m.getDataBatismo(), avisoTelefoneDuplicado,
                m.getDeletedAt() != null
        );
    }

    private static EnderecoDTO enderecoDe(com.domus.api.shared.dominio.Endereco e) {
        if (e == null) return null;
        return new EnderecoDTO(e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
}
