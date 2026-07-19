package com.domus.api.modules.igreja.familia.consolidado;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse;
import com.domus.api.modules.igreja.familia.consolidado.DTO.ConsolidadoResponse.*;
import com.domus.api.modules.membro.StatusMembro;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monta a visão geral da família: 3 consultas agregadas + montagem em memória.
 *
 * <p><b>Semântica dos períodos</b> (proposital, e diferente entre os blocos):
 * o financeiro respeita o período escolhido na tela; membros e eventos são a
 * <b>fotografia de agora</b> — quantos membros existem hoje, quantos eventos já
 * aconteceram e quantos ainda vêm.
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

        Map<UUID, Membros> membrosPorIgreja = agruparMembros(idsDaFamilia);
        Map<UUID, Eventos> eventosPorIgreja = agruparEventos(idsDaFamilia, agora);
        Map<UUID, Financeiro> financeiroPorIgreja = agruparFinanceiro(idsDaFamilia, dataInicio, dataFim);

        // Os nomes numa tacada só — buscar igreja a igreja dentro do laço seria um N+1,
        // exatamente o que as 3 consultas agregadas acima evitaram.
        Map<UUID, String> nomes = new HashMap<>();
        igrejaRepository.findAllById(idsDaFamilia).forEach(i -> nomes.put(i.getId(), i.getNome()));

        List<LinhaIgreja> linhas = new ArrayList<>();
        for (UUID id : idsDaFamilia) {
            linhas.add(new LinhaIgreja(
                    id,
                    nomes.getOrDefault(id, "—"),
                    id.equals(igrejaSolicitanteId),
                    membrosPorIgreja.getOrDefault(id, membrosZerado()),
                    eventosPorIgreja.getOrDefault(id, new Eventos(0, 0, 0)),
                    financeiroPorIgreja.getOrDefault(id, financeiroZerado())));
        }

        return new ConsolidadoResponse(somar(linhas), linhas);
    }

    private Map<UUID, Membros> agruparMembros(List<UUID> ids) {
        // A consulta devolve uma linha por (igreja, status); aqui viram os 3 números de cada igreja.
        Map<UUID, long[]> acumulador = new HashMap<>();
        for (var linha : repository.contarMembros(ids)) {
            long[] contagens = acumulador.computeIfAbsent(linha.getIgrejaId(), k -> new long[3]);
            StatusMembro status = StatusMembro.valueOf(linha.getStatus());
            contagens[status.ordinal()] += linha.getTotal();
        }

        Map<UUID, Membros> resultado = new HashMap<>();
        acumulador.forEach((id, c) -> {
            long ativos = c[StatusMembro.ATIVO.ordinal()];
            long inativos = c[StatusMembro.INATIVO.ordinal()];
            long visitantes = c[StatusMembro.VISITANTE.ordinal()];
            resultado.put(id, new Membros(ativos + inativos + visitantes, ativos, inativos, visitantes));
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
        long membrosAtivos = 0, membrosInativos = 0, membrosVisitantes = 0;
        long eventosRealizados = 0, eventosProximos = 0;
        BigDecimal entradas = BigDecimal.ZERO, saidas = BigDecimal.ZERO;

        for (LinhaIgreja l : linhas) {
            membrosAtivos += l.membros().ativos();
            membrosInativos += l.membros().inativos();
            membrosVisitantes += l.membros().visitantes();
            eventosRealizados += l.eventos().realizados();
            eventosProximos += l.eventos().proximos();
            entradas = entradas.add(l.financeiro().entradas());
            saidas = saidas.add(l.financeiro().saidas());
        }

        return new Totais(
                new Membros(membrosAtivos + membrosInativos + membrosVisitantes,
                        membrosAtivos, membrosInativos, membrosVisitantes),
                new Eventos(eventosRealizados + eventosProximos, eventosRealizados, eventosProximos),
                new Financeiro(entradas, saidas, entradas.subtract(saidas)));
    }

    private Membros membrosZerado() {
        return new Membros(0, 0, 0, 0);
    }

    private Financeiro financeiroZerado() {
        return new Financeiro(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
