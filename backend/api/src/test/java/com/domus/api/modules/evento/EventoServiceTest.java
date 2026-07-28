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
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Vinculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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

        service = new EventoService(
                eventoRepository, igrejaRepository, cacheEvictor, outboxRegistrador,
                inscricaoService, fotoService, elegibilidadeService, pessoaRepository,
                localEventoRepository, usuarioRepository
        );

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

    @Test
    void cadastrarEventoSemInformarRestricaoGravaFalse() {
        EventoRequest req = requestComRestricao(null);
        service.cadastrarEvento(req, igrejaId, usuarioId);
        verify(eventoRepository).save(argThat(e -> !e.isRestritoPropriaIgreja()));
    }
}
