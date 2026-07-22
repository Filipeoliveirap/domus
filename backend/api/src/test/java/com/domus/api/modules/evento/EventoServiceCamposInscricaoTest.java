package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Trava os campos de inscrição (vagas, preço e a restrição de exclusividade) nos dois
 * caminhos de escrita.
 *
 * <p>Existe por causa do modo de falha específico deste tipo de mudança: um campo ligado só no
 * cadastro faz a API <b>aceitar</b> a edição e <b>descartar</b> em silêncio. Não quebra teste
 * nenhum, não aparece em log — o usuário só descobre quando reabre a tela e o valor voltou.
 */
class EventoServiceCamposInscricaoTest {

    EventoRepository eventoRepository;
    IgrejaRepository igrejaRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    InscricaoService inscricaoService;
    FotoService fotoService;
    ElegibilidadeService elegibilidadeService;
    PessoaRepository pessoaRepository;
    LocalEventoRepository localEventoRepository;
    UsuarioRepository usuarioRepository;
    EventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        inscricaoService = mock(InscricaoService.class);
        fotoService = mock(FotoService.class);
        elegibilidadeService = mock(ElegibilidadeService.class);
        pessoaRepository = mock(PessoaRepository.class);
        localEventoRepository = mock(LocalEventoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new EventoService(eventoRepository, igrejaRepository, cacheEvictor,
                outboxRegistrador, inscricaoService, fotoService, elegibilidadeService, pessoaRepository,
                localEventoRepository, usuarioRepository);

        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inscricaoService.removerInscritosNaoElegiveis(any(), anyBoolean()))
                .thenReturn(0);
        when(eventoRepository.tiposUsadosPorFrequencia(any())).thenReturn(java.util.List.of());
        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        Pessoa pessoaDoUsuario = new Pessoa();
        pessoaDoUsuario.setId(UUID.randomUUID());
        pessoaDoUsuario.setNome("Fulano");
        usuario.setPessoa(pessoaDoUsuario);
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario));
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private EventoRequest request(Integer vagas, BigDecimal preco, Boolean exclusivoMembros) {
        return new EventoRequest("Retiro", "desc", LocalDateTime.now().plusDays(5), null,
                null, "Templo", null, null, null, null, null, null, null,
                vagas, preco, exclusivoMembros, true, null);
    }

    @Test
    void cadastrarGravaOsCamposDeInscricao() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja()));

        EventoResponse r = service.cadastrarEvento(
                request(50, new BigDecimal("120.00"), true), igrejaId, usuarioId);

        assertThat(r.vagas()).isEqualTo(50);
        assertThat(r.preco()).isEqualByComparingTo("120.00");
        assertThat(r.exclusivoMembros()).isTrue();
    }

    @Test
    void atualizarGravaOsCamposDeInscricao() {
        // Evento nasce SEM os campos; a edição precisa realmente persisti-los.
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.atualizarEvento(eventoId, request(30, new BigDecimal("80.50"), true), igrejaId, usuarioId);

        assertThat(existente.getVagas()).isEqualTo(30);
        assertThat(existente.getPreco()).isEqualByComparingTo("80.50");
        assertThat(existente.isExclusivoMembros()).isTrue();
    }

    @Test
    void atualizarLimpaVagasEPrecoQuandoVemNulo() {
        // Nulo é significativo aqui: vagas nula = sem limite, preço nulo = gratuito.
        // Se a atualização ignorasse o nulo, não haveria como voltar atrás de um evento pago.
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .vagas(10).preco(new BigDecimal("50.00"))
                .exclusivoMembros(true)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.atualizarEvento(eventoId, request(null, null, null), igrejaId, usuarioId);

        assertThat(existente.getVagas()).isNull();
        assertThat(existente.getPreco()).isNull();
        // Boolean ausente no JSON vira false: a atualização é substituição total (PUT),
        // não remendo parcial (PATCH). O front precisa enviar sempre o valor corrente.
        assertThat(existente.isExclusivoMembros()).isFalse();
    }

    @Test
    void atualizarDevolveQuantasInscricoesForamRemovidasAoRestringir() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(inscricaoService.removerInscritosNaoElegiveis(eventoId, false)).thenReturn(3);

        EventoResponse r = service.atualizarEvento(
                eventoId, request(null, null, false), igrejaId, usuarioId);

        assertThat(r.inscricoesRemovidas()).isEqualTo(3);
    }

    @Test
    void buscarPorIdExpoeSituacaoAgendadaQuandoAindaNaoComecou() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThat(service.buscarPorId(eventoId, igrejaId).situacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.AGENDADO);
    }

    @Test
    void buscarPorIdExpoeSituacaoEmAndamentoEntreInicioEFim() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusHours(1))
                .fimEm(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThat(service.buscarPorId(eventoId, igrejaId).situacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.EM_ANDAMENTO);
    }

    @Test
    void buscarPorIdExpoeSituacaoEncerradaAposOFim() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusDays(2))
                .fimEm(LocalDateTime.now().minusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThat(service.buscarPorId(eventoId, igrejaId).situacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.ENCERRADO);
    }

    @Test
    void semFimDeclaradoEhEmAndamentoNoMesmoDiaEEncerradoNoDiaSeguinte() {
        // Fronteira do null fimEm: considerado em andamento até o fim do PRÓPRIO dia de
        // início, encerrado a partir do dia seguinte.
        Evento comecouHoje = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Culto")
                .inicioEm(LocalDateTime.now().minusHours(2))
                .build();
        assertThat(comecouHoje.getSituacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.EM_ANDAMENTO);

        Evento comecouOntem = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Culto")
                .inicioEm(LocalDateTime.now().minusDays(1))
                .build();
        assertThat(comecouOntem.getSituacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.ENCERRADO);
    }

    @Test
    void atualizarRecusaEventoEmAndamento() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusHours(1))
                .fimEm(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizarEvento(
                eventoId, request(null, null, null), igrejaId, usuarioId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("em andamento");
    }

    @Test
    void atualizarRecusaEventoEncerrado() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusDays(2))
                .fimEm(LocalDateTime.now().minusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizarEvento(
                eventoId, request(null, null, null), igrejaId, usuarioId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("encerrado");
    }

    @Test
    void atualizarRecusaReduzirVagasAbaixoDosInscritos() {
        // A9: 20 vagas, 10 pessoas confirmadas -> não pode editar para 9.
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .vagas(20)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(inscricaoService.contarPessoasConfirmadas(eventoId)).thenReturn(10L);

        assertThatThrownBy(() -> service.atualizarEvento(
                eventoId, request(9, null, null), igrejaId, usuarioId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("10");
        assertThat(existente.getVagas()).isEqualTo(20); // não mudou nada
    }

    @Test
    void atualizarPermiteReduzirVagasParaExatamenteOTotalDeInscritos() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .vagas(20)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(inscricaoService.contarPessoasConfirmadas(eventoId)).thenReturn(10L);

        service.atualizarEvento(eventoId, request(10, null, null), igrejaId, usuarioId);

        assertThat(existente.getVagas()).isEqualTo(10);
    }

    @Test
    void atualizarPermiteLimparVagasParaNuloMesmoComInscritos() {
        // null = sem limite: nunca há o que estourar.
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .vagas(20)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.atualizarEvento(eventoId, request(null, null, null), igrejaId, usuarioId);

        assertThat(existente.getVagas()).isNull();
        verify(inscricaoService, never()).contarPessoasConfirmadas(any());
    }

    @Test
    void arquivarRecusaEventoEmAndamento() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusHours(1))
                .fimEm(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.arquivarEvento(eventoId, igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("em andamento");
    }

    @Test
    void arquivarPermiteEventoEncerrado() {
        // Decisão: arquivar é faxina normal de evento passado — não trava em ENCERRADO,
        // só em EM_ANDAMENTO (evento rolando agora).
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusDays(2))
                .fimEm(LocalDateTime.now().minusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.arquivarEvento(eventoId, igrejaId);

        verify(eventoRepository).delete(existente);
    }
}
