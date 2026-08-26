package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.UUID;

/**
 * Item da resposta de {@code POST /eventos/{id}/inscricoes/pessoas} — o que o front
 * precisa por pessoa inscrita num evento pago pra decidir o próximo passo:
 * {@code inscricaoId} (Plano 4) permite anexar respostas de campos personalizados via
 * {@code PUT /inscricoes/{id}/respostas} depois de criar; {@code cobrancaId} não nulo +
 * {@code tokenLinkPublico} nulo → navegar pra rota de checkout (paga agora);
 * {@code cobrancaId} + {@code tokenLinkPublico} não nulos → abrir
 * {@code ModalCompartilharCobranca} (a pessoa recebe um link pra pagar sozinha depois);
 * os dois nulos → evento gratuito, nada a fazer.
 */
public record PessoaInscritaComCobranca(
        UUID pessoaId,
        UUID inscricaoId,
        UUID cobrancaId,
        String tokenLinkPublico
) {}
