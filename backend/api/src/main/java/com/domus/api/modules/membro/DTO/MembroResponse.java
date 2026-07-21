package com.domus.api.modules.membro.DTO;

import com.domus.api.modules.membro.EstadoCivil;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.StatusMembro;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MembroResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        LocalDate dataNascimento,
        EnderecoDTO endereco,
        StatusMembro status,
        EstadoCivil estadoCivil,
        String ministerio,
        String foto,
        String observacoes,
        LocalDateTime createdAt,
        boolean batizado,
        LocalDate dataBatismo
) {
    public static MembroResponse from(Membro m) {
        return new MembroResponse(
                m.getId(), m.getNome(), m.getEmail(), m.getTelefone(),
                m.getDataNascimento(), enderecoDe(m.getEndereco()), m.getStatus(),
                m.getEstadoCivil(), m.getMinisterio(), m.getFoto(),
                m.getObservacoes(), m.getCreatedAt(),
                m.isBatizado(), m.getDataBatismo()
        );
    }

    private static EnderecoDTO enderecoDe(com.domus.api.modules.membro.Endereco e) {
        if (e == null) return null;
        return new EnderecoDTO(e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
}