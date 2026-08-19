package com.domus.api.modules.igreja;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaAdminRequest;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.termos.TermoAceiteService;
import com.domus.api.shared.security.AuthCookieFactory;
import com.domus.api.shared.security.UsuarioAutenticado;
import com.domus.api.shared.web.ClienteIpResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cadastro de igreja monta o SessaoDTO na mão (não passa por AuthService.sessaoDe nem
 * pelo helper do AuthenticationController) — precisa consultar o TermoAceiteService
 * separadamente, senão a resposta do próprio cadastro mente sobre o aceite que acabou
 * de registrar.
 */
class IgrejaControllerTermosTest {

    IgrejaService igrejaService;
    AuthCookieFactory cookieFactory;
    UsuarioAutenticado usuarioAutenticado;
    ClienteIpResolver clienteIpResolver;
    TermoAceiteService termoAceiteService;
    IgrejaController controller;

    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        igrejaService = mock(IgrejaService.class);
        cookieFactory = new AuthCookieFactory(true, "/api", 600_000L, 604_800_000L);
        usuarioAutenticado = mock(UsuarioAutenticado.class);
        clienteIpResolver = mock(ClienteIpResolver.class);
        termoAceiteService = mock(TermoAceiteService.class);
        controller = new IgrejaController(igrejaService, cookieFactory, usuarioAutenticado, clienteIpResolver, termoAceiteService);
    }

    @Test
    void respostaDoCadastroTrazDataDoAceiteQueAcabouDeRegistrar() {
        RegistrarIgrejaAdminRequest data = new RegistrarIgrejaAdminRequest();
        when(clienteIpResolver.resolver(org.mockito.ArgumentMatchers.any())).thenReturn("203.0.113.9");
        when(igrejaService.registrar(data, "203.0.113.9")).thenReturn(new RegistrarIgrejaResponse(
                usuarioId, "jwt", "refresh", "Admin", "ADMIN_IGREJA", UUID.randomUUID(), "Igreja X"));
        when(termoAceiteService.precisaAceitar(usuarioId)).thenReturn(false);
        LocalDateTime aceiteAgora = LocalDateTime.now();
        when(termoAceiteService.dataUltimoAceite(usuarioId)).thenReturn(aceiteAgora);

        SessaoDTO sessao = controller.cadastrarIgreja(data, new MockHttpServletRequest()).getBody();

        assertThat(sessao.precisaAceitarTermos()).isFalse();
        assertThat(sessao.termosAceitosEm()).isEqualTo(aceiteAgora);
    }
}
