package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.GoogleLoginDTO;
import com.domus.api.modules.auth.DTO.GoogleRegistrarDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.auth.DTO.TokenPairDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.SessaoExpiradaException;
import com.domus.api.shared.security.AuthCookieFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerCookieTest {

    @Mock private AuthService authService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private GoogleAuthService googleAuthService;
    @Mock private com.domus.api.shared.web.ClienteIpResolver clienteIpResolver;
    @Mock private com.domus.api.modules.termos.TermoAceiteService termoAceiteService;

    private final AuthCookieFactory cookieFactory =
            new AuthCookieFactory(true, "/api", 600_000L, 604_800_000L);

    private AuthenticationController controller() {
        return new AuthenticationController(
                authService, passwordResetService, googleAuthService, cookieFactory, clienteIpResolver, termoAceiteService);
    }

    @Test
    void loginDeveEmitirOsDoisCookiesENaoVazarTokenNoCorpo() {
        UUID id = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        AuthenticationDTO entrada = new AuthenticationDTO("ana@igreja.com", "senha123");

        when(authService.login(entrada)).thenReturn(new LoginResponseDTO(
                id, "Ana", "ADMIN_IGREJA", igrejaId, "Igreja Central", null, null, null, null,
                "jwt-abc", "refresh-xyz", List.of()));

        ResponseEntity<SessaoDTO> resposta = controller().login(entrada);

        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().anyMatch(c ->
                c.startsWith("domus_access=jwt-abc") && c.contains("HttpOnly") && c.contains("SameSite=Lax")));
        assertTrue(cookies.stream().anyMatch(c ->
                c.startsWith("domus_refresh=refresh-xyz") && c.contains("Path=/api/auth")));

        SessaoDTO corpo = resposta.getBody();
        assertNotNull(corpo);
        assertEquals("Ana", corpo.nome());
        assertEquals("ADMIN_IGREJA", corpo.role());
        assertEquals(igrejaId, corpo.igrejaId());
    }

    @Test
    void googleLoginTambemEmiteCookies() {
        UUID id = UUID.randomUUID();
        when(googleAuthService.login("id-token-do-google")).thenReturn(new LoginResponseDTO(
                id, "Bia", "ACESSO_COMUM", UUID.randomUUID(), "Igreja Central", null, null, null, null,
                "jwt-g", "refresh-g", List.of()));

        ResponseEntity<SessaoDTO> resposta =
                controller().googleLogin(new GoogleLoginDTO("id-token-do-google"));

        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        // Atributos também aqui: o caminho do Google é código separado do login nativo e
        // poderia emitir dois cookies sem httpOnly sem que nenhum outro teste percebesse.
        assertTrue(cookies.stream().allMatch(c -> c.contains("HttpOnly") && c.contains("SameSite=Lax")));
        assertEquals("Bia", resposta.getBody().nome());
    }

    @Test
    void refreshDeveLerORefreshDoCookieEReemitirOsDois() {
        when(authService.refresh("refresh-antigo"))
                .thenReturn(new TokenPairDTO("jwt-novo", "refresh-novo"));

        ResponseEntity<Void> resposta = controller().refresh("refresh-antigo");

        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("domus_access=jwt-novo")));
        assertTrue(cookies.stream().anyMatch(c -> c.startsWith("domus_refresh=refresh-novo")),
                "a rotação precisa reemitir o refresh, senão a próxima renovação usa um token morto");
    }

    @Test
    void refreshSemCookieDeveSer401ENaoTocarNoService() {
        SessaoExpiradaException erro =
                assertThrows(SessaoExpiradaException.class, () -> controller().refresh(null));

        assertEquals("REFRESH_INVALIDO", erro.getCodigo());
        verifyNoInteractions(authService);
    }

    @Test
    void refreshComCookieEmBrancoTambemE401() {
        assertThrows(SessaoExpiradaException.class, () -> controller().refresh("  "));
        verifyNoInteractions(authService);
    }

    @Test
    void logoutComRefreshValidoRevogaASessao() {
        controller().logout("refresh-vivo");

        verify(authService).logout("refresh-vivo");
    }

    /** Principal é entidade desanexada e `igreja` é LAZY — ler além do id estoura LazyInitializationException em produção. */
    @Test
    void meDeveBuscarASessaoPeloIdSemTocarNoPrincipalDesanexado() {
        UUID id = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        SessaoDTO esperada = new SessaoDTO(id, "Ana", "ADMIN_IGREJA", igrejaId, "Igreja Central",
                null, null, null, null);

        when(authService.sessaoDe(id)).thenReturn(esperada);

        // Principal com APENAS o id: se o controller tentar ler membro/role/igreja daqui,
        // estoura NullPointerException — que é o teste falhando, como tem que ser.
        Usuario principal = Usuario.builder().id(id).build();

        SessaoDTO sessao = controller().me(principal).getBody();

        assertSame(esperada, sessao);
        verify(authService).sessaoDe(id);
    }

    @Test
    void logoutDeveExpirarOsCookiesMesmoSemRefreshValido() {
        ResponseEntity<Void> resposta = controller().logout(null);

        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().allMatch(c -> c.contains("Max-Age=0")),
                "o JS não consegue apagar cookie httpOnly — quem expira é o servidor");
    }

    /** googleRegistrar monta o SessaoDTO na mão — sem consultar o TermoAceiteService, mentiria sobre o aceite recém-registrado. */
    @Test
    void googleRegistrarTrazDataDoAceiteQueAcabouDeRegistrar() {
        UUID id = UUID.randomUUID();
        GoogleRegistrarDTO dados = new GoogleRegistrarDTO("id-token", "Igreja Nova", null, "11999999999", true);
        when(googleAuthService.registrar(dados, "203.0.113.9")).thenReturn(new RegistrarIgrejaResponse(
                id, "jwt", "refresh", "Dono", "ADMIN_IGREJA", UUID.randomUUID(), "Igreja Nova"));
        when(clienteIpResolver.resolver(any())).thenReturn("203.0.113.9");
        when(termoAceiteService.precisaAceitar(id)).thenReturn(false);
        LocalDateTime aceiteAgora = LocalDateTime.now();
        when(termoAceiteService.dataUltimoAceite(id)).thenReturn(aceiteAgora);

        SessaoDTO sessao = controller().googleRegistrar(dados, new MockHttpServletRequest()).getBody();

        assertFalse(sessao.precisaAceitarTermos());
        assertEquals(aceiteAgora, sessao.termosAceitosEm());
    }
}
