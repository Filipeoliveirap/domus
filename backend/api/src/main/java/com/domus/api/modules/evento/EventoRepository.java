package com.domus.api.modules.evento;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, UUID> {

    Optional<Evento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT e FROM Evento e
        WHERE e.id = :id
          AND e.igreja.id IN :idsFamilia
          AND (e.igreja.id = :minhaIgreja OR e.restritoPropriaIgreja = false)
    """)
    Optional<Evento> buscarVisivelParaFamilia(@Param("id") UUID id,
                                              @Param("minhaIgreja") UUID minhaIgreja,
                                              @Param("idsFamilia") java.util.Set<UUID> idsFamilia);

    // FK ON DELETE SET NULL nunca dispara — LocalEvento usa soft delete.
    // Este método resolve o vínculo antes de arquivar o local.
    List<Evento> findByLocalIdAndIgrejaId(UUID localId, UUID igrejaId);

    /** Local associado a algum evento ativo? Front usa pra pedir confirmação por escrito
     *  só quando arquivar o local de fato tira o lugar de um evento (fica sem local). */
    long countByLocalIdAndIgrejaId(UUID localId, UUID igrejaId);

    // Zera local_id E local_texto (inclusive em eventos arquivados) — o evento fica sem
    // local, não vira texto livre com o nome do local arquivado (decisão explícita: um
    // local arquivado não é endereço válido pra continuar aparecendo).
    // Nativo porque @SQLRestriction esconde os arquivados do JPQL.
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento
           SET local_texto = NULL, local_id = NULL
         WHERE local_id = :localId
        """, nativeQuery = true)
    int desvincularLocal(@Param("localId") UUID localId);

    // Mesmo padrão de desvincularLocal: responsavel_pessoa_id ON DELETE SET NULL nunca dispara (Pessoa usa soft delete).
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento
           SET responsavel_texto = :nome, responsavel_pessoa_id = NULL
         WHERE responsavel_pessoa_id = :pessoaId
        """, nativeQuery = true)
    int desvincularResponsavel(@Param("pessoaId") UUID pessoaId, @Param("nome") String nome);

    // Desvincula usuário de criado_por e atualizado_por (inclusive arquivados).
    // CASE WHEN independente: mesmo usuário pode aparecer nas duas colunas do mesmo evento.
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);

    // Pessimistic write lock no evento — serializa a contagem de vagas sob READ COMMITTED.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id AND e.igreja.id = :igrejaId")
    Optional<Evento> buscarComLock(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT e FROM Evento e
        WHERE e.id = :id
          AND e.igreja.id IN :idsFamilia
          AND (e.igreja.id = :minhaIgreja OR e.restritoPropriaIgreja = false)
    """)
    Optional<Evento> buscarComLockVisivelParaFamilia(@Param("id") UUID id,
                                                      @Param("minhaIgreja") UUID minhaIgreja,
                                                      @Param("idsFamilia") java.util.Set<UUID> idsFamilia);

    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id = :igrejaId AND e.inicioEm >= :agora
        ORDER BY e.inicioEm ASC
    """)
    List<Evento> proximos(@Param("igrejaId") UUID igrejaId, @Param("agora") LocalDateTime agora, Pageable pageable);

    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id IN :idsFamilia
          AND (e.igreja.id = :minhaIgreja OR e.restritoPropriaIgreja = false)
          AND e.inicioEm >= :agora
        ORDER BY e.inicioEm ASC
    """)
    List<Evento> proximosDaFamilia(@Param("minhaIgreja") UUID minhaIgreja,
                                    @Param("idsFamilia") java.util.Set<UUID> idsFamilia,
                                    @Param("agora") LocalDateTime agora,
                                    Pageable pageable);

    long countByIgrejaIdAndInicioEmBetween(UUID igrejaId, LocalDateTime de, LocalDateTime ate);

    @Query(value = """
        SELECT * FROM evento e
        WHERE e.deleted_at IS NULL
          AND e.igreja_id = ANY(CAST(:idsFamilia AS uuid[]))
          AND (e.igreja_id = :minhaIgreja OR e.restrito_propria_igreja = false)
          AND (CAST(:q AS text) IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
        ORDER BY
          CASE
            WHEN CAST(:agora AS timestamp) >= e.inicio_em
                 AND CAST(:agora AS timestamp) <= COALESCE(e.fim_em, date_trunc('day', e.inicio_em) + INTERVAL '23:59:59')
              THEN 0
            WHEN CAST(:agora AS timestamp) < e.inicio_em
                 AND CAST(e.inicio_em AS date) = CAST(CAST(:agora AS timestamp) AS date)
              THEN 1
            WHEN CAST(:agora AS timestamp) < e.inicio_em
              THEN 2
            ELSE 3
          END,
          e.inicio_em ASC
        """,
        countQuery = """
        SELECT COUNT(*) FROM evento e
        WHERE e.deleted_at IS NULL
          AND e.igreja_id = ANY(CAST(:idsFamilia AS uuid[]))
          AND (e.igreja_id = :minhaIgreja OR e.restrito_propria_igreja = false)
          AND (CAST(:q AS text) IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
        """,
        nativeQuery = true)
    Page<Evento> buscarPorFamilia(@Param("minhaIgreja") UUID minhaIgreja,
                                   @Param("idsFamilia") UUID[] idsFamilia,
                                   @Param("q") String q,
                                   @Param("tipo") String tipo,
                                   @Param("recorteEtario") String recorteEtario,
                                   @Param("agora") LocalDateTime agora,
                                   Pageable pageable);

    @Query("""
        SELECT e.tipo FROM Evento e
         WHERE e.igreja.id = :igrejaId AND e.tipo IS NOT NULL
         GROUP BY e.tipo
         ORDER BY COUNT(e) DESC, e.tipo ASC
        """)
    List<String> tiposUsadosPorFrequencia(@Param("igrejaId") UUID igrejaId);

    @Query(value = """
        SELECT * FROM evento e
        WHERE e.igreja_id = :igrejaId
          AND e.deleted_at IS NULL
          AND (CAST(:inicio AS timestamp) IS NULL OR e.inicio_em >= CAST(:inicio AS timestamp))
          AND (CAST(:fim AS timestamp) IS NULL OR e.inicio_em <= CAST(:fim AS timestamp))
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
        ORDER BY e.inicio_em DESC
        """, nativeQuery = true)
    List<Evento> buscarParaRelatorio(@Param("igrejaId") UUID igrejaId,
                                      @Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim,
                                      @Param("recorteEtario") String recorteEtario,
                                      @Param("tipo") String tipo);

    @Query(value = """
        SELECT * FROM evento e
        WHERE e.igreja_id = :igrejaId
          AND e.deleted_at IS NULL
          AND e.controla_presenca = true
          AND e.inicio_em >= :desde
          AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
          AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
        ORDER BY e.inicio_em ASC
        """, nativeQuery = true)
    List<Evento> buscarComControlaPresenca(@Param("igrejaId") UUID igrejaId,
                                            @Param("desde") LocalDateTime desde,
                                            @Param("recorteEtario") String recorteEtario,
                                            @Param("tipo") String tipo);

    Optional<Evento> findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
            UUID igrejaId, String tipo, LocalDateTime inicioEm);

    @Modifying
    @Query(value = "DELETE FROM evento WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    @Modifying
    @Query(value = "DELETE FROM evento WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);

    /** @SQLRestriction esconde arquivados de qualquer find derivado/JPQL — precisa de SQL nativo. */
    @Query(value = """
        SELECT * FROM evento
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY inicio_em DESC
        """, nativeQuery = true)
    List<Evento> findArquivadosPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivados também. */
    @Query(value = "SELECT * FROM evento WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<Evento> findByIdAndIgrejaIdIncluindoArquivados(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Retorna 0 se o id não pertence a essa igreja — nunca confiar em "id" sozinho. */
    @Modifying
    @Query(value = "UPDATE evento SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    long countByIgrejaId(UUID igrejaId);

    Optional<Evento> findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(UUID serieId);

    List<Evento> findBySerieIdAndInicioEmGreaterThanEqual(UUID serieId, LocalDateTime de);

    /** Sem @SQLRestriction de propósito — soft-deletado (feriado cancelado) também conta,
     *  senão o job de materialização ressuscitaria a data no próximo dia de rodagem. */
    @Query(value = "SELECT COUNT(*) > 0 FROM evento WHERE serie_id = :serieId AND inicio_em = :inicioEm",
           nativeQuery = true)
    boolean existsBySerieIdAndInicioEm(@Param("serieId") UUID serieId, @Param("inicioEm") LocalDateTime inicioEm);
}