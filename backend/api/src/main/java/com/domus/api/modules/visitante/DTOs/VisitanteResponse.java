package com.domus.api.modules.visitante.DTOs;

import com.domus.api.modules.pessoa.DTO.EnderecoDTO;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.visitante.Visitante;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record VisitanteResponse(
        UUID id,
        String nome,
        String telefone,
        EnderecoDTO endereco,
        Sexo sexo,
        EstadoCivil estadoCivil,
        LocalDate dataNascimento,
        boolean temFilhos,
        Integer quantidadeFilhos,
        String observacoes,
        boolean contatoRealizado,
        boolean visitaRealizada,
        boolean acompanhamentoFeito,
        boolean entrouEmCelula,
        boolean convertido,
        LocalDateTime createdAt
) {
    public static VisitanteResponse from(Visitante v) {
        return new VisitanteResponse(
                v.getId(), v.getNome(), v.getTelefone(),
                enderecoDe(v.getEndereco()),
                v.getSexo(), v.getEstadoCivil(), v.getDataNascimento(),
                Boolean.TRUE.equals(v.getTemFilhos()), v.getQuantidadeFilhos(),
                v.getObservacoes(),
                Boolean.TRUE.equals(v.getContatoRealizado()),
                Boolean.TRUE.equals(v.getVisitaRealizada()),
                Boolean.TRUE.equals(v.getAcompanhamentoFeito()),
                v.getEntrouEmCelulaEm() != null,
                v.getConvertidoPessoaId() != null,
                v.getCreatedAt()
        );
    }

    private static EnderecoDTO enderecoDe(com.domus.api.shared.dominio.Endereco e) {
        if (e == null) return null;
        return new EnderecoDTO(e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
}
