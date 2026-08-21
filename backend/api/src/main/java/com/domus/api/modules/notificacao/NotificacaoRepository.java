package com.domus.api.modules.notificacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByUsuarioDestinatarioId(UUID usuarioId, Pageable pageable);

    long countByUsuarioDestinatarioIdAndLidaFalse(UUID usuarioId);

    Optional<Notificacao> findByIdAndUsuarioDestinatarioId(UUID id, UUID usuarioId);

    List<Notificacao> findByUsuarioDestinatarioIdAndLidaFalse(UUID usuarioId);
}
