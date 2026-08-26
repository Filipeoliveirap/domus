package com.domus.api.modules.pagamento.cobranca;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cobranca_evento")
public class CobrancaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "igreja_id", nullable = false)
    private UUID igrejaId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "inscricao_id", nullable = false)
    private UUID inscricaoId;

    @Column(name = "pessoa_id")
    private UUID pessoaId;

    @Column(name = "acompanhante_id")
    private UUID acompanhanteId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCobranca status;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "token_link_publico", unique = true)
    private String tokenLinkPublico;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "pago_em")
    private Instant pagoEm;

    @Column(name = "criado_por_usuario_id", nullable = false)
    private UUID criadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected CobrancaEvento() {}

    public CobrancaEvento(UUID igrejaId, UUID eventoId, UUID inscricaoId, UUID pessoaId,
                           UUID acompanhanteId, BigDecimal valor, Instant expiraEm,
                           UUID criadoPorUsuarioId, String tokenLinkPublico) {
        // Os dois nulos = convidado sem cadastro (resolvido só por inscricaoId, ver
        // CobrancaController) — só os dois PREENCHIDOS ao mesmo tempo é inválido.
        if (pessoaId != null && acompanhanteId != null) {
            throw new IllegalArgumentException(
                "CobrancaEvento não pode ter pessoaId e acompanhanteId ao mesmo tempo");
        }
        this.igrejaId = igrejaId;
        this.eventoId = eventoId;
        this.inscricaoId = inscricaoId;
        this.pessoaId = pessoaId;
        this.acompanhanteId = acompanhanteId;
        this.valor = valor;
        this.status = StatusCobranca.PENDENTE;
        this.expiraEm = expiraEm;
        this.criadoPorUsuarioId = criadoPorUsuarioId;
        this.criadoEm = Instant.now();
        this.tokenLinkPublico = tokenLinkPublico;
    }

    /**
     * Critical 5 (revisão final de branch): grava o {@code mpPaymentId} assim que
     * {@code POST /cobrancas/{id}/pagar} cria o pagamento no Mercado Pago com sucesso —
     * ANTES de o webhook confirmar. Decisão de design: em vez de criar um status
     * intermediário novo (ex.: PROCESSANDO), a cobrança continua PENDENTE até o webhook
     * confirmar (menos invasivo no schema/nas telas que já leem {@code StatusCobranca}) —
     * só o campo {@code mpPaymentId} passa a existir mais cedo, e é ELE que
     * {@code CobrancaController.pagar} passa a checar pra recusar uma segunda tentativa de
     * pagamento da mesma cobrança (evita cobrança duplicada se o pagador clicar "pagar"
     * duas vezes, ou reenviar a requisição, antes do webhook chegar).
     */
    public void registrarTentativaPagamento(String mpPaymentId) {
        this.mpPaymentId = mpPaymentId;
    }

    /**
     * Libera a cobrança para uma nova tentativa de pagamento depois que a tentativa
     * anterior (que gravou {@code mpPaymentId} via {@link #registrarTentativaPagamento})
     * termina recusada/cancelada no Mercado Pago. Sem isto, {@code mpPaymentId} ficava
     * preenchido pra sempre num pagamento recusado e {@code CobrancaController.pagar}
     * recusava toda tentativa seguinte com {@code COBRANCA_JA_EM_PROCESSAMENTO} — a
     * pessoa nunca conseguia tentar outro cartão ou PIX. O status da cobrança continua
     * PENDENTE: só o campo que trava a idempotência é limpo.
     */
    public void liberarParaNovaTentativa() {
        this.mpPaymentId = null;
    }

    public void marcarComoPago(String mpPaymentId) {
        this.status = StatusCobranca.PAGO;
        this.mpPaymentId = mpPaymentId;
        this.pagoEm = Instant.now();
    }

    public void marcarComoExpirado() { this.status = StatusCobranca.EXPIRADO; }
    public void marcarComoCancelado() { this.status = StatusCobranca.CANCELADO; }
    public void marcarComoReembolsado() { this.status = StatusCobranca.REEMBOLSADO; }

    public UUID getId() { return id; }
    public UUID getIgrejaId() { return igrejaId; }
    public UUID getEventoId() { return eventoId; }
    public UUID getInscricaoId() { return inscricaoId; }
    public UUID getPessoaId() { return pessoaId; }
    public UUID getAcompanhanteId() { return acompanhanteId; }
    public BigDecimal getValor() { return valor; }
    public StatusCobranca getStatus() { return status; }
    public String getMpPaymentId() { return mpPaymentId; }
    public String getTokenLinkPublico() { return tokenLinkPublico; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getPagoEm() { return pagoEm; }
    public UUID getCriadoPorUsuarioId() { return criadoPorUsuarioId; }

    /**
     * "É do titular" quer dizer "quem paga é quem já está vendo a própria tela mudar" —
     * exige as DUAS coisas: (1) é uma pessoa cadastrada ({@code pessoaId != null}, não um
     * acompanhante sem cadastro) E (2) ninguém gerou um link público pra essa cobrança
     * ({@code tokenLinkPublico == null}). Antes desta correção (Important 6, revisão
     * final de branch) o discriminador era só {@code pessoaId != null} — o que classificava
     * errado uma cobrança de link gerado pra OUTRA pessoa CADASTRADA (que também tem
     * {@code pessoaId != null}, sem {@code acompanhanteId}) como "do titular", e por isso
     * quem gerou o link nunca era notificado quando ela pagava. Cobrança de acompanhante
     * (sem cadastro, {@code pessoaId == null}) nunca é "do titular", com ou sem link —
     * continua notificando quem inscreveu, como sempre foi.
     */
    public boolean ehDoTitular() { return pessoaId != null && tokenLinkPublico == null; }
}
