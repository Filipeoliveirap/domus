package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.NovaCobrancaCriadaEvent;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira cobrança de evento pago vencida, liberando a vaga, e cancela a inscrição
 * vinculada (que nasceu AGUARDANDO_PAGAMENTO — ninguém pagou a tempo).
 *
 * <p>Otimização para Neon Free/Launch:
 * <ul>
 *   <li>Mantém {@code temCobrancasPendentes} em memória.</li>
 *   <li>Se não há cobranças com status PENDENTE no banco, o timer a cada 15min checa apenas
 *       a flag na memória e retorna em 0ms sem abrir conexão JDBC ou executar SQL,
 *       permitindo que o Postgres suspenda (scale-to-zero).</li>
 *   <li>Quando uma cobrança nova é criada (capturada por {@link NovaCobrancaCriadaEvent}),
 *       a flag ativa e o job volta a inspecionar até todas estarem pagas/canceladas/expiradas.</li>
 *   <li>Heartbeat de segurança a cada 6 horas reativa a flag para conferência preventiva.</li>
 * </ul>
 */
@Component
public class CobrancaEventoExpiracaoJob {

    private final CobrancaEventoRepository repository;
    private final InscricaoRepository inscricaoRepository;
    private final AtomicBoolean temCobrancasPendentes = new AtomicBoolean(true);

    public CobrancaEventoExpiracaoJob(CobrancaEventoRepository repository,
                                       InscricaoRepository inscricaoRepository) {
        this.repository = repository;
        this.inscricaoRepository = inscricaoRepository;
    }

    @EventListener
    public void onNovaCobranca(NovaCobrancaCriadaEvent event) {
        notificarNovaCobranca();
    }

    public void notificarNovaCobranca() {
        this.temCobrancasPendentes.set(true);
    }

    @Scheduled(cron = "${app.pagamento.expiracao-job.heartbeat-cron:0 30 */6 * * *}")
    public void heartbeat() {
        this.temCobrancasPendentes.set(true);
    }

    @Scheduled(
            fixedDelayString = "${app.pagamento.expiracao-job.intervalo-ms:900000}",
            initialDelayString = "60000")
    public void executar() {
        if (!temCobrancasPendentes.get()) {
            return;
        }

        var vencidas = repository.findByStatusAndExpiraEmBefore(StatusCobranca.PENDENTE, Instant.now());
        if (!vencidas.isEmpty()) {
            vencidas.forEach(CobrancaEvento::marcarComoExpirado);
            repository.saveAll(vencidas);

            var inscricaoIds = vencidas.stream().map(CobrancaEvento::getInscricaoId).toList();
            var inscricoes = inscricaoRepository.findAllById(inscricaoIds).stream()
                    .filter(InscricaoEvento::estaAguardandoPagamento)
                    // [C1] quem já pagou o valor original e só deve um complemento de reajuste
                    // NÃO perde a vaga aqui — o complemento fica pendente na lista de inscritos
                    // ("Falta complementar") até o gestor decidir manter/lembrar/remover.
                    .filter(i -> !repository.existePagaParaInscricao(i.getId()))
                    .toList();
            if (!inscricoes.isEmpty()) {
                inscricoes.forEach(i -> i.setStatus(StatusInscricao.CANCELADA));
                inscricaoRepository.saveAll(inscricoes);
            }
        }

        if (!repository.existsByStatus(StatusCobranca.PENDENTE)) {
            temCobrancasPendentes.set(false);
        }
    }
}
