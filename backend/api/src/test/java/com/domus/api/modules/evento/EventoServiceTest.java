package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.foto.FotoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.shared.DTO.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class EventoServiceTest {

    EventoRepository eventoRepository;
    IgrejaRepository igrejaRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    InscricaoService inscricaoService;
    InscricaoRepository inscricaoRepository;
    FotoService fotoService;
    ElegibilidadeService elegibilidadeService;
    PessoaRepository pessoaRepository;
    LocalEventoRepository localEventoRepository;
    UsuarioRepository usuarioRepository;
    FamiliaIgrejaService familiaIgrejaService;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    com.domus.api.modules.evento.serie.EventoSerieRepository eventoSerieRepository;
    EventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        inscricaoService = mock(InscricaoService.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        fotoService = mock(FotoService.class);
        elegibilidadeService = mock(ElegibilidadeService.class);
        pessoaRepository = mock(PessoaRepository.class);
        localEventoRepository = mock(LocalEventoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        eventoSerieRepository = mock(com.domus.api.modules.evento.serie.EventoSerieRepository.class);

        service = new EventoService(
                eventoRepository, igrejaRepository, cacheEvictor, outboxRegistrador,
                inscricaoService, inscricaoRepository, fotoService, elegibilidadeService, pessoaRepository,
                localEventoRepository, usuarioRepository, familiaIgrejaService, notificacaoService,
                eventoSerieRepository
        );

        when(familiaIgrejaService.idsDaFamiliaCompleta(any())).thenReturn(Set.of(igrejaId));

        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        Pessoa pessoa = new Pessoa();
        pessoa.setId(UUID.randomUUID());
        pessoa.setNome("Test User");
        pessoa.setVinculo(Vinculo.MEMBRO);

        Usuario usuario = new Usuario();
        usuario.setId(usuarioId);
        usuario.setPessoa(pessoa);
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(Optional.of(usuario));

        when(fotoService.buscarParaVincular(any(), any())).thenReturn(null);
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.tiposUsadosPorFrequencia(any())).thenReturn(java.util.List.of());
    }

    private EventoRequest requestComRestricao(Boolean valor) {
        return requestComResponsavel(null, valor);
    }

    private EventoRequest requestComResponsavel(UUID responsavelPessoaId) {
        return requestComResponsavel(responsavelPessoaId, false);
    }

    private EventoRequest requestComResponsavel(UUID responsavelPessoaId, Boolean restritoPropriaIgreja) {
        return new EventoRequest(
                "Culto Dominical",
                "Descrição do evento",
                LocalDateTime.now().plusDays(1),
                null,
                null,
                "Salão Social",
                "Culto",
                responsavelPessoaId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                restritoPropriaIgreja,
                null,
                null
        );
    }

    private EventoRequest requestComRecorrencia(com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest recorrencia) {
        return new EventoRequest(
                "Culto Dominical", "Descrição do evento", LocalDateTime.now().plusDays(1),
                null, null, "Salão Social", "Culto", null, null, null, null, null, null,
                null, null, false, false, false, false, null, recorrencia);
    }

    @Test
    void cadastrarEventoComRecorrenciaCriaASerieEAPrimeiraOcorrencia() {
        var recorrencia = new com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest(
                com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL, 1,
                java.util.Set.of(com.domus.api.modules.celula.DiaSemana.QUINTA), null, null, null);
        EventoRequest req = requestComRecorrencia(recorrencia);
        when(eventoSerieRepository.save(any())).thenAnswer(inv -> {
            var serie = (com.domus.api.modules.evento.serie.EventoSerie) inv.getArgument(0);
            serie.setId(UUID.randomUUID());
            return serie;
        });

        EventoResponse response = service.cadastrarEvento(req, igrejaId, usuarioId);

        assertThat(response.serieId()).isNotNull();
        assertThat(response.divergeDaSerie()).isFalse();
    }

    @Test
    void cadastrarEventoSemRecorrenciaNaoCriaSerie() {
        EventoRequest req = requestComRestricao(false);
        service.cadastrarEvento(req, igrejaId, usuarioId);
        verify(eventoSerieRepository, never()).save(any());
        verify(eventoRepository).save(argThat(e -> e.getSerie() == null));
    }

    @Test
    void cadastrarEventoGravaRestritoPropriaIgrejaComoTrue() {
        EventoRequest req = requestComRestricao(true);
        EventoResponse response = service.cadastrarEvento(req, igrejaId, usuarioId);
        assertThat(response).isNotNull();
        verify(eventoRepository).save(argThat(e -> e.isRestritoPropriaIgreja()));
    }

    @Test
    void cadastrarEventoResponseReflecteRestritoPropriaIgrejaTrue() {
        EventoRequest req = requestComRestricao(true);
        EventoResponse response = service.cadastrarEvento(req, igrejaId, usuarioId);
        assertThat(response.restritoPropriaIgreja()).isTrue();
    }

    @Test
    void atualizarEventoResponseReflecteRestritoPropriaIgrejaSalvo() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .restritoPropriaIgreja(false)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        EventoRequest req = requestComRestricao(true);
        EventoResponse response = service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        assertThat(response.restritoPropriaIgreja()).isTrue();
    }

    @Test
    void cadastrarEventoSemInformarRestricaoGravaFalse() {
        EventoRequest req = requestComRestricao(null);
        service.cadastrarEvento(req, igrejaId, usuarioId);
        verify(eventoRepository).save(argThat(e -> !e.isRestritoPropriaIgreja()));
    }

    @Test
    void atualizarEventoGravaRestritoPropriaIgrejaComoTrue() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .restritoPropriaIgreja(false)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        EventoRequest req = requestComRestricao(true);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        assertThat(existente.isRestritoPropriaIgreja()).isTrue();
    }

    @Test
    void atualizarEventoComEscopoEstaMarcaDivergeDaSerie() {
        UUID eventoId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .serie(com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).build())
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

        EventoRequest req = requestComRestricao(false);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        assertThat(existente.isDivergeDaSerie()).isTrue();
    }

    @Test
    void atualizarEventoAvulsoIgnoraEscopo() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build(); // sem série
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

        EventoRequest req = requestComRestricao(false);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        assertThat(existente.isDivergeDaSerie()).isFalse(); // nunca marca quem não tem série
    }

    @Test
    void atualizarEventoComEscopoSerieAtualizaTodasAsFuturasAgendadas() {
        UUID eventoId = UUID.randomUUID();
        UUID outraOcorrenciaId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).build();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1))
                .serie(serie).build();
        Evento outraFutura = Evento.builder()
                .id(outraOcorrenciaId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(8))
                .serie(serie).divergeDaSerie(true).build(); // divergência antiga — deve ser limpa
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(eq(serieId), any()))
                .thenReturn(List.of(existente, outraFutura));

        EventoRequest req = requestComRestricao(false);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.SERIE);

        assertThat(outraFutura.getTitulo()).isEqualTo(req.titulo());
        assertThat(outraFutura.isDivergeDaSerie()).isFalse();
    }

    @Test
    void atualizarEventoComEscopoEstaESeguintesDivideASerie() {
        UUID eventoId = UUID.randomUUID();
        UUID outraFuturaId = UUID.randomUUID();
        UUID serieAntigaId = UUID.randomUUID();
        var serieAntiga = com.domus.api.modules.evento.serie.EventoSerie.builder()
                .id(serieAntigaId)
                .frequencia(com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL)
                .intervalo(1).diasSemana("QUINTA").build();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1))
                .serie(serieAntiga).build();
        Evento outraFutura = Evento.builder()
                .id(outraFuturaId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(8))
                .serie(serieAntiga).build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(eq(serieAntigaId), any()))
                .thenReturn(List.of(existente, outraFutura));
        when(eventoSerieRepository.save(any())).thenAnswer(inv -> {
            var s = (com.domus.api.modules.evento.serie.EventoSerie) inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        EventoRequest req = requestComRestricao(false);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId, false,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA_E_SEGUINTES);

        assertThat(serieAntiga.getDataFim()).isEqualTo(
                existente.getInicioEm().toLocalDate().minusDays(1));
        assertThat(outraFutura.getSerie()).isNotSameAs(serieAntiga);
        assertThat(outraFutura.getSerie().getFrequencia())
                .isEqualTo(com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL);
    }

    @Test
    void atualizarEventoNotificaInscritosQuandoDataMuda() {
        UUID eventoId = UUID.randomUUID();
        UUID pessoaIdInscrito = UUID.randomUUID();
        UUID usuarioIdInscrito = UUID.randomUUID();
        LocalDateTime dataAntiga = LocalDateTime.now().plusDays(1);
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(dataAntiga)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(inscricaoRepository.findPessoaIdsByEventoIdAndStatus(eventoId, com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(pessoaIdInscrito));
        when(usuarioRepository.findByPessoaId(pessoaIdInscrito))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioIdInscrito).build()));

        EventoRequest req = requestComRestricao(false);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_ALTERADO), eq(igrejaId),
                eq(usuarioIdInscrito), anyString(), eq("/eventos?detalhe=" + eventoId));
    }

    @Test
    void atualizarEventoNaoConsultaInscritosQuandoDataELocalNaoMudam() {
        UUID eventoId = UUID.randomUUID();
        EventoRequest req = requestComRestricao(false);
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(req.inicioEm())
                .localTexto(req.localTexto())
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(inscricaoRepository, never()).findPessoaIdsByEventoIdAndStatus(any(), any());
    }

    @Test
    void atualizarEventoNaoNotificaAtorQuandoEleMesmoEstaInscrito() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(inscricaoRepository.findPessoaIdsByEventoIdAndStatus(eq(eventoId), any()))
                .thenReturn(List.of(UUID.randomUUID()));
        // O próprio ator (quem está editando) é um dos inscritos — não deve receber a notificação.
        when(usuarioRepository.findByPessoaId(any()))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioId).build()));

        EventoRequest req = requestComRestricao(false);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void cadastrarEventoNotificaNovoResponsavel() {
        UUID pessoaResponsavelId = UUID.randomUUID();
        UUID usuarioResponsavelId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoaResponsavelId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioResponsavelId).build()));
        Pessoa responsavel = new Pessoa();
        responsavel.setId(pessoaResponsavelId);
        when(pessoaRepository.findByIdAndIgrejaId(pessoaResponsavelId, igrejaId)).thenReturn(Optional.of(responsavel));

        EventoRequest req = requestComResponsavel(pessoaResponsavelId);
        service.cadastrarEvento(req, igrejaId, usuarioId);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.RESPONSAVEL_EVENTO), eq(igrejaId),
                eq(usuarioResponsavelId), anyString(), anyString());
    }

    @Test
    void cadastrarEventoNaoNotificaQuandoAtorEOProprioResponsavel() {
        UUID pessoaResponsavelId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoaResponsavelId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioId).build()));
        Pessoa responsavel = new Pessoa();
        responsavel.setId(pessoaResponsavelId);
        when(pessoaRepository.findByIdAndIgrejaId(pessoaResponsavelId, igrejaId)).thenReturn(Optional.of(responsavel));

        EventoRequest req = requestComResponsavel(pessoaResponsavelId);
        service.cadastrarEvento(req, igrejaId, usuarioId);

        verify(notificacaoService, never()).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.RESPONSAVEL_EVENTO), any(), any(), any(), any());
    }

    @Test
    void cadastrarEventoNotificaTodosOsUsuariosAtivosDaIgrejaMenosQuemCadastrou() {
        UUID outroUsuarioId = UUID.randomUUID();
        when(usuarioRepository.findIdsAtivosPorIgreja(igrejaId))
                .thenReturn(List.of(usuarioId, outroUsuarioId));

        EventoRequest req = requestComRestricao(false);
        service.cadastrarEvento(req, igrejaId, usuarioId);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.NOVO_EVENTO), eq(igrejaId),
                eq(outroUsuarioId), anyString(), anyString());
        verify(notificacaoService, never()).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.NOVO_EVENTO), any(), eq(usuarioId), any(), any());
    }

    @Test
    void cadastrarEventoDeSerieNotificaComTextoDeLembrete() {
        UUID outroUsuarioId = UUID.randomUUID();
        when(usuarioRepository.findIdsAtivosPorIgreja(igrejaId)).thenReturn(List.of(outroUsuarioId));
        when(eventoSerieRepository.save(any())).thenAnswer(inv -> {
            var s = (com.domus.api.modules.evento.serie.EventoSerie) inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
        var recorrencia = new com.domus.api.modules.evento.serie.DTOs.RecorrenciaRequest(
                com.domus.api.modules.evento.serie.FrequenciaRecorrencia.SEMANAL, 1,
                java.util.Set.of(com.domus.api.modules.celula.DiaSemana.QUINTA), null, null, null);
        EventoRequest req = requestComRecorrencia(recorrencia);

        service.cadastrarEvento(req, igrejaId, usuarioId);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.NOVO_EVENTO), eq(igrejaId),
                eq(outroUsuarioId),
                argThat(texto -> !texto.startsWith("Novo evento") && texto.contains("Vem participar")),
                anyString());
    }

    @Test
    void atualizarEventoNotificaSoQuandoResponsavelMuda() {
        UUID eventoId = UUID.randomUUID();
        UUID pessoaResponsavelAntigaId = UUID.randomUUID();
        UUID pessoaResponsavelNovaId = UUID.randomUUID();
        UUID usuarioResponsavelNovoId = UUID.randomUUID();
        Pessoa responsavelAntigo = new Pessoa();
        responsavelAntigo.setId(pessoaResponsavelAntigaId);
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .responsavel(responsavelAntigo)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        Pessoa responsavelNovo = new Pessoa();
        responsavelNovo.setId(pessoaResponsavelNovaId);
        when(pessoaRepository.findByIdAndIgrejaId(pessoaResponsavelNovaId, igrejaId)).thenReturn(Optional.of(responsavelNovo));
        when(usuarioRepository.findByPessoaId(pessoaResponsavelNovaId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioResponsavelNovoId).build()));

        EventoRequest req = requestComResponsavel(pessoaResponsavelNovaId);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.RESPONSAVEL_EVENTO), eq(igrejaId),
                eq(usuarioResponsavelNovoId), anyString(), anyString());
    }

    @Test
    void atualizarEventoNaoNotificaResponsavelQuandoNaoMudou() {
        UUID eventoId = UUID.randomUUID();
        UUID pessoaResponsavelId = UUID.randomUUID();
        Pessoa responsavel = new Pessoa();
        responsavel.setId(pessoaResponsavelId);
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .responsavel(responsavel)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(pessoaRepository.findByIdAndIgrejaId(pessoaResponsavelId, igrejaId)).thenReturn(Optional.of(responsavel));

        EventoRequest req = requestComResponsavel(pessoaResponsavelId);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(notificacaoService, never()).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.RESPONSAVEL_EVENTO), any(), any(), any(), any());
    }

    @Test
    void arquivarEventoNotificaInscritos() {
        UUID eventoId = UUID.randomUUID();
        UUID pessoaIdInscrito = UUID.randomUUID();
        UUID usuarioIdInscrito = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(inscricaoRepository.findPessoaIdsByEventoIdAndStatus(eventoId, com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(pessoaIdInscrito));
        when(usuarioRepository.findByPessoaId(pessoaIdInscrito))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioIdInscrito).build()));

        service.arquivarEvento(eventoId, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_ALTERADO), eq(igrejaId),
                eq(usuarioIdInscrito), anyString(), eq("/eventos"));
    }

    @Test
    void arquivarEventoNaoNotificaAtorQuandoEleMesmoEstaInscrito() {
        UUID eventoId = UUID.randomUUID();
        UUID pessoaIdInscrito = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(inscricaoRepository.findPessoaIdsByEventoIdAndStatus(eventoId, com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(pessoaIdInscrito));
        when(usuarioRepository.findByPessoaId(pessoaIdInscrito))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioId).build()));

        service.arquivarEvento(eventoId, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void arquivarEventoComEscopoSerieArquivaTodasAsFuturasEDesativaASerie() {
        UUID eventoId = UUID.randomUUID();
        UUID outraFuturaId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).ativa(true).build();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).serie(serie).build();
        Evento outraFutura = Evento.builder()
                .id(outraFuturaId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(8)).serie(serie).build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));
        when(eventoRepository.findBySerieIdAndInicioEmGreaterThanEqual(eq(serieId), any()))
                .thenReturn(List.of(existente, outraFutura));

        service.arquivarEvento(eventoId, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.SERIE);

        verify(eventoRepository).delete(existente);
        verify(eventoRepository).delete(outraFutura);
        assertThat(serie.isAtiva()).isFalse();
    }

    @Test
    void arquivarEventoComEscopoEstaArquivaSoAqueleDia() {
        UUID eventoId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).ativa(true).build();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).serie(serie).build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(existente));

        service.arquivarEvento(eventoId, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(eventoRepository).delete(existente);
        verify(eventoRepository, never()).findBySerieIdAndInicioEmGreaterThanEqual(any(), any());
        assertThat(serie.isAtiva()).isTrue(); // série continua — só esta ocorrência sumiu
    }

    @Test
    void restaurarEventoAvulsoComEscopoEstaRestauraSoAqueleId() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(eventoRepository.restaurarPorId(eventoId, igrejaId)).thenReturn(1);

        service.restaurar(eventoId, igrejaId, com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(eventoRepository).restaurarPorId(eventoId, igrejaId);
        verify(eventoRepository, never()).restaurarPorSerie(any(), any());
        verify(eventoRepository, never()).restaurarPorSerieAPartirDe(any(), any(), any());
    }

    @Test
    void restaurarComEscopoSerieRestauraTodasSemFiltroDeDataEReativaASerie() {
        UUID eventoId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).ativa(false).build();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).serie(serie).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(eventoRepository.restaurarPorSerie(serieId, igrejaId)).thenReturn(3);

        service.restaurar(eventoId, igrejaId, com.domus.api.modules.evento.serie.EscopoEdicaoEvento.SERIE);

        verify(eventoRepository).restaurarPorSerie(serieId, igrejaId);
        verify(eventoRepository, never()).restaurarPorId(any(), any());
        assertThat(serie.isAtiva()).isTrue();
    }

    @Test
    void restaurarComEscopoEstaESeguintesRestauraSoAPartirDaData() {
        UUID eventoId = UUID.randomUUID();
        UUID serieId = UUID.randomUUID();
        var dataDaOcorrencia = LocalDateTime.now().plusDays(1);
        var serie = com.domus.api.modules.evento.serie.EventoSerie.builder().id(serieId).ativa(false).build();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(dataDaOcorrencia).serie(serie).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(eventoRepository.restaurarPorSerieAPartirDe(serieId, igrejaId, dataDaOcorrencia)).thenReturn(2);

        service.restaurar(eventoId, igrejaId, com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA_E_SEGUINTES);

        verify(eventoRepository).restaurarPorSerieAPartirDe(serieId, igrejaId, dataDaOcorrencia);
        assertThat(serie.isAtiva()).isTrue();
    }

    @Test
    void restaurarLancaNotFoundQuandoNaoAcheiNenhumaLinha() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical").inicioEm(LocalDateTime.now().plusDays(1)).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(eventoRepository.restaurarPorId(eventoId, igrejaId)).thenReturn(0);

        assertThatThrownBy(() ->
                service.restaurar(eventoId, igrejaId, com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }

    @Test
    void listarEventosIncluiCompartilhadosDaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));

        Evento meu = evento(igrejaId, false);
        Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
        Page<Evento> pagina = new PageImpl<>(List.of(meu, compartilhado));
        when(eventoRepository.buscarPorFamilia(eq(igrejaId),
                argThat(arr -> Set.of(arr).equals(Set.of(igrejaId, outraIgrejaId))),
                isNull(), isNull(), isNull(), any(), any())).thenReturn(pagina);

        PagedResponse<EventoResponse> resposta = service.listarEventos(
                igrejaId, null, null, null, "ADMIN_IGREJA", PageRequest.of(0, 12));

        assertThat(resposta.getContent()).hasSize(2);
    }

    @Test
    void listarEventosAcessoComumNuncaGerenciaMesmoNaPropriaIgreja() {
        Evento meu = evento(igrejaId, false);
        Page<Evento> pagina = new PageImpl<>(List.of(meu));
        when(eventoRepository.buscarPorFamilia(eq(igrejaId),
                argThat(arr -> Set.of(arr).equals(Set.of(igrejaId))),
                isNull(), isNull(), isNull(), any(), any())).thenReturn(pagina);

        PagedResponse<EventoResponse> resposta = service.listarEventos(
                igrejaId, null, null, null, "ACESSO_COMUM", PageRequest.of(0, 12));

        assertThat(resposta.getContent().get(0).podeGerenciarEsteEvento()).isFalse();
    }

    @Test
    void listarEventosAdminGerenciaEventoDaPropriaIgreja() {
        Evento meu = evento(igrejaId, false);
        Page<Evento> pagina = new PageImpl<>(List.of(meu));
        when(eventoRepository.buscarPorFamilia(eq(igrejaId),
                argThat(arr -> Set.of(arr).equals(Set.of(igrejaId))),
                isNull(), isNull(), isNull(), any(), any())).thenReturn(pagina);

        PagedResponse<EventoResponse> resposta = service.listarEventos(
                igrejaId, null, null, null, "ADMIN_IGREJA", PageRequest.of(0, 12));

        assertThat(resposta.getContent().get(0).podeGerenciarEsteEvento()).isTrue();
    }

    @Test
    void buscarPorIdRetornaEventoCompartilhadoDeOutraIgrejaDaFamilia() {
        UUID eventoId = UUID.randomUUID();
        UUID outraIgrejaId = UUID.randomUUID();
        Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(compartilhado));

        EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA");

        assertThat(response.podeGerenciarEsteEvento()).isFalse();
    }

    @Test
    void buscarPorIdRecusaEventoRestritoDeOutraIgreja() {
        UUID eventoId = UUID.randomUUID();
        UUID outraIgrejaId = UUID.randomUUID();
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /** Tela de Arquivados precisa abrir o detalhe de um evento arquivado, igual célula/ministério. */
    @Test
    void buscarPorIdEnxergaEventoArquivadoDaPropriaIgreja() {
        UUID eventoId = UUID.randomUUID();
        Evento arquivado = evento(igrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.empty());
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .thenReturn(Optional.of(arquivado));

        EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA");

        assertThat(response).isNotNull();
        assertThat(response.podeGerenciarEsteEvento()).isTrue();
    }

    @Test
    void buscarPorIdDaPropriaIgrejaSempreDeixaGerenciar() {
        UUID eventoId = UUID.randomUUID();
        Evento meu = evento(igrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(meu));

        EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA");

        assertThat(response.podeGerenciarEsteEvento()).isTrue();
    }

    @Test
    void buscarPorIdAcessoComumNuncaGerencia() {
        UUID eventoId = UUID.randomUUID();
        Evento meu = evento(igrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(meu));

        EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ACESSO_COMUM");

        assertThat(response.podeGerenciarEsteEvento()).isFalse();
    }

    private Evento evento(UUID igrejaId, boolean restrito) {
        return Evento.builder()
                .id(UUID.randomUUID())
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .restritoPropriaIgreja(restrito)
                .build();
    }

    private Evento eventoDeOutraIgreja(UUID igrejaId, boolean restrito) {
        return evento(igrejaId, restrito);
    }

    @Test
    void atualizarEventoSemInformarRestricaoGravaFalse() {
        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .restritoPropriaIgreja(true)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        EventoRequest req = requestComRestricao(null);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        assertThat(existente.isRestritoPropriaIgreja()).isFalse();
    }

    @Test
    void elegibilidadeFuncionaParaEventoCompartilhadoDeOutraIgreja() {
        UUID eventoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID outraIgrejaId = UUID.randomUUID();
        Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(compartilhado));
        Pessoa pessoa = new Pessoa();
        pessoa.setId(pessoaId);
        pessoa.setNome("Maria");
        pessoa.setVinculo(Vinculo.MEMBRO);
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa));
        when(elegibilidadeService.avaliar(compartilhado, pessoa))
                .thenReturn(new com.domus.api.modules.evento.elegibilidade.Elegibilidade(true, List.of()));

        var response = service.elegibilidade(eventoId, pessoaId, igrejaId);

        assertThat(response).isNotNull();
    }

    @Test
    void atualizarEventoLimpaCacheDeTodaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));

        UUID eventoId = UUID.randomUUID();
        Evento existente = Evento.builder()
                .id(eventoId)
                .igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Culto Dominical")
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        EventoRequest req = requestComRestricao(null);
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId,
                com.domus.api.modules.evento.serie.EscopoEdicaoEvento.ESTA);

        verify(cacheEvictor).evictPorIgreja("eventos", igrejaId);
        verify(cacheEvictor).evictPorIgreja("eventos", outraIgrejaId);
    }
}
