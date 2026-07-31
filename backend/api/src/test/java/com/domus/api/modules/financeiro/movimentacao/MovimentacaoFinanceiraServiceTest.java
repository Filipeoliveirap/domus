package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraService;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.DTOs.ContribuinteDTO;
import com.domus.api.modules.financeiro.movimentacao.DTOs.MovimentacaoRequestDTO;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MovimentacaoFinanceiraServiceTest {

    MovimentacaoFinanceiraRepository repository;
    CategoriaFinanceiraService categoriaService;
    IgrejaRepository igrejaRepository;
    PessoaRepository pessoaRepository;
    UsuarioRepository usuarioRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    MovimentacaoFinanceiraService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID categoriaId = UUID.randomUUID();
    UUID pessoaId1 = UUID.randomUUID();
    UUID pessoaId2 = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(MovimentacaoFinanceiraRepository.class);
        categoriaService = mock(CategoriaFinanceiraService.class);
        igrejaRepository = mock(IgrejaRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        service = new MovimentacaoFinanceiraService(repository, categoriaService, igrejaRepository,
                pessoaRepository, usuarioRepository, cacheEvictor, outboxRegistrador);

        CategoriaFinanceira categoria = CategoriaFinanceira.builder()
                .id(categoriaId).nome("Dízimo").tipo(TipoCategoria.AMBOS).build();
        when(categoriaService.buscarEntidade(categoriaId, igrejaId)).thenReturn(categoria);

        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setPessoa(Pessoa.builder().nome("Admin").build());
        when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);

        Pessoa pessoa1 = Pessoa.builder().id(pessoaId1).nome("Fulano").build();
        Pessoa pessoa2 = Pessoa.builder().id(pessoaId2).nome("Beltrano").build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId1, igrejaId)).thenReturn(Optional.of(pessoa1));
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId2, igrejaId)).thenReturn(Optional.of(pessoa2));

        when(repository.save(any())).thenAnswer(inv -> {
            MovimentacaoFinanceira mov = inv.getArgument(0);
            mov.setId(UUID.randomUUID());
            ultimaSalva = mov;
            return mov;
        });
        when(repository.buscarPorIdComRelacoes(any(), any())).thenAnswer(inv -> Optional.ofNullable(ultimaSalva));
    }

    private MovimentacaoFinanceira ultimaSalva;

    private MovimentacaoRequestDTO dto(BigDecimal valor, List<ContribuinteDTO> contribuintes) {
        return new MovimentacaoRequestDTO(TipoMovimentacao.ENTRADA, valor, categoriaId,
                LocalDate.now(), contribuintes, null);
    }

    @Test
    void cadastrarRecusaQuandoSomaDosContribuintesDivergeDoValor() {
        var contribuintes = List.of(
                new ContribuinteDTO(pessoaId1, new BigDecimal("30.00")),
                new ContribuinteDTO(pessoaId2, new BigDecimal("30.00")));

        assertThatThrownBy(() -> service.cadastrar(dto(new BigDecimal("100.00"), contribuintes), igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não bate");
        verify(repository, never()).save(any());
    }

    @Test
    void cadastrarRecusaContribuinteDuplicado() {
        var contribuintes = List.of(
                new ContribuinteDTO(pessoaId1, new BigDecimal("50.00")),
                new ContribuinteDTO(pessoaId1, new BigDecimal("50.00")));

        assertThatThrownBy(() -> service.cadastrar(dto(new BigDecimal("100.00"), contribuintes), igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("duas vezes");
        verify(repository, never()).save(any());
    }

    @Test
    void cadastrarAceitaListaVaziaDeContribuintes() {
        service.cadastrar(dto(new BigDecimal("100.00"), List.of()), igrejaId, usuarioId);

        verify(repository).save(any());
    }

    @Test
    void cadastrarAceitaSomaExataDosContribuintes() {
        var contribuintes = List.of(
                new ContribuinteDTO(pessoaId1, new BigDecimal("40.00")),
                new ContribuinteDTO(pessoaId2, new BigDecimal("60.00")));

        service.cadastrar(dto(new BigDecimal("100.00"), contribuintes), igrejaId, usuarioId);

        verify(repository).save(any());
    }

    @Test
    void cadastrarGravaOsContribuintesNaMovimentacao() {
        var contribuintes = List.of(
                new ContribuinteDTO(pessoaId1, new BigDecimal("40.00")),
                new ContribuinteDTO(pessoaId2, new BigDecimal("60.00")));

        service.cadastrar(dto(new BigDecimal("100.00"), contribuintes), igrejaId, usuarioId);

        var captor = org.mockito.ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getContribuintes()).hasSize(2);
    }
}
