package com.domus.api.modules.evento.inscricao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /** Projeção (só o id da pessoa, sem materializar InscricaoEvento/Evento): usada pra
     *  notificar quem está inscrito quando o evento muda ou é arquivado. Carregar a entidade
     *  InscricaoEvento gerenciada aqui (em vez da projeção) deixa `evento` referenciado por
     *  ela ainda no contexto de persistência quando `arquivarEvento` soft-deleta o evento
     *  logo depois — o próximo autoflush confunde o Hibernate ("unsaved transient instance"). */
    @Query("SELECT i.pessoa.id FROM InscricaoEvento i WHERE i.evento.id = :eventoId AND i.status = :status")
    List<UUID> findPessoaIdsByEventoIdAndStatus(@Param("eventoId") UUID eventoId, @Param("status") StatusInscricao status);

    /** Nativa: JPQL aqui herda o @SQLRestriction de Evento e some com evento arquivado. */
    @Query(value = "SELECT * FROM inscricao_evento WHERE evento_id = :eventoId", nativeQuery = true)
    List<InscricaoEvento> findByEventoId(@Param("eventoId") UUID eventoId);

    /** Mesmo motivo de {@link #findByEventoId}. */
    @Query(value = "SELECT COUNT(*) FROM inscricao_evento WHERE evento_id = :eventoId", nativeQuery = true)
    long countByEventoId(@Param("eventoId") UUID eventoId);

    Optional<InscricaoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("SELECT i FROM InscricaoEvento i WHERE i.id = :id AND i.igreja.id IN :idsFamilia")
    Optional<InscricaoEvento> buscarVisivelParaFamilia(@Param("id") UUID id,
                                                        @Param("idsFamilia") java.util.Set<UUID> idsFamilia);

    // Sem FETCH em i.pessoa de propósito: pessoa arquivada (mas não excluída) precisa
    // continuar mostrando os dados reais, não só "não sumir" — um JOIN (mesmo LEFT) herda o
    // @SQLRestriction de Pessoa e não tem como distinguir "arquivada" de "de vez". O chamador
    // resolve pessoa em lote via PessoaRepository#findByIdInIncluindoArquivadas (bypassa a
    // restrição por ser SQL nativo). i.getPessoa().getId() continua seguro (não inicializa o
    // proxy); nunca chamar outro getter em i.getPessoa() aqui.
    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        WHERE i.evento.id = :eventoId AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
        ORDER BY i.createdAt ASC
    """)
    List<InscricaoEvento> listarPorEvento(@Param("eventoId") UUID eventoId);

    // CAST(:busca AS string) é necessário: sem ele, o PostgreSQL infere bytea quando o
    // parâmetro é null e lower(bytea) não existe.
    // LEFT JOIN: pessoa excluída definitivamente não pode sumir da paginação, só não bate
    // busca por nome (não tem nome pra buscar).
    @Query(value = """
        SELECT i.id FROM InscricaoEvento i
        LEFT JOIN i.pessoa p
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND (CAST(:busca AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
        ORDER BY i.createdAt ASC
        """,
        countQuery = """
        SELECT COUNT(i) FROM InscricaoEvento i
        LEFT JOIN i.pessoa p
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND (CAST(:busca AS string) IS NULL OR LOWER(p.nome) LIKE LOWER(CONCAT('%', CAST(:busca AS string), '%')))
        """)
    Page<UUID> listarIdsPaginadoPorEvento(@Param("eventoId") UUID eventoId,
                                           @Param("busca") String busca,
                                           Pageable pageable);

    // Sem FETCH em i.pessoa — mesmo motivo de listarPorEvento.
    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
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

    /** Exclusão definitiva de pessoa: mantém a inscrição (e a contagem no relatório do evento),
     *  mostra "Pessoa removida do sistema". */
    @Modifying
    @Query(value = "UPDATE inscricao_evento SET pessoa_id = NULL WHERE pessoa_id = :pessoaId", nativeQuery = true)
    void desvincularPessoa(@Param("pessoaId") UUID pessoaId);

    /** Já é NULL = auto-inscrição no fluxo normal; aqui só cobre o caso do usuário ter sido excluído. */
    @Modifying
    @Query(value = "UPDATE inscricao_evento SET inscrito_por_usuario_id = NULL WHERE inscrito_por_usuario_id = :usuarioId", nativeQuery = true)
    void desvincularInscritoPor(@Param("usuarioId") UUID usuarioId);

    /** Purga da igreja: acompanhante_inscricao cascadeia sozinho via ON DELETE CASCADE. */
    @Modifying
    @Query(value = "DELETE FROM inscricao_evento WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}
