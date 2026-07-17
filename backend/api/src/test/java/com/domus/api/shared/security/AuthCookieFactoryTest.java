package com.domus.api.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.*;

class AuthCookieFactoryTest {

    // 10 min de access, 7 dias de refresh — os mesmos valores usados em dev.
    private final AuthCookieFactory factory =
            new AuthCookieFactory(true, "/api", 600_000L, 604_800_000L);

    @Test
    void accessDeveSerHttpOnlySecureLaxNaRaizDoPrefixo() {
        ResponseCookie cookie = factory.access("jwt-abc");

        assertEquals("domus_access", cookie.getName());
        assertEquals("jwt-abc", cookie.getValue());
        assertTrue(cookie.isHttpOnly(), "access precisa ser httpOnly — é o ponto da migração");
        assertTrue(cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/api", cookie.getPath());
        assertEquals(600, cookie.getMaxAge().getSeconds());
    }

    @Test
    void refreshDeveTerPathEstreitoNasRotasDeAuth() {
        ResponseCookie cookie = factory.refresh("opaco-xyz");

        assertEquals("domus_refresh", cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/api/auth", cookie.getPath(),
                "refresh só deve viajar nas rotas de auth, não em toda requisição");
        assertEquals(604_800, cookie.getMaxAge().getSeconds());
    }

    @Test
    void semPrefixoOsPathsCaemNaRaiz() {
        AuthCookieFactory semPrefixo = new AuthCookieFactory(true, "", 600_000L, 604_800_000L);

        assertEquals("/", semPrefixo.access("t").getPath());
        assertEquals("/auth", semPrefixo.refresh("t").getPath());
    }

    @Test
    void secureDesligadoRespeitaAConfig() {
        AuthCookieFactory inseguro = new AuthCookieFactory(false, "/api", 600_000L, 604_800_000L);

        assertFalse(inseguro.access("t").isSecure());
    }

    @Test
    void cookiesExpiradosZeramValorEMaxAge() {
        ResponseCookie access = factory.accessExpirado();
        ResponseCookie refresh = factory.refreshExpirado();

        assertEquals("", access.getValue());
        assertEquals(0, access.getMaxAge().getSeconds());
        assertEquals("/api", access.getPath());

        assertEquals("", refresh.getValue());
        assertEquals(0, refresh.getMaxAge().getSeconds());
        assertEquals("/api/auth", refresh.getPath(),
                "o Path do cookie de expiração precisa bater com o do original, senão o navegador não apaga");
    }
}
