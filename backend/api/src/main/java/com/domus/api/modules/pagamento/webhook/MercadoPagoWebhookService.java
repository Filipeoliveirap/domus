package com.domus.api.modules.pagamento.webhook;

import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Confirma o pagamento de uma {@link CobrancaEvento} a partir do webhook do Mercado Pago,
 * depois de a assinatura já ter sido validada pelo controller.
 *
 * <p>Quando a cobrança é de um acompanhante (ver {@link CobrancaEvento#getAcompanhanteId()}),
 * quem pagou não necessariamente tem conta no Domus — quem precisa saber que "fulano pagou"
 * é quem inscreveu/gerou o link, ou seja {@link CobrancaEvento#getCriadoPorUsuarioId()}.
 * Quando a cobrança é do próprio titular ({@link CobrancaEvento#ehDoTitular()}), a pessoa que
 * pagou já está vendo o próprio status de inscrição mudar na hora — notificar o titular dele
 * mesmo seria ruído.
 */
@Service
public class MercadoPagoWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookService.class);

    /** Único status do Mercado Pago que confirma o pagamento de verdade (Critical 2). */
    private static final String STATUS_APROVADO = "approved";

    private final CobrancaEventoRepository cobrancaRepository;
    private final NotificacaoService notificacaoService;

    public MercadoPagoWebhookService(CobrancaEventoRepository cobrancaRepository,
                                      NotificacaoService notificacaoService) {
        this.cobrancaRepository = cobrancaRepository;
        this.notificacaoService = notificacaoService;
    }

    /**
     * Critical 2 (revisão final de branch): antes desta correção, este método marcava a
     * cobrança como PAGO incondicionalmente — PIX pendente, cartão recusado ou pagamento
     * cancelado confirmavam a cobrança do mesmo jeito que um pagamento de verdade aprovado.
     * Agora só confirma quando {@code status} é {@code "approved"}; qualquer outro valor
     * (ex.: {@code pending}, {@code in_process}, {@code rejected}, {@code cancelled},
     * {@code refunded}, {@code charged_back}) só loga e não muda nada — a cobrança
     * continua PENDENTE, esperando um webhook futuro (ex.: PIX que ainda vai ser pago) ou
     * expirando naturalmente.
     */
    public void confirmarPagamento(String cobrancaId, String mpPaymentId, String status) {
        if (!STATUS_APROVADO.equals(status)) {
            log.info("Webhook do Mercado Pago com status não aprovado, cobrança não confirmada. "
                + "cobrancaId={} mpPaymentId={} status={}", cobrancaId, mpPaymentId, status);
            return;
        }

        cobrancaRepository.findById(UUID.fromString(cobrancaId)).ifPresent(cobranca -> {
            cobranca.marcarComoPago(mpPaymentId);
            cobrancaRepository.save(cobranca);

            if (!cobranca.ehDoTitular()) {
                notificacaoService.criar(
                    TipoNotificacao.COBRANCA_EVENTO_PAGA,
                    cobranca.getIgrejaId(),
                    cobranca.getCriadoPorUsuarioId(),
                    "O pagamento foi confirmado.",
                    "/eventos/" + cobranca.getEventoId() + "/inscritos");
            }
        });
    }
}
