package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaMembroRepository extends JpaRepository<CelulaMembro, UUID> {

    /** Nativa de propósito: derived query vazaria o @SQLRestriction de Celula e esconderia membros de célula arquivada. */
    @Query(value = "SELECT * FROM celula_membro WHERE celula_id = :celulaId ORDER BY papel ASC", nativeQuery = true)
    List<CelulaMembro> findByCelulaIdOrderByPapelAsc(@Param("celulaId") UUID celulaId);

    Optional<CelulaMembro> findByPessoaId(UUID pessoaId);

    Optional<CelulaMembro> findByVisitanteId(UUID visitanteId);

    /** Nativa pelo mesmo motivo de findByCelulaIdOrderByPapelAsc. */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM celula_membro WHERE celula_id = :celulaId)", nativeQuery = true)
    boolean existsByCelulaId(@Param("celulaId") UUID celulaId);

    /** Nativa pelo mesmo motivo de findByCelulaIdOrderByPapelAsc. */
    @Query(value = """
        SELECT EXISTS(
            SELECT 1 FROM celula_membro
            WHERE celula_id = :celulaId AND pessoa_id = :pessoaId AND papel = :papel
        )
        """, nativeQuery = true)
    boolean existsByCelulaIdAndPessoaIdAndPapel(@Param("celulaId") UUID celulaId,
                                                 @Param("pessoaId") UUID pessoaId,
                                                 @Param("papel") String papel);

    List<CelulaMembro> findByCelulaIdAndVisitanteIdIsNotNull(UUID celulaId);

    List<CelulaMembro> findByCelulaIdAndPessoaIdIsNotNull(UUID celulaId);

    /** Pra reindexação em lote: evita N+1 consultando visitante->célula um a um. */
    @Query("SELECT cm.visitante.id, cm.celula.id FROM CelulaMembro cm WHERE cm.visitante IS NOT NULL")
    List<Object[]> buscarCelulaIdPorVisitanteId();

    /** Exclusão definitiva de pessoa: sai da lista de membros da célula. */
    @Modifying
    @Query(value = "DELETE FROM celula_membro WHERE pessoa_id = :pessoaId", nativeQuery = true)
    void deleteByPessoaId(@Param("pessoaId") UUID pessoaId);

    @Modifying
    @Query(value = """
        UPDATE celula_membro
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);
}
