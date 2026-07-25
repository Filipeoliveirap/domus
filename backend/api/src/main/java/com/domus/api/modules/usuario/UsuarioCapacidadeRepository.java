package com.domus.api.modules.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UsuarioCapacidadeRepository extends JpaRepository<UsuarioCapacidade, UsuarioCapacidadeId> {

    List<UsuarioCapacidade> findByUsuarioId(UUID usuarioId);

    void deleteByUsuarioIdAndCapacidade(UUID usuarioId, String capacidade);

    boolean existsByUsuarioIdAndCapacidade(UUID usuarioId, String capacidade);
}
