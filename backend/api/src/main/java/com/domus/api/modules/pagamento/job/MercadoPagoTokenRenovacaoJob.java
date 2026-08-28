package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.conta.MercadoPagoOAuthService;
import com.domus.api.modules.usuario.UsuarioRepository;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Renova sozinho o token do Mercado Pago antes de vencer — achado em revisão de segurança
 * (2026-08-26): sem isto, o token expirava (~180 dias) sem nenhum aviso, e todo pagamento
 * daquela igreja parava de funcionar silenciosamente até alguém notar e reconectar
 * manualmente. Roda uma vez por dia, renovando toda conta cujo token vence dentro de
 * {@link #MARGEM_RENOVACAO} — bem antes do vencimento de verdade, pra sobrar tempo de
 * tentar de novo no dia seguinte se a chamada ao Mercado Pago falhar por instabilidade.
 *
 * <p>Se a renovação falhar porque o PRÓPRIO refresh token também está vencido/revogado
 * (não é uma falha de rede, é a igreja ter ficado tempo demais sem que o job conseguisse
 * renovar — ex.: token e refresh token venceram juntos, ou a conta foi desconectada do
 * lado do Mercado Pago), não tem como automatizar mais nada: notifica o(s) admin(s) da
 * igreja pra reconectar a conta manualmente (mesmo fluxo de "Conectar conta" inicial).
 */
@Component
public class MercadoPagoTokenRenovacaoJob {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoTokenRenovacaoJob.class);

    /** Renova qualquer conta que vença dentro de 15 dias — margem generosa pro job ter
     *  várias tentativas diárias antes do vencimento de verdade. */
    private static final Duration MARGEM_RENOVACAO = Duration.ofDays(15);

    private final ContaPagamentoIgrejaRepository contaRepository;
    private final MercadoPagoOAuthService oauthService;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;

    public MercadoPagoTokenRenovacaoJob(ContaPagamentoIgrejaRepository contaRepository,
                                         MercadoPagoOAuthService oauthService,
                                         UsuarioRepository usuarioRepository,
                                         NotificacaoService notificacaoService) {
        this.contaRepository = contaRepository;
        this.oauthService = oauthService;
        this.usuarioRepository = usuarioRepository;
        this.notificacaoService = notificacaoService;
    }

    @Scheduled(cron = "0 0 6 * * *") // 06:00 todo dia — mesmo horário do backup do Postgres
    public void executar() {
        var contasParaRenovar = contaRepository.findByExpiraEmBefore(Instant.now().plus(MARGEM_RENOVACAO));
        for (ContaPagamentoIgreja conta : contasParaRenovar) {
            try {
                oauthService.renovarTokenDaConta(conta);
                log.info("Token do Mercado Pago renovado. igreja_id={}", conta.getIgrejaId());
            } catch (Exception e) {
                log.error("Falha ao renovar token do Mercado Pago — igreja precisa reconectar "
                    + "manualmente. igreja_id={}", conta.getIgrejaId(), e);
                notificarFalha(conta.getIgrejaId());
            }
        }
    }

    private void notificarFalha(java.util.UUID igrejaId) {
        var admins = usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA");
        for (var admin : admins) {
            notificacaoService.criar(
                TipoNotificacao.CONTA_PAGAMENTO_RECONEXAO_NECESSARIA,
                igrejaId,
                admin.getId(),
                "A conexão com o Mercado Pago expirou e precisa ser refeita — pagamentos de eventos vão parar de funcionar até reconectar.",
                "/configuracoes/igreja");
        }
    }
}
