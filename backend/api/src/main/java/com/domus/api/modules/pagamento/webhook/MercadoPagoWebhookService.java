package com.domus.api.modules.pagamento.webhook;

import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
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

    /**
     * Status terminais do Mercado Pago que significam "esse pagamento não vai mais virar
     * approved" — pagamento recusado (cartão sem limite, dados errados, etc.) ou cancelado.
     * Encontra fix wave (2026-08-25): {@code CobrancaController.pagar} grava
     * {@code mpPaymentId} assim que o pagamento é CRIADO no Mercado Pago (Critical 5), pra
     * impedir uma segunda tentativa concorrente antes do webhook confirmar. Mas nada limpava
     * esse campo quando o pagamento terminava recusado — a cobrança ficava travada em
     * {@code COBRANCA_JA_EM_PROCESSAMENTO} até expirar sozinha (30min–48h depois), mesmo o
     * cenário (tentar outro cartão/PIX) sendo legítimo e comum. Não inclui {@code pending}/
     * {@code in_process}: esses ainda podem virar {@code approved} depois, então liberar
     * retry ali criaria pagamento duplicado pro mesmo PIX pendente.
     */
    private static final java.util.Set<String> STATUS_TERMINAL_NAO_APROVADO =
        java.util.Set.of("rejected", "cancelled");

    private final CobrancaEventoRepository cobrancaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final NotificacaoService notificacaoService;

    public MercadoPagoWebhookService(CobrancaEventoRepository cobrancaRepository,
                                      InscricaoRepository inscricaoRepository,
                                      NotificacaoService notificacaoService) {
        this.cobrancaRepository = cobrancaRepository;
        this.inscricaoRepository = inscricaoRepository;
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

            if (STATUS_TERMINAL_NAO_APROVADO.contains(status)) {
                cobrancaRepository.findById(UUID.fromString(cobrancaId)).ifPresent(cobranca -> {
                    cobranca.liberarParaNovaTentativa();
                    cobrancaRepository.save(cobranca);
                });
            }
            return;
        }

        cobrancaRepository.findById(UUID.fromString(cobrancaId)).ifPresent(cobranca -> {
            cobranca.marcarComoPago(mpPaymentId);
            cobrancaRepository.save(cobranca);

            // A inscrição só confirma quando o pagamento é aprovado de verdade — ver
            // InscricaoService.inscreverInterno, que a cria como AGUARDANDO_PAGAMENTO.
            inscricaoRepository.findById(cobranca.getInscricaoId()).ifPresent(inscricao -> {
                inscricao.setStatus(StatusInscricao.CONFIRMADA);
                inscricaoRepository.save(inscricao);
            });

            // Plano 4b — convidado sem cadastro via convite público não tem
            // criadoPorUsuarioId (inscritoPorUsuarioId=null): não há quem notificar.
            if (!cobranca.ehDoTitular() && cobranca.getCriadoPorUsuarioId() != null) {
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
