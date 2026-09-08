package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.EventoResponsavel;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrazoInscricaoJobTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    UsuarioRepository usuarioRepository;
    NotificacaoService notificacaoService;
    PrazoInscricaoProcessador processador;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        processador = new PrazoInscricaoProcessador(
                eventoRepository, inscricaoRepository, usuarioRepository, notificacaoService);
        ReflectionTestUtils.setField(processador, "avisoPrazoDias", 3);
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(any())).thenReturn(List.of());
        lenient().when(inscricaoRepository.contarPorEventoIdEStatus(any(), any())).thenReturn(0L);
    }

    private Igreja igreja() {
        var i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento eventoComPrazo(LocalDateTime inscricoesAte) {
        return Evento.builder()
                .id(UUID.randomUUID()).igreja(igreja())
                .titulo("Congresso").inicioEm(LocalDateTime.now().plusDays(30))
                .requerInscricao(true).inscricoesAte(inscricoesAte)
                .build();
    }

    private EventoResponsavel responsavelComLogin(Evento evento, UUID usuarioId) {
        var pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Resp").build();
        var usuario = Usuario.builder().id(usuarioId).build();
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(usuario));
        return EventoResponsavel.builder().evento(evento).igreja(igreja()).pessoa(pessoa).build();
    }

    private void processar(Evento evento) {
        when(eventoRepository.findById(evento.getId())).thenReturn(Optional.of(evento));
        processador.processar(evento.getId(), LocalDateTime.now());
    }

    @Test
    void avisaResponsavelQuandoPrazoFechouUmaVezSo() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(2));
        var usuarioId = UUID.randomUUID();
        evento.getResponsaveis().add(responsavelComLogin(evento, usuarioId));

        processar(evento);
        processar(evento); // 2ª vez: carimbo já preenchido

        verify(notificacaoService, times(1)).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_FECHADO),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
    }

    @Test
    void avisaPrazoProximoDentroDaJanela() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(2));
        var usuarioId = UUID.randomUUID();
        evento.getResponsaveis().add(responsavelComLogin(evento, usuarioId));

        processar(evento);

        verify(notificacaoService).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_PROXIMO),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
        verify(notificacaoService, never()).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_FECHADO),
                any(), any(), any(), any());
    }

    @Test
    void naoAvisaPrazoProximoForaDaJanela() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(10));
        evento.getResponsaveis().add(responsavelComLogin(evento, UUID.randomUUID()));

        processar(evento);

        verifyNoInteractions(notificacaoService);
    }

    @Test
    void avisaInscricaoIncompletaUmaVezSo() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(2));
        var pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Fulano").build();
        var usuarioId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoa.getId()))
                .thenReturn(Optional.of(Usuario.builder().id(usuarioId).build()));
        var inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId()))
                .thenReturn(List.of(inscricao));

        processar(evento);

        // O "uma vez só" é garantido pelo carimbo (aviso_prazo_incompleto_em), não pelo
        // re-stub do repositório: a query já filtra quem tem o carimbo.
        assertThat(inscricao.getAvisoPrazoIncompletoEm()).isNotNull();
        verify(notificacaoService, times(1)).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_INCOMPLETA),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
        verify(inscricaoRepository).save(inscricao);
    }

    @Test
    void pulaResponsavelSemLogin() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1));
        var pessoaSemLogin = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("X").build();
        when(usuarioRepository.findByPessoaId(pessoaSemLogin.getId())).thenReturn(Optional.empty());
        evento.getResponsaveis().add(EventoResponsavel.builder()
                .evento(evento).igreja(igreja()).pessoa(pessoaSemLogin).build());
        // responsável só com nomeTexto também é pulado
        evento.getResponsaveis().add(EventoResponsavel.builder()
                .evento(evento).igreja(igreja()).nomeTexto("Pessoa removida do sistema").build());

        processar(evento);

        verifyNoInteractions(notificacaoService);
    }

    @Test
    void janelaDeProximoPerdidaNaoImpedeAvisoDeFechado() {
        // prazo já venceu e o aviso "próximo" nunca chegou a sair (avisoPrazoProximoEm nulo)
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(3));
        var usuarioId = UUID.randomUUID();
        evento.getResponsaveis().add(responsavelComLogin(evento, usuarioId));

        processar(evento);

        verify(notificacaoService).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_FECHADO),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
        verify(notificacaoService, never()).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_PROXIMO),
                any(), any(), any(), any());
    }

    @Test
    void jobIteraEEngoleExcecaoDeUmEvento() {
        var bom = eventoComPrazo(LocalDateTime.now().plusDays(2));
        var ruim = eventoComPrazo(LocalDateTime.now().plusDays(2));
        var processadorMock = mock(PrazoInscricaoProcessador.class);
        doThrow(new RuntimeException("boom")).when(processadorMock).processar(eq(ruim.getId()), any());
        when(eventoRepository.buscarComPrazoAtivoParaJob(any())).thenReturn(List.of(ruim, bom));
        var job = new PrazoInscricaoJob(eventoRepository, processadorMock);

        job.executar();

        verify(processadorMock).processar(eq(ruim.getId()), any());
        verify(processadorMock).processar(eq(bom.getId()), any());
    }
}
