package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.UUID;

/**
 * Resumo (nome + foto) de quem inscreveu alguém em um evento, usado para resolver
 * {@code inscritoPorUsuarioId} em lote na lista de inscritos — evita N+1 (ver
 * {@link com.domus.api.modules.usuario.UsuarioRepository#buscarRegistrantes}).
 */
public record RegistranteResumo(UUID usuarioId, String nome, String foto) {
}
