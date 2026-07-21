package com.domus.api.modules.pessoa;

/**
 * Relação da pessoa com a igreja.
 *
 * <p>Substitui o antigo {@code StatusMembro} e o boolean {@code batizado}, que juntos
 * permitiam o estado impossível "membro não batizado".
 *
 * <p>Não existe "inativo": quem parou de frequentar é <b>arquivado</b> (soft delete),
 * que é o mesmo mecanismo usado por todo o resto do sistema.
 */
public enum Vinculo {
    /** Batizado, formalmente membro da igreja. */
    MEMBRO,
    /** Frequenta, não é batizado. Absorveu o antigo VISITANTE. */
    CONGREGANTE
}
