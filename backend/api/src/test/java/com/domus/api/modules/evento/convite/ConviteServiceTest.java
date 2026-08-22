package com.domus.api.modules.evento.convite;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
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
    PessoaRepository pessoaRepository;
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
        pessoaRepository = mock(PessoaRepository.class);
        service = new ConviteService(redisTemplate, eventoRepository, pessoaRepository);

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

    private Pessoa pessoa() {
        return Pessoa.builder().id(pessoaId).igreja(igreja()).nome("Ana").build();
    }

    @Test
    void gerarTokenNaoExigeInscricaoDoConvidante() {
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa()));

        // Não mocka InscricaoRepository nenhum — prova que gerarToken não depende de inscrição.
        String token = service.gerarToken(eventoId, pessoaId, igrejaId);

        assertThat(token).isNotBlank();
        verify(valueOps).set(eq("convite:" + token), eq(eventoId + ":" + pessoaId), any(Duration.class));
    }

    @Test
    void gerarTokenLancaNotFoundQuandoEventoNaoPertenceAIgreja() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.gerarToken(eventoId, pessoaId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void gerarTokenLancaNotFoundQuandoPessoaNaoPertenceAIgreja() {
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.gerarToken(eventoId, pessoaId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolverDevolveEventoEConvidanteQuandoValido() {
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        Pessoa convidante = pessoa();

        when(valueOps.get("convite:abc")).thenReturn(eventoId + ":" + pessoaId);
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(convidante));

        ConviteService.ConviteResolvido resolvido = service.resolver("abc");

        assertThat(resolvido.evento().getId()).isEqualTo(eventoId);
        assertThat(resolvido.convidante().getId()).isEqualTo(pessoaId);
    }

    @Test
    void resolverLancaInvalidoQuandoTokenNaoExiste() {
        when(valueOps.get("convite:abc")).thenReturn(null);

        assertThatThrownBy(() -> service.resolver("abc"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resolverLancaEncerradoQuandoEventoJaAconteceu() {
        Evento evento = eventoComFim(LocalDateTime.now().minusDays(1));
        when(valueOps.get("convite:abc")).thenReturn(eventoId + ":" + pessoaId);
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa()));

        assertThatThrownBy(() -> service.resolver("abc"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCodigo()).isEqualTo("EVENTO_ENCERRADO"));
    }

    @Test
    void resolverLancaInvalidoQuandoPessoaFoiExcluida() {
        Evento evento = eventoComFim(LocalDateTime.now().plusDays(5));
        when(valueOps.get("convite:abc")).thenReturn(eventoId + ":" + pessoaId);
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento));
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolver("abc"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
