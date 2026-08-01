package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BalanceteRepositoryTest {

    @Autowired BalanceteRepository balanceteRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;
    Usuario usuario;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Balancete " + UUID.randomUUID())
                .emailContato("balancete-" + UUID.randomUUID() + "@teste.com")
                .build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja)
                .nome("Tesoureiro Teste")
                .vinculo(Vinculo.MEMBRO)
                .build());

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();

        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja)
                .pessoa(pessoa)
                .role(role)
                .ativo(true)
                .build());

        entityManager.flush();
    }

    private CategoriaFinanceira categoria(String nome, TipoCategoria tipo) {
        return categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome(nome).tipo(tipo).build());
    }

    private void movimentacao(CategoriaFinanceira categoria, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data) {
        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(tipo).valor(valor).dataMovimentacao(data).build());
    }

    @Test
    void agregaPorCategoriaEMesDentroDoAno() {
        CategoriaFinanceira dizimos = categoria("Dizimos Teste " + UUID.randomUUID(), TipoCategoria.ENTRADA);
        movimentacao(dizimos, TipoMovimentacao.ENTRADA, new BigDecimal("100.00"), LocalDate.of(2026, 1, 10));
        movimentacao(dizimos, TipoMovimentacao.ENTRADA, new BigDecimal("50.00"), LocalDate.of(2026, 1, 20));
        movimentacao(dizimos, TipoMovimentacao.ENTRADA, new BigDecimal("30.00"), LocalDate.of(2025, 12, 31));
        entityManager.flush();
        entityManager.clear();

        List<BalanceteProjections.LinhaMensalAgregada> linhas =
                balanceteRepository.agregarPorCategoriaEMes(igreja.getId(), 2026);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getMes()).isEqualTo(1);
        assertThat(linhas.get(0).getTotal()).isEqualByComparingTo("150.00");
        assertThat(linhas.get(0).getArquivada()).isFalse();
    }

    @Test
    void categoriaArquivadaComMovimentoNoAnoApareceNaAgregacao() {
        CategoriaFinanceira categoria = categoria("Doacao Especial " + UUID.randomUUID(), TipoCategoria.ENTRADA);
        movimentacao(categoria, TipoMovimentacao.ENTRADA, new BigDecimal("200.00"), LocalDate.of(2026, 3, 5));
        entityManager.flush();
        entityManager.clear(); // limpa o contexto de persistência antes do soft delete da
                                // categoria: sem isso, a movimentação já persistida continua
                                // gerenciada na sessão e, no flush seguinte, o Hibernate revalida
                                // a FK não-nula categoria_id contra a categoria (agora marcada
                                // como removida pelo @SQLDelete) e lança TransientObjectException
                                // por engano — é um efeito colateral conhecido de @SQLDelete
                                // (DELETE vira UPDATE, mas o estado da entidade na sessão continua
                                // "removido") combinado com @SQLRestriction.
        categoria = categoriaRepository.findById(categoria.getId()).orElseThrow();
        categoriaRepository.delete(categoria); // soft delete via @SQLDelete
        entityManager.flush();
        entityManager.clear();

        List<BalanceteProjections.LinhaMensalAgregada> linhas =
                balanceteRepository.agregarPorCategoriaEMes(igreja.getId(), 2026);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getArquivada()).isTrue();
        assertThat(linhas.get(0).getTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void saldoAntesDeSomaSoMovimentacoesAnterioresAoAno() {
        CategoriaFinanceira c = categoria("Ofertas Teste " + UUID.randomUUID(), TipoCategoria.AMBOS);
        movimentacao(c, TipoMovimentacao.ENTRADA, new BigDecimal("1000.00"), LocalDate.of(2025, 6, 1));
        movimentacao(c, TipoMovimentacao.SAIDA, new BigDecimal("300.00"), LocalDate.of(2025, 12, 31));
        movimentacao(c, TipoMovimentacao.ENTRADA, new BigDecimal("500.00"), LocalDate.of(2026, 1, 1)); // não entra
        entityManager.flush();
        entityManager.clear();

        BigDecimal saldo = balanceteRepository.saldoAntesDe(igreja.getId(), LocalDate.of(2026, 1, 1));

        assertThat(saldo).isEqualByComparingTo("700.00");
    }

    @Test
    void saldoAntesDeSemMovimentacaoRetornaZeroNuncaNull() {
        BigDecimal saldo = balanceteRepository.saldoAntesDe(igreja.getId(), LocalDate.of(2026, 1, 1));
        assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
