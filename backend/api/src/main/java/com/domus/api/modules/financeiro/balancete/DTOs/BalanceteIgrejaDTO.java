package com.domus.api.modules.financeiro.balancete.DTOs;

import java.util.UUID;

public record BalanceteIgrejaDTO(
        UUID igrejaId,
        String nomeIgreja,
        boolean ehSede,
        BalanceteResponseDTO balancete
) {}
