package com.domus.api.modules.igreja.familia.consolidado.DTO;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * A tabela consolidada da aba "Congregações": o somatório da família no topo e a linha
 * de cada igreja embaixo. A mãe é uma igreja operante, então ela entra na soma e também
 * aparece como linha.
 */
public record ConsolidadoResponse(
        Totais familia,
        List<LinhaIgreja> porIgreja) {

    public record Membros(long total, long ativos, long inativos, long visitantes) {}

    public record Eventos(long total, long realizados, long proximos) {}

    public record Financeiro(BigDecimal entradas, BigDecimal saidas, BigDecimal saldo) {}

    public record Totais(Membros membros, Eventos eventos, Financeiro financeiro) {}

    public record LinhaIgreja(
            UUID igrejaId,
            String nome,
            boolean ehMae,
            Membros membros,
            Eventos eventos,
            Financeiro financeiro) {}
}
