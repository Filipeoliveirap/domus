package com.domus.api.modules.evento.elegibilidade;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.pessoa.Pessoa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ElegibilidadeService {

    /**
     * O Spring injeta TODAS as implementações. Adicionar uma restrição nova = criar um
     * arquivo com @Component. Nenhuma linha daqui muda — é o "estenda sem editar" do CLAUDE.md.
     *
     * ⚠️ RegraVagas NÃO entra aqui. A contagem autoritativa de vagas vive no InscricaoService,
     * dentro da transação com lock pessimista. Duplicá-la reabriria a corrida que a Spec A
     * fechou.
     */
    private final List<RegraElegibilidade> regras;

    public Elegibilidade avaliar(Evento evento, Pessoa pessoa) {
        List<Impedimento> encontrados = regras.stream()
                .map(r -> r.avaliar(evento, pessoa))
                .flatMap(Optional::stream)
                .toList();

        // Avalia TODAS antes de decidir: a pessoa vê de uma vez tudo o que a impede.
        return new Elegibilidade(encontrados.isEmpty(), encontrados);
    }
}
