package com.domus.api.modules.evento;

/**
 * Qual base de contagem alimentou uma variação do relatório geral (Decisão 4 do spec):
 * {@code COMPARECIMENTO} quando os dois eventos comparados têm {@code controlaPresenca=true};
 * {@code INSCRITOS} (inscritos confirmados, pessoa+convidado) quando não. Nunca implícito —
 * o front sempre mostra qual foi usada (tooltip/click).
 */
public enum BaseComparacao {
    COMPARECIMENTO,
    INSCRITOS
}
