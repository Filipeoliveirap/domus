package com.domus.api.modules.evento.local;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalEventoRepository extends JpaRepository<LocalEvento, UUID> {

    List<LocalEvento> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<LocalEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query(value = "SELECT * FROM local_evento WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL ORDER BY nome ASC",
            nativeQuery = true)
    List<LocalEvento> findArquivadosPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findByIdAndIgrejaId}, mas enxerga arquivados também — tela de Arquivados. */
    @Query(value = "SELECT * FROM local_evento WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<LocalEvento> findByIdAndIgrejaIdIncluindoArquivados(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = "UPDATE local_evento SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM local_evento WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    @Modifying
    @Query(value = "DELETE FROM local_evento WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
}
