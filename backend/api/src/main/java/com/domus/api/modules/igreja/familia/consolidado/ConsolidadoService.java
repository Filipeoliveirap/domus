package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse;
import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse.*;
import com.domus.api.modules.pessoa.Vinculo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta a visão geral da família: 3 consultas agregadas + montagem em memória.
 * Financeiro respeita o período escolhido; membros e eventos são fotografia de agora.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ConsolidadoService {

    private final ConsolidadoRepository repository;
    private final IgrejaRepository igrejaRepository;
    private final FamiliaIgrejaService familiaService;

    @Transactional(readOnly = true)
    public ConsolidadoResponse gerar(UUID igrejaSolicitanteId, LocalDate dataInicio, LocalDate dataFim) {
        List<UUID> idsDaFamilia = familiaService.idsDaFamilia(igrejaSolicitanteId);
        log.info("Gerando consolidado da família. igreja_id={}, igrejas_na_familia={}, periodo={} a {}",
                igrejaSolicitanteId, idsDaFamilia.size(), dataInicio, dataFim);

        LocalDateTime agora = LocalDateTime.now();

        Map<UUID, Pessoas> membrosPorIgreja = agruparMembros(idsDaFamilia);
        Map<UUID, Eventos> eventosPorIgreja = agruparEventos(idsDaFamilia, agora);
        Map<UUID, Financeiro> financeiroPorIgreja = agruparFinanceiro(idsDaFamilia, dataInicio, dataFim);

        // findAllById em vez de findById por igreja — evita N+1 no laço abaixo.
        Map<UUID, String> nomes = new HashMap<>();
        igrejaRepository.findAllById(idsDaFamilia).forEach(i -> nomes.put(i.getId(), i.getNome()));

        List<LinhaIgreja> linhas = new ArrayList<>();
        for (UUID id : idsDaFamilia) {
            linhas.add(new LinhaIgreja(
                    id,
                    nomes.getOrDefault(id, "—"),
                    id.equals(igrejaSolicitanteId),
                    membrosPorIgreja.getOrDefault(id, pessoasZerado()),
                    eventosPorIgreja.getOrDefault(id, new Eventos(0, 0, 0)),
                    financeiroPorIgreja.getOrDefault(id, financeiroZerado())));
        }

        return new ConsolidadoResponse(somar(linhas), linhas);
    }

    private Map<UUID, Pessoas> agruparMembros(List<UUID> ids) {
        // EnumMap<Vinculo, Long> em vez de array indexado por ordinal — um novo vínculo não
        // quebraria índices fixos sem aviso do compilador.
        Map<UUID, Map<Vinculo, Long>> acumulador = new HashMap<>();
        for (var linha : repository.contarMembros(ids)) {
            Map<Vinculo, Long> contagens = acumulador.computeIfAbsent(
                    linha.getIgrejaId(), k -> new EnumMap<>(Vinculo.class));
            Vinculo vinculo = Vinculo.valueOf(linha.getVinculo());
            contagens.merge(vinculo, linha.getTotal(), Long::sum);
        }

        Map<UUID, Pessoas> resultado = new HashMap<>();
        acumulador.forEach((id, contagens) -> {
            long membros = contagens.getOrDefault(Vinculo.MEMBRO, 0L);
            long congregantes = contagens.getOrDefault(Vinculo.CONGREGANTE, 0L);
            resultado.put(id, new Pessoas(membros + congregantes, membros, congregantes));
        });
        return resultado;
    }

    private Map<UUID, Eventos> agruparEventos(List<UUID> ids, LocalDateTime agora) {
        Map<UUID, Eventos> resultado = new HashMap<>();
        for (var linha : repository.contarEventos(ids, agora)) {
            long realizados = linha.getRealizados();
            long proximos = linha.getProximos();
            resultado.put(linha.getIgrejaId(), new Eventos(realizados + proximos, realizados, proximos));
        }
        return resultado;
    }

    private Map<UUID, Financeiro> agruparFinanceiro(List<UUID> ids, LocalDate inicio, LocalDate fim) {
        Map<UUID, Financeiro> resultado = new HashMap<>();
        for (var linha : repository.agregarFinanceiro(ids, inicio, fim)) {
            BigDecimal entradas = linha.getEntradas();
            BigDecimal saidas = linha.getSaidas();
            resultado.put(linha.getIgrejaId(), new Financeiro(entradas, saidas, entradas.subtract(saidas)));
        }
        return resultado;
    }

    /** O consolidado é mãe + filhas — a mãe opera, não pode ficar de fora da soma. */
    private Totais somar(List<LinhaIgreja> linhas) {
        long membrosTotal = 0, congregantesTotal = 0;
        long eventosRealizados = 0, eventosProximos = 0;
        BigDecimal entradas = BigDecimal.ZERO, saidas = BigDecimal.ZERO;

        for (LinhaIgreja l : linhas) {
            membrosTotal += l.pessoas().membros();
            congregantesTotal += l.pessoas().congregantes();
            eventosRealizados += l.eventos().realizados();
            eventosProximos += l.eventos().proximos();
            entradas = entradas.add(l.financeiro().entradas());
            saidas = saidas.add(l.financeiro().saidas());
        }

        return new Totais(
                new Pessoas(membrosTotal + congregantesTotal, membrosTotal, congregantesTotal),
                new Eventos(eventosRealizados + eventosProximos, eventosRealizados, eventosProximos),
                new Financeiro(entradas, saidas, entradas.subtract(saidas)));
    }

    private Pessoas pessoasZerado() {
        return new Pessoas(0, 0, 0);
    }

    private Financeiro financeiroZerado() {
        return new Financeiro(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
