package com.domus.api.modules.financeiro.categoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaFinanceiraRepository extends JpaRepository<CategoriaFinanceira, UUID> {

    Optional<CategoriaFinanceira> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM categoria_financeira WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    /** @SQLRestriction esconde arquivadas de qualquer find derivado/JPQL — precisa de SQL nativo. */
    @Query(value = """
        SELECT * FROM categoria_financeira
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY nome ASC
        """, nativeQuery = true)
    List<CategoriaFinanceira> findArquivadasPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivadas também. */
    @Query(value = "SELECT * FROM categoria_financeira WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<CategoriaFinanceira> findByIdAndIgrejaIdIncluindoArquivadas(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaIdIncluindoArquivadas}, mas em lote. */
    @Query(value = "SELECT * FROM categoria_financeira WHERE id IN (:ids) AND igreja_id = :igrejaId", nativeQuery = true)
    List<CategoriaFinanceira> findByIdInAndIgrejaIdIncluindoArquivadas(@Param("ids") List<UUID> ids, @Param("igrejaId") UUID igrejaId);

    /** Retorna 0 se o id não pertence a essa igreja — nunca confiar em "id" sozinho. */
    @Modifying
    @Query(value = "UPDATE categoria_financeira SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Query("""
        SELECT c FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
          AND (:q IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY c.nome ASC
    """)
    Page<CategoriaFinanceira> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                              @Param("q") String q,
                                              Pageable pageable);
    @Query("""
        SELECT c FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
        ORDER BY c.nome ASC
    """)
    List<CategoriaFinanceira> buscarTodasPorIgreja(@Param("igrejaId") UUID igrejaId);

    @Query("""
        SELECT COUNT(c) > 0 FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
          AND LOWER(TRIM(c.nome)) = LOWER(TRIM(:nome))
          AND c.id <> :idIgnorar
    """)
    boolean existeComNome(@Param("igrejaId") UUID igrejaId,
                          @Param("nome") String nome,
                          @Param("idIgnorar") UUID idIgnorar);

    /** Usado por {@code MovimentacaoAutomaticaService} pra achar a categoria de eventos já
     *  existente antes de criar uma nova, tolerando variações de nome digitadas pelo admin
     *  (singular/plural, maiúsculas). {@code nomesNormalizados} já vem em minúsculo/trim. */
    @Query("""
        SELECT c FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
          AND LOWER(TRIM(c.nome)) IN :nomesNormalizados
        """)
    List<CategoriaFinanceira> buscarPorIgrejaENomeNormalizado(@Param("igrejaId") UUID igrejaId,
                                                                @Param("nomesNormalizados") java.util.Set<String> nomesNormalizados);

    /** Purga da igreja: chamar só depois de purgar movimentacao_financeira (que referencia categoria). */
    @Modifying
    @Query(value = "DELETE FROM categoria_financeira WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}