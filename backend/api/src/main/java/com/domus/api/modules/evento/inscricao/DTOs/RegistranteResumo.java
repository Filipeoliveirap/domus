package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.UUID;

/** Resolve {@code inscritoPorUsuarioId} em lote na lista de inscritos — evita N+1. */
public record RegistranteResumo(UUID usuarioId, String nome, UUID fotoId) {
}
