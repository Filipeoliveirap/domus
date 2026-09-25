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

    /** Órfãs: sem referência nas tabelas que usam foto — acontece quando o upload é abandonado sem salvar.
     *  Nativa: JPQL respeita @SQLRestriction("deleted_at IS NULL") das entidades (Pessoa, Evento, Postagem, etc.),
     *  o que fazia a query JPQL ignorar registros com soft delete e tentar apagar foto ainda referenciada por FK.
     */
    @Query(value = """
        SELECT f.* FROM foto f
        WHERE f.created_at < :corte
          AND NOT EXISTS (SELECT 1 FROM pessoa p WHERE p.foto_id = f.id)
          AND NOT EXISTS (SELECT 1 FROM evento e WHERE e.foto_id = f.id)
          AND NOT EXISTS (SELECT 1 FROM igreja i WHERE i.logo_foto_id = f.id)
          AND NOT EXISTS (SELECT 1 FROM ministerio m WHERE m.foto_id = f.id)
          AND NOT EXISTS (SELECT 1 FROM celula c WHERE c.foto_id = f.id)
          AND NOT EXISTS (SELECT 1 FROM postagem pos WHERE pos.foto_id = f.id)
    """, nativeQuery = true)
    List<Foto> buscarOrfas(@Param("corte") LocalDateTime corte);

    /** Nativa: {@code Pessoa} tem {@code @SQLRestriction("deleted_at IS NULL")}, então JPQL não enxerga arquivados. */
    @Query(value = """
        SELECT f.* FROM foto f
        JOIN pessoa p ON p.foto_id = f.id
        WHERE p.deleted_at IS NOT NULL AND p.deleted_at < :corte
    """, nativeQuery = true)
    List<Foto> buscarDeArquivadas(@Param("corte") LocalDateTime corte);
}
