package com.domus.api.modules.financeiro.movimentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Task 7 (2026-09-09): pagamento/estorno de evento pago passa a lançar DOIS registros no
 * financeiro — o valor bruto na categoria "Eventos" e a taxa do Mercado Pago (ou a
 * devolução dela) na categoria "Taxas de pagamento". A taxa só vira lançamento quando é
 * maior que zero.
 */
class MovimentacaoAutomaticaServiceTest {

    CategoriaFinanceiraRepository categoriaRepository;
    MovimentacaoFinanceiraRepository movimentacaoRepository;
    IgrejaRepository igrejaRepository;
    PessoaRepository pessoaRepository;
    UsuarioRepository usuarioRepository;
    NotificacaoService notificacaoService;
    OutboxRegistrador outboxRegistrador;
    CacheEvictor cacheEvictor;
    MovimentacaoAutomaticaService service;

    UUID igrejaId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();

    CategoriaFinanceira categoriaEventos;

    @BeforeEach
    void setup() {
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        cacheEvictor = mock(CacheEvictor.class);
        service = new MovimentacaoAutomaticaService(categoriaRepository, movimentacaoRepository,
            igrejaRepository, pessoaRepository, usuarioRepository, notificacaoService,
            outboxRegistrador, cacheEvictor);

        when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(Igreja.builder().id(igrejaId).build());
        when(pessoaRepository.getReferenceById(pessoaId)).thenReturn(Pessoa.builder().id(pessoaId).build());
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA")).thenReturn(List.of());
        when(usuarioRepository.findByIgrejaIdAndCapacidadeAndAtivoTrue(igrejaId, "TESOUREIRO")).thenReturn(List.of());

        categoriaEventos = CategoriaFinanceira.builder().id(UUID.randomUUID())
            .igreja(Igreja.builder().id(igrejaId).build()).nome("Eventos").tipo(TipoCategoria.AMBOS).build();

        // "Eventos" já existe; a categoria de taxa ainda não — força o caminho de criação.
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), any())).thenAnswer(inv -> {
            Set<String> nomes = inv.getArgument(1);
            if (nomes.contains("taxas de pagamento")) return List.of();
            return List.of(categoriaEventos);
        });
        when(categoriaRepository.save(any())).thenAnswer(inv -> {
            CategoriaFinanceira c = inv.getArgument(0);
            return CategoriaFinanceira.builder().id(UUID.randomUUID()).igreja(c.getIgreja())
                .nome(c.getNome()).tipo(c.getTipo()).build();
        });
    }

    @Test
    void pagamentoConfirmado_registraEntradaBrutaEmEventosESaidaDeTaxa() {
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("110.49"), new BigDecimal("10.49"),
            "Pagamento de inscrição — Acampamento (João)", pessoaId, "João");

        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());
        var salvos = mov.getAllValues();

        var entrada = salvos.stream().filter(m -> m.getTipo() == TipoMovimentacao.ENTRADA).findFirst().orElseThrow();
        assertThat(entrada.getValor()).isEqualByComparingTo("110.49");
        assertThat(entrada.getCategoria().getNome()).isEqualTo("Eventos");
        assertThat(entrada.getContribuintes()).hasSize(1);

        var saida = salvos.stream().filter(m -> m.getTipo() == TipoMovimentacao.SAIDA).findFirst().orElseThrow();
        assertThat(saida.getValor()).isEqualByComparingTo("10.49");
        assertThat(saida.getCategoria().getNome()).isEqualTo("Taxas de pagamento");
        assertThat(saida.getDescricao()).isEqualTo("Taxa Mercado Pago — Acampamento (João)");
        assertThat(saida.getContribuintes()).isEmpty();
    }

    @Test
    void taxaZeroOuNula_naoRegistraSaidaDeTaxa() {
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("100.00"), null,
            "Pagamento de inscrição — Retiro (João)", pessoaId, "João");
        verify(movimentacaoRepository, times(1)).save(any());

        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("100.00"), BigDecimal.ZERO,
            "Pagamento de inscrição — Retiro (João)", pessoaId, "João");
        verify(movimentacaoRepository, times(2)).save(any()); // +1, só a entrada de novo
        verify(categoriaRepository, never()).save(argThat(c -> "Taxas de pagamento".equals(c.getNome())));
    }

    @Test
    void primeiraTaxa_criaCategoriaTaxasDePagamentoENotifica() {
        UUID adminId = UUID.randomUUID();
        UUID tesoureiroId = UUID.randomUUID();
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA"))
            .thenReturn(List.of(Usuario.builder().id(adminId).build()));
        when(usuarioRepository.findByIgrejaIdAndCapacidadeAndAtivoTrue(igrejaId, "TESOUREIRO"))
            .thenReturn(List.of(Usuario.builder().id(tesoureiroId).build()));

        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("104.70"), new BigDecimal("4.70"),
            "Pagamento de inscrição — Congresso (João)", pessoaId, "João");

        ArgumentCaptor<CategoriaFinanceira> cat = ArgumentCaptor.forClass(CategoriaFinanceira.class);
        verify(categoriaRepository, atLeastOnce()).save(cat.capture());
        assertThat(cat.getAllValues()).anySatisfy(c -> {
            assertThat(c.getNome()).isEqualTo("Taxas de pagamento");
            assertThat(c.getTipo()).isEqualTo(TipoCategoria.SAIDA);
        });
        verify(notificacaoService).criar(eq(TipoNotificacao.CATEGORIA_FINANCEIRA_AUTO_CRIADA),
            eq(igrejaId), eq(adminId), contains("Taxas de pagamento"), any());
        verify(notificacaoService).criar(eq(TipoNotificacao.CATEGORIA_FINANCEIRA_AUTO_CRIADA),
            eq(igrejaId), eq(tesoureiroId), contains("Taxas de pagamento"), any());
    }

    @Test
    void categoriaTaxaJaExiste_toleraVariacaoDeNome() {
        var existente = CategoriaFinanceira.builder().id(UUID.randomUUID())
            .nome("Taxa de Pagamento").tipo(TipoCategoria.SAIDA).build();
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), argThat(s -> s.contains("taxa de pagamento"))))
            .thenReturn(List.of(existente));

        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("104.70"), new BigDecimal("4.70"),
            "Pagamento de inscrição — Congresso (João)", pessoaId, "João");

        verify(categoriaRepository, never()).save(argThat(c -> "Taxas de pagamento".equals(c.getNome())));
        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());
        assertThat(mov.getAllValues()).anySatisfy(m ->
            assertThat(m.getCategoria()).isEqualTo(existente));
    }

    @Test
    void estorno_registraSaidaBrutaEmEventosEDevolucaoDeTaxa() {
        service.registrarSaidaDeEvento(igrejaId, new BigDecimal("110.49"), new BigDecimal("10.49"),
            "Reembolso — Acampamento (João)", pessoaId, "João");

        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());

        var saidaEventos = mov.getAllValues().stream()
            .filter(m -> m.getCategoria().getNome().equals("Eventos")).findFirst().orElseThrow();
        assertThat(saidaEventos.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(saidaEventos.getValor()).isEqualByComparingTo("110.49");

        var entradaTaxa = mov.getAllValues().stream()
            .filter(m -> m.getCategoria().getNome().equals("Taxas de pagamento")).findFirst().orElseThrow();
        assertThat(entradaTaxa.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(entradaTaxa.getValor()).isEqualByComparingTo("10.49");
        assertThat(entradaTaxa.getDescricao()).isEqualTo("Devolução de taxa — Acampamento (João)");
    }

    @Test
    void estornoSemTaxaDevolvida_soRegistraSaidaEmEventos() {
        service.registrarSaidaDeEvento(igrejaId, new BigDecimal("110.49"), BigDecimal.ZERO,
            "Reembolso — Acampamento (João)", pessoaId, "João");
        verify(movimentacaoRepository, times(1)).save(any());
    }

    @Test
    void contribuinteSemCadastro_usaNomeExternoNaEntrada() {
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("101.00"), new BigDecimal("1.00"),
            "Pagamento de inscrição — Feira (Convidado Zé)", null, "Convidado Zé");

        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());
        var entrada = mov.getAllValues().stream()
            .filter(m -> m.getTipo() == TipoMovimentacao.ENTRADA).findFirst().orElseThrow();
        assertThat(entrada.getContribuintes()).hasSize(1);
        assertThat(entrada.getContribuintes().get(0).getPessoa()).isNull();
        assertThat(entrada.getContribuintes().get(0).getNomeExterno()).isEqualTo("Convidado Zé");
    }
}
