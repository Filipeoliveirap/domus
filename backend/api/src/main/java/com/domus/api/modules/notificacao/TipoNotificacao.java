package com.domus.api.modules.notificacao;

/** Um tipo por produtor. Extensível: adicionar produtor novo é uma entrada nova aqui — nunca
 *  editar NotificacaoService, banco ou frontend por causa de um tipo novo. */
public enum TipoNotificacao {
    PEDIDO_MINISTERIO,
    ENTRADA_CELULA,
    ACESSO_CONCEDIDO,
    INSCRICAO_EVENTO_RESPONSAVEL,
    PROMOVIDO_LIDER_CELULA,
    EVENTO_ALTERADO,
    PEDIDO_VINCULO_FAMILIA,
    EXCLUSAO_IGREJA_AGENDADA,
    RESPONSAVEL_EVENTO,
    ADICIONADO_CELULA,
    REMOVIDO_CELULA,
    ADICIONADO_MINISTERIO,
    REMOVIDO_MINISTERIO,
    CELULA_ALTERADA,
    NOVO_EVENTO,
    CAMPO_PERSONALIZADO_PENDENTE,
    COBRANCA_EVENTO_PAGA,
    CONTA_PAGAMENTO_RECONEXAO_NECESSARIA,
    CATEGORIA_FINANCEIRA_AUTO_CRIADA
}
