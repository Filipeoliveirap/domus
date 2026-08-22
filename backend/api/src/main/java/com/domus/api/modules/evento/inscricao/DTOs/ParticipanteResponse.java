package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.pessoa.Pessoa;
import java.util.List;
import java.util.UUID;

/**
 * Visível a QUALQUER MEMBRO autenticado — por isso omite, em relação a {@link InscritoResponse},
 * telefone de convidado, quem inscreveu quem e data da inscrição (dados administrativos, não de "quem vai").
 */
public record ParticipanteResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        UUID fotoId,
        String convidadoPorNome,
        List<String> convidados,
        EventoResponse.IgrejaResumo igrejaDaPessoa
) {
    private static final String NOME_PESSOA_REMOVIDA = "Pessoa removida do sistema";

    /** @param pessoaResolvida/@param convidadoPorResolvida resolvidas em lote via bypass —
     *  ver Javadoc de {@link InscritoResponse#from}. */
    public static ParticipanteResponse from(InscricaoEvento i, Pessoa pessoaResolvida, Pessoa convidadoPorResolvida) {
        String nome = pessoaResolvida != null ? pessoaResolvida.getNome()
                : i.getNomeConvidado() != null ? i.getNomeConvidado()
                : NOME_PESSOA_REMOVIDA;

        return new ParticipanteResponse(
                i.getId(),
                pessoaResolvida == null ? null : pessoaResolvida.getId(),
                nome,
                pessoaResolvida != null && pessoaResolvida.getFoto() != null ? pessoaResolvida.getFoto().getId() : null,
                convidadoPorResolvida == null ? null : convidadoPorResolvida.getNome(),
                i.getAcompanhantes().stream().map(a -> a.getNome()).toList(),
                EventoResponse.IgrejaResumo.de(pessoaResolvida != null ? pessoaResolvida.getIgreja() : i.getIgreja())
        );
    }
}
