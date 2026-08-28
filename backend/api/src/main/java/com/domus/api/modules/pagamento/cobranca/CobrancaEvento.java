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

    /** Quanto desta cobrança JÁ foi estornado de verdade (soma de todo estorno parcial já
     *  feito) — zero até o primeiro estorno. Essencial pra saber quanto ainda dá pra
     *  estornar: sem isto, cancelar uma inscrição que já tinha recebido um estorno parcial
     *  (reajuste de preço pra baixo, ver InscricaoService.aplicarMudancaValorPago) tentava
     *  estornar o valor CHEIO de novo, e o Mercado Pago recusava por falta de saldo
     *  (achado ao vivo, 2026-08-27). */
    @Column(name = "valor_estornado", nullable = false)
    private BigDecimal valorEstornado = BigDecimal.ZERO;

    /** {@code true} quando uma tentativa de estorno (em lote ou individual) falhou e
     *  ainda não foi resolvida — usado pela lista de inscritos pra mostrar a tag "Estorno
     *  pendente" com botão de tentar de novo (2026-08-27). Nunca fica {@code true} sozinho
     *  pra sempre: {@link #registrarEstorno} limpa assim que um estorno dá certo. */
    @Column(name = "estorno_pendente", nullable = false)
    private boolean estornoPendente = false;

    protected CobrancaEvento() {}

    public CobrancaEvento(UUID igrejaId, UUID eventoId, UUID inscricaoId, UUID pessoaId,
                           BigDecimal valor, Instant expiraEm,
                           UUID criadoPorUsuarioId, String tokenLinkPublico) {
        // pessoaId nulo = convidado sem cadastro (resolvido só por inscricaoId, ver
        // CobrancaController).
        this.igrejaId = igrejaId;
        this.eventoId = eventoId;
        this.inscricaoId = inscricaoId;
        this.pessoaId = pessoaId;
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

    /** Cobrança PENDENTE (ainda não paga) tendo seu valor ajustado depois de o admin mudar
     *  o preço do evento — nunca chamado numa cobrança PAGO (ver InscricaoService
     *  .aplicarMudancaValorPago: cobrança já paga recebe uma cobrança de diferença nova, ou
     *  estorno, nunca tem o próprio valor reescrito). */
    public void atualizarValor(BigDecimal valorNovo) { this.valor = valorNovo; }

    public void marcarComoExpirado() { this.status = StatusCobranca.EXPIRADO; }
    public void marcarComoCancelado() { this.status = StatusCobranca.CANCELADO; }

    /** Registra que {@code valor} foi estornado de verdade (soma no total já devolvido),
     *  limpa a pendência (estorno deu certo) e marca REEMBOLSADO quando não sobra mais
     *  nada pra devolver — nunca chamar com um valor maior que
     *  {@link #valorRestanteParaEstornar()}. */
    public void registrarEstorno(BigDecimal valor) {
        this.valorEstornado = this.valorEstornado.add(valor);
        this.estornoPendente = false;
        if (this.valorEstornado.compareTo(this.valor) >= 0) {
            this.status = StatusCobranca.REEMBOLSADO;
        }
    }

    /** Quanto ainda dá pra estornar desta cobrança — o valor original menos o que já foi
     *  devolvido em estornos parciais anteriores. Nunca negativo. */
    public BigDecimal valorRestanteParaEstornar() {
        BigDecimal restante = this.valor.subtract(this.valorEstornado);
        return restante.signum() > 0 ? restante : BigDecimal.ZERO;
    }

    /** Uma tentativa de estorno (em lote ou individual) falhou — fica marcada até alguém
     *  tentar de novo com sucesso ({@link #registrarEstorno} limpa) ou até o "restante" da
     *  cobrança já ter sido zerado por outro caminho. */
    public void marcarEstornoPendente() { this.estornoPendente = true; }

    public boolean isEstornoPendente() { return estornoPendente; }

    public UUID getId() { return id; }
    public UUID getIgrejaId() { return igrejaId; }
    public UUID getEventoId() { return eventoId; }
    public UUID getInscricaoId() { return inscricaoId; }
    public UUID getPessoaId() { return pessoaId; }
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
     * convidado sem cadastro) E (2) ninguém gerou um link público pra essa cobrança
     * ({@code tokenLinkPublico == null}). Antes desta correção (Important 6, revisão
     * final de branch) o discriminador era só {@code pessoaId != null} — o que classificava
     * errado uma cobrança de link gerado pra OUTRA pessoa CADASTRADA (que também tem
     * {@code pessoaId != null}) como "do titular", e por isso quem gerou o link nunca era
     * notificado quando ela pagava. Cobrança de convidado (sem cadastro, {@code pessoaId == null})
     * nunca é "do titular", com ou sem link — continua notificando quem inscreveu, como sempre foi.
     */
    public boolean ehDoTitular() { return pessoaId != null && tokenLinkPublico == null; }
}
