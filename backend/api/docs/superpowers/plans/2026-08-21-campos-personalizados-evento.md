# Campos Personalizados de Evento (Spec 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar admin/líder montar um formulário extra por evento (rótulo, tipo, obrigatório
ou não) e as respostas ficarem vinculadas à inscrição — titular e cada acompanhante — visíveis
na lista de inscritos.

**Architecture:** Duas tabelas novas (`campo_personalizado_evento` = definição,
`resposta_campo_personalizado` = valor snapshot em texto). Serviço próprio
(`CampoPersonalizadoService`) separado de `InscricaoService` — nunca mexe em `inscrever()`, por
isso a obrigatoriedade nunca trava inscrição em lote, só fica pendente. Front: um bloco novo
dentro de `EventoForm.tsx` (config, só em edição) e um bloco novo dentro do drawer de detalhe do
evento (responder + ver respostas).

**Tech Stack:** Spring Boot / JPA / Postgres (Flyway) no back; Next.js / React Hook Form / Zod
no front — mesmo stack do resto do projeto, nada novo.

**Spec:** `docs/superpowers/specs/2026-08-21-campos-personalizados-evento-design.md`

## Global Constraints

- `igreja_id` sempre extraído do JWT (via `UsuarioAutenticado`), nunca do corpo da requisição.
- Soft delete em `campo_personalizado_evento` (`@SQLDelete`/`@SQLRestriction`, padrão do
  projeto) — nunca `DELETE` de verdade em uso normal.
- Editar/apagar campo (inclusive opções) é **livre, sem trava**, mesmo com resposta já dada —
  resposta guarda snapshot em texto, nunca referência a uma opção.
- Campo obrigatório nunca bloqueia `InscricaoService.inscrever()`/`inscreverPessoas()` — a
  validação de obrigatoriedade só existe em `CampoPersonalizadoService.responder()`.
- Campos personalizados valem tanto pro titular quanto pra cada acompanhante.
- `visivel_ao_publico` entra no schema nesta spec (groundwork pra Spec 2), sem nenhum efeito
  aqui — sempre `true` por padrão, sem UI funcional pra ele ainda (toggle desabilitado).
- Testes de service: Mockito puro, estilo A do projeto (`mock()` manual no `@BeforeEach`, sem
  `@ExtendWith(MockitoExtension.class)`) — é o estilo dominante (~15 arquivos) e o que
  `EventoServiceTest.java` usa.
- Rodar suite completa (`set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`) antes
  de cada commit de task do backend — precisa de Docker rodando (Testcontainers).

---

## Backend

### Task 1: `CampoPersonalizadoEvento` — migration, enum, entidade, repositório

**Files:**
- Create: `src/main/resources/db/migration/V23__campo_personalizado_evento.sql`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/TipoCampoPersonalizado.java`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEvento.java`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEventoRepository.java`
- Test: `src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEventoRepositoryTest.java`

**Interfaces:**
- Produces: `TipoCampoPersonalizado` enum (`TEXTO_CURTO`, `OPCAO_UNICA`, `MULTIPLA_ESCOLHA`,
  `SIM_NAO`); `CampoPersonalizadoEvento` entidade com `getOpcoesComoLista(): List<String>` e
  `setOpcoesComoLista(List<String>)`; `CampoPersonalizadoEventoRepository` com
  `findByEventoIdAndIgrejaIdOrderByOrdemAsc(UUID eventoId, UUID igrejaId): List<CampoPersonalizadoEvento>`
  e `findByIdAndIgrejaId(UUID id, UUID igrejaId): Optional<CampoPersonalizadoEvento>`.

- [ ] **Step 1: Escrever a migration**

```sql
CREATE TABLE campo_personalizado_evento (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id           UUID NOT NULL REFERENCES igreja(id),
    evento_id           UUID NOT NULL REFERENCES evento(id),
    label               VARCHAR(120) NOT NULL,
    placeholder         VARCHAR(160),
    tipo                VARCHAR(20) NOT NULL,
    opcoes              TEXT,
    obrigatorio         BOOLEAN NOT NULL DEFAULT FALSE,
    visivel_ao_publico  BOOLEAN NOT NULL DEFAULT TRUE,
    ordem               INTEGER NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_campo_personalizado_evento ON campo_personalizado_evento (evento_id);
```

- [ ] **Step 2: Criar o enum**

```java
package com.domus.api.modules.evento.campopersonalizado;

public enum TipoCampoPersonalizado {
    TEXTO_CURTO,
    OPCAO_UNICA,
    MULTIPLA_ESCOLHA,
    SIM_NAO
}
```

- [ ] **Step 3: Criar a entidade**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campo_personalizado_evento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE campo_personalizado_evento SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CampoPersonalizadoEvento {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 160)
    private String placeholder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCampoPersonalizado tipo;

    /** Uma opção por linha; só usado quando tipo é OPCAO_UNICA ou MULTIPLA_ESCOLHA. */
    @Column(columnDefinition = "TEXT")
    private String opcoes;

    @Column(nullable = false)
    @Builder.Default
    private boolean obrigatorio = false;

    /** Groundwork pra Spec 2 (formulário público) — sem efeito nenhum nesta spec. */
    @Column(name = "visivel_ao_publico", nullable = false)
    @Builder.Default
    private boolean visivelAoPublico = true;

    @Column(nullable = false)
    @Builder.Default
    private int ordem = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public List<String> getOpcoesComoLista() {
        if (opcoes == null || opcoes.isBlank()) return List.of();
        List<String> resultado = new ArrayList<>();
        for (String linha : opcoes.split("\n")) {
            String limpa = linha.trim();
            if (!limpa.isEmpty()) resultado.add(limpa);
        }
        return resultado;
    }

    public void setOpcoesComoLista(List<String> lista) {
        this.opcoes = (lista == null || lista.isEmpty()) ? null : String.join("\n", lista);
    }
}
```

- [ ] **Step 4: Criar o repositório**

```java
package com.domus.api.modules.evento.campopersonalizado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampoPersonalizadoEventoRepository extends JpaRepository<CampoPersonalizadoEvento, UUID> {

    List<CampoPersonalizadoEvento> findByEventoIdAndIgrejaIdOrderByOrdemAsc(UUID eventoId, UUID igrejaId);

    Optional<CampoPersonalizadoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
```

- [ ] **Step 5: Escrever o teste do repositório**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampoPersonalizadoEventoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired CampoPersonalizadoEventoRepository repository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EventoRepository eventoRepository;

    @Test
    void salvaERecuperaOrdenadoPorOrdem() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste Campo Personalizado");
        igreja.setEmailContato("campo@teste.com");
        igreja = igrejaRepository.save(igreja);

        Evento evento = Evento.builder()
                .igreja(igreja).titulo("Retiro de Jovens")
                .inicioEm(LocalDateTime.now().plusDays(10))
                .build();
        evento = eventoRepository.save(evento);

        CampoPersonalizadoEvento segundo = CampoPersonalizadoEvento.builder()
                .igreja(igreja).evento(evento).label("Tamanho da camiseta")
                .tipo(TipoCampoPersonalizado.OPCAO_UNICA).ordem(1).build();
        segundo.setOpcoesComoLista(List.of("P", "M", "G"));
        repository.save(segundo);

        CampoPersonalizadoEvento primeiro = CampoPersonalizadoEvento.builder()
                .igreja(igreja).evento(evento).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).ordem(0).build();
        repository.save(primeiro);

        List<CampoPersonalizadoEvento> campos =
                repository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(evento.getId(), igreja.getId());

        assertThat(campos).extracting(CampoPersonalizadoEvento::getLabel)
                .containsExactly("Restrição alimentar", "Tamanho da camiseta");
        assertThat(campos.get(1).getOpcoesComoLista()).containsExactly("P", "M", "G");
    }
}
```

- [ ] **Step 6: Rodar o teste e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoEventoRepositoryTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V23__campo_personalizado_evento.sql \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/TipoCampoPersonalizado.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEvento.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEventoRepository.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoEventoRepositoryTest.java
git commit -m "feat(evento): migration, enum e entidade CampoPersonalizadoEvento"
```

---

### Task 2: `RespostaCampoPersonalizado` — migration, entidade, repositório

**Files:**
- Modify: `src/main/resources/db/migration/V23__campo_personalizado_evento.sql`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizado.java`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizadoRepository.java`
- Test: `src/test/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizadoRepositoryTest.java`

**Interfaces:**
- Consumes: `CampoPersonalizadoEvento` (Task 1); `InscricaoEvento`/`AcompanhanteInscricao`
  (`com.domus.api.modules.evento.inscricao`, já existem).
- Produces: `RespostaCampoPersonalizado` entidade; `RespostaCampoPersonalizadoRepository` com
  `findByInscricaoId(UUID inscricaoId): List<RespostaCampoPersonalizado>` e
  `findByCampoIdAndInscricaoIdAndAcompanhanteId(UUID campoId, UUID inscricaoId, UUID acompanhanteId): Optional<RespostaCampoPersonalizado>`.

- [ ] **Step 1: Adicionar a tabela na mesma migration da Task 1**

```sql
CREATE TABLE resposta_campo_personalizado (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campo_id        UUID NOT NULL REFERENCES campo_personalizado_evento(id),
    inscricao_id    UUID NOT NULL REFERENCES inscricao_evento(id),
    acompanhante_id UUID REFERENCES acompanhante_inscricao(id),
    valor           TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX idx_resposta_campo_inscricao ON resposta_campo_personalizado (inscricao_id);

-- UNIQUE simples não serve: no Postgres, NULL nunca é igual a NULL numa constraint UNIQUE,
-- então duas respostas do titular (acompanhante_id sempre NULL) não seriam bloqueadas.
CREATE UNIQUE INDEX idx_resposta_titular_unica
    ON resposta_campo_personalizado (campo_id, inscricao_id)
    WHERE acompanhante_id IS NULL;

CREATE UNIQUE INDEX idx_resposta_acompanhante_unica
    ON resposta_campo_personalizado (campo_id, acompanhante_id)
    WHERE acompanhante_id IS NOT NULL;
```

Adicione este bloco **no final** do arquivo `V23__campo_personalizado_evento.sql` criado na
Task 1 (migration já aplicada em nenhum ambiente ainda, então é seguro editar o mesmo arquivo
em vez de criar V24).

- [ ] **Step 2: Criar a entidade**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.inscricao.AcompanhanteInscricao;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resposta_campo_personalizado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RespostaCampoPersonalizado {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campo_id", nullable = false)
    private CampoPersonalizadoEvento campo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false)
    private InscricaoEvento inscricao;

    /** NULL = resposta do titular; preenchido = resposta desse acompanhante específico. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acompanhante_id")
    private AcompanhanteInscricao acompanhante;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 3: Criar o repositório**

```java
package com.domus.api.modules.evento.campopersonalizado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RespostaCampoPersonalizadoRepository extends JpaRepository<RespostaCampoPersonalizado, UUID> {

    List<RespostaCampoPersonalizado> findByInscricaoId(UUID inscricaoId);

    Optional<RespostaCampoPersonalizado> findByCampoIdAndInscricaoIdAndAcompanhanteId(
            UUID campoId, UUID inscricaoId, UUID acompanhanteId);
}
```

> Nota: Spring Data JPA converte automaticamente `propriedade = :param` em `propriedade IS
> NULL` quando o parâmetro chega `null` em tempo de execução — por isso este método funciona
> tanto pra resposta de acompanhante (`acompanhanteId` preenchido) quanto de titular
> (`acompanhanteId = null`), sem precisar de dois métodos.

- [ ] **Step 4: Escrever o teste do repositório**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RespostaCampoPersonalizadoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired RespostaCampoPersonalizadoRepository respostaRepository;
    @Autowired CampoPersonalizadoEventoRepository campoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired IgrejaRepository igrejaRepository;

    @Test
    void encontraRespostaDoTitularPorAcompanhanteIdNulo() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste Resposta");
        igreja.setEmailContato("resposta@teste.com");
        igreja = igrejaRepository.save(igreja);

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Retiro").inicioEm(LocalDateTime.now().plusDays(5)).build());

        CampoPersonalizadoEvento campo = campoRepository.save(CampoPersonalizadoEvento.builder()
                .igreja(igreja).evento(evento).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).build());

        InscricaoEvento inscricao = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).status(StatusInscricao.CONFIRMADA).build());

        respostaRepository.save(RespostaCampoPersonalizado.builder()
                .campo(campo).inscricao(inscricao).valor("Sem lactose").build());

        Optional<RespostaCampoPersonalizado> encontrada = respostaRepository
                .findByCampoIdAndInscricaoIdAndAcompanhanteId(campo.getId(), inscricao.getId(), null);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getValor()).isEqualTo("Sem lactose");
    }
}
```

- [ ] **Step 5: Rodar o teste e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=RespostaCampoPersonalizadoRepositoryTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration/V23__campo_personalizado_evento.sql \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizado.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizadoRepository.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/RespostaCampoPersonalizadoRepositoryTest.java
git commit -m "feat(evento): entidade RespostaCampoPersonalizado com indices unicos parciais"
```

---

### Task 3: DTOs de configuração — `CampoPersonalizadoRequest`/`Response`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequest.java`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequestTest.java`

**Interfaces:**
- Consumes: `TipoCampoPersonalizado`, `CampoPersonalizadoEvento` (Task 1).
- Produces: `CampoPersonalizadoRequest(UUID id, String label, String placeholder, TipoCampoPersonalizado tipo, List<String> opcoes, boolean obrigatorio, boolean visivelAoPublico, int ordem)`
  com `isOpcoesValidas(): boolean`; `CampoPersonalizadoResponse.from(CampoPersonalizadoEvento): CampoPersonalizadoResponse`.

- [ ] **Step 1: Escrever o teste de validação (falhando)**

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CampoPersonalizadoRequestTest {

    private final Validator validator;

    CampoPersonalizadoRequestTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void opcaoUnicaSemOpcoesEInvalida() {
        var request = new CampoPersonalizadoRequest(
                null, "Tamanho da camiseta", null, TipoCampoPersonalizado.OPCAO_UNICA,
                List.of(), false, true, 0);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void textoCurtoSemOpcoesEValido() {
        var request = new CampoPersonalizadoRequest(
                UUID.randomUUID(), "Restrição alimentar", "Ex.: sem lactose",
                TipoCampoPersonalizado.TEXTO_CURTO, null, true, true, 1);

        assertThat(validator.validate(request)).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar (classe não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoRequestTest`
Expected: FAIL — `cannot find symbol CampoPersonalizadoRequest`

- [ ] **Step 3: Criar `CampoPersonalizadoRequest`**

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** {@code id} nulo = campo novo; preenchido = atualiza o existente (ver salvar() na Task 4). */
public record CampoPersonalizadoRequest(
        UUID id,
        @NotBlank(message = "O rótulo é obrigatório.")
        @Size(max = 120, message = "Máximo 120 caracteres.")
        String label,
        @Size(max = 160, message = "Máximo 160 caracteres.")
        String placeholder,
        @NotNull(message = "Escolha o tipo do campo.")
        TipoCampoPersonalizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean visivelAoPublico,
        int ordem
) {
    @AssertTrue(message = "Informe pelo menos uma opção.")
    public boolean isOpcoesValidas() {
        boolean precisaDeOpcoes = tipo == TipoCampoPersonalizado.OPCAO_UNICA
                || tipo == TipoCampoPersonalizado.MULTIPLA_ESCOLHA;
        return !precisaDeOpcoes || (opcoes != null && !opcoes.isEmpty());
    }
}
```

- [ ] **Step 4: Criar `CampoPersonalizadoResponse`**

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEvento;
import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;

import java.util.List;
import java.util.UUID;

public record CampoPersonalizadoResponse(
        UUID id,
        String label,
        String placeholder,
        TipoCampoPersonalizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean visivelAoPublico,
        int ordem
) {
    public static CampoPersonalizadoResponse from(CampoPersonalizadoEvento c) {
        return new CampoPersonalizadoResponse(
                c.getId(), c.getLabel(), c.getPlaceholder(), c.getTipo(),
                c.getOpcoesComoLista(), c.isObrigatorio(), c.isVisivelAoPublico(), c.getOrdem()
        );
    }
}
```

- [ ] **Step 5: Rodar o teste e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoRequestTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequest.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoResponse.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/DTOs/CampoPersonalizadoRequestTest.java
git commit -m "feat(evento): DTOs de configuracao de campo personalizado"
```

---

### Task 4: `CampoPersonalizadoService.listar` e `salvar`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java`

**Interfaces:**
- Consumes: `CampoPersonalizadoEventoRepository` (Task 1), `EventoRepository`
  (`com.domus.api.modules.evento`, já existe — `findByIdAndIgrejaId`).
- Produces: `CampoPersonalizadoService.listar(UUID eventoId, UUID igrejaId): List<CampoPersonalizadoResponse>`;
  `CampoPersonalizadoService.salvar(UUID eventoId, UUID igrejaId, List<CampoPersonalizadoRequest> dados): List<CampoPersonalizadoResponse>`.

- [ ] **Step 1: Escrever o teste de `salvar` (falhando)**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CampoPersonalizadoServiceTest {

    CampoPersonalizadoEventoRepository campoRepository;
    RespostaCampoPersonalizadoRepository respostaRepository;
    EventoRepository eventoRepository;
    com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository;
    CampoPersonalizadoService service;

    UUID igrejaId;
    UUID eventoId;

    @BeforeEach
    void setup() {
        campoRepository = mock(CampoPersonalizadoEventoRepository.class);
        respostaRepository = mock(RespostaCampoPersonalizadoRepository.class);
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(com.domus.api.modules.evento.inscricao.InscricaoRepository.class);
        service = new CampoPersonalizadoService(campoRepository, respostaRepository, eventoRepository, inscricaoRepository);

        igrejaId = UUID.randomUUID();
        eventoId = UUID.randomUUID();
    }

    private Evento evento() {
        return Evento.builder().id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Retiro de Jovens").build();
    }

    @Test
    void salvarCriaCamposNovosQuandoIdENulo() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());
        when(campoRepository.save(any())).thenAnswer(inv -> {
            CampoPersonalizadoEvento c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var request = new CampoPersonalizadoRequest(
                null, "Tamanho da camiseta", null, TipoCampoPersonalizado.OPCAO_UNICA,
                List.of("P", "M", "G"), true, true, 0);

        List<CampoPersonalizadoResponse> resultado = service.salvar(eventoId, igrejaId, List.of(request));

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).label()).isEqualTo("Tamanho da camiseta");
        assertThat(resultado.get(0).opcoes()).containsExactly("P", "M", "G");
        verify(campoRepository).save(any());
    }

    @Test
    void salvarArquivaCampoQueSumiuDaListaEnviada() {
        var existente = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Campo antigo").tipo(TipoCampoPersonalizado.TEXTO_CURTO).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));

        service.salvar(eventoId, igrejaId, List.of());

        verify(campoRepository).delete(existente);
    }

    @Test
    void salvarLancaNotFoundQuandoEventoNaoPertenceAIgreja() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salvar(eventoId, igrejaId, List.of()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar (classe não existe)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: FAIL — `cannot find symbol CampoPersonalizadoService`

- [ ] **Step 3: Implementar o service**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CampoPersonalizadoService {

    private final CampoPersonalizadoEventoRepository campoRepository;
    private final RespostaCampoPersonalizadoRepository respostaRepository;
    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;

    public List<CampoPersonalizadoResponse> listar(UUID eventoId, UUID igrejaId) {
        return campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId).stream()
                .map(CampoPersonalizadoResponse::from)
                .toList();
    }

    /** Substitui a lista inteira: cria o que não tem id, atualiza o que tem, arquiva
     *  (soft delete) o que já existia e sumiu da lista enviada. Editar campo (inclusive
     *  opções) é sempre livre, mesmo com resposta já dada — resposta guarda snapshot. */
    @Transactional
    public List<CampoPersonalizadoResponse> salvar(UUID eventoId, UUID igrejaId, List<CampoPersonalizadoRequest> dados) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        List<CampoPersonalizadoEvento> existentes =
                campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId);
        Map<UUID, CampoPersonalizadoEvento> porId = new HashMap<>();
        for (CampoPersonalizadoEvento c : existentes) porId.put(c.getId(), c);

        java.util.Set<UUID> idsEnviados = new java.util.HashSet<>();
        List<CampoPersonalizadoEvento> resultado = new java.util.ArrayList<>();

        for (CampoPersonalizadoRequest r : dados) {
            CampoPersonalizadoEvento campo = r.id() != null ? porId.get(r.id()) : null;
            if (campo == null) {
                campo = CampoPersonalizadoEvento.builder().igreja(evento.getIgreja()).evento(evento).build();
            } else {
                idsEnviados.add(campo.getId());
            }
            campo.setLabel(r.label());
            campo.setPlaceholder(r.placeholder());
            campo.setTipo(r.tipo());
            campo.setOpcoesComoLista(r.opcoes());
            campo.setObrigatorio(r.obrigatorio());
            campo.setVisivelAoPublico(r.visivelAoPublico());
            campo.setOrdem(r.ordem());
            resultado.add(campoRepository.save(campo));
        }

        for (CampoPersonalizadoEvento existente : existentes) {
            if (!idsEnviados.contains(existente.getId())) {
                campoRepository.delete(existente);
            }
        }

        return resultado.stream().map(CampoPersonalizadoResponse::from).toList();
    }
}
```

- [ ] **Step 4: Rodar o teste e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: PASS (3 testes)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java
git commit -m "feat(evento): CampoPersonalizadoService.listar e salvar"
```

---

### Task 5: DTOs de resposta + `CampoPersonalizadoService.responder`/`respostasPorInscricao`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/RespostaRequest.java`
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/RespostaResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java`
- Modify: `src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java`

**Interfaces:**
- Consumes: `InscricaoRepository` (`findByIdAndIgrejaId`, já existe), `InscricaoEvento`
  (`getPessoa()`, `getAcompanhantes()`), `Permissoes.podeGerenciarEventos(String role)`
  (`com.domus.api.shared.security`, já existe).
- Produces: `RespostaRequest(UUID campoId, String valor)`; `RespostaResponse(UUID campoId, String label, TipoCampoPersonalizado tipo, String valor)`;
  `CampoPersonalizadoService.responder(UUID inscricaoId, UUID acompanhanteId, List<RespostaRequest> respostas, UUID igrejaId, UUID pessoaLogadaId, String role): void`;
  `CampoPersonalizadoService.respostasPorInscricao(UUID inscricaoId, UUID acompanhanteId, UUID igrejaId): List<RespostaResponse>`.

- [ ] **Step 1: Criar os DTOs de resposta**

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code valor}: pra MULTIPLA_ESCOLHA, o front já manda serializado como
 *  {@code "opção 1 | opção 2"} (mesmo separador salvo em texto — ver spec). */
public record RespostaRequest(
        @NotNull(message = "Campo inválido.") UUID campoId,
        String valor
) {}
```

```java
package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizado;
import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;

import java.util.UUID;

public record RespostaResponse(
        UUID campoId,
        String label,
        TipoCampoPersonalizado tipo,
        String valor
) {
    public static RespostaResponse from(RespostaCampoPersonalizado r) {
        return new RespostaResponse(r.getCampo().getId(), r.getCampo().getLabel(), r.getCampo().getTipo(), r.getValor());
    }
}
```

- [ ] **Step 2: Escrever os testes de `responder` (falhando)**

Adicionar ao `CampoPersonalizadoServiceTest.java` da Task 4:

```java
    @Test
    void respondeComTodosObrigatoriosPreenchidosSalvaAsRespostas() {
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID campoObrigatorioId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(pessoaId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        var campoObrigatorio = CampoPersonalizadoEvento.builder()
                .id(campoObrigatorioId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).obrigatorio(true).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoObrigatorio));
        when(respostaRepository.findByCampoIdAndInscricaoIdAndAcompanhanteId(campoObrigatorioId, inscricaoId, null))
                .thenReturn(Optional.empty());

        service.responder(inscricaoId, null,
                List.of(new com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest(campoObrigatorioId, "Sem lactose")),
                igrejaId, pessoaId, "ACESSO_COMUM");

        verify(respostaRepository).save(any());
    }

    @Test
    void respondeSemPreencherObrigatorioLancaExcecao() {
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID campoObrigatorioId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(pessoaId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        var campoObrigatorio = CampoPersonalizadoEvento.builder()
                .id(campoObrigatorioId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).obrigatorio(true).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoObrigatorio));

        assertThatThrownBy(() -> service.responder(inscricaoId, null, List.of(), igrejaId, pessoaId, "ACESSO_COMUM"))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);

        verify(respostaRepository, never()).save(any());
    }

    @Test
    void terceiroSemGerenciarNaoPodeResponderPorOutraPessoa() {
        UUID inscricaoId = UUID.randomUUID();
        UUID donoDaInscricaoId = UUID.randomUUID();
        UUID quemTaTentandoId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(donoDaInscricaoId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.responder(inscricaoId, null, List.of(), igrejaId, quemTaTentandoId, "ACESSO_COMUM"))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);
    }

    @Test
    void gestorPodeResponderPorQualquerPessoa() {
        UUID inscricaoId = UUID.randomUUID();
        UUID donoDaInscricaoId = UUID.randomUUID();
        UUID gestorId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(donoDaInscricaoId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());

        service.responder(inscricaoId, null, List.of(), igrejaId, gestorId, "LIDER");

        // Não lança — chegou até o fim sem exceção de autorização.
    }
```

- [ ] **Step 3: Rodar e ver falhar (métodos não existem)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: FAIL — `cannot find symbol responder`

- [ ] **Step 4: Implementar `responder` e `respostasPorInscricao`**

Adicionar ao final de `CampoPersonalizadoService.java`:

```java
    /** Titular responde quando {@code acompanhanteId == null}; senão, responde por esse
     *  acompanhante específico. Valida obrigatoriedade aqui — nunca em inscrever(). */
    @Transactional
    public void responder(UUID inscricaoId, UUID acompanhanteId,
                          List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> respostas,
                          UUID igrejaId, UUID pessoaLogadaId, String role) {
        var inscricao = inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        boolean ehDono = inscricao.getPessoa() != null
                && java.util.Objects.equals(inscricao.getPessoa().getId(), pessoaLogadaId);
        if (!ehDono && !com.domus.api.shared.security.Permissoes.podeGerenciarEventos(role)) {
            throw new com.domus.api.shared.exception.BusinessException(
                    "SEM_PERMISSAO", "Você não pode responder por essa inscrição.");
        }

        List<CampoPersonalizadoEvento> campos = campoRepository
                .findByEventoIdAndIgrejaIdOrderByOrdemAsc(inscricao.getEvento().getId(), igrejaId);

        Map<UUID, String> valoresEnviados = new HashMap<>();
        for (var r : respostas) valoresEnviados.put(r.campoId(), r.valor());

        for (CampoPersonalizadoEvento campo : campos) {
            if (!campo.isObrigatorio()) continue;
            String valor = valoresEnviados.get(campo.getId());
            boolean respondidoAgora = valor != null && !valor.isBlank();
            boolean jaRespondidoAntes = !respondidoAgora && respostaRepository
                    .findByCampoIdAndInscricaoIdAndAcompanhanteId(campo.getId(), inscricaoId, acompanhanteId)
                    .map(r -> r.getValor() != null && !r.getValor().isBlank())
                    .orElse(false);
            if (!respondidoAgora && !jaRespondidoAntes) {
                throw new com.domus.api.shared.exception.BusinessException(
                        "CAMPO_OBRIGATORIO_PENDENTE", "\"" + campo.getLabel() + "\" é obrigatório.");
            }
        }

        for (var r : respostas) {
            CampoPersonalizadoEvento campo = campos.stream()
                    .filter(c -> c.getId().equals(r.campoId())).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Campo não encontrado."));

            var existente = respostaRepository
                    .findByCampoIdAndInscricaoIdAndAcompanhanteId(r.campoId(), inscricaoId, acompanhanteId);

            RespostaCampoPersonalizado resposta = existente.orElseGet(() -> {
                var nova = RespostaCampoPersonalizado.builder().campo(campo).inscricao(inscricao).build();
                if (acompanhanteId != null) {
                    var achado = inscricao.getAcompanhantes().stream()
                            .filter(a -> a.getId().equals(acompanhanteId)).findFirst()
                            .orElseThrow(() -> new ResourceNotFoundException("Acompanhante não encontrado."));
                    nova.setAcompanhante(achado);
                }
                return nova;
            });
            resposta.setValor(r.valor());
            respostaRepository.save(resposta);
        }
    }

    public List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaResponse> respostasPorInscricao(
            UUID inscricaoId, UUID acompanhanteId, UUID igrejaId) {
        inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));

        return respostaRepository.findByInscricaoId(inscricaoId).stream()
                .filter(r -> java.util.Objects.equals(
                        r.getAcompanhante() == null ? null : r.getAcompanhante().getId(), acompanhanteId))
                .map(com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaResponse::from)
                .toList();
    }
```

- [ ] **Step 5: Rodar o teste e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test -Dtest=CampoPersonalizadoServiceTest`
Expected: PASS (7 testes)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/RespostaRequest.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/DTOs/RespostaResponse.java \
  src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoService.java \
  src/test/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoServiceTest.java
git commit -m "feat(evento): CampoPersonalizadoService.responder valida obrigatoriedade e dono"
```

---

### Task 6: `CampoPersonalizadoController` — configuração

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoController.java`

**Interfaces:**
- Consumes: `CampoPersonalizadoService` (Task 4/5), `UsuarioAutenticado`
  (`com.domus.api.shared.security`, já existe — `getIgrejaId()`).
- Produces: `GET /eventos/{eventoId}/campos-personalizados`, `PUT /eventos/{eventoId}/campos-personalizados`.

- [ ] **Step 1: Criar o controller**

```java
package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/eventos/{eventoId}/campos-personalizados")
@RequiredArgsConstructor
public class CampoPersonalizadoController {

    private final CampoPersonalizadoService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping
    public ResponseEntity<List<CampoPersonalizadoResponse>> listar(@PathVariable UUID eventoId) {
        return ResponseEntity.ok(service.listar(eventoId, usuarioAutenticado.getIgrejaId()));
    }

    @PutMapping
    public ResponseEntity<List<CampoPersonalizadoResponse>> salvar(
            @PathVariable UUID eventoId,
            @Valid @RequestBody List<CampoPersonalizadoRequest> dados) {
        return ResponseEntity.ok(service.salvar(eventoId, usuarioAutenticado.getIgrejaId(), dados));
    }
}
```

> Sem mudança no `SecurityConfig`: `/eventos/**` já cobre GET com `hasAnyRole(ADMIN, LIDER,
> COMUM)` e PUT com `hasAnyRole(ADMIN, LIDER)` (ver `SecurityConfig.java` linhas ~115-120) —
> exatamente a autorização que este endpoint precisa.

- [ ] **Step 2: Compilar e rodar a suite completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS (compila e nenhum teste existente quebra)

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/campopersonalizado/CampoPersonalizadoController.java
git commit -m "feat(evento): endpoints de configuracao de campos personalizados"
```

---

### Task 7: Endpoints de resposta em `InscricaoController` + `SecurityConfig`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java`
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java`

**Interfaces:**
- Consumes: `CampoPersonalizadoService.responder`/`respostasPorInscricao` (Task 5),
  `UsuarioAutenticado.getPessoaId()`/`getRole()` (já existem).
- Produces: `GET /inscricoes/{inscricaoId}/respostas?acompanhanteId=`, `PUT /inscricoes/{inscricaoId}/respostas?acompanhanteId=`.

- [ ] **Step 1: Injetar `CampoPersonalizadoService` e adicionar os dois endpoints**

Em `InscricaoController.java`, adicionar o campo (Lombok `@RequiredArgsConstructor` já injeta
automaticamente, só declarar):

```java
    private final com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoService campoPersonalizadoService;
```

E os dois métodos novos (perto de onde já existe `DELETE /inscricoes/{id}`, mesmo padrão de
`@PathVariable`/`@RequestParam` do resto do controller):

```java
    @GetMapping("/inscricoes/{inscricaoId}/respostas")
    public ResponseEntity<List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaResponse>> respostas(
            @PathVariable UUID inscricaoId,
            @RequestParam(required = false) UUID acompanhanteId) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(campoPersonalizadoService.respostasPorInscricao(inscricaoId, acompanhanteId, igrejaId));
    }

    @PutMapping("/inscricoes/{inscricaoId}/respostas")
    public ResponseEntity<Void> responder(
            @PathVariable UUID inscricaoId,
            @RequestParam(required = false) UUID acompanhanteId,
            @jakarta.validation.Valid @RequestBody
            List<com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest> dados) {
        campoPersonalizadoService.responder(inscricaoId, acompanhanteId, dados,
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), usuarioAutenticado.getRole());
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 2: Liberar a rota no `SecurityConfig`**

Adicionar logo abaixo da linha existente `.requestMatchers(HttpMethod.DELETE,
"/inscricoes/**", "/acompanhantes/**").hasAnyRole(ADMIN, LIDER, COMUM)`:

```java
                        .requestMatchers(HttpMethod.GET, "/inscricoes/*/respostas")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.PUT, "/inscricoes/*/respostas")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
```

> A autorização por *dono da inscrição vs. terceiro* continua sendo checada dentro de
> `CampoPersonalizadoService.responder()` (Task 5) — Spring Security só garante "está
> logado com um desses papéis", não "é dono deste registro específico".

- [ ] **Step 3: Rodar a suite completa**

Run: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java \
  src/main/java/com/domus/api/config/SecurityConfig.java
git commit -m "feat(evento): endpoints de responder e listar respostas de campo personalizado"
```

---

## Frontend

### Task 8: Types e services

**Files:**
- Create: `frontend/src/types/campoPersonalizado.type.ts`
- Create: `frontend/src/services/campoPersonalizado.service.ts`
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/types/inscricao.type.ts`
- Modify: `frontend/src/services/inscricao.service.ts`

**Interfaces:**
- Produces: `TipoCampoPersonalizado`, `CampoPersonalizadoResponse`, `CampoPersonalizadoRequest`,
  `RespostaRequest`, `RespostaResponse` (types); `camposPersonalizadosService.{listar,salvar}`;
  `inscricoesService.{respostas,responder}`.

- [ ] **Step 1: Criar os tipos**

```typescript
// frontend/src/types/campoPersonalizado.type.ts
export type TipoCampoPersonalizado = 'TEXTO_CURTO' | 'OPCAO_UNICA' | 'MULTIPLA_ESCOLHA' | 'SIM_NAO'

export interface CampoPersonalizadoResponse {
  id: string
  label: string
  placeholder: string | null
  tipo: TipoCampoPersonalizado
  opcoes: string[]
  obrigatorio: boolean
  visivelAoPublico: boolean
  ordem: number
}

export interface CampoPersonalizadoRequest {
  id: string | null
  label: string
  placeholder: string | null
  tipo: TipoCampoPersonalizado
  opcoes: string[] | null
  obrigatorio: boolean
  visivelAoPublico: boolean
  ordem: number
}

export interface RespostaRequest {
  campoId: string
  valor: string
}

export interface RespostaResponse {
  campoId: string
  label: string
  tipo: TipoCampoPersonalizado
  valor: string
}
```

- [ ] **Step 2: Adicionar as rotas em `endpoints.ts`**

Dentro de `eventos: { ... }`, adicionar:

```typescript
    CAMPOS_PERSONALIZADOS: (id: string) => `/eventos/${id}/campos-personalizados`,
```

Dentro de `inscricoes: { ... }`, adicionar:

```typescript
    RESPOSTAS: (inscricaoId: string) => `/inscricoes/${inscricaoId}/respostas`,
```

- [ ] **Step 3: Criar o service de campos personalizados**

```typescript
// frontend/src/services/campoPersonalizado.service.ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { CampoPersonalizadoResponse, CampoPersonalizadoRequest } from '@/types/campoPersonalizado.type'

export const camposPersonalizadosService = {
  listar: (eventoId: string): Promise<CampoPersonalizadoResponse[]> =>
    api.get<CampoPersonalizadoResponse[]>(Endpoints.eventos.CAMPOS_PERSONALIZADOS(eventoId)).then(res => res.data),

  salvar: (eventoId: string, dados: CampoPersonalizadoRequest[]): Promise<CampoPersonalizadoResponse[]> =>
    api.put<CampoPersonalizadoResponse[]>(Endpoints.eventos.CAMPOS_PERSONALIZADOS(eventoId), dados)
      .then(res => res.data),
}
```

- [ ] **Step 4: Adicionar `respostas`/`responder` em `inscricao.service.ts`**

```typescript
  respostas: (inscricaoId: string, acompanhanteId?: string): Promise<RespostaResponse[]> =>
    api.get<RespostaResponse[]>(Endpoints.inscricoes.RESPOSTAS(inscricaoId), { params: { acompanhanteId } })
      .then(res => res.data),

  responder: (inscricaoId: string, dados: RespostaRequest[], acompanhanteId?: string): Promise<void> =>
    api.put(Endpoints.inscricoes.RESPOSTAS(inscricaoId), dados, { params: { acompanhanteId } })
      .then(() => undefined),
```

E adicionar o import no topo do arquivo:

```typescript
import type { RespostaRequest, RespostaResponse } from '@/types/campoPersonalizado.type'
```

- [ ] **Step 5: Checar tipos e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/types/campoPersonalizado.type.ts src/services/campoPersonalizado.service.ts src/lib/endpoints.ts src/types/inscricao.type.ts src/services/inscricao.service.ts`
Expected: Sem erros

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/types/campoPersonalizado.type.ts src/services/campoPersonalizado.service.ts \
  src/lib/endpoints.ts src/types/inscricao.type.ts src/services/inscricao.service.ts
git commit -m "feat(evento): types e services de campos personalizados no frontend"
```

---

### Task 9: Hook `useCamposPersonalizados` + `useSalvarCamposPersonalizados`

**Files:**
- Create: `frontend/src/hooks/evento/useCamposPersonalizados.ts`
- Create: `frontend/src/hooks/evento/useSalvarCamposPersonalizados.ts`

**Interfaces:**
- Consumes: `camposPersonalizadosService` (Task 8).
- Produces: `useCamposPersonalizados(eventoId: string)` (TanStack Query, retorna
  `{ data, isLoading }`); `useSalvarCamposPersonalizados()` (retorna
  `{ salvar: (eventoId, dados) => Promise<void>, isLoading }`).

- [ ] **Step 1: Criar `useCamposPersonalizados`**

```typescript
import { useQuery } from '@tanstack/react-query'
import { camposPersonalizadosService } from '@/services/campoPersonalizado.service'

export function useCamposPersonalizados(eventoId: string) {
  return useQuery({
    queryKey: ['campos-personalizados', eventoId],
    queryFn: () => camposPersonalizadosService.listar(eventoId),
    enabled: !!eventoId,
  })
}
```

- [ ] **Step 2: Criar `useSalvarCamposPersonalizados`**

```typescript
import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { camposPersonalizadosService } from '@/services/campoPersonalizado.service'
import type { CampoPersonalizadoRequest } from '@/types/campoPersonalizado.type'
import type { ApiError } from '@/types/api.types'

export function useSalvarCamposPersonalizados() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)

  async function salvar(eventoId: string, dados: CampoPersonalizadoRequest[]) {
    setIsLoading(true)
    try {
      await camposPersonalizadosService.salvar(eventoId, dados)
      queryClient.invalidateQueries({ queryKey: ['campos-personalizados', eventoId] })
      notificar.sucesso('Campos personalizados salvos.')
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.'
        : 'Erro ao salvar. Tente novamente.'
      notificar.erro(mensagem)
      throw error
    } finally {
      setIsLoading(false)
    }
  }

  return { salvar, isLoading }
}
```

- [ ] **Step 3: Checar tipos e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/hooks/evento/useCamposPersonalizados.ts src/hooks/evento/useSalvarCamposPersonalizados.ts`
Expected: Sem erros

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/hooks/evento/useCamposPersonalizados.ts src/hooks/evento/useSalvarCamposPersonalizados.ts
git commit -m "feat(evento): hooks de listar/salvar campos personalizados"
```

---

### Task 10: `CamposPersonalizadosPainel.tsx` — configuração com prévia

**Files:**
- Create: `frontend/src/components/module/eventos/CamposPersonalizadosPainel.tsx`
- Create: `frontend/src/components/module/eventos/CamposPersonalizadosPainel.module.css`
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx`

**Interfaces:**
- Consumes: `useCamposPersonalizados`, `useSalvarCamposPersonalizados` (Task 9);
  `CampoPersonalizadoResponse`/`Request` (Task 8).
- Produces: `<CamposPersonalizadosPainel eventoId={string} />` — componente autocontido, com
  seu próprio botão de salvar (não depende do submit principal do `EventoForm`, porque campo
  personalizado só existe depois que o evento já existe).

- [ ] **Step 1: Criar o componente**

```tsx
'use client'

import { useEffect, useState } from 'react'
import { Plus, Trash2, GripVertical } from 'lucide-react'
import { Input } from '@/components/common/input/Input'
import { Select } from '@/components/common/select/Select'
import { Button } from '@/components/common/button/Button'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useSalvarCamposPersonalizados } from '@/hooks/evento/useSalvarCamposPersonalizados'
import type { CampoPersonalizadoRequest, TipoCampoPersonalizado } from '@/types/campoPersonalizado.type'
import styles from './CamposPersonalizadosPainel.module.css'

const TIPOS: { value: TipoCampoPersonalizado; label: string }[] = [
  { value: 'TEXTO_CURTO', label: 'Texto curto' },
  { value: 'OPCAO_UNICA', label: 'Opção única' },
  { value: 'MULTIPLA_ESCOLHA', label: 'Múltipla escolha' },
  { value: 'SIM_NAO', label: 'Sim/Não' },
]

function campoVazio(ordem: number): CampoPersonalizadoRequest {
  return { id: null, label: '', placeholder: null, tipo: 'TEXTO_CURTO', opcoes: null, obrigatorio: false, visivelAoPublico: true, ordem }
}

export function CamposPersonalizadosPainel({ eventoId }: { eventoId: string }) {
  const { data, isLoading } = useCamposPersonalizados(eventoId)
  const { salvar, isLoading: salvando } = useSalvarCamposPersonalizados()
  const [campos, setCampos] = useState<CampoPersonalizadoRequest[]>([])

  useEffect(() => {
    if (data) {
      setCampos(data.map((c) => ({
        id: c.id, label: c.label, placeholder: c.placeholder, tipo: c.tipo,
        opcoes: c.opcoes.length ? c.opcoes : null, obrigatorio: c.obrigatorio,
        visivelAoPublico: c.visivelAoPublico, ordem: c.ordem,
      })))
    }
  }, [data])

  function atualizarCampo(indice: number, patch: Partial<CampoPersonalizadoRequest>) {
    setCampos((atual) => atual.map((c, i) => (i === indice ? { ...c, ...patch } : c)))
  }

  function adicionarCampo() {
    setCampos((atual) => [...atual, campoVazio(atual.length)])
  }

  function removerCampo(indice: number) {
    setCampos((atual) => atual.filter((_, i) => i !== indice).map((c, i) => ({ ...c, ordem: i })))
  }

  async function aoSalvar() {
    await salvar(eventoId, campos)
  }

  if (isLoading) return null

  return (
    <div className={styles.wrap}>
      <div className={styles.colunaEditor}>
        {campos.map((campo, indice) => (
          <div key={campo.id ?? `novo-${indice}`} className={styles.cartaoCampo}>
            <div className={styles.cabecalhoCartao}>
              <GripVertical size={16} className={styles.iconeArraste} aria-hidden="true" />
              <button type="button" className={styles.botaoRemover} onClick={() => removerCampo(indice)}>
                <Trash2 size={14} />
              </button>
            </div>

            <Input
              id={`campo-label-${indice}`}
              label="Rótulo do campo"
              value={campo.label}
              onChange={(e) => atualizarCampo(indice, { label: e.target.value })}
            />

            <Input
              id={`campo-placeholder-${indice}`}
              label="Texto de ajuda (placeholder)"
              value={campo.placeholder ?? ''}
              onChange={(e) => atualizarCampo(indice, { placeholder: e.target.value || null })}
            />

            <Select
              id={`campo-tipo-${indice}`}
              label="Tipo"
              options={TIPOS}
              value={campo.tipo}
              onChange={(e) => atualizarCampo(indice, { tipo: e.target.value as TipoCampoPersonalizado })}
            />

            {(campo.tipo === 'OPCAO_UNICA' || campo.tipo === 'MULTIPLA_ESCOLHA') && (
              <div className={styles.campoOpcoes}>
                <span className={styles.labelOpcoes}>Opções (uma por linha)</span>
                <textarea
                  className={styles.textareaOpcoes}
                  value={(campo.opcoes ?? []).join('\n')}
                  onChange={(e) => atualizarCampo(indice, {
                    opcoes: e.target.value.split('\n').map((l) => l.trim()).filter(Boolean),
                  })}
                />
              </div>
            )}

            <label className={styles.toggleLinha}>
              <input
                type="checkbox"
                checked={campo.obrigatorio}
                onChange={(e) => atualizarCampo(indice, { obrigatorio: e.target.checked })}
              />
              Obrigatório
            </label>

            {/* Sem efeito ainda — groundwork pro formulário público (spec futura). */}
            <label className={styles.toggleLinha} title="Ainda sem efeito — chega com o formulário público">
              <input type="checkbox" checked={campo.visivelAoPublico} disabled />
              Visível ao público
            </label>
          </div>
        ))}

        <button type="button" className={styles.botaoAdicionar} onClick={adicionarCampo}>
          <Plus size={16} /> Adicionar campo
        </button>

        <Button type="button" onClick={aoSalvar} disabled={salvando}>
          {salvando ? 'Salvando…' : 'Salvar campos personalizados'}
        </Button>
      </div>

      <div className={styles.colunaPreview}>
        <span className={styles.tituloPreview}>Visualização — como quem responde vai ver</span>
        {campos.map((campo, indice) => (
          <div key={campo.id ?? `preview-${indice}`} className={styles.previewCampo}>
            <label>{campo.label || 'Rótulo do campo'}{campo.obrigatorio && ' *'}</label>
            {campo.tipo === 'TEXTO_CURTO' && <input disabled placeholder={campo.placeholder ?? ''} />}
            {campo.tipo === 'SIM_NAO' && <input type="checkbox" disabled />}
            {(campo.tipo === 'OPCAO_UNICA' || campo.tipo === 'MULTIPLA_ESCOLHA') && (
              <select disabled>
                <option>Selecione</option>
                {(campo.opcoes ?? []).map((o) => <option key={o}>{o}</option>)}
              </select>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Criar o CSS (grid de duas colunas, colapsa em 1 no mobile — regra do projeto)**

```css
.wrap {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 24px;
  align-items: start;
}
.colunaEditor { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
.colunaPreview {
  display: flex; flex-direction: column; gap: 12px; min-width: 0;
  background: var(--color-bg-subtle); border-radius: var(--radius-lg); padding: 16px;
  position: sticky; top: 16px;
}
.tituloPreview { font-size: var(--font-size-sm); font-weight: var(--font-weight-medium); color: var(--color-text-muted); }
.previewCampo { display: flex; flex-direction: column; gap: 4px; }
.previewCampo label { font-size: var(--font-size-sm); }
.previewCampo input, .previewCampo select { padding: 8px; border-radius: var(--radius-md); border: 1px solid var(--color-border-input); }

.cartaoCampo {
  display: flex; flex-direction: column; gap: 10px;
  border: 1px solid var(--color-border); border-radius: var(--radius-lg); padding: 16px;
}
.cabecalhoCartao { display: flex; justify-content: space-between; align-items: center; }
.iconeArraste { color: var(--color-text-muted); }
.botaoRemover { background: none; border: none; color: var(--color-danger); cursor: pointer; padding: 4px; }

.campoOpcoes { display: flex; flex-direction: column; gap: 6px; }
.labelOpcoes { font-size: var(--font-size-sm); color: var(--color-text-muted); }
.textareaOpcoes { min-height: 80px; padding: 8px; border-radius: var(--radius-md); border: 1px solid var(--color-border-input); font-family: inherit; }

.toggleLinha { display: flex; align-items: center; gap: 8px; font-size: var(--font-size-sm); }

.botaoAdicionar {
  display: flex; align-items: center; gap: 6px;
  background: none; border: 1px dashed var(--color-border-input); border-radius: var(--radius-md);
  padding: 10px; color: var(--color-primary); cursor: pointer; justify-content: center;
}

@media (max-width: 768px) {
  .wrap { grid-template-columns: 1fr; }
  .colunaPreview { position: static; }
}
```

- [ ] **Step 3: Encaixar no `EventoForm.tsx`, só em edição**

Dentro da seção "Inscrições" (`EventoForm.tsx`, logo depois do bloco `{tipoInscricao === 'PAGO' && (...)}`, ainda dentro de `{requerInscricao && (...)}`), adicionar:

```tsx
                {ehEdicao && eventoId && (
                  <div className={styles.campoBloco}>
                    <span className={styles.labelData}>CAMPOS PERSONALIZADOS</span>
                    <CamposPersonalizadosPainel eventoId={eventoId} />
                  </div>
                )}
```

`EventoFormProps` precisa de um `eventoId?: string` (a página de edição já tem o id via
`useParams`) — adicionar ao tipo `EventoFormProps` e repassar de
`frontend/src/app/(app)/eventos/[id]/page.tsx` (`<EventoForm {...form} eventoId={id} />`).
Import no topo do `EventoForm.tsx`:

```tsx
import { CamposPersonalizadosPainel } from './CamposPersonalizadosPainel'
```

- [ ] **Step 4: Checar tipos e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/CamposPersonalizadosPainel.tsx src/components/module/eventos/EventoForm.tsx "src/app/(app)/eventos/[id]/page.tsx"`
Expected: Sem erros

- [ ] **Step 5: Testar manualmente no navegador**

Abrir um evento existente em modo edição, rolar até "Inscrições" → "Campos personalizados",
adicionar um campo do tipo "Opção única" com 2 opções, marcar obrigatório, salvar, recarregar
a página e confirmar que o campo persiste.

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/components/module/eventos/CamposPersonalizadosPainel.tsx \
  src/components/module/eventos/CamposPersonalizadosPainel.module.css \
  src/components/module/eventos/EventoForm.tsx \
  "src/app/(app)/eventos/[id]/page.tsx"
git commit -m "feat(evento): painel de configuracao de campos personalizados com preview"
```

---

### Task 11: `RespostasCamposPersonalizados.tsx` — responder

**Files:**
- Create: `frontend/src/hooks/inscricao/useRespostasCampos.ts`
- Create: `frontend/src/hooks/inscricao/useResponderCampos.ts`
- Create: `frontend/src/components/module/eventos/RespostasCamposPersonalizados.tsx`
- Modify: `frontend/src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx`

**Interfaces:**
- Consumes: `inscricoesService.respostas`/`responder` (Task 8), `useCamposPersonalizados`
  (Task 9), `useMinhaInscricao` (já existe).
- Produces: `<RespostasCamposPersonalizados eventoId inscricaoId acompanhanteId? />` — mostra
  os campos pendentes (obrigatório sem resposta) e um formulário pra responder.

- [ ] **Step 1: Criar os hooks**

```typescript
// frontend/src/hooks/inscricao/useRespostasCampos.ts
import { useQuery } from '@tanstack/react-query'
import { inscricoesService } from '@/services/inscricao.service'

export function useRespostasCampos(inscricaoId: string, acompanhanteId?: string) {
  return useQuery({
    queryKey: ['respostas-campos', inscricaoId, acompanhanteId ?? null],
    queryFn: () => inscricoesService.respostas(inscricaoId, acompanhanteId),
    enabled: !!inscricaoId,
  })
}
```

```typescript
// frontend/src/hooks/inscricao/useResponderCampos.ts
import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { RespostaRequest } from '@/types/campoPersonalizado.type'
import type { ApiError } from '@/types/api.types'

export function useResponderCampos() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)
  const [erro, setErro] = useState<string | null>(null)

  async function responder(inscricaoId: string, dados: RespostaRequest[], acompanhanteId?: string) {
    setIsLoading(true)
    setErro(null)
    try {
      await inscricoesService.responder(inscricaoId, dados, acompanhanteId)
      queryClient.invalidateQueries({ queryKey: ['respostas-campos', inscricaoId, acompanhanteId ?? null] })
      notificar.sucesso('Respostas salvas.')
      return true
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.'
        : 'Erro ao salvar. Tente novamente.'
      setErro(mensagem)
      notificar.erro(mensagem)
      return false
    } finally {
      setIsLoading(false)
    }
  }

  return { responder, isLoading, erro }
}
```

- [ ] **Step 2: Criar o componente de resposta**

```tsx
'use client'

import { useEffect, useState } from 'react'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import { Button } from '@/components/common/button/Button'
import { Input } from '@/components/common/input/Input'
import styles from './RespostasCamposPersonalizados.module.css'

export function RespostasCamposPersonalizados({
  eventoId, inscricaoId, acompanhanteId,
}: { eventoId: string; inscricaoId: string; acompanhanteId?: string }) {
  const { data: campos } = useCamposPersonalizados(eventoId)
  const { data: respostas } = useRespostasCampos(inscricaoId, acompanhanteId)
  const { responder, isLoading, erro } = useResponderCampos()
  const [valores, setValores] = useState<Record<string, string>>({})

  useEffect(() => {
    if (respostas) {
      setValores(Object.fromEntries(respostas.map((r) => [r.campoId, r.valor])))
    }
  }, [respostas])

  if (!campos || campos.length === 0) return null

  const pendentes = campos.filter((c) => c.obrigatorio && !(valores[c.id]?.trim()))

  async function aoSalvar() {
    const dados = campos!.map((c) => ({ campoId: c.id, valor: valores[c.id] ?? '' }))
    await responder(inscricaoId, dados, acompanhanteId)
  }

  return (
    <div className={styles.wrap}>
      {pendentes.length > 0 && (
        <p className={styles.aviso}>{pendentes.length} campo(s) obrigatório(s) pendente(s)</p>
      )}
      {campos.map((campo) => (
        <div key={campo.id} className={styles.campo}>
          {campo.tipo === 'SIM_NAO' ? (
            <label className={styles.checkboxLinha}>
              <input
                type="checkbox"
                checked={valores[campo.id] === 'Sim'}
                onChange={(e) => setValores((v) => ({ ...v, [campo.id]: e.target.checked ? 'Sim' : 'Não' }))}
              />
              {campo.label}
            </label>
          ) : campo.tipo === 'OPCAO_UNICA' ? (
            <div>
              <span className={styles.label}>{campo.label}{campo.obrigatorio && ' *'}</span>
              <select
                value={valores[campo.id] ?? ''}
                onChange={(e) => setValores((v) => ({ ...v, [campo.id]: e.target.value }))}
              >
                <option value="">Selecione</option>
                {campo.opcoes.map((o) => <option key={o} value={o}>{o}</option>)}
              </select>
            </div>
          ) : campo.tipo === 'MULTIPLA_ESCOLHA' ? (
            <div>
              <span className={styles.label}>{campo.label}{campo.obrigatorio && ' *'}</span>
              {campo.opcoes.map((o) => {
                const selecionadas = (valores[campo.id] ?? '').split(' | ').filter(Boolean)
                const marcado = selecionadas.includes(o)
                return (
                  <label key={o} className={styles.checkboxLinha}>
                    <input
                      type="checkbox"
                      checked={marcado}
                      onChange={() => {
                        const novas = marcado ? selecionadas.filter((s) => s !== o) : [...selecionadas, o]
                        setValores((v) => ({ ...v, [campo.id]: novas.join(' | ') }))
                      }}
                    />
                    {o}
                  </label>
                )
              })}
            </div>
          ) : (
            <Input
              id={`resposta-${campo.id}`}
              label={campo.label + (campo.obrigatorio ? ' *' : '')}
              placeholder={campo.placeholder ?? undefined}
              value={valores[campo.id] ?? ''}
              onChange={(e) => setValores((v) => ({ ...v, [campo.id]: e.target.value }))}
            />
          )}
        </div>
      ))}
      {erro && <p className={styles.erro}>{erro}</p>}
      <Button type="button" onClick={aoSalvar} disabled={isLoading}>
        {isLoading ? 'Salvando…' : 'Salvar respostas'}
      </Button>
    </div>
  )
}
```

- [ ] **Step 3: Criar o CSS**

```css
.wrap { display: flex; flex-direction: column; gap: 12px; margin-top: 16px; }
.aviso { font-size: var(--font-size-sm); color: var(--color-warning); }
.campo { display: flex; flex-direction: column; gap: 4px; }
.label { font-size: var(--font-size-sm); font-weight: var(--font-weight-medium); }
.checkboxLinha { display: flex; align-items: center; gap: 8px; font-size: var(--font-size-sm); }
.erro { font-size: var(--font-size-sm); color: var(--color-danger); }
```

- [ ] **Step 4: Encaixar no `DrawerDetalheEvento.tsx`**

Perto de onde `minha` (retorno de `useMinhaInscricao`) já é usado, renderizar quando a pessoa
está inscrita:

```tsx
{minha?.inscrito && minha.id && (
  <RespostasCamposPersonalizados eventoId={eventoId} inscricaoId={minha.id} />
)}
```

Import no topo:

```tsx
import { RespostasCamposPersonalizados } from '@/components/module/eventos/RespostasCamposPersonalizados'
```

- [ ] **Step 5: Checar tipos e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/hooks/inscricao/useRespostasCampos.ts src/hooks/inscricao/useResponderCampos.ts src/components/module/eventos/RespostasCamposPersonalizados.tsx src/app/\(app\)/eventos/\(lista\)/\(detalhe\)/DrawerDetalheEvento.tsx`
Expected: Sem erros

- [ ] **Step 6: Testar manualmente no navegador**

Criar um evento com um campo obrigatório (Task 10), se inscrever nele, abrir o drawer de
detalhe e confirmar que o formulário de resposta aparece com o aviso de pendência; preencher e
salvar; recarregar e confirmar que o valor persiste.

- [ ] **Step 7: Commit**

```bash
cd frontend
git add src/hooks/inscricao/useRespostasCampos.ts src/hooks/inscricao/useResponderCampos.ts \
  src/components/module/eventos/RespostasCamposPersonalizados.tsx \
  src/components/module/eventos/RespostasCamposPersonalizados.module.css \
  "src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx"
git commit -m "feat(evento): tela de responder campos personalizados pendentes"
```

---

### Task 12: Respostas visíveis na lista de inscritos

**Files:**
- Modify: `frontend/src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx`

**Interfaces:**
- Consumes: `useRespostasCampos` (Task 11), `InscritoResponse` (já existe).

- [ ] **Step 1: Mostrar as respostas de cada inscrito na lista completa**

Dentro do componente que renderiza cada linha da lista de inscritos (ADMIN/LÍDER), adicionar um
sub-componente que busca e mostra as respostas sob demanda (ex.: ao expandir a linha):

```tsx
function RespostasDoInscrito({ inscricaoId }: { inscricaoId: string }) {
  const { data: respostas } = useRespostasCampos(inscricaoId)
  if (!respostas || respostas.length === 0) return null
  return (
    <ul className={styles.listaRespostas}>
      {respostas.map((r) => (
        <li key={r.campoId}><strong>{r.label}:</strong> {r.valor || '—'}</li>
      ))}
    </ul>
  )
}
```

Import necessário: `import { useRespostasCampos } from '@/hooks/inscricao/useRespostasCampos'`.
Renderizar `<RespostasDoInscrito inscricaoId={inscrito.id} />` dentro da linha expandida de
cada inscrito, junto de onde os acompanhantes já são listados.

Adicionar ao CSS do drawer (mesmo arquivo `.module.css` já usado pelo componente):

```css
.listaRespostas { margin-top: 8px; padding-left: 16px; font-size: var(--font-size-sm); color: var(--color-text-muted); }
```

- [ ] **Step 2: Checar tipos e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint "src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx"`
Expected: Sem erros

- [ ] **Step 3: Testar manualmente no navegador**

Como ADMIN, abrir a lista completa de inscritos de um evento com campos personalizados
respondidos e confirmar que as respostas aparecem por pessoa.

- [ ] **Step 4: Commit**

```bash
cd frontend
git add "src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx"
git commit -m "feat(evento): respostas de campo personalizado na lista de inscritos"
```

---

### Task 13: Fechar o backlog

**Files:**
- Modify: `docs/BACKLOG-PRE-VENDA.md`

- [ ] **Step 1: Marcar o item 7 como resolvido (só a Spec 1)**

No cabeçalho do item 7, trocar `## 7. Campos personalizados + formulário público (Spec D, com
escopo maior)` por:

```markdown
## 7. Campos personalizados de evento (Spec 1) ~~RESOLVIDO~~ (2026-08-21) — formulário público (Spec 2) ainda pendente
```

E adicionar um parágrafo de fechamento logo após o parágrafo de motivação original,
antes da lista de capacidades:

```markdown
**Spec 1 feita:** `campo_personalizado_evento` (definição, com `visivel_ao_publico` já como
groundwork) + `resposta_campo_personalizado` (snapshot em texto, índices únicos parciais pra
titular/acompanhante). Configuração dentro do formulário de evento (edição), com prévia ao
vivo. Resposta pendente após inscrição em lote, preenchível pela própria pessoa ou por quem
gerencia — nunca bloqueia `inscrever()`. Ver spec/plano em `docs/superpowers/`.

**Spec 2 (pendente):** formulário público sem login, campos fixos de identidade, e a lógica de
reaproveitar cadastro existente (mesmo padrão do fluxo de visitante de Célula) — brainstorm
próprio quando chegar a vez.
```

- [ ] **Step 2: Commit**

```bash
git add docs/BACKLOG-PRE-VENDA.md
git commit -m "docs(backlog): fecha Spec 1 do item 7 (campos personalizados de evento)"
```

---

## Verificação final

- [ ] Rodar a suite completa do backend uma última vez: `set -a; source .env >/dev/null 2>&1; set +a; ./mvnw -q -o test`
- [ ] Rodar `tsc --noEmit` e `eslint` no frontend inteiro (ou pelo menos nos arquivos tocados)
- [ ] Testar ao vivo no navegador, pelo menos uma vez cada: criar campo obrigatório num
      evento existente, inscrever alguém em lote (confirmar que não trava), responder como a
      própria pessoa, responder como gestor por outra pessoa, editar um campo já respondido
      (trocar tipo, apagar opção) e confirmar que a resposta antiga continua aparecendo sem
      erro, ver as respostas na lista de inscritos.
