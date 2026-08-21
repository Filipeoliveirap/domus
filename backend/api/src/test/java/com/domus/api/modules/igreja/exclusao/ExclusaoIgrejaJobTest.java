package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExclusaoIgrejaJobTest {

    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    EmailService emailService;
    PurgaIgrejaService purgaIgrejaService;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    ExclusaoIgrejaJob job;

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        emailService = mock(EmailService.class);
        purgaIgrejaService = mock(PurgaIgrejaService.class);
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        job = new ExclusaoIgrejaJob(igrejaRepository, usuarioRepository, emailService, purgaIgrejaService,
                notificacaoService);
    }

    private Igreja igrejaAgendadaHa(int dias) {
        return Igreja.builder().id(UUID.randomUUID()).nome("Igreja X").emailContato("x@x.com")
                .exclusaoAgendadaEm(LocalDateTime.now().minusDays(dias)).build();
    }

    @Test
    void enviaLembreteQuandoFaltamExatamente5Dias() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(5)));

        job.verificarPrazos();

        verify(emailService).enviar(eq("x@x.com"), contains("5 dias"), anyString());
        verify(purgaIgrejaService, never()).purgar(any());
    }

    @Test
    void enviarLembreteNotificaTodosOsAdminsAtivos() {
        UUID usuarioIdAdmin = UUID.randomUUID();
        Igreja igreja = igrejaAgendadaHa(5);
        com.domus.api.modules.usuario.Usuario admin = com.domus.api.modules.usuario.Usuario.builder()
                .id(usuarioIdAdmin).build();
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igreja));
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igreja.getId(), "ADMIN_IGREJA"))
                .thenReturn(List.of(admin));

        job.verificarPrazos();

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.EXCLUSAO_IGREJA_AGENDADA), eq(igreja.getId()),
                eq(usuarioIdAdmin), anyString(), eq("/configuracoes/igreja"));
    }

    @Test
    void enviaLembreteQuandoFaltaExatamente1Dia() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(9)));

        job.verificarPrazos();

        verify(emailService).enviar(eq("x@x.com"), contains("1 dia"), anyString());
        verify(purgaIgrejaService, never()).purgar(any());
    }

    @Test
    void naoEnviaLembreteForaDosMarcosDe5E1Dia() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(3)));

        job.verificarPrazos();

        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }

    @Test
    void executaPurgaQuandoPrazoVenceu() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(10)));

        job.verificarPrazos();

        verify(purgaIgrejaService).purgar(any());
    }
}
