package com.domus.api.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Fábrica dos cookies de sessão.
 *
 * <p>Os tokens deixaram de trafegar no corpo/header e passaram a viver em cookies
 * {@code httpOnly} — o JavaScript não os lê, então um XSS não rouba a sessão.
 *
 * <p>O {@code path-prefix} existe porque o front chama a API através de um proxy do Next
 * ({@code /api/*}). O {@code Path} do cookie precisa ser escrito na visão do NAVEGADOR
 * ({@code /api/auth}), não na do Spring ({@code /auth}) — o Spring não enxerga esse prefixo.
 */
@Component
public class AuthCookieFactory {

    public static final String COOKIE_ACCESS = "domus_access";
    public static final String COOKIE_REFRESH = "domus_refresh";

    private final boolean secure;
    private final String pathPrefix;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public AuthCookieFactory(
            @Value("${app.cookie.secure:true}") boolean secure,
            @Value("${app.cookie.path-prefix:}") String pathPrefix,
            @Value("${security.jwt.expiration-ms}") long accessExpirationMs,
            @Value("${security.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.secure = secure;
        this.pathPrefix = pathPrefix;
        this.accessTtl = Duration.ofMillis(accessExpirationMs);
        this.refreshTtl = Duration.ofMillis(refreshExpirationMs);
    }

    public ResponseCookie access(String token) {
        return montar(COOKIE_ACCESS, token, pathRaiz(), accessTtl);
    }

    public ResponseCookie refresh(String token) {
        return montar(COOKIE_REFRESH, token, pathAuth(), refreshTtl);
    }

    /** Cookie de mesmo nome/Path com Max-Age 0: é assim que o servidor apaga um cookie. */
    public ResponseCookie accessExpirado() {
        return montar(COOKIE_ACCESS, "", pathRaiz(), Duration.ZERO);
    }

    public ResponseCookie refreshExpirado() {
        return montar(COOKIE_REFRESH, "", pathAuth(), Duration.ZERO);
    }

    private ResponseCookie montar(String nome, String valor, String path, Duration maxAge) {
        return ResponseCookie.from(nome, valor)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private String pathRaiz() {
        return pathPrefix.isEmpty() ? "/" : pathPrefix;
    }

    private String pathAuth() {
        return pathPrefix + "/auth";
    }
}
