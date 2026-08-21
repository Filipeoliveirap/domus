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
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BalanceteRepositoryConsolidadoTest implements PostgresTestContainerSupport {

    @Autowired BalanceteRepository balanceteRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    Igreja sede;
    Igreja congregacao;
    Usuario usuarioSede;
    Usuario usuarioCongregacao;

    @BeforeEach
    void setup() {
        sede = igrejaRepository.save(Igreja.builder()
                .nome("Sede Teste Consolidado " + UUID.randomUUID())
                .emailContato("sede-" + UUID.randomUUID() + "@teste.com")
                .build());
        congregacao = igrejaRepository.save(Igreja.builder()
                .nome("Congregacao Teste Consolidado " + UUID.randomUUID())
                .emailContato("congregacao-" + UUID.randomUUID() + "@teste.com")
                .igrejaMae(sede)
                .build());

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        usuarioSede = usuarioRepository.save(Usuario.builder().igreja(sede)
                .pessoa(pessoaRepository.save(Pessoa.builder().igreja(sede).nome("Tesoureiro Sede").vinculo(Vinculo.MEMBRO).build()))
                .role(role).ativo(true).build());
        usuarioCongregacao = usuarioRepository.save(Usuario.builder().igreja(congregacao)
                .pessoa(pessoaRepository.save(Pessoa.builder().igreja(congregacao).nome("Tesoureiro Congregacao").vinculo(Vinculo.MEMBRO).build()))
                .role(role).ativo(true).build());
        entityManager.flush();
    }

    @Test
    void casaCategoriasDeNomeIgualEntreIgrejasDiferentesIgnorandoAcentoECaixa() {
        CategoriaFinanceira dizimosSede = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(sede).nome("Dízimos").tipo(TipoCategoria.ENTRADA).build());
        CategoriaFinanceira dizimosCongregacao = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(congregacao).nome("dizimos").tipo(TipoCategoria.ENTRADA).build());

        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(sede).categoria(dizimosSede).criadoPor(usuarioSede)
                .tipo(TipoMovimentacao.ENTRADA).valor(new BigDecimal("100.00"))
                .dataMovimentacao(LocalDate.of(2026, 1, 5)).build());
        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(congregacao).categoria(dizimosCongregacao).criadoPor(usuarioCongregacao)
                .tipo(TipoMovimentacao.ENTRADA).valor(new BigDecimal("50.00"))
                .dataMovimentacao(LocalDate.of(2026, 1, 10)).build());
        entityManager.flush();
        entityManager.clear();

        List<BalanceteProjections.LinhaMensalConsolidada> linhas =
                balanceteRepository.agregarConsolidadoPorCategoriaEMes(
                        List.of(sede.getId(), congregacao.getId()), 2026);

        assertThat(linhas).hasSize(1); // uma única linha, casada por nome normalizado
        assertThat(linhas.get(0).getTotal()).isEqualByComparingTo("150.00");
    }
}
