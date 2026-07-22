package com.domus.api.modules.evento.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<InscricaoEvento, UUID> {

    /**
     * Vagas contam PESSOAS, não inscrições: cada inscrição confirmada vale 1 (o membro)
     * mais o número de acompanhantes que ele trouxe. Canceladas não contam.
     */
    @Query("""
        SELECT COALESCE(COUNT(i), 0) + COALESCE(
                   (SELECT COUNT(a) FROM AcompanhanteInscricao a
                     WHERE a.inscricao.evento.id = :eventoId
                       AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA), 0)
        FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long contarPessoasConfirmadas(@Param("eventoId") UUID eventoId);

    Optional<InscricaoEvento> findByEventoIdAndPessoaId(UUID eventoId, UUID pessoaId);

    Optional<InscricaoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.pessoa
        WHERE i.evento.id = :eventoId AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
        ORDER BY i.createdAt ASC
    """)
    List<InscricaoEvento> listarPorEvento(@Param("eventoId") UUID eventoId);

    List<InscricaoEvento> findByPessoaIdAndStatus(UUID pessoaId, StatusInscricao status);

    /**
     * Inscrições CONFIRMADAS de uma pessoa em eventos {@code exclusivoMembros} — usada quando
     * o vínculo da pessoa deixa de ser MEMBRO, para cancelar automaticamente onde ela não é
     * mais elegível (ver {@link InscricaoService#cancelarInscricoesEmEventosExclusivos}).
     */
    List<InscricaoEvento> findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
            UUID pessoaId, StatusInscricao status);

    /**
     * Ids, dentre {@code pessoaIds}, que já têm inscrição CONFIRMADA no evento — usada para
     * validar a inscrição em lote ANTES de inserir qualquer linha (tudo-ou-nada), permitindo
     * uma mensagem que nomeia quantos já estavam inscritos.
     */
    @Query("""
        SELECT i.pessoa.id FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId AND i.pessoa.id IN :pessoaIds
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    List<UUID> listarPessoaIdsJaInscritos(@Param("eventoId") UUID eventoId,
                                          @Param("pessoaIds") List<UUID> pessoaIds);
}
