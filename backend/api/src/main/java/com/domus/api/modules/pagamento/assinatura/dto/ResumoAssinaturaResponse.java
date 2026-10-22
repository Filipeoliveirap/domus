package com.domus.api.modules.pagamento.assinatura.dto;

import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.igreja.StatusAssinatura;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ResumoAssinaturaResponse(
    PlanoAssinatura plano,
    String nomePlano,
    BigDecimal valorMensal,
    StatusAssinatura status,
    long diasRestantesTrial,
    long totalPessoas,
    int limitePessoas,
    int percentualPessoas,
    long totalCongregacoes,
    int limiteCongregacoes,
    int percentualCongregacoes,
    boolean ehMatriz,
    String nomeMatriz,
    LocalDateTime cicloExpiraEm,
    Long diasRestantesCiclo
) {}
