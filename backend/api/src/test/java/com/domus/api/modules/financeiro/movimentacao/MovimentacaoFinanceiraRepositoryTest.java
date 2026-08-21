package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
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
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Regressão: arquivar a categoria não pode fazer a movimentação sumir da listagem/total/detalhe. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MovimentacaoFinanceiraRepositoryTest implements PostgresTestContainerSupport {

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
                .nome("Igreja Teste Movimentacao " + UUID.randomUUID())
                .emailContato("mov-" + UUID.randomUUID() + "@teste.com")
                .build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Tesoureiro Teste").vinculo(Vinculo.MEMBRO).build());

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();

        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());

        entityManager.flush();
    }

    private CategoriaFinanceira categoria() {
        return categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Dízimo " + UUID.randomUUID()).tipo(TipoCategoria.ENTRADA).build());
    }

    private MovimentacaoFinanceira movimentacao(CategoriaFinanceira categoria) {
        return movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build());
    }

    @Test
    void buscarComFiltros_continuaEnxergandoMovimentacaoDeCategoriaArquivada() {
        CategoriaFinanceira categoria = categoria();
        MovimentacaoFinanceira mov = movimentacao(categoria);
        entityManager.flush();
        entityManager.clear();

        categoriaRepository.delete(categoria); // arquiva (soft delete)
        entityManager.flush();
        entityManager.clear();

        var pagina = movimentacaoRepository.buscarComFiltros(
                igreja.getId(), null, null,
                LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31),
                null, null, PageRequest.of(0, 20));

        assertThat(pagina.getContent()).extracting(MovimentacaoFinanceira::getId).containsExactly(mov.getId());
        // Id acessível via proxy, sem fetch join.
        assertThat(pagina.getContent().get(0).getCategoria().getId()).isEqualTo(categoria.getId());
    }

    @Test
    void buscarPorIdComRelacoes_continuaEnxergandoMovimentacaoDeCategoriaArquivada() {
        CategoriaFinanceira categoria = categoria();
        MovimentacaoFinanceira mov = movimentacao(categoria);
        entityManager.flush();
        entityManager.clear();

        categoriaRepository.delete(categoria);
        entityManager.flush();
        entityManager.clear();

        var encontrada = movimentacaoRepository.buscarPorIdComRelacoes(mov.getId(), igreja.getId());

        assertThat(encontrada).isPresent();
    }

    @Test
    void agregarTotaisComFiltros_continuaContandoMovimentacaoDeCategoriaArquivada() {
        CategoriaFinanceira categoria = categoria();
        movimentacao(categoria);
        entityManager.flush();
        entityManager.clear();

        categoriaRepository.delete(categoria);
        entityManager.flush();
        entityManager.clear();

        var totais = movimentacaoRepository.agregarTotaisComFiltros(
                igreja.getId(), null, null,
                LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31), null, null);

        assertThat(totais.getQuantidade()).isEqualTo(1L);
        assertThat(totais.getTotalEntradas()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    void categoriaService_resolveNomeDeCategoriaArquivadaEmLote() {
        CategoriaFinanceira categoria = categoria();
        String nomeOriginal = categoria.getNome();
        entityManager.flush();
        entityManager.clear();

        categoriaRepository.delete(categoria);
        entityManager.flush();
        entityManager.clear();

        List<CategoriaFinanceira> encontradas = categoriaRepository
                .findByIdInAndIgrejaIdIncluindoArquivadas(List.of(categoria.getId()), igreja.getId());

        assertThat(encontradas).hasSize(1);
        assertThat(encontradas.get(0).getNome()).isEqualTo(nomeOriginal);
    }
}
