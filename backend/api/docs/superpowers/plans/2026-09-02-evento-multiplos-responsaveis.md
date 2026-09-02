# Evento com múltiplos responsáveis — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Um evento pode ter zero, um ou vários responsáveis (hoje é no máximo um).

**Architecture:** Tabela de junção `evento_responsavel` (`pessoa_id` XOR `nome_texto`, no padrão de `movimentacao_contribuinte` V15). `Evento` ganha `@OneToMany`. O contrato `EventoResponse.responsavel` (singular) vira `responsaveis` (lista). Migração V37 move o responsável único de cada evento pra tabela nova e dropa as 2 colunas de `evento`.

**Tech Stack:** Java 21, Spring Boot, Flyway, PostgreSQL, JPA/Hibernate, JUnit 5 + Mockito + AssertJ, Testcontainers. Next.js 16 (App Router), TypeScript, React Hook Form, Zod, TanStack Query, CSS Modules.

**Spec:** `backend/api/docs/superpowers/specs/2026-09-02-evento-multiplos-responsaveis-design.md`

## Global Constraints

- `igreja_id` sempre do JWT, nunca do corpo da requisição. Toda entidade de domínio carrega `igreja_id`.
- Services retornam DTOs, nunca entidades.
- Teste de regra de negócio = Mockito puro, sem contexto Spring (regra padrão). `@DataJpaTest`/`@SpringBootTest` implementam `PostgresTestContainerSupport` e começam com **banco vazio** (só schema) — criar o fixture que o teste precisa.
- Nomenclatura de teste: classe `{Alvo}Test`, método `snake_case` em português.
- AssertJ primário (`assertThat`, `assertThatThrownBy`).
- **Não commitar antes de o autor testar** o pedaço. Um commit coerente por pedaço.
- Migration nova = **V37** (última é V36).
- Padrão do texto-fallback LGPD ("Pessoa removida do sistema"): `pessoa_id` nulável XOR `nome_texto`, como `movimentacao_contribuinte` (V15) e o antigo `evento.responsavel_texto` (V4).
- Front: animação/suavidade é parte da entrega — chips entram com `<Transicao modo="escala">`, respeitar `prefers-reduced-motion` (já herdado dos componentes).
- Notificação de "novo responsável": tipo `TipoNotificacao.RESPONSAVEL_EVENTO`, texto **`"Você foi definido como responsável pelo evento \"<titulo>\"."`**, rota `"/eventos?detalhe=" + evento.getId()`. Nunca notifica o ator (`usuarioIdAtor`).

## Ponto de partida

`main` limpo. Última migration V36. `Evento` tem `@ManyToOne Pessoa responsavel` +
`String responsavelTexto`. `EventoRequest.responsavelPessoaId` é o 9º componente do record
(`... String tipo, UUID responsavelPessoaId, String recorteEtario, ...`), record com 23
componentes no total (termina em `... EnderecoDTO enderecoLocal, LocalEventoRequest novoLocal`).

---

## File Structure

**Backend:**
- `src/main/resources/db/migration/V37__evento_multiplos_responsaveis.sql` — **novo**.
- `src/main/java/com/domus/api/modules/evento/EventoResponsavel.java` — **novo** (entidade).
- `src/main/java/com/domus/api/modules/evento/EventoResponsavelRepository.java` — **novo**.
- `src/main/java/com/domus/api/modules/evento/Evento.java` — troca `responsavel`+`responsavelTexto` por `@OneToMany List<EventoResponsavel> responsaveis`.
- `src/main/java/com/domus/api/modules/evento/EventoRepository.java` — remove `desvincularResponsavel`.
- `src/main/java/com/domus/api/modules/pessoa/PessoaService.java` — 2 chamadas passam a usar `eventoResponsavelRepository.desvincularPessoa`.
- `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java` — `responsavelPessoaId` → `List<UUID> responsavelPessoaIds`.
- `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java` — `PessoaResumo responsavel` → `List<PessoaResumo> responsaveis`.
- `src/main/java/com/domus/api/modules/evento/EventoService.java` — `resolverResponsaveis` / `sincronizarResponsaveis` / `notificarResponsaveis`; criar/atualizar/séries.

**Backend testes:**
- `src/test/java/com/domus/api/modules/evento/EventoMultiplosResponsaveisMigracaoTest.java` — **novo** (`@DataJpaTest`).
- `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` — cenários de responsáveis.
- Ajustes de compilação em `EventoServiceTest`, `EventoServiceCamposInscricaoTest`, `EventoTipoENormalizacaoTest`, `ImpactoRestricaoTest`, `EventoRequestTest`, `EventoResponseLocalInfoTest` (se algum construir `EventoRequest` posicional ou ler `EventoResponse.responsavel()`).

**Frontend:**
- `src/types/evento.type.ts` — `responsavel` → `responsaveis`, `responsavelPessoaId` → `responsavelPessoaIds`.
- `src/lib/validators.ts` — `responsavelPessoaIds: z.array(z.string()).default([])`.
- `src/hooks/evento/useEventoForm.ts` — default / reidrata / payload / `responsaveisIniciais`.
- `src/components/module/eventos/SeletorResponsavel.tsx` + `.module.css` — múltiplo (chips).
- `src/components/module/eventos/EventoForm.tsx` — props do seletor.
- `src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx` — lista de responsáveis.

---

## Task 1: Schema + entidade + repositório + LGPD

**Files:**
- Create: `src/main/resources/db/migration/V37__evento_multiplos_responsaveis.sql`
- Create: `src/main/java/com/domus/api/modules/evento/EventoResponsavel.java`
- Create: `src/main/java/com/domus/api/modules/evento/EventoResponsavelRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/pessoa/PessoaService.java:298,361`
- Test: `src/test/java/com/domus/api/modules/evento/EventoMultiplosResponsaveisMigracaoTest.java`

**Interfaces:**
- Produces:
  - Tabela `evento_responsavel(id, igreja_id, evento_id, pessoa_id, nome_texto)`.
  - `EventoResponsavel` entidade: `getId/getIgreja/getEvento/getPessoa/getNomeTexto` + setters + `@Builder`.
  - `Evento.getResponsaveis()` → `List<EventoResponsavel>` (nunca null; inicializado).
  - `int EventoResponsavelRepository.desvincularPessoa(UUID pessoaId, String nome)`.

- [ ] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V37__evento_multiplos_responsaveis.sql`:

```sql
CREATE TABLE evento_responsavel (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id   UUID NOT NULL REFERENCES igreja(id),
    evento_id   UUID NOT NULL REFERENCES evento(id) ON DELETE CASCADE,
    pessoa_id   UUID REFERENCES pessoa(id),
    nome_texto  VARCHAR(255),
    CONSTRAINT chk_evento_responsavel_pessoa_ou_texto
        CHECK (pessoa_id IS NOT NULL OR nome_texto IS NOT NULL)
);

CREATE UNIQUE INDEX uq_evento_responsavel_evento_pessoa
    ON evento_responsavel (evento_id, pessoa_id)
    WHERE pessoa_id IS NOT NULL;

CREATE INDEX idx_evento_responsavel_evento ON evento_responsavel (evento_id);
CREATE INDEX idx_evento_responsavel_pessoa ON evento_responsavel (pessoa_id);

-- Migra o responsável único (pessoa OU texto) de cada evento.
INSERT INTO evento_responsavel (igreja_id, evento_id, pessoa_id, nome_texto)
SELECT igreja_id, id, responsavel_pessoa_id, responsavel_texto
FROM evento
WHERE responsavel_pessoa_id IS NOT NULL OR responsavel_texto IS NOT NULL;

ALTER TABLE evento
    DROP COLUMN responsavel_pessoa_id,
    DROP COLUMN responsavel_texto;
```

- [ ] **Step 2: Escrever o teste da migration**

`src/test/java/com/domus/api/modules/evento/EventoMultiplosResponsaveisMigracaoTest.java`:

```java
package com.domus.api.modules.evento;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoMultiplosResponsaveisMigracaoTest implements PostgresTestContainerSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    void colunasAntigasDeResponsavelSumiramDaTabelaEvento() {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'evento'
              AND column_name IN ('responsavel_pessoa_id', 'responsavel_texto')
            """, Integer.class);
        assertThat(n).isZero();
    }

    @Test
    void tabelaEventoResponsavelExisteComOCheck() {
        jdbc.update("INSERT INTO igreja (id, nome, email) VALUES "
                + "('b1111111-1111-1111-1111-111111111111', 'Igreja Resp', 'r@r.com')");
        jdbc.update("""
            INSERT INTO evento (id, igreja_id, titulo, inicio_em)
            VALUES ('b2222222-2222-2222-2222-222222222222', 'b1111111-1111-1111-1111-111111111111'::uuid, 'Ev', now())
            """);

        // pessoa_id e nome_texto ambos nulos viola o CHECK.
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO evento_responsavel (igreja_id, evento_id)
            VALUES ('b1111111-1111-1111-1111-111111111111'::uuid, 'b2222222-2222-2222-2222-222222222222'::uuid)
            """))
            .hasMessageContaining("chk_evento_responsavel_pessoa_ou_texto");

        // só nome_texto = ok.
        int ins = jdbc.update("""
            INSERT INTO evento_responsavel (igreja_id, evento_id, nome_texto)
            VALUES ('b1111111-1111-1111-1111-111111111111'::uuid, 'b2222222-2222-2222-2222-222222222222'::uuid, 'Pessoa removida do sistema')
            """);
        assertThat(ins).isEqualTo(1);
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=EventoMultiplosResponsaveisMigracaoTest` (precisa de Docker)
Expected: FAIL — a migration ainda não existe / colunas ainda estão lá.

> Como a migration está escrita no Step 1, este teste na verdade já pode passar (Flyway roda
> tudo). Se passar direto, tudo bem — o valor do teste é travar a estrutura pra sempre.

- [ ] **Step 4: Criar `EventoResponsavel`**

```java
package com.domus.api.modules.evento;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "evento_responsavel")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventoResponsavel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    /** XOR com {@link #nomeTexto}: pessoa cadastrada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    /** XOR com {@link #pessoa}: nome preservado quando a pessoa foi excluída/arquivada (LGPD). */
    @Column(name = "nome_texto")
    private String nomeTexto;
}
```

- [ ] **Step 5: Alterar `Evento`**

Trocar (linhas ~62-67):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_pessoa_id")
    private Pessoa responsavel;

    @Column(name = "responsavel_texto")
    private String responsavelTexto;
```

por:

```java
    @OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<EventoResponsavel> responsaveis = new java.util.ArrayList<>();
```

> Depois disso o projeto NÃO compila até a Task 2 (o `EventoService` e o `EventoResponse`
> ainda usam `getResponsavel()`). Isso é esperado — Tasks 1 e 2 formam um pedaço só de
> back; se preferir, junte-as. O plano as separa só pra revisão.

- [ ] **Step 6: Criar `EventoResponsavelRepository`**

```java
package com.domus.api.modules.evento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface EventoResponsavelRepository extends JpaRepository<EventoResponsavel, UUID> {

    /** Rede de segurança pra arquivar/excluir uma pessoa: soft delete não dispara FK e o
     *  proxy LAZY estoura. Converte o vínculo dela em texto ("Pessoa removida do sistema"). */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE evento_responsavel
           SET pessoa_id = NULL, nome_texto = :nome
         WHERE pessoa_id = :pessoaId
        """, nativeQuery = true)
    int desvincularPessoa(@Param("pessoaId") UUID pessoaId, @Param("nome") String nome);
}
```

- [ ] **Step 7: Remover `EventoRepository.desvincularResponsavel`**

Apagar o método (linhas ~50-57 do `EventoRepository.java`, o bloco com o `@Query` nativo
que faz `UPDATE evento SET responsavel_texto = :nome, responsavel_pessoa_id = NULL`).

- [ ] **Step 8: Trocar as chamadas em `PessoaService`**

Injetar `EventoResponsavelRepository eventoResponsavelRepository` no `PessoaService`
(campo `private final`, `@RequiredArgsConstructor` já cuida do construtor).

Linha ~298 (`arquivarMembro`): trocar
`eventoRepository.desvincularResponsavel(membro.getId(), membro.getNome());`
por
`eventoResponsavelRepository.desvincularPessoa(membro.getId(), membro.getNome());`

Linha ~361 (`excluirDefinitivo`): trocar
`eventoRepository.desvincularResponsavel(id, nome);`
por
`eventoResponsavelRepository.desvincularPessoa(id, nome);`

- [ ] **Step 9: Rodar migration test + compilar**

Run: `mvn -q -o test -Dtest=EventoMultiplosResponsaveisMigracaoTest`
Expected: PASS (2 testes). O `mvn -q -o test-compile` ainda falha em `EventoService`/
`EventoResponse` — resolvido na Task 2.

- [ ] **Step 10: Commit** (junto com a Task 2, ou aqui se preferir pedaço menor de schema)

```bash
git add src/main/resources/db/migration/V37__evento_multiplos_responsaveis.sql \
        src/main/java/com/domus/api/modules/evento/EventoResponsavel.java \
        src/main/java/com/domus/api/modules/evento/EventoResponsavelRepository.java \
        src/main/java/com/domus/api/modules/evento/Evento.java \
        src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/main/java/com/domus/api/modules/pessoa/PessoaService.java \
        src/test/java/com/domus/api/modules/evento/EventoMultiplosResponsaveisMigracaoTest.java
git commit -m "feat(evento): tabela evento_responsavel e migração do responsável único (V37)"
```

---

## Task 2: DTO + serviço + response

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` (+ ajustes de compilação nos outros)

**Interfaces:**
- Consumes: `EventoResponsavel` builder, `Evento.getResponsaveis()` (Task 1).
- Produces:
  - `EventoRequest.responsavelPessoaIds()` → `java.util.List<UUID>` (nulável).
  - `EventoResponse.responsaveis()` → `java.util.List<PessoaResumo>`.
  - `EventoService`:
    - `private List<EventoResponsavel> resolverResponsaveis(List<UUID> ids, UUID igrejaId, Igreja igreja, Evento evento)`
    - `private List<EventoResponsavel> sincronizarResponsaveis(Evento evento, List<UUID> ids, UUID igrejaId, Igreja igreja)` — devolve os **adicionados agora**.
    - `private void notificarResponsaveis(Evento evento, List<EventoResponsavel> adicionados, UUID usuarioIdAtor)`

- [ ] **Step 1: `EventoRequest`**

Trocar `UUID responsavelPessoaId,` (com o Javadoc acima) por:

```java
        /** Pessoas responsáveis pelo evento; null ou lista vazia = sem responsável. */
        java.util.List<UUID> responsavelPessoaIds,
```

- [ ] **Step 2: `EventoResponse`**

Trocar o componente `PessoaResumo responsavel,` por `java.util.List<PessoaResumo> responsaveis,`.

Em `from(...)` (linha ~98), trocar
`PessoaResumo.dePessoa(e.getResponsavel(), e.getResponsavelTexto()),`
por uma variável montada antes do `new EventoResponse(...)`:

```java
        java.util.List<PessoaResumo> responsaveis = e.getResponsaveis().stream()
                .map(r -> PessoaResumo.dePessoa(r.getPessoa(), r.getNomeTexto()))
                .filter(java.util.Objects::nonNull)
                .toList();
```

e passar `responsaveis` na posição.

- [ ] **Step 3: Escrever os testes de serviço**

No `EventoServiceTest` (Mockito puro). O setup já mocka `pessoaRepository`. Adicionar
helper e testes:

```java
    private EventoRequest requestComResponsaveis(java.util.List<UUID> ids) {
        return new EventoRequest(
                "Culto Dominical", "Descrição do evento", LocalDateTime.now().plusDays(1),
                null, null, null, "Culto", null, ids, null, null, null, null,
                null, null, false, false, false, false, null, null, null, null);
    }

    private com.domus.api.modules.pessoa.Pessoa pessoaMock(UUID id, String nome) {
        var p = new com.domus.api.modules.pessoa.Pessoa();
        p.setId(id);
        p.setNome(nome);
        when(pessoaRepository.findByIdAndIgrejaId(id, igrejaId)).thenReturn(java.util.Optional.of(p));
        return p;
    }

    @Test
    void cadastrarEventoComDoisResponsaveis_gravaOsDois() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        pessoaMock(a, "Ana");
        pessoaMock(b, "Bruno");

        service.cadastrarEvento(requestComResponsaveis(java.util.List.of(a, b)), igrejaId, usuarioId);

        verify(eventoRepository).save(argThat(e ->
                e.getResponsaveis().size() == 2
                && e.getResponsaveis().stream().allMatch(r -> r.getPessoa() != null && r.getNomeTexto() == null)));
    }

    @Test
    void cadastrarEvento_idDeResponsavelRepetido_gravaUmSo() {
        UUID a = UUID.randomUUID();
        pessoaMock(a, "Ana");

        service.cadastrarEvento(requestComResponsaveis(java.util.List.of(a, a)), igrejaId, usuarioId);

        verify(eventoRepository).save(argThat(e -> e.getResponsaveis().size() == 1));
    }

    @Test
    void cadastrarEvento_responsavelDeOutraIgreja_recusa() {
        UUID a = UUID.randomUUID();
        when(pessoaRepository.findByIdAndIgrejaId(a, igrejaId)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.cadastrarEvento(requestComResponsaveis(java.util.List.of(a)), igrejaId, usuarioId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
```

> Para o cenário de `atualizarEvento` (adiciona um, remove outro), montar um `Evento`
> existente com `responsaveis` pré-preenchidos e `when(eventoRepository.findByIdAndIgrejaId(...))`.
> Seguir o estilo dos testes de `atualizarEvento` já existentes no arquivo. Verificar que
> `notificacaoService.criar` foi chamado **só** pro id novo (com `ArgumentCaptor` ou
> `verify(..., times(1))` + `never()` pro antigo).

- [ ] **Step 4: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: erro de compilação (`responsavelPessoaIds`, `resolverResponsaveis` não existem;
`new EventoRequest(...)` com aridade errada nos outros helpers).

- [ ] **Step 5: `resolverResponsaveis` + `sincronizarResponsaveis` + `notificarResponsaveis`**

Substituir `resolverResponsavel` (linha ~728) por:

```java
    /** Monta as linhas de EventoResponsavel a partir dos ids do request (dedup). Cada id
     *  precisa ser de uma pessoa da própria igreja. Não persiste — quem chama põe no
     *  {@code evento.getResponsaveis()} e o cascade grava. */
    private java.util.List<EventoResponsavel> resolverResponsaveis(
            java.util.List<UUID> ids, UUID igrejaId, Igreja igreja, Evento evento) {
        if (ids == null || ids.isEmpty()) return java.util.List.of();
        return ids.stream().distinct()
                .map(id -> {
                    Pessoa p = pessoaRepository.findByIdAndIgrejaId(id, igrejaId)
                            .orElseThrow(() -> new ResourceNotFoundException("Pessoa responsável não encontrada."));
                    return EventoResponsavel.builder().igreja(igreja).evento(evento).pessoa(p).build();
                })
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
    }

    /** Aplica a lista nova de responsáveis a um evento existente. Devolve as linhas
     *  ADICIONADAS agora (pra notificar só elas). Não toca nas linhas de texto-fallback
     *  ({@code pessoa == null}) — o form nunca as manda nem as remove. */
    private java.util.List<EventoResponsavel> sincronizarResponsaveis(
            Evento evento, java.util.List<UUID> idsRequest, UUID igrejaId, Igreja igreja) {
        java.util.Set<UUID> idsNovos = idsRequest == null ? java.util.Set.of()
                : new java.util.LinkedHashSet<>(idsRequest);

        // Remove quem saiu (só linhas com pessoa).
        evento.getResponsaveis().removeIf(r ->
                r.getPessoa() != null && !idsNovos.contains(r.getPessoa().getId()));

        java.util.Set<UUID> idsAtuais = evento.getResponsaveis().stream()
                .map(EventoResponsavel::getPessoa).filter(java.util.Objects::nonNull)
                .map(Pessoa::getId).collect(java.util.stream.Collectors.toSet());

        java.util.List<EventoResponsavel> adicionados = new java.util.ArrayList<>();
        for (UUID id : idsNovos) {
            if (idsAtuais.contains(id)) continue;
            Pessoa p = pessoaRepository.findByIdAndIgrejaId(id, igrejaId)
                    .orElseThrow(() -> new ResourceNotFoundException("Pessoa responsável não encontrada."));
            EventoResponsavel novo = EventoResponsavel.builder()
                    .igreja(igreja).evento(evento).pessoa(p).build();
            evento.getResponsaveis().add(novo);
            adicionados.add(novo);
        }
        return adicionados;
    }

    /** Notifica cada responsável ADICIONADO agora (menos o ator). Texto igual ao antigo. */
    private void notificarResponsaveis(Evento evento, java.util.List<EventoResponsavel> adicionados, UUID usuarioIdAtor) {
        for (EventoResponsavel r : adicionados) {
            if (r.getPessoa() == null) continue;
            usuarioRepository.findByPessoaId(r.getPessoa().getId())
                    .filter(usuario -> !usuario.getId().equals(usuarioIdAtor))
                    .ifPresent(usuario -> notificacaoService.criar(
                            com.domus.api.modules.notificacao.TipoNotificacao.RESPONSAVEL_EVENTO,
                            evento.getIgreja().getId(), usuario.getId(),
                            "Você foi definido como responsável pelo evento \"" + evento.getTitulo() + "\".",
                            "/eventos?detalhe=" + evento.getId()));
        }
    }
```

Remover o método `notificarNovoResponsavel` (linha ~475).

- [ ] **Step 6: `cadastrarEvento`**

Trocar (linha ~109) `Pessoa responsavel = resolverResponsavel(data.responsavelPessoaId(), igrejaId);`
— **apagar essa linha** (o `igreja` é resolvido logo abaixo).

No builder do `Evento` (linha ~128), **remover** `.responsavel(responsavel)`.

Depois do `Evento evento = Evento.builder()...build();` e antes de `eventoRepository.save(evento)`:

```java
        java.util.List<EventoResponsavel> respAdicionados =
                resolverResponsaveis(data.responsavelPessoaIds(), igrejaId, igreja, evento);
        evento.getResponsaveis().addAll(respAdicionados);
```

Depois do `save` — trocar a chamada `notificarNovoResponsavel(salvo, igrejaId, usuarioId)`
(linha ~158) por `notificarResponsaveis(salvo, respAdicionados, usuarioId)`.

> Atenção: `respAdicionados` aponta pro `evento` pré-save; após `save` os ids existem.
> `notificarResponsaveis` só usa `r.getPessoa().getId()` e `evento.getId()`/`getTitulo()`,
> que já estão OK. Usar `salvo` no lugar de `evento` na chamada.

- [ ] **Step 7: `atualizarEvento`**

Trocar (linha ~182) `Pessoa responsavel = resolverResponsavel(data.responsavelPessoaId(), igrejaId);`
— **apagar**.

Precisa do `Igreja igreja` no escopo de `atualizarEvento`. Se não existir, buscar:
`Igreja igreja = evento.getIgreja();` (o evento já foi carregado com a igreja — LAZY, mas
`getId()` no proxy é seguro; para montar `EventoResponsavel.igreja` o proxy serve).

Trocar (linha ~203) `UUID responsavelIdAntigo = evento.getResponsavel() != null ? ... : null;`
— **apagar**.

Trocar (linha ~213) `evento.setResponsavel(responsavel);` por:

```java
        java.util.List<EventoResponsavel> respAdicionados =
                sincronizarResponsaveis(evento, data.responsavelPessoaIds(), igrejaId, evento.getIgreja());
```

Trocar o bloco de notificação (linhas ~285-288):

```java
        UUID responsavelIdNovo = responsavel != null ? responsavel.getId() : null;
        if (!java.util.Objects.equals(responsavelIdAntigo, responsavelIdNovo)) {
            notificarNovoResponsavel(salvo, igrejaId, usuarioId);
        }
```

por:

```java
        notificarResponsaveis(salvo, respAdicionados, usuarioId);
```

- [ ] **Step 8: Séries — propagar a lista pras ocorrências**

No 1º bloco de propagação (linha ~416), trocar `ocorrencia.setResponsavel(editado.getResponsavel());` por:

```java
            ocorrencia.getResponsaveis().clear();
            for (EventoResponsavel r : editado.getResponsaveis()) {
                ocorrencia.getResponsaveis().add(EventoResponsavel.builder()
                        .igreja(r.getIgreja()).evento(ocorrencia)
                        .pessoa(r.getPessoa()).nomeTexto(r.getNomeTexto()).build());
            }
```

> `orphanRemoval = true` + `clear()` apaga as linhas antigas da ocorrência; o cascade grava
> as novas no `eventoRepository.save(ocorrencia)` que já existe no loop. O 2º bloco de
> propagação (linha ~455-468) NÃO copia responsável hoje — deixar como está.

- [ ] **Step 9: Ajustar compilação dos outros testes**

`grep -rn "new EventoRequest\|\.responsavel()" src/test/java/com/domus/api/modules/evento/`
e em cada `new EventoRequest(...)` posicional, o 9º argumento passa de `UUID`/`null` para
`java.util.List<UUID>`/`null`. O único que passa valor real é `requestComResponsavel(UUID)`
no `EventoServiceTest` — trocar por `java.util.List.of(responsavelPessoaId)` (ou manter o
helper devolvendo `requestComResponsaveis(List.of(id))`). Testes que leem
`response.responsavel()` passam a ler `response.responsaveis()` (lista).

- [ ] **Step 10: Rodar e ver passar**

Run: `mvn -q -o test -Dtest='EventoServiceTest,EventoTipoENormalizacaoTest,EventoServiceCamposInscricaoTest,ImpactoRestricaoTest,EventoRequestTest,EventoResponseLocalInfoTest'`
Expected: PASS (todos).

- [ ] **Step 11: Suíte inteira**

Run: `cd backend/api && set -a; . ./.env; set +a; mvn -o test`
Expected: BUILD SUCCESS (2 skipped conhecidos). Se `PessoaServiceTest` / testes de
arquivamento/exclusão de pessoa afirmavam algo sobre `desvincularResponsavel`, ajustar
para o novo repositório.

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/ src/test/java/com/domus/api/modules/evento/
git commit -m "feat(evento): múltiplos responsáveis — request/response em lista, sincronização e notificação"
```

---

## Task 3: Front — tipos + validators + `useEventoForm`

**Files:**
- Modify: `src/types/evento.type.ts`
- Modify: `src/lib/validators.ts`
- Modify: `src/hooks/evento/useEventoForm.ts`

**Interfaces:**
- Consumes: nada novo.
- Produces:
  - `EventoResponse.responsaveis: EventoPessoaResumo[]`, `EventoRequest.responsavelPessoaIds?: string[]`.
  - `useEventoForm` expõe `responsaveisIniciais: { id: string; nome: string }[]`.

- [ ] **Step 1: `evento.type.ts`**

- `responsavel: EventoPessoaResumo | null` → `responsaveis: EventoPessoaResumo[]`.
- `responsavelPessoaId?: string | null` → `responsavelPessoaIds?: string[]`.

- [ ] **Step 2: `validators.ts`**

Trocar `responsavelPessoaId: opcional(z.string()),` por:

```typescript
  responsavelPessoaIds: z.array(z.string()).default([]),
```

- [ ] **Step 3: `useEventoForm.ts`**

- default (linha ~76): `responsavelPessoaId: undefined` → `responsavelPessoaIds: []`.
- reidrata (linha ~136): `responsavelPessoaId: eventoInicial.responsavel?.id ?? undefined` →
  ```typescript
  responsavelPessoaIds: (eventoInicial.responsaveis ?? []).filter((r) => r.id).map((r) => r.id as string),
  ```
- payload (linha ~235): `responsavelPessoaId: data.responsavelPessoaId || null` →
  `responsavelPessoaIds: data.responsavelPessoaIds ?? []`.
- exposição (linha ~391-395): `const responsavelNomeInicial = eventoInicial?.responsavel?.nome` →
  ```typescript
  const responsaveisIniciais = (eventoInicial?.responsaveis ?? [])
    .filter((r): r is { id: string; nome: string } => !!r.id)
  ```
  e no retorno, trocar `responsavelNomeInicial` por `responsaveisIniciais`.

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: erros só em `EventoForm.tsx` / `DrawerDetalheEvento.tsx` (Task 4). Se aparecer
erro em outro arquivo que lê `evento.responsavel`, anotar pra Task 4.

- [ ] **Step 5: Commit** (segurar até a Task 4 — o front não funciona no meio)

---

## Task 4: Front — `SeletorResponsavel` múltiplo + `EventoForm` + drawer

**Files:**
- Modify: `src/components/module/eventos/SeletorResponsavel.tsx`
- Modify: `src/components/module/eventos/SeletorResponsavel.module.css`
- Modify: `src/components/module/eventos/EventoForm.tsx`
- Modify: `src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx`

**Interfaces:**
- Consumes: `responsaveisIniciais` (Task 3), `EventoResponse.responsaveis` (Task 3).
- Produces: `SeletorResponsavel` props `valores: {id,nome}[]` + `onChange: (lista: {id,nome}[]) => void`.

- [ ] **Step 1: Reescrever `SeletorResponsavel.tsx`**

```tsx
'use client'

import { useState } from 'react'
import { Search, X, Check } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useDebounce } from '@/hooks/useDebounce'
import { Transicao } from '@/components/common/Transicao/Transicao'
import styles from './SeletorResponsavel.module.css'

interface Pessoa { id: string; nome: string }

interface SeletorResponsavelProps {
  valores: Pessoa[]
  onChange: (lista: Pessoa[]) => void
}

export function SeletorResponsavel({ valores, onChange }: SeletorResponsavelProps) {
  const [busca, setBusca] = useState('')
  const buscaDebounced = useDebounce(busca, 300)
  const habilitado = buscaDebounced.trim().length >= 2
  const { data, isLoading } = usePessoas({ q: habilitado ? buscaDebounced : '', page: 0, size: 8 })

  const jaEscolhidos = new Set(valores.map((v) => v.id))
  const resultados = habilitado ? (data?.content ?? []).filter((p) => !jaEscolhidos.has(p.id)) : []

  function adicionar(p: Pessoa) {
    onChange([...valores, { id: p.id, nome: p.nome }])
    setBusca('')
  }
  function remover(id: string) {
    onChange(valores.filter((v) => v.id !== id))
  }

  return (
    <div className={styles.campo}>
      <span className={styles.label}>
        RESPONSÁVEIS <span className={styles.opcional}>(opcional)</span>
      </span>

      {valores.length > 0 && (
        <div className={styles.chips}>
          {valores.map((v) => (
            <Transicao key={v.id} modo="escala" className={styles.chip}>
              <span className={styles.chipNome}>{v.nome}</span>
              <button type="button" className={styles.chipRemover} onClick={() => remover(v.id)}
                aria-label={`Remover ${v.nome}`}>
                <X size={16} />
              </button>
            </Transicao>
          ))}
        </div>
      )}

      <div className={styles.buscaWrap}>
        <Search size={16} className={styles.buscaIcone} aria-hidden="true" />
        <input
          type="text"
          className={styles.buscaInput}
          placeholder="Buscar pessoa pelo nome"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      </div>

      {habilitado && (
        <Transicao key={buscaDebounced} modo="subir" className={styles.resultados}>
          {isLoading ? (
            <p className={styles.aviso}>Buscando…</p>
          ) : resultados.length === 0 ? (
            <p className={styles.aviso}>Ninguém novo encontrado com esse nome.</p>
          ) : (
            resultados.map((p) => (
              <button key={p.id} type="button" className={styles.opcao} onClick={() => adicionar(p)}>
                <span>{p.nome}</span>
                <Check size={15} className={styles.opcaoCheck} aria-hidden="true" />
              </button>
            ))
          )}
        </Transicao>
      )}
    </div>
  )
}
```

- [ ] **Step 2: CSS — `.chips` (lista de chips que quebra linha)**

Adicionar em `SeletorResponsavel.module.css`:

```css
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.chip {
  flex: 0 1 auto;
  max-width: 100%;
}
```

(O `.chip` já existe; o `flex`/`max-width` deixa ele encolher e quebrar linha no mobile.)

- [ ] **Step 3: `EventoForm.tsx`**

- linha ~45: prop `responsavelNomeInicial?: string` → `responsaveisIniciais?: { id: string; nome: string }[]`.
- linha ~75: trocar na desestruturação `responsavelNomeInicial` por `responsaveisIniciais`.
- linha ~106: `const responsavelAtual = watch('responsavelPessoaId') as string | undefined` →
  ```tsx
  const responsavelIdsAtual = (watch('responsavelPessoaIds') as string[] | undefined) ?? []
  ```
- linhas ~455-458 (o `<SeletorResponsavel .../>`):
  ```tsx
  <SeletorResponsavel
    valores={responsavelIdsAtual.map((id) => ({
      id,
      nome: (responsaveisIniciais ?? []).find((r) => r.id === id)?.nome ?? 'Responsável',
    }))}
    onChange={(lista) => setValue('responsavelPessoaIds', lista.map((v) => v.id), { shouldDirty: true })}
  />
  ```

  > Problema: pessoa recém-adicionada não está em `responsaveisIniciais`, então o nome
  > cairia em "Responsável". Solução: o `SeletorResponsavel` guardar o próprio mapa
  > id→nome. Ajuste no componente: manter `const [nomes, setNomes] = useState<Record<string,string>>({})`,
  > populado a partir de `valores` no primeiro render e a cada `adicionar`. Então o pai
  > pode passar `valores` só com `{id}` e o componente resolve o nome (dos iniciais que o
  > pai passa OU do que ele mesmo guardou). **Refinar na implementação**: a forma mais
  > limpa é o `SeletorResponsavel` receber `iniciais: {id,nome}[]` + `ids: string[]` +
  > `onChange: (ids: string[]) => void`, e ele cuida de nome internamente. Escolher essa
  > assinatura se a de cima ficar frágil.

- [ ] **Step 4: `DrawerDetalheEvento.tsx`** (~linha 192)

```tsx
{evento.responsaveis.length > 0 && (
  <div className={styles.infoItem}>
    <span className={styles.infoIcone}><UserCircle size={20} /></span>
    <div>
      <p className={styles.infoLabel}>{evento.responsaveis.length > 1 ? 'Responsáveis' : 'Responsável'}</p>
      <p className={styles.infoValor}>{evento.responsaveis.map((r) => r.nome).join(', ')}</p>
    </div>
  </div>
)}
```

- [ ] **Step 5: Typecheck + lint + build**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/SeletorResponsavel.tsx src/components/module/eventos/EventoForm.tsx "src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx" src/hooks/evento/useEventoForm.ts && npm run build`
Expected: sem erros.

- [ ] **Step 6: Teste manual (autor)**

Checklist:
- Cadastrar evento com 2 responsáveis → salvar → abrir detalhe → "Responsáveis: A, B".
- Editar → adicionar 3º → salvar → o 3º recebe notificação, os outros não.
- Editar → remover 1 → salvar → some do drawer.
- Editar evento antigo (migrado) → o responsável que já existia aparece como chip.
- Arquivar uma pessoa que é responsável → o evento passa a mostrar o nome dela como
  texto (sem link), os outros responsáveis continuam.
- Mobile: chips quebram linha, sem overflow horizontal.

- [ ] **Step 7: Commit** (Tasks 3 + 4 juntas, após OK do autor)

```bash
git add frontend/src/types/evento.type.ts frontend/src/lib/validators.ts frontend/src/hooks/evento/useEventoForm.ts frontend/src/components/module/eventos/SeletorResponsavel.tsx frontend/src/components/module/eventos/SeletorResponsavel.module.css frontend/src/components/module/eventos/EventoForm.tsx "frontend/src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx"
git commit -m "feat(evento): seletor de responsáveis múltiplo (chips) e lista no detalhe"
```

---

## Task 5: Verificação final + PR

- [ ] **Step 1: Suíte backend**

Run: `cd backend/api && set -a; . ./.env; set +a; mvn -o test`
Expected: BUILD SUCCESS (2 skipped conhecidos).

- [ ] **Step 2: Front**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: sem erros.

- [ ] **Step 3: `graphify update`**

Run: `graphify update backend/api`

- [ ] **Step 4: Diagrama ER no `CLAUDE.md`**

- Em `EVENTO { ... }`: remover as linhas `uuid responsavel_pessoa_id FK` e
  `varchar responsavel_texto`.
- Adicionar bloco `EVENTO_RESPONSAVEL { uuid id PK; uuid igreja_id FK; uuid evento_id FK
  "ON DELETE CASCADE"; uuid pessoa_id FK "nulável - XOR com nome_texto"; varchar nome_texto
  "V37 - 'Pessoa removida do sistema' quando a pessoa some (LGPD)" }` e a relação
  `EVENTO ||--o{ EVENTO_RESPONSAVEL : "V37 - zero, um ou vários responsáveis"` +
  `PESSOA ||--o{ EVENTO_RESPONSAVEL : "é responsável por (ou nome_texto se removida)"`.
- Atualizar "Estado atual: **V36**" → "**V37**".
- No parágrafo "Cadastro de evento enriquecido", trocar a menção a `responsavel_pessoa_id`
  (single) pela relação N via `EVENTO_RESPONSAVEL`.

- [ ] **Step 5: Commit da doc**

```bash
git add backend/api/CLAUDE.md
git commit -m "docs: diagrama ER com evento_responsavel (múltiplos responsáveis, V37)"
```

- [ ] **Step 6: PR**

```bash
git push -u origin feat/evento-multiplos-responsaveis
gh pr create --base main --title "Evento com múltiplos responsáveis" --body "$(cat <<'EOF'
Um evento pode ter zero, um ou vários responsáveis (era no máximo um).

## O que muda
- Tabela de junção `evento_responsavel` (`pessoa_id` XOR `nome_texto`, padrão do `movimentacao_contribuinte`). Migration **V37** move o responsável único de cada evento e dropa `evento.responsavel_pessoa_id`/`responsavel_texto`.
- `EventoRequest.responsavelPessoaId` → `responsavelPessoaIds` (lista). `EventoResponse.responsavel` → `responsaveis` (lista).
- `EventoService`: `sincronizarResponsaveis` (diff add/remove), notifica **só** os recém-adicionados, séries copiam a lista pras ocorrências.
- Front: `SeletorResponsavel` vira múltiplo (busca + chips removíveis, sem limite); drawer de detalhe lista os responsáveis.
- Arquivar/excluir pessoa converte o vínculo dela em texto ("Pessoa removida do sistema") em cada evento, via `EventoResponsavelRepository.desvincularPessoa`.

## Testes
- Back: migração (colunas velhas sumiram, CHECK), `EventoServiceTest` (2 responsáveis, dedup, id de outra igreja, adiciona/remove na edição, notifica só o novo). Suíte inteira verde.
- Front: manual (checklist no plano).

Spec: `backend/api/docs/superpowers/specs/2026-09-02-evento-multiplos-responsaveis-design.md`
Plano: `backend/api/docs/superpowers/plans/2026-09-02-evento-multiplos-responsaveis.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

**1. Spec coverage:**
- Tabela `evento_responsavel` + migração de dados + drop colunas → Task 1. ✓
- Entidade `EventoResponsavel` + `Evento.@OneToMany` → Task 1. ✓
- `EventoRequest.responsavelPessoaIds` / `EventoResponse.responsaveis` → Task 2. ✓
- `resolverResponsaveis` / `sincronizarResponsaveis` / `notificarResponsaveis` (notifica só os novos) → Task 2. ✓
- Séries propagam a lista → Task 2 Step 8. ✓ (só o 1º bloco copia responsável hoje.)
- `EventoResponsavelRepository.desvincularPessoa` + trocar as 2 chamadas em `PessoaService` → Task 1 Steps 6-8. ✓
- `SeletorResponsavel` múltiplo (chips) → Task 4. ✓
- `useEventoForm` / validators / tipos → Task 3. ✓
- `DrawerDetalheEvento` lista → Task 4 Step 4. ✓
- ES não muda → nenhuma task (correto, `EventoDocument` não tem responsável). ✓
- Diagrama ER → Task 5 Step 4. ✓
- Testes back (serviço + migração) → Tasks 1 e 2. ✓

**2. Placeholder scan:** Task 4 Step 3 tem uma nota `> Refinar na implementação` sobre a
assinatura do `SeletorResponsavel` (passar `{id,nome}[]` vs. `ids[] + iniciais[]`). É uma
decisão de forma pequena com as duas opções escritas e um critério ("se a de cima ficar
frágil") — não é um TODO vago. As demais notas `>` são avisos de contexto (compilação
quebra entre Task 1 e 2; qual bloco de série copia responsável), não placeholders.

**3. Type consistency:**
- `EventoResponsavel.builder().igreja(...).evento(...).pessoa(...).nomeTexto(...)` — usado
  consistente em `resolverResponsaveis`, `sincronizarResponsaveis`, séries.
- `resolverResponsaveis(List<UUID>, UUID, Igreja, Evento)` e
  `sincronizarResponsaveis(Evento, List<UUID>, UUID, Igreja)` — assinaturas fixas, usadas
  em criar/atualizar.
- `EventoResponse.responsaveis` (`List<PessoaResumo>`) ↔ front `EventoPessoaResumo[]`. ✓
- `EventoRequest.responsavelPessoaIds` (`List<UUID>`) ↔ front `string[]`. ✓
- `SeletorResponsavel` props `valores: {id,nome}[]` + `onChange: ({id,nome}[]) => void` —
  Task 4 Step 1 define, Step 3 consome (com a ressalva de forma anotada).
- `responsaveisIniciais: {id: string; nome: string}[]` — Task 3 define, Task 4 consome.
