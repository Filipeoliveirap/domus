package com.domus.api.modules.financeiro.balancete.DTOs;

import java.util.List;

public record BalanceteFamiliaResponseDTO(
        List<BalanceteIgrejaDTO> porIgreja,
        BalanceteResponseDTO consolidado
) {}
