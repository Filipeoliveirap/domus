package com.domus.api.config;

import com.domus.api.modules.admin.AdminJwtService;
import com.domus.api.modules.igreja.StatusTenant;
import com.domus.api.modules.usuario.PrincipalCache;
import com.domus.api.modules.usuario.PrincipalCacheService;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.ErrorResponse;
import com.domus.api.shared.security.AuthCookieFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final AdminJwtService adminJwtService;
    private final PrincipalCacheService principalCacheService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        var token = this.recoverToken(request);
        if (token != null) {
            // 1. Tenta validar como Token de SuperAdmin Domus
            var decodedAdminJwt = adminJwtService.validateToken(token);
            if (decodedAdminJwt != null) {
                String adminId = decodedAdminJwt.getSubject();
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_DOMUS_ADMIN"));
                var authentication = new UsernamePasswordAuthenticationToken(adminId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                org.slf4j.MDC.put("admin_id", adminId);
                log.debug("SuperAdmin autenticado via token. id={}", adminId);
                filterChain.doFilter(request, response);
                return;
            }

            // 2. Valida como Token de Usuário de Igreja
            var subject = tokenService.validateToken(token);
            if (subject != null) {
                try {
                    PrincipalCache cache = principalCacheService.buscar(UUID.fromString(subject));
                    Usuario usuario = cache == null ? null : principalCacheService.reidratar(cache);
                    if (usuario != null && usuario.isEnabled()) {
                        // Verifica se o tenant está suspenso
                        if (usuario.getIgreja() != null && usuario.getIgreja().getStatusTenant() == StatusTenant.SUSPENSO) {
                            log.warn("Bloqueando requisição de tenant suspenso. igreja_id={}", usuario.getIgreja().getId());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            objectMapper.writeValue(response.getWriter(), ErrorResponse.of(403, "TENANT_SUSPENSO", "Conta suspensa. Entre em contato com o suporte"));
                            return;
                        }

                        var authentication = new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
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
                    log.warn("Subject do token não é um id válido. subject={}", subject);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String recoverToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;

        for (Cookie cookie : cookies) {
            if (AuthCookieFactory.COOKIE_ACCESS.equals(cookie.getName()) || "domus_admin_token".equals(cookie.getName())) {
                String valor = cookie.getValue();
                if (valor != null && !valor.isBlank()) {
                    return valor;
                }
            }
        }
        return null;
    }
}
