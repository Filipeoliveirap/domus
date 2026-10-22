package com.domus.api.modules.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Processa a tabela `outbox` e delega o processamento ao {@link OutboxDrenador}.
 *
 * <p>Estratégia híbrida reativa + heartbeat para Neon Free/Launch:
 * <ul>
 *   <li>Orientado a eventos: quando um serviço registra um evento via {@link OutboxRegistrador},
 *       um {@link OutboxItemSalvoEvent} é emitido e capturado via
 *       {@code @TransactionalEventListener(AFTER_COMMIT)}. Isso ativa {@code temTrabalhoPendente = true}.</li>
 *   <li>Enquanto houver eventos ({@code processados > 0}), o loop a cada 3s continua drenando até zerar a fila.</li>
 *   <li>Quando a fila zera ({@code processados == 0}), {@code temTrabalhoPendente} vira {@code false}.</li>
 *   <li>Em repouso, {@link #pollOutbox()} checa apenas a flag em memória e retorna imediatamente (0ms),
 *       sem abrir conexão JDBC nem executar queries SQL contra o Postgres, permitindo que o Neon entre em
 *       scale-to-zero (auto-suspend após 5min de inatividade).</li>
 *   <li>Heartbeat de segurança: a cada 6 horas (ou no startup), acorda uma única vez para garantir que nenhum
 *       registro pendente fique retido por eventuais falhas transitórias.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessador {

    private final OutboxDrenador drenador;
    private final AtomicBoolean temTrabalhoPendente = new AtomicBoolean(true);
    private final AtomicLong ciclosIdleConsecutivos = new AtomicLong(0);

    @Value("${app.outbox.intervalo-min-ms:3000}")
    private long intervaloMinMs;

    @Value("${app.outbox.intervalo-max-ms:600000}")
    private long intervaloMaxMs;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNovoEventoOutbox(OutboxItemSalvoEvent event) {
        notificarTrabalho();
    }

    public void notificarTrabalho() {
        temTrabalhoPendente.set(true);
        ciclosIdleConsecutivos.set(0);
    }

    @Scheduled(cron = "${app.outbox.heartbeat-cron:0 0 */6 * * *}")
    public void heartbeat() {
        log.debug("Heartbeat periódico do Outbox disparado.");
        temTrabalhoPendente.set(true);
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.intervalo-min-ms:3000}",
            initialDelayString = "${app.outbox.delay-inicial-ms:5000}")
    public void pollOutbox() {
        if (!temTrabalhoPendente.get()) {
            return;
        }

        long inicioMs = System.currentTimeMillis();
        int processados;
        try {
            processados = drenador.drenar();
        } catch (Exception e) {
            log.warn("Falha ao drenar outbox: {}. Aplicando backoff.", e.getMessage());
            processados = 0;
        }
        long duracaoMs = System.currentTimeMillis() - inicioMs;

        if (processados == 0) {
            temTrabalhoPendente.set(false);
            long idle = ciclosIdleConsecutivos.incrementAndGet();
            long backoff = calcularBackoff(idle);
            if (deveLogarMarco(idle)) {
                log.info("Outbox ocioso há {} ciclo(s) (poll durou {}ms). Fila vazia, entrando em repouso.",
                        idle, duracaoMs);
            } else {
                log.debug("Outbox idle (poll #{}, {}ms). Fila vazia.", idle, duracaoMs);
            }
        } else {
            long idleAnterior = ciclosIdleConsecutivos.getAndSet(0);
            if (idleAnterior > 0) {
                log.info("Outbox processou {} evento(s) em {}ms. Resetando backoff.",
                        processados, duracaoMs);
            } else {
                log.debug("Outbox processou {} evento(s) em {}ms.", processados, duracaoMs);
            }
        }
    }

    /**
     * Backoff exponencial com cap. Cresce em ciclo n:
     * {@code min(min * 2^(n-1), max)}.
     * Cap no shift de 20 (~17min) para não overflow.
     */
    long calcularBackoff(long ciclosIdle) {
        long shift = Math.min(ciclosIdle - 1, 20);
        long backoff = intervaloMinMs * (1L << shift);
        return Math.min(backoff, intervaloMaxMs);
    }

    /** Loga apenas pontos-chave pra não floodar os logs. */
    private boolean deveLogarMarco(long idle) {
        if (idle == 1) return true;                                  // 1ª idle
        if (idle == 2 || idle == 5 || idle == 10 || idle == 30) return true;
        if (idle == 60 || idle == 300 || idle == 600) return true;
        return idle % 1800 == 0;
    }

    private void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Sleep do backoff do outbox interrompido.");
        }
    }
}
