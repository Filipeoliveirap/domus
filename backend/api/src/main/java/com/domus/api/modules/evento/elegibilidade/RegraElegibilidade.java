package com.domus.api.modules.evento.elegibilidade;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.pessoa.Pessoa;

import java.util.Optional;

public interface RegraElegibilidade {
    /** Vazio = aprovado. Preenchido = por que não pode. */
    Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa);
}
