package com.domus.api.modules.pagamento.conta;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conta_pagamento_igreja")
public class ContaPagamentoIgreja {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "igreja_id", nullable = false, unique = true)
    private UUID igrejaId;

    @Column(name = "mp_user_id", nullable = false)
    private String mpUserId;

    // Guardados JÁ criptografados — a criptografia/descriptografia acontece no
    // service (MercadoPagoOAuthService), nunca aqui, pra manter a entidade sem
    // dependência do CredencialEncryptor.
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessTokenCriptografado;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenCriptografado;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "conectado_em", nullable = false)
    private Instant conectadoEm;

    @Column(name = "conectado_por_usuario_id", nullable = false)
    private UUID conectadoPorUsuarioId;

    protected ContaPagamentoIgreja() {}

    public ContaPagamentoIgreja(UUID igrejaId, String mpUserId, String accessTokenCriptografado,
                                 String refreshTokenCriptografado, Instant expiraEm,
                                 UUID conectadoPorUsuarioId) {
        this.igrejaId = igrejaId;
        this.mpUserId = mpUserId;
        this.accessTokenCriptografado = accessTokenCriptografado;
        this.refreshTokenCriptografado = refreshTokenCriptografado;
        this.expiraEm = expiraEm;
        this.conectadoEm = Instant.now();
        this.conectadoPorUsuarioId = conectadoPorUsuarioId;
    }

    public void atualizarTokens(String accessTokenCriptografado, String refreshTokenCriptografado,
                                 Instant expiraEm) {
        this.accessTokenCriptografado = accessTokenCriptografado;
        this.refreshTokenCriptografado = refreshTokenCriptografado;
        this.expiraEm = expiraEm;
    }

    public UUID getId() { return id; }
    public UUID getIgrejaId() { return igrejaId; }
    public String getMpUserId() { return mpUserId; }
    public String getAccessTokenCriptografado() { return accessTokenCriptografado; }
    public String getRefreshTokenCriptografado() { return refreshTokenCriptografado; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getConectadoEm() { return conectadoEm; }
}
