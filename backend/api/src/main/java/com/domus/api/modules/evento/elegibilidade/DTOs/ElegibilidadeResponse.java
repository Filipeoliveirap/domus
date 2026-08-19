package com.domus.api.modules.evento.elegibilidade.DTOs;

import com.domus.api.modules.evento.elegibilidade.Elegibilidade;
import com.domus.api.modules.evento.elegibilidade.Impedimento;

import java.util.List;

/** Conveniência de UX, nunca defesa — o POST direto continua barrado pela mesma validação. */
public record ElegibilidadeResponse(boolean apto, List<Impedimento> impedimentos) {
    public static ElegibilidadeResponse from(Elegibilidade elegibilidade) {
        return new ElegibilidadeResponse(elegibilidade.apto(), elegibilidade.impedimentos());
    }
}
