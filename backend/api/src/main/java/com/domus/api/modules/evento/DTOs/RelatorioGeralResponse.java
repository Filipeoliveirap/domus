package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.BaseComparacao;
import com.domus.api.shared.DTO.PagedResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RelatorioGeralResponse(
        Resumo resumo,
        EventoMaisPopular eventoMaisPopular,
        List<PontoTendencia> tendencia,
        /** Só esta lista pagina — resumo/eventoMaisPopular/tendencia usam o filtro inteiro. */
        PagedResponse<UltimoEvento> ultimosEventos
) {
    /** {@code null} (nunca {@code 0}) quando nenhum evento do filtro controla presença — zero mentiria "ninguém foi". */
    public record Resumo(long totalEventos, Double comparecimentoMedio, Long participantesUnicos) {}

    /** Sempre baseado em inscritos confirmados (pessoa+convidado) — funciona em QUALQUER evento. */
    public record EventoMaisPopular(UUID eventoId, String titulo, long totalInscritos) {}

    /** {@code mes} no formato ISO "aaaa-mm". {@code comparecimentoMedio} null = sem dado no mês. */
    public record PontoTendencia(String mes, Double comparecimentoMedio) {}

    /** {@code base} nunca implícita — o front sempre expõe qual foi usada (tooltip/click). */
    public record Variacao(double percentual, BaseComparacao base) {}

    public record UltimoEvento(
            UUID eventoId,
            String titulo,
            LocalDateTime data,
            long totalParticipantes,
            /** {@code null} quando não há evento anterior do mesmo tipo. */
            Variacao variacaoEventoAnterior,
            Variacao variacaoMediaGeral
    ) {}
}
