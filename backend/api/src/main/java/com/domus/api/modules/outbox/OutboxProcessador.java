package com.domus.api.modules.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Polla a tabela `outbox` periodicamente e delega o processamento ao
 * {@link OutboxDrenador}. Aplica backoff exponencial entre polls quando a
 * fila está vazia para reduzir CU-h no Neon Free — sem backoff, cada poll
 * mantém o compute acordado indefinidamente.
 *
 * <p>Estratégia:
 * <ul>
 *   <li>{@code @Scheduled(fixedDelay)} — delay mínimo de {@code intervaloMinMs}
 *       (default 3s) entre o fim de uma execução e o início da próxima.</li>
 *   <li>Se a fila está vazia, {@link #pollOutbox()} aplica um backoff
 *       exponencial crescente (3s → 6s → 12s → ... até {@code intervaloMaxMs}
 *       = 5min) e dorme dentro do método antes de retornar.</li>
 *   <li>Como o sleep acontece DEPOIS do drenador ter commitado/rollbackado,
 *       não seguramos conexão JDBC aberta durante o sleep.</li>
 *   <li>Quando aparece trabalho, o contador de idle reseta e voltamos a
 *       pollar no intervalo mínimo — UX preservada.</li>
 * </ul>
 *
 * <p>Properties configuráveis:
 * <ul>
 *   <li>{@code app.outbox.intervalo-min-ms} (default 3000)</li>
 *   <li>{@code app.outbox.intervalo-max-ms} (default 300000)</li>
 *   <li>{@code app.outbox.delay-inicial-ms} (default 5000)</li>
 * </ul>
 *
 * <p>Antes desta mudança o intervalo era fixo em 30s, mas o painel
 * Monitoring → Query performance do Neon mostrou ~31 calls/min (1 a cada
 * 2s) para a query do outbox. A investigação revelou que o backoff estava
 * ausente e a fila fica vazia 99.99% do tempo em produção — causando
 * queries ociosas em massa que mantinham o Neon acordado.
 *
 * <p><strong>Por que cap em 5min:</strong> o Neon Free/Launch só suspende
 * o compute após 5min de inatividade. Se o cap do backoff for menor que
 * isso (ex.: 30s), cada poll reseta o timer de inatividade e o Neon
 * nunca suspende — a redução de CU-h prometida não acontece. Cap de 5min
 * iguala o threshold do Neon: em idle profundo, o compute SUSPENSA e
 * deixa de consumir CU-h até o próximo INSERT/UPDATE na app.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxProcessador {

    private final OutboxDrenador drenador;
    private final AtomicLong ciclosIdleConsecutivos = new AtomicLong(0);

    @Value("${app.outbox.intervalo-min-ms:3000}")
    private long intervaloMinMs;

    @Value("${app.outbox.intervalo-max-ms:300000}")
    private long intervaloMaxMs;

    @Scheduled(
            fixedDelayString = "${app.outbox.intervalo-min-ms:3000}",
            initialDelayString = "${app.outbox.delay-inicial-ms:5000}")
    public void pollOutbox() {
        long inicioMs = System.currentTimeMillis();
        int processados;
        try {
            processados = drenador.drenar();
        } catch (Exception e) {
            // Falha no dreno: tratar como idle para entrar em backoff.
            log.warn("Falha ao drenar outbox: {}. Aplicando backoff.", e.getMessage());
            processados = 0;
        }
        long duracaoMs = System.currentTimeMillis() - inicioMs;

        if (processados == 0) {
            long idle = ciclosIdleConsecutivos.incrementAndGet();
            long backoff = calcularBackoff(idle);
            if (deveLogarMarco(idle)) {
                log.info("Outbox ocioso há {} ciclo(s) (poll durou {}ms). Backoff {}ms (cap {}ms).",
                        idle, duracaoMs, backoff, intervaloMaxMs);
            } else {
                log.debug("Outbox idle (poll #{}, {}ms). Backoff {}ms.", idle, duracaoMs, backoff);
            }
            dormir(backoff);
        } else {
            long idleAnterior = ciclosIdleConsecutivos.getAndSet(0);
            if (idleAnterior > 0) {
                log.info("Outbox voltou a ter trabalho após {} ciclo(s) idle. Processou {} evento(s) em {}ms. Resetando backoff.",
                        idleAnterior, processados, duracaoMs);
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
