package com.domus.api.modules.evento.serie;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Materializa ocorrências futuras de série numa janela móvel — mesmo padrão de
 *  ExclusaoIgrejaJob/LimpezaFotosJob (cron diário, de madrugada, depois do backup). */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventoSerieMaterializacaoJob {

    private static final int JANELA_DIAS = 60;

    private final EventoSerieRepository serieRepository;
    private final EventoRepository eventoRepository;

    @Scheduled(cron = "0 0 5 * * *")
    @Transactional
    public void materializar() {
        List<EventoSerie> series = serieRepository.findAll().stream()
                .filter(EventoSerie::isAtiva)
                .toList();
        LocalDate limite = LocalDate.now().plusDays(JANELA_DIAS);
        int totalCriadas = 0;

        for (EventoSerie serie : series) {
            totalCriadas += materializarSerie(serie, limite);
        }
        log.info("Materialização de séries concluída. series_processadas={}, ocorrencias_criadas={}",
                series.size(), totalCriadas);
    }

    private int materializarSerie(EventoSerie serie, LocalDate limite) {
        Evento ultima = eventoRepository
                .findTopBySerieIdAndDivergeDaSerieFalseOrderByInicioEmDesc(serie.getId())
                .orElse(null);
        if (ultima == null) return 0; // série sem nenhuma ocorrência não-divergente pra clonar

        List<LocalDateTime> proximasDatas = RecorrenciaCalculator.proximasDatas(
                serie, ultima.getInicioEm(), limite, 1);

        int criadas = 0;
        for (LocalDateTime data : proximasDatas) {
            if (eventoRepository.existsBySerieIdAndInicioEm(serie.getId(), data)) continue;
            eventoRepository.save(clonar(ultima, data));
            criadas++;
        }
        return criadas;
    }

    private Evento clonar(Evento origem, LocalDateTime novaData) {
        long duracaoMinutos = origem.getFimEm() == null ? -1
                : java.time.Duration.between(origem.getInicioEm(), origem.getFimEm()).toMinutes();
        return Evento.builder()
                .igreja(origem.getIgreja())
                .titulo(origem.getTitulo())
                .descricao(origem.getDescricao())
                .inicioEm(novaData)
                .fimEm(duracaoMinutos < 0 ? null : novaData.plusMinutes(duracaoMinutos))
                .local(origem.getLocal())
                .localTexto(origem.getLocalTexto())
                .tipo(origem.getTipo())
                .responsavel(origem.getResponsavel())
                .recorteEtario(origem.getRecorteEtario())
                .idadeMin(origem.getIdadeMin())
                .idadeMax(origem.getIdadeMax())
                .restricaoEstadoCivil(origem.getRestricaoEstadoCivil())
                .restricaoSexo(origem.getRestricaoSexo())
                .foto(origem.getFoto())
                .vagas(origem.getVagas())
                .preco(origem.getPreco())
                .exclusivoMembros(origem.isExclusivoMembros())
                .requerInscricao(origem.isRequerInscricao())
                .controlaPresenca(origem.isControlaPresenca())
                .restritoPropriaIgreja(origem.isRestritoPropriaIgreja())
                .serie(origem.getSerie())
                .divergeDaSerie(false)
                .build();
    }
}
