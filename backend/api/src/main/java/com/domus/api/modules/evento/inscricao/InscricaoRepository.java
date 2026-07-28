package com.domus.api.modules.evento.inscricao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<InscricaoEvento, UUID> {

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

    // Visível pela família: InscricaoEvento.igreja é sempre a igreja ORGANIZADORA do
    // evento, não a da pessoa — por isso a auto-inscrição/cancelamento entre igrejas da
    // mesma família não pode exigir igualdade estrita de igreja_id.
    @Query("SELECT i FROM InscricaoEvento i WHERE i.id = :id AND i.igreja.id IN :idsFamilia")
    Optional<InscricaoEvento> buscarVisivelParaFamilia(@Param("id") UUID id,
                                                        @Param("idsFamilia") java.util.Set<UUID> idsFamilia);

    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.pessoa
        WHERE i.evento.id = :eventoId AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
        ORDER BY i.createdAt ASC
    """)
    List<InscricaoEvento> listarPorEvento(@Param("eventoId") UUID eventoId);

    // CAST(:busca AS string) é necessário: sem ele, o PostgreSQL infere bytea quando o
    // parâmetro é null e lower(bytea) não existe.
    @Query(value = """
        SELECT i.id FROM InscricaoEvento i
        JOIN i.pessoa p
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND (CAST(:busca AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
        ORDER BY i.createdAt ASC
        """,
        countQuery = """
        SELECT COUNT(i) FROM InscricaoEvento i
        JOIN i.pessoa p
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND (CAST(:busca AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
        """)
    Page<UUID> listarIdsPaginadoPorEvento(@Param("eventoId") UUID eventoId,
                                           @Param("busca") String busca,
                                           Pageable pageable);

    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.pessoa
        WHERE i.id IN :ids
        ORDER BY i.createdAt ASC
        """)
    List<InscricaoEvento> listarComDetalhesPorIds(@Param("ids") List<UUID> ids);

    List<InscricaoEvento> findByPessoaIdAndStatus(UUID pessoaId, StatusInscricao status);

    List<InscricaoEvento> findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
            UUID pessoaId, StatusInscricao status);

    @Query("""
        SELECT i.pessoa.id FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId AND i.pessoa.id IN :pessoaIds
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    List<UUID> listarPessoaIdsJaInscritos(@Param("eventoId") UUID eventoId,
                                          @Param("pessoaIds") List<UUID> pessoaIds);

    @Query("""
        SELECT COUNT(i) FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long countPessoasInscritas(@Param("eventoId") UUID eventoId);

    @Query("""
        SELECT COUNT(a) FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long countConvidadosInscritos(@Param("eventoId") UUID eventoId);

    @Query("""
        SELECT COUNT(i) FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND i.compareceu = true
    """)
    long countPessoasCompareceram(@Param("eventoId") UUID eventoId);

    @Query("""
        SELECT COUNT(a) FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND a.compareceu = true
    """)
    long countConvidadosCompareceram(@Param("eventoId") UUID eventoId);

    @Query("""
        SELECT COUNT(DISTINCT i.pessoa.id) FROM InscricaoEvento i
        WHERE i.evento.id IN :eventoIds
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND i.compareceu = true
    """)
    long contarParticipantesUnicos(@Param("eventoIds") List<UUID> eventoIds);
}
