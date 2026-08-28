package com.domus.api.modules.pagamento.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.conta.MercadoPagoOAuthService;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoTokenRenovacaoJobTest {

    ContaPagamentoIgrejaRepository contaRepository;
    MercadoPagoOAuthService oauthService;
    UsuarioRepository usuarioRepository;
    NotificacaoService notificacaoService;
    MercadoPagoTokenRenovacaoJob job;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        contaRepository = mock(ContaPagamentoIgrejaRepository.class);
        oauthService = mock(MercadoPagoOAuthService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        job = new MercadoPagoTokenRenovacaoJob(contaRepository, oauthService, usuarioRepository, notificacaoService);
    }

    private ContaPagamentoIgreja conta() {
        return new ContaPagamentoIgreja(igrejaId, "mp-user", "access", "refresh",
            Instant.now().plusSeconds(60), UUID.randomUUID());
    }

    @Test
    void renovaTodaContaPertoDeVencerSemNotificarNinguemQuandoDaCerto() {
        var conta = conta();
        when(contaRepository.findByExpiraEmBefore(any())).thenReturn(List.of(conta));

        job.executar();

        verify(oauthService).renovarTokenDaConta(conta);
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void notificaAdminsQuandoRenovacaoFalha() {
        var conta = conta();
        when(contaRepository.findByExpiraEmBefore(any())).thenReturn(List.of(conta));
        doThrow(new IllegalStateException("refresh_token inválido")).when(oauthService).renovarTokenDaConta(conta);
        UUID adminId = UUID.randomUUID();
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igrejaId, "ADMIN_IGREJA"))
            .thenReturn(List.of(Usuario.builder().id(adminId).build()));

        job.executar();

        verify(notificacaoService).criar(
            eq(TipoNotificacao.CONTA_PAGAMENTO_RECONEXAO_NECESSARIA), eq(igrejaId), eq(adminId), any(), any());
    }

    @Test
    void falhaEmUmaContaNaoImpedeAsOutras() {
        var contaComFalha = conta();
        var contaOk = conta();
        when(contaRepository.findByExpiraEmBefore(any())).thenReturn(List.of(contaComFalha, contaOk));
        doThrow(new IllegalStateException("falhou")).when(oauthService).renovarTokenDaConta(contaComFalha);
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(any(), any())).thenReturn(List.of());

        job.executar();

        verify(oauthService).renovarTokenDaConta(contaOk);
    }

    @Test
    void naoFazNadaQuandoNaoHaContaPertoDeVencer() {
        when(contaRepository.findByExpiraEmBefore(any())).thenReturn(List.of());

        job.executar();

        verifyNoInteractions(oauthService, notificacaoService);
    }
}
