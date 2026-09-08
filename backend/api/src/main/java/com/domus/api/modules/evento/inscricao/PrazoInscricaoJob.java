package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Notificações in-app ligadas ao prazo de inscrição (V38). Roda diariamente às 06:30
 *  (depois da renovação de token do Mercado Pago). Sem fila — segue o padrão de
 *  {@code CobrancaEventoExpiracaoJob}. Fininho de propósito: itera os eventos e delega
 *  cada um ao {@link PrazoInscricaoProcessador} (transação própria por evento); uma falha
 *  num evento é logada e não interrompe os demais. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrazoInscricaoJob {

    private final EventoRepository eventoRepository;
    private final PrazoInscricaoProcessador processador;

    @Scheduled(cron = "0 30 6 * * *")
    public void executar() {
        LocalDateTime agora = LocalDateTime.now();
        for (Evento evento : eventoRepository.buscarComPrazoAtivoParaJob(agora)) {
            try {
                processador.processar(evento.getId(), agora);
            } catch (Exception e) {
                log.error("Falha ao processar avisos de prazo do evento {} — seguindo", evento.getId(), e);
            }
        }
    }
}
