package com.domus.api.modules.evento;

/** Estado do "portão" de inscrição de um evento, DERIVADO de inicioEm/inscricoesAte —
 *  não é coluna. Diz só o estado do prazo; quem pode furá-lo (admin/líder) é decisão do
 *  InscricaoService, e o front cruza isto com a role do usuário logado. */
public enum SituacaoInscricao {
    /** Sem prazo, ou o prazo ainda não venceu, e o evento não começou. */
    ABERTA,
    /** O evento ainda não começou, mas o prazo de inscrição já venceu. */
    ENCERRADA_POR_PRAZO,
    /** O evento já começou/acabou — fechamento automático que já existia. */
    ENCERRADA_POR_INICIO
}
