package com.domus.api.modules.evento.campopersonalizado;

/** De onde veio o valor exibido no modal de respostas do gestor. */
public enum OrigemResposta {
    /** A pessoa respondeu (ou foi auto-preenchido no fluxo de resposta). */
    RESPONDIDO,
    /** Campo mapeado (idade/estado civil/sexo/endereço) — valor lido do cadastro da Pessoa, não respondido no evento. */
    CADASTRO,
    /** Campo sem resposta e sem valor no cadastro. */
    SEM_RESPOSTA
}
