# Central de Notificações Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar ao Domus um único lugar (sino no `TopBar` + tabela `notificacao`) onde qualquer
usuário vê o que aconteceu que é relevante pra ele — pedido de entrada em ministério, gente
nova na célula, acesso concedido, evento mudando, etc. — sem cada feature inventar o próprio
mecanismo de aviso.

**Architecture:** `NotificacaoService.criar(...)` é uma fachada única e sem estado de domínio
próprio — todo produtor (serviço que já existe: `MinisterioService`, `CelulaService`,
`UsuarioService`, `InscricaoService`, `EventoService`, `VinculoService`,
`ExclusaoIgrejaJob`) chama esse método, síncrono, na própria transação, no ponto onde o
evento de negócio já acontece. Sem fila, sem `@Async`, sem event listener — mesmo padrão que
`CacheEvictor.evictPorIgreja(...)` já usa no projeto. Frontend: polling via TanStack Query
(`refetchInterval`) pro contador, sem WebSocket.

**Tech Stack:** Spring Boot (JPA, Flyway), Next.js/TanStack Query, PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-08-20-central-notificacoes-design.md`

## Global Constraints

- Destinatário de notificação é sempre `usuario` (não `pessoa`) — só quem tem login recebe.
- Isolamento multi-tenant: toda notificação carrega `igreja_id`; toda consulta do usuário
  autenticado é escopada por `usuario_destinatario_id` do JWT, nunca por parâmetro do cliente.
- Se a pessoa/admin relevante não tiver `usuario` (login), o produtor simplesmente não gera
  notificação pra ela — sem erro, sem log de warning, é um caminho normal.
- Notificação de "evento mudou" só dispara quando `inicioEm`, `localId` ou `localTexto`
  mudarem de verdade — nunca em qualquer edição do evento.
- Notificação de "entrada na célula" nunca notifica a própria pessoa que acabou de entrar.
- Sem interface nova pro `NotificacaoService` — troca de implementação não é prevista aqui
  (decisão já documentada no spec e no `CLAUDE.md` do projeto: interface sem troca prevista é
  cerimônia).
- Frontend: o nome "Notificação" já é usado por `components/common/Notificacao/notificar.tsx`
  (toast de feedback de ação — sucesso/erro). Pra não colidir, todo arquivo novo desta feature
  usa **"notificações" no plural** ou o termo **"sino"**/"central" — nunca o singular
  "Notificacao" sozinho no frontend.

---

## Task 1: Migration, entidade, enum e repositório

**Files:**
- Create: `src/main/resources/db/migration/V21__notificacao.sql`
- Create: `src/main/java/com/domus/api/modules/notificacao/TipoNotificacao.java`
- Create: `src/main/java/com/domus/api/modules/notificacao/Notificacao.java`
- Create: `src/main/java/com/domus/api/modules/notificacao/NotificacaoRepository.java`
- Test: `src/test/java/com/domus/api/modules/notificacao/NotificacaoRepositoryTest.java`

**Interfaces:**
- Produces: `Notificacao` (entidade JPA), `TipoNotificacao` (enum), `NotificacaoRepository`
  com `Page<Notificacao> findByUsuarioDestinatarioId(UUID usuarioId, Pageable pageable)`,
  `long countByUsuarioDestinatarioIdAndLidaFalse(UUID usuarioId)`,
  `Optional<Notificacao> findByIdAndUsuarioDestinatarioId(UUID id, UUID usuarioId)`,
  `List<Notificacao> findByUsuarioDestinatarioIdAndLidaFalse(UUID usuarioId)`.

- [ ] **Step 1: Criar a migration**

```sql
CREATE TABLE notificacao (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id                UUID NOT NULL REFERENCES igreja(id),
    usuario_destinatario_id  UUID NOT NULL REFERENCES usuario(id),
    tipo                     VARCHAR(60) NOT NULL,
    texto                    VARCHAR(500) NOT NULL,
    link                     VARCHAR(255),
    lida                     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at               TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notificacao_destinatario ON notificacao (usuario_destinatario_id, lida, created_at DESC);
```

Salvar em `src/main/resources/db/migration/V21__notificacao.sql`.

- [ ] **Step 2: Criar o enum `TipoNotificacao`**

```java
package com.domus.api.modules.notificacao;

/** Um tipo por produtor. Extensível: adicionar produtor novo é uma entrada nova aqui — nunca
 *  editar NotificacaoService, banco ou frontend por causa de um tipo novo. */
public enum TipoNotificacao {
    PEDIDO_MINISTERIO,
    ENTRADA_CELULA,
    ACESSO_CONCEDIDO,
    INSCRICAO_EVENTO_RESPONSAVEL,
    PROMOVIDO_LIDER_CELULA,
    EVENTO_ALTERADO,
    PEDIDO_VINCULO_FAMILIA,
    EXCLUSAO_IGREJA_AGENDADA
}
```

- [ ] **Step 3: Criar a entidade `Notificacao`**

```java
package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notificacao")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_destinatario_id", nullable = false)
    private Usuario usuarioDestinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 60)
    private TipoNotificacao tipo;

    @Column(name = "texto", nullable = false, length = 500)
    private String texto;

    @Column(name = "link", length = 255)
    private String link;

    @Column(name = "lida", nullable = false)
    private boolean lida;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: Criar o repositório**

```java
package com.domus.api.modules.notificacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByUsuarioDestinatarioId(UUID usuarioId, Pageable pageable);

    long countByUsuarioDestinatarioIdAndLidaFalse(UUID usuarioId);

    Optional<Notificacao> findByIdAndUsuarioDestinatarioId(UUID id, UUID usuarioId);

    List<Notificacao> findByUsuarioDestinatarioIdAndLidaFalse(UUID usuarioId);
}
```

- [ ] **Step 5: Escrever o teste do repositório**

```java
package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificacaoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;

    Igreja igreja;
    Usuario usuario;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Notificação " + UUID.randomUUID())
                .emailContato("notif-" + UUID.randomUUID() + "@teste.com")
                .build());
        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano").vinculo(Vinculo.MEMBRO).build());
        Role role = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();
        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(role).ativo(true).build());
    }

    @Test
    void contaSoAsNaoLidasDoUsuarioCerto() {
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste 1").lida(false).build());
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste 2").lida(true).build());

        assertThat(notificacaoRepository.countByUsuarioDestinatarioIdAndLidaFalse(usuario.getId())).isEqualTo(1);
    }

    @Test
    void findByIdAndUsuarioDestinatarioId_naoAcertaNotificacaoDeOutroUsuario() {
        Notificacao salva = notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste").lida(false).build());

        assertThat(notificacaoRepository.findByIdAndUsuarioDestinatarioId(salva.getId(), UUID.randomUUID()))
                .isEmpty();
        assertThat(notificacaoRepository.findByIdAndUsuarioDestinatarioId(salva.getId(), usuario.getId()))
                .isPresent();
    }

    @Test
    void listaPaginadaOrdenaPorMaisRecente() {
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Primeira").lida(false).build());
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(usuario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Segunda").lida(false).build());

        var pagina = notificacaoRepository.findByUsuarioDestinatarioId(
                usuario.getId(), PageRequest.of(0, 10));

        assertThat(pagina.getTotalElements()).isEqualTo(2);
    }
}
```

- [ ] **Step 6: Rodar o teste**

Run: `./mvnw -q -o test -Dtest=NotificacaoRepositoryTest`
Expected: PASS (3 testes) — o Testcontainers sobe o Postgres e a migration V21 roda sozinha.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V21__notificacao.sql \
        src/main/java/com/domus/api/modules/notificacao/ \
        src/test/java/com/domus/api/modules/notificacao/NotificacaoRepositoryTest.java
git commit -m "feat(notificacao): entidade, enum e repositorio da central de notificacoes"
```

---

## Task 2: `NotificacaoService`

**Files:**
- Create: `src/main/java/com/domus/api/modules/notificacao/NotificacaoService.java`
- Create: `src/main/java/com/domus/api/modules/notificacao/DTO/NotificacaoResponse.java`
- Test: `src/test/java/com/domus/api/modules/notificacao/NotificacaoServiceTest.java`

**Interfaces:**
- Consumes: `Notificacao`, `TipoNotificacao`, `NotificacaoRepository` (Task 1),
  `com.domus.api.modules.igreja.IgrejaRepository` (já existe, tem `getReferenceById`),
  `com.domus.api.modules.usuario.UsuarioRepository` (já existe, tem `getReferenceById`).
- Produces: `NotificacaoService.criar(TipoNotificacao tipo, UUID igrejaId, UUID usuarioDestinatarioId, String texto, String link)`,
  `NotificacaoService.listar(UUID usuarioId, Pageable pageable)` → `PagedResponse<NotificacaoResponse>`,
  `NotificacaoService.contarNaoLidas(UUID usuarioId)` → `long`,
  `NotificacaoService.marcarComoLida(UUID id, UUID usuarioId)`,
  `NotificacaoService.marcarTodasComoLidas(UUID usuarioId)`.
  Todos os produtores das Tasks 4–11 chamam `criar(...)`.

- [ ] **Step 1: Escrever o DTO de resposta**

```java
package com.domus.api.modules.notificacao.DTO;

import com.domus.api.modules.notificacao.Notificacao;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificacaoResponse(
        UUID id,
        String tipo,
        String texto,
        String link,
        boolean lida,
        LocalDateTime criadoEm
) {
    public static NotificacaoResponse from(Notificacao n) {
        return new NotificacaoResponse(
                n.getId(), n.getTipo().name(), n.getTexto(), n.getLink(), n.isLida(), n.getCreatedAt());
    }
}
```

- [ ] **Step 2: Escrever o teste do serviço (Mockito puro)**

```java
package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificacaoServiceTest {

    NotificacaoRepository notificacaoRepository;
    IgrejaRepository igrejaRepository;
    UsuarioRepository usuarioRepository;
    NotificacaoService service;

    @BeforeEach
    void setup() {
        notificacaoRepository = mock(NotificacaoRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        service = new NotificacaoService(notificacaoRepository, igrejaRepository, usuarioRepository);
    }

    @Test
    void criarGravaNotificacaoComOsCamposCertos() {
        UUID igrejaId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Igreja igreja = Igreja.builder().id(igrejaId).build();
        Usuario usuario = Usuario.builder().id(usuarioId).build();
        when(igrejaRepository.getReferenceById(igrejaId)).thenReturn(igreja);
        when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(usuario);

        service.criar(TipoNotificacao.ACESSO_CONCEDIDO, igrejaId, usuarioId, "Você recebeu acesso.", "/inicio");

        verify(notificacaoRepository).save(argThat(n ->
                n.getTipo() == TipoNotificacao.ACESSO_CONCEDIDO
                        && n.getTexto().equals("Você recebeu acesso.")
                        && n.getLink().equals("/inicio")
                        && !n.isLida()
                        && n.getIgreja() == igreja
                        && n.getUsuarioDestinatario() == usuario));
    }

    @Test
    void contarNaoLidasDelegaPraRepository() {
        UUID usuarioId = UUID.randomUUID();
        when(notificacaoRepository.countByUsuarioDestinatarioIdAndLidaFalse(usuarioId)).thenReturn(3L);

        assertThat(service.contarNaoLidas(usuarioId)).isEqualTo(3L);
    }

    @Test
    void listarMapeiaPaginaParaPagedResponse() {
        UUID usuarioId = UUID.randomUUID();
        Notificacao n = Notificacao.builder()
                .id(UUID.randomUUID()).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste").lida(false).build();
        when(notificacaoRepository.findByUsuarioDestinatarioId(eq(usuarioId), any()))
                .thenReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 10), 1));

        PagedResponse<com.domus.api.modules.notificacao.DTO.NotificacaoResponse> resposta =
                service.listar(usuarioId, PageRequest.of(0, 10));

        assertThat(resposta.getContent()).hasSize(1);
        assertThat(resposta.getContent().get(0).texto()).isEqualTo("Teste");
    }

    @Test
    void marcarComoLida_soMarcaSeForDoUsuarioCerto() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        Notificacao n = Notificacao.builder().id(id).lida(false).build();
        when(notificacaoRepository.findByIdAndUsuarioDestinatarioId(id, usuarioId)).thenReturn(Optional.of(n));

        service.marcarComoLida(id, usuarioId);

        assertThat(n.isLida()).isTrue();
        verify(notificacaoRepository).save(n);
    }

    @Test
    void marcarComoLida_naoFazNadaSeNaoForDoUsuario() {
        UUID id = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        when(notificacaoRepository.findByIdAndUsuarioDestinatarioId(id, usuarioId)).thenReturn(Optional.empty());

        service.marcarComoLida(id, usuarioId);

        verify(notificacaoRepository, never()).save(any());
    }

    @Test
    void marcarTodasComoLidasMarcaTodasAsNaoLidasDoUsuario() {
        UUID usuarioId = UUID.randomUUID();
        Notificacao n1 = Notificacao.builder().id(UUID.randomUUID()).lida(false).build();
        Notificacao n2 = Notificacao.builder().id(UUID.randomUUID()).lida(false).build();
        when(notificacaoRepository.findByUsuarioDestinatarioIdAndLidaFalse(usuarioId)).thenReturn(List.of(n1, n2));

        service.marcarTodasComoLidas(usuarioId);

        assertThat(n1.isLida()).isTrue();
        assertThat(n2.isLida()).isTrue();
        verify(notificacaoRepository).saveAll(List.of(n1, n2));
    }
}
```

- [ ] **Step 3: Rodar e ver falhar (classe não existe ainda)**

Run: `./mvnw -q -o test -Dtest=NotificacaoServiceTest`
Expected: FAIL (compilação — `NotificacaoService` não existe)

- [ ] **Step 4: Implementar `NotificacaoService`**

```java
package com.domus.api.modules.notificacao;

import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.notificacao.DTO.NotificacaoResponse;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.DTO.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Fachada única de notificação in-app. Todo produtor (MinisterioService, CelulaService,
 *  UsuarioService, InscricaoService, EventoService, VinculoService, ExclusaoIgrejaJob) chama
 *  {@link #criar} no ponto onde o evento de negócio já acontece — síncrono, mesma transação
 *  do produtor, sem fila. Não conhece nem precisa conhecer célula/ministério/evento: quem
 *  monta o texto e resolve o(s) destinatário(s) é sempre o produtor. */
@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public void criar(TipoNotificacao tipo, UUID igrejaId, UUID usuarioDestinatarioId, String texto, String link) {
        notificacaoRepository.save(Notificacao.builder()
                .igreja(igrejaRepository.getReferenceById(igrejaId))
                .usuarioDestinatario(usuarioRepository.getReferenceById(usuarioDestinatarioId))
                .tipo(tipo)
                .texto(texto)
                .link(link)
                .lida(false)
                .build());
    }

    @Transactional(readOnly = true)
    public PagedResponse<NotificacaoResponse> listar(UUID usuarioId, Pageable pageable) {
        Page<NotificacaoResponse> pagina = notificacaoRepository
                .findByUsuarioDestinatarioId(usuarioId, pageable)
                .map(NotificacaoResponse::from);
        return PagedResponse.from(pagina);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID usuarioId) {
        return notificacaoRepository.countByUsuarioDestinatarioIdAndLidaFalse(usuarioId);
    }

    @Transactional
    public void marcarComoLida(UUID id, UUID usuarioId) {
        notificacaoRepository.findByIdAndUsuarioDestinatarioId(id, usuarioId).ifPresent(n -> {
            n.setLida(true);
            notificacaoRepository.save(n);
        });
    }

    @Transactional
    public void marcarTodasComoLidas(UUID usuarioId) {
        List<Notificacao> naoLidas = notificacaoRepository.findByUsuarioDestinatarioIdAndLidaFalse(usuarioId);
        naoLidas.forEach(n -> n.setLida(true));
        notificacaoRepository.saveAll(naoLidas);
    }
}
```

Checar se `PagedResponse.from(Page<T>)` já existe (usado em outros services) — se sim, é isso
que este método usa; confirmado em `src/main/java/com/domus/api/shared/DTO/PagedResponse.java`.

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw -q -o test -Dtest=NotificacaoServiceTest`
Expected: PASS (6 testes)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/notificacao/NotificacaoService.java \
        src/main/java/com/domus/api/modules/notificacao/DTO/NotificacaoResponse.java \
        src/test/java/com/domus/api/modules/notificacao/NotificacaoServiceTest.java
git commit -m "feat(notificacao): NotificacaoService, fachada unica pros produtores"
```

---

## Task 3: `NotificacaoController` (API)

**Files:**
- Create: `src/main/java/com/domus/api/modules/notificacao/NotificacaoController.java`
- Test: `src/test/java/com/domus/api/modules/notificacao/NotificacaoControllerTest.java`

**Interfaces:**
- Consumes: `NotificacaoService` (Task 2), `com.domus.api.shared.security.UsuarioAutenticado`
  (já existe, `getUsuarioId()`), `com.domus.api.shared.security.AutenticacaoTestSupport` (já
  existe, pro teste).
- Produces: `GET /notificacoes`, `GET /notificacoes/contagem-nao-lidas`,
  `PATCH /notificacoes/{id}/lida`, `PATCH /notificacoes/lidas`.

- [ ] **Step 1: Implementar o controller**

```java
package com.domus.api.modules.notificacao;

import com.domus.api.shared.DTO.PagedResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notificacoes")
@RequiredArgsConstructor
public class NotificacaoController {

    private final NotificacaoService notificacaoService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public PagedResponse<com.domus.api.modules.notificacao.DTO.NotificacaoResponse> listar(
            @PageableDefault(size = 20) Pageable pageable) {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        Pageable ordenado = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificacaoService.listar(usuarioId, ordenado);
    }

    @GetMapping("/contagem-nao-lidas")
    public Map<String, Long> contagemNaoLidas() {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        return Map.of("total", notificacaoService.contarNaoLidas(usuarioId));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarComoLida(@PathVariable UUID id) {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        notificacaoService.marcarComoLida(id, usuarioId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarTodasComoLidas() {
        UUID usuarioId = usuarioAutenticado.getUsuarioId();
        notificacaoService.marcarTodasComoLidas(usuarioId);
        return ResponseEntity.noContent().build();
    }
}
```

Nada a mudar em `SecurityConfig`: nenhum `requestMatchers` cobre `/notificacoes/**`, então cai
no `.anyRequest().authenticated()` final — qualquer perfil logado acessa, e o isolamento por
usuário já é garantido dentro do `NotificacaoService` (Task 2), não por role.

- [ ] **Step 2: Escrever o teste do controller**

```java
package com.domus.api.modules.notificacao;

import com.domus.api.config.TokenService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.security.AutenticacaoTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificacaoControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired TokenService tokenService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired EntityManager entityManager;

    AutenticacaoTestSupport auth;
    Igreja igreja;
    Usuario dono;
    Usuario outroUsuario;

    @BeforeEach
    void setup() {
        auth = new AutenticacaoTestSupport(tokenService);
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Notificação " + UUID.randomUUID())
                .emailContato("notif-ctrl-" + UUID.randomUUID() + "@teste.com")
                .build());
        Role role = roleRepository.findByNome("ACESSO_COMUM").orElseThrow();

        Pessoa pessoaDono = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Dono").vinculo(Vinculo.MEMBRO).build());
        dono = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoaDono).role(role).ativo(true).build());

        Pessoa pessoaOutro = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Outro").vinculo(Vinculo.MEMBRO).build());
        outroUsuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoaOutro).role(role).ativo(true).build());

        entityManager.flush();
    }

    private Notificacao notificacaoDe(Usuario destinatario) {
        Notificacao n = notificacaoRepository.save(Notificacao.builder()
                .igreja(igreja).usuarioDestinatario(destinatario).tipo(TipoNotificacao.ACESSO_CONCEDIDO)
                .texto("Teste").lida(false).build());
        entityManager.flush();
        return n;
    }

    @Test
    void listarSoTrazNotificacaoDoProprioUsuario() throws Exception {
        notificacaoDe(dono);
        notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(get("/notificacoes"), dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void contagemNaoLidasContaSoDoUsuarioAutenticado() throws Exception {
        notificacaoDe(dono);
        notificacaoDe(dono);
        notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(get("/notificacoes/contagem-nao-lidas"), dono))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void marcarComoLida_naoDeixaMarcarNotificacaoDeOutroUsuario() throws Exception {
        Notificacao doOutro = notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(patch("/notificacoes/" + doOutro.getId() + "/lida"), dono))
                .andExpect(status().isNoContent());

        entityManager.clear();
        Notificacao recarregada = notificacaoRepository.findById(doOutro.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(recarregada.isLida())
                .as("dono não pode marcar como lida uma notificação que não é dele")
                .isFalse();
    }

    @Test
    void marcarTodasComoLidasSoAfetaAsDoUsuarioAutenticado() throws Exception {
        notificacaoDe(dono);
        Notificacao doOutro = notificacaoDe(outroUsuario);

        mockMvc.perform(auth.autenticado(patch("/notificacoes/lidas"), dono))
                .andExpect(status().isNoContent());

        entityManager.clear();
        Notificacao recarregada = notificacaoRepository.findById(doOutro.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(recarregada.isLida()).isFalse();
    }
}
```

- [ ] **Step 3: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=NotificacaoControllerTest`
Expected: PASS (4 testes)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/notificacao/NotificacaoController.java \
        src/test/java/com/domus/api/modules/notificacao/NotificacaoControllerTest.java
git commit -m "feat(notificacao): API GET/PATCH da central de notificacoes"
```

---

## Task 4: Produtor — acesso concedido / convite aceito

**Files:**
- Modify: `src/main/java/com/domus/api/modules/usuario/UsuarioService.java`
- Test: `src/test/java/com/domus/api/modules/usuario/UsuarioServiceConviteTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)` (Task 2), `TipoNotificacao.ACESSO_CONCEDIDO`.

- [ ] **Step 1: Injetar `NotificacaoService` e chamar `criar` em `concederAcesso` e `reativarAcesso`**

Adicionar o campo (perto de `cacheEvictor`):

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

Em `concederAcesso`, logo após `cacheEvictor.evictPorIgreja("usuarios", igrejaId);` e antes do
`return UsuarioResponseDTO.from(salvo);`:

```java
        notificacaoService.criar(
                com.domus.api.modules.notificacao.TipoNotificacao.ACESSO_CONCEDIDO,
                igrejaId, salvo.getId(),
                "Você recebeu acesso ao Domus da igreja " + membro.getIgreja().getNome() + ".",
                "/inicio");
```

Em `reativarAcesso`, no mesmo ponto (depois do `cacheEvictor.evictPorIgreja("usuarios", igrejaId);`
que já existe ali, antes do `return`):

```java
        notificacaoService.criar(
                com.domus.api.modules.notificacao.TipoNotificacao.ACESSO_CONCEDIDO,
                igrejaId, salvo.getId(),
                "Seu acesso ao Domus foi reativado.",
                "/inicio");
```

- [ ] **Step 2: Escrever os testes (Mockito puro, mesmo arquivo de teste existente)**

Adicionar em `UsuarioServiceConviteTest.java` (o arquivo já mocka `CacheEvictor` — seguir o
mesmo padrão pro novo mock):

```java
    @Test
    void concederAcessoNotificaOUsuarioNovo() {
        // arrange: mesmo setup de pessoa/role já usado nos outros testes deste arquivo
        ConcederAcessoRequestDTO data = new ConcederAcessoRequestDTO(pessoa.getId(), "ADMIN_IGREJA", null);
        when(membroRepository.findByIdAndIgrejaId(pessoa.getId(), igrejaId)).thenReturn(Optional.of(pessoa));
        when(usuarioRepository.findByPessoaIdIncluindoArquivados(pessoa.getId())).thenReturn(Optional.empty());
        when(roleRepository.findByNome("ADMIN_IGREJA")).thenReturn(Optional.of(role));
        when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.concederAcesso(data, igrejaId);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.ACESSO_CONCEDIDO), eq(igrejaId), any(), anyString(), eq("/inicio"));
    }
```

Ajustar o `@BeforeEach` do arquivo (ou o construtor de `service`) pra incluir o novo mock
`notificacaoService = mock(NotificacaoService.class);` na lista de parâmetros de
`new UsuarioService(...)` — seguir a ordem de campos declarados em `UsuarioService.java`.

- [ ] **Step 3: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=UsuarioServiceConviteTest,UsuarioServiceCapacidadeTest`
Expected: PASS — o segundo arquivo (`UsuarioServiceCapacidadeTest`) também instancia
`UsuarioService`, então precisa do mock novo no construtor também.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/usuario/UsuarioService.java \
        src/test/java/com/domus/api/modules/usuario/UsuarioServiceConviteTest.java \
        src/test/java/com/domus/api/modules/usuario/UsuarioServiceCapacidadeTest.java
git commit -m "feat(notificacao): notifica usuario quando acesso e concedido/reativado"
```

---

## Task 5: Produtor — pedido de entrada em ministério

**Files:**
- Modify: `src/main/java/com/domus/api/modules/ministerio/MinisterioMembroRepository.java`
- Modify: `src/main/java/com/domus/api/modules/ministerio/MinisterioService.java`
- Test: `src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.PEDIDO_MINISTERIO`.
- Produces: `MinisterioMembroRepository.findByMinisterioIdAndPapelAndStatus(UUID, String, String)`.

- [ ] **Step 1: Adicionar o método no repositório**

Em `MinisterioMembroRepository.java`, junto dos outros métodos nativos (mesmo motivo de
`findByMinisterioIdOrderByPapelAsc`: derived query aqui vazaria o `@SQLRestriction` de
`Ministerio`):

```java
    /** Nativa pelo mesmo motivo de findByMinisterioIdOrderByPapelAsc. */
    @Query(value = "SELECT * FROM ministerio_membro WHERE ministerio_id = :ministerioId AND papel = :papel AND status = :status",
           nativeQuery = true)
    List<MinisterioMembro> findByMinisterioIdAndPapelAndStatus(
            @Param("ministerioId") UUID ministerioId, @Param("papel") String papel, @Param("status") String status);
```

- [ ] **Step 2: Injetar `NotificacaoService` e notificar líderes em `pedirEntrada`**

Adicionar o campo em `MinisterioService`:

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

Em `pedirEntrada`, depois de `membroRepository.save(MinisterioMembro.builder()...build());`:

```java
        List<MinisterioMembro> lideres = membroRepository.findByMinisterioIdAndPapelAndStatus(
                ministerioId, Papel.LIDER.name(), StatusMembro.ATIVO.name());
        for (MinisterioMembro lider : lideres) {
            usuarioRepository.findByPessoaId(lider.getPessoa().getId()).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.PEDIDO_MINISTERIO,
                            igrejaId, usuario.getId(),
                            pessoa.getNome() + " pediu pra entrar em " + ministerio.getNome() + ".",
                            "/ministerios/" + ministerioId));
        }
```

- [ ] **Step 3: Escrever o teste (Mockito puro, seguindo o padrão do arquivo existente)**

```java
    @Test
    void pedirEntradaNotificaTodosOsLideresAtivos() {
        UUID pessoaIdSolicitante = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(pessoaIdSolicitante).nome("Fulano").build();
        Ministerio ministerio = Ministerio.builder().id(ministerioId).igreja(igreja).nome("Louvor").build();
        Pessoa pessoaLider = Pessoa.builder().id(UUID.randomUUID()).build();
        MinisterioMembro liderMembro = MinisterioMembro.builder().pessoa(pessoaLider).papel(Papel.LIDER).build();
        Usuario usuarioLider = Usuario.builder().id(UUID.randomUUID()).build();

        when(ministerioRepository.findByIdAndIgrejaId(ministerioId, igrejaId)).thenReturn(Optional.of(ministerio));
        when(membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaIdSolicitante)).thenReturn(Optional.empty());
        when(pessoaRepository.findByIdAndIgrejaId(pessoaIdSolicitante, igrejaId)).thenReturn(Optional.of(pessoa));
        when(membroRepository.findByMinisterioIdAndPapelAndStatus(ministerioId, "LIDER", "ATIVO"))
                .thenReturn(List.of(liderMembro));
        when(usuarioRepository.findByPessoaId(pessoaLider.getId())).thenReturn(Optional.of(usuarioLider));

        service.pedirEntrada(ministerioId, pessoaIdSolicitante, igrejaId);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.PEDIDO_MINISTERIO), eq(igrejaId), eq(usuarioLider.getId()),
                anyString(), eq("/ministerios/" + ministerioId));
    }

    @Test
    void pedirEntradaNaoQuebraSeLiderNaoTemUsuario() {
        UUID pessoaIdSolicitante = UUID.randomUUID();
        Pessoa pessoa = Pessoa.builder().id(pessoaIdSolicitante).nome("Fulano").build();
        Ministerio ministerio = Ministerio.builder().id(ministerioId).igreja(igreja).nome("Louvor").build();
        Pessoa pessoaLider = Pessoa.builder().id(UUID.randomUUID()).build();
        MinisterioMembro liderMembro = MinisterioMembro.builder().pessoa(pessoaLider).papel(Papel.LIDER).build();

        when(ministerioRepository.findByIdAndIgrejaId(ministerioId, igrejaId)).thenReturn(Optional.of(ministerio));
        when(membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaIdSolicitante)).thenReturn(Optional.empty());
        when(pessoaRepository.findByIdAndIgrejaId(pessoaIdSolicitante, igrejaId)).thenReturn(Optional.of(pessoa));
        when(membroRepository.findByMinisterioIdAndPapelAndStatus(ministerioId, "LIDER", "ATIVO"))
                .thenReturn(List.of(liderMembro));
        when(usuarioRepository.findByPessoaId(pessoaLider.getId())).thenReturn(Optional.empty());

        service.pedirEntrada(ministerioId, pessoaIdSolicitante, igrejaId);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }
```

Ajustar o construtor de `service` no `@BeforeEach` do arquivo (ou onde for instanciado) pra
incluir `notificacaoService = mock(NotificacaoService.class);`. Conferir se `ministerioId`,
`igreja`/`igrejaId` já existem como campos de classe no arquivo (o padrão do projeto declara
esses UUIDs no topo da classe de teste) — reusar, não recriar.

- [ ] **Step 4: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=MinisterioServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/ministerio/MinisterioMembroRepository.java \
        src/main/java/com/domus/api/modules/ministerio/MinisterioService.java \
        src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java
git commit -m "feat(notificacao): notifica lideres de ministerio quando alguem pede entrada"
```

---

## Task 6: Produtor — pessoa/visitante entra na célula

**Files:**
- Modify: `src/main/java/com/domus/api/modules/celula/CelulaService.java`
- Test: `src/test/java/com/domus/api/modules/celula/CelulaServiceTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.ENTRADA_CELULA`,
  `CelulaMembroRepository.findByCelulaIdAndPessoaIdIsNotNull(UUID)` (já existe).

- [ ] **Step 1: Injetar `NotificacaoService`**

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

- [ ] **Step 2: Fazer `adicionarPessoa` devolver a `Pessoa` e adicionar o helper de notificação**

Trocar a assinatura de `adicionarPessoa` de `void` pra `Pessoa` (só o `return pessoa;` no fim
de cada caminho):

```java
    private Pessoa adicionarPessoa(Celula celula, UUID pessoaId, UUID igrejaId, UUID usuarioId) {
        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        CelulaMembro existente = membroRepository.findByPessoaId(pessoaId).orElse(null);
        if (existente != null) {
            existente.setCelula(celula);
            membroRepository.save(existente);
            return pessoa;
        }

        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;
        membroRepository.save(CelulaMembro.builder()
                .igreja(celula.getIgreja()).celula(celula).pessoa(pessoa)
                .criadoPor(usuario).atualizadoPor(usuario).build());
        return pessoa;
    }
```

Adicionar o helper de notificação (perto de `adicionarPessoa`/`adicionarVisitante`):

```java
    /** Notifica todo mundo que já está na célula, exceto quem acabou de entrar. */
    private void notificarEntradaNaCelula(Celula celula, UUID igrejaId, String nomeEntrante, UUID pessoaIdEntranteOuNull) {
        List<CelulaMembro> membros = membroRepository.findByCelulaIdAndPessoaIdIsNotNull(celula.getId());
        for (CelulaMembro membro : membros) {
            UUID pessoaIdMembro = membro.getPessoa().getId();
            if (pessoaIdMembro.equals(pessoaIdEntranteOuNull)) continue;
            usuarioRepository.findByPessoaId(pessoaIdMembro).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.ENTRADA_CELULA,
                            igrejaId, usuario.getId(),
                            nomeEntrante + " entrou na célula " + celula.getNome() + ".",
                            "/celulas/" + celula.getId()));
        }
    }
```

- [ ] **Step 3: Chamar o helper em `adicionarMembro`**

```java
    @Transactional
    public void adicionarMembro(UUID celulaId, AdicionarMembroCelulaRequest data,
                                 UUID igrejaId, UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        exigirAdminOuLider(celulaId, atorPessoaId, isAdmin);

        if (data.pessoaId() != null) {
            Pessoa pessoa = adicionarPessoa(celula, data.pessoaId(), igrejaId, usuarioId);
            notificarEntradaNaCelula(celula, igrejaId, pessoa.getNome(), pessoa.getId());
        } else if (data.visitanteId() != null) {
            adicionarVisitante(celula, data.visitanteId(), igrejaId, usuarioId);

            Visitante v = visitanteRepository.findByIdAndIgrejaId(data.visitanteId(), igrejaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));
            if (v.getEntrouEmCelulaEm() == null) {
                v.setEntrouEmCelulaEm(LocalDateTime.now());
                visitanteRepository.save(v);
            }
            notificarEntradaNaCelula(celula, igrejaId, v.getNome(), null);
        } else {
            throw new BusinessException("MEMBRO_INVALIDO",
                    "Informe pessoaId ou visitanteId.");
        }
    }
```

- [ ] **Step 4: Escrever os testes**

```java
    @Test
    void adicionarPessoaNotificaOsOutrosMembrosMasNaoQuemEntrou() {
        UUID membroExistentePessoaId = UUID.randomUUID();
        UUID membroExistenteUsuarioId = UUID.randomUUID();
        UUID pessoaNovaId = UUID.randomUUID();
        UUID usuarioIdAtor = UUID.randomUUID();

        Celula celula = Celula.builder().id(celulaId).igreja(igreja).nome("Célula Central").build();
        Pessoa pessoaNova = Pessoa.builder().id(pessoaNovaId).nome("Novato").build();
        CelulaMembro membroExistente = CelulaMembro.builder()
                .pessoa(Pessoa.builder().id(membroExistentePessoaId).build()).build();
        Usuario usuarioMembroExistente = Usuario.builder().id(membroExistenteUsuarioId).build();
        AdicionarMembroCelulaRequest data = new AdicionarMembroCelulaRequest(pessoaNovaId, null);

        when(celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId)).thenReturn(Optional.of(celula));
        when(pessoaRepository.findByIdAndIgrejaId(pessoaNovaId, igrejaId)).thenReturn(Optional.of(pessoaNova));
        when(membroRepository.findByPessoaId(pessoaNovaId)).thenReturn(Optional.empty());
        when(membroRepository.findByCelulaIdAndPessoaIdIsNotNull(celulaId))
                .thenReturn(List.of(membroExistente));
        when(usuarioRepository.findByPessoaId(membroExistentePessoaId)).thenReturn(Optional.of(usuarioMembroExistente));

        service.adicionarMembro(celulaId, data, igrejaId, UUID.randomUUID(), true, usuarioIdAtor);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.ENTRADA_CELULA), eq(igrejaId), eq(membroExistenteUsuarioId),
                anyString(), eq("/celulas/" + celulaId));
        // A pessoa que acabou de entrar nunca é destinatária da própria notificação.
        verify(notificacaoService, never()).criar(any(), any(), eq(pessoaNovaId), anyString(), anyString());
    }
```

Ajustar `exigirAdminOuLider`/mocks já usados no arquivo pra este teste passar da checagem de
permissão (seguir o padrão dos outros testes de `adicionarMembro` já existentes no arquivo —
provavelmente já mockam `celulaRepository`/liderança da mesma forma). Ajustar o construtor de
`service` pra incluir `notificacaoService = mock(NotificacaoService.class);`.

- [ ] **Step 5: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=CelulaServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/celula/CelulaService.java \
        src/test/java/com/domus/api/modules/celula/CelulaServiceTest.java
git commit -m "feat(notificacao): notifica membros da celula quando chega gente nova"
```

---

## Task 7: Produtor — promovido a líder de célula

**Files:**
- Modify: `src/main/java/com/domus/api/modules/celula/CelulaService.java`
- Test: `src/test/java/com/domus/api/modules/celula/CelulaServiceTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.PROMOVIDO_LIDER_CELULA`.

- [ ] **Step 1: Capturar a célula e detectar a promoção em `atualizarPapel`**

```java
    @Transactional
    public void atualizarPapel(UUID celulaId, UUID membroId, AtualizarPapelCelulaRequest data,
                                UUID igrejaId, boolean isAdmin) {
        Celula celula = buscarDaIgrejaOuFalhar(celulaId, igrejaId);
        if (!isAdmin) {
            throw new AccessDeniedException(
                    "Só um administrador pode promover ou rebaixar líder de célula.");
        }

        CelulaMembro membro = membroRepository.findById(membroId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));
        if (membro.getVisitante() != null) {
            throw new BusinessException("VISITANTE_NAO_PODE_SER_LIDER",
                    "Um visitante não pode ser promovido a líder de célula.");
        }
        boolean vaiVirarLider = data.papel() == PapelCelula.LIDER && membro.getPapel() != PapelCelula.LIDER;
        membro.setPapel(data.papel());
        membroRepository.save(membro);

        if (vaiVirarLider) {
            usuarioRepository.findByPessoaId(membro.getPessoa().getId()).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.PROMOVIDO_LIDER_CELULA,
                            igrejaId, usuario.getId(),
                            "Você foi promovido a líder da célula " + celula.getNome() + ".",
                            "/celulas/" + celula.getId()));
        }
    }
```

- [ ] **Step 2: Escrever os testes**

```java
    @Test
    void promoverParaLiderNotificaAPessoaPromovida() {
        UUID pessoaIdPromovida = UUID.randomUUID();
        UUID usuarioIdPromovido = UUID.randomUUID();
        Celula celula = Celula.builder().id(celulaId).igreja(igreja).nome("Célula Central").build();
        CelulaMembro membro = CelulaMembro.builder()
                .id(membroId).pessoa(Pessoa.builder().id(pessoaIdPromovida).build())
                .papel(PapelCelula.MEMBRO).build();
        AtualizarPapelCelulaRequest data = new AtualizarPapelCelulaRequest(PapelCelula.LIDER);

        when(celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId)).thenReturn(Optional.of(celula));
        when(membroRepository.findById(membroId)).thenReturn(Optional.of(membro));
        when(usuarioRepository.findByPessoaId(pessoaIdPromovida))
                .thenReturn(Optional.of(Usuario.builder().id(usuarioIdPromovido).build()));

        service.atualizarPapel(celulaId, membroId, data, igrejaId, true);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.PROMOVIDO_LIDER_CELULA), eq(igrejaId), eq(usuarioIdPromovido),
                anyString(), eq("/celulas/" + celulaId));
    }

    @Test
    void naoNotificaQuandoJaEraLiderEContinuaLider() {
        UUID pessoaIdLider = UUID.randomUUID();
        Celula celula = Celula.builder().id(celulaId).igreja(igreja).nome("Célula Central").build();
        CelulaMembro membro = CelulaMembro.builder()
                .id(membroId).pessoa(Pessoa.builder().id(pessoaIdLider).build())
                .papel(PapelCelula.LIDER).build();
        AtualizarPapelCelulaRequest data = new AtualizarPapelCelulaRequest(PapelCelula.LIDER);

        when(celulaRepository.findByIdAndIgrejaId(celulaId, igrejaId)).thenReturn(Optional.of(celula));
        when(membroRepository.findById(membroId)).thenReturn(Optional.of(membro));

        service.atualizarPapel(celulaId, membroId, data, igrejaId, true);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }
```

- [ ] **Step 3: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=CelulaServiceTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/celula/CelulaService.java \
        src/test/java/com/domus/api/modules/celula/CelulaServiceTest.java
git commit -m "feat(notificacao): notifica pessoa promovida a lider de celula"
```

---

## Task 8: Produtor — inscrição em evento que você é responsável

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.INSCRICAO_EVENTO_RESPONSAVEL`.

Escopo desta task: só o método `inscrever` (auto-inscrição e inscrição individual feita por
admin/líder) — `inscreverPessoas` (inscrição em lote) fica de fora da v1, não pediu.

- [ ] **Step 1: Injetar `NotificacaoService`**

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

- [ ] **Step 2: Notificar o responsável em `inscrever`**

Depois de `InscricaoEvento salva = inscricaoRepository.save(inscricao);` e antes do `log.info`:

```java
        if (evento.getResponsavel() != null && !evento.getResponsavel().getId().equals(pessoaId)) {
            usuarioRepository.findByPessoaId(evento.getResponsavel().getId()).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.INSCRICAO_EVENTO_RESPONSAVEL,
                            igrejaId, usuario.getId(),
                            membro.getNome() + " se inscreveu em " + evento.getTitulo() + ".",
                            "/eventos/" + eventoId));
        }
```

(`membro` aqui é a variável `Pessoa` já resolvida no início do método — a pessoa que está se
inscrevendo, não confundir com o `evento.getResponsavel()`.)

- [ ] **Step 3: Escrever os testes**

```java
    @Test
    void inscreverNotificaOResponsavelDoEvento() {
        UUID pessoaIdResponsavel = UUID.randomUUID();
        UUID usuarioIdResponsavel = UUID.randomUUID();
        Pessoa responsavel = Pessoa.builder().id(pessoaIdResponsavel).build();
        Evento evento = evento(null);
        evento.setResponsavel(responsavel);
        Pessoa pessoaInscrita = pessoa(Vinculo.CONGREGANTE);

        dado(evento, pessoaInscrita, 0);
        when(usuarioRepository.findByPessoaId(pessoaIdResponsavel))
                .thenReturn(Optional.of(Usuario.builder().id(usuarioIdResponsavel).build()));

        service.inscrever(eventoId, pessoaInscrita.getId(), null, pessoaInscrita.getId(), "ACESSO_COMUM", false, igrejaId);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.INSCRICAO_EVENTO_RESPONSAVEL), eq(igrejaId), eq(usuarioIdResponsavel),
                anyString(), eq("/eventos/" + eventoId));
    }

    @Test
    void inscreverNaoNotificaQuandoResponsavelInscreveASiMesmo() {
        Evento evento = evento(null);
        Pessoa responsavelQueTambemSeInscreve = pessoa(Vinculo.MEMBRO);
        evento.setResponsavel(responsavelQueTambemSeInscreve);

        dado(evento, responsavelQueTambemSeInscreve, 0);

        service.inscrever(eventoId, responsavelQueTambemSeInscreve.getId(), null,
                responsavelQueTambemSeInscreve.getId(), "ACESSO_COMUM", false, igrejaId);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }
```

Usar os helpers privados `evento(Integer vagas)`, `pessoa(Vinculo vinculo)` e `dado(...)` já
existentes neste arquivo de teste (padrão documentado no `CLAUDE.md` — "Helpers privados por
classe"). Ajustar o construtor de `service` no `@BeforeEach` pra incluir
`notificacaoService = mock(NotificacaoService.class);` e `usuarioRepository` se ainda não
estiver mockado nesse arquivo.

- [ ] **Step 4: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=InscricaoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(notificacao): notifica responsavel do evento quando alguem se inscreve"
```

---

## Task 9: Produtor — evento muda data/local ou é cancelado

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.EVENTO_ALTERADO`.
- Produces: `InscricaoRepository.findByEventoIdAndStatus(UUID, StatusInscricao)`.

- [ ] **Step 1: Adicionar o método no repositório de inscrição**

Em `InscricaoRepository.java`, junto de `findByEventoIdAndPessoaId` (derived query simples,
mesmo padrão — `InscricaoEvento` não tem `@SQLRestriction`, então não precisa de nativa):

```java
    List<InscricaoEvento> findByEventoIdAndStatus(UUID eventoId, StatusInscricao status);
```

- [ ] **Step 2: Injetar `NotificacaoService` e o helper de notificação em `EventoService`**

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

```java
    private void notificarInscritos(Evento evento, UUID igrejaId, String texto) {
        List<com.domus.api.modules.evento.inscricao.InscricaoEvento> inscricoes = inscricaoRepository
                .findByEventoIdAndStatus(evento.getId(), com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA);
        for (var inscricao : inscricoes) {
            usuarioRepository.findByPessoaId(inscricao.getPessoa().getId()).ifPresent(usuario ->
                    notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_ALTERADO,
                            igrejaId, usuario.getId(), texto, "/eventos/" + evento.getId()));
        }
    }
```

- [ ] **Step 3: Capturar valores antigos em `atualizarEvento` e notificar se mudou**

Antes de `evento.setTitulo(...)` (primeira linha que muda o `evento`), capturar:

```java
        java.time.LocalDateTime inicioAntigo = evento.getInicioEm();
        UUID localIdAntigo = evento.getLocal() != null ? evento.getLocal().getId() : null;
        String localTextoAntigo = evento.getLocalTexto();
```

Depois de `Evento salvo = eventoRepository.save(evento);` e antes de
`boolean fotoMudou = ...`:

```java
        boolean dataOuLocalMudou = !Objects.equals(inicioAntigo, salvo.getInicioEm())
                || !Objects.equals(localIdAntigo, salvo.getLocal() != null ? salvo.getLocal().getId() : null)
                || !Objects.equals(localTextoAntigo, salvo.getLocalTexto());
        if (dataOuLocalMudou) {
            notificarInscritos(salvo, igrejaId, "O evento \"" + salvo.getTitulo() + "\" mudou de data ou local.");
        }
```

(`Objects` já está importado em `EventoService.java` como `java.util.Objects`, usado em
`fotoMudou` logo abaixo — conferir o import no topo do arquivo.)

- [ ] **Step 4: Notificar em `arquivarEvento`**

Em `arquivarEvento`, antes de `eventoRepository.delete(evento);`:

```java
        notificarInscritos(evento, igrejaId, "O evento \"" + evento.getTitulo() + "\" foi cancelado.");
```

- [ ] **Step 5: Escrever os testes**

```java
    @Test
    void atualizarEventoNotificaInscritosQuandoDataMuda() {
        Evento evento = evento(null);
        Pessoa inscrito = pessoa(Vinculo.MEMBRO);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .pessoa(inscrito).status(StatusInscricao.CONFIRMADA).build();
        UUID usuarioIdInscrito = UUID.randomUUID();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));
        when(inscricaoRepository.findByEventoIdAndStatus(eventoId, StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(inscricao));
        when(usuarioRepository.findByPessoaId(inscrito.getId()))
                .thenReturn(Optional.of(Usuario.builder().id(usuarioIdInscrito).build()));
        // demais mocks de atualizarEvento (usuarioRepository.findByIdAndIgrejaId, resolverLocal,
        // resolverResponsavel, fotoService) seguem o padrão já usado nos outros testes de
        // atualizarEvento deste arquivo — reusar o setup existente, só trocando a data.

        EventoRequest data = eventoRequestComNovaData(evento, evento.getInicioEm().plusDays(1));
        service.atualizarEvento(eventoId, data, igrejaId, usuarioId);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.EVENTO_ALTERADO), eq(igrejaId), eq(usuarioIdInscrito),
                anyString(), eq("/eventos/" + eventoId));
    }

    @Test
    void atualizarEventoNaoNotificaQuandoSoADescricaoMuda() {
        Evento evento = evento(null);

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));
        // mesmos mocks de sempre, sem trocar data nem local

        EventoRequest data = eventoRequestComMesmaDataENovaDescricao(evento);
        service.atualizarEvento(eventoId, data, igrejaId, usuarioId);

        verify(inscricaoRepository, never()).findByEventoIdAndStatus(any(), any());
    }

    @Test
    void arquivarEventoNotificaInscritos() {
        Evento evento = evento(null);
        Pessoa inscrito = pessoa(Vinculo.MEMBRO);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .pessoa(inscrito).status(StatusInscricao.CONFIRMADA).build();
        UUID usuarioIdInscrito = UUID.randomUUID();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));
        when(inscricaoRepository.findByEventoIdAndStatus(eventoId, StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(inscricao));
        when(usuarioRepository.findByPessoaId(inscrito.getId()))
                .thenReturn(Optional.of(Usuario.builder().id(usuarioIdInscrito).build()));

        service.arquivarEvento(eventoId, igrejaId);

        verify(notificacaoService).criar(
                eq(TipoNotificacao.EVENTO_ALTERADO), eq(igrejaId), eq(usuarioIdInscrito),
                anyString(), eq("/eventos/" + eventoId));
    }
```

`eventoRequestComNovaData`/`eventoRequestComMesmaDataENovaDescricao` são helpers a construir a
partir do `EventoRequest` já usado nos testes existentes de `atualizarEvento` neste arquivo —
copiar o `EventoRequest` usado lá e só trocar o campo relevante (mesmo espírito dos helpers
`evento(Integer vagas)`/`pessoa(Vinculo vinculo)` já existentes). Ajustar o construtor de
`service` pra incluir `notificacaoService = mock(NotificacaoService.class);`.

- [ ] **Step 6: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=EventoServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(notificacao): notifica inscritos quando evento muda ou e cancelado"
```

---

## Task 10: Produtor — pedido de vínculo de família de igrejas

**Files:**
- Modify: `src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/familia/VinculoService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/familia/VinculoServiceTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.PEDIDO_VINCULO_FAMILIA`.
- Produces: `UsuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(UUID, String)` — também
  usado pela Task 11.

- [ ] **Step 1: Adicionar o método no `UsuarioRepository`**

Junto de `countByIgrejaIdAndRole_NomeAndAtivoTrue` (mesmo filtro, versão que devolve a lista
em vez da contagem):

```java
    List<Usuario> findByIgrejaIdAndRole_NomeAndAtivoTrue(UUID igrejaId, String roleNome);
```

- [ ] **Step 2: Injetar `NotificacaoService` em `VinculoService` e notificar em `entrarNaFamilia`**

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

Depois de `igrejaRepository.save(filha);` e antes do `log.info`:

```java
        List<Usuario> adminsDaSede = usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(
                mae.getId(), com.domus.api.shared.security.Perfil.ADMIN_IGREJA.name());
        for (Usuario admin : adminsDaSede) {
            notificacaoService.criar(
                    com.domus.api.modules.notificacao.TipoNotificacao.PEDIDO_VINCULO_FAMILIA,
                    mae.getId(), admin.getId(),
                    filha.getNome() + " pediu pra entrar na sua família de igrejas.",
                    "/configuracoes/igrejas-vinculadas");
        }
```

(`Usuario` já precisa estar importado em `VinculoService.java` — conferir; se não estiver,
adicionar `import com.domus.api.modules.usuario.Usuario;`.)

- [ ] **Step 3: Escrever o teste**

```java
    @Test
    void entrarNaFamiliaNotificaAdminsDaSede() {
        UUID usuarioIdAdminSede = UUID.randomUUID();
        Igreja mae = igreja("Sede", null);
        Igreja filha = igreja("Filha", null);
        mae.setCodigoVinculo("ABCD1234");
        Usuario adminSede = Usuario.builder().id(usuarioIdAdminSede).build();

        when(igrejaRepository.findByCodigoVinculo("ABCD1234")).thenReturn(Optional.of(mae));
        // demais mocks de lock/buscar já usados nos outros testes de entrarNaFamilia deste arquivo
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(mae.getId(), "ADMIN_IGREJA"))
                .thenReturn(List.of(adminSede));

        service.entrarNaFamilia(filha.getId(), UUID.randomUUID(), "abcd-1234");

        verify(notificacaoService).criar(
                eq(TipoNotificacao.PEDIDO_VINCULO_FAMILIA), eq(mae.getId()), eq(usuarioIdAdminSede),
                anyString(), eq("/configuracoes/igrejas-vinculadas"));
    }
```

Usar o helper `igreja(...)` (ou equivalente) já existente no arquivo de teste pra montar `mae`
e `filha`, e os mesmos mocks de lock (`travar`) que os outros testes de `entrarNaFamilia`
nesse arquivo já configuram. Ajustar o construtor de `service` pra incluir
`notificacaoService = mock(NotificacaoService.class);`.

- [ ] **Step 4: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=VinculoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java \
        src/main/java/com/domus/api/modules/igreja/familia/VinculoService.java \
        src/test/java/com/domus/api/modules/igreja/familia/VinculoServiceTest.java
git commit -m "feat(notificacao): notifica admins da sede quando uma igreja pede vinculo"
```

---

## Task 11: Produtor — exclusão de conta agendada perto do prazo

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJob.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJobTest.java`

**Interfaces:**
- Consumes: `NotificacaoService.criar(...)`, `TipoNotificacao.EXCLUSAO_IGREJA_AGENDADA`,
  `UsuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(UUID, String)` (Task 10).

- [ ] **Step 1: Injetar `NotificacaoService`**

```java
    private final com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
```

- [ ] **Step 2: Notificar os admins em `enviarLembrete`**

Depois do laço `for (String destinatario : destinatarios) { emailService.enviar(...); }`,
dentro do mesmo método `enviarLembrete`:

```java
        List<com.domus.api.modules.usuario.Usuario> admins = usuarioRepository
                .findByIgrejaIdAndRole_NomeAndAtivoTrue(igreja.getId(),
                        com.domus.api.shared.security.Perfil.ADMIN_IGREJA.name());
        for (var admin : admins) {
            notificacaoService.criar(
                    com.domus.api.modules.notificacao.TipoNotificacao.EXCLUSAO_IGREJA_AGENDADA,
                    igreja.getId(), admin.getId(), assunto, "/configuracoes/igreja");
        }
```

- [ ] **Step 3: Escrever o teste**

```java
    @Test
    void enviarLembreteNotificaTodosOsAdminsAtivos() {
        UUID usuarioIdAdmin = UUID.randomUUID();
        Igreja igreja = igrejaComExclusaoAgendadaEm(5); // helper já usado nos outros testes deste arquivo
        Usuario admin = Usuario.builder().id(usuarioIdAdmin).build();

        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igreja));
        when(usuarioRepository.buscarEmailsAdminsAtivos(igreja.getId())).thenReturn(List.of());
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(igreja.getId(), "ADMIN_IGREJA"))
                .thenReturn(List.of(admin));

        job.verificarPrazos();

        verify(notificacaoService).criar(
                eq(TipoNotificacao.EXCLUSAO_IGREJA_AGENDADA), eq(igreja.getId()), eq(usuarioIdAdmin),
                anyString(), eq("/configuracoes/igreja"));
    }
```

Usar o helper de fixture que o arquivo já tiver pra "igreja com exclusão agendada há N dias"
(o job já tem testes cobrindo D-5/D-1/purga — seguir o mesmo padrão de setup). Ajustar o
construtor de `job` pra incluir `notificacaoService = mock(NotificacaoService.class);`.

- [ ] **Step 4: Rodar os testes**

Run: `./mvnw -q -o test -Dtest=ExclusaoIgrejaJobTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJob.java \
        src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJobTest.java
git commit -m "feat(notificacao): notifica admins quando exclusao de conta esta perto do prazo"
```

---

## Task 12: Frontend — tipos, service e hooks

**Files:**
- Create: `frontend/src/types/notificacaoCentral.type.ts`
- Create: `frontend/src/services/notificacaoCentral.service.ts`
- Create: `frontend/src/hooks/notificacoes/useContagemNaoLidas.ts`
- Create: `frontend/src/hooks/notificacoes/useListaNotificacoes.ts`
- Create: `frontend/src/hooks/notificacoes/useMarcarNotificacaoLida.ts`
- Modify: `frontend/src/lib/endpoints.ts`

**Interfaces:**
- Produces: `NotificacaoCentral` (type), `notificacaoCentralService.{listar,contagemNaoLidas,marcarLida,marcarTodasLidas}`,
  `useContagemNaoLidas()`, `useListaNotificacoes(habilitado: boolean)`,
  `useMarcarNotificacaoLida()`, `useMarcarTodasNotificacoesLidas()`. Consumidos pela Task 13.

- [ ] **Step 1: Adicionar os endpoints**

Em `frontend/src/lib/endpoints.ts`, dentro do objeto `Endpoints` (junto de `dashboard`):

```ts
  notificacoes: {
    LISTAR: '/notificacoes',
    CONTAGEM_NAO_LIDAS: '/notificacoes/contagem-nao-lidas',
    MARCAR_LIDA: (id: string) => `/notificacoes/${id}/lida`,
    MARCAR_TODAS_LIDAS: '/notificacoes/lidas',
  },
```

- [ ] **Step 2: Criar o tipo**

```ts
// frontend/src/types/notificacaoCentral.type.ts
export interface NotificacaoCentral {
  id: string
  tipo: string
  texto: string
  link: string | null
  lida: boolean
  criadoEm: string
}
```

- [ ] **Step 3: Criar o service**

```ts
// frontend/src/services/notificacaoCentral.service.ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { NotificacaoCentral } from '@/types/notificacaoCentral.type'
import type { PagedResponse } from '@/types/pagedResponse.type'

export const notificacaoCentralService = {
  listar: (): Promise<PagedResponse<NotificacaoCentral>> =>
    api.get<PagedResponse<NotificacaoCentral>>(Endpoints.notificacoes.LISTAR).then((res) => res.data),

  contagemNaoLidas: (): Promise<number> =>
    api.get<{ total: number }>(Endpoints.notificacoes.CONTAGEM_NAO_LIDAS).then((res) => res.data.total),

  marcarLida: (id: string): Promise<void> =>
    api.patch(Endpoints.notificacoes.MARCAR_LIDA(id)).then(() => undefined),

  marcarTodasLidas: (): Promise<void> =>
    api.patch(Endpoints.notificacoes.MARCAR_TODAS_LIDAS).then(() => undefined),
}
```

- [ ] **Step 4: Criar os hooks**

```ts
// frontend/src/hooks/notificacoes/useContagemNaoLidas.ts
import { useQuery } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

// Polling: sem WebSocket no projeto, 30s é suficiente pro volume de uma igreja.
export function useContagemNaoLidas() {
  return useQuery({
    queryKey: ['notificacoes', 'contagem-nao-lidas'],
    queryFn: () => notificacaoCentralService.contagemNaoLidas(),
    refetchInterval: 30 * 1000,
  })
}
```

```ts
// frontend/src/hooks/notificacoes/useListaNotificacoes.ts
import { useQuery } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

// Só busca a lista quando o dropdown está aberto — não mantém sincronizada em segundo plano.
export function useListaNotificacoes(habilitado: boolean) {
  return useQuery({
    queryKey: ['notificacoes', 'lista'],
    queryFn: () => notificacaoCentralService.listar(),
    enabled: habilitado,
  })
}
```

```ts
// frontend/src/hooks/notificacoes/useMarcarNotificacaoLida.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificacaoCentralService } from '@/services/notificacaoCentral.service'

export function useMarcarNotificacaoLida() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => notificacaoCentralService.marcarLida(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notificacoes'] })
    },
  })
}

export function useMarcarTodasNotificacoesLidas() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => notificacaoCentralService.marcarTodasLidas(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notificacoes'] })
    },
  })
}
```

- [ ] **Step 5: Checar tipos**

Run: `cd ../../frontend && npx tsc --noEmit`
Expected: sem erro relacionado aos arquivos novos.

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/types/notificacaoCentral.type.ts \
        frontend/src/services/notificacaoCentral.service.ts \
        frontend/src/hooks/notificacoes/ \
        frontend/src/lib/endpoints.ts
git commit -m "feat(notificacao): types, service e hooks da central de notificacoes"
```

---

## Task 13: Frontend — sino no `TopBar`

**Files:**
- Create: `frontend/src/components/layout/notificacoes/SinoNotificacoes.tsx`
- Create: `frontend/src/components/layout/notificacoes/SinoNotificacoes.module.css`
- Modify: `frontend/src/components/layout/TopBar.tsx`

**Interfaces:**
- Consumes: `useContagemNaoLidas`, `useListaNotificacoes`, `useMarcarNotificacaoLida`,
  `useMarcarTodasNotificacoesLidas` (Task 12).

- [ ] **Step 1: Criar o componente do sino**

```tsx
// frontend/src/components/layout/notificacoes/SinoNotificacoes.tsx
'use client'

import { useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { Bell } from 'lucide-react'
import { useContagemNaoLidas } from '@/hooks/notificacoes/useContagemNaoLidas'
import { useListaNotificacoes } from '@/hooks/notificacoes/useListaNotificacoes'
import { useMarcarNotificacaoLida, useMarcarTodasNotificacoesLidas } from '@/hooks/notificacoes/useMarcarNotificacaoLida'
import type { NotificacaoCentral } from '@/types/notificacaoCentral.type'
import styles from './SinoNotificacoes.module.css'

export function SinoNotificacoes() {
  const [aberto, setAberto] = useState(false)
  const wrapperRef = useRef<HTMLDivElement>(null)
  const router = useRouter()

  const { data: totalNaoLidas } = useContagemNaoLidas()
  const { data: pagina, isLoading } = useListaNotificacoes(aberto)
  const marcarLida = useMarcarNotificacaoLida()
  const marcarTodasLidas = useMarcarTodasNotificacoesLidas()

  useState(() => {
    function handleClickFora(e: MouseEvent) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setAberto(false)
      }
    }
    document.addEventListener('mousedown', handleClickFora)
    return () => document.removeEventListener('mousedown', handleClickFora)
  })

  function clicarNotificacao(n: NotificacaoCentral) {
    if (!n.lida) marcarLida.mutate(n.id)
    setAberto(false)
    if (n.link) router.push(n.link)
  }

  const notificacoes = pagina?.content ?? []
  const contador = totalNaoLidas ?? 0

  return (
    <div className={styles.wrapper} ref={wrapperRef}>
      <button
        type="button"
        className={styles.botaoSino}
        onClick={() => setAberto((v) => !v)}
        aria-label={contador > 0 ? `Notificações, ${contador} não lidas` : 'Notificações'}
      >
        <Bell size={20} />
        {contador > 0 && <span className={styles.badge}>{contador > 9 ? '9+' : contador}</span>}
      </button>

      {aberto && (
        <div className={styles.dropdown}>
          <div className={styles.cabecalho}>
            <span>Notificações</span>
            {notificacoes.length > 0 && (
              <button
                type="button"
                className={styles.marcarTodas}
                onClick={() => marcarTodasLidas.mutate()}
              >
                Marcar todas como lidas
              </button>
            )}
          </div>

          {isLoading && <p className={styles.vazio}>Carregando…</p>}
          {!isLoading && notificacoes.length === 0 && (
            <p className={styles.vazio}>Nenhuma notificação por aqui.</p>
          )}

          <ul className={styles.lista}>
            {notificacoes.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  className={`${styles.item} ${n.lida ? styles.itemLido : ''}`}
                  onClick={() => clicarNotificacao(n)}
                >
                  {n.texto}
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Criar o CSS module**

```css
/* frontend/src/components/layout/notificacoes/SinoNotificacoes.module.css */
.wrapper {
  position: relative;
}

.botaoSino {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 8px;
  color: #1e293b;
  flex-shrink: 0;
  transition: background 150ms ease;
}
.botaoSino:hover { background: #eff6ff; }

.badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  font-size: 0.65rem;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dropdown {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  max-height: 400px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
  z-index: 50;
}

.cabecalho {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e2e8f0;
  font-weight: 600;
  font-size: 0.875rem;
  color: #1e293b;
}

.marcarTodas {
  font-size: 0.75rem;
  font-weight: 500;
  color: #2563eb;
}
.marcarTodas:hover { text-decoration: underline; }

.vazio {
  padding: 24px 16px;
  text-align: center;
  color: #64748b;
  font-size: 0.875rem;
}

.lista {
  list-style: none;
}

.item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 12px 16px;
  font-size: 0.8125rem;
  color: #1e293b;
  border-bottom: 1px solid #f1f5f9;
  background: #eff6ff;
}
.item:hover { background: #dbeafe; }

.itemLido {
  background: #fff;
  color: #64748b;
}
.itemLido:hover { background: #f8fafc; }
```

- [ ] **Step 3: Integrar no `TopBar`**

```tsx
// frontend/src/components/layout/TopBar.tsx
'use client'

import Image from 'next/image'
import { Church, Menu } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useUiStore } from '@/store/uiStore'
import { BuscaGlobal } from './busca/BuscaGlobal'
import { SinoNotificacoes } from './notificacoes/SinoNotificacoes'
import { urlFoto } from '@/lib/urlFoto'
import styles from './TopBar.module.css'

export function TopBar() {
  const igrejaNome = useAuthStore((state) => state.igrejaNome)
  const igrejaSigla = useAuthStore((state) => state.igrejaSigla)
  const igrejaLogoId = useAuthStore((state) => state.igrejaLogoId)
  const alternarNav = useUiStore((state) => state.alternarNav)

  return (
    <header className={styles.topbar}>
      <button type="button" className={styles.hamburger} onClick={alternarNav} aria-label="Abrir menu">
        <Menu size={22} />
      </button>

      <BuscaGlobal />

      <SinoNotificacoes />

      <div className={styles.igreja}>
        <div className={styles.igrejaIcone}>
          {urlFoto(igrejaLogoId, 'THUMB') ? (
            <Image src={urlFoto(igrejaLogoId, 'THUMB')!} alt={igrejaNome ?? 'Igreja'} width={32} height={32} unoptimized className={styles.igrejaLogo} />
          ) : (
            <Church size={18} />
          )}
        </div>
        <span className={styles.igrejaNome}>{igrejaSigla ?? igrejaNome ?? 'Minha Igreja'}</span>
      </div>
    </header>
  )
}
```

- [ ] **Step 4: Checar tipos e lint**

Run: `cd ../../frontend && npx tsc --noEmit && npx eslint src/components/layout/notificacoes/SinoNotificacoes.tsx src/components/layout/TopBar.tsx`
Expected: sem erro.

- [ ] **Step 5: Testar no navegador**

Com backend e frontend rodando: logar, clicar no sino no `TopBar`, confirmar que abre o
dropdown (mesmo vazio, "Nenhuma notificação por aqui"). Disparar uma notificação de verdade
(ex.: conceder acesso a alguém — Task 4) e confirmar que o badge aparece e o texto certo
some da lista depois de clicar.

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/components/layout/notificacoes/ frontend/src/components/layout/TopBar.tsx
git commit -m "feat(notificacao): sino de notificacoes no TopBar"
```

---

## Task 14: Fechar o item no backlog

**Files:**
- Modify: `backend/api/docs/BACKLOG-PRE-VENDA.md`

- [ ] **Step 1: Marcar o item 4 como resolvido**

No cabeçalho da seção `## 4. Central de notificações (in-app)`, trocar por
`## 4. ~~Central de notificações (in-app)~~ RESOLVIDO (data da implementação)` e adicionar uma
linha no topo da seção apontando pro commit/PR, seguindo o mesmo padrão de
`BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md` (riscado + "RESOLVIDO" + data + resumo curto do que foi
feito, incluindo os 8 produtores ligados).

- [ ] **Step 2: Rodar a suíte inteira uma última vez**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS, todos os testes passando (os antigos + os novos das Tasks 1–11).

- [ ] **Step 3: Commit**

```bash
git add docs/BACKLOG-PRE-VENDA.md
git commit -m "docs(backlog): marca central de notificacoes como resolvida"
```
