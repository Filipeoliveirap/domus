package com.domus.api.modules.inicio;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.inicio.dto.InicioResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InicioServiceTest {

    PessoaRepository pessoaRepository;
    EventoRepository eventoRepository;
    FamiliaIgrejaService familiaIgrejaService;
    InicioService service;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        pessoaRepository = mock(PessoaRepository.class);
        eventoRepository = mock(EventoRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        service = new InicioService(pessoaRepository, eventoRepository, familiaIgrejaService);

        when(pessoaRepository.aniversariantesDoMes(eq(igrejaId), anyInt())).thenReturn(List.of());
    }

    private Igreja igreja(UUID id) {
        Igreja i = new Igreja();
        i.setId(id);
        return i;
    }

    private Evento evento(UUID id, UUID igrejaId) {
        return Evento.builder().id(id).igreja(igreja(igrejaId))
                .titulo("Culto").inicioEm(LocalDateTime.now().plusDays(1)).build();
    }

    @Test
    void proximosEventosIncluiCompartilhadosDaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.proximosDaFamilia(eq(igrejaId), eq(Set.of(igrejaId, outraIgrejaId)),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(evento(UUID.randomUUID(), igrejaId), evento(UUID.randomUUID(), outraIgrejaId)));

        InicioResponse response = service.carregar(igrejaId);

        assertThat(response.proximosEventos()).hasSize(2);
    }

    @Test
    void proximosEventosTrazemAIgrejaOrganizadora() {
        UUID eventoId = UUID.randomUUID();
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
        when(eventoRepository.proximosDaFamilia(eq(igrejaId), eq(Set.of(igrejaId)),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(evento(eventoId, igrejaId)));

        InicioResponse response = service.carregar(igrejaId);

        assertThat(response.proximosEventos().get(0).igrejaOrganizadora().id()).isEqualTo(igrejaId);
    }
}
