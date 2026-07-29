package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
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
    FotoService fotoService;
    ElegibilidadeService elegibilidadeService;
    PessoaRepository pessoaRepository;
    LocalEventoRepository localEventoRepository;
    UsuarioRepository usuarioRepository;
    FamiliaIgrejaService familiaIgrejaService;
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
        fotoService = mock(FotoService.class);
        elegibilidadeService = mock(ElegibilidadeService.class);
        pessoaRepository = mock(PessoaRepository.class);
        localEventoRepository = mock(LocalEventoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);

        service = new EventoService(
                eventoRepository, igrejaRepository, cacheEvictor, outboxRegistrador,
                inscricaoService, fotoService, elegibilidadeService, pessoaRepository,
                localEventoRepository, usuarioRepository, familiaIgrejaService
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
        return new EventoRequest(
                "Culto Dominical",
                "Descrição do evento",
                LocalDateTime.now().plusDays(1),
                null,
                null,
                "Salão Social",
                "Culto",
                null,
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
                valor,
                null
        );
    }

    @Test
    void cadastrarEventoGravaRestritoPropriaIgrejaComoTrue() {
        EventoRequest req = requestComRestricao(true);
        EventoResponse response = service.cadastrarEvento(req, igrejaId, usuarioId);
        assertThat(response).isNotNull();
        verify(eventoRepository).save(argThat(e -> e.isRestritoPropriaIgreja()));
    }

    /**
     * Finding 1 do review final: restritoPropriaIgreja precisa vir na RESPOSTA da API, não
     * só na entidade — senão o front rehidrata o toggle de edição sempre como false e
     * regrava restritoPropriaIgreja=false ao salvar, desprotegendo o evento.
     */
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
        EventoResponse response = service.atualizarEvento(eventoId, req, igrejaId, usuarioId);

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
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId);

        assertThat(existente.isRestritoPropriaIgreja()).isTrue();
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
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId);

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
        service.atualizarEvento(eventoId, req, igrejaId, usuarioId);

        verify(cacheEvictor).evictPorIgreja("eventos", igrejaId);
        verify(cacheEvictor).evictPorIgreja("eventos", outraIgrejaId);
    }
}
