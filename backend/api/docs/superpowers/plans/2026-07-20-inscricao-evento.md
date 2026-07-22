# Inscrição em Evento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que membros confirmem presença em eventos, inscrevam outros membros e levem convidados de fora, respeitando limite de vagas.

**Architecture:** Novo submódulo `modules/evento/inscricao` (controller → service → repository, DTOs), seguindo o padrão do módulo de evento. A contagem de vagas é serializada por um lock pessimista na linha do evento. Campos novos em `evento` e `membro` entram na mesma migration.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL, JUnit 5 + AssertJ + Mockito; front Next.js 16 + TypeScript + TanStack Query + React Hook Form/Zod + CSS Modules.

**Spec:** `docs/superpowers/specs/2026-07-20-inscricao-evento-design.md`

## Global Constraints

- `igreja_id` SEMPRE do JWT (`usuarioAutenticado.getIgrejaId()`), NUNCA do corpo da requisição.
- Camadas `controller → service → repository`; services retornam DTOs, nunca entidades.
- Soft delete (`deleted_at`) nas entidades de domínio; `@SQLRestriction("deleted_at IS NULL")`.
- Toda tela nova entra com mobile ajustado na mesma entrega (tabelas viram cards).
- Notificações: `notificar.sucesso/erro/aviso/info` — NUNCA `toast` do sonner direto.
- Ação destrutiva usa `ModalConfirmacaoCritica`; NUNCA `window.confirm`.
- Invalidação de cache: `invalidarCache(queryClient, 'evento')` — nunca `invalidateQueries` manual.
- Do `@AuthenticationPrincipal` use só o id; ler campo LAZY dele estoura (principal desanexado).
- Commits sem `Co-Authored-By`.
- Migration atual é V14; a próxima é **V15**.

---

## File Structure

**Backend — criar:**
- `db/migration/V15__inscricao_evento.sql` — schema
- `modules/evento/inscricao/InscricaoEvento.java` — entidade
- `modules/evento/inscricao/AcompanhanteInscricao.java` — entidade
- `modules/evento/inscricao/StatusInscricao.java` — enum
- `modules/evento/inscricao/InscricaoRepository.java`
- `modules/evento/inscricao/AcompanhanteRepository.java`
- `modules/evento/inscricao/InscricaoService.java` — regras + lock
- `modules/evento/inscricao/InscricaoController.java`
- `modules/evento/inscricao/DTOs/` — `InscreverMembrosRequest`, `AcompanhanteRequest`, `InscricaoResponse`, `ListaInscritosResponse`, `ResumoInscricaoResponse`

**Backend — modificar:**
- `modules/evento/Evento.java` — `vagas`, `preco`, `exclusivoMembros`, `exclusivoBatizados`
- `modules/evento/EventoRepository.java` — `buscarComLock`
- `modules/evento/DTOs/EventoRequest.java` + `EventoResponse.java`
- `modules/membro/Membro.java` — `batizado`, `dataBatismo`
- `modules/membro/DTOs/` — request/response de membro
- `config/SecurityConfig.java` — **matchers de inscrição ANTES dos curingas de `/eventos/**`**

**Front — criar:**
- `types/inscricao.type.ts`, `services/inscricao.service.ts`
- `hooks/inscricao/useInscricao.ts`, `useInscreverMembros.ts`, `useCancelarInscricao.ts`, `useListaInscritos.ts`
- `components/module/eventos/BotaoConfirmarPresenca.tsx` (+ CSS)
- `components/module/eventos/ModalInscreverMembros.tsx` (+ CSS)
- `components/module/eventos/ModalConvidadoExterno.tsx` (+ CSS)
- `app/(app)/eventos/[id]/inscritos/page.tsx` (+ CSS)

---

### Task 1: Migration V15 + campos nas entidades

**Files:**
- Create: `src/main/resources/db/migration/V15__inscricao_evento.sql`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/membro/Membro.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/StatusInscricao.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteInscricao.java`

**Interfaces:**
- Produces: entidades `InscricaoEvento` (getters: `getId`, `getEvento`, `getMembro`, `getStatus`, `getInscritoPorUsuarioId`, `getAcompanhantes`), `AcompanhanteInscricao`, enum `StatusInscricao{CONFIRMADA,CANCELADA}`; campos `Evento.getVagas()/getPreco()/isExclusivoMembros()/isExclusivoBatizados()`, `Membro.isBatizado()/getDataBatismo()`.

- [ ] **Step 1: Escrever a migration**

```sql
-- V15: inscrição em evento (Spec A).
-- Vagas contam PESSOAS (inscritos CONFIRMADA + seus acompanhantes), não inscrições.

ALTER TABLE evento
    ADD COLUMN vagas               INTEGER,
    ADD COLUMN preco               NUMERIC(10,2),
    ADD COLUMN exclusivo_membros   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN exclusivo_batizados BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_vagas_positivas CHECK (vagas IS NULL OR vagas > 0),
    ADD CONSTRAINT chk_evento_preco_positivo  CHECK (preco IS NULL OR preco > 0);

-- ATIVO nunca significou batizado (criança ATIVA não é batizada; quem se mudou é
-- batizado e está INATIVO). Por isso campo próprio, e não reuso de status.
ALTER TABLE membro
    ADD COLUMN batizado     BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_batismo DATE;

CREATE TABLE inscricao_evento (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id               UUID        NOT NULL REFERENCES igreja(id),
    evento_id               UUID        NOT NULL REFERENCES evento(id),
    membro_id               UUID        NOT NULL REFERENCES membro(id),
    inscrito_por_usuario_id UUID        REFERENCES usuario(id),  -- NULL = auto-inscrição
    status                  VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    -- Cancelar NÃO apaga a linha (preserva quem inscreveu quem). Por isso a
    -- reinscrição REAPROVEITA esta linha em vez de inserir outra.
    CONSTRAINT uk_inscricao_evento_membro UNIQUE (evento_id, membro_id)
);

CREATE INDEX idx_inscricao_evento_id  ON inscricao_evento (evento_id);
CREATE INDEX idx_inscricao_membro_id  ON inscricao_evento (membro_id);

CREATE TABLE acompanhante_inscricao (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inscricao_id UUID         NOT NULL REFERENCES inscricao_evento(id) ON DELETE CASCADE,
    nome         VARCHAR(255) NOT NULL,
    telefone     VARCHAR(20),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_acompanhante_inscricao_id ON acompanhante_inscricao (inscricao_id);

COMMENT ON COLUMN inscricao_evento.inscrito_por_usuario_id IS
    'NULL = a própria pessoa se inscreveu. Preenchido = alguém a inscreveu.';
COMMENT ON TABLE acompanhante_inscricao IS
    'Só para quem NÃO é membro da igreja. Existe para responder, ao ler a lista, '
    '"de onde veio essa pessoa que ninguém conhece".';
```

- [ ] **Step 2: Rodar a migration e verificar**

Run: `mvn -q spring-boot:run` (ou subir a app) e conferir o log do Flyway.
Expected: `Migrating schema "public" to version "15 - inscricao evento"` e `Successfully applied 1 migration`.

Alternativa sem subir a app: `mvn -q flyway:migrate` se o plugin estiver configurado.

- [ ] **Step 3: Adicionar os campos em `Evento.java`**

Depois de `private String foto;`:

```java
    /** NULL = sem limite de vagas. */
    @Column(name = "vagas")
    private Integer vagas;

    /** NULL = gratuito. Informativo: o Domus registra a inscrição, não o pagamento. */
    @Column(name = "preco", precision = 10, scale = 2)
    private java.math.BigDecimal preco;

    @Column(name = "exclusivo_membros", nullable = false)
    @Builder.Default
    private boolean exclusivoMembros = false;

    @Column(name = "exclusivo_batizados", nullable = false)
    @Builder.Default
    private boolean exclusivoBatizados = false;
```

> `@Builder.Default` é obrigatório: sem ele o Lombok ignora o valor inicial e grava `false`
> por acaso, não por decisão — e num boolean isso passa despercebido.

- [ ] **Step 4: Adicionar os campos em `Membro.java`**

Depois de `private String observacoes;`:

```java
    @Column(name = "batizado", nullable = false)
    @Builder.Default
    private boolean batizado = false;

    /** Opcional: a secretaria nem sempre tem a data. */
    @Column(name = "data_batismo")
    private LocalDate dataBatismo;
```

- [ ] **Step 5: Criar o enum e as entidades**

`StatusInscricao.java`:

```java
package com.domus.api.modules.evento.inscricao;

public enum StatusInscricao {
    CONFIRMADA,
    CANCELADA
}
```

`InscricaoEvento.java`:

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.membro.Membro;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inscricao_evento")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InscricaoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membro_id", nullable = false)
    private Membro membro;

    /** NULL = auto-inscrição. Preenchido = alguém inscreveu esta pessoa. */
    @Column(name = "inscrito_por_usuario_id")
    private UUID inscritoPorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusInscricao status = StatusInscricao.CONFIRMADA;

    @OneToMany(mappedBy = "inscricao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AcompanhanteInscricao> acompanhantes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean estaConfirmada() {
        return status == StatusInscricao.CONFIRMADA;
    }
}
```

> A cascata aqui é segura (ao contrário da de `Igreja`, registrada no BACKLOG): acompanhante
> só existe enquanto a inscrição existe, então `orphanRemoval` é a semântica correta.

`AcompanhanteInscricao.java`:

```java
package com.domus.api.modules.evento.inscricao;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "acompanhante_inscricao")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AcompanhanteInscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false)
    private InscricaoEvento inscricao;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: Compilar**

Run: `mvn -q compile; echo "EXIT=$?"`
Expected: `EXIT=0`

> Não encadeie `&& echo OK` depois de um `| tail` — o status vira o do `tail` e o comando
> mente que compilou. Use `$?` ou `${PIPESTATUS[0]}`.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V15__inscricao_evento.sql \
        src/main/java/com/domus/api/modules/evento/ \
        src/main/java/com/domus/api/modules/membro/Membro.java
git commit -m "feat(inscricao): schema e entidades de inscrição em evento"
```

---

### Task 2: SecurityConfig — matchers antes dos curingas

**Files:**
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java:104-112`
- Test: `src/test/java/com/domus/api/config/SecurityFilterTest.java`

**Por que é tarefa própria:** os curingas `POST /eventos/**` (ADMIN+LÍDER) e `GET /eventos/**`
(todos) quebram a feature em direções opostas — o POST barra o MEMBRO, que é o usuário
principal; o GET vaza a lista de inscritos, que é ADMIN/LÍDER só. É a mesma armadilha já
documentada em `/igrejas/*`. Sem isto, todo o resto do plano falha em runtime.

**Interfaces:**
- Produces: rotas `/eventos/*/inscricoes**` liberadas para MEMBRO; `GET /eventos/*/inscricoes` restrita a ADMIN_IGREJA e LIDER.

- [ ] **Step 1: Escrever o teste que falha**

Em `SecurityFilterTest.java`, seguindo o padrão já existente no arquivo:

```java
    @Test
    void membroPodeSeInscreverEmEvento() throws Exception {
        mockMvc.perform(post("/eventos/" + UUID.randomUUID() + "/inscricoes")
                        .with(usuarioComRole("MEMBRO")).with(csrf()))
                .andExpect(status().is(not(403)));
    }

    @Test
    void membroNaoVeListaDeInscritos() throws Exception {
        mockMvc.perform(get("/eventos/" + UUID.randomUUID() + "/inscricoes")
                        .with(usuarioComRole("MEMBRO")))
                .andExpect(status().isForbidden());
    }
```

> Se o helper `usuarioComRole` não existir com esse nome no arquivo, use o helper de
> autenticação que já estiver lá — não crie um segundo.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=SecurityFilterTest`
Expected: FAIL — `membroPodeSeInscreverEmEvento` recebe 403 (curinga do POST);
`membroNaoVeListaDeInscritos` recebe 200/404 em vez de 403 (curinga do GET).

- [ ] **Step 3: Inserir os matchers ANTES do bloco de eventos**

Em `SecurityConfig.java`, imediatamente antes do comentário `//Eventos` da linha 104:

```java
                        //Inscrição em evento — DEVE vir ANTES dos curingas /eventos/**,
                        //senão o POST curinga (ADMIN+LÍDER) barra o MEMBRO, que é justamente
                        //quem se inscreve, e o GET curinga (todos) vaza a lista de inscritos.
                        //Mesma armadilha de ordenação já corrigida em /igrejas/*.
                        .requestMatchers(HttpMethod.GET, "/eventos/*/inscricoes")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER")
                        .requestMatchers("/eventos/*/inscricoes/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER", "MEMBRO")
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER", "MEMBRO")
                        .requestMatchers(HttpMethod.DELETE, "/inscricoes/**", "/acompanhantes/**")
                        .hasAnyRole("ADMIN_IGREJA", "LIDER", "MEMBRO")
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test -Dtest=SecurityFilterTest`
Expected: PASS (todos os testes do arquivo)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/config/SecurityConfig.java \
        src/test/java/com/domus/api/config/SecurityFilterTest.java
git commit -m "fix(security): rotas de inscrição antes dos curingas de /eventos"
```

---

### Task 3: Repositories + lock pessimista

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`

**Interfaces:**
- Consumes: `InscricaoEvento`, `AcompanhanteInscricao`, `StatusInscricao` (Task 1)
- Produces: `EventoRepository.buscarComLock(UUID id, UUID igrejaId)`, `InscricaoRepository.contarPessoasConfirmadas(UUID eventoId)`, `.findByEventoIdAndMembroId(...)`, `.listarPorEvento(UUID)`, `.findByIdAndIgrejaId(...)`, `AcompanhanteRepository.findById`.

- [ ] **Step 1: `buscarComLock` no `EventoRepository`**

```java
    /**
     * Trava a LINHA do evento para serializar a contagem de vagas.
     *
     * <p>Sem isto, sob READ COMMITTED duas inscrições simultâneas na última vaga leem a
     * mesma contagem antiga e AMBAS passam. Mesma classe de erro do vínculo de igrejas (V14).
     *
     * <p>O lock é por evento, então inscrições em eventos diferentes não se bloqueiam.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id AND e.igreja.id = :igrejaId")
    Optional<Evento> buscarComLock(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);
```

Imports a adicionar: `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`.

- [ ] **Step 2: Criar `InscricaoRepository`**

```java
package com.domus.api.modules.evento.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<InscricaoEvento, UUID> {

    /**
     * Vagas contam PESSOAS, não inscrições: cada inscrição confirmada vale 1 (o membro)
     * mais o número de acompanhantes que ele trouxe. Canceladas não contam.
     */
    @Query("""
        SELECT COALESCE(COUNT(i), 0) + COALESCE(
                   (SELECT COUNT(a) FROM AcompanhanteInscricao a
                     WHERE a.inscricao.evento.id = :eventoId
                       AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA), 0)
        FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long contarPessoasConfirmadas(@Param("eventoId") UUID eventoId);

    Optional<InscricaoEvento> findByEventoIdAndMembroId(UUID eventoId, UUID membroId);

    Optional<InscricaoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.membro
        WHERE i.evento.id = :eventoId AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
        ORDER BY i.createdAt ASC
    """)
    List<InscricaoEvento> listarPorEvento(@Param("eventoId") UUID eventoId);

    List<InscricaoEvento> findByMembroIdAndStatus(UUID membroId, StatusInscricao status);
}
```

> `JOIN FETCH` na listagem evita N+1: sem ele, uma lista de 80 inscritos dispara 80 consultas
> extras só para ler o nome de cada membro.

- [ ] **Step 3: Criar `AcompanhanteRepository`**

```java
package com.domus.api.modules.evento.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AcompanhanteRepository extends JpaRepository<AcompanhanteInscricao, UUID> {
}
```

- [ ] **Step 4: Compilar**

Run: `mvn -q compile; echo "EXIT=$?"`
Expected: `EXIT=0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/
git commit -m "feat(inscricao): repositories e lock pessimista de vagas"
```

---

### Task 4: DTOs

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/InscreverMembrosRequest.java`
- Create: `.../DTOs/AcompanhanteRequest.java`
- Create: `.../DTOs/AcompanhanteResponse.java`
- Create: `.../DTOs/InscritoResponse.java`
- Create: `.../DTOs/ListaInscritosResponse.java`
- Create: `.../DTOs/MinhaInscricaoResponse.java`

**Interfaces:**
- Produces: os 6 records abaixo, com os nomes de campo exatos consumidos pelo front na Task 9.

- [ ] **Step 1: Criar os DTOs**

```java
// InscreverMembrosRequest.java
package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record InscreverMembrosRequest(
        @NotEmpty(message = "Selecione ao menos um membro.")
        List<UUID> membroIds
) {}
```

```java
// AcompanhanteRequest.java
package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcompanhanteRequest(
        @NotBlank(message = "O nome do convidado é obrigatório.")
        @Size(max = 255)
        String nome,
        @Size(max = 20)
        String telefone
) {}
```

```java
// AcompanhanteResponse.java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.AcompanhanteInscricao;
import java.util.UUID;

public record AcompanhanteResponse(UUID id, String nome, String telefone) {
    public static AcompanhanteResponse from(AcompanhanteInscricao a) {
        return new AcompanhanteResponse(a.getId(), a.getNome(), a.getTelefone());
    }
}
```

```java
// InscritoResponse.java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Uma linha da lista de inscritos (ADMIN/LÍDER). */
public record InscritoResponse(
        UUID id,
        UUID membroId,
        String nome,
        String foto,
        /** NULL = a pessoa se inscreveu sozinha. */
        UUID inscritoPorUsuarioId,
        LocalDateTime inscritoEm,
        List<AcompanhanteResponse> acompanhantes
) {
    public static InscritoResponse from(InscricaoEvento i) {
        return new InscritoResponse(
                i.getId(),
                i.getMembro().getId(),
                i.getMembro().getNome(),
                i.getMembro().getFoto(),
                i.getInscritoPorUsuarioId(),
                i.getCreatedAt(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList()
        );
    }
}
```

```java
// ListaInscritosResponse.java
package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.List;

public record ListaInscritosResponse(
        long totalPessoas,
        Integer vagas,
        Integer vagasRestantes,
        List<InscritoResponse> inscritos
) {}
```

```java
// MinhaInscricaoResponse.java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.util.List;
import java.util.UUID;

/** O que o próprio usuário vê sobre a sua inscrição no evento. */
public record MinhaInscricaoResponse(
        UUID id,
        boolean inscrito,
        List<AcompanhanteResponse> acompanhantes
) {
    public static MinhaInscricaoResponse from(InscricaoEvento i) {
        return new MinhaInscricaoResponse(
                i.getId(),
                i.estaConfirmada(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList()
        );
    }

    public static MinhaInscricaoResponse naoInscrito() {
        return new MinhaInscricaoResponse(null, false, List.of());
    }
}
```

- [ ] **Step 2: Compilar e commitar**

Run: `mvn -q compile; echo "EXIT=$?"` → `EXIT=0`

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/DTOs/
git commit -m "feat(inscricao): DTOs de inscrição e lista de inscritos"
```

---

### Task 5: InscricaoService — inscrever (auto e outros)

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: repositories (Task 3), DTOs (Task 4), `UsuarioRepository`, `MembroRepository`
- Produces: `inscrever(UUID eventoId, UUID membroId, UUID inscritoPorOuNull, UUID igrejaId)`, `inscreverMembros(...)`, `minhaInscricao(...)`

- [ ] **Step 1: Escrever os testes que falham**

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InscricaoServiceTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    MembroRepository membroRepository;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID membroId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        acompanhanteRepository = mock(AcompanhanteRepository.class);
        membroRepository = mock(MembroRepository.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, membroRepository);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(Integer vagas) {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Retiro").inicioEm(LocalDateTime.now().plusDays(10))
                .vagas(vagas)
                .build();
    }

    private Membro membro(boolean batizado, StatusMembro status) {
        return Membro.builder()
                .id(membroId).igreja(igreja()).nome("Maria")
                .status(status).batizado(batizado)
                .build();
    }

    private void dado(Evento e, Membro m, long ocupadas) {
        when(eventoRepository.buscarComLock(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(membroRepository.findByIdAndIgrejaId(membroId, igrejaId)).thenReturn(Optional.of(m));
        when(inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId))
                .thenReturn(Optional.empty());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(ocupadas);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void inscreveQuandoHaVaga() {
        dado(evento(10), membro(true, StatusMembro.ATIVO), 3);

        service.inscrever(eventoId, membroId, null, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void recusaQuandoVagasEsgotadas() {
        dado(evento(5), membro(true, StatusMembro.ATIVO), 5);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");
    }

    @Test
    void vagasNulasSignificamSemLimite() {
        dado(evento(null), membro(true, StatusMembro.ATIVO), 9999);

        service.inscrever(eventoId, membroId, null, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void eventoExclusivoDeBatizadosRecusaNaoBatizado() {
        Evento e = evento(10);
        e.setExclusivoBatizados(true);
        dado(e, membro(false, StatusMembro.ATIVO), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("batizados");
    }

    @Test
    void eventoExclusivoDeMembrosRecusaVisitante() {
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        dado(e, membro(true, StatusMembro.VISITANTE), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recusaEventoJaEncerrado() {
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusDays(1));
        dado(e, membro(true, StatusMembro.ATIVO), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aconteceu");
    }

    @Test
    void recusaInscricaoDuplicada() {
        dado(evento(10), membro(true, StatusMembro.ATIVO), 0);
        InscricaoEvento existente = InscricaoEvento.builder()
                .id(UUID.randomUUID()).status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.inscrever(eventoId, membroId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está inscrit");
    }

    @Test
    void reinscricaoReaproveitaLinhaCancelada() {
        dado(evento(10), membro(true, StatusMembro.ATIVO), 0);
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .id(UUID.randomUUID()).status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId))
                .thenReturn(Optional.of(cancelada));

        service.inscrever(eventoId, membroId, null, igrejaId);

        assertThat(cancelada.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(inscricaoRepository).save(cancelada);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL — `InscricaoService` não existe (erro de compilação do teste).

- [ ] **Step 3: Implementar o serviço**

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.membro.Membro;
import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.StatusMembro;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class InscricaoService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final AcompanhanteRepository acompanhanteRepository;
    private final MembroRepository membroRepository;

    /**
     * Inscreve um membro. {@code inscritoPorOuNull} é NULL na auto-inscrição.
     *
     * <p>O evento é buscado COM LOCK: a contagem de vagas e o insert precisam ser atômicos,
     * senão duas inscrições simultâneas na última vaga passam as duas.
     */
    @Transactional
    public MinhaInscricaoResponse inscrever(UUID eventoId, UUID membroId,
                                            UUID inscritoPorOuNull, UUID igrejaId) {
        Evento evento = eventoRepository.buscarComLock(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        Membro membro = membroRepository.findByIdAndIgrejaId(membroId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Membro não encontrado."));

        validarEventoAberto(evento);
        validarElegibilidade(evento, membro);

        InscricaoEvento inscricao = inscricaoRepository
                .findByEventoIdAndMembroId(eventoId, membroId)
                .orElse(null);

        if (inscricao != null && inscricao.estaConfirmada()) {
            throw new BusinessException("JA_INSCRITO", "Esta pessoa já está inscrita neste evento.");
        }

        validarVaga(evento, 1);

        if (inscricao != null) {
            // Reaproveita a linha cancelada: o UNIQUE (evento_id, membro_id) impediria inserir
            // outra, e sem isto quem cancelasse ficaria impedido de voltar ao próprio evento.
            inscricao.setStatus(StatusInscricao.CONFIRMADA);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .membro(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(StatusInscricao.CONFIRMADA)
                    .build();
        }

        InscricaoEvento salva = inscricaoRepository.save(inscricao);
        log.info("Inscrição confirmada. evento_id={}, membro_id={}, inscrito_por={}, igreja_id={}",
                eventoId, membroId, inscritoPorOuNull, igrejaId);
        return MinhaInscricaoResponse.from(salva);
    }

    /**
     * Inscreve vários membros de uma vez (modal de seleção múltipla).
     *
     * <p><b>Tudo ou nada, por decisão:</b> se um membro falhar (ex.: já inscrito), a transação
     * inteira volta atrás e nenhum é inscrito. O erro nomeia a pessoa, quem escolheu desmarca
     * e reenvia. Resultado parcial exigiria DTO e tela próprios para um caso que ainda não
     * sabemos se acontece.
     */
    @Transactional
    public void inscreverMembros(UUID eventoId, List<UUID> membroIds,
                                 UUID inscritoPorUsuarioId, UUID igrejaId) {
        for (UUID membroId : membroIds) {
            inscrever(eventoId, membroId, inscritoPorUsuarioId, igrejaId);
        }
    }

    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID membroId) {
        return inscricaoRepository.findByEventoIdAndMembroId(eventoId, membroId)
                .filter(InscricaoEvento::estaConfirmada)
                .map(MinhaInscricaoResponse::from)
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }

    private void validarEventoAberto(Evento evento) {
        if (evento.getInicioEm().isBefore(LocalDateTime.now())) {
            throw new BusinessException("EVENTO_ENCERRADO",
                    "Este evento já aconteceu. Não é mais possível se inscrever.");
        }
    }

    private void validarElegibilidade(Evento evento, Membro membro) {
        if (evento.isExclusivoMembros() && membro.getStatus() == StatusMembro.VISITANTE) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros da igreja.");
        }
        if (evento.isExclusivoBatizados() && !membro.isBatizado()) {
            throw new BusinessException("EXCLUSIVO_BATIZADOS",
                    "Este evento é exclusivo para membros batizados.");
        }
    }

    /** {@code vagas == null} significa sem limite. */
    void validarVaga(Evento evento, int pessoasAAdicionar) {
        if (evento.getVagas() == null) return;

        long ocupadas = inscricaoRepository.contarPessoasConfirmadas(evento.getId());
        if (ocupadas + pessoasAAdicionar > evento.getVagas()) {
            throw new BusinessException("VAGAS_ESGOTADAS",
                    "As vagas deste evento estão esgotadas.");
        }
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: PASS — 8 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(inscricao): regras de inscrição, elegibilidade e vagas"
```

---

### Task 6: Acompanhantes e cancelamento

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Modify: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Produces: `adicionarAcompanhante(UUID inscricaoId, AcompanhanteRequest, UUID usuarioId, UUID igrejaId)`, `removerAcompanhante(UUID, UUID usuarioId, UUID igrejaId)`, `cancelar(UUID inscricaoId, UUID usuarioId, UUID membroIdDoUsuario, String role, UUID igrejaId)`

- [ ] **Step 1: Escrever os testes que falham**

```java
    @Test
    void acompanhanteOcupaVaga() {
        Evento e = evento(2);
        when(eventoRepository.buscarComLock(eventoId, igrejaId)).thenReturn(Optional.of(e));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e).membro(membro(true, StatusMembro.ATIVO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(minha.getId(), igrejaId))
                .thenReturn(Optional.of(minha));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(2L);

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("João", null), usuarioId, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");
    }

    @Test
    void quemInscreveuNaoPodeDesinscrever() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .membro(membro(true, StatusMembro.ATIVO))
                .inscritoPorUsuarioId(usuarioId)      // fui EU quem inscrevi
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(outra.getId(), igrejaId))
                .thenReturn(Optional.of(outra));

        // sou MEMBRO, o membro da inscrição não sou eu
        assertThatThrownBy(() -> service.cancelar(
                outra.getId(), usuarioId, UUID.randomUUID(), "MEMBRO", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode cancelar");
    }

    @Test
    void oProprioInscritoPodeCancelar() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .membro(membro(true, StatusMembro.ATIVO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(minha.getId(), igrejaId))
                .thenReturn(Optional.of(minha));

        service.cancelar(minha.getId(), usuarioId, membroId, "MEMBRO", igrejaId);

        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void adminPodeCancelarInscricaoDeQualquerUm() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .membro(membro(true, StatusMembro.ATIVO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(outra.getId(), igrejaId))
                .thenReturn(Optional.of(outra));

        service.cancelar(outra.getId(), usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        assertThat(outra.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }
```

Adicionar ao topo da classe de teste: `UUID usuarioId = UUID.randomUUID();` e os imports
`com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL — métodos `adicionarAcompanhante`/`cancelar` não existem.

- [ ] **Step 3: Implementar**

Adicionar ao `InscricaoService`:

```java
    /** Convidado de fora, pendurado na inscrição de quem o trouxe. Ocupa vaga. */
    @Transactional
    public AcompanhanteResponse adicionarAcompanhante(UUID inscricaoId, AcompanhanteRequest data,
                                                      UUID usuarioId, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        if (inscricao.getEvento().isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        validarEventoAberto(inscricao.getEvento());

        // Trava o evento antes de contar: mesma corrida da inscrição.
        eventoRepository.buscarComLock(inscricao.getEvento().getId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarVaga(inscricao.getEvento(), 1);

        AcompanhanteInscricao a = AcompanhanteInscricao.builder()
                .inscricao(inscricao)
                .nome(com.domus.api.shared.util.TextoUtil.capitalizar(data.nome()))
                .telefone(data.telefone())
                .build();

        AcompanhanteInscricao salvo = acompanhanteRepository.save(a);
        log.info("Acompanhante adicionado. inscricao_id={}, igreja_id={}", inscricaoId, igrejaId);
        return AcompanhanteResponse.from(salvo);
    }

    @Transactional
    public void removerAcompanhante(UUID acompanhanteId, UUID meuMembroId, String role, UUID igrejaId) {
        AcompanhanteInscricao a = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        InscricaoEvento inscricao = a.getInscricao();
        if (!inscricao.getIgreja().getId().equals(igrejaId)) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        // A permissão vem de SER DONO DA INSCRIÇÃO, não de ter sido quem inscreveu.
        // Comparar com inscritoPorUsuarioId seria furo: ele é NULL em toda auto-inscrição
        // (o caso mais comum), e qualquer NULL-check liberaria geral.
        boolean ehAdmin = "ADMIN_IGREJA".equals(role) || "LIDER".equals(role);
        boolean souODono = inscricao.getMembro().getId().equals(meuMembroId);

        if (!ehAdmin && !souODono) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você só pode remover convidados da sua própria inscrição.");
        }
        acompanhanteRepository.delete(a);
    }

    /**
     * Cancela uma inscrição.
     *
     * <p>Regra: você controla VOCÊ MESMO e o que você trouxe. Quem inscreveu alguém
     * NÃO pode desinscrever — inscrever ocupa uma vaga, mas desinscrever tira a pessoa de
     * um evento que ela achava que ia, e ela só descobre no dia.
     */
    @Transactional
    public void cancelar(UUID inscricaoId, UUID usuarioId, UUID meuMembroId,
                         String role, UUID igrejaId) {
        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);

        boolean ehAdmin = "ADMIN_IGREJA".equals(role) || "LIDER".equals(role);
        boolean souEu = inscricao.getMembro().getId().equals(meuMembroId);

        if (!ehAdmin && !souEu) {
            throw new BusinessException("SEM_PERMISSAO",
                    "Você não pode cancelar a inscrição de outra pessoa. "
                    + "Peça a ela ou a um líder da igreja.");
        }

        inscricao.setStatus(StatusInscricao.CANCELADA);
        inscricaoRepository.save(inscricao);
        log.info("Inscrição cancelada. id={}, por_usuario={}, igreja_id={}",
                inscricaoId, usuarioId, igrejaId);
    }

    private InscricaoEvento buscarInscricao(UUID id, UUID igrejaId) {
        return inscricaoRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
    }
```

Imports novos: `AcompanhanteRequest`, `AcompanhanteResponse`.

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: PASS — 12 testes.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/ \
        src/test/java/com/domus/api/modules/evento/inscricao/
git commit -m "feat(inscricao): acompanhantes e regras de cancelamento"
```

---

### Task 7: Lista de inscritos + Controller

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java`
- Modify: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Produces: endpoints conforme a spec; `listarInscritos(UUID eventoId, UUID igrejaId) → ListaInscritosResponse`

- [ ] **Step 1: Teste da lista**

```java
    @Test
    void listaTrazTotalDePessoasEVagasRestantes() {
        Evento e = evento(10);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(List.of());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(4L);

        ListaInscritosResponse r = service.listarInscritos(eventoId, igrejaId);

        assertThat(r.totalPessoas()).isEqualTo(4);
        assertThat(r.vagas()).isEqualTo(10);
        assertThat(r.vagasRestantes()).isEqualTo(6);
    }

    @Test
    void vagasRestantesEhNuloQuandoNaoHaLimite() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId))
                .thenReturn(Optional.of(evento(null)));
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(List.of());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(50L);

        assertThat(service.listarInscritos(eventoId, igrejaId).vagasRestantes()).isNull();
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL — `listarInscritos` não existe.

- [ ] **Step 3: Implementar no serviço**

```java
    @Transactional(readOnly = true)
    public ListaInscritosResponse listarInscritos(UUID eventoId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<InscritoResponse> inscritos = inscricaoRepository.listarPorEvento(eventoId)
                .stream().map(InscritoResponse::from).toList();

        long total = inscricaoRepository.contarPessoasConfirmadas(eventoId);
        Integer restantes = evento.getVagas() == null
                ? null
                : Math.max(0, evento.getVagas() - (int) total);

        return new ListaInscritosResponse(total, evento.getVagas(), restantes, inscritos);
    }
```

- [ ] **Step 4: Criar o controller**

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.inscricao.DTOs.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InscricaoController {

    private final InscricaoService inscricaoService;
    private final UsuarioAutenticado usuarioAutenticado;

    /**
     * Auto-inscrição. NÃO recebe identidade alguma no corpo — o membro vem do JWT.
     * É o que torna esta rota impossível de usar errado: não há campo a adulterar.
     */
    @PostMapping("/eventos/{eventoId}/inscricoes")
    public ResponseEntity<MinhaInscricaoResponse> inscrever(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        var response = inscricaoService.inscrever(
                eventoId, usuario.getMembro().getId(), null, usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/eventos/{eventoId}/inscricoes/minha")
    public ResponseEntity<MinhaInscricaoResponse> minhaInscricao(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        return ResponseEntity.ok(
                inscricaoService.minhaInscricao(eventoId, usuario.getMembro().getId()));
    }

    /** Inscrever outras pessoas: aqui os ids VÊM do cliente, então são validados um a um. */
    @PostMapping("/eventos/{eventoId}/inscricoes/membros")
    public ResponseEntity<Void> inscreverMembros(@PathVariable UUID eventoId,
                                                 @Valid @RequestBody InscreverMembrosRequest data) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.inscreverMembros(eventoId, data.membroIds(),
                usuario.getId(), usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/eventos/{eventoId}/inscricoes/{inscricaoId}/acompanhantes")
    public ResponseEntity<AcompanhanteResponse> adicionarAcompanhante(
            @PathVariable UUID eventoId,
            @PathVariable UUID inscricaoId,
            @Valid @RequestBody AcompanhanteRequest data) {
        var usuario = usuarioAutenticado.get();
        var response = inscricaoService.adicionarAcompanhante(
                inscricaoId, data, usuario.getId(), usuario.getIgreja().getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Lista de inscritos — ADMIN e LÍDER só (travado no SecurityConfig). */
    @GetMapping("/eventos/{eventoId}/inscricoes")
    public ResponseEntity<ListaInscritosResponse> listar(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(inscricaoService.listarInscritos(
                eventoId, usuarioAutenticado.getIgrejaId()));
    }

    @DeleteMapping("/inscricoes/{id}")
    public ResponseEntity<Void> cancelar(@PathVariable UUID id) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.cancelar(id, usuario.getId(), usuario.getMembro().getId(),
                usuario.getRole().getNome(), usuario.getIgreja().getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/acompanhantes/{id}")
    public ResponseEntity<Void> removerAcompanhante(@PathVariable UUID id) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.removerAcompanhante(id, usuario.getMembro().getId(),
                usuario.getRole().getNome(), usuario.getIgreja().getId());
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 5: Rodar a suíte inteira**

Run: `mvn -q test; echo "EXIT=$?"`
Expected: `EXIT=0`. A suíte tinha 97 testes; deve passar de 110.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/ \
        src/test/java/com/domus/api/modules/evento/inscricao/
git commit -m "feat(inscricao): lista de inscritos e endpoints"
```

---

### Task 8: Teste de concorrência (a última vaga)

**Files:**
- Create: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoConcorrenciaTest.java`

**Por que é tarefa própria:** é o único teste que prova o lock. Testes com mock não provam
nada sobre concorrência — precisam de Postgres real e duas transações de verdade. Segue o
padrão do `HierarquiaIgrejaTriggerTest`.

**Interfaces:**
- Consumes: `InscricaoService.inscrever(...)` (Task 5)

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova que duas inscrições simultâneas na ÚLTIMA vaga não passam as duas.
 *
 * <p>Precisa de Postgres real e de duas transações de verdade: com mock, o teste passaria
 * mesmo sem o lock, porque não haveria concorrência nenhuma para observar.
 */
@SpringBootTest
class InscricaoConcorrenciaTest {

    @Autowired InscricaoService inscricaoService;
    @Autowired EntityManager em;

    @Test
    void duasInscricoesNaUltimaVaga_apenasUmaVence() throws Exception {
        // Cenário: criar igreja, evento com vagas=1 e DOIS membros.
        // Usar SQL nativo (como o HierarquiaIgrejaTriggerTest) para montar o cenário
        // sem depender das regras de serviço.
        Dados d = criarCenarioComUmaVaga();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger recusas = new AtomicInteger();

        Callable<Void> tentativa = () -> {
            largada.await();  // as duas threads partem juntas
            try {
                inscricaoService.inscrever(d.eventoId, d.membroA, null, d.igrejaId);
                sucessos.incrementAndGet();
            } catch (BusinessException e) {
                recusas.incrementAndGet();
            }
            return null;
        };
        Callable<Void> tentativaB = () -> {
            largada.await();
            try {
                inscricaoService.inscrever(d.eventoId, d.membroB, null, d.igrejaId);
                sucessos.incrementAndGet();
            } catch (BusinessException e) {
                recusas.incrementAndGet();
            }
            return null;
        };

        Future<Void> f1 = pool.submit(tentativa);
        Future<Void> f2 = pool.submit(tentativaB);
        largada.countDown();
        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(sucessos.get()).isEqualTo(1);
        assertThat(recusas.get()).isEqualTo(1);
    }

    private record Dados(UUID igrejaId, UUID eventoId, UUID membroA, UUID membroB) {}

    @Transactional
    Dados criarCenarioComUmaVaga() {
        UUID igrejaId = (UUID) em.createNativeQuery("""
                INSERT INTO igreja (nome, email) VALUES ('Concorrencia', 'c@c.test') RETURNING id
                """).getSingleResult();

        UUID eventoId = (UUID) em.createNativeQuery("""
                INSERT INTO evento (igreja_id, titulo, inicio_em, vagas)
                VALUES (:ig, 'Retiro', NOW() + INTERVAL '10 days', 1) RETURNING id
                """).setParameter("ig", igrejaId).getSingleResult();

        UUID a = criarMembro(igrejaId, "Ana");
        UUID b = criarMembro(igrejaId, "Bruno");
        return new Dados(igrejaId, eventoId, a, b);
    }

    private UUID criarMembro(UUID igrejaId, String nome) {
        return (UUID) em.createNativeQuery("""
                INSERT INTO membro (igreja_id, nome, email, status)
                VALUES (:ig, :nome, :email, 'ATIVO') RETURNING id
                """)
                .setParameter("ig", igrejaId)
                .setParameter("nome", nome)
                .setParameter("email", nome.toLowerCase() + "@c.test")
                .getSingleResult();
    }
}
```

- [ ] **Step 2: Rodar**

Run: `mvn -q test -Dtest=InscricaoConcorrenciaTest; echo "EXIT=$?"`
Expected: `EXIT=0` — exatamente 1 sucesso e 1 recusa.

> **Se falhar com 2 sucessos**, o lock não está sendo aplicado: verifique que
> `inscrever` usa `buscarComLock` (não `findByIdAndIgrejaId`) e que o método é `@Transactional`.
> Um `@Transactional` ausente faz o lock ser liberado antes do insert — o pior caso, porque o
> código *parece* correto.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/domus/api/modules/evento/inscricao/InscricaoConcorrenciaTest.java
git commit -m "test(inscricao): concorrência na última vaga"
```

---

### Task 9: Campos novos nos DTOs de evento e membro

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Modify: DTOs de membro (`MembroRequest`/`MembroResponse` — confirmar nomes exatos em `modules/membro/DTOs/`)
- Modify: `MembroService` (cadastrar e atualizar)

**Interfaces:**
- Produces: `EventoResponse` com `vagas`, `preco`, `exclusivoMembros`, `exclusivoBatizados`; `MembroResponse` com `batizado`, `dataBatismo`.

- [ ] **Step 1: `EventoRequest`** — acrescentar ao record:

```java
        @Positive(message = "As vagas devem ser maiores que zero.")
        Integer vagas,
        @Positive(message = "O valor deve ser maior que zero.")
        java.math.BigDecimal preco,
        Boolean exclusivoMembros,
        Boolean exclusivoBatizados
```

Import: `jakarta.validation.constraints.Positive`.

- [ ] **Step 2: `EventoResponse`** — acrescentar os 4 campos e mapear em `from`:

```java
        Integer vagas,
        java.math.BigDecimal preco,
        boolean exclusivoMembros,
        boolean exclusivoBatizados,
```

```java
                e.getVagas(), e.getPreco(), e.isExclusivoMembros(), e.isExclusivoBatizados(),
```

- [ ] **Step 3: `EventoService`** — em `cadastrarEvento` (builder) e `atualizarEvento` (setters):

```java
                .vagas(data.vagas())
                .preco(data.preco())
                .exclusivoMembros(Boolean.TRUE.equals(data.exclusivoMembros()))
                .exclusivoBatizados(Boolean.TRUE.equals(data.exclusivoBatizados()))
```

```java
        evento.setVagas(data.vagas());
        evento.setPreco(data.preco());
        evento.setExclusivoMembros(Boolean.TRUE.equals(data.exclusivoMembros()));
        evento.setExclusivoBatizados(Boolean.TRUE.equals(data.exclusivoBatizados()));
```

> `Boolean.TRUE.equals(...)` trata o `null` do JSON como `false` sem NPE.

- [ ] **Step 4: DTOs de membro** — mesmo padrão: `Boolean batizado`, `LocalDate dataBatismo` no
request; `boolean batizado`, `LocalDate dataBatismo` no response; setters no service.

- [ ] **Step 5: Rodar a suíte**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/
git commit -m "feat(evento): vagas, preço e restrições; batizado no membro"
```

---

### Task 10: Front — tipos, serviço e hooks

**Files:**
- Create: `frontend/src/types/inscricao.type.ts`
- Create: `frontend/src/services/inscricao.service.ts`
- Create: `frontend/src/hooks/inscricao/useMinhaInscricao.ts`, `useInscrever.ts`, `useInscreverMembros.ts`, `useCancelarInscricao.ts`, `useAcompanhante.ts`, `useListaInscritos.ts`
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/lib/cacheInvalidacao.ts`
- Modify: `frontend/src/types/evento.type.ts`

**Interfaces:**
- Produces: `useMinhaInscricao(eventoId)`, `useInscrever()`, `useInscreverMembros()`, `useCancelarInscricao()`, `useAdicionarAcompanhante()`, `useRemoverAcompanhante()`, `useListaInscritos(eventoId)`

- [ ] **Step 1: Registrar a entidade no mapa de cache**

Em `cacheInvalidacao.ts`, adicionar `'inscricao'` ao type `Entidade` e ao mapa:

```ts
  inscricao: [
    ['inscricao'],      // minha inscrição e lista de inscritos
    ['eventos'],        // o card mostra vagas restantes
    ['evento'],
    ['inicio'],         // eventos da semana no início
  ],
```

> É o único lugar que precisa saber disso — não invalide manualmente nas mutações.

- [ ] **Step 2: Endpoints**

Em `endpoints.ts`, dentro do objeto raiz:

```ts
  inscricoes: {
    INSCREVER: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    MINHA: (eventoId: string) => `/eventos/${eventoId}/inscricoes/minha`,
    INSCREVER_MEMBROS: (eventoId: string) => `/eventos/${eventoId}/inscricoes/membros`,
    ACOMPANHANTES: (eventoId: string, inscricaoId: string) =>
      `/eventos/${eventoId}/inscricoes/${inscricaoId}/acompanhantes`,
    LISTAR: (eventoId: string) => `/eventos/${eventoId}/inscricoes`,
    CANCELAR: (id: string) => `/inscricoes/${id}`,
    REMOVER_ACOMPANHANTE: (id: string) => `/acompanhantes/${id}`,
  },
```

- [ ] **Step 3: Tipos**

```ts
// types/inscricao.type.ts
export interface AcompanhanteResponse {
  id: string
  nome: string
  telefone: string | null
}

export interface MinhaInscricaoResponse {
  id: string | null
  inscrito: boolean
  acompanhantes: AcompanhanteResponse[]
}

export interface InscritoResponse {
  id: string
  membroId: string
  nome: string
  foto: string | null
  /** null = a pessoa se inscreveu sozinha */
  inscritoPorUsuarioId: string | null
  inscritoEm: string
  acompanhantes: AcompanhanteResponse[]
}

export interface ListaInscritosResponse {
  totalPessoas: number
  vagas: number | null
  /** null = evento sem limite de vagas */
  vagasRestantes: number | null
  inscritos: InscritoResponse[]
}

export interface AcompanhanteRequest {
  nome: string
  telefone?: string
}
```

Em `evento.type.ts`, acrescentar aos dois interfaces:

```ts
  vagas: number | null
  preco: number | null
  exclusivoMembros: boolean
  exclusivoBatizados: boolean
```

(no `EventoRequest`, como opcionais: `vagas?: number` etc.)

- [ ] **Step 4: Serviço**

```ts
// services/inscricao.service.ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  AcompanhanteRequest, AcompanhanteResponse,
  ListaInscritosResponse, MinhaInscricaoResponse,
} from '@/types/inscricao.type'

export const inscricaoService = {
  minha: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.get<MinhaInscricaoResponse>(Endpoints.inscricoes.MINHA(eventoId)).then(r => r.data),

  inscrever: (eventoId: string): Promise<MinhaInscricaoResponse> =>
    api.post<MinhaInscricaoResponse>(Endpoints.inscricoes.INSCREVER(eventoId)).then(r => r.data),

  inscreverMembros: (eventoId: string, membroIds: string[]): Promise<void> =>
    api.post(Endpoints.inscricoes.INSCREVER_MEMBROS(eventoId), { membroIds }).then(() => undefined),

  adicionarAcompanhante: (
    eventoId: string, inscricaoId: string, data: AcompanhanteRequest,
  ): Promise<AcompanhanteResponse> =>
    api.post<AcompanhanteResponse>(
      Endpoints.inscricoes.ACOMPANHANTES(eventoId, inscricaoId), data,
    ).then(r => r.data),

  listar: (eventoId: string): Promise<ListaInscritosResponse> =>
    api.get<ListaInscritosResponse>(Endpoints.inscricoes.LISTAR(eventoId)).then(r => r.data),

  cancelar: (id: string): Promise<void> =>
    api.delete(Endpoints.inscricoes.CANCELAR(id)).then(() => undefined),

  removerAcompanhante: (id: string): Promise<void> =>
    api.delete(Endpoints.inscricoes.REMOVER_ACOMPANHANTE(id)).then(() => undefined),
}
```

- [ ] **Step 5: Hooks**

```ts
// hooks/inscricao/useMinhaInscricao.ts
import { useQuery } from '@tanstack/react-query'
import { inscricaoService } from '@/services/inscricao.service'

export function useMinhaInscricao(eventoId: string | undefined) {
  return useQuery({
    queryKey: ['inscricao', 'minha', eventoId],
    queryFn: () => inscricaoService.minha(eventoId!),
    enabled: !!eventoId,
  })
}
```

```ts
// hooks/inscricao/useInscrever.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { inscricaoService } from '@/services/inscricao.service'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { notificar } from '@/components/common/Notificacao/notificar'
import { extrairMensagemErro } from '@/lib/erros'

export function useInscrever() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (eventoId: string) => inscricaoService.inscrever(eventoId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      notificar.sucesso('Presença confirmada', 'Você está inscrito neste evento.')
    },
    onError: (erro) => notificar.erro('Não foi possível confirmar', extrairMensagemErro(erro)),
  })
}
```

> Confirme o nome do helper de erro já usado no projeto (`extrairMensagemErro` ou equivalente
> em `lib/`) e reuse — não crie um segundo.

Os demais hooks seguem exatamente este formato, trocando `mutationFn` e as mensagens:
- `useInscreverMembros` → `({eventoId, membroIds}) => inscricaoService.inscreverMembros(eventoId, membroIds)`; sucesso: `'Membros inscritos'`
- `useCancelarInscricao` → `(id) => inscricaoService.cancelar(id)`; sucesso: `'Inscrição cancelada'`
- `useAdicionarAcompanhante` → `({eventoId, inscricaoId, data}) => inscricaoService.adicionarAcompanhante(...)`; sucesso: `'Convidado adicionado'`
- `useRemoverAcompanhante` → `(id) => inscricaoService.removerAcompanhante(id)`; sucesso: `'Convidado removido'`
- `useListaInscritos(eventoId)` → `useQuery` com `queryKey: ['inscricao', 'lista', eventoId]`

- [ ] **Step 6: Verificar tipos e commitar**

Run: `cd frontend && npx tsc --noEmit; echo "EXIT=$?"` → `EXIT=0`

```bash
git add frontend/src/types frontend/src/services frontend/src/hooks frontend/src/lib
git commit -m "feat(inscricao): tipos, serviço e hooks no front"
```

---

### Task 11: Front — botão Confirmar presença

**Files:**
- Create: `frontend/src/components/module/eventos/BotaoConfirmarPresenca.tsx` (+ `.module.css`)
- Modify: `frontend/src/app/(app)/inicio/ModalEventoResumo.tsx:145-148`
- Modify: `frontend/src/app/(app)/eventos/(detalhe)/DrawerDetalheEvento.tsx`

**Interfaces:**
- Consumes: `useMinhaInscricao`, `useInscrever`, `useCancelarInscricao` (Task 10)
- Produces: `<BotaoConfirmarPresenca eventoId={...} />`

- [ ] **Step 1: Criar o componente**

```tsx
'use client'

import { CheckCircle2, Loader2, X } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import styles from './BotaoConfirmarPresenca.module.css'

interface Props {
  eventoId: string
  /** Evento que já passou não aceita inscrição. */
  encerrado?: boolean
  esgotado?: boolean
}

export function BotaoConfirmarPresenca({ eventoId, encerrado, esgotado }: Props) {
  const { data, isLoading } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever()
  const cancelar = useCancelarInscricao()

  const ocupado = inscrever.isPending || cancelar.isPending

  if (isLoading) {
    return (
      <button type="button" className={styles.botao} disabled>
        <Loader2 size={18} className={styles.girando} aria-hidden="true" />
        Carregando…
      </button>
    )
  }

  if (data?.inscrito && data.id) {
    return (
      <div className={styles.grupo}>
        <span className={styles.confirmado}>
          <CheckCircle2 size={18} aria-hidden="true" />
          Presença confirmada
        </span>
        <button
          type="button"
          className={styles.cancelar}
          onClick={() => cancelar.mutate(data.id!)}
          disabled={ocupado}
        >
          <X size={16} aria-hidden="true" />
          Cancelar
        </button>
      </div>
    )
  }

  if (encerrado) {
    return <button type="button" className={styles.botao} disabled>Evento encerrado</button>
  }
  if (esgotado) {
    return <button type="button" className={styles.botao} disabled>Vagas esgotadas</button>
  }

  return (
    <button
      type="button"
      className={styles.botao}
      onClick={() => inscrever.mutate(eventoId)}
      disabled={ocupado}
    >
      <CheckCircle2 size={18} aria-hidden="true" />
      {inscrever.isPending ? 'Confirmando…' : 'Confirmar presença'}
    </button>
  )
}
```

- [ ] **Step 2: CSS**

Copiar os tokens de `ModalEventoResumo.module.css` (`.botaoConfirmar`) para manter a
aparência já aprovada, e acrescentar `.grupo` (flex, gap), `.confirmado` (verde de sucesso),
`.cancelar` (botão texto discreto) e `.girando` (`animation: girar 1s linear infinite`).

No mobile (`@media (max-width: 640px)`), `.grupo` empilha (`flex-direction: column`).

- [ ] **Step 3: Trocar o botão desabilitado no `ModalEventoResumo`**

Substituir o bloco das linhas 140-148 (o comentário "Desabilitado porque a inscrição em
evento não existe" e o `<button ... disabled>`) por:

```tsx
            <BotaoConfirmarPresenca
              eventoId={eventoId}
              encerrado={new Date(evento.inicioEm) < new Date()}
            />
```

Import: `import { BotaoConfirmarPresenca } from '@/components/module/eventos/BotaoConfirmarPresenca'`

- [ ] **Step 4: Verificar**

Run: `cd frontend && npx tsc --noEmit && npx eslint src --max-warnings=0; echo "EXIT=$?"` → `EXIT=0`

Testar no navegador: abrir `/inicio`, clicar em "Ver detalhes" num evento, confirmar presença,
ver o estado mudar para "Presença confirmada", cancelar e ver voltar.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/module/eventos frontend/src/app/\(app\)/inicio
git commit -m "feat(inscricao): botão de confirmar presença"
```

---

### Task 12: Front — modais de inscrever membros e convidado externo

**Files:**
- Create: `frontend/src/components/module/eventos/ModalInscreverMembros.tsx` (+ CSS)
- Create: `frontend/src/components/module/eventos/ModalConvidadoExterno.tsx` (+ CSS)
- Modify: `frontend/src/app/(app)/inicio/ModalEventoResumo.tsx`

**Interfaces:**
- Consumes: `useMembros` (hook existente de listagem de membros), `useInscreverMembros`, `useAdicionarAcompanhante`

- [ ] **Step 1: `ModalInscreverMembros`** — busca + seleção múltipla

Comportamento exigido:
- campo de busca por nome (`useMembros({ q })`, debounce de 300ms — reusar o padrão de
  debounce já existente na tela de membros)
- lista com checkbox por membro; selecionados ficam destacados
- contador "N selecionados" e botão "Inscrever" desabilitado com zero seleção
- `onSuccess` → fechar o modal

A seleção múltipla é deliberada por desenho: escolher um a um e confirmar é o que impede
inscrição acidental em massa. Não adicionar "selecionar todos".

- [ ] **Step 2: `ModalConvidadoExterno`** — formulário simples

React Hook Form + Zod:

```ts
const esquema = z.object({
  nome: z.string().min(1, 'Informe o nome do convidado.').max(255),
  telefone: z.string().max(20).optional(),
})
```

Aparece só quando `!evento.exclusivoMembros`. Rótulo do botão que o abre:
**"Vou levar alguém de fora"**.

- [ ] **Step 3: Ligar os dois no `ModalEventoResumo`**

Abaixo do `BotaoConfirmarPresenca`, dois botões secundários: "Inscrever membros" e (condicional)
"Vou levar alguém de fora" — este último só habilitado quando o usuário já está inscrito, já que
o convidado pendura na inscrição dele.

- [ ] **Step 4: Verificar (incluindo mobile)**

Run: `cd frontend && npx tsc --noEmit && npx eslint src --max-warnings=0; echo "EXIT=$?"` → `EXIT=0`

No DevTools, viewport de 375px: modal com padding reduzido, lista rolável, botões empilhados,
sem overflow horizontal.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(inscricao): modais de inscrever membros e convidado externo"
```

---

### Task 13: Front — lista de inscritos (ADMIN/LÍDER)

**Files:**
- Create: `frontend/src/app/(app)/eventos/[id]/inscritos/page.tsx` (+ CSS)
- Modify: `frontend/src/app/(app)/eventos/(detalhe)/DrawerDetalheEvento.tsx` — link "Ver inscritos"

**Interfaces:**
- Consumes: `useListaInscritos`, `useCancelarInscricao`, `useRemoverAcompanhante`

- [ ] **Step 1: Construir a página**

Elementos exigidos:
- cabeçalho com título do evento e o contador: `{totalPessoas} de {vagas} vagas` (ou
  `{totalPessoas} inscritos` quando `vagas === null`)
- tabela: Nome | Inscrito por | Data | Ações
- **"Inscrito por"**: `inscritoPorUsuarioId === null` → renderizar `Ele mesmo`; caso contrário,
  o nome de quem inscreveu
- acompanhantes aparecem **indentados sob o responsável**, com um selo "Convidado" — é o que
  responde "de onde veio essa pessoa"
- cancelar usa `ModalConfirmacaoCritica` (nunca `window.confirm`)

- [ ] **Step 2: Mobile**

A tabela vira cards no viewport pequeno, com `grid-template-areas` — mesmo padrão aplicado em
`movimentacoes.module.css`. Não usar `display: none` para esconder coluna: o dado some em vez
de se reorganizar.

- [ ] **Step 3: Verificar**

Run: `cd frontend && npx tsc --noEmit && npx eslint src --max-warnings=0 && npx next build; echo "EXIT=$?"` → `EXIT=0`

Testar com conta MEMBRO: a rota deve dar 403 na API (a tela não deve ser alcançável pelo menu).

- [ ] **Step 4: Commit**

```bash
git add frontend/src
git commit -m "feat(inscricao): tela de lista de inscritos"
```

---

### Task 14: Front — campos novos nos formulários

**Files:**
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx` (+ CSS)
- Modify: o formulário de membro (`components/module/membros/MembroForm.tsx` — confirmar caminho)

**Interfaces:**
- Consumes: `EventoRequest` e `MembroRequest` atualizados (Tasks 9 e 10)

- [ ] **Step 1: Campos no `EventoForm`**

Nova seção "Inscrição":
- `vagas` (number, opcional) — legenda: *"Deixe vazio para não limitar."*
- `preco` (decimal, opcional) — legenda: *"Informativo. O pagamento é combinado com a igreja —
  informe PIX ou contato na descrição do evento."*
- checkbox `exclusivoMembros` — *"Somente membros da igreja"*
- checkbox `exclusivoBatizados` — *"Somente membros batizados"*, com o aviso **fixo** logo abaixo:

```tsx
{/*
  Aviso SEMPRE visível com o toggle ligado — não condicionado a contagem de batizados.
  É declaração da regra, não detecção de estado vazio: sem consulta extra, vale igual para
  igreja com 0 ou 300 batizados, e não vira alarme que o usuário aprende a ignorar.
*/}
{exclusivoBatizados && (
  <p className={styles.avisoRegra}>
    Membros que não estiverem marcados como batizados não poderão se inscrever nem ser inscritos.
  </p>
)}
```

Zod: `vagas: z.coerce.number().int().positive().optional()`,
`preco: z.coerce.number().positive().optional()`.

- [ ] **Step 2: Campos no formulário de membro**

- checkbox `batizado` — *"Batizado"*
- `dataBatismo` (date, opcional), visível só quando `batizado` está marcado — legenda:
  *"Opcional."*

- [ ] **Step 3: Verificar (incluindo mobile)**

Run: `cd frontend && npx tsc --noEmit && npx eslint src --max-warnings=0 && npx next build; echo "EXIT=$?"` → `EXIT=0`

Viewport 375px: a grade do formulário colapsa para 1 coluna.

Teste ponta a ponta: criar evento com 2 vagas → inscrever 2 pessoas → a terceira recebe
"vagas esgotadas".

- [ ] **Step 4: Commit**

```bash
git add frontend/src
git commit -m "feat(evento): campos de vagas, preço e restrições no formulário"
```

---

### Task 15: Documentação

**Files:**
- Modify: `CLAUDE.md` — diagrama ER (estado atual passa a **V15**)
- Modify: `docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`

- [ ] **Step 1: Atualizar o diagrama ER**

Adicionar as entidades `INSCRICAO_EVENTO` e `ACOMPANHANTE_INSCRICAO` com as relações
`EVENTO ||--o{ INSCRICAO_EVENTO`, `MEMBRO ||--o{ INSCRICAO_EVENTO`,
`INSCRICAO_EVENTO ||--o{ ACOMPANHANTE_INSCRICAO`; os 4 campos novos em `EVENTO` e os 2 em
`MEMBRO`; trocar "Estado atual: **V13**" por "**V15**".

- [ ] **Step 2: Marcar o item da Fase 2 como feito** no `CLAUDE.md`, no estilo dos demais
(`**FEITO**` com data e o que ficou de fora).

- [ ] **Step 3: Registrar no BACKLOG** o que ficou pendente: lista de espera quando esgotar,
notificação por e-mail ao inscrito, e exportar lista de inscritos.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md docs/
git commit -m "docs: inscrição em evento no diagrama ER e no roadmap"
```

---

## Prompt para o Stitch

Colar no Stitch para gerar o protótipo das telas novas. Descreve as três telas de uma vez,
mantendo a identidade visual já usada no Domus.

```
Design three screens for "Domus", a church management SaaS in Brazilian Portuguese.
All copy must be in Brazilian Portuguese.

VISUAL STYLE
Clean, modern admin dashboard. Light background (#F8FAFC), white cards with soft shadows
and 12px rounded corners. Primary color is a strong blue (#2563EB). Sans-serif type.
Generous whitespace, compact and information-dense but never cramped. Sidebar navigation
on the left with the DOMUS logo at the top. Subtle borders (#E2E8F0), muted secondary
text (#64748B). Avatars are circular with initials as fallback.

SCREEN 1 — Modal: "Inscrever membros"
A centered modal over a dimmed page.
- Header: title "Inscrever membros", subtitle "Selecione quem você quer inscrever neste
  evento", and a close X button.
- A search field with a magnifier icon, placeholder "Buscar membro pelo nome...".
- Below it, a scrollable list of members. Each row: circular avatar, member name, and a
  small muted line beneath with their ministry. On the right of each row, a checkbox.
  Selected rows have a light blue background and a blue checked checkbox.
- Show 6 rows, 2 of them selected.
- Sticky footer inside the modal: on the left the muted text "2 selecionados", on the
  right a secondary "Cancelar" button and a primary blue "Inscrever" button.

SCREEN 2 — Modal: "Vou levar alguém de fora"
A smaller centered modal.
- Header: title "Vou levar alguém de fora", subtitle "Essa pessoa entra na lista como seu
  convidado e ocupa uma vaga."
- Field "Nome do convidado" (required), placeholder "Ex: Maria Souza".
- Field "Telefone" marked as optional, placeholder "(00) 00000-0000".
- An informational box with a soft blue background and an info icon reading: "Convidados
  aparecem na lista de inscritos vinculados ao seu nome."
- Footer: secondary "Cancelar" and primary blue "Adicionar convidado".

SCREEN 3 — Page: "Inscritos"
A full dashboard page with the left sidebar visible.
- Page header: back arrow, title "Inscritos", and beneath it the event name
  "Retiro de Jovens 2026" in muted text.
- A row of three small stat cards: "Total de pessoas: 34", "Vagas: 50",
  "Vagas restantes: 16". The last one has a subtle blue accent.
- A table with columns: "Nome", "Inscrito por", "Data", and an actions column.
  Rows show a circular avatar plus the person's name.
  In "Inscrito por", some rows say "Ele mesmo" in muted text, others show another
  person's name.
  Guest rows are visually indented one level under the member who brought them, with a
  small gray pill badge reading "Convidado" next to the name, and a thin vertical
  connector line showing they belong to the row above.
  The actions column has a subtle "Cancelar" text button.
- Show about 6 member rows, two of which have one indented guest each.
- Include an empty-state illustration variant of this page with the message
  "Ninguém se inscreveu ainda".

Also produce mobile versions of all three screens (375px wide). On mobile the table from
Screen 3 becomes stacked cards: each card shows the avatar and name on the first line,
"Inscrito por" and date as labeled rows beneath, and the cancel action at the bottom.
Guests appear as nested mini-cards inside their host's card.
```

---

## Self-Review

**Cobertura da spec:** modelo de dados → Task 1; concorrência → Tasks 3, 5, 8; cancelamento e
reinscrição → Tasks 5, 6; acompanhantes → Task 6; `batizado` → Tasks 1, 9, 14; endpoints →
Task 7; telas → Tasks 11–14; testes listados na spec → Tasks 2, 5, 6, 7, 8.

**Riscos conhecidos:**
1. **Ordenação no `SecurityConfig`** (Task 2) — sem ela, tudo compila e nada funciona.
2. **`@Transactional` no `inscrever`** — sem ele o lock é liberado antes do insert, e o código
   *parece* correto. É o cenário que a Task 8 existe para pegar.
3. **Nomes a confirmar no código existente:** helper de erro do front, caminho do formulário de
   membro, nome do helper de autenticação no `SecurityFilterTest`. Reusar o que existe.
