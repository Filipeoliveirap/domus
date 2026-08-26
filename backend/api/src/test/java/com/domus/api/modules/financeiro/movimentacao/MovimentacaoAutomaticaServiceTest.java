package com.domus.api.modules.financeiro.movimentacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
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
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA")).thenReturn(List.of());
        when(usuarioRepository.findByIgrejaIdAndCapacidadeAndAtivoTrue(igrejaId, "TESOUREIRO")).thenReturn(List.of());
    }

    private CategoriaFinanceira categoriaExistente(String nome) {
        return CategoriaFinanceira.builder().id(UUID.randomUUID())
            .igreja(Igreja.builder().id(igrejaId).build()).nome(nome).tipo(TipoCategoria.AMBOS).build();
    }

    @Test
    void reaproveitaCategoriaExistenteComNomeVariante() {
        // "eventos" minúsculo, plural — não deve criar uma nova "Eventos" duplicada.
        var categoria = categoriaExistente("eventos");
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), any())).thenReturn(List.of(categoria));

        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("50.00"), "Pagamento — Retiro (Maria)", null, "Maria");

        verify(categoriaRepository, never()).save(any());
        var captor = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoria()).isEqualTo(categoria);
    }

    @Test
    void criaCategoriaEventosQuandoNaoExisteENotificaAdminETesoureiro() {
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), any())).thenReturn(List.of());
        when(categoriaRepository.save(any())).thenAnswer(inv -> {
            CategoriaFinanceira c = inv.getArgument(0);
            return CategoriaFinanceira.builder().id(UUID.randomUUID()).igreja(c.getIgreja())
                .nome(c.getNome()).tipo(c.getTipo()).build();
        });
        UUID adminId = UUID.randomUUID();
        UUID tesoureiroId = UUID.randomUUID();
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA"))
            .thenReturn(List.of(Usuario.builder().id(adminId).build()));
        when(usuarioRepository.findByIgrejaIdAndCapacidadeAndAtivoTrue(igrejaId, "TESOUREIRO"))
            .thenReturn(List.of(Usuario.builder().id(tesoureiroId).build()));

        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("50.00"), "Pagamento — Retiro (Maria)", null, "Maria");

        var categoriaCaptor = ArgumentCaptor.forClass(CategoriaFinanceira.class);
        verify(categoriaRepository).save(categoriaCaptor.capture());
        assertThat(categoriaCaptor.getValue().getNome()).isEqualTo("Eventos");
        assertThat(categoriaCaptor.getValue().getTipo()).isEqualTo(TipoCategoria.AMBOS);

        verify(notificacaoService).criar(eq(TipoNotificacao.CATEGORIA_FINANCEIRA_AUTO_CRIADA), eq(igrejaId), eq(adminId), any(), any());
        verify(notificacaoService).criar(eq(TipoNotificacao.CATEGORIA_FINANCEIRA_AUTO_CRIADA), eq(igrejaId), eq(tesoureiroId), any(), any());
    }

    @Test
    void registraEntradaComContribuinteQuandoPessoaConhecida() {
        var categoria = categoriaExistente("Eventos");
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), any())).thenReturn(List.of(categoria));
        UUID pessoaId = UUID.randomUUID();
        when(pessoaRepository.getReferenceById(pessoaId)).thenReturn(Pessoa.builder().id(pessoaId).build());

        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("50.00"), "Pagamento — Retiro (Maria)", pessoaId, "Maria");

        var captor = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository).save(captor.capture());
        var mov = captor.getValue();
        assertThat(mov.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(mov.getValor()).isEqualByComparingTo("50.00");
        assertThat(mov.getContribuintes()).hasSize(1);
        assertThat(mov.getContribuintes().get(0).getPessoa().getId()).isEqualTo(pessoaId);
        verify(outboxRegistrador).registrar(eq(TipoEntidadeOutbox.MOVIMENTACAO), eq(TipoEventoOutbox.CRIADO), eq(mov.getId()), eq(igrejaId));
        verify(cacheEvictor).evictPorIgreja("movimentacoes", igrejaId);
    }

    @Test
    void registraSaidaComNomeExternoQuandoPessoaDesconhecida() {
        // Convidado sem cadastro/acompanhante (pessoaId nulo) — o contribuinte não fica
        // vazio, entra com o nome, senão some da coluna Contribuinte/Beneficiário e do
        // relatório por contribuinte (achado revisando com o usuário, 2026-08-26).
        var categoria = categoriaExistente("Eventos");
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), any())).thenReturn(List.of(categoria));

        service.registrarSaidaDeEvento(igrejaId, new BigDecimal("50.00"), "Reembolso — Retiro (Convidado)", null, "Convidado");

        var captor = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(captor.getValue().getContribuintes()).hasSize(1);
        assertThat(captor.getValue().getContribuintes().get(0).getPessoa()).isNull();
        assertThat(captor.getValue().getContribuintes().get(0).getNomeExterno()).isEqualTo("Convidado");
    }
}
