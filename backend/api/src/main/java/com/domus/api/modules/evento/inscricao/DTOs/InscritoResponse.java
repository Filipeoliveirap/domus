package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Uma linha da lista de inscritos (ADMIN/LÍDER). */
public record InscritoResponse(
        UUID id,
        UUID pessoaId,
        String nome,
        String foto,
        /** NULL = a pessoa se inscreveu sozinha. */
        UUID inscritoPorUsuarioId,
        /**
         * Nome de quem inscreveu. NULL quando {@code inscritoPorUsuarioId} também é NULL
         * (auto-inscrição) OU quando a conta de quem inscreveu foi arquivada depois — nos
         * dois casos o front não deve inventar um nome, só decidir a mensagem certa.
         */
        String inscritoPorNome,
        String inscritoPorFoto,
        LocalDateTime inscritoEm,
        List<AcompanhanteResponse> acompanhantes
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
                i.getPessoa().getFoto(),
                i.getInscritoPorUsuarioId(),
                registrante == null ? null : registrante.nome(),
                registrante == null ? null : registrante.foto(),
                i.getCreatedAt(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList()
        );
    }
}
