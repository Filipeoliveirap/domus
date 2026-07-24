package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Ministerio;
import java.util.List;
import java.util.UUID;

public record MinisterioDetalheResponse(
        UUID id,
        String nome,
        List<MembroResponse> membros,
        List<MembroResponse> pedidosPendentes,
        boolean souLiderDesteMinisterio,
        boolean souMembroAtivo,
        boolean tenhoPedidoPendente
) {
    public static MinisterioDetalheResponse from(
            Ministerio ministerio, List<MembroResponse> membros,
            List<MembroResponse> pedidosPendentes, boolean souLiderDesteMinisterio,
            boolean souMembroAtivo, boolean tenhoPedidoPendente) {
        return new MinisterioDetalheResponse(
                ministerio.getId(), ministerio.getNome(), membros, pedidosPendentes,
                souLiderDesteMinisterio, souMembroAtivo, tenhoPedidoPendente);
    }
}
