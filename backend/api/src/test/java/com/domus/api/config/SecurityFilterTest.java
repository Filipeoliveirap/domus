package com.domus.api.config;

import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.security.AuthCookieFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock private TokenService tokenService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private FilterChain filterChain;

    @InjectMocks private SecurityFilter filter;

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveValidarOTokenVindoDoCookieDeAcesso() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tokenService).validateToken("jwt-do-cookie");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void deveIgnorarOHeaderAuthorization() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-do-header");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tokenService, never()).validateToken(anyString());
        assertNull(SecurityContextHolder.getContext().getAuthentication(),
                "o header não pode mais autenticar — senão a migração é decorativa");
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void semCookiesNaoTentaValidarNada() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tokenService, never()).validateToken(anyString());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void cookieDeAcessoVazioNaoTentaValidar() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, ""));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(tokenService, never()).validateToken(anyString());
    }
}
