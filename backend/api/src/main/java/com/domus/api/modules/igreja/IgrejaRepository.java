package com.domus.api.modules.igreja;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IgrejaRepository extends JpaRepository<Igreja, UUID> {
    boolean existsByCnpj(String cnpj);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Igreja i WHERE i.id = :id")
    Optional<Igreja> buscarComLock(@Param("id") UUID id);

    Optional<Igreja> findByCodigoVinculo(String codigoVinculo);

    /** "Sou mãe?" é ter pelo menos uma filha — não é possuir um código. */
    boolean existsByIgrejaMaeId(UUID igrejaMaeId);

    List<Igreja> findByIgrejaMaeIdOrderByNomeAsc(UUID igrejaMaeId);

    /** Só os ids das filhas — evita carregar entidade à toa no cálculo da família. */
    @Query("SELECT i.id FROM Igreja i WHERE i.igrejaMae.id = :maeId")
    List<UUID> buscarIdsDasFilhas(@Param("maeId") UUID maeId);

    @Modifying
    @Query(value = """
        UPDATE igreja
           SET atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END,
               vinculado_por_texto = CASE WHEN vinculado_por_usuario_id = :usuarioId THEN :nome ELSE vinculado_por_texto END,
               vinculado_por_usuario_id = CASE WHEN vinculado_por_usuario_id = :usuarioId THEN NULL ELSE vinculado_por_usuario_id END
         WHERE atualizado_por_usuario_id = :usuarioId OR vinculado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);

    /** Toda igreja com exclusão agendada — o job varre esta lista todo dia. */
    @Query("SELECT i FROM Igreja i WHERE i.exclusaoAgendadaEm IS NOT NULL")
    List<Igreja> buscarComExclusaoAgendada();

    @Modifying
    @Query(value = """
        UPDATE igreja
           SET exclusao_agendada_em = :agora, exclusao_agendada_por_usuario_id = :usuarioId
         WHERE id = :igrejaId
        """, nativeQuery = true)
    void marcarExclusaoAgendada(@Param("igrejaId") UUID igrejaId,
                                 @Param("usuarioId") UUID usuarioId,
                                 @Param("agora") java.time.LocalDateTime agora);

    @Modifying
    @Query(value = """
        UPDATE igreja
           SET exclusao_agendada_em = NULL, exclusao_agendada_por_usuario_id = NULL
         WHERE id = :igrejaId
        """, nativeQuery = true)
    void cancelarExclusaoAgendada(@Param("igrejaId") UUID igrejaId);
}
