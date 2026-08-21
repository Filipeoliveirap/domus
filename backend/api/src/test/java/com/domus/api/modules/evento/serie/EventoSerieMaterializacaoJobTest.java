package com.domus.api.modules.evento.serie;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EventoSerieMaterializacaoJobTest {

    EventoSerieRepository serieRepository;
    EventoRepository eventoRepository;
    EventoSerieMaterializacaoJob job;

    UUID igrejaId = UUID.randomUUID();
    UUID serieId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        serieRepository = mock(EventoSerieRepository.class);
        eventoRepository = mock(EventoRepository.class);
        job = new EventoSerieMaterializacaoJob(serieRepository, eventoRepository);
    }

    @Test
    void materializaOcorrenciaNovaQuandoNaoExisteAindaParaAData() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        EventoSerie serie = EventoSerie.builder()
                .id(serieId).igreja(igreja)
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1).build();
        when(serieRepository.findAll()).thenReturn(List.of(serie));

        Evento ultima = Evento.builder()
                .igreja(igreja).titulo("Culto").inicioEm(LocalDateTime.now().minusDays(1))
                .serie(serie).divergeDaSerie(false).build();
        when(eventoRepository.findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(serieId))
                .thenReturn(Optional.of(ultima));
        when(eventoRepository.existsBySerieIdAndInicioEm(eq(serieId), any())).thenReturn(false);
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        job.materializar();

        verify(eventoRepository, atLeastOnce()).save(argThat(e ->
                e.getSerie() != null && e.getSerie().getId().equals(serieId)
                        && e.getTitulo().equals("Culto") && !e.isDivergeDaSerie()));
    }

    @Test
    void naoMaterializaDataQueJaExisteParaASerie() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        EventoSerie serie = EventoSerie.builder()
                .id(serieId).igreja(igreja)
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1).build();
        when(serieRepository.findAll()).thenReturn(List.of(serie));

        Evento ultima = Evento.builder()
                .igreja(igreja).titulo("Culto").inicioEm(LocalDateTime.now().minusDays(1))
                .serie(serie).divergeDaSerie(false).build();
        when(eventoRepository.findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(serieId))
                .thenReturn(Optional.of(ultima));
        // Toda data já existe (inclusive as arquivadas) — nada deve ser materializado.
        when(eventoRepository.existsBySerieIdAndInicioEm(eq(serieId), any())).thenReturn(true);

        job.materializar();

        verify(eventoRepository, never()).save(any());
    }

    @Test
    void ignoraSerieInativa() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        EventoSerie serieInativa = EventoSerie.builder()
                .id(serieId).igreja(igreja)
                .frequencia(FrequenciaRecorrencia.DIARIA).intervalo(1).ativa(false).build();
        when(serieRepository.findAll()).thenReturn(List.of(serieInativa));

        job.materializar();

        verify(eventoRepository, never()).findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(any());
        verify(eventoRepository, never()).save(any());
    }
}
