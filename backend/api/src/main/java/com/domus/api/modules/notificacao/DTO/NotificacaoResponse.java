package com.domus.api.modules.notificacao.DTO;

import com.domus.api.modules.notificacao.Notificacao;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoResponse(
        UUID id,
        String tipo,
        String texto,
        String link,
        boolean lida,
        LocalDateTime criadoEm
) {
    public static NotificacaoResponse from(Notificacao n) {
        return new NotificacaoResponse(
                n.getId(), n.getTipo().name(), n.getTexto(), n.getLink(), n.isLida(), n.getCreatedAt());
    }
}
