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
    WHERE m.igreja.id = :igrejaId
      AND m.dataMovimentacao >= :dataInicio
      AND m.dataMovimentacao <= :dataFim
""")
    RelatorioProjections.ResumoAgregado agregarResumo(@Param("igrejaId") UUID igrejaId,
                                                      @Param("dataInicio") LocalDate dataInicio,
                                                      @Param("dataFim") LocalDate dataFim);

    @Query("""
    SELECT
        COALESCE(SUM(CASE WHEN ct.movimentacao.tipo = 'ENTRADA' THEN ct.valor ELSE 0 END), 0) AS totalEntradas,
        COALESCE(SUM(CASE WHEN ct.movimentacao.tipo = 'SAIDA' THEN ct.valor ELSE 0 END), 0) AS totalSaidas,
        COUNT(DISTINCT ct.movimentacao) AS quantidade
    FROM MovimentacaoContribuinte ct
    WHERE ct.movimentacao.igreja.id = :igrejaId
      AND ct.movimentacao.dataMovimentacao >= :dataInicio
      AND ct.movimentacao.dataMovimentacao <= :dataFim
      AND ct.pessoa.vinculo = :vinculo
""")
    RelatorioProjections.ResumoAgregado agregarResumoPorVinculo(@Param("igrejaId") UUID igrejaId,
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
    WHERE m.igreja.id = :igrejaId
      AND m.dataMovimentacao >= :dataInicio
      AND m.dataMovimentacao <= :dataFim
    GROUP BY c.id, c.nome, m.tipo
    ORDER BY SUM(m.valor) DESC
""")
    List<RelatorioProjections.CategoriaAgregada> agregarPorCategoria(@Param("igrejaId") UUID igrejaId,
                                                                     @Param("dataInicio") LocalDate dataInicio,
                                                                     @Param("dataFim") LocalDate dataFim);

    @Query("""
    SELECT
        c.id AS categoriaId,
        c.nome AS categoriaNome,
        ct.movimentacao.tipo AS tipo,
        SUM(ct.valor) AS total
    FROM MovimentacaoContribuinte ct
    JOIN ct.movimentacao.categoria c
    WHERE ct.movimentacao.igreja.id = :igrejaId
      AND ct.movimentacao.dataMovimentacao >= :dataInicio
      AND ct.movimentacao.dataMovimentacao <= :dataFim
      AND ct.pessoa.vinculo = :vinculo
    GROUP BY c.id, c.nome, ct.movimentacao.tipo
    ORDER BY SUM(ct.valor) DESC
""")
    List<RelatorioProjections.CategoriaAgregada> agregarPorCategoriaPorVinculo(@Param("igrejaId") UUID igrejaId,
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
    WHERE m.igreja.id = :igrejaId
      AND m.dataMovimentacao >= :dataInicio
      AND m.dataMovimentacao <= :dataFim
    GROUP BY EXTRACT(YEAR FROM m.dataMovimentacao), EXTRACT(MONTH FROM m.dataMovimentacao)
    ORDER BY EXTRACT(YEAR FROM m.dataMovimentacao), EXTRACT(MONTH FROM m.dataMovimentacao)
""")
    List<RelatorioProjections.MesAgregado> agregarEvolucaoMensal(@Param("igrejaId") UUID igrejaId,
                                                                 @Param("dataInicio") LocalDate dataInicio,
                                                                 @Param("dataFim") LocalDate dataFim);

    @Query("""
    SELECT
        CAST(EXTRACT(YEAR FROM ct.movimentacao.dataMovimentacao) AS integer) AS ano,
        CAST(EXTRACT(MONTH FROM ct.movimentacao.dataMovimentacao) AS integer) AS mes,
        COALESCE(SUM(CASE WHEN ct.movimentacao.tipo = 'ENTRADA' THEN ct.valor ELSE 0 END), 0) AS entradas,
        COALESCE(SUM(CASE WHEN ct.movimentacao.tipo = 'SAIDA' THEN ct.valor ELSE 0 END), 0) AS saidas
    FROM MovimentacaoContribuinte ct
    WHERE ct.movimentacao.igreja.id = :igrejaId
      AND ct.movimentacao.dataMovimentacao >= :dataInicio
      AND ct.movimentacao.dataMovimentacao <= :dataFim
      AND ct.pessoa.vinculo = :vinculo
    GROUP BY EXTRACT(YEAR FROM ct.movimentacao.dataMovimentacao), EXTRACT(MONTH FROM ct.movimentacao.dataMovimentacao)
    ORDER BY EXTRACT(YEAR FROM ct.movimentacao.dataMovimentacao), EXTRACT(MONTH FROM ct.movimentacao.dataMovimentacao)
""")
    List<RelatorioProjections.MesAgregado> agregarEvolucaoMensalPorVinculo(@Param("igrejaId") UUID igrejaId,
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
        WHERE m.igreja.id = :igrejaId
          AND m.dataMovimentacao >= :dataInicio
          AND m.dataMovimentacao <= :dataFim
        ORDER BY m.valor DESC
        LIMIT 1
    """)
    RelatorioProjections.MaiorLancamento buscarMaiorLancamento(@Param("igrejaId") UUID igrejaId,
                                                               @Param("dataInicio") LocalDate dataInicio,
                                                               @Param("dataFim") LocalDate dataFim);

    @Query("""
        SELECT
            m.id AS id,
            m.descricao AS descricao,
            c.nome AS categoriaNome,
            ct.movimentacao.tipo AS tipo,
            ct.valor AS valor,
            m.dataMovimentacao AS dataMovimentacao
        FROM MovimentacaoContribuinte ct
        JOIN ct.movimentacao m
        JOIN m.categoria c
        WHERE m.igreja.id = :igrejaId
          AND m.dataMovimentacao >= :dataInicio
          AND m.dataMovimentacao <= :dataFim
          AND ct.pessoa.vinculo = :vinculo
        ORDER BY ct.valor DESC
        LIMIT 1
    """)
    RelatorioProjections.MaiorLancamento buscarMaiorLancamentoPorVinculo(@Param("igrejaId") UUID igrejaId,
                                                                         @Param("dataInicio") LocalDate dataInicio,
                                                                         @Param("dataFim") LocalDate dataFim,
                                                                         @Param("vinculo") Vinculo vinculo);

    /**
     * Nativa + LEFT JOIN de propósito: um contribuinte cuja pessoa foi excluída (pessoa_id
     * NULL) precisa continuar contando aqui — JOIN normal (JPQL) sumiria com ele. Agrupa
     * todos os anônimos numa linha só (pessoa_id NULL).
     */
    @Query(value = """
        SELECT
            p.id AS pessoaId,
            COALESCE(p.nome, 'Pessoa removida do sistema') AS pessoaNome,
            m.tipo AS tipo,
            SUM(ct.valor) AS total
        FROM movimentacao_contribuinte ct
        JOIN movimentacao_financeira m ON m.id = ct.movimentacao_id
        LEFT JOIN pessoa p ON p.id = ct.pessoa_id
        WHERE m.igreja_id = :igrejaId
          AND m.deleted_at IS NULL
          AND m.data_movimentacao >= :dataInicio
          AND m.data_movimentacao <= :dataFim
        GROUP BY p.id, p.nome, m.tipo
        ORDER BY SUM(ct.valor) DESC
        """, nativeQuery = true)
    List<RelatorioProjections.ContribuinteAgregado> agregarPorContribuinte(@Param("igrejaId") UUID igrejaId,
                                                                           @Param("dataInicio") LocalDate dataInicio,
                                                                           @Param("dataFim") LocalDate dataFim);

    @Query("""
    SELECT
        p.id AS pessoaId,
        p.nome AS pessoaNome,
        ct.movimentacao.tipo AS tipo,
        SUM(ct.valor) AS total
    FROM MovimentacaoContribuinte ct
    JOIN ct.pessoa p
    WHERE ct.movimentacao.igreja.id = :igrejaId
      AND ct.movimentacao.dataMovimentacao >= :dataInicio
      AND ct.movimentacao.dataMovimentacao <= :dataFim
      AND p.vinculo = :vinculo
    GROUP BY p.id, p.nome, ct.movimentacao.tipo
    ORDER BY SUM(ct.valor) DESC
""")
    List<RelatorioProjections.ContribuinteAgregado> agregarPorContribuintePorVinculo(@Param("igrejaId") UUID igrejaId,
                                                                                      @Param("dataInicio") LocalDate dataInicio,
                                                                                      @Param("dataFim") LocalDate dataFim,
                                                                                      @Param("vinculo") Vinculo vinculo);
}
