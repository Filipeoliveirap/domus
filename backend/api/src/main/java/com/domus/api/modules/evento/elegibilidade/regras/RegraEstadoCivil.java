package com.domus.api.modules.evento.elegibilidade.regras;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.elegibilidade.CodigoImpedimento;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.RegraElegibilidade;
import com.domus.api.modules.pessoa.Pessoa;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RegraEstadoCivil implements RegraElegibilidade {
    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (evento.getRestricaoEstadoCivil() == null) return Optional.empty();
        if (pessoa.getEstadoCivil() == null) {
            return Optional.of(new Impedimento(CodigoImpedimento.SEM_ESTADO_CIVIL,
                    "O cadastro de " + pessoa.getNome() + " não informa o estado civil. "
                    + "Procure a secretaria da igreja para completá-lo.", true));
        }
        if (pessoa.getEstadoCivil() == evento.getRestricaoEstadoCivil()) return Optional.empty();

        String rotulo = evento.getRestricaoEstadoCivil().name().toLowerCase();
        return Optional.of(new Impedimento(CodigoImpedimento.ESTADO_CIVIL,
                "Este evento é para pessoas com estado civil: " + rotulo + ".", true));
    }
}
