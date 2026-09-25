package com.domus.api.modules.postagem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostagemRepository extends JpaRepository<Postagem, UUID> {

    @Query("SELECT p FROM Postagem p WHERE (p.igreja.id = :minhaIgrejaId OR (p.igreja.id IN :restoDaFamilia AND p.restritoPropriaIgreja = false)) AND p.oficial = true ORDER BY p.fixado DESC, p.criadoEm DESC")
    List<Postagem> findMuralAvisos(@Param("minhaIgrejaId") UUID minhaIgrejaId, @Param("restoDaFamilia") Collection<UUID> restoDaFamilia);

    @Query("SELECT p FROM Postagem p WHERE (p.igreja.id = :minhaIgrejaId OR (p.igreja.id IN :restoDaFamilia AND p.restritoPropriaIgreja = false)) AND p.oficial = false AND (:tipo IS NULL OR p.tipo = :tipo) ORDER BY p.criadoEm DESC")
    Page<Postagem> findFeed(@Param("minhaIgrejaId") UUID minhaIgrejaId, @Param("restoDaFamilia") Collection<UUID> restoDaFamilia, @Param("tipo") TipoPostagem tipo, Pageable pageable);

    @Query("SELECT p FROM Postagem p WHERE p.id = :id AND (p.igreja.id = :minhaIgrejaId OR (p.igreja.id IN :restoDaFamilia AND p.restritoPropriaIgreja = false))")
    Optional<Postagem> findByIdAndFamilia(@Param("id") UUID id, @Param("minhaIgrejaId") UUID minhaIgrejaId, @Param("restoDaFamilia") Collection<UUID> restoDaFamilia);

    Optional<Postagem> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
