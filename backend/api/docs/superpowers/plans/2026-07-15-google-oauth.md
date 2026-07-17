# Google OAuth (login + cadastro) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar "Entrar com Google" e "Cadastrar minha igreja com Google" ao lado do login/senha nativo, reusando toda a emissão de sessão (JWT + refresh) já existente.

**Architecture:** O front (Next.js + `@react-oauth/google`) obtém um ID token do Google e o envia ao backend. Um `GoogleAuthService` valida o token com `GoogleIdTokenVerifier`, identifica a pessoa por `google_sub`/e-mail, e daí em diante reusa `TokenService` + `RefreshTokenService`. A criação de igreja+admin é extraída para um método compartilhado entre o cadastro nativo e o Google.

**Tech Stack:** Java 21, Spring Boot 3.5.13, `google-api-client` (backend), `@react-oauth/google` (frontend), Flyway, PostgreSQL, Redis.

## Global Constraints

- Java 21, Spring Boot 3.5.13 (parent).
- `igreja_id` sempre do JWT, nunca do corpo (não se aplica aqui — auth é pré-tenant, mas a identidade vem SEMPRE do ID token validado, nunca do corpo).
- Services retornam DTOs, nunca entidades.
- `BusinessException` → HTTP 400 + `codigo` (convenção do `GlobalExceptionHandler`). Front distingue pelo `codigo`.
- Soft delete (`delete_at`) e `@SQLRestriction("delete_at IS NULL")` já ativos na entidade `Usuario`.
- Client ID Google: `1006320938803-sbqitjuq96r07cog7s77a9h4i1bdursh.apps.googleusercontent.com` (público; via env).
- Migrations Flyway: próxima é `V10__...` (a última é `V9__create_outbox.sql`).
- Testes back rodam com `./mvnw test` (ou `mvn test`).

---

### Task 1: Migration + entidade (schema para contas Google)

**Files:**
- Create: `src/main/resources/db/migration/V10__add_google_auth_ao_usuario.sql`
- Modify: `src/main/java/com/domus/api/modules/usuario/Usuario.java:43-44` (senhaHash nullable + campo googleSub)

**Interfaces:**
- Consumes: nada.
- Produces: coluna `usuario.google_sub` (VARCHAR, nullable, único); `usuario.senha_hash` nullable. Entidade `Usuario` com `String getGoogleSub()` / `setGoogleSub(String)` e `senhaHash` nullable.

- [ ] **Step 1: Escrever a migration**

```sql
-- V10__add_google_auth_ao_usuario.sql
-- Suporte a contas que entram só pelo Google:
-- senha_hash passa a aceitar NULL (conta sem senha nativa)
-- google_sub guarda o ID imutável do Google, único (múltiplos NULLs permitidos pelo Postgres)
ALTER TABLE usuario ALTER COLUMN senha_hash DROP NOT NULL;
ALTER TABLE usuario ADD COLUMN google_sub VARCHAR(255);
CREATE UNIQUE INDEX ux_usuario_google_sub ON usuario (google_sub);
```

- [ ] **Step 2: Ajustar a entidade Usuario**

Em `Usuario.java`, trocar a coluna senhaHash para nullable e adicionar googleSub logo abaixo:

```java
    @Column(name = "senha_hash", length = 255)
    private String senhaHash;

    @Column(name = "google_sub", length = 255, unique = true)
    private String googleSub;
```

(Lombok `@Getter/@Setter` já geram `getGoogleSub()/setGoogleSub()`.)

- [ ] **Step 3: Subir a app para o Flyway aplicar a migration**

Run: `./mvnw -q spring-boot:run` (ou reiniciar pela IDE). Esperado: log do Flyway "Migrating schema ... to version 10" sem erro; app sobe.
Parar a app depois de confirmar.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V10__add_google_auth_ao_usuario.sql src/main/java/com/domus/api/modules/usuario/Usuario.java
git commit -m "feat(auth): schema para contas Google (senha_hash nullable + google_sub)"
```

---

### Task 2: Dependência + configuração do verificador Google

**Files:**
- Modify: `pom.xml` (nova dependency)
- Modify: `src/main/resources/application.properties` (propriedade `google.client-id`)
- Modify: `.env` e `.env.example` (GOOGLE_CLIENT_ID)
- Create: `src/main/java/com/domus/api/config/GoogleTokenConfig.java`

**Interfaces:**
- Consumes: nada.
- Produces: bean `com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier` (Spring bean singleton) configurado com o Client ID; disponível para injeção.

- [ ] **Step 1: Adicionar a dependency no pom.xml**

Junto às outras dependencies (perto do `resend-java`):

```xml
        <dependency>
            <groupId>com.google.api-client</groupId>
            <artifactId>google-api-client</artifactId>
            <version>2.7.0</version>
        </dependency>
```

- [ ] **Step 2: Recarregar Maven**

Run: `./mvnw -q dependency:resolve`. Esperado: baixa `google-api-client` sem erro. (Na IDE: "Reload All Maven Projects".)

- [ ] **Step 3: Adicionar propriedade em application.properties**

```properties
# ─── Google OAuth ───
google.client-id=${GOOGLE_CLIENT_ID}
```

- [ ] **Step 4: Adicionar a env no .env e .env.example**

No `.env` (valor real) e `.env.example` (placeholder):

```
# .env
GOOGLE_CLIENT_ID=1006320938803-sbqitjuq96r07cog7s77a9h4i1bdursh.apps.googleusercontent.com
# .env.example
GOOGLE_CLIENT_ID=seu-client-id.apps.googleusercontent.com
```

- [ ] **Step 5: Criar o bean do verificador**

```java
package com.domus.api.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class GoogleTokenConfig {

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${google.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }
}
```

- [ ] **Step 6: Subir a app para validar a config**

Run: `./mvnw -q spring-boot:run`. Esperado: app sobe sem erro de bean/propriedade faltante. Parar depois.

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/resources/application.properties .env.example src/main/java/com/domus/api/config/GoogleTokenConfig.java
git commit -m "chore(auth): dependency google-api-client e bean GoogleIdTokenVerifier"
```

(`.env` é gitignored — NÃO commitar.)

---

### Task 3: Repositório — busca por google_sub

**Files:**
- Modify: `src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java:39` (novo método)
- Test: `src/test/java/com/domus/api/modules/usuario/UsuarioRepositoryTest.java`

**Interfaces:**
- Consumes: entidade `Usuario` (Task 1).
- Produces: `Optional<Usuario> UsuarioRepository.findByGoogleSub(String googleSub)`.

- [ ] **Step 1: Escrever o teste (falha)**

```java
package com.domus.api.modules.usuario;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UsuarioRepositoryTest {

    @Autowired
    UsuarioRepository usuarioRepository;

    @Test
    void findByGoogleSub_retornaVazioQuandoNaoExiste() {
        assertThat(usuarioRepository.findByGoogleSub("sub-inexistente")).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar (compilação)**

Run: `./mvnw -q -Dtest=UsuarioRepositoryTest test`
Esperado: FALHA de compilação — método `findByGoogleSub` não existe.

- [ ] **Step 3: Implementar o método no repositório**

Em `UsuarioRepository.java`, adicionar:

```java
    Optional<Usuario> findByGoogleSub(String googleSub);
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -q -Dtest=UsuarioRepositoryTest test`
Esperado: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java src/test/java/com/domus/api/modules/usuario/UsuarioRepositoryTest.java
git commit -m "feat(auth): UsuarioRepository.findByGoogleSub"
```

---

### Task 4: Extrair criação de igreja+admin no IgrejaService (refatoração)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaService.java:39-99`
- Test: `src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java` (rodar o teste existente do registrar, se houver; senão validar via Task 8 manual)

**Interfaces:**
- Consumes: `Igreja`, `Membro`, `Usuario`, `Role`, repositórios existentes.
- Produces: método público `Usuario IgrejaService.criarIgrejaComAdmin(DadosNovaIgreja dados)` onde `DadosNovaIgreja` é um record com `(String nomeIgreja, String emailContato, String cnpj, String telefoneContato, String nomeAdmin, String emailAdmin, String senhaHashOuNull, String googleSubOuNull)`. Retorna o `Usuario` admin salvo (com igreja e membro persistidos). Faz as checagens de EMAIL_DUPLICADO e CNPJ_DUPLICADO. NÃO emite tokens (isso fica no chamador).

- [ ] **Step 1: Criar o record DadosNovaIgreja**

Create `src/main/java/com/domus/api/modules/igreja/DadosNovaIgreja.java`:

```java
package com.domus.api.modules.igreja;

public record DadosNovaIgreja(
        String nomeIgreja,
        String emailContato,
        String cnpj,
        String telefoneContato,
        String nomeAdmin,
        String emailAdmin,
        String senhaHashOuNull,
        String googleSubOuNull
) {}
```

- [ ] **Step 2: Extrair o método `criarIgrejaComAdmin` no IgrejaService**

Adicionar o método (mantendo `@Transactional`) contendo a lógica que hoje está dentro de `registrar`, e fazer `registrar` chamá-lo. O corpo:

```java
    @Transactional
    public Usuario criarIgrejaComAdmin(DadosNovaIgreja dados) {
        if (membroRepository.existsByEmail(dados.emailAdmin())) {
            log.warn("E-mail já cadastrado. email={}", dados.emailAdmin());
            throw new BusinessException("EMAIL_DUPLICADO", "E-mail já cadastrado no sistema.");
        }
        if (dados.cnpj() != null && !dados.cnpj().isBlank()
                && igrejaRepository.existsByCnpj(dados.cnpj())) {
            log.warn("CNPJ já cadastrado. cnpj={}", dados.cnpj());
            throw new BusinessException("CNPJ_DUPLICADO", "CNPJ já cadastrado no sistema.");
        }

        Igreja igreja = Igreja.builder()
                .nome(dados.nomeIgreja())
                .emailContato(dados.emailContato())
                .cnpj(dados.cnpj())
                .telefoneContato(dados.telefoneContato())
                .build();
        igrejaRepository.save(igreja);

        Role roleAdmin = roleRepository.findByNome("ADMIN_IGREJA")
                .orElseThrow(() -> new IllegalStateException("Role ADMIN_IGREJA não encontrada. Verifique o seed da migration V2."));

        Membro membroAdmin = Membro.builder()
                .igreja(igreja)
                .nome(dados.nomeAdmin())
                .email(dados.emailAdmin())
                .status(StatusMembro.ATIVO)
                .build();
        membroRepository.save(membroAdmin);

        Usuario admin = Usuario.builder()
                .igreja(igreja)
                .membro(membroAdmin)
                .senhaHash(dados.senhaHashOuNull())
                .googleSub(dados.googleSubOuNull())
                .ativo(true)
                .role(roleAdmin)
                .build();
        admin.registrarLogin();
        usuarioRepository.save(admin);
        log.info("Igreja + admin criados. usuario_id={}, igreja_id={}", admin.getId(), igreja.getId());
        return admin;
    }
```

- [ ] **Step 3: Reescrever `registrar` para reusar o método**

```java
    @Transactional
    public RegistrarIgrejaResponse registrar(RegistrarIgrejaAdminRequest request) {
        log.info("Iniciando o cadastro da igreja. nome={}, emailAdmin={}", request.getNomeIgreja(), request.getEmailAdmin());

        Usuario admin = criarIgrejaComAdmin(new DadosNovaIgreja(
                request.getNomeIgreja(),
                request.getEmailContato(),
                request.getCnpj(),
                request.getTelefoneContato(),
                request.getNomeAdmin(),
                request.getEmailAdmin(),
                passwordEncoder.encode(request.getSenhaAdmin()),
                null
        ));

        var token = tokenService.generateToken(admin);
        var refreshToken = refreshTokenService.criar(admin.getId());

        return new RegistrarIgrejaResponse(
                admin.getId(),
                token,
                refreshToken,
                admin.getNome(),
                admin.getRole().getNome(),
                admin.getIgreja().getId(),
                admin.getIgreja().getNome()
        );
    }
```

- [ ] **Step 4: Compilar**

Run: `./mvnw -q -DskipTests compile`
Esperado: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/DadosNovaIgreja.java src/main/java/com/domus/api/modules/igreja/IgrejaService.java
git commit -m "refactor(igreja): extrai criarIgrejaComAdmin reutilizável"
```

---

### Task 5: GoogleAuthService — login (cenário 1)

**Files:**
- Create: `src/main/java/com/domus/api/modules/auth/GoogleAuthService.java`
- Test: `src/test/java/com/domus/api/modules/auth/GoogleAuthServiceTest.java`

**Interfaces:**
- Consumes: `GoogleIdTokenVerifier` (Task 2), `UsuarioRepository.findByGoogleSub`/`findByEmail` (Task 3), `TokenService`, `RefreshTokenService`.
- Produces: `LoginResponseDTO GoogleAuthService.login(String idToken)`. Método privado `GoogleIdToken.Payload verificar(String idToken)` que lança `BusinessException("TOKEN_GOOGLE_INVALIDO", ...)` se inválido ou `email_verified != true`.

- [ ] **Step 1: Escrever os testes (falham)**

```java
package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaService;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.RefreshTokenService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GoogleAuthServiceTest {

    GoogleIdTokenVerifier verifier;
    UsuarioRepository usuarioRepository;
    TokenService tokenService;
    RefreshTokenService refreshTokenService;
    IgrejaService igrejaService;
    GoogleAuthService service;

    @BeforeEach
    void setup() {
        verifier = mock(GoogleIdTokenVerifier.class);
        usuarioRepository = mock(UsuarioRepository.class);
        tokenService = mock(TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        igrejaService = mock(IgrejaService.class);
        service = new GoogleAuthService(verifier, usuarioRepository, tokenService, refreshTokenService, igrejaService);
    }

    private GoogleIdToken tokenComPayload(String sub, String email, boolean emailVerified, String nome) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(sub);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.set("name", nome);
        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);
        return idToken;
    }

    private Usuario usuarioFake() {
        Igreja igreja = Igreja.builder().nome("Igreja X").build();
        igreja.setId(UUID.randomUUID());
        Membro membro = Membro.builder().nome("Fulano").email("fulano@x.com").igreja(igreja).build();
        Role role = new Role();
        role.setNome("ADMIN_IGREJA");
        return Usuario.builder().id(UUID.randomUUID()).igreja(igreja).membro(membro).role(role).ativo(true).build();
    }

    @Test
    void login_tokenInvalido_lancaTokenGoogleInvalido() throws Exception {
        when(verifier.verify("ruim")).thenReturn(null);
        assertThatThrownBy(() -> service.login("ruim"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Google");
    }

    @Test
    void login_emailNaoVerificado_lancaTokenGoogleInvalido() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", false, "A"));
        assertThatThrownBy(() -> service.login("t")).isInstanceOf(BusinessException.class);
    }

    @Test
    void login_achaPorGoogleSub_emiteSessao() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", true, "A"));
        Usuario u = usuarioFake();
        u.setGoogleSub("sub1");
        when(usuarioRepository.findByGoogleSub("sub1")).thenReturn(Optional.of(u));
        when(tokenService.generateToken(u)).thenReturn("jwt");
        when(refreshTokenService.criar(u.getId())).thenReturn("refresh");

        LoginResponseDTO resp = service.login("t");

        assertThat(resp.token()).isEqualTo("jwt");
        assertThat(resp.refreshToken()).isEqualTo("refresh");
        verify(usuarioRepository, never()).findByEmail(any());
    }

    @Test
    void login_achaPorEmail_gravaGoogleSub() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", true, "A"));
        Usuario u = usuarioFake();
        when(usuarioRepository.findByGoogleSub("sub1")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("a@a.com")).thenReturn(Optional.of(u));
        when(tokenService.generateToken(u)).thenReturn("jwt");
        when(refreshTokenService.criar(u.getId())).thenReturn("refresh");

        service.login("t");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getGoogleSub()).isEqualTo("sub1");
    }

    @Test
    void login_naoAcha_lancaContaNaoEncontrada() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub1", "a@a.com", true, "A"));
        when(usuarioRepository.findByGoogleSub("sub1")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("a@a.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("t"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Não encontramos");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -q -Dtest=GoogleAuthServiceTest test`
Esperado: FALHA de compilação — `GoogleAuthService` não existe.

- [ ] **Step 3: Implementar o GoogleAuthService (só login)**

```java
package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.LoginResponseDTO;
import com.domus.api.modules.igreja.IgrejaService;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.RefreshTokenService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final UsuarioRepository usuarioRepository;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    private final IgrejaService igrejaService;

    public LoginResponseDTO login(String idToken) {
        GoogleIdToken.Payload payload = verificar(idToken);
        String sub = payload.getSubject();
        String email = payload.getEmail();

        Usuario usuario = usuarioRepository.findByGoogleSub(sub).orElse(null);
        if (usuario == null) {
            usuario = usuarioRepository.findByEmail(email).orElse(null);
            if (usuario != null) {
                usuario.setGoogleSub(sub);
                usuarioRepository.save(usuario);
                log.info("Vínculo Google criado no primeiro login. usuario_id={}", usuario.getId());
            }
        }

        if (usuario == null) {
            throw new BusinessException("CONTA_NAO_ENCONTRADA",
                    "Não encontramos uma conta vinculada a este Google. Se você é responsável por uma igreja, cadastre-a primeiro. Se você é membro de uma igreja já cadastrada, peça ao administrador dela para conceder seu acesso.");
        }
        if (!usuario.isAtivo()) {
            throw new BusinessException("USUARIO_INATIVO",
                    "Sua conta está desativada. Entre em contato com o administrador.");
        }

        usuario.registrarLogin();
        usuarioRepository.save(usuario);

        String token = tokenService.generateToken(usuario);
        String refreshToken = refreshTokenService.criar(usuario.getId());
        log.info("Login Google bem-sucedido. usuario_id={}, igreja_id={}", usuario.getId(), usuario.getIgreja().getId());

        return new LoginResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().getNome(),
                usuario.getIgreja().getId(),
                usuario.getIgreja().getNome(),
                token,
                refreshToken
        );
    }

    GoogleIdToken.Payload verificar(String idToken) {
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("Falha ao verificar ID token do Google.", e);
            throw new BusinessException("TOKEN_GOOGLE_INVALIDO", "Não foi possível validar seu login com o Google. Tente novamente.");
        }
        if (token == null || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
            throw new BusinessException("TOKEN_GOOGLE_INVALIDO", "Não foi possível validar seu login com o Google. Tente novamente.");
        }
        return token.getPayload();
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -q -Dtest=GoogleAuthServiceTest test`
Esperado: PASS (5 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/GoogleAuthService.java src/test/java/com/domus/api/modules/auth/GoogleAuthServiceTest.java
git commit -m "feat(auth): GoogleAuthService.login (valida ID token + emite sessão)"
```

---

### Task 6: GoogleAuthService — cadastro (cenário 3)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/auth/GoogleAuthService.java` (novo método `registrar`)
- Create: `src/main/java/com/domus/api/modules/auth/DTO/GoogleRegistrarDTO.java`
- Test: `src/test/java/com/domus/api/modules/auth/GoogleAuthServiceTest.java` (adicionar casos)

**Interfaces:**
- Consumes: `IgrejaService.criarIgrejaComAdmin` (Task 4), `verificar` (Task 5).
- Produces: `RegistrarIgrejaResponse GoogleAuthService.registrar(GoogleRegistrarDTO dados)`. `GoogleRegistrarDTO` = record `(String idToken, String nomeIgreja, String cnpj, String telefoneContato)` com validações.

- [ ] **Step 1: Criar o DTO de entrada**

```java
package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleRegistrarDTO(
        @NotBlank(message = "idToken é obrigatório")
        String idToken,

        @NotBlank(message = "Nome da igreja é obrigatório")
        @Size(max = 255)
        String nomeIgreja,

        @Size(max = 18, message = "CNPJ deve ter no máximo 18 caracteres")
        String cnpj,

        @NotBlank(message = "Telefone para contato é obrigatório")
        @Pattern(regexp = "^\\d{10,11}$", message = "Telefone inválido. Informe DDD + número (10 ou 11 dígitos)")
        String telefoneContato
) {}
```

- [ ] **Step 2: Escrever os testes (falham)**

Adicionar em `GoogleAuthServiceTest`:

```java
    @Test
    void registrar_criaIgrejaComAdminSemSenha() throws Exception {
        when(verifier.verify("t")).thenReturn(tokenComPayload("sub9", "dono@ig.com", true, "Dono"));
        Usuario admin = usuarioFake();
        admin.setGoogleSub("sub9");
        when(igrejaService.criarIgrejaComAdmin(any())).thenReturn(admin);
        when(tokenService.generateToken(admin)).thenReturn("jwt");
        when(refreshTokenService.criar(admin.getId())).thenReturn("refresh");

        var dados = new com.domus.api.modules.auth.DTO.GoogleRegistrarDTO("t", "Nova Igreja", null, "11999999999");
        var resp = service.registrar(dados);

        assertThat(resp.token()).isEqualTo("jwt");
        ArgumentCaptor<com.domus.api.modules.igreja.DadosNovaIgreja> captor =
                ArgumentCaptor.forClass(com.domus.api.modules.igreja.DadosNovaIgreja.class);
        verify(igrejaService).criarIgrejaComAdmin(captor.capture());
        assertThat(captor.getValue().senhaHashOuNull()).isNull();
        assertThat(captor.getValue().googleSubOuNull()).isEqualTo("sub9");
        assertThat(captor.getValue().emailAdmin()).isEqualTo("dono@ig.com");
        assertThat(captor.getValue().nomeAdmin()).isEqualTo("Dono");
    }

    @Test
    void registrar_tokenInvalido_lanca() throws Exception {
        when(verifier.verify("t")).thenReturn(null);
        var dados = new com.domus.api.modules.auth.DTO.GoogleRegistrarDTO("t", "Nova Igreja", null, "11999999999");
        assertThatThrownBy(() -> service.registrar(dados)).isInstanceOf(BusinessException.class);
    }
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./mvnw -q -Dtest=GoogleAuthServiceTest test`
Esperado: FALHA de compilação — método `registrar` não existe.

- [ ] **Step 4: Implementar `registrar` no GoogleAuthService**

```java
    public com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse registrar(
            com.domus.api.modules.auth.DTO.GoogleRegistrarDTO dados) {
        GoogleIdToken.Payload payload = verificar(dados.idToken());
        String sub = payload.getSubject();
        String email = payload.getEmail();
        String nome = (String) payload.get("name");

        Usuario admin = igrejaService.criarIgrejaComAdmin(new com.domus.api.modules.igreja.DadosNovaIgreja(
                dados.nomeIgreja(),
                email,               // emailContato = e-mail do dono (verificado pelo Google)
                dados.cnpj(),
                dados.telefoneContato(),
                nome,
                email,
                null,                // sem senha nativa
                sub
        ));

        String token = tokenService.generateToken(admin);
        String refreshToken = refreshTokenService.criar(admin.getId());
        log.info("Cadastro Google concluído. usuario_id={}, igreja_id={}", admin.getId(), admin.getIgreja().getId());

        return new com.domus.api.modules.igreja.DTO.RegistrarIgrejaResponse(
                admin.getId(),
                token,
                refreshToken,
                admin.getNome(),
                admin.getRole().getNome(),
                admin.getIgreja().getId(),
                admin.getIgreja().getNome()
        );
    }
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw -q -Dtest=GoogleAuthServiceTest test`
Esperado: PASS (7 testes).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/GoogleAuthService.java src/main/java/com/domus/api/modules/auth/DTO/GoogleRegistrarDTO.java src/test/java/com/domus/api/modules/auth/GoogleAuthServiceTest.java
git commit -m "feat(auth): GoogleAuthService.registrar (cadastro de igreja via Google)"
```

---

### Task 7: Login nativo barra conta sem senha (CONTA_SEM_SENHA)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/auth/AuthService.java:31-78`
- Test: `src/test/java/com/domus/api/modules/auth/AuthServiceContaSemSenhaTest.java`

**Interfaces:**
- Consumes: `UsuarioRepository.findByEmail`, entidade `Usuario` (senhaHash pode ser null).
- Produces: `AuthService.login` lança `BusinessException("CONTA_SEM_SENHA", ...)` quando o usuário existe e `senhaHash == null`.

- [ ] **Step 1: Escrever o teste (falha)**

```java
package com.domus.api.modules.auth;

import com.domus.api.config.TokenService;
import com.domus.api.modules.auth.DTO.AuthenticationDTO;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.security.LoginAttemptService;
import com.domus.api.shared.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceContaSemSenhaTest {

    AuthenticationManager authenticationManager;
    TokenService tokenService;
    RefreshTokenService refreshTokenService;
    LoginAttemptService loginAttemptService;
    UsuarioRepository usuarioRepository;
    PasswordEncoder passwordEncoder;
    AuthService service;

    @BeforeEach
    void setup() {
        authenticationManager = mock(AuthenticationManager.class);
        tokenService = mock(TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(authenticationManager, tokenService, refreshTokenService,
                loginAttemptService, usuarioRepository, passwordEncoder);
    }

    @Test
    void login_contaSoGoogle_lancaContaSemSenha() {
        when(loginAttemptService.estaBloqueado("g@g.com")).thenReturn(false);
        Usuario u = Usuario.builder().senhaHash(null).ativo(true).build();
        when(usuarioRepository.findByEmail("g@g.com")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.login(new AuthenticationDTO("g@g.com", "qualquer")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Google");

        verifyNoInteractions(authenticationManager);
    }
}
```

(Confirmar a ordem dos parâmetros do construtor de `AuthService` — ver Task summary do arquivo atual; ajustar o `new AuthService(...)` se necessário para casar com os campos `@RequiredArgsConstructor`.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw -q -Dtest=AuthServiceContaSemSenhaTest test`
Esperado: FALHA — hoje não há a checagem; o mock do authenticationManager seria chamado.

- [ ] **Step 3: Implementar a checagem em AuthService.login**

Logo após o bloco `estaBloqueado` e antes do `try`, adicionar:

```java
        usuarioRepository.findByEmail(data.email())
                .filter(u -> u.getSenhaHash() == null)
                .ifPresent(u -> {
                    log.warn("Login nativo em conta só-Google. email={}", data.email());
                    throw new BusinessException("CONTA_SEM_SENHA",
                            "Esta conta usa login com Google. Entre com Google ou defina uma senha para acessar por e-mail.");
                });
```

- [ ] **Step 4: Rodar e ver passar**

Run: `./mvnw -q -Dtest=AuthServiceContaSemSenhaTest test`
Esperado: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/AuthService.java src/test/java/com/domus/api/modules/auth/AuthServiceContaSemSenhaTest.java
git commit -m "feat(auth): login nativo barra conta só-Google (CONTA_SEM_SENHA)"
```

---

### Task 8: Endpoints REST + rotas públicas

**Files:**
- Modify: `src/main/java/com/domus/api/modules/auth/AuthenticationController.java`
- Create: `src/main/java/com/domus/api/modules/auth/DTO/GoogleLoginDTO.java`
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java` (liberar as rotas)

**Interfaces:**
- Consumes: `GoogleAuthService.login`/`registrar`.
- Produces: `POST /auth/google/login` (body `{idToken}`) → `LoginResponseDTO`; `POST /auth/google/registrar` (body `GoogleRegistrarDTO`) → `RegistrarIgrejaResponse`. Ambas públicas.

- [ ] **Step 1: Criar o DTO de login**

```java
package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginDTO(@NotBlank(message = "idToken é obrigatório") String idToken) {}
```

- [ ] **Step 2: Adicionar os endpoints no controller**

Injetar `GoogleAuthService googleAuthService` (adicionar ao `@RequiredArgsConstructor` via campo `private final`) e adicionar:

```java
    @PostMapping("/google/login")
    public ResponseEntity<LoginResponseDTO> googleLogin(@RequestBody @Valid GoogleLoginDTO data) {
        return ResponseEntity.ok(googleAuthService.login(data.idToken()));
    }

    @PostMapping("/google/registrar")
    public ResponseEntity<RegistrarIgrejaResponse> googleRegistrar(@RequestBody @Valid GoogleRegistrarDTO data) {
        return ResponseEntity.ok(googleAuthService.registrar(data));
    }
```

(Adicionar os imports: `GoogleLoginDTO`, `GoogleRegistrarDTO`, `RegistrarIgrejaResponse`, `GoogleAuthService`.)

- [ ] **Step 3: Liberar as rotas no SecurityConfig**

Adicionar `/auth/google/login` e `/auth/google/registrar` à lista de `permitAll()` (junto de `/auth/login`, `/auth/refresh`, etc.).

- [ ] **Step 4: Subir a app e testar login com token inválido (smoke)**

Run: `./mvnw -q spring-boot:run` e em outra shell:
```bash
curl -s -X POST http://localhost:8080/auth/google/login -H 'Content-Type: application/json' -d '{"idToken":"token-falso"}'
```
Esperado: HTTP 400 com `"codigo":"TOKEN_GOOGLE_INVALIDO"`. Parar a app.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/AuthenticationController.java src/main/java/com/domus/api/modules/auth/DTO/GoogleLoginDTO.java src/main/java/com/domus/api/config/SecurityConfig.java
git commit -m "feat(auth): endpoints POST /auth/google/login e /auth/google/registrar"
```

---

### Task 9: Frontend — setup + serviço/tipos

**Files:**
- Modify: `frontend/package.json` (dependency `@react-oauth/google`)
- Modify: `frontend/.env.local` e `frontend/.env.example` (`NEXT_PUBLIC_GOOGLE_CLIENT_ID`)
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/services/auth.service.ts`
- Modify: `frontend/src/types/auth.types.ts`
- Modify: `frontend/src/app/(auth)/layout.tsx` (envolver com `GoogleOAuthProvider`)

**Interfaces:**
- Consumes: endpoints da Task 8.
- Produces: `authService.googleLogin(idToken)` → `LoginResponse`; `authService.googleRegistrar(payload)` → `RegistrarIgrejaResponse`. Provider Google disponível nas telas de auth.

- [ ] **Step 1: Instalar a lib**

Run (em `frontend/`): `npm install @react-oauth/google`
Esperado: adiciona a dependency sem erro.

- [ ] **Step 2: Adicionar a env pública**

Em `frontend/.env.local`:
```
NEXT_PUBLIC_GOOGLE_CLIENT_ID=1006320938803-sbqitjuq96r07cog7s77a9h4i1bdursh.apps.googleusercontent.com
```
Em `frontend/.env.example`:
```
NEXT_PUBLIC_GOOGLE_CLIENT_ID=seu-client-id.apps.googleusercontent.com
```

- [ ] **Step 3: Adicionar endpoints**

Em `endpoints.ts`, no bloco de auth:
```ts
  GOOGLE_LOGIN: '/auth/google/login',
  GOOGLE_REGISTRAR: '/auth/google/registrar',
```

- [ ] **Step 4: Adicionar tipos**

Em `auth.types.ts`:
```ts
export interface GoogleLoginRequest { idToken: string }
export interface GoogleRegistrarRequest {
  idToken: string
  nomeIgreja: string
  cnpj?: string
  telefoneContato: string
}
```

- [ ] **Step 5: Adicionar métodos no serviço**

Em `auth.service.ts` (seguindo o padrão dos métodos existentes que usam `api` e retornam `.data`):
```ts
  googleLogin: (idToken: string) =>
    api.post<LoginResponse>(ENDPOINTS.GOOGLE_LOGIN, { idToken }).then((r) => r.data),

  googleRegistrar: (payload: GoogleRegistrarRequest) =>
    api.post<RegistrarIgrejaResponse>(ENDPOINTS.GOOGLE_REGISTRAR, payload).then((r) => r.data),
```
(Ajustar imports de tipos.)

- [ ] **Step 6: Envolver o layout de auth com o provider**

Em `frontend/src/app/(auth)/layout.tsx` (se não existir, criar como client component que envolve `{children}`):
```tsx
'use client'
import { GoogleOAuthProvider } from '@react-oauth/google'

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <GoogleOAuthProvider clientId={process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID as string}>
      {children}
    </GoogleOAuthProvider>
  )
}
```

- [ ] **Step 7: Verificar build**

Run (em `frontend/`): `npm run build`
Esperado: compila sem erro de tipo. (O erro pré-existente do Sidebar `foto` é conhecido; se bloquear o build, anotar mas não corrigir aqui.)

- [ ] **Step 8: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/.env.example frontend/src/lib/endpoints.ts frontend/src/services/auth.service.ts frontend/src/types/auth.types.ts "frontend/src/app/(auth)/layout.tsx"
git commit -m "feat(auth): front setup Google OAuth (provider, serviço, tipos)"
```

---

### Task 10: Frontend — botão na tela de login + tratamento de erros

**Files:**
- Modify: `frontend/src/app/(auth)/login/page.tsx`
- Modify: `frontend/src/app/(auth)/login/page.module.css` (estilo do bloco Google e do aviso CONTA_SEM_SENHA)
- Modify: `frontend/src/hooks/auth/useLogin.ts` (tratar códigos e integrar googleLogin)

**Interfaces:**
- Consumes: `authService.googleLogin` (Task 9), store de auth (`setTokens`/persist), roteador.
- Produces: UI de "Entrar com Google" abaixo do divisor "OU"; tratamento de `CONTA_NAO_ENCONTRADA` e `CONTA_SEM_SENHA` (este último com botões "Entrar com Google" e "Definir senha" → `/forgot-password?email=`).

- [ ] **Step 1: Adicionar o botão Google e o handler no login**

Importar `useGoogleLogin` de `@react-oauth/google`. Como usamos ID token (não access token), usar o componente `<GoogleLogin>` (que devolve `credentialResponse.credential` = ID token). Abaixo do bloco `<div className={styles.divider}>`:

```tsx
import { GoogleLogin } from '@react-oauth/google'
// ...
<div className={styles.googleWrap}>
  <GoogleLogin
    onSuccess={(cred) => cred.credential && onGoogleLogin(cred.credential)}
    onError={() => setErroGeral('Não foi possível iniciar o login com Google.')}
    text="signin_with"
    locale="pt-BR"
    width="100%"
  />
</div>
```

- [ ] **Step 2: Implementar onGoogleLogin no useLogin**

No hook `useLogin`, adicionar uma função que chama `authService.googleLogin(idToken)`, e em sucesso faz o MESMO que o login nativo faz hoje (guarda tokens no store + redireciona). Em erro, ler `error.response.data.codigo`:
- `CONTA_NAO_ENCONTRADA` → setErroGeral com a mensagem completa (dono cadastra / membro fala com admin).

```ts
const onGoogleLogin = async (idToken: string) => {
  try {
    const data = await authService.googleLogin(idToken)
    aplicarSessao(data)          // mesma função usada pelo login nativo
    router.push('/inicio')
  } catch (e) {
    const codigo = (e as AxiosError<{ codigo?: string }>).response?.data?.codigo
    if (codigo === 'CONTA_NAO_ENCONTRADA') {
      setErroGeral('Não encontramos uma conta vinculada a este Google. Se você é responsável por uma igreja, cadastre-a primeiro. Se você é membro de uma igreja já cadastrada, peça ao administrador para conceder seu acesso.')
    } else {
      setErroGeral('Não foi possível entrar com o Google. Tente novamente.')
    }
  }
}
```

(Refatorar o sucesso do login nativo para uma função `aplicarSessao(data)` reutilizável, se ainda não existir.)

- [ ] **Step 3: Tratar CONTA_SEM_SENHA no login nativo (dois botões)**

No `onSubmit` nativo, ao pegar erro com `codigo === 'CONTA_SEM_SENHA'`, setar um estado `contaSemSenha = true` (guardando o email digitado). Renderizar, no lugar do `erroGeral` comum, um aviso com dois botões:

```tsx
{contaSemSenha ? (
  <div className={styles.avisoGoogle}>
    <p>Esta conta usa login com Google. Entre com Google ou defina uma senha.</p>
    <div className={styles.avisoAcoes}>
      {/* O próprio <GoogleLogin> acima já cobre "Entrar com Google" */}
      <Link href={`/forgot-password?email=${encodeURIComponent(emailDigitado)}`} className={styles.forgotLink}>
        Definir senha
      </Link>
    </div>
  </div>
) : (
  erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>
)}
```

- [ ] **Step 4: Pré-preencher email em /forgot-password**

Em `frontend/src/hooks/auth/useEsqueciSenha.ts` (ou na page), ler `useSearchParams().get('email')` e usar como `defaultValue` do campo e-mail. (A page já deve estar sob Suspense — se não, envolver.)

- [ ] **Step 5: Estilos**

Adicionar em `login/page.module.css`: `.googleWrap` (centralizado, `margin-top` coerente), `.avisoGoogle`/`.avisoAcoes` (usando os tokens de cor existentes, ex.: `--color-danger-bg`).

- [ ] **Step 6: Teste manual**

Subir back + front. Testar: (a) login com sua conta Google de teste que já tem usuário → entra; (b) login com Google de e-mail desconhecido → mensagem CONTA_NAO_ENCONTRADA; (c) login nativo numa conta só-Google → aviso com botões.

- [ ] **Step 7: Commit**

```bash
git add "frontend/src/app/(auth)/login/" frontend/src/hooks/auth/useLogin.ts frontend/src/hooks/auth/useEsqueciSenha.ts
git commit -m "feat(auth): botão Entrar com Google + tratamento CONTA_SEM_SENHA/NAO_ENCONTRADA"
```

---

### Task 11: Frontend — cadastro de igreja com Google

**Files:**
- Modify: `frontend/src/app/(auth)/cadastro/page.tsx`
- Modify: `frontend/src/app/(auth)/cadastro/page.module.css`
- Modify: `frontend/src/hooks/auth/` (hook de cadastro correspondente)

**Interfaces:**
- Consumes: `authService.googleRegistrar` (Task 9), store de auth, roteador.
- Produces: botão "Cadastrar minha igreja com Google" → formulário com nome/e-mail read-only (do token) + nome da igreja/CNPJ/telefone → cria e loga.

- [ ] **Step 1: Adicionar o botão Google no cadastro**

`<GoogleLogin>` (mesmo componente). No `onSuccess`, decodificar o ID token só para EXIBIR nome/e-mail (usar `jwtDecode` do pacote `jwt-decode` — instalar se necessário — apenas para preencher a UI; a verificação real é no back). Guardar o `idToken` em estado e revelar o formulário de igreja.

```tsx
import { GoogleLogin } from '@react-oauth/google'
import { jwtDecode } from 'jwt-decode'
// onSuccess:
const payload = jwtDecode<{ email: string; name: string }>(cred.credential!)
setGoogleData({ idToken: cred.credential!, email: payload.email, nome: payload.name })
```

- [ ] **Step 2: Renderizar o formulário de igreja pré-preenchido**

Quando `googleData` existir, mostrar: nome (read-only, `googleData.nome`), e-mail (read-only, `googleData.email`), e inputs de `nomeIgreja` (obrigatório), `cnpj`, `telefoneContato`. Botão "Concluir cadastro".

- [ ] **Step 3: Submeter para googleRegistrar**

```ts
const data = await authService.googleRegistrar({
  idToken: googleData.idToken,
  nomeIgreja, cnpj: cnpj || undefined, telefoneContato,
})
aplicarSessao(data)
router.push('/inicio')
```
Tratar `EMAIL_DUPLICADO` → "Este Google já tem conta. Faça login."

- [ ] **Step 4: Teste manual**

Cadastrar uma igreja nova com uma conta Google de teste sem conta → cria e entra. Repetir com o mesmo Google → EMAIL_DUPLICADO.

- [ ] **Step 5: Commit**

```bash
git add "frontend/src/app/(auth)/cadastro/" frontend/src/hooks/auth/ frontend/package.json frontend/package-lock.json
git commit -m "feat(auth): cadastro de igreja com Google (formulário pré-preenchido)"
```

---

### Task 12: Verificação final ponta a ponta + roadmap

**Files:**
- Modify: `CLAUDE.md` (marcar Google OAuth como feito na Fase 1)

- [ ] **Step 1: Rodar toda a suíte de testes do back**

Run: `./mvnw -q test`
Esperado: BUILD SUCCESS, todos os testes passam.

- [ ] **Step 2: Teste manual dos 4 fluxos-chave**

Com back + front no ar:
1. Cadastro de igreja com Google (conta nova) → entra.
2. Logout → login com Google (mesma conta) → entra (agora cenário 1).
3. Login nativo numa conta só-Google → aviso com "Entrar com Google" / "Definir senha".
4. "Definir senha" → recebe e-mail, define senha, faz login nativo → entra (conta virou híbrida).

- [ ] **Step 3: Marcar no roadmap**

Em `CLAUDE.md`, Fase 1, trocar `[ ] Login E cadastro com Google (OAuth) — pendente.` por `[x]` e ajustar o texto do item de autenticação híbrida para refletir Google feito.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(auth): marca Google OAuth como concluído na Fase 1"
```

---

## Notas de execução

- **Google Cloud (Testing):** só e-mails na lista de test users conseguem logar. Garanta que os e-mails de teste estão lá.
- **Segundo Google de teste:** para testar o cenário "cadastro" (e-mail desconhecido) você precisa de uma segunda conta Google (ou limpar a conta de teste do banco Neon antes).
- **Dados de teste no Neon:** o e-mail `josefilipe.dev@gmail.com` pode já estar ocupado por igrejas de teste — limpar antes de testar o cadastro com ele.
- **Erro pré-existente do Sidebar (`foto`):** não corrigir aqui; é da feature de upload (Fase 2).
