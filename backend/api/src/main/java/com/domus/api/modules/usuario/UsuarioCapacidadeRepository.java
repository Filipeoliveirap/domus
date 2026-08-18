package com.domus.api.modules.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UsuarioCapacidadeRepository extends JpaRepository<UsuarioCapacidade, UsuarioCapacidadeId> {

    List<UsuarioCapacidade> findByUsuarioId(UUID usuarioId);

    void deleteByUsuarioIdAndCapacidade(UUID usuarioId, String capacidade);

    void deleteByUsuarioId(UUID usuarioId);

    boolean existsByUsuarioIdAndCapacidade(UUID usuarioId, String capacidade);

    /** Quem concedeu a capacidade some do rastro se for excluído — metadado, não vale a pena preservar como texto. */
    @Modifying
    @Query(value = "UPDATE usuario_capacidade SET concedido_por_usuario_id = NULL WHERE concedido_por_usuario_id = :usuarioId", nativeQuery = true)
    void desvincularConcedidoPor(@Param("usuarioId") UUID usuarioId);
}
