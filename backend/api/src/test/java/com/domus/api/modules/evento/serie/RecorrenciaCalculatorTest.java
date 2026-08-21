package com.domus.api.modules.evento.serie;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciaCalculatorTest {

    @Test
    void diariaSomaIntervaloDeDias() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(2).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0);

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 8), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 3, 19, 0),
                LocalDateTime.of(2026, 9, 5, 19, 0),
                LocalDateTime.of(2026, 9, 7, 19, 0));
    }

    @Test
    void semanalRespeitaDiasDaSemanaEscolhidos() {
        // Serie de terça e quinta; última ocorrência foi terça 2026-09-01.
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.SEMANAL).intervalo(1)
                .diasSemana("TERCA,QUINTA").build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0); // terça

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 10), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 3, 19, 0),  // quinta
                LocalDateTime.of(2026, 9, 8, 19, 0),  // terça (semana seguinte)
                LocalDateTime.of(2026, 9, 10, 19, 0)); // quinta
    }

    @Test
    void semanalQuinzenalPulaUmaSemanaInteira() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.SEMANAL).intervalo(2)
                .diasSemana("QUINTA").build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 3, 19, 0); // quinta

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 24), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 17, 19, 0));
    }

    @Test
    void mensalDiaFixoMantemODiaDoMes() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.MENSAL).intervalo(1)
                .tipoRecorrenciaMensal(TipoRecorrenciaMensal.DIA_FIXO).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 8, 15, 19, 0);

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 10, 20), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 15, 19, 0),
                LocalDateTime.of(2026, 10, 15, 19, 0));
    }

    @Test
    void mensalDiaDaSemanaMantemAPosicao() {
        // 1ª terça de agosto/2026 é dia 04.
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.MENSAL).intervalo(1)
                .tipoRecorrenciaMensal(TipoRecorrenciaMensal.DIA_DA_SEMANA).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 8, 4, 19, 0); // 1ª terça de agosto

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 10, 10), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 1, 19, 0),  // 1ª terça de setembro
                LocalDateTime.of(2026, 10, 6, 19, 0)); // 1ª terça de outubro
    }

    @Test
    void respeitaNumeroDeOcorrenciasRestante() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1)
                .numeroOcorrencias(3).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0);

        // ocorrenciasJaGeradas=1 (a primeira, criada na hora do cadastro) — só faltam 2.
        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 30), 1);

        assertThat(proximas).hasSize(2);
    }

    @Test
    void respeitaDataFim() {
        EventoSerie serie = EventoSerie.builder()
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1)
                .dataFim(LocalDate.of(2026, 9, 4)).build();
        LocalDateTime ultima = LocalDateTime.of(2026, 9, 1, 19, 0);

        List<LocalDateTime> proximas = RecorrenciaCalculator.proximasDatas(
                serie, ultima, LocalDate.of(2026, 9, 30), 1);

        assertThat(proximas).containsExactly(
                LocalDateTime.of(2026, 9, 2, 19, 0),
                LocalDateTime.of(2026, 9, 3, 19, 0),
                LocalDateTime.of(2026, 9, 4, 19, 0));
    }
}
