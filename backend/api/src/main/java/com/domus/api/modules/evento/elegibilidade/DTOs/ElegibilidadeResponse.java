package com.domus.api.modules.evento.elegibilidade.DTOs;

import com.domus.api.modules.evento.elegibilidade.Elegibilidade;
import com.domus.api.modules.evento.elegibilidade.Impedimento;

import java.util.List;

/**
 * É conveniência de UX — a tela usa para decidir o que mostrar ANTES de tentar o POST —,
 * NUNCA defesa: quem chamar o POST direto continua esbarrando na mesma
 * {@link com.domus.api.modules.evento.elegibilidade.NaoElegivelException}.
 */
public record ElegibilidadeResponse(boolean apto, List<Impedimento> impedimentos) {
    public static ElegibilidadeResponse from(Elegibilidade elegibilidade) {
        return new ElegibilidadeResponse(elegibilidade.apto(), elegibilidade.impedimentos());
    }
}
