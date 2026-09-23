package com.domus.api.modules.postagem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComentarioPostagemRepository extends JpaRepository<ComentarioPostagem, UUID> {

    List<ComentarioPostagem> findByPostagemIdOrderByCriadoEmAsc(UUID postagemId);

    long countByPostagemId(UUID postagemId);

    Optional<ComentarioPostagem> findByIdAndPostagemId(UUID id, UUID postagemId);
}
