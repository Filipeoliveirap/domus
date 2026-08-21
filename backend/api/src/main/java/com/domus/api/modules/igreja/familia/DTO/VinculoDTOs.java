package com.domus.api.modules.igreja.familia.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class VinculoDTOs {

    private VinculoDTOs() {}

    /** Os três estados são mutuamente exclusivos — a regra dos 2 níveis garante isso. */
    public enum EstadoVinculo { INDEPENDENTE, MAE, FILHA }

    /** {@code cidade}/{@code uf} vêm do endereço, podem ser nulos (endereço é opcional). */
    public record IgrejaResumo(
            UUID id,
            String nome,
            String cidade,
            String uf,
            LocalDateTime vinculadoEm) {}

    /** {@code codigoVinculo} preenchido só para INDEPENDENTE/MAE; {@code mae} só para FILHA; {@code congregacoes} só para MAE. */
    public record VinculoStatusResponse(
            EstadoVinculo estado,
            String codigoVinculo,
            LocalDateTime codigoGeradoEm,
            IgrejaResumo mae,
            List<IgrejaResumo> congregacoes) {}

    public record EntrarNaFamiliaRequest(
            @NotBlank(message = "Informe o código de vínculo.")
            @Size(max = 20, message = "Código de vínculo inválido.")
            String codigo) {}
}
