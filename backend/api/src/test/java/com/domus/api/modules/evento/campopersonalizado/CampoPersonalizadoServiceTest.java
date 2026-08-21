package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampoPersonalizadoServiceTest {

    CampoPersonalizadoEventoRepository campoRepository;
    RespostaCampoPersonalizadoRepository respostaRepository;
    EventoRepository eventoRepository;
    com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository;
    CampoPersonalizadoService service;

    UUID igrejaId;
    UUID eventoId;

    @BeforeEach
    void setup() {
        campoRepository = mock(CampoPersonalizadoEventoRepository.class);
        respostaRepository = mock(RespostaCampoPersonalizadoRepository.class);
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(com.domus.api.modules.evento.inscricao.InscricaoRepository.class);
        service = new CampoPersonalizadoService(campoRepository, respostaRepository, eventoRepository, inscricaoRepository);

        igrejaId = UUID.randomUUID();
        eventoId = UUID.randomUUID();
    }

    private Evento evento() {
        return Evento.builder().id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Retiro de Jovens").build();
    }

    @Test
    void salvarCriaCamposNovosQuandoIdENulo() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());
        when(campoRepository.save(any())).thenAnswer(inv -> {
            CampoPersonalizadoEvento c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var request = new CampoPersonalizadoRequest(
                null, "Tamanho da camiseta", null, TipoCampoPersonalizado.OPCAO_UNICA,
                List.of("P", "M", "G"), true, true, 0);

        List<CampoPersonalizadoResponse> resultado = service.salvar(eventoId, igrejaId, List.of(request));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).label()).isEqualTo("Tamanho da camiseta");
        assertThat(resultado.get(0).opcoes()).containsExactly("P", "M", "G");
        verify(campoRepository).save(any());
    }

    @Test
    void salvarArquivaCampoQueSumiuDaListaEnviada() {
        var existente = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Campo antigo").tipo(TipoCampoPersonalizado.TEXTO_CURTO).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));

        service.salvar(eventoId, igrejaId, List.of());

        verify(campoRepository).delete(existente);
    }

    @Test
    void salvarLancaNotFoundQuandoEventoNaoPertenceAIgreja() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salvar(eventoId, igrejaId, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
