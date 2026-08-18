package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExclusaoIgrejaJobTest {

    IgrejaRepository igrejaRepository;
    EmailService emailService;
    ExclusaoIgrejaJob job;

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        emailService = mock(EmailService.class);
        job = new ExclusaoIgrejaJob(igrejaRepository, emailService, null);
    }

    private Igreja igrejaAgendadaHa(int dias) {
        return Igreja.builder().nome("Igreja X").emailContato("x@x.com")
                .exclusaoAgendadaEm(LocalDateTime.now().minusDays(dias)).build();
    }

    @Test
    void enviaLembreteQuandoFaltamExatamente5Dias() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(5)));

        job.verificarPrazos();

        verify(emailService).enviar(eq("x@x.com"), contains("5 dias"), anyString());
    }

    @Test
    void enviaLembreteQuandoFaltaExatamente1Dia() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(9)));

        job.verificarPrazos();

        verify(emailService).enviar(eq("x@x.com"), contains("1 dia"), anyString());
    }

    @Test
    void naoEnviaLembreteForaDosMarcosDe5E1Dia() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(3)));

        job.verificarPrazos();

        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }
}
