package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConviteServiceTest {

    StringRedisTemplate redisTemplate;
    ValueOperations<String, String> valueOps;
    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    ConviteService service;

    UUID igrejaId;
    UUID eventoId;
    UUID pessoaId;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        service = new ConviteService(redisTemplate, eventoRepository, inscricaoRepository);

        igrejaId = UUID.randomUUID();
        eventoId = UUID.randomUUID();
        pessoaId = UUID.randomUUID();
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento eventoComFim(LocalDateTime fimEm) {
        return Evento.builder().id(eventoId).igreja(igreja()).titulo("Retiro")
                .inicioEm(fimEm.minusHours(2)).fimEm(fimEm).requerInscricao(true).build();
    }

    @Test
    void gerarTokenGravaNoRedisComTtlAteFimDoEvento() {
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        UUID inscricaoId = UUID.randomUUID();
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CONFIRMADA).build();

        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.of(inscricao));

        String token = service.gerarToken(eventoId, pessoaId, igrejaId);

        assertThat(token).isNotBlank();
        verify(valueOps).set(eq("convite:" + token), eq(inscricaoId.toString()), any(Duration.class));
    }

    @Test
    void gerarTokenLancaNotFoundQuandoNaoInscrito() {
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.gerarToken(eventoId, pessoaId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolverInscricaoConvidanteDevolveInscricaoQuandoValido() {
        UUID inscricaoId = UUID.randomUUID();
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CONFIRMADA).build();

        when(valueOps.get("convite:abc")).thenReturn(inscricaoId.toString());
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        InscricaoEvento resolvida = service.resolverInscricaoConvidante("abc");

        assertThat(resolvida.getId()).isEqualTo(inscricaoId);
    }

    @Test
    void resolverInscricaoConvidanteLancaInvalidoQuandoTokenNaoExiste() {
        when(valueOps.get("convite:abc")).thenReturn(null);

        assertThatThrownBy(() -> service.resolverInscricaoConvidante("abc"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCodigo()).isEqualTo("CONVITE_INVALIDO"));
    }

    @Test
    void resolverInscricaoConvidanteLancaEncerradoQuandoEventoJaAconteceu() {
        UUID inscricaoId = UUID.randomUUID();
        Evento evento = eventoComFim(LocalDateTime.now().minusDays(1));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CONFIRMADA).build();

        when(valueOps.get("convite:abc")).thenReturn(inscricaoId.toString());
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.resolverInscricaoConvidante("abc"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCodigo()).isEqualTo("EVENTO_ENCERRADO"));
    }

    @Test
    void resolverInscricaoConvidanteLancaInvalidoQuandoInscricaoFoiCancelada() {
        UUID inscricaoId = UUID.randomUUID();
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).status(StatusInscricao.CANCELADA).build();

        when(valueOps.get("convite:abc")).thenReturn(inscricaoId.toString());
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.resolverInscricaoConvidante("abc"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCodigo()).isEqualTo("CONVITE_INVALIDO"));
    }
}
