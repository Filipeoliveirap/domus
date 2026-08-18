package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Roda uma vez por dia: lembra em D-5 e D-1, e (a partir da Fase 2) executa a purga em D-10. */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExclusaoIgrejaJob {

    private final IgrejaRepository igrejaRepository;
    private final EmailService emailService;
    private final PurgaIgrejaService purgaIgrejaService;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void verificarPrazos() {
        var igrejas = igrejaRepository.buscarComExclusaoAgendada();
        int lembretes = 0;

        for (Igreja igreja : igrejas) {
            long decorridos = ChronoUnit.DAYS.between(igreja.getExclusaoAgendadaEm(), LocalDateTime.now());
            long faltam = 10 - decorridos;

            if (faltam == 5) {
                emailService.enviar(igreja.getEmailContato(),
                        "Sua igreja será excluída em 5 dias",
                        "Faltam 5 dias para a exclusão definitiva de \"" + igreja.getNome() + "\". "
                                + "Cancele em Configurações → Sistema, se quiser manter sua conta.");
                lembretes++;
            } else if (faltam == 1) {
                emailService.enviar(igreja.getEmailContato(),
                        "Sua igreja será excluída em 1 dia",
                        "Falta 1 dia para a exclusão definitiva de \"" + igreja.getNome() + "\". "
                                + "Cancele em Configurações → Sistema, se quiser manter sua conta.");
                lembretes++;
            }
        }

        log.info("Verificação diária de exclusões agendadas concluída. igrejas_agendadas={}, lembretes_enviados={}",
                igrejas.size(), lembretes);
    }
}
