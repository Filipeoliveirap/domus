# Relatório de eventos (presença + engajamento) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** dar à igreja um relatório individual de presença por evento e um relatório geral de
engajamento entre eventos, apoiados em três colunas booleanas novas (`evento.controla_presenca`,
`inscricao_evento.compareceu`, `acompanhante_inscricao.compareceu`) e sem tabela nova.

**Architecture:** presença é opt-in por evento (`controla_presenca`, só permitido quando
`requer_inscricao=true`) e marcada por pessoa física (inscrito + cada acompanhante). Dois
endpoints de leitura agregam os dados na hora (sem pré-cálculo, escala pequena): relatório
individual por evento e relatório geral com filtros. Toda métrica que depende de comparecimento
real declara a base usada (`COMPARECIMENTO` ou `INSCRITOS`) — nunca mistura em silêncio.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA (JPQL), Flyway, PostgreSQL — back;
Next.js, TypeScript, CSS Modules, TanStack Query, React Hook Form + Zod, Recharts — front.

## Global Constraints

- Migration nova é `V6__` (a última existente é `V5__inscricao_por_excecao.sql`).
- `evento.controla_presenca BOOLEAN DEFAULT false`, com `CHECK (NOT controla_presenca OR requer_inscricao)`.
- `inscricao_evento.compareceu BOOLEAN DEFAULT false`; `acompanhante_inscricao.compareceu BOOLEAN DEFAULT false`.
- Nenhuma tabela nova — só as três colunas.
- `igreja_id` sempre vem do JWT/usuário autenticado (via `UsuarioAutenticado`), nunca do corpo da requisição.
- Services retornam DTOs (`record`), nunca entidades JPA.
- Toda checagem de permissão passa por `Permissoes` — reusar `podeGerenciarInscricoes(role)` para marcar presença (mesma capacidade de quem já gerencia inscrição); nenhuma role nova.
- Endpoints de marcar presença retornam **409** quando `evento.controlaPresenca=false` (nova exceção `ConflitoNegocioException`, não reusar `BusinessException` que é 400).
- "Pessoas da Igreja" / "Convidados" são os rótulos de **tela** no front — nunca "Membros"/"Visitantes" (colide com o enum `Vinculo`).
- `percentualIgreja` do relatório individual: só pessoas cadastradas que compareceram ÷ total de pessoas ATIVAS da igreja (`PessoaRepository.countByIgrejaId`, que já exclui arquivadas via `@SQLRestriction`). Convidado nunca entra nesse cálculo.
- Comparecimento médio, participantes únicos e tendência mensal só consideram eventos com `controlaPresenca=true`; mês sem nenhum evento assim é `null` explícito, nunca `0`.
- "Evento mais popular" usa inscritos confirmados (pessoa+convidado), funciona em qualquer evento.
- Variação (evento anterior do mesmo tipo / média geral do filtro) usa comparecimento real quando aplicável, cai para inscritos confirmados senão — o campo `base` do DTO sempre indica qual foi usada.
- "Evento anterior do mesmo tipo" = evento mais recente da mesma igreja, mesmo `tipo`, `inicioEm` anterior ao evento atual.
- Responsividade obrigatória em toda tela nova de front (tabelas → cards, filtros empilham, `min-width:0` na cadeia flex/grid) — validar em viewport de celular.

---

## File Structure

**Backend — novos arquivos:**
- `src/main/resources/db/migration/V6__relatorio_presenca.sql` — as três colunas + o CHECK.
- `src/main/java/com/domus/api/shared/exception/ConflitoNegocioException.java` — exceção nova, mapeada a 409.
- `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/MarcarPresencaRequest.java`
- `src/main/java/com/domus/api/modules/evento/DTOs/RelatorioEventoResponse.java` — relatório individual.
- `src/main/java/com/domus/api/modules/evento/DTOs/RelatorioGeralResponse.java` — relatório geral (+ `BaseComparacao` enum).
- `src/main/java/com/domus/api/modules/evento/BaseComparacao.java` — enum `COMPARECIMENTO`/`INSCRITOS`.
- `src/main/java/com/domus/api/modules/evento/EventoRelatorioService.java` — toda a lógica de agregação (arquivo próprio: razão de mudar diferente de `EventoService`, que é CRUD).
- `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPresencaTest.java` — testes de marcar presença.
- `src/test/java/com/domus/api/modules/evento/EventoRelatorioServiceTest.java` — testes de agregação.

**Backend — arquivos modificados:**
- `Evento.java` — campo `controlaPresenca`.
- `InscricaoEvento.java` — campo `compareceu`.
- `AcompanhanteInscricao.java` — campo `compareceu`.
- `EventoRequest.java` / `EventoResponse.java` — campo `controlaPresenca`.
- `EventoService.java` — valida `controlaPresenca` exige `requerInscricao` (cadastrar + atualizar).
- `EventoRepository.java` — `buscarParaRelatorio`, `buscarComControlaPresenca`, `findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc`.
- `InscricaoRepository.java` — `countPessoasInscritas`, `countConvidadosInscritos`, `countPessoasCompareceram`, `countConvidadosCompareceram`, `contarParticipantesUnicos`, `findByIdAndIgrejaId` já existe (reusar).
- `InscricaoService.java` — `marcarTodosPresentes`, `marcarPresencaInscricao`, `marcarPresencaAcompanhante`.
- `InscricaoController.java` — os 3 endpoints de presença.
- `EventoController.java` — `GET /eventos/{id}/relatorio`, `GET /eventos/relatorio-geral`.
- `GlobalExceptionHandler.java` — handler de `ConflitoNegocioException` → 409.

**Frontend — novos arquivos:**
- `frontend/src/hooks/inscricao/useMarcarTodosPresentes.ts`
- `frontend/src/hooks/inscricao/useMarcarPresencaInscricao.ts`
- `frontend/src/hooks/inscricao/useMarcarPresencaAcompanhante.ts`
- `frontend/src/hooks/evento/useRelatorioEvento.ts`
- `frontend/src/hooks/evento/useRelatorioGeral.ts`
- `frontend/src/components/module/eventos/CardsRelatorioEvento.tsx` + `.module.css` — os 3 cards do relatório individual.
- `frontend/src/app/(app)/eventos/relatorio/page.tsx` + `relatorio.module.css` — página do relatório geral.
- `frontend/src/components/module/eventos/GraficoTendenciaComparecimento.tsx` — gráfico Recharts.
- `frontend/src/components/module/eventos/CardVariacao.tsx` — badge de variação com tooltip de base.

**Frontend — arquivos modificados:**
- `frontend/src/types/evento.type.ts` — `controlaPresenca` em `EventoResponse`/`EventoRequest`; tipos do relatório.
- `frontend/src/types/inscricao.type.ts` — `compareceu` em `InscritoResponse`/`AcompanhanteResponse`.
- `frontend/src/lib/validators.ts` — `controlaPresenca` no `eventoSchemaBase`.
- `frontend/src/lib/endpoints.ts` — rotas novas.
- `frontend/src/services/evento.service.ts` — `relatorio`, `relatorioGeral`.
- `frontend/src/services/inscricao.service.ts` — `marcarTodosPresentes`, `marcarPresencaInscricao`, `marcarPresencaAcompanhante`.
- `frontend/src/hooks/evento/useEventoForm.ts` — envia `controlaPresenca` no payload.
- `frontend/src/lib/cacheInvalidacao.ts` — presença invalida `['inscricoes']`/`['evento']` (já cobertos pela entrada `inscricao` existente — sem mudança necessária, mas revisitado na Tarefa 12).
- `frontend/src/app/(app)/eventos/[id]/inscritos/page.tsx` — coluna de presença + botão "marcar todos vieram" + `<CardsRelatorioEvento>`.
- `frontend/src/app/(app)/eventos/cadastrar/page.tsx` (e o formulário de edição equivalente) — toggle "Controlar presença".

---
## Task 1: Migration V6 — as três colunas de presença

**Files:**
- Create: `src/main/resources/db/migration/V6__relatorio_presenca.sql`
- Test: verificação manual via `./mvnw flyway:info` / subida do contexto Spring (Flyway roda no boot)

**Interfaces:**
- Consumes: nada (primeira tarefa).
- Produces: colunas `evento.controla_presenca`, `inscricao_evento.compareceu`,
  `acompanhante_inscricao.compareceu` — usadas por toda tarefa seguinte.

- [ ] **Step 1: Escrever a migration**

```sql
-- V6: presença é opt-in por evento — "dar baixa" em quem realmente compareceu, separado
-- de quem apenas se inscreveu. Granular por PESSOA FÍSICA (inscrito e cada acompanhante),
-- porque acompanhante ocupa vaga e esteve lá igual.
--
-- CHECK: só pode controlar presença quem já organiza lista de inscrição (requer_inscricao).
-- Sem lista prévia não há quem "chamar" — controlar presença sem inscrição não faz sentido.
ALTER TABLE evento
    ADD COLUMN controla_presenca BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_controla_presenca_exige_inscricao
    CHECK (NOT controla_presenca OR requer_inscricao);

ALTER TABLE inscricao_evento
    ADD COLUMN compareceu BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE acompanhante_inscricao
    ADD COLUMN compareceu BOOLEAN NOT NULL DEFAULT false;
```

- [ ] **Step 2: Subir o contexto local (ou rodar os testes) para o Flyway aplicar a migration**

Run: `./mvnw test -Dtest=EventoServiceCamposInscricaoTest` (qualquer teste que suba o contexto
Spring/Flyway serve de verificação; se usar banco local via docker, `./mvnw spring-boot:run`
e observar o log `Successfully applied 1 migration to schema "public", now at version v6`).
Expected: log de sucesso do Flyway aplicando `V6__relatorio_presenca.sql`, sem erro de CHECK
(a base de dev/teste não tem evento com `controla_presenca=true` ainda, então o CHECK não
falha ao aplicar sobre dado existente).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V6__relatorio_presenca.sql
git commit -m "feat(evento): migration V6 - colunas de presenca (controla_presenca, compareceu)"
```

---

## Task 2: `ConflitoNegocioException` — nova exceção mapeada a 409

O projeto já tem exceções dedicadas por status (`BusinessException`→400, `ResourceNotFoundException`→404,
`SessaoExpiradaException`→401). Falta uma para 409: marcar presença num evento sem
`controlaPresenca` não é "dado inválido" (400) nem "não encontrado" (404) — é "esta ação não
se aplica ao estado atual do recurso", que é exatamente o que 409 Conflict significa.

**Files:**
- Create: `src/main/java/com/domus/api/shared/exception/ConflitoNegocioException.java`
- Modify: `src/main/java/com/domus/api/shared/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/com/domus/api/shared/exception/GlobalExceptionHandlerTest.java` (criar se não existir; se já houver testes de handler equivalentes, seguir o mesmo arquivo)

**Interfaces:**
- Consumes: nada.
- Produces: `ConflitoNegocioException(String codigo, String message)` com `getCodigo()` — usada
  pela Tarefa 5 (`InscricaoService.marcarTodosPresentes` etc.) para sinalizar
  `controlaPresenca=false`.

- [ ] **Step 1: Escrever o teste do handler**

```java
package com.domus.api.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void conflitoNegocio_vira409ComCodigoEMensagem() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/eventos/x/presenca/marcar-todos");

        ConflitoNegocioException ex = new ConflitoNegocioException(
                "PRESENCA_NAO_HABILITADA", "Este evento não controla presença.");

        ResponseEntity<ErrorResponse> resposta = handler.handleConflitoNegocio(ex, request);

        assertThat(resposta.getStatusCode().value()).isEqualTo(409);
        assertThat(resposta.getBody().error()).isEqualTo("PRESENCA_NAO_HABILITADA");
        assertThat(resposta.getBody().message()).isEqualTo("Este evento não controla presença.");
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — `ConflitoNegocioException` e `handleConflitoNegocio` não existem ainda (erro de compilação).

- [ ] **Step 3: Criar a exceção**

```java
package com.domus.api.shared.exception;

/**
 * "Esta ação não se aplica ao estado atual do recurso" — vira HTTP 409 Conflict.
 *
 * <p>Diferente de {@link BusinessException} (400, dado inválido) e de
 * {@link ResourceNotFoundException} (404, recurso não existe): aqui o recurso existe e o
 * dado é válido, mas o estado atual (ex.: {@code evento.controlaPresenca=false}) torna a
 * operação sem sentido — marcar presença onde não há lista de presença para marcar.
 */
public class ConflitoNegocioException extends RuntimeException {
    private final String codigo;

    public ConflitoNegocioException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
```

- [ ] **Step 4: Adicionar o handler**

No arquivo `GlobalExceptionHandler.java`, adicionar (antes do `@ExceptionHandler(DataIntegrityViolationException.class)`):

```java
    @ExceptionHandler(ConflitoNegocioException.class)
    public ResponseEntity<ErrorResponse> handleConflitoNegocio(ConflitoNegocioException ex, HttpServletRequest request) {
        log.warn("Conflito de negócio. path={}, codigo={}", request.getRequestURI(), ex.getCodigo());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(409, ex.getCodigo(), ex.getMessage()));
    }
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/shared/exception/ConflitoNegocioException.java \
        src/main/java/com/domus/api/shared/exception/GlobalExceptionHandler.java \
        src/test/java/com/domus/api/shared/exception/GlobalExceptionHandlerTest.java
git commit -m "feat(shared): ConflitoNegocioException para respostas 409"
```

---

## Task 3: Entidades — `controlaPresenca` / `compareceu`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteInscricao.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoDefaultsTest.java` (criar)

**Interfaces:**
- Consumes: colunas da Tarefa 1.
- Produces: `Evento.isControlaPresenca()`/`setControlaPresenca(boolean)`,
  `InscricaoEvento.isCompareceu()`/`setCompareceu(boolean)`,
  `AcompanhanteInscricao.isCompareceu()`/`setCompareceu(boolean)` — usados por toda tarefa
  de service/repository seguinte.

- [ ] **Step 1: Escrever o teste de default**

```java
package com.domus.api.modules.evento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventoDefaultsTest {

    @Test
    void controlaPresenca_defaultFalse_quandoNaoInformado() {
        Evento evento = Evento.builder()
                .titulo("Culto")
                .inicioEm(java.time.LocalDateTime.now())
                .build();

        assertThat(evento.isControlaPresenca()).isFalse();
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `./mvnw test -Dtest=EventoDefaultsTest`
Expected: FAIL — `isControlaPresenca()` não existe (erro de compilação).

- [ ] **Step 3: Adicionar o campo em `Evento.java`**

Logo abaixo do campo `requerInscricao` (que já existe), adicionar:

```java
    /**
     * Presença é opt-in por evento (Fase relatório de presença): só pode ser {@code true}
     * quando {@link #requerInscricao} também é {@code true} — CHECK do banco (V6) garante
     * isso na origem; {@link com.domus.api.modules.evento.EventoService} repete a checagem
     * para devolver mensagem decente em vez de 500 genérico.
     */
    @Column(name = "controla_presenca", nullable = false)
    @Builder.Default
    private boolean controlaPresenca = false;
```

- [ ] **Step 4: Adicionar o campo em `InscricaoEvento.java`**

Logo abaixo do campo `inscritoPorExcecao`, adicionar:

```java
    /**
     * Presença marcada nesta inscrição (a PESSOA cadastrada, não seus acompanhantes — ver
     * {@link AcompanhanteInscricao#isCompareceu()}). Só tem sentido quando
     * {@code evento.controlaPresenca=true}; ver {@code InscricaoService.marcarPresencaInscricao}.
     */
    @Column(name = "compareceu", nullable = false)
    @Builder.Default
    private boolean compareceu = false;
```

- [ ] **Step 5: Adicionar o campo em `AcompanhanteInscricao.java`**

Logo abaixo do campo `telefone`, adicionar:

```java
    /**
     * Presença marcada para ESTE convidado especificamente — acompanhante ocupa vaga e
     * esteve lá igual ao inscrito, por isso a presença é granular por pessoa física.
     */
    @Column(name = "compareceu", nullable = false)
    @Builder.Default
    private boolean compareceu = false;
```

- [ ] **Step 6: Rodar e ver passar**

Run: `./mvnw test -Dtest=EventoDefaultsTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/Evento.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java \
        src/main/java/com/domus/api/modules/evento/inscricao/AcompanhanteInscricao.java \
        src/test/java/com/domus/api/modules/evento/EventoDefaultsTest.java
git commit -m "feat(evento): campos controlaPresenca/compareceu nas entidades"
```

---

## Task 4: `EventoRequest`/`EventoResponse` + validação no `EventoService`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceCamposInscricaoTest.java` (arquivo já existe — adicionar casos)

**Interfaces:**
- Consumes: `Evento.isControlaPresenca()`/`setControlaPresenca()` (Tarefa 3).
- Produces: `EventoRequest.controlaPresenca()` (Boolean, nullable — mesmo padrão de
  `requerInscricao`/`exclusivoMembros`), `EventoResponse.controlaPresenca()` (boolean).
  `EventoService.cadastrarEvento`/`atualizarEvento` lançam `BusinessException("CONTROLA_PRESENCA_SEM_INSCRICAO", ...)`
  quando `controlaPresenca=true` e `requerInscricao=false` no mesmo payload.

- [ ] **Step 1: Ler o teste existente para saber o padrão de mock**

Run: `sed -n '1,60p' src/test/java/com/domus/api/modules/evento/EventoServiceCamposInscricaoTest.java`
(nenhuma alteração ainda — só para copiar o padrão de setup de mocks usado pelos testes de
`EventoService` antes de escrever o novo teste.)

- [ ] **Step 2: Escrever o teste que falha**

Adicionar ao final da classe `EventoServiceCamposInscricaoTest` (mesmo arquivo, mesmo `setup()`
de mocks já existente — usar os mocks/helpers já presentes no arquivo, como `igreja()`,
`usuarioRepository`, `eventoRepository`, etc., sem recriá-los):

```java
    @Test
    void cadastrarEvento_recusaControlaPresencaSemRequerInscricao() {
        EventoRequest data = new EventoRequest(
                "Culto", null, LocalDateTime.now().plusDays(1), null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, false, false, // requerInscricao=false
                null
        );
        // controlaPresenca não faz parte do record ainda — este teste some quando o Step 3
        // adicionar o campo; reescrito no Step 4 abaixo já com o campo presente.
    }
```

- [ ] **Step 3: Adicionar `controlaPresenca` em `EventoRequest.java`**

No `record EventoRequest`, logo após `Boolean requerInscricao,`:

```java
        Boolean requerInscricao,
        /**
         * Só pode ser {@code true} quando {@code requerInscricao} também é — ver
         * {@link com.domus.api.modules.evento.EventoService#validarControlaPresenca}.
         */
        Boolean controlaPresenca,
```

- [ ] **Step 4: Reescrever o teste do Step 2 já com o campo, e adicionar o caso positivo**

Substituir o teste provisório do Step 2 por:

```java
    @Test
    void cadastrarEvento_recusaControlaPresencaSemRequerInscricao() {
        EventoRequest data = new EventoRequest(
                "Culto", null, LocalDateTime.now().plusDays(1), null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, false, false, true, // requerInscricao=false, controlaPresenca=true
                null
        );

        assertThatThrownBy(() -> service.cadastrarEvento(data, igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("controlar presença");
    }

    @Test
    void cadastrarEvento_aceitaControlaPresencaComRequerInscricao() {
        Igreja igreja = igreja();
        when(igrejaRepository.findById(igrejaId)).thenReturn(java.util.Optional.of(igreja));
        Usuario usuario = usuario();
        when(usuarioRepository.findByIdAndIgrejaId(usuarioId, igrejaId)).thenReturn(java.util.Optional.of(usuario));
        when(eventoRepository.save(any(Evento.class))).thenAnswer(inv -> inv.getArgument(0));

        EventoRequest data = new EventoRequest(
                "Retiro", null, LocalDateTime.now().plusDays(1), null,
                null, null, null, null,
                null, null, null, null, null,
                null, null, false, true, true, // requerInscricao=true, controlaPresenca=true
                null
        );

        EventoResponse response = service.cadastrarEvento(data, igrejaId, usuarioId);
        assertThat(response.controlaPresenca()).isTrue();
    }
```

> Nota para quem implementa: os helpers `igreja()`, `usuario()`, os mocks
> `igrejaRepository`/`usuarioRepository`/`eventoRepository` e o import de `assertThatThrownBy`/
> `assertThat`/`any`/`when` já existem no arquivo (mesmo padrão usado pelos testes vizinhos de
> `EventoServiceCamposInscricaoTest`) — não recriar, só reusar. Se o nome do helper de usuário
> for diferente de `usuario()`, usar o nome já existente no arquivo.

- [ ] **Step 5: Rodar e ver falhar**

Run: `./mvnw test -Dtest=EventoServiceCamposInscricaoTest`
Expected: FAIL — `EventoResponse.controlaPresenca()` não existe e `EventoRequest` ainda não
tem 18 posições (erro de compilação/assinatura).

- [ ] **Step 6: Adicionar `controlaPresenca` em `EventoResponse.java`**

No `record EventoResponse`, logo após `boolean requerInscricao,`:

```java
        boolean requerInscricao,
        boolean controlaPresenca,
```

E no método `from`, logo após `e.isRequerInscricao(), ...`:

```java
                e.isRequerInscricao(), e.isControlaPresenca(), e.getSituacao(), inscricoesRemovidas,
```

(substitui a linha existente `e.isRequerInscricao(), e.getSituacao(), inscricoesRemovidas,` —
mesma posição, só insere `e.isControlaPresenca()` no meio).

- [ ] **Step 7: Adicionar a validação e a gravação em `EventoService.java`**

Adicionar o método privado (junto a `validarDatas`/`validarIdades`):

```java
    /**
     * Espelha o CHECK do banco (V6) do lado de cá, para devolver mensagem decente em vez de
     * 500 genérico vindo da constraint. Controlar presença sem organizar inscrição não faz
     * sentido — não há lista prévia de quem "chamar".
     */
    private void validarControlaPresenca(EventoRequest data) {
        boolean controlaPresenca = Boolean.TRUE.equals(data.controlaPresenca());
        boolean requerInscricao = Boolean.TRUE.equals(data.requerInscricao());
        if (controlaPresenca && !requerInscricao) {
            throw new BusinessException("CONTROLA_PRESENCA_SEM_INSCRICAO",
                    "Só é possível controlar presença em eventos que também exigem inscrição.");
        }
    }
```

Chamar `validarControlaPresenca(data);` logo após `validarIdades(data);` em **ambos**
`cadastrarEvento` e `atualizarEvento` (o de 4 parâmetros e o de 5 parâmetros — como o de 4
delega para o de 5, basta adicionar no de 5 e no `cadastrarEvento`).

No `Evento.builder()...` de `cadastrarEvento`, adicionar:

```java
                .controlaPresenca(Boolean.TRUE.equals(data.controlaPresenca()))
```

(logo após `.requerInscricao(Boolean.TRUE.equals(data.requerInscricao()))`).

Em `atualizarEvento`, logo após `evento.setRequerInscricao(Boolean.TRUE.equals(data.requerInscricao()));`,
adicionar:

```java
        evento.setControlaPresenca(Boolean.TRUE.equals(data.controlaPresenca()));
```

- [ ] **Step 8: Rodar e ver passar**

Run: `./mvnw test -Dtest=EventoServiceCamposInscricaoTest`
Expected: PASS — incluindo os testes já existentes no arquivo (o novo campo é aditivo, não
deve quebrar nenhum teste antigo; se algum teste antigo construía `EventoRequest` posicional
sem o novo campo, ajustá-lo para passar `null`/`false` na posição de `controlaPresenca`).

- [ ] **Step 9: Rodar a suíte completa do módulo evento para checar quebras por posição do record**

Run: `./mvnw test -Dtest="com.domus.api.modules.evento.**"`
Expected: PASS em todos — `EventoRequest` é `record` posicional, então qualquer outro teste
que construía um `new EventoRequest(...)` sem o campo novo precisa ganhar mais um argumento
(`null` ou `false`) na posição de `controlaPresenca`, logo após `requerInscricao`. Ajustar
cada ocorrência encontrada.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java \
        src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/evento/EventoServiceCamposInscricaoTest.java
git commit -m "feat(evento): controlaPresenca no request/response, com validacao contra requerInscricao"
```

---

## Task 5: `InscricaoRepository` — contagens de inscritos/comparecimento

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoRepositoryPresencaTest.java` (criar — `@DataJpaTest`)

Este projeto não tem exemplo de `@DataJpaTest` no módulo de inscrição ainda (os testes de
service usam mocks). Para não inventar infraestrutura nova, siga o padrão de
`LocalEventoServiceTest`/`InscricaoServiceTest` (mocks) para os testes de **service** — mas
as contagens em si (SQL/JPQL correto) merecem um teste de integração leve. Se o projeto não
tiver `application-test.yml`/perfil de teste com banco H2 ou Testcontainers configurado, PULE
este teste de repositório isolado e valide as queries indiretamente pelos testes de
`EventoRelatorioServiceTest` (Tarefa 7/8, que mockam o repositório) — a Tarefa 5 então vira
só a Step de escrever os métodos, sem teste de banco próprio.

**Interfaces:**
- Consumes: `StatusInscricao.CONFIRMADA` (já existe), campos `compareceu` (Tarefa 3).
- Produces (assinaturas que a Tarefa 7/8 vão consumir):
  - `long countPessoasInscritas(UUID eventoId)`
  - `long countConvidadosInscritos(UUID eventoId)`
  - `long countPessoasCompareceram(UUID eventoId)`
  - `long countConvidadosCompareceram(UUID eventoId)`
  - `long contarParticipantesUnicos(List<UUID> eventoIds)`

- [ ] **Step 1: Adicionar os métodos em `InscricaoRepository.java`**

Adicionar ao final da interface, antes do `}` de fechamento:

```java
    /** Quantas PESSOAS cadastradas (sem contar convidados) estão inscritas e confirmadas. */
    @Query("""
        SELECT COUNT(i) FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long countPessoasInscritas(@Param("eventoId") UUID eventoId);

    /** Quantos CONVIDADOS (acompanhantes) estão sob inscrições confirmadas do evento. */
    @Query("""
        SELECT COUNT(a) FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long countConvidadosInscritos(@Param("eventoId") UUID eventoId);

    /**
     * Quantas PESSOAS cadastradas de fato compareceram (marca de presença, não inscrição) —
     * só faz sentido em evento com {@code controlaPresenca=true}, checagem que é
     * responsabilidade do chamador ({@code EventoRelatorioService}).
     */
    @Query("""
        SELECT COUNT(i) FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND i.compareceu = true
    """)
    long countPessoasCompareceram(@Param("eventoId") UUID eventoId);

    /** Quantos CONVIDADOS de fato compareceram. */
    @Query("""
        SELECT COUNT(a) FROM AcompanhanteInscricao a
        WHERE a.inscricao.evento.id = :eventoId
          AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND a.compareceu = true
    """)
    long countConvidadosCompareceram(@Param("eventoId") UUID eventoId);

    /**
     * Pessoas CADASTRADAS distintas que compareceram de fato em qualquer um dos eventos
     * informados — usada pelo relatório geral ("participantes únicos"). Convidados não
     * entram: sem cadastro, não há como saber se é "a mesma pessoa" entre dois eventos.
     */
    @Query("""
        SELECT COUNT(DISTINCT i.pessoa.id) FROM InscricaoEvento i
        WHERE i.evento.id IN :eventoIds
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
          AND i.compareceu = true
    """)
    long contarParticipantesUnicos(@Param("eventoIds") List<UUID> eventoIds);
```

- [ ] **Step 2: Compilar**

Run: `./mvnw compile`
Expected: BUILD SUCCESS (estes métodos ainda não têm chamador, só precisa compilar).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java
git commit -m "feat(inscricao): queries de contagem de inscritos/comparecimento por evento"
```

---

## Task 6: `InscricaoService` — marcar presença (lote + individual) + endpoints

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java`
- Create: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/MarcarPresencaRequest.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPresencaTest.java`

**Interfaces:**
- Consumes: `InscricaoRepository.listarPorEvento`/`findByIdAndIgrejaId` (já existem),
  `AcompanhanteRepository` (já existe), `ConflitoNegocioException` (Tarefa 2),
  `Permissoes.podeGerenciarInscricoes(String)` (já existe).
- Produces:
  - `InscricaoService.marcarTodosPresentes(UUID eventoId, UUID igrejaId, String role): int` (retorna quantas pessoas físicas foram marcadas)
  - `InscricaoService.marcarPresencaInscricao(UUID eventoId, UUID inscricaoId, boolean compareceu, UUID igrejaId, String role): void`
  - `InscricaoService.marcarPresencaAcompanhante(UUID eventoId, UUID acompanhanteId, boolean compareceu, UUID igrejaId, String role): void`
  - Endpoints: `POST /eventos/{eventoId}/presenca/marcar-todos`,
    `PATCH /eventos/{eventoId}/presenca/inscricoes/{inscricaoId}`,
    `PATCH /eventos/{eventoId}/presenca/acompanhantes/{acompanhanteId}`.

- [ ] **Step 1: Escrever `MarcarPresencaRequest`**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotNull;

public record MarcarPresencaRequest(@NotNull boolean compareceu) {}
```

- [ ] **Step 2: Escrever os testes que falham (arquivo novo `InscricaoPresencaTest.java`)**

```java
package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.ConflitoNegocioException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class InscricaoPresencaTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    PessoaRepository pessoaRepository;
    UsuarioRepository usuarioRepository;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID inscricaoId = UUID.randomUUID();
    UUID acompanhanteId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        acompanhanteRepository = mock(AcompanhanteRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        ElegibilidadeService elegibilidadeService = new ElegibilidadeService(List.of());
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, pessoaRepository, usuarioRepository, elegibilidadeService);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(boolean controlaPresenca) {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Culto de Celebração").inicioEm(LocalDateTime.now().minusHours(2))
                .requerInscricao(true).controlaPresenca(controlaPresenca)
                .build();
    }

    private InscricaoEvento inscricao(Evento evento) {
        Pessoa pessoa = Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Maria").build();
        return InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CONFIRMADA).build();
    }

    @Test
    void marcarTodosPresentes_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.marcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);

        verify(inscricaoRepository, never()).listarPorEvento(any());
    }

    @Test
    void marcarTodosPresentes_marcaInscritoEAcompanhantes_quandoControlaPresenca() {
        Evento evento = evento(true);
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento));

        InscricaoEvento inscricao = inscricao(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        inscricao.setAcompanhantes(new java.util.ArrayList<>(List.of(acompanhante)));

        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(List.of(inscricao));

        int marcados = service.marcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA");

        assertThat(marcados).isEqualTo(2); // 1 inscrito + 1 acompanhante
        assertThat(inscricao.isCompareceu()).isTrue();
        assertThat(acompanhante.isCompareceu()).isTrue();
        verify(inscricaoRepository).save(inscricao);
    }

    @Test
    void marcarPresencaInscricao_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        InscricaoEvento inscricao = inscricao(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() ->
                service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);
    }

    @Test
    void marcarPresencaInscricao_marcaEDesmarca_individualmente() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ADMIN_IGREJA");
        assertThat(inscricao.isCompareceu()).isTrue();

        service.marcarPresencaInscricao(eventoId, inscricaoId, false, igrejaId, "ADMIN_IGREJA");
        assertThat(inscricao.isCompareceu()).isFalse();
    }

    @Test
    void marcarPresencaInscricao_recusaSemPermissao() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() ->
                service.marcarPresencaInscricao(eventoId, inscricaoId, true, igrejaId, "ACESSO_COMUM"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void marcarPresencaAcompanhante_recusa409_quandoEventoNaoControlaPresenca() {
        Evento evento = evento(false);
        InscricaoEvento inscricao = inscricao(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() ->
                service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ConflitoNegocioException.class);
    }

    @Test
    void marcarPresencaAcompanhante_marcaEDesmarca() {
        Evento evento = evento(true);
        InscricaoEvento inscricao = inscricao(evento);
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA");
        assertThat(acompanhante.isCompareceu()).isTrue();
    }

    @Test
    void marcarPresencaAcompanhante_naoEncontrado_deOutraIgreja() {
        Evento evento = evento(true);
        Igreja outraIgreja = new Igreja();
        outraIgreja.setId(UUID.randomUUID());
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).igreja(outraIgreja).evento(evento)
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(outraIgreja).nome("Maria").build())
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(acompanhanteId).inscricao(inscricao).nome("Convidado").build();
        when(acompanhanteRepository.findById(acompanhanteId)).thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() ->
                service.marcarPresencaAcompanhante(eventoId, acompanhanteId, true, igrejaId, "ADMIN_IGREJA"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `./mvnw test -Dtest=InscricaoPresencaTest`
Expected: FAIL — `marcarTodosPresentes`/`marcarPresencaInscricao`/`marcarPresencaAcompanhante`
não existem em `InscricaoService` (erro de compilação).

- [ ] **Step 4: Implementar os três métodos em `InscricaoService.java`**

Adicionar ao final da classe, antes do `}` de fechamento (após `buscarInscricao`):

```java
    /**
     * Marca presente TODO inscrito CONFIRMADO do evento e TODOS os seus acompanhantes —
     * o fluxo real é "quase todo mundo veio", e cada linha ganha um checkbox individual
     * para corrigir a exceção depois (ver {@link #marcarPresencaInscricao}/
     * {@link #marcarPresencaAcompanhante}).
     *
     * @return quantas PESSOAS FÍSICAS (inscritos + acompanhantes) foram marcadas.
     */
    @Transactional
    public int marcarTodosPresentes(UUID eventoId, UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
        validarControlaPresenca(evento);

        List<InscricaoEvento> inscricoes = inscricaoRepository.listarPorEvento(eventoId);
        int marcados = 0;
        for (InscricaoEvento inscricao : inscricoes) {
            inscricao.setCompareceu(true);
            marcados++;
            for (AcompanhanteInscricao acompanhante : inscricao.getAcompanhantes()) {
                acompanhante.setCompareceu(true);
                marcados++;
            }
            inscricaoRepository.save(inscricao);
        }

        log.info("Presença marcada em lote. evento_id={}, pessoas_marcadas={}, igreja_id={}",
                eventoId, marcados, igrejaId);
        return marcados;
    }

    /** Corrige a exceção de um inscrito específico após o "marcar todos" (ou o contrário). */
    @Transactional
    public void marcarPresencaInscricao(UUID eventoId, UUID inscricaoId, boolean compareceu,
                                        UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        InscricaoEvento inscricao = buscarInscricao(inscricaoId, igrejaId);
        validarControlaPresenca(inscricao.getEvento());

        inscricao.setCompareceu(compareceu);
        inscricaoRepository.save(inscricao);
        log.info("Presença individual marcada. inscricao_id={}, compareceu={}, igreja_id={}",
                inscricaoId, compareceu, igrejaId);
    }

    /** Corrige a exceção de UM convidado específico (o inscrito veio, o convidado não, ou vice-versa). */
    @Transactional
    public void marcarPresencaAcompanhante(UUID eventoId, UUID acompanhanteId, boolean compareceu,
                                           UUID igrejaId, String role) {
        if (!Permissoes.podeGerenciarInscricoes(role)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Você não tem permissão para marcar presença.");
        }

        AcompanhanteInscricao acompanhante = acompanhanteRepository.findById(acompanhanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Convidado não encontrado."));

        // Mesmo isolamento multi-tenant de removerAcompanhante(): id de outra igreja é
        // tratado como inexistente, nunca vaza que existe fora da própria igreja.
        if (!acompanhante.getInscricao().getIgreja().getId().equals(igrejaId)) {
            throw new ResourceNotFoundException("Convidado não encontrado.");
        }

        validarControlaPresenca(acompanhante.getInscricao().getEvento());

        acompanhante.setCompareceu(compareceu);
        acompanhanteRepository.save(acompanhante);
        log.info("Presença de convidado marcada. acompanhante_id={}, compareceu={}, igreja_id={}",
                acompanhanteId, compareceu, igrejaId);
    }

    /** Espelha o CHECK do banco (V6): sem controlaPresenca não existe presença para marcar. */
    private void validarControlaPresenca(Evento evento) {
        if (!evento.isControlaPresenca()) {
            throw new ConflitoNegocioException("PRESENCA_NAO_HABILITADA",
                    "Este evento não controla presença.");
        }
    }
```

Adicionar os imports que faltam no topo do arquivo:

```java
import com.domus.api.shared.exception.ConflitoNegocioException;
```

- [ ] **Step 5: Rodar e ver passar**

Run: `./mvnw test -Dtest=InscricaoPresencaTest`
Expected: PASS.

- [ ] **Step 6: Adicionar os três endpoints em `InscricaoController.java`**

Adicionar ao final da classe, antes do `}` de fechamento:

```java
    /**
     * Botão "marcar todos vieram" — marca presente todo inscrito confirmado E seus
     * acompanhantes. Corrige exceção depois com os PATCHs abaixo.
     */
    @PostMapping("/eventos/{eventoId}/presenca/marcar-todos")
    public ResponseEntity<Void> marcarTodosPresentes(@PathVariable UUID eventoId) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.marcarTodosPresentes(eventoId, usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/eventos/{eventoId}/presenca/inscricoes/{inscricaoId}")
    public ResponseEntity<Void> marcarPresencaInscricao(
            @PathVariable UUID eventoId,
            @PathVariable UUID inscricaoId,
            @Valid @RequestBody MarcarPresencaRequest data) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.marcarPresencaInscricao(eventoId, inscricaoId, data.compareceu(),
                usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/eventos/{eventoId}/presenca/acompanhantes/{acompanhanteId}")
    public ResponseEntity<Void> marcarPresencaAcompanhante(
            @PathVariable UUID eventoId,
            @PathVariable UUID acompanhanteId,
            @Valid @RequestBody MarcarPresencaRequest data) {
        var usuario = usuarioAutenticado.get();
        inscricaoService.marcarPresencaAcompanhante(eventoId, acompanhanteId, data.compareceu(),
                usuario.getIgreja().getId(), usuario.getRole().getNome());
        return ResponseEntity.noContent().build();
    }
```

O import `com.domus.api.modules.evento.inscricao.DTOs.*` já cobre `MarcarPresencaRequest`
(o arquivo já importa o pacote inteiro via wildcard).

- [ ] **Step 7: Registrar as rotas no `SecurityConfig` (mesma régua de `GET /eventos/*/inscricoes`)**

Em `src/main/java/com/domus/api/config/SecurityConfig.java`, o bloco de inscrição hoje é:

```java
                        .requestMatchers(HttpMethod.GET, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers("/eventos/*/inscricoes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.POST, "/eventos/*/inscricoes")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
                        .requestMatchers(HttpMethod.DELETE, "/inscricoes/**", "/acompanhantes/**")
                        .hasAnyRole(ADMIN, LIDER, COMUM)
```

Marcar presença é `podeGerenciarInscricoes` (só ADMIN/LÍDER, NUNCA `COMUM` — diferente de
`/eventos/*/inscricoes/**`, que é `COMUM` incluso porque cobre a auto-inscrição). Adicionar,
imediatamente ACIMA do bloco acima (matcher mais específico antes do curinga
`/eventos/*/inscricoes/**`, mesma armadilha de ordenação já documentada no comentário
vizinho):

```java
                        .requestMatchers(HttpMethod.POST, "/eventos/*/presenca/marcar-todos")
                        .hasAnyRole(ADMIN, LIDER)
                        .requestMatchers(HttpMethod.PATCH, "/eventos/*/presenca/**")
                        .hasAnyRole(ADMIN, LIDER)
```

- [ ] **Step 8: Rodar a suíte inteira do módulo de inscrição**

Run: `./mvnw test -Dtest="com.domus.api.modules.evento.inscricao.**"`
Expected: PASS em todos, incluindo `InscricaoServiceTest`, `InscricaoElegibilidadeTest`,
`InscricaoConcorrenciaTest`, `InscricaoPresencaTest`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java \
        src/main/java/com/domus/api/modules/evento/inscricao/DTOs/MarcarPresencaRequest.java \
        src/main/java/com/domus/api/config/SecurityConfig.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoPresencaTest.java
git commit -m "feat(inscricao): marcar presenca em lote e por excecao (inscrito/acompanhante)"
```

---

## Task 7: Relatório individual — `GET /eventos/{id}/relatorio`

**Files:**
- Create: `src/main/java/com/domus/api/modules/evento/BaseComparacao.java`
- Create: `src/main/java/com/domus/api/modules/evento/DTOs/RelatorioEventoResponse.java`
- Create: `src/main/java/com/domus/api/modules/evento/EventoRelatorioService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java`
- Create: `src/test/java/com/domus/api/modules/evento/EventoRelatorioServiceTest.java`

**Interfaces:**
- Consumes: `InscricaoRepository.countPessoasInscritas/countConvidadosInscritos/countPessoasCompareceram/countConvidadosCompareceram` (Tarefa 5), `PessoaRepository.countByIgrejaId` (já existe), `Evento.isControlaPresenca()` (Tarefa 3).
- Produces: `EventoRelatorioService.relatorioIndividual(UUID eventoId, UUID igrejaId): RelatorioEventoResponse`
  — consumido pela Tarefa 8 (nada) e pelo endpoint deste mesmo Task.
  `RelatorioEventoResponse(Inscritos inscritos, Compareceram compareceram, Double percentualIgreja)`
  onde `Inscritos(long pessoas, long convidados)` e `Compareceram(long pessoas, long convidados)`.

- [ ] **Step 1: Criar o enum `BaseComparacao`**

```java
package com.domus.api.modules.evento;

/**
 * Qual base de contagem alimentou uma variação do relatório geral (Decisão 4 do spec):
 * {@code COMPARECIMENTO} quando os dois eventos comparados têm {@code controlaPresenca=true};
 * {@code INSCRITOS} (inscritos confirmados, pessoa+convidado) quando não. Nunca implícito —
 * o front sempre mostra qual foi usada (tooltip/click).
 */
public enum BaseComparacao {
    COMPARECIMENTO,
    INSCRITOS
}
```

- [ ] **Step 2: Criar `RelatorioEventoResponse`**

```java
package com.domus.api.modules.evento.DTOs;

/**
 * Relatório individual de um evento (modal de detalhe). {@code compareceram} e
 * {@code percentualIgreja} são {@code null} quando {@code evento.controlaPresenca=false} —
 * a seção some inteira no front, nunca aparece zerada (ver EventoRelatorioService).
 */
public record RelatorioEventoResponse(
        Inscritos inscritos,
        Compareceram compareceram,
        Double percentualIgreja
) {
    public record Inscritos(long pessoas, long convidados) {}

    /** {@code pessoas}/{@code convidados} são "Pessoas da Igreja"/"Convidados" no front. */
    public record Compareceram(long pessoas, long convidados) {}
}
```

- [ ] **Step 3: Escrever o teste que falha**

```java
package com.domus.api.modules.evento;

import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EventoRelatorioServiceTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    PessoaRepository pessoaRepository;
    EventoRelatorioService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        service = new EventoRelatorioService(eventoRepository, inscricaoRepository, pessoaRepository);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(boolean controlaPresenca) {
        return Evento.builder()
                .id(eventoId).igreja(igreja()).titulo("Culto")
                .inicioEm(LocalDateTime.now().minusDays(1))
                .requerInscricao(true).controlaPresenca(controlaPresenca)
                .build();
    }

    @Test
    void relatorioIndividual_naoEncontrado_lancaExcecao() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.relatorioIndividual(eventoId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void relatorioIndividual_semControlaPresenca_naoTraSecaoDeComparecimento() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento(false)));
        when(inscricaoRepository.countPessoasInscritas(eventoId)).thenReturn(10L);
        when(inscricaoRepository.countConvidadosInscritos(eventoId)).thenReturn(3L);

        var relatorio = service.relatorioIndividual(eventoId, igrejaId);

        assertThat(relatorio.inscritos().pessoas()).isEqualTo(10);
        assertThat(relatorio.inscritos().convidados()).isEqualTo(3);
        assertThat(relatorio.compareceram()).isNull();
        assertThat(relatorio.percentualIgreja()).isNull();
        verify(inscricaoRepository, never()).countPessoasCompareceram(any());
    }

    @Test
    void relatorioIndividual_comControlaPresenca_calculaPercentualIgrejaSoComPessoasCadastradas() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento(true)));
        when(inscricaoRepository.countPessoasInscritas(eventoId)).thenReturn(20L);
        when(inscricaoRepository.countConvidadosInscritos(eventoId)).thenReturn(5L);
        when(inscricaoRepository.countPessoasCompareceram(eventoId)).thenReturn(15L);
        when(inscricaoRepository.countConvidadosCompareceram(eventoId)).thenReturn(4L);
        when(pessoaRepository.countByIgrejaId(igrejaId)).thenReturn(150L);

        var relatorio = service.relatorioIndividual(eventoId, igrejaId);

        assertThat(relatorio.compareceram().pessoas()).isEqualTo(15);
        assertThat(relatorio.compareceram().convidados()).isEqualTo(4);
        // 15 pessoas cadastradas presentes / 150 pessoas ativas da igreja = 10.0% — convidado
        // NUNCA entra neste cálculo (nem nos 4 do numerador, nem em lugar nenhum do denominador).
        assertThat(relatorio.percentualIgreja()).isEqualTo(10.0);
    }
}
```

- [ ] **Step 4: Rodar e ver falhar**

Run: `./mvnw test -Dtest=EventoRelatorioServiceTest`
Expected: FAIL — `EventoRelatorioService` não existe (erro de compilação).

- [ ] **Step 5: Implementar `EventoRelatorioService`**

```java
package com.domus.api.modules.evento;

import com.domus.api.modules.evento.DTOs.RelatorioEventoResponse;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Agregações de presença/engajamento — relatório individual (por evento) e geral (entre
 * eventos, ver Tarefa 8). Arquivo separado de {@link EventoService}: aquele é CRUD de evento,
 * este é leitura agregada; razões de mudar diferentes.
 */
@Service
@RequiredArgsConstructor
public class EventoRelatorioService {

    private final EventoRepository eventoRepository;
    private final InscricaoRepository inscricaoRepository;
    private final PessoaRepository pessoaRepository;

    /**
     * Relatório de UM evento: inscritos sempre aparecem; comparecimento e
     * {@code percentualIgreja} só quando {@code evento.controlaPresenca=true} — {@code null}
     * explícito no contrário, para a seção sumir inteira no front (nunca aparecer zerada).
     */
    @Transactional(readOnly = true)
    public RelatorioEventoResponse relatorioIndividual(UUID eventoId, UUID igrejaId) {
        Evento evento = eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        long pessoasInscritas = inscricaoRepository.countPessoasInscritas(eventoId);
        long convidadosInscritos = inscricaoRepository.countConvidadosInscritos(eventoId);
        var inscritos = new RelatorioEventoResponse.Inscritos(pessoasInscritas, convidadosInscritos);

        if (!evento.isControlaPresenca()) {
            return new RelatorioEventoResponse(inscritos, null, null);
        }

        long pessoasCompareceram = inscricaoRepository.countPessoasCompareceram(eventoId);
        long convidadosCompareceram = inscricaoRepository.countConvidadosCompareceram(eventoId);
        var compareceram = new RelatorioEventoResponse.Compareceram(pessoasCompareceram, convidadosCompareceram);

        // "Impacto Global": só pessoas CADASTRADAS que compareceram sobre o total de pessoas
        // ATIVAS da igreja — convidado nunca entra (nem no numerador, nem no denominador),
        // porque a base é "pessoas da igreja", não "gente que apareceu".
        long totalAtivas = pessoaRepository.countByIgrejaId(igrejaId);
        Double percentualIgreja = totalAtivas > 0
                ? arredondar((pessoasCompareceram * 100.0) / totalAtivas)
                : 0.0;

        return new RelatorioEventoResponse(inscritos, compareceram, percentualIgreja);
    }

    /** Uma casa decimal — precisão suficiente para um percentual/média de presença. */
    static double arredondar(double valor) {
        return Math.round(valor * 10.0) / 10.0;
    }
}
```

- [ ] **Step 6: Rodar e ver passar**

Run: `./mvnw test -Dtest=EventoRelatorioServiceTest`
Expected: PASS (os 3 testes deste Task).

- [ ] **Step 7: Adicionar o endpoint em `EventoController.java`**

Adicionar o `EventoRelatorioService` como dependência (`private final EventoRelatorioService eventoRelatorioService;`,
junto de `private final EventoService eventoService;`), e o endpoint, logo após `buscarPorId`:

```java
    @GetMapping("/{id}/relatorio")
    public ResponseEntity<com.domus.api.modules.evento.DTOs.RelatorioEventoResponse> relatorio(@PathVariable UUID id) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        return ResponseEntity.ok(eventoRelatorioService.relatorioIndividual(id, igrejaId));
    }
```

- [ ] **Step 8: Registrar a rota no `SecurityConfig`**

O relatório individual traz `percentualIgreja` (fatia da base de pessoas ativas) — mesma
sensibilidade de `podeVerListaCompletaDeInscritos`. Adicionar, no mesmo bloco de inscrição do
Task 6 (Step 7), ANTES do curinga `/eventos/**` genérico (verificar com
`grep -n "\"/eventos/\*\*\"" src/main/java/com/domus/api/config/SecurityConfig.java` onde o
curinga geral de eventos está, para inserir o matcher específico antes dele):

```java
                        .requestMatchers(HttpMethod.GET, "/eventos/*/relatorio")
                        .hasAnyRole(ADMIN, LIDER)
```

- [ ] **Step 9: Rodar a suíte do módulo evento**

Run: `./mvnw test -Dtest="com.domus.api.modules.evento.**"`
Expected: PASS em todos.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/BaseComparacao.java \
        src/main/java/com/domus/api/modules/evento/DTOs/RelatorioEventoResponse.java \
        src/main/java/com/domus/api/modules/evento/EventoRelatorioService.java \
        src/main/java/com/domus/api/modules/evento/EventoController.java \
        src/main/java/com/domus/api/config/SecurityConfig.java \
        src/test/java/com/domus/api/modules/evento/EventoRelatorioServiceTest.java
git commit -m "feat(evento): relatorio individual de presenca (GET /eventos/{id}/relatorio)"
```

---

## Task 8: Relatório geral — `GET /eventos/relatorio-geral`

Esta é a tarefa mais densa do plano: agrega vários eventos com os filtros do spec e calcula
quatro coisas que dependem umas das outras (resumo, evento mais popular, tendência de 6 meses,
últimos eventos com variação). Ler a Global Constraints sobre `BaseComparacao` antes de
implementar — cada variação **declara** a base usada, nunca mistura em silêncio.

**Decisões de preenchimento de lacuna do spec (documentadas em código, não deixadas implícitas):**
- `totalParticipantes` de cada "último evento": comparecimento real quando
  `controlaPresenca=true` (é o dado mais fiel disponível), inscritos confirmados senão.
- "Variação vs. média geral do filtro": usa `COMPARECIMENTO` (comparado contra a média de
  comparecimento dos eventos do filtro que controlam presença) quando o evento atual
  `controlaPresenca=true` E existe ao menos um evento do filtro com dado de comparecimento;
  cai para `INSCRITOS` (contra a média de inscritos de TODOS os eventos do filtro) senão.
- Quando o valor-base da comparação (evento anterior, ou média do filtro) é `0`, a variação
  percentual não é calculável (divisão por zero não tem significado de "cresceu/caiu") —
  omitida (`variacaoEventoAnterior=null`) ou registrada como `0%` na média geral, que sempre
  existe (nunca `null`, ao contrário da variação por evento anterior, que pode não ter par).

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Create: `src/main/java/com/domus/api/modules/evento/DTOs/RelatorioGeralResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRelatorioService.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java`
- Modify: `src/test/java/com/domus/api/modules/evento/EventoRelatorioServiceTest.java`

**Interfaces:**
- Consumes: `EventoRelatorioService` (Task 7, mesma classe/arquivo), `InscricaoRepository.contarParticipantesUnicos` (Task 5).
- Produces: `EventoRelatorioService.relatorioGeral(UUID igrejaId, LocalDateTime inicio, LocalDateTime fim, String recorteEtario, String tipo): RelatorioGeralResponse`.
  `RelatorioGeralResponse(Resumo resumo, EventoMaisPopular eventoMaisPopular, List<PontoTendencia> tendencia, List<UltimoEvento> ultimosEventos)`
  com `Resumo(long totalEventos, Double comparecimentoMedio, Long participantesUnicos)`,
  `EventoMaisPopular(UUID eventoId, String titulo, long totalInscritos)`,
  `PontoTendencia(String mes, Double comparecimentoMedio)` (mes no formato `"2026-07"`),
  `Variacao(double percentual, BaseComparacao base)`,
  `UltimoEvento(UUID eventoId, String titulo, LocalDateTime data, long totalParticipantes, Variacao variacaoEventoAnterior, Variacao variacaoMediaGeral)`.

- [ ] **Step 1: Adicionar os métodos de busca em `EventoRepository.java`**

```java
    /**
     * Eventos filtrados para o relatório geral — mais recente primeiro. Todos os filtros são
     * combináveis e opcionais (spec: Período, Recorte Etário, Tipo).
     */
    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id = :igrejaId
          AND (:inicio IS NULL OR e.inicioEm >= :inicio)
          AND (:fim IS NULL OR e.inicioEm <= :fim)
          AND (:recorteEtario IS NULL OR e.recorteEtario = :recorteEtario)
          AND (:tipo IS NULL OR e.tipo = :tipo)
        ORDER BY e.inicioEm DESC
    """)
    List<Evento> buscarParaRelatorio(@Param("igrejaId") UUID igrejaId,
                                      @Param("inicio") LocalDateTime inicio,
                                      @Param("fim") LocalDateTime fim,
                                      @Param("recorteEtario") String recorteEtario,
                                      @Param("tipo") String tipo);

    /**
     * Eventos que CONTROLAM presença, a partir de {@code desde} — alimenta o gráfico de
     * tendência (Decisão 4: só quem ativou controle de presença entra na conta; mês sem
     * nenhum evento assim vira {@code null}, nunca zero). Respeita recorte etário/tipo, mas
     * NÃO o filtro de período do relatório geral — a tendência tem sua própria janela fixa
     * de 6 meses.
     */
    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id = :igrejaId AND e.controlaPresenca = true AND e.inicioEm >= :desde
          AND (:recorteEtario IS NULL OR e.recorteEtario = :recorteEtario)
          AND (:tipo IS NULL OR e.tipo = :tipo)
        ORDER BY e.inicioEm ASC
    """)
    List<Evento> buscarComControlaPresenca(@Param("igrejaId") UUID igrejaId,
                                            @Param("desde") LocalDateTime desde,
                                            @Param("recorteEtario") String recorteEtario,
                                            @Param("tipo") String tipo);

    /**
     * "Evento anterior do mesmo tipo" (Decisão 4 do spec): o mais recente da mesma igreja,
     * mesmo {@code tipo}, com {@code inicioEm} anterior ao evento atual.
     */
    Optional<Evento> findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
            UUID igrejaId, String tipo, LocalDateTime inicioEm);
```

- [ ] **Step 2: Rodar `./mvnw compile`**

Expected: BUILD SUCCESS.

- [ ] **Step 3: Criar `RelatorioGeralResponse`**

```java
package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.BaseComparacao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RelatorioGeralResponse(
        Resumo resumo,
        EventoMaisPopular eventoMaisPopular,
        List<PontoTendencia> tendencia,
        List<UltimoEvento> ultimosEventos
) {
    /**
     * {@code comparecimentoMedio}/{@code participantesUnicos} são {@code null} quando NENHUM
     * evento do filtro controla presença — nunca {@code 0} (Decisão 4: zero mentiria "ninguém
     * foi", quando na verdade ninguém controlou).
     */
    public record Resumo(long totalEventos, Double comparecimentoMedio, Long participantesUnicos) {}

    /** Sempre baseado em inscritos confirmados (pessoa+convidado) — funciona em QUALQUER evento. */
    public record EventoMaisPopular(UUID eventoId, String titulo, long totalInscritos) {}

    /** {@code mes} no formato ISO "aaaa-mm". {@code comparecimentoMedio} null = sem dado no mês. */
    public record PontoTendencia(String mes, Double comparecimentoMedio) {}

    /** {@code base} nunca implícita — o front sempre expõe qual foi usada (tooltip/click). */
    public record Variacao(double percentual, BaseComparacao base) {}

    public record UltimoEvento(
            UUID eventoId,
            String titulo,
            LocalDateTime data,
            long totalParticipantes,
            /** {@code null} quando não há evento anterior do mesmo tipo. */
            Variacao variacaoEventoAnterior,
            Variacao variacaoMediaGeral
    ) {}
}
```

- [ ] **Step 4: Escrever os testes que falham (adicionar à classe `EventoRelatorioServiceTest`)**

Adicionar ao final da classe (mesmo `setup()`/helpers `igreja()` já existentes do Task 7):

```java
    private Evento eventoComTipo(UUID id, String tipo, LocalDateTime inicioEm, boolean controlaPresenca) {
        return Evento.builder()
                .id(id).igreja(igreja()).titulo("Evento " + tipo)
                .inicioEm(inicioEm).tipo(tipo)
                .requerInscricao(true).controlaPresenca(controlaPresenca)
                .build();
    }

    @Test
    void relatorioGeral_eventoMaisPopular_usaInscritosMesmoSemControlarPresenca() {
        UUID idPopular = UUID.randomUUID();
        UUID idMenor = UUID.randomUUID();
        Evento popular = eventoComTipo(idPopular, "Culto", LocalDateTime.now().minusDays(1), false);
        Evento menor = eventoComTipo(idMenor, "Culto", LocalDateTime.now().minusDays(2), false);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(popular, menor));
        when(inscricaoRepository.countPessoasInscritas(idPopular)).thenReturn(100L);
        when(inscricaoRepository.countConvidadosInscritos(idPopular)).thenReturn(20L);
        when(inscricaoRepository.countPessoasInscritas(idMenor)).thenReturn(10L);
        when(inscricaoRepository.countConvidadosInscritos(idMenor)).thenReturn(0L);
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of());

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null);

        assertThat(relatorio.eventoMaisPopular().eventoId()).isEqualTo(idPopular);
        assertThat(relatorio.eventoMaisPopular().totalInscritos()).isEqualTo(120);
        // Nenhum evento controla presença: resumo não mente com zero.
        assertThat(relatorio.resumo().comparecimentoMedio()).isNull();
        assertThat(relatorio.resumo().participantesUnicos()).isNull();
    }

    @Test
    void relatorioGeral_tendencia_mesSemEventoControladoVemComoNull() {
        UUID idEvento = UUID.randomUUID();
        Evento evento = eventoComTipo(idEvento, "Culto", LocalDateTime.now().withDayOfMonth(1), true);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(evento));
        when(inscricaoRepository.countPessoasInscritas(idEvento)).thenReturn(10L);
        when(inscricaoRepository.countConvidadosInscritos(idEvento)).thenReturn(0L);
        when(inscricaoRepository.countPessoasCompareceram(idEvento)).thenReturn(8L);
        when(inscricaoRepository.countConvidadosCompareceram(idEvento)).thenReturn(0L);
        // Só o mês atual tem evento com controlaPresenca=true; os outros 5 meses ficam vazios.
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of(evento));
        when(inscricaoRepository.contarParticipantesUnicos(any())).thenReturn(8L);

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null);

        assertThat(relatorio.tendencia()).hasSize(6);
        long mesesComDado = relatorio.tendencia().stream()
                .filter(p -> p.comparecimentoMedio() != null).count();
        long mesesSemDado = relatorio.tendencia().stream()
                .filter(p -> p.comparecimentoMedio() == null).count();
        assertThat(mesesComDado).isEqualTo(1);
        assertThat(mesesSemDado).isEqualTo(5); // null, NUNCA zero (Decisão 4)
    }

    @Test
    void relatorioGeral_variacao_usaComparecimento_quandoAmbosControlamPresenca() {
        UUID idAtual = UUID.randomUUID();
        UUID idAnterior = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        Evento atual = eventoComTipo(idAtual, "Retiro", agora, true);
        Evento anterior = eventoComTipo(idAnterior, "Retiro", agora.minusMonths(1), true);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.countPessoasInscritas(idAtual)).thenReturn(50L);
        when(inscricaoRepository.countConvidadosInscritos(idAtual)).thenReturn(0L);
        when(inscricaoRepository.countPessoasCompareceram(idAtual)).thenReturn(40L);
        when(inscricaoRepository.countConvidadosCompareceram(idAtual)).thenReturn(0L);
        when(eventoRepository.findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
                igrejaId, "Retiro", atual.getInicioEm())).thenReturn(java.util.Optional.of(anterior));
        when(inscricaoRepository.countPessoasCompareceram(idAnterior)).thenReturn(20L);
        when(inscricaoRepository.countConvidadosCompareceram(idAnterior)).thenReturn(0L);
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.contarParticipantesUnicos(any())).thenReturn(40L);

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null);
        var ultimoEvento = relatorio.ultimosEventos().get(0);

        // 40 presentes agora vs. 20 presentes no retiro anterior = +100%, base COMPARECIMENTO
        // (os DOIS retiros controlam presença).
        assertThat(ultimoEvento.variacaoEventoAnterior().base()).isEqualTo(BaseComparacao.COMPARECIMENTO);
        assertThat(ultimoEvento.variacaoEventoAnterior().percentual()).isEqualTo(100.0);
    }

    @Test
    void relatorioGeral_variacao_caiParaInscritos_quandoUmDosDoisNaoControlaPresenca() {
        UUID idAtual = UUID.randomUUID();
        UUID idAnterior = UUID.randomUUID();
        LocalDateTime agora = LocalDateTime.now();
        // Atual controla presença; o ANTERIOR não — não dá pra comparar comparecimento com
        // comparecimento porque o anterior não tem esse dado.
        Evento atual = eventoComTipo(idAtual, "Retiro", agora, true);
        Evento anterior = eventoComTipo(idAnterior, "Retiro", agora.minusMonths(1), false);

        when(eventoRepository.buscarParaRelatorio(eq(igrejaId), any(), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.countPessoasInscritas(idAtual)).thenReturn(50L);
        when(inscricaoRepository.countConvidadosInscritos(idAtual)).thenReturn(0L);
        when(inscricaoRepository.countPessoasCompareceram(idAtual)).thenReturn(40L);
        when(inscricaoRepository.countConvidadosCompareceram(idAtual)).thenReturn(0L);
        when(eventoRepository.findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(
                igrejaId, "Retiro", atual.getInicioEm())).thenReturn(java.util.Optional.of(anterior));
        when(inscricaoRepository.countPessoasInscritas(idAnterior)).thenReturn(25L);
        when(inscricaoRepository.countConvidadosInscritos(idAnterior)).thenReturn(0L);
        when(eventoRepository.buscarComControlaPresenca(eq(igrejaId), any(), any(), any()))
                .thenReturn(java.util.List.of(atual));
        when(inscricaoRepository.contarParticipantesUnicos(any())).thenReturn(40L);

        var relatorio = service.relatorioGeral(igrejaId, null, null, null, null);
        var ultimoEvento = relatorio.ultimosEventos().get(0);

        // 50 inscritos agora vs. 25 inscritos no retiro anterior = +100%, base INSCRITOS
        // (o anterior não controla presença — não dá pra comparar comparecimento com quem não tem).
        assertThat(ultimoEvento.variacaoEventoAnterior().base()).isEqualTo(BaseComparacao.INSCRITOS);
        assertThat(ultimoEvento.variacaoEventoAnterior().percentual()).isEqualTo(100.0);
    }
```

Adicionar os imports necessários no topo do arquivo de teste: `import static org.mockito.ArgumentMatchers.eq;`
(o `any()` já deve estar importado do Task 7; se não estiver, adicionar
`import static org.mockito.ArgumentMatchers.any;`).

- [ ] **Step 5: Rodar e ver falhar**

Run: `./mvnw test -Dtest=EventoRelatorioServiceTest`
Expected: FAIL — `service.relatorioGeral(...)` não existe (erro de compilação).

- [ ] **Step 6: Implementar `relatorioGeral` em `EventoRelatorioService.java`**

Adicionar ao final da classe (após `relatorioIndividual`), junto dos imports necessários no
topo (`java.time.YearMonth`, `java.util.ArrayList`, `java.util.Comparator`, `java.util.HashMap`,
`java.util.List`, `java.util.Map`, `com.domus.api.modules.evento.DTOs.RelatorioGeralResponse`,
`com.domus.api.modules.evento.BaseComparacao`):

```java
    /**
     * Relatório entre vários eventos, com filtros combináveis e opcionais (Período, Recorte
     * Etário, Tipo). Ver Decisão 4 do spec: comparecimento médio/participantes
     * únicos/tendência só existem entre eventos com {@code controlaPresenca=true}; evento
     * mais popular e (parte de) variação caem para inscritos confirmados quando faltar dado
     * de comparecimento — sempre com a base declarada, nunca implícita.
     */
    @Transactional(readOnly = true)
    public RelatorioGeralResponse relatorioGeral(UUID igrejaId, LocalDateTime inicio, LocalDateTime fim,
                                                  String recorteEtario, String tipo) {
        List<Evento> eventosFiltrados = eventoRepository.buscarParaRelatorio(igrejaId, inicio, fim, recorteEtario, tipo);

        // Totais por evento calculados uma vez só e reusados no resumo, no mais popular e
        // nos últimos eventos — evita recontar a mesma coisa três vezes.
        Map<UUID, Long> totalInscritosPorEvento = new HashMap<>();
        Map<UUID, Long> totalCompareceramPorEvento = new HashMap<>(); // só entra quem controlaPresenca=true
        for (Evento e : eventosFiltrados) {
            long inscritos = inscricaoRepository.countPessoasInscritas(e.getId())
                    + inscricaoRepository.countConvidadosInscritos(e.getId());
            totalInscritosPorEvento.put(e.getId(), inscritos);

            if (e.isControlaPresenca()) {
                long compareceram = inscricaoRepository.countPessoasCompareceram(e.getId())
                        + inscricaoRepository.countConvidadosCompareceram(e.getId());
                totalCompareceramPorEvento.put(e.getId(), compareceram);
            }
        }

        RelatorioGeralResponse.Resumo resumo = montarResumo(eventosFiltrados, totalCompareceramPorEvento);
        RelatorioGeralResponse.EventoMaisPopular maisPopular = eventoMaisPopular(eventosFiltrados, totalInscritosPorEvento);
        List<RelatorioGeralResponse.PontoTendencia> tendencia = montarTendencia(igrejaId, recorteEtario, tipo, totalCompareceramPorEvento);
        List<RelatorioGeralResponse.UltimoEvento> ultimosEventos = montarUltimosEventos(
                igrejaId, eventosFiltrados, totalInscritosPorEvento, totalCompareceramPorEvento);

        return new RelatorioGeralResponse(resumo, maisPopular, tendencia, ultimosEventos);
    }

    private RelatorioGeralResponse.Resumo montarResumo(List<Evento> eventosFiltrados,
                                                        Map<UUID, Long> totalCompareceramPorEvento) {
        Double comparecimentoMedio = totalCompareceramPorEvento.isEmpty() ? null
                : arredondar(totalCompareceramPorEvento.values().stream().mapToLong(Long::longValue).average().orElse(0));

        Long participantesUnicos = totalCompareceramPorEvento.isEmpty() ? null
                : inscricaoRepository.contarParticipantesUnicos(new ArrayList<>(totalCompareceramPorEvento.keySet()));

        return new RelatorioGeralResponse.Resumo(eventosFiltrados.size(), comparecimentoMedio, participantesUnicos);
    }

    /** Sempre por inscritos confirmados — funciona em QUALQUER evento, com ou sem controle de presença. */
    private RelatorioGeralResponse.EventoMaisPopular eventoMaisPopular(List<Evento> eventosFiltrados,
                                                                        Map<UUID, Long> totalInscritosPorEvento) {
        return eventosFiltrados.stream()
                .max(Comparator.comparingLong(e -> totalInscritosPorEvento.get(e.getId())))
                .map(e -> new RelatorioGeralResponse.EventoMaisPopular(
                        e.getId(), e.getTitulo(), totalInscritosPorEvento.get(e.getId())))
                .orElse(null);
    }

    /**
     * 6 meses fixos (o atual + 5 anteriores), independente do filtro de período — só o
     * recorte etário/tipo se aplicam aqui. Mês sem NENHUM evento com controlaPresenca=true
     * vira {@code null} explícito (nunca {@code 0} — zero mentiria "ninguém foi").
     */
    private List<RelatorioGeralResponse.PontoTendencia> montarTendencia(
            UUID igrejaId, String recorteEtario, String tipo, Map<UUID, Long> totalCompareceramJaCalculado) {
        YearMonth mesAtual = YearMonth.now();
        LocalDateTime desde = mesAtual.minusMonths(5).atDay(1).atStartOfDay();

        List<Evento> eventosControlados = eventoRepository.buscarComControlaPresenca(igrejaId, desde, recorteEtario, tipo);

        Map<YearMonth, List<Long>> comparecimentosPorMes = new HashMap<>();
        for (Evento e : eventosControlados) {
            long compareceram = totalCompareceramJaCalculado.containsKey(e.getId())
                    ? totalCompareceramJaCalculado.get(e.getId())
                    : inscricaoRepository.countPessoasCompareceram(e.getId())
                        + inscricaoRepository.countConvidadosCompareceram(e.getId());
            YearMonth mes = YearMonth.from(e.getInicioEm());
            comparecimentosPorMes.computeIfAbsent(mes, k -> new ArrayList<>()).add(compareceram);
        }

        List<RelatorioGeralResponse.PontoTendencia> tendencia = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth mes = mesAtual.minusMonths(i);
            List<Long> valores = comparecimentosPorMes.get(mes);
            Double media = (valores == null || valores.isEmpty()) ? null
                    : arredondar(valores.stream().mapToLong(Long::longValue).average().orElse(0));
            tendencia.add(new RelatorioGeralResponse.PontoTendencia(mes.toString(), media));
        }
        return tendencia;
    }

    private List<RelatorioGeralResponse.UltimoEvento> montarUltimosEventos(
            UUID igrejaId, List<Evento> eventosFiltrados,
            Map<UUID, Long> totalInscritosPorEvento, Map<UUID, Long> totalCompareceramPorEvento) {

        double mediaInscritosFiltro = eventosFiltrados.isEmpty() ? 0
                : totalInscritosPorEvento.values().stream().mapToLong(Long::longValue).average().orElse(0);
        Double mediaComparecimentoFiltro = totalCompareceramPorEvento.isEmpty() ? null
                : totalCompareceramPorEvento.values().stream().mapToLong(Long::longValue).average().orElse(0);

        List<RelatorioGeralResponse.UltimoEvento> ultimosEventos = new ArrayList<>();
        for (Evento e : eventosFiltrados) {
            boolean controlaAtual = e.isControlaPresenca();
            long totalInscritosAtual = totalInscritosPorEvento.get(e.getId());
            Long totalCompareceuAtual = totalCompareceramPorEvento.get(e.getId());

            // "totalParticipantes": comparecimento real quando existe (dado mais fiel),
            // inscritos confirmados senão.
            long totalParticipantes = (controlaAtual && totalCompareceuAtual != null)
                    ? totalCompareceuAtual : totalInscritosAtual;

            RelatorioGeralResponse.Variacao variacaoAnterior = calcularVariacaoEventoAnterior(
                    igrejaId, e, controlaAtual, totalInscritosAtual, totalCompareceuAtual);
            RelatorioGeralResponse.Variacao variacaoMedia = calcularVariacaoMediaGeral(
                    controlaAtual, totalInscritosAtual, totalCompareceuAtual, mediaInscritosFiltro, mediaComparecimentoFiltro);

            ultimosEventos.add(new RelatorioGeralResponse.UltimoEvento(
                    e.getId(), e.getTitulo(), e.getInicioEm(), totalParticipantes, variacaoAnterior, variacaoMedia));
        }
        return ultimosEventos;
    }

    private RelatorioGeralResponse.Variacao calcularVariacaoEventoAnterior(
            UUID igrejaId, Evento atual, boolean controlaAtual, long totalInscritosAtual, Long totalCompareceuAtual) {
        if (atual.getTipo() == null) return null;

        Evento anterior = eventoRepository
                .findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc(igrejaId, atual.getTipo(), atual.getInicioEm())
                .orElse(null);
        if (anterior == null) return null;

        boolean usaComparecimento = controlaAtual && anterior.isControlaPresenca();
        long valorAtual = usaComparecimento
                ? (totalCompareceuAtual != null ? totalCompareceuAtual : 0)
                : totalInscritosAtual;
        long valorAnterior = usaComparecimento
                ? inscricaoRepository.countPessoasCompareceram(anterior.getId()) + inscricaoRepository.countConvidadosCompareceram(anterior.getId())
                : inscricaoRepository.countPessoasInscritas(anterior.getId()) + inscricaoRepository.countConvidadosInscritos(anterior.getId());

        if (valorAnterior == 0) return null; // divisão por zero não tem "cresceu/caiu" que faça sentido

        double percentual = arredondar(((valorAtual - valorAnterior) * 100.0) / valorAnterior);
        return new RelatorioGeralResponse.Variacao(percentual, usaComparecimento ? BaseComparacao.COMPARECIMENTO : BaseComparacao.INSCRITOS);
    }

    private RelatorioGeralResponse.Variacao calcularVariacaoMediaGeral(
            boolean controlaAtual, long totalInscritosAtual, Long totalCompareceuAtual,
            double mediaInscritosFiltro, Double mediaComparecimentoFiltro) {

        if (controlaAtual && mediaComparecimentoFiltro != null && mediaComparecimentoFiltro > 0) {
            long valorAtual = totalCompareceuAtual != null ? totalCompareceuAtual : 0;
            double percentual = arredondar(((valorAtual - mediaComparecimentoFiltro) * 100.0) / mediaComparecimentoFiltro);
            return new RelatorioGeralResponse.Variacao(percentual, BaseComparacao.COMPARECIMENTO);
        }
        if (mediaInscritosFiltro > 0) {
            double percentual = arredondar(((totalInscritosAtual - mediaInscritosFiltro) * 100.0) / mediaInscritosFiltro);
            return new RelatorioGeralResponse.Variacao(percentual, BaseComparacao.INSCRITOS);
        }
        // Filtro com um evento só (média = o próprio evento) ou vazio: sem variação real, 0%.
        return new RelatorioGeralResponse.Variacao(0.0, BaseComparacao.INSCRITOS);
    }
```

- [ ] **Step 7: Rodar e ver passar**

Run: `./mvnw test -Dtest=EventoRelatorioServiceTest`
Expected: PASS em todos os testes do Task 7 e do Task 8.

- [ ] **Step 8: Adicionar o endpoint em `EventoController.java`**

```java
    @GetMapping("/relatorio-geral")
    public ResponseEntity<com.domus.api.modules.evento.DTOs.RelatorioGeralResponse> relatorioGeral(
            @RequestParam(required = false) java.time.LocalDateTime inicio,
            @RequestParam(required = false) java.time.LocalDateTime fim,
            @RequestParam(required = false) String recorteEtario,
            @RequestParam(required = false) String tipo) {
        UUID igrejaId = usuarioAutenticado.getIgrejaId();
        String recorteFiltro = (recorteEtario == null || recorteEtario.isBlank()) ? null : recorteEtario.trim();
        String tipoFiltro = (tipo == null || tipo.isBlank()) ? null : tipo.trim();
        return ResponseEntity.ok(eventoRelatorioService.relatorioGeral(igrejaId, inicio, fim, recorteFiltro, tipoFiltro));
    }
```

Adicionar ANTES de `@GetMapping("/{id}")` no arquivo (mesma armadilha de ordenação de rota já
documentada no `GET /eventos/tipos`: matcher específico `/relatorio-geral` precisa vir antes
do curinga `/{id}`, senão o Spring tentaria interpretar "relatorio-geral" como um `UUID` de
`id` e devolveria 400 de conversão de tipo).

- [ ] **Step 9: Registrar a rota no `SecurityConfig`**

Adicionar junto do matcher do Task 7 (Step 8):

```java
                        .requestMatchers(HttpMethod.GET, "/eventos/relatorio-geral")
                        .hasAnyRole(ADMIN, LIDER)
```

ANTES do curinga `/eventos/**`, e também antes de qualquer matcher de `/eventos/{id}` que use
`hasAnyRole(ADMIN, LIDER, COMUM)` — mesma armadilha de ordenação (`/eventos/tipos` já resolve
o mesmo problema no controller; aqui é o equivalente no `SecurityConfig`).

- [ ] **Step 10: Rodar a suíte inteira do backend**

Run: `./mvnw test`
Expected: BUILD SUCCESS — nenhuma regressão nos módulos de evento, inscrição ou qualquer outro.

- [ ] **Step 11: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoRepository.java \
        src/main/java/com/domus/api/modules/evento/DTOs/RelatorioGeralResponse.java \
        src/main/java/com/domus/api/modules/evento/EventoRelatorioService.java \
        src/main/java/com/domus/api/modules/evento/EventoController.java \
        src/main/java/com/domus/api/config/SecurityConfig.java \
        src/test/java/com/domus/api/modules/evento/EventoRelatorioServiceTest.java
git commit -m "feat(evento): relatorio geral de engajamento (GET /eventos/relatorio-geral)"
```

---

## Task 9: Frontend — tipos, endpoints e services

**Files:**
- Modify: `frontend/src/types/evento.type.ts`
- Modify: `frontend/src/types/inscricao.type.ts`
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/services/evento.service.ts`
- Modify: `frontend/src/services/inscricao.service.ts`

**Interfaces:**
- Consumes: nada (primeira tarefa de front; espelha os DTOs do back das Tarefas 4, 6, 7, 8).
- Produces: `EventoResponse.controlaPresenca: boolean`, `EventoRequest.controlaPresenca?: boolean`,
  `RelatorioEventoResponse`, `RelatorioGeralResponse`, `BaseComparacao` (tipo),
  `InscritoResponse.compareceu: boolean`, `AcompanhanteResponse.compareceu: boolean`,
  `eventosService.relatorio(id)`, `eventosService.relatorioGeral(filtros)`,
  `inscricoesService.marcarTodosPresentes(eventoId)`,
  `inscricoesService.marcarPresencaInscricao(eventoId, inscricaoId, compareceu)`,
  `inscricoesService.marcarPresencaAcompanhante(eventoId, acompanhanteId, compareceu)` —
  consumidos pelas Tarefas 10-15.

Este é um Task só de tipos/rede (sem lógica própria a testar em isolamento) — o projeto não
tem suíte de teste automatizado para a camada `services`/`types` do front (verificado: não há
`*.test.ts` ao lado de nenhum arquivo em `services/`). A verificação é `tsc` (compilação) mais
o `ESLint` do projeto, e a cobertura funcional real vem das Tarefas 10-15 (hooks/UI), que
exercitam esta camada.

- [ ] **Step 1: Adicionar `controlaPresenca` em `EventoResponse`/`EventoRequest` (`evento.type.ts`)**

Em `EventoResponse`, logo após `requerInscricao: boolean`:

```typescript
  requerInscricao: boolean
  controlaPresenca: boolean
```

Em `EventoRequest`, logo após `requerInscricao?: boolean`:

```typescript
  requerInscricao?: boolean
  /** Só pode ser true quando `requerInscricao` também é — backend recusa a combinação inversa. */
  controlaPresenca?: boolean
```

- [ ] **Step 2: Adicionar os tipos do relatório em `evento.type.ts`**

Ao final do arquivo:

```typescript
/** Mesmos dois valores de `com.domus.api.modules.evento.BaseComparacao` (back) — união de
 *  tipos, nunca string crua, para o front nunca comparar por texto solto. */
export type BaseComparacao = 'COMPARECIMENTO' | 'INSCRITOS'

/** Espelha `RelatorioEventoResponse` (relatório individual, modal/página de inscritos). */
export interface RelatorioEventoResponse {
  inscritos: { pessoas: number; convidados: number }
  /** null quando o evento não controla presença — a seção de comparecimento some inteira. */
  compareceram: { pessoas: number; convidados: number } | null
  /** null pela mesma razão de `compareceram`. */
  percentualIgreja: number | null
}

/** Uma variação (evento anterior do mesmo tipo, ou média geral do filtro) com a base explícita. */
export interface VariacaoRelatorio {
  percentual: number
  base: BaseComparacao
}

export interface EventoMaisPopular {
  eventoId: string
  titulo: string
  totalInscritos: number
}

/** Um ponto do gráfico de tendência. `comparecimentoMedio` null = mês sem evento controlado. */
export interface PontoTendencia {
  /** Formato ISO "aaaa-mm", ex.: "2026-07". */
  mes: string
  comparecimentoMedio: number | null
}

export interface UltimoEventoRelatorio {
  eventoId: string
  titulo: string
  data: string
  totalParticipantes: number
  /** null quando não existe evento anterior do mesmo tipo. */
  variacaoEventoAnterior: VariacaoRelatorio | null
  variacaoMediaGeral: VariacaoRelatorio
}

/** Espelha `RelatorioGeralResponse` (página `/eventos/relatorio`). */
export interface RelatorioGeralResponse {
  resumo: {
    totalEventos: number
    /** null quando nenhum evento do filtro controla presença — nunca 0 (não mentir "ninguém foi"). */
    comparecimentoMedio: number | null
    participantesUnicos: number | null
  }
  eventoMaisPopular: EventoMaisPopular | null
  tendencia: PontoTendencia[]
  ultimosEventos: UltimoEventoRelatorio[]
}

/** Filtros combináveis e opcionais do relatório geral. */
export interface RelatorioGeralFiltros {
  inicio?: string
  fim?: string
  recorteEtario?: string
  tipo?: string
}
```

- [ ] **Step 3: Adicionar `compareceu` em `InscritoResponse`/`AcompanhanteResponse` (`inscricao.type.ts`)**

Em `AcompanhanteResponse`:

```typescript
export interface AcompanhanteResponse {
  id: string
  nome: string
  telefone: string | null
  /** Só significativo quando o evento controla presença — ver `EventoResponse.controlaPresenca`. */
  compareceu: boolean
}
```

Em `InscritoResponse`, logo após `inscritoEm: string`:

```typescript
  inscritoEm: string
  /** Só significativo quando o evento controla presença. */
  compareceu: boolean
  acompanhantes: AcompanhanteResponse[]
```

- [ ] **Step 4: Adicionar as rotas em `endpoints.ts`**

Em `eventos`, logo após `ELEGIBILIDADE`:

```typescript
    RELATORIO: (id: string) => `/eventos/${id}/relatorio`,
    RELATORIO_GERAL: '/eventos/relatorio-geral',
```

Em `inscricoes`, logo após `REMOVER_ACOMPANHANTE`:

```typescript
  },

  presenca: {
    MARCAR_TODOS: (eventoId: string) => `/eventos/${eventoId}/presenca/marcar-todos`,
    INSCRICAO: (eventoId: string, inscricaoId: string) =>
      `/eventos/${eventoId}/presenca/inscricoes/${inscricaoId}`,
    ACOMPANHANTE: (eventoId: string, acompanhanteId: string) =>
      `/eventos/${eventoId}/presenca/acompanhantes/${acompanhanteId}`,
```

(o `},` extra fecha o bloco `inscricoes` existente antes de abrir o bloco `presenca` novo —
ao editar, confirmar que a chave de fechamento de `inscricoes` não duplica.)

- [ ] **Step 5: Adicionar `relatorio`/`relatorioGeral` em `evento.service.ts`**

```typescript
import type {
  EventoRequest, EventoResponse, ImpactoRestricaoResponse,
  RelatorioEventoResponse, RelatorioGeralResponse, RelatorioGeralFiltros,
} from '@/types/evento.type'
```

(substituir o `import type` existente por este, que só acrescenta os três tipos novos.)

No objeto `eventosService`, ao final:

```typescript
  relatorio: (id: string): Promise<RelatorioEventoResponse> =>
    api.get<RelatorioEventoResponse>(Endpoints.eventos.RELATORIO(id)).then(res => res.data),

  relatorioGeral: (filtros: RelatorioGeralFiltros): Promise<RelatorioGeralResponse> =>
    api.get<RelatorioGeralResponse>(Endpoints.eventos.RELATORIO_GERAL, {
      params: {
        inicio: filtros.inicio || undefined,
        fim: filtros.fim || undefined,
        recorteEtario: filtros.recorteEtario || undefined,
        tipo: filtros.tipo || undefined,
      },
    }).then(res => res.data),
```

- [ ] **Step 6: Adicionar os três métodos de presença em `inscricao.service.ts`**

Ao final do objeto `inscricoesService`:

```typescript
  marcarTodosPresentes: (eventoId: string): Promise<void> =>
    api.post(Endpoints.inscricoes.presenca.MARCAR_TODOS(eventoId)).then(() => undefined),

  marcarPresencaInscricao: (eventoId: string, inscricaoId: string, compareceu: boolean): Promise<void> =>
    api.patch(Endpoints.inscricoes.presenca.INSCRICAO(eventoId, inscricaoId), { compareceu })
      .then(() => undefined),

  marcarPresencaAcompanhante: (eventoId: string, acompanhanteId: string, compareceu: boolean): Promise<void> =>
    api.patch(Endpoints.inscricoes.presenca.ACOMPANHANTE(eventoId, acompanhanteId), { compareceu })
      .then(() => undefined),
```

- [ ] **Step 7: Verificar que o projeto compila**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos (os campos adicionados são todos aditivos; se algum outro arquivo
construir `EventoResponse`/`InscritoResponse`/`AcompanhanteResponse` manualmente — ex.: mocks
de teste — adicionar `controlaPresenca`/`compareceu` lá também).

- [ ] **Step 8: Commit**

```bash
cd frontend
git add src/types/evento.type.ts src/types/inscricao.type.ts src/lib/endpoints.ts \
        src/services/evento.service.ts src/services/inscricao.service.ts
git commit -m "feat(evento): tipos, rotas e services do relatorio de presenca e engajamento"
```

---

## Task 10: Frontend — toggle "Controlar presença" no formulário de evento

**Files:**
- Modify: `frontend/src/lib/validators.ts`
- Modify: `frontend/src/hooks/evento/useEventoForm.ts`
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx`

**Interfaces:**
- Consumes: `EventoRequest.controlaPresenca` (Tarefa 9).
- Produces: `EventoFormData.controlaPresenca: boolean` — consumido só dentro do próprio form (sem tarefa downstream).

- [ ] **Step 1: Adicionar `controlaPresenca` ao `eventoSchemaBase` (`validators.ts`)**

Logo após `requerInscricao: z.boolean().default(false),`:

```typescript
  requerInscricao: z.boolean().default(false),
  /**
   * Só pode ser true quando `requerInscricao` também é — o toggle no form fica desabilitado
   * (e forçado a false) enquanto `requerInscricao=false`, espelhando o CHECK do banco.
   */
  controlaPresenca: z.boolean().default(false),
```

- [ ] **Step 2: Reidratar e enviar `controlaPresenca` em `useEventoForm.ts`**

No `defaultValues` do `useAppForm`, logo após `requerInscricao: false,`:

```typescript
      requerInscricao: false,
      controlaPresenca: false,
```

No `useEffect` de `reset(...)` (reidratação em edição), logo após `requerInscricao: eventoInicial.requerInscricao,`:

```typescript
        requerInscricao: eventoInicial.requerInscricao,
        controlaPresenca: eventoInicial.controlaPresenca,
```

No `payload` montado em `onSubmit`, logo após `requerInscricao: data.requerInscricao,`:

```typescript
        requerInscricao: data.requerInscricao,
        // Mesmo raciocínio de vagas/preço: sempre enviado com o valor atual do form (nunca
        // omitido condicionalmente), mesmo quando a seção está escondida — o PUT substitui a
        // entidade inteira e um campo ausente vira false. Forçado a false quando
        // requerInscricao=false: o toggle já fica desabilitado nesse estado (Step 3), mas o
        // valor no form pode ter sobrevivido de uma edição anterior.
        controlaPresenca: data.requerInscricao ? data.controlaPresenca : false,
```

- [ ] **Step 3: Adicionar o toggle na seção "Inscrições" do `EventoForm.tsx`**

Adicionar `watch('controlaPresenca')` junto aos outros `watch`, logo após
`const exclusivoMembros = watch('exclusivoMembros')`:

```typescript
  const controlaPresenca = watch('controlaPresenca')
```

Adicionar o `ClipboardCheck` ao import de ícones (linha 4):

```typescript
import { CalendarClock, FileText, MapPin, Info, Ticket, UserCog, ClipboardCheck } from 'lucide-react'
```

Dentro do bloco `{requerInscricao && (<div className={styles.campos}>...)}`, logo após o
`</div>` que fecha o bloco `grupoData`/segmentado de tipo de inscrição (o mesmo `<div>` que
contém "TIPO DE INSCRIÇÃO" e os botões Gratuito/Pago) e ANTES do bloco de preço condicional
(`{tipoInscricao === 'PAGO' && (...)}`), adicionar:

```tsx
                <label className={styles.toggleRow}>
                  <span className={styles.toggleTexto}>
                    <span className={styles.toggleTitulo}>
                      <ClipboardCheck size={16} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
                      Controlar presença
                    </span>
                    <span className={styles.toggleDescricao}>
                      Ative para marcar quem realmente compareceu e ver o relatório de presença deste evento.
                    </span>
                  </span>
                  <span className={styles.switch}>
                    <input type="checkbox" className={styles.switchInput} {...register('controlaPresenca')} />
                    <span className={styles.switchTrilho} />
                  </span>
                </label>
```

> Nota: este toggle FICA DENTRO do `{requerInscricao && (...)}`, então desaparece (e some do
> DOM) quando `requerInscricao` é desligado — o que já zera visualmente a intenção, mas o
> valor no form pode sobreviver (RHF não desmonta o registro, mesmo raciocínio documentado em
> `useEventoForm.ts`). É por isso que o Step 2 força `controlaPresenca: false` no payload
> quando `requerInscricao` é false — a UI e o payload enviado ficam consistentes mesmo que o
> valor "fantasma" do form continue true internamente.

- [ ] **Step 4: Rodar o lint/typecheck do front**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/EventoForm.tsx src/hooks/evento/useEventoForm.ts src/lib/validators.ts`
Expected: sem erros.

- [ ] **Step 5: Testar manualmente no navegador (mobile + desktop)**

Abrir `/eventos/cadastrar`, ligar "Requer inscrição prévia", confirmar que "Controlar
presença" aparece logo abaixo do segmentado Gratuito/Pago; desligar "Requer inscrição prévia"
e confirmar que o toggle some junto. Redimensionar para largura de celular (~375px) e
confirmar que a linha do toggle não estoura horizontalmente (mesmo padrão visual do toggle
"Requer inscrição prévia" vizinho, que já é responsivo).

- [ ] **Step 6: Commit**

```bash
cd frontend
git add src/lib/validators.ts src/hooks/evento/useEventoForm.ts src/components/module/eventos/EventoForm.tsx
git commit -m "feat(evento): toggle Controlar presenca no formulario, condicionado a requer inscricao"
```

---

## Task 11: Frontend — hooks de marcar presença

**Files:**
- Create: `frontend/src/hooks/inscricao/useMarcarTodosPresentes.ts`
- Create: `frontend/src/hooks/inscricao/useMarcarPresencaInscricao.ts`
- Create: `frontend/src/hooks/inscricao/useMarcarPresencaAcompanhante.ts`

**Interfaces:**
- Consumes: `inscricoesService.marcarTodosPresentes/marcarPresencaInscricao/marcarPresencaAcompanhante` (Tarefa 9).
- Produces: os três hooks — consumidos pela Tarefa 12 (lista de inscritos).

- [ ] **Step 1: `useMarcarTodosPresentes.ts`**

```typescript
'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** Botão "marcar todos vieram" — presença em lote de todo inscrito confirmado + acompanhantes. */
export function useMarcarTodosPresentes(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => inscricoesService.marcarTodosPresentes(eventoId),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      queryClient.invalidateQueries({ queryKey: ['relatorio-evento', eventoId] })
      notificar.sucesso('Presença marcada para todos os inscritos.')
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error) ? error.response?.data?.message : undefined
      notificar.erro('Não foi possível marcar presença', mensagem ?? 'Tente novamente.')
    },
  })
}
```

- [ ] **Step 2: `useMarcarPresencaInscricao.ts`**

```typescript
'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/**
 * Corrige a exceção de UM inscrito específico (o checkbox individual da lista) — depois de
 * um "marcar todos", ou independentemente dele.
 */
export function useMarcarPresencaInscricao(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ inscricaoId, compareceu }: { inscricaoId: string; compareceu: boolean }) =>
      inscricoesService.marcarPresencaInscricao(eventoId, inscricaoId, compareceu),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      queryClient.invalidateQueries({ queryKey: ['relatorio-evento', eventoId] })
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error) ? error.response?.data?.message : undefined
      notificar.erro('Não foi possível marcar presença', mensagem ?? 'Tente novamente.')
    },
  })
}
```

- [ ] **Step 3: `useMarcarPresencaAcompanhante.ts`**

```typescript
'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'

/** Mesma correção pontual de `useMarcarPresencaInscricao`, só que para UM convidado. */
export function useMarcarPresencaAcompanhante(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ acompanhanteId, compareceu }: { acompanhanteId: string; compareceu: boolean }) =>
      inscricoesService.marcarPresencaAcompanhante(eventoId, acompanhanteId, compareceu),
    onSuccess: () => {
      invalidarCache(queryClient, 'inscricao')
      queryClient.invalidateQueries({ queryKey: ['relatorio-evento', eventoId] })
    },
    onError: (error: unknown) => {
      const mensagem = axios.isAxiosError<ApiError>(error) ? error.response?.data?.message : undefined
      notificar.erro('Não foi possível marcar presença', mensagem ?? 'Tente novamente.')
    },
  })
}
```

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/hooks/inscricao/useMarcarTodosPresentes.ts \
        src/hooks/inscricao/useMarcarPresencaInscricao.ts \
        src/hooks/inscricao/useMarcarPresencaAcompanhante.ts
git commit -m "feat(inscricao): hooks de marcar presenca (lote, inscrito, acompanhante)"
```

---

## Task 12: Frontend — coluna de presença + "marcar todos vieram" na lista de inscritos

**Files:**
- Modify: `frontend/src/app/(app)/eventos/[id]/inscritos/page.tsx`
- Modify: `frontend/src/app/(app)/eventos/[id]/inscritos/inscritos.module.css`

**Interfaces:**
- Consumes: `useMarcarTodosPresentes`/`useMarcarPresencaInscricao`/`useMarcarPresencaAcompanhante` (Tarefa 11), `InscritoResponse.compareceu`/`AcompanhanteResponse.compareceu` (Tarefa 9), `evento.controlaPresenca` (Tarefa 9), `ModalConfirmacao` (já existe em `@/components/common/ModalConfirmacao/ModalConfirmacao`).
- Produces: nada consumido por tarefa seguinte (é folha da árvore de dependência do front, junto com a Tarefa 13).

- [ ] **Step 1: Adicionar a coluna "Presença" ao CSS (`inscritos.module.css`)**

Logo após a regra `.tabelaHeader { ... }`, adicionar uma variante com presença (6 colunas
em vez de 5 — a coluna nova fica ENTRE convidados e ações):

```css
.tabelaHeaderComPresenca,
.linhaComPresenca {
  grid-template-columns: 2fr 1fr 1.2fr 1fr 90px 100px;
}
```

Logo após `.colConvidados { ... }`, adicionar:

```css
.colPresenca {
  display: flex;
  align-items: center;
  justify-content: center;
}
.checkboxPresenca {
  width: 20px;
  height: 20px;
  cursor: pointer;
  accent-color: var(--color-primary);
}
.botaoMarcarTodos {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border-input);
  background: var(--color-bg-white);
  color: var(--color-text-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
}
.botaoMarcarTodos:hover {
  background: var(--color-bg-page);
}
.botaoMarcarTodos:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
```

Dentro do bloco `@media (max-width: 900px) { ... }`, logo após `.colConvidados { grid-area: convidados; }`,
adicionar a variante mobile (presença fica ao lado de ações, ambas na mesma "linha" superior
do card, mesmo espírito de `"participante acoes"`):

```css
  .linhaComPresenca {
    grid-template-areas:
      "participante acoes"
      "presenca acoes"
      "data data"
      "inscritopor convidados";
  }
  .colPresenca { grid-area: presenca; justify-content: flex-start; gap: 6px; }
  .colPresenca::before {
    content: "Compareceu";
    font-size: var(--font-size-xs);
    color: var(--color-text-muted);
  }
```

- [ ] **Step 2: Escrever o teste manual do cenário (roteiro, sem suíte automatizada de front — mesma situação da Tarefa 9)**

Este projeto não tem testes de componente React configurados (`grep -rn "@testing-library"
package.json` não retorna nada) — a verificação desta tarefa é `tsc`/`eslint` + roteiro manual
no navegador (Step 6). Não invente uma suíte Jest/RTL nova só para esta tarefa; seguir o
padrão do projeto.

- [ ] **Step 3: Importar os hooks novos e o estado de "marcar todos" em `page.tsx`**

No topo do arquivo, junto dos outros imports de hooks:

```typescript
import { CheckCircle2 } from 'lucide-react'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useMarcarTodosPresentes } from '@/hooks/inscricao/useMarcarTodosPresentes'
import { useMarcarPresencaInscricao } from '@/hooks/inscricao/useMarcarPresencaInscricao'
import { useMarcarPresencaAcompanhante } from '@/hooks/inscricao/useMarcarPresencaAcompanhante'
```

(o import de `ChevronRight, Users, Ticket, Armchair, UserPlus, ArrowLeft` já existe — adicionar
`CheckCircle2` a essa mesma linha de import do `lucide-react` em vez de criar uma segunda
linha.)

- [ ] **Step 4: Declarar os hooks e o estado de confirmação, dentro de `InscritosPage`**

Logo após `const removerConvidado = useRemoverConvidado()`:

```typescript
  const marcarTodos = useMarcarTodosPresentes(eventoId)
  const marcarPresencaInscricao = useMarcarPresencaInscricao(eventoId)
  const marcarPresencaAcompanhante = useMarcarPresencaAcompanhante(eventoId)
  const [confirmarMarcarTodos, setConfirmarMarcarTodos] = useState(false)
```

(o `useState` já está importado no topo do arquivo — reusar o import existente.)

- [ ] **Step 5: Adicionar o botão "Marcar todos vieram" no cabeçalho, condicionado a `controlaPresenca`**

No `<header className={styles.cabecalho}>`, logo ANTES do `<button ... Nova Inscrição>`
existente, adicionar (só quando o evento controla presença e há inscritos):

```tsx
          {evento?.controlaPresenca && lista && lista.inscritos.length > 0 && (
            <button
              type="button"
              className={styles.botaoMarcarTodos}
              onClick={() => setConfirmarMarcarTodos(true)}
              disabled={marcarTodos.isPending}
            >
              <CheckCircle2 size={16} aria-hidden="true" />
              Marcar todos vieram
            </button>
          )}
```

- [ ] **Step 6: Adicionar a coluna "Presença" no header e nas linhas da tabela**

No `<div className={styles.tabelaHeader}>`, trocar a className para incluir a variante
condicional e adicionar a coluna nova:

```tsx
                <div className={`${styles.tabelaHeader} ${evento?.controlaPresenca ? styles.tabelaHeaderComPresenca : ''}`}>
                  <span className={styles.colParticipante}>PARTICIPANTE</span>
                  <span className={styles.colData}>DATA</span>
                  <span className={styles.colInscritoPor}>INSCRITO POR</span>
                  <span className={styles.colConvidados}>CONVIDADOS</span>
                  {evento?.controlaPresenca && <span className={styles.colPresenca}>PRESENÇA</span>}
                  <span className={styles.colAcoes}>AÇÕES</span>
                </div>
```

No `<div className={styles.linha}>` de cada inscrito, mesma troca de className, e a coluna
nova ANTES de `colAcoes`:

```tsx
                      <div className={`${styles.linha} ${evento?.controlaPresenca ? styles.linhaComPresenca : ''}`}>
```

... (o conteúdo interno de `colParticipante`/`colData`/`colInscritoPor`/`colConvidados`
continua idêntico — só inserir, logo ANTES do `<div className={styles.colAcoes}>` já
existente):

```tsx
                        {evento?.controlaPresenca && (
                          <div className={styles.colPresenca}>
                            <input
                              type="checkbox"
                              className={styles.checkboxPresenca}
                              checked={inscrito.compareceu}
                              aria-label={`${inscrito.nome} compareceu`}
                              onChange={(e) =>
                                marcarPresencaInscricao.mutate({
                                  inscricaoId: inscrito.id,
                                  compareceu: e.target.checked,
                                })
                              }
                            />
                          </div>
                        )}
```

E na linha de cada convidado (`<div className={styles.linhaConvidado}>`), mesma troca de
className e, ANTES do `<div className={styles.colAcoes}>` daquele bloco:

```tsx
                      <div className={`${styles.linhaConvidado} ${evento?.controlaPresenca ? styles.linhaComPresenca : ''}`}>
```

```tsx
                          {evento?.controlaPresenca && (
                            <div className={styles.colPresenca}>
                              <input
                                type="checkbox"
                                className={styles.checkboxPresenca}
                                checked={convidado.compareceu}
                                aria-label={`${convidado.nome} compareceu`}
                                onChange={(e) =>
                                  marcarPresencaAcompanhante.mutate({
                                    acompanhanteId: convidado.id,
                                    compareceu: e.target.checked,
                                  })
                                }
                              />
                            </div>
                          )}
```

> Nota: `convidado` no laço `inscrito.acompanhantes.map((convidado) => ...)` já é do tipo
> `AcompanhanteResponse` (Tarefa 9 adicionou `compareceu` a ele) — nenhuma mudança de tipo
> adicional necessária aqui.

- [ ] **Step 7: Adicionar o modal de confirmação de "marcar todos"**

Ao final do JSX, junto dos outros modais condicionais (`{inscritoCancelando && (...)}` etc.):

```tsx
      {confirmarMarcarTodos && (
        <ModalConfirmacao
          titulo="Marcar todos como presentes?"
          mensagem="Todo inscrito confirmado e seus convidados serão marcados como presentes. Você pode corrigir exceções (quem não veio) depois, um por um."
          textoConfirmar="Marcar todos"
          isLoading={marcarTodos.isPending}
          onConfirmar={() => marcarTodos.mutate(undefined, { onSuccess: () => setConfirmarMarcarTodos(false) })}
          onClose={() => setConfirmarMarcarTodos(false)}
        />
      )}
```

- [ ] **Step 8: Typecheck e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint "src/app/(app)/eventos/[id]/inscritos/page.tsx"`
Expected: sem erros.

- [ ] **Step 9: Testar manualmente**

Abrir um evento com `controlaPresenca=true` e inscritos: confirmar que o botão "Marcar todos
vieram" aparece, que o modal de confirmação abre e, ao confirmar, todos os checkboxes da
tabela (inscritos e convidados) ficam marcados. Desmarcar um checkbox individual e confirmar
que só aquela linha muda (a lista não é recarregada do zero visualmente — só o campo
`compareceu` daquela linha). Abrir um evento com `controlaPresenca=false` e confirmar que
nem o botão, nem a coluna "Presença" aparecem. Redimensionar para ~375px e confirmar que o
card de cada inscrito mostra "Compareceu" com o checkbox, sem estourar horizontalmente.

- [ ] **Step 10: Commit**

```bash
cd frontend
git add "src/app/(app)/eventos/[id]/inscritos/page.tsx" \
        "src/app/(app)/eventos/[id]/inscritos/inscritos.module.css"
git commit -m "feat(evento): marcar presenca (lote e individual) na lista de inscritos"
```

---

## Task 13: Frontend — relatório individual (3 cards) na lista de inscritos

**Files:**
- Create: `frontend/src/hooks/evento/useRelatorioEvento.ts`
- Create: `frontend/src/components/module/eventos/CardsRelatorioEvento.tsx`
- Create: `frontend/src/components/module/eventos/CardsRelatorioEvento.module.css`
- Modify: `frontend/src/app/(app)/eventos/[id]/inscritos/page.tsx`

**Interfaces:**
- Consumes: `eventosService.relatorio` (Tarefa 9), `RelatorioEventoResponse` (Tarefa 9).
- Produces: `<CardsRelatorioEvento relatorio={...} />` — usado só nesta página (folha da árvore).

- [ ] **Step 1: `useRelatorioEvento.ts`**

```typescript
import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'

/**
 * Relatório individual de presença — só faz sentido pedir quando `evento.controlaPresenca`
 * é true (senão a seção de comparecimento nem existiria na resposta); `enabled` deixa o
 * chamador decidir isso pela própria situação do evento, sem disparar a query à toa.
 */
export function useRelatorioEvento(eventoId: string | undefined, enabled = true) {
  return useQuery({
    queryKey: ['relatorio-evento', eventoId],
    queryFn: () => eventosService.relatorio(eventoId!),
    enabled: !!eventoId && enabled,
  })
}
```

- [ ] **Step 2: `CardsRelatorioEvento.module.css`**

```css
.grade {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 24px;
}

.card {
  padding: 20px;
  background: var(--color-bg-page);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cardTitulo {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

/* ─── Presença Total: círculo de progresso simples via conic-gradient ─── */
.presencaLinha {
  display: flex;
  align-items: center;
  gap: 16px;
}
.circulo {
  --pct: 0;
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: 50%;
  flex-shrink: 0;
  background: conic-gradient(var(--color-primary) calc(var(--pct) * 1%), var(--color-bg-table-header) 0);
  display: flex;
  align-items: center;
  justify-content: center;
}
.circulo::before {
  content: '';
  position: absolute;
  inset: 6px;
  border-radius: 50%;
  background: var(--color-bg-page);
}
.circuloTexto {
  position: relative;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-primary);
}
.presencaValor {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
}
.presencaLabel {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

/* ─── Composição ─── */
.composicaoLinha {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.composicaoValor {
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
}
.composicaoLabel {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

/* ─── Impacto Global ─── */
.impactoValor {
  font-size: 28px;
  font-weight: var(--font-weight-bold);
  color: var(--color-text-dark);
}
.impactoLabel {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

@media (max-width: 900px) {
  .grade {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 3: `CardsRelatorioEvento.tsx`**

```tsx
'use client'

import type { RelatorioEventoResponse } from '@/types/evento.type'
import styles from './CardsRelatorioEvento.module.css'

interface Props {
  relatorio: RelatorioEventoResponse
}

/**
 * Os 3 cards do relatório individual (modal/página de inscritos), visíveis só quando o
 * evento controla presença (`relatorio.compareceram !== null` — a página que chama já
 * garante isso condicionando a renderização, mas o componente também se defende sozinho).
 *
 * "Pessoas da Igreja" / "Convidados": rótulo de TELA — nunca "Membros"/"Visitantes", que
 * colidiriam com o enum de vínculo do domínio (ver CLAUDE.md do projeto).
 */
export function CardsRelatorioEvento({ relatorio }: Props) {
  if (!relatorio.compareceram) return null

  const totalInscritos = relatorio.inscritos.pessoas + relatorio.inscritos.convidados
  const totalCompareceram = relatorio.compareceram.pessoas + relatorio.compareceram.convidados
  const percentualPresenca = totalInscritos > 0
    ? Math.round((totalCompareceram / totalInscritos) * 1000) / 10
    : 0

  return (
    <div className={styles.grade}>
      <div className={styles.card}>
        <span className={styles.cardTitulo}>Presença Total</span>
        <div className={styles.presencaLinha}>
          <div
            className={styles.circulo}
            style={{ '--pct': percentualPresenca } as React.CSSProperties}
          >
            <span className={styles.circuloTexto}>{percentualPresenca}%</span>
          </div>
          <div>
            <p className={styles.presencaValor}>{totalCompareceram} de {totalInscritos}</p>
            <p className={styles.presencaLabel}>compareceram</p>
          </div>
        </div>
      </div>

      <div className={styles.card}>
        <span className={styles.cardTitulo}>Composição</span>
        <div className={styles.composicaoLinha}>
          <span className={styles.composicaoValor}>{relatorio.compareceram.pessoas}</span>
          <span className={styles.composicaoLabel}>Pessoas da Igreja</span>
        </div>
        <div className={styles.composicaoLinha}>
          <span className={styles.composicaoValor}>{relatorio.compareceram.convidados}</span>
          <span className={styles.composicaoLabel}>Convidados</span>
        </div>
      </div>

      <div className={styles.card}>
        <span className={styles.cardTitulo}>Impacto Global</span>
        <p className={styles.impactoValor}>{relatorio.percentualIgreja ?? 0}%</p>
        <p className={styles.impactoLabel}>da igreja compareceu a este evento</p>
      </div>
    </div>
  )
}
```

- [ ] **Step 4: Wirar na página de inscritos (`page.tsx`)**

Importar, junto dos outros hooks:

```typescript
import { useRelatorioEvento } from '@/hooks/evento/useRelatorioEvento'
import { CardsRelatorioEvento } from '@/components/module/eventos/CardsRelatorioEvento'
```

Declarar o hook, logo após `const { data: lista, ... } = useListaInscritos(eventoId, autorizado)`:

```typescript
  const { data: relatorio } = useRelatorioEvento(eventoId, autorizado && !!evento?.controlaPresenca)
```

Renderizar os cards logo ABAIXO do `<div className={styles.painel}>` da tabela (fora dele,
como um bloco irmão, depois do `</div>` que fecha o painel e ANTES do `{modalInscreverAberto && (...)}`):

```tsx
          {relatorio && <CardsRelatorioEvento relatorio={relatorio} />}
```

- [ ] **Step 5: Typecheck e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/CardsRelatorioEvento.tsx src/hooks/evento/useRelatorioEvento.ts`
Expected: sem erros.

- [ ] **Step 6: Testar manualmente**

Abrir a lista de inscritos de um evento com `controlaPresenca=true`: os 3 cards aparecem
abaixo da tabela, com os números batendo com quem está marcado como presente. Marcar/desmarcar
presença de alguém e confirmar que os cards atualizam (a invalidação de
`['relatorio-evento', eventoId]` feita pelos hooks da Tarefa 11 cobre isso). Num evento com
`controlaPresenca=false`, confirmar que os cards não aparecem. Redimensionar para ~375px e
confirmar que os 3 cards empilham em coluna única.

- [ ] **Step 7: Commit**

```bash
cd frontend
git add src/hooks/evento/useRelatorioEvento.ts \
        src/components/module/eventos/CardsRelatorioEvento.tsx \
        src/components/module/eventos/CardsRelatorioEvento.module.css \
        "src/app/(app)/eventos/[id]/inscritos/page.tsx"
git commit -m "feat(evento): cards de relatorio individual de presenca na lista de inscritos"
```

---

## Task 14: Frontend — página de relatório geral: filtros + cards de resumo

**Files:**
- Create: `frontend/src/hooks/evento/useRelatorioGeral.ts`
- Create: `frontend/src/app/(app)/eventos/relatorio/page.tsx`
- Create: `frontend/src/app/(app)/eventos/relatorio/relatorio.module.css`

**Interfaces:**
- Consumes: `eventosService.relatorioGeral` (Tarefa 9), `RelatorioGeralResponse`/`RelatorioGeralFiltros` (Tarefa 9), `PainelFiltros`/`useFiltrosUrl` (já existem), `RECORTES_ETARIOS` (já existe em `BlocoParaQuemE`), `CampoData` (já existe), `podeGerenciarInscricoes`/`podeVerListaCompletaDeInscritos` (já existem em `lib/permissoes`).
- Produces: `useRelatorioGeral(filtros)` — consumido pela própria página (e pela Tarefa 15,
  que só ACRESCENTA seções à mesma página, sem duplicar a busca).

- [ ] **Step 1: `useRelatorioGeral.ts`**

```typescript
import { useQuery } from '@tanstack/react-query'
import { eventosService } from '@/services/evento.service'
import type { RelatorioGeralFiltros } from '@/types/evento.type'

export function useRelatorioGeral(filtros: RelatorioGeralFiltros) {
  return useQuery({
    queryKey: ['relatorio-geral', filtros],
    queryFn: () => eventosService.relatorioGeral(filtros),
  })
}
```

- [ ] **Step 2: `relatorio.module.css` — base da página (filtros, cabeçalho, cards de resumo)**

```css
.pagina {
  display: flex;
  flex-direction: column;
  gap: 24px;
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: var(--font-size-sm);
}
.breadcrumbLink { color: var(--color-text-muted); }
.breadcrumbSep { color: var(--color-text-muted); }
.breadcrumbAtual { color: var(--color-text-primary); font-weight: var(--font-weight-medium); }

.cabecalho {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}
.titulo {
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-bold);
  color: var(--color-text-dark);
}

.filtros {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}
.filtroData {
  width: 160px;
}

.resumoGrade {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.resumoCard {
  padding: 20px;
  background: var(--color-bg-page);
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}
.resumoValor {
  font-size: 26px;
  font-weight: var(--font-weight-bold);
  color: var(--color-text-dark);
}
.resumoLabel {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}
.resumoSemDado {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  font-style: italic;
}
.resumoPopularTitulo {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 900px) {
  .resumoGrade {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 767px) {
  .pagina { padding: 16px; }
  .cabecalho { flex-direction: column; align-items: stretch; }
  .filtros { flex-direction: column; align-items: stretch; }
  .filtroData { width: 100%; }
  .resumoGrade { grid-template-columns: 1fr; }
}
```

- [ ] **Step 3: `page.tsx` — filtros + cards de resumo (o gráfico e a lista de últimos eventos entram na Tarefa 15)**

```tsx
'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { podeVerListaCompletaDeInscritos } from '@/lib/permissoes'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { useTiposEvento } from '@/hooks/evento/useTiposEvento'
import { useRelatorioGeral } from '@/hooks/evento/useRelatorioGeral'
import { PainelFiltros, GrupoFiltro } from '@/components/common/PainelFiltros/PainelFiltros'
import { RECORTES_ETARIOS } from '@/components/module/eventos/BlocoParaQuemE'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import styles from './relatorio.module.css'

const OPCOES_RECORTE = RECORTES_ETARIOS.map((r) => ({ valor: r.nome, label: r.nome }))

function RelatorioGeralConteudo() {
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeVerListaCompletaDeInscritos(role)

  const { filtros, setFiltros } = useFiltrosUrl({ tipo: '', recorteEtario: '' })
  const [periodo, setPeriodo] = useState({ inicio: '', fim: '' })

  const { data: tipos = [] } = useTiposEvento()

  const gruposFiltro: GrupoFiltro[] = [
    { chave: 'tipo', titulo: 'Tipo', opcoes: tipos.map((t) => ({ valor: t, label: t })) },
    { chave: 'recorteEtario', titulo: 'Recorte etário', opcoes: OPCOES_RECORTE },
  ]

  const { data: relatorio, isLoading, isError, refetch } = useRelatorioGeral({
    inicio: periodo.inicio ? `${periodo.inicio}T00:00:00` : undefined,
    fim: periodo.fim ? `${periodo.fim}T23:59:59` : undefined,
    tipo: filtros.tipo || undefined,
    recorteEtario: filtros.recorteEtario || undefined,
  })

  if (!hidratado) {
    return <div className={styles.pagina} />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  function aoAplicarFiltros(valores: Record<string, string>) {
    setFiltros(valores as { tipo: string; recorteEtario: string })
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Relatório de engajamento</span>
      </nav>

      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Relatório de Engajamento</h1>

        <div className={styles.filtros}>
          <div className={styles.filtroData}>
            <CampoData
              id="periodo-inicio"
              label="DE"
              value={periodo.inicio}
              onChange={(iso) => setPeriodo((p) => ({ ...p, inicio: iso }))}
            />
          </div>
          <div className={styles.filtroData}>
            <CampoData
              id="periodo-fim"
              label="ATÉ"
              value={periodo.fim}
              onChange={(iso) => setPeriodo((p) => ({ ...p, fim: iso }))}
            />
          </div>
          <PainelFiltros grupos={gruposFiltro} valores={filtros} onAplicar={aoAplicarFiltros} />
        </div>
      </header>

      {isLoading ? (
        <p className={styles.resumoSemDado}>Carregando relatório…</p>
      ) : isError || !relatorio ? (
        <EstadoErro
          titulo="Não foi possível carregar o relatório"
          mensagem="Verifique sua conexão e tente novamente."
          aoTentarNovamente={() => refetch()}
        />
      ) : (
        <>
          <div className={styles.resumoGrade}>
            <div className={styles.resumoCard}>
              <span className={styles.resumoValor}>{relatorio.resumo.totalEventos}</span>
              <span className={styles.resumoLabel}>Total de eventos</span>
            </div>

            <div className={styles.resumoCard}>
              {relatorio.resumo.comparecimentoMedio == null ? (
                <span className={styles.resumoSemDado}>Sem dado de presença no período</span>
              ) : (
                <span className={styles.resumoValor}>{relatorio.resumo.comparecimentoMedio}</span>
              )}
              <span className={styles.resumoLabel}>Comparecimento médio</span>
            </div>

            <div className={styles.resumoCard}>
              {relatorio.resumo.participantesUnicos == null ? (
                <span className={styles.resumoSemDado}>Sem dado de presença no período</span>
              ) : (
                <span className={styles.resumoValor}>{relatorio.resumo.participantesUnicos}</span>
              )}
              <span className={styles.resumoLabel}>Participantes únicos</span>
            </div>

            <div className={styles.resumoCard}>
              {relatorio.eventoMaisPopular ? (
                <>
                  <span className={styles.resumoPopularTitulo}>{relatorio.eventoMaisPopular.titulo}</span>
                  <span className={styles.resumoLabel}>
                    {relatorio.eventoMaisPopular.totalInscritos} inscritos — evento mais popular
                  </span>
                </>
              ) : (
                <span className={styles.resumoSemDado}>Nenhum evento no período</span>
              )}
            </div>
          </div>
          {/* Gráfico de tendência e lista "Últimos Eventos" entram na Tarefa 15. */}
        </>
      )}
    </div>
  )
}

export default function RelatorioGeralPage() {
  return (
    <Suspense fallback={<div className={styles.pagina} />}>
      <RelatorioGeralConteudo />
    </Suspense>
  )
}
```

- [ ] **Step 4: Adicionar o link de navegação (a partir de `/eventos`)**

Em `frontend/src/app/(app)/eventos/page.tsx`, no `<header>` da página (mesmo cabeçalho onde
já mora o botão de novo evento/filtros), adicionar um link visível só para quem
`podeVerListaCompletaDeInscritos(role)` (mesma régua da página de relatório):

```tsx
        {podeGerenciar && (
          <Link href="/eventos/relatorio" className={styles.linkRelatorio}>
            Relatório de engajamento
          </Link>
        )}
```

Adicionar a classe `.linkRelatorio` ao `Page.module.css` (mesmo arquivo de estilos da página
de eventos), com uma cor de texto simples (ex.: `color: var(--color-primary); font-size:
var(--font-size-sm); font-weight: var(--font-weight-medium);`) — decisão de layout final
(onde exatamente esse link mora no cabeçalho) fica a critério de quem implementar, o spec já
marcou isso como fora de escopo desta entrega ("decisão de layout, resolvida no
protótipo/implementação do front").

- [ ] **Step 5: Typecheck e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint "src/app/(app)/eventos/relatorio/page.tsx" src/hooks/evento/useRelatorioGeral.ts`
Expected: sem erros.

- [ ] **Step 6: Testar manualmente**

Acessar `/eventos/relatorio` logado como ADMIN/LÍDER: os 4 cards de resumo aparecem, os
filtros de período/tipo/recorte etário existem e ao aplicá-los a URL muda (mesmo padrão de
`/eventos`) e os cards recalculam. Logado como ACESSO_COMUM, a página mostra `<AcessoRestrito>`.
Redimensionar para ~375px: os filtros empilham, os 4 cards viram 1 coluna.

- [ ] **Step 7: Commit**

```bash
cd frontend
git add src/hooks/evento/useRelatorioGeral.ts \
        "src/app/(app)/eventos/relatorio/page.tsx" \
        "src/app/(app)/eventos/relatorio/relatorio.module.css" \
        "src/app/(app)/eventos/page.tsx" \
        "src/app/(app)/eventos/Page.module.css"
git commit -m "feat(evento): pagina de relatorio geral - filtros e cards de resumo"
```

---

## Task 15: Frontend — gráfico de tendência + lista "Últimos Eventos" com variação

**Files:**
- Create: `frontend/src/components/module/eventos/GraficoTendenciaComparecimento.tsx`
- Create: `frontend/src/components/module/eventos/CardVariacao.tsx`
- Create: `frontend/src/components/module/eventos/CardVariacao.module.css`
- Modify: `frontend/src/app/(app)/eventos/relatorio/page.tsx`
- Modify: `frontend/src/app/(app)/eventos/relatorio/relatorio.module.css`

**Interfaces:**
- Consumes: `RelatorioGeralResponse.tendencia`/`ultimosEventos` (Tarefa 9), `useRelatorioGeral` (Tarefa 14), Recharts (`recharts@^3.9.2`, já é dependência do projeto).
- Produces: nada consumido por tarefa seguinte — última tarefa do plano.

- [ ] **Step 1: `CardVariacao.module.css`**

```css
.badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  cursor: help;
  border: none;
  background: none;
}
.positivo {
  color: var(--color-success, #1a7f37);
  background: var(--color-success-bg, #e6f4ea);
}
.negativo {
  color: var(--color-danger);
  background: var(--color-danger-bg);
}
.neutro {
  color: var(--color-text-muted);
  background: var(--color-bg-table-header);
}

.legenda {
  display: block;
  margin-top: 4px;
  font-size: 10px;
  color: var(--color-text-muted);
}
```

- [ ] **Step 2: `CardVariacao.tsx`**

```tsx
'use client'

import { useState } from 'react'
import { ArrowUp, ArrowDown, Minus } from 'lucide-react'
import type { VariacaoRelatorio } from '@/types/evento.type'
import styles from './CardVariacao.module.css'

interface Props {
  variacao: VariacaoRelatorio
  /** Rótulo do QUE está sendo comparado (ex.: "vs. evento anterior", "vs. média do filtro"). */
  rotulo: string
}

const TEXTO_BASE: Record<VariacaoRelatorio['base'], string> = {
  COMPARECIMENTO: 'Comparado por comparecimento real (os dois eventos controlam presença).',
  INSCRITOS: 'Comparado por inscritos confirmados (comparecimento indisponível em um dos eventos).',
}

/**
 * Badge de variação percentual — SEMPRE mostra a base usada (Decisão 4 do spec: nunca
 * implícito). `title` cobre o hover no desktop; o clique alterna um texto visível, que é o
 * que cobre o toque no mobile (onde não existe `:hover`/`title`).
 */
export function CardVariacao({ variacao, rotulo }: Props) {
  const [legendaAberta, setLegendaAberta] = useState(false)
  const positivo = variacao.percentual > 0
  const negativo = variacao.percentual < 0
  const classe = positivo ? styles.positivo : negativo ? styles.negativo : styles.neutro
  const Icone = positivo ? ArrowUp : negativo ? ArrowDown : Minus

  return (
    <span>
      <button
        type="button"
        className={`${styles.badge} ${classe}`}
        title={TEXTO_BASE[variacao.base]}
        onClick={() => setLegendaAberta((v) => !v)}
        aria-expanded={legendaAberta}
      >
        <Icone size={12} aria-hidden="true" />
        {Math.abs(variacao.percentual)}% {rotulo}
      </button>
      {legendaAberta && <span className={styles.legenda}>{TEXTO_BASE[variacao.base]}</span>}
    </span>
  )
}
```

- [ ] **Step 3: `GraficoTendenciaComparecimento.tsx`**

```tsx
'use client'

import {
  ResponsiveContainer, LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip,
} from 'recharts'
import type { PontoTendencia } from '@/types/evento.type'

interface Props {
  tendencia: PontoTendencia[]
}

/** "aaaa-mm" -> "mmm" abreviado em pt-BR, só para o eixo X (o valor completo fica no tooltip). */
function mesAbreviado(iso: string): string {
  const [ano, mes] = iso.split('-')
  const data = new Date(Number(ano), Number(mes) - 1, 1)
  return data.toLocaleDateString('pt-BR', { month: 'short' }).replace('.', '')
}

/**
 * Comparecimento médio mensal dos últimos 6 meses — só eventos com `controlaPresenca=true`
 * entram na conta (Decisão 4). Mês sem dado (`comparecimentoMedio: null`) vira um GAP na
 * linha (Recharts pula pontos `null` por padrão em vez de desenhar zero), então o gráfico
 * nunca finge que "ninguém foi" num mês em que ninguém simplesmente controlou presença.
 */
export function GraficoTendenciaComparecimento({ tendencia }: Props) {
  const dados = tendencia.map((p) => ({ mes: mesAbreviado(p.mes), valor: p.comparecimentoMedio }))
  const semNenhumDado = tendencia.every((p) => p.comparecimentoMedio == null)

  if (semNenhumDado) {
    return (
      <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
        Nenhum evento do período controla presença — sem dado de tendência para mostrar.
      </p>
    )
  }

  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={dados} margin={{ top: 8, right: 16, bottom: 0, left: -16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="var(--color-border)" vertical={false} />
        <XAxis dataKey="mes" tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={false} tickLine={false} />
        <YAxis tick={{ fontSize: 12, fill: 'var(--color-text-muted)' }} axisLine={false} tickLine={false} allowDecimals={false} />
        <Tooltip
          formatter={(valor: number | null) => (valor == null ? 'Sem dado' : `${valor} pessoas`)}
          contentStyle={{ fontSize: 13, borderRadius: 8 }}
        />
        <Line
          type="monotone"
          dataKey="valor"
          stroke="var(--color-primary)"
          strokeWidth={2}
          dot={{ r: 3 }}
          connectNulls={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
```

- [ ] **Step 4: Adicionar as seções em `relatorio.module.css`**

Ao final do arquivo:

```css
.secao {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px;
  background: var(--color-bg-page);
  border-radius: var(--radius-lg);
}
.secaoTitulo {
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
}

.listaEventos {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.linhaEvento {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: var(--color-bg-white);
  border-radius: var(--radius-md);
  flex-wrap: wrap;
}
.linhaEventoInfo {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.linhaEventoTitulo {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.linhaEventoData {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}
.linhaEventoParticipantes {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
  white-space: nowrap;
}
.linhaEventoVariacoes {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 767px) {
  .linhaEvento {
    flex-direction: column;
    align-items: stretch;
  }
}
```

- [ ] **Step 5: Wirar as duas seções na `page.tsx` (logo após o `.resumoGrade`, substituindo o comentário deixado na Tarefa 14)**

Importar os componentes novos:

```typescript
import { GraficoTendenciaComparecimento } from '@/components/module/eventos/GraficoTendenciaComparecimento'
import { CardVariacao } from '@/components/module/eventos/CardVariacao'
```

Substituir o comentário `{/* Gráfico de tendência e lista "Últimos Eventos" entram na Tarefa 15. */}`
por:

```tsx
          <section className={styles.secao}>
            <h2 className={styles.secaoTitulo}>Tendência de comparecimento (6 meses)</h2>
            <GraficoTendenciaComparecimento tendencia={relatorio.tendencia} />
          </section>

          <section className={styles.secao}>
            <h2 className={styles.secaoTitulo}>Últimos Eventos</h2>
            {relatorio.ultimosEventos.length === 0 ? (
              <p className={styles.resumoSemDado}>Nenhum evento no período.</p>
            ) : (
              <div className={styles.listaEventos}>
                {relatorio.ultimosEventos.map((evento) => (
                  <div key={evento.eventoId} className={styles.linhaEvento}>
                    <div className={styles.linhaEventoInfo}>
                      <span className={styles.linhaEventoTitulo}>{evento.titulo}</span>
                      <span className={styles.linhaEventoData}>
                        {new Date(evento.data).toLocaleDateString('pt-BR')}
                      </span>
                    </div>
                    <span className={styles.linhaEventoParticipantes}>
                      {evento.totalParticipantes} participantes
                    </span>
                    <div className={styles.linhaEventoVariacoes}>
                      {evento.variacaoEventoAnterior && (
                        <CardVariacao variacao={evento.variacaoEventoAnterior} rotulo="vs. anterior" />
                      )}
                      <CardVariacao variacao={evento.variacaoMediaGeral} rotulo="vs. média do filtro" />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
```

- [ ] **Step 6: Typecheck e lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/GraficoTendenciaComparecimento.tsx src/components/module/eventos/CardVariacao.tsx "src/app/(app)/eventos/relatorio/page.tsx"`
Expected: sem erros.

- [ ] **Step 7: Testar manualmente**

Em `/eventos/relatorio`: o gráfico de linha aparece com os últimos 6 meses, meses sem evento
controlado aparecem como um GAP na linha (não como um vale até zero — abrir o DevTools e
inspecionar os dados de `tendencia` se houver dúvida visual). Cada linha de "Últimos Eventos"
mostra o total de participantes e até dois badges de variação; passar o mouse (desktop) ou
clicar (mobile) num badge revela qual base foi usada (COMPARECIMENTO ou INSCRITOS). Num
evento sem par anterior do mesmo tipo, só o badge "vs. média do filtro" aparece. Redimensionar
para ~375px: cada linha de evento empilha em coluna, sem overflow horizontal; o gráfico
(`ResponsiveContainer width="100%"`) se ajusta à largura da tela.

- [ ] **Step 8: Rodar a suíte completa do backend e o typecheck do front, uma última vez, de ponta a ponta**

Run: `cd backend/api && ./mvnw test`
Expected: BUILD SUCCESS.

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 9: Commit**

```bash
cd frontend
git add src/components/module/eventos/GraficoTendenciaComparecimento.tsx \
        src/components/module/eventos/CardVariacao.tsx \
        src/components/module/eventos/CardVariacao.module.css \
        "src/app/(app)/eventos/relatorio/page.tsx" \
        "src/app/(app)/eventos/relatorio/relatorio.module.css"
git commit -m "feat(evento): grafico de tendencia e lista de ultimos eventos com variacao"
```

---

## Self-Review

**1. Cobertura do spec:**

- Decisão 1 (opt-in, `controla_presenca`, CHECK contra `requer_inscricao`) → Migration V6
  (Task 1), entidade + validação de service (Tasks 3-4), toggle de UI condicionado (Task 10).
- Decisão 2 (presença granular por pessoa física) → colunas `compareceu` em
  `inscricao_evento`/`acompanhante_inscricao` (Tasks 1, 3), UI de checkbox por linha (Task 12).
- Decisão 3 (marcar em lote + exceção, 3 endpoints, 409, `podeGerenciarInscricoes`) →
  Task 6 (service+controller+testes) e Task 2 (a exceção 409 em si).
- Decisão 4 (duas bases, nunca misturadas em silêncio) → `BaseComparacao` +
  `EventoRelatorioService` (Task 7 e 8, com testes explícitos dos 4 casos pedidos: evento mais
  popular por inscritos mesmo sem presença controlada, mês sem dado como `null`, variação por
  comparecimento quando os dois controlam e caindo para inscritos quando não) + `CardVariacao`
  no front (Task 15) sempre expondo a base.
- Relatório individual (cards Presença Total/Composição/Impacto Global, rótulos "Pessoas da
  Igreja"/"Convidados", seção some quando `controlaPresenca=false`) → Tasks 7 (back) e 13
  (front, `CardsRelatorioEvento` retorna `null` quando `compareceram` é `null`).
- Relatório geral (filtros Período/Recorte Etário/Tipo, 4 cards de resumo, tendência 6 meses,
  lista "Últimos Eventos" com 2 variações e base visível) → Tasks 8 (back), 14 e 15 (front).
- "Evento anterior do mesmo tipo" = mais recente da mesma igreja, mesmo `tipo`, `inicioEm`
  anterior → `findFirstByIgrejaIdAndTipoAndInicioEmLessThanOrderByInicioEmDesc` (Task 8).
- Nenhuma tabela nova → confirmado (só 3 colunas booleanas na Task 1).
- `igreja_id` sempre do JWT → todo método novo de service recebe `igrejaId` do
  `UsuarioAutenticado` no controller, nunca do corpo (Tasks 6, 7, 8).
- Services retornam DTOs → `EventoRelatorioService`, `InscricaoService` (métodos de presença
  retornam `void`/`int`, nunca entidade) — confirmado em todas as assinaturas.
- Fora de escopo (onde o link do relatório geral mora na navegação; exportar dados; bug do
  toggle `requerInscricao` escondendo faixa etária) → deliberadamente NÃO implementados; a
  Task 14 Step 4 marca a posição do link como "decisão de layout... fora de escopo desta
  entrega", citando o próprio spec.
- Responsividade obrigatória → cada tarefa de front tem CSS `@media` dedicado (Tasks 12, 13,
  14, 15) e um passo de teste manual em viewport de celular.

**2. Placeholder scan:** busca por `TBD`/`TODO`/`implement later`/`similar to Task N`/`add
appropriate` não encontrou nenhuma ocorrência real (as únicas coincidências foram a palavra
portuguesa "TODOS", de "marcar todos" — não o marcador em inglês). Todo bloco de código em
toda tarefa está completo, sem reticências nem comentário de "resto igual".

**3. Consistência de tipo/assinatura entre tarefas:**

- `Evento.isControlaPresenca()`/`setControlaPresenca()` (Task 3) é usado identicamente em
  `EventoService` (Task 4), `InscricaoService` (Task 6) e `EventoRelatorioService` (Tasks 7-8).
- `InscricaoRepository.countPessoasInscritas/countConvidadosInscritos/countPessoasCompareceram/
  countConvidadosCompareceram/contarParticipantesUnicos` (Task 5) usados com a mesma
  assinatura em `EventoRelatorioService` (Tasks 7-8) — nomes e tipos (`UUID`/`List<UUID>` →
  `long`) batem em todas as chamadas.
- `ConflitoNegocioException(String codigo, String message)` (Task 2) é lançada com essa
  assinatura exata em `InscricaoService.validarControlaPresenca` (Task 6).
- `RelatorioEventoResponse`/`RelatorioGeralResponse` (Tasks 7-8, back) espelham exatamente
  `RelatorioEventoResponse`/`RelatorioGeralResponse` (Task 9, front): mesmos nomes de campo
  (`inscritos.pessoas/convidados`, `compareceram`, `percentualIgreja`, `resumo.
  totalEventos/comparecimentoMedio/participantesUnicos`, `eventoMaisPopular.eventoId/titulo/
  totalInscritos`, `tendencia[].mes/comparecimentoMedio`, `ultimosEventos[].eventoId/titulo/
  data/totalParticipantes/variacaoEventoAnterior/variacaoMediaGeral`, `variacao.percentual/base`)
  — exigido explicitamente pelo enunciado e verificado campo a campo nesta revisão.
- `BaseComparacao` (back, Task 7) e `BaseComparacao` (front, Task 9) têm os mesmos dois
  valores literais (`COMPARECIMENTO`, `INSCRITOS`).
- `useMarcarTodosPresentes(eventoId)`/`useMarcarPresencaInscricao(eventoId)`/
  `useMarcarPresencaAcompanhante(eventoId)` (Task 11) são chamados com a mesma assinatura na
  Task 12; os nomes de mutação (`inscricaoId`/`compareceu` e `acompanhanteId`/`compareceu`)
  batem com os parâmetros usados nos `onChange` dos checkboxes.
- `useRelatorioEvento(eventoId, enabled)` (Task 13) e `useRelatorioGeral(filtros)` (Task 14)
  são consumidos exatamente como declarados, sem parâmetro extra ou faltante.
- `CardVariacao({ variacao, rotulo })` (Task 15) recebe `VariacaoRelatorio` (Task 9) — mesmo
  formato `{ percentual, base }` em ambas as pontas.
- Verificado que `EventoRequest` é `record` posicional: a Task 4 inclui um passo explícito
  (Step 9) para localizar e corrigir toda construção posicional pré-existente (`new
  EventoRequest(...)` em `ImpactoRestricaoTest`/`EventoTipoENormalizacaoTest`/o próprio arquivo
  de teste editado) que quebraria com o campo novo — risco real, não hipotético, confirmado
  por grep antes de escrever a tarefa.
