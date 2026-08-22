package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.ConflitoNegocioException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InscricaoPresencaTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    PessoaRepository pessoaRepository;
    UsuarioRepository usuarioRepository;
    FamiliaIgrejaService familiaIgrejaService;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository campoPersonalizadoRepository;
    com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository respostaCampoPersonalizadoRepository;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID inscricaoId = UUID.randomUUID();
    UUID acompanhanteId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        acompanhanteRepository = mock(AcompanhanteRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        when(familiaIgrejaService.idsDaFamiliaCompleta(any())).thenReturn(java.util.Set.of(igrejaId));
        ElegibilidadeService elegibilidadeService = new ElegibilidadeService(List.of());
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        campoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository.class);
        respostaCampoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, pessoaRepository, usuarioRepository, elegibilidadeService,
                familiaIgrejaService, notificacaoService, campoPersonalizadoRepository, respostaCampoPersonalizadoRepository);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(boolean controlaPresenca) {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Culto de Celebração").inicioEm(LocalDateTime.now().minusHours(2))
                .requerInscricao(true).controlaPresenca(controlaPresenca)
                .build();
    }

    private InscricaoEvento inscricao(Evento evento) {
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Maria").build();
        return InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CONFIRMADA).build();
    }

    private InscricaoEvento inscricaoCancelada(Evento evento) {
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("João").build();
        return InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CANCELADA).build();
    }

    @Test
    void marcarTodosPresentes_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.marcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);

        verify(inscricaoRepository, never()).listarPorEvento(any());
    }

    @Test
    void marcarTodosPresentes_marcaInscritoEAcompanhantes_quandoControlaPresenca() {
        Evento evento = evento(true);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));

        InscricaoEvento inscricao = inscricao(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        inscricao.setAcompanhantes(new java.util.ArrayList<>(List.of(acompanhante)));

        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(List.of(inscricao));

        int marcados = service.marcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA");

        assertThat(marcados).isEqualTo(2); // 1 inscrito + 1 acompanhante
        assertThat(inscricao.isCompareceu()).isTrue();
        assertThat(acompanhante.isCompareceu()).isTrue();
        verify(inscricaoRepository).save(inscricao);
    }

    @Test
    void desmarcarTodosPresentes_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.desmarcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);

        verify(inscricaoRepository, never()).listarPorEvento(any());
    }

    @Test
    void desmarcarTodosPresentes_desmarcaInscritoEAcompanhantes_quandoControlaPresenca() {
        Evento evento = evento(true);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));

        InscricaoEvento inscricao = inscricao(evento);
        inscricao.setCompareceu(true);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").compareceu(true).build();
        inscricao.setAcompanhantes(new java.util.ArrayList<>(List.of(acompanhante)));

        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(List.of(inscricao));

        int desmarcados = service.desmarcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA");

        assertThat(desmarcados).isEqualTo(2); // 1 inscrito + 1 acompanhante
        assertThat(inscricao.isCompareceu()).isFalse();
        assertThat(acompanhante.isCompareceu()).isFalse();
        verify(inscricaoRepository).save(inscricao);
    }

    @Test
    void desmarcarTodosPresentes_recusaSemPermissao() {
        assertThatThrownBy(() -> service.desmarcarTodosPresentes(eventoId, igrejaId, "ACESSO_COMUM"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        verify(eventoRepository, never()).findByIdAndIgrejaId(any(), any());
    }

    @Test
    void marcarPresencaInscricao_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        InscricaoEvento inscricao = inscricao(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() ->
                service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);
    }

    @Test
    void marcarPresencaInscricao_marcaEDesmarca_individualmente() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ADMIN_IGREJA");
        assertThat(inscricao.isCompareceu()).isTrue();

        service.marcarPresencaInscricao(eventoId, inscricaoId, false, igrejaId, "ADMIN_IGREJA");
        assertThat(inscricao.isCompareceu()).isFalse();
    }

    @Test
    void marcarPresencaInscricao_recusaSemPermissao() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() ->
                service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ACESSO_COMUM"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void marcarPresencaInscricao_recusa409_quandoInscricaoCancelada() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricaoCancelada(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() ->
                service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);

        assertThat(inscricao.isCompareceu()).isFalse();
        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void marcarPresencaInscricao_naoEnxergaInscricaoDeOutraIgrejaDaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(java.util.Set.of(igrejaId, outraIgrejaId));
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, java.util.Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricao));
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void marcarPresencaAcompanhante_recusa409_quandoInscricaoCancelada() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricaoCancelada(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() ->
                service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);

        assertThat(acompanhante.isCompareceu()).isFalse();
        verify(acompanhanteRepository, never()).save(any());
    }

    @Test
    void marcarPresencaAcompanhante_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        InscricaoEvento inscricao = inscricao(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() ->
                service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);
    }

    @Test
    void marcarPresencaAcompanhante_marcaEDesmarca() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA");
        assertThat(acompanhante.isCompareceu()).isTrue();
    }

    @Test
    void marcarPresencaAcompanhante_naoEncontrado_deOutraIgreja() {
        Evento evento = evento(true);
        Igreja outraIgreja = new Igreja();
        outraIgreja.setId(UUID.randomUUID());
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(outraIgreja).evento(evento)
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(outraIgreja).nome("Maria").build())
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() ->
                service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
