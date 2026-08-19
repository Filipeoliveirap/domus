package com.domus.api.modules.foto;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FotoRepository extends JpaRepository<Foto, UUID> {

    Optional<Foto> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    List<Foto> findByIgrejaId(UUID igrejaId);

    /** Órfãs: sem referência nas três tabelas — acontece quando o upload é abandonado sem salvar. */
    @Query("""
        SELECT f FROM Foto f
        WHERE f.createdAt < :corte
          AND NOT EXISTS (SELECT 1 FROM Pessoa p WHERE p.foto = f)
          AND NOT EXISTS (SELECT 1 FROM Evento e WHERE e.foto = f)
          AND NOT EXISTS (SELECT 1 FROM Igreja i WHERE i.logoFoto = f)
    """)
    List<Foto> buscarOrfas(@Param("corte") LocalDateTime corte);

    /** Nativa: {@code Pessoa} tem {@code @SQLRestriction("deleted_at IS NULL")}, então JPQL não enxerga arquivados. */
    @Query(value = """
        SELECT f.* FROM foto f
        JOIN pessoa p ON p.foto_id = f.id
        WHERE p.deleted_at IS NOT NULL AND p.deleted_at < :corte
    """, nativeQuery = true)
    List<Foto> buscarDeArquivadas(@Param("corte") LocalDateTime corte);
}
