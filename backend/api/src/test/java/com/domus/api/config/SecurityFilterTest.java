package com.domus.api.config;

import com.domus.api.modules.usuario.PrincipalCache;
import com.domus.api.modules.usuario.PrincipalCacheService;
import com.domus.api.modules.usuario.Usuario;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityFilterTest {

    @Mock private TokenService tokenService;
    @Mock private com.domus.api.modules.admin.AdminJwtService adminJwtService;
    @Mock private com.domus.api.modules.admin.UsuarioDomusAdminRepository adminRepository;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private PrincipalCacheService principalCacheService;
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
        PrincipalCache cache = new PrincipalCache(usuarioId, igrejaId, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN_IGREJA", true);
        Usuario reidratado = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(com.domus.api.modules.igreja.Igreja.builder().id(igrejaId).build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ADMIN_IGREJA").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(reidratado);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        // Vai até o fim: sem isto o teste passaria mesmo se o ramo que popula o
        // SecurityContext fosse deletado (o mock devolveria null e ninguém notaria).
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "o cookie válido precisa autenticar de verdade");
        assertEquals(usuarioId, ((Usuario) auth.getPrincipal()).getId());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void usuarioDesativadoNaoAutenticaMesmoComTokenValido() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        PrincipalCache cache = new PrincipalCache(usuarioId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ACESSO_COMUM", false);
        Usuario desativado = Usuario.builder()
                .id(usuarioId)
                .ativo(false)
                .igreja(com.domus.api.modules.igreja.Igreja.builder().id(UUID.randomUUID()).build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ACESSO_COMUM").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(desativado);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void usuarioNaoEncontradoNoCacheNaoAutentica() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void deveAceitarOHeaderAuthorizationSePassado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer jwt-do-header");

        filter.doFilter(request, new MockHttpServletResponse(), filterChain);

        verify(adminJwtService).validateToken("jwt-do-header");
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

    @Test
    void deveBloquearEscritaQuandoStatusAssinaturaForPausada() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        PrincipalCache cache = new PrincipalCache(usuarioId, igrejaId, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN_IGREJA", true);
        Usuario reidratado = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(com.domus.api.modules.igreja.Igreja.builder()
                        .id(igrejaId)
                        .statusAssinatura(com.domus.api.modules.igreja.StatusAssinatura.PAUSADA)
                        .build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ADMIN_IGREJA").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(reidratado);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/pessoas");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(402, response.getStatus());
        org.mockito.ArgumentCaptor<com.domus.api.shared.exception.ErrorResponse> captor =
                org.mockito.ArgumentCaptor.forClass(com.domus.api.shared.exception.ErrorResponse.class);
        verify(objectMapper).writeValue(any(java.io.Writer.class), captor.capture());
        assertEquals("ASSINATURA_PAUSADA_LEITURA_APENAS", captor.getValue().error());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void deveRetornarCodigoDesvinculadaQuandoMotivoSuspensaoForDesvinculadaMatriz() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        PrincipalCache cache = new PrincipalCache(usuarioId, igrejaId, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN_IGREJA", true);
        Usuario reidratado = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(com.domus.api.modules.igreja.Igreja.builder()
                        .id(igrejaId)
                        .statusAssinatura(com.domus.api.modules.igreja.StatusAssinatura.PAUSADA)
                        .motivoSuspensao("DESVINCULADA_MATRIZ")
                        .build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ADMIN_IGREJA").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(reidratado);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/eventos");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(402, response.getStatus());
        org.mockito.ArgumentCaptor<com.domus.api.shared.exception.ErrorResponse> captorDesv =
                org.mockito.ArgumentCaptor.forClass(com.domus.api.shared.exception.ErrorResponse.class);
        verify(objectMapper).writeValue(any(java.io.Writer.class), captorDesv.capture());
        assertEquals("ASSINATURA_PAUSADA_DESVINCULADA", captorDesv.getValue().error());
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void devePermitirLeituraQuandoStatusAssinaturaForPausada() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        PrincipalCache cache = new PrincipalCache(usuarioId, igrejaId, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN_IGREJA", true);
        Usuario reidratado = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(com.domus.api.modules.igreja.Igreja.builder()
                        .id(igrejaId)
                        .statusAssinatura(com.domus.api.modules.igreja.StatusAssinatura.PAUSADA)
                        .build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ADMIN_IGREJA").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(reidratado);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/pessoas");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void devePermitirAcoesDeAssinaturaEExclusaoMesmoComStatusAssinaturaPausada() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        PrincipalCache cache = new PrincipalCache(usuarioId, igrejaId, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN_IGREJA", true);
        Usuario reidratado = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(com.domus.api.modules.igreja.Igreja.builder()
                        .id(igrejaId)
                        .statusAssinatura(com.domus.api.modules.igreja.StatusAssinatura.PAUSADA)
                        .build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ADMIN_IGREJA").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(reidratado);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/igrejas/minha/assinatura/contratar");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void devePermitirVincularAMatrizMesmoComStatusAssinaturaPausada() throws Exception {
        UUID usuarioId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        PrincipalCache cache = new PrincipalCache(usuarioId, igrejaId, UUID.randomUUID(),
                UUID.randomUUID(), "ADMIN_IGREJA", true);
        Usuario reidratado = Usuario.builder()
                .id(usuarioId)
                .ativo(true)
                .igreja(com.domus.api.modules.igreja.Igreja.builder()
                        .id(igrejaId)
                        .statusAssinatura(com.domus.api.modules.igreja.StatusAssinatura.PAUSADA)
                        .motivoSuspensao("DESVINCULADA_MATRIZ")
                        .build())
                .pessoa(com.domus.api.modules.pessoa.Pessoa.builder().build())
                .role(com.domus.api.modules.usuario.Role.builder().nome("ADMIN_IGREJA").build())
                .build();

        when(tokenService.validateToken("jwt-do-cookie")).thenReturn(usuarioId.toString());
        when(principalCacheService.buscar(usuarioId)).thenReturn(cache);
        when(principalCacheService.reidratar(cache)).thenReturn(reidratado);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/igrejas-vinculadas/entrar");
        request.setCookies(new Cookie(AuthCookieFactory.COOKIE_ACCESS, "jwt-do-cookie"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(any(), any());
    }
}
