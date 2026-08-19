package com.domus.api.modules.dashboard;

import com.domus.api.modules.dashboard.dto.DashboardResponse;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.DTOs.MovimentacaoResponse;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.pessoa.Pessoa;
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
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final FamiliaIgrejaService familiaIgrejaService;

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
                relatorioRepository.agregarResumo(igrejaId, inicioMes, fimMes);
        BigDecimal entradas = resumo.getTotalEntradas();
        BigDecimal saidas = resumo.getTotalSaidas();
        BigDecimal saldo = entradas.subtract(saidas);

        List<MovimentacaoFinanceira> movimentacoesRecentes =
                movimentacaoRepository.recentes(igrejaId, PageRequest.of(0, LIMITE));
        // Nunca m.getCategoria().getNome() direto — categoria arquivada estoura EntityNotFoundException.
        List<UUID> idsCategoria = movimentacoesRecentes.stream().map(m -> m.getCategoria().getId()).distinct().toList();
        var nomesPorCategoria = idsCategoria.isEmpty()
                ? java.util.Map.<UUID, String>of()
                : categoriaRepository.findByIdInAndIgrejaIdIncluindoArquivadas(idsCategoria, igrejaId).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                com.domus.api.modules.financeiro.categoria.CategoriaFinanceira::getId,
                                com.domus.api.modules.financeiro.categoria.CategoriaFinanceira::getNome));
        List<UUID> idsPessoaContribuinte = movimentacoesRecentes.stream()
                .flatMap(m -> m.getContribuintes().stream())
                .map(c -> c.getPessoa())
                .filter(p -> p != null)
                .map(Pessoa::getId)
                .distinct()
                .toList();
        var pessoasContribuintes = idsPessoaContribuinte.isEmpty()
                ? java.util.Map.<UUID, Pessoa>of()
                : pessoaRepository.findByIdInIncluindoArquivadas(idsPessoaContribuinte).stream()
                        .collect(java.util.stream.Collectors.toMap(Pessoa::getId, p -> p));
        List<MovimentacaoResponse> recentes = movimentacoesRecentes.stream()
                .map(m -> MovimentacaoResponse.de(m, nomesPorCategoria.get(m.getCategoria().getId()), pessoasContribuintes))
                .toList();

        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        List<EventoResumoDTO> proximos =
                eventoRepository.proximosDaFamilia(igrejaId, idsFamilia, LocalDateTime.now(), PageRequest.of(0, LIMITE))
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
