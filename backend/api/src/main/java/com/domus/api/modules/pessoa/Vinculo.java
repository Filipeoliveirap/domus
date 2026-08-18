package com.domus.api.modules.pessoa;

/** Relação da pessoa com a igreja. Não existe "inativo": quem parou de frequentar é arquivado (soft delete). */
public enum Vinculo {
    /** Batizado, formalmente membro da igreja. */
    MEMBRO,
    /** Frequenta, não é batizado. Absorveu o antigo VISITANTE. */
    CONGREGANTE
}
