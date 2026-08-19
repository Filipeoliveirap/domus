package com.domus.api.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Path do cookie usa a visão do navegador ({@code /api/auth}), não a do Spring ({@code /auth}) — o proxy do Next some do lado do Spring. */
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
