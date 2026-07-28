package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Uma linha da lista de inscritos (ADMIN/LÍDER). */
public record InscritoResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        UUID fotoId,
        /** NULL = a pessoa se inscreveu sozinha. */
        UUID inscritoPorUsuarioId,
        /**
         * Nome de quem inscreveu. NULL quando {@code inscritoPorUsuarioId} também é NULL
         * (auto-inscrição) OU quando a conta de quem inscreveu foi arquivada depois — nos
         * dois casos o front não deve inventar um nome, só decidir a mensagem certa.
         */
        String inscritoPorNome,
        UUID inscritoPorFotoId,
        LocalDateTime inscritoEm,
        List<AcompanhanteResponse> acompanhantes,
        EventoResponse.IgrejaResumo igrejaDaPessoa
) {
    /**
     * @param registrante resumo (nome/foto) de quem inscreveu, já resolvido em lote pelo
     *                     chamador; NULL se {@code inscritoPorUsuarioId} for NULL (auto-inscrição)
     *                     ou se a conta/membro de quem inscreveu estiver arquivada.
     */
    public static InscritoResponse from(InscricaoEvento i, RegistranteResumo registrante) {
        return new InscritoResponse(
                i.getId(),
                i.getPessoa().getId(),
                i.getPessoa().getNome(),
                i.getPessoa().getFoto() != null ? i.getPessoa().getFoto().getId() : null,
                i.getInscritoPorUsuarioId(),
                registrante == null ? null : registrante.nome(),
                registrante == null ? null : registrante.fotoId(),
                i.getCreatedAt(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList(),
                EventoResponse.IgrejaResumo.de(i.getPessoa().getIgreja())
        );
    }
}
