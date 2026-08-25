package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.UUID;

/**
 * Item da resposta de {@code POST /eventos/{id}/inscricoes/pessoas} (Task 14, revisão
 * pós-review) — o que o front precisa por pessoa inscrita num evento pago pra decidir o
 * próximo passo: {@code cobrancaId} não nulo + {@code tokenLinkPublico} nulo → abrir
 * {@code PaymentBrickCheckout} (a pessoa que fez a ação vai pagar agora);
 * {@code cobrancaId} + {@code tokenLinkPublico} não nulos → abrir
 * {@code ModalCompartilharCobranca} (a pessoa recebe um link pra pagar sozinha depois);
 * os dois nulos → evento gratuito, nada a fazer.
 */
public record PessoaInscritaComCobranca(
        UUID pessoaId,
        UUID cobrancaId,
        String tokenLinkPublico
) {}
