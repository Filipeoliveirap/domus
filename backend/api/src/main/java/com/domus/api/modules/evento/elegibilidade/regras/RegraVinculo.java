package com.domus.api.modules.evento.elegibilidade.regras;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.elegibilidade.CodigoImpedimento;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.RegraElegibilidade;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Vinculo;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RegraVinculo implements RegraElegibilidade {
    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (!evento.isExclusivoMembros()) return Optional.empty();
        if (pessoa.getVinculo() == Vinculo.MEMBRO) return Optional.empty();
        return Optional.of(new Impedimento(
                CodigoImpedimento.EXCLUSIVO_MEMBROS,
                "Este evento é exclusivo para membros batizados.",
                true));
    }
}
