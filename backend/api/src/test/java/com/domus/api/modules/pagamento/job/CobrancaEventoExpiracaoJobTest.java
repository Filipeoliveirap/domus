package com.domus.api.modules.pagamento.job;

import static org.mockito.Mockito.*;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CobrancaEventoExpiracaoJobTest {

    CobrancaEventoRepository repository;
    InscricaoRepository inscricaoRepository;
    CobrancaEventoExpiracaoJob job;

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        job = new CobrancaEventoExpiracaoJob(repository, inscricaoRepository);
    }

    @Test
    void cancelaInscricaoVinculadaQuandoCobrancaExpira() {
        UUID inscricaoId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), inscricaoId,
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().minusSeconds(60), UUID.randomUUID(), null);
        when(repository.findByStatusAndExpiraEmBefore(eq(StatusCobranca.PENDENTE), any()))
            .thenReturn(List.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findAllById(List.of(inscricaoId))).thenReturn(List.of(inscricao));

        job.executar();

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        verify(inscricaoRepository).saveAll(List.of(inscricao));
    }

    @Test
    void expiraTodasAsCobrancasPendentesVencidas() {
        var cobranca1 = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().minusSeconds(60), UUID.randomUUID(), null);
        var cobranca2 = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, UUID.randomUUID(), BigDecimal.TEN, Instant.now().minusSeconds(60), UUID.randomUUID(), "token");

        when(repository.findByStatusAndExpiraEmBefore(eq(StatusCobranca.PENDENTE), any()))
            .thenReturn(List.of(cobranca1, cobranca2));

        job.executar();

        org.assertj.core.api.Assertions.assertThat(cobranca1.getStatus()).isEqualTo(StatusCobranca.EXPIRADO);
        org.assertj.core.api.Assertions.assertThat(cobranca2.getStatus()).isEqualTo(StatusCobranca.EXPIRADO);
        verify(repository).saveAll(List.of(cobranca1, cobranca2));
    }

    @Test
    void naoFazNadaQuandoNaoHaCobrancaVencida() {
        when(repository.findByStatusAndExpiraEmBefore(eq(StatusCobranca.PENDENTE), any()))
            .thenReturn(List.of());

        job.executar();

        verify(repository, never()).saveAll(any());
    }
}
