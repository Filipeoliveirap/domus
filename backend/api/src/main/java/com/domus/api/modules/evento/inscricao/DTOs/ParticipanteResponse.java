package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.util.List;
import java.util.UUID;

/**
 * Uma linha da lista de participantes visível a QUALQUER MEMBRO autenticado (revisão de
 * 2026-07-21: ver quem vai motiva a ir).
 *
 * <p>Deliberadamente OMITE, em relação a {@link InscritoResponse}: o telefone dos convidados
 * (era seguro enquanto só a administração lia; abrir para a igreja inteira exporia o contato
 * de um visitante para centenas de pessoas), quem inscreveu quem ({@code inscritoPorUsuarioId})
 * e a data da inscrição — são dados administrativos, não de "quem vai".
 */
public record ParticipanteResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        UUID fotoId,
        List<String> convidados,
        EventoResponse.IgrejaResumo igrejaDaPessoa
) {
    public static ParticipanteResponse from(InscricaoEvento i) {
        return new ParticipanteResponse(
                i.getId(),
                i.getPessoa().getId(),
                i.getPessoa().getNome(),
                i.getPessoa().getFoto() != null ? i.getPessoa().getFoto().getId() : null,
                i.getAcompanhantes().stream().map(a -> a.getNome()).toList(),
                EventoResponse.IgrejaResumo.de(i.getPessoa().getIgreja())
        );
    }
}
