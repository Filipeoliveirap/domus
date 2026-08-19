# Termos de Uso + Política de Privacidade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Exigir e registrar o consentimento (Termos de Uso + Política de Privacidade) de todo `usuario` do Domus — garantido pelo backend, não só por checkbox no front — com versionamento e reaceite bloqueante quando a versão mudar.

**Architecture:** Nova tabela `termo_aceite` (um registro por tipo de documento por aceite, nunca editado/apagado) ligada a `usuario`. `TermoAceiteService` concentra validar+registrar no momento da criação de conta (nativo e Google) e checar/registrar reaceite. `GET /auth/me` e as respostas de login passam a expor `precisaAceitarTermos`; o front bloqueia a navegação com um modal até a pessoa aceitar de novo via `POST /termos/aceitar`. Contas existentes (sem nenhuma linha) caem automaticamente nesse mesmo fluxo — sem migração de dados nem caso especial.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, Next.js/TypeScript, TanStack Query não é usado aqui (fluxo de auth já não usa), Zustand, React Hook Form + Zod.

**Spec:** `backend/api/docs/superpowers/specs/2026-08-19-termos-uso-politica-privacidade-design.md`

## Global Constraints

- **TDD estrito, sem exceção.** Todo passo de código escreve o teste primeiro, roda e vê falhar (RED) com o motivo esperado, só depois implementa. **Nunca altere um teste só pra fazer passar** — se um teste não pode passar como está escrito, pare e diga isso explicitamente em vez de enfraquecer a asserção. Nunca reporte "passou" sem ter rodado o comando de verdade e lido a saída. (Regra do `CLAUDE.md` do projeto, seção "Convenções de teste → Regras práticas".)
- Aceite ligado ao `usuario` (login), nunca à `pessoa`.
- Dois tipos separados: `TERMOS_DE_USO` e `POLITICA_PRIVACIDADE` — cada aceite grava as duas linhas juntas.
- Tabela `termo_aceite`: registro histórico/jurídico, **nunca editado nem apagado** — sem soft delete, sem update.
- Versão atual vive numa constante literal (`"1.0"`), igual dos dois lados (back e front), não numa tabela.
- Guarda IP de quem aceitou (reusa o padrão de resolução de IP já existente em `RateLimitFilter`).
- **Só dois endpoints de criação de conta exigem `aceitouTermos` de forma síncrona**: `POST /igrejas/registrar` e `POST /auth/google/registrar`. `POST /auth/reset-password` e `POST /auth/google/login` são endpoints **compartilhados** com fluxos recorrentes (esqueci senha, login normal) e **não** ganham essa exigência — quem entra pela primeira vez por convite cai sozinho no modal de reaceite bloqueante no primeiro `GET /auth/me`, sem tratamento especial nesses dois endpoints.
- `igrejaId`/`usuarioId` sempre vêm do JWT (`UsuarioAutenticado`), nunca do corpo da requisição.
- Serviços retornam DTOs, nunca entidades.
- Toda tela nova de front precisa funcionar em mobile (responsivo).

---

## Fase 1 — Backend: modelo de dados e serviço central

### Task 1: Migration + entidade `TermoAceite` + `TipoTermo` + `TermoAceiteRepository`

**Files:**
- Create: `src/main/resources/db/migration/V20__termo_aceite.sql`
- Create: `src/main/java/com/domus/api/modules/termos/TipoTermo.java`
- Create: `src/main/java/com/domus/api/modules/termos/TermoAceite.java`
- Create: `src/main/java/com/domus/api/modules/termos/TermoAceiteRepository.java`
- Test: `src/test/java/com/domus/api/modules/termos/TermoAceiteRepositoryTest.java`

**Interfaces:**
- Consumes: nada (fundação da feature).
- Produces: `TipoTermo` (enum `TERMOS_DE_USO`, `POLITICA_PRIVACIDADE`), `TermoAceite` (entidade), `TermoAceiteRepository.countByUsuarioIdAndVersao(UUID, String)`, `TermoAceiteRepository.buscarUltimoAceite(UUID)` — usados por `TermoAceiteService` (Task 3).

- [ ] **Step 1: Criar a migration**

```sql
-- V20__termo_aceite.sql
CREATE TABLE termo_aceite (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES usuario(id),
    tipo       VARCHAR(30) NOT NULL,
    versao     VARCHAR(20) NOT NULL,
    ip         VARCHAR(45),
    aceito_em  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_termo_aceite_tipo CHECK (tipo IN ('TERMOS_DE_USO', 'POLITICA_PRIVACIDADE'))
);

CREATE INDEX ix_termo_aceite_usuario ON termo_aceite (usuario_id, tipo);
```

- [ ] **Step 2: Criar `TipoTermo`**

```java
package com.domus.api.modules.termos;

public enum TipoTermo {
    TERMOS_DE_USO,
    POLITICA_PRIVACIDADE
}
```

- [ ] **Step 3: Escrever o teste do repositório (RED)**

```java
package com.domus.api.modules.termos;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class TermoAceiteRepositoryTest {

    @Autowired TermoAceiteRepository termoAceiteRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    private Usuario criarUsuario() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja de Teste Termo Aceite").emailContato("termo@teste.com").build());
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano").email("fulano-termo@teste.com")
                .vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        return usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).senhaHash("hash").ativo(true).build());
    }

    @Test
    void countByUsuarioIdAndVersaoContaSoAVersaoCerta() {
        Usuario usuario = criarUsuario();
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.TERMOS_DE_USO).versao("1.0").ip("1.2.3.4").build());
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.POLITICA_PRIVACIDADE).versao("1.0").ip("1.2.3.4").build());
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.TERMOS_DE_USO).versao("0.9").ip("1.2.3.4").build());
        entityManager.flush();
        entityManager.clear();

        long total = termoAceiteRepository.countByUsuarioIdAndVersao(usuario.getId(), "1.0");

        assertThat(total).isEqualTo(2L);
    }

    @Test
    void buscarUltimoAceiteRetornaODataMaisRecente() {
        Usuario usuario = criarUsuario();
        TermoAceite antigo = termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.TERMOS_DE_USO).versao("1.0").ip("1.2.3.4").build());
        entityManager.flush();
        antigo.setAceitoEm(LocalDateTime.now().minusDays(5));
        termoAceiteRepository.save(antigo);
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuario).tipo(TipoTermo.POLITICA_PRIVACIDADE).versao("1.0").ip("1.2.3.4").build());
        entityManager.flush();
        entityManager.clear();

        LocalDateTime ultimo = termoAceiteRepository.buscarUltimoAceite(usuario.getId());

        assertThat(ultimo).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    @Test
    void buscarUltimoAceiteRetornaNullQuandoNuncaAceitou() {
        Usuario usuario = criarUsuario();

        LocalDateTime ultimo = termoAceiteRepository.buscarUltimoAceite(usuario.getId());

        assertThat(ultimo).isNull();
    }
}
```

- [ ] **Step 4: Rodar e ver falhar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=TermoAceiteRepositoryTest`
Expected: FAIL (compilação — `TermoAceite`/`TermoAceiteRepository` não existem ainda)

- [ ] **Step 5: Criar a entidade `TermoAceite`**

```java
package com.domus.api.modules.termos;

import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro de consentimento — histórico jurídico. Nunca editado nem apagado depois de criado. */
@Entity
@Table(name = "termo_aceite")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class TermoAceite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoTermo tipo;

    @Column(name = "versao", nullable = false, length = 20)
    private String versao;

    @Column(name = "ip", length = 45)
    private String ip;

    @CreationTimestamp
    @Column(name = "aceito_em", nullable = false, updatable = false)
    private LocalDateTime aceitoEm;
}
```

- [ ] **Step 6: Criar `TermoAceiteRepository`**

```java
package com.domus.api.modules.termos;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface TermoAceiteRepository extends JpaRepository<TermoAceite, UUID> {

    long countByUsuarioIdAndVersao(UUID usuarioId, String versao);

    @Query("SELECT MAX(t.aceitoEm) FROM TermoAceite t WHERE t.usuario.id = :usuarioId")
    LocalDateTime buscarUltimoAceite(@Param("usuarioId") UUID usuarioId);
}
```

- [ ] **Step 7: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=TermoAceiteRepositoryTest`
Expected: PASS (3/3)

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V20__termo_aceite.sql src/main/java/com/domus/api/modules/termos/ src/test/java/com/domus/api/modules/termos/TermoAceiteRepositoryTest.java
git commit -m "feat(termos): schema e repositório de termo_aceite"
```

---

### Task 2: `TermosConstantes` + `ClienteIpResolver`

**Files:**
- Create: `src/main/java/com/domus/api/modules/termos/TermosConstantes.java`
- Create: `src/main/java/com/domus/api/shared/web/ClienteIpResolver.java`
- Test: `src/test/java/com/domus/api/shared/web/ClienteIpResolverTest.java`

**Interfaces:**
- Consumes: nada.
- Produces: `TermosConstantes.VERSAO_ATUAL` (String), `ClienteIpResolver.resolver(HttpServletRequest)` — usados por `TermoAceiteService`/`TermoAceiteController` (Task 3/4) e pelos controllers de cadastro (Task 5).

`ClienteIpResolver` extrai o mesmo padrão já usado em `RateLimitFilter.resolverIp` (CF-Connecting-IP → X-Forwarded-For último elemento → `getRemoteAddr()`), reusando a property `app.ratelimit.trust-forwarded-for` que já existe — não cria uma nova.

- [ ] **Step 1: Criar `TermosConstantes`**

```java
package com.domus.api.modules.termos;

/** Versão atual dos documentos — muda junto com o texto (front + aqui), no mesmo PR. */
public final class TermosConstantes {

    public static final String VERSAO_ATUAL = "1.0";

    private TermosConstantes() {}
}
```

- [ ] **Step 2: Escrever o teste do `ClienteIpResolver` (RED)**

```java
package com.domus.api.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClienteIpResolverTest {

    @Test
    void usaCfConnectingIpQuandoConfiaEmForwarded() {
        ClienteIpResolver resolver = new ClienteIpResolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolver(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void usaUltimoElementoDoXForwardedForQuandoSemCfConnectingIp() {
        ClienteIpResolver resolver = new ClienteIpResolver(true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 203.0.113.2");
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolver(request)).isEqualTo("203.0.113.2");
    }

    @Test
    void usaRemoteAddrQuandoNaoConfiaEmForwarded() {
        ClienteIpResolver resolver = new ClienteIpResolver(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1");
        request.setRemoteAddr("10.0.0.1");

        assertThat(resolver.resolver(request)).isEqualTo("10.0.0.1");
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=ClienteIpResolverTest`
Expected: FAIL (compilação — `ClienteIpResolver` não existe)

- [ ] **Step 4: Criar `ClienteIpResolver`**

```java
package com.domus.api.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Mesmo padrão de resolução de IP do RateLimitFilter — reusado aqui pra registrar aceite de termos. */
@Component
public class ClienteIpResolver {

    private final boolean trustForwardedFor;

    public ClienteIpResolver(@Value("${app.ratelimit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolver(HttpServletRequest request) {
        if (trustForwardedFor) {
            String cf = request.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] partes = forwarded.split(",");
                return partes[partes.length - 1].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=ClienteIpResolverTest`
Expected: PASS (3/3)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/termos/TermosConstantes.java src/main/java/com/domus/api/shared/web/ClienteIpResolver.java src/test/java/com/domus/api/shared/web/ClienteIpResolverTest.java
git commit -m "feat(termos): constante de versão + resolvedor de IP do cliente"
```

---

### Task 3: `TermoAceiteService`

**Files:**
- Create: `src/main/java/com/domus/api/modules/termos/TermoAceiteService.java`
- Test: `src/test/java/com/domus/api/modules/termos/TermoAceiteServiceTest.java`

**Interfaces:**
- Consumes: `TermoAceiteRepository` (Task 1), `TermosConstantes.VERSAO_ATUAL` (Task 2).
- Produces: `TermoAceiteService.exigirAceite(boolean aceitouTermos)`, `TermoAceiteService.registrarAceite(UUID usuarioId, String ip)`, `TermoAceiteService.precisaAceitar(UUID usuarioId)`, `TermoAceiteService.dataUltimoAceite(UUID usuarioId)` — usados por `IgrejaService`/`GoogleAuthService` (Task 5), `AuthService`/`AuthenticationController` (Task 6) e `TermoAceiteController` (Task 4).

- [ ] **Step 1: Escrever o teste (RED)**

```java
package com.domus.api.modules.termos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TermoAceiteServiceTest {

    TermoAceiteRepository termoAceiteRepository;
    TermoAceiteService service;
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        termoAceiteRepository = mock(TermoAceiteRepository.class);
        service = new TermoAceiteService(termoAceiteRepository);
    }

    @Test
    void exigirAceiteNaoLancaQuandoTrue() {
        service.exigirAceite(true);
    }

    @Test
    void exigirAceiteLancaQuandoFalse() {
        assertThatThrownBy(() -> service.exigirAceite(false))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class)
                .hasMessageContaining("Termos");
    }

    @Test
    void exigirAceiteLancaQuandoNull() {
        assertThatThrownBy(() -> service.exigirAceite(null))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);
    }

    @Test
    void registrarAceiteSalvaAsDuasLinhas() {
        service.registrarAceite(usuarioId, "203.0.113.9");

        verify(termoAceiteRepository, times(2)).save(any(TermoAceite.class));
    }

    @Test
    void registrarAceiteGravaOsDoisTiposComVersaoEIpCorretos() {
        var capturado = org.mockito.ArgumentCaptor.forClass(TermoAceite.class);

        service.registrarAceite(usuarioId, "203.0.113.9");

        verify(termoAceiteRepository, times(2)).save(capturado.capture());
        var tipos = capturado.getAllValues().stream().map(TermoAceite::getTipo).toList();
        assertThat(tipos).containsExactlyInAnyOrder(TipoTermo.TERMOS_DE_USO, TipoTermo.POLITICA_PRIVACIDADE);
        capturado.getAllValues().forEach(t -> {
            assertThat(t.getVersao()).isEqualTo(TermosConstantes.VERSAO_ATUAL);
            assertThat(t.getIp()).isEqualTo("203.0.113.9");
            assertThat(t.getUsuario().getId()).isEqualTo(usuarioId);
        });
    }

    @Test
    void precisaAceitarFalseQuandoAmbosOsTiposBatemComVersaoAtual() {
        when(termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL))
                .thenReturn(2L);

        assertThat(service.precisaAceitar(usuarioId)).isFalse();
    }

    @Test
    void precisaAceitarTrueQuandoNenhumRegistro() {
        when(termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL))
                .thenReturn(0L);

        assertThat(service.precisaAceitar(usuarioId)).isTrue();
    }

    @Test
    void precisaAceitarTrueQuandoSoUmTipoBateComVersaoAtual() {
        when(termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL))
                .thenReturn(1L);

        assertThat(service.precisaAceitar(usuarioId)).isTrue();
    }

    @Test
    void dataUltimoAceiteDelegaParaORepositorio() {
        LocalDateTime esperado = LocalDateTime.now();
        when(termoAceiteRepository.buscarUltimoAceite(usuarioId)).thenReturn(esperado);

        assertThat(service.dataUltimoAceite(usuarioId)).isEqualTo(esperado);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=TermoAceiteServiceTest`
Expected: FAIL (compilação — `TermoAceiteService` não existe)

- [ ] **Step 3: Implementar `TermoAceiteService`**

```java
package com.domus.api.modules.termos;

import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/** Consentimento (Termos de Uso + Política de Privacidade) — nunca editado, só acumulado. */
@Service
@RequiredArgsConstructor
public class TermoAceiteService {

    private final TermoAceiteRepository termoAceiteRepository;

    /** Chamado antes de criar a conta — recusa a operação se não veio true. */
    public void exigirAceite(Boolean aceitouTermos) {
        if (!Boolean.TRUE.equals(aceitouTermos)) {
            throw new BusinessException("TERMOS_NAO_ACEITOS",
                    "É necessário aceitar os Termos de Uso e a Política de Privacidade para continuar.");
        }
    }

    /** Grava as duas linhas (Termos + Política) com a versão atual. */
    @Transactional
    public void registrarAceite(UUID usuarioId, String ip) {
        Usuario usuarioRef = com.domus.api.modules.usuario.Usuario.builder().id(usuarioId).build();
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuarioRef).tipo(TipoTermo.TERMOS_DE_USO)
                .versao(TermosConstantes.VERSAO_ATUAL).ip(ip).build());
        termoAceiteRepository.save(TermoAceite.builder()
                .usuario(usuarioRef).tipo(TipoTermo.POLITICA_PRIVACIDADE)
                .versao(TermosConstantes.VERSAO_ATUAL).ip(ip).build());
    }

    /** true = falta aceitar Termos e/ou Política na versão atual (nunca aceitou, ou versão antiga). */
    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {
        return termoAceiteRepository.countByUsuarioIdAndVersao(usuarioId, TermosConstantes.VERSAO_ATUAL) < 2;
    }

    @Transactional(readOnly = true)
    public LocalDateTime dataUltimoAceite(UUID usuarioId) {
        return termoAceiteRepository.buscarUltimoAceite(usuarioId);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=TermoAceiteServiceTest`
Expected: PASS (9/9)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/termos/TermoAceiteService.java src/test/java/com/domus/api/modules/termos/TermoAceiteServiceTest.java
git commit -m "feat(termos): TermoAceiteService — exigir, registrar, checar reaceite"
```

---

### Task 4: `POST /termos/aceitar`

**Files:**
- Create: `src/main/java/com/domus/api/modules/termos/TermoAceiteController.java`
- Test: manual (sem harness de `@WebMvcTest` no projeto — dívida técnica documentada; validar com curl)

**Interfaces:**
- Consumes: `TermoAceiteService.registrarAceite` (Task 3), `ClienteIpResolver.resolver` (Task 2), `UsuarioAutenticado.getUsuarioId()` (já existe).
- Produces: `POST /termos/aceitar` — usado pelo modal de reaceite do front (Task 10).

- [ ] **Step 1: Criar o controller**

```java
package com.domus.api.modules.termos;

import com.domus.api.shared.security.UsuarioAutenticado;
import com.domus.api.shared.web.ClienteIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/termos")
@RequiredArgsConstructor
public class TermoAceiteController {

    private final TermoAceiteService termoAceiteService;
    private final UsuarioAutenticado usuarioAutenticado;
    private final ClienteIpResolver clienteIpResolver;

    @PostMapping("/aceitar")
    public ResponseEntity<Void> aceitar(HttpServletRequest request) {
        termoAceiteService.registrarAceite(usuarioAutenticado.getUsuarioId(), clienteIpResolver.resolver(request));
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 2: Compilar**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Validar manualmente com curl** (dev local, sessão autenticada): `POST /api/termos/aceitar` retorna 200; sem sessão retorna 401.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/termos/TermoAceiteController.java
git commit -m "feat(termos): endpoint POST /termos/aceitar"
```

---

## Fase 2 — Backend: enforcement no cadastro e no login

### Task 5: Exigir aceite nos dois endpoints de criação de conta

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/DTO/RegistrarIgrejaAdminRequest.java`
- Modify: `src/main/java/com/domus/api/modules/auth/DTO/GoogleRegistrarDTO.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaService.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaController.java`
- Modify: `src/main/java/com/domus/api/modules/auth/AuthenticationController.java`
- Test: `src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java` (estende o arquivo existente)

**Interfaces:**
- Consumes: `TermoAceiteService.exigirAceite`/`registrarAceite` (Task 3), `ClienteIpResolver.resolver` (Task 2).
- Produces: `IgrejaService.criarIgrejaComAdmin(DadosNovaIgreja dados, boolean aceitouTermos, String ip)` (assinatura estendida — era `criarIgrejaComAdmin(DadosNovaIgreja dados)`).

- [ ] **Step 1: Adicionar `aceitouTermos` aos dois DTOs**

Em `RegistrarIgrejaAdminRequest.java`, adicionar o campo (boolean primitivo — ausente no JSON já vira `false`, que é exatamente o comportamento desejado):

```java
    private boolean aceitouTermos;
```

Em `GoogleRegistrarDTO.java` (record), adicionar como último componente:

```java
        boolean aceitouTermos
```

- [ ] **Step 2: Escrever os testes (RED) — estender `IgrejaServiceTest.java`**

Adicionar ao arquivo existente (o construtor de `IgrejaService` no `@BeforeEach` precisa passar a receber `TermoAceiteService` mockado — ajustar in-place):

```java
    @Test
    void criarIgrejaComAdminLancaQuandoNaoAceitouTermos() {
        assertThatThrownBy(() -> igrejaService.criarIgrejaComAdmin(
                new DadosNovaIgreja("Igreja X", "contato@x.com", null, "11999999999",
                        "Admin", "admin@x.com", "hash", null),
                false, "203.0.113.9"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Termos");

        verify(igrejaRepository, never()).save(any());
    }

    @Test
    void criarIgrejaComAdminRegistraAceiteQuandoTrue() {
        when(membroRepository.existsByEmail(anyString())).thenReturn(false);
        when(igrejaRepository.save(any(Igreja.class))).thenAnswer(inv -> {
            Igreja i = inv.getArgument(0);
            i.setId(igrejaId);
            return i;
        });
        when(roleRepository.findByNome("ADMIN_IGREJA")).thenReturn(Optional.of(
                Role.builder().id(UUID.randomUUID()).nome("ADMIN_IGREJA").build()));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        igrejaService.criarIgrejaComAdmin(
                new DadosNovaIgreja("Igreja Y", "contato@y.com", null, "11999999999",
                        "Admin", "admin@y.com", "hash", null),
                true, "203.0.113.9");

        verify(termoAceiteService).registrarAceite(any(UUID.class), eq("203.0.113.9"));
    }
```

(Ajustar imports/mocks conforme o arquivo existente já usa — `Optional`, `Role`, `Usuario`, `UUID`, `verify`, `any`, `eq`, `when`, `anyString` já devem estar importados; adicionar `import static org.mockito.ArgumentMatchers.eq;` se faltar. O subagente que executar esta task deve abrir o arquivo primeiro pra confirmar o `@BeforeEach` exato antes de editar — os nomes de mock (`igrejaRepository`, `membroRepository`, `roleRepository`, `usuarioRepository`) já existem no arquivo real, confirmados por leitura anterior desta sessão.)

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=IgrejaServiceTest`
Expected: FAIL (assinatura de `criarIgrejaComAdmin` não bate, `termoAceiteService` não existe no construtor)

- [ ] **Step 4: Atualizar `IgrejaService`**

Adicionar `private final TermoAceiteService termoAceiteService;` aos campos (Lombok `@RequiredArgsConstructor` já gera o construtor). Mudar a assinatura de `criarIgrejaComAdmin`:

```java
    @Transactional
    public Usuario criarIgrejaComAdmin(DadosNovaIgreja dados, boolean aceitouTermos, String ip) {
        termoAceiteService.exigirAceite(aceitouTermos);

        if (membroRepository.existsByEmail(dados.emailAdmin())) {
```

(o corpo original do método continua igual a partir daqui — só a linha `if (membroRepository...` em diante, sem mudanças). No final do método, antes do `return admin;`, adicionar:

```java
        termoAceiteService.registrarAceite(admin.getId(), ip);
```

- [ ] **Step 5: Atualizar `IgrejaService.registrar` (chamador nativo)**

```java
    @Transactional
    public RegistrarIgrejaResponse registrar(RegistrarIgrejaAdminRequest request, String ip) {
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
        ), request.isAceitouTermos(), ip);
```

(o resto do método continua igual)

- [ ] **Step 6: Atualizar `IgrejaController.cadastrarIgreja` pra extrair o IP e repassar**

```java
    private final IgrejaService igrejaService;
    private final AuthCookieFactory cookieFactory;
    private final UsuarioAutenticado usuarioAutenticado;
    private final com.domus.api.shared.web.ClienteIpResolver clienteIpResolver;

    @PostMapping("/registrar")
    public ResponseEntity<SessaoDTO> cadastrarIgreja(
            @RequestBody @Valid RegistrarIgrejaAdminRequest data,
            jakarta.servlet.http.HttpServletRequest request) {
        RegistrarIgrejaResponse response = igrejaService.registrar(data, clienteIpResolver.resolver(request));
```

(o resto do método continua igual — só a chamada a `igrejaService.registrar` ganhou o segundo argumento)

- [ ] **Step 7: Atualizar `GoogleAuthService.registrar` (chamador Google) e `AuthenticationController.googleRegistrar`**

Em `GoogleAuthService.java`, o método `registrar(GoogleRegistrarDTO dados)` ganha um parâmetro `String ip`:

```java
    public RegistrarIgrejaResponse registrar(GoogleRegistrarDTO dados, String ip) {
        GoogleIdToken.Payload payload = verificar(dados.idToken());
        String sub = payload.getSubject();
        String email = payload.getEmail();
        String nome = (String) payload.get("name");

        Usuario admin = igrejaService.criarIgrejaComAdmin(new DadosNovaIgreja(
                dados.nomeIgreja(),
                email,
                dados.cnpj(),
                dados.telefoneContato(),
                nome,
                email,
                null,
                sub
        ), dados.aceitouTermos(), ip);
```

(o resto do método continua igual)

Em `AuthenticationController.java`:

```java
    private final com.domus.api.shared.web.ClienteIpResolver clienteIpResolver;

    @PostMapping("/google/registrar")
    public ResponseEntity<SessaoDTO> googleRegistrar(
            @RequestBody @Valid GoogleRegistrarDTO data,
            jakarta.servlet.http.HttpServletRequest request) {
        RegistrarIgrejaResponse r = googleAuthService.registrar(data, clienteIpResolver.resolver(request));
```

(o resto do método continua igual)

- [ ] **Step 8: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=IgrejaServiceTest`
Expected: PASS

- [ ] **Step 9: Compilar tudo (confirma que os outros chamadores de `IgrejaService.registrar`/`GoogleAuthService.registrar` não quebraram)**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS — se houver outro teste chamando `igrejaService.registrar(request)` (1 argumento) ou `googleAuthService.registrar(data)` (1 argumento) em algum outro arquivo de teste, ajustar a chamada pra passar um IP de teste (ex.: `"127.0.0.1"`) também. O subagente deve rodar `mvn -q -o test-compile` e corrigir qualquer chamada desatualizada antes de seguir.

- [ ] **Step 10: Rodar a suíte inteira**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q -o test`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/ src/main/java/com/domus/api/modules/auth/ src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java
git commit -m "feat(termos): exige e registra aceite nos dois cadastros (nativo e Google)"
```

---

### Task 6: `precisaAceitarTermos` e `termosAceitosEm` no login e no `/auth/me`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/auth/DTO/SessaoDTO.java`
- Modify: `src/main/java/com/domus/api/modules/auth/AuthService.java`
- Modify: `src/main/java/com/domus/api/modules/auth/AuthenticationController.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaController.java`
- Test: `src/test/java/com/domus/api/modules/auth/AuthServiceTest.java` (estende, se existir — senão criar seguindo o padrão dos outros testes de service do módulo)

**Interfaces:**
- Consumes: `TermoAceiteService.precisaAceitar`/`dataUltimoAceite` (Task 3).
- Produces: `SessaoDTO.precisaAceitarTermos` (boolean), `SessaoDTO.termosAceitosEm` (LocalDateTime, nullable) — consumidos pelo front (Task 7).

- [ ] **Step 1: Atualizar `SessaoDTO`**

Adicionar os dois campos ao record e um construtor de compatibilidade pros dois call sites que ainda não têm essa informação calculada (cadastro nativo/Google — contas recém-criadas, acabaram de aceitar):

```java
public record SessaoDTO(
        UUID id,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome,
        UUID fotoId,
        String cargo,
        String igrejaSigla,
        UUID igrejaLogoId,
        List<String> capacidadesExtras,
        boolean precisaAceitarTermos,
        java.time.LocalDateTime termosAceitosEm
) {
    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                List.of(), false, java.time.LocalDateTime.now());
    }

    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId,
                      List<String> capacidadesExtras) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                capacidadesExtras, false, java.time.LocalDateTime.now());
    }
}
```

(Os dois construtores curtos existentes continuam funcionando sem mudança nos chamadores de `IgrejaController`/`AuthenticationController.googleRegistrar` — contas recém-criadas nunca precisam aceitar de novo na hora, `precisaAceitarTermos=false` é sempre verdade ali.)

- [ ] **Step 2: Escrever o teste (RED)**

Se `AuthServiceTest.java` já existir, adicionar; senão criar seguindo Estilo A (mock manual). Teste mínimo:

```java
package com.domus.api.modules.auth;

import com.domus.api.modules.auth.DTO.SessaoDTO;
import com.domus.api.modules.termos.TermoAceiteService;
import com.domus.api.modules.usuario.UsuarioCapacidadeRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceSessaoDeTermosTest {

    UsuarioRepository usuarioRepository;
    TermoAceiteService termoAceiteService;
    UsuarioCapacidadeRepository capacidadeRepository;
    AuthService authService;
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        usuarioRepository = mock(UsuarioRepository.class);
        termoAceiteService = mock(TermoAceiteService.class);
        capacidadeRepository = mock(UsuarioCapacidadeRepository.class);
        authService = new AuthService(null, null, null, null, usuarioRepository, null,
                capacidadeRepository, termoAceiteService);
    }

    @Test
    void sessaoDeIncluiPrecisaAceitarTermosDoService() {
        when(usuarioRepository.findSessaoById(usuarioId)).thenReturn(Optional.of(
                new com.domus.api.modules.auth.DTO.SessaoDTO(usuarioId, "Fulano", "ADMIN_IGREJA",
                        UUID.randomUUID(), "Igreja X", null, null, null, null)));
        when(capacidadeRepository.findByUsuarioId(usuarioId)).thenReturn(List.of());
        when(termoAceiteService.precisaAceitar(usuarioId)).thenReturn(true);
        LocalDateTime ultimo = LocalDateTime.now();
        when(termoAceiteService.dataUltimoAceite(usuarioId)).thenReturn(ultimo);

        SessaoDTO sessao = authService.sessaoDe(usuarioId);

        assertThat(sessao.precisaAceitarTermos()).isTrue();
        assertThat(sessao.termosAceitosEm()).isEqualTo(ultimo);
    }
}
```

O subagente que executar esta task deve abrir `AuthService.java` primeiro pra confirmar a ordem exata dos parâmetros do construtor atual (`@RequiredArgsConstructor` — a ordem segue a ordem de declaração dos campos `private final`) antes de escrever a chamada `new AuthService(...)` no teste — o construtor acima é ilustrativo da forma, não necessariamente a ordem exata; ajustar pra bater com os campos reais.

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=AuthServiceSessaoDeTermosTest`
Expected: FAIL (compilação — `AuthService` não tem `TermoAceiteService` no construtor ainda; `SessaoDTO` não tem os novos campos até o Step 1 estar aplicado, então isso já deve passar depois do Step 1 — a falha aqui é só sobre o construtor do `AuthService`)

- [ ] **Step 4: Atualizar `AuthService.sessaoDe`**

Adicionar `private final TermoAceiteService termoAceiteService;` aos campos de `AuthService` (Lombok gera o construtor). Atualizar o método:

```java
    public SessaoDTO sessaoDe(UUID usuarioId) {
        SessaoDTO sessao = usuarioRepository.findSessaoById(usuarioId)
                .orElseThrow(() -> {
                    log.warn("Sessão pedida para usuário inexistente. usuario_id={}", usuarioId);
                    return new SessaoExpiradaException("SESSAO_INVALIDA",
                            "Sessão expirada. Faça login novamente.");
                });
        return new SessaoDTO(sessao.id(), sessao.nome(), sessao.role(),
                sessao.igrejaId(), sessao.igrejaNome(), sessao.fotoId(),
                sessao.cargo(), sessao.igrejaSigla(), sessao.igrejaLogoId(),
                capacidadeRepository.findByUsuarioId(usuarioId).stream()
                        .map(UsuarioCapacidade::getCapacidade).toList(),
                termoAceiteService.precisaAceitar(usuarioId),
                termoAceiteService.dataUltimoAceite(usuarioId));
    }
```

- [ ] **Step 5: Atualizar `AuthenticationController.sessaoDe(LoginResponseDTO r)` (usado por `/login` e `/google/login`)**

Esse helper precisa do `TermoAceiteService` também — injetar no controller:

```java
    private final TermoAceiteService termoAceiteService;

    private SessaoDTO sessaoDe(LoginResponseDTO r) {
        return new SessaoDTO(r.id(), r.nome(), r.role(), r.igrejaId(), r.igrejaNome(),
                r.fotoId(), r.cargo(), r.igrejaSigla(), r.igrejaLogoId(), r.capacidadesExtras(),
                termoAceiteService.precisaAceitar(r.id()), termoAceiteService.dataUltimoAceite(r.id()));
    }
```

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=AuthServiceSessaoDeTermosTest`
Expected: PASS

- [ ] **Step 7: Compilar tudo e rodar a suíte inteira**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q -o test`
Expected: PASS — corrigir qualquer outro chamador de `new AuthService(...)` em teste que precise do novo parâmetro (o subagente deve rodar `mvn -q -o test-compile` primeiro pra achar todos antes de rodar a suíte).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/ src/main/java/com/domus/api/modules/igreja/IgrejaController.java src/test/java/com/domus/api/modules/auth/
git commit -m "feat(termos): expõe precisaAceitarTermos/termosAceitosEm em login e /auth/me"
```

> **Checkpoint Fase 2 / Backend completo:** rodar `mvn -q -o test` uma vez inteiro, testar manualmente com curl: cadastrar igreja sem `aceitouTermos` → 400; com `true` → 200 e duas linhas em `termo_aceite`; `GET /auth/me` de uma conta recém-criada → `precisaAceitarTermos: false`; de uma conta antiga (sem nenhuma linha) → `precisaAceitarTermos: true`.

---

## Fase 3 — Frontend

### Task 7: `endpoints.ts`, tipos e `authStore`

**Files:**
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/types/auth.types.ts`
- Modify: `frontend/src/store/authStore.ts`
- Create: `frontend/src/lib/termos.ts`

**Interfaces:**
- Consumes: nada.
- Produces: `Endpoints.termos.ACEITAR`, `Sessao.precisaAceitarTermos`/`termosAceitosEm`, `RegistrarIgrejaRequest.aceitouTermos`, `GoogleRegistrarRequest.aceitouTermos`, `useAuthStore().precisaAceitarTermos`/`termosAceitosEm`/`confirmarAceiteTermos()`, `VERSAO_TERMOS_ATUAL` — consumidos pelas Tasks 8-12.

- [ ] **Step 1: Adicionar o endpoint**

Em `endpoints.ts`, dentro do objeto `auth` já existente:

```typescript
  termos: {
    ACEITAR: '/termos/aceitar',
  },
```

- [ ] **Step 2: Criar a constante de versão**

```typescript
// frontend/src/lib/termos.ts
/** Precisa bater com TermosConstantes.VERSAO_ATUAL no backend — muda junto, no mesmo PR. */
export const VERSAO_TERMOS_ATUAL = '1.0'
```

- [ ] **Step 3: Atualizar `auth.types.ts`**

No `Sessao`:

```typescript
    /** true = precisa aceitar Termos/Política de novo (nunca aceitou, ou versão mudou). */
    precisaAceitarTermos: boolean;
    /** Data do aceite mais recente, ou null se nunca aceitou. */
    termosAceitosEm: string | null;
```

Em `RegistrarIgrejaRequest`:

```typescript
    aceitouTermos: boolean;
```

Em `GoogleRegistrarRequest`:

```typescript
    aceitouTermos: boolean;
```

- [ ] **Step 4: Atualizar `authStore.ts`**

Em `AuthState`, junto dos demais campos:

```typescript
  precisaAceitarTermos: boolean
  termosAceitosEm: string | null
```

Em `estadoDeslogado`:

```typescript
  precisaAceitarTermos: false,
  termosAceitosEm: null,
```

Adicionar a ação (mesmo estilo de `atualizarExclusaoAgendada`):

```typescript
  confirmarAceiteTermos: () => void
```

```typescript
  confirmarAceiteTermos: () => set({ precisaAceitarTermos: false, termosAceitosEm: new Date().toISOString() }),
```

(O `login: (data) => set({ ...data, ... })` já vai popular `precisaAceitarTermos`/`termosAceitosEm` automaticamente por causa do spread de `data: Sessao` — sem mudança extra ali.)

- [ ] **Step 5: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 6: Commit**

```bash
git add frontend/src/lib/endpoints.ts frontend/src/lib/termos.ts frontend/src/types/auth.types.ts frontend/src/store/authStore.ts
git commit -m "feat(front): endpoints, tipos e authStore de termo de aceite"
```

---

### Task 8: Enviar `aceitouTermos` de verdade no cadastro nativo

**Files:**
- Modify: `frontend/src/hooks/auth/UseRegistrarIgreja.ts`

**Interfaces:**
- Consumes: `RegistrarIgrejaRequest.aceitouTermos` (Task 7).
- Produces: nada novo — corrige um bug existente (o campo já existe no formulário e na validação, mas é descartado antes de chamar a API).

O formulário nativo (`Passo2.tsx`) **já tem** a checkbox `aceitouTermos` funcionando (schema Zod já valida, `requiredChecks: ['aceitouTermos']`), mas o hook descarta o valor antes de enviar pro backend — `const { confirmarSenha, aceitouTermos, ...dadosAdmin } = dataPasso2` joga o campo fora. Esta task só corrige esse envio, sem mexer na UI.

- [ ] **Step 1: Corrigir `onSubmit`**

Em `UseRegistrarIgreja.ts`, dentro de `onSubmit`:

```typescript
        try {
            const { confirmarSenha, ...dadosAdmin } = dataPasso2

            const dadosIgreja = {
                ...dataPasso1,
                telefoneContato: dataPasso1.telefoneContato.replace(/\D/g, ''),
                cnpj: dataPasso1.cnpj?.replace(/\D/g, '') || undefined,
            }
            const response = await authService.registrarIgreja({
                ...dadosIgreja,
                ...dadosAdmin,
            })
```

(a única mudança é remover `aceitouTermos` da desestruturação que descartava — `dadosAdmin` agora inclui `aceitouTermos: boolean`, que bate exatamente com o campo novo de `RegistrarIgrejaRequest`)

- [ ] **Step 2: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos (`RegistrarIgrejaRequest` agora exige `aceitouTermos`, e `dadosAdmin` já carrega ele — se der erro de tipo aqui, é sinal de que a Task 7 não foi aplicada antes desta, confirmar ordem)

- [ ] **Step 3: Testar manualmente no navegador**: cadastrar uma igreja nova pela tela, confirmar no Network tab que o payload de `POST /api/igrejas/registrar` inclui `"aceitouTermos": true`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/hooks/auth/UseRegistrarIgreja.ts
git commit -m "fix(front): envia aceitouTermos de verdade no cadastro nativo (antes era descartado)"
```

---

### Task 9: Checkbox de termos no cadastro via Google

**Files:**
- Modify: `frontend/src/hooks/auth/UseRegistrarIgreja.ts`
- Modify: `frontend/src/app/(auth)/cadastro/Passo1.tsx`
- Modify: `frontend/src/app/(auth)/cadastro/Passo1.module.css`

**Interfaces:**
- Consumes: `GoogleRegistrarRequest.aceitouTermos` (Task 7).
- Produces: nada consumido por outras tasks — fecha o gap do cadastro via Google, que hoje não tem checkbox nenhuma (o cadastro via Google submete direto do Passo1, sem passar pelo Passo2 onde a checkbox nativa vive).

- [ ] **Step 1: Adicionar estado da checkbox no hook**

Em `UseRegistrarIgreja.ts`, junto dos outros `useState`:

```typescript
    const [aceitouTermosGoogle, setAceitouTermosGoogle] = useState(false)
```

Atualizar `onSubmitGoogle` pra enviar o campo:

```typescript
    const onSubmitGoogle = async (dataIgreja: RegistrarIgrejaFormData1) => {
        if (!googleData) return
        setErroGeral(null)
        setIsLoading(true)
        try {
            const response = await authService.googleRegistrar({
                idToken: googleData.idToken,
                nomeIgreja: dataIgreja.nomeIgreja,
                cnpj: dataIgreja.cnpj?.replace(/\D/g, '') || undefined,
                telefoneContato: dataIgreja.telefoneContato.replace(/\D/g, ''),
                aceitouTermos: aceitouTermosGoogle,
            })
```

Adicionar `aceitouTermosGoogle` e `setAceitouTermosGoogle` ao objeto retornado pelo hook, junto dos outros valores já retornados (`onSubmitGoogle`, `onGoogleAuth`, etc.).

- [ ] **Step 2: Adicionar a checkbox em `Passo1.tsx`**

Atualizar a interface de props:

```typescript
interface Passo1Props {
  register: UseFormRegister<RegistrarIgrejaFormData1>
  handleSubmit: UseFormHandleSubmit<RegistrarIgrejaFormData1>
  errors: FieldErrors<RegistrarIgrejaFormData1>
  passo1Incompleto: boolean
  setValue: UseFormSetValue<RegistrarIgrejaFormData1>
  onAvancar: (data: RegistrarIgrejaFormData1) => void
  googleData: { nome: string; email: string } | null
  onGoogleAuth: (idToken: string) => void
  onGoogleError: () => void
  onSubmitGoogle: (data: RegistrarIgrejaFormData1) => void
  erroGeral: string | null
  isLoading: boolean
  aceitouTermosGoogle: boolean
  setAceitouTermosGoogle: (v: boolean) => void
}
```

```typescript
export function Passo1({
  register, handleSubmit, setValue, errors, passo1Incompleto, onAvancar,
  googleData, onGoogleAuth, onGoogleError, onSubmitGoogle, erroGeral, isLoading,
  aceitouTermosGoogle, setAceitouTermosGoogle,
}: Passo1Props) {
```

Adicionar a checkbox logo antes do bloco `{erroGeral && ...}`, só quando `modoGoogle` for true:

```tsx
        {modoGoogle && (
          <div className={styles.termosWrapper}>
            <label className={styles.termosLabel}>
              <input
                type="checkbox"
                className={styles.checkbox}
                checked={aceitouTermosGoogle}
                onChange={(e) => setAceitouTermosGoogle(e.target.checked)}
              />
              <span className={styles.termosTexto}>
                Ao criar minha conta, eu concordo com os{' '}
                <Link href="/termos" className={styles.termosLink} target="_blank">Termos de Uso</Link>
                {' '}e a{' '}
                <Link href="/privacidade" className={styles.termosLink} target="_blank">Política de Privacidade</Link>
                {' '}do Domus.
              </span>
            </label>
          </div>
        )}

        {erroGeral && <div className={styles.erroGeral}>{erroGeral}</div>}
```

Atualizar o `disabled` do botão de submeter pra também exigir a checkbox no modo Google:

```typescript
          <Button
            type="submit"
            variant="primary"
            size="md"
            disabled={passo1Incompleto || isLoading || (modoGoogle && !aceitouTermosGoogle)}
            isLoading={modoGoogle && isLoading}
            loadingText="Cadastrando..."
          >
```

- [ ] **Step 3: Atualizar `page.tsx` (repassa as duas novas props pro `Passo1`)**

Adicionar `aceitouTermosGoogle` e `setAceitouTermosGoogle` na desestruturação do hook e na passagem de props pro `<Passo1 .../>`, seguindo o mesmo padrão das demais props já repassadas ali (`googleData`, `onGoogleAuth`, etc. — o subagente confirma o nome exato do arquivo que renderiza `<Passo1>` e replica o padrão).

- [ ] **Step 4: Adicionar as classes CSS que faltam em `Passo1.module.css`**

Copiar exatamente de `Passo2.module.css` (classes `.termosWrapper`, `.termosLabel`, `.checkbox`, `.termosTexto`, `.termosLink`, `.termosLink:hover`):

```css
.termosWrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.termosLabel {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
}

.checkbox {
  width: 16px;
  height: 16px;
  margin-top: 3px;
  flex-shrink: 0;
  cursor: pointer;
  accent-color: var(--color-primary);
}

.termosTexto {
  font-size: var(--font-size-sm);
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.termosLink {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
  transition: opacity var(--transition-fast);
}

.termosLink:hover {
  opacity: 0.75;
}
```

- [ ] **Step 5: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 6: Testar manualmente no navegador**: cadastro via Google sem marcar a checkbox → botão "Concluir cadastro" desabilitado; marcando → habilita; confirmar no Network que `POST /api/auth/google/registrar` inclui `"aceitouTermos": true`.

- [ ] **Step 7: Testar responsividade em viewport de celular** (checkbox não pode quebrar o layout)

- [ ] **Step 8: Commit**

```bash
git add frontend/src/hooks/auth/UseRegistrarIgreja.ts frontend/src/app/\(auth\)/cadastro/Passo1.tsx frontend/src/app/\(auth\)/cadastro/Passo1.module.css frontend/src/app/\(auth\)/cadastro/page.tsx
git commit -m "feat(front): checkbox de termos no cadastro via Google (não existia)"
```

---

### Task 10: Modal bloqueante de reaceite

**Files:**
- Create: `frontend/src/components/common/ModalReaceitarTermos/ModalReaceitarTermos.tsx`
- Create: `frontend/src/components/common/ModalReaceitarTermos/ModalReaceitarTermos.module.css`
- Modify: `frontend/src/components/auth/AuthGuard.tsx`

**Interfaces:**
- Consumes: `Endpoints.termos.ACEITAR` (Task 7), `useAuthStore().precisaAceitarTermos`/`confirmarAceiteTermos()` (Task 7).
- Produces: componente `ModalReaceitarTermos`, renderizado dentro de `AuthGuard` — bloqueia toda a área `(app)` até a pessoa aceitar.

- [ ] **Step 1: Criar o componente**

```tsx
'use client'

import { useState } from 'react'
import Link from 'next/link'
import { AlertTriangle } from 'lucide-react'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import { useAuthStore } from '@/store/authStore'
import styles from './ModalReaceitarTermos.module.css'

/**
 * Modal bloqueante — sem "X", sem clicar fora, sem navegar — até a pessoa aceitar de
 * novo os Termos/Política. Cobre tanto quem tinha aceitado uma versão antiga quanto
 * contas criadas antes desta feature (nunca tiveram nenhum registro).
 */
export function ModalReaceitarTermos() {
  const confirmarAceiteTermos = useAuthStore((s) => s.confirmarAceiteTermos)
  const [aceitou, setAceitou] = useState(false)
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function confirmar() {
    setCarregando(true)
    setErro(null)
    try {
      await api.post(Endpoints.termos.ACEITAR)
      confirmarAceiteTermos()
    } catch {
      setErro('Não foi possível registrar seu aceite. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className={styles.overlay}>
      <div className={styles.modal} role="dialog" aria-modal="true">
        <div className={styles.cabecalho}>
          <span className={styles.iconBox}>
            <AlertTriangle size={22} aria-hidden="true" />
          </span>
          <h2 className={styles.titulo}>Atualizamos nossos Termos</h2>
        </div>

        <p className={styles.mensagem}>
          Nossos Termos de Uso e/ou Política de Privacidade mudaram. Pra continuar
          usando o Domus, revise e aceite a versão atual.
        </p>

        <label className={styles.termosLabel}>
          <input
            type="checkbox"
            className={styles.checkbox}
            checked={aceitou}
            onChange={(e) => setAceitou(e.target.checked)}
          />
          <span className={styles.termosTexto}>
            Li e concordo com os{' '}
            <Link href="/termos" className={styles.termosLink} target="_blank">Termos de Uso</Link>
            {' '}e a{' '}
            <Link href="/privacidade" className={styles.termosLink} target="_blank">Política de Privacidade</Link>.
          </span>
        </label>

        {erro && <p className={styles.erro}>{erro}</p>}

        <button
          type="button"
          className={styles.btnConfirmar}
          disabled={!aceitou || carregando}
          onClick={confirmar}
        >
          {carregando ? 'Confirmando…' : 'Aceitar e continuar'}
        </button>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Criar o CSS** (overlay cobrindo a tela toda, `z-index` alto, modal centralizado — seguir o mesmo padrão visual de `ModalExcluirIgreja.module.css` já existente no projeto: `.overlay` com `position: fixed; inset: 0`, `.modal` com fundo, padding, `max-width`, responsivo com `@media (max-width: 767px)` reduzindo padding)

```css
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 16px;
}

.modal {
  background: var(--color-surface, #fff);
  border-radius: 12px;
  padding: 32px;
  max-width: 480px;
  width: 100%;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.cabecalho {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.iconBox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: var(--color-warning-bg, #fef3c7);
  color: var(--color-warning, #d97706);
  flex-shrink: 0;
}

.titulo {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.mensagem {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.6;
  margin-bottom: 20px;
}

.termosLabel {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
  margin-bottom: 16px;
}

.checkbox {
  width: 16px;
  height: 16px;
  margin-top: 3px;
  flex-shrink: 0;
  cursor: pointer;
  accent-color: var(--color-primary);
}

.termosTexto {
  font-size: var(--font-size-sm);
  line-height: 1.5;
  color: var(--color-text-secondary);
}

.termosLink {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
  text-decoration: none;
}

.termosLink:hover {
  opacity: 0.75;
}

.erro {
  font-size: var(--font-size-sm);
  color: var(--color-danger);
  margin-bottom: 12px;
}

.btnConfirmar {
  width: 100%;
  padding: 12px 24px;
  background: var(--color-primary);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
}

.btnConfirmar:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

@media (max-width: 767px) {
  .modal {
    padding: 20px;
  }
}
```

- [ ] **Step 3: Renderizar dentro de `AuthGuard`**

Em `AuthGuard.tsx`, adicionar o import e a checagem logo antes de renderizar `children`:

```tsx
import { ModalReaceitarTermos } from '@/components/common/ModalReaceitarTermos/ModalReaceitarTermos'
```

```tsx
  if (!hidratado || !isAuthenticated) return null

  const precisaAceitarTermos = useAuthStore((s) => s.precisaAceitarTermos)
  if (precisaAceitarTermos) return <ModalReaceitarTermos />

  return <>{children}</>
```

O subagente que executar esta task deve conferir a posição exata dos hooks existentes em `AuthGuard.tsx` — `useAuthStore((s) => s.precisaAceitarTermos)` precisa ser chamado no mesmo nível dos outros `useAuthStore((s) => ...)` já existentes no componente (regra dos hooks: nunca depois de um `return` condicional), então mover essa linha pra junto dos outros `useAuthStore` no topo do componente, não literalmente onde o trecho acima sugere — o trecho mostra a LÓGICA (checar e retornar o modal antes de `children`), não a posição exata da declaração do hook.

- [ ] **Step 4: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 5: Testar manualmente no navegador**: logar com uma conta que nunca aceitou termos (ex.: uma conta criada antes desta feature, ou apagar as linhas de `termo_aceite` de uma conta de teste direto no banco) → modal bloqueante aparece, sem jeito de fechar; marcar a checkbox e confirmar → modal some, app carrega normal.

- [ ] **Step 6: Testar responsividade em viewport de celular**

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/common/ModalReaceitarTermos/ frontend/src/components/auth/AuthGuard.tsx
git commit -m "feat(front): modal bloqueante de reaceite de termos"
```

---

### Task 11: Páginas de conteúdo `/termos` e `/privacidade`

**Files:**
- Create: `frontend/src/app/(auth)/termos/page.tsx`
- Create: `frontend/src/app/(auth)/termos/page.module.css`
- Create: `frontend/src/app/(auth)/privacidade/page.tsx`
- Create: `frontend/src/app/(auth)/privacidade/page.module.css`

**Interfaces:**
- Consumes: nada.
- Produces: as duas rotas já referenciadas pelos links em `Passo2.tsx`, `Passo1.tsx` (Task 9) e `ModalReaceitarTermos` (Task 10) — hoje esses links **já existem** e apontam pra `/termos`/`/privacidade`, mas as páginas em si não existem (404).

O texto abaixo é o ponto de partida real (não placeholder) — cobre exatamente os pontos exigidos na spec: LGPD controlador/operador, direito à eliminação referenciando as features já existentes, subprocessadores, cookies. Revisão jurídica fina fica de fora do escopo desta plan (é conteúdo, não código) — mas o conteúdo abaixo é completo o suficiente pra publicar.

- [ ] **Step 1: Criar `/termos`**

```tsx
// frontend/src/app/(auth)/termos/page.tsx
import Link from 'next/link'
import styles from './page.module.css'

export default function TermosDeUsoPage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.titulo}>Termos de Uso do Domus</h1>
        <p className={styles.versao}>Versão 1.0 — última atualização em 19/08/2026</p>

        <h2>1. Aceitação dos Termos</h2>
        <p>
          Ao criar uma conta ou usar o Domus, você concorda com estes Termos de Uso e
          com a nossa <Link href="/privacidade">Política de Privacidade</Link>. Se você
          não concordar, não utilize o produto.
        </p>

        <h2>2. O que é o Domus</h2>
        <p>
          O Domus é um sistema de gestão administrativa (cadastro de pessoas, eventos,
          financeiro e busca) voltado para igrejas de pequeno e médio porte.
        </p>

        <h2>3. Quem pode usar</h2>
        <p>
          O cadastro de uma igreja cria uma conta administradora (ADMIN_IGREJA), responsável
          por conceder acesso a outras pessoas dentro da mesma igreja. Cada pessoa com acesso
          precisa aceitar estes Termos e a Política de Privacidade individualmente.
        </p>

        <h2>4. Responsabilidade pelos dados cadastrados</h2>
        <p>
          A igreja que usa o Domus é a <strong>controladora</strong> dos dados de pessoas,
          membros e visitantes que ela cadastra — é quem decide o que coletar e para quê,
          nos termos da Lei Geral de Proteção de Dados (LGPD). O Domus atua como
          <strong> operador</strong>, processando esses dados em nome da igreja, conforme
          descrito na nossa Política de Privacidade.
        </p>

        <h2>5. Uso adequado</h2>
        <p>
          Você concorda em não usar o Domus para fins ilegais, para armazenar dados sem
          autorização das pessoas envolvidas, ou para qualquer atividade que viole direitos
          de terceiros.
        </p>

        <h2>6. Exclusão de conta</h2>
        <p>
          A qualquer momento, o administrador da igreja pode agendar a exclusão definitiva
          da conta e de todos os dados associados, com carência de 10 dias cancelável, pela
          tela de Configurações. Depois do prazo, a exclusão é irreversível.
        </p>

        <h2>7. Mudanças nestes Termos</h2>
        <p>
          Podemos atualizar estes Termos. Mudanças relevantes exigem um novo aceite, que
          será pedido no seu próximo acesso.
        </p>

        <h2>8. Contato</h2>
        <p>Dúvidas sobre estes Termos? Fale com a gente pelo suporte do Domus.</p>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Criar `/privacidade`**

```tsx
// frontend/src/app/(auth)/privacidade/page.tsx
import Link from 'next/link'
import styles from './page.module.css'

export default function PoliticaDePrivacidadePage() {
  return (
    <div className={styles.page}>
      <div className={styles.container}>
        <h1 className={styles.titulo}>Política de Privacidade do Domus</h1>
        <p className={styles.versao}>Versão 1.0 — última atualização em 19/08/2026</p>

        <h2>1. Quem somos</h2>
        <p>
          O Domus processa dados pessoais para permitir que igrejas gerenciem seus membros,
          eventos e finanças. Esta política explica como tratamos esses dados, em conformidade
          com a Lei Geral de Proteção de Dados (LGPD — Lei 13.709/2018).
        </p>

        <h2>2. Controlador e operador</h2>
        <p>
          A igreja que usa o Domus é a <strong>controladora</strong> dos dados de pessoas,
          membros e visitantes que ela cadastra — decide o que coletar, por quanto tempo manter
          e com quem compartilhar. O Domus é o <strong>operador</strong>: processamos esses
          dados exclusivamente em nome da igreja, seguindo suas instruções, e nunca os usamos
          para finalidade própria (como venda a terceiros ou publicidade).
        </p>
        <p>
          Já os dados da própria conta de acesso (login, senha, aceite de termos) têm o Domus
          como controlador.
        </p>

        <h2>3. Quais dados coletamos</h2>
        <ul>
          <li>Dados de cadastro: nome, e-mail, telefone, endereço, data de nascimento.</li>
          <li>Dados financeiros da igreja (movimentações, categorias) — não dados de cartão ou conta bancária pessoal.</li>
          <li>Fotos de pessoas e eventos, quando enviadas.</li>
          <li>Dados técnicos de acesso: e-mail de login, IP no momento do aceite dos Termos, registros de auditoria de ações no sistema.</li>
        </ul>

        <h2>4. Subprocessadores</h2>
        <p>Para funcionar, o Domus usa os seguintes serviços de terceiros, todos sob contrato:</p>
        <ul>
          <li><strong>Neon</strong> — banco de dados (armazenamento dos dados cadastrados).</li>
          <li><strong>Cloudflare R2</strong> — armazenamento de fotos, em bucket privado.</li>
          <li><strong>Resend</strong> — envio de e-mails transacionais (recuperação de senha, convites, avisos).</li>
          <li><strong>Google</strong> — autenticação via login com Google (OAuth), quando você escolhe esse método.</li>
          <li><strong>Elasticsearch</strong> — busca interna dos dados, auto-hospedado, sem compartilhamento externo.</li>
        </ul>

        <h2>5. Cookies</h2>
        <p>
          Usamos apenas cookies de sessão, estritamente necessários (<code>httpOnly</code>,
          nunca acessíveis por JavaScript): um para manter você autenticado e outro para renovar
          a sessão automaticamente. <strong>Não usamos cookies de rastreamento, analytics ou
          publicidade de terceiros.</strong>
        </p>

        <h2>6. Seus direitos (LGPD)</h2>
        <p>Como titular de dados, você tem direito a:</p>
        <ul>
          <li>Confirmar a existência de tratamento e acessar seus dados.</li>
          <li>Corrigir dados incompletos, inexatos ou desatualizados.</li>
          <li>
            <strong>Solicitar a eliminação</strong> dos seus dados — o Domus já implementa esse
            direito de forma concreta: o administrador da sua igreja pode excluir definitivamente
            seu cadastro a qualquer momento, e a própria igreja pode agendar a exclusão de toda
            a conta (com carência de 10 dias cancelável), apagando todos os dados de forma
            irreversível ao final do prazo.
          </li>
          <li>Revogar consentimento, quando aplicável.</li>
        </ul>
        <p>
          Para exercer esses direitos sobre dados cadastrados por uma igreja (ex.: seu cadastro
          como membro), procure o administrador dessa igreja — ele é o controlador desses dados.
          Para dados da sua própria conta de acesso, use as opções em Configurações ou fale com
          o suporte do Domus.
        </p>

        <h2>7. Segurança</h2>
        <p>
          Senhas são armazenadas com hash (nunca em texto puro), a sessão usa cookies
          <code>httpOnly</code> protegidos contra acesso via JavaScript, e o tráfego é sempre
          criptografado (HTTPS).
        </p>

        <h2>8. Mudanças nesta Política</h2>
        <p>
          Podemos atualizar esta Política. Mudanças relevantes exigem um novo aceite, pedido
          no seu próximo acesso.
        </p>

        <h2>9. Contato</h2>
        <p>
          Dúvidas sobre esta Política ou sobre seus dados? Fale com a gente pelo suporte do
          Domus. Veja também os <Link href="/termos">Termos de Uso</Link>.
        </p>
      </div>
    </div>
  )
}
```

- [ ] **Step 3: CSS compartilhado (mesmo conteúdo pros dois arquivos, ajustando o nome do arquivo)**

```css
.page {
  min-height: 100vh;
  padding: 40px 16px;
  background: var(--color-background, #f8f9fa);
}

.container {
  max-width: 720px;
  margin: 0 auto;
  background: var(--color-surface, #fff);
  padding: 40px;
  border-radius: 12px;
  line-height: 1.7;
}

.titulo {
  font-size: var(--font-size-2xl, 28px);
  font-weight: var(--font-weight-bold);
  margin-bottom: 4px;
}

.versao {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  margin-bottom: 32px;
}

.container h2 {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  margin-top: 28px;
  margin-bottom: 8px;
}

.container p,
.container ul {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: 12px;
}

.container ul {
  padding-left: 20px;
}

.container li {
  margin-bottom: 6px;
}

@media (max-width: 767px) {
  .container {
    padding: 24px 20px;
  }
}
```

- [ ] **Step 4: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 5: Testar manualmente no navegador**: abrir `/termos` e `/privacidade` diretamente, e a partir dos links do cadastro/modal — conferir que carregam e são legíveis em mobile.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/\(auth\)/termos/ frontend/src/app/\(auth\)/privacidade/
git commit -m "feat(front): páginas de Termos de Uso e Política de Privacidade"
```

---

### Task 12: Linha de transparência no perfil

**Files:**
- Modify: `frontend/src/app/(app)/perfil/page.tsx`

**Interfaces:**
- Consumes: `useAuthStore().termosAceitosEm` (Task 7).
- Produces: nada consumido por outras tasks — última task do plano.

- [ ] **Step 1: Adicionar a linha**

Em `perfil/page.tsx`, importar `useAuthStore` (já importado) e adicionar, próximo da seção de `AlterarSenhaForm` (ou em qualquer bloco visível de "dados da conta" já existente na página — o subagente confirma o layout real ao abrir o arquivo):

```tsx
  const termosAceitosEm = useAuthStore((s) => s.termosAceitosEm)
```

```tsx
      {termosAceitosEm && (
        <p style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-muted)' }}>
          Termos aceitos em{' '}
          {new Date(termosAceitosEm).toLocaleDateString('pt-BR', {
            day: '2-digit', month: '2-digit', year: 'numeric',
          })}
        </p>
      )}
```

- [ ] **Step 2: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 3: Testar manualmente no navegador**: abrir Perfil, conferir que a data aparece formatada (`DD/MM/AAAA`) e some se `termosAceitosEm` for `null` (caso teórico — na prática, depois do login/modal, sempre vai ter uma data).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(app\)/perfil/page.tsx
git commit -m "feat(front): mostra data do último aceite de termos no perfil"
```

> **Checkpoint final:** rodar `mvn -q -o test` (backend) e `npx tsc --noEmit` (frontend) uma última vez. Testar o fluxo ponta-a-ponta: cadastro nativo sem marcar checkbox (bloqueado no front) → marcando e enviando (400 se manipulado via curl sem `aceitouTermos`, 200 com) → cadastro Google (mesma coisa) → login numa conta antiga sem `termo_aceite` → modal bloqueante aparece → aceitar → app libera → perfil mostra a data.

---

## Self-Review

**1. Cobertura da spec:**
- Aceite ligado a `usuario` → tabela `termo_aceite.usuario_id`, Task 1.
- Versionado com reaceite → `TermosConstantes.VERSAO_ATUAL` + `precisaAceitar` (Task 2/3) + modal (Task 10).
- Texto no código do front, não CMS → Task 11, arquivos estáticos.
- Guarda IP → `TermoAceite.ip` + `ClienteIpResolver`, Task 1/2.
- Contas antigas tratadas como "desatualizada" → `precisaAceitar` retorna `true` quando `count < 2` (nunca teve registro cai no mesmo caminho de "versão errada"), sem código especial — confirmado na Task 3.
- Dois tipos separados → `TipoTermo` enum, `registrarAceite` grava sempre os dois juntos — Task 1/3.
- Só 2 endpoints exigem aceite síncrono (não 4) → Task 5, com a explicação de por que `reset-password`/`google/login` ficam de fora, igual à spec corrigida.
- `GET /auth/me` + login ganham `precisaAceitarTermos`/`termosAceitosEm` → Task 6.
- `POST /termos/aceitar` → Task 4.
- Transparência no perfil → Task 12.
- Conteúdo (LGPD controlador/operador, direito à eliminação referenciando features reais, subprocessadores, cookies) → Task 11, todos os pontos presentes no texto.
- Fora de escopo (CMS, reaceite seletivo por tipo) → nenhuma task implementa isso, consistente.

**2. Placeholder scan:** Task 5 e 6 pedem explicitamente ao subagente pra abrir o arquivo real e confirmar a ordem exata de campos/parâmetros do construtor `@RequiredArgsConstructor` antes de escrever a chamada no teste — isso não é um placeholder de lógica (a lógica e o código estão completos), é uma instrução de verificação porque a ordem exata de campos privados de duas classes (`IgrejaService`, `AuthService`) não foi confirmada char-a-char nesta investigação; a alternativa seria travar a plan numa suposição errada. Mantido de propósito, com a lógica plenamente especificada.

**3. Consistência de tipos:** `SessaoDTO`/`Sessao` ganham exatamente os mesmos dois campos nos dois lados (`precisaAceitarTermos: boolean`, `termosAceitosEm`). `RegistrarIgrejaAdminRequest.aceitouTermos` (back, boolean primitivo) ↔ `RegistrarIgrejaRequest.aceitouTermos` (front, boolean) — mesmo nome dos dois lados. `GoogleRegistrarDTO.aceitouTermos` ↔ `GoogleRegistrarRequest.aceitouTermos` — idem. `TermoAceiteService.precisaAceitar`/`dataUltimoAceite`/`registrarAceite`/`exigirAceite` usados com a mesma assinatura em todas as tasks que os chamam (3, 4, 5, 6). `ClienteIpResolver.resolver(HttpServletRequest)` usado com a mesma assinatura nas Tasks 4 e 5.

## Execution Handoff

Plano completo e salvo em `docs/superpowers/plans/2026-08-19-termos-uso-politica-privacidade.md`. Duas opções de execução:

**1. Subagent-Driven (recomendado)** — dispatco um subagente por task, revisando entre uma e outra.

**2. Inline Execution** — executo as tasks nesta sessão via executing-plans, com checkpoints pra revisão.

Qual prefere?
