package com.domus.api.modules.evento;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.evento.DTOs.EventoRequest;
import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    EventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        service = new EventoService(eventoRepository, igrejaRepository, cacheEvictor, outboxRegistrador);

        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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
}
