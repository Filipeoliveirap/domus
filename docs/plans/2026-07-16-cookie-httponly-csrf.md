# Sessão em cookie httpOnly + CSRF — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tirar os dois tokens do `localStorage` e do cookie setado por JS, passando a sessão para cookies `httpOnly`+`Secure`+`SameSite=Lax` emitidos pelo backend, com CSRF double-submit reativado.

**Architecture:** O front chama `/api/*` na própria origem e o Next repassa pro Spring via `rewrites` — assim o cookie é sempre first-party, independente de onde a API for hospedada. O backend emite `domus_access` (JWT, 10 min, `Path=/api`) e `domus_refresh` (opaco/Redis, 7 dias, `Path=/api/auth`). Como o JS não lê mais o cookie, o servidor vira dono da verdade da sessão via `GET /auth/me`.

**Tech Stack:** Java 21, Spring Boot 3.5.13, Spring Security 6 (`CookieCsrfTokenRepository`, `ResponseCookie`), Redis; Next.js 16 (`rewrites`), TypeScript, zustand, axios; testes com Mockito puro (sem contexto Spring).

**Spec:** `backend/api/docs/superpowers/specs/2026-07-16-cookie-httponly-csrf-design.md`

## Global Constraints

- Repositório único em `/home/jos-filipe-oliveira-pereira/Documents/domus` (contém `backend/api` e `frontend`). Branch: `producao`.
- **Sem trailer `Co-Authored-By`** em commits (instrução do autor).
- `./mvnw` está quebrado — usar o `mvn` do sistema. Rodar de `backend/api`.
- Testes de back: **Mockito puro**, sem `@SpringBootTest`. Se um teste usar `ObjectMapper`, criar com `new ObjectMapper().findAndRegisterModules()` (o `LocalDateTime` quebra sem isso).
- Nomes dos cookies, exatos: `domus_access`, `domus_refresh`. **Nunca** usar `:` em nome de cookie (inválido pela RFC 6265).
- `SameSite=Lax` (não `Strict` — quebraria o link do e-mail de reset).
- `app.cookie.secure` default `true`; `app.cookie.path-prefix` = `/api`.
- Rotação/detecção de reuso/famílias no Redis **não podem ser alteradas** — muda só o transporte do token.
- Não commitar `.env` (gitignored).
- **Estado intermediário esperado:** entre a Task 2 e a Task 8 o login pelo navegador fica quebrado (back já é cookie-only, front ainda não). Os commits compilam e os testes passam; a app só volta a funcionar de ponta a ponta na Task 8. Isso é aceitável — não há usuário real ainda.

---

### Task 1: `AuthCookieFactory` — construção dos cookies

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/shared/security/AuthCookieFactory.java`
- Modify: `backend/api/src/main/resources/application.properties` (fim do arquivo)
- Modify: `backend/api/.env.example`
- Test: `backend/api/src/test/java/com/domus/api/shared/security/AuthCookieFactoryTest.java`

**Interfaces:**
- Consumes: properties `security.jwt.expiration-ms` e `security.jwt.refresh-expiration-ms` (já existem).
- Produces: `AuthCookieFactory.COOKIE_ACCESS` (`"domus_access"`), `AuthCookieFactory.COOKIE_REFRESH` (`"domus_refresh"`); métodos `ResponseCookie access(String)`, `ResponseCookie refresh(String)`, `ResponseCookie accessExpirado()`, `ResponseCookie refreshExpirado()`.

- [ ] **Step 1: Write the failing test**

Create `backend/api/src/test/java/com/domus/api/shared/security/AuthCookieFactoryTest.java`:

```java
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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend/api && mvn -o -q test -Dtest=AuthCookieFactoryTest
```
Expected: FAIL com erro de compilação — `cannot find symbol: class AuthCookieFactory`.

- [ ] **Step 3: Write minimal implementation**

Create `backend/api/src/main/java/com/domus/api/shared/security/AuthCookieFactory.java`:

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend/api && mvn -o -q test -Dtest=AuthCookieFactoryTest
```
Expected: PASS — 5 testes, 0 falhas.

- [ ] **Step 5: Add the properties**

Append to `backend/api/src/main/resources/application.properties`:

```properties

# ─── Cookies de sessão ─────────────────────────────────────────
# Secure exige HTTPS; navegadores abrem exceção para localhost, então pode ficar true em dev.
app.cookie.secure=${COOKIE_SECURE:true}
# Prefixo do proxy do Next (/api/*). O Path do cookie é escrito na visão do navegador.
app.cookie.path-prefix=${COOKIE_PATH_PREFIX:/api}
```

Append to `backend/api/.env.example`:

```bash
# Cookies de sessão
COOKIE_SECURE=true
COOKIE_PATH_PREFIX=/api
```

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/java/com/domus/api/shared/security/AuthCookieFactory.java \
        backend/api/src/test/java/com/domus/api/shared/security/AuthCookieFactoryTest.java \
        backend/api/src/main/resources/application.properties \
        backend/api/.env.example
git commit -m "feat(auth): AuthCookieFactory para os cookies de sessão httpOnly

Emite domus_access (Path=/api) e domus_refresh (Path=/api/auth) com
httpOnly+Secure+SameSite=Lax, e as versões expiradas para o logout.
O path-prefix existe porque o Path do cookie é escrito na visão do
navegador (proxy /api do Next), não na do Spring."
```

---

### Task 2: `SecurityFilter` lê o cookie e ignora o header

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/config/SecurityFilter.java:60-64` (método `recoverToken`)
- Test: `backend/api/src/test/java/com/domus/api/config/SecurityFilterTest.java`

**Interfaces:**
- Consumes: `AuthCookieFactory.COOKIE_ACCESS` (Task 1).
- Produces: nada novo — muda só a origem do token dentro do filtro.

O teste do header é o mais importante do plano: é ele que garante que a migração não ficou decorativa. Se o header continuasse funcionando, o `localStorage` seguiria sendo uma opção viável.

- [ ] **Step 1: Write the failing test**

Create `backend/api/src/test/java/com/domus/api/config/SecurityFilterTest.java`:

```java
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend/api && mvn -o -q test -Dtest=SecurityFilterTest
```
Expected: FAIL — `deveValidarOTokenVindoDoCookieDeAcesso` falha (`validateToken` nunca chamado, pois o filtro ainda lê só o header) e `deveIgnorarOHeaderAuthorization` falha (`validateToken` foi chamado com `jwt-do-header`).

- [ ] **Step 3: Write minimal implementation**

In `backend/api/src/main/java/com/domus/api/config/SecurityFilter.java`, replace the `recoverToken` method:

```java
    /**
     * O token vem do cookie httpOnly, nunca mais do header Authorization.
     *
     * <p>Não existe fallback pro header de propósito: mantê-lo deixaria o localStorage
     * viável no front e a migração seria só decorativa.
     */
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
```

Add the imports next to the existing ones:

```java
import com.domus.api.shared.security.AuthCookieFactory;
import jakarta.servlet.http.Cookie;
```

Also fix the now-stale comment on line ~40 (it names a class that was renamed):

```java
                        // O RequestIdFilter limpa o MDC ao fim da requisição.
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd backend/api && mvn -o -q test -Dtest=SecurityFilterTest
```
Expected: PASS — 4 testes, 0 falhas.

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/java/com/domus/api/config/SecurityFilter.java \
        backend/api/src/test/java/com/domus/api/config/SecurityFilterTest.java
git commit -m "feat(auth): SecurityFilter autentica pelo cookie, não pelo header

Sem fallback pro Authorization de propósito: mantê-lo deixaria o
localStorage viável no front e tornaria a migração decorativa.
Corrige de passagem um comentário que citava RequestContextFilter
(classe renomeada para RequestIdFilter)."
```

---

### Task 3: `SessaoDTO` + login e Google emitindo cookies

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/auth/DTO/SessaoDTO.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/auth/AuthenticationController.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/IgrejaController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/auth/AuthenticationControllerCookieTest.java`

**Interfaces:**
- Consumes: `AuthCookieFactory#access/#refresh` (Task 1); `AuthService#login` retornando `LoginResponseDTO` (inalterado); `GoogleAuthService#login`/`#registrar`; `IgrejaService#registrar`.
- Produces: `SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome)` — o corpo que o front passa a receber em login, Google e `/auth/me`.

`AuthService`, `GoogleAuthService` e `IgrejaService` **não mudam**: eles continuam devolvendo os tokens internamente e o controller os transforma em cookie. Menos churn e mantém a regra de negócio fora da camada HTTP.

- [ ] **Step 1: Write the failing test**

Create `backend/api/src/test/java/com/domus/api/modules/auth/AuthenticationControllerCookieTest.java`:

```java
package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.AuthenticationDTO;
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
                controller().googleLogin(new com.domus.api.modules.auth.DTO.GoogleLoginDTO("id-token-do-google"));

        List<String> cookies = resposta.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertNotNull(cookies);
        assertEquals(2, cookies.size());
        assertEquals("Bia", resposta.getBody().nome());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd backend/api && mvn -o -q test -Dtest=AuthenticationControllerCookieTest
```
Expected: FAIL com erro de compilação — `cannot find symbol: class SessaoDTO` e construtor de `AuthenticationController` com 3 argumentos.

- [ ] **Step 3: Create `SessaoDTO`**

Create `backend/api/src/main/java/com/domus/api/modules/auth/DTO/SessaoDTO.java`:

```java
package com.domus.api.modules.auth.DTO;

import java.util.UUID;

/**
 * O que o front precisa saber sobre a sessão — e nada além disso.
 *
 * <p>Não carrega token: os tokens viajam em cookie httpOnly e o JavaScript
 * nunca os vê. Este é o corpo de /auth/login, /auth/google/* e /auth/me.
 */
public record SessaoDTO(
        UUID id,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome
) {
}
```

- [ ] **Step 4: Rewrite `AuthenticationController`**

Replace `backend/api/src/main/java/com/domus/api/modules/auth/AuthenticationController.java` with:

```java
package com.domus.api.modules.auth;


import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.auth.DTO.ForgotPasswordDTO;
import com.domus.api.modules.auth.DTO.GoogleLoginDTO;
import com.domus.api.modules.auth.DTO.GoogleRegistrarDTO;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.auth.DTO.ResetPasswordDTO;
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.auth.DTO.TokenPairDTO;
import com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.AuthCookieFactory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final GoogleAuthService googleAuthService;
    private final AuthCookieFactory cookieFactory;

    @PostMapping("/login")
    public ResponseEntity<SessaoDTO> login(@RequestBody @Valid AuthenticationDTO data) {
        LoginResponseDTO r = authService.login(data);
        return comCookies(r.token(), r.refreshToken()).body(sessaoDe(r));
    }

    @PostMapping("/google/login")
    public ResponseEntity<SessaoDTO> googleLogin(@RequestBody @Valid GoogleLoginDTO data) {
        LoginResponseDTO r = googleAuthService.login(data.idToken());
        return comCookies(r.token(), r.refreshToken()).body(sessaoDe(r));
    }

    @PostMapping("/google/registrar")
    public ResponseEntity<SessaoDTO> googleRegistrar(@RequestBody @Valid GoogleRegistrarDTO data) {
        RegistrarIgrejaResponse r = googleAuthService.registrar(data);
        return comCookies(r.token(), r.refreshToken())
                .body(new SessaoDTO(r.id(), r.nome(), r.role(), r.igrejaId(), r.igrejaNome()));
    }

    /**
     * Quem sou eu?
     *
     * <p>Com o cookie httpOnly o JavaScript não consegue mais ler a sessão, então o servidor
     * vira o dono da verdade e o front pergunta. Rota autenticada: sem cookie válido o
     * Spring Security devolve 401 pelo HttpStatusEntryPoint, antes de chegar aqui.
     */
    @GetMapping("/me")
    public ResponseEntity<SessaoDTO> me(@AuthenticationPrincipal Usuario usuario) {
        return ResponseEntity.ok(new SessaoDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().getNome(),
                usuario.getIgreja().getId(),
                usuario.getIgreja().getNome()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(name = AuthCookieFactory.COOKIE_REFRESH, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("REFRESH_INVALIDO", "Sessão expirada. Faça login novamente.");
        }
        TokenPairDTO par = authService.refresh(refreshToken);
        return comCookies(par.token(), par.refreshToken()).build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AuthCookieFactory.COOKIE_REFRESH, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        // Expira os cookies mesmo sem refresh válido: o JS não consegue apagá-los sozinho.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.accessExpirado().toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refreshExpirado().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody @Valid ForgotPasswordDTO data) {
        passwordResetService.solicitar(data.email());
        // Resposta genérica de propósito: não revela se o e-mail existe (anti-enumeração).
        return ResponseEntity.ok(Map.of(
                "message", "Se houver uma conta com esse e-mail, enviamos um link para redefinir a senha."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@RequestBody @Valid ResetPasswordDTO data) {
        passwordResetService.redefinir(data.token(), data.novaSenha());
        return ResponseEntity.ok(Map.of("message", "Senha redefinida com sucesso. Faça login com a nova senha."));
    }

    private ResponseEntity.BodyBuilder comCookies(String access, String refresh) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieFactory.access(access).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refresh(refresh).toString());
    }

    private SessaoDTO sessaoDe(LoginResponseDTO r) {
        return new SessaoDTO(r.id(), r.nome(), r.role(), r.igrejaId(), r.igrejaNome());
    }
}
```

- [ ] **Step 5: Update `IgrejaController` to emit cookies too**

`/igrejas/registrar` cria a igreja e já deixa a pessoa logada, então também precisa emitir cookies em vez de devolver token no corpo.

Read the current file first:

```bash
cd backend/api && cat src/main/java/com/domus/api/modules/igreja/IgrejaController.java
```

Change the `cadastrarIgreja` handler so that it returns `ResponseEntity<SessaoDTO>`, injecting `AuthCookieFactory cookieFactory` as a new `final` field (the class already uses constructor injection via Lombok).

**Preserve the `201 CREATED`** — the current handler uses `ResponseEntity.status(HttpStatus.CREATED)`, not `ok()`. Trocar para 200 seria uma regressão silenciosa de contrato, alheia ao objetivo desta migração:

```java
    @PostMapping("/registrar")
    public ResponseEntity<SessaoDTO> cadastrarIgreja(
            @RequestBody @Valid RegistrarIgrejaAdminRequest data) {
        RegistrarIgrejaResponse response = igrejaService.registrar(data);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, cookieFactory.access(response.token()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieFactory.refresh(response.refreshToken()).toString())
                .body(new SessaoDTO(
                        response.id(), response.nome(), response.role(),
                        response.igrejaId(), response.igrejaNome()));
    }
```

Add the imports:

```java
import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.shared.security.AuthCookieFactory;
import org.springframework.http.HttpHeaders;
```

Keep the rest of the file (annotations, other handlers, the `@RequestMapping`) exactly as it is.

- [ ] **Step 6: Delete the now-unused `RefreshRequestDTO`**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
rm backend/api/src/main/java/com/domus/api/modules/auth/DTO/RefreshRequestDTO.java
grep -rn "RefreshRequestDTO" backend/api/src/ || echo "OK: nenhuma referência restante"
```
Expected: `OK: nenhuma referência restante`

- [ ] **Step 7: Run the full test suite**

```bash
cd backend/api && mvn -o -q test
```
Expected: PASS — todos os testes, incluindo `AuthenticationControllerCookieTest`, `AuthServiceContaSemSenhaTest` e `GoogleAuthServiceTest`.

- [ ] **Step 8: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/java/com/domus/api/modules/auth/ \
        backend/api/src/main/java/com/domus/api/modules/igreja/IgrejaController.java \
        backend/api/src/test/java/com/domus/api/modules/auth/AuthenticationControllerCookieTest.java
git commit -m "feat(auth): login, Google e registro emitem cookies; adiciona GET /auth/me

Os tokens saem do corpo da resposta e passam a viajar em cookie httpOnly.
Refresh e logout leem o refresh do cookie (RefreshRequestDTO removido).
/auth/me existe porque, sem acesso do JS ao cookie, o servidor vira dono
da verdade da sessão — e de quebra mata a role velha em cache no front.
AuthService/GoogleAuthService/IgrejaService seguem intactos: a conversão
para cookie fica na camada HTTP."
```

---

### Task 4: Reativar o CSRF

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/config/SecurityConfig.java:37` (a linha `.csrf(csrf -> csrf.disable())`)

**Interfaces:**
- Consumes: nada de tasks anteriores.
- Produces: cookie `XSRF-TOKEN` (legível por JS) em toda resposta; exige header `X-XSRF-TOKEN` em POST/PUT/PATCH/DELETE fora das rotas isentas.

- [ ] **Step 1: Replace the csrf line**

In `backend/api/src/main/java/com/domus/api/config/SecurityConfig.java`, replace line 37:

```java
                .csrf(csrf -> csrf.disable())
```

with:

```java
                // Com o token em cookie, o navegador o envia SOZINHO em toda requisição —
                // inclusive nas disparadas por outro site. É isso que abre CSRF e o que o
                // header Authorization impedia de graça. Defesa em duas camadas:
                // SameSite=Lax (o navegador não anexa o cookie em POST cross-site) e
                // double-submit (o site atacante faz o cookie ser enviado, mas a Same-Origin
                // Policy o impede de LER o valor para repetir no header).
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler())
                        // Rotas públicas: rodam sem sessão, então não há cookie para um
                        // atacante cavalgar. Resíduo aceito: login CSRF (ver BACKLOG);
                        // o SameSite=Lax já o barra na prática.
                        .ignoringRequestMatchers(
                                "/auth/login",
                                "/auth/google/login",
                                "/auth/google/registrar",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/igrejas/registrar"))
```

- [ ] **Step 2: Add the handler bean method**

Add this private method to `SecurityConfig`, right before the `authenticationManager` bean:

```java
    /**
     * O XSRF-TOKEN é deliberadamente legível por JS — e isso não contradiz o httpOnly dos
     * cookies de sessão. Ele NÃO é credencial: não prova quem você é, só prova que quem
     * montou a requisição enxerga a mesma origem. Um XSS lendo-o não ganha nada, porque XSS
     * já roda dentro da origem. httpOnly defende do script injetado DENTRO da página; CSRF
     * defende do site DE FORA.
     *
     * <p>setCsrfRequestAttributeName(null) desliga o carregamento adiado (padrão do Spring
     * Security 6). Sem isso o token é resolvido tarde demais e o cookie não é escrito nas
     * respostas — o front nunca teria o valor para devolver no header.
     */
    private CsrfTokenRequestAttributeHandler csrfTokenRequestHandler() {
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
```

Add the imports:

```java
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
```

- [ ] **Step 3: Verify it compiles and tests still pass**

```bash
cd backend/api && mvn -o -q test
```
Expected: PASS — a suíte inteira.

- [ ] **Step 4: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/java/com/domus/api/config/SecurityConfig.java
git commit -m "feat(security): reativa CSRF (double-submit) agora que a sessão é cookie

O .csrf().disable() só era aceitável porque o token ia no header
Authorization, que nenhum navegador manda sozinho. Cookie inverte isso,
então CSRF volta a ser obrigatório — os dois itens são uma tarefa só.
Rotas públicas de auth ficam isentas: rodam sem sessão para cavalgar."
```

---

### Task 5: Proxy do Next (`/api/*` → backend)

**Files:**
- Modify: `frontend/next.config.ts`
- Modify: `frontend/.env` (não commitar) e `frontend/.env.example`

**Interfaces:**
- Consumes: nada do backend em tempo de build.
- Produces: rota same-origin `/api/*`; env server-side `API_INTERNAL_URL`; `NEXT_PUBLIC_API_URL` passa a valer `/api`.

Dev e prod passam a usar **o mesmo caminho** — se dev falasse direto com `localhost:8080` e prod pelo proxy, testaríamos um caminho e entregaríamos outro. Cookie é exatamente o assunto onde essa diferença morde.

- [ ] **Step 1: Add the rewrite and tighten the CSP**

In `frontend/next.config.ts`, replace the `apiUrl` const and the `nextConfig` object:

```ts
// Destino real do Spring. Env SERVER-SIDE (sem NEXT_PUBLIC_): só o servidor do Next a lê,
// para montar o rewrite. O navegador nunca fala com a API direto.
const apiInternalUrl = process.env.API_INTERNAL_URL ?? "http://localhost:8080";
```

Then in the `csp` array, replace the `connect-src` line with:

```ts
  // A API é same-origin agora (via rewrite /api/*), então 'self' basta.
  "connect-src 'self' https://accounts.google.com https://*.sentry.io",
```

And replace the `nextConfig` object with:

```ts
const nextConfig: NextConfig = {
  // O front chama /api/* na PRÓPRIA origem e o Next repassa pro Spring. Assim o cookie de
  // sessão é sempre first-party (SameSite=Lax) independente de onde a API for hospedada —
  // e a decisão de hospedagem sai do caminho crítico. Custo: um salto de rede a mais.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${apiInternalUrl}/:path*` },
    ];
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
    ];
  },
};
```

- [ ] **Step 2: Update the env files**

In `frontend/.env`, set:

```bash
NEXT_PUBLIC_API_URL=/api
API_INTERNAL_URL=http://localhost:8080
```

In `frontend/.env.example`, set the same keys with comments:

```bash
# Caminho que o navegador usa (proxy same-origin do Next). Não mudar.
NEXT_PUBLIC_API_URL=/api
# Destino real do Spring, lido só pelo servidor do Next (rewrites).
API_INTERNAL_URL=http://localhost:8080
```

- [ ] **Step 3: Verify the proxy works end to end**

Start the backend and the front (in separate terminals), then:

```bash
curl -i -s -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"nao-existe@teste.com","senha":"errada"}' | head -20
```
Expected: uma resposta **do Spring** (HTTP 400 com `"error":"CREDENCIAIS_INVALIDAS"`), provando que o rewrite alcançou o backend. Se vier 404 do Next, o rewrite não está ativo.

- [ ] **Step 4: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/next.config.ts frontend/.env.example
git commit -m "feat(front): proxy /api/* para o backend (same-origin)

O navegador passa a falar só com a origem do front; o Next repassa pro
Spring. Com isso o cookie de sessão é sempre first-party, independente
de onde a API for hospedada — desacopla a migração de cookie da decisão
de hospedagem, que ainda não foi tomada. CSP: connect-src volta a 'self'."
```

---

### Task 6: `authStore` sem `localStorage`

**Files:**
- Modify: `frontend/src/store/authStore.ts` (reescrita)
- Modify: `frontend/src/types/auth.types.ts`
- Modify: `frontend/src/services/auth.service.ts`
- Modify: `frontend/src/lib/endpoints.ts:9` (adicionar `ME`)

**Interfaces:**
- Consumes: `SessaoDTO` do backend (Task 3) — `{id, nome, role, igrejaId, igrejaNome}`.
- Produces: tipo `Sessao`; `authService.me(): Promise<Sessao>`; `authService.logout(): Promise<void>` (sem argumento); `useAuthStore` com `login(data: Sessao)`, `logout()`, `setHidratado()`, e os campos `id/nome/role/foto/igrejaId/igrejaNome/isAuthenticated/hidratado`. **Os nomes dos seletores não mudam** — nenhuma página precisa ser tocada.

Nota: `authService.refresh` **deixa de existir**. O refresh é chamado só de dentro do `api.ts` (Task 7), e importar o `authService` lá criaria um ciclo de import (`authService` → `api` → `authService`). O `api.ts` chama `api.post(Endpoints.auth.REFRESH)` direto.

- [ ] **Step 1: Update the types**

In `frontend/src/types/auth.types.ts`, replace the `LoginResponse`, `TokenPair` and `RegistrarIgrejaResponse` interfaces with:

```ts
/** O que o backend devolve sobre a sessão. Sem token: eles vivem em cookie httpOnly. */
export interface Sessao {
    id: string;
    nome: string;
    role: Role;
    igrejaId: string;
    igrejaNome: string;
}
```

Delete `LoginResponse`, `TokenPair` and `RegistrarIgrejaResponse` entirely, and keep everything else (`LoginRequest`, `GoogleRegistrarRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest`, `MensagemResponse`, `RegistrarIgrejaRequest`) untouched.

- [ ] **Step 2: Add the `ME` endpoint**

In `frontend/src/lib/endpoints.ts`, inside the `auth` object, add after the `LOGOUT` line:

```ts
    ME: '/auth/me',
```

- [ ] **Step 3: Update `auth.service.ts`**

Replace the import line and the token-carrying methods in `frontend/src/services/auth.service.ts`:

```ts
import { api } from "@/lib/api";
import { Endpoints } from "@/lib/endpoints";
import { ForgotPasswordRequest, GoogleRegistrarRequest, LoginRequest, MensagemResponse, RegistrarIgrejaRequest, ResetPasswordRequest, Sessao } from "@/types/auth.types";

export const authService = {
    login: (data: LoginRequest) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.LOGIN, data).then(res => res.data),
    googleLogin: (idToken: string) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.GOOGLE_LOGIN, { idToken }).then(res => res.data),
    googleRegistrar: (data: GoogleRegistrarRequest) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.GOOGLE_REGISTRAR, data).then(res => res.data),
    /** Quem sou eu? O servidor é o dono da verdade — o JS não lê o cookie httpOnly. */
    me: () : Promise<Sessao> =>
        api.get<Sessao>(Endpoints.auth.ME).then(res => res.data),
    /** Sem argumento: o refresh vai no cookie. O servidor expira os dois cookies. */
    logout: () : Promise<void> =>
        api.post(Endpoints.auth.LOGOUT).then(() => undefined),
    forgotPassword: (data: ForgotPasswordRequest) : Promise<MensagemResponse> =>
        api.post<MensagemResponse>(Endpoints.auth.FORGOT_PASSWORD, data).then(res => res.data),
    resetPassword: (data: ResetPasswordRequest) : Promise<MensagemResponse> =>
        api.post<MensagemResponse>(Endpoints.auth.RESET_PASSWORD, data).then(res => res.data),
    registrarIgreja: (data : RegistrarIgrejaRequest) : Promise<Sessao> =>
        api.post<Sessao>(Endpoints.auth.REGISTER_IGREJA, data).then(res => res.data),
}
```

- [ ] **Step 4: Rewrite `authStore.ts`**

Replace `frontend/src/store/authStore.ts` with:

```ts
import { create } from 'zustand'
import type { Role } from '@/types/usuario.types'
import type { Sessao } from '@/types/auth.types'

/**
 * Estado da sessão — em MEMÓRIA, nunca em localStorage.
 *
 * Os tokens vivem em cookie httpOnly (o JS não os lê) e a verdade sobre a sessão é do
 * servidor: no load, o AuthGuard pergunta via GET /auth/me e popula este store.
 * Persistir isso no localStorage seria o front ADIVINHAR — o cookie pode ter expirado
 * enquanto o localStorage segue afirmando que há sessão.
 */
interface AuthState {
  id: string | null
  nome: string | null
  role: Role | null
  foto: string | null
  igrejaId: string | null
  igrejaNome: string | null
  isAuthenticated: boolean
  /** true = já perguntamos ao servidor quem somos (não "o localStorage foi lido"). */
  hidratado: boolean
  login: (data: Sessao) => void
  logout: () => void
  atualizarUsuarioLogado: (data: Partial<Pick<AuthState, 'nome' | 'role'>>) => void
  setHidratado: () => void
}

const estadoDeslogado = {
  id: null,
  nome: null,
  role: null,
  foto: null,
  igrejaId: null,
  igrejaNome: null,
  isAuthenticated: false,
} as const

export const useAuthStore = create<AuthState>()((set) => ({
  ...estadoDeslogado,
  hidratado: false,
  login: (data) => set({ ...data, foto: null, isAuthenticated: true, hidratado: true }),
  logout: () => set({ ...estadoDeslogado, hidratado: true }),
  atualizarUsuarioLogado: (data) => set(data),
  setHidratado: () => set({ hidratado: true }),
}))
```

- [ ] **Step 5: Verify nothing references the removed fields**

```bash
cd frontend
grep -rn "setTokens\|refreshToken\|state.token\|s.token\|domus:auth" src/ || echo "OK: nenhuma referência restante"
```
Expected: só aparecem ocorrências em `src/lib/api.ts` e `src/components/layout/Sidebar.tsx` e `src/hooks/auth/` — todas serão corrigidas na Task 7. Anote quais apareceram.

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/store/authStore.ts frontend/src/types/auth.types.ts \
        frontend/src/services/auth.service.ts frontend/src/lib/endpoints.ts
git commit -m "refactor(auth): authStore em memória, sem token e sem localStorage

Saem token, refreshToken, setTokens e o middleware persist inteiro — com
ele o localStorage some da autenticação, inclusive o id. Os seletores
usados pelas telas mantêm os mesmos nomes, então nenhuma página muda:
só a ORIGEM do dado passa a ser o servidor (GET /auth/me).
NOTA: api.ts, Sidebar e hooks de auth ainda não compilam — Task 7."
```

---

### Task 7: `api.ts`, `AuthGuard`, call sites e remoção da auth do `proxy.ts`

**Files:**
- Modify: `frontend/src/lib/api.ts` (reescrita)
- Modify: `frontend/src/components/auth/AuthGuard.tsx` (reescrita)
- Modify: `frontend/src/proxy.ts` (reescrita)
- Modify: `frontend/src/components/layout/Sidebar.tsx:60-75` (handler de logout)
- Modify: `frontend/src/hooks/auth/useLogin.ts:72,104`
- Modify: `frontend/src/hooks/auth/UseRegistrarIgreja.ts:84,148`

**Interfaces:**
- Consumes: `authService.me/refresh/logout` e `useAuthStore.login/logout/setHidratado` (Task 6); rota `/api` (Task 5).
- Produces: front funcional de ponta a ponta.

- [ ] **Step 1: Rewrite `api.ts`**

Replace `frontend/src/lib/api.ts` with:

```ts
import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/store/authStore'
import { Endpoints } from '@/lib/endpoints'

export const api = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  // Cookies same-origin já iriam de qualquer forma; explícito porque a sessão depende disso.
  withCredentials: true,
})

// Não há interceptor de request: o token vive em cookie httpOnly e o navegador o envia
// sozinho. O header X-XSRF-TOKEN do CSRF também é automático — os defaults do axios já são
// xsrfCookieName 'XSRF-TOKEN' e xsrfHeaderName 'X-XSRF-TOKEN', e como o proxy nos deixa
// same-origin ele faz isso sem configuração.

// Endpoints de auth que NÃO devem disparar uma tentativa de refresh ao receber 401.
// /auth/me NÃO entra aqui de propósito: se o access expirou mas o refresh é válido,
// queremos justamente que o load renove a sessão em vez de deslogar o usuário.
const rotasAuth = [Endpoints.auth.LOGIN, Endpoints.auth.REFRESH, Endpoints.auth.LOGOUT]

// Single-flight: um único refresh em andamento por vez. Requisições 401 concorrentes
// esperam nesta mesma promessa em vez de dispararem refreshes paralelos (que a rotação
// do backend invalidaria entre si).
let refreshPromise: Promise<void> | null = null

function encerrarSessao() {
  useAuthStore.getState().logout()
  if (typeof window !== 'undefined') {
    window.location.href = '/login'
  }
}

// O servidor reemite os cookies na resposta; o front não vê nem toca em token nenhum.
async function renovarAccessToken(): Promise<void> {
  await api.post(Endpoints.auth.REFRESH)
}

// Interceptor de response — no 401, tenta renovar o access token uma vez e reenvia a
// requisição original. Se o refresh falhar, encerra a sessão de verdade.
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined
    const status = error.response?.status
    const url = original?.url ?? ''

    const ehRotaAuth = rotasAuth.some((rota) => url.includes(rota))

    if (status !== 401 || !original || ehRotaAuth) {
      return Promise.reject(error)
    }

    if (original._retry) {
      encerrarSessao()
      return Promise.reject(error)
    }
    original._retry = true

    try {
      if (!refreshPromise) {
        refreshPromise = renovarAccessToken().finally(() => {
          refreshPromise = null
        })
      }
      await refreshPromise
      return api(original)
    } catch {
      // Sem sessão renovável: limpa o estado. Não chamamos /auth/logout aqui — o refresh
      // já está morto, e o cookie de access expira sozinho em 10 min.
      encerrarSessao()
      return Promise.reject(error)
    }
  }
)
```

- [ ] **Step 2: Rewrite `AuthGuard.tsx`**

Replace `frontend/src/components/auth/AuthGuard.tsx` with:

```tsx
'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useAuthStore } from '@/store/authStore'
import { authService } from '@/services/auth.service'

/**
 * Porteiro da área autenticada.
 *
 * Como o token vive em cookie httpOnly, o JS não consegue olhar e saber se há sessão —
 * então perguntamos ao servidor (GET /auth/me) uma vez, no load. Enquanto a resposta não
 * vem, nada é renderizado (evita piscar tela).
 *
 * Não confundir com falta de PERMISSÃO: quem não tem role para uma tela vê o componente
 * AcessoRestrito na própria página. Aqui só tratamos ausência de SESSÃO.
 */
export function AuthGuard({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const hidratado = useAuthStore((s) => s.hidratado)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const login = useAuthStore((s) => s.login)
  const setHidratado = useAuthStore((s) => s.setHidratado)

  useEffect(() => {
    if (hidratado) return
    let cancelado = false

    authService
      .me()
      .then((sessao) => {
        if (!cancelado) login(sessao)
      })
      .catch(() => {
        if (!cancelado) setHidratado()
      })

    return () => {
      cancelado = true
    }
  }, [hidratado, login, setHidratado])

  useEffect(() => {
    if (hidratado && !isAuthenticated) router.replace('/login')
  }, [hidratado, isAuthenticated, router])

  if (!hidratado || !isAuthenticated) return null
  return <>{children}</>
}
```

- [ ] **Step 3: Strip the auth logic from `proxy.ts`**

O `proxy.ts` checava a presença de um cookie que qualquer JS podia forjar (`domus:token=banana` passava) — nunca foi segurança, era conforto visual. E agora quebraria de verdade: com `domus_access` durando 10 minutos reais, ficar idle e dar F5 chutaria o usuário pro `/login` com a sessão válida, porque o `domus_refresh` (7 dias) tem `Path=/api/auth` e o navegador nem o manda numa requisição de página.

Replace `frontend/src/proxy.ts` with:

```ts
import { NextResponse } from 'next/server'

/**
 * A decisão de sessão saiu daqui.
 *
 * Este middleware checava a PRESENÇA de um cookie que qualquer JS podia forjar — nunca foi
 * um porteiro, era conforto visual. O porteiro sempre foi o backend, e no cliente quem
 * decide agora é o AuthGuard + GET /auth/me, que é a verdade real.
 */
export function proxy() {
  return NextResponse.next()
}

export const config = {
  matcher: [],
}
```

- [ ] **Step 4: Fix the logout in `Sidebar.tsx`**

Read the current handler:

```bash
cd frontend && sed -n '55,80p' src/components/layout/Sidebar.tsx
```

Replace the `handleLogout` function body so it calls `authService.logout()` **without arguments** (the refresh goes in the cookie) and drops any read of `refreshToken` from the store:

```tsx
  async function handleLogout() {
    try {
      await authService.logout()
    } catch {
      // Sessão já pode estar morta no servidor; o logout local acontece de qualquer forma.
    }
    logout()
    router.push('/login')
  }
```

Keep the rest of the component (imports, `const logout = useAuthStore((state) => state.logout)`, JSX) as it is, removing only the now-invalid `refreshToken` selector if present.

- [ ] **Step 5: Fix the login call sites**

In `frontend/src/hooks/auth/useLogin.ts`, the `response` of `authService.login(data)` (line ~72) and `authService.googleLogin(idToken)` (line ~104) is now a `Sessao` — no `token`/`refreshToken`. The `login(...)` of the store takes it directly:

```ts
const response = await authService.login(data)
login(response)
```

and

```ts
const response = await authService.googleLogin(idToken)
login(response)
```

Apply the same in `frontend/src/hooks/auth/UseRegistrarIgreja.ts` for `authService.registrarIgreja(...)` (line ~84) and `authService.googleRegistrar(...)` (line ~148): pass the response straight into the store's `login(...)`, removing any `token`/`refreshToken` field spread.

- [ ] **Step 6: Add the one-time localStorage cleanup**

Em `frontend/src/components/auth/AuthGuard.tsx`, add this `useEffect` right after the two existing ones:

```tsx
  // Limpeza única da migração: chaves órfãs da era do localStorage. Sem isso, token velho
  // fica apodrecendo na máquina de quem já usou o sistema.
  useEffect(() => {
    localStorage.removeItem('domus:token')
    localStorage.removeItem('domus:auth')
    document.cookie = 'domus:token=; path=/; max-age=0'
  }, [])
```

- [ ] **Step 7: Typecheck and build**

```bash
cd frontend
rm -rf .next
npx tsc --noEmit
```
Expected: exit 0, sem erros. (O `rm -rf .next` evita erro de tipos gerados obsoletos referenciando rotas removidas.)

```bash
npm run build
```
Expected: build passa, exit 0.

- [ ] **Step 8: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/lib/api.ts frontend/src/components/auth/AuthGuard.tsx \
        frontend/src/proxy.ts frontend/src/components/layout/Sidebar.tsx \
        frontend/src/hooks/auth/useLogin.ts frontend/src/hooks/auth/UseRegistrarIgreja.ts
git commit -m "feat(front): sessão via cookie httpOnly ponta a ponta

api.ts perde o interceptor de request (o navegador manda o cookie sozinho)
e o CSRF vai de graça nos defaults do axios. AuthGuard pergunta ao servidor
quem somos via GET /auth/me. proxy.ts perde a lógica de auth: checava um
cookie forjável e quebraria com o access de 10 min reais, chutando pro
login quem tem refresh válido. Limpeza única das chaves órfãs."
```

---

### Task 8: Validação ao vivo

**Files:**
- Modify: `backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`
- Modify: `backend/api/CLAUDE.md`

**Interfaces:**
- Consumes: tudo das Tasks 1-7.
- Produces: evidência de que o objetivo foi atingido.

Testes unitários provam que as peças funcionam; só a validação ao vivo prova que a **sessão** funciona. É aqui que a verdade aparece.

- [ ] **Step 1: Restart both services**

Backend (de `backend/api`) e front (de `frontend`, `npm run dev`). Confirme que o Redis está de pé.

- [ ] **Step 2: Confirm the cookie attributes**

```bash
curl -i -s -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"SEU_EMAIL","senha":"SUA_SENHA"}' | grep -i 'set-cookie'
```
Expected: três `Set-Cookie` — `domus_access` com `Path=/api; Secure; HttpOnly; SameSite=Lax`; `domus_refresh` com `Path=/api/auth; Secure; HttpOnly; SameSite=Lax`; e `XSRF-TOKEN` **sem** `HttpOnly` (é o esperado — ver o comentário no `SecurityConfig`).

E confirme que o corpo **não** traz token:

```bash
curl -s -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"SEU_EMAIL","senha":"SUA_SENHA"}'
```
Expected: JSON com `id`, `nome`, `role`, `igrejaId`, `igrejaNome` — e **nenhum** campo `token` ou `refreshToken`.

- [ ] **Step 3: Confirm CSRF blocks a POST without the header**

```bash
COOKIES=$(mktemp)
curl -s -c "$COOKIES" -X POST http://localhost:3000/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"SEU_EMAIL","senha":"SUA_SENHA"}' > /dev/null

curl -i -s -b "$COOKIES" -X POST http://localhost:3000/api/categorias \
  -H 'Content-Type: application/json' -d '{"nome":"Teste CSRF"}' | head -1
```
Expected: **`HTTP/1.1 403`** — o cookie de sessão foi enviado, mas sem o header `X-XSRF-TOKEN` o Spring recusa. É exatamente o ataque que estamos barrando.

- [ ] **Step 4: The test that matters most — the browser**

No navegador, faça login e abra o console do DevTools:

```js
document.cookie
```
Expected: **NÃO** pode aparecer `domus_access` nem `domus_refresh`. Só `XSRF-TOKEN` (e o que mais for público). Se algum dos dois aparecer, o `httpOnly` falhou e o trabalho todo foi em vão.

Confirme também em Application → Local Storage: **nenhuma** chave `domus:token` ou `domus:auth`.

E em Application → Cookies: `domus_access` e `domus_refresh` com a coluna **HttpOnly marcada**.

- [ ] **Step 5: Confirm the session survives an expired access token**

Fique **mais de 10 minutos** sem interagir (ou reduza `JWT_EXPIRATION_MS` temporariamente para `60000` e espere 1 min). Depois navegue entre telas e dê F5.
Expected: a app continua funcionando, **sem cair no login**. Na aba Network deve aparecer um `POST /api/auth/refresh` (204) seguido do reenvio da requisição original. Este passo é o que prova que o bug do `proxy.ts` foi realmente evitado.

- [ ] **Step 6: Confirm logout**

Clique em sair.
Expected: redireciona pro `/login`; em Application → Cookies, `domus_access` e `domus_refresh` sumiram.

- [ ] **Step 7: Record the residuals in the BACKLOG**

In `backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`, add to the section `## Segurança / autorização — a discutir (decisão de produto)`:

```markdown
- **Login CSRF (resíduo aceito na migração de cookie, 2026-07-16).** As rotas públicas de
  auth (`/auth/login`, `/auth/google/*`, `/igrejas/registrar`, `/auth/forgot-password`,
  `/auth/reset-password`) são isentas do double-submit: rodam sem sessão para um atacante
  cavalgar, e protegê-las exigiria buscar um token CSRF antes de cada formulário público em
  4 telas. Fica possível o **login CSRF** (forçar a vítima a logar na conta do atacante e
  digitar dados achando que é a própria). Impacto modesto e o `SameSite=Lax` já o barra na
  prática (é POST cross-site). Reavaliar se surgir fluxo sensível pré-login.

- **Janela de convivência cookie+header.** A migração para cookie httpOnly matou toda sessão
  existente (o `SecurityFilter` parou de ler o header `Authorization`). Foi aceitável porque
  não havia usuário real. Se um dia for preciso migrar auth sem deslogar todo mundo, o
  padrão é ler cookie **e** header por uma janela e só então remover o header.
```

Add to the section `## Fora do scope do piloto (próximo scope / camada comercial)`:

```markdown
- **Decisão de hospedagem.** Desacoplada de propósito pelo proxy `/api/*` do Next: o cookie
  é first-party independente de onde o Spring rodar. Quando for decidir, note que um domínio
  único (front + `api.dominio`) permitiria remover o proxy e o salto de rede extra — mas nada
  obriga.
```

- [ ] **Step 8: Tick the roadmap**

In `backend/api/CLAUDE.md`, replace the `- [ ] **⚠️ Token fora do `localStorage` (XSS):**` item (and its whole paragraph) with:

```markdown
    - [x] **Token fora do `localStorage` (XSS)** — **FEITO** (2026-07-16): a sessão vive em
      cookies `httpOnly`+`Secure`+`SameSite=Lax` emitidos pelo backend (`domus_access` 10 min,
      `domus_refresh` 7 dias com `Path` estreito). Saiu o `persist` do zustand, o
      `localStorage.setItem` e o `document.cookie` setado por JS. **CSRF reativado** junto
      (double-submit via `CookieCsrfTokenRepository`), como exigia o modelo de cookie.
      Entrou `GET /auth/me` (o servidor virou dono da verdade da sessão) e o front passou a
      falar com a API por um proxy same-origin (`/api/*`) — o que desacopla o cookie da
      decisão de hospedagem. Resíduos anotados no BACKLOG (login CSRF nas rotas públicas).
      Ver spec/plano em `docs/superpowers/`.
```

- [ ] **Step 9: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md
git commit -m "docs(backlog): resíduos da migração de cookie httpOnly + CSRF

Login CSRF nas rotas públicas isentas, janela de convivência cookie+header
(não feita: não há usuário real) e a nota de que a decisão de hospedagem
ficou desacoplada pelo proxy do Next."
```

---

## Verificação final

- [ ] `cd backend/api && mvn -o -q test` → PASS
- [ ] `cd frontend && rm -rf .next && npx tsc --noEmit && npm run build` → exit 0
- [ ] `document.cookie` no navegador **não** mostra `domus_access` nem `domus_refresh`
- [ ] `localStorage` sem `domus:token` e sem `domus:auth`
- [ ] POST sem `X-XSRF-TOKEN` → 403
- [ ] Sessão sobrevive a 10+ min idle via refresh transparente
- [ ] `git status` limpo (nenhum `.env` staged)
