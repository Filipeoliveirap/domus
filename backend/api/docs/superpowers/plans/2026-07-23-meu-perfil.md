# Meu Perfil (+ avatar na tabela de usuários) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** criar a rota `/perfil` (já referenciada, hoje quebrada, no rodapé da Sidebar) onde o
usuário logado vê todos os dados da pessoa vinculada e troca foto/senha; `ADMIN_IGREJA` edita
tudo (exceto e-mail); `LIDER`/`ACESSO_COMUM` só trocam a foto. De quebra, a tabela de usuários
passa a mostrar foto real em vez de sempre iniciais.

**Architecture:** back expõe `GET/PUT /pessoas/me` (resolve `pessoa_id` do usuário autenticado,
nunca do corpo) e `PUT /auth/change-password` (valida senha atual, revoga as outras sessões);
front adiciona um componente `<Avatar>` compartilhado, um hook `useMinhaPessoa`, um hook
`useAlterarSenha` e a página `app/(app)/perfil/page.tsx`.

**Tech Stack:** Spring Boot (Java 21) + JPA/Hibernate + Redis (refresh tokens) no back; Next.js +
TypeScript + React Hook Form + Zod + TanStack Query no front.

## Global Constraints

- `igreja_id` sempre extraído do JWT (`UsuarioAutenticado.getIgrejaId()`), nunca do corpo.
- Services retornam DTOs, nunca entidades.
- Capacidade, não identidade: reusar `Permissoes.podeGerenciarPessoas(role)` (back) e
  `podeGerenciarPessoas(role)` (front, `lib/permissoes.ts`) — já existem, não criar duplicata.
- `pessoa.email` nunca editável por `/pessoas/me` (chave de login), para nenhum perfil.
- Esconder no front não é esconder — o back decide o que cada perfil pode gravar; o front só
  reflete (desabilita campo) o que o back já vai recusar.
- Commitar só depois do autor testar cada task (rodar os testes automatizados conta; testar no
  navegador é o autor que faz — sinalizar quando uma task precisa disso antes do commit final).

---

### Task 1: Backend — `fotoId` na listagem de usuários

**Files:**
- Modify: `src/main/java/com/domus/api/modules/usuario/DTO/UsuarioResponseDTO.java`
- Test: `src/test/java/com/domus/api/modules/usuario/DTO/UsuarioResponseDTOTest.java` (criar)

**Interfaces:**
- Produces: `UsuarioResponseDTO.fotoId(): UUID` (nullable) — consumido pelo front na Task 8.

- [ ] **Step 1: Escrever o teste que falha**

```java
package com.domus.api.modules.usuario.DTO;

import com.domus.api.modules.foto.Foto;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UsuarioResponseDTOTest {

    @Test
    void from_incluiFotoIdDaPessoaVinculada() {
        UUID fotoId = UUID.randomUUID();
        Foto foto = new Foto();
        foto.setId(fotoId);

        Pessoa pessoa = Pessoa.builder().nome("Ana").email("ana@ex.com").foto(foto).build();
        Role role = new Role();
        role.setNome("ACESSO_COMUM");
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).pessoa(pessoa).role(role).ativo(true).build();

        UsuarioResponseDTO dto = UsuarioResponseDTO.from(usuario);

        assertThat(dto.fotoId()).isEqualTo(fotoId);
    }

    @Test
    void from_semFoto_fotoIdNulo() {
        Pessoa pessoa = Pessoa.builder().nome("Ana").email("ana@ex.com").build();
        Role role = new Role();
        role.setNome("ACESSO_COMUM");
        Usuario usuario = Usuario.builder().id(UUID.randomUUID()).pessoa(pessoa).role(role).ativo(true).build();

        UsuarioResponseDTO dto = UsuarioResponseDTO.from(usuario);

        assertThat(dto.fotoId()).isNull();
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -Dtest=UsuarioResponseDTOTest test`
Expected: FAIL — `fotoId` não existe no record (erro de compilação do teste).

- [ ] **Step 3: Implementar**

```java
package com.domus.api.modules.usuario.DTO;


import com.domus.api.modules.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String role,
        boolean ativo,
        LocalDateTime ultimoLoginEm,
        boolean convitePendente,
        LocalDateTime criadoEm,
        UUID fotoId
) {
    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole().getNome(),
                u.isAtivo(),
                u.getUltimoLoginEm(),
                u.getUltimoLoginEm() == null,
                u.getCreatedAt(),
                u.getPessoa().getFoto() != null ? u.getPessoa().getFoto().getId() : null
        );
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -Dtest=UsuarioResponseDTOTest test`
Expected: PASS (2 testes verdes)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/usuario/DTO/UsuarioResponseDTO.java src/test/java/com/domus/api/modules/usuario/DTO/UsuarioResponseDTOTest.java
git commit -m "feat(usuario): inclui fotoId da pessoa na listagem de usuarios"
```

---

### Task 2: Backend — `UsuarioAutenticado.getPessoaId()`

**Files:**
- Modify: `src/main/java/com/domus/api/shared/security/UsuarioAutenticado.java`

**Interfaces:**
- Consumes: `Usuario.getPessoa(): Pessoa` (relação `@OneToOne(fetch = EAGER)`, sempre carregada —
  sem risco de `LazyInitializationException`, diferente de `igreja`/`foto` que são LAZY).
- Produces: `UsuarioAutenticado.getPessoaId(): UUID` — consumido pelo `PessoaController` (Task 4).

Sem teste isolado — é um getter de uma linha sobre uma relação EAGER já coberta pelos testes de
integração existentes de `UsuarioAutenticado` (usado por todo controller). Adiciona-se junto da
Task 4, que o exercita de ponta a ponta.

- [ ] **Step 1: Implementar**

```java
package com.domus.api.shared.security;

import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UsuarioAutenticado {

    public Usuario get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new BusinessException("Usuário não autenticado.");
        }
        return usuario;
    }

    public UUID getIgrejaId() {
        return get().getIgreja().getId();
    }

    public UUID getUsuarioId() {
        return get().getId();
    }

    public UUID getPessoaId() {
        return get().getPessoa().getId();
    }

    public String getRole() { return get().getRole().getNome();}
}
```

- [ ] **Step 2: Compilar**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/domus/api/shared/security/UsuarioAutenticado.java
git commit -m "feat(auth): UsuarioAutenticado.getPessoaId() para endpoints self-service"
```

---

### Task 3: Backend — `PessoaService.atualizarMinhaFoto`

Endpoint de "editar só a foto", usado por `ACESSO_COMUM`/`LIDER` no `/pessoas/me`. Reaproveita
`FotoService.buscarParaVincular` e a mesma ordem "vincula a nova antes de remover a antiga" que
`atualizarMembro` já usa.

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pessoa/PessoaService.java`
- Test: `src/test/java/com/domus/api/modules/pessoa/PessoaServiceTest.java`

**Interfaces:**
- Consumes: `FotoService.buscarParaVincular(UUID fotoId, UUID igrejaId): Foto`,
  `FotoService.remover(UUID fotoId): void` (já usados em `atualizarMembro`).
- Produces: `PessoaService.atualizarMinhaFoto(UUID id, UUID novoFotoId, UUID igrejaId): PessoaResponse`
  — consumido pelo `PessoaController` (Task 4).

- [ ] **Step 1: Escrever o teste que falha**

Adicionar ao final de `PessoaServiceTest.java` (mesma classe, já tem `pessoaRepository`,
`fotoService`, `igrejaId`, `pessoaId` no escopo — ver setup existente):

```java
    @org.junit.jupiter.api.Test
    void atualizarMinhaFoto_trocaSoAFoto_mantemRestoIntacto() {
        Foto fotoAntiga = new Foto();
        fotoAntiga.setId(UUID.randomUUID());
        Pessoa existente = Pessoa.builder()
                .id(pessoaId).nome("Ana").email("ana@ex.com")
                .vinculo(Vinculo.CONGREGANTE).foto(fotoAntiga)
                .build();
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(existente));

        Foto fotoNova = new Foto();
        fotoNova.setId(UUID.randomUUID());
        when(fotoService.buscarParaVincular(fotoNova.getId(), igrejaId)).thenReturn(fotoNova);

        PessoaResponse resposta = service.atualizarMinhaFoto(pessoaId, fotoNova.getId(), igrejaId);

        assertThat(resposta.fotoId()).isEqualTo(fotoNova.getId());
        assertThat(resposta.nome()).isEqualTo("Ana");
        verify(fotoService).remover(fotoAntiga.getId());
    }

    @org.junit.jupiter.api.Test
    void atualizarMinhaFoto_pessoaDeOutraIgreja_lancaNaoEncontrado() {
        when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizarMinhaFoto(pessoaId, UUID.randomUUID(), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }
```

(Import necessário no topo do arquivo de teste: `import com.domus.api.modules.pessoa.DTO.PessoaResponse;`
— confirmar se já não está importado antes de adicionar.)

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -Dtest=PessoaServiceTest test`
Expected: FAIL — `atualizarMinhaFoto` não existe em `PessoaService`.

- [ ] **Step 3: Implementar**

Adicionar em `PessoaService.java`, logo após `atualizarMembro`:

```java
    /**
     * Update "self" de foto — usado por quem só pode trocar a própria foto
     * (ACESSO_COMUM/LIDER em Meu Perfil). Mesma ordem de troca de `atualizarMembro`:
     * vincula a nova antes de remover a antiga (o ON DELETE RESTRICT recusaria o contrário).
     */
    @Transactional
    public PessoaResponse atualizarMinhaFoto(UUID id, UUID novoFotoId, UUID igrejaId) {
        Pessoa membro = membroRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));

        Foto fotoAntiga = membro.getFoto();
        Foto fotoNova = fotoService.buscarParaVincular(novoFotoId, igrejaId);
        membro.setFoto(fotoNova);

        Pessoa salvo = membroRepository.save(membro);

        boolean fotoMudou = !java.util.Objects.equals(
                fotoAntiga == null ? null : fotoAntiga.getId(),
                fotoNova == null ? null : fotoNova.getId());
        if (fotoMudou && fotoAntiga != null) {
            fotoService.remover(fotoAntiga.getId());
        }

        cacheEvictor.evictPorIgreja("pessoas", igrejaId);
        log.info("Foto de perfil atualizada (self-service). id={}, igreja_id={}", id, igrejaId);

        return PessoaResponse.from(salvo, null, true);
    }
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -Dtest=PessoaServiceTest test`
Expected: PASS (todos os testes da classe, incluindo os 2 novos)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pessoa/PessoaService.java src/test/java/com/domus/api/modules/pessoa/PessoaServiceTest.java
git commit -m "feat(pessoa): atualizarMinhaFoto para self-service de Meu Perfil"
```

---

### Task 4: Backend — `GET/PUT /pessoas/me`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pessoa/PessoaController.java`

**Interfaces:**
- Consumes: `UsuarioAutenticado.getPessoaId()` (Task 2), `PessoaService.buscarPorId(UUID, UUID, boolean)`
  (já existe), `PessoaService.atualizarMembro(UUID, PessoaRequestDTO, UUID)` (já existe),
  `PessoaService.atualizarMinhaFoto(UUID, UUID, UUID)` (Task 3),
  `Permissoes.podeGerenciarPessoas(String): boolean` (já existe).
- Produces: rotas `GET /pessoas/me`, `PUT /pessoas/me` — consumidas pelo front na Task 9.

Sem teste unitário novo de controller (o projeto não tem suíte de `@WebMvcTest` para
`PessoaController` hoje — os testes existentes são de `PessoaService`). A verificação desta task
é manual, via `curl`/navegador, na Task 12 (teste de ponta a ponta).

- [ ] **Step 1: Implementar**

Adicionar em `PessoaController.java`, logo antes do `@DeleteMapping("/{id}")`:

```java
    /**
     * "Meu Perfil": sempre a pessoa vinculada a quem está logado, nunca um id do corpo/query.
     * Dados sensíveis (endereço, observações) sempre inclusos aqui — são os PRÓPRIOS dados de
     * quem pergunta, a restrição de `podeVerDadosSensiveis()` é sobre olhar o dado de OUTRA
     * pessoa.
     */
    @GetMapping("/me")
    public ResponseEntity<PessoaResponse> buscarMe() {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID pessoaId = usuarioAutenticado.getPessoaId();
        return ResponseEntity.ok(pessoaService.buscarPorId(pessoaId, igrejaId, true));
    }

    /**
     * ADMIN_IGREJA edita qualquer campo do próprio cadastro (menos e-mail, que o front nem
     * envia — email é sempre o do JWT/sessão, ignorado aqui). LIDER/ACESSO_COMUM só trocam a
     * própria foto: a checagem de capacidade decide qual método do service roda, não um
     * whitelist de campos dentro de `atualizarMembro` (mais simples de auditar).
     */
    @PutMapping("/me")
    public ResponseEntity<PessoaResponse> atualizarMe(@Valid @RequestBody PessoaRequestDTO data) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        UUID pessoaId = usuarioAutenticado.getPessoaId();

        PessoaResponse resposta = Permissoes.podeGerenciarPessoas(usuarioAutenticado.getRole())
                ? pessoaService.atualizarMembro(pessoaId, data, igrejaId)
                : pessoaService.atualizarMinhaFoto(pessoaId, data.fotoId(), igrejaId);

        return ResponseEntity.ok(resposta);
    }
```

- [ ] **Step 2: Compilar**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Testar manualmente (curl, sessão logada)**

```bash
curl -b cookies.txt http://localhost:8080/pessoas/me
curl -b cookies.txt -X PUT http://localhost:8080/pessoas/me -H "Content-Type: application/json" \
  -d '{"nome":"Teste","vinculo":"CONGREGANTE","fotoId":null}'
```
Expected: 200 nos dois; o segundo reflete a alteração (ou só a foto, se logado como
ACESSO_COMUM/LIDER — testar com os dois perfis).

- [ ] **Step 4: Commit** (só depois de o autor confirmar o teste manual acima)

```bash
git add src/main/java/com/domus/api/modules/pessoa/PessoaController.java
git commit -m "feat(pessoa): GET/PUT /pessoas/me para a tela Meu Perfil"
```

---

### Task 5: Backend — `RefreshTokenService.revogarTodasSessoesExceto`

**Files:**
- Modify: `src/main/java/com/domus/api/shared/security/RefreshTokenService.java`
- Test: `src/test/java/com/domus/api/shared/security/RefreshTokenServiceTest.java` (criar, ou
  adicionar à suíte existente se já houver uma para esta classe — verificar antes com
  `find . -iname RefreshTokenServiceTest.java`)

**Interfaces:**
- Produces: `RefreshTokenService.revogarTodasSessoesExceto(UUID usuarioId, String tokenAtual): void`
  — consumido por `AuthService.alterarSenha` (Task 6).

- [ ] **Step 1: Verificar se já existe suíte de teste para a classe**

Run: `find . -iname "RefreshTokenServiceTest.java"`

Se existir, adicionar os testes abaixo nela (reaproveitando o `@BeforeEach` já existente, que
provavelmente já mocka `StringRedisTemplate`). Se não existir, criar do zero como no Step 1b.

- [ ] **Step 1b (se não existir suíte): escrever o teste que falha**

```java
package com.domus.api.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    StringRedisTemplate redisTemplate;
    ValueOperations<String, String> valueOps;
    SetOperations<String, String> setOps;
    RefreshTokenService service;

    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new RefreshTokenService(redisTemplate, 604_800_000L); // 7 dias
    }

    @Test
    void revogarTodasSessoesExceto_mantemAFamiliaDoTokenAtual() {
        String tokenAtual = "token-atual";
        String familiaAtual = "familia-atual";
        String familiaOutra = "familia-outra";

        when(valueOps.get("refresh:" + tokenAtual)).thenReturn(usuarioId + "|" + familiaAtual);
        when(setOps.members("usuariofamilias:" + usuarioId))
                .thenReturn(Set.of(familiaAtual, familiaOutra));

        service.revogarTodasSessoesExceto(usuarioId, tokenAtual);

        verify(redisTemplate, never()).delete("refreshfam:" + familiaAtual);
        verify(redisTemplate).delete("refreshfam:" + familiaOutra);
    }

    @Test
    void revogarTodasSessoesExceto_tokenAtualNulo_revogaTodasMesmoAssim() {
        when(setOps.members("usuariofamilias:" + usuarioId)).thenReturn(Set.of("familia-x"));

        service.revogarTodasSessoesExceto(usuarioId, null);

        verify(redisTemplate).delete("refreshfam:familia-x");
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -Dtest=RefreshTokenServiceTest test`
Expected: FAIL — `revogarTodasSessoesExceto` não existe.

- [ ] **Step 3: Implementar**

Adicionar em `RefreshTokenService.java`, logo após `revogarTodasSessoes`:

```java
    /**
     * Revoga todas as sessões do usuário MENOS a família do token informado — usado na troca
     * de senha "sabendo a senha atual" (Meu Perfil), onde derrubar a sessão de quem acabou de
     * confirmar a própria identidade seria pior UX sem ganho de segurança. `tokenAtual == null`
     * (ex.: cookie ausente) revoga tudo, igual a `revogarTodasSessoes`.
     */
    public void revogarTodasSessoesExceto(UUID usuarioId, String tokenAtual) {
        String familiaAtual = familiaDoToken(tokenAtual);
        String chaveIndice = chaveUsuarioFamilias(usuarioId);
        var familias = redisTemplate.opsForSet().members(chaveIndice);
        if (familias != null) {
            for (String familyId : familias) {
                if (!familyId.equals(familiaAtual)) {
                    revogarFamilia(familyId);
                }
            }
        }
        log.info("Sessões revogadas, exceto a atual. usuario_id={}", usuarioId);
    }

    private String familiaDoToken(String token) {
        if (token == null || token.isBlank()) return null;
        String valor = redisTemplate.opsForValue().get(chaveToken(token));
        if (valor == null) return null;
        return valor.split("\\" + SEPARADOR)[1];
    }
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -Dtest=RefreshTokenServiceTest test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/shared/security/RefreshTokenService.java src/test/java/com/domus/api/shared/security/RefreshTokenServiceTest.java
git commit -m "feat(auth): revogarTodasSessoesExceto para troca de senha sem derrubar a sessao atual"
```

---

### Task 6: Backend — `AuthService.alterarSenha` + `ChangePasswordDTO`

**Files:**
- Create: `src/main/java/com/domus/api/modules/auth/DTO/ChangePasswordDTO.java`
- Modify: `src/main/java/com/domus/api/modules/auth/AuthService.java`
- Test: `src/test/java/com/domus/api/modules/auth/AuthServiceTest.java` (criar, ou adicionar à
  suíte se já existir — checar com `find . -iname AuthServiceTest.java`)

**Interfaces:**
- Consumes: `RefreshTokenService.revogarTodasSessoesExceto(UUID, String)` (Task 5),
  `PasswordEncoder.matches/encode` (já injetado em `AuthService`).
- Produces: `AuthService.alterarSenha(UUID usuarioId, String refreshTokenAtual, ChangePasswordDTO data): void`
  — consumido pelo `AuthenticationController` (Task 7).

- [ ] **Step 1: Criar o DTO**

```java
package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDTO(
        @NotBlank(message = "Senha atual é obrigatória")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, message = "A nova senha deve ter pelo menos 8 caracteres")
        String novaSenha
) {}
```

- [ ] **Step 2: Escrever o teste que falha**

```java
package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.ChangePasswordDTO;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    AuthenticationManager authenticationManager;
    com.domus.api.config.TokenService tokenService;
    RefreshTokenService refreshTokenService;
    LoginAttemptService loginAttemptService;
    UsuarioRepository usuarioRepository;
    PasswordEncoder passwordEncoder;
    AuthService service;

    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        authenticationManager = mock(AuthenticationManager.class);
        tokenService = mock(com.domus.api.config.TokenService.class);
        refreshTokenService = mock(RefreshTokenService.class);
        loginAttemptService = mock(LoginAttemptService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthService(authenticationManager, tokenService, refreshTokenService,
                loginAttemptService, usuarioRepository, passwordEncoder);
    }

    private Usuario usuarioComSenha(String hash) {
        Usuario u = new Usuario();
        u.setId(usuarioId);
        u.setSenhaHash(hash);
        return u;
    }

    @Test
    void alterarSenha_senhaAtualErrada_lancaErro() {
        Usuario usuario = usuarioComSenha("hash-antigo");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("errada", "hash-antigo")).thenReturn(false);

        var data = new ChangePasswordDTO("errada", "novaSenha123");

        assertThatThrownBy(() -> service.alterarSenha(usuarioId, "token-x", data))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("atual");
    }

    @Test
    void alterarSenha_contaSoGoogle_lancaContaSemSenha() {
        Usuario usuario = usuarioComSenha(null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        var data = new ChangePasswordDTO("qualquer", "novaSenha123");

        assertThatThrownBy(() -> service.alterarSenha(usuarioId, "token-x", data))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(
                        ((BusinessException) e).getCodigo()).isEqualTo("CONTA_SEM_SENHA"));
    }

    @Test
    void alterarSenha_sucesso_atualizaHashERevogaOutrasSessoes() {
        Usuario usuario = usuarioComSenha("hash-antigo");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("correta", "hash-antigo")).thenReturn(true);
        when(passwordEncoder.encode("novaSenha123")).thenReturn("hash-novo");
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var data = new ChangePasswordDTO("correta", "novaSenha123");
        service.alterarSenha(usuarioId, "token-atual", data);

        verify(usuarioRepository).save(argThat(u -> "hash-novo".equals(u.getSenhaHash())));
        verify(refreshTokenService).revogarTodasSessoesExceto(usuarioId, "token-atual");
    }
}
```

- [ ] **Step 3: Rodar e confirmar que falha**

Run: `mvn -q -Dtest=AuthServiceTest test`
Expected: FAIL — `alterarSenha` não existe em `AuthService`.

- [ ] **Step 4: Implementar**

Adicionar em `AuthService.java`, logo após `sessaoDe`:

```java
    /**
     * Troca a própria senha (Meu Perfil), sabendo a atual — padrão de mercado, diferente do
     * reset por token (que não exige senha atual porque é "esqueci a senha"). Revoga as OUTRAS
     * sessões, mantendo a atual: quem acabou de provar a senha não devia ser derrubado.
     */
    public void alterarSenha(UUID usuarioId, String refreshTokenAtual, ChangePasswordDTO data) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new SessaoExpiradaException("SESSAO_INVALIDA",
                        "Sessão expirada. Faça login novamente."));

        if (usuario.getSenhaHash() == null) {
            log.warn("Troca de senha em conta só-Google. usuario_id={}", usuarioId);
            throw new BusinessException("CONTA_SEM_SENHA",
                    "Esta conta usa login com Google e não tem senha para trocar.");
        }

        if (!passwordEncoder.matches(data.senhaAtual(), usuario.getSenhaHash())) {
            log.warn("Troca de senha com senha atual incorreta. usuario_id={}", usuarioId);
            throw new BusinessException("SENHA_ATUAL_INCORRETA", "A senha atual informada está incorreta.");
        }

        usuario.setSenhaHash(passwordEncoder.encode(data.novaSenha()));
        usuarioRepository.save(usuario);

        refreshTokenService.revogarTodasSessoesExceto(usuarioId, refreshTokenAtual);
        log.info("Senha alterada pelo próprio usuário. usuario_id={}", usuarioId);
    }
```

Adicionar o import no topo do arquivo: `import com.domus.api.modules.auth.DTO.ChangePasswordDTO;`

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `mvn -q -Dtest=AuthServiceTest test`
Expected: PASS (3 testes verdes)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/DTO/ChangePasswordDTO.java src/main/java/com/domus/api/modules/auth/AuthService.java src/test/java/com/domus/api/modules/auth/AuthServiceTest.java
git commit -m "feat(auth): AuthService.alterarSenha com validacao de senha atual"
```

---

### Task 7: Backend — `PUT /auth/change-password`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/auth/AuthenticationController.java`

**Interfaces:**
- Consumes: `AuthService.alterarSenha(UUID, String, ChangePasswordDTO)` (Task 6),
  `AuthCookieFactory.COOKIE_REFRESH` (constante já usada em `/refresh` e `/logout`).
- Produces: rota `PUT /auth/change-password` — consumida pelo front na Task 10.

- [ ] **Step 1: Implementar**

Adicionar em `AuthenticationController.java`, logo após o método `me`:

```java
    @org.springframework.web.bind.annotation.PutMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal Usuario usuario,
            @CookieValue(name = AuthCookieFactory.COOKIE_REFRESH, required = false) String refreshToken,
            @RequestBody @Valid com.domus.api.modules.auth.DTO.ChangePasswordDTO data) {
        authService.alterarSenha(usuario.getId(), refreshToken, data);
        return ResponseEntity.ok(Map.of("message", "Senha alterada com sucesso."));
    }
```

- [ ] **Step 2: Compilar**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Testar manualmente**

```bash
curl -b cookies.txt -X PUT http://localhost:8080/auth/change-password -H "Content-Type: application/json" \
  -d '{"senhaAtual":"senha-errada","novaSenha":"novaSenha123"}'
# esperado: 400 SENHA_ATUAL_INCORRETA
curl -b cookies.txt -X PUT http://localhost:8080/auth/change-password -H "Content-Type: application/json" \
  -d '{"senhaAtual":"<senha real da conta de teste>","novaSenha":"novaSenha123"}'
# esperado: 200, e a sessão atual (cookies.txt) continua válida num GET /auth/me em seguida
```

- [ ] **Step 4: Commit** (só depois de o autor confirmar o teste manual)

```bash
git add src/main/java/com/domus/api/modules/auth/AuthenticationController.java
git commit -m "feat(auth): endpoint PUT /auth/change-password"
```

---

### Task 8: Frontend — componente `<Avatar>` compartilhado

**Files:**
- Create: `frontend/src/components/common/Avatar/Avatar.tsx`
- Create: `frontend/src/components/common/Avatar/Avatar.module.css`

**Interfaces:**
- Consumes: `urlFoto(id, tamanho): string | null` (`lib/urlFoto.ts`, já existe),
  `iniciais(nome): string` (`lib/formats/pessoaFormat.ts`, já existe).
- Produces: `<Avatar fotoId nome tamanho />` — consumido pela Task 9 (tabela de usuários) e
  Task 13 (Meu Perfil).

- [ ] **Step 1: Implementar o componente**

```tsx
import styles from './Avatar.module.css'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'

interface AvatarProps {
  fotoId: string | null | undefined
  nome: string
  tamanho?: 'sm' | 'md' | 'lg'
}

export function Avatar({ fotoId, nome, tamanho = 'md' }: AvatarProps) {
  const url = urlFoto(fotoId, tamanho === 'lg' ? 'DISPLAY' : 'THUMB')

  return url ? (
    <img src={url} alt={nome} className={`${styles.avatar} ${styles[tamanho]}`} />
  ) : (
    <span className={`${styles.avatar} ${styles.iniciais} ${styles[tamanho]}`}>
      {iniciais(nome)}
    </span>
  )
}
```

- [ ] **Step 2: CSS (segue o padrão de tamanho já usado em `usuarios/page.tsx` e Sidebar)**

```css
.avatar {
  border-radius: 50%;
  object-fit: cover;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.iniciais {
  background: var(--cor-primaria-fraca, #e0e7ff);
  color: var(--cor-primaria, #4338ca);
  font-weight: 600;
}

.sm { width: 32px; height: 32px; font-size: 0.75rem; }
.md { width: 48px; height: 48px; font-size: 0.9rem; }
.lg { width: 96px; height: 96px; font-size: 1.5rem; }
```

Se o projeto já tiver variáveis de cor diferentes destas (`--cor-primaria*`), ajustar para as
variáveis reais — checar `frontend/src/app/globals.css` ou o CSS module de `usuarios/page.tsx`
antes de finalizar.

- [ ] **Step 3: Verificar visualmente**

Run: `npm run dev` (dentro de `frontend/`), abrir qualquer tela que será tocada na Task 9 depois
de integrada — este componente isolado não tem tela própria ainda, então a verificação visual
real acontece ao final da Task 9.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/common/Avatar/
git commit -m "feat(common): componente Avatar compartilhado (foto ou iniciais)"
```

---

### Task 9: Frontend — avatar na tabela de usuários

**Files:**
- Modify: `frontend/src/app/(app)/usuarios/page.tsx`
- Modify: `frontend/src/types/usuario.types.ts` (adicionar `fotoId` ao tipo `UsuarioResponse`)

**Interfaces:**
- Consumes: `<Avatar fotoId nome tamanho="sm" />` (Task 8), `UsuarioResponseDTO.fotoId` (Task 1,
  já chega serializado no JSON do `GET /usuarios`).

- [ ] **Step 1: Adicionar `fotoId` ao tipo**

Abrir `frontend/src/types/usuario.types.ts`, localizar a interface/tipo `UsuarioResponse` e
adicionar o campo:

```ts
fotoId: string | null
```

- [ ] **Step 2: Trocar o `<span>` de iniciais pelo `<Avatar>`**

Em `usuarios/page.tsx`, localizar o `<span className={styles.avatar}>{iniciais(u.nome)}</span>`
(célula da tabela) e substituir por:

```tsx
<Avatar fotoId={u.fotoId} nome={u.nome} tamanho="sm" />
```

Adicionar o import no topo: `import { Avatar } from '@/components/common/Avatar/Avatar'`.
Remover o import de `iniciais` deste arquivo se ele deixar de ser usado em qualquer outro lugar
da página (checar com `grep -n "iniciais" frontend/src/app/\(app\)/usuarios/page.tsx`).

- [ ] **Step 3: Testar no navegador**

Run: `npm run dev`, abrir `/usuarios` logado como `ADMIN_IGREJA`. Um usuário cuja pessoa tenha
foto deve mostrar a foto; sem foto, continua mostrando iniciais. Testar também em viewport
mobile (a tabela vira cards — conferir que o avatar não estoura o card).

- [ ] **Step 4: Commit** (só depois do autor confirmar visualmente)

```bash
git add frontend/src/app/\(app\)/usuarios/page.tsx frontend/src/types/usuario.types.ts
git commit -m "feat(usuarios): mostra foto real (Avatar) na tabela em vez de so iniciais"
```

---

### Task 10: Frontend — services e endpoints (`/pessoas/me`, `/auth/change-password`)

**Files:**
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/services/pessoa.service.ts`
- Modify: `frontend/src/services/auth.service.ts`
- Modify: `frontend/src/lib/validators.ts` (novo schema `alterarSenhaSchema`)

**Interfaces:**
- Produces: `pessoasService.buscarMe(): Promise<PessoaResponse>`,
  `pessoasService.atualizarMe(data: PessoaRequest): Promise<PessoaResponse>`,
  `authService.alterarSenha(data: AlterarSenhaRequest): Promise<{message: string}>`,
  `alterarSenhaSchema` (Zod) — todos consumidos pelas Tasks 11 e 12.

- [ ] **Step 1: Endpoints**

Em `frontend/src/lib/endpoints.ts`, dentro de `pessoas: { ... }` adicionar:

```ts
ME: '/pessoas/me',
```

Dentro de `auth: { ... }` adicionar:

```ts
CHANGE_PASSWORD: '/auth/change-password',
```

- [ ] **Step 2: `pessoa.service.ts`**

Adicionar ao objeto `pessoasService` (após `buscar`):

```ts
  buscarMe: (): Promise<PessoaResponse> =>
    api.get<PessoaResponse>(Endpoints.pessoas.ME).then(res => res.data),

  atualizarMe: (data: PessoaRequest): Promise<PessoaResponse> =>
    api.put<PessoaResponse>(Endpoints.pessoas.ME, data).then(res => res.data),
```

- [ ] **Step 3: `auth.service.ts`**

Abrir o arquivo (padrão idêntico ao de `pessoa.service.ts`: `api.<verbo>(...).then(res => res.data)`)
e adicionar:

```ts
  alterarSenha: (data: AlterarSenhaRequest): Promise<{ message: string }> =>
    api.put<{ message: string }>(Endpoints.auth.CHANGE_PASSWORD, data).then(res => res.data),
```

Em `frontend/src/types/auth.types.ts`, adicionar o tipo:

```ts
export interface AlterarSenhaRequest {
  senhaAtual: string
  novaSenha: string
}
```

E importar `AlterarSenhaRequest` no `auth.service.ts`.

- [ ] **Step 4: Schema Zod de troca de senha**

Em `frontend/src/lib/validators.ts`, adicionar:

```ts
export const alterarSenhaSchema = z.object({
  senhaAtual: z.string().min(1, 'Senha atual é obrigatória'),
  novaSenha: z.string().min(8, 'Mínimo 8 caracteres'),
  confirmarNovaSenha: z.string().min(1, 'Confirme a nova senha'),
}).refine(data => data.novaSenha === data.confirmarNovaSenha, {
  message: 'As senhas não coincidem',
  path: ['confirmarNovaSenha'],
})

export type AlterarSenhaFormData = z.infer<typeof alterarSenhaSchema>
```

- [ ] **Step 5: Compilar (typecheck)**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos relacionados a estes arquivos.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/endpoints.ts frontend/src/services/pessoa.service.ts frontend/src/services/auth.service.ts frontend/src/lib/validators.ts frontend/src/types/auth.types.ts
git commit -m "feat(perfil): services/endpoints/schema para pessoas/me e auth/change-password"
```

---

### Task 11: Frontend — hooks `useMinhaPessoa` e `useAlterarSenha`

**Files:**
- Create: `frontend/src/hooks/pessoa/useMinhaPessoa.ts`
- Create: `frontend/src/hooks/auth/useAlterarSenha.ts`

**Interfaces:**
- Consumes: `pessoasService.buscarMe/atualizarMe` (Task 10), `authService.alterarSenha` (Task 10),
  `useAuthStore.atualizarUsuarioLogado` (já existe em `authStore.ts`), `notificar` (já existe).
- Produces: `useMinhaPessoa(): UseQueryResult<PessoaResponse>`,
  `useAtualizarMinhaPessoa(): UseMutationResult<...>`, `useAlterarSenha(): UseMutationResult<...>`
  — consumidos pela página `/perfil` (Task 13).

- [ ] **Step 1: `useMinhaPessoa` (query + mutation), seguindo o padrão de `usePessoa.ts`**

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { pessoasService } from '@/services/pessoa.service'
import { useAuthStore } from '@/store/authStore'
import type { PessoaRequest } from '@/types/pessoa.type'

export function useMinhaPessoa() {
  return useQuery({
    queryKey: ['pessoa', 'me'],
    queryFn: () => pessoasService.buscarMe(),
  })
}

export function useAtualizarMinhaPessoa() {
  const queryClient = useQueryClient()
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)

  return useMutation({
    mutationFn: (data: PessoaRequest) => pessoasService.atualizarMe(data),
    onSuccess: (resposta) => {
      queryClient.setQueryData(['pessoa', 'me'], resposta)
      // Sidebar e authStore usam nome/fotoId da sessão — sem isto, a troca de foto
      // só apareceria lá depois de um F5.
      atualizarUsuarioLogado({ nome: resposta.nome, fotoId: resposta.fotoId })
    },
  })
}
```

- [ ] **Step 2: `useAlterarSenha`**

```ts
import { useMutation } from '@tanstack/react-query'
import { authService } from '@/services/auth.service'
import type { AlterarSenhaRequest } from '@/types/auth.types'

export function useAlterarSenha() {
  return useMutation({
    mutationFn: (data: AlterarSenhaRequest) => authService.alterarSenha(data),
  })
}
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/pessoa/useMinhaPessoa.ts frontend/src/hooks/auth/useAlterarSenha.ts
git commit -m "feat(perfil): hooks useMinhaPessoa e useAlterarSenha"
```

---

### Task 12: Frontend — bloco "Alterar senha" (componente isolado)

Isolado numa task própria porque tem sua própria lógica de erro (`CONTA_SEM_SENHA`,
`SENHA_ATUAL_INCORRETA`) e pode ser testado sem depender da página inteira estar pronta.

**Files:**
- Create: `frontend/src/components/module/perfil/AlterarSenhaForm.tsx`
- Create: `frontend/src/components/module/perfil/AlterarSenhaForm.module.css`

**Interfaces:**
- Consumes: `useAlterarSenha()` (Task 11), `alterarSenhaSchema`/`AlterarSenhaFormData` (Task 10),
  `notificar` (`components/common/Notificacao/notificar`), `Input`/`Button` (`components/common`).
- Produces: `<AlterarSenhaForm />` — consumido pela página `/perfil` (Task 13).

- [ ] **Step 1: Implementar**

```tsx
'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import axios from 'axios'
import { Lock } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAlterarSenha } from '@/hooks/auth/useAlterarSenha'
import { alterarSenhaSchema, type AlterarSenhaFormData } from '@/lib/validators'
import type { ApiError } from '@/types/api.types'
import styles from './AlterarSenhaForm.module.css'

export function AlterarSenhaForm() {
  const { register, handleSubmit, reset, setError, formState: { errors } } =
    useForm<AlterarSenhaFormData>({ resolver: zodResolver(alterarSenhaSchema) })
  const { mutate, isPending } = useAlterarSenha()

  const onSubmit = (data: AlterarSenhaFormData) => {
    mutate(
      { senhaAtual: data.senhaAtual, novaSenha: data.novaSenha },
      {
        onSuccess: () => {
          notificar.sucesso('Senha alterada com sucesso.')
          reset()
        },
        onError: (error) => {
          if (axios.isAxiosError<ApiError>(error)) {
            const e = error.response?.data
            if (e?.error === 'SENHA_ATUAL_INCORRETA') {
              setError('senhaAtual', { type: 'server', message: e.message })
              return
            }
            if (e?.error === 'CONTA_SEM_SENHA') {
              notificar.erro(e.message)
              return
            }
            notificar.erro(e?.message ?? 'Erro ao alterar senha. Tente novamente.')
          } else {
            notificar.erro('Erro ao alterar senha. Tente novamente.')
          }
        },
      },
    )
  }

  return (
    <section className={styles.secao}>
      <div className={styles.header}>
        <Lock size={18} />
        <div>
          <h2 className={styles.titulo}>Alterar senha</h2>
          <p className={styles.subtitulo}>Proteja sua conta com uma senha forte.</p>
        </div>
      </div>
      <form className={styles.form} onSubmit={handleSubmit(onSubmit)}>
        <Input id="senhaAtual" type="password" label="SENHA ATUAL*"
          error={errors.senhaAtual?.message} {...register('senhaAtual')} />
        <div className={styles.grid2}>
          <Input id="novaSenha" type="password" label="NOVA SENHA*" placeholder="Mínimo 8 caracteres"
            error={errors.novaSenha?.message} {...register('novaSenha')} />
          <Input id="confirmarNovaSenha" type="password" label="CONFIRMAR NOVA SENHA*"
            error={errors.confirmarNovaSenha?.message} {...register('confirmarNovaSenha')} />
        </div>
        <Button type="submit" variant="primary" isLoading={isPending} disabled={isPending}>
          Alterar senha
        </Button>
      </form>
    </section>
  )
}
```

Conferir a assinatura real de `Input`/`Button` (props `error`, `isLoading`, `label`) contra
`PessoaForm.tsx`, que já os usa da mesma forma — ajustar se algum prop tiver nome diferente.

- [ ] **Step 2: CSS mínimo, seguindo o padrão de seção do `PessoaForm.module.css`**

```css
.secao {
  background: var(--cor-fundo-card, #fff);
  border-radius: 12px;
  padding: 24px;
  border: 1px solid var(--cor-borda, #e5e7eb);
}
.header { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 16px; }
.titulo { font-size: 1rem; font-weight: 600; margin: 0; }
.subtitulo { font-size: 0.85rem; color: var(--cor-texto-secundario, #6b7280); margin: 4px 0 0; }
.form { display: flex; flex-direction: column; gap: 16px; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 640px) {
  .grid2 { grid-template-columns: 1fr; }
}
```

Ajustar as variáveis de cor para as reais do projeto (checar `PessoaForm.module.css`).

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/module/perfil/AlterarSenhaForm.tsx frontend/src/components/module/perfil/AlterarSenhaForm.module.css
git commit -m "feat(perfil): formulario de alterar senha (padrao mercado, com senha atual)"
```

---

### Task 13: Frontend — página `/perfil`

Última task: monta a página usando tudo das tasks anteriores.

**Files:**
- Create: `frontend/src/app/(app)/perfil/page.tsx`
- Create: `frontend/src/app/(app)/perfil/page.module.css`

**Interfaces:**
- Consumes: `useMinhaPessoa`/`useAtualizarMinhaPessoa` (Task 11), `<AlterarSenhaForm />`
  (Task 12), `<UploadFoto>` (já existe), `<Avatar>` (Task 8, para o preview antes de editar, se
  optar por mostrar), `podeGerenciarPessoas(role)` (`lib/permissoes.ts`, já existe),
  `useAuthStore` (`role`, já existe), `pessoaSchema`/`PessoaFormData` (já existe em
  `validators.ts` — reaproveitado, sem criar um novo schema).

- [ ] **Step 1: Hook de formulário da página**

Criar `frontend/src/hooks/pessoa/usePerfilForm.ts`, adaptando `usePessoaForm.ts` (mesmo padrão
de `useAppForm` + `zodResolver(pessoaSchema)` + `reset()` em `useEffect`), mas usando
`useMinhaPessoa`/`useAtualizarMinhaPessoa` em vez de `usePessoa`/`pessoasService.atualizar`:

```ts
import { useEffect, useState } from 'react'
import axios from 'axios'
import { zodResolver } from '@hookform/resolvers/zod'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAppForm } from '../forms/useAppForm'
import { pessoaSchema, type PessoaFormInput, type PessoaFormData } from '@/lib/validators'
import { useAtualizarMinhaPessoa } from './useMinhaPessoa'
import { formatarTelefone, formatarCep } from '@/lib/masks'
import type { PessoaRequest, PessoaResponse } from '@/types/pessoa.type'
import type { ApiError } from '@/types/api.types'

export function usePerfilForm(pessoaInicial: PessoaResponse | undefined) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const { mutateAsync, isPending } = useAtualizarMinhaPessoa()

  const form = useAppForm<PessoaFormInput, PessoaFormData>({
    resolver: zodResolver(pessoaSchema),
    defaultValues: {
      nome: '', email: '', telefone: '', dataNascimento: '',
      endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
      vinculo: 'CONGREGANTE', estadoCivil: '', sexo: '',
      ministerio: '', observacoes: '', dataBatismo: '', fotoId: null,
    },
    requiredFields: ['nome'],
  })

  const { reset } = form

  useEffect(() => {
    if (pessoaInicial) {
      reset({
        nome: pessoaInicial.nome,
        email: pessoaInicial.email ?? '',
        telefone: pessoaInicial.telefone ? formatarTelefone(pessoaInicial.telefone) : '',
        dataNascimento: pessoaInicial.dataNascimento ?? '',
        endereco: {
          cep: pessoaInicial.endereco?.cep ? formatarCep(pessoaInicial.endereco.cep) : '',
          logradouro: pessoaInicial.endereco?.logradouro ?? '',
          numero: pessoaInicial.endereco?.numero ?? '',
          complemento: pessoaInicial.endereco?.complemento ?? '',
          bairro: pessoaInicial.endereco?.bairro ?? '',
          cidade: pessoaInicial.endereco?.cidade ?? '',
          uf: pessoaInicial.endereco?.uf ?? '',
        },
        vinculo: pessoaInicial.vinculo,
        estadoCivil: pessoaInicial.estadoCivil ?? '',
        sexo: pessoaInicial.sexo ?? '',
        ministerio: pessoaInicial.ministerio ?? '',
        observacoes: pessoaInicial.observacoes ?? '',
        dataBatismo: pessoaInicial.dataBatismo ?? '',
        fotoId: pessoaInicial.fotoId ?? null,
      })
    }
  }, [pessoaInicial, reset])

  const onSubmit = async (data: PessoaFormData) => {
    setErroGeral(null)
    try {
      const payload: PessoaRequest = {
        ...data,
        telefone: data.telefone?.replace(/\D/g, '') || undefined,
        estadoCivil: data.estadoCivil || undefined,
        sexo: data.sexo || undefined,
        endereco: { ...data.endereco, cep: data.endereco?.cep?.replace(/\D/g, '') || undefined },
        dataBatismo: data.vinculo === 'MEMBRO' ? (data.dataBatismo || undefined) : undefined,
      }
      await mutateAsync(payload)
      notificar.sucesso('Perfil atualizado!')
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar. Tente novamente.')
      }
    }
  }

  return { ...form, onSubmit, erroGeral, isLoading: isPending }
}
```

- [ ] **Step 2: A página**

```tsx
'use client'

import { useAuthStore } from '@/store/authStore'
import { useMinhaPessoa } from '@/hooks/pessoa/useMinhaPessoa'
import { usePerfilForm } from '@/hooks/pessoa/usePerfilForm'
import { podeGerenciarPessoas } from '@/lib/permissoes'
import { AlterarSenhaForm } from '@/components/module/perfil/AlterarSenhaForm'
import { UploadFoto } from '@/components/common/UploadFoto/UploadFoto'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
import styles from './page.module.css'

export default function PerfilPage() {
  const role = useAuthStore((s) => s.role)
  const podeEditarTudo = podeGerenciarPessoas(role)

  const { data: pessoa, isLoading: carregando } = useMinhaPessoa()
  const {
    register, handleSubmit, setValue, watch,
    formState: { errors },
    erroGeral, isLoading, onSubmit,
  } = usePerfilForm(pessoa)

  const fotoIdAtual = watch('fotoId') as string | null | undefined
  const nomeAtual = (watch('nome') as string | undefined) ?? ''

  if (carregando) return <div className={styles.pagina}>Carregando…</div>

  return (
    <div className={styles.pagina}>
      <h1 className={styles.titulo}>Meu perfil</h1>
      <p className={styles.subtitulo}>Gerencie suas informações pessoais e segurança da conta.</p>

      <form className={styles.card} onSubmit={handleSubmit(onSubmit)}>
        <div className={styles.fotoWrap}>
          <UploadFoto
            valor={fotoIdAtual}
            onChange={(id) => setValue('fotoId', id, { shouldDirty: true })}
            formato="circulo"
            nomeFallback={nomeAtual}
          />
        </div>

        {!podeEditarTudo && (
          <div className={styles.aviso}>
            Seus dados só podem ser alterados pela secretaria da igreja, caso estejam
            incorretos ou desatualizados.
          </div>
        )}

        <div className={styles.grid2}>
          <Input id="nome" label="NOME COMPLETO*" error={errors.nome?.message}
            disabled={!podeEditarTudo} {...register('nome')} />
          <Input id="email" label="EMAIL" disabled value={pessoa?.email ?? ''} />
          <Input id="role" label="PERFIL / CARGO" disabled value={role ?? ''} />
          <Input id="telefone" label="TELEFONE" disabled={!podeEditarTudo}
            error={errors.telefone?.message} {...register('telefone')} />
        </div>

        {/* Demais campos (endereço, ministério, vínculo, etc.) seguem o mesmo padrão:
            <Input .../<Select>/<StatusCards> com disabled={!podeEditarTudo}, replicando
            PessoaForm.tsx — conferir esse arquivo linha a linha ao implementar esta task
            para não deixar nenhum campo de fora. */}

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}

        <Button type="submit" variant="primary" isLoading={isLoading} disabled={isLoading}>
          Salvar alterações
        </Button>
      </form>

      <AlterarSenhaForm />
    </div>
  )
}
```

> **Nota para quem implementar:** o bloco de campos acima é o núcleo (nome/email/role/telefone)
> para provar o fluxo de ponta a ponta rápido. Antes de considerar a task pronta, complete os
> campos restantes de `PessoaResponse` (endereço completo com CEP/ViaCEP, vínculo, estado civil,
> sexo, ministério, data de nascimento, data de batismo) copiando os blocos equivalentes de
> `PessoaForm.tsx`, todos com `disabled={!podeEditarTudo}` (ou variante somente-leitura). Isso é
> trabalho mecânico de repetir um padrão já validado, não uma decisão nova — mas é obrigatório
> para fechar o requisito do design ("todos os dados da Pessoa").

- [ ] **Step 3: CSS responsivo (obrigatório — CLAUDE.md)**

```css
.pagina { max-width: 720px; margin: 0 auto; padding: 24px; }
.titulo { font-size: 1.5rem; font-weight: 700; margin: 0 0 4px; }
.subtitulo { color: var(--cor-texto-secundario, #6b7280); margin: 0 0 24px; }
.card {
  background: var(--cor-fundo-card, #fff);
  border: 1px solid var(--cor-borda, #e5e7eb);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.fotoWrap { display: flex; justify-content: center; }
.aviso {
  background: var(--cor-aviso-fundo, #fef9c3);
  color: var(--cor-aviso-texto, #854d0e);
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 0.85rem;
}
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; min-width: 0; }
.erroGeral { color: var(--cor-erro, #dc2626); font-size: 0.85rem; }

@media (max-width: 640px) {
  .pagina { padding: 16px; }
  .grid2 { grid-template-columns: 1fr; }
}
```

- [ ] **Step 4: Testar no navegador com os três perfis**

Run: `npm run dev`, logar como `ACESSO_COMUM` → conferir que só foto é editável e o aviso
aparece; logar como `LIDER` → mesmo comportamento; logar como `ADMIN_IGREJA` → todos os campos
editáveis, email sempre desabilitado. Testar em viewport mobile (grid vira 1 coluna). Testar o
link do rodapé da Sidebar → deve abrir esta página (hoje é 404).

- [ ] **Step 5: Commit** (só depois do autor confirmar visualmente nos três perfis)

```bash
git add frontend/src/app/\(app\)/perfil/ frontend/src/hooks/pessoa/usePerfilForm.ts
git commit -m "feat(perfil): tela Meu Perfil (dados da pessoa + troca de senha)"
```

---

## Self-Review

**Cobertura do spec:**
- Foto na tabela de usuários → Tasks 1, 8, 9. ✅
- `/perfil` mostra todos os dados de Pessoa → Task 13 (núcleo + nota explícita para completar
  os campos restantes copiando `PessoaForm.tsx`). ✅
- ADMIN_IGREJA edita tudo exceto email; ACESSO_COMUM/LIDER só foto → Tasks 4, 13 (back decide,
  front reflete). ✅
- Aviso de "só a secretaria pode mudar" → Task 13. ✅
- Trocar senha com senha atual (padrão mercado) → Tasks 5, 6, 7, 12. ✅
- Conta só-Google não troca senha → Task 6 (`CONTA_SEM_SENHA`). ✅
- Avatar compartilhado (item novo, decidido no brainstorming) → Task 8. ✅

**Consistência de tipos:** `PessoaResponse.fotoId`, `PessoaRequest.fotoId` já existem (usados
por `UploadFoto`/`PessoaForm` hoje) — reaproveitados sem mudança de nome. `AlterarSenhaRequest`
usado igual em `auth.service.ts` (Task 10) e `useAlterarSenha` (Task 11). `ChangePasswordDTO`
usado igual em `AuthService.alterarSenha` (Task 6) e `AuthenticationController` (Task 7).

**Sem placeholders reais de código** — a única ressalva textual (não um "TODO" de código) é a
Task 13, que é explícita sobre precisar repetir um padrão já mostrado por completo em
`PessoaForm.tsx`, em vez de reescrevê-lo por inteiro nesta task (o arquivo já tem 235 linhas
citadas acima; copiá-lo inteiro aqui só infla o plano sem ensinar nada novo).
