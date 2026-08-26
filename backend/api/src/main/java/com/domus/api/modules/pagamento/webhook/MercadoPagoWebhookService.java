package com.domus.api.modules.pagamento.webhook;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.AcompanhanteRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.email.EmailService;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

    private static final DateTimeFormatter FORMATADOR_DATA =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", new Locale("pt", "BR"));

    private final CobrancaEventoRepository cobrancaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final NotificacaoService notificacaoService;
    private final EventoRepository eventoRepository;
    private final PessoaRepository pessoaRepository;
    private final EmailService emailService;
    private final MovimentacaoAutomaticaService movimentacaoAutomaticaService;
    private final AcompanhanteRepository acompanhanteRepository;

    public MercadoPagoWebhookService(CobrancaEventoRepository cobrancaRepository,
                                      InscricaoRepository inscricaoRepository,
                                      NotificacaoService notificacaoService,
                                      EventoRepository eventoRepository,
                                      PessoaRepository pessoaRepository,
                                      EmailService emailService,
                                      MovimentacaoAutomaticaService movimentacaoAutomaticaService,
                                      AcompanhanteRepository acompanhanteRepository) {
        this.cobrancaRepository = cobrancaRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.notificacaoService = notificacaoService;
        this.eventoRepository = eventoRepository;
        this.pessoaRepository = pessoaRepository;
        this.emailService = emailService;
        this.movimentacaoAutomaticaService = movimentacaoAutomaticaService;
        this.acompanhanteRepository = acompanhanteRepository;
    }

    /**
     * Ponto de entrada único, chamado tanto pelo webhook quanto pelo poll ativo
     * ({@code PagamentoPollingService}) — os dois correm em paralelo pra resolver quem
     * confirmar primeiro, o que só é seguro porque este método é idempotente (guard logo
     * abaixo): quem chegar depois de a cobrança já ter saído de PENDENTE não repete nenhum
     * efeito colateral (e-mail em duplicidade, notificação em duplicidade). Mesma guarda
     * protege contra o Mercado Pago reenviar o mesmo webhook mais de uma vez, o que é
     * documentado e acontece.
     *
     * <p>Critical 2 (revisão final de branch): só confirma quando {@code status} é
     * {@code "approved"}; qualquer outro valor (ex.: {@code pending}, {@code in_process},
     * {@code rejected}, {@code cancelled}, {@code refunded}, {@code charged_back}) só loga e
     * não muda nada — a cobrança continua PENDENTE, esperando uma confirmação futura ou
     * expirando naturalmente.
     */
    public void confirmarPagamento(String cobrancaId, String mpPaymentId, String status) {
        var cobranca = cobrancaRepository.findById(UUID.fromString(cobrancaId)).orElse(null);
        if (cobranca == null || cobranca.getStatus() != StatusCobranca.PENDENTE) {
            log.info("Confirmação de pagamento ignorada — cobrança já resolvida ou inexistente. "
                + "cobrancaId={} mpPaymentId={} status={}", cobrancaId, mpPaymentId, status);
            return;
        }

        if (!STATUS_APROVADO.equals(status)) {
            log.info("Webhook do Mercado Pago com status não aprovado, cobrança não confirmada. "
                + "cobrancaId={} mpPaymentId={} status={}", cobrancaId, mpPaymentId, status);

            if (STATUS_TERMINAL_NAO_APROVADO.contains(status)) {
                cobranca.liberarParaNovaTentativa();
                cobrancaRepository.save(cobranca);
            }
            return;
        }

        cobranca.marcarComoPago(mpPaymentId);
        cobrancaRepository.save(cobranca);

        // A inscrição só confirma quando o pagamento é aprovado de verdade — ver
        // InscricaoService.inscreverInterno, que a cria como AGUARDANDO_PAGAMENTO.
        inscricaoRepository.findById(cobranca.getInscricaoId()).ifPresent(inscricao -> {
            inscricao.setStatus(StatusInscricao.CONFIRMADA);
            inscricaoRepository.save(inscricao);
            enviarEmailConfirmacao(cobranca, inscricao);
            registrarNoFinanceiro(cobranca, inscricao);
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
    }

    /**
     * Nunca falha o pagamento por causa do financeiro — o registro aqui é um "a mais", a
     * cobrança já está PAGO de verdade independente disso. Falha só loga, mesmo padrão do
     * e-mail de confirmação.
     */
    private void registrarNoFinanceiro(CobrancaEvento cobranca, InscricaoEvento inscricao) {
        try {
            Evento evento = eventoRepository.findById(cobranca.getEventoId()).orElse(null);
            if (evento == null) return;

            String nomePagador = resolverNomePagador(cobranca, inscricao);
            movimentacaoAutomaticaService.registrarEntradaDeEvento(
                cobranca.getIgrejaId(), cobranca.getValor(),
                "Pagamento de inscrição — " + evento.getTitulo() + " (" + nomePagador + ")",
                cobranca.getPessoaId());
        } catch (RuntimeException e) {
            log.error("Falha ao registrar movimentação financeira do pagamento. cobrancaId={}", cobranca.getId(), e);
        }
    }

    private String resolverNomePagador(CobrancaEvento cobranca, InscricaoEvento inscricao) {
        if (cobranca.getPessoaId() != null) {
            return pessoaRepository.findById(cobranca.getPessoaId())
                .map(Pessoa::getNome)
                .orElse("pagador removido");
        }
        if (cobranca.getAcompanhanteId() != null) {
            return acompanhanteRepository.findById(cobranca.getAcompanhanteId())
                .map(com.domus.api.modules.evento.inscricao.AcompanhanteInscricao::getNome)
                .orElse("acompanhante");
        }
        return inscricao.getNomeConvidado();
    }

    /**
     * Confirmação de pagamento por e-mail — só pra evento pago (o único jeito de chegar
     * aqui) e só quando existe um e-mail pra mandar: pessoa cadastrada sempre tem (campo
     * único no domínio); acompanhante nunca tem (modelo antigo, sem e-mail); convidado sem
     * cadastro tem desde que o e-mail virou obrigatório em evento pago (ver
     * {@code InscricaoService.inscreverConvidado}). Falha de envio nunca pode quebrar a
     * confirmação do pagamento em si — só loga, mesmo padrão de
     * {@code PasswordResetService}.
     */
    private void enviarEmailConfirmacao(CobrancaEvento cobranca, InscricaoEvento inscricao) {
        String nomeDestinatario;
        String email;
        UUID igrejaDaPessoaId = null;

        if (cobranca.getPessoaId() != null) {
            Pessoa pessoa = pessoaRepository.findById(cobranca.getPessoaId()).orElse(null);
            if (pessoa == null) return;
            nomeDestinatario = pessoa.getNome();
            email = pessoa.getEmail();
            igrejaDaPessoaId = pessoa.getIgreja().getId();
        } else if (cobranca.getAcompanhanteId() != null) {
            // Acompanhante (modelo antigo, ver Plano 4b) nunca tem e-mail — nada a enviar.
            return;
        } else {
            nomeDestinatario = inscricao.getNomeConvidado();
            email = inscricao.getEmailConvidado();
        }

        if (email == null || email.isBlank()) {
            log.info("Cobrança confirmada sem e-mail pra enviar comprovante. cobrancaId={}", cobranca.getId());
            return;
        }

        Evento evento = eventoRepository.findById(cobranca.getEventoId()).orElse(null);
        if (evento == null) return;

        // Evento de outra igreja da família/rede (ver eventos-compartilhados) — a pessoa
        // precisa saber que não foi a própria igreja dela que organizou.
        String igrejaOrganizadora = igrejaDaPessoaId != null && !igrejaDaPessoaId.equals(evento.getIgreja().getId())
            ? evento.getIgreja().getNome()
            : null;

        try {
            emailService.enviar(email, "Inscrição confirmada — " + evento.getTitulo(),
                montarCorpoEmail(nomeDestinatario, evento, cobranca, igrejaOrganizadora));
            log.info("E-mail de confirmação de pagamento enviado. cobrancaId={}", cobranca.getId());
        } catch (RuntimeException e) {
            log.error("Falha ao enviar e-mail de confirmação de pagamento. cobrancaId={}", cobranca.getId(), e);
        }
    }

    private String montarCorpoEmail(String nome, Evento evento, CobrancaEvento cobranca, String igrejaOrganizadora) {
        String valorFormatado = java.text.NumberFormat.getCurrencyInstance(new Locale("pt", "BR"))
            .format(cobranca.getValor());
        String local = evento.getLocalExibicao();

        return """
                <div style="font-family: Arial, sans-serif; max-width: 480px; margin: 0 auto;">
                  <p style="text-align: center; margin-bottom: 8px;">
                    <span style="display: inline-flex; align-items: center; justify-content: center;
                       width: 48px; height: 48px; border-radius: 999px; background: #f0fdf4;
                       color: #16a34a; font-size: 28px; line-height: 48px;">&#10003;</span>
                  </p>
                  <h2 style="text-align: center; color: #131b2e;">Pagamento aprovado!</h2>
                  <p>Olá, %s.</p>
                  <p>Sua inscrição no evento abaixo está confirmada.</p>
                  <div style="background: #f8fafc; border-radius: 8px; padding: 16px; margin: 24px 0;">
                    <p style="margin: 0 0 4px; font-weight: bold; color: #131b2e;">%s</p>
                    <p style="margin: 0; color: #64748b; font-size: 14px;">%s%s</p>
                  </div>
                  <p style="color: #64748b; font-size: 14px;">Valor pago: <strong>%s</strong></p>
                  %s
                </div>
                """.formatted(
            nome,
            evento.getTitulo(),
            FORMATADOR_DATA.format(evento.getInicioEm()),
            local != null ? " — " + local : "",
            valorFormatado,
            igrejaOrganizadora != null
                ? "<p style=\"color: #64748b; font-size: 12px;\">Evento organizado por: " + igrejaOrganizadora + "</p>"
                : ""
        );
    }
}
