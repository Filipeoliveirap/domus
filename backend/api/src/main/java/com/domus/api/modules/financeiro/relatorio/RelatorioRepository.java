package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.pessoa.Vinculo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RelatorioRepository extends JpaRepository<MovimentacaoFinanceira, UUID> {

    @Query("""
    SELECT
        COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE 0 END), 0) AS totalEntradas,
        COALESCE(SUM(CASE WHEN m.tipo = 'SAIDA' THEN m.valor ELSE 0 END), 0) AS totalSaidas,
        COUNT(m) AS quantidade
    FROM MovimentacaoFinanceira m
    LEFT JOIN m.pessoa p
    WHERE m.igreja.id = :igrejaId
      AND m.dataMovimentacao >= :dataInicio
      AND m.dataMovimentacao <= :dataFim
      AND (:vinculo IS NULL OR p.vinculo = :vinculo)
""")
    RelatorioProjections.ResumoAgregado agregarResumo(@Param("igrejaId") UUID igrejaId,
                                                      @Param("dataInicio") LocalDate dataInicio,
                                                      @Param("dataFim") LocalDate dataFim,
                                                      @Param("vinculo") Vinculo vinculo);
    @Query("""
    SELECT
        c.id AS categoriaId,
        c.nome AS categoriaNome,
        m.tipo AS tipo,
        SUM(m.valor) AS total
    FROM MovimentacaoFinanceira m
    JOIN m.categoria c
    LEFT JOIN m.pessoa p
    WHERE m.igreja.id = :igrejaId
      AND m.dataMovimentacao >= :dataInicio
      AND m.dataMovimentacao <= :dataFim
      AND (:vinculo IS NULL OR p.vinculo = :vinculo)
    GROUP BY c.id, c.nome, m.tipo
    ORDER BY SUM(m.valor) DESC
""")
    List<RelatorioProjections.CategoriaAgregada> agregarPorCategoria(@Param("igrejaId") UUID igrejaId,
                                                                     @Param("dataInicio") LocalDate dataInicio,
                                                                     @Param("dataFim") LocalDate dataFim,
                                                                     @Param("vinculo") Vinculo vinculo);
    @Query("""
    SELECT
        CAST(EXTRACT(YEAR FROM m.dataMovimentacao) AS integer) AS ano,
        CAST(EXTRACT(MONTH FROM m.dataMovimentacao) AS integer) AS mes,
        COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE 0 END), 0) AS entradas,
        COALESCE(SUM(CASE WHEN m.tipo = 'SAIDA' THEN m.valor ELSE 0 END), 0) AS saidas
    FROM MovimentacaoFinanceira m
    LEFT JOIN m.pessoa p
    WHERE m.igreja.id = :igrejaId
      AND m.dataMovimentacao >= :dataInicio
      AND m.dataMovimentacao <= :dataFim
      AND (:vinculo IS NULL OR p.vinculo = :vinculo)
    GROUP BY EXTRACT(YEAR FROM m.dataMovimentacao), EXTRACT(MONTH FROM m.dataMovimentacao)
    ORDER BY EXTRACT(YEAR FROM m.dataMovimentacao), EXTRACT(MONTH FROM m.dataMovimentacao)
""")
    List<RelatorioProjections.MesAgregado> agregarEvolucaoMensal(@Param("igrejaId") UUID igrejaId,
                                                                 @Param("dataInicio") LocalDate dataInicio,
                                                                 @Param("dataFim") LocalDate dataFim,
                                                                 @Param("vinculo") Vinculo vinculo);

    @Query("""
        SELECT
            m.id AS id,
            m.descricao AS descricao,
            c.nome AS categoriaNome,
            m.tipo AS tipo,
            m.valor AS valor,
            m.dataMovimentacao AS dataMovimentacao
        FROM MovimentacaoFinanceira m
        JOIN m.categoria c
        LEFT JOIN m.pessoa p
        WHERE m.igreja.id = :igrejaId
          AND m.dataMovimentacao >= :dataInicio
          AND m.dataMovimentacao <= :dataFim
          AND (:vinculo IS NULL OR p.vinculo = :vinculo)
        ORDER BY m.valor DESC
        LIMIT 1
    """)
    RelatorioProjections.MaiorLancamento buscarMaiorLancamento(@Param("igrejaId") UUID igrejaId,
                                                               @Param("dataInicio") LocalDate dataInicio,
                                                               @Param("dataFim") LocalDate dataFim,
                                                               @Param("vinculo") Vinculo vinculo);
}
