package com.domus.api.shared.security;

import com.domus.api.config.TokenService;
import com.domus.api.modules.usuario.Usuario;
import jakarta.servlet.http.Cookie;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Autentica requisições de teste MockMvc como o {@code SecurityFilter} de produção autentica:
 * um JWT real (via {@link TokenService}) no cookie {@code domus_access}. Sem isso, o filtro
 * customizado do projeto (que lê o cookie na mão, não usa @WithMockUser) nunca autentica.
 *
 * Uso: {@code new AutenticacaoTestSupport(tokenService)} no teste, com {@code tokenService}
 * injetado via {@code @Autowired} normalmente (não é um bean Spring, só agrupa o helper).
 */
public class AutenticacaoTestSupport {

    private final TokenService tokenService;

    public AutenticacaoTestSupport(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public Cookie cookieDe(Usuario usuario) {
        return new Cookie(AuthCookieFactory.COOKIE_ACCESS, tokenService.generateToken(usuario));
    }

    /** Anexa cookie de sessão + token CSRF válido — cobre GET e escrita (POST/PUT/PATCH/DELETE). */
    public MockHttpServletRequestBuilder autenticado(MockHttpServletRequestBuilder builder, Usuario usuario) {
        return builder.cookie(cookieDe(usuario)).with(csrf());
    }
}
