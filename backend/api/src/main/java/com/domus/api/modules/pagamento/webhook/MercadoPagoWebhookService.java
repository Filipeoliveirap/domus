package com.domus.api.modules.pagamento.webhook;

import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import java.util.UUID;
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

    private final CobrancaEventoRepository cobrancaRepository;
    private final NotificacaoService notificacaoService;

    public MercadoPagoWebhookService(CobrancaEventoRepository cobrancaRepository,
                                      NotificacaoService notificacaoService) {
        this.cobrancaRepository = cobrancaRepository;
        this.notificacaoService = notificacaoService;
    }

    public void confirmarPagamento(String cobrancaId, String mpPaymentId) {
        cobrancaRepository.findById(UUID.fromString(cobrancaId)).ifPresent(cobranca -> {
            cobranca.marcarComoPago(mpPaymentId);
            cobrancaRepository.save(cobranca);

            if (!cobranca.ehDoTitular()) {
                notificacaoService.criar(
                    TipoNotificacao.COBRANCA_EVENTO_PAGA,
                    cobranca.getIgrejaId(),
                    cobranca.getCriadoPorUsuarioId(),
                    "O pagamento do convidado foi confirmado.",
                    "/eventos/" + cobranca.getEventoId() + "/inscritos");
            }
        });
    }
}
