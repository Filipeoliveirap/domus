package com.domus.api.modules.contapagar.dto;

import java.math.BigDecimal;

/** Resumo de contas a pagar para um mês de referência. */
public record ResumoResponse(
    BigDecimal venceHoje,
    BigDecimal aVencerNoMes,
    BigDecimal atrasadas,
    BigDecimal pagasNoMes
) {}
