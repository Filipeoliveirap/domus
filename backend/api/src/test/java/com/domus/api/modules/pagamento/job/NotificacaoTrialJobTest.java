package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacaoTrialJobTest {

    @Mock
    private IgrejaRepository igrejaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificacaoTrialJob notificacaoTrialJob;

    private Igreja igreja7d, igreja3d, igreja1d, igrejaOutroDia;

    @BeforeEach
    void setUp() {
        LocalDateTime agora = LocalDateTime.now();

        igreja7d = Igreja.builder()
                .id(UUID.randomUUID())
                .nome("Igreja 7 Dias")
                .emailContato("contato7d@igreja.com")
                .statusAssinatura(StatusAssinatura.TRIAL)
                .trialExpiraEm(agora.plusDays(7))
                .build();

        igreja3d = Igreja.builder()
                .id(UUID.randomUUID())
                .nome("Igreja 3 Dias")
                .emailContato("contato3d@igreja.com")
                .statusAssinatura(StatusAssinatura.TRIAL)
                .trialExpiraEm(agora.plusDays(3))
                .build();

        igreja1d = Igreja.builder()
                .id(UUID.randomUUID())
                .nome("Igreja 1 Dia")
                .emailContato("contato1d@igreja.com")
                .statusAssinatura(StatusAssinatura.TRIAL)
                .trialExpiraEm(agora.plusDays(1))
                .build();

        igrejaOutroDia = Igreja.builder()
                .id(UUID.randomUUID())
                .nome("Igreja Outro Dia")
                .emailContato("contato10d@igreja.com")
                .statusAssinatura(StatusAssinatura.TRIAL)
                .trialExpiraEm(agora.plusDays(10))
                .build();
    }

    @Test
    @DisplayName("notificarExpiracaoTrial_enviaEmailsApenasParaMarcosDe7_3_e_1_dias")
    void notificarExpiracaoTrial_enviaEmailsApenasParaMarcosDe7_3_e_1_dias() {
        when(igrejaRepository.findAll()).thenReturn(List.of(igreja7d, igreja3d, igreja1d, igrejaOutroDia));
        when(usuarioRepository.buscarEmailsAdminsAtivos(any())).thenReturn(List.of());

        notificacaoTrialJob.notificarExpiracaoTrial();

        verify(emailService, times(1)).enviar(eq("contato7d@igreja.com"), eq("Seu plano grátis acaba em 7 dias! - Domus"), anyString());
        verify(emailService, times(1)).enviar(eq("contato3d@igreja.com"), eq("Seu plano grátis acaba em 3 dias! - Domus"), anyString());

        ArgumentCaptor<String> captorCorpo1d = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).enviar(eq("contato1d@igreja.com"), eq("Seu plano grátis acaba amanhã! - Domus"), captorCorpo1d.capture());

        assertTrue(captorCorpo1d.getValue().contains("Seu plano grátis de 14 dias acaba amanhã"));
        verify(emailService, never()).enviar(eq("contato10d@igreja.com"), anyString(), anyString());
    }
}
