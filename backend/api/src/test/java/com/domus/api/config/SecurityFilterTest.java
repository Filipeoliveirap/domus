package com.domus.api.config;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void deveAutenticarComOTokenVindoDoCookieDeAcesso() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        Usuario usuario = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .pessoa(Pessoa.builder().nome("Ana").build())
                .role(Role.builder().nome("ADMIN_IGREJA").build())
                .igreja(Igreja.builder().id(igrejaId).nome("Igreja Central").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // Vai até o fim: sem isto o teste passaria mesmo se o ramo que popula o
        // SecurityContext fosse deletado (o mock devolveria null e ninguém notaria).
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "o cookie válido precisa autenticar de verdade");
        assertSame(usuario, auth.getPrincipal());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void usuarioDesativadoNaoAutenticaMesmoComTokenValido() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        Usuario desativado = Usuario.builder()
                .id(usuarioId)
                .ativo(false)
                .pessoa(Pessoa.builder().nome("Bia").build())
                .role(Role.builder().nome("ACESSO_COMUM").build())
                .igreja(Igreja.builder().id(UUID.randomUUID()).nome("Igreja Central").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(desativado));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
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
