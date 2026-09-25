package com.domus.api.modules.contapagar;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Calcula próximas datas de vencimento de uma conta recorrente.
 *  Reaproveita a ideia do {@code RecorrenciaCalculator} de EventoSerie,
 *  mas para o domínio mais simples de conta a pagar (só frequências fixas). */
public class RecorrenciaCalculator {

    private RecorrenciaCalculator() {}

    /** Gera as próximas datas a partir de uma conta geradora.
     *
     * @param frequencia periodicidade
     * @param ultimaData data de referência (último vencimento gerado)
     * @param limite até quando gerar
     * @param vezes número máximo de ocorrências (null = sem limite)
     * @param ate data máxima (null = sem limite)
     * @param diaAncora dia do mês desejado (1-28); null = usa o dia da data de referência
     * @return datas ordenadas
     */
    public static List<LocalDate> proximasDatas(RecorrenciaFrequencia frequencia,
                                                  LocalDate ultimaData,
                                                  LocalDate limite,
                                                  Integer vezes,
                                                  LocalDate ate,
                                                  Integer diaAncora) {
        List<LocalDate> resultado = new ArrayList<>();
        LocalDate atual = ultimaData;
        int geradas = 0;

        while (true) {
            atual = proxima(frequencia, atual, diaAncora);
            if (atual.isAfter(limite)) break;
            if (ate != null && atual.isAfter(ate)) break;
            if (vezes != null && geradas >= vezes) break;
            resultado.add(atual);
            geradas++;
        }
        return resultado;
    }

    private static LocalDate proxima(RecorrenciaFrequencia frequencia, LocalDate de, Integer diaAncora) {
        LocalDate proximo = switch (frequencia) {
            case MENSAL -> de.plusMonths(1);
            case TRIMESTRAL -> de.plusMonths(3);
            case SEMESTRAL -> de.plusMonths(6);
            case ANUAL -> de.plusYears(1);
        };
        if (diaAncora != null && diaAncora != proximo.getDayOfMonth()) {
            // Reposiciona para o dia âncora, mas nunca passa do dia 28
            int maxDia = Math.min(diaAncora, proximo.lengthOfMonth());
            return proximo.withDayOfMonth(maxDia);
        }
        return proximo;
    }
}
