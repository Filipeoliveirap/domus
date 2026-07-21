package com.domus.api.modules.evento.inscricao.DTOs;

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
        UUID membroId,
        String nome,
        String foto,
        List<String> convidados
) {
    public static ParticipanteResponse from(InscricaoEvento i) {
        return new ParticipanteResponse(
                i.getId(),
                i.getMembro().getId(),
                i.getMembro().getNome(),
                i.getMembro().getFoto(),
                i.getAcompanhantes().stream().map(a -> a.getNome()).toList()
        );
    }
}
