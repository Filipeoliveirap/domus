package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO da rota `GET /cobrancas/id/{id}` — pública pela mesma garantia de posse por UUID
 * que já vale para `/cobrancas/{id}/pagar` e `/cobrancas/{id}/status` (ver javadoc de
 * {@code CobrancaController}). Usada pela página de checkout (`/eventos/{eventoId}/
 * pagamento/{cobrancaId}`) para montar o cabeçalho com contexto do evento — por isso
 * carrega {@code eventoId}/{@code inicioEmEvento} além do que {@link CobrancaPublicaDTO}
 * já tinha.
 *
 * <p>Segurança (achado em revisão, 2026-08-26): este DTO chegou a carregar
 * {@code emailPagador} pra pré-preencher o Payment Brick — mas essa rota é pública
 * (sem sessão, garantia só pelo UUID da cobrança na URL), e o UUID pode vazar indiretamente
 * (histórico de navegador compartilhado, log de proxy) mesmo sendo impraticável de
 * adivinhar. Vazar o UUID nesses casos vazaria também o e-mail completo do pagador — o
 * mesmo motivo pelo qual {@link CobrancaPublicaDTO} nunca carregou e-mail/telefone. Removido
 * pra alinhar as duas rotas públicas na mesma regra; o Payment Brick volta a pedir o e-mail
 * quando não vem pré-preenchido, comportamento que a rota `/cobrancas/{token}` já tinha.
 */
public record CobrancaCheckoutDTO(
    UUID id,
    UUID eventoId,
    String tituloEvento,
    LocalDateTime inicioEmEvento,
    String nomePagador,
    BigDecimal valor,
    String status,
    Instant expiraEm,
    /** {@code true} quando já existe uma tentativa de pagamento em voo (mpPaymentId
     *  gravado, esperando o webhook confirmar) — achado testando o fluxo de ponta a ponta
     *  (2026-08-26): sem isso, dar reload na página enquanto o Pix ainda não confirmou
     *  mostrava o formulário de novo, como se nada tivesse sido tentado, e uma nova
     *  tentativa esbarraria em COBRANCA_JA_EM_PROCESSAMENTO. */
    boolean pagamentoEmAndamento
) {}
