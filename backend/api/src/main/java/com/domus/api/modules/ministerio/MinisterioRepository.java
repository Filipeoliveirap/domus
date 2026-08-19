package com.domus.api.modules.ministerio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinisterioRepository extends JpaRepository<Ministerio, UUID> {

    List<Ministerio> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<Ministerio> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM ministerio WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    /** @SQLRestriction esconde arquivados de qualquer find derivado/JPQL — precisa de SQL nativo. */
    @Query(value = """
        SELECT * FROM ministerio
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY nome ASC
        """, nativeQuery = true)
    List<Ministerio> findArquivadasPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivados também. */
    @Query(value = "SELECT * FROM ministerio WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<Ministerio> findByIdAndIgrejaIdIncluindoArquivadas(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Retorna 0 se o id não pertence a essa igreja — nunca confiar em "id" sozinho. */
    @Modifying
    @Query(value = "UPDATE ministerio SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = """
        UPDATE ministerio
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);

    long countByIgrejaId(UUID igrejaId);

    /** Purga definitiva da igreja: em lote, depois de ministerio_membro. */
    @Modifying
    @Query(value = "DELETE FROM ministerio WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}
