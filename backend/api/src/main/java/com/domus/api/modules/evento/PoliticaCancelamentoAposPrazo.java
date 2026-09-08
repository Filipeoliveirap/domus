package com.domus.api.modules.evento;

/** O que acontece quando alguém tenta cancelar a inscrição DEPOIS do prazo (V39).
 *  Só tem efeito quando o evento tem prazo (inscricoesAte != null). Antes do prazo,
 *  o cancelamento é sempre livre e com reembolso total. */
public enum PoliticaCancelamentoAposPrazo {
    /** A pessoa não pode cancelar sozinha após o prazo — só a organização remove. */
    NAO_PERMITIDO,
    /** Cancelamento permitido; o valor pago é estornado (comportamento antes do prazo). */
    PERMITIDO_COM_REEMBOLSO,
    /** Cancelamento permitido, mas sem estorno — a vaga é liberada, o valor não volta. */
    PERMITIDO_SEM_REEMBOLSO
}
