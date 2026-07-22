package com.domus.api.modules.evento.elegibilidade.regras;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.elegibilidade.CodigoImpedimento;
import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.domus.api.modules.evento.elegibilidade.RegraElegibilidade;
import com.domus.api.modules.pessoa.Pessoa;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@Component
public class RegraFaixaEtaria implements RegraElegibilidade {

    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (evento.getIdadeMin() == null && evento.getIdadeMax() == null) {
            return Optional.empty();  // evento sem restrição de idade
        }
        if (pessoa.getDataNascimento() == null) {
            return Optional.of(new Impedimento(
                    CodigoImpedimento.SEM_DATA_NASCIMENTO,
                    "O cadastro de " + pessoa.getNome() + " não tem data de nascimento. "
                    + "Procure a secretaria da igreja para completá-lo.",
                    true));
        }
        int idade = Period.between(pessoa.getDataNascimento(), LocalDate.now()).getYears();

        // Limites INCLUSIVOS: "de 18 até 29" aceita 18 e 29.
        boolean abaixo = evento.getIdadeMin() != null && idade < evento.getIdadeMin();
        boolean acima  = evento.getIdadeMax() != null && idade > evento.getIdadeMax();
        if (!abaixo && !acima) return Optional.empty();

        return Optional.of(new Impedimento(
                CodigoImpedimento.FAIXA_ETARIA,
                pessoa.getNome() + " tem " + idade + " anos e este evento é para "
                        + descreverFaixa(evento) + ".",
                true));
    }

    private String descreverFaixa(Evento e) {
        if (e.getIdadeMin() != null && e.getIdadeMax() != null)
            return e.getIdadeMin() + " a " + e.getIdadeMax() + " anos";
        if (e.getIdadeMin() != null) return "maiores de " + e.getIdadeMin() + " anos";
        return "menores de " + e.getIdadeMax() + " anos";
    }
}
