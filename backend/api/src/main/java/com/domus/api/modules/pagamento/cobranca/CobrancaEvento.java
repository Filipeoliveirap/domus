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
        if ((pessoaId == null) == (acompanhanteId == null)) {
            throw new IllegalArgumentException(
                "CobrancaEvento precisa de exatamente pessoaId OU acompanhanteId");
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
    public boolean ehDoTitular() { return pessoaId != null; }
}
