package com.domus.api.modules.termos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TermoAceiteRepository extends JpaRepository<TermoAceite, UUID> {

    long countByUsuarioIdAndVersao(UUID usuarioId, String versao);

    @Query("SELECT MAX(t.aceitoEm) FROM TermoAceite t WHERE t.usuario.id = :usuarioId")
    LocalDateTime buscarUltimoAceite(@Param("usuarioId") UUID usuarioId);
}
