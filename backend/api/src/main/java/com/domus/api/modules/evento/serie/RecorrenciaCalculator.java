package com.domus.api.modules.evento.serie;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Calcula as próximas datas de uma série, sem tocar banco — usado pelo job de
 *  materialização e, na criação, só pra validar que a regra gera pelo menos uma data. */
public class RecorrenciaCalculator {

    private RecorrenciaCalculator() {}

    public static List<LocalDateTime> proximasDatas(EventoSerie serie, LocalDateTime ultimaOcorrencia,
                                                      LocalDate limiteJanela, int ocorrenciasJaGeradas) {
        List<LocalDateTime> resultado = new ArrayList<>();
        LocalDateTime atual = ultimaOcorrencia;
        int geradas = ocorrenciasJaGeradas;

        while (true) {
            atual = proxima(serie, atual);
            if (atual.toLocalDate().isAfter(limiteJanela)) break;
            if (serie.getDataFim() != null && atual.toLocalDate().isAfter(serie.getDataFim())) break;
            if (serie.getNumeroOcorrencias() != null && geradas >= serie.getNumeroOcorrencias()) break;
            resultado.add(atual);
            geradas++;
        }
        return resultado;
    }

    private static LocalDateTime proxima(EventoSerie serie, LocalDateTime de) {
        return switch (serie.getFrequencia()) {
            case DIARIA -> de.plusDays(serie.getIntervalo());
            case SEMANAL -> proximaSemanal(serie, de);
            case MENSAL -> proximaMensal(serie, de);
        };
    }

    private static LocalDateTime proximaSemanal(EventoSerie serie, LocalDateTime de) {
        Set<DayOfWeek> dias = paraDayOfWeek(serie.getDiasSemanaComoSet());
        LocalDateTime candidata = de.plusDays(1);
        while (!dias.contains(candidata.getDayOfWeek())
                || semanasEntre(de, candidata) % serie.getIntervalo() != 0) {
            candidata = candidata.plusDays(1);
        }
        return candidata;
    }

    /** Conta em semanas ISO cheias a partir da última ocorrência gerada. */
    private static long semanasEntre(LocalDateTime origem, LocalDateTime candidata) {
        return java.time.temporal.ChronoUnit.WEEKS.between(
                origem.toLocalDate().with(DayOfWeek.MONDAY),
                candidata.toLocalDate().with(DayOfWeek.MONDAY));
    }

    private static LocalDateTime proximaMensal(EventoSerie serie, LocalDateTime de) {
        LocalDateTime proximoMes = de.plusMonths(serie.getIntervalo());
        if (serie.getTipoRecorrenciaMensal() == TipoRecorrenciaMensal.DIA_FIXO) {
            return proximoMes;
        }
        // DIA_DA_SEMANA: mesma posição (1ª, 2ª...) do mesmo dia da semana de `de`.
        DayOfWeek diaDaSemana = de.getDayOfWeek();
        int posicao = (de.getDayOfMonth() - 1) / 7 + 1;
        LocalDate primeiroDoMes = proximoMes.toLocalDate().withDayOfMonth(1);
        LocalDate resultado = primeiroDoMes.with(TemporalAdjusters.dayOfWeekInMonth(posicao, diaDaSemana));
        return resultado.atTime(de.toLocalTime());
    }

    private static Set<DayOfWeek> paraDayOfWeek(Set<com.domus.api.modules.celula.DiaSemana> dias) {
        Set<DayOfWeek> resultado = new java.util.HashSet<>();
        for (var d : dias) {
            resultado.add(switch (d) {
                case SEGUNDA -> DayOfWeek.MONDAY;
                case TERCA -> DayOfWeek.TUESDAY;
                case QUARTA -> DayOfWeek.WEDNESDAY;
                case QUINTA -> DayOfWeek.THURSDAY;
                case SEXTA -> DayOfWeek.FRIDAY;
                case SABADO -> DayOfWeek.SATURDAY;
                case DOMINGO -> DayOfWeek.SUNDAY;
            });
        }
        return resultado;
    }
}
