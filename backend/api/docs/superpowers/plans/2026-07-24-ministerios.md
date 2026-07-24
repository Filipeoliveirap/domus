# Ministérios da Igreja — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Substituir o campo livre `pessoa.ministerio` por um cadastro estruturado de ministérios da igreja, com liderança por ministério (autorização por recurso), fluxo de pedido de entrada, e remoção do dado antigo.

**Architecture:** Módulo novo `com.domus.api.modules.ministerio` no backend, seguindo o padrão já usado por `LocalEvento`/`CategoriaFinanceira` (tabela por igreja, nome único case/acento-insensitive, soft delete, `criado_por`/`atualizado_por`). Uma segunda tabela `ministerio_membro` modela o vínculo N-para-N pessoa↔ministério, carregando `papel` (LIDER/MEMBRO) e `status` (PENDENTE/ATIVO) — sem tabela de log genérica. Autorização por recurso ("é líder deste ministério") vive só no `MinisterioService`, não num framework de permissão. Frontend: páginas novas `/ministerios` (lista, gestão de cadastro) e `/ministerios/[id]` (detalhe: membros, pedidos pendentes, botão de pedir entrada), seguindo o padrão de `/eventos/locais`. Remoção do `MinisterioInput` de texto livre do formulário de pessoa.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL; Next.js, TypeScript, TanStack Query, CSS Modules.

## Global Constraints

- `igreja_id` sempre extraído do usuário autenticado (`UsuarioAutenticado.getIgrejaId()`), nunca do corpo da requisição.
- Camadas `controller → service → repository`; services retornam DTOs, nunca entidades JPA.
- Soft delete (`deleted_at`) na entidade `Ministerio`; `ministerio_membro` usa **hard delete** (é uma relação, não uma entidade de domínio com histórico — decisão do spec).
- Perfis de acesso: `ADMIN_IGREJA`, `LIDER`, `ACESSO_COMUM` — nenhum dá poder automático sobre um ministério específico; "líder do ministério X" é autorização por recurso, checada no service.
- Responsividade obrigatória em toda tela nova de front (tabelas viram cards no mobile, headers empilham, `min-width: 0` na cadeia flex/grid).
- Sem tabela de log de atividade genérica — só `criado_por_usuario_id`/`atualizado_por_usuario_id`.
- Não commitar antes do autor testar — ver spec `docs/superpowers/specs/2026-07-24-ministerios-design.md`.

---

## Backend

### Task 1: Migration — tabelas `ministerio`/`ministerio_membro` e remoção de `pessoa.ministerio`

**Files:**
- Create: `src/main/resources/db/migration/V9__ministerio.sql`
- Modify: `src/test/resources/` — nenhum arquivo específico; a suíte de testes de integração roda migrations automaticamente via Flyway (confirmar no passo de teste).

**Interfaces:**
- Produces: tabelas `ministerio(id, igreja_id, nome, criado_por_usuario_id, atualizado_por_usuario_id, created_at, updated_at, deleted_at)` e `ministerio_membro(id, igreja_id, ministerio_id, pessoa_id, papel, status, criado_por_usuario_id, atualizado_por_usuario_id, created_at, updated_at)`; remove `pessoa.ministerio`.

- [ ] **Step 1: Escrever a migration**

```sql
-- V9__ministerio.sql
-- imutavel_unaccent já existe (criada em V3__evento_enriquecido.sql) — só reaproveitar.

CREATE TABLE ministerio (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(150) NOT NULL,
    criado_por_usuario_id UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_ministerio_igreja_nome
    ON ministerio (igreja_id, LOWER(imutavel_unaccent(nome)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_ministerio_igreja ON ministerio (igreja_id) WHERE deleted_at IS NULL;

CREATE TABLE ministerio_membro (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id     UUID NOT NULL REFERENCES igreja(id),
    ministerio_id UUID NOT NULL REFERENCES ministerio(id),
    pessoa_id     UUID NOT NULL REFERENCES pessoa(id),
    papel         VARCHAR(20) NOT NULL DEFAULT 'MEMBRO',
    status        VARCHAR(20) NOT NULL DEFAULT 'ATIVO',
    criado_por_usuario_id UUID REFERENCES usuario(id),
    atualizado_por_usuario_id UUID REFERENCES usuario(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_ministerio_membro_papel CHECK (papel IN ('LIDER', 'MEMBRO')),
    CONSTRAINT chk_ministerio_membro_status CHECK (status IN ('PENDENTE', 'ATIVO')),
    CONSTRAINT uq_ministerio_membro_pessoa UNIQUE (ministerio_id, pessoa_id)
);

CREATE INDEX ix_ministerio_membro_pessoa ON ministerio_membro (pessoa_id);
CREATE INDEX ix_ministerio_membro_ministerio ON ministerio_membro (ministerio_id);

-- Descarta o texto livre antigo (decisão explícita do spec — sem migrar dado).
ALTER TABLE pessoa DROP COLUMN ministerio;
```

- [ ] **Step 2: Validar que a migration sobe limpa**

Run: `mvn -q flyway:migrate` (ou suba a aplicação local com `mvn spring-boot:run` e observe o log de migrations)
Expected: `V9__ministerio` aplicada sem erro; nenhuma constraint violada no banco de dev.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V9__ministerio.sql
git commit -m "feat(ministerio): migration das tabelas ministerio e ministerio_membro, remove pessoa.ministerio"
```

---

### Task 2: Entidades JPA `Ministerio` e `MinisterioMembro`

**Files:**
- Create: `src/main/java/com/domus/api/modules/ministerio/Ministerio.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/MinisterioMembro.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/Papel.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/StatusMembro.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/MinisterioRepository.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/MinisterioMembroRepository.java`

**Interfaces:**
- Consumes: `com.domus.api.modules.igreja.Igreja`, `com.domus.api.modules.pessoa.Pessoa`, `com.domus.api.modules.usuario.Usuario`.
- Produces: `Ministerio` (getters: `getId()`, `getIgreja()`, `getNome()`, `setNome(String)`), `MinisterioMembro` (getters: `getId()`, `getMinisterio()`, `getPessoa()`, `getPapel()`, `setPapel(Papel)`, `getStatus()`, `setStatus(StatusMembro)`), enums `Papel{LIDER,MEMBRO}` e `StatusMembro{PENDENTE,ATIVO}`, `MinisterioRepository.findByIgrejaIdOrderByNomeAsc(UUID)`, `MinisterioRepository.findByIdAndIgrejaId(UUID,UUID)`, `MinisterioMembroRepository.findByMinisterioIdAndPessoaId(UUID,UUID)`, `MinisterioMembroRepository.findByMinisterioIdOrderByPapelAsc(UUID)` (lista membros — filtra `status` no service), `MinisterioMembroRepository.findByPessoaIdAndIgrejaId(UUID,UUID)` (ministérios ativos de uma pessoa).

- [ ] **Step 1: Criar os enums**

```java
// src/main/java/com/domus/api/modules/ministerio/Papel.java
package com.domus.api.modules.ministerio;

public enum Papel {
    LIDER,
    MEMBRO
}
```

```java
// src/main/java/com/domus/api/modules/ministerio/StatusMembro.java
package com.domus.api.modules.ministerio;

public enum StatusMembro {
    PENDENTE,
    ATIVO
}
```

- [ ] **Step 2: Criar a entidade `Ministerio`**

```java
// src/main/java/com/domus/api/modules/ministerio/Ministerio.java
package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ministerio")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE ministerio SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Ministerio {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(nullable = false, length = 150)
    private String nome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Criar a entidade `MinisterioMembro`**

```java
// src/main/java/com/domus/api/modules/ministerio/MinisterioMembro.java
package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Vínculo pessoa↔ministério. Hard delete de verdade (sem soft delete): é uma relação,
 * não uma entidade de domínio com histórico próprio — recusar pedido ou remover membro
 * apaga a linha (ver spec 2026-07-24-ministerios-design.md, seção "Modelo de dados"). */
@Entity
@Table(name = "ministerio_membro")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MinisterioMembro {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ministerio_id", nullable = false)
    private Ministerio ministerio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Papel papel = Papel.MEMBRO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusMembro status = StatusMembro.ATIVO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: Criar os repositories**

```java
// src/main/java/com/domus/api/modules/ministerio/MinisterioRepository.java
package com.domus.api.modules.ministerio;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinisterioRepository extends JpaRepository<Ministerio, UUID> {

    List<Ministerio> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<Ministerio> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
```

```java
// src/main/java/com/domus/api/modules/ministerio/MinisterioMembroRepository.java
package com.domus.api.modules.ministerio;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinisterioMembroRepository extends JpaRepository<MinisterioMembro, UUID> {

    Optional<MinisterioMembro> findByMinisterioIdAndPessoaId(UUID ministerioId, UUID pessoaId);

    List<MinisterioMembro> findByMinisterioIdOrderByPapelAsc(UUID ministerioId);

    List<MinisterioMembro> findByPessoaIdAndIgrejaIdAndStatus(UUID pessoaId, UUID igrejaId, StatusMembro status);

    boolean existsByMinisterioIdAndPessoaIdAndPapelAndStatus(
            UUID ministerioId, UUID pessoaId, Papel papel, StatusMembro status);
}
```

- [ ] **Step 5: Compilar**

Run: `mvn -q -pl . compile`
Expected: BUILD SUCCESS (nenhum teste ainda — só valida que as classes compilam).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/ministerio/Ministerio.java \
        src/main/java/com/domus/api/modules/ministerio/MinisterioMembro.java \
        src/main/java/com/domus/api/modules/ministerio/Papel.java \
        src/main/java/com/domus/api/modules/ministerio/StatusMembro.java \
        src/main/java/com/domus/api/modules/ministerio/MinisterioRepository.java \
        src/main/java/com/domus/api/modules/ministerio/MinisterioMembroRepository.java
git commit -m "feat(ministerio): entidades Ministerio/MinisterioMembro e repositories"
```

---

### Task 3: DTOs

**Files:**
- Create: `src/main/java/com/domus/api/modules/ministerio/DTOs/MinisterioRequest.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/DTOs/MinisterioResponse.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/DTOs/MembroResponse.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/DTOs/MinisterioDetalheResponse.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/DTOs/AdicionarMembroRequest.java`
- Create: `src/main/java/com/domus/api/modules/ministerio/DTOs/AtualizarPapelRequest.java`

**Interfaces:**
- Consumes: `Ministerio`, `MinisterioMembro`, `Papel`, `StatusMembro` (Task 2).
- Produces: `MinisterioRequest(String nome)`, `MinisterioResponse(UUID id, String nome, List<String> lideres, int totalMembros)` com `from(Ministerio)` (básico, sem membros) e `comResumo(Ministerio, List<MinisterioMembro>)` (usado na listagem — nomes dos líderes e contagem de membros ativos, estilo do mockup do Stitch), `MembroResponse(UUID pessoaId, String nome, UUID fotoId, Papel papel)` com `from(MinisterioMembro)`, `MinisterioDetalheResponse(UUID id, String nome, List<MembroResponse> membros, List<MembroResponse> pedidosPendentes, boolean souLiderDesteMinisterio, boolean souMembroAtivo, boolean tenhoPedidoPendente)`, `AdicionarMembroRequest(UUID pessoaId)`, `AtualizarPapelRequest(Papel papel)`.
  > `souMembroAtivo`/`tenhoPedidoPendente` são calculados no backend a partir da pessoa logada — o front (`authStore`) não guarda `pessoaId`, só `id` (usuarioId) e `role` (ver `src/store/authStore.ts`), então esses dois flags evitam qualquer necessidade de o front conhecer o próprio `pessoaId` para decidir se mostra "pedir para entrar".

- [ ] **Step 1: `MinisterioRequest`**

```java
package com.domus.api.modules.ministerio.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MinisterioRequest(
        @NotBlank(message = "O nome do ministério é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome
) {}
```

- [ ] **Step 2: `MinisterioResponse`**

```java
package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Ministerio;
import com.domus.api.modules.ministerio.MinisterioMembro;
import com.domus.api.modules.ministerio.Papel;
import java.util.List;
import java.util.UUID;

public record MinisterioResponse(UUID id, String nome, List<String> lideres, int totalMembros) {
    /** Usado onde só o cadastro básico importa (ex.: `GET /pessoas/{id}/ministerios`) — sem
     * consultar membros, então líderes/contagem vêm zerados. */
    public static MinisterioResponse from(Ministerio ministerio) {
        return new MinisterioResponse(ministerio.getId(), ministerio.getNome(), List.of(), 0);
    }

    /** Usado na listagem (`GET /ministerios`), onde o card mostra líder(es) e quantidade de
     * membros — resumo visual pedido no mockup do Stitch (sem descrição nem frequência: fora
     * do escopo do cadastro, que é só nome). */
    public static MinisterioResponse comResumo(Ministerio ministerio, List<MinisterioMembro> membrosAtivos) {
        List<String> lideres = membrosAtivos.stream()
                .filter(m -> m.getPapel() == Papel.LIDER)
                .map(m -> m.getPessoa().getNome())
                .toList();
        return new MinisterioResponse(ministerio.getId(), ministerio.getNome(), lideres, membrosAtivos.size());
    }
}
```

- [ ] **Step 3: `MembroResponse`**

```java
package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.MinisterioMembro;
import com.domus.api.modules.ministerio.Papel;
import java.util.UUID;

public record MembroResponse(UUID pessoaId, String nome, UUID fotoId, Papel papel) {
    public static MembroResponse from(MinisterioMembro membro) {
        var pessoa = membro.getPessoa();
        UUID fotoId = pessoa.getFoto() != null ? pessoa.getFoto().getId() : null;
        return new MembroResponse(pessoa.getId(), pessoa.getNome(), fotoId, membro.getPapel());
    }
}
```

- [ ] **Step 4: `MinisterioDetalheResponse`**

```java
package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Ministerio;
import java.util.List;
import java.util.UUID;

public record MinisterioDetalheResponse(
        UUID id,
        String nome,
        List<MembroResponse> membros,
        List<MembroResponse> pedidosPendentes,
        boolean souLiderDesteMinisterio,
        boolean souMembroAtivo,
        boolean tenhoPedidoPendente
) {
    public static MinisterioDetalheResponse from(
            Ministerio ministerio, List<MembroResponse> membros,
            List<MembroResponse> pedidosPendentes, boolean souLiderDesteMinisterio,
            boolean souMembroAtivo, boolean tenhoPedidoPendente) {
        return new MinisterioDetalheResponse(
                ministerio.getId(), ministerio.getNome(), membros, pedidosPendentes,
                souLiderDesteMinisterio, souMembroAtivo, tenhoPedidoPendente);
    }
}
```

- [ ] **Step 5: `AdicionarMembroRequest` e `AtualizarPapelRequest`**

```java
package com.domus.api.modules.ministerio.DTOs;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdicionarMembroRequest(@NotNull(message = "A pessoa é obrigatória.") UUID pessoaId) {}
```

```java
package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Papel;
import jakarta.validation.constraints.NotNull;

public record AtualizarPapelRequest(@NotNull(message = "O papel é obrigatório.") Papel papel) {}
```

- [ ] **Step 6: Compilar e commitar**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

```bash
git add src/main/java/com/domus/api/modules/ministerio/DTOs/
git commit -m "feat(ministerio): DTOs de request/response"
```

---

### Task 4: `MinisterioService` — cadastro (criar/atualizar/arquivar/listar)

**Files:**
- Create: `src/main/java/com/domus/api/modules/ministerio/MinisterioService.java`
- Test: `src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java`

**Interfaces:**
- Consumes: `MinisterioRepository`, `MinisterioMembroRepository` (Task 2), `IgrejaRepository`, `TextoUtil.capitalizar/normalizarParaComparacao` (`com.domus.api.shared.util.TextoUtil`), `BusinessException`, `ResourceNotFoundException` (`com.domus.api.shared.exception`).
- Produces: `MinisterioService.listar(UUID igrejaId): List<MinisterioResponse>`, `MinisterioService.criar(MinisterioRequest, UUID igrejaId, UUID usuarioId): MinisterioResponse`, `MinisterioService.atualizar(UUID id, MinisterioRequest, UUID igrejaId, UUID usuarioId): MinisterioResponse`, `MinisterioService.arquivar(UUID id, UUID igrejaId): void`. (Métodos de membros/pedidos vêm na Task 5, mesma classe.)

- [ ] **Step 1: Escrever os testes de cadastro (falhando)**

```java
// src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java
package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MinisterioServiceTest {

    @Autowired MinisterioService service;
    @Autowired MinisterioRepository repository;
    @Autowired IgrejaRepository igrejaRepository;

    UUID igrejaId;
    UUID outraIgrejaId;

    @BeforeEach
    void setup() {
        igrejaId = igrejaRepository.save(novaIgreja("Igreja do Teste de Ministério")).getId();
        outraIgrejaId = igrejaRepository.save(novaIgreja("Outra Igreja")).getId();
    }

    private Igreja novaIgreja(String nome) {
        Igreja igreja = new Igreja();
        igreja.setNome(nome);
        igreja.setEmailContato(nome.toLowerCase().replace(" ", ".") + "@teste.com");
        igreja.setEndereco(Endereco.builder()
                .cep("01000-000").logradouro("Rua da Igreja").numero("100")
                .bairro("Centro").cidade("São Paulo").uf("SP")
                .build());
        return igreja;
    }

    @Test
    void cria_ministerio_e_retorna_id_e_nome() {
        MinisterioResponse response = service.criar(new MinisterioRequest("Louvor"), igrejaId, null);

        assertThat(response.nome()).isEqualTo("Louvor");
        assertThat(repository.findByIdAndIgrejaId(response.id(), igrejaId)).isPresent();
    }

    @Test
    void nao_permite_dois_ministerios_com_mesmo_nome_ignorando_acento_e_caixa() {
        service.criar(new MinisterioRequest("Recepção"), igrejaId, null);

        assertThatThrownBy(() -> service.criar(new MinisterioRequest("recepcao"), igrejaId, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um ministério");
    }

    @Test
    void ministerio_de_outra_igreja_nao_e_encontrado() {
        UUID id = service.criar(new MinisterioRequest("Infantil"), igrejaId, null).id();

        assertThat(repository.findByIdAndIgrejaId(id, outraIgrejaId)).isEmpty();
    }

    @Test
    void arquivar_some_da_listagem() {
        UUID id = service.criar(new MinisterioRequest("Diaconato"), igrejaId, null).id();

        service.arquivar(id, igrejaId);

        assertThat(service.listar(igrejaId)).extracting(MinisterioResponse::id).doesNotContain(id);
    }
}
```

- [ ] **Step 2: Rodar os testes e confirmar que falham (classe `MinisterioService` não existe)**

Run: `mvn -q test -Dtest=MinisterioServiceTest`
Expected: FAIL — compilation error, `MinisterioService` não existe.

- [ ] **Step 3: Implementar `MinisterioService` (cadastro)**

```java
// src/main/java/com/domus/api/modules/ministerio/MinisterioService.java
package com.domus.api.modules.ministerio;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.DTOs.MinisterioRequest;
import com.domus.api.modules.ministerio.DTOs.MinisterioResponse;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinisterioService {

    private final MinisterioRepository ministerioRepository;
    private final MinisterioMembroRepository membroRepository;
    private final IgrejaRepository igrejaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<MinisterioResponse> listar(UUID igrejaId) {
        // N+1 deliberado: uma igreja tem dezenas de ministérios, não milhares — uma query de
        // membros por ministério na tela de listagem é aceitável (YAGNI evita otimizar cedo
        // demais). Se a lista crescer muito, trocar por uma query agregada única.
        return ministerioRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .map(m -> MinisterioResponse.comResumo(m, membrosAtivosDe(m.getId())))
                .toList();
    }

    private List<MinisterioMembro> membrosAtivosDe(UUID ministerioId) {
        return membroRepository.findByMinisterioIdOrderByPapelAsc(ministerioId).stream()
                .filter(m -> m.getStatus() == StatusMembro.ATIVO)
                .toList();
    }

    @Transactional
    public MinisterioResponse criar(MinisterioRequest data, UUID igrejaId, UUID usuarioId) {
        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, null);

        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));
        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;

        Ministerio ministerio = Ministerio.builder()
                .igreja(igreja)
                .nome(nome)
                .criadoPor(usuario)
                .atualizadoPor(usuario)
                .build();

        return MinisterioResponse.from(ministerioRepository.save(ministerio));
    }

    @Transactional
    public MinisterioResponse atualizar(UUID id, MinisterioRequest data, UUID igrejaId, UUID usuarioId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);

        String nome = TextoUtil.capitalizar(data.nome());
        validarNaoDuplicado(nome, igrejaId, id);

        ministerio.setNome(nome);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(ministerio::setAtualizadoPor);
        }

        return MinisterioResponse.from(ministerioRepository.save(ministerio));
    }

    @Transactional
    public void arquivar(UUID id, UUID igrejaId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(id, igrejaId);
        ministerioRepository.delete(ministerio);
    }

    Ministerio buscarDaIgrejaOuFalhar(UUID id, UUID igrejaId) {
        return ministerioRepository.findByIdAndIgrejaId(id, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Ministério não encontrado."));
    }

    private void validarNaoDuplicado(String nome, UUID igrejaId, UUID ignorarId) {
        String normalizado = TextoUtil.normalizarParaComparacao(nome);
        boolean duplicado = ministerioRepository.findByIgrejaIdOrderByNomeAsc(igrejaId).stream()
                .filter(m -> ignorarId == null || !m.getId().equals(ignorarId))
                .anyMatch(m -> TextoUtil.normalizarParaComparacao(m.getNome()).equals(normalizado));
        if (duplicado) {
            throw new BusinessException("MINISTERIO_DUPLICADO", "Já existe um ministério com esse nome.");
        }
    }
}
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `mvn -q test -Dtest=MinisterioServiceTest`
Expected: PASS (4 testes verdes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/ministerio/MinisterioService.java \
        src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java
git commit -m "feat(ministerio): cadastro de ministério (criar/atualizar/arquivar/listar)"
```

---

### Task 5: `MinisterioService` — membros e pedidos de entrada

**Files:**
- Modify: `src/main/java/com/domus/api/modules/ministerio/MinisterioService.java`
- Modify: `src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java`

**Interfaces:**
- Consumes: `MinisterioMembroRepository` (Task 2), `PessoaRepository` (`com.domus.api.modules.pessoa`).
- Produces: `MinisterioService.detalhe(UUID ministerioId, UUID igrejaId, UUID pessoaLogadaId, boolean isAdmin): MinisterioDetalheResponse`, `.adicionarMembro(UUID ministerioId, AdicionarMembroRequest, UUID igrejaId, UUID atorPessoaId, boolean isAdmin, UUID usuarioId): void`, `.removerMembro(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin): void`, `.atualizarPapel(UUID ministerioId, UUID pessoaId, AtualizarPapelRequest, UUID igrejaId): void`, `.pedirEntrada(UUID ministerioId, UUID pessoaId, UUID igrejaId): void`, `.aceitarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin, UUID usuarioId): void`, `.recusarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin): void`, `.listarMinisteriosDaPessoa(UUID pessoaId, UUID igrejaId): List<MinisterioResponse>`, `.ehLiderDoMinisterio(UUID ministerioId, UUID pessoaId): boolean`.

- [ ] **Step 1: Escrever os testes (falhando)**

Adicionar ao final da classe `MinisterioServiceTest` (mesmo arquivo do Task 4):

```java
    @org.springframework.beans.factory.annotation.Autowired
    com.domus.api.modules.pessoa.PessoaRepository pessoaRepository;

    private com.domus.api.modules.pessoa.Pessoa novaPessoa(String nome, UUID igrejaId) {
        Igreja igreja = igrejaRepository.findById(igrejaId).orElseThrow();
        com.domus.api.modules.pessoa.Pessoa pessoa = com.domus.api.modules.pessoa.Pessoa.builder()
                .igreja(igreja)
                .nome(nome)
                .email(nome.toLowerCase().replace(" ", ".") + "@teste.com")
                .vinculo(com.domus.api.modules.pessoa.Vinculo.MEMBRO)
                .build();
        return pessoaRepository.save(pessoa);
    }

    @Test
    void admin_adiciona_membro_direto_como_ativo() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor"), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Ana", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaId),
                igrejaId, null, true, null);

        var detalhe = service.detalhe(ministerioId, igrejaId, null, true);
        assertThat(detalhe.membros()).extracting(m -> m.pessoaId()).contains(pessoaId);
    }

    @Test
    void pessoa_comum_nao_pode_adicionar_membro_sem_ser_lider() {
        UUID ministerioId = service.criar(new MinisterioRequest("Louvor"), igrejaId, null).id();
        UUID pessoaAlvo = novaPessoa("Bia", igrejaId).getId();
        UUID pessoaComum = novaPessoa("Carlos", igrejaId).getId();

        assertThatThrownBy(() -> service.adicionarMembro(
                ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(pessoaAlvo),
                igrejaId, pessoaComum, false, null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void lider_do_ministerio_aceita_pedido_de_entrada() {
        UUID ministerioId = service.criar(new MinisterioRequest("Recepção"), igrejaId, null).id();
        UUID liderId = novaPessoa("Duda", igrejaId).getId();
        UUID candidataId = novaPessoa("Elis", igrejaId).getId();

        service.adicionarMembro(ministerioId, new com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest(liderId),
                igrejaId, null, true, null);
        service.atualizarPapel(ministerioId, liderId,
                new com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest(Papel.LIDER), igrejaId);

        service.pedirEntrada(ministerioId, candidataId, igrejaId);
        assertThat(service.detalhe(ministerioId, igrejaId, liderId, false).pedidosPendentes())
                .extracting(m -> m.pessoaId()).contains(candidataId);

        service.aceitarPedido(ministerioId, candidataId, igrejaId, liderId, false, null);

        var detalhe = service.detalhe(ministerioId, igrejaId, liderId, false);
        assertThat(detalhe.membros()).extracting(m -> m.pessoaId()).contains(candidataId);
        assertThat(detalhe.pedidosPendentes()).isEmpty();
    }

    @Test
    void nao_permite_pedir_entrada_duas_vezes() {
        UUID ministerioId = service.criar(new MinisterioRequest("Missões"), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Fábio", igrejaId).getId();

        service.pedirEntrada(ministerioId, pessoaId, igrejaId);

        assertThatThrownBy(() -> service.pedirEntrada(ministerioId, pessoaId, igrejaId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void recusar_pedido_remove_a_linha_permitindo_pedir_de_novo() {
        UUID ministerioId = service.criar(new MinisterioRequest("Jovens"), igrejaId, null).id();
        UUID pessoaId = novaPessoa("Gustavo", igrejaId).getId();

        service.pedirEntrada(ministerioId, pessoaId, igrejaId);
        service.recusarPedido(ministerioId, pessoaId, igrejaId, null, true);

        assertThat(membroRepositoryVazio(ministerioId, pessoaId)).isTrue();
        service.pedirEntrada(ministerioId, pessoaId, igrejaId); // não deve lançar
    }

    private boolean membroRepositoryVazio(UUID ministerioId, UUID pessoaId) {
        return service.detalhe(ministerioId, igrejaId, null, true).pedidosPendentes().isEmpty()
                && service.detalhe(ministerioId, igrejaId, null, true).membros().isEmpty();
    }
```

(Precisa injetar `membroRepository` como `@Autowired MinisterioMembroRepository membroRepository;` no topo da classe de teste, ao lado dos outros `@Autowired`.)

- [ ] **Step 2: Rodar e confirmar que falham**

Run: `mvn -q test -Dtest=MinisterioServiceTest`
Expected: FAIL — métodos `adicionarMembro`, `detalhe`, `atualizarPapel`, `pedirEntrada`, `aceitarPedido`, `recusarPedido` não existem em `MinisterioService`.

- [ ] **Step 3: Implementar os métodos em `MinisterioService`**

Adicionar ao final da classe (antes do `}` final), junto com os imports necessários no topo do arquivo (`AccessDeniedException`, `AdicionarMembroRequest`, `AtualizarPapelRequest`, `MembroResponse`, `MinisterioDetalheResponse`, `Papel`, `StatusMembro`, `PessoaRepository`, `Pessoa`):

```java
    // --- imports adicionais no topo do arquivo ---
    // import com.domus.api.modules.ministerio.DTOs.AdicionarMembroRequest;
    // import com.domus.api.modules.ministerio.DTOs.AtualizarPapelRequest;
    // import com.domus.api.modules.ministerio.DTOs.MembroResponse;
    // import com.domus.api.modules.ministerio.DTOs.MinisterioDetalheResponse;
    // import com.domus.api.modules.pessoa.Pessoa;
    // import com.domus.api.modules.pessoa.PessoaRepository;
    // import org.springframework.security.access.AccessDeniedException;

    @Transactional(readOnly = true)
    public MinisterioDetalheResponse detalhe(UUID ministerioId, UUID igrejaId, UUID pessoaLogadaId, boolean isAdmin) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        boolean souLider = isAdmin || (pessoaLogadaId != null && ehLiderDoMinisterio(ministerioId, pessoaLogadaId));

        List<MinisterioMembro> todos = membroRepository.findByMinisterioIdOrderByPapelAsc(ministerioId);
        List<MembroResponse> membros = todos.stream()
                .filter(m -> m.getStatus() == StatusMembro.ATIVO)
                .map(MembroResponse::from)
                .toList();
        List<MembroResponse> pedidosPendentes = souLider
                ? todos.stream()
                        .filter(m -> m.getStatus() == StatusMembro.PENDENTE)
                        .map(MembroResponse::from)
                        .toList()
                : List.of();

        // O front não guarda pessoaId no authStore (só usuarioId/role) — calcula aqui pra
        // decidir "pedir para entrar" vs "pedido enviado" sem o front precisar saber quem é.
        boolean souMembroAtivo = pessoaLogadaId != null && todos.stream()
                .anyMatch(m -> m.getPessoa().getId().equals(pessoaLogadaId) && m.getStatus() == StatusMembro.ATIVO);
        boolean tenhoPedidoPendente = pessoaLogadaId != null && todos.stream()
                .anyMatch(m -> m.getPessoa().getId().equals(pessoaLogadaId) && m.getStatus() == StatusMembro.PENDENTE);

        return MinisterioDetalheResponse.from(
                ministerio, membros, pedidosPendentes, souLider, souMembroAtivo, tenhoPedidoPendente);
    }

    public boolean ehLiderDoMinisterio(UUID ministerioId, UUID pessoaId) {
        return membroRepository.existsByMinisterioIdAndPessoaIdAndPapelAndStatus(
                ministerioId, pessoaId, Papel.LIDER, StatusMembro.ATIVO);
    }

    private void exigirAdminOuLider(UUID ministerioId, UUID atorPessoaId, boolean isAdmin) {
        if (isAdmin) return;
        if (atorPessoaId == null || !ehLiderDoMinisterio(ministerioId, atorPessoaId)) {
            throw new AccessDeniedException("Só o líder deste ministério ou um administrador pode fazer isso.");
        }
    }

    @Transactional
    public void adicionarMembro(UUID ministerioId, AdicionarMembroRequest data, UUID igrejaId,
                                 UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        if (membroRepository.findByMinisterioIdAndPessoaId(ministerioId, data.pessoaId()).isPresent()) {
            throw new BusinessException("MEMBRO_JA_VINCULADO", "Essa pessoa já está vinculada a este ministério.");
        }

        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(data.pessoaId(), igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));
        Usuario usuario = usuarioId != null ? usuarioRepository.findById(usuarioId).orElse(null) : null;

        membroRepository.save(MinisterioMembro.builder()
                .igreja(ministerio.getIgreja())
                .ministerio(ministerio)
                .pessoa(pessoa)
                .papel(Papel.MEMBRO)
                .status(StatusMembro.ATIVO)
                .criadoPor(usuario)
                .atualizadoPor(usuario)
                .build());
    }

    @Transactional
    public void removerMembro(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado."));
        membroRepository.delete(membro);
    }

    @Transactional
    public void atualizarPapel(UUID ministerioId, UUID pessoaId, AtualizarPapelRequest data, UUID igrejaId) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado."));
        if (membro.getStatus() != StatusMembro.ATIVO) {
            throw new BusinessException("MEMBRO_NAO_ATIVO", "A pessoa precisa ser membro ativo antes de virar líder.");
        }
        membro.setPapel(data.papel());
        membroRepository.save(membro);
    }

    @Transactional
    public void pedirEntrada(UUID ministerioId, UUID pessoaId, UUID igrejaId) {
        Ministerio ministerio = buscarDaIgrejaOuFalhar(ministerioId, igrejaId);

        if (membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId).isPresent()) {
            throw new BusinessException("PEDIDO_JA_EXISTE", "Você já está vinculado ou já tem um pedido pendente neste ministério.");
        }

        Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        membroRepository.save(MinisterioMembro.builder()
                .igreja(ministerio.getIgreja())
                .ministerio(ministerio)
                .pessoa(pessoa)
                .papel(Papel.MEMBRO)
                .status(StatusMembro.PENDENTE)
                .build());
    }

    @Transactional
    public void aceitarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId,
                               UUID atorPessoaId, boolean isAdmin, UUID usuarioId) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        membro.setStatus(StatusMembro.ATIVO);
        if (usuarioId != null) {
            usuarioRepository.findById(usuarioId).ifPresent(membro::setAtualizadoPor);
        }
        membroRepository.save(membro);
    }

    @Transactional
    public void recusarPedido(UUID ministerioId, UUID pessoaId, UUID igrejaId, UUID atorPessoaId, boolean isAdmin) {
        buscarDaIgrejaOuFalhar(ministerioId, igrejaId);
        exigirAdminOuLider(ministerioId, atorPessoaId, isAdmin);

        MinisterioMembro membro = membroRepository.findByMinisterioIdAndPessoaId(ministerioId, pessoaId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado."));
        membroRepository.delete(membro);
    }

    @Transactional(readOnly = true)
    public List<MinisterioResponse> listarMinisteriosDaPessoa(UUID pessoaId, UUID igrejaId) {
        return membroRepository.findByPessoaIdAndIgrejaIdAndStatus(pessoaId, igrejaId, StatusMembro.ATIVO).stream()
                .map(m -> MinisterioResponse.from(m.getMinisterio()))
                .toList();
    }
```

Também adicionar `private final PessoaRepository pessoaRepository;` na lista de campos `@RequiredArgsConstructor` no topo da classe.

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `mvn -q test -Dtest=MinisterioServiceTest`
Expected: PASS (todos os testes verdes, incluindo os do Task 4).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/ministerio/MinisterioService.java \
        src/test/java/com/domus/api/modules/ministerio/MinisterioServiceTest.java
git commit -m "feat(ministerio): gestão de membros e fluxo de pedido de entrada"
```

---

### Task 6: `MinisterioController`, `Permissoes` e `Perfil`

**Files:**
- Create: `src/main/java/com/domus/api/modules/ministerio/MinisterioController.java`
- Modify: `src/main/java/com/domus/api/shared/security/Permissoes.java`

**Interfaces:**
- Consumes: `MinisterioService` (Tasks 4/5), `UsuarioAutenticado` (`shared/security`).
- Produces: endpoints REST listados no spec; `Permissoes.podeGerenciarCadastroMinisterios(String role): boolean`.

- [ ] **Step 1: Adicionar `podeGerenciarCadastroMinisterios` em `Permissoes.java`**

```java
// adicionar ao final da classe Permissoes, junto com os outros métodos:
public static boolean podeGerenciarCadastroMinisterios(String role) { return tem(role, SO_ADMIN); }
```

(Só `ADMIN_IGREJA` cria/renomeia/arquiva ministério e promove/rebaixa líder — decisão do spec. A gestão de membros/pedidos comuns é checada no service via `ehLiderDoMinisterio`, não em `Permissoes`.)

- [ ] **Step 2: Criar `MinisterioController`**

```java
package com.domus.api.modules.ministerio;

import com.domus.api.modules.ministerio.DTOs.*;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/ministerios")
@RequiredArgsConstructor
public class MinisterioController {

    private final MinisterioService ministerioService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<MinisterioResponse>> listar() {
        return ResponseEntity.ok(ministerioService.listar(usuarioAutenticado.getIgrejaId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MinisterioDetalheResponse> detalhe(@PathVariable UUID id) {
        return ResponseEntity.ok(ministerioService.detalhe(
                id, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), souAdmin()));
    }

    @PostMapping
    public ResponseEntity<MinisterioResponse> criar(@Valid @RequestBody MinisterioRequest data) {
        exigirAdmin();
        MinisterioResponse response = ministerioService.criar(
                data, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MinisterioResponse> atualizar(@PathVariable UUID id, @Valid @RequestBody MinisterioRequest data) {
        exigirAdmin();
        return ResponseEntity.ok(ministerioService.atualizar(
                id, data, usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> arquivar(@PathVariable UUID id) {
        exigirAdmin();
        ministerioService.arquivar(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/membros")
    public ResponseEntity<Void> adicionarMembro(@PathVariable UUID id, @Valid @RequestBody AdicionarMembroRequest data) {
        ministerioService.adicionarMembro(id, data, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @DeleteMapping("/{id}/membros/{pessoaId}")
    public ResponseEntity<Void> removerMembro(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        ministerioService.removerMembro(id, pessoaId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/membros/{pessoaId}/papel")
    public ResponseEntity<Void> atualizarPapel(@PathVariable UUID id, @PathVariable UUID pessoaId,
                                                @Valid @RequestBody AtualizarPapelRequest data) {
        exigirAdmin();
        ministerioService.atualizarPapel(id, pessoaId, data, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/pedidos")
    public ResponseEntity<Void> pedirEntrada(@PathVariable UUID id) {
        ministerioService.pedirEntrada(id, usuarioAutenticado.getPessoaId(), usuarioAutenticado.getIgrejaId());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{id}/pedidos/{pessoaId}/aceitar")
    public ResponseEntity<Void> aceitarPedido(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        ministerioService.aceitarPedido(id, pessoaId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin(), usuarioAutenticado.getUsuarioId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/pedidos/{pessoaId}")
    public ResponseEntity<Void> recusarPedido(@PathVariable UUID id, @PathVariable UUID pessoaId) {
        ministerioService.recusarPedido(id, pessoaId, usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(), souAdmin());
        return ResponseEntity.noContent().build();
    }

    private boolean souAdmin() {
        return "ADMIN_IGREJA".equals(usuarioAutenticado.getRole());
    }

    private void exigirAdmin() {
        if (!Permissoes.podeGerenciarCadastroMinisterios(usuarioAutenticado.getRole())) {
            throw new AccessDeniedException("Só um administrador pode gerenciar o cadastro de ministérios.");
        }
    }
}
```

- [ ] **Step 3: Adicionar endpoint `GET /pessoas/{id}/ministerios`**

Modificar `src/main/java/com/domus/api/modules/pessoa/PessoaController.java`: adicionar o método abaixo (e injetar `MinisterioService` no construtor — `@RequiredArgsConstructor` já resolve, só adicionar o campo `private final MinisterioService ministerioService;`):

```java
    @GetMapping("/{id}/ministerios")
    public ResponseEntity<java.util.List<com.domus.api.modules.ministerio.DTOs.MinisterioResponse>> ministerios(
            @PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(ministerioService.listarMinisteriosDaPessoa(id, igrejaId));
    }
```

- [ ] **Step 4: Compilar**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Teste manual dos endpoints (smoke test)**

Run: subir a aplicação (`mvn spring-boot:run`) e, com um token válido de `ADMIN_IGREJA`:
```bash
curl -s -X POST localhost:8080/ministerios -H "Content-Type: application/json" \
  -H "Cookie: domus_access=<token>" -d '{"nome":"Louvor"}'
```
Expected: `201` com `{"id":"...","nome":"Louvor"}`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/ministerio/MinisterioController.java \
        src/main/java/com/domus/api/shared/security/Permissoes.java \
        src/main/java/com/domus/api/modules/pessoa/PessoaController.java
git commit -m "feat(ministerio): endpoints REST e autorização de cadastro"
```

---

### Task 7: Remover `ministerio` de `Pessoa`, DTOs e busca

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pessoa/Pessoa.java:67-68`
- Modify: `src/main/java/com/domus/api/modules/pessoa/DTO/PessoaRequestDTO.java`
- Modify: `src/main/java/com/domus/api/modules/pessoa/DTO/PessoaResponse.java`
- Modify: `src/main/java/com/domus/api/modules/pessoa/PessoaController.java:112`
- Modify: `src/main/java/com/domus/api/modules/pessoa/PessoaService.java:95,171`
- Modify: `src/main/java/com/domus/api/modules/pessoa/busca/PessoaDocument.java`
- Modify: `src/main/java/com/domus/api/modules/pessoa/busca/BuscaPessoaService.java`

**Interfaces:**
- Nenhuma nova; remove referências ao campo `ministerio` já mapeado no Task 1 (coluna já foi dropada na migration).

- [ ] **Step 1: Remover o campo da entidade `Pessoa`**

Remover as linhas 67-68 de `Pessoa.java`:
```java
    @Column(name = "ministerio", length = 255)
    private String ministerio;
```

- [ ] **Step 2: Remover o campo de `PessoaRequestDTO`**

Remover do record:
```java
        @Size(max = 255)
        String ministerio,
```

- [ ] **Step 3: Remover o campo de `PessoaResponse`**

Remover `String ministerio,` do record e `m.getMinisterio(),` de todos os `new PessoaResponse(...)` dentro dos métodos `from(...)`.

- [ ] **Step 4: Atualizar `PessoaController.atualizarMe`**

Remover a linha `data.ministerio(),` do construtor posicional de `PessoaRequestDTO` em `atualizarMe` (linha 112) — os argumentos seguintes (`data.cargo()`, `data.observacoes()`, `data.dataBatismo()`, `data.fotoId()`) deslocam uma posição pra cima.

- [ ] **Step 5: Atualizar `PessoaService`**

Remover `.ministerio(normalizar(data.ministerio()))` (linha 95, dentro do builder de criação) e `membro.setMinisterio(normalizar(data.ministerio()));` (linha 171, dentro de atualização).

- [ ] **Step 6: Atualizar índice de busca (`PessoaDocument`/`BuscaPessoaService`)**

Remover o campo `ministerio` de `PessoaDocument.java` (linhas 36,51) e a entrada `"ministerio"` da lista de campos buscados em `BuscaPessoaService.java` (linhas 41,49,79) — ex.: `fields("email^2", "ministerio", "cargo")` vira `fields("email^2", "cargo")`.

- [ ] **Step 7: Compilar e rodar a suíte de testes de pessoa**

Run: `mvn -q compile && mvn -q test -Dtest=PessoaServiceTest,PessoaControllerTest`
Expected: BUILD SUCCESS; testes existentes de pessoa continuam verdes (ajustar qualquer teste que ainda monte `PessoaRequestDTO` com o argumento `ministerio` — buscar por `ministerio` nos testes: `grep -rn "ministerio" src/test/`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/pessoa/
git commit -m "refactor(pessoa): remove campo livre ministerio (substituído pelo cadastro estruturado)"
```

---

## Frontend

### Task 8: Tipos, endpoints e service de Ministério

**Files:**
- Create: `src/types/ministerio.type.ts`
- Modify: `src/lib/endpoints.ts`
- Create: `src/services/ministerio.service.ts`
- Modify: `src/types/pessoa.type.ts`
- Create: `src/lib/rotulosMinisterio.ts`

**Interfaces:**
- Produces: `Papel = 'LIDER' | 'MEMBRO'`, `MinisterioResponse{id,nome}`, `MembroResponse{pessoaId,nome,fotoId,papel}`, `MinisterioDetalheResponse{id,nome,membros,pedidosPendentes,souLiderDesteMinisterio}`, `MinisterioRequest{nome}`, `ministerioService.{listar,criar,atualizar,arquivar,detalhe,adicionarMembro,removerMembro,atualizarPapel,pedirEntrada,aceitarPedido,recusarPedido}`, `ROTULO_MINISTERIO`/`ROTULO_MINISTERIO_PLURAL` (`@/lib/rotulosMinisterio`).

- [ ] **Step 0: Criar o ponto único de rótulo visível (`src/lib/rotulosMinisterio.ts`)**

Decisão de 2026-07-24: nem toda igreja chama isso de "ministério" (departamento, rede...).
O **código** (tabelas, tipos, hooks, rotas — tudo que já foi definido nas Tasks 1-9) continua
`ministerio`, mesmo tratamento que `congregacao` recebeu quando o rótulo virou "Unidade" (ver
memória `congregacao-virou-unidade-no-front`). Só o **texto visível** muda, e vem de um
arquivo só — assim, quando a Fase 5 tornar isso self-service por igreja (ver
`BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`), o texto some de um lugar só, não de uma dúzia de
componentes.

```ts
// src/lib/rotulosMinisterio.ts

/**
 * Rótulo visível para o módulo de ministério/departamento/rede. Hardcoded pro piloto (esta
 * igreja usa "rede") — quando abrir para outras igrejas (Fase 5), isto vira uma config por
 * igreja (`igreja.rotuloMinisterio` ou similar) em vez de constante. Até lá, mudar aqui é
 * a ÚNICA coisa que muda para trocar o termo em toda a aplicação.
 */
export const ROTULO_MINISTERIO = 'Rede'
export const ROTULO_MINISTERIO_PLURAL = 'Redes'
```

- [ ] **Step 1: Criar `src/types/ministerio.type.ts`**

```ts
export type Papel = 'LIDER' | 'MEMBRO'

export interface MinisterioRequest {
  nome: string
}

export interface MinisterioResponse {
  id: string
  nome: string
  /** Vazio em respostas que não consultam membros (ex.: GET /pessoas/{id}/ministerios). */
  lideres: string[]
  totalMembros: number
}

export interface MembroResponse {
  pessoaId: string
  nome: string
  fotoId: string | null
  papel: Papel
}

export interface MinisterioDetalheResponse {
  id: string
  nome: string
  membros: MembroResponse[]
  pedidosPendentes: MembroResponse[]
  souLiderDesteMinisterio: boolean
  souMembroAtivo: boolean
  tenhoPedidoPendente: boolean
}
```

- [ ] **Step 2: Remover `ministerio` de `src/types/pessoa.type.ts`**

Remover `ministerio?: string` de `PessoaRequest` e `ministerio: string | null` de `PessoaResponse`.

- [ ] **Step 3: Adicionar endpoints em `src/lib/endpoints.ts`**

```ts
  ministerios: {
    LISTAR: '/ministerios',
    CRIAR: '/ministerios',
    BY_ID: (id: string) => `/ministerios/${id}`,
    MEMBROS: (id: string) => `/ministerios/${id}/membros`,
    MEMBRO: (id: string, pessoaId: string) => `/ministerios/${id}/membros/${pessoaId}`,
    PAPEL: (id: string, pessoaId: string) => `/ministerios/${id}/membros/${pessoaId}/papel`,
    PEDIDOS: (id: string) => `/ministerios/${id}/pedidos`,
    ACEITAR_PEDIDO: (id: string, pessoaId: string) => `/ministerios/${id}/pedidos/${pessoaId}/aceitar`,
    RECUSAR_PEDIDO: (id: string, pessoaId: string) => `/ministerios/${id}/pedidos/${pessoaId}`,
  },
```

(Inserir logo após o bloco `locaisEvento`, seguindo a mesma indentação/estilo do arquivo.) Adicionar também `PESSOA_MINISTERIOS: (pessoaId: string) => \`/pessoas/${pessoaId}/ministerios\`,` dentro do bloco `pessoas` já existente.

- [ ] **Step 4: Criar `src/services/ministerio.service.ts`**

```ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type {
  MinisterioRequest, MinisterioResponse, MinisterioDetalheResponse,
} from '@/types/ministerio.type'

export const ministerioService = {
  listar: (): Promise<MinisterioResponse[]> =>
    api.get<MinisterioResponse[]>(Endpoints.ministerios.LISTAR).then(res => res.data),

  detalhe: (id: string): Promise<MinisterioDetalheResponse> =>
    api.get<MinisterioDetalheResponse>(Endpoints.ministerios.BY_ID(id)).then(res => res.data),

  criar: (data: MinisterioRequest): Promise<MinisterioResponse> =>
    api.post<MinisterioResponse>(Endpoints.ministerios.CRIAR, data).then(res => res.data),

  atualizar: (id: string, data: MinisterioRequest): Promise<MinisterioResponse> =>
    api.put<MinisterioResponse>(Endpoints.ministerios.BY_ID(id), data).then(res => res.data),

  arquivar: (id: string): Promise<void> =>
    api.delete(Endpoints.ministerios.BY_ID(id)).then(() => undefined),

  adicionarMembro: (id: string, pessoaId: string): Promise<void> =>
    api.post(Endpoints.ministerios.MEMBROS(id), { pessoaId }).then(() => undefined),

  removerMembro: (id: string, pessoaId: string): Promise<void> =>
    api.delete(Endpoints.ministerios.MEMBRO(id, pessoaId)).then(() => undefined),

  atualizarPapel: (id: string, pessoaId: string, papel: 'LIDER' | 'MEMBRO'): Promise<void> =>
    api.put(Endpoints.ministerios.PAPEL(id, pessoaId), { papel }).then(() => undefined),

  pedirEntrada: (id: string): Promise<void> =>
    api.post(Endpoints.ministerios.PEDIDOS(id)).then(() => undefined),

  aceitarPedido: (id: string, pessoaId: string): Promise<void> =>
    api.put(Endpoints.ministerios.ACEITAR_PEDIDO(id, pessoaId)).then(() => undefined),

  recusarPedido: (id: string, pessoaId: string): Promise<void> =>
    api.delete(Endpoints.ministerios.RECUSAR_PEDIDO(id, pessoaId)).then(() => undefined),
}
```

- [ ] **Step 5: Verificar tipos**

Run: `cd ../../frontend && npx tsc --noEmit`
Expected: sem erros novos relacionados a `ministerio`/`pessoa.type.ts` (erros pré-existentes não relacionados, se houver, não bloqueiam este passo).

- [ ] **Step 6: Commit**

```bash
git add src/types/ministerio.type.ts src/types/pessoa.type.ts src/lib/endpoints.ts src/services/ministerio.service.ts src/lib/rotulosMinisterio.ts
git commit -m "feat(ministerio): tipos, endpoints e service do módulo de ministérios"
```

---

### Task 9: Hooks (TanStack Query)

**Files:**
- Create: `src/hooks/ministerio/useMinisterios.ts`
- Create: `src/hooks/ministerio/useMinisterioDetalhe.ts`
- Create: `src/hooks/ministerio/useMinisterioForm.ts`
- Create: `src/hooks/ministerio/useMembroMinisterio.ts`
- Create: `src/hooks/ministerio/usePedidoMinisterio.ts`
- Create: `src/hooks/pessoa/usePessoaMinisterios.ts`
- Modify: `src/lib/cacheInvalidacao.ts`

**Interfaces:**
- Consumes: `ministerioService` (Task 8), `invalidarCache` (`@/lib/cacheInvalidacao`), `notificar` (`@/components/common/Notificacao/notificar`, **não** `@/lib/notificar` — esse caminho não existe no repo).
- Produces: `useMinisterios(): UseQueryResult<MinisterioResponse[]>`, `useMinisterioDetalhe(id): UseQueryResult<MinisterioDetalheResponse>`, `useCriarMinisterio()/useAtualizarMinisterio()/useArquivarMinisterio(): UseMutationResult`, `useAdicionarMembro(ministerioId)/useRemoverMembro(ministerioId)/useAtualizarPapel(ministerioId): UseMutationResult`, `usePedirEntrada(ministerioId)/useAceitarPedido(ministerioId)/useRecusarPedido(ministerioId): UseMutationResult`, `usePessoaMinisterios(pessoaId): UseQueryResult<MinisterioResponse[]>`.

Todas as mutations já disparam `notificar.sucesso`/`notificar.erro` sozinhas (padrão real do repo, visto em `useCancelarInscricao.ts`) — os componentes das Tasks 11/12 **não** chamam `notificar` de novo em cima delas, só tratam o estado de `isPending`/erro se precisarem de UI extra.

- [ ] **Step 0: Registrar `ministerio` no mapa de invalidação de cache**

`src/lib/cacheInvalidacao.ts` centraliza "o que fica velho quando isto muda" (ver comentário no topo do arquivo) — toda mutation nova precisa entrar aqui, não inventar sua própria invalidação solta. Adicionar `'ministerio'` ao tipo `Entidade` e uma entrada no `AFETADAS`:

```ts
type Entidade = 'evento' | 'pessoa' | 'movimentacao' | 'categoria' | 'usuario' | 'igreja' | 'inscricao' | 'localEvento' | 'ministerio'
```

```ts
  ministerio: [
    ['ministerios'],
    // ATENÇÃO (mesma armadilha do evento/pessoa acima): `['ministerios']` (lista) NÃO cobre
    // `['ministerios', id]` (detalhe) — invalidação é por prefixo, e o id não é prefixo da
    // lista. As duas entradas são necessárias.
    ['pessoas'], // a seção "Ministérios" do perfil de pessoa também fica velha
  ],
```

> A queryKey de detalhe usada nos hooks abaixo é `['ministerios', id]` — como `['ministerios']` já é prefixo dela, uma única entrada `['ministerios']` no mapa cobre lista E detalhe (diferente do caso de evento/pessoa, que usam singular/plural distintos — `['evento']` vs `['eventos']`). Ainda assim, mantenha o padrão de comentário do arquivo para quem ler depois.

- [ ] **Step 1: `useMinisterios` e `useMinisterioDetalhe`**

```ts
// src/hooks/ministerio/useMinisterios.ts
import { useQuery } from '@tanstack/react-query'
import { ministerioService } from '@/services/ministerio.service'

export function useMinisterios() {
  return useQuery({
    queryKey: ['ministerios'],
    queryFn: () => ministerioService.listar(),
    staleTime: 60 * 1000,
  })
}
```

```ts
// src/hooks/ministerio/useMinisterioDetalhe.ts
import { useQuery } from '@tanstack/react-query'
import { ministerioService } from '@/services/ministerio.service'

export function useMinisterioDetalhe(id: string) {
  return useQuery({
    queryKey: ['ministerios', id],
    queryFn: () => ministerioService.detalhe(id),
    enabled: !!id,
  })
}
```

- [ ] **Step 2: `useMinisterioForm` (criar/atualizar/arquivar)**

```ts
// src/hooks/ministerio/useMinisterioForm.ts
import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { MinisterioRequest } from '@/types/ministerio.type'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function useCriarMinisterio() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.criar(data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Ministério criado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível criar o ministério', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAtualizarMinisterio(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: MinisterioRequest) => ministerioService.atualizar(id, data),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Ministério atualizado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível atualizar o ministério', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useArquivarMinisterio() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => ministerioService.arquivar(id),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Ministério arquivado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível arquivar o ministério', mensagemErro(error, 'Tente novamente.')),
  })
}
```

- [ ] **Step 3: `useMembroMinisterio` (adicionar/remover/promover)**

```ts
// src/hooks/ministerio/useMembroMinisterio.ts
import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function useAdicionarMembro(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.adicionarMembro(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pessoa adicionada ao ministério.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível adicionar a pessoa', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useRemoverMembro(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.removerMembro(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Membro removido.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível remover o membro', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAtualizarPapel(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ pessoaId, papel }: { pessoaId: string; papel: 'LIDER' | 'MEMBRO' }) =>
      ministerioService.atualizarPapel(ministerioId, pessoaId, papel),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Papel atualizado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível atualizar o papel', mensagemErro(error, 'Tente novamente.')),
  })
}
```

- [ ] **Step 4: `usePedidoMinisterio` (pedir/aceitar/recusar)**

```ts
// src/hooks/ministerio/usePedidoMinisterio.ts
import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { ministerioService } from '@/services/ministerio.service'
import type { ApiError } from '@/types/api.types'

function mensagemErro(error: unknown, fallback: string): string {
  return axios.isAxiosError<ApiError>(error) ? (error.response?.data?.message ?? fallback) : fallback
}

export function usePedirEntrada(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => ministerioService.pedirEntrada(ministerioId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pedido enviado. Aguarde a aprovação do líder.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível enviar o pedido', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useAceitarPedido(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.aceitarPedido(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pedido aceito.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível aceitar o pedido', mensagemErro(error, 'Tente novamente.')),
  })
}

export function useRecusarPedido(ministerioId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (pessoaId: string) => ministerioService.recusarPedido(ministerioId, pessoaId),
    onSuccess: () => {
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso('Pedido recusado.')
    },
    onError: (error: unknown) => notificar.erro('Não foi possível recusar o pedido', mensagemErro(error, 'Tente novamente.')),
  })
}
```

- [ ] **Step 5: `usePessoaMinisterios` (perfil)**

```ts
// src/hooks/pessoa/usePessoaMinisterios.ts
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { MinisterioResponse } from '@/types/ministerio.type'

export function usePessoaMinisterios(pessoaId: string) {
  return useQuery({
    queryKey: ['pessoas', pessoaId, 'ministerios'],
    queryFn: () =>
      api.get<MinisterioResponse[]>(Endpoints.pessoas.PESSOA_MINISTERIOS(pessoaId))
        .then(res => res.data),
    enabled: !!pessoaId,
  })
}
```

- [ ] **Step 6: Verificar tipos**

Run: `npx tsc --noEmit`
Expected: sem erros novos.

- [ ] **Step 7: Commit**

```bash
git add src/hooks/ministerio/ src/hooks/pessoa/usePessoaMinisterios.ts src/lib/cacheInvalidacao.ts
git commit -m "feat(ministerio): hooks de dados (listagem, detalhe, membros, pedidos)"
```

---

### Task 10: `permissoes.ts` — `podeGerenciarCadastroMinisterios`

**Files:**
- Modify: `src/lib/permissoes.ts`

**Interfaces:**
- Produces: `podeGerenciarCadastroMinisterios(role: Role | null | undefined): boolean`.

- [ ] **Step 1: Adicionar a função**

```ts
export const podeGerenciarCadastroMinisterios = (r: Role | null | undefined) => tem(r, SO_ADMIN)
```

- [ ] **Step 2: Verificar tipos e commit**

Run: `npx tsc --noEmit`
Expected: sem erros.

```bash
git add src/lib/permissoes.ts
git commit -m "feat(ministerio): podeGerenciarCadastroMinisterios no front"
```

---

### Task 11: Página `/ministerios` (lista + CRUD do cadastro)

**Files:**
- Create: `src/app/(app)/ministerios/page.tsx`
- Create: `src/app/(app)/ministerios/ministerios.module.css`
- Create: `src/app/(app)/ministerios/ModalMinisterioForm.tsx`
- Create: `src/app/(app)/ministerios/ModalMinisterioForm.module.css`
- Create: `src/app/(app)/ministerios/ModalArquivarMinisterio.tsx`
- Create: `src/hooks/ministerio/useArquivarMinisterioConfirmacao.ts`

**Interfaces:**
- Consumes: `useMinisterios`, `useCriarMinisterio`, `useAtualizarMinisterio`, `useArquivarMinisterio` (Task 9), `podeGerenciarCadastroMinisterios` (Task 10), `MinisterioResponse` (Task 8).

- [ ] **Step 1: Criar `ModalMinisterioForm.tsx`**

```tsx
'use client'

import { useState } from 'react'
import { useCriarMinisterio, useAtualizarMinisterio } from '@/hooks/ministerio/useMinisterioForm'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './ModalMinisterioForm.module.css'

interface Props {
  ministerio: MinisterioResponse | null
  onClose: () => void
}

// As mutations (useCriarMinisterio/useAtualizarMinisterio) já disparam notificar.sucesso/erro
// sozinhas (ver Task 9) — este componente só decide fechar o modal em caso de sucesso.
export function ModalMinisterioForm({ ministerio, onClose }: Props) {
  const [nome, setNome] = useState(ministerio?.nome ?? '')
  const criar = useCriarMinisterio()
  const atualizar = useAtualizarMinisterio(ministerio?.id ?? '')
  const salvando = criar.isPending || atualizar.isPending

  async function salvar() {
    try {
      if (ministerio) {
        await atualizar.mutateAsync({ nome })
      } else {
        await criar.mutateAsync({ nome })
      }
      onClose()
    } catch {
      // erro já notificado pela mutation; modal fica aberto para o usuário tentar de novo.
    }
  }

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <h2 className={styles.titulo}>{ministerio ? `Editar ${ROTULO_MINISTERIO.toLowerCase()}` : `Nova ${ROTULO_MINISTERIO.toLowerCase()}`}</h2>
        <label className={styles.label} htmlFor="nome-ministerio">Nome</label>
        <input
          id="nome-ministerio"
          className={styles.input}
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          placeholder="Ex.: Louvor"
        />
        <div className={styles.acoes}>
          <button type="button" className={styles.botaoSecundario} onClick={onClose}>Cancelar</button>
          <button type="button" className={styles.botaoPrimario} disabled={!nome.trim() || salvando} onClick={salvar}>
            Salvar
          </button>
        </div>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Criar CSS do modal (`ModalMinisterioForm.module.css`)**

```css
.overlay {
  position: fixed; inset: 0; background: rgba(0, 0, 0, 0.4);
  display: flex; align-items: center; justify-content: center; z-index: 50; padding: 1rem;
}
.modal {
  background: var(--cor-fundo, #fff); border-radius: 0.75rem; padding: 1.5rem;
  width: 100%; max-width: 420px; min-width: 0;
}
.titulo { font-size: 1.125rem; font-weight: 600; margin-bottom: 1rem; }
.label { display: block; font-size: 0.875rem; margin-bottom: 0.25rem; }
.input { width: 100%; padding: 0.5rem 0.75rem; border-radius: 0.5rem; border: 1px solid #d1d5db; margin-bottom: 1rem; }
.acoes { display: flex; justify-content: flex-end; gap: 0.5rem; }
.botaoPrimario { padding: 0.5rem 1rem; border-radius: 0.5rem; background: var(--cor-primaria, #2563eb); color: #fff; border: none; }
.botaoSecundario { padding: 0.5rem 1rem; border-radius: 0.5rem; background: transparent; border: 1px solid #d1d5db; }
```

> Ajustar as variáveis de cor (`--cor-fundo`, `--cor-primaria`) para as reais do projeto — conferir em `ModalLocalForm.module.css` antes de finalizar, para manter consistência visual exata com o resto do app.

- [ ] **Step 3: Criar `ModalArquivarMinisterio.tsx`**

Este módulo segue exatamente o padrão de `src/app/(app)/eventos/locais/ModalArquivarLocal.tsx` (arquivar é confirmação "digite o nome", via `ModalConfirmacaoCritica` — não `window.confirm`, ver memória `ui-notificar-e-confirmacao`). Como as mutations do Task 9 já centralizam `notificar`, o hook de suporte aqui só precisa expor `confirmar`/`isLoading`/`erroGeral` como o `useArquivarLocalEvento.ts` original faz — só que reaproveitando `useArquivarMinisterio` por baixo em vez de chamar o service direto:

```tsx
// src/hooks/ministerio/useArquivarMinisterioConfirmacao.ts
import { useState } from 'react'
import axios from 'axios'
import { ministerioService } from '@/services/ministerio.service'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { notificar } from '@/components/common/Notificacao/notificar'
import type { MinisterioResponse } from '@/types/ministerio.type'
import type { ApiError } from '@/types/api.types'

export function useArquivarMinisterioConfirmacao(ministerio: MinisterioResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await ministerioService.arquivar(ministerio.id)
      invalidarCache(queryClient, 'ministerio')
      notificar.sucesso(`"${ministerio.nome}" foi arquivado.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao arquivar. Tente novamente.')
      } else {
        setErroGeral('Erro ao arquivar. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
```

```tsx
// src/app/(app)/ministerios/ModalArquivarMinisterio.tsx
'use client'

import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarMinisterioConfirmacao } from '@/hooks/ministerio/useArquivarMinisterioConfirmacao'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'

/**
 * Confirmação "digite o nome" (não a leve): arquivar um ministério tira o acesso de todo
 * mundo que estava vinculado a ele — vale o atrito extra de ler antes de confirmar, mesmo
 * padrão de ModalArquivarLocal.tsx.
 */
export function ModalArquivarMinisterio({ ministerio, onClose }: { ministerio: MinisterioResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useArquivarMinisterioConfirmacao(ministerio, onClose)
  const rotulo = ROTULO_MINISTERIO.toLowerCase()

  return (
    <ModalConfirmacaoCritica
      titulo={`Arquivar ${rotulo}?`}
      mensagem={
        <>
          Ao arquivar <strong>{ministerio.nome}</strong>, ela deixará de aparecer na lista de
          {' '}{ROTULO_MINISTERIO_PLURAL.toLowerCase()} e ninguém mais poderá ver ou pedir para entrar nela.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: `Some da lista de ${ROTULO_MINISTERIO_PLURAL.toLowerCase()} da igreja` },
        { tipo: 'mantem', texto: 'O histórico de quem foi membro não é apagado do banco' },
      ]}
      palavraConfirmacao={ministerio.nome}
      textoConfirmar={`Arquivar ${rotulo}`}
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
```

> Isso substitui `useArquivarMinisterio` (Task 9, Step 2) **só** no fluxo desta tela de confirmação — o hook de mutation simples continua existindo e servindo qualquer outro lugar que precise arquivar sem o fluxo de "digite o nome" (nenhum previsto por ora, mas mantido por paridade com `useArquivarLocalEvento`/`LocalEventoService`).

- [ ] **Step 4: Criar a página de listagem**

```tsx
// src/app/(app)/ministerios/page.tsx
'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Pencil, Archive, Users, Crown } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterios } from '@/hooks/ministerio/useMinisterios'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { ModalMinisterioForm } from './ModalMinisterioForm'
import { ModalArquivarMinisterio } from './ModalArquivarMinisterio'
import { ROTULO_MINISTERIO, ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './ministerios.module.css'

// NOTA: "Nova {ROTULO_MINISTERIO}" assume gênero feminino ("rede"), que é o que esta igreja
// usa. Se ROTULO_MINISTERIO virar "Ministério" (masculino) sem ajustar o artigo, o texto
// erra a concordância — aceitável para hardcode de piloto de 1 igreja; a versão self-service
// da Fase 5 precisa carregar o gênero junto com o rótulo (não é problema de agora).
// Rótulo de líder(es) do card — segue o mockup do Stitch (nome do líder + contagem de
// membros no próprio card, sem precisar abrir o detalhe). Sem líder ainda = "Sem líder".
function rotuloLideres(lideres: string[]): string {
  if (lideres.length === 0) return 'Sem líder'
  if (lideres.length === 1) return lideres[0]
  return `${lideres[0]} +${lideres.length - 1}`
}

export default function MinisteriosPage() {
  const role = useAuthStore((s) => s.role)
  const hidratado = useAuthStore((s) => s.hidratado)
  const podeGerenciar = podeGerenciarCadastroMinisterios(role)

  const { data: ministerios = [], isLoading } = useMinisterios()
  const [formAberto, setFormAberto] = useState<'novo' | MinisterioResponse | null>(null)
  const [arquivando, setArquivando] = useState<MinisterioResponse | null>(null)

  if (!hidratado || isLoading) {
    return <div className={styles.pagina} />
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>{ROTULO_MINISTERIO_PLURAL}</h1>
          <p className={styles.subtitulo}>{ROTULO_MINISTERIO_PLURAL} da igreja e quem participa de cada uma</p>
        </div>
        {podeGerenciar && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setFormAberto('novo')}>
            Nova {ROTULO_MINISTERIO.toLowerCase()}
          </button>
        )}
      </header>

      {ministerios.length === 0 ? (
        <EstadoVazio
          icone={Users}
          titulo={`Nenhuma ${ROTULO_MINISTERIO.toLowerCase()} cadastrada`}
          mensagem={podeGerenciar
            ? `Cadastre a primeira ${ROTULO_MINISTERIO.toLowerCase()} da igreja.`
            : `Nenhuma ${ROTULO_MINISTERIO.toLowerCase()} foi cadastrada ainda.`}
          acaoPrimaria={podeGerenciar ? { label: `Nova ${ROTULO_MINISTERIO.toLowerCase()}`, onClick: () => setFormAberto('novo') } : undefined}
        />
      ) : (
        <div className={styles.grade}>
          {ministerios.map((ministerio) => {
            const acoes: ItemAcao[] = [
              { label: 'Editar', icone: Pencil, onClick: () => setFormAberto(ministerio) },
              { label: 'Arquivar', icone: Archive, onClick: () => setArquivando(ministerio), perigo: true, separadorAntes: true },
            ]
            return (
              <div key={ministerio.id} className={styles.card}>
                <div className={styles.cardTopo}>
                  <Link href={`/ministerios/${ministerio.id}`} className={styles.cardTitulo}>
                    {ministerio.nome}
                  </Link>
                  {podeGerenciar && <MenuAcoes itens={acoes} />}
                </div>
                <div className={styles.cardLider}>
                  <Crown size={14} />
                  <span>{rotuloLideres(ministerio.lideres)}</span>
                </div>
                <div className={styles.cardMembros}>
                  {ministerio.totalMembros} {ministerio.totalMembros === 1 ? 'membro' : 'membros'}
                </div>
              </div>
            )
          })}
        </div>
      )}

      {formAberto && (
        <ModalMinisterioForm ministerio={formAberto === 'novo' ? null : formAberto} onClose={() => setFormAberto(null)} />
      )}
      {arquivando && (
        <ModalArquivarMinisterio ministerio={arquivando} onClose={() => setArquivando(null)} />
      )}
    </div>
  )
}
```

- [ ] **Step 5: Criar `ministerios.module.css` (responsivo — cards em grid, colapsa em 1 coluna no mobile)**

```css
.pagina { padding: 1.5rem; min-width: 0; }
.cabecalho {
  display: flex; align-items: center; justify-content: space-between;
  gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap;
}
.titulo { font-size: 1.5rem; font-weight: 700; }
.subtitulo { color: #6b7280; font-size: 0.875rem; }
.botaoPrimario { padding: 0.5rem 1rem; border-radius: 0.5rem; background: var(--cor-primaria, #2563eb); color: #fff; border: none; }
.grade {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 1rem; min-width: 0;
}
.card {
  display: flex; flex-direction: column; gap: 0.5rem;
  padding: 1rem; border-radius: 0.75rem; border: 1px solid #e5e7eb; min-width: 0;
}
.cardTopo { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; min-width: 0; }
.cardTitulo { font-weight: 600; text-decoration: none; color: inherit; min-width: 0; }
.cardLider {
  display: flex; align-items: center; gap: 0.375rem;
  font-size: 0.8125rem; color: #6b7280; min-width: 0;
}
.cardMembros { font-size: 0.8125rem; color: #6b7280; }

@media (max-width: 640px) {
  .cabecalho { flex-direction: column; align-items: stretch; }
  .grade { grid-template-columns: 1fr; }
}
```

- [ ] **Step 6: Rodar o front local e verificar visualmente**

Run: `npm run dev` (na pasta `frontend`), abrir `http://localhost:3000/ministerios` logado como `ADMIN_IGREJA`.
Expected: lista carrega, "Novo ministério" cria um registro que aparece na grade; testar também logado como `ACESSO_COMUM` — botão de criar não aparece, mas a lista é visível.

- [ ] **Step 7: Verificar tipos e commit**

Run: `npx tsc --noEmit`
Expected: sem erros.

```bash
git add "frontend/src/app/(app)/ministerios/" frontend/src/hooks/ministerio/useArquivarMinisterioConfirmacao.ts
git commit -m "feat(ministerio): página de listagem e cadastro de ministérios"
```

---

### Task 12: Página de detalhe `/ministerios/[id]` (membros, pedidos, pedir entrada)

**Files:**
- Create: `src/app/(app)/ministerios/[id]/page.tsx`
- Create: `src/app/(app)/ministerios/[id]/detalhe.module.css`
- Create: `src/app/(app)/ministerios/[id]/ModalAdicionarMembro.tsx`

**Interfaces:**
- Consumes: `useMinisterioDetalhe`, `useAdicionarMembro`, `useRemoverMembro`, `useAtualizarPapel`, `usePedirEntrada`, `useAceitarPedido`, `useRecusarPedido` (Task 9), `usePessoas` (hook já existente, ver `ModalInscreverPessoas.tsx`), `podeGerenciarCadastroMinisterios` (Task 10).

- [ ] **Step 1: Criar `ModalAdicionarMembro.tsx`**

Seguir o mesmo padrão de busca+seleção do `ModalInscreverPessoas.tsx` (hook `usePessoas`, `useDebounce`), mas de seleção única e chamando `useAdicionarMembro`:

```tsx
'use client'

import { useState, useRef } from 'react'
import { Search, X } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import { useAdicionarMembro } from '@/hooks/ministerio/useMembroMinisterio'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import styles from './detalhe.module.css'

interface Props {
  ministerioId: string
  membrosAtuaisIds: Set<string>
  onClose: () => void
}

// useAdicionarMembro já dispara notificar.sucesso/erro sozinho (Task 9) — aqui só fecha o
// modal em caso de sucesso; em erro, o modal continua aberto (toast já informou o motivo).
export function ModalAdicionarMembro({ ministerioId, membrosAtuaisIds, onClose }: Props) {
  const [busca, setBusca] = useState('')
  const buscaDebounced = useDebounce(busca, 300)
  const inputRef = useRef<HTMLInputElement>(null)
  const { data } = usePessoas({ q: buscaDebounced })
  const adicionar = useAdicionarMembro(ministerioId)

  async function selecionar(pessoaId: string) {
    try {
      await adicionar.mutateAsync(pessoaId)
      onClose()
    } catch {
      // erro já notificado pela mutation.
    }
  }

  const resultados = (data?.conteudo ?? []).filter((p) => !membrosAtuaisIds.has(p.id))

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div className={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div className={styles.buscaWrap}>
          <Search size={18} />
          <input
            ref={inputRef}
            autoFocus
            className={styles.buscaInput}
            placeholder="Buscar pessoa por nome"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <button type="button" onClick={onClose}><X size={18} /></button>
        </div>
        <ul className={styles.listaResultados}>
          {resultados.map((pessoa) => (
            <li key={pessoa.id} className={styles.itemResultado} onClick={() => selecionar(pessoa.id)}>
              {pessoa.fotoId ? (
                <img src={urlFoto(pessoa.fotoId, 'thumb')} alt="" className={styles.avatar} />
              ) : (
                <span className={styles.avatarIniciais}>{iniciais(pessoa.nome)}</span>
              )}
              <span>{pessoa.nome}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
```

> **Verificar antes de codar:** conferir a assinatura exata de `usePessoas` (parâmetros de busca, formato de retorno paginado) em `src/hooks/pessoa/usePessoas.ts` — o exemplo acima assume `usePessoas({ q })` retornando `{ conteudo: PessoaResponse[] }`, seguindo o padrão de paginação já visto em `PagedResponse`; ajustar para a assinatura real se divergir.

- [ ] **Step 2: Criar a página de detalhe**

```tsx
// src/app/(app)/ministerios/[id]/page.tsx
'use client'

import { useState } from 'react'
import { useParams } from 'next/navigation'
import { Check, X as XIcon, UserPlus, UserMinus, Crown } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCadastroMinisterios } from '@/lib/permissoes'
import { useMinisterioDetalhe } from '@/hooks/ministerio/useMinisterioDetalhe'
import { useRemoverMembro, useAtualizarPapel } from '@/hooks/ministerio/useMembroMinisterio'
import { usePedirEntrada, useAceitarPedido, useRecusarPedido } from '@/hooks/ministerio/usePedidoMinisterio'
import { iniciais } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
import { ModalAdicionarMembro } from './ModalAdicionarMembro'
import styles from './detalhe.module.css'

// Todas as mutations usadas aqui (useRemoverMembro, useAtualizarPapel, usePedirEntrada,
// useAceitarPedido, useRecusarPedido) já disparam notificar.sucesso/erro sozinhas (Task 9)
// — este componente só chama .mutate()/.mutateAsync(), sem repetir o toast.
export default function MinisterioDetalhePage() {
  const { id } = useParams<{ id: string }>()
  const role = useAuthStore((s) => s.role)
  const isAdmin = podeGerenciarCadastroMinisterios(role)

  const { data: ministerio, isLoading } = useMinisterioDetalhe(id)
  const removerMembro = useRemoverMembro(id)
  const atualizarPapel = useAtualizarPapel(id)
  const pedirEntrada = usePedirEntrada(id)
  const aceitarPedido = useAceitarPedido(id)
  const recusarPedido = useRecusarPedido(id)

  const [adicionarAberto, setAdicionarAberto] = useState(false)

  if (isLoading || !ministerio) {
    return <div className={styles.pagina} />
  }

  // souMembroAtivo/tenhoPedidoPendente vêm prontos do backend (GET /ministerios/{id}) —
  // o authStore não guarda pessoaId, só usuarioId/role, então o cálculo é feito no
  // service (MinisterioService.detalhe), que já sabe a pessoa logada via UsuarioAutenticado.
  const souMembro = ministerio.souMembroAtivo
  const jaTemPedido = ministerio.tenhoPedidoPendente
  const podeGerenciarMembros = ministerio.souLiderDesteMinisterio

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>{ministerio.nome}</h1>
        {podeGerenciarMembros && (
          <button type="button" className={styles.botaoPrimario} onClick={() => setAdicionarAberto(true)}>
            <UserPlus size={16} /> Adicionar pessoa
          </button>
        )}
        {!podeGerenciarMembros && !souMembro && !jaTemPedido && (
          <button type="button" className={styles.botaoPrimario} onClick={() => pedirEntrada.mutate()}>
            Pedir para entrar
          </button>
        )}
        {!podeGerenciarMembros && jaTemPedido && (
          <span className={styles.tagPendente}>Pedido enviado — aguardando aprovação</span>
        )}
      </header>

      {podeGerenciarMembros && ministerio.pedidosPendentes.length > 0 && (
        <section className={styles.secao}>
          <h2 className={styles.subtitulo}>Pedidos pendentes</h2>
          <ul className={styles.lista}>
            {ministerio.pedidosPendentes.map((membro) => (
              <li key={membro.pessoaId} className={styles.itemMembro}>
                <span className={styles.nomeMembro}>{membro.nome}</span>
                <div className={styles.acoesPedido}>
                  <button type="button" className={styles.botaoAceitar}
                    onClick={() => aceitarPedido.mutate(membro.pessoaId)}>
                    <Check size={16} />
                  </button>
                  <button type="button" className={styles.botaoRecusar}
                    onClick={() => recusarPedido.mutate(membro.pessoaId)}>
                    <XIcon size={16} />
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className={styles.secao}>
        <h2 className={styles.subtitulo}>Membros</h2>
        {ministerio.membros.length === 0 ? (
          <EstadoVazio titulo="Nenhum membro ainda" mensagem={`Adicione pessoas a esta ${ROTULO_MINISTERIO.toLowerCase()}.`} />
        ) : (
          <ul className={styles.lista}>
            {ministerio.membros.map((membro) => (
              <li key={membro.pessoaId} className={styles.itemMembro}>
                {membro.fotoId ? (
                  <img src={urlFoto(membro.fotoId, 'thumb')} alt="" className={styles.avatar} />
                ) : (
                  <span className={styles.avatarIniciais}>{iniciais(membro.nome)}</span>
                )}
                <span className={styles.nomeMembro}>{membro.nome}</span>
                {membro.papel === 'LIDER' && (
                  <span className={styles.badgeLider}><Crown size={12} /> Líder</span>
                )}
                {isAdmin && (
                  <button type="button" className={styles.botaoPromover}
                    onClick={() => atualizarPapel.mutate({
                      pessoaId: membro.pessoaId,
                      papel: membro.papel === 'LIDER' ? 'MEMBRO' : 'LIDER',
                    })}>
                    {membro.papel === 'LIDER' ? 'Remover liderança' : 'Tornar líder'}
                  </button>
                )}
                {podeGerenciarMembros && (
                  <button type="button" className={styles.botaoRemover}
                    onClick={() => removerMembro.mutate(membro.pessoaId)}>
                    <UserMinus size={16} />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {adicionarAberto && (
        <ModalAdicionarMembro
          ministerioId={id}
          membrosAtuaisIds={new Set(ministerio.membros.map((m) => m.pessoaId))}
          onClose={() => setAdicionarAberto(false)}
        />
      )}
    </div>
  )
}
```

- [ ] **Step 3: CSS responsivo (`detalhe.module.css`) — lista vira cards empilhados no mobile**

```css
.pagina { padding: 1.5rem; min-width: 0; }
.cabecalho {
  display: flex; align-items: center; justify-content: space-between;
  gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap;
}
.titulo { font-size: 1.5rem; font-weight: 700; }
.subtitulo { font-size: 1.125rem; font-weight: 600; margin-bottom: 0.75rem; }
.secao { margin-bottom: 2rem; min-width: 0; }
.lista { display: flex; flex-direction: column; gap: 0.5rem; list-style: none; padding: 0; }
.itemMembro {
  display: flex; align-items: center; gap: 0.75rem; padding: 0.75rem;
  border: 1px solid #e5e7eb; border-radius: 0.5rem; min-width: 0; flex-wrap: wrap;
}
.nomeMembro { flex: 1; min-width: 0; }
.avatar { width: 32px; height: 32px; border-radius: 50%; object-fit: cover; }
.avatarIniciais {
  width: 32px; height: 32px; border-radius: 50%; background: #e5e7eb;
  display: flex; align-items: center; justify-content: center; font-size: 0.75rem; font-weight: 600;
}
.badgeLider {
  display: inline-flex; align-items: center; gap: 0.25rem; font-size: 0.75rem;
  background: #fef3c7; color: #92400e; padding: 0.125rem 0.5rem; border-radius: 999px;
}
.botaoPrimario {
  display: inline-flex; align-items: center; gap: 0.375rem;
  padding: 0.5rem 1rem; border-radius: 0.5rem; background: var(--cor-primaria, #2563eb); color: #fff; border: none;
}
.botaoAceitar, .botaoRecusar, .botaoPromover, .botaoRemover {
  padding: 0.375rem 0.625rem; border-radius: 0.375rem; border: 1px solid #d1d5db; background: #fff;
}
.acoesPedido { display: flex; gap: 0.5rem; }
.tagPendente { font-size: 0.875rem; color: #6b7280; }

@media (max-width: 640px) {
  .cabecalho { flex-direction: column; align-items: stretch; }
  .itemMembro { flex-direction: row; flex-wrap: wrap; }
}
```

- [ ] **Step 4: Teste manual completo do fluxo**

Run: `npm run dev`, testar como `ADMIN_IGREJA`: criar ministério, entrar no detalhe, adicionar membro, promover a líder. Logar como a pessoa promovida (ou simular via outro usuário) e testar aceitar/recusar pedido feito por um terceiro usuário `ACESSO_COMUM` que clicou "Pedir para entrar" em outro ministério.
Expected: fluxo completo funciona; usuário sem gerência não vê botões de adicionar/remover/promover, só "pedir para entrar" (ou "pedido enviado" se já pediu).

- [ ] **Step 5: Verificar tipos e commit**

Run: `npx tsc --noEmit`
Expected: sem erros.

```bash
git add "frontend/src/app/(app)/ministerios/[id]/"
git commit -m "feat(ministerio): página de detalhe com membros, pedidos e pedir entrada"
```

---

### Task 13: Remover `MinisterioInput` do formulário de pessoa e exibir ministérios no perfil

**Files:**
- Modify: `src/components/module/pessoas/PessoaForm.tsx`
- Delete: `src/components/module/pessoas/MinisterioInput.tsx`
- Delete: `src/components/module/pessoas/MinisterioInput.module.css`
- Modify: `src/app/(app)/perfil/page.tsx`
- Modify: componente de exibição de detalhe de pessoa (`DrawerDetalhePessoa.tsx` — confirmar caminho exato com `grep -rn "DrawerDetalhePessoa" frontend/src --include=*.tsx -l`)

**Interfaces:**
- Consumes: `usePessoaMinisterios` (Task 9).

- [ ] **Step 1: Remover o bloco de Ministério de `PessoaForm.tsx`**

Remover a linha 62 (`const ministerioAtual = ...`), o import de `MinisterioInput`, e o bloco JSX (linhas 207-212) mostrado no relatório de exploração — **sem tocar** no bloco de `CARGO` logo abaixo (campo separado, mesmo wrapper CSS).

- [ ] **Step 2: Apagar `MinisterioInput.tsx` e `MinisterioInput.module.css`**

```bash
git rm frontend/src/components/module/pessoas/MinisterioInput.tsx frontend/src/components/module/pessoas/MinisterioInput.module.css
```

- [ ] **Step 3: Remover uso equivalente em `app/(app)/perfil/page.tsx`**

Aplicar a mesma remoção (linhas 203-210 mencionadas no relatório de exploração) — o campo de ministério não é mais editável ali; a Task 4 abaixo adiciona a exibição somente-leitura via `usePessoaMinisterios`.

- [ ] **Step 4: Adicionar seção de rótulo (`ROTULO_MINISTERIO_PLURAL`) no drawer/perfil de detalhe da pessoa**

Localizar o arquivo exato com:
```bash
grep -rln "DrawerDetalhePessoa" frontend/src --include=*.tsx
```
No componente encontrado, adicionar (próximo de onde hoje mostrava `pessoa.ministerio` como texto cru, linhas ~118-123 do relatório de exploração):

```tsx
import { usePessoaMinisterios } from '@/hooks/pessoa/usePessoaMinisterios'
import { ROTULO_MINISTERIO_PLURAL } from '@/lib/rotulosMinisterio'

// dentro do componente, após obter `pessoa`:
const { data: ministerios = [] } = usePessoaMinisterios(pessoa.id)

// no JSX, substituindo o texto cru antigo de "Ministério":
<div className={styles.campo}>
  <span className={styles.rotulo}>{ROTULO_MINISTERIO_PLURAL}</span>
  {ministerios.length === 0 ? (
    <span className={styles.valorVazio}>Nenhum</span>
  ) : (
    <div className={styles.chipsMinisterio}>
      {ministerios.map((m) => (
        <span key={m.id} className={styles.chip}>{m.nome}</span>
      ))}
    </div>
  )}
</div>
```

> **Verificar antes de codar:** os nomes exatos de classe (`styles.campo`, `styles.rotulo`, `styles.valorVazio`) devem ser conferidos no CSS module real do componente — usar as classes já existentes no arquivo para os outros campos (ex.: `estadoCivil`, `sexo`) como referência, em vez de inventar novas. Adicionar só `.chipsMinisterio`/`.chip` se não existir algo equivalente.

- [ ] **Step 5: Teste manual**

Run: `npm run dev`, abrir cadastro/edição de pessoa (confirmar que o campo Ministério sumiu, mantendo Cargo), abrir o drawer/perfil de uma pessoa que foi adicionada a um ministério na Task 12 e confirmar que aparece na seção "Ministérios".
Expected: sem campo de ministério editável em nenhum formulário de pessoa; exibição correta no perfil.

- [ ] **Step 6: Verificar tipos e commit**

Run: `npx tsc --noEmit`
Expected: sem erros.

```bash
git add frontend/src/components/module/pessoas/PessoaForm.tsx "frontend/src/app/(app)/perfil/page.tsx"
git add -u frontend/src/components/module/pessoas/
git commit -m "refactor(pessoa): remove input livre de ministério, exibe ministérios estruturados no perfil"
```

---

## Self-Review Notes (para quem executa)

- Alguns steps marcados **"Verificar antes de codar"** dependem de conferir um arquivo real do repo antes de finalizar (nome de hook, classe CSS, campo do store). Isso é intencional — o agente que explorou o código não teve acesso a 100% dos arquivos de frontend (não achou telas de gestão equivalentes pra `LocalEvento`/`CategoriaFinanceira` que servissem de referência completa para o padrão de modal de confirmação e paginação de `usePessoas`). Não pule esses steps.
- `ModalArquivarMinisterio.tsx` (Task 11, Step 3) tem um corpo deliberadamente incompleto — o step seguinte instrui a copiar `ModalArquivarLocal.tsx` como referência real antes de finalizar. Isso não é um placeholder esquecido; é a instrução explícita do plano para esse componente específico.
