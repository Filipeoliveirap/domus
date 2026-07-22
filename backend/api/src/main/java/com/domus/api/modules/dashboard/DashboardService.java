package com.domus.api.modules.dashboard;

import com.domus.api.modules.dashboard.dto.DashboardResponse;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.movimentacao.DTOs.MovimentacaoResponse;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.relatorio.RelatorioProjections;
import com.domus.api.modules.financeiro.relatorio.RelatorioRepository;
import com.domus.api.modules.inicio.dto.EventoResumoDTO;
import com.domus.api.modules.pessoa.PessoaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int LIMITE = 5;

    private final PessoaRepository pessoaRepository;
    private final EventoRepository eventoRepository;
    private final RelatorioRepository relatorioRepository;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;

    @Transactional(readOnly = true)
    public DashboardResponse carregar(UUID igrejaId) {
        LocalDate hoje = LocalDate.now();
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.withDayOfMonth(hoje.lengthOfMonth());
        LocalDate inicioSemana = hoje.with(DayOfWeek.MONDAY);
        LocalDate fimSemana = inicioSemana.plusDays(6);

        long totalMembros = pessoaRepository.countByIgrejaId(igrejaId);
        long novosMembros = pessoaRepository.countByIgrejaIdAndCreatedAtAfter(igrejaId, inicioMes.atStartOfDay());

        long eventosMes = eventoRepository.countByIgrejaIdAndInicioEmBetween(
                igrejaId, inicioMes.atStartOfDay(), fimMes.atTime(LocalTime.MAX));
        long eventosSemana = eventoRepository.countByIgrejaIdAndInicioEmBetween(
                igrejaId, inicioSemana.atStartOfDay(), fimSemana.atTime(LocalTime.MAX));

        RelatorioProjections.ResumoAgregado resumo =
                relatorioRepository.agregarResumo(igrejaId, inicioMes, fimMes, null);
        BigDecimal entradas = resumo.getTotalEntradas();
        BigDecimal saidas = resumo.getTotalSaidas();
        BigDecimal saldo = entradas.subtract(saidas);

        List<MovimentacaoResponse> recentes =
                movimentacaoRepository.recentes(igrejaId, PageRequest.of(0, LIMITE))
                        .stream().map(MovimentacaoResponse::de).toList();

        List<EventoResumoDTO> proximos =
                eventoRepository.proximos(igrejaId, LocalDateTime.now(), PageRequest.of(0, LIMITE))
                        .stream().map(EventoResumoDTO::from).toList();

        return new DashboardResponse(
                new DashboardResponse.Pessoas(totalMembros, novosMembros),
                new DashboardResponse.Eventos(eventosMes, eventosSemana),
                new DashboardResponse.Financeiro(entradas, saidas, saldo),
                recentes,
                proximos
        );
    }
}
