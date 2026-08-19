package com.domus.api.modules.financeiro.categoria;

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
class CategoriaFinanceiraRepositoryTest {

    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
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
                .nome("Igreja Teste Categoria " + UUID.randomUUID())
                .emailContato("categoria-" + UUID.randomUUID() + "@teste.com")
                .build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Tesoureiro Teste").vinculo(Vinculo.MEMBRO).build());

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();

        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());

        entityManager.flush();
    }

    @Test
    void countByCategoriaIdAndIgrejaId_enxergaMovimentacaoDeCategoriaArquivada() {
        // Mesmo bug de CelulaMembroRepository/MinisterioMembroRepository: @SQLRestriction vaza pro JOIN implícito e faz a contagem mentir que categoria arquivada está vazia.
        CategoriaFinanceira categoria = categoriaRepository.save(
                CategoriaFinanceira.builder().igreja(igreja).nome("Dízimo " + UUID.randomUUID())
                        .tipo(TipoCategoria.ENTRADA).build());

        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build());
        entityManager.flush();
        entityManager.clear();

        categoriaRepository.delete(categoria); // arquiva (soft delete)
        entityManager.flush();
        entityManager.clear();

        long total = movimentacaoRepository.countByCategoriaIdAndIgrejaId(categoria.getId(), igreja.getId());

        assertThat(total).isEqualTo(1);
    }

    @Test
    void countByCategoriaIdAndIgrejaId_ignoraMovimentacaoJaArquivada() {
        CategoriaFinanceira categoria = categoriaRepository.save(
                CategoriaFinanceira.builder().igreja(igreja).nome("Oferta " + UUID.randomUUID())
                        .tipo(TipoCategoria.ENTRADA).build());

        MovimentacaoFinanceira mov = movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.ONE)
                .dataMovimentacao(LocalDate.now()).build());
        entityManager.flush();
        entityManager.clear();

        movimentacaoRepository.delete(movimentacaoRepository.findById(mov.getId()).orElseThrow());
        entityManager.flush();
        entityManager.clear();

        long total = movimentacaoRepository.countByCategoriaIdAndIgrejaId(categoria.getId(), igreja.getId());

        assertThat(total).isZero();
    }

    @Test
    void findArquivadasPorIgrejaTrazSoAsArquivadas() {
        CategoriaFinanceira ativa = categoriaRepository.save(
                CategoriaFinanceira.builder().igreja(igreja).nome("Ativa " + UUID.randomUUID())
                        .tipo(TipoCategoria.SAIDA).build());
        CategoriaFinanceira arquivada = categoriaRepository.save(
                CategoriaFinanceira.builder().igreja(igreja).nome("Arquivada " + UUID.randomUUID())
                        .tipo(TipoCategoria.SAIDA).build());
        categoriaRepository.delete(arquivada);
        entityManager.flush();
        entityManager.clear();

        List<CategoriaFinanceira> arquivadas = categoriaRepository.findArquivadasPorIgreja(igreja.getId());

        assertThat(arquivadas).extracting(CategoriaFinanceira::getId).containsExactly(arquivada.getId());
        assertThat(arquivadas).extracting(CategoriaFinanceira::getId).doesNotContain(ativa.getId());
    }

    @Test
    void restaurarPorIdSoRestauraDaIgrejaCerta() {
        CategoriaFinanceira categoria = categoriaRepository.save(
                CategoriaFinanceira.builder().igreja(igreja).nome("Vai e volta " + UUID.randomUUID())
                        .tipo(TipoCategoria.AMBOS).build());
        UUID id = categoria.getId();
        categoriaRepository.delete(categoria);
        entityManager.flush();
        entityManager.clear();

        int linhasOutraIgreja = categoriaRepository.restaurarPorId(id, UUID.randomUUID());
        assertThat(linhasOutraIgreja).isZero();

        int linhas = categoriaRepository.restaurarPorId(id, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(linhas).isEqualTo(1);
        assertThat(categoriaRepository.findById(id)).isPresent();
    }
}
