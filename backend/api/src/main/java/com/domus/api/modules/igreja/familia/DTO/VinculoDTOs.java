package com.domus.api.modules.igreja.familia.DTO;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class VinculoDTOs {

    private VinculoDTOs() {}

    /** Os três estados são mutuamente exclusivos — a regra dos 2 níveis garante isso. */
    public enum EstadoVinculo { INDEPENDENTE, MAE, FILHA }

    /**
     * A congregação como a tela de "Igrejas vinculadas" a mostra.
     * {@code cidade}/{@code uf} vêm do endereço (podem ser nulos — endereço é opcional).
     */
    public record IgrejaResumo(
            UUID id,
            String nome,
            String cidade,
            String uf,
            LocalDateTime vinculadoEm) {}

    /**
     * Tudo que a tela precisa, numa chamada só.
     * {@code codigoVinculo} vem preenchido para INDEPENDENTE e MAE (a filha não gera código);
     * {@code mae} só para FILHA; {@code congregacoes} só para MAE.
     */
    public record VinculoStatusResponse(
            EstadoVinculo estado,
            String codigoVinculo,
            LocalDateTime codigoGeradoEm,
            IgrejaResumo mae,
            List<IgrejaResumo> congregacoes) {}

    public record EntrarNaFamiliaRequest(@NotBlank(message = "Informe o código de vínculo.") String codigo) {}
}
