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
        LocalDate dataBatismo,
        String avisoTelefoneDuplicado
) {
    public static MembroResponse from(Membro m) {
        return from(m, null);
    }

    /**
     * B2: {@code avisoTelefoneDuplicado} carrega o NOME de outro membro da mesma igreja com o
     * mesmo telefone — é AVISO, não bloqueio (telefone é legitimamente compartilhado: casal,
     * família, idoso que usa o número de um filho). Só é preenchido logo após
     * cadastrar/atualizar (ver {@code MembroService}); a leitura normal ({@code from(Membro)})
     * não recalcula o aviso a cada GET.
     */
    public static MembroResponse from(Membro m, String avisoTelefoneDuplicado) {
        return new MembroResponse(
                m.getId(), m.getNome(), m.getEmail(), m.getTelefone(),
                m.getDataNascimento(), enderecoDe(m.getEndereco()), m.getStatus(),
                m.getEstadoCivil(), m.getMinisterio(), m.getFoto(),
                m.getObservacoes(), m.getCreatedAt(),
                m.isBatizado(), m.getDataBatismo(), avisoTelefoneDuplicado
        );
    }

    private static EnderecoDTO enderecoDe(com.domus.api.modules.membro.Endereco e) {
        if (e == null) return null;
        return new EnderecoDTO(e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
}