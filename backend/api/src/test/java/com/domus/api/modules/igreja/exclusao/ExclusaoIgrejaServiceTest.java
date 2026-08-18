package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExclusaoIgrejaServiceTest {

    IgrejaRepository igrejaRepository;
    PessoaRepository pessoaRepository;
    EventoRepository eventoRepository;
    MovimentacaoFinanceiraRepository movimentacaoRepository;
    CelulaRepository celulaRepository;
    MinisterioRepository ministerioRepository;
    UsuarioRepository usuarioRepository;
    EmailService emailService;
    ExclusaoIgrejaService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        eventoRepository = mock(EventoRepository.class);
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        celulaRepository = mock(CelulaRepository.class);
        ministerioRepository = mock(MinisterioRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        emailService = mock(EmailService.class);
        service = new ExclusaoIgrejaService(igrejaRepository, pessoaRepository, eventoRepository,
                movimentacaoRepository, celulaRepository, ministerioRepository, usuarioRepository, emailService);
    }

    private Igreja igreja() {
        return Igreja.builder().id(igrejaId).nome("Igreja Batista Central").emailContato("contato@igreja.com").build();
    }

    @Test
    void agendaExclusaoQuandoNomeConfere() {
        Igreja igreja = igreja();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        service.agendar(igrejaId, usuarioId, "Igreja Batista Central");

        verify(igrejaRepository).marcarExclusaoAgendada(eq(igrejaId), eq(usuarioId), any());
        verify(emailService).enviar(eq("contato@igreja.com"), anyString(), anyString());
    }

    @Test
    void recusaAgendarQuandoNomeNaoConfere() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja()));

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Nome Errado"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nome");

        verify(igrejaRepository, never()).marcarExclusaoAgendada(any(), any(), any());
    }

    @Test
    void recusaAgendarSeIgrejaNaoExiste() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Qualquer"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelaSemPrecisarDeNadaAlemDoIgrejaId() {
        service.cancelar(igrejaId);

        verify(igrejaRepository).cancelarExclusaoAgendada(igrejaId);
    }

    @Test
    void resumoContaTudoQueSeraApagado() {
        when(pessoaRepository.countByIgrejaId(igrejaId)).thenReturn(42L);
        when(eventoRepository.countByIgrejaId(igrejaId)).thenReturn(10L);
        when(movimentacaoRepository.countByIgrejaId(igrejaId)).thenReturn(200L);
        when(celulaRepository.countByIgrejaId(igrejaId)).thenReturn(5L);
        when(ministerioRepository.countByIgrejaId(igrejaId)).thenReturn(3L);
        when(usuarioRepository.countByIgrejaId(igrejaId)).thenReturn(8L);
        when(igrejaRepository.buscarIdsDasFilhas(igrejaId)).thenReturn(List.of());

        ResumoExclusaoResponse resumo = service.resumo(igrejaId);

        assertThat(resumo.pessoas()).isEqualTo(42L);
        assertThat(resumo.eventos()).isEqualTo(10L);
        assertThat(resumo.igrejasVinculadas()).isEmpty();
    }
}
