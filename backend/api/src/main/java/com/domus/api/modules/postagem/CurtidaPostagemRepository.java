package com.domus.api.modules.postagem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CurtidaPostagemRepository extends JpaRepository<CurtidaPostagem, UUID> {

    Optional<CurtidaPostagem> findByPostagemIdAndPessoaId(UUID postagemId, UUID pessoaId);

    long countByPostagemId(UUID postagemId);
}
