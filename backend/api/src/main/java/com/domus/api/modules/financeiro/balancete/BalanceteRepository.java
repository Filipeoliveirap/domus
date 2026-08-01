package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BalanceteRepository extends JpaRepository<MovimentacaoFinanceira, UUID> {

    @Query(value = """
        SELECT c.id AS categoriaId,
               c.nome AS nomeCategoria,
               (c.deleted_at IS NOT NULL) AS arquivada,
               m.tipo AS tipo,
               EXTRACT(MONTH FROM m.data_movimentacao)::int AS mes,
               SUM(m.valor) AS total
        FROM movimentacao_financeira m
        JOIN categoria_financeira c ON c.id = m.categoria_id
        WHERE m.igreja_id = :igrejaId
          AND m.deleted_at IS NULL
          AND EXTRACT(YEAR FROM m.data_movimentacao) = :ano
        GROUP BY c.id, c.nome, c.deleted_at, m.tipo, EXTRACT(MONTH FROM m.data_movimentacao)
        """, nativeQuery = true)
    List<BalanceteProjections.LinhaMensalAgregada> agregarPorCategoriaEMes(
            @Param("igrejaId") UUID igrejaId, @Param("ano") int ano);

    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0)
        FROM movimentacao_financeira m
        WHERE m.igreja_id = :igrejaId
          AND m.deleted_at IS NULL
          AND m.data_movimentacao < :inicioAno
        """, nativeQuery = true)
    BigDecimal saldoAntesDe(@Param("igrejaId") UUID igrejaId, @Param("inicioAno") LocalDate inicioAno);

    @Query(value = """
        SELECT unaccent(lower(c.nome)) AS chave,
               MIN(c.nome) AS nomeExibicao,
               m.tipo AS tipo,
               EXTRACT(MONTH FROM m.data_movimentacao)::int AS mes,
               SUM(m.valor) AS total
        FROM movimentacao_financeira m
        JOIN categoria_financeira c ON c.id = m.categoria_id
        WHERE m.igreja_id IN (:igrejaIds)
          AND m.deleted_at IS NULL
          AND EXTRACT(YEAR FROM m.data_movimentacao) = :ano
        GROUP BY unaccent(lower(c.nome)), m.tipo, EXTRACT(MONTH FROM m.data_movimentacao)
        """, nativeQuery = true)
    List<BalanceteProjections.LinhaMensalConsolidada> agregarConsolidadoPorCategoriaEMes(
            @Param("igrejaIds") List<UUID> igrejaIds, @Param("ano") int ano);

    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0)
        FROM movimentacao_financeira m
        WHERE m.igreja_id IN (:igrejaIds)
          AND m.deleted_at IS NULL
          AND m.data_movimentacao < :inicioAno
        """, nativeQuery = true)
    BigDecimal saldoAntesDeVariasIgrejas(@Param("igrejaIds") List<UUID> igrejaIds,
            @Param("inicioAno") LocalDate inicioAno);
}
