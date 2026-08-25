package com.domus.api.modules.pagamento.job;

import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CobrancaEventoExpiracaoJobTest {

    CobrancaEventoRepository repository;
    CobrancaEventoExpiracaoJob job;

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        job = new CobrancaEventoExpiracaoJob(repository);
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
