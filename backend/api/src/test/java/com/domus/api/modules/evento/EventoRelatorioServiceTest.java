package com.domus.api.modules.evento;

import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(pessoaRepository.countByIgrejaId(igrejaId)).thenReturn(50L);

        var relatorio = service.relatorioIndividual(eventoId, igrejaId);

        assertThat(relatorio.inscritos().pessoas()).isEqualTo(10);
        assertThat(relatorio.inscritos().convidados()).isEqualTo(3);
        // percentualIgrejaInscritos é SEMPRE calculado, mesmo sem controlaPresenca — não
        // depende de comparecimento, só de inscrição (10 pessoas / 50 ativas = 20%).
        assertThat(relatorio.percentualIgrejaInscritos()).isEqualTo(20.0);
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
        // 20 pessoas cadastradas inscritas / 150 ativas = 13.3%.
        assertThat(relatorio.percentualIgrejaInscritos()).isEqualTo(13.3);
    }

    private Evento eventoComTipo(UUID id, String tipo, LocalDateTime inicioEm, boolean controlaPresenca) {
        return Evento.builder()
                .id(id).igreja(igreja()).titulo("Evento " + tipo)
                .inicioEm(inicioEm).tipo(tipo)
                .requerInscricao(true).controlaPresenca(controlaPresenca)
                .build();
    }

    @Test
    void relatorioGeral_eventoMaisPopular_usaInscritosMesmoSemControlarPresenca() {
        UUID idPopular = UUID.randomUUID();
        UUID idMenor = UUID.randomUUID();
        Evento popular = eventoComTipo(idPopular, "Culto", LocalDateTime.now().minusDays(1), false);
        Evento menor = eventoComTipo(idMenor, "Culto", LocalDateTime.now().minusDays(2), false);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(popular, menor));
        when(inscricaoRepository.countPessoasInscritas(idPopular)).thenReturn(100L);
        when(inscricaoRepository.countConvidadosInscritos(idPopular)).thenReturn(20L);
        when(inscricaoRepository.countPessoasInscritas(idMenor)).thenReturn(10L);
        when(inscricaoRepository.countConvidadosInscritos(idMenor)).thenReturn(0L);
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of());

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null, PageRequest.of(0, 20));

        assertThat(relatorio.eventoMaisPopular().eventoId()).isEqualTo(idPopular);
        assertThat(relatorio.eventoMaisPopular().totalInscritos()).isEqualTo(120);
        // Nenhum evento controla presença: resumo não mente com zero.
        assertThat(relatorio.resumo().comparecimentoMedio()).isNull();
        assertThat(relatorio.resumo().participantesUnicos()).isNull();
    }

    @Test
    void relatorioGeral_tendencia_mesSemEventoControladoVemComoNull() {
        UUID idEvento = UUID.randomUUID();
        Evento evento = eventoComTipo(idEvento, "Culto", LocalDateTime.now().withDayOfMonth(1), true);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(evento));
        when(inscricaoRepository.countPessoasInscritas(idEvento)).thenReturn(10L);
        when(inscricaoRepository.countConvidadosInscritos(idEvento)).thenReturn(0L);
        when(inscricaoRepository.countPessoasCompareceram(idEvento)).thenReturn(8L);
        when(inscricaoRepository.countConvidadosCompareceram(idEvento)).thenReturn(0L);
        // Só o mês atual tem evento com controlaPresenca=true; os outros 5 meses ficam vazios.
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of(evento));
        when(inscricaoRepository.contarParticipantesUnicos(any())).thenReturn(8L);

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null, PageRequest.of(0, 20));

        assertThat(relatorio.tendencia()).hasSize(6);
        long mesesComDado = relatorio.tendencia().stream()
                .filter(p -> p.comparecimentoMedio() != null).count();
        long mesesSemDado = relatorio.tendencia().stream()
                .filter(p -> p.comparecimentoMedio() == null).count();
        assertThat(mesesComDado).isEqualTo(1);
        assertThat(mesesSemDado).isEqualTo(5); // null, NUNCA zero (Decisão 4)
    }

    @Test
    void relatorioGeral_variacao_usaComparecimento_quandoAmbosControlamPresenca() {
        UUID idAtual = UUID.randomUUID();
        UUID idAnterior = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        Evento atual = eventoComTipo(idAtual, "Retiro", agora, true);
        Evento anterior = eventoComTipo(idAnterior, "Retiro", agora.minusMonths(1), true);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.countPessoasInscritas(idAtual)).thenReturn(50L);
        when(inscricaoRepository.countConvidadosInscritos(idAtual)).thenReturn(0L);
        when(inscricaoRepository.countPessoasCompareceram(idAtual)).thenReturn(40L);
        when(inscricaoRepository.countConvidadosCompareceram(idAtual)).thenReturn(0L);
        when(eventoRepository.findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
                igrejaId, "Retiro", atual.getInicioEm())).thenReturn(java.util.Optional.of(anterior));
        when(inscricaoRepository.countPessoasCompareceram(idAnterior)).thenReturn(20L);
        when(inscricaoRepository.countConvidadosCompareceram(idAnterior)).thenReturn(0L);
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.contarParticipantesUnicos(any())).thenReturn(40L);

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null, PageRequest.of(0, 20));
        var ultimoEvento = relatorio.ultimosEventos().getContent().get(0);

        // 40 presentes agora vs. 20 presentes no retiro anterior = +100%, base COMPARECIMENTO
        // (os DOIS retiros controlam presença).
        assertThat(ultimoEvento.variacaoEventoAnterior().base()).isEqualTo(BaseComparacao.COMPARECIMENTO);
        assertThat(ultimoEvento.variacaoEventoAnterior().percentual()).isEqualTo(100.0);
    }

    @Test
    void relatorioGeral_variacao_caiParaInscritos_quandoUmDosDoisNaoControlaPresenca() {
        UUID idAtual = UUID.randomUUID();
        UUID idAnterior = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        // Atual controla presença; o ANTERIOR não — não dá pra comparar comparecimento com
        // comparecimento porque o anterior não tem esse dado.
        Evento atual = eventoComTipo(idAtual, "Retiro", agora, true);
        Evento anterior = eventoComTipo(idAnterior, "Retiro", agora.minusMonths(1), false);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.countPessoasInscritas(idAtual)).thenReturn(50L);
        when(inscricaoRepository.countConvidadosInscritos(idAtual)).thenReturn(0L);
        when(inscricaoRepository.countPessoasCompareceram(idAtual)).thenReturn(40L);
        when(inscricaoRepository.countConvidadosCompareceram(idAtual)).thenReturn(0L);
        when(eventoRepository.findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
                igrejaId, "Retiro", atual.getInicioEm())).thenReturn(java.util.Optional.of(anterior));
        when(inscricaoRepository.countPessoasInscritas(idAnterior)).thenReturn(25L);
        when(inscricaoRepository.countConvidadosInscritos(idAnterior)).thenReturn(0L);
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.contarParticipantesUnicos(any())).thenReturn(40L);

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null, PageRequest.of(0, 20));
        var ultimoEvento = relatorio.ultimosEventos().getContent().get(0);

        // 50 inscritos agora vs. 25 inscritos no retiro anterior = +100%, base INSCRITOS
        // (o anterior não controla presença — não dá pra comparar comparecimento com quem não tem).
        assertThat(ultimoEvento.variacaoEventoAnterior().base()).isEqualTo(BaseComparacao.INSCRITOS);
        assertThat(ultimoEvento.variacaoEventoAnterior().percentual()).isEqualTo(100.0);
    }
}
