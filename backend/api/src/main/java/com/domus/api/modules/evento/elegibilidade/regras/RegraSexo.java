package com.domus.api.modules.evento.elegibilidade.regras;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.elegibilidade.CodigoImpedimento;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.RegraElegibilidade;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Sexo;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class RegraSexo implements RegraElegibilidade {
    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (evento.getRestricaoSexo() == null) return Optional.empty();
        if (pessoa.getSexo() == null) {
            return Optional.of(new Impedimento(CodigoImpedimento.SEM_SEXO,
                    "O cadastro de " + pessoa.getNome() + " não informa o sexo. "
                    + "Procure a secretaria da igreja para completá-lo.", true));
        }
        if (pessoa.getSexo() == evento.getRestricaoSexo()) return Optional.empty();
        return Optional.of(new Impedimento(CodigoImpedimento.SEXO,
                "Este evento é para " + (evento.getRestricaoSexo() == Sexo.MULHER
                        ? "mulheres" : "homens") + ".", true));
    }
}
