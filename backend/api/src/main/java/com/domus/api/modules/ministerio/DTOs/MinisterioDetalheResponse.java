package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Ministerio;
import java.util.List;
import java.util.UUID;

public record MinisterioDetalheResponse(
        UUID id,
        String nome,
        UUID fotoId,
        List<MembroResponse> membros,
        List<MembroResponse> pedidosPendentes,
        boolean souLiderDesteMinisterio,
        boolean souMembroAtivo,
        boolean tenhoPedidoPendente,
        boolean arquivada
) {
    public static MinisterioDetalheResponse from(
            Ministerio ministerio, List<MembroResponse> membros,
            List<MembroResponse> pedidosPendentes, boolean souLiderDesteMinisterio,
            boolean souMembroAtivo, boolean tenhoPedidoPendente) {
        return new MinisterioDetalheResponse(
                ministerio.getId(), ministerio.getNome(),
                ministerio.getFoto() != null ? ministerio.getFoto().getId() : null,
                membros, pedidosPendentes,
                souLiderDesteMinisterio, souMembroAtivo, tenhoPedidoPendente,
                ministerio.getDeletedAt() != null);
    }
}
