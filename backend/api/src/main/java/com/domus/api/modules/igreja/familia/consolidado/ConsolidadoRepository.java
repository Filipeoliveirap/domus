package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.pessoa.Pessoa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** {@code :igrejaIds} sempre vem de {@code FamiliaIgrejaService.idsDaFamilia} — nunca direto da requisição. */
@Repository
public interface ConsolidadoRepository extends JpaRepository<Pessoa, UUID> {

    @Query("""
    SELECT
        m.igreja.id AS igrejaId,
        CAST(m.vinculo AS string) AS vinculo,
        COUNT(m) AS total
    FROM Pessoa m
    WHERE m.igreja.id IN :igrejaIds
    GROUP BY m.igreja.id, m.vinculo
""")
    List<ConsolidadoProjections.MembrosPorIgreja> contarMembros(
            @Param("igrejaIds") List<UUID> igrejaIds);

    /** Realizado = já terminou; usa {@code fimEm} quando existe, senão cai pra {@code inicioEm}. */
    @Query("""
    SELECT
        e.igreja.id AS igrejaId,
        COALESCE(SUM(CASE WHEN COALESCE(e.fimEm, e.inicioEm) < :agora THEN 1 ELSE 0 END), 0) AS realizados,
        COALESCE(SUM(CASE WHEN COALESCE(e.fimEm, e.inicioEm) >= :agora THEN 1 ELSE 0 END), 0) AS proximos
    FROM Evento e
    WHERE e.igreja.id IN :igrejaIds
    GROUP BY e.igreja.id
""")
    List<ConsolidadoProjections.EventosPorIgreja> contarEventos(
            @Param("igrejaIds") List<UUID> igrejaIds,
            @Param("agora") LocalDateTime agora);

    @Query("""
    SELECT
        mv.igreja.id AS igrejaId,
        COALESCE(SUM(CASE WHEN mv.tipo = 'ENTRADA' THEN mv.valor ELSE 0 END), 0) AS entradas,
        COALESCE(SUM(CASE WHEN mv.tipo = 'SAIDA' THEN mv.valor ELSE 0 END), 0) AS saidas
    FROM MovimentacaoFinanceira mv
    WHERE mv.igreja.id IN :igrejaIds
      AND mv.dataMovimentacao >= :dataInicio
      AND mv.dataMovimentacao <= :dataFim
    GROUP BY mv.igreja.id
""")
    List<ConsolidadoProjections.FinanceiroPorIgreja> agregarFinanceiro(
            @Param("igrejaIds") List<UUID> igrejaIds,
            @Param("dataInicio") LocalDate dataInicio,
            @Param("dataFim") LocalDate dataFim);
}
