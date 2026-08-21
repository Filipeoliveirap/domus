package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinte;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoContribuinteRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Contribuição de pessoa excluída definitivamente (pessoa_id NULL) precisa continuar contando no relatório geral. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RelatorioRepositoryContribuinteAnonimoTest implements PostgresTestContainerSupport {

    @Autowired RelatorioRepository relatorioRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired MovimentacaoContribuinteRepository contribuinteRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Contribuinte Anônimo " + UUID.randomUUID())
                .emailContato("contrib-" + UUID.randomUUID() + "@teste.com")
                .build());
    }

    @Test
    void agregarPorContribuinte_contaContribuicaoDePessoaExcluida() {
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Doador " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        CategoriaFinanceira categoria = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Dízimo " + UUID.randomUUID()).tipo(TipoCategoria.ENTRADA).build());
        MovimentacaoFinanceira mov = movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.valueOf(100))
                .dataMovimentacao(LocalDate.now()).build());
        contribuinteRepository.save(MovimentacaoContribuinte.builder()
                .movimentacao(mov).pessoa(pessoa).valor(BigDecimal.valueOf(100)).build());
        entityManager.flush();
        entityManager.clear();

        // Simula a exclusão definitiva da pessoa: desvincula sem apagar a linha.
        contribuinteRepository.desvincularPessoa(pessoa.getId());
        entityManager.flush();
        entityManager.clear();

        var agregados = relatorioRepository.agregarPorContribuinte(
                igreja.getId(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        assertThat(agregados).hasSize(1);
        assertThat(agregados.get(0).getPessoaId()).isNull();
        assertThat(agregados.get(0).getPessoaNome()).isEqualTo("Pessoa removida do sistema");
        assertThat(agregados.get(0).getTotal()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }
}
