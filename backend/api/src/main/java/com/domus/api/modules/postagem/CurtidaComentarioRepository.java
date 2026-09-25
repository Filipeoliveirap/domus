package com.domus.api.modules.postagem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CurtidaComentarioRepository extends JpaRepository<CurtidaComentario, UUID> {
    long countByComentarioId(UUID comentarioId);
    Optional<CurtidaComentario> findByComentarioIdAndPessoaId(UUID comentarioId, UUID pessoaId);
    boolean existsByComentarioIdAndPessoaId(UUID comentarioId, UUID pessoaId);
}
