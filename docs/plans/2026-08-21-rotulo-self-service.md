# Rótulo self-service por igreja — Plano de implementação

> **Para quem for executar:** REQUIRED SUB-SKILL: use superpowers:executing-plans
> task a task. Execução **inline nesta sessão**: back todo primeiro (Tasks 1-8),
> depois front em pedaços pequenos e testáveis um a um (Tasks 9-15), parando pra
> teste manual no navegador entre cada pedaço do front.

**Goal:** cada igreja pode renomear "Ministério", "Congregação" e "Célula" (singular,
plural, gênero) numa tela própria de configurações; o rótulo escolhido substitui o
termo técnico em toda tela — sem mexer no domínio/tipos/nomes internos de código.

**Architecture:** três trios nome+gênero como colunas nuláveis em `igreja`
(`NULL` = usa o padrão do sistema). Endpoint dedicado `PUT /igrejas/minha/rotulos`
pra salvar (independente do form grande de dados institucionais). O objeto resolvido
viaja em `/igrejas/minha` (GET, pra tela de config) e em `/auth/me`/`SessaoDTO` (pra
toda a aplicação, via `useAuthStore`). Front consome com um hook central
`useRotulos()` que aplica o fallback pro padrão quando a igreja não customizou.

**Tech Stack:** Java 21/Spring Boot/JPA/Flyway (back); Next.js/TypeScript/Zustand/
TanStack Query/React Hook Form+Zod (front).

**Spec:** `docs/superpowers/specs/2026-08-21-rotulo-self-service-design.md`

## Global Constraints

- Config é **só front** — nenhum texto gerado no backend (notificações, e-mails) usa
  o rótulo customizado.
- Singular e plural são sempre **digitados pela igreja**, nunca pluralizados
  automaticamente.
- Um trio (singular, plural, gênero) é atômico: os três preenchidos ou os três nulos.
  Validado no service, não em `CHECK` cruzando colunas.
- Nomes técnicos de código (rotas, tipos, `CelulaResponse`, `podeGerenciarCelulas`,
  variáveis `congregacao*`) **nunca mudam** — só o texto visível ao usuário.
- Preview da tela de configuração é genuinamente interativo (nunca `disabled`),
  atualiza a cada tecla — regra de UX do projeto.
- Próxima migration é `V25` (última existente: `V24__resposta_campo_personalizado.sql`).

---

## Task 1: Migration + enum de gênero + colunas na entidade `Igreja`

**Files:**
- Create: `src/main/resources/db/migration/V25__rotulos_customizados_igreja.sql`
- Create: `src/main/java/com/domus/api/modules/igreja/GeneroGramatical.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/Igreja.java`

**Interfaces:**
- Produces: `GeneroGramatical` enum (`MASCULINO`, `FEMININO`), 9 novos getters/setters
  em `Igreja` (`ministerioNomeSingular`, `ministerioNomePlural`, `ministerioGenero`,
  `congregacaoNomeSingular`, `congregacaoNomePlural`, `congregacaoGenero`,
  `celulaNomeSingular`, `celulaNomePlural`, `celulaGenero`).

- [ ] **Step 1: Criar a migration**

```sql
ALTER TABLE igreja
  ADD COLUMN ministerio_nome_singular   VARCHAR(40),
  ADD COLUMN ministerio_nome_plural     VARCHAR(40),
  ADD COLUMN ministerio_genero          VARCHAR(9) CHECK (ministerio_genero IN ('MASCULINO', 'FEMININO')),
  ADD COLUMN congregacao_nome_singular  VARCHAR(40),
  ADD COLUMN congregacao_nome_plural    VARCHAR(40),
  ADD COLUMN congregacao_genero         VARCHAR(9) CHECK (congregacao_genero IN ('MASCULINO', 'FEMININO')),
  ADD COLUMN celula_nome_singular       VARCHAR(40),
  ADD COLUMN celula_nome_plural         VARCHAR(40),
  ADD COLUMN celula_genero              VARCHAR(9) CHECK (celula_genero IN ('MASCULINO', 'FEMININO'));
```

- [ ] **Step 2: Criar o enum**

```java
package com.domus.api.modules.igreja;

public enum GeneroGramatical {
    MASCULINO, FEMININO
}
```

- [ ] **Step 3: Adicionar as 9 colunas na entidade**

Em `Igreja.java`, logo após o campo `sigla` (linha 49):

```java
    @Column(name = "ministerio_nome_singular", length = 40)
    private String ministerioNomeSingular;

    @Column(name = "ministerio_nome_plural", length = 40)
    private String ministerioNomePlural;

    @Enumerated(EnumType.STRING)
    @Column(name = "ministerio_genero", length = 9)
    private GeneroGramatical ministerioGenero;

    @Column(name = "congregacao_nome_singular", length = 40)
    private String congregacaoNomeSingular;

    @Column(name = "congregacao_nome_plural", length = 40)
    private String congregacaoNomePlural;

    @Enumerated(EnumType.STRING)
    @Column(name = "congregacao_genero", length = 9)
    private GeneroGramatical congregacaoGenero;

    @Column(name = "celula_nome_singular", length = 40)
    private String celulaNomeSingular;

    @Column(name = "celula_nome_plural", length = 40)
    private String celulaNomePlural;

    @Enumerated(EnumType.STRING)
    @Column(name = "celula_genero", length = 9)
    private GeneroGramatical celulaGenero;
```

(`@Enumerated` e `@Column`/`@Entity` já vêm do `import jakarta.persistence.*;` existente
no topo do arquivo — nenhum import novo necessário.)

- [ ] **Step 4: Compilar pra confirmar que não quebrou nada**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V25__rotulos_customizados_igreja.sql \
        src/main/java/com/domus/api/modules/igreja/GeneroGramatical.java \
        src/main/java/com/domus/api/modules/igreja/Igreja.java
git commit -m "feat(igreja): colunas de rotulo customizado (ministerio/congregacao/celula)"
```

---

## Task 2: `RotulosDTO` (leitura) + `RotulosRequest` (escrita)

**Files:**
- Create: `src/main/java/com/domus/api/modules/igreja/DTO/RotulosDTO.java`
- Create: `src/main/java/com/domus/api/modules/igreja/DTO/RotulosRequest.java`

**Interfaces:**
- Consumes: `Igreja` (Task 1), `GeneroGramatical` (Task 1).
- Produces: `RotulosDTO.from(Igreja)` — usado pelas Tasks 3, 4 e 6.
  `RotulosRequest` com record aninhado `RotulosRequest.Bloco(String singular, String
  plural, GeneroGramatical genero)` — usado pela Task 3.

- [ ] **Step 1: `RotulosDTO`**

```java
package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.GeneroGramatical;
import com.domus.api.modules.igreja.Igreja;

/** Nulo por campo = a igreja não customizou aquele módulo; o front resolve o padrão. */
public record RotulosDTO(
        String ministerioSingular, String ministerioPlural, GeneroGramatical ministerioGenero,
        String congregacaoSingular, String congregacaoPlural, GeneroGramatical congregacaoGenero,
        String celulaSingular, String celulaPlural, GeneroGramatical celulaGenero) {

    public static RotulosDTO from(Igreja igreja) {
        return new RotulosDTO(
                igreja.getMinisterioNomeSingular(), igreja.getMinisterioNomePlural(), igreja.getMinisterioGenero(),
                igreja.getCongregacaoNomeSingular(), igreja.getCongregacaoNomePlural(), igreja.getCongregacaoGenero(),
                igreja.getCelulaNomeSingular(), igreja.getCelulaNomePlural(), igreja.getCelulaGenero());
    }
}
```

- [ ] **Step 2: `RotulosRequest`**

```java
package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.GeneroGramatical;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Cada bloco é opcional; {@code null} = não mexe nesse módulo. Um bloco presente com
 *  os 3 campos nulos reseta o módulo pro padrão ("Restaurar padrão"). Trio parcialmente
 *  preenchido é rejeitado pelo service (não dá pra expressar isso em Bean Validation
 *  simples sem acoplar os 3 campos). */
public record RotulosRequest(
        @Valid Bloco ministerio,
        @Valid Bloco congregacao,
        @Valid Bloco celula) {

    public record Bloco(
            @Size(max = 40) String singular,
            @Size(max = 40) String plural,
            GeneroGramatical genero) {}
}
```

- [ ] **Step 3: Compilar**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/DTO/RotulosDTO.java \
        src/main/java/com/domus/api/modules/igreja/DTO/RotulosRequest.java
git commit -m "feat(igreja): DTOs de leitura/escrita de rotulos customizados"
```

---

## Task 3: `IgrejaService.atualizarRotulos` + validação de trio + endpoint

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaService.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaController.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/DTO/IgrejaDetalheDTO.java`
- Test: `src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java`

**Interfaces:**
- Consumes: `RotulosRequest`/`RotulosDTO` (Task 2).
- Produces: `IgrejaService.atualizarRotulos(UUID igrejaId, RotulosRequest data): RotulosDTO`
  — usado pela Task 4 (nenhuma, é o fim da cadeia backend de escrita) e pelo front (Task 10).
  `IgrejaDetalheDTO.rotulos(): RotulosDTO` — usado pelo GET `/igrejas/minha` (Task 9).

- [ ] **Step 1: Escrever os testes (falhando)**

Em `IgrejaServiceTest.java`, adicionar após o método `criarIgrejaComAdminRegistraAceiteQuandoTrue`
(ou em qualquer ponto do corpo da classe):

```java
    @Test
    void atualizarRotulosSalvaTrioCompletoDeUmModulo() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        RotulosRequest request = new RotulosRequest(
                new RotulosRequest.Bloco("Departamento", "Departamentos", GeneroGramatical.MASCULINO),
                null, null);

        RotulosDTO resultado = igrejaService.atualizarRotulos(igrejaId, request);

        assertThat(resultado.ministerioSingular()).isEqualTo("Departamento");
        assertThat(resultado.ministerioPlural()).isEqualTo("Departamentos");
        assertThat(resultado.ministerioGenero()).isEqualTo(GeneroGramatical.MASCULINO);
        verify(igrejaRepository).save(igreja);
    }

    @Test
    void atualizarRotulosRecusaTrioParcial() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        RotulosRequest request = new RotulosRequest(
                new RotulosRequest.Bloco("Departamento", null, null), null, null);

        assertThatThrownBy(() -> igrejaService.atualizarRotulos(igrejaId, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("obrigat");

        verify(igrejaRepository, never()).save(any());
    }

    @Test
    void atualizarRotulosComBlocoTotalmenteVazioRestauraPadrao() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste")
                .celulaNomeSingular("Pequeno Grupo").celulaNomePlural("Pequenos Grupos")
                .celulaGenero(GeneroGramatical.MASCULINO)
                .build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        RotulosRequest request = new RotulosRequest(
                null, null, new RotulosRequest.Bloco(null, null, null));

        RotulosDTO resultado = igrejaService.atualizarRotulos(igrejaId, request);

        assertThat(resultado.celulaSingular()).isNull();
        assertThat(resultado.celulaPlural()).isNull();
        assertThat(resultado.celulaGenero()).isNull();
    }
```

Adicionar os imports correspondentes no topo do arquivo:
`com.domus.api.modules.igreja.DTO.RotulosDTO`, `com.domus.api.modules.igreja.DTO.RotulosRequest`,
`com.domus.api.modules.igreja.GeneroGramatical`.

- [ ] **Step 2: Rodar e confirmar que falha (método não existe ainda)**

Run: `mvn -q test -Dtest=IgrejaServiceTest`
Expected: FAIL (compilação — `atualizarRotulos` não existe)

- [ ] **Step 3: Implementar `atualizarRotulos` em `IgrejaService`**

Adicionar após o método `atualizar` (depois da linha 192, `return IgrejaDetalheDTO.from(...)`):

```java
    /** Cada bloco vem completo (3 campos) ou totalmente vazio (reseta pro padrão) — nunca parcial. */
    @Transactional
    public RotulosDTO atualizarRotulos(UUID igrejaId, RotulosRequest data) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        aplicarBloco(data.ministerio(), igreja::setMinisterioNomeSingular,
                igreja::setMinisterioNomePlural, igreja::setMinisterioGenero);
        aplicarBloco(data.congregacao(), igreja::setCongregacaoNomeSingular,
                igreja::setCongregacaoNomePlural, igreja::setCongregacaoGenero);
        aplicarBloco(data.celula(), igreja::setCelulaNomeSingular,
                igreja::setCelulaNomePlural, igreja::setCelulaGenero);

        igrejaRepository.save(igreja);
        cacheManager.getCache("igreja").evictIfPresent(igrejaId);

        log.info("Rótulos customizados atualizados. igreja_id={}", igrejaId);
        return RotulosDTO.from(igreja);
    }

    private void aplicarBloco(
            RotulosRequest.Bloco bloco,
            java.util.function.Consumer<String> setSingular,
            java.util.function.Consumer<String> setPlural,
            java.util.function.Consumer<GeneroGramatical> setGenero) {
        if (bloco == null) return;

        boolean algumPreenchido = bloco.singular() != null || bloco.plural() != null || bloco.genero() != null;
        boolean todosPreenchidos = bloco.singular() != null && !bloco.singular().isBlank()
                && bloco.plural() != null && !bloco.plural().isBlank()
                && bloco.genero() != null;

        if (algumPreenchido && !todosPreenchidos) {
            throw new BusinessException("ROTULO_INCOMPLETO",
                    "Preencha singular, plural e gênero, ou deixe os três em branco pra restaurar o padrão.");
        }

        setSingular.accept(todosPreenchidos ? bloco.singular() : null);
        setPlural.accept(todosPreenchidos ? bloco.plural() : null);
        setGenero.accept(todosPreenchidos ? bloco.genero() : null);
    }
```

Adicionar os imports: `com.domus.api.modules.igreja.DTO.RotulosDTO`,
`com.domus.api.modules.igreja.DTO.RotulosRequest`.

- [ ] **Step 4: Incluir `rotulos` em `IgrejaDetalheDTO`**

Em `IgrejaDetalheDTO.java`, adicionar o campo no record (depois de `diasRestantes`) e
preencher no `.from()`:

```java
        Integer diasRestantes,
        RotulosDTO rotulos) {

    public static IgrejaDetalheDTO from(Igreja igreja, String atualizadoPorNome) {
        // ... (corpo existente sem mudança até o `return`)

        return new IgrejaDetalheDTO(
                // ... (campos existentes sem mudança)
                diasRestantes,
                RotulosDTO.from(igreja));
    }
}
```

- [ ] **Step 5: Rodar os testes de novo**

Run: `mvn -q test -Dtest=IgrejaServiceTest`
Expected: PASS (todos, incluindo os 3 novos)

- [ ] **Step 6: Endpoint no controller**

Em `IgrejaController.java`, adicionar após `atualizarMinhaIgreja`:

```java
    @PutMapping("/minha/rotulos")
    public ResponseEntity<RotulosDTO> atualizarRotulos(
            @RequestBody @Valid RotulosRequest data) {
        return ResponseEntity.ok(igrejaService.atualizarRotulos(usuarioAutenticado.getIgrejaId(), data));
    }
```

Adicionar os imports `com.domus.api.modules.igreja.DTO.RotulosDTO` e
`com.domus.api.modules.igreja.DTO.RotulosRequest`. Não precisa mexer no `SecurityConfig`
— `/igrejas/**` já cai nas regras existentes (mesma cobertura de `PUT /igrejas/minha`).

- [ ] **Step 7: Compilar tudo**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/IgrejaService.java \
        src/main/java/com/domus/api/modules/igreja/IgrejaController.java \
        src/main/java/com/domus/api/modules/igreja/DTO/IgrejaDetalheDTO.java \
        src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java
git commit -m "feat(igreja): endpoint PUT /igrejas/minha/rotulos com validacao de trio"
```

---

## Task 4: Propagar `rotulos` pra `SessaoDTO` / `GET /auth/me`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/auth/DTO/SessaoDTO.java`
- Modify: `src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java`
- Modify: `src/main/java/com/domus/api/modules/auth/AuthService.java`
- Test: `src/test/java/com/domus/api/modules/auth/AuthServiceTest.java`

**Interfaces:**
- Consumes: `RotulosDTO` (Task 2).
- Produces: `SessaoDTO.rotulos(): RotulosDTO` — consumido pelo front (Task 8) via
  `GET /auth/me`.

**Por que aqui:** `/auth/me` é carregado uma vez por sessão em `AuthGuard.tsx` e
alimenta o `useAuthStore` — é o ponto de onde `useRotulos()` vai ler em toda a
aplicação (não do `GET /igrejas/minha`, que só a tela de configuração busca).

- [ ] **Step 1: Adicionar `rotulos` ao final do record principal de `SessaoDTO`**

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
        java.time.LocalDateTime termosAceitosEm,
        com.domus.api.modules.igreja.DTO.RotulosDTO rotulos
) {
    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                List.of(), false, null, null);
    }

    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId,
                      List<String> capacidadesExtras) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                capacidadesExtras, false, null, null);
    }
}
```

Os 5 call sites que usam os construtores telescópicos curtos (login nativo, Google
login/registrar, cadastro de igreja) **não precisam mudar** — `rotulos` entra `null`
por padrão, o que é correto: igreja recém-criada nunca customizou nada ainda.

- [ ] **Step 2: JPQL de `findSessaoById` — incluir o `RotulosDTO` aninhado**

```java
    @Query("""
    SELECT new com.domus.api.modules.auth.DTO.SessaoDTO(
        u.id, u.pessoa.nome, u.role.nome, u.igreja.id, u.igreja.nome,
        u.pessoa.foto.id, u.pessoa.cargo, u.igreja.sigla, u.igreja.logoFoto.id,
        new com.domus.api.modules.igreja.DTO.RotulosDTO(
            u.igreja.ministerioNomeSingular, u.igreja.ministerioNomePlural, u.igreja.ministerioGenero,
            u.igreja.congregacaoNomeSingular, u.igreja.congregacaoNomePlural, u.igreja.congregacaoGenero,
            u.igreja.celulaNomeSingular, u.igreja.celulaNomePlural, u.igreja.celulaGenero))
    FROM Usuario u
    WHERE u.id = :id
    """)
    Optional<SessaoDTO> findSessaoById(@Param("id") UUID id);
```

Essa query usa o construtor de 9 argumentos de `SessaoDTO` (sem `capacidadesExtras`/
`precisaAceitarTermos`/`termosAceitosEm`) — **não existe hoje**. Adicionar em
`SessaoDTO.java` mais um construtor telescópico:

```java
    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId,
                      com.domus.api.modules.igreja.DTO.RotulosDTO rotulos) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                List.of(), false, null, rotulos);
    }
```

(Isso substitui o que era antes o construtor de 9 args puro — hoje esse caso não tinha
overload próprio, o `findSessaoById` original usava o de 9 args já existente. Conferir:
se o projeto já tiver esse overload de 9 args sem `rotulos`, ajustá-lo pra receber
`rotulos` como 10º parâmetro em vez de criar um novo.)

- [ ] **Step 3: Repassar `rotulos` em `AuthService.sessaoDe`**

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
                termoAceiteService.dataUltimoAceite(usuarioId),
                sessao.rotulos());
    }
```

- [ ] **Step 4: Rodar a suíte de auth pra garantir que nada quebrou**

Run: `mvn -q test -Dtest=AuthServiceTest`
Expected: PASS (nenhum teste novo necessário aqui — é passthrough de um campo já
coberto indiretamente pelos testes existentes de `sessaoDe`; se `AuthServiceTest` não
tiver teste de `sessaoDe`, este passo só confirma que a suíte não quebrou)

- [ ] **Step 5: Compilar o projeto inteiro (pega os outros call sites de `SessaoDTO`)**

Run: `mvn -q compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/auth/DTO/SessaoDTO.java \
        src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java \
        src/main/java/com/domus/api/modules/auth/AuthService.java
git commit -m "feat(auth): SessaoDTO carrega rotulos customizados da igreja"
```

---

## Task 5: Rodar a suíte inteira do backend antes de ir pro front

**Files:** nenhum (task de verificação).

- [ ] **Step 1: Suíte completa**

Run: `mvn -q test`
Expected: BUILD SUCCESS, todos os testes verdes (Docker precisa estar rodando —
Testcontainers sobe o Postgres da suíte).

- [ ] **Step 2: Subir o servidor de dev e conferir manualmente**

Run: `mvn -q spring-boot:run` (em background) e depois, com uma sessão autenticada
válida (cookie de um usuário de teste):

```bash
curl -s -b cookies.txt http://localhost:8080/auth/me | python3 -m json.tool
curl -s -b cookies.txt -X PUT http://localhost:8080/igrejas/minha/rotulos \
  -H "Content-Type: application/json" \
  -d '{"ministerio":{"singular":"Departamento","plural":"Departamentos","genero":"MASCULINO"},"congregacao":null,"celula":null}'
curl -s -b cookies.txt http://localhost:8080/auth/me | python3 -m json.tool
```

Expected: primeiro `me` traz `rotulos` com os 9 campos `null`; o `PUT` devolve o bloco
`ministerio` preenchido; o segundo `me` já reflete `ministerioSingular: "Departamento"`.
**Nunca imprima o `.env`/segredos** ao rodar isso — só o corpo JSON de resposta, que
não contém segredo nenhum.

- [ ] **Step 3: Parar o servidor de dev** (encerrar o processo em background)

Backend fechado aqui — avisar o autor e esperar ele confirmar antes de seguir pro front,
por segurança, embora esta task seja só verificação (sem commit).

---

## Task 6: Front — tipos, service, endpoint, hook `useRotulos` (sem UI ainda)

**Files:**
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/types/igreja/igreja.type.ts`
- Modify: `frontend/src/services/igreja/` (novo arquivo `rotulos.service.ts`)
- Modify: `frontend/src/types/auth.types.ts`
- Modify: `frontend/src/store/authStore.ts`
- Create: `frontend/src/lib/rotulos/concordancia.ts`
- Create: `frontend/src/lib/rotulos/useRotulos.ts`

**Interfaces:**
- Produces: tipo `Rotulos` (`{ ministerio, congregacao, celula }`, cada um
  `{ singular: string, plural: string, genero: 'MASCULINO' | 'FEMININO' }` — já
  resolvido, sem `null`), hook `useRotulos(): Rotulos`, `concordar(genero, forma): string`.
  Usado pelas Tasks 7 a 11.

- [ ] **Step 1: Endpoint novo**

Em `lib/endpoints.ts`, dentro do bloco `igreja` (perto de `MINHA: '/igrejas/minha'`):

```ts
    MINHA: '/igrejas/minha',
    ROTULOS: '/igrejas/minha/rotulos',
```

- [ ] **Step 2: Tipos**

Em `types/igreja/igreja.type.ts`, adicionar ao final:

```ts
export type Genero = 'MASCULINO' | 'FEMININO'

export interface RotulosCustomizados {
  ministerioSingular: string | null
  ministerioPlural: string | null
  ministerioGenero: Genero | null
  congregacaoSingular: string | null
  congregacaoPlural: string | null
  congregacaoGenero: Genero | null
  celulaSingular: string | null
  celulaPlural: string | null
  celulaGenero: Genero | null
}

export interface BlocoRotuloRequest {
  singular: string | null
  plural: string | null
  genero: Genero | null
}

export interface RotulosRequest {
  ministerio: BlocoRotuloRequest | null
  congregacao: BlocoRotuloRequest | null
  celula: BlocoRotuloRequest | null
}
```

E adicionar `rotulos: RotulosCustomizados` no `IgrejaDetalhe` existente (depois de
`diasRestantes: number | null`).

- [ ] **Step 3: Service**

Criar `services/igreja/rotulos.service.ts`:

```ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { RotulosCustomizados, RotulosRequest } from '@/types/igreja/igreja.type'

export const rotulosService = {
  atualizar: async (body: RotulosRequest): Promise<RotulosCustomizados> => {
    const { data } = await api.put(Endpoints.igreja.ROTULOS, body)
    return data
  },
}
```

- [ ] **Step 4: `Sessao` ganha `rotulos`**

Em `types/auth.types.ts`, adicionar em `Sessao` (depois de `termosAceitosEm`):

```ts
  /** Rótulos customizados pela igreja (Ministério/Congregação/Célula). Campo null = padrão. */
  rotulos: import('@/types/igreja/igreja.type').RotulosCustomizados | null;
```

- [ ] **Step 5: `authStore` carrega e permite atualizar `rotulos`**

Em `store/authStore.ts`: adicionar `rotulos: RotulosCustomizados | null` na interface
`AuthState` e em `estadoDeslogado` (`rotulos: null`); incluir `'rotulos'` no `Pick<>`
de `atualizarUsuarioLogado`:

```ts
  atualizarUsuarioLogado: (data: Partial<Pick<AuthState, 'nome' | 'role' | 'fotoId' | 'cargo' | 'igrejaSigla' | 'igrejaLogoId' | 'rotulos'>>) => void
```

Import de `RotulosCustomizados` de `@/types/igreja/igreja.type`.

- [ ] **Step 6: `concordancia.ts`**

```ts
import type { Genero } from '@/types/igreja/igreja.type'

/** Cresce sob demanda — só as formas realmente usadas em textos existentes. */
const FORMAS: Record<string, { MASCULINO: string; FEMININO: string }> = {
  novo: { MASCULINO: 'Novo', FEMININO: 'Nova' },
  nenhum: { MASCULINO: 'Nenhum', FEMININO: 'Nenhuma' },
  o: { MASCULINO: 'o', FEMININO: 'a' },
  um: { MASCULINO: 'um', FEMININO: 'uma' },
}

export function concordar(genero: Genero, forma: keyof typeof FORMAS): string {
  return FORMAS[forma][genero]
}
```

- [ ] **Step 7: `useRotulos.ts`**

```ts
import { useAuthStore } from '@/store/authStore'
import { concordar } from './concordancia'
import type { Genero } from '@/types/igreja/igreja.type'

interface RotuloModulo {
  singular: string
  plural: string
  genero: Genero
}

interface Rotulos {
  ministerio: RotuloModulo
  congregacao: RotuloModulo
  celula: RotuloModulo
}

const PADRAO: Rotulos = {
  ministerio: { singular: 'Ministério', plural: 'Ministérios', genero: 'MASCULINO' },
  congregacao: { singular: 'Unidade', plural: 'Unidades', genero: 'FEMININO' },
  celula: { singular: 'Célula', plural: 'Células', genero: 'FEMININO' },
}

export function useRotulos() {
  const custom = useAuthStore((s) => s.rotulos)

  const resolver = (
    singular: string | null | undefined, plural: string | null | undefined,
    genero: Genero | null | undefined, padrao: RotuloModulo,
  ): RotuloModulo =>
    singular && plural && genero ? { singular, plural, genero } : padrao

  const rotulos: Rotulos = {
    ministerio: resolver(custom?.ministerioSingular, custom?.ministerioPlural, custom?.ministerioGenero, PADRAO.ministerio),
    congregacao: resolver(custom?.congregacaoSingular, custom?.congregacaoPlural, custom?.congregacaoGenero, PADRAO.congregacao),
    celula: resolver(custom?.celulaSingular, custom?.celulaPlural, custom?.celulaGenero, PADRAO.celula),
  }

  return { ...rotulos, concordar }
}
```

`useRotulos()` devolve `{ ministerio, congregacao, celula, concordar }` — cada módulo
já resolvido (nunca `null`), pronto pra usar direto em JSX; `concordar` é reexportado
pra quem precisar de "Novo X"/"Nova X" sem importar de dois lugares.

- [ ] **Step 8: `tsc` e `eslint`**

Run: `npm run typecheck` (ou `npx tsc --noEmit`) e `npm run lint`
Expected: sem erros novos (o hook ainda não é usado em lugar nenhum — só precisa compilar).

- [ ] **Step 9: Commit**

```bash
git add frontend/src/lib/endpoints.ts frontend/src/types/igreja/igreja.type.ts \
        frontend/src/services/igreja/rotulos.service.ts frontend/src/types/auth.types.ts \
        frontend/src/store/authStore.ts frontend/src/lib/rotulos/
git commit -m "feat(front): mecanismo de rotulos customizados (tipos, service, useRotulos)"
```

---

## Task 7: Front — seção "Nomenclatura" em `/configuracoes/igreja` **[testável no navegador]**

**Files:**
- Create: `frontend/src/app/(app)/configuracoes/igreja/SecaoNomenclatura.tsx`
- Create: `frontend/src/app/(app)/configuracoes/igreja/SecaoNomenclatura.module.css`
- Modify: `frontend/src/app/(app)/configuracoes/igreja/page.tsx`

**Interfaces:**
- Consumes: `useRotulos()` (Task 6), `rotulosService.atualizar` (Task 6),
  `igreja.rotulos: RotulosCustomizados` (do `useMinhaIgreja()` já existente, GET
  `/igrejas/minha` já estendido na Task 3).

Esta é a primeira entrega testável no navegador: dá pra abrir
`/configuracoes/igreja`, digitar um rótulo novo, ver o preview mudar em tempo real,
salvar, recarregar a página e ver que persistiu — mesmo que nenhuma outra tela do
sistema ainda use o rótulo customizado (isso vem nas próximas tasks).

- [ ] **Step 1: Componente da seção**

```tsx
'use client'

import { useEffect, useState } from 'react'
import { Type, Save, RotateCcw } from 'lucide-react'
import { rotulosService } from '@/services/igreja/rotulos.service'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import type { RotulosCustomizados, Genero, RotulosRequest, BlocoRotuloRequest } from '@/types/igreja/igreja.type'
import baseStyles from '../configuracoes.module.css'
import styles from './SecaoNomenclatura.module.css'

interface BlocoEstado { singular: string; plural: string; genero: Genero }

const PADRAO: Record<'ministerio' | 'congregacao' | 'celula', BlocoEstado> = {
  ministerio: { singular: 'Ministério', plural: 'Ministérios', genero: 'MASCULINO' },
  congregacao: { singular: 'Unidade', plural: 'Unidades', genero: 'FEMININO' },
  celula: { singular: 'Célula', plural: 'Células', genero: 'FEMININO' },
}

function estadoInicial(dados: RotulosCustomizados): Record<'ministerio' | 'congregacao' | 'celula', BlocoEstado> {
  return {
    ministerio: dados.ministerioSingular && dados.ministerioPlural && dados.ministerioGenero
      ? { singular: dados.ministerioSingular, plural: dados.ministerioPlural, genero: dados.ministerioGenero }
      : PADRAO.ministerio,
    congregacao: dados.congregacaoSingular && dados.congregacaoPlural && dados.congregacaoGenero
      ? { singular: dados.congregacaoSingular, plural: dados.congregacaoPlural, genero: dados.congregacaoGenero }
      : PADRAO.congregacao,
    celula: dados.celulaSingular && dados.celulaPlural && dados.celulaGenero
      ? { singular: dados.celulaSingular, plural: dados.celulaPlural, genero: dados.celulaGenero }
      : PADRAO.celula,
  }
}

interface Props { rotulosAtuais: RotulosCustomizados }

export function SecaoNomenclatura({ rotulosAtuais }: Props) {
  const [blocos, setBlocos] = useState(() => estadoInicial(rotulosAtuais))
  const [salvando, setSalvando] = useState(false)
  const atualizarUsuarioLogado = useAuthStore((s) => s.atualizarUsuarioLogado)

  // Segue o mesmo padrão do form grande da página: espelha a igreja quando os dados chegam/mudam.
  useEffect(() => { setBlocos(estadoInicial(rotulosAtuais)) }, [rotulosAtuais])

  function atualizarCampo(modulo: keyof typeof blocos, campo: keyof BlocoEstado, valor: string) {
    setBlocos((atual) => ({ ...atual, [modulo]: { ...atual[modulo], [campo]: valor } }))
  }

  function restaurarPadrao(modulo: keyof typeof blocos) {
    setBlocos((atual) => ({ ...atual, [modulo]: PADRAO[modulo] }))
  }

  async function salvar() {
    setSalvando(true)
    try {
      const paraRequest = (modulo: keyof typeof blocos): BlocoRotuloRequest | null => {
        const b = blocos[modulo]
        const p = PADRAO[modulo]
        const ehPadrao = b.singular === p.singular && b.plural === p.plural && b.genero === p.genero
        return ehPadrao ? { singular: null, plural: null, genero: null } : b
      }

      const request: RotulosRequest = {
        ministerio: paraRequest('ministerio'),
        congregacao: paraRequest('congregacao'),
        celula: paraRequest('celula'),
      }

      const resultado = await rotulosService.atualizar(request)
      atualizarUsuarioLogado({ rotulos: resultado })
      notificar.sucesso('Nomenclatura salva', 'Os novos rótulos já valem em todo o sistema.')
    } catch (erro: unknown) {
      const mensagem =
        (erro as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Tente novamente em alguns instantes.'
      notificar.erro('Não foi possível salvar', mensagem)
    } finally {
      setSalvando(false)
    }
  }

  const modulos: { chave: keyof typeof blocos; titulo: string; ajuda: string }[] = [
    { chave: 'ministerio', titulo: 'Ministério', ajuda: 'Sugestões: Departamento, Rede, Equipe.' },
    { chave: 'congregacao', titulo: 'Congregação', ajuda: 'Sugestões: Unidade, Filial, Polo.' },
    { chave: 'celula', titulo: 'Célula', ajuda: 'Sugestões: Pequeno Grupo, GC, Grupo de Multiplicação.' },
  ]

  return (
    <section className={baseStyles.cartao}>
      <div className={styles.cabecalho}>
        <Type size={20} aria-hidden="true" />
        <div>
          <h2 className={styles.titulo}>Nomenclatura</h2>
          <p className={styles.subtitulo}>
            Adapte os termos do sistema à linguagem da sua igreja. Vale pra menus,
            botões e listas em toda a aplicação.
          </p>
        </div>
      </div>

      <div className={styles.blocos}>
        {modulos.map(({ chave, titulo, ajuda }) => (
          <div key={chave} className={styles.bloco}>
            <div className={styles.blocoCabecalho}>
              <h3>{titulo}</h3>
              <p>{ajuda}</p>
            </div>
            <div className={styles.blocoGrade}>
              <div className={styles.campo}>
                <label className={styles.rotulo} htmlFor={`${chave}-singular`}>Singular</label>
                <input
                  id={`${chave}-singular`}
                  className={styles.input}
                  placeholder={`Ex.: ${PADRAO[chave].singular}`}
                  value={blocos[chave].singular}
                  onChange={(e) => atualizarCampo(chave, 'singular', e.target.value)}
                />
              </div>
              <div className={styles.campo}>
                <label className={styles.rotulo} htmlFor={`${chave}-plural`}>Plural</label>
                <input
                  id={`${chave}-plural`}
                  className={styles.input}
                  placeholder={`Ex.: ${PADRAO[chave].plural}`}
                  value={blocos[chave].plural}
                  onChange={(e) => atualizarCampo(chave, 'plural', e.target.value)}
                />
              </div>
              <div className={styles.campo}>
                <label className={styles.rotulo} htmlFor={`${chave}-genero`}>Concordância</label>
                <select
                  id={`${chave}-genero`}
                  className={styles.input}
                  value={blocos[chave].genero}
                  onChange={(e) => atualizarCampo(chave, 'genero', e.target.value as Genero)}
                >
                  <option value="MASCULINO">Masculino (ex.: &quot;Novo {blocos[chave].singular || '...'}&quot;)</option>
                  <option value="FEMININO">Feminino (ex.: &quot;Nova {blocos[chave].singular || '...'}&quot;)</option>
                </select>
              </div>
            </div>
            <div className={styles.preview}>
              <span className={styles.previewRotulo}>Como vai aparecer no menu:</span>
              <span className={styles.previewValor}>{blocos[chave].plural || PADRAO[chave].plural}</span>
              <button type="button" className={styles.botaoRestaurar} onClick={() => restaurarPadrao(chave)}>
                <RotateCcw size={14} aria-hidden="true" />
                Restaurar padrão
              </button>
            </div>
          </div>
        ))}
      </div>

      <div className={styles.acoes}>
        <button type="button" className={baseStyles.botaoPrimario} onClick={salvar} disabled={salvando}>
          <Save size={16} aria-hidden="true" />
          {salvando ? 'Salvando...' : 'Salvar nomenclatura'}
        </button>
      </div>
    </section>
  )
}
```

(Preview é o próprio `<span>{blocos[chave].plural}</span>` lendo direto do estado
controlado — reativo de verdade a cada tecla, sem `disabled`, como a regra de UX do
projeto exige.)

- [ ] **Step 2: CSS da seção**

```css
.cabecalho { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 20px; }
.titulo { font-size: var(--font-size-lg); font-weight: var(--font-weight-semibold); margin: 0 0 4px; }
.subtitulo { font-size: var(--font-size-sm); color: var(--color-text-secondary); margin: 0; }

.blocos { display: flex; flex-direction: column; gap: 20px; }
.bloco { border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 16px; }
.blocoCabecalho h3 { margin: 0 0 2px; font-size: var(--font-size-md); }
.blocoCabecalho p { margin: 0 0 12px; font-size: var(--font-size-xs); color: var(--color-text-secondary); }

.blocoGrade { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
@media (max-width: 640px) { .blocoGrade { grid-template-columns: 1fr; } }

.campo { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.rotulo { font-size: var(--font-size-xs); font-weight: var(--font-weight-medium); }
.input {
  padding: 8px 10px; border-radius: var(--radius-sm); border: 1px solid var(--color-border-input);
  font-size: var(--font-size-sm); width: 100%;
}

.preview {
  display: flex; align-items: center; gap: 8px; margin-top: 12px; padding-top: 12px;
  border-top: 1px dashed var(--color-border); flex-wrap: wrap;
}
.previewRotulo { font-size: var(--font-size-xs); color: var(--color-text-secondary); }
.previewValor { font-size: var(--font-size-sm); font-weight: var(--font-weight-semibold); color: var(--color-primary); }
.botaoRestaurar {
  margin-left: auto; display: flex; align-items: center; gap: 4px; background: none; border: none;
  color: var(--color-text-secondary); font-size: var(--font-size-xs); cursor: pointer;
}

.acoes { margin-top: 20px; display: flex; justify-content: flex-end; }
```

- [ ] **Step 3: Encaixar na página**

Em `configuracoes/igreja/page.tsx`, importar `SecaoNomenclatura` e renderizar logo
depois do `</div>` que fecha `styles.colunas` (linha 300), antes de `styles.cardsRodape`:

```tsx
      </div>

      <SecaoNomenclatura rotulosAtuais={igreja.rotulos} />

      <div className={styles.cardsRodape}>
```

- [ ] **Step 4: `tsc`/`eslint`**

Run: `npm run typecheck && npm run lint`
Expected: sem erros.

- [ ] **Step 5: Parar aqui e testar no navegador**

Subir back (`mvn -q spring-boot:run`) e front (`npm run dev`), abrir
`/configuracoes/igreja` logado como `ADMIN_IGREJA`, confirmar: os 3 blocos aparecem
com o padrão preenchido; digitar um novo singular/plural atualiza o preview
imediatamente; trocar o seletor de gênero atualiza o texto de exemplo
("Novo X"/"Nova X"); salvar mostra toast de sucesso; recarregar a página mantém o
valor salvo; "Restaurar padrão" limpa o bloco de volta pro texto padrão e salvar de
novo reseta no banco (conferível reabrindo a página).

**Aguardar confirmação do autor antes de commitar e seguir pra Task 8.**

- [ ] **Step 6: Commit** (só depois do teste manual confirmado)

```bash
git add frontend/src/app/\(app\)/configuracoes/igreja/SecaoNomenclatura.tsx \
        frontend/src/app/\(app\)/configuracoes/igreja/SecaoNomenclatura.module.css \
        frontend/src/app/\(app\)/configuracoes/igreja/page.tsx
git commit -m "feat(front): secao Nomenclatura em configuracoes/igreja"
```

---

## Task 8: Swap de Ministério — deletar `rotulosMinisterio.ts`, usar `useRotulos()` **[testável]**

**Files:**
- Delete: `frontend/src/lib/rotulosMinisterio.ts`
- Modify (trocar `ROTULO_MINISTERIO`/`ROTULO_MINISTERIO_PLURAL` por `useRotulos().ministerio.singular`/`.plural`):
  - `frontend/src/app/(app)/ministerios/(lista)/page.tsx`
  - `frontend/src/app/(app)/ministerios/(lista)/arquivados/page.tsx`
  - `frontend/src/app/(app)/ministerios/(lista)/ModalMinisterioForm.tsx`
  - `frontend/src/app/(app)/ministerios/(lista)/layout.tsx`
  - `frontend/src/app/(app)/ministerios/[id]/page.tsx`
  - `frontend/src/app/(app)/pessoas/(lista)/(detalhe)/DrawerDetalhePessoa.tsx`
  - `frontend/src/hooks/ministerio/useMinisterioForm.ts`
  - `frontend/src/hooks/ministerio/useMembroMinisterio.ts`
  - `frontend/src/components/layout/Sidebar.tsx`
  - `frontend/src/components/module/pessoas/SeletorRedes.tsx`
  - `frontend/src/components/layout/busca/BuscaGlobal.tsx` (rótulo `"Redes"` hardcoded
    do tipo `MINISTERIO`, achado à parte do `rotulosMinisterio.ts` — não estava na
    listagem original, mas é a mesma categoria de string solta)

**Interfaces:**
- Consumes: `useRotulos()` (Task 6).

- [ ] **Step 1: Trocar em cada arquivo de componente/página**

Padrão de troca (repetir por arquivo): remover o `import { ROTULO_MINISTERIO... } from
'@/lib/rotulosMinisterio'`, adicionar `import { useRotulos } from '@/lib/rotulos/useRotulos'`,
chamar `const { ministerio } = useRotulos()` dentro do componente/hook, e trocar cada
uso de `ROTULO_MINISTERIO` por `ministerio.singular` e `ROTULO_MINISTERIO_PLURAL` por
`ministerio.plural`.

Em `hooks/ministerio/useMinisterioForm.ts` e `useMembroMinisterio.ts`: como já são
hooks, `useRotulos()` entra direto no corpo da função, sem mudança estrutural.

- [ ] **Step 2: `Sidebar.tsx` — mover `navItems` pra dentro do componente**

O array hoje é `const navItems` no escopo do módulo (linha 22), o que impede chamar
um hook pra resolver o label. Mover a definição do array pra dentro da função
`Sidebar()`, computada a cada render (array pequeno, sem necessidade de memoização):

```tsx
export function Sidebar() {
  const { ministerio, celula } = useRotulos()
  // ... (demais hooks existentes: pathname, role, etc.)

  const navItems: NavItem[] = [
    { href: '/inicio',     label: 'Início',    icon: Home,            roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/dashboard',  label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN_IGREJA'] },
    { href: '/eventos',    label: 'Eventos',   icon: Calendar,        roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/ministerios', label: ministerio.plural, icon: UsersRound, roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/celulas',    label: celula.plural,  icon: Grid3x3,          roles: ['ADMIN_IGREJA', 'LIDER', 'ACESSO_COMUM'] },
    { href: '/financeiro/movimentacoes', label: 'Financeiro',  icon: Wallet,          roles: ['ADMIN_IGREJA'], visivel: (r, c) => podeVerFinanceiro(r, c) },
    { href: '/usuarios',   label: 'Usuários',  icon: UserCog,         roles: ['ADMIN_IGREJA'] },
  ]
  // ... (resto do corpo do componente sem mudança)
```

(A constante `type NavItem` continua no escopo do módulo — só o array de dados desce
pro componente. `celula.plural` já vem preparado aqui mesmo, adiantando a Task 10.)
Remover o import de `ROTULO_MINISTERIO_PLURAL` e adicionar
`import { useRotulos } from '@/lib/rotulos/useRotulos'`.

- [ ] **Step 3: `BuscaGlobal.tsx` — mover `TIPO_CONFIG` pra dentro do componente**

Mesma situação: `TIPO_CONFIG` é módulo-level. Mover pra dentro de `BuscaGlobal()`,
trocando `MINISTERIO: { label: 'Redes', ... }` por `MINISTERIO: { label: ministerio.plural, ... }`
e `CELULA: { label: 'Células', ... }` por `CELULA: { label: celula.plural, ... }`
(adiantando também a Task 10 aqui, já que o arquivo precisa ser tocado de qualquer jeito).

- [ ] **Step 4: Deletar o arquivo antigo**

```bash
rm frontend/src/lib/rotulosMinisterio.ts
```

- [ ] **Step 5: `tsc`/`eslint` — confirma que nenhum import quebrado sobrou**

Run: `npm run typecheck && npm run lint`
Expected: sem erros (nenhuma referência a `rotulosMinisterio` deve sobrar — se sobrar,
o `tsc` aponta o arquivo).

- [ ] **Step 6: Testar no navegador**

Com o rótulo de Ministério ainda no padrão ("Ministérios"): confirmar que a Sidebar,
a busca global e as telas de `/ministerios` continuam mostrando "Ministério(s)"
normalmente (nada mudou visualmente ainda). Depois, ir em `/configuracoes/igreja` e
trocar o rótulo de Ministério pra algo como "Departamento"/"Departamentos", salvar, e
conferir que a Sidebar, a busca global e as telas de `/ministerios` **agora mostram
"Departamento(s)"** sem precisar de reload manual (o `atualizarUsuarioLogado` da
Task 7 já atualiza o `authStore` na hora).

**Aguardar confirmação do autor antes de commitar.**

- [ ] **Step 7: Commit**

```bash
git add -A frontend/src/lib/rotulosMinisterio.ts frontend/src/app/\(app\)/ministerios \
        frontend/src/app/\(app\)/pessoas/\(lista\)/\(detalhe\)/DrawerDetalhePessoa.tsx \
        frontend/src/hooks/ministerio frontend/src/components/layout/Sidebar.tsx \
        frontend/src/components/module/pessoas/SeletorRedes.tsx \
        frontend/src/components/layout/busca/BuscaGlobal.tsx
git commit -m "feat(front): rotulo de Ministerio customizavel via useRotulos"
```

---

## Task 9: Swap de Congregação **[testável]**

**Files:**
- `frontend/src/app/(app)/configuracoes/igrejas-vinculadas/page.tsx` (linhas 86, 167, 258)
- `frontend/src/app/(app)/financeiro/relatorios/page.tsx` (linha 53)
- `frontend/src/app/(app)/financeiro/relatorios/balancete/page.tsx` (linha 54)
- `frontend/src/app/(app)/financeiro/relatorios/VisaoGeralCongregacoes.tsx`

**Interfaces:**
- Consumes: `useRotulos()` (Task 6).

- [ ] **Step 1: Trocar as strings soltas**

Em cada arquivo, importar `useRotulos`, chamar `const { congregacao } = useRotulos()`
no corpo do componente, e trocar cada string literal ("Unidade", "Unidades",
"Congregação", `data-rotulo="Unidade"`, `{ valor: 'CONGREGACOES', rotulo: 'Unidades' }`,
"Por Congregação") pelo `congregacao.singular`/`congregacao.plural` correspondente.
Nomes internos (`useDesvincularCongregacao`, `congregacaoParaRemover`,
`VisaoGeralCongregacoes`, a rota `/configuracoes/igrejas-vinculadas`) **não mudam** —
só o texto visível.

- [ ] **Step 2: `tsc`/`eslint`**

Run: `npm run typecheck && npm run lint`
Expected: sem erros.

- [ ] **Step 3: Testar no navegador**

Com o rótulo de Congregação no padrão: conferir `/configuracoes/igrejas-vinculadas` e
os relatórios financeiros por congregação mostrando "Unidade(s)" normalmente. Trocar
pra um rótulo customizado em `/configuracoes/igreja` (ex.: "Filial"/"Filiais") e
conferir as mesmas telas refletindo sem reload.

**Aguardar confirmação do autor antes de commitar.**

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(app\)/configuracoes/igrejas-vinculadas/page.tsx \
        frontend/src/app/\(app\)/financeiro/relatorios/page.tsx \
        frontend/src/app/\(app\)/financeiro/relatorios/balancete/page.tsx \
        frontend/src/app/\(app\)/financeiro/relatorios/VisaoGeralCongregacoes.tsx
git commit -m "feat(front): rotulo de Congregacao customizavel via useRotulos"
```

---

## Task 10: Swap de Célula **[testável]**

**Files:**
- `frontend/src/hooks/celula/useCelulaForm.ts` (toasts "Célula criada"/"Célula atualizada")
- Demais telas de `/celulas/**` que exibem "Célula(s)" como texto (títulos de página,
  `layout.tsx`, `ModalAdicionarMembro.tsx`, `ModalConverterVisitante.tsx`) — Sidebar e
  BuscaGlobal **já foram feitos na Task 8** (o array/objeto já foi movido pra dentro
  do componente e já usa `celula.plural`, adiantado por já estar tocando o arquivo).

**Interfaces:**
- Consumes: `useRotulos()` (Task 6).

- [ ] **Step 1: `useCelulaForm.ts`**

```ts
import { useRotulos } from '@/lib/rotulos/useRotulos'
// ...
export function useCelulaForm({ celulaId, celulaInicial }: UseCelulaFormParams = {}) {
  const { celula } = useRotulos()
  // ...
      if (ehEdicao) {
        await celulaService.atualizar(celulaId!, payload)
        invalidarCache(queryClient, 'celula')
        notificar.sucesso(`${celula.singular} atualizada com sucesso!`)
      } else {
        await celulaService.criar(payload)
        invalidarCache(queryClient, 'celula')
        notificar.sucesso(`${celula.singular} criada com sucesso!`)
      }
```

(Concordância do particípio "atualizada"/"criada" fica fixa em feminino aqui de
propósito — regra de concordância completa do particípio pro `singular` custom não
está no escopo desta entrega; se a igreja customizar Célula pra um termo masculino, o
texto do toast fica gramaticalmente estranho, é uma limitação conhecida e aceitável
pro v1, YAGNI.)

- [ ] **Step 2: Demais telas de `/celulas`**

Repetir o padrão: importar `useRotulos`, pegar `celula`, trocar toda ocorrência
literal de "Célula"/"Células" visível ao usuário (títulos `<h1>`, `<title>` de
`layout.tsx`, textos de modal) por `celula.singular`/`celula.plural`. **Não tocar**
em nomes de rota, tipos (`CelulaResponse`), nomes de hook (`useCelulas`), nem em
`podeGerenciarCelulas`.

- [ ] **Step 3: `tsc`/`eslint`**

Run: `npm run typecheck && npm run lint`
Expected: sem erros.

- [ ] **Step 4: Testar no navegador**

Com Célula no padrão: `/celulas`, criar/editar uma célula (toast "Célula criada com
sucesso!"), Sidebar e busca global mostrando "Células" — todos já confirmados
funcionando desde a Task 8. Trocar o rótulo em `/configuracoes/igreja` pra algo como
"Pequeno Grupo"/"Pequenos Grupos", confirmar que a lista, os modais, os toasts e a
Sidebar passam a usar o termo novo.

**Aguardar confirmação do autor antes de commitar.**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/celula/useCelulaForm.ts frontend/src/app/\(app\)/celulas
git commit -m "feat(front): rotulo de Celula customizavel via useRotulos"
```

---

## Task 11: Fechar o backlog

**Files:**
- Modify: `docs/BACKLOG-PRE-VENDA.md`

- [ ] **Step 1: Marcar o item 8 como resolvido**, seguindo o mesmo padrão de fechamento
usado pros itens 4, 6 e 7 (`~~texto original~~ RESOLVIDO (data)` + parágrafo resumindo
o que foi entregue).

- [ ] **Step 2: Commit**

```bash
git add docs/BACKLOG-PRE-VENDA.md
git commit -m "docs: fecha item 8 do backlog (rotulo self-service por igreja)"
```
