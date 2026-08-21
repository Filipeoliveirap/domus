package com.domus.api.modules.ministerio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinisterioMembroRepository extends JpaRepository<MinisterioMembro, UUID> {

    Optional<MinisterioMembro> findByMinisterioIdAndPessoaId(UUID ministerioId, UUID pessoaId);

    /** Nativa de propósito: derived query aqui vazaria o @SQLRestriction de Ministerio e esconderia membros de ministério arquivado. */
    @Query(value = "SELECT * FROM ministerio_membro WHERE ministerio_id = :ministerioId ORDER BY papel ASC", nativeQuery = true)
    List<MinisterioMembro> findByMinisterioIdOrderByPapelAsc(@Param("ministerioId") UUID ministerioId);

    List<MinisterioMembro> findByPessoaIdAndIgrejaIdAndStatus(UUID pessoaId, UUID igrejaId, StatusMembro status);

    /** Nativa pelo mesmo motivo de findByMinisterioIdOrderByPapelAsc. */
    @Query(value = "SELECT * FROM ministerio_membro WHERE ministerio_id = :ministerioId AND papel = :papel AND status = :status",
           nativeQuery = true)
    List<MinisterioMembro> findByMinisterioIdAndPapelAndStatus(
            @Param("ministerioId") UUID ministerioId, @Param("papel") String papel, @Param("status") String status);

    /** Nativa pelo mesmo motivo de findByMinisterioIdOrderByPapelAsc. */
    @Query(value = """
        SELECT EXISTS(
            SELECT 1 FROM ministerio_membro
            WHERE ministerio_id = :ministerioId AND pessoa_id = :pessoaId
              AND papel = :papel AND status = :status
        )
        """, nativeQuery = true)
    boolean existsByMinisterioIdAndPessoaIdAndPapelAndStatus(
            @Param("ministerioId") UUID ministerioId, @Param("pessoaId") UUID pessoaId,
            @Param("papel") String papel, @Param("status") String status);

    /** Exclusão definitiva de pessoa: sai da lista de membros de todos os ministérios. */
    @Modifying
    @Query(value = "DELETE FROM ministerio_membro WHERE pessoa_id = :pessoaId", nativeQuery = true)
    void deleteByPessoaId(@Param("pessoaId") UUID pessoaId);

    @Modifying
    @Query(value = """
        UPDATE ministerio_membro
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);

    /** Purga definitiva da igreja: apaga direto por igreja_id própria, antes do ministério pai. */
    @Modifying
    @Query(value = "DELETE FROM ministerio_membro WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}
