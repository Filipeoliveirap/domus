# Eventos Compartilhados Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que eventos criados por uma igreja sejam vistos e recebam inscrição de
pessoas de outras igrejas da mesma família (sede/congregações), preservando total
isolamento de gestão (editar, arquivar, controlar presença, inscrever terceiros
continua exigindo ser da mesma igreja do evento).

**Architecture:** Um campo booleano novo em `evento` (`restrito_propria_igreja`)
controla se o evento é visível só pela própria igreja ou por toda a família. Toda regra
de visibilidade (listagem, detalhe, busca, inscrição, elegibilidade) passa a checar
"minha igreja OU (família E não restrito)" em vez de só "minha igreja". Toda regra de
**gestão** continua igual a hoje — os métodos que já filtram por `findByIdAndIgrejaId(id,
minhaIgreja)` (editar, arquivar, marcar presença, listar inscritos administrativo)
**não mudam**, porque essa query já embute "só a própria igreja" — é exatamente a
garantia que a spec pede, sem precisar de código novo ali.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL, Flyway, Elasticsearch
(spring-data-elasticsearch), JUnit 5 + Mockito + AssertJ, Next.js/TypeScript no front.

## Global Constraints

- Migrations em `src/main/resources/db/migration`, próxima disponível: `V14`.
- Toda entidade de domínio já usa `igreja_id` extraído do JWT (`UsuarioAutenticado`),
  nunca do corpo da requisição — nenhuma mudança deste plano quebra essa regra.
- Testes de service: Mockito puro (`mock()` manual em `@BeforeEach`), AssertJ,
  nomenclatura de método em português descrevendo o cenário (`{ClasseAlvo}Test.java`).
- Nenhum commit antes de o autor testar manualmente — normalmente cada task deste plano
  termina com testes automatizados passando, mas o commit final da feature fica a
  critério do autor testar na aplicação rodando antes de subir.
- Sem comentários explicativos no código além do que já é convenção do arquivo (o
  projeto já usa comentários curtos de uma linha só quando o "porquê" não é óbvio — não
  adicionar blocos de comentário novos).

---

### Task 1: `FamiliaIgrejaService.idsDaFamiliaCompleta` (família bidirecional)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/familia/FamiliaIgrejaService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/familia/FamiliaIgrejaServiceTest.java` (criar se não existir)

**Interfaces:**
- Produces: `FamiliaIgrejaService.idsDaFamiliaCompleta(UUID igrejaId): Set<UUID>` —
  bidirecional. Se a igreja tem `igrejaMae`, retorna `{idMae} ∪ {ids de todas as filhas
  da mãe, inclusive a própria}`. Se não tem mãe, retorna `{igrejaId} ∪ {ids das próprias
  filhas}` (igual ao `idsDaFamilia` já existente). Usado pelas Tasks 3, 4, 6, 7, 9.

- [ ] **Step 1: Escrever o teste (3 cenários)**

```java
package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FamiliaIgrejaServiceTest {

    IgrejaRepository igrejaRepository;
    FamiliaIgrejaService service;

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        service = new FamiliaIgrejaService(igrejaRepository);
    }

    private Igreja igreja(UUID id, Igreja mae) {
        Igreja i = new Igreja();
        i.setId(id);
        i.setIgrejaMae(mae);
        return i;
    }

    @Test
    void igrejaComMaeVeMaeEIrmas() {
        UUID maeId = UUID.randomUUID();
        UUID euId = UUID.randomUUID();
        UUID irmaId = UUID.randomUUID();
        Igreja mae = igreja(maeId, null);
        Igreja eu = igreja(euId, mae);

        when(igrejaRepository.findById(euId)).thenReturn(Optional.of(eu));
        when(igrejaRepository.findByIgrejaMaeIdOrderByNomeAsc(maeId))
                .thenReturn(List.of(igreja(euId, mae), igreja(irmaId, mae)));

        Set<UUID> familia = service.idsDaFamiliaCompleta(euId);

        assertThat(familia).containsExactlyInAnyOrder(maeId, euId, irmaId);
    }

    @Test
    void igrejaSedeVeSiMesmaEFilhas() {
        UUID sedeId = UUID.randomUUID();
        UUID filhaId = UUID.randomUUID();
        Igreja sede = igreja(sedeId, null);

        when(igrejaRepository.findById(sedeId)).thenReturn(Optional.of(sede));
        when(igrejaRepository.buscarIdsDasFilhas(sedeId)).thenReturn(List.of(filhaId));

        Set<UUID> familia = service.idsDaFamiliaCompleta(sedeId);

        assertThat(familia).containsExactlyInAnyOrder(sedeId, filhaId);
    }

    @Test
    void igrejaIndependenteSoVeASiMesma() {
        UUID id = UUID.randomUUID();
        Igreja independente = igreja(id, null);

        when(igrejaRepository.findById(id)).thenReturn(Optional.of(independente));
        when(igrejaRepository.buscarIdsDasFilhas(id)).thenReturn(List.of());

        Set<UUID> familia = service.idsDaFamiliaCompleta(id);

        assertThat(familia).containsExactly(id);
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha (método não existe)**

Run: `mvn -q -o test -Dtest=FamiliaIgrejaServiceTest`
Expected: FAIL — `cannot find symbol: method idsDaFamiliaCompleta`

- [ ] **Step 3: Implementar `idsDaFamiliaCompleta`**

Adicionar em `FamiliaIgrejaService.java`, ao lado de `idsDaFamilia`:

```java
/**
 * Bidirecional: {@code {mãe} ∪ {todas as filhas da mãe, inclusive eu}} quando a igreja
 * tem mãe; {@code {eu} ∪ {minhas filhas}} quando não tem (sede ou independente).
 */
@Transactional(readOnly = true)
public java.util.Set<UUID> idsDaFamiliaCompleta(UUID igrejaId) {
    Igreja igreja = buscar(igrejaId);
    UUID maeId = igreja.getIgrejaMae() != null ? igreja.getIgrejaMae().getId() : null;

    if (maeId == null) {
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        ids.add(igrejaId);
        ids.addAll(igrejaRepository.buscarIdsDasFilhas(igrejaId));
        return ids;
    }

    java.util.Set<UUID> ids = new java.util.HashSet<>();
    ids.add(maeId);
    igrejaRepository.findByIgrejaMaeIdOrderByNomeAsc(maeId)
            .forEach(filha -> ids.add(filha.getId()));
    return ids;
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=FamiliaIgrejaServiceTest`
Expected: PASS (3 testes)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/familia/FamiliaIgrejaService.java \
        src/test/java/com/domus/api/modules/igreja/familia/FamiliaIgrejaServiceTest.java
git commit -m "feat(igreja): adiciona idsDaFamiliaCompleta (família bidirecional)"
```

---

### Task 2: Migration + campo `restritoPropriaIgreja` na entidade e DTOs de Evento

**Files:**
- Create: `src/main/resources/db/migration/V14__evento_restrito_propria_igreja.sql`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` (adicionar aos existentes)

**Interfaces:**
- Consumes: nada de tasks anteriores.
- Produces: `Evento.isRestritoPropriaIgreja()/setRestritoPropriaIgreja(boolean)`;
  `EventoRequest.restritoPropriaIgreja(): Boolean`; `EventoResponse` ganha os campos
  `igrejaOrganizadora: IgrejaResumo` e `podeGerenciarEsteEvento: boolean` — usados pelas
  Tasks 3-5 e pelo frontend (Task 10). `IgrejaResumo(UUID id, String nome, String
  sigla)` é um record aninhado novo em `EventoResponse`.

- [ ] **Step 1: Escrever a migration**

```sql
ALTER TABLE evento ADD COLUMN restrito_propria_igreja BOOLEAN NOT NULL DEFAULT false;

UPDATE evento e
SET restrito_propria_igreja = true
WHERE EXISTS (
    SELECT 1 FROM igreja i
    WHERE i.id = e.igreja_id
      AND (i.igreja_mae_id IS NOT NULL OR EXISTS (SELECT 1 FROM igreja f WHERE f.igreja_mae_id = i.id))
);
```

Salvar em `src/main/resources/db/migration/V14__evento_restrito_propria_igreja.sql`.

- [ ] **Step 2: Adicionar o campo na entidade `Evento`**

Em `Evento.java`, logo após o campo `controlaPresenca` (linha ~112):

```java
@Column(name = "restrito_propria_igreja", nullable = false)
@Builder.Default
private boolean restritoPropriaIgreja = false;
```

- [ ] **Step 3: Adicionar o campo em `EventoRequest`**

Em `EventoRequest.java`, como último campo do record, antes de `fotoId`:

```java
Boolean restritoPropriaIgreja,
```

(Segue o mesmo padrão de `exclusivoMembros`/`requerInscricao`: `Boolean` nulável no
request, resolvido para `boolean` primitivo no service com `Boolean.TRUE.equals(...)`.)

- [ ] **Step 4: Adicionar `igrejaOrganizadora` e `podeGerenciarEsteEvento` em `EventoResponse`**

No record principal, adicionar dois campos ao final da lista (antes do fechamento de
parênteses):

```java
IgrejaResumo igrejaOrganizadora,
boolean podeGerenciarEsteEvento
```

Adicionar o record aninhado, ao lado de `LocalInfo`/`PessoaResumo`:

```java
public record IgrejaResumo(UUID id, String nome, String sigla) {
    static IgrejaResumo de(com.domus.api.modules.igreja.Igreja igreja) {
        return new IgrejaResumo(igreja.getId(), igreja.getNome(), igreja.getSigla());
    }
}
```

Atualizar as duas fábricas `from(Evento e)` / `from(Evento e, Integer
inscricoesRemovidas)` para aceitarem quem está vendo, já que
`podeGerenciarEsteEvento`/o uso do badge dependem de `minhaIgrejaId`. Trocar a
assinatura para:

```java
public static EventoResponse from(Evento e, UUID minhaIgrejaId, boolean podeGerenciar) {
    return from(e, null, minhaIgrejaId, podeGerenciar);
}

public static EventoResponse from(Evento e, Integer inscricoesRemovidas,
                                   UUID minhaIgrejaId, boolean podeGerenciar) {
    return new EventoResponse(
            e.getId(), e.getTitulo(), e.getDescricao(),
            e.getInicioEm(), e.getFimEm(), LocalInfo.from(e), e.getTipo(),
            PessoaResumo.dePessoa(e.getResponsavel(), e.getResponsavelTexto()),
            PessoaResumo.deUsuario(e.getCriadoPor(), e.getCriadoPorTexto()),
            PessoaResumo.deUsuario(e.getAtualizadoPor(), e.getAtualizadoPorTexto()),
            e.getFoto() != null ? e.getFoto().getId() : null, e.getCreatedAt(),
            e.getVagas(), e.getPreco(), e.isExclusivoMembros(),
            e.isRequerInscricao(), e.isControlaPresenca(), e.getSituacao(), inscricoesRemovidas,
            e.getRecorteEtario(), e.getIdadeMin(), e.getIdadeMax(),
            e.getRestricaoEstadoCivil(), e.getRestricaoSexo(),
            IgrejaResumo.de(e.getIgreja()), podeGerenciar
    );
}
```

`podeGerenciar` é sempre `e.getIgreja().getId().equals(minhaIgrejaId)` combinado com a
role — quem chama `EventoResponse.from` (Task 3/4/5) calcula isso e passa pronto.

- [ ] **Step 5: Atualizar `EventoService` para compilar com a nova assinatura (passo temporário)**

Em `EventoService.java`, trocar as três chamadas a `EventoResponse.from(...)` (em
`cadastrarEvento`, `atualizarEvento`, e a versão sem `inscricoesRemovidas`) para passar
`igrejaId` como `minhaIgrejaId` e `true` como `podeGerenciar` — quem cadastra/atualiza
sempre gerencia o próprio evento nesse ponto do código. Isso é só para o projeto
voltar a compilar; `buscarPorId` e `listarEventos` recebem o cálculo real nas Tasks 3/4.

Em `cadastrarEvento` (linha ~132):

```java
return EventoResponse.from(salvo, igrejaId, true);
```

Em `atualizarEvento` (linha ~232):

```java
return EventoResponse.from(salvo, inscricoesRemovidas, igrejaId, true);
```

- [ ] **Step 6: Rodar os testes existentes de Evento para confirmar que nada quebrou**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: PASS (os testes existentes não checam os campos novos ainda)

- [ ] **Step 7: Escrever teste da migration (validação manual, seguindo o padrão do projeto)**

Este projeto não tem harness automatizado de migration (`@DataJpaTest` já roda contra o
Neon de testes só quando a query é não-trivial — ver convenção de testes do CLAUDE.md).
Validar manualmente:

```bash
set -a; source .env >/dev/null 2>&1; set +a
mvn -q -o flyway:info
```

Confirmar que `V14` aparece como pendente, aplicar com `mvn -q -o flyway:migrate` (ou
deixar o Spring Boot aplicar no próximo start), e rodar:

```sql
SELECT restrito_propria_igreja, COUNT(*) FROM evento GROUP BY 1;
```

Confirmar que eventos de igrejas com família (checar contra `igreja.igreja_mae_id`)
vieram `true`.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V14__evento_restrito_propria_igreja.sql \
        src/main/java/com/domus/api/modules/evento/Evento.java \
        src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java \
        src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java
git commit -m "feat(evento): adiciona campo restrito_propria_igreja"
```

---

### Task 3: `EventoService.cadastrarEvento`/`atualizarEvento` gravam o toggle

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `Evento.setRestritoPropriaIgreja(boolean)` (Task 2).
- Produces: nada novo — só fecha o ciclo de escrita do campo.

- [ ] **Step 1: Escrever os testes**

```java
@Test
void cadastrarEventoGravaRestritoPropriaIgrejaComoTrue() {
    EventoRequest req = requestComRestricao(true);
    EventoResponse response = service.cadastrarEvento(req, igrejaId, usuarioId);
    assertThat(response).isNotNull();
    verify(eventoRepository).save(argThat(e -> e.isRestritoPropriaIgreja()));
}

@Test
void cadastrarEventoSemInformarRestricaoGravaFalse() {
    EventoRequest req = requestComRestricao(null);
    service.cadastrarEvento(req, igrejaId, usuarioId);
    verify(eventoRepository).save(argThat(e -> !e.isRestritoPropriaIgreja()));
}
```

(`requestComRestricao(Boolean valor)` é um helper privado novo no teste — construir um
`EventoRequest` igual ao já usado no arquivo, mudando só o campo `restritoPropriaIgreja`.)

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=EventoServiceTest#cadastrarEventoGravaRestritoPropriaIgrejaComoTrue`
Expected: FAIL — `restritoPropriaIgreja` nunca é setado no builder de `cadastrarEvento`

- [ ] **Step 3: Implementar**

Em `EventoService.cadastrarEvento` (dentro do `Evento.builder()...build()`, junto de
`.controlaPresenca(...)`):

```java
.restritoPropriaIgreja(Boolean.TRUE.equals(data.restritoPropriaIgreja()))
```

Em `EventoService.atualizarEvento`, junto de `evento.setControlaPresenca(...)`:

```java
evento.setRestritoPropriaIgreja(Boolean.TRUE.equals(data.restritoPropriaIgreja()));
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): grava restritoPropriaIgreja no cadastro e na edição"
```

---

### Task 4: Listagem (`GET /eventos`) enxerga a família

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `FamiliaIgrejaService.idsDaFamiliaCompleta(UUID): Set<UUID>` (Task 1);
  `EventoResponse.from(Evento, UUID minhaIgrejaId, boolean podeGerenciar)` (Task 2).
- Produces: `EventoRepository.buscarPorFamilia(minhaIgrejaId, idsFamilia, q, tipo,
  recorteEtario, agora, pageable): Page<Evento>` — usado só aqui nesta task.

- [ ] **Step 1: Escrever o teste de service**

```java
@Test
void listarEventosIncluiCompartilhadosDaFamilia() {
    UUID outraIgrejaId = UUID.randomUUID();
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));

    Evento meu = evento(igrejaId, false);
    Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
    Page<Evento> pagina = new PageImpl<>(List.of(meu, compartilhado));
    when(eventoRepository.buscarPorFamilia(eq(igrejaId), eq(Set.of(igrejaId, outraIgrejaId)),
            isNull(), isNull(), isNull(), any(), any())).thenReturn(pagina);

    PagedResponse<EventoResponse> resposta = service.listarEventos(
            igrejaId, null, null, null, PageRequest.of(0, 12));

    assertThat(resposta.getContent()).hasSize(2);
}
```

(`eventoDeOutraIgreja(UUID igrejaId, boolean restrito)` é um helper novo, análogo ao
`evento(...)` já existente no arquivo, só que com `.igreja(new Igreja() {{ setId(igrejaId);
}})` — reaproveitar o helper `igreja(UUID id)` já existente se houver, ou criar um.)

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=EventoServiceTest#listarEventosIncluiCompartilhadosDaFamilia`
Expected: FAIL — `buscarPorFamilia` não existe ainda

- [ ] **Step 3: Adicionar a query em `EventoRepository`**

```java
@Query(value = """
    SELECT * FROM evento e
    WHERE e.deleted_at IS NULL
      AND e.igreja_id = ANY(CAST(:idsFamilia AS uuid[]))
      AND (e.igreja_id = :minhaIgreja OR e.restrito_propria_igreja = false)
      AND (CAST(:q AS text) IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
      AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
      AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
    ORDER BY
      CASE
        WHEN CAST(:agora AS timestamp) >= e.inicio_em
             AND CAST(:agora AS timestamp) <= COALESCE(e.fim_em, date_trunc('day', e.inicio_em) + INTERVAL '23:59:59')
          THEN 0
        WHEN CAST(:agora AS timestamp) < e.inicio_em
             AND CAST(e.inicio_em AS date) = CAST(CAST(:agora AS timestamp) AS date)
          THEN 1
        WHEN CAST(:agora AS timestamp) < e.inicio_em
          THEN 2
        ELSE 3
      END,
      e.inicio_em ASC
    """,
    countQuery = """
    SELECT COUNT(*) FROM evento e
    WHERE e.deleted_at IS NULL
      AND e.igreja_id = ANY(CAST(:idsFamilia AS uuid[]))
      AND (e.igreja_id = :minhaIgreja OR e.restrito_propria_igreja = false)
      AND (CAST(:q AS text) IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS text), '%')))
      AND (CAST(:tipo AS text) IS NULL OR e.tipo = CAST(:tipo AS text))
      AND (CAST(:recorteEtario AS text) IS NULL OR e.recorte_etario = CAST(:recorteEtario AS text))
    """,
    nativeQuery = true)
Page<Evento> buscarPorFamilia(@Param("minhaIgreja") UUID minhaIgreja,
                               @Param("idsFamilia") java.util.UUID[] idsFamilia,
                               @Param("q") String q,
                               @Param("tipo") String tipo,
                               @Param("recorteEtario") String recorteEtario,
                               @Param("agora") LocalDateTime agora,
                               Pageable pageable);
```

(O parâmetro do método é `UUID[]`, não `Set<UUID>`/`List<UUID>` — Postgres `= ANY(uuid[])`
precisa de array; converter o `Set<UUID>` para array no service com
`idsFamilia.toArray(new UUID[0])`.)

- [ ] **Step 4: Atualizar `EventoService.listarEventos`**

```java
@Cacheable(
        value = "eventos",
        key = "T(com.domus.api.config.redis.CacheKeys).eventos(#igrejaId, #q, #tipo, #recorteEtario, #pageable)"
)
@Transactional(readOnly = true)
public PagedResponse<EventoResponse> listarEventos(
        UUID igrejaId, String q, String tipo, String recorteEtario, Pageable pageable) {
    var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
    Page<EventoResponse> pagina = eventoRepository
            .buscarPorFamilia(igrejaId, idsFamilia.toArray(new UUID[0]), q, tipo, recorteEtario,
                    java.time.LocalDateTime.now(), pageable)
            .map(e -> EventoResponse.from(e, igrejaId, e.getIgreja().getId().equals(igrejaId)));
    return PagedResponse.from(pagina);
}
```

Adicionar o campo `private final FamiliaIgrejaService familiaIgrejaService;` em
`EventoService` (junto dos demais campos — `@RequiredArgsConstructor` já gera o
construtor sozinho).

Atualizar `EventoServiceTest.setup()`: adicionar
`familiaIgrejaService = mock(FamiliaIgrejaService.class);` e incluir esse mock na
chamada `new EventoService(...)` existente, na posição correspondente ao novo campo.
Sem isso os testes já escritos nas Tasks 2/3 deixam de compilar.

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): listagem inclui eventos compartilhados da familia"
```

---

### Task 5: Detalhe (`GET /eventos/{id}`) enxerga a família + `podeGerenciarEsteEvento`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `FamiliaIgrejaService.idsDaFamiliaCompleta` (Task 1); `Permissoes
  .podeGerenciarEventos(String role)` (já existente).
- Produces: `EventoRepository.buscarVisivelParaFamilia(id, minhaIgreja, idsFamilia):
  Optional<Evento>`; `EventoService.buscarPorId(UUID id, UUID igrejaId, String role)` —
  assinatura muda (ganha `role`), ajustar o único chamador (`EventoController`).

- [ ] **Step 1: Escrever os testes**

```java
@Test
void buscarPorIdRetornaEventoCompartilhadoDeOutraIgrejaDaFamilia() {
    UUID outraIgrejaId = UUID.randomUUID();
    Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
            .thenReturn(Optional.of(compartilhado));

    EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA");

    assertThat(response.podeGerenciarEsteEvento()).isFalse();
}

@Test
void buscarPorIdRecusaEventoRestritoDeOutraIgreja() {
    UUID outraIgrejaId = UUID.randomUUID();
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
            .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA"))
            .isInstanceOf(ResourceNotFoundException.class);
}

@Test
void buscarPorIdDaPropriaIgrejaSempreDeixaGerenciar() {
    Evento meu = evento(igrejaId, false);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
    when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
            .thenReturn(Optional.of(meu));

    EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ADMIN_IGREJA");

    assertThat(response.podeGerenciarEsteEvento()).isTrue();
}

@Test
void buscarPorIdAcessoComumNuncaGerencia() {
    Evento meu = evento(igrejaId, false);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
    when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
            .thenReturn(Optional.of(meu));

    EventoResponse response = service.buscarPorId(eventoId, igrejaId, "ACESSO_COMUM");

    assertThat(response.podeGerenciarEsteEvento()).isFalse();
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: FAIL — `buscarVisivelParaFamilia` não existe; `buscarPorId` não aceita `role`

- [ ] **Step 3: Adicionar a query em `EventoRepository`**

```java
@Query("""
    SELECT e FROM Evento e
    WHERE e.id = :id
      AND e.igreja.id IN :idsFamilia
      AND (e.igreja.id = :minhaIgreja OR e.restritoPropriaIgreja = false)
""")
Optional<Evento> buscarVisivelParaFamilia(@Param("id") UUID id,
                                          @Param("minhaIgreja") UUID minhaIgreja,
                                          @Param("idsFamilia") java.util.Set<UUID> idsFamilia);
```

- [ ] **Step 4: Atualizar `EventoService.buscarPorId`**

```java
@Transactional(readOnly = true)
public EventoResponse buscarPorId(UUID id, UUID igrejaId, String role) {
    var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
    Evento evento = eventoRepository.buscarVisivelParaFamilia(id, igrejaId, idsFamilia)
            .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    boolean podeGerenciar = Permissoes.podeGerenciarEventos(role)
            && evento.getIgreja().getId().equals(igrejaId);
    return EventoResponse.from(evento, igrejaId, podeGerenciar);
}
```

Atualizar o único chamador em `EventoController.buscarPorId`:

```java
@GetMapping("/{id}")
public ResponseEntity<EventoResponse> buscarPorId(@PathVariable UUID id) {
    UUID igrejaId = usuarioAutenticado.getIgrejaId();
    String role = usuarioAutenticado.getRole();
    return ResponseEntity.ok(eventoService.buscarPorId(id, igrejaId, role));
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/main/java/com/domus/api/modules/evento/EventoController.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): detalhe do evento enxerga a familia e calcula podeGerenciarEsteEvento"
```

---

### Task 6: Cache evict por família (efeito colateral da visibilidade nova)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `FamiliaIgrejaService.idsDaFamiliaCompleta` (Task 1); `CacheEvictor
  .evictPorIgreja(String, UUID)` (já existente, sem mudança de assinatura).
- Produces: nada novo — só corrige um efeito colateral.

Sem esta task, editar um evento pra virar compartilhado (ou deixar de ser) só limpa o
cache de listagem da própria igreja — as outras igrejas da família continuam vendo a
lista antiga em cache até expirar sozinho.

- [ ] **Step 1: Escrever o teste**

```java
@Test
void atualizarEventoLimpaCacheDeTodaFamilia() {
    UUID outraIgrejaId = UUID.randomUUID();
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    dadoQueEventoExiste();

    service.atualizarEvento(eventoId, requestValido(), igrejaId, usuarioId, false);

    verify(cacheEvictor).evictPorIgreja("eventos", igrejaId);
    verify(cacheEvictor).evictPorIgreja("eventos", outraIgrejaId);
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=EventoServiceTest#atualizarEventoLimpaCacheDeTodaFamilia`
Expected: FAIL — só `igrejaId` é evictado hoje

- [ ] **Step 3: Implementar**

Em `EventoService`, extrair um helper privado e usá-lo nos três pontos que hoje chamam
`cacheEvictor.evictPorIgreja("eventos", igrejaId)` (`cadastrarEvento`, `atualizarEvento`,
`arquivarEvento`):

```java
private void evictarCacheDeEventosDaFamilia(UUID igrejaId) {
    familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)
            .forEach(id -> cacheEvictor.evictPorIgreja("eventos", id));
}
```

Trocar `cacheEvictor.evictPorIgreja("eventos", igrejaId);` pelas três ocorrências por
`evictarCacheDeEventosDaFamilia(igrejaId);`.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "fix(evento): evicta cache de eventos para toda a familia, nao so a propria igreja"
```

---

### Task 7: Inscrição — auto-inscrição, cancelamento e convidado enxergam a família

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `FamiliaIgrejaService.idsDaFamiliaCompleta` (Task 1).
- Produces: `EventoRepository.buscarComLockVisivelParaFamilia(id, minhaIgreja,
  idsFamilia): Optional<Evento>`; `InscricaoRepository.buscarVisivelParaFamilia(id,
  idsFamilia): Optional<InscricaoEvento>`. `InscricaoService.inscrever`/`cancelar`/
  `adicionarAcompanhante`/`removerAcompanhante` passam a resolver visibilidade pela
  família em vez de igualdade estrita de `igrejaId`.

Contexto: `InscricaoEvento.igreja` é sempre a igreja **do evento** (a organizadora), não
a da pessoa que se inscreveu. Hoje `buscarInscricao(id, igrejaId)` e `inscrever(...,
igrejaId)` comparam contra o `igrejaId` de quem está chamando — que deixa de bater
quando a pessoa é de outra igreja da família se inscrevendo num evento compartilhado.
Por isso os métodos abaixo passam a resolver visibilidade pela família, não por
igualdade estrita.

- [ ] **Step 1: Escrever os testes**

```java
@Test
void pessoaDeOutraIgrejaDaFamiliaConseguSeInscreverEmEventoCompartilhado() {
    UUID outraIgrejaId = UUID.randomUUID();
    Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
            .thenReturn(Optional.of(compartilhado));
    when(membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa()));
    when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());

    MinhaInscricaoResponse response = service.inscrever(
            eventoId, pessoaId, null, pessoaId, "ACESSO_COMUM", false, igrejaId);

    assertThat(response).isNotNull();
    verify(inscricaoRepository).save(any());
}

@Test
void pessoaDeIgrejaForaDaFamiliaNaoConseguSeInscrever() {
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
    when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
            .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.inscrever(
            eventoId, pessoaId, null, pessoaId, "ACESSO_COMUM", false, igrejaId))
            .isInstanceOf(ResourceNotFoundException.class);
}

@Test
void cancelarProrpiaInscricaoFuncionaMesmoEmEventoDeOutraIgrejaDaFamilia() {
    UUID outraIgrejaId = UUID.randomUUID();
    InscricaoEvento inscricao = inscricaoConfirmada(outraIgrejaId, pessoaId);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId, outraIgrejaId)))
            .thenReturn(Optional.of(inscricao));

    service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

    verify(inscricaoRepository).save(argThat(i -> i.getStatus() == StatusInscricao.CANCELADA));
}
```

(Helpers `eventoDeOutraIgreja`, `inscricaoConfirmada` seguem o padrão dos helpers já
existentes no arquivo de teste — construir via `.builder()` como os demais.)

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=InscricaoServiceTest`
Expected: FAIL — métodos novos de repository não existem

- [ ] **Step 3: Adicionar as queries**

Em `EventoRepository.java`:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
    SELECT e FROM Evento e
    WHERE e.id = :id
      AND e.igreja.id IN :idsFamilia
      AND (e.igreja.id = :minhaIgreja OR e.restritoPropriaIgreja = false)
""")
Optional<Evento> buscarComLockVisivelParaFamilia(@Param("id") UUID id,
                                                  @Param("minhaIgreja") UUID minhaIgreja,
                                                  @Param("idsFamilia") java.util.Set<UUID> idsFamilia);
```

Em `InscricaoRepository.java`:

```java
@Query("SELECT i FROM InscricaoEvento i WHERE i.id = :id AND i.igreja.id IN :idsFamilia")
Optional<InscricaoEvento> buscarVisivelParaFamilia(@Param("id") UUID id,
                                                    @Param("idsFamilia") java.util.Set<UUID> idsFamilia);
```

- [ ] **Step 4: Injetar `FamiliaIgrejaService` em `InscricaoService` e atualizar os métodos**

Adicionar `private final FamiliaIgrejaService familiaIgrejaService;` no construtor.

Em `inscrever` (trocar a primeira linha do método):

```java
var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
        .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
```

Em `buscarInscricao` (usado por `cancelar`, `adicionarAcompanhante`), trocar a
implementação:

```java
private InscricaoEvento buscarInscricao(UUID id, UUID minhaIgrejaId) {
    var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(minhaIgrejaId);
    return inscricaoRepository.buscarVisivelParaFamilia(id, idsFamilia)
            .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
}
```

`removerAcompanhante` já busca o `AcompanhanteInscricao` por id direto e depois compara
`inscricao.getIgreja().getId().equals(igrejaId)` manualmente (linha 277) — trocar essa
comparação por `familiaIgrejaService.idsDaFamiliaCompleta(igrejaId).contains(inscricao
.getIgreja().getId())`.

Atualizar `InscricaoServiceTest.setup()`: adicionar
`familiaIgrejaService = mock(FamiliaIgrejaService.class);` e incluir esse mock na
chamada `new InscricaoService(...)` existente, na posição correspondente ao novo campo.
Sem isso os testes já escritos antes desta task deixam de compilar.

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=InscricaoServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(inscricao): auto-inscricao e cancelamento enxergam eventos da familia"
```

---

### Task 8: Elegibilidade e lista de participantes enxergam a família

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `EventoRepository.buscarVisivelParaFamilia` (Task 5).
- Produces: `EventoService.elegibilidade(UUID, UUID, UUID)` sem mudança de assinatura,
  só de implementação; `InscricaoService.listarParticipantes(UUID, UUID)` idem.

- [ ] **Step 1: Escrever os testes**

```java
// EventoServiceTest
@Test
void elegibilidadeFuncionaParaEventoCompartilhadoDeOutraIgreja() {
    UUID outraIgrejaId = UUID.randomUUID();
    Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
            .thenReturn(Optional.of(compartilhado));
    when(pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(pessoa()));

    ElegibilidadeResponse response = service.elegibilidade(eventoId, pessoaId, igrejaId);

    assertThat(response).isNotNull();
}
```

```java
// InscricaoServiceTest
@Test
void listarParticipantesFuncionaParaEventoCompartilhadoDeOutraIgreja() {
    UUID outraIgrejaId = UUID.randomUUID();
    Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
    when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
            .thenReturn(Set.of(igrejaId, outraIgrejaId));
    when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
            .thenReturn(Optional.of(compartilhado));
    when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(List.of());

    List<ParticipanteResponse> resposta = service.listarParticipantes(eventoId, igrejaId);

    assertThat(resposta).isEmpty();
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=EventoServiceTest,InscricaoServiceTest`
Expected: FAIL — os dois métodos ainda usam `findByIdAndIgrejaId` estrito

- [ ] **Step 3: Implementar**

Em `EventoService.elegibilidade`, trocar a busca do evento:

```java
@Transactional(readOnly = true)
public ElegibilidadeResponse elegibilidade(UUID eventoId, UUID pessoaId, UUID igrejaId) {
    var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
    Evento evento = eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
            .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    Pessoa pessoa = pessoaRepository.findByIdAndIgrejaId(pessoaId, igrejaId)
            .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrado."));
    return ElegibilidadeResponse.from(elegibilidadeService.avaliar(evento, pessoa));
}
```

Em `InscricaoService.listarParticipantes`, mesma troca (precisa injetar
`EventoRepository`/`FamiliaIgrejaService` — `EventoRepository` já está injetado nesta
classe):

```java
@Transactional(readOnly = true)
public List<ParticipanteResponse> listarParticipantes(UUID eventoId, UUID igrejaId) {
    var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
    eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
            .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

    return inscricaoRepository.listarPorEvento(eventoId)
            .stream().map(ParticipanteResponse::from).toList();
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=EventoServiceTest,InscricaoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(evento): elegibilidade e lista de participantes enxergam a familia"
```

---

### Task 9: Rastreio de igreja na lista de inscritos e nos participantes

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/InscritoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ParticipanteResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `Pessoa.getIgreja(): Igreja` (já existente).
- Produces: `InscritoResponse.igrejaDaPessoa: IgrejaResumo`; `ParticipanteResponse
  .igrejaDaPessoa: IgrejaResumo` — reaproveita o record `EventoResponse.IgrejaResumo`
  (Task 2), tornando-o público/importável.

- [ ] **Step 1: Tornar `EventoResponse.IgrejaResumo` reaproveitável**

Em `EventoResponse.java`, o record `IgrejaResumo` já é público (Task 2) — só extrair o
método de fábrica para aceitar diretamente uma `Igreja`, o que já é o caso
(`IgrejaResumo.de(Igreja)`), então nenhuma mudança adicional aqui além de garantir que
o record não seja `private`.

- [ ] **Step 2: Escrever os testes**

```java
@Test
void inscritoResponseTrazIgrejaDaPessoa() {
    Pessoa pessoaDeOutraIgreja = pessoaComIgreja(UUID.randomUUID(), "Congregação Norte", "CN");
    InscricaoEvento inscricao = InscricaoEvento.builder()
            .id(UUID.randomUUID()).pessoa(pessoaDeOutraIgreja)
            .acompanhantes(new ArrayList<>()).createdAt(java.time.LocalDateTime.now())
            .build();

    InscritoResponse response = InscritoResponse.from(inscricao, null);

    assertThat(response.igrejaDaPessoa().nome()).isEqualTo("Congregação Norte");
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `mvn -q -o test -Dtest=InscricaoServiceTest#inscritoResponseTrazIgrejaDaPessoa`
Expected: FAIL — `igrejaDaPessoa()` não existe

- [ ] **Step 3: Implementar**

Em `InscritoResponse.java`, adicionar `com.domus.api.modules.evento.DTOs.EventoResponse
.IgrejaResumo igrejaDaPessoa` como último campo do record e, na fábrica `from`, passar
`EventoResponse.IgrejaResumo.de(i.getPessoa().getIgreja())`.

Em `ParticipanteResponse.java`, mesma coisa: campo `igrejaDaPessoa` e
`EventoResponse.IgrejaResumo.de(i.getPessoa().getIgreja())` na fábrica.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `mvn -q -o test -Dtest=InscricaoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/DTOs/InscritoResponse.java \
        src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ParticipanteResponse.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(inscricao): lista de inscritos e participantes trazem a igreja da pessoa"
```

---

### Task 10: Busca global (Elasticsearch) enxerga a família

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/busca/EventoDocument.java`
- Modify: `src/main/java/com/domus/api/modules/evento/busca/BuscaEventoService.java`
- Modify: `src/main/java/com/domus/api/shared/busca/BuscaGlobalService.java`
- Test: manual (busca contra Elasticsearch real — projeto não tem harness automatizado
  de índice, ver dívida técnica "sem testes de integração ES" no CLAUDE.md)

**Interfaces:**
- Consumes: `FamiliaIgrejaService.idsDaFamiliaCompleta` (Task 1).
- Produces: `BuscaEventoService.buscar(String termo, UUID minhaIgreja, Set<UUID>
  idsFamilia, int limite): List<ResultadoBusca>` — assinatura muda, ajustar o único
  chamador (`BuscaGlobalService`).

- [ ] **Step 1: Adicionar `restritoPropriaIgreja` ao `EventoDocument`**

```java
@Field(type = FieldType.Boolean)
private boolean restritoPropriaIgreja;
```

E em `EventoDocument.de(Evento evento)`:

```java
doc.setRestritoPropriaIgreja(evento.isRestritoPropriaIgreja());
```

- [ ] **Step 2: Atualizar `BuscaEventoService.buscar`**

```java
public List<ResultadoBusca> buscar(String termo, UUID minhaIgreja, java.util.Set<UUID> idsFamilia, int limite) {
    List<String> idsFamiliaStr = idsFamilia.stream().map(UUID::toString).toList();

    Query filtroVisibilidade = Query.of(q -> q
            .bool(b -> b
                    .should(s -> s.term(t -> t.field("igrejaId").value(minhaIgreja.toString())))
                    .should(s -> s.bool(bb -> bb
                            .filter(f -> f.terms(t -> t.field("igrejaId")
                                    .terms(tt -> tt.value(idsFamiliaStr.stream()
                                            .map(co.elastic.clients.elasticsearch._types.FieldValue::of).toList()))))
                            .filter(f -> f.term(t -> t.field("restritoPropriaIgreja").value(false)))
                    ))
                    .minimumShouldMatch("1")
            )
    );

    // ... resto do método igual, trocando "filtroIgreja" por "filtroVisibilidade" no bool final
}
```

Trocar, no `queryFinal`, `.filter(filtroIgreja)` por `.filter(filtroVisibilidade)`.

- [ ] **Step 3: Atualizar o chamador em `BuscaGlobalService`**

Localizar a chamada a `buscaEventoService.buscar(termo, igrejaId, limite)` e trocar
para injetar `FamiliaIgrejaService` (se ainda não estiver injetado) e passar:

```java
buscaEventoService.buscar(termo, igrejaId,
        familiaIgrejaService.idsDaFamiliaCompleta(igrejaId), limite);
```

- [ ] **Step 4: Reindexar e validar manualmente**

Como o índice ganhou um campo novo (`restritoPropriaIgreja`), rodar o endpoint de
reindexação já existente (ver memória `reindexacao-es-endpoint-admin`):

```bash
curl -X POST http://localhost:8080/admin/reindexacao -H "Cookie: <sessão de admin>"
```

Confirmar manualmente no navegador: criar um evento compartilhado numa igreja, logar
com usuário de outra igreja da família, buscar pelo título — deve aparecer.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/busca/EventoDocument.java \
        src/main/java/com/domus/api/modules/evento/busca/BuscaEventoService.java \
        src/main/java/com/domus/api/shared/busca/BuscaGlobalService.java
git commit -m "feat(busca): evento compartilhado aparece na busca global da familia"
```

---

### Task 11: Frontend — toggle, badge, gates de gestão

**Files:**
- Modify: `frontend/src/types/evento.type.ts`
- Modify: formulário de evento (localizar via `find frontend/src/app -iname "*evento*form*" -o -iname "*EventoForm*"`)
- Modify: card/listagem de evento (`frontend/src/app/(app)/eventos/page.tsx` ou
  equivalente — confirmar caminho exato antes de editar)
- Modify: detalhe de evento (`frontend/src/app/(app)/eventos/[id]/page.tsx` ou
  equivalente)
- Modify: lista de inscritos / participantes (componente correspondente)

**Interfaces:**
- Consumes: `EventoResponse.igrejaOrganizadora: {id, nome, sigla}`,
  `EventoResponse.podeGerenciarEsteEvento: boolean` (Task 2/4/5);
  `InscritoResponse.igrejaDaPessoa`/`ParticipanteResponse.igrejaDaPessoa` (Task 9).

Esta task não tem passo de teste automatizado (não há harness de front no projeto —
dívida técnica já conhecida, ver CLAUDE.md). Validar manualmente no navegador antes de
marcar como concluída.

- [ ] **Step 1: Atualizar o tipo `EventoResponse` no front**

Em `frontend/src/types/evento.type.ts`, adicionar:

```typescript
export interface IgrejaResumo {
  id: string
  nome: string
  sigla: string | null
}

// dentro de EventoResponse:
igrejaOrganizadora: IgrejaResumo
podeGerenciarEsteEvento: boolean
restritoPropriaIgreja?: boolean
```

E em `InscritoResponse`/`ParticipanteResponse` (mesmo arquivo ou tipo próprio de
inscrição): `igrejaDaPessoa: IgrejaResumo`.

- [ ] **Step 2: Checkbox "Apenas minha igreja" no formulário**

Localizar o formulário de criar/editar evento. Adicionar checkbox "Apenas minha
igreja", visível só quando `minhaIgreja.igrejaMaeId != null || minhaIgreja.temFilhas`
(esse dado já deve estar disponível no `useAuthStore` ou equivalente — se não estiver,
adicionar ao `GET /auth/me`, fora do escopo desta task se não existir: nesse caso,
anotar como pendência e seguir com o campo sempre visível até essa informação existir).
Mapear pro campo `restritoPropriaIgreja` do request.

- [ ] **Step 3: Badge de igreja organizadora no card**

No componente de card/linha de evento da listagem, exibir um badge com
`evento.igrejaOrganizadora.sigla ?? evento.igrejaOrganizadora.nome` só quando
`evento.igrejaOrganizadora.id !== minhaIgrejaId`.

- [ ] **Step 4: Gating dos botões de gestão no detalhe**

Trocar toda checagem de `podeGerenciarEventos(role)` feita hoje na tela de detalhe do
evento por `evento.podeGerenciarEsteEvento` (que já veio calculado da API, combinando
role + mesma igreja).

- [ ] **Step 5: Badge de igreja na lista de inscritos/participantes**

No componente de lista de inscritos e de participantes, exibir a coluna/badge de
`igrejaDaPessoa` só quando `!evento.restritoPropriaIgreja` OU quando há mais de uma
igreja distinta entre os itens da lista carregada.

- [ ] **Step 6: Validar manualmente no navegador**

Rodar o front e o back localmente, logar com duas contas de igrejas diferentes da mesma
família (a igreja piloto já tem isso em produção — usar um ambiente de dev/staging, não
produção), e conferir:
- Evento criado como compartilhado por uma igreja aparece na listagem da outra.
- Badge de igreja organizadora aparece certo.
- Botões de gestão somem para quem não é da igreja organizadora.
- Inscrição funciona de uma igreja pra evento da outra.
- Lista de participantes mostra a igreja de cada inscrito quando aplicável.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/types/evento.type.ts <demais arquivos de front modificados>
git commit -m "feat(evento): frontend do evento compartilhado (toggle, badge, gates)"
```

---

### Task 12: Rodar a suíte completa e revisão final

**Files:** nenhum arquivo novo — task de verificação.

- [ ] **Step 1: Rodar toda a suíte de backend**

```bash
set -a; source .env >/dev/null 2>&1; set +a
mvn -q test
```

Expected: PASS em todos os módulos, sem regressão nos testes de Evento, Inscrição,
Igreja/Família que já existiam antes deste plano.

- [ ] **Step 2: Rodar o typecheck do frontend**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 3: Revisar a spec original contra o que foi entregue**

Reler `docs/superpowers/specs/2026-07-28-eventos-compartilhados-design.md` seção por
seção e confirmar que cada uma tem uma task correspondente neste plano (Tasks 1-11).
Não deve sobrar nada sem cobertura, exceto os itens listados em "Fora de escopo" da
spec.

- [ ] **Step 4: Avisar o autor para teste manual antes de qualquer commit adicional/push**

Seguindo a convenção do projeto (`nao-commitar-antes-do-teste`): os commits desta task
já foram feitos incrementalmente por task; não fazer squash nem push sem o autor testar
a feature completa rodando localmente.
