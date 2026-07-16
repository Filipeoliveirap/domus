package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.GoogleLoginDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.shared.security.AuthCookieFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerCookieTest {

    @Mock private AuthService authService;
    @Mock private PasswordResetService passwordResetService;
    @Mock private GoogleAuthService googleAuthService;

    private final AuthCookieFactory cookieFactory =
            new AuthCookieFactory(true, "/api", 600_000L, 604_800_000L);

    private AuthenticationController controller() {
        return new AuthenticationController(
                authService, passwordResetService, googleAuthService, cookieFactory);
    }

    @Test
    void loginDeveEmitirOsDoisCookiesENaoVazarTokenNoCorpo() {
        UUID id = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        AuthenticationDTO entrada = new AuthenticationDTO("ana@igreja.com", "senha123");

        when(authService.login(entrada)).thenReturn(new LoginResponseDTO(
                id, "Ana", "ADMIN_IGREJA", igrejaId, "Igreja Central", "jwt-abc", "refresh-xyz"));

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
                id, "Bia", "MEMBRO", UUID.randomUUID(), "Igreja Central", "jwt-g", "refresh-g"));

        ResponseEntity<SessaoDTO> resposta =
                controller().googleLogin(new GoogleLoginDTO("id-token-do-google"));

        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertEquals("Bia", resposta.getBody().nome());
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
}
