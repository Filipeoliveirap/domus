package com.domus.api.modules.evento;

import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EventoRelatorioServiceTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    PessoaRepository pessoaRepository;
    EventoRelatorioService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        service = new EventoRelatorioService(eventoRepository, inscricaoRepository, pessoaRepository);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(boolean controlaPresenca) {
        return Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Culto")
                .inicioEm(LocalDateTime.now().minusDays(1))
                .requerInscricao(true).controlaPresenca(controlaPresenca)
                .build();
    }

    @Test
    void relatorioIndividual_naoEncontrado_lancaExcecao() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relatorioIndividual(eventoId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void relatorioIndividual_semControlaPresenca_naoTraSecaoDeComparecimento() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento(false)));
        when(inscricaoRepository.countPessoasInscritas(eventoId)).thenReturn(10L);
        when(inscricaoRepository.countConvidadosInscritos(eventoId)).thenReturn(3L);

        var relatorio = service.relatorioIndividual(eventoId, igrejaId);

        assertThat(relatorio.inscritos().pessoas()).isEqualTo(10);
        assertThat(relatorio.inscritos().convidados()).isEqualTo(3);
        assertThat(relatorio.compareceram()).isNull();
        assertThat(relatorio.percentualIgreja()).isNull();
        verify(inscricaoRepository, never()).countPessoasCompareceram(any());
    }

    @Test
    void relatorioIndividual_comControlaPresenca_calculaPercentualIgrejaSoComPessoasCadastradas() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento(true)));
        when(inscricaoRepository.countPessoasInscritas(eventoId)).thenReturn(20L);
        when(inscricaoRepository.countConvidadosInscritos(eventoId)).thenReturn(5L);
        when(inscricaoRepository.countPessoasCompareceram(eventoId)).thenReturn(15L);
        when(inscricaoRepository.countConvidadosCompareceram(eventoId)).thenReturn(4L);
        when(pessoaRepository.countByIgrejaId(igrejaId)).thenReturn(150L);

        var relatorio = service.relatorioIndividual(eventoId, igrejaId);

        assertThat(relatorio.compareceram().pessoas()).isEqualTo(15);
        assertThat(relatorio.compareceram().convidados()).isEqualTo(4);
        // 15 pessoas cadastradas presentes / 150 pessoas ativas da igreja = 10.0% — convidado
        // NUNCA entra neste cálculo (nem nos 4 do numerador, nem em lugar nenhum do denominador).
        assertThat(relatorio.percentualIgreja()).isEqualTo(10.0);
    }
}
