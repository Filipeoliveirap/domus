# Prazo de inscrição opcional no evento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que um evento tenha um prazo opcional "inscrições até"; passado o prazo, membro comum e convidado por link público não se inscrevem mais (admin/líder passam com aviso), o cancelamento respeita um toggle do criador, e um job diário dispara notificações in-app.

**Architecture:** Uma coluna nullable `inscricoes_ate` + um flag `permite_cancelar_apos_prazo` no `evento`, mais 3 colunas de carimbo pra dedup do job (2 em `evento`, 1 em `inscricao_evento`). A regra de "quem pode furar o prazo" mora no `InscricaoService` (pergunta pela capacidade `podeGerenciarInscricoes`, não pelo nome do perfil). O front cruza a situação derivada `SituacaoInscricao` com a role do usuário logado pra decidir entre botão desabilitado e botão-ativo-com-aviso. O job segue o padrão de `CobrancaEventoExpiracaoJob` (`@Scheduled`, sem fila).

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL (Testcontainers nos testes), JUnit 5 + Mockito + AssertJ. Front: Next.js, TypeScript, React Hook Form + Zod, TanStack Query, CSS Modules.

**Spec:** `backend/api/docs/superpowers/specs/2026-09-07-evento-prazo-inscricao-design.md`

## Global Constraints

- **Diretório de trabalho:** `backend/api` (back) e `frontend` (front). Caminhos abaixo são relativos a esses.
- **Migrations:** próximo número livre é **V38**. Arquivo em `backend/api/src/main/resources/db/migration/V38__evento_prazo_inscricao.sql`.
- **Rodar testes back:** Docker rodando + Redis + Elasticsearch de pé (`docker compose up -d redis elasticsearch` na raiz do repo) + variáveis do `.env` carregadas (`set -a; . ./.env; set +a` a partir da raiz). Postgres sobe sozinho via Testcontainers. Comando: `mvn -o test -Dtest=NomeDaClasse` (a partir de `backend/api`).
- **Estilo de teste back:** Mockito puro é a regra (90% do projeto). `mock()` manual no `@BeforeEach`, montando o service com `new`. AssertJ primário (`assertThat`, `assertThatThrownBy`). Método de teste em `snake_case`/português descrevendo o cenário. Helpers privados por classe, sem base class nem fixture compartilhada.
- **Camadas:** `controller → service → repository`. Services retornam **DTOs**, nunca entidades.
- **Regras de negócio internas** (ex.: `ElegibilidadeService`) são instanciadas de verdade nos testes, nunca mockadas. Só repositories e services externos ao SUT são mockados.
- **`igreja_id` sempre do JWT**, nunca do corpo. Soft delete via `deleted_at`.
- **Não commitar antes de o autor testar** cada pedaço — mas cada task deste plano termina com um commit (o autor testa o pedaço inteiro, formado por várias tasks, antes de seguir; ver "Checkpoints de teste do autor" no fim). Um commit coerente por task.
- **Nunca enfraquecer teste pra passar.** Se um teste existente quebrar, corrigir o código de produção ou parar e explicar.
- **Front:** responsividade obrigatória (mobile Android + iOS). Animação/suavidade padrão (`<Transicao>`, `<Revelar>`). Feedback via `notificar()` — nunca toast do sonner, nunca `window.confirm`. Rótulo de perfil ("Administrador"/"Líder") vem de UMA função/constante.
- **Nomes de perfil / capacidade:** back usa `com.domus.api.shared.security.Permissoes.podeGerenciarInscricoes(String role)`. Front usa `@/lib/permissoes`.

---

## File Structure

### Backend — criar
- `src/main/resources/db/migration/V38__evento_prazo_inscricao.sql` — as 5 colunas novas.
- `src/main/java/com/domus/api/modules/evento/inscricao/SituacaoInscricao.java` — enum derivado.
- `src/main/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJob.java` — job diário.
- `src/test/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJobTest.java`
- `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPrazoTest.java` — testes de guarda de inscrição/cancelamento (arquivo novo pra não inchar mais o `InscricaoServiceTest`, seguindo o precedente de `InscricaoElegibilidadeTest`).

### Backend — modificar
- `src/main/java/com/domus/api/modules/evento/Evento.java` — 4 campos + método `getSituacaoInscricao()`.
- `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java` — 1 campo.
- `src/main/java/com/domus/api/modules/evento/EventoService.java` — validação + `permiteCancelarAposPrazo` em `copiarCamposEditaveisPara` + set nos builders/updates.
- `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java` — 2 campos.
- `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java` — 3 campos (`inscricoesAte`, `permiteCancelarAposPrazo`, `situacaoInscricao`).
- `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java` — `validarPrazoInscricao` + `validarCancelamentoPermitido` nos pontos certos.
- `src/main/java/com/domus/api/modules/evento/convite/DTOs/ConvitePublicoResponse.java` — 1 campo `situacaoInscricao`.
- `src/main/java/com/domus/api/modules/evento/convite/ConviteController.java` — preencher o campo novo.
- `src/main/java/com/domus/api/modules/notificacao/TipoNotificacao.java` — 3 valores.
- `src/main/java/com/domus/api/modules/evento/EventoRepository.java` — 1 query pro job.
- `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java` — 1 query pro job.
- `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` — testes de validação e propagação.
- Testes de migration existentes (procurar por `@DataJpaTest` que valida colunas — provavelmente `MigracaoV*Test`; se não houver um genérico, criar um mínimo).

### Frontend — modificar
- `src/lib/validators.ts` — `eventoSchemaBase` + refine.
- `src/hooks/evento/useEventoForm.ts` — reidratar e montar o request.
- `src/components/module/eventos/EventoForm.tsx` — campos novos no bloco de inscrição.
- `src/types/evento.type.ts` — `SituacaoInscricao`, campos em `EventoResponse` e `EventoRequest`.
- `src/components/module/eventos/BotaoConfirmarPresenca.tsx` — estado desabilitado/aviso.
- `src/components/module/eventos/SeloPrazoInscricao.tsx` (**criar**) + uso em `EventoCard.tsx`.
- `src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx` — linha do prazo + aviso.
- `src/components/module/eventos/ModalInscreverAlguem.tsx` / `ModalInscreverPessoas.tsx` — aviso no topo.
- `src/types/convite.type.ts` + `src/app/convite/[token]/page.tsx` — estado "encerradas".
- Handler de erros de API (procurar onde `PRAZO_*`/códigos de erro viram texto — provavelmente um `mapaErros` em `src/lib` ou no próprio `notificar`).

---

## PEDAÇO 1 — Modelo de dados + validação (backend)

### Task 1: Migration V38 + campos nas entidades

**Files:**
- Create: `src/main/resources/db/migration/V38__evento_prazo_inscricao.sql`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoPrazoMigracaoTest.java` (create)

**Interfaces:**
- Produces:
  - `Evento.getInscricoesAte()` / `setInscricoesAte(LocalDateTime)` — nullable
  - `Evento.isPermiteCancelarAposPrazo()` / `setPermiteCancelarAposPrazo(boolean)` — default `true`
  - `Evento.getAvisoPrazoProximoEm()` / `setAvisoPrazoProximoEm(LocalDateTime)` — nullable
  - `Evento.getAvisoPrazoFechadoEm()` / `setAvisoPrazoFechadoEm(LocalDateTime)` — nullable
  - `InscricaoEvento.getAvisoPrazoIncompletoEm()` / `setAvisoPrazoIncompletoEm(LocalDateTime)` — nullable

- [ ] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V38__evento_prazo_inscricao.sql`:

```sql
-- Prazo de inscrição opcional (ver docs/superpowers/specs/2026-09-07-evento-prazo-inscricao-design.md).
ALTER TABLE evento
    ADD COLUMN inscricoes_ate              TIMESTAMP,
    ADD COLUMN permite_cancelar_apos_prazo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN aviso_prazo_proximo_em      TIMESTAMP,
    ADD COLUMN aviso_prazo_fechado_em      TIMESTAMP;

ALTER TABLE inscricao_evento
    ADD COLUMN aviso_prazo_incompleto_em   TIMESTAMP;
```

- [ ] **Step 2: Adicionar os campos em `Evento.java`**

Depois do bloco `divergeDaSerie` (por volta da linha 126), antes de `deletedAt`:

```java
    /** Prazo de inscrição (V38). NULL = aceita inscrição até o evento começar (comportamento antigo). */
    @Column(name = "inscricoes_ate")
    private LocalDateTime inscricoesAte;

    /** V38. Só tem efeito quando inscricoesAte != null. TRUE = quem já se inscreveu ainda
     *  pode cancelar depois do prazo; FALSE = lista travada no prazo. */
    @Column(name = "permite_cancelar_apos_prazo", nullable = false)
    @Builder.Default
    private boolean permiteCancelarAposPrazo = true;

    /** V38 — carimbo do PrazoInscricaoJob (dedup do aviso "prazo chegando"). */
    @Column(name = "aviso_prazo_proximo_em")
    private LocalDateTime avisoPrazoProximoEm;

    /** V38 — carimbo do PrazoInscricaoJob (dedup do aviso "prazo fechou"). */
    @Column(name = "aviso_prazo_fechado_em")
    private LocalDateTime avisoPrazoFechadoEm;
```

- [ ] **Step 3: Adicionar o campo em `InscricaoEvento.java`**

Junto dos outros campos anuláveis (perto de `visitanteId`):

```java
    /** V38 — carimbo do PrazoInscricaoJob (dedup do aviso "inscrição incompleta, prazo chegando"). */
    @Column(name = "aviso_prazo_incompleto_em")
    private java.time.LocalDateTime avisoPrazoIncompletoEm;
```

(Se a classe usar `@Getter/@Setter` do Lombok — confirmar no topo — não precisa de getter/setter manual. Se usar builder e o campo for `boolean` com default, seguir o padrão `@Builder.Default` já usado em `Evento`.)

- [ ] **Step 4: Escrever o teste de migration**

`src/test/java/com/domus/api/modules/evento/EventoPrazoMigracaoTest.java`. Procurar primeiro um teste de migration existente pra copiar o cabeçalho exato (`grep -rl "PostgresTestContainerSupport" src/test | grep -i migra`). Padrão esperado:

```java
package com.domus.api.modules.evento;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoPrazoMigracaoTest implements PostgresTestContainerSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    void v38_cria_colunas_de_prazo_no_evento() {
        var colunas = jdbc.queryForList(
            "SELECT column_name, is_nullable, column_default FROM information_schema.columns " +
            "WHERE table_name = 'evento' AND column_name IN " +
            "('inscricoes_ate','permite_cancelar_apos_prazo','aviso_prazo_proximo_em','aviso_prazo_fechado_em')");
        assertThat(colunas).hasSize(4);
        var permiteCancelar = colunas.stream()
            .filter(c -> c.get("column_name").equals("permite_cancelar_apos_prazo")).findFirst().orElseThrow();
        assertThat(permiteCancelar.get("is_nullable")).isEqualTo("NO");
        assertThat(String.valueOf(permiteCancelar.get("column_default"))).contains("true");
    }

    @Test
    void v38_cria_coluna_de_carimbo_na_inscricao() {
        var count = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.columns " +
            "WHERE table_name = 'inscricao_evento' AND column_name = 'aviso_prazo_incompleto_em'", Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
```

- [ ] **Step 5: Rodar o teste — deve passar**

```
docker compose up -d redis elasticsearch    # na raiz do repo (se ainda não estiverem de pé)
set -a; . ./.env; set +a                     # na raiz do repo
cd backend/api && mvn -o test -Dtest=EventoPrazoMigracaoTest
```
Esperado: PASS (2 testes). Se falhar por contexto (ES/Redis/`.env`), resolver o ambiente antes de seguir.

- [ ] **Step 6: Rodar a suíte de evento pra garantir que nada quebrou com as colunas novas**

```
mvn -o test -Dtest='EventoServiceTest,InscricaoServiceTest,EventoPrazoMigracaoTest'
```
Esperado: PASS. (Colunas aditivas nullable + um boolean com default não devem quebrar nada.)

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V38__evento_prazo_inscricao.sql \
        src/main/java/com/domus/api/modules/evento/Evento.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java \
        src/test/java/com/domus/api/modules/evento/EventoPrazoMigracaoTest.java
git commit -m "feat(evento): V38 — colunas de prazo de inscrição e carimbos do job"
```

---

### Task 2: Enum `SituacaoInscricao` + `Evento.getSituacaoInscricao()`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/SituacaoInscricao.java`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java` (importa o enum do subpacote `inscricao` — já há import cruzado nesse sentido? Se gerar ciclo de pacote incômodo, colocar o enum em `com.domus.api.modules.evento` ao lado de `SituacaoEvento`. **Preferir `com.domus.api.modules.evento`** pra ficar junto de `SituacaoEvento` e sem import cruzado.)
- Test: `src/test/java/com/domus/api/modules/evento/EventoSituacaoInscricaoTest.java` (create)

**Decisão:** criar o enum em `src/main/java/com/domus/api/modules/evento/SituacaoInscricao.java` (mesmo pacote de `SituacaoEvento`).

**Interfaces:**
- Produces:
  - `enum SituacaoInscricao { ABERTA, ENCERRADA_POR_PRAZO, ENCERRADA_POR_INICIO }`
  - `Evento.getSituacaoInscricao()` → `SituacaoInscricao`

- [ ] **Step 1: Escrever o teste**

`src/test/java/com/domus/api/modules/evento/EventoSituacaoInscricaoTest.java`:

```java
package com.domus.api.modules.evento;

import com.domus.api.modules.igreja.Igreja;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventoSituacaoInscricaoTest {

    private Evento evento(LocalDateTime inicioEm, LocalDateTime inscricoesAte) {
        return Evento.builder()
                .igreja(new Igreja())
                .titulo("Retiro")
                .inicioEm(inicioEm)
                .inscricoesAte(inscricoesAte)
                .requerInscricao(true)
                .build();
    }

    @Test
    void aberta_quando_nao_ha_prazo_e_evento_no_futuro() {
        var e = evento(LocalDateTime.now().plusDays(10), null);
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ABERTA);
    }

    @Test
    void aberta_quando_prazo_ainda_nao_venceu() {
        var e = evento(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(3));
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ABERTA);
    }

    @Test
    void encerrada_por_prazo_quando_prazo_venceu_e_evento_nao_comecou() {
        var e = evento(LocalDateTime.now().plusDays(10), LocalDateTime.now().minusHours(1));
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_PRAZO);
    }

    @Test
    void encerrada_por_inicio_tem_precedencia_sobre_prazo() {
        // evento já começou (inicioEm no passado) E prazo também no passado
        var e = evento(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(3));
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_INICIO);
    }

    @Test
    void encerrada_por_inicio_quando_evento_comecou_e_nao_ha_prazo() {
        var e = evento(LocalDateTime.now().minusHours(2), null);
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_INICIO);
    }
}
```

- [ ] **Step 2: Rodar — deve falhar**

```
mvn -o test -Dtest=EventoSituacaoInscricaoTest
```
Esperado: FAIL — compilação (`SituacaoInscricao` e `getSituacaoInscricao` não existem).

- [ ] **Step 3: Criar o enum**

`src/main/java/com/domus/api/modules/evento/SituacaoInscricao.java`:

```java
package com.domus.api.modules.evento;

/** Estado do "portão" de inscrição de um evento, DERIVADO de inicioEm/inscricoesAte —
 *  não é coluna. Diz só o estado do prazo; quem pode furá-lo (admin/líder) é decisão do
 *  InscricaoService, e o front cruza isto com a role do usuário logado. */
public enum SituacaoInscricao {
    /** Sem prazo, ou o prazo ainda não venceu, e o evento não começou. */
    ABERTA,
    /** O evento ainda não começou, mas o prazo de inscrição já venceu. */
    ENCERRADA_POR_PRAZO,
    /** O evento já começou/acabou — fechamento automático que já existia. */
    ENCERRADA_POR_INICIO
}
```

- [ ] **Step 4: Implementar `getSituacaoInscricao()` em `Evento.java`**

Logo abaixo de `getSituacao()`:

```java
    /** Ver {@link SituacaoInscricao}. ENCERRADA_POR_INICIO é testado primeiro: como a
     *  validação garante inscricoesAte <= inicioEm, um evento já começado também está
     *  "depois do prazo", mas o estado correto pra exibir é ENCERRADA_POR_INICIO. */
    public SituacaoInscricao getSituacaoInscricao() {
        if (getSituacao() != SituacaoEvento.AGENDADO) {
            return SituacaoInscricao.ENCERRADA_POR_INICIO;
        }
        if (inscricoesAte != null && LocalDateTime.now().isAfter(inscricoesAte)) {
            return SituacaoInscricao.ENCERRADA_POR_PRAZO;
        }
        return SituacaoInscricao.ABERTA;
    }
```

- [ ] **Step 5: Rodar — deve passar**

```
mvn -o test -Dtest=EventoSituacaoInscricaoTest
```
Esperado: PASS (5 testes).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/SituacaoInscricao.java \
        src/main/java/com/domus/api/modules/evento/Evento.java \
        src/test/java/com/domus/api/modules/evento/EventoSituacaoInscricaoTest.java
git commit -m "feat(evento): SituacaoInscricao derivada de inicioEm/inscricoesAte"
```

---

### Task 3: Validação do prazo no `EventoService` + campos no `EventoRequest`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `Evento.setInscricoesAte`, `Evento.setPermiteCancelarAposPrazo` (Task 1)
- Produces:
  - `EventoRequest.inscricoesAte()` → `LocalDateTime` (nullable)
  - `EventoRequest.permiteCancelarAposPrazo()` → `Boolean` (nullable; `null`/`true` = permite)
  - Erros de negócio: `PRAZO_APOS_INICIO`, `PRAZO_SEM_INSCRICAO`
  - `EventoService` grava os dois campos no `cadastrarEvento` e no `atualizarEvento`

- [ ] **Step 1: Adicionar os campos no `EventoRequest.java`**

Junto de `requerInscricao` / `controlaPresenca`:

```java
        /** Prazo de inscrição (V38). {@code null} = aceita até o evento começar. */
        LocalDateTime inscricoesAte,

        /** V38. {@code null} tratado como {@code true}. Só relevante com {@code inscricoesAte} preenchido. */
        Boolean permiteCancelarAposPrazo,
```

- [ ] **Step 2: Escrever os testes de validação no `EventoServiceTest.java`**

Primeiro `grep -n "cadastrarEvento\|private EventoRequest\|requestValido\|EventoRequest(" src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` pra achar o helper que monta um `EventoRequest` válido. Adicionar (ajustando os nomes de helper ao que já existe no arquivo):

```java
    @Test
    void recusaPrazoDepoisDoInicio() {
        var inicio = LocalDateTime.now().plusDays(10);
        var req = requestValido().withInicioEm(inicio).withRequerInscricao(true)
                .withInscricoesAte(inicio.plusDays(1));   // depois do início
        assertThatThrownBy(() -> service.cadastrarEvento(req, igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("antes");
        // Se BusinessException expõe getCodigo(): .extracting("codigo").isEqualTo("PRAZO_APOS_INICIO")
    }

    @Test
    void recusaPrazoSemRequerInscricao() {
        var inicio = LocalDateTime.now().plusDays(10);
        var req = requestValido().withInicioEm(inicio).withRequerInscricao(false)
                .withInscricoesAte(inicio.minusDays(1));
        assertThatThrownBy(() -> service.cadastrarEvento(req, igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inscrição");
    }

    @Test
    void aceitaPrazoAntesDoInicioComInscricaoObrigatoria() {
        var inicio = LocalDateTime.now().plusDays(10);
        var req = requestValido().withInicioEm(inicio).withRequerInscricao(true)
                .withInscricoesAte(inicio.minusDays(2));
        // não lança; assertivas conforme o padrão do arquivo (ex.: verify(eventoRepository).save(...))
        service.cadastrarEvento(req, igrejaId, usuarioId);
    }
```

Se `EventoRequest` não tiver métodos `withX` (record puro sem Lombok `@With`), o arquivo de teste provavelmente já tem um builder/helper próprio — usar esse. Se não houver, montar o `new EventoRequest(...)` completo copiando de um teste vizinho e trocando só os campos relevantes.

- [ ] **Step 3: Rodar — deve falhar**

```
mvn -o test -Dtest=EventoServiceTest
```
Esperado: FAIL (compila, mas os 2 primeiros não lançam nada porque a validação não existe; o `EventoRequest` novo pode nem compilar até o Step 1 estar feito — garantir Step 1 antes).

- [ ] **Step 4: Implementar a validação no `EventoService.java`**

Novo método privado (perto de `validarPreco`):

```java
    /** Prazo de inscrição (V38): se informado, tem que ser antes do início e o evento
     *  precisa exigir inscrição — senão o prazo não significa nada. */
    private void validarPrazoInscricao(EventoRequest data) {
        if (data.inscricoesAte() == null) return;
        if (!Boolean.TRUE.equals(data.requerInscricao())) {
            throw new BusinessException("PRAZO_SEM_INSCRICAO",
                    "O prazo de inscrição só faz sentido com inscrição obrigatória ligada.");
        }
        if (data.inscricoesAte().isAfter(data.inicioEm())) {
            throw new BusinessException("PRAZO_APOS_INICIO",
                    "O prazo de inscrição tem que ser antes do início do evento.");
        }
    }
```

Chamar em `cadastrarEvento` e no `atualizarEvento` de 5 args, junto das outras `validar*` (logo depois de `validarPreco(data);`).

- [ ] **Step 5: Gravar os campos no builder do `cadastrarEvento`**

No `Evento.builder()...`:

```java
                .inscricoesAte(data.inscricoesAte())
                .permiteCancelarAposPrazo(data.permiteCancelarAposPrazo() == null
                        || data.permiteCancelarAposPrazo())
```

- [ ] **Step 6: Gravar os campos no `atualizarEvento`**

Junto dos `evento.setX(data.x())`:

```java
        evento.setInscricoesAte(data.inscricoesAte());
        evento.setPermiteCancelarAposPrazo(data.permiteCancelarAposPrazo() == null
                || data.permiteCancelarAposPrazo());
```

- [ ] **Step 7: Rodar — deve passar**

```
mvn -o test -Dtest=EventoServiceTest
```
Esperado: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): valida prazo de inscrição (antes do início, exige inscrição)"
```

---

### Task 4: Propagação de série — só `permiteCancelarAposPrazo`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java` (`copiarCamposEditaveisPara`, ~linha 428)
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `copiarCamposEditaveisPara(Evento editado, Evento ocorrencia)` (privado)

- [ ] **Step 1: Escrever os testes**

Procurar no `EventoServiceTest` um teste de propagação de série existente (`grep -n "propag\|SERIE\|copiarCampos\|EscopoEdicao" src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`) e copiar o cenário. Adicionar:

```java
    @Test
    void edicaoDeSerieNaoPropagaInscricoesAte() {
        // monta série com 2 ocorrências; edita a 1ª com escopo SERIE, setando inscricoesAte;
        // verifica que a 2ª ocorrência continua com inscricoesAte == null (ou o valor antigo).
        // (usar o mesmo setup de mocks do teste de propagação vizinho)
    }

    @Test
    void edicaoDeSeriePropagaPermiteCancelarAposPrazo() {
        // edita a 1ª ocorrência com escopo SERIE, permiteCancelarAposPrazo = false;
        // verifica que a 2ª ocorrência recebeu permiteCancelarAposPrazo == false.
    }
```

Preencher o corpo seguindo exatamente o teste de propagação que já existe no arquivo (mesmos mocks de `eventoRepository.findBySerieId`, `save`, etc.).

- [ ] **Step 2: Rodar — o teste de propagação deve falhar**

```
mvn -o test -Dtest=EventoServiceTest#edicaoDeSeriePropagaPermiteCancelarAposPrazo
```
Esperado: FAIL — a 2ª ocorrência não recebe o flag.

- [ ] **Step 3: Adicionar 1 linha em `copiarCamposEditaveisPara`**

Junto de `ocorrencia.setExclusivoMembros(editado.isExclusivoMembros());`:

```java
        ocorrencia.setPermiteCancelarAposPrazo(editado.isPermiteCancelarAposPrazo());
```

**NÃO** adicionar `setInscricoesAte` aqui — é data, fica de fora igual `inicioEm` e `foto`.

- [ ] **Step 4: Rodar — deve passar**

```
mvn -o test -Dtest=EventoServiceTest
```
Esperado: PASS (os dois novos + todos os antigos).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): permiteCancelarAposPrazo propaga na série; inscricoesAte não"
```

---

## PEDAÇO 2 — Enforcement + DTOs (backend)

### Task 5: Guarda de prazo na inscrição

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPrazoTest.java` (create)

**Interfaces:**
- Consumes: `Evento.getInscricoesAte()`, `Permissoes.podeGerenciarInscricoes(String)`
- Produces:
  - `private void validarPrazoInscricao(Evento evento, String role)` — lança `BusinessException("PRAZO_INSCRICAO_ENCERRADO", ...)`
  - chamada em `inscreverInterno` (role real), `inscreverPessoas` (role de gestor), `inscreverConvidado` (role derivada de `inscritoPorUsuarioId`)

- [ ] **Step 1: Escrever o arquivo de teste**

`src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPrazoTest.java`. Copiar o cabeçalho, o `@BeforeEach` e os helpers (`igreja()`, `dado()`, `contaPagamentoIgrejaRepositoryComContaConectada()`) de `InscricaoElegibilidadeTest.java` **verbatim** (mesma lista de mocks no construtor do `InscricaoService`). Depois:

```java
    private Evento eventoComPrazo(LocalDateTime inscricoesAte, boolean permiteCancelar) {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Congresso").inicioEm(LocalDateTime.now().plusDays(20))
                .requerInscricao(true)
                .inscricoesAte(inscricoesAte)
                .permiteCancelarAposPrazo(permiteCancelar)
                .build();
    }

    private Pessoa pessoaComum() {
        return Pessoa.builder()
                .id(UUID.randomUUID()).igreja(igreja()).nome("Fulano").email("f@e.com")
                .vinculo(com.domus.api.modules.pessoa.Vinculo.MEMBRO)
                .build();
    }

    @Test
    void recusaAutoInscricaoDeComumDepoisDoPrazo() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        assertThatThrownBy(() -> service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prazo");
        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void permiteAutoInscricaoDeAdminDepoisDoPrazo() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ADMIN_IGREJA", false, igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void permiteAutoInscricaoDeComumAntesDoPrazo() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(5), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void semPrazoInscreveComoHoje() {
        var evento = eventoComPrazo(null, true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void reinscricaoRecusadaDepoisDoPrazoParaComum() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        var cancelada = InscricaoEvento.builder()
                .igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoa.getId()))
                .thenReturn(java.util.Optional.of(cancelada));
        assertThatThrownBy(() -> service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prazo");
    }
```

Verificar o nome exato dos valores de role: `grep -n "ADMIN_IGREJA\|ACESSO_COMUM\|LIDER" src/main/java/com/domus/api/shared/security/Permissoes.java`.

- [ ] **Step 2: Rodar — deve falhar**

```
mvn -o test -Dtest=InscricaoPrazoTest
```
Esperado: FAIL — `recusaAutoInscricaoDeComumDepoisDoPrazo` e `reinscricaoRecusadaDepoisDoPrazoParaComum` não lançam (guarda não existe).

- [ ] **Step 3: Implementar `validarPrazoInscricao`**

Em `InscricaoService.java`, perto de `validarEventoAberto` (~linha 334):

```java
    /** Prazo de inscrição (V38). NULL = sem prazo. Admin/líder passam — pergunta pela
     *  CAPACIDADE (podeGerenciarInscricoes), não pelo nome do perfil. O caminho público
     *  não tem role (role == null), então nunca passa. */
    private void validarPrazoInscricao(Evento evento, String role) {
        if (evento.getInscricoesAte() == null) return;
        if (!java.time.LocalDateTime.now().isAfter(evento.getInscricoesAte())) return;
        if (Permissoes.podeGerenciarInscricoes(role)) return;
        throw new BusinessException("PRAZO_INSCRICAO_ENCERRADO",
                "O prazo de inscrição neste evento encerrou em "
                + evento.getInscricoesAte().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")) + ".");
    }
```

- [ ] **Step 4: Chamar nos 3 pontos**

1. `inscreverInterno` (~linha 139): logo depois de `validarEventoAberto(evento);` adicionar:
   ```java
   validarPrazoInscricao(evento, role);
   ```
2. `inscreverPessoas` (~linha 277): depois de `validarEventoAberto(evento);` adicionar:
   ```java
   validarPrazoInscricao(evento, role);
   ```
   (aqui `role` é sempre de gestor — a guarda passa; a chamada fica pra consistência/clareza e cobre o caso de a rota ser reconfigurada.)
3. `inscreverConvidado` (~linha 477): depois de `validarEventoAberto(evento);` adicionar:
   ```java
   // inscritoPorUsuarioId != null => veio de um gestor autenticado (endpoint /inscricoes/convidados,
   // que já exige ADMIN/LIDER); null => link público. Derivamos a "role" pra guarda a partir disso.
   validarPrazoInscricao(evento, inscritoPorUsuarioId != null ? "ADMIN_IGREJA" : null);
   ```
   Adicionar um comentário curto explicando o mapeamento (é a única checagem que não recebe `role` de verdade).

- [ ] **Step 5: Rodar — deve passar**

```
mvn -o test -Dtest='InscricaoPrazoTest,InscricaoServiceTest,InscricaoElegibilidadeTest'
```
Esperado: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPrazoTest.java
git commit -m "feat(inscricao): barra inscrição fora do prazo (comum e link público; gestor passa)"
```

---

### Task 6: Guarda de cancelamento

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java` (`cancelar`, ~linha 549)
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPrazoTest.java`

**Interfaces:**
- Consumes: `Evento.getInscricoesAte()`, `Evento.isPermiteCancelarAposPrazo()`
- Produces: `private void validarCancelamentoPermitido(Evento evento, boolean souEu, boolean ehGestor)` → lança `BusinessException("CANCELAMENTO_ENCERRADO_POR_PRAZO", ...)`

- [ ] **Step 1: Escrever os testes**

Adicionar em `InscricaoPrazoTest.java`. Verificar a assinatura de `cancelar` (`grep -n "public void cancelar" src/main/java/.../InscricaoService.java`) e como um teste existente exercita cancelamento (`grep -ln "\.cancelar(" src/test/java/com/domus/api/modules/evento/inscricao/*.java`). Padrão:

```java
    @Test
    void cancelamentoBloqueadoQuandoToggleDesligadoEPrazoVencidoParaComum() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), false); // não permite cancelar
        var pessoa = pessoaComum();
        var inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(inscricao.getId(), igrejaId))   // conferir o nome real
                .thenReturn(java.util.Optional.of(inscricao));
        assertThatThrownBy(() -> service.cancelar(inscricao.getId(), UUID.randomUUID(), pessoa.getId(),
                "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("prazo");
    }

    @Test
    void cancelamentoLivreQuandoToggleLigado() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true); // permite cancelar
        // ... mesmo setup, cancelar não lança
    }

    @Test
    void gestorCancelaMesmoComToggleDesligado() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), false);
        // ... cancelar com role "ADMIN_IGREJA" não lança
    }
```

Ajustar o nome do finder de inscrição (`buscarInscricao` chama qual método do repo?) — `grep -n "private .* buscarInscricao" -A5 src/main/java/.../InscricaoService.java`.

- [ ] **Step 2: Rodar — deve falhar**

```
mvn -o test -Dtest=InscricaoPrazoTest
```
Esperado: FAIL — `cancelamentoBloqueado...` não lança.

- [ ] **Step 3: Implementar**

```java
    /** Depois do prazo, o auto-cancelamento respeita o toggle do evento. Gestor sempre
     *  passa (remove alguém da lista pela gestão de inscritos). */
    private void validarCancelamentoPermitido(Evento evento, boolean souEu, boolean ehGestor) {
        if (ehGestor) return;
        if (!souEu) return; // outro erro (SEM_PERMISSAO) já barra esse caso
        if (evento.getInscricoesAte() == null) return;
        if (evento.isPermiteCancelarAposPrazo()) return;
        if (!java.time.LocalDateTime.now().isAfter(evento.getInscricoesAte())) return;
        throw new BusinessException("CANCELAMENTO_ENCERRADO_POR_PRAZO",
                "Depois do prazo de inscrição não dá mais pra cancelar sua inscrição neste evento. "
                + "Fale com a organização.");
    }
```

Chamar em `cancelar`, logo depois de calcular `souEu` / `gestorDaMesmaIgreja` e ANTES de `cancelarInterno(inscricao)`:

```java
        validarCancelamentoPermitido(inscricao.getEvento(), souEu, ehGestor);
```

**Não** chamar em `cancelarPorCobranca` nem no caminho de `CobrancaEventoExpiracaoJob` — é o sistema cancelando.

- [ ] **Step 4: Rodar — deve passar**

```
mvn -o test -Dtest='InscricaoPrazoTest,InscricaoServiceTest'
```
Esperado: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPrazoTest.java
git commit -m "feat(inscricao): cancelamento respeita permiteCancelarAposPrazo (gestor sempre passa)"
```

---

### Task 7: Campos no `EventoResponse` + `EventoRequest` já feito

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` (ou um teste de DTO se existir)

**Interfaces:**
- Produces: `EventoResponse` ganha `LocalDateTime inscricoesAte`, `boolean permiteCancelarAposPrazo`, `SituacaoInscricao situacaoInscricao`

- [ ] **Step 1: Adicionar os 3 campos no record `EventoResponse`**

Depois de `divergeDaSerie`:

```java
        boolean divergeDaSerie,
        LocalDateTime inscricoesAte,
        boolean permiteCancelarAposPrazo,
        com.domus.api.modules.evento.SituacaoInscricao situacaoInscricao
```

- [ ] **Step 2: Preencher nos dois `from(...)`**

No `from(Evento e, Integer inscricoesRemovidas, UUID minhaIgrejaId, boolean podeGerenciar)`, no fim do `new EventoResponse(...)`:

```java
                e.isDivergeDaSerie(),
                e.getInscricoesAte(),
                e.isPermiteCancelarAposPrazo(),
                e.getSituacaoInscricao()
```

- [ ] **Step 3: Compilar + rodar a suíte de evento**

```
mvn -o test -Dtest='EventoServiceTest,EventoControllerTest'
```
Esperado: PASS. Se `EventoControllerTest` não existe, rodar `mvn -o test-compile` pra garantir que todos os call-sites de `new EventoResponse(...)` foram atualizados (só há os 2 `from` estáticos — o compilador acusa se faltar).

- [ ] **Step 4: Adicionar um teste de que `situacaoInscricao` sai no response**

No `EventoServiceTest`, no teste que já verifica o retorno de `cadastrarEvento`/`buscarEvento` (ou criar um):

```java
    @Test
    void responseTrazSituacaoInscricaoEncerradaPorPrazo() {
        var inicio = LocalDateTime.now().plusDays(10);
        // cadastra com inscricoesAte no passado (evento antigo editado) OU
        // monta o Evento direto e chama EventoResponse.from(...) e verifica:
        // assertThat(resp.situacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_PRAZO);
    }
```

- [ ] **Step 5: Rodar — deve passar**

```
mvn -o test -Dtest=EventoServiceTest
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): EventoResponse expõe inscricoesAte, permiteCancelarAposPrazo, situacaoInscricao"
```

---

### Task 8: `situacaoInscricao` no convite público + teste de controller do prazo

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/convite/DTOs/ConvitePublicoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/convite/ConviteController.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoControllerTest.java` (adicionar 1 caso)

**Interfaces:**
- Produces: `ConvitePublicoResponse` ganha `com.domus.api.modules.evento.SituacaoInscricao situacaoInscricao` no fim.

- [ ] **Step 1: Adicionar o campo no record**

```java
        boolean requerInscricao,
        com.domus.api.modules.evento.SituacaoInscricao situacaoInscricao
```

- [ ] **Step 2: Preencher no `ConviteController.consultar`**

No `new ConvitePublicoResponse(...)`, adicionar como último argumento:

```java
                evento.isRequerInscricao(), evento.getSituacaoInscricao()
```

- [ ] **Step 3: Teste de controller do prazo em `InscricaoControllerTest.java`**

Procurar o padrão exato (`@SpringBootTest`? `AutenticacaoTestSupport`? `@Sql`?) no arquivo. Adicionar um caso que:
1. cria igreja + pessoa comum + usuário + evento com `inscricoes_ate` no passado (via `@Sql` ou repositório);
2. `POST /eventos/{id}/inscricoes` autenticado como `ACESSO_COMUM`;
3. espera `400` com corpo `error == "PRAZO_INSCRICAO_ENCERRADO"`.

Se `InscricaoControllerTest` ainda não usa o harness de autenticação, seguir o padrão de `VisitanteControllerTest`/`CobrancaControllerTest` (o mais próximo). Se for grande demais pra esta task, criar `InscricaoPrazoControllerTest.java` só com esse caso.

- [ ] **Step 4: Rodar**

```
mvn -o test -Dtest='InscricaoControllerTest,ConviteControllerTest'
```
Esperado: PASS. (`ConviteControllerTest` se existir — senão só compilar.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/convite/ \
        src/test/java/com/domus/api/modules/evento/inscricao/
git commit -m "feat(convite): expõe situacaoInscricao; teste de 400 PRAZO_INSCRICAO_ENCERRADO"
```

---

## PEDAÇO 3 — Frontend do fluxo principal

> Sem infra de teste no front (dívida conhecida). Cada task termina com `npm run lint && npm run build` (a partir de `frontend/`) verde + validação manual descrita.

### Task 9: Tipos + schema + form de evento

**Files:**
- Modify: `src/types/evento.type.ts`
- Modify: `src/lib/validators.ts`
- Modify: `src/hooks/evento/useEventoForm.ts`
- Modify: `src/components/module/eventos/EventoForm.tsx`
- Modify: `src/components/module/eventos/EventoForm.module.css` (se precisar de estilo pro aviso/checkbox)

**Interfaces:**
- Produces:
  - `type SituacaoInscricao = 'ABERTA' | 'ENCERRADA_POR_PRAZO' | 'ENCERRADA_POR_INICIO'`
  - `EventoResponse` ganha `inscricoesAte: string | null`, `permiteCancelarAposPrazo: boolean`, `situacaoInscricao: SituacaoInscricao`
  - `EventoRequest` ganha `inscricoesAte?: string | null`, `permiteCancelarAposPrazo?: boolean`
  - form fields: `inscricoesAte` (string `''` ou `YYYY-MM-DDTHH:mm`), `permiteCancelarAposPrazo` (boolean, default `true`)

- [ ] **Step 1: Tipos em `src/types/evento.type.ts`**

```ts
export type SituacaoInscricao = 'ABERTA' | 'ENCERRADA_POR_PRAZO' | 'ENCERRADA_POR_INICIO'
```

Em `EventoResponse` (depois de `divergeDaSerie`):
```ts
  inscricoesAte: string | null
  permiteCancelarAposPrazo: boolean
  situacaoInscricao: SituacaoInscricao
```

Em `EventoRequest` (perto de `requerInscricao?`):
```ts
  inscricoesAte?: string | null
  permiteCancelarAposPrazo?: boolean
```

- [ ] **Step 2: Schema em `src/lib/validators.ts`**

Em `eventoSchemaBase`, junto de `requerInscricao`:
```ts
  inscricoesAte: opcional(
    z.string().regex(/^\d{4}-\d{2}-\d{2}T([01]\d|2[0-3]):[0-5]\d$/, 'Data e hora inválidas.'),
  ),
  permiteCancelarAposPrazo: z.boolean().default(true),
```

Novo `.refine` na cadeia de `eventoSchema` (depois do refine de idade):
```ts
).refine(
  (data) => {
    if (!data.inscricoesAte) return true
    const inicio = new Date(`${data.inicioData}T${data.inicioHora}`)
    const prazo = new Date(data.inscricoesAte)
    if (isNaN(inicio.getTime()) || isNaN(prazo.getTime())) return true
    return prazo <= inicio
  },
  { message: 'O prazo tem que ser antes do início do evento.', path: ['inscricoesAte'] }
```

- [ ] **Step 3: Reidratar no `useEventoForm.ts`**

No objeto do `reset({...})` de edição (perto de `requerInscricao: eventoInicial.requerInscricao`):
```ts
        inscricoesAte: eventoInicial.inscricoesAte
          ? eventoInicial.inscricoesAte.slice(0, 16)   // ISO -> "YYYY-MM-DDTHH:mm"
          : '',
        permiteCancelarAposPrazo: eventoInicial.permiteCancelarAposPrazo ?? true,
```

E no default do cadastro novo (perto de `requerInscricao: false`):
```ts
        inscricoesAte: '',
        permiteCancelarAposPrazo: true,
```

No mapeamento pro request (`toRequest` / objeto `payload`, perto de `requerInscricao: data.requerInscricao`):
```ts
        inscricoesAte: (data.requerInscricao && data.inscricoesAte)
          ? data.inscricoesAte
          : null,
        permiteCancelarAposPrazo: data.permiteCancelarAposPrazo,
```

- [ ] **Step 4: Campos no `EventoForm.tsx`**

Dentro do bloco `{requerInscricao && (<Revelar className={styles.campos}>...</Revelar>)}` (perto de vagas/preço), adicionar:

```tsx
<div className={styles.campo}>
  <label htmlFor="inscricoesAte">Inscrições até (opcional)</label>
  <input
    id="inscricoesAte"
    type="datetime-local"
    {...register('inscricoesAte')}
    aria-describedby="inscricoesAte-ajuda"
  />
  <p id="inscricoesAte-ajuda" className={styles.ajuda}>
    Ex.: 15/03/2026 23:59 — deixe vazio pra aceitar inscrições até o evento começar.
  </p>
  {errors.inscricoesAte && <span className={styles.erro}>{errors.inscricoesAte.message}</span>}
</div>

<Revelar>
  {watch('inscricoesAte') ? (
    <label className={styles.checkboxLinha}>
      <input type="checkbox" {...register('permiteCancelarAposPrazo')} />
      <span>
        Permitir cancelamento após o prazo
        <small>Desmarque para travar a lista de inscritos no prazo — ninguém entra nem sai depois.</small>
      </span>
    </label>
  ) : null}
</Revelar>
```

Usar as classes CSS que já existem no `EventoForm.module.css` pra campos análogos (checar `styles.campo`, `styles.ajuda`, `styles.erro`, e o padrão de checkbox/switch já usado pra `exclusivoMembros`). Se precisar de `styles.checkboxLinha`, copiar do estilo de checkbox mais próximo.

- [ ] **Step 5: Lint + build**

```
cd frontend && npm run lint && npm run build
```
Esperado: sem erros.

- [ ] **Step 6: Validação manual**

`npm run dev`, criar evento com inscrição obrigatória:
- preencher "Inscrições até" com data DEPOIS do início → erro Zod "O prazo tem que ser antes...".
- preencher com data ANTES → o checkbox "Permitir cancelamento após o prazo" aparece com animação.
- salvar; reabrir a edição → campo e checkbox reidratados.
- mobile (viewport iPhone + Android): campo não estoura, checkbox legível.

- [ ] **Step 7: Commit**

```bash
git add src/types/evento.type.ts src/lib/validators.ts src/hooks/evento/useEventoForm.ts \
        src/components/module/eventos/EventoForm.tsx src/components/module/eventos/EventoForm.module.css
git commit -m "feat(evento): campo 'inscrições até' e toggle de cancelamento no form"
```

---

### Task 10: Selo de prazo no card

**Files:**
- Create: `src/components/module/eventos/SeloPrazoInscricao.tsx` + `.module.css`
- Modify: `src/components/module/eventos/EventoCard.tsx`

**Interfaces:**
- Consumes: `EventoResponse.inscricoesAte`, `EventoResponse.situacaoInscricao`
- Produces: `<SeloPrazoInscricao evento={evento} />` — renderiza `null` quando `inscricoesAte == null`

- [ ] **Step 1: Criar `SeloPrazoInscricao.tsx`**

```tsx
'use client'

import { CalendarClock } from 'lucide-react'
import type { EventoResponse } from '@/types/evento.type'
import styles from './SeloPrazoInscricao.module.css'

const DIAS_DESTAQUE = 3

function formatarData(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

export function SeloPrazoInscricao({ evento }: { evento: EventoResponse }) {
  if (!evento.inscricoesAte || evento.situacaoInscricao === 'ENCERRADA_POR_INICIO') return null

  const data = formatarData(evento.inscricoesAte)

  if (evento.situacaoInscricao === 'ENCERRADA_POR_PRAZO') {
    return <span className={styles.encerrado}><CalendarClock size={12} /> Inscrições encerradas</span>
  }

  const diasRestantes = Math.ceil(
    (new Date(evento.inscricoesAte).getTime() - Date.now()) / 86_400_000,
  )
  const destaque = diasRestantes <= DIAS_DESTAQUE
  return (
    <span className={destaque ? styles.destaque : styles.neutro}>
      <CalendarClock size={12} />
      Inscrições até {data}
      {destaque && diasRestantes >= 0 && ` · faltam ${diasRestantes} dia${diasRestantes === 1 ? '' : 's'}`}
    </span>
  )
}
```

- [ ] **Step 2: Criar `SeloPrazoInscricao.module.css`**

Copiar as regras base dos selos existentes (`SelosInscricaoCard.module.css` — `.seloInscrito`, `.seloPendencia`) pra manter o mesmo tamanho/tipografia. Três variantes: `.neutro` (cinza), `.destaque` (âmbar), `.encerrado` (apagado). Incluir `@media (max-width: 767px)` se os selos vizinhos tiverem.

- [ ] **Step 3: Usar no `EventoCard.tsx`**

Perto do `<SelosInscricaoCard ... />` (linha ~115):
```tsx
<SeloPrazoInscricao evento={evento} />
```
Confirmar que fica dentro do mesmo container de selos e não quebra o layout.

- [ ] **Step 4: Lint + build**

```
cd frontend && npm run lint && npm run build
```

- [ ] **Step 5: Validação manual**

- evento com prazo daqui a 10 dias → selo neutro "Inscrições até DD/MM".
- prazo daqui a 2 dias → selo âmbar "· faltam 2 dias".
- prazo no passado (evento futuro) → "Inscrições encerradas".
- evento sem prazo → nenhum selo novo.
- mobile: card não estoura horizontalmente.

- [ ] **Step 6: Commit**

```bash
git add src/components/module/eventos/SeloPrazoInscricao.tsx \
        src/components/module/eventos/SeloPrazoInscricao.module.css \
        src/components/module/eventos/EventoCard.tsx
git commit -m "feat(evento): selo de prazo de inscrição no card (neutro/destaque/encerrado)"
```

---

### Task 11: Drawer de detalhe — linha do prazo + botão desabilitado/aviso

**Files:**
- Modify: `src/components/module/eventos/BotaoConfirmarPresenca.tsx`
- Modify: `src/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento.tsx`
- Modify: `.module.css` correspondentes

**Interfaces:**
- Consumes: `EventoResponse.situacaoInscricao`, `EventoResponse.inscricoesAte`; `role` de `useAuthStore`; `@/lib/permissoes`
- `BotaoConfirmarPresenca` ganha props: `situacaoInscricao: SituacaoInscricao`, `inscricoesAte: string | null`

- [ ] **Step 1: Helper de rótulo de perfil**

Verificar se já existe em `@/lib/permissoes` algo como `rotuloPerfil(role)`. Se não, adicionar:
```ts
export function rotuloPerfil(r: Role | null | undefined): string {
  if (r === 'ADMIN_IGREJA') return 'administrador'
  if (r === 'LIDER') return 'líder'
  return 'membro'
}
export function podeGerenciarInscricoes(r: Role | null | undefined): boolean {
  return r === 'ADMIN_IGREJA' || r === 'LIDER'
}
```
(Conferir os literais de `Role` no projeto — `grep -n "type Role" src/`.)

- [ ] **Step 2: `BotaoConfirmarPresenca.tsx` — estado de prazo**

Adicionar às `Props`:
```ts
  situacaoInscricao: SituacaoInscricao
  inscricoesAte: string | null
```

No corpo, antes do return principal:
```tsx
const role = useAuthStore((s) => s.role)
const podeFurarPrazo = podeGerenciarInscricoes(role)
const encerradoPorPrazo = situacaoInscricao === 'ENCERRADA_POR_PRAZO'
const dataPrazo = inscricoesAte
  ? new Date(inscricoesAte).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
  : ''
```

Quando `encerradoPorPrazo && !podeFurarPrazo`: renderizar o botão desabilitado com texto "Inscrições encerradas em {dataPrazo}" (seguir o padrão do bloco `<button disabled>` que já existe nas linhas ~171/~322).

Quando `encerradoPorPrazo && podeFurarPrazo`: renderizar um aviso acima do botão normal (que segue ativo):
```tsx
{encerradoPorPrazo && podeFurarPrazo && (
  <Transicao modo="fade">
    <p className={styles.avisoPrazo}>
      O prazo de inscrição encerrou em {dataPrazo}, mas você como {rotuloPerfil(role)} pode inscrever assim mesmo.
    </p>
  </Transicao>
)}
```

- [ ] **Step 3: `DrawerDetalheEvento.tsx` — linha do prazo + passar props**

Passar as props novas ao `<BotaoConfirmarPresenca .../>` (linha ~320):
```tsx
situacaoInscricao={evento.situacaoInscricao}
inscricoesAte={evento.inscricoesAte}
```

Adicionar uma linha na área de infos do evento (onde já se mostram data/local), só quando `evento.inscricoesAte`:
```tsx
{evento.inscricoesAte && (
  <div className={styles.infoLinha}>
    <CalendarClock size={16} />
    <span>
      Inscrições até {new Date(evento.inscricoesAte).toLocaleString('pt-BR', {
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
      })}
      {evento.situacaoInscricao === 'ENCERRADA_POR_PRAZO' && ' · encerradas'}
    </span>
  </div>
)}
```
(Usar as classes de "linha de info" que o drawer já usa pra data/local.)

- [ ] **Step 4: Lint + build**

```
cd frontend && npm run lint && npm run build
```

- [ ] **Step 5: Validação manual**

Com evento cujo prazo já venceu (evento futuro):
- logado como comum → botão "Se inscrever" desabilitado, "Inscrições encerradas em DD/MM".
- logado como admin/líder → aviso âmbar em cima, botão ativo, inscrição funciona.
- linha "Inscrições até ..." aparece no detalhe.
- evento sem prazo → nada muda.
- mobile: aviso e linha não estouram.

- [ ] **Step 6: Commit**

```bash
git add src/components/module/eventos/BotaoConfirmarPresenca.tsx \
        src/app/\(app\)/eventos/\(lista\)/\(detalhe\)/DrawerDetalheEvento.tsx \
        src/lib/permissoes.ts \
        src/components/module/eventos/*.module.css
git commit -m "feat(evento): drawer mostra prazo; botão desabilitado p/ comum, aviso p/ gestor"
```

---

### Task 12: Aviso nos modais de gestão + mapa de erros de API

**Files:**
- Modify: `src/components/module/eventos/ModalInscreverAlguem.tsx`
- Modify: `src/components/module/eventos/ModalInscreverPessoas.tsx`
- Modify: o mapa de erros de API (localizar: `grep -rn "JA_INSCRITO\|EXCLUSIVO_MEMBROS\|VAGAS_ESGOTADAS" src/ | grep -v test`)

**Interfaces:**
- Consumes: `evento.situacaoInscricao`

- [ ] **Step 1: Aviso no topo dos modais**

Nos dois modais, quando `evento?.situacaoInscricao === 'ENCERRADA_POR_PRAZO'`, renderizar um bloco de aviso no topo do corpo:
```tsx
{evento?.situacaoInscricao === 'ENCERRADA_POR_PRAZO' && (
  <div className={styles.avisoPrazo}>
    O prazo de inscrição deste evento já encerrou. Como {rotuloPerfil(role)}, você ainda pode inscrever.
  </div>
)}
```
Verificar se esses modais já recebem o `evento` inteiro como prop; se não, passar `situacaoInscricao` do `DrawerDetalheEvento`.

- [ ] **Step 2: Mensagens dos erros novos**

No mapa de erros (objeto tipo `{ CODIGO: 'mensagem amigável' }`), adicionar:
```ts
PRAZO_INSCRICAO_ENCERRADO: 'O prazo de inscrição deste evento já encerrou.',
CANCELAMENTO_ENCERRADO_POR_PRAZO: 'Depois do prazo não é mais possível cancelar sua inscrição. Fale com a organização.',
PRAZO_APOS_INICIO: 'O prazo de inscrição tem que ser antes do início do evento.',
PRAZO_SEM_INSCRICAO: 'O prazo de inscrição só vale para eventos com inscrição obrigatória.',
```
Se não houver mapa central e o tratamento for caso-a-caso via `notificar(err)`, garantir que o `notificar` já usa a `message` da API (a maioria do projeto usa). Nesse caso, só confirmar que os `BusinessException` do back têm mensagens boas (têm — foram escritas na Task 5/6/3) e não fazer nada aqui além dos modais.

- [ ] **Step 3: Lint + build**

```
cd frontend && npm run lint && npm run build
```

- [ ] **Step 4: Validação manual**

Admin abre "Inscrever alguém" num evento com prazo vencido → aviso no topo, fluxo funciona.
Comum tenta se inscrever fora do prazo (forçando via cURL ou botão que escapou) → `notificar` mostra "O prazo de inscrição deste evento já encerrou."

- [ ] **Step 5: Commit**

```bash
git add src/components/module/eventos/ModalInscrever*.tsx src/lib/  # ajustar aos arquivos tocados
git commit -m "feat(evento): aviso de prazo nos modais de gestão + mensagens dos erros novos"
```

---

## PEDAÇO 4 — Convite público

### Task 13: Estado "inscrições encerradas" no convite público

**Files:**
- Modify: `src/types/convite.type.ts`
- Modify: `src/app/convite/[token]/page.tsx`
- Modify: `.module.css` da página do convite

**Interfaces:**
- Consumes: `ConvitePublicoResponse.situacaoInscricao` (backend Task 8)
- `ConvitePublico` (tipo front) ganha `situacaoInscricao: SituacaoInscricao`

- [ ] **Step 1: Tipo**

Em `src/types/convite.type.ts`, no `interface ConvitePublico`:
```ts
  situacaoInscricao: 'ABERTA' | 'ENCERRADA_POR_PRAZO' | 'ENCERRADA_POR_INICIO'
```
(ou importar `SituacaoInscricao` de `evento.type` se não gerar ciclo.)

- [ ] **Step 2: Estado na página**

Em `src/app/convite/[token]/page.tsx`, depois do bloco `if (!convite) return null` e antes de renderizar a landing/formulário:
```tsx
if (convite.situacaoInscricao === 'ENCERRADA_POR_PRAZO') {
  const prazo = convite.inscricoesAte
    ? new Date(convite.inscricoesAte).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
    : ''
  return (
    <div className={styles.pagina}>
      <div className={styles.erroCard}>
        <h1>Inscrições encerradas</h1>
        <p>As inscrições para &quot;{convite.titulo}&quot; encerraram{prazo && ` em ${prazo}`}.</p>
      </div>
    </div>
  )
}
```
Se o `ConvitePublicoResponse` do back não devolve `inscricoesAte` hoje (não devolve — ver DTO), adicionar `LocalDateTime inscricoesAte` ao record e ao `consultar()` do `ConviteController` nesta task (1 linha cada), OU só omitir a data na mensagem ("As inscrições ... encerraram."). **Decisão: incluir `inscricoesAte` no DTO** — é barato e a mensagem fica melhor. Ajustar Task 8 mentalmente ou fazer aqui.

- [ ] **Step 3: Envolver em `<Transicao modo="fade">`**

Seguir o padrão do projeto pros blocos que aparecem.

- [ ] **Step 4: Lint + build**

```
cd frontend && npm run lint && npm run build
```

- [ ] **Step 5: Validação manual**

Gerar um link de convite (`/convite/{token}`) pra um evento com prazo vencido → página mostra "Inscrições encerradas", sem formulário. Evento com prazo aberto → fluxo normal. Mobile.

- [ ] **Step 6: Commit**

```bash
git add src/types/convite.type.ts src/app/convite/\[token\]/ \
        ../backend/api/src/main/java/com/domus/api/modules/evento/convite/
git commit -m "feat(convite): link público mostra 'inscrições encerradas' quando o prazo venceu"
```

---

## PEDAÇO 5 — Job de notificação

### Task 14: 3 valores em `TipoNotificacao` + queries do job

**Files:**
- Modify: `src/main/java/com/domus/api/modules/notificacao/TipoNotificacao.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
- Test: coberto pela Task 15 (queries testadas via o job)

**Interfaces:**
- Produces:
  - `TipoNotificacao.PRAZO_INSCRICAO_INCOMPLETA`, `PRAZO_INSCRICAO_PROXIMO`, `PRAZO_INSCRICAO_FECHADO`
  - `EventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime agora)` → `List<Evento>` — eventos não deletados, `requer_inscricao = true`, `inscricoes_ate IS NOT NULL`, `inicio_em > agora`
  - `InscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(UUID eventoId)` → `List<InscricaoEvento>` — status `AGUARDANDO_PAGAMENTO` e `aviso_prazo_incompleto_em IS NULL`

- [ ] **Step 1: Enum**

Adicionar no fim de `TipoNotificacao`:
```java
    PRAZO_INSCRICAO_INCOMPLETA,
    PRAZO_INSCRICAO_PROXIMO,
    PRAZO_INSCRICAO_FECHADO
```

- [ ] **Step 2: Query em `EventoRepository`**

```java
    @Query("""
        SELECT e FROM Evento e
        WHERE e.requerInscricao = true
          AND e.inscricoesAte IS NOT NULL
          AND e.inicioEm > :agora
        """)
    List<Evento> buscarComPrazoAtivoParaJob(@Param("agora") LocalDateTime agora);
```
(O `@SQLRestriction("deleted_at IS NULL")` da entidade já exclui os arquivados.)

- [ ] **Step 3: Query em `InscricaoRepository`**

```java
    @Query("""
        SELECT i FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.AGUARDANDO_PAGAMENTO
          AND i.avisoPrazoIncompletoEm IS NULL
        """)
    List<InscricaoEvento> buscarAguardandoPagamentoSemAvisoDePrazo(@Param("eventoId") UUID eventoId);
```
Confirmar o nome do enum de status (`StatusInscricao.AGUARDANDO_PAGAMENTO`) e se `InscricaoEvento` mapeia `evento` como `@ManyToOne` (mapeia).

- [ ] **Step 4: Compilar**

```
mvn -o test-compile
```
Esperado: OK.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/notificacao/TipoNotificacao.java \
        src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java
git commit -m "feat(notificacao): tipos de prazo de inscrição + queries do job"
```

---

### Task 15: `PrazoInscricaoJob`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJob.java`
- Create: `src/test/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJobTest.java`
- Modify: `src/main/resources/application.properties` (config `app.eventos.aviso-prazo-dias`)

**Interfaces:**
- Consumes: `EventoRepository.buscarComPrazoAtivoParaJob`, `InscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo`, `NotificacaoService.criar(TipoNotificacao, UUID igrejaId, UUID usuarioDestinatarioId, String texto, String link)`, `UsuarioRepository.findByPessoaId(UUID)`
- Produces: `PrazoInscricaoJob.executar()` — `@Scheduled(cron = "0 30 6 * * *")`

- [ ] **Step 1: Escrever o teste primeiro**

`src/test/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJobTest.java` — Mockito puro:

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.EventoResponsavel;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PrazoInscricaoJobTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    UsuarioRepository usuarioRepository;
    NotificacaoService notificacaoService;
    PrazoInscricaoJob job;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        job = new PrazoInscricaoJob(eventoRepository, inscricaoRepository, usuarioRepository, notificacaoService);
        // dias de antecedência do aviso "chegando"
        org.springframework.test.util.ReflectionTestUtils.setField(job, "avisoPrazoDias", 3);
        when(eventoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Igreja igreja() { var i = new Igreja(); i.setId(igrejaId); return i; }

    private Evento eventoComPrazo(LocalDateTime inscricoesAte) {
        return Evento.builder()
                .id(UUID.randomUUID()).igreja(igreja())
                .titulo("Congresso").inicioEm(LocalDateTime.now().plusDays(30))
                .requerInscricao(true).inscricoesAte(inscricoesAte)
                .build();
    }

    private EventoResponsavel responsavelComLogin(Evento evento, UUID usuarioId) {
        var pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Resp").build();
        var usuario = Usuario.builder().id(usuarioId).build();  // conferir builder de Usuario
        when(usuarioRepository.findByPessoaId(pessoa.getId())).thenReturn(Optional.of(usuario));
        return EventoResponsavel.builder().evento(evento).igreja(igreja()).pessoa(pessoa).build();
    }

    @Test
    void avisaResponsavelQuandoPrazoFechouUmaVezSo() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(2));
        var usuarioId = UUID.randomUUID();
        evento.getResponsaveis().add(responsavelComLogin(evento, usuarioId));
        when(eventoRepository.buscarComPrazoAtivoParaJob(any())).thenReturn(List.of(evento));
        when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId())).thenReturn(List.of());

        job.executar();
        job.executar(); // 2ª vez não repete

        verify(notificacaoService, times(1)).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_FECHADO),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
    }

    @Test
    void avisaPrazoProximoDentroDaJanela() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(2)); // dentro de 3 dias
        var usuarioId = UUID.randomUUID();
        evento.getResponsaveis().add(responsavelComLogin(evento, usuarioId));
        when(eventoRepository.buscarComPrazoAtivoParaJob(any())).thenReturn(List.of(evento));
        when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId())).thenReturn(List.of());

        job.executar();

        verify(notificacaoService).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_PROXIMO),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
        verify(notificacaoService, never()).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_FECHADO),
                any(), any(), any(), any());
    }

    @Test
    void naoAvisaPrazoProximoForaDaJanela() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(10)); // fora de 3 dias
        var usuarioId = UUID.randomUUID();
        evento.getResponsaveis().add(responsavelComLogin(evento, usuarioId));
        when(eventoRepository.buscarComPrazoAtivoParaJob(any())).thenReturn(List.of(evento));
        when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId())).thenReturn(List.of());

        job.executar();

        verifyNoInteractions(notificacaoService);
    }

    @Test
    void avisaInscricaoIncompletaUmaVezSo() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(2));
        var pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Fulano").build();
        var usuarioId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoa.getId()))
                .thenReturn(Optional.of(Usuario.builder().id(usuarioId).build()));
        var inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(eventoRepository.buscarComPrazoAtivoParaJob(any())).thenReturn(List.of(evento));
        when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId()))
                .thenReturn(List.of(inscricao)).thenReturn(List.of()); // 2ª chamada já carimbada

        job.executar();
        job.executar();

        verify(notificacaoService, times(1)).criar(eq(TipoNotificacao.PRAZO_INSCRICAO_INCOMPLETA),
                eq(igrejaId), eq(usuarioId), anyString(), anyString());
    }

    @Test
    void pulaResponsavelSemLogin() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1));
        var pessoaSemLogin = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("X").build();
        when(usuarioRepository.findByPessoaId(pessoaSemLogin.getId())).thenReturn(Optional.empty());
        evento.getResponsaveis().add(EventoResponsavel.builder()
                .evento(evento).igreja(igreja()).pessoa(pessoaSemLogin).build());
        when(eventoRepository.buscarComPrazoAtivoParaJob(any())).thenReturn(List.of(evento));
        when(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId())).thenReturn(List.of());

        job.executar();

        verifyNoInteractions(notificacaoService);
    }
```

Conferir os builders de `Usuario` e `Pessoa` (têm `@Builder`? `Usuario.builder().id(...)` funciona?). Ajustar se necessário (talvez `new Usuario()` + `setId`).

- [ ] **Step 2: Rodar — deve falhar**

```
mvn -o test -Dtest=PrazoInscricaoJobTest
```
Esperado: FAIL — `PrazoInscricaoJob` não existe.

- [ ] **Step 3: Implementar o job**

`src/main/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJob.java`:

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.EventoResponsavel;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.usuario.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** Notificações in-app ligadas ao prazo de inscrição (V38). Roda diariamente às 06:30
 *  (depois da renovação de token do Mercado Pago). Sem fila — segue o padrão de
 *  CobrancaEventoExpiracaoJob. Idempotente: cada aviso tem um carimbo no próprio registro
 *  (evento.avisoPrazoProximoEm / evento.avisoPrazoFechadoEm / inscricao.avisoPrazoIncompletoEm),
 *  então rodar 2x no mesmo dia não duplica. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PrazoInscricaoJob {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacaoService notificacaoService;

    @Value("${app.eventos.aviso-prazo-dias:3}")
    private int avisoPrazoDias;

    @Scheduled(cron = "0 30 6 * * *")
    public void executar() {
        LocalDateTime agora = LocalDateTime.now();
        var eventos = eventoRepository.buscarComPrazoAtivoParaJob(agora);
        for (Evento evento : eventos) {
            try {
                processar(evento, agora);
            } catch (Exception e) {
                log.error("Falha ao processar avisos de prazo do evento {} — seguindo", evento.getId(), e);
            }
        }
    }

    @Transactional
    protected void processar(Evento evento, LocalDateTime agora) {
        LocalDateTime prazo = evento.getInscricoesAte();
        boolean venceu = agora.isAfter(prazo);
        boolean naJanela = !venceu && agora.isAfter(prazo.minusDays(avisoPrazoDias));

        // 1) lembrete de inscrição incompleta (evento pago) — na janela do "chegando"
        if (naJanela) {
            for (var inscricao : inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(evento.getId())) {
                if (inscricao.getPessoa() == null) continue;
                usuarioRepository.findByPessoaId(inscricao.getPessoa().getId()).ifPresent(usuario ->
                    notificacaoService.criar(TipoNotificacao.PRAZO_INSCRICAO_INCOMPLETA,
                        evento.getIgreja().getId(), usuario.getId(),
                        "O prazo de inscrição em \"" + evento.getTitulo() + "\" encerra em "
                            + prazo.format(DATA) + ". Falta pagar pra garantir sua vaga.",
                        "/eventos?detalhe=" + evento.getId()));
                inscricao.setAvisoPrazoIncompletoEm(agora);
                inscricaoRepository.save(inscricao);
            }
        }

        // 2) aviso ao responsável: prazo chegando
        if (naJanela && evento.getAvisoPrazoProximoEm() == null) {
            long dias = Math.max(0, java.time.Duration.between(agora, prazo).toDays());
            notificarResponsaveis(evento, TipoNotificacao.PRAZO_INSCRICAO_PROXIMO,
                "As inscrições de \"" + evento.getTitulo() + "\" encerram em " + prazo.format(DATA)
                    + " (" + dias + " dia" + (dias == 1 ? "" : "s") + ").",
                "/eventos?detalhe=" + evento.getId());
            evento.setAvisoPrazoProximoEm(agora);
            eventoRepository.save(evento);
        }

        // 3) aviso ao responsável: prazo fechou
        if (venceu && evento.getAvisoPrazoFechadoEm() == null) {
            long confirmadas = inscricaoRepository.contarPorEventoIdEStatus(
                evento.getId(), StatusInscricao.CONFIRMADA);            // conferir/criar
            long aguardando = inscricaoRepository.contarPorEventoIdEStatus(
                evento.getId(), StatusInscricao.AGUARDANDO_PAGAMENTO);
            notificarResponsaveis(evento, TipoNotificacao.PRAZO_INSCRICAO_FECHADO,
                "As inscrições de \"" + evento.getTitulo() + "\" encerraram. "
                    + confirmadas + " confirmados, " + aguardando + " aguardando pagamento.",
                "/eventos/" + evento.getId() + "/inscritos");
            evento.setAvisoPrazoFechadoEm(agora);
            eventoRepository.save(evento);
        }
    }

    private void notificarResponsaveis(Evento evento, TipoNotificacao tipo, String texto, String link) {
        for (EventoResponsavel r : evento.getResponsaveis()) {
            if (r.getPessoa() == null) continue;
            usuarioRepository.findByPessoaId(r.getPessoa().getId()).ifPresent(usuario ->
                notificacaoService.criar(tipo, evento.getIgreja().getId(), usuario.getId(), texto, link));
        }
    }
}
```

**Notas de implementação:**
- Se `inscricaoRepository.contarPorEventoIdEStatus` não existir, criar (`@Query("SELECT count(i) FROM InscricaoEvento i WHERE i.evento.id = :id AND i.status = :status")`). Ajustar o teste `avisaResponsavelQuandoPrazoFechouUmaVezSo` pra mockar essas contagens (`when(...contarPorEventoIdEStatus(...)).thenReturn(0L)`).
- `@Transactional` em método `protected` chamado de dentro da própria classe **não** cria transação (self-invocation). Duas opções: (a) mover `processar` pra um bean separado `PrazoInscricaoProcessador` injetado no job (padrão limpo, recomendado); (b) anotar `executar()` com `@Transactional` e aceitar transação única (uma falha derruba tudo — pior). **Escolher (a):** criar `PrazoInscricaoProcessador` com o método `processar` `@Transactional public`, e o job só itera e chama `processador.processar(evento, agora)` num try/catch. Atualizar o teste pra instanciar as duas classes ou testar o `Processador` diretamente.

- [ ] **Step 4: Config**

Em `src/main/resources/application.properties`, junto de outras `app.*`:
```properties
app.eventos.aviso-prazo-dias=${EVENTOS_AVISO_PRAZO_DIAS:3}
```

- [ ] **Step 5: Verificar `@EnableScheduling`**

`grep -rn "@EnableScheduling" src/main/java` — já deve existir (outros jobs usam). Se não, adicionar na classe de config principal.

- [ ] **Step 6: Rodar — deve passar**

```
mvn -o test -Dtest=PrazoInscricaoJobTest
```
Esperado: PASS.

- [ ] **Step 7: Rodar a suíte de inscrição inteira**

```
mvn -o test -Dtest='InscricaoServiceTest,InscricaoPrazoTest,InscricaoElegibilidadeTest,PrazoInscricaoJobTest'
```
Esperado: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJob.java \
        src/main/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoProcessador.java \
        src/test/java/com/domus/api/modules/evento/inscricao/PrazoInscricaoJobTest.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java \
        src/main/resources/application.properties
git commit -m "feat(evento): job diário de notificação de prazo de inscrição"
```

---

## Task 16: Atualizar o diagrama ER no CLAUDE.md

**Files:**
- Modify: `backend/api/CLAUDE.md` (bloco `EVENTO` e `INSCRICAO_EVENTO` do mermaid; nota de "Cadastro de evento enriquecido"; "Estado atual: **V37**" → **V38**)

- [ ] **Step 1: Editar o bloco `EVENTO` do mermaid**

Adicionar:
```
        timestamp inscricoes_ate "V38 - prazo de inscrição; NULL = aceita até o evento começar"
        boolean   permite_cancelar_apos_prazo "V38 - FALSE trava a lista no prazo"
        timestamp aviso_prazo_proximo_em "V38 - carimbo do PrazoInscricaoJob"
        timestamp aviso_prazo_fechado_em "V38 - carimbo do PrazoInscricaoJob"
```

- [ ] **Step 2: Editar o bloco `INSCRICAO_EVENTO`**

```
        timestamp aviso_prazo_incompleto_em "V38 - carimbo do PrazoInscricaoJob"
```

- [ ] **Step 3: Atualizar o texto**

- "Estado atual: **V37**" → "**V38**".
- Na nota "Cadastro de evento enriquecido (V3, V36)", acrescentar uma frase: "**Prazo de inscrição (V38):** `inscricoes_ate` opcional fecha a inscrição antes de o evento começar — 5ª porta, independente das restrições de elegibilidade; admin/líder furam o prazo, link público não."

- [ ] **Step 4: Commit**

```bash
git add backend/api/CLAUDE.md
git commit -m "docs: diagrama ER — prazo de inscrição (V38)"
```

---

## Checkpoints de teste do autor

Depois de cada PEDAÇO, **parar** e pedir pro autor testar antes de seguir:

1. **Após Pedaço 1** (Tasks 1-4): autor roda `mvn -o test -Dtest='EventoServiceTest,EventoPrazoMigracaoTest,EventoSituacaoInscricaoTest'` e confere criar/editar evento com prazo.
2. **Após Pedaço 2** (Tasks 5-8): autor testa via API/UI que comum barra no prazo, admin passa, cancelamento respeita o toggle.
3. **Após Pedaço 3** (Tasks 9-12): autor testa o form, o selo do card, o drawer (comum vs admin) no navegador + mobile.
4. **Após Pedaço 4** (Task 13): autor abre um link de convite de evento com prazo vencido.
5. **Após Pedaço 5** (Tasks 14-16): autor baixa o cron pra teste (ou ajusta o horário) e confere as 3 notificações.

Nenhum merge pra `main` sem o OK do autor em todos os pedaços. Commits ficam na branch `develop`.

---

## Self-Review (feito pelo autor do plano)

**1. Cobertura do spec:**
- Modelo de dados (5 colunas, entidades, `SituacaoInscricao`, validação, propagação série) → Tasks 1-4, 7. ✓
- Enforcement (guarda inscrição + cancelamento, capacidade, 4 caminhos) → Tasks 5-6. ✓
- DTOs (`EventoResponse`, `EventoRequest`, convite) → Tasks 3, 7, 8, 13. ✓
- Job (3 notificações, dedup por carimbo, cron, config, responsável sem login, janela perdida) → Tasks 14-15. ✓
- Frontend (form, selo card, drawer, modais, convite, erros) → Tasks 9-13. ✓
- Diagrama ER → Task 16. ✓
- Fora de escopo (elegibilidade+KIDS, prazo relativo série, e-mail, waitlist) → não há task, correto. ✓

**2. Placeholders:** os "conferir o nome real de X" são instruções de verificação concretas (o executor roda um `grep` nomeado), não TODOs de design. Aceitável.

**3. Consistência de tipos:**
- `SituacaoInscricao` no pacote `com.domus.api.modules.evento` (Task 2) — usado em `Evento` (Task 2), `EventoResponse` (Task 7), `ConvitePublicoResponse` (Task 8). ✓
- `validarPrazoInscricao(Evento, String)` (Task 5) vs `validarCancelamentoPermitido(Evento, boolean, boolean)` (Task 6) — assinaturas distintas de propósito, nomes consistentes. ✓
- `TipoNotificacao.PRAZO_INSCRICAO_FECHADO` (Task 14) usado no job (Task 15) e citado no self-review do spec. ✓ (distinto do código de erro `PRAZO_INSCRICAO_ENCERRADO`).
- Carimbos: `avisoPrazoProximoEm`/`avisoPrazoFechadoEm` (Evento), `avisoPrazoIncompletoEm` (InscricaoEvento) — nomes idênticos entre Task 1, 14, 15. ✓
- Front: `inscricoesAte`/`permiteCancelarAposPrazo`/`situacaoInscricao` idênticos entre `evento.type.ts`, schema, form, componentes. ✓

**Ajuste aplicado inline:** Task 15 Step 3 nota (a) — extrair `PrazoInscricaoProcessador` pra o `@Transactional` funcionar (self-invocation não abre transação). Task 15 Step 8 já inclui o arquivo novo no commit.
