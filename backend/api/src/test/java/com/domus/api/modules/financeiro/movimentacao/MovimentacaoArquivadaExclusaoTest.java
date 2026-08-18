package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.DTOs.MovimentacaoArquivadaResponse;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Diferente de Categoria, movimentação é a própria linha financeira — excluir de vez nunca
 * bloqueia, mesmo com contribuintes vinculados (eles são filhos dela, somem junto via
 * ON DELETE CASCADE; o aviso do front avisa isso antes de confirmar).
 */
@SpringBootTest
@Transactional
class MovimentacaoArquivadaExclusaoTest {

    @Autowired MovimentacaoFinanceiraService service;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired MovimentacaoContribuinteRepository contribuinteRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;
    CategoriaFinanceira categoria;
    Usuario usuario;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Movimentação Arquivada " + UUID.randomUUID())
                .emailContato("movarq-" + UUID.randomUUID() + "@teste.com")
                .build());
        categoria = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Dízimo " + UUID.randomUUID()).tipo(TipoCategoria.ENTRADA).build());
        Pessoa pessoaTesoureiro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Tesoureiro Teste").vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoaTesoureiro).role(role).ativo(true).build());
        entityManager.flush();
    }

    @Test
    void arquivar_listarArquivadas_restaurarEExcluirDefinitivo_semNuncaBloquear() {
        Pessoa contribuinte = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Contribuinte " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        MovimentacaoFinanceira mov = movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build());
        UUID movId = mov.getId();
        contribuinteRepository.save(MovimentacaoContribuinte.builder()
                .movimentacao(mov).pessoa(contribuinte).valor(BigDecimal.TEN).build());
        entityManager.flush();
        entityManager.clear();

        service.arquivar(movId, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        List<MovimentacaoArquivadaResponse> arquivadas = service.listarArquivadas(igreja.getId());
        assertThat(arquivadas).hasSize(1);
        assertThat(arquivadas.get(0).id()).isEqualTo(movId);
        assertThat(arquivadas.get(0).temContribuinte()).isTrue();

        service.restaurar(movId, igreja.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(service.listarArquivadas(igreja.getId())).isEmpty();
        assertThat(movimentacaoRepository.findById(movId)).isPresent();

        service.arquivar(movId, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        service.excluirDefinitivo(movId, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(movimentacaoRepository.findByIdAndIgrejaIdIncluindoArquivadas(movId, igreja.getId())).isEmpty();
        // Contribuinte é filho da movimentação (ON DELETE CASCADE) — some junto, não bloqueia.
        assertThat(contribuinteRepository.findAll().stream().noneMatch(c -> c.getMovimentacao().getId().equals(movId)))
                .isTrue();
    }

    @Test
    void excluirDefinitivo_falhaQuandoMovimentacaoNaoEncontrada() {
        assertThatThrownBy(() -> service.excluirDefinitivo(UUID.randomUUID(), igreja.getId()))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }
}
