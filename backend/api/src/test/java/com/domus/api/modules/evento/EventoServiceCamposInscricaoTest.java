package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.evento.inscricao.InscricaoService;
import com.domus.api.modules.outbox.OutboxRegistrador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Trava os campos de inscrição (vagas, preço e as duas restrições) nos dois caminhos de escrita.
 *
 * <p>Existe por causa do modo de falha específico deste tipo de mudança: um campo ligado só no
 * cadastro faz a API <b>aceitar</b> a edição e <b>descartar</b> em silêncio. Não quebra teste
 * nenhum, não aparece em log — o usuário só descobre quando reabre a tela e o valor voltou.
 */
class EventoServiceCamposInscricaoTest {

    EventoRepository eventoRepository;
    IgrejaRepository igrejaRepository;
    CacheEvictor cacheEvictor;
    OutboxRegistrador outboxRegistrador;
    InscricaoService inscricaoService;
    EventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        inscricaoService = mock(InscricaoService.class);
        service = new EventoService(eventoRepository, igrejaRepository, cacheEvictor,
                outboxRegistrador, inscricaoService);

        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inscricaoService.removerInscritosNaoElegiveis(any(), anyBoolean(), anyBoolean()))
                .thenReturn(0);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private EventoRequest request(Integer vagas, BigDecimal preco,
                                  Boolean exclusivoMembros, Boolean exclusivoBatizados) {
        return new EventoRequest("Retiro", "desc", LocalDateTime.now().plusDays(5), null,
                "Templo", null, vagas, preco, exclusivoMembros, exclusivoBatizados, true);
    }

    @Test
    void cadastrarGravaOsCamposDeInscricao() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja()));

        EventoResponse r = service.cadastrarEvento(
                request(50, new BigDecimal("120.00"), true, true), igrejaId);

        assertThat(r.vagas()).isEqualTo(50);
        assertThat(r.preco()).isEqualByComparingTo("120.00");
        assertThat(r.exclusivoMembros()).isTrue();
        assertThat(r.exclusivoBatizados()).isTrue();
    }

    @Test
    void atualizarGravaOsCamposDeInscricao() {
        // Evento nasce SEM os campos; a edição precisa realmente persisti-los.
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.atualizarEvento(eventoId, request(30, new BigDecimal("80.50"), true, true), igrejaId);

        assertThat(existente.getVagas()).isEqualTo(30);
        assertThat(existente.getPreco()).isEqualByComparingTo("80.50");
        assertThat(existente.isExclusivoMembros()).isTrue();
        assertThat(existente.isExclusivoBatizados()).isTrue();
    }

    @Test
    void atualizarLimpaVagasEPrecoQuandoVemNulo() {
        // Nulo é significativo aqui: vagas nula = sem limite, preço nulo = gratuito.
        // Se a atualização ignorasse o nulo, não haveria como voltar atrás de um evento pago.
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .vagas(10).preco(new BigDecimal("50.00"))
                .exclusivoMembros(true).exclusivoBatizados(true)
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.atualizarEvento(eventoId, request(null, null, null, null), igrejaId);

        assertThat(existente.getVagas()).isNull();
        assertThat(existente.getPreco()).isNull();
        // Boolean ausente no JSON vira false: a atualização é substituição total (PUT),
        // não remendo parcial (PATCH). O front precisa enviar sempre o valor corrente.
        assertThat(existente.isExclusivoMembros()).isFalse();
        assertThat(existente.isExclusivoBatizados()).isFalse();
    }

    @Test
    void atualizarDevolveQuantasInscricoesForamRemovidasAoRestringir() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));
        when(inscricaoService.removerInscritosNaoElegiveis(eventoId, false, true)).thenReturn(3);

        EventoResponse r = service.atualizarEvento(
                eventoId, request(null, null, false, true), igrejaId);

        assertThat(r.inscricoesRemovidas()).isEqualTo(3);
    }

    @Test
    void buscarPorIdExpoeSituacaoAgendadaQuandoAindaNaoComecou() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThat(service.buscarPorId(eventoId, igrejaId).situacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.AGENDADO);
    }

    @Test
    void buscarPorIdExpoeSituacaoEmAndamentoEntreInicioEFim() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusHours(1))
                .fimEm(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThat(service.buscarPorId(eventoId, igrejaId).situacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.EM_ANDAMENTO);
    }

    @Test
    void buscarPorIdExpoeSituacaoEncerradaAposOFim() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusDays(2))
                .fimEm(LocalDateTime.now().minusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThat(service.buscarPorId(eventoId, igrejaId).situacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.ENCERRADO);
    }

    @Test
    void semFimDeclaradoEhEmAndamentoNoMesmoDiaEEncerradoNoDiaSeguinte() {
        // Fronteira do null fimEm: considerado em andamento até o fim do PRÓPRIO dia de
        // início, encerrado a partir do dia seguinte.
        Evento comecouHoje = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Culto")
                .inicioEm(LocalDateTime.now().minusHours(2))
                .build();
        assertThat(comecouHoje.getSituacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.EM_ANDAMENTO);

        Evento comecouOntem = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Culto")
                .inicioEm(LocalDateTime.now().minusDays(1))
                .build();
        assertThat(comecouOntem.getSituacao())
                .isEqualTo(com.domus.api.modules.evento.SituacaoEvento.ENCERRADO);
    }

    @Test
    void atualizarRecusaEventoEmAndamento() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusHours(1))
                .fimEm(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizarEvento(
                eventoId, request(null, null, null, null), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("em andamento");
    }

    @Test
    void atualizarRecusaEventoEncerrado() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusDays(2))
                .fimEm(LocalDateTime.now().minusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.atualizarEvento(
                eventoId, request(null, null, null, null), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("encerrado");
    }

    @Test
    void arquivarRecusaEventoEmAndamento() {
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusHours(1))
                .fimEm(LocalDateTime.now().plusHours(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.arquivarEvento(eventoId, igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("em andamento");
    }

    @Test
    void arquivarPermiteEventoEncerrado() {
        // Decisão: arquivar é faxina normal de evento passado — não trava em ENCERRADO,
        // só em EM_ANDAMENTO (evento rolando agora).
        Evento existente = Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(LocalDateTime.now().minusDays(2))
                .fimEm(LocalDateTime.now().minusDays(1))
                .build();
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(existente));

        service.arquivarEvento(eventoId, igrejaId);

        verify(eventoRepository).delete(existente);
    }
}
