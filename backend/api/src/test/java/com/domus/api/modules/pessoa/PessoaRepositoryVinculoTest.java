package com.domus.api.modules.pessoa;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 9: prova que o filtro opcional {@code vinculo} da listagem paginada de pessoas funciona —
 * filtrar por MEMBRO devolve só os batizados; sem filtro (vinculo = null), devolve todos. Roda
 * contra o Postgres de testes de verdade, dentro da transação que o {@code @DataJpaTest} desfaz
 * ao final (não deixa linha nenhuma no banco compartilhado).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PessoaRepositoryVinculoTest {

    @Autowired
    PessoaRepository pessoaRepository;

    @Autowired
    IgrejaRepository igrejaRepository;

    @Test
    void filtraPorVinculoQuandoInformadoEDevolveTodosQuandoNulo() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Vinculo T9 " + UUID.randomUUID())
                .emailContato("vinculo-t9-" + UUID.randomUUID() + "@teste.com")
                .build());
        UUID igrejaId = igreja.getId();

        Pessoa membro = salvar(igreja, "Membro Um", Vinculo.MEMBRO);
        Pessoa congregante = salvar(igreja, "Congregante Um", Vinculo.CONGREGANTE);

        Page<Pessoa> apenasMembros = pessoaRepository.buscarPorIgreja(
                igrejaId, null, Vinculo.MEMBRO, PageRequest.of(0, 10));
        assertThat(apenasMembros.getContent()).extracting(Pessoa::getId)
                .containsExactly(membro.getId());

        Page<Pessoa> apenasCongregantes = pessoaRepository.buscarPorIgreja(
                igrejaId, null, Vinculo.CONGREGANTE, PageRequest.of(0, 10));
        assertThat(apenasCongregantes.getContent()).extracting(Pessoa::getId)
                .containsExactly(congregante.getId());

        Page<Pessoa> todos = pessoaRepository.buscarPorIgreja(
                igrejaId, null, null, PageRequest.of(0, 10));
        assertThat(todos.getContent()).extracting(Pessoa::getId)
                .containsExactlyInAnyOrder(membro.getId(), congregante.getId());
    }

    private Pessoa salvar(Igreja igreja, String nome, Vinculo vinculo) {
        return pessoaRepository.save(Pessoa.builder()
                .igreja(igreja)
                .nome(nome)
                .email(nome.toLowerCase().replace(" ", ".") + "." + UUID.randomUUID() + "@teste.com")
                .vinculo(vinculo)
                .build());
    }
}
