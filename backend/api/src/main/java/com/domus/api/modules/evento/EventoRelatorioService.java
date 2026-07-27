package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.RelatorioEventoResponse;
import com.domus.api.modules.evento.DTOs.RelatorioGeralResponse;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventoRelatorioService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PessoaRepository pessoaRepository;

    @Transactional(readOnly = true)
    public RelatorioEventoResponse relatorioIndividual(UUID eventoId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        long pessoasInscritas = inscricaoRepository.countPessoasInscritas(eventoId);
        long convidadosInscritos = inscricaoRepository.countConvidadosInscritos(eventoId);
        var inscritos = new RelatorioEventoResponse.Inscritos(pessoasInscritas, convidadosInscritos);

        long totalAtivas = pessoaRepository.countByIgrejaId(igrejaId);
        Double percentualIgrejaInscritos = totalAtivas > 0
                ? arredondar((pessoasInscritas * 100.0) / totalAtivas)
                : 0.0;

        if (!evento.isControlaPresenca()) {
            return new RelatorioEventoResponse(inscritos, percentualIgrejaInscritos, null, null);
        }

        long pessoasCompareceram = inscricaoRepository.countPessoasCompareceram(eventoId);
        long convidadosCompareceram = inscricaoRepository.countConvidadosCompareceram(eventoId);
        var compareceram = new RelatorioEventoResponse.Compareceram(pessoasCompareceram, convidadosCompareceram);

        Double percentualIgreja = totalAtivas > 0
                ? arredondar((pessoasCompareceram * 100.0) / totalAtivas)
                : 0.0;

        return new RelatorioEventoResponse(inscritos, percentualIgrejaInscritos, compareceram, percentualIgreja);
    }

    static double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }

    /**
     * Relatório entre eventos com filtros combináveis e opcionais (período, recorte etário, tipo).
     * Comparecimento médio e participantes únicos só existem entre eventos com controlaPresenca=true;
     * evento mais popular cai para inscritos confirmados quando falta dado de comparecimento.
     */
    @Transactional(readOnly = true)
    public RelatorioGeralResponse relatorioGeral(UUID igrejaId, LocalDateTime inicio, LocalDateTime fim,
                                                  String recorteEtario, String tipo, Pageable pageable) {
        List<Evento> eventosFiltrados = eventoRepository.buscarParaRelatorio(igrejaId, inicio, fim, recorteEtario, tipo);

        Map<UUID, Long> totalInscritosPorEvento = new HashMap<>();
        Map<UUID, Long> totalCompareceramPorEvento = new HashMap<>();
        for (Evento e : eventosFiltrados) {
            long inscritos = inscricaoRepository.countPessoasInscritas(e.getId())
                    + inscricaoRepository.countConvidadosInscritos(e.getId());
            totalInscritosPorEvento.put(e.getId(), inscritos);

            if (e.isControlaPresenca()) {
                long compareceram = inscricaoRepository.countPessoasCompareceram(e.getId())
                        + inscricaoRepository.countConvidadosCompareceram(e.getId());
                totalCompareceramPorEvento.put(e.getId(), compareceram);
            }
        }

        RelatorioGeralResponse.Resumo resumo = montarResumo(eventosFiltrados, totalCompareceramPorEvento);
        RelatorioGeralResponse.EventoMaisPopular maisPopular = eventoMaisPopular(eventosFiltrados, totalInscritosPorEvento);
        List<RelatorioGeralResponse.PontoTendencia> tendencia = montarTendencia(igrejaId, recorteEtario, tipo, totalCompareceramPorEvento);

        List<RelatorioGeralResponse.UltimoEvento> todosUltimosEventos = montarUltimosEventos(
                igrejaId, eventosFiltrados, totalInscritosPorEvento, totalCompareceramPorEvento);
        PagedResponse<RelatorioGeralResponse.UltimoEvento> ultimosEventosPaginado = paginar(todosUltimosEventos, pageable);

        return new RelatorioGeralResponse(resumo, maisPopular, tendencia, ultimosEventosPaginado);
    }

    private static <T> PagedResponse<T> paginar(List<T> lista, Pageable pageable) {
        int inicio = Math.min((int) pageable.getOffset(), lista.size());
        int fim = Math.min(inicio + pageable.getPageSize(), lista.size());
        Page<T> pagina = new PageImpl<>(lista.subList(inicio, fim), pageable, lista.size());
        return PagedResponse.from(pagina);
    }

    private RelatorioGeralResponse.Resumo montarResumo(List<Evento> eventosFiltrados,
                                                        Map<UUID, Long> totalCompareceramPorEvento) {
        Double comparecimentoMedio = totalCompareceramPorEvento.isEmpty() ? null
                : arredondar(totalCompareceramPorEvento.values().stream().mapToLong(Long::longValue).average().orElse(0));

        Long participantesUnicos = totalCompareceramPorEvento.isEmpty() ? null
                : inscricaoRepository.contarParticipantesUnicos(new ArrayList<>(totalCompareceramPorEvento.keySet()));

        return new RelatorioGeralResponse.Resumo(eventosFiltrados.size(), comparecimentoMedio, participantesUnicos);
    }

    private RelatorioGeralResponse.EventoMaisPopular eventoMaisPopular(List<Evento> eventosFiltrados,
                                                                        Map<UUID, Long> totalInscritosPorEvento) {
        return eventosFiltrados.stream()
                .max(Comparator.comparingLong(e -> totalInscritosPorEvento.get(e.getId())))
                .map(e -> new RelatorioGeralResponse.EventoMaisPopular(
                        e.getId(), e.getTitulo(), totalInscritosPorEvento.get(e.getId())))
                .orElse(null);
    }

    /**
     * Últimos 6 meses (atual + 5 anteriores), fixo, independente do filtro de período.
     * Recorte etário e tipo se aplicam; mês sem evento com controlaPresenca=true vira null.
     */
    private List<RelatorioGeralResponse.PontoTendencia> montarTendencia(
            UUID igrejaId, String recorteEtario, String tipo, Map<UUID, Long> totalCompareceramJaCalculado) {
        java.time.YearMonth mesAtual = java.time.YearMonth.now();
        LocalDateTime desde = mesAtual.minusMonths(5).atDay(1).atStartOfDay();

        List<Evento> eventosControlados = eventoRepository.buscarComControlaPresenca(igrejaId, desde, recorteEtario, tipo);

        Map<java.time.YearMonth, List<Long>> comparecimentosPorMes = new HashMap<>();
        for (Evento e : eventosControlados) {
            long compareceram = totalCompareceramJaCalculado.containsKey(e.getId())
                    ? totalCompareceramJaCalculado.get(e.getId())
                    : inscricaoRepository.countPessoasCompareceram(e.getId())
                        + inscricaoRepository.countConvidadosCompareceram(e.getId());
            java.time.YearMonth mes = java.time.YearMonth.from(e.getInicioEm());
            comparecimentosPorMes.computeIfAbsent(mes, k -> new ArrayList<>()).add(compareceram);
        }

        List<RelatorioGeralResponse.PontoTendencia> tendencia = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            java.time.YearMonth mes = mesAtual.minusMonths(i);
            List<Long> valores = comparecimentosPorMes.get(mes);
            Double media = (valores == null || valores.isEmpty()) ? null
                    : arredondar(valores.stream().mapToLong(Long::longValue).average().orElse(0));
            tendencia.add(new RelatorioGeralResponse.PontoTendencia(mes.toString(), media));
        }
        return tendencia;
    }

    private List<RelatorioGeralResponse.UltimoEvento> montarUltimosEventos(
            UUID igrejaId, List<Evento> eventosFiltrados,
            Map<UUID, Long> totalInscritosPorEvento, Map<UUID, Long> totalCompareceramPorEvento) {

        double mediaInscritosFiltro = eventosFiltrados.isEmpty() ? 0
                : totalInscritosPorEvento.values().stream().mapToLong(Long::longValue).average().orElse(0);
        Double mediaComparecimentoFiltro = totalCompareceramPorEvento.isEmpty() ? null
                : totalCompareceramPorEvento.values().stream().mapToLong(Long::longValue).average().orElse(0);

        List<RelatorioGeralResponse.UltimoEvento> ultimosEventos = new ArrayList<>();
        for (Evento e : eventosFiltrados) {
            boolean controlaAtual = e.isControlaPresenca();
            long totalInscritosAtual = totalInscritosPorEvento.get(e.getId());
            Long totalCompareceuAtual = totalCompareceramPorEvento.get(e.getId());

            long totalParticipantes = (controlaAtual && totalCompareceuAtual != null)
                    ? totalCompareceuAtual : totalInscritosAtual;

            RelatorioGeralResponse.Variacao variacaoAnterior = calcularVariacaoEventoAnterior(
                    igrejaId, e, controlaAtual, totalInscritosAtual, totalCompareceuAtual);
            RelatorioGeralResponse.Variacao variacaoMedia = calcularVariacaoMediaGeral(
                    controlaAtual, totalInscritosAtual, totalCompareceuAtual, mediaInscritosFiltro, mediaComparecimentoFiltro);

            ultimosEventos.add(new RelatorioGeralResponse.UltimoEvento(
                    e.getId(), e.getTitulo(), e.getInicioEm(), totalParticipantes, variacaoAnterior, variacaoMedia));
        }
        return ultimosEventos;
    }

    private RelatorioGeralResponse.Variacao calcularVariacaoEventoAnterior(
            UUID igrejaId, Evento atual, boolean controlaAtual, long totalInscritosAtual, Long totalCompareceuAtual) {
        if (atual.getTipo() == null) return null;

        Evento anterior = eventoRepository
                .findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(igrejaId, atual.getTipo(), atual.getInicioEm())
                .orElse(null);
        if (anterior == null) return null;

        boolean usaComparecimento = controlaAtual && anterior.isControlaPresenca();
        long valorAtual = usaComparecimento
                ? (totalCompareceuAtual != null ? totalCompareceuAtual : 0)
                : totalInscritosAtual;
        long valorAnterior = usaComparecimento
                ? inscricaoRepository.countPessoasCompareceram(anterior.getId()) + inscricaoRepository.countConvidadosCompareceram(anterior.getId())
                : inscricaoRepository.countPessoasInscritas(anterior.getId()) + inscricaoRepository.countConvidadosInscritos(anterior.getId());

        if (valorAnterior == 0) return null;

        double percentual = arredondar(((valorAtual - valorAnterior) * 100.0) / valorAnterior);
        return new RelatorioGeralResponse.Variacao(percentual, usaComparecimento ? BaseComparacao.COMPARECIMENTO : BaseComparacao.INSCRITOS);
    }

    private RelatorioGeralResponse.Variacao calcularVariacaoMediaGeral(
            boolean controlaAtual, long totalInscritosAtual, Long totalCompareceuAtual,
            double mediaInscritosFiltro, Double mediaComparecimentoFiltro) {

        if (controlaAtual && mediaComparecimentoFiltro != null && mediaComparecimentoFiltro > 0) {
            long valorAtual = totalCompareceuAtual != null ? totalCompareceuAtual : 0;
            double percentual = arredondar(((valorAtual - mediaComparecimentoFiltro) * 100.0) / mediaComparecimentoFiltro);
            return new RelatorioGeralResponse.Variacao(percentual, BaseComparacao.COMPARECIMENTO);
        }
        if (mediaInscritosFiltro > 0) {
            double percentual = arredondar(((totalInscritosAtual - mediaInscritosFiltro) * 100.0) / mediaInscritosFiltro);
            return new RelatorioGeralResponse.Variacao(percentual, BaseComparacao.INSCRITOS);
        }
        return new RelatorioGeralResponse.Variacao(0.0, BaseComparacao.INSCRITOS);
    }
}
