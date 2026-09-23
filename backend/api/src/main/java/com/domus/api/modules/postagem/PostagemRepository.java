package com.domus.api.modules.postagem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostagemRepository extends JpaRepository<Postagem, UUID> {

    @Query("SELECT p FROM Postagem p WHERE p.igreja.id = :igrejaId AND p.oficial = true ORDER BY p.fixado DESC, p.criadoEm DESC")
    List<Postagem> findMuralAvisos(@Param("igrejaId") UUID igrejaId);

    @Query("SELECT p FROM Postagem p WHERE p.igreja.id = :igrejaId AND (:tipo IS NULL OR p.tipo = :tipo) ORDER BY p.criadoEm DESC")
    Page<Postagem> findFeed(@Param("igrejaId") UUID igrejaId, @Param("tipo") TipoPostagem tipo, Pageable pageable);

    Optional<Postagem> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
