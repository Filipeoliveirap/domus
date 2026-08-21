package com.domus.api.config;

import com.domus.api.modules.usuario.PrincipalCache;
import com.domus.api.modules.usuario.PrincipalCacheService;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.security.AuthCookieFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final PrincipalCacheService principalCacheService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null) {
            var subject = tokenService.validateToken(token);
            if (subject != null) {
                try {
                    PrincipalCache cache = principalCacheService.buscar(UUID.fromString(subject));
                    Usuario usuario = cache == null ? null : principalCacheService.reidratar(cache);
                    if (usuario != null && usuario.isEnabled()) {
                        var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        // Enriquece o contexto de log com quem é o usuário e a igreja (multi-tenant).
                        // O RequestIdFilter limpa o MDC ao fim da requisição.
                        org.slf4j.MDC.put("usuario_id", subject);
                        if (usuario.getIgreja() != null) {
                            org.slf4j.MDC.put("igreja_id", String.valueOf(usuario.getIgreja().getId()));
                        }
                        log.debug("Usuário autenticado via token. id={}", subject);
                    } else if (usuario == null) {
                        log.warn("Token válido mas usuário não encontrado. id={}", subject);
                    } else {
                        log.warn("Usuário desativado tentou usar token. id={}", subject);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Subject do token não é um id válido (token legado?). subject={}", subject);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Sem fallback pro header Authorization de propósito — mantê-lo deixaria localStorage viável no front. */
    private String recoverToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (AuthCookieFactory.COOKIE_ACCESS.equals(cookie.getName())) {
                String valor = cookie.getValue();
                return (valor == null || valor.isBlank()) ? null : valor;
            }
        }
        return null;
    }
}
