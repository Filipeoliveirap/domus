# Fluxo de local do evento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar claro, para um usuário novo, "onde o evento acontece": três caminhos visíveis (endereço cadastrado / digitar simples / endereço completo ad-hoc), cadastro de endereço sem sair do formulário, e botão "usar o endereço da igreja".

**Architecture:** Backend ganha um `@Embedded Endereco enderecoLocal` em `evento` (migration V36) e a localização vira um XOR de 3 formas validado em `EventoService.resolverLocalizacao`. Frontend troca o seletor de local por um segmentado de 3 opções; a opção "endereço completo" reusa o padrão de endereço+ViaCEP já existente em `PessoaForm`.

**Tech Stack:** Java 21, Spring Boot, Flyway, PostgreSQL, JPA/Hibernate, JUnit 5 + Mockito + AssertJ. Next.js 16 (App Router), TypeScript, React Hook Form, Zod, TanStack Query, CSS Modules.

**Spec:** `backend/api/docs/superpowers/specs/2026-09-02-evento-endereco-adhoc-design.md`

## Global Constraints

- `igreja_id` sempre do JWT, nunca do corpo da requisição.
- Services retornam DTOs, nunca entidades.
- Checagem de permissão por capacidade nomeada (`Permissoes.podeGerenciarEventos(role)`), nunca `role == 'X'`.
- Nada de literal de domínio solto — enums no back, união de tipos no front.
- Teste de regra de negócio = Mockito puro, sem contexto Spring (regra padrão do projeto).
- Nomenclatura de teste: classe `{Alvo}Test`, método `snake_case` em português.
- AssertJ primário (`assertThat`, `assertThatThrownBy`).
- **Não commitar antes de o autor testar** o pedaço. Um commit coerente por pedaço.
- Rótulo de tela pode divergir do domínio: no código, "local" continua "local"/`LocalEvento`; só o texto visível fala "endereço".
- Responsividade obrigatória: grid de endereço colapsa para 1 coluna no mobile.
- Placeholder de campo sempre com exemplo concreto.
- Migration nova = **V36** (última é V35).
- `EnderecoDTO` reutilizável: `com.domus.api.modules.pessoa.DTO.EnderecoDTO` (7 campos, todos `@Size`, nada obrigatório).
- ViaCEP no front: `useBuscaCep()` de `@/hooks/pessoa/useBuscaCep` — devolve `{ cep, logradouro, bairro, cidade, uf }` ou `null`.

## Ponto de partida (estado do branch `feat/evento-endereco-fluxo`)

Já aplicado no working tree, **não commitado** (a "leva A+B"):

- Rename visível "Locais" → "Endereços" em `/eventos` (botão), `locais/layout.tsx` (breadcrumb), `locais/page.tsx` (h1/subtítulo/botão/estado vazio), `locais/arquivados/page.tsx`, `ModalArquivarLocal.tsx`, `ModalDetalheLocal.tsx`.
- `ModalLocalForm.tsx` + `.module.css` movidos de `src/app/(app)/eventos/locais/` para `src/components/module/eventos/`. Import em `locais/page.tsx` ajustado para `@/components/module/eventos/ModalLocalForm`.
- `useLocalEventoForm(local, onClose, onCriado?)` — 3º parâmetro opcional; `salvar` chama `onCriado(criado)` no caminho de criação, antes de `onClose`.
- `ModalLocalForm` aceita prop `onCriado?: (local: LocalEventoResponse) => void` e repassa ao hook.
- `SeletorLocal.tsx` reescrito com segmentado de **2** opções (`Endereço cadastrado` | `Só este evento`), estado vazio com card, botão "＋ Novo endereço" abrindo `ModalLocalForm` inline com auto-seleção via `onCriado`. `.module.css` com `.segmentado/.segmentoBtn/.segmentoAtivo/.vazio/.botaoCadastrar/.botaoNovo`.

**Task 0** commita essa leva. As tasks seguintes assumem esse estado.

---

## File Structure

**Backend:**
- `src/main/resources/db/migration/V36__evento_endereco_adhoc.sql` — cria 7 colunas em `evento`, troca o CHECK.
- `src/main/java/com/domus/api/shared/dominio/Endereco.java` — adiciona `estaPreenchido()`.
- `src/main/java/com/domus/api/shared/dominio/EnderecoFormatter.java` — **novo**: `emLinhaUnica(Endereco)`.
- `src/main/java/com/domus/api/modules/evento/Evento.java` — `@Embedded Endereco enderecoLocal`; `getLocalExibicao()` cobre o ad-hoc.
- `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java` — campo `EnderecoDTO enderecoLocal`.
- `src/main/java/com/domus/api/modules/evento/EventoService.java` — `resolverLocal` → `resolverLocalizacao` (record `Localizacao`), criar/atualizar setam as 3 formas.
- `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java` — `LocalInfo` ganha `EnderecoDTO enderecoLocal`; novo ramo em `from`.
- `src/main/java/com/domus/api/modules/evento/local/DTOs/LocalEventoResponse.java` — formatação delega a `EnderecoFormatter`.

**Backend testes:**
- `src/test/java/com/domus/api/shared/dominio/EnderecoFormatterTest.java` — **novo**.
- `src/test/java/com/domus/api/shared/dominio/EnderecoTest.java` — **novo** (`estaPreenchido`).
- `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` — cenários do XOR de 3 formas.

**Frontend:**
- `src/types/evento.type.ts` — `EventoRequest.enderecoLocal`, `LocalInfo.enderecoLocal`.
- `src/lib/ufs.ts` — **novo**: `UF_OPTIONS` (hoje duplicado em `PessoaForm`/`VisitanteForm`).
- `src/lib/formats/endereco.ts` — **novo**: `enderecoParaLinhaUnica`, `enderecoIgrejaParaCamposCompactos`.
- `src/lib/validators.ts` — `eventoSchemaBase.enderecoLocal` + refine do XOR.
- `src/hooks/evento/useEventoForm.ts` — default, reidrata, payload de submit.
- `src/components/module/eventos/SeletorLocal.tsx` — segmentado de 3, seção "endereço completo".
- `src/components/module/eventos/SeletorLocal.module.css` — grid de endereço.
- `src/components/module/eventos/EventoForm.tsx` — passa `enderecoLocal` (watch + setter).
- `src/components/module/eventos/ModalLocalForm.tsx` — remove subtítulo de herança, adiciona botão "usar endereço da igreja".

---

## Task 0: Commitar a leva A+B (baseline)

**Files:** todos os listados em "Ponto de partida".

- [ ] **Step 1: Rodar typecheck e lint do front**

```bash
cd frontend && npx tsc --noEmit && npx eslint "src/components/module/eventos/SeletorLocal.tsx" "src/components/module/eventos/ModalLocalForm.tsx" "src/app/(app)/eventos/locais/page.tsx"
```
Expected: sem erros.

- [ ] **Step 2: Pedir ao autor para testar no navegador**

Checklist: menu Eventos → botão "Endereços"; criar/editar/arquivar endereço; no cadastro de evento sem nenhum endereço, ver o card + "Cadastrar endereço" inline + auto-seleção; com endereços, dropdown + "Novo endereço"; alternar o segmentado sem perder o resto do formulário; editar um evento existente (reidrata no modo certo); tudo no viewport de celular.

- [ ] **Step 3: Commit (só após o OK do autor)**

```bash
git add -A -- frontend/src
git commit -m "feat(evento): renomeia 'Locais' para 'Endereços' e cadastro de endereço inline no formulário

- /eventos/locais vira 'Endereços' na UI (rota e código continuam 'local')
- ModalLocalForm movido para components/module/eventos (reutilizável pelo formulário)
- SeletorLocal: controle segmentado (endereço cadastrado | só este evento),
  estado vazio com CTA, botão '+ Novo endereço' abre o modal inline e já
  seleciona o endereço criado (useLocalEventoForm ganha callback onCriado)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01Y6cgugWmfpNAaakHNNcGix"
```

---

## Task 1: `Endereco.estaPreenchido()` + `EnderecoFormatter`

**Files:**
- Modify: `src/main/java/com/domus/api/shared/dominio/Endereco.java`
- Create: `src/main/java/com/domus/api/shared/dominio/EnderecoFormatter.java`
- Test: `src/test/java/com/domus/api/shared/dominio/EnderecoTest.java`, `src/test/java/com/domus/api/shared/dominio/EnderecoFormatterTest.java`

**Interfaces:**
- Produces:
  - `boolean Endereco.estaPreenchido()` — `true` se `cep`, `logradouro` ou `cidade` tiver conteúdo (trim não-vazio). Complemento/número/bairro sozinhos não contam.
  - `String EnderecoFormatter.emLinhaUnica(Endereco e)` — ex.: `"Rua das Flores, 123 - Centro, Recife/PE (50000-000)"`. Partes vazias omitidas. `null`/endereço vazio → `null`.

- [ ] **Step 1: Escrever os testes de `EnderecoTest`**

```java
package com.domus.api.shared.dominio;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EnderecoTest {

    @Test
    void vazioNaoEstaPreenchido() {
        assertThat(new Endereco().estaPreenchido()).isFalse();
        assertThat(Endereco.builder().cep("  ").logradouro("").build().estaPreenchido()).isFalse();
    }

    @Test
    void cepLogradouroOuCidadeSozinhosContam() {
        assertThat(Endereco.builder().cep("50000-000").build().estaPreenchido()).isTrue();
        assertThat(Endereco.builder().logradouro("Rua X").build().estaPreenchido()).isTrue();
        assertThat(Endereco.builder().cidade("Recife").build().estaPreenchido()).isTrue();
    }

    @Test
    void complementoNumeroOuBairroSozinhosNaoContam() {
        assertThat(Endereco.builder().numero("123").build().estaPreenchido()).isFalse();
        assertThat(Endereco.builder().complemento("Apto 2").build().estaPreenchido()).isFalse();
        assertThat(Endereco.builder().bairro("Centro").build().estaPreenchido()).isFalse();
    }
}
```

- [ ] **Step 2: Escrever os testes de `EnderecoFormatterTest`**

```java
package com.domus.api.shared.dominio;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EnderecoFormatterTest {

    @Test
    void enderecoNuloOuVazioViraNull() {
        assertThat(EnderecoFormatter.emLinhaUnica(null)).isNull();
        assertThat(EnderecoFormatter.emLinhaUnica(new Endereco())).isNull();
    }

    @Test
    void completoFormataTudo() {
        Endereco e = Endereco.builder()
                .cep("50000-000").logradouro("Rua das Flores").numero("123")
                .complemento("Sala 4").bairro("Centro").cidade("Recife").uf("PE").build();
        assertThat(EnderecoFormatter.emLinhaUnica(e))
                .isEqualTo("Rua das Flores, 123, Sala 4 - Centro, Recife/PE (50000-000)");
    }

    @Test
    void parcialOmiteOQueFalta() {
        Endereco e = Endereco.builder().logradouro("Praça da Matriz").cidade("Olinda").uf("PE").build();
        assertThat(EnderecoFormatter.emLinhaUnica(e)).isEqualTo("Praça da Matriz - Olinda/PE");
    }

    @Test
    void soCidade() {
        assertThat(EnderecoFormatter.emLinhaUnica(Endereco.builder().cidade("Recife").build()))
                .isEqualTo("Recife");
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=EnderecoTest,EnderecoFormatterTest`
Expected: erro de compilação (`estaPreenchido`, `EnderecoFormatter` não existem).

- [ ] **Step 4: Implementar `Endereco.estaPreenchido()`**

Adicionar ao final de `Endereco.java` (antes do `}` de fecho da classe):

```java
    /** Heurística de "tem endereço digitado": CEP, logradouro ou cidade preenchidos.
     *  Complemento/número/bairro sozinhos não caracterizam um endereço. */
    public boolean estaPreenchido() {
        return temTexto(cep) || temTexto(logradouro) || temTexto(cidade);
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
```

- [ ] **Step 5: Implementar `EnderecoFormatter`**

```java
package com.domus.api.shared.dominio;

/** Formata um {@link Endereco} numa linha só para exibição (drawer de evento, busca, notificação). */
public final class EnderecoFormatter {

    private EnderecoFormatter() {}

    public static String emLinhaUnica(Endereco e) {
        if (e == null || !e.estaPreenchido()) return null;

        StringBuilder linha = new StringBuilder();
        acrescentar(linha, e.getLogradouro(), "");
        acrescentar(linha, e.getNumero(), ", ");
        acrescentar(linha, e.getComplemento(), ", ");

        StringBuilder bairroCidade = new StringBuilder();
        acrescentar(bairroCidade, e.getBairro(), "");
        String cidadeUf = e.getCidade();
        if (temTexto(cidadeUf) && temTexto(e.getUf())) cidadeUf = cidadeUf + "/" + e.getUf();
        acrescentar(bairroCidade, cidadeUf, ", ");

        if (bairroCidade.length() > 0) {
            if (linha.length() > 0) linha.append(" - ");
            linha.append(bairroCidade);
        }
        if (temTexto(e.getCep())) linha.append(" (").append(e.getCep().trim()).append(")");
        return linha.toString();
    }

    private static void acrescentar(StringBuilder sb, String parte, String separador) {
        if (!temTexto(parte)) return;
        if (sb.length() > 0) sb.append(separador);
        sb.append(parte.trim());
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
```

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=EnderecoTest,EnderecoFormatterTest`
Expected: PASS (7 testes).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/shared/dominio/ src/test/java/com/domus/api/shared/dominio/
git commit -m "feat(endereco): Endereco.estaPreenchido() e EnderecoFormatter.emLinhaUnica()"
```

---

## Task 2: Migration V36 + `Evento.enderecoLocal`

**Files:**
- Create: `src/main/resources/db/migration/V36__evento_endereco_adhoc.sql`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoEnderecoAdHocMigracaoTest.java` (novo, `@DataJpaTest`)

**Interfaces:**
- Produces:
  - `Endereco Evento.getEnderecoLocal()` / `void setEnderecoLocal(Endereco)` (via Lombok `@Getter/@Setter` já na classe; campo `@Embedded`).
  - `Evento.getLocalExibicao()` passa a devolver, quando `local == null && localTexto == null`, `EnderecoFormatter.emLinhaUnica(enderecoLocal)`.

- [ ] **Step 1: Escrever a migration**

`src/main/resources/db/migration/V36__evento_endereco_adhoc.sql`:

```sql
-- Endereço estruturado AD-HOC do evento: estruturado, mas só daquele evento (não vira
-- LocalEvento reutilizável). Terceira forma de localização, além de local_id e local_texto.
ALTER TABLE evento
    ADD COLUMN cep         VARCHAR(9),
    ADD COLUMN logradouro  VARCHAR(255),
    ADD COLUMN numero      VARCHAR(20),
    ADD COLUMN complemento VARCHAR(255),
    ADD COLUMN bairro      VARCHAR(255),
    ADD COLUMN cidade      VARCHAR(255),
    ADD COLUMN uf          CHAR(2);

-- As três formas são mutuamente exclusivas (validado em EventoService; isto é rede de
-- segurança). Substitui o CHECK antigo "local_id IS NULL OR local_texto IS NULL".
ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_local_id_ou_texto;
ALTER TABLE evento DROP CONSTRAINT IF EXISTS evento_local_check;

ALTER TABLE evento ADD CONSTRAINT evento_localizacao_unica CHECK (
    (CASE WHEN local_id IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN local_texto IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN cep IS NOT NULL OR logradouro IS NOT NULL OR cidade IS NOT NULL THEN 1 ELSE 0 END)
  <= 1
);
```

> **Verificar antes:** o nome real do CHECK atual. Rodar
> `grep -rn "local_id IS NULL OR local_texto" src/main/resources/db/migration/` e, se o
> CHECK tiver nome explícito diferente, ajustar os `DROP CONSTRAINT IF EXISTS`. Se for um
> CHECK sem nome (inline na criação da tabela em V1/V3), descobrir o nome gerado pelo
> Postgres com `\d evento` num banco de teste e dropar por esse nome.

- [ ] **Step 2: Escrever o teste da migration**

`src/test/java/com/domus/api/modules/evento/EventoEnderecoAdHocMigracaoTest.java`:

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
class EventoEnderecoAdHocMigracaoTest implements PostgresTestContainerSupport {

    @Autowired JdbcTemplate jdbc;

    @Test
    void colunasDeEnderecoAdHocExistem() {
        Integer n = jdbc.queryForObject("""
            SELECT count(*) FROM information_schema.columns
            WHERE table_name = 'evento'
              AND column_name IN ('cep','logradouro','numero','complemento','bairro','cidade','uf')
            """, Integer.class);
        assertThat(n).isEqualTo(7);
    }

    @Test
    void checkRecusaDuasFormasDeLocalizacao() {
        // Um evento com local_texto E endereço ad-hoc ao mesmo tempo viola o CHECK.
        String igrejaId = jdbc.queryForObject("SELECT id FROM igreja LIMIT 1", String.class);
        if (igrejaId == null) {
            jdbc.update("INSERT INTO igreja (id, nome, email) VALUES (gen_random_uuid(), 'T', 't@t.com')");
            igrejaId = jdbc.queryForObject("SELECT id FROM igreja LIMIT 1", String.class);
        }
        final String ig = igrejaId;
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_texto, cidade)
            VALUES (gen_random_uuid(), ?::uuid, 'Ev', now(), 'Chácara', 'Recife')
            """, ig))
            .hasMessageContaining("evento_localizacao_unica");
    }
}
```

> Ajustar as colunas obrigatórias do INSERT (`titulo`, `inicio_em`, `igreja_id`) se o schema
> exigir mais NOT NULLs — conferir com `\d evento`.

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=EventoEnderecoAdHocMigracaoTest` (precisa de Docker)
Expected: FAIL (colunas não existem / CHECK não barra).

- [ ] **Step 4: Adicionar o campo em `Evento.java`**

Perto de `private String localTexto;` (linha ~51):

```java
    // Endereço estruturado AD-HOC (só deste evento, não vira LocalEvento).
    // Terceira forma de localização — exclusiva com `local` e `localTexto` (ver EventoService).
    @jakarta.persistence.Embedded
    private com.domus.api.shared.dominio.Endereco enderecoLocal;
```

- [ ] **Step 5: Atualizar `getLocalExibicao()`**

```java
    public String getLocalExibicao() {
        if (local != null) return local.getNome();
        if (localTexto != null) return localTexto;
        return com.domus.api.shared.dominio.EnderecoFormatter.emLinhaUnica(enderecoLocal);
    }
```

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=EventoEnderecoAdHocMigracaoTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/db/migration/V36__evento_endereco_adhoc.sql src/main/java/com/domus/api/modules/evento/Evento.java src/test/java/com/domus/api/modules/evento/EventoEnderecoAdHocMigracaoTest.java
git commit -m "feat(evento): coluna de endereço ad-hoc no evento (V36)"
```

---

## Task 3: `resolverLocalizacao` — XOR de 3 formas

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java`

**Interfaces:**
- Consumes: `Endereco.estaPreenchido()` (Task 1).
- Produces:
  - `EventoRequest.enderecoLocal()` → `EnderecoDTO` (nulável).
  - `record EventoService.Localizacao(LocalEvento local, String localTexto, Endereco enderecoLocal)` — no máximo um campo não-nulo.
  - `private Localizacao resolverLocalizacao(EventoRequest data, UUID igrejaId)` — substitui `resolverLocal`.

- [ ] **Step 1: Adicionar o campo no `EventoRequest`**

Depois de `String localTexto,` (linha ~27), antes de `String tipo`:

```java
        /** Endereço estruturado ad-hoc — só deste evento. Exclusivo com {@code localId} e {@code localTexto}. */
        @jakarta.validation.Valid
        com.domus.api.modules.pessoa.DTO.EnderecoDTO enderecoLocal,
```

- [ ] **Step 2: Escrever os testes no `EventoServiceTest`**

Localizar o padrão de setup da classe (mocks de `localEventoRepository`, `igrejaRepository`, etc.) e o helper de `EventoRequest`. Adicionar:

```java
    @Test
    void recusaLocalCadastradoJuntoComEnderecoAdHoc() {
        EventoRequest req = requestBase().withLocalId(UUID.randomUUID())
                .withEnderecoLocal(new EnderecoDTO(null, "Rua X", null, null, null, "Recife", "PE"));
        assertThatThrownBy(() -> service.cadastrarEvento(req, igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("uma forma");
    }

    @Test
    void recusaTextoSimplesJuntoComEnderecoAdHoc() {
        EventoRequest req = requestBase().withLocalTexto("Chácara do João")
                .withEnderecoLocal(new EnderecoDTO(null, null, null, null, null, "Recife", "PE"));
        assertThatThrownBy(() -> service.cadastrarEvento(req, igrejaId, usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("uma forma");
    }

    @Test
    void aceitaSomenteEnderecoAdHoc_gravaOEnderecoELimpaOsOutros() {
        EnderecoDTO end = new EnderecoDTO("50000-000", "Rua das Flores", "123", null, "Centro", "Recife", "PE");
        EventoRequest req = requestBase().withEnderecoLocal(end);

        service.cadastrarEvento(req, igrejaId, usuarioId);

        Evento salvo = capturarEventoSalvo();
        assertThat(salvo.getLocal()).isNull();
        assertThat(salvo.getLocalTexto()).isNull();
        assertThat(salvo.getEnderecoLocal()).isNotNull();
        assertThat(salvo.getEnderecoLocal().getCidade()).isEqualTo("Recife");
    }

    @Test
    void aceitaEventoSemLocalNenhum() {
        service.cadastrarEvento(requestBase(), igrejaId, usuarioId);
        Evento salvo = capturarEventoSalvo();
        assertThat(salvo.getLocal()).isNull();
        assertThat(salvo.getLocalTexto()).isNull();
        assertThat(salvo.getEnderecoLocal() == null || !salvo.getEnderecoLocal().estaPreenchido()).isTrue();
    }
```

> Adaptar `requestBase()`, `withLocalId`/`withLocalTexto`/`withEnderecoLocal` e
> `capturarEventoSalvo()` ao estilo real do arquivo (o `EventoServiceTest` pode montar o
> `EventoRequest` com um construtor gigante ou um builder de teste — seguir o que já existe;
> se for construtor cru, escrever um helper `requestBase()` que devolve um record com todos
> os campos e ir sobrescrevendo por cópia). `capturarEventoSalvo()` = `ArgumentCaptor<Evento>`
> em `verify(eventoRepository).save(...)`.

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=EventoServiceTest` (precisa de Docker — é `@SpringBootTest`)
Expected: erro de compilação (`enderecoLocal`, `EnderecoDTO` não usados ainda / método não existe).

- [ ] **Step 4: Implementar `resolverLocalizacao`**

Substituir `resolverLocal` (linha ~661) por:

```java
    /** Record com no máximo um campo não-nulo — a forma de localização escolhida. */
    public record Localizacao(LocalEvento local, String localTexto, com.domus.api.shared.dominio.Endereco enderecoLocal) {}

    /** As três formas de dizer onde o evento acontece são mutuamente exclusivas.
     *  A constraint do banco é rede de segurança; a validação de verdade é aqui. */
    private Localizacao resolverLocalizacao(EventoRequest data, UUID igrejaId) {
        boolean temTexto = data.localTexto() != null && !data.localTexto().isBlank();
        com.domus.api.shared.dominio.Endereco endereco = mapearEndereco(data.enderecoLocal());
        boolean temEndereco = endereco != null && endereco.estaPreenchido();

        int formas = (data.localId() != null ? 1 : 0) + (temTexto ? 1 : 0) + (temEndereco ? 1 : 0);
        if (formas > 1) {
            throw new BusinessException("LOCALIZACAO_AMBIGUA",
                    "Escolha só uma forma de definir o local: um endereço cadastrado, "
                    + "um texto simples, ou um endereço completo.");
        }

        if (data.localId() != null) {
            LocalEvento local = localEventoRepository.findByIdAndIgrejaId(data.localId(), igrejaId)
                    .orElseThrow(() -> new BusinessException("LOCAL_NAO_ENCONTRADO", "Local não encontrado."));
            return new Localizacao(local, null, null);
        }
        if (temTexto) return new Localizacao(null, TextoUtil.capitalizar(data.localTexto()), null);
        if (temEndereco) return new Localizacao(null, null, endereco);
        return new Localizacao(null, null, null);
    }

    private com.domus.api.shared.dominio.Endereco mapearEndereco(com.domus.api.modules.pessoa.DTO.EnderecoDTO dto) {
        if (dto == null) return null;
        return com.domus.api.shared.dominio.Endereco.builder()
                .cep(dto.cep()).logradouro(dto.logradouro()).numero(dto.numero())
                .complemento(dto.complemento()).bairro(dto.bairro()).cidade(dto.cidade()).uf(dto.uf())
                .build();
    }
```

- [ ] **Step 5: Usar em `cadastrarEvento`**

Trocar (linha ~108):

```java
        LocalEvento local = resolverLocal(data, igrejaId);
```

por:

```java
        Localizacao loc = resolverLocalizacao(data, igrejaId);
```

e no builder do `Evento` (linhas ~124-125), trocar:

```java
                .local(local)
                .localTexto(local == null ? TextoUtil.capitalizar(data.localTexto()) : null)
```

por:

```java
                .local(loc.local())
                .localTexto(loc.localTexto())
                .enderecoLocal(loc.enderecoLocal())
```

- [ ] **Step 6: Usar em `atualizarEvento`**

Trocar (linha ~180) `LocalEvento local = resolverLocal(data, igrejaId);` por
`Localizacao loc = resolverLocalizacao(data, igrejaId);`.

Trocar (linhas ~209-210):

```java
        evento.setLocal(local);
        evento.setLocalTexto(local == null ? TextoUtil.capitalizar(data.localTexto()) : null);
```

por:

```java
        evento.setLocal(loc.local());
        evento.setLocalTexto(loc.localTexto());
        evento.setEnderecoLocal(loc.enderecoLocal());
```

Ajustar a detecção de "mudou de local" (linhas ~201-202, 277-278): incluir
`enderecoLocal` antigo/novo na comparação que dispara a notificação
`"...mudou de data ou local."`:

```java
        String localExibicaoAntigo = evento.getLocalExibicao();
        // ... depois do set:
        boolean mudouLocal = !java.util.Objects.equals(localIdAntigo, salvo.getLocal() != null ? salvo.getLocal().getId() : null)
                || !java.util.Objects.equals(localTextoAntigo, salvo.getLocalTexto())
                || !java.util.Objects.equals(localExibicaoAntigo, salvo.getLocalExibicao());
```

> Ler o trecho real de detecção de mudança e adaptar — a variável e o `if` exatos podem
> diferir; o objetivo é: trocar o endereço ad-hoc também conta como "mudou de local".

- [ ] **Step 7: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=EventoServiceTest`
Expected: PASS (todos, incluindo os 4 novos).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/ src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "feat(evento): localização em 3 formas exclusivas (endereço cadastrado, texto, endereço ad-hoc)"
```

---

## Task 4: `EventoResponse.LocalInfo` — expor o endereço ad-hoc

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/local/DTOs/LocalEventoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` (ou onde `LocalInfo.from` é testado)

**Interfaces:**
- Consumes: `EnderecoFormatter.emLinhaUnica` (Task 1), `Evento.getEnderecoLocal()` (Task 2).
- Produces: `EventoResponse.LocalInfo` ganha o campo final `com.domus.api.modules.pessoa.DTO.EnderecoDTO enderecoLocal` (null exceto no caso ad-hoc estruturado). O front usa isso para reidratar o modo "endereço completo".

- [ ] **Step 1: Escrever o teste**

```java
    @Test
    void localInfoDoEnderecoAdHoc_temTextoFormatadoEEstruturado() {
        Evento e = Evento.builder()
                .enderecoLocal(com.domus.api.shared.dominio.Endereco.builder()
                        .logradouro("Rua das Flores").numero("123").cidade("Recife").uf("PE").build())
                .build();
        EventoResponse.LocalInfo info = EventoResponse.LocalInfo.from(e);
        assertThat(info.id()).isNull();
        assertThat(info.nome()).isEqualTo("Rua das Flores, 123 - Recife/PE");
        assertThat(info.enderecoLocal()).isNotNull();
        assertThat(info.enderecoLocal().cidade()).isEqualTo("Recife");
    }
```

> Se `LocalInfo.from` for package-private, o teste vai no mesmo pacote
> (`com.domus.api.modules.evento.DTOs`). Criar `EventoResponseLocalInfoTest` nesse pacote
> se `EventoServiceTest` não alcançar.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q -o test -Dtest=EventoServiceTest` (ou a classe nova)
Expected: erro de compilação (`enderecoLocal()` não existe em `LocalInfo`).

- [ ] **Step 3: Alterar `LocalInfo`**

```java
    public record LocalInfo(UUID id, String nome, String endereco, boolean enderecoHerdado,
                            com.domus.api.modules.pessoa.DTO.EnderecoDTO enderecoLocal) {
        static LocalInfo from(Evento e) {
            LocalEvento local = e.getLocal();
            if (local != null) {
                var r = com.domus.api.modules.evento.local.DTOs.LocalEventoResponse.from(local);
                return new LocalInfo(local.getId(), local.getNome(), r.endereco(), r.enderecoHerdado(), null);
            }
            if (e.getLocalTexto() != null) {
                return new LocalInfo(null, e.getLocalTexto(), null, false, null);
            }
            var end = e.getEnderecoLocal();
            if (end != null && end.estaPreenchido()) {
                String fmt = com.domus.api.shared.dominio.EnderecoFormatter.emLinhaUnica(end);
                return new LocalInfo(null, fmt, fmt, false, paraDTO(end));
            }
            return null;
        }

        private static com.domus.api.modules.pessoa.DTO.EnderecoDTO paraDTO(com.domus.api.shared.dominio.Endereco e) {
            return new com.domus.api.modules.pessoa.DTO.EnderecoDTO(
                    e.getCep(), e.getLogradouro(), e.getNumero(), e.getComplemento(),
                    e.getBairro(), e.getCidade(), e.getUf());
        }
    }
```

- [ ] **Step 4: Simplificar `LocalEventoResponse` para usar `EnderecoFormatter`**

Ler `formatarEnderecoDaIgreja(Endereco e)` em `LocalEventoResponse.java` e trocar o corpo por
`return EnderecoFormatter.emLinhaUnica(e);` (import `com.domus.api.shared.dominio.EnderecoFormatter`).
Rodar `LocalEventoResponseTest` (se existir) e o teste de `LocalEvento` para garantir que o
formato exibido não regrediu; se um teste afirma um formato antigo específico, avaliar se o
novo formato é aceitável e **ajustar o teste com justificativa** (não enfraquecer).

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q -o test -Dtest=EventoServiceTest,LocalEventoResponseTest`
Expected: PASS.

- [ ] **Step 6: Rodar a suíte inteira**

Run: `mvn -q -o test`
Expected: BUILD SUCCESS (pode haver 2 skipped conhecidos).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/ src/test/java/com/domus/api/modules/evento/
git commit -m "feat(evento): LocalInfo expõe o endereço ad-hoc formatado e estruturado"
```

---

## Task 5: Front — tipos + `UF_OPTIONS` + formatadores de endereço

**Files:**
- Modify: `src/types/evento.type.ts`
- Create: `src/lib/ufs.ts`
- Create: `src/lib/formats/endereco.ts`

**Interfaces:**
- Produces:
  - `EventoRequest.enderecoLocal?: Endereco` (tipo `Endereco` de `@/types/pessoa.type`).
  - `LocalInfo.enderecoLocal: Endereco | null`.
  - `UF_OPTIONS: { value: string; label: string }[]` em `@/lib/ufs`.
  - `enderecoParaLinhaUnica(e: Endereco): string` e
    `enderecoIgrejaParaCamposCompactos(e: Endereco): { linha1: string; linha2: string }` em `@/lib/formats/endereco`.

- [ ] **Step 1: Adicionar campos em `evento.type.ts`**

No tipo `EventoRequest`, junto de `localId?`/`localTexto?`:

```typescript
  enderecoLocal?: import('./pessoa.type').Endereco
```

No tipo da `LocalInfo` (o objeto `local` dentro de `EventoResponse` / `EventoDetalhe` — localizar):

```typescript
  enderecoLocal: import('./pessoa.type').Endereco | null
```

> Conferir o nome exato do tipo do campo `local` no `evento.type.ts` (pode ser `EventoLocalInfo`).

- [ ] **Step 2: Extrair `UF_OPTIONS`**

Criar `src/lib/ufs.ts` copiando o array de `src/components/module/pessoas/PessoaForm.tsx:42`:

```typescript
export const UF_OPTIONS = [
  { value: 'AC', label: 'AC' }, /* ...todas as 27... */ { value: 'TO', label: 'TO' },
] as const
```

Trocar as declarações locais em `PessoaForm.tsx` e `VisitanteForm.tsx` por
`import { UF_OPTIONS } from '@/lib/ufs'`. Rodar `npx tsc --noEmit`.

- [ ] **Step 3: Criar `src/lib/formats/endereco.ts`**

```typescript
import type { Endereco } from '@/types/pessoa.type'

/** "Rua X, 123, Apto 2 - Centro, Recife/PE (50000-000)" — partes vazias omitidas. */
export function enderecoParaLinhaUnica(e: Endereco): string {
  const linha = [e.logradouro, e.numero, e.complemento].filter(Boolean).join(', ')
  const cidadeUf = e.cidade && e.uf ? `${e.cidade}/${e.uf}` : e.cidade || e.uf || ''
  const bairroCidade = [e.bairro, cidadeUf].filter(Boolean).join(', ')
  let out = [linha, bairroCidade].filter(Boolean).join(' - ')
  if (e.cep) out += ` (${e.cep})`
  return out
}

/** Para os 2 campos compactos do ModalLocalForm (cepLogradouroNumero / complementoBairroCidadeUf). */
export function enderecoIgrejaParaCamposCompactos(e: Endereco): { linha1: string; linha2: string } {
  const linha1 = [e.cep, e.logradouro, e.numero].filter(Boolean).join(', ')
  const cidadeUf = e.cidade && e.uf ? `${e.cidade}/${e.uf}` : e.cidade || e.uf || ''
  const linha2 = [e.complemento, e.bairro, cidadeUf].filter(Boolean).join(' - ')
  return { linha1, linha2 }
}
```

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/evento.type.ts frontend/src/lib/ufs.ts frontend/src/lib/formats/endereco.ts frontend/src/components/module/pessoas/PessoaForm.tsx frontend/src/components/module/visitantes/VisitanteForm.tsx
git commit -m "feat(evento): tipos e formatadores para endereço ad-hoc; extrai UF_OPTIONS"
```

---

## Task 6: Front — validators do evento com `enderecoLocal`

**Files:**
- Modify: `src/lib/validators.ts`

**Interfaces:**
- Consumes: nada novo.
- Produces: `eventoSchemaBase` ganha `enderecoLocal` (objeto opcional de 7 strings) e `eventoSchema` ganha um `.superRefine` que garante: no máximo uma das três formas (`localId`, `localTexto`, `enderecoLocal` com conteúdo); e se `enderecoLocal` tem qualquer campo, exige `cidade`.

- [ ] **Step 1: Adicionar o campo em `eventoSchemaBase`**

Junto de `localId`/`localTexto` (linha ~129):

```typescript
  enderecoLocal: z.object({
    cep: z.string().optional(),
    logradouro: z.string().optional(),
    numero: z.string().optional(),
    complemento: z.string().optional(),
    bairro: z.string().optional(),
    cidade: z.string().optional(),
    uf: z.string().max(2).optional(),
  }).partial().optional(),
```

- [ ] **Step 2: Adicionar o refine no `eventoSchema`**

No fim da cadeia de `.refine(...)` de `eventoSchema` (após o último, linha ~204):

```typescript
.superRefine((data, ctx) => {
  const temEndereco = !!data.enderecoLocal
    && Object.values(data.enderecoLocal).some((v) => typeof v === 'string' && v.trim() !== '')
  const formas = [!!data.localId, !!data.localTexto?.trim(), temEndereco].filter(Boolean).length
  if (formas > 1) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Escolha só uma forma de definir o local.',
      path: ['enderecoLocal'],
    })
  }
  if (temEndereco && !data.enderecoLocal?.cidade?.trim()) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'Informe ao menos a cidade.',
      path: ['enderecoLocal', 'cidade'],
    })
  }
})
```

- [ ] **Step 3: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros (o tipo `EventoFormData`/`EventoFormInput` inclui `enderecoLocal` agora).

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/validators.ts
git commit -m "feat(evento): validação do endereço ad-hoc no schema do formulário"
```

---

## Task 7: Front — `SeletorLocal` com 3 modos + endereço completo

**Files:**
- Modify: `src/components/module/eventos/SeletorLocal.tsx`
- Modify: `src/components/module/eventos/SeletorLocal.module.css`

**Interfaces:**
- Consumes: `UF_OPTIONS` (Task 5), `useBuscaCep`, `useMinhaIgreja`, `formatarCep` de `@/lib/masks`, `enderecoIgrejaParaCamposCompactos` não (esse é do ModalLocalForm).
- Produces: `SeletorLocal` passa a aceitar props:
  - `enderecoLocal?: Endereco`
  - `onChangeEnderecoLocal: (e: Endereco | undefined) => void`
  - `errosEndereco?: Partial<Record<keyof Endereco, string>>`
  E os 3 modos: `'cadastrado' | 'simples' | 'completo'`.

- [ ] **Step 1: Reescrever `SeletorLocal.tsx`**

```tsx
'use client'

import { useState } from 'react'
import { MapPin, Plus, Landmark } from 'lucide-react'
import { SelectMenu } from '@/components/common/SelectMenu/SelectMenu'
import { InputComSugestoes } from '@/components/common/InputComSugestoes/InputComSugestoes'
import { Input } from '@/components/common/input/Input'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useLocaisEvento } from '@/hooks/evento/useLocaisEvento'
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { useBuscaCep } from '@/hooks/pessoa/useBuscaCep'
import { formatarCep } from '@/lib/masks'
import { UF_OPTIONS } from '@/lib/ufs'
import { ModalLocalForm } from './ModalLocalForm'
import styles from './SeletorLocal.module.css'
import type { LocalEventoResponse } from '@/types/evento.type'
import type { Endereco } from '@/types/pessoa.type'

type Modo = 'cadastrado' | 'simples' | 'completo'

interface SeletorLocalProps {
  localId?: string
  localTexto?: string
  enderecoLocal?: Endereco
  error?: string
  errosEndereco?: Partial<Record<keyof Endereco, string>>
  onChangeLocalId: (id: string | undefined) => void
  onChangeLocalTexto: (texto: string | undefined) => void
  onChangeEnderecoLocal: (e: Endereco | undefined) => void
  onCapacidadeSugerida?: (capacidade: number) => void
}

function enderecoTemConteudo(e?: Endereco): boolean {
  return !!e && Object.values(e).some((v) => typeof v === 'string' && v.trim() !== '')
}

export function SeletorLocal({
  localId, localTexto, enderecoLocal, error, errosEndereco,
  onChangeLocalId, onChangeLocalTexto, onChangeEnderecoLocal, onCapacidadeSugerida,
}: SeletorLocalProps) {
  const { data: locais = [] } = useLocaisEvento()
  const { data: igreja } = useMinhaIgreja()
  const { buscar, carregando: carregandoCep } = useBuscaCep()

  const [modo, setModo] = useState<Modo>(() => {
    if (enderecoTemConteudo(enderecoLocal)) return 'completo'
    if (localTexto && !localId) return 'simples'
    return 'cadastrado'
  })
  const [modalAberto, setModalAberto] = useState(false)

  function trocarModo(novo: Modo) {
    setModo(novo)
    if (novo !== 'cadastrado') onChangeLocalId(undefined)
    if (novo !== 'simples') onChangeLocalTexto(undefined)
    if (novo !== 'completo') onChangeEnderecoLocal(undefined)
  }

  function patchEndereco(patch: Partial<Endereco>) {
    onChangeEnderecoLocal({ ...(enderecoLocal ?? {}), ...patch })
  }

  function selecionar(id: string) {
    onChangeLocalId(id || undefined)
    const l = locais.find((x) => x.id === id)
    if (l?.capacidade != null) onCapacidadeSugerida?.(l.capacidade)
  }

  function aoCriar(l: LocalEventoResponse) {
    trocarModo('cadastrado')
    onChangeLocalId(l.id)
    if (l.capacidade != null) onCapacidadeSugerida?.(l.capacidade)
  }

  async function aoSairDoCep(valor: string) {
    const achado = await buscar(valor)
    if (!achado) return
    patchEndereco({
      cep: achado.cep,
      logradouro: achado.logradouro || enderecoLocal?.logradouro,
      bairro: achado.bairro || enderecoLocal?.bairro,
      cidade: achado.cidade || enderecoLocal?.cidade,
      uf: achado.uf || enderecoLocal?.uf,
    })
  }

  function usarEnderecoDaIgreja() {
    if (!igreja?.endereco) return
    onChangeEnderecoLocal({ ...igreja.endereco })
  }

  const BOTOES: { modo: Modo; label: string }[] = [
    { modo: 'cadastrado', label: 'Endereço cadastrado' },
    { modo: 'simples', label: 'Digitar simples' },
    { modo: 'completo', label: 'Endereço completo' },
  ]

  return (
    <div className={styles.wrapper}>
      <div className={styles.segmentado} role="group" aria-label="Como definir o local do evento">
        {BOTOES.map((b) => (
          <button
            key={b.modo}
            type="button"
            className={`${styles.segmentoBtn} ${modo === b.modo ? styles.segmentoAtivo : ''}`}
            aria-pressed={modo === b.modo}
            onClick={() => trocarModo(b.modo)}
          >
            {b.label}
          </button>
        ))}
      </div>

      <Transicao key={modo} modo="fade" className={styles.wrapper}>
        {modo === 'simples' && (
          <InputComSugestoes
            id="local-texto"
            label="ONDE VAI SER"
            placeholder="Ex: Chácara do João, Praça da Matriz"
            sugestoes={[]}
            value={localTexto ?? ''}
            error={error}
            registerProps={{
              value: localTexto ?? '',
              onChange: (e) => onChangeLocalTexto(e.target.value || undefined),
            }}
            onSelecionarSugestao={() => {}}
          />
        )}

        {modo === 'completo' && (
          <div className={styles.enderecoCompleto}>
            {igreja?.endereco && (
              <button type="button" className={styles.botaoNovo} onClick={usarEnderecoDaIgreja}>
                <Landmark size={16} aria-hidden="true" />
                Usar o endereço da igreja
              </button>
            )}
            <div className={styles.gridEndereco}>
              <div className={styles.spanFull}>
                <Input
                  id="ev-cep" label="CEP" placeholder="00000-000" inputMode="numeric" maxLength={9}
                  value={enderecoLocal?.cep ?? ''}
                  error={errosEndereco?.cep}
                  onChange={(e) => patchEndereco({ cep: formatarCep(e.target.value) })}
                  onBlur={(e) => void aoSairDoCep(e.target.value)}
                />
                {carregandoCep && <span className={styles.erro}>buscando CEP…</span>}
              </div>
              <div className={styles.spanFull}>
                <Input id="ev-logradouro" label="LOGRADOURO" placeholder="Rua, avenida…"
                  value={enderecoLocal?.logradouro ?? ''} error={errosEndereco?.logradouro}
                  onChange={(e) => patchEndereco({ logradouro: e.target.value })} />
              </div>
              <Input id="ev-numero" label="NÚMERO" placeholder="123, s/n…"
                value={enderecoLocal?.numero ?? ''} error={errosEndereco?.numero}
                onChange={(e) => patchEndereco({ numero: e.target.value })} />
              <Input id="ev-complemento" label="COMPLEMENTO" placeholder="Bloco, sala…"
                value={enderecoLocal?.complemento ?? ''} error={errosEndereco?.complemento}
                onChange={(e) => patchEndereco({ complemento: e.target.value })} />
              <Input id="ev-bairro" label="BAIRRO" placeholder="Centro…"
                value={enderecoLocal?.bairro ?? ''} error={errosEndereco?.bairro}
                onChange={(e) => patchEndereco({ bairro: e.target.value })} />
              <Input id="ev-cidade" label="CIDADE" placeholder="Recife…"
                value={enderecoLocal?.cidade ?? ''} error={errosEndereco?.cidade}
                onChange={(e) => patchEndereco({ cidade: e.target.value })} />
              <div className={styles.campoUf}>
                <span className={styles.label}>UF</span>
                <SelectMenu
                  value={enderecoLocal?.uf ?? ''}
                  onChange={(v) => patchEndereco({ uf: v })}
                  placeholder="UF" ariaLabel="Estado (UF)" options={UF_OPTIONS}
                />
                {errosEndereco?.uf && <span className={styles.erro}>{errosEndereco.uf}</span>}
              </div>
            </div>
          </div>
        )}

        {modo === 'cadastrado' && (
          locais.length === 0 ? (
            <div className={styles.vazio}>
              <MapPin size={22} aria-hidden="true" className={styles.vazioIcone} />
              <p className={styles.vazioTexto}>
                Nenhum endereço cadastrado ainda. Cadastre um para reaproveitar
                nos próximos eventos — ou use <strong>&quot;Digitar simples&quot;</strong> /
                <strong> &quot;Endereço completo&quot;</strong> só para este.
              </p>
              <button type="button" className={styles.botaoCadastrar} onClick={() => setModalAberto(true)}>
                <Plus size={16} aria-hidden="true" />
                Cadastrar endereço
              </button>
              {error && <span className={styles.erro}>{error}</span>}
            </div>
          ) : (
            <>
              <label className={styles.label}>ENDEREÇO DO EVENTO</label>
              <SelectMenu
                value={localId ?? ''}
                onChange={selecionar}
                placeholder="Selecione um endereço"
                ariaLabel="Endereço do evento"
                options={locais.map((l) => ({
                  value: l.id,
                  label: l.capacidade != null ? `${l.nome} — cap. ${l.capacidade}` : l.nome,
                }))}
              />
              <button type="button" className={styles.botaoNovo} onClick={() => setModalAberto(true)}>
                <Plus size={16} aria-hidden="true" />
                Novo endereço
              </button>
              {error && <span className={styles.erro}>{error}</span>}
            </>
          )
        )}
      </Transicao>

      {modalAberto && (
        <ModalLocalForm local={null} onClose={() => setModalAberto(false)} onCriado={aoCriar} />
      )}
    </div>
  )
}
```

- [ ] **Step 2: CSS — grid de endereço + segmentado que empilha**

Adicionar em `SeletorLocal.module.css`:

```css
.enderecoCompleto { display: flex; flex-direction: column; gap: 10px; }

.gridEndereco {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.spanFull { grid-column: 1 / -1; }
.campoUf { display: flex; flex-direction: column; gap: 6px; }

@media (max-width: 640px) {
  .segmentado { flex-direction: column; }
  .gridEndereco { grid-template-columns: 1fr; }
}
```

- [ ] **Step 3: Typecheck + lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/SeletorLocal.tsx`
Expected: sem erros.

- [ ] **Step 4: Commit** (só após o autor testar a Task 8, que liga tudo)

Segurar o commit; a Task 8 completa a integração. Se preferir commits menores, commitar
aqui com `git add frontend/src/components/module/eventos/SeletorLocal.*` e mensagem
`feat(evento): SeletorLocal com 3 modos e endereço completo ad-hoc` — mas o componente só
funciona de verdade depois da Task 8.

---

## Task 8: Front — ligar `SeletorLocal` no `EventoForm` / `useEventoForm`

**Files:**
- Modify: `src/components/module/eventos/EventoForm.tsx:180-197`
- Modify: `src/hooks/evento/useEventoForm.ts` (defaults ~75, reidrata ~129, payload ~225)

**Interfaces:**
- Consumes: props novas de `SeletorLocal` (Task 7).
- Produces: o formulário lê/grava `enderecoLocal`; o payload de submit inclui `enderecoLocal` (undefined quando não é o modo completo).

- [ ] **Step 1: `useEventoForm` — default**

Em `defaultValues` (~linha 75), junto de `localId: undefined, localTexto: undefined`:

```typescript
      enderecoLocal: undefined,
```

- [ ] **Step 2: `useEventoForm` — reidratar na edição**

No `reset({...})` do `useEffect` (~linha 129), trocar o bloco de `localId`/`localTexto` por:

```typescript
        localId: eventoInicial.local?.id ?? undefined,
        localTexto:
          eventoInicial.local && eventoInicial.local.id == null && !eventoInicial.local.enderecoLocal
            ? eventoInicial.local.nome
            : undefined,
        enderecoLocal: eventoInicial.local?.enderecoLocal ?? undefined,
```

- [ ] **Step 3: `useEventoForm` — payload de submit**

No `payload` de `onSubmit` (~linha 225), junto de `localId`/`localTexto`:

```typescript
        localId: data.localId || undefined,
        localTexto: data.localTexto || undefined,
        enderecoLocal:
          data.enderecoLocal && Object.values(data.enderecoLocal).some((v) => typeof v === 'string' && v.trim() !== '')
            ? data.enderecoLocal
            : undefined,
```

- [ ] **Step 4: `EventoForm.tsx` — passar as props**

Onde `<SeletorLocal ... />` é renderizado (linha ~181), adicionar:

```tsx
              enderecoLocal={watch('enderecoLocal') as import('@/types/pessoa.type').Endereco | undefined}
              errosEndereco={{
                cep: errors.enderecoLocal?.cep?.message,
                logradouro: errors.enderecoLocal?.logradouro?.message,
                numero: errors.enderecoLocal?.numero?.message,
                complemento: errors.enderecoLocal?.complemento?.message,
                bairro: errors.enderecoLocal?.bairro?.message,
                cidade: errors.enderecoLocal?.cidade?.message,
                uf: errors.enderecoLocal?.uf?.message,
              }}
              onChangeEnderecoLocal={(e) => setValue('enderecoLocal', e, { shouldDirty: true, shouldValidate: true })}
```

Manter os `onChangeLocalId`/`onChangeLocalTexto` existentes (o `SeletorLocal` já limpa os
outros ao trocar de modo, mas manter a limpeza no pai não faz mal — deixar como está).

- [ ] **Step 5: Typecheck + lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/EventoForm.tsx src/hooks/evento/useEventoForm.ts`
Expected: sem erros.

- [ ] **Step 6: Teste manual (autor)**

Checklist:
- Criar evento, modo "Endereço completo": preencher CEP → ViaCEP completa; salvar; abrir o
  detalhe → endereço aparece formatado.
- "Usar o endereço da igreja" preenche os 7 campos.
- Alternar entre os 3 modos: cada troca limpa os outros (não sobra `localId` fantasma).
- Salvar sem escolher nada → evento sem local, sem erro.
- Editar um evento que era "digitar simples" → volta no modo simples; trocar para completo,
  salvar, reabrir → volta no modo completo com os campos certos.
- Mobile: segmentado empilha, grid de endereço em 1 coluna, sem overflow horizontal.

- [ ] **Step 7: Commit** (após OK do autor)

```bash
git add frontend/src/components/module/eventos/SeletorLocal.tsx frontend/src/components/module/eventos/SeletorLocal.module.css frontend/src/components/module/eventos/EventoForm.tsx frontend/src/hooks/evento/useEventoForm.ts
git commit -m "feat(evento): terceiro caminho de local — endereço completo só deste evento"
```

---

## Task 9: `ModalLocalForm` — botão "usar endereço da igreja" + tira o texto de herança

**Files:**
- Modify: `src/components/module/eventos/ModalLocalForm.tsx`
- Modify: `src/components/module/eventos/ModalLocalForm.module.css` (botão)

**Interfaces:**
- Consumes: `enderecoIgrejaParaCamposCompactos` (Task 5), `useMinhaIgreja`.

- [ ] **Step 1: Remover o `<p className={styles.subtitle}>`**

Apagar o parágrafo "Deixe o endereço em branco para herdar o endereço da igreja." (fica só o `<h2>` no `.intro`).

- [ ] **Step 2: Botão "usar endereço da igreja"**

Importar:

```tsx
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { enderecoIgrejaParaCamposCompactos } from '@/lib/formats/endereco'
import { Landmark } from 'lucide-react'
```

Dentro do componente:

```tsx
  const { data: igreja } = useMinhaIgreja()
  const { setValue } = useForm(/* já existe — usar o mesmo objeto do useForm atual */)
```

> `ModalLocalForm` já chama `useForm`; adicionar `setValue` à desestruturação do retorno.

Acima do primeiro `<Input>` do form:

```tsx
  {!local && igreja?.endereco && (
    <button
      type="button"
      className={styles.btnUsarIgreja}
      onClick={() => {
        const { linha1, linha2 } = enderecoIgrejaParaCamposCompactos(igreja.endereco!)
        setValue('cepLogradouroNumero', linha1, { shouldDirty: true })
        setValue('complementoBairroCidadeUf', linha2, { shouldDirty: true })
      }}
    >
      <Landmark size={16} aria-hidden="true" />
      Usar o endereço da igreja
    </button>
  )}
```

(Só na criação — `!local` — para não sobrescrever a edição de um endereço existente.)

- [ ] **Step 3: CSS do botão**

```css
.btnUsarIgreja {
  align-self: flex-start;
  display: inline-flex; align-items: center; gap: 6px;
  font-size: var(--font-size-sm); color: var(--color-primary);
  background: none; border: none; padding: 0 0 4px; cursor: pointer;
}
.btnUsarIgreja:hover { text-decoration: underline; }
```

- [ ] **Step 4: Typecheck + lint**

Run: `cd frontend && npx tsc --noEmit && npx eslint src/components/module/eventos/ModalLocalForm.tsx`
Expected: sem erros.

- [ ] **Step 5: Teste manual (autor)**

- No modal de "Novo endereço" (pela tela de gestão E pelo botão dentro do formulário de
  evento): botão "Usar o endereço da igreja" preenche os 2 campos compactos.
- Botão some quando a igreja não tem endereço e no modo edição.
- Cadastrar sem preencher endereço → continua funcionando (herança backend intacta); abrir
  o detalhe do endereço → "Usa o endereço da igreja — não tem um próprio".

- [ ] **Step 6: Commit** (após OK do autor)

```bash
git add frontend/src/components/module/eventos/ModalLocalForm.tsx frontend/src/components/module/eventos/ModalLocalForm.module.css
git commit -m "feat(evento): botão 'usar o endereço da igreja' no cadastro de endereço; remove aviso de herança"
```

---

## Task 10: Verificação final + PR

- [ ] **Step 1: Suíte backend**

Run: `cd backend/api && set -a; . ./.env; set +a; mvn -o test`
Expected: BUILD SUCCESS (2 skipped conhecidos — `MigracaoV3Test`, `UsuarioRepositoryTest`).

- [ ] **Step 2: Front typecheck + lint + build**

Run: `cd frontend && npx tsc --noEmit && npm run lint && npm run build`
Expected: sem erros.

- [ ] **Step 3: Regressão de exibição de local**

Abrir, em `develop`/local, uma lista de eventos com: um evento com endereço cadastrado, um
com texto simples, um com endereço ad-hoc. Conferir que o local aparece certo em: card da
lista, `DrawerDetalheEvento`, `ModalEventoResumo` (tela início), e na busca global (digitar
parte do endereço ad-hoc e achar o evento).

- [ ] **Step 4: Atualizar o diagrama ER no `CLAUDE.md`**

Em `backend/api/CLAUDE.md`, seção "Modelo de dados": no bloco `EVENTO { ... }`, adicionar as
7 colunas de `enderecoLocal` (`cep`, `logradouro`, `numero`, `complemento`, `bairro`,
`cidade`, `uf` — "V36 - endereço ad-hoc; XOR com local_id e local_texto"). Atualizar
"Estado atual: **V32**" → "**V36**". Ajustar o parágrafo de "Cadastro de evento
enriquecido" mencionando a terceira forma de localização.

- [ ] **Step 5: Commit da doc**

```bash
git add backend/api/CLAUDE.md
git commit -m "docs: diagrama ER com endereço ad-hoc do evento (V36)"
```

- [ ] **Step 6: Abrir o PR**

```bash
git push -u origin feat/evento-endereco-fluxo
gh pr create --title "Fluxo de local do evento: 3 caminhos claros + endereço ad-hoc" --body "$(cat <<'EOF'
Reescreve a seção "Local" do formulário de evento para um usuário novo entender de primeira.

## O que muda
- Aba "Locais" → "Endereços" (só rótulo de tela; rota e código continuam `local`/`LocalEvento`).
- `ModalLocalForm` reutilizável pelo formulário de evento (cadastro de endereço sem sair da tela, com auto-seleção).
- Seletor de local vira um segmentado de 3: **endereço cadastrado** · **digitar simples** · **endereço completo** (estruturado, só daquele evento).
- Endereço completo ad-hoc: novo `@Embedded Endereco` em `evento` (migration V36), XOR com `local_id`/`local_texto` validado em `EventoService.resolverLocalizacao`.
- Botão "usar o endereço da igreja" no endereço completo e no modal de cadastro; sai o aviso "deixe em branco para herdar".

## Testes
- Back: `EnderecoTest`, `EnderecoFormatterTest`, `EventoEnderecoAdHocMigracaoTest`, novos cenários em `EventoServiceTest` (XOR de 3 formas). Suíte inteira verde.
- Front: validação manual (sem harness) — checklist no plano.

Spec: `backend/api/docs/superpowers/specs/2026-09-02-evento-endereco-adhoc-design.md`
Plano: `backend/api/docs/superpowers/plans/2026-09-02-evento-endereco-fluxo.md`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

**1. Spec coverage:**
- Migration V36 + `@Embedded Endereco` → Task 2. ✓
- XOR de 3 formas + `resolverLocalizacao` → Task 3. ✓
- `LocalInfo.from` ramo ad-hoc → Task 4. ✓ (spec dizia só `nome`/`endereco`; o plano adiciona `enderecoLocal` estruturado no `LocalInfo` — **refinamento necessário** para o front reidratar o modo "completo" na edição).
- ES / `getLocalExibicao` → Task 2 Step 5 + Task 3 Step 6. ✓
- Segmentado 3-way (empilha no mobile) → Task 7. ✓
- Campos de endereço completo + ViaCEP → Task 7. ✓
- Botão "usar endereço da igreja" nos 2 lugares → Task 7 (SeletorLocal) + Task 9 (ModalLocalForm). ✓
- Remover texto de herança → Task 9 Step 1. ✓
- Validators front → Task 6. ✓
- Testes back → Tasks 1, 2, 3, 4. ✓
- Display (drawer/resumo/lista/busca) → Task 10 Step 3. ✓
- `EnderecoFormatter` compartilhado + `LocalEventoResponse` delega → Task 1 + Task 4 Step 4. ✓
- Sequência de pedaços da spec → Tasks 1-4 (back), 5-9 (front), casando com "1. back / 2-3. front". ✓

**2. Placeholder scan:** As notas ">" são instruções de verificação contextual (nome real do CHECK, estilo do builder de teste), não placeholders de implementação — cada uma diz exatamente o que conferir e como. Nenhum "TODO"/"TBD"/"add error handling".

**3. Type consistency:**
- `resolverLocalizacao` / `record Localizacao(LocalEvento, String, Endereco)` — usado consistente em Tasks 3.
- `LocalInfo(UUID, String, String, boolean, EnderecoDTO)` — 5 args, consistente entre Task 4 Step 3 e o teste Step 1.
- `SeletorLocal` props (`enderecoLocal`, `onChangeEnderecoLocal`, `errosEndereco`) — definidas em Task 7, consumidas em Task 8 com os mesmos nomes.
- `enderecoIgrejaParaCamposCompactos` → `{ linha1, linha2 }` — Task 5 define, Task 9 consome. ✓
- `Endereco` (front) de `@/types/pessoa.type` usado em Tasks 5-9. ✓
- `Modo = 'cadastrado' | 'simples' | 'completo'` — Task 7, e a reidratação em Task 8 casa (`enderecoLocal` presente → completo; `localTexto` sem `enderecoLocal` → simples). ✓
