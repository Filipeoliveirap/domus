# Cadastro de evento enriquecido + elegibilidade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enriquecer o cadastro de evento (local como entidade, tipo, responsável, banner,
auditoria, layout de duas colunas) e adicionar elegibilidade por perfil (faixa etária, vínculo,
estado civil, sexo) avaliada por regras independentes no backend.

**Architecture:** Migration V3 acrescenta `local_evento`, colunas em `evento` e `pessoa.sexo`.
A elegibilidade vira um conjunto de implementações de `RegraElegibilidade` que o Spring injeta
como lista — adicionar uma restrição cria um arquivo e não edita nenhum. O `POST /inscricoes`
é a validação real; `GET /elegibilidade` só pinta tela.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL, JUnit 5;
Next.js, TypeScript, CSS Modules, TanStack Query, React Hook Form + Zod.

**Spec:** `docs/superpowers/specs/2026-07-22-evento-cadastro-enriquecido-design.md`

## Global Constraints

- `igreja_id` **sempre do JWT, nunca do corpo da requisição**. Vale para `local_evento` também.
- Services retornam **DTOs**, nunca entidades. Camadas `controller → service → repository`.
- **Soft delete** (`deleted_at` + `@SQLDelete`/`@SQLRestriction`) em `local_evento`.
- Autorização **por capacidade**, via `Permissoes.*`. Nenhuma comparação `role == "..."` nova,
  em nenhum dos dois lados.
- **Nunca chamar o tipo de evento de "categoria"** — o nome já é de `categoria_financeira`.
- **Nada escondido só no front:** todo dado restrito sai reduzido do backend.
- **Mobile é parte da entrega**, não etapa separada: colunas colapsam para 1, sem overflow
  horizontal.
- Migration nova é **V3** (a última é V2). `ddl-auto=validate` — entidade e schema têm que bater.
- **A contagem de vagas com lock pessimista da Spec A continua sendo a única autoridade sobre
  vaga.** `RegraVagas` só serve ao `GET /elegibilidade`.
- Normalização de texto **reusa `TextoUtil`** (`capitalizar`, `normalizarParaComparacao`). Não
  escrever normalização nova.
- Commits **sem** `Co-Authored-By`.

## Estrutura de arquivos

**Backend — criar**

| Arquivo | Responsabilidade |
|---|---|
| `db/migration/V3__evento_enriquecido.sql` | schema |
| `modules/pessoa/Sexo.java` | enum `HOMEM, MULHER` |
| `modules/evento/local/LocalEvento.java` | entidade |
| `modules/evento/local/LocalEventoRepository.java` | consultas + isolamento |
| `modules/evento/local/LocalEventoService.java` | CRUD |
| `modules/evento/local/LocalEventoController.java` | `/locais-evento` |
| `modules/evento/local/DTOs/LocalEventoRequest.java` · `LocalEventoResponse.java` | contrato |
| `modules/evento/elegibilidade/RegraElegibilidade.java` | interface |
| `modules/evento/elegibilidade/Impedimento.java` · `Elegibilidade.java` | records |
| `modules/evento/elegibilidade/regras/RegraFaixaEtaria.java` | idade |
| `modules/evento/elegibilidade/regras/RegraVinculo.java` | absorve `exclusivoMembros` |
| `modules/evento/elegibilidade/regras/RegraEstadoCivil.java` | estado civil |
| `modules/evento/elegibilidade/regras/RegraSexo.java` | sexo |
| `modules/evento/elegibilidade/regras/RegraVagas.java` | **só leitura**, para a tela |
| `modules/evento/elegibilidade/ElegibilidadeService.java` | roda todas, acumula |

**Backend — modificar:** `Evento`, `EventoRequest`, `EventoResponse`, `EventoService`,
`EventoController`, `EventoRepository`, `Pessoa`, `PessoaRequestDTO`, `PessoaResponse`,
`PessoaService`, `InscricaoService`, `InscricaoController`, `SecurityConfig`.

**Frontend — criar:** `components/common/InputComSugestoes/`,
`components/module/eventos/SeletorLocal.tsx`, `components/module/eventos/BlocoParaQuemE.tsx`,
`components/module/eventos/ModalImpactoRestricao.tsx`, `app/(app)/eventos/locais/page.tsx`,
`hooks/evento/useLocaisEvento.ts`, `hooks/evento/useTiposEvento.ts`,
`hooks/inscricao/useElegibilidade.ts`, `types/localEvento.types.ts`.

**Frontend — modificar:** `EventoForm.tsx` (+CSS), `useEventoForm.ts`, `validators.ts`,
`EventoCard.tsx`, `DrawerDetalheEvento.tsx`, `ModalEventoResumo.tsx`,
`ModalInscreverPessoas.tsx`, `BotaoConfirmarPresenca.tsx`, `PessoaForm.tsx`,
`app/(app)/eventos/page.tsx`, `lib/endpoints.ts`, `lib/cacheInvalidacao.ts`.

---

## Task 1: Migration V3 e entidades

**Files:**
- Create: `src/main/resources/db/migration/V3__evento_enriquecido.sql`
- Create: `src/main/java/com/domus/api/modules/pessoa/Sexo.java`
- Create: `src/main/java/com/domus/api/modules/evento/local/LocalEvento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/pessoa/Pessoa.java`
- Test: `src/test/java/com/domus/api/modules/evento/MigracaoV3Test.java`

**Interfaces:**
- Produces: `Sexo.HOMEM|MULHER`; `LocalEvento` com `getNome()`, `getCapacidade()`,
  `getCepLogradouroNumero()`, `getComplementoBairroCidadeUf()`; `Evento.getLocalTexto()`,
  `getLocal()` (agora `LocalEvento`), `getTipo()`, `getResponsavel()`, `getRecorteEtario()`,
  `getIdadeMin()`, `getIdadeMax()`, `getRestricaoEstadoCivil()`, `getRestricaoSexo()`.

⚠️ **`evento.local` (VARCHAR) é RENOMEADO para `local_texto`.** Não criar coluna nova e deixar a
velha: os 24 eventos da demo têm dados ali e precisam continuar íntegros.

- [ ] **Step 1: Escrever a migration**

```sql
-- V3__evento_enriquecido.sql

CREATE TABLE local_evento (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id  UUID NOT NULL REFERENCES igreja(id),
    nome       VARCHAR(150) NOT NULL,
    capacidade INTEGER,
    cep_logradouro_numero        VARCHAR(255),
    complemento_bairro_cidade_uf VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    CONSTRAINT chk_local_capacidade CHECK (capacidade IS NULL OR capacidade > 0)
);

-- Nome único por igreja, ignorando acento/caixa e considerando só os não arquivados.
CREATE UNIQUE INDEX ux_local_evento_igreja_nome
    ON local_evento (igreja_id, LOWER(UNACCENT(nome)))
    WHERE deleted_at IS NULL;

CREATE INDEX ix_local_evento_igreja ON local_evento (igreja_id) WHERE deleted_at IS NULL;

-- O texto livre que já existia vira local_texto. RENAME preserva os dados.
ALTER TABLE evento RENAME COLUMN local TO local_texto;

ALTER TABLE evento
    ADD COLUMN local_id                  UUID REFERENCES local_evento(id) ON DELETE SET NULL,
    ADD COLUMN tipo                      VARCHAR(80),
    ADD COLUMN responsavel_pessoa_id     UUID REFERENCES pessoa(id) ON DELETE SET NULL,
    ADD COLUMN criado_por_usuario_id     UUID REFERENCES usuario(id),
    ADD COLUMN atualizado_por_usuario_id UUID REFERENCES usuario(id),
    ADD COLUMN recorte_etario            VARCHAR(40),
    ADD COLUMN idade_min                 INTEGER,
    ADD COLUMN idade_max                 INTEGER,
    ADD COLUMN restricao_estado_civil    VARCHAR(20),
    ADD COLUMN restricao_sexo            VARCHAR(10);

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_local_exclusivo
        CHECK (local_id IS NULL OR local_texto IS NULL),
    ADD CONSTRAINT chk_evento_idades
        CHECK (idade_min IS NULL OR idade_max IS NULL OR idade_min <= idade_max),
    ADD CONSTRAINT chk_evento_idade_min CHECK (idade_min IS NULL OR idade_min >= 0),
    ADD CONSTRAINT chk_evento_idade_max CHECK (idade_max IS NULL OR idade_max >= 0),
    ADD CONSTRAINT chk_evento_estado_civil
        CHECK (restricao_estado_civil IS NULL
               OR restricao_estado_civil IN ('SOLTEIRO','CASADO','DIVORCIADO','VIUVO')),
    ADD CONSTRAINT chk_evento_restricao_sexo
        CHECK (restricao_sexo IS NULL OR restricao_sexo IN ('HOMEM','MULHER'));

CREATE INDEX ix_evento_local ON evento (local_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_evento_tipo  ON evento (igreja_id, tipo) WHERE deleted_at IS NULL;

ALTER TABLE pessoa
    ADD COLUMN sexo VARCHAR(10),
    ADD CONSTRAINT chk_pessoa_sexo CHECK (sexo IS NULL OR sexo IN ('HOMEM','MULHER'));
```

⚠️ `UNACCENT` exige a extensão. Antes de rodar, confira se ela já existe:
`SELECT extname FROM pg_extension WHERE extname='unaccent';`
Se não existir, acrescente `CREATE EXTENSION IF NOT EXISTS unaccent;` como **primeira linha** da
migration. Se o usuário do banco não tiver permissão para criar extensão, troque o índice por
`ON local_evento (igreja_id, LOWER(nome)) WHERE deleted_at IS NULL` e resolva o acento no
service via `TextoUtil.normalizarParaComparacao` — **não deixe o índice de fora**, ele é o que
impede dois "Salão Social" na mesma igreja.

- [ ] **Step 2: Criar o enum `Sexo`**

```java
package com.domus.api.modules.pessoa;

/**
 * Só dois valores, por decisão do autor: o uso é restringir inscrição em evento
 * ("encontro de mulheres", "café dos homens"), não descrever identidade.
 */
public enum Sexo { HOMEM, MULHER }
```

- [ ] **Step 3: Criar a entidade `LocalEvento`**

Espelhe o estilo de `Evento.java` (Lombok `@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@Builder`, `@SQLDelete`/`@SQLRestriction`, `@CreationTimestamp`/`@UpdateTimestamp`).

```java
package com.domus.api.modules.evento.local;

import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "local_evento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE local_evento SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class LocalEvento {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(nullable = false, length = 150)
    private String nome;

    /** NULL = não declarada. SUGERE as vagas do evento; nunca as impõe. */
    private Integer capacidade;

    /**
     * NULL = herda o endereço da igreja. O "Santuário Principal" não tem endereço próprio —
     * ele É o endereço da igreja, e duplicá-lo criaria duas fontes que divergem na mudança.
     */
    @Column(name = "cep_logradouro_numero")
    private String cepLogradouroNumero;

    @Column(name = "complemento_bairro_cidade_uf")
    private String complementoBairroCidadeUf;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Endereço próprio? Se não, quem exibe deve cair no da igreja. */
    public boolean temEnderecoProprio() {
        return cepLogradouroNumero != null && !cepLogradouroNumero.isBlank();
    }
}
```

- [ ] **Step 4: Ajustar `Evento`**

Trocar `private String local;` por:

```java
    /** Local cadastrado. Mutuamente exclusivo com {@link #localTexto} (CHECK no banco). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_id")
    private LocalEvento local;

    /** Local ad-hoc ("chácara do João"). Era a coluna `local` até a V3. */
    @Column(name = "local_texto")
    private String localTexto;

    /** Texto normalizado por TextoUtil.capitalizar. NULL = sem tipo. */
    @Column(name = "tipo", length = 80)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_pessoa_id")
    private Pessoa responsavel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    /** Nome do recorte (Kids, Jovens...). Alimenta selo e filtro; NÃO valida nada. */
    @Column(name = "recorte_etario", length = 40)
    private String recorteEtario;

    /** Quem valida é este par. NULL = sem restrição daquele lado. */
    @Column(name = "idade_min") private Integer idadeMin;
    @Column(name = "idade_max") private Integer idadeMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "restricao_estado_civil", length = 20)
    private EstadoCivil restricaoEstadoCivil;

    @Enumerated(EnumType.STRING)
    @Column(name = "restricao_sexo", length = 10)
    private Sexo restricaoSexo;
```

Acrescente também o método de exibição, que centraliza a escolha entre os dois campos:

```java
    /** O local a exibir: o nome do cadastrado, ou o texto ad-hoc, ou null. */
    public String getLocalExibicao() {
        if (local != null) return local.getNome();
        return localTexto;
    }
```

- [ ] **Step 5: Ajustar `Pessoa`**

```java
    @Enumerated(EnumType.STRING)
    @Column(name = "sexo", length = 10)
    private Sexo sexo;
```

- [ ] **Step 6: Escrever o teste da migration**

```java
package com.domus.api.modules.evento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class MigracaoV3Test {

    @Autowired JdbcTemplate jdbc;

    @Test
    void local_texto_preserva_o_conteudo_da_antiga_coluna_local() {
        // A coluna foi RENOMEADA, não recriada: se alguém trocar o RENAME por um ADD COLUMN,
        // os eventos existentes perdem o local em silêncio e este teste é o que denuncia.
        Integer existe = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='evento' AND column_name='local_texto'", Integer.class);
        assertThat(existe).isEqualTo(1);

        Integer antiga = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='evento' AND column_name='local'", Integer.class);
        assertThat(antiga).isZero();
    }

    @Test
    void check_recusa_local_id_e_local_texto_juntos() {
        String igrejaId = jdbc.queryForObject("SELECT id::text FROM igreja LIMIT 1", String.class);
        jdbc.update("INSERT INTO local_evento (igreja_id, nome) VALUES (?::uuid, 'Salão Teste')",
                igrejaId);
        String localId = jdbc.queryForObject(
                "SELECT id::text FROM local_evento WHERE nome='Salão Teste'", String.class);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento (igreja_id, titulo, inicio_em, local_id, local_texto, " +
                "exclusivo_membros, requer_inscricao) " +
                "VALUES (?::uuid, 'X', NOW(), ?::uuid, 'texto', false, false)",
                igrejaId, localId))
                .hasMessageContaining("chk_evento_local_exclusivo");
    }

    @Test
    void check_recusa_idade_min_maior_que_max() {
        String igrejaId = jdbc.queryForObject("SELECT id::text FROM igreja LIMIT 1", String.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO evento (igreja_id, titulo, inicio_em, idade_min, idade_max, " +
                "exclusivo_membros, requer_inscricao) " +
                "VALUES (?::uuid, 'X', NOW(), 30, 18, false, false)", igrejaId))
                .hasMessageContaining("chk_evento_idades");
    }

    @Test
    void check_recusa_sexo_invalido_em_pessoa() {
        String igrejaId = jdbc.queryForObject("SELECT id::text FROM igreja LIMIT 1", String.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO pessoa (igreja_id, nome, vinculo, sexo) " +
                "VALUES (?::uuid, 'Teste', 'CONGREGANTE', 'OUTRO')", igrejaId))
                .hasMessageContaining("chk_pessoa_sexo");
    }
}
```

- [ ] **Step 7: Rodar**

`./mvnw test -Dtest=MigracaoV3Test`
Esperado: 4 testes passando. O boot da aplicação com `ddl-auto=validate` já prova que entidade e
schema batem — se divergirem, o contexto **não sobe** e todos os testes falham juntos.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V3__evento_enriquecido.sql \
        src/main/java/com/domus/api/modules/pessoa/Sexo.java \
        src/main/java/com/domus/api/modules/evento/local/LocalEvento.java \
        src/main/java/com/domus/api/modules/evento/Evento.java \
        src/main/java/com/domus/api/modules/pessoa/Pessoa.java \
        src/test/java/com/domus/api/modules/evento/MigracaoV3Test.java
git commit -m "feat(evento): schema V3 — local_evento, tipo, responsavel, elegibilidade e pessoa.sexo"
```

---

## Task 2: CRUD de locais

**Files:**
- Create: `local/LocalEventoRepository.java`, `LocalEventoService.java`,
  `LocalEventoController.java`, `DTOs/LocalEventoRequest.java`, `DTOs/LocalEventoResponse.java`
- Modify: `src/main/java/com/domus/api/config/SecurityConfig.java`
- Test: `src/test/java/com/domus/api/modules/evento/local/LocalEventoServiceTest.java`

**Interfaces:**
- Consumes: `LocalEvento` (Task 1).
- Produces: `LocalEventoResponse(UUID id, String nome, Integer capacidade, String endereco,
  boolean enderecoHerdado)`; `LocalEventoService.listar(UUID igrejaId)`,
  `.criar(LocalEventoRequest, UUID igrejaId)`, `.atualizar(UUID, LocalEventoRequest, UUID igrejaId)`,
  `.arquivar(UUID, UUID igrejaId)`, `.buscarDaIgreja(UUID id, UUID igrejaId)`.

- [ ] **Step 1: Repository**

```java
public interface LocalEventoRepository extends JpaRepository<LocalEvento, UUID> {

    List<LocalEvento> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<LocalEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    boolean existsByIgrejaIdAndNomeIgnoreCase(UUID igrejaId, String nome);
}
```

- [ ] **Step 2: DTOs**

```java
public record LocalEventoRequest(
        @NotBlank(message = "O nome do local é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome,
        @Positive(message = "A capacidade deve ser maior que zero.")
        Integer capacidade,
        String cepLogradouroNumero,
        String complementoBairroCidadeUf
) {}
```

```java
/**
 * @param endereco        já resolvido: o próprio, ou o da igreja quando herdado
 * @param enderecoHerdado true quando o endereço veio da igreja — a tela avisa o usuário
 */
public record LocalEventoResponse(
        UUID id, String nome, Integer capacidade,
        String endereco, boolean enderecoHerdado
) {}
```

- [ ] **Step 3: Service**

Regras obrigatórias:
- `igrejaId` **sempre do parâmetro** (que vem do JWT), nunca do request.
- Nome normalizado com `TextoUtil.capitalizar` ao gravar.
- Duplicata: comparar com `TextoUtil.normalizarParaComparacao` contra os já existentes da igreja
  e lançar `BusinessException("LOCAL_DUPLICADO", "Já existe um local com esse nome.")`.
- Ao montar o `LocalEventoResponse`, se `!local.temEnderecoProprio()`, preencher `endereco` com o
  da **igreja** e `enderecoHerdado = true`.
- `arquivar` usa `repository.delete(...)` (o `@SQLDelete` faz o soft delete).

- [ ] **Step 4: Controller e rotas**

`@RestController @RequestMapping("/locais-evento")`, com `GET`, `POST`, `PUT /{id}`,
`DELETE /{id}`. Todos extraem `igrejaId` do `@AuthenticationPrincipal`.

⚠️ **Só o id do principal.** Ler campo `LAZY` do principal desanexado estoura — é armadilha
conhecida do projeto.

Em `SecurityConfig`, escrever e ler **exige perfis diferentes**:

```java
.requestMatchers(HttpMethod.GET, "/locais-evento").authenticated()
.requestMatchers("/locais-evento/**").hasAnyRole("ADMIN_IGREJA", "LIDER")
```

⚠️ **Ordem importa:** o matcher específico do `GET` vem **antes** do curinga. Regra que já
mordeu este projeto três vezes.

- [ ] **Step 5: Teste**

```java
@SpringBootTest @Transactional
class LocalEventoServiceTest {

    @Test
    void nao_permite_dois_locais_com_o_mesmo_nome_ignorando_acento_e_caixa() {
        service.criar(new LocalEventoRequest("Salão Social", 80, null, null), igrejaId);
        assertThatThrownBy(() ->
                service.criar(new LocalEventoRequest("salao social", 50, null, null), igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe um local");
    }

    @Test
    void endereco_nulo_herda_o_da_igreja_e_sinaliza_a_heranca() {
        UUID id = service.criar(new LocalEventoRequest("Santuário", 300, null, null), igrejaId).id();
        LocalEventoResponse r = service.listar(igrejaId).stream()
                .filter(l -> l.id().equals(id)).findFirst().orElseThrow();

        assertThat(r.enderecoHerdado()).isTrue();
        assertThat(r.endereco()).isEqualTo(igrejaDoTeste.getCepLogradouroNumero());
    }

    @Test
    void endereco_proprio_nao_herda() {
        UUID id = service.criar(
                new LocalEventoRequest("Chácara Betel", 120, "12345-000, Estrada X, 10", "Zona Rural"),
                igrejaId).id();
        LocalEventoResponse r = service.listar(igrejaId).stream()
                .filter(l -> l.id().equals(id)).findFirst().orElseThrow();

        assertThat(r.enderecoHerdado()).isFalse();
        assertThat(r.endereco()).contains("Estrada X");
    }

    @Test
    void local_de_outra_igreja_nao_e_encontrado() {
        UUID id = service.criar(new LocalEventoRequest("Salão", 80, null, null), igrejaId).id();
        assertThat(repository.findByIdAndIgrejaId(id, outraIgrejaId)).isEmpty();
    }
}
```

- [ ] **Step 6: Rodar e commitar**

`./mvnw test -Dtest=LocalEventoServiceTest` → 4 passando.

```bash
git commit -m "feat(evento): CRUD de locais com capacidade e endereco herdado da igreja"
```

---

## Task 3: Elegibilidade — interface, regras e service

**Esta é a task de maior risco do plano.** Todo campo que ela lê é nulável.

**Files:**
- Create: `elegibilidade/RegraElegibilidade.java`, `Impedimento.java`, `Elegibilidade.java`,
  `CodigoImpedimento.java`, `ElegibilidadeService.java`
- Create: `elegibilidade/regras/RegraFaixaEtaria.java`, `RegraVinculo.java`,
  `RegraEstadoCivil.java`, `RegraSexo.java`
- Test: `src/test/java/com/domus/api/modules/evento/elegibilidade/ElegibilidadeServiceTest.java`

**Interfaces:**
- Consumes: `Evento` e `Pessoa` (Task 1).
- Produces: `ElegibilidadeService.avaliar(Evento, Pessoa) -> Elegibilidade`;
  `Elegibilidade.apto()`, `.impedimentos()`, `.impedimentosNaoContornaveis()`.

- [ ] **Step 1: Contratos**

```java
package com.domus.api.modules.evento.elegibilidade;

/** Códigos em um lugar só: o front decide por código, nunca por texto de mensagem. */
public final class CodigoImpedimento {
    private CodigoImpedimento() {}
    public static final String FAIXA_ETARIA          = "FAIXA_ETARIA";
    public static final String SEM_DATA_NASCIMENTO   = "SEM_DATA_NASCIMENTO";
    public static final String EXCLUSIVO_MEMBROS     = "EXCLUSIVO_MEMBROS";
    public static final String ESTADO_CIVIL          = "ESTADO_CIVIL";
    public static final String SEM_ESTADO_CIVIL      = "SEM_ESTADO_CIVIL";
    public static final String SEXO                  = "SEXO";
    public static final String SEM_SEXO              = "SEM_SEXO";
    public static final String VAGAS_ESGOTADAS       = "VAGAS_ESGOTADAS";
}
```

```java
/**
 * @param contornavel quem gerencia pode inscrever assim mesmo (equipe, preletor, motorista).
 *                    VAGAS_ESGOTADAS NUNCA é contornável: vaga que não existe não vira
 *                    exceção administrativa.
 */
public record Impedimento(String codigo, String mensagem, boolean contornavel) {}
```

```java
public record Elegibilidade(boolean apto, List<Impedimento> impedimentos) {

    public static Elegibilidade apto() { return new Elegibilidade(true, List.of()); }

    public List<Impedimento> impedimentosNaoContornaveis() {
        return impedimentos.stream().filter(i -> !i.contornavel()).toList();
    }

    /** Quem gerencia consegue passar por cima de TODOS os impedimentos presentes? */
    public boolean totalmenteContornavel() {
        return !apto && impedimentosNaoContornaveis().isEmpty();
    }
}
```

```java
public interface RegraElegibilidade {
    /** Vazio = aprovado. Preenchido = por que não pode. */
    Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa);
}
```

- [ ] **Step 2: Escrever os testes ANTES das regras**

```java
package com.domus.api.modules.evento.elegibilidade;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class ElegibilidadeServiceTest {

    private final ElegibilidadeService service = new ElegibilidadeService(List.of(
            new RegraFaixaEtaria(), new RegraVinculo(),
            new RegraEstadoCivil(), new RegraSexo()));

    private Evento eventoSemRestricao() { return Evento.builder().titulo("Culto").build(); }

    private Pessoa pessoaCom(int idade) {
        return Pessoa.builder()
                .nome("Fulano")
                .dataNascimento(LocalDate.now().minusYears(idade))
                .vinculo(Vinculo.MEMBRO)
                .build();
    }

    @Test
    void evento_sem_restricao_aprova_qualquer_pessoa() {
        assertThat(service.avaliar(eventoSemRestricao(), pessoaCom(40)).apto()).isTrue();
    }

    @Test
    void dentro_da_faixa_aprova() {
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        assertThat(service.avaliar(e, pessoaCom(25)).apto()).isTrue();
    }

    @Test
    void fora_da_faixa_reprova_e_e_contornavel() {
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        Elegibilidade r = service.avaliar(e, pessoaCom(34));

        assertThat(r.apto()).isFalse();
        assertThat(r.impedimentos()).hasSize(1);
        assertThat(r.impedimentos().get(0).codigo()).isEqualTo(CodigoImpedimento.FAIXA_ETARIA);
        assertThat(r.impedimentos().get(0).contornavel()).isTrue();
    }

    @Test
    void limites_da_faixa_sao_INCLUSIVOS() {
        // "de 18 até 29" tem que aceitar quem tem 18 e quem tem 29. Errar aqui é um bug
        // silencioso que só aparece no aniversário de alguém.
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        assertThat(service.avaliar(e, pessoaCom(18)).apto()).isTrue();
        assertThat(service.avaliar(e, pessoaCom(29)).apto()).isTrue();
        assertThat(service.avaliar(e, pessoaCom(17)).apto()).isFalse();
        assertThat(service.avaliar(e, pessoaCom(30)).apto()).isFalse();
    }

    @Test
    void sem_data_de_nascimento_reprova_com_CODIGO_PROPRIO() {
        // O código separado é o que permite a tela dizer "procure a secretaria" em vez de
        // "você está fora da faixa" — a causa é um cadastro incompleto, não a idade.
        Evento e = eventoSemRestricao(); e.setIdadeMin(18); e.setIdadeMax(29);
        Pessoa p = Pessoa.builder().nome("Sem Data").vinculo(Vinculo.MEMBRO).build();

        Elegibilidade r = service.avaliar(e, p);

        assertThat(r.apto()).isFalse();
        assertThat(r.impedimentos().get(0).codigo())
                .isEqualTo(CodigoImpedimento.SEM_DATA_NASCIMENTO);
    }

    @Test
    void impedimentos_sao_ACUMULADOS_nao_interrompidos_na_primeira_falha() {
        // Parar na primeira faria a pessoa corrigir um problema e descobrir o seguinte.
        Evento e = eventoSemRestricao();
        e.setIdadeMin(18); e.setIdadeMax(29);
        e.setRestricaoSexo(Sexo.MULHER);
        e.setExclusivoMembros(true);

        Pessoa p = Pessoa.builder().nome("Homem 40")
                .dataNascimento(LocalDate.now().minusYears(40))
                .sexo(Sexo.HOMEM).vinculo(Vinculo.CONGREGANTE).build();

        Elegibilidade r = service.avaliar(e, p);

        assertThat(r.impedimentos()).extracting(Impedimento::codigo)
                .containsExactlyInAnyOrder(
                        CodigoImpedimento.FAIXA_ETARIA,
                        CodigoImpedimento.SEXO,
                        CodigoImpedimento.EXCLUSIVO_MEMBROS);
    }

    @Test
    void sem_sexo_cadastrado_reprova_com_codigo_proprio() {
        Evento e = eventoSemRestricao(); e.setRestricaoSexo(Sexo.MULHER);
        Pessoa p = Pessoa.builder().nome("Sem Sexo").vinculo(Vinculo.MEMBRO).build();
        assertThat(service.avaliar(e, p).impedimentos().get(0).codigo())
                .isEqualTo(CodigoImpedimento.SEM_SEXO);
    }

    @Test
    void sem_estado_civil_cadastrado_reprova_com_codigo_proprio() {
        Evento e = eventoSemRestricao(); e.setRestricaoEstadoCivil(EstadoCivil.CASADO);
        Pessoa p = Pessoa.builder().nome("Sem EC").vinculo(Vinculo.MEMBRO).build();
        assertThat(service.avaliar(e, p).impedimentos().get(0).codigo())
                .isEqualTo(CodigoImpedimento.SEM_ESTADO_CIVIL);
    }

    @Test
    void restricao_de_vinculo_usa_o_exclusivoMembros_que_ja_existia() {
        Evento e = eventoSemRestricao(); e.setExclusivoMembros(true);
        Pessoa congregante = Pessoa.builder().nome("C").vinculo(Vinculo.CONGREGANTE).build();
        Pessoa membro = Pessoa.builder().nome("M").vinculo(Vinculo.MEMBRO).build();

        assertThat(service.avaliar(e, congregante).apto()).isFalse();
        assertThat(service.avaliar(e, membro).apto()).isTrue();
    }
}
```

- [ ] **Step 3: Rodar e ver falhar**

`./mvnw test -Dtest=ElegibilidadeServiceTest`
Esperado: erro de compilação (as classes ainda não existem). É o "vermelho" do TDD.

- [ ] **Step 4: Implementar as regras**

```java
@Component
public class RegraFaixaEtaria implements RegraElegibilidade {

    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (evento.getIdadeMin() == null && evento.getIdadeMax() == null) {
            return Optional.empty();  // evento sem restrição de idade
        }
        if (pessoa.getDataNascimento() == null) {
            return Optional.of(new Impedimento(
                    CodigoImpedimento.SEM_DATA_NASCIMENTO,
                    "O cadastro de " + pessoa.getNome() + " não tem data de nascimento. "
                    + "Procure a secretaria da igreja para completá-lo.",
                    true));
        }
        int idade = Period.between(pessoa.getDataNascimento(), LocalDate.now()).getYears();

        // Limites INCLUSIVOS: "de 18 até 29" aceita 18 e 29.
        boolean abaixo = evento.getIdadeMin() != null && idade < evento.getIdadeMin();
        boolean acima  = evento.getIdadeMax() != null && idade > evento.getIdadeMax();
        if (!abaixo && !acima) return Optional.empty();

        return Optional.of(new Impedimento(
                CodigoImpedimento.FAIXA_ETARIA,
                pessoa.getNome() + " tem " + idade + " anos e este evento é para "
                        + descreverFaixa(evento) + ".",
                true));
    }

    private String descreverFaixa(Evento e) {
        if (e.getIdadeMin() != null && e.getIdadeMax() != null)
            return e.getIdadeMin() + " a " + e.getIdadeMax() + " anos";
        if (e.getIdadeMin() != null) return "maiores de " + e.getIdadeMin() + " anos";
        return "menores de " + e.getIdadeMax() + " anos";
    }
}
```

```java
@Component
public class RegraVinculo implements RegraElegibilidade {
    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (!evento.isExclusivoMembros()) return Optional.empty();
        if (pessoa.getVinculo() == Vinculo.MEMBRO) return Optional.empty();
        return Optional.of(new Impedimento(
                CodigoImpedimento.EXCLUSIVO_MEMBROS,
                "Este evento é exclusivo para membros batizados.",
                true));
    }
}
```

```java
@Component
public class RegraSexo implements RegraElegibilidade {
    @Override
    public Optional<Impedimento> avaliar(Evento evento, Pessoa pessoa) {
        if (evento.getRestricaoSexo() == null) return Optional.empty();
        if (pessoa.getSexo() == null) {
            return Optional.of(new Impedimento(CodigoImpedimento.SEM_SEXO,
                    "O cadastro de " + pessoa.getNome() + " não informa o sexo. "
                    + "Procure a secretaria da igreja para completá-lo.", true));
        }
        if (pessoa.getSexo() == evento.getRestricaoSexo()) return Optional.empty();
        return Optional.of(new Impedimento(CodigoImpedimento.SEXO,
                "Este evento é para " + (evento.getRestricaoSexo() == Sexo.MULHER
                        ? "mulheres" : "homens") + ".", true));
    }
}
```

`RegraEstadoCivil` segue o mesmo formato, usando `getRestricaoEstadoCivil()`,
`CodigoImpedimento.SEM_ESTADO_CIVIL` / `ESTADO_CIVIL`, e a mensagem
`"Este evento é para pessoas com estado civil: " + rotulo + "."`.

- [ ] **Step 5: O service**

```java
@Service
@RequiredArgsConstructor
public class ElegibilidadeService {

    /**
     * O Spring injeta TODAS as implementações. Adicionar uma restrição nova = criar um
     * arquivo com @Component. Nenhuma linha daqui muda — é o "estenda sem editar" do CLAUDE.md.
     *
     * ⚠️ RegraVagas NÃO entra aqui. A contagem autoritativa de vagas vive no InscricaoService,
     * dentro da transação com lock pessimista. Duplicá-la reabriria a corrida que a Spec A
     * fechou.
     */
    private final List<RegraElegibilidade> regras;

    public Elegibilidade avaliar(Evento evento, Pessoa pessoa) {
        List<Impedimento> encontrados = regras.stream()
                .map(r -> r.avaliar(evento, pessoa))
                .flatMap(Optional::stream)
                .toList();

        // Avalia TODAS antes de decidir: a pessoa vê de uma vez tudo o que a impede.
        return new Elegibilidade(encontrados.isEmpty(), encontrados);
    }
}
```

- [ ] **Step 6: Rodar até verde**

`./mvnw test -Dtest=ElegibilidadeServiceTest` → 9 testes passando.

- [ ] **Step 7: Verificação por sabotagem**

Troque `idade < evento.getIdadeMin()` por `idade <= evento.getIdadeMin()` e rode de novo.
Esperado: `limites_da_faixa_sao_INCLUSIVOS` **falha**. Desfaça a sabotagem.
Um teste que não quebra quando o código quebra não está testando nada.

- [ ] **Step 8: Commit**

```bash
git commit -m "feat(evento): elegibilidade por regras independentes com impedimentos acumulados"
```

---

## Task 4: Integrar a elegibilidade na inscrição

**Files:**
- Modify: `InscricaoService.java`, `InscricaoController.java`, `EventoController.java`
- Modify: `SecurityConfig.java`
- Create: `elegibilidade/DTOs/ElegibilidadeResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoElegibilidadeTest.java`

**Interfaces:**
- Consumes: `ElegibilidadeService.avaliar(...)` (Task 3).
- Produces: `GET /eventos/{id}/elegibilidade`; `POST /eventos/{id}/inscricoes?confirmado=true`.

- [ ] **Step 1: Regras da integração**

No `InscricaoService`, antes de confirmar a inscrição:

1. Rodar `elegibilidadeService.avaliar(evento, pessoa)`.
2. Se `apto()` → segue.
3. Se **não** apto:
   - **auto-inscrição** (a pessoa se inscrevendo) → sempre recusa, **mesmo que ela gerencie**.
     A exceção existe para inscrever terceiros (equipe, preletor), não para burlar a própria.
   - **inscrevendo outra pessoa**, com `podeGerenciarInscricoes(role)` **e** `confirmado == true`
     **e** `elegibilidade.totalmenteContornavel()` → segue.
   - caso contrário → `BusinessException("NAO_ELEGIVEL", ...)` carregando a lista, respondida
     como **422**.

⚠️ `confirmado=true` enviado por quem **não** tem a permissão é **ignorado**, não aceito.

⚠️ A validação de vagas existente **permanece exatamente onde está**, dentro da transação com
lock. Não mover, não duplicar.

- [ ] **Step 2: Testes**

```java
@Test
void auto_inscricao_fora_da_faixa_e_recusada_mesmo_para_admin() {
    // A exceção existe para inscrever TERCEIROS. Deixar o admin burlar a própria inscrição
    // transformaria a restrição em decoração para quem tem acesso.
    assertThatThrownBy(() -> service.inscrever(eventoJovens, adminDe40, "ADMIN_IGREJA", true))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("codigo", "NAO_ELEGIVEL");
}

@Test
void admin_inscreve_terceiro_fora_da_faixa_com_confirmado() {
    service.inscreverPessoas(eventoJovens, List.of(lider34), usuarioAdmin, "ADMIN_IGREJA", true);
    assertThat(inscricaoRepository.countConfirmadas(eventoJovens)).isEqualTo(1);
}

@Test
void confirmado_de_quem_nao_gerencia_e_IGNORADO() {
    assertThatThrownBy(() -> service.inscreverPessoas(
            eventoJovens, List.of(lider34), usuarioComum, "ACESSO_COMUM", true))
            .isInstanceOf(BusinessException.class);
}

@Test
void confirmado_NAO_derruba_vagas_esgotadas() {
    // Vaga que não existe não vira exceção administrativa.
    assertThatThrownBy(() -> service.inscreverPessoas(
            eventoLotado, List.of(pessoaQualquer), usuarioAdmin, "ADMIN_IGREJA", true))
            .hasFieldOrPropertyWithValue("codigo", "VAGAS_ESGOTADAS");
}

@Test
void GET_elegibilidade_e_POST_concordam_sobre_a_mesma_pessoa() {
    var previa = elegibilidadeService.avaliar(eventoJovens, pessoaDe34);
    assertThat(previa.apto()).isFalse();
    assertThatThrownBy(() -> service.inscrever(eventoJovens, pessoaDe34, "ACESSO_COMUM", false))
            .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 3: Rotas**

```java
.requestMatchers(HttpMethod.GET, "/eventos/*/elegibilidade").authenticated()
```

⚠️ **Antes** dos curingas `/eventos/**`. Mesma armadilha das rotas de inscrição.

- [ ] **Step 4: Rodar a suíte inteira**

`./mvnw test`

⚠️ **O teste de concorrência da Spec A tem que continuar passando.** Se ele quebrar, a
`RegraVagas` invadiu o caminho do `POST` — reverta essa parte.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(evento): elegibilidade aplicada na inscricao com contorno para quem gerencia"
```

---

## Task 5: Evento — tipo, responsável, local e auditoria no serviço

**Files:**
- Modify: `EventoRequest.java`, `EventoResponse.java`, `EventoService.java`,
  `EventoController.java`, `EventoRepository.java`
- Test: `src/test/java/com/domus/api/modules/evento/EventoTipoENormalizacaoTest.java`

**Interfaces:**
- Produces: `GET /eventos/tipos` → `List<String>`; `EventoResponse` com `tipo`,
  `local` (`{id, nome, endereco, enderecoHerdado}` ou texto), `responsavel`,
  `criadoPor`/`atualizadoPor`, `recorteEtario`, `idadeMin`, `idadeMax`,
  `restricaoEstadoCivil`, `restricaoSexo`.

- [ ] **Step 1: `EventoRequest`**

Remover `String local`. Acrescentar: `UUID localId`, `String localTexto`, `String tipo`,
`UUID responsavelPessoaId`, `String recorteEtario`, `@PositiveOrZero Integer idadeMin`,
`@PositiveOrZero Integer idadeMax`, `EstadoCivil restricaoEstadoCivil`, `Sexo restricaoSexo`.

- [ ] **Step 2: Validação no service**

- `localId` **e** `localTexto` juntos → `BusinessException("LOCAL_AMBIGUO", "Escolha um local
  cadastrado ou digite um local, não os dois.")`. O `CHECK` do banco é a rede de segurança; a
  mensagem decente é responsabilidade daqui.
- `localId` de **outra igreja** → tratar como inexistente (`findByIdAndIgrejaId`), nunca vazar
  que existe.
- `idadeMin > idadeMax` → `BusinessException("FAIXA_INVALIDA", "A idade mínima não pode ser
  maior que a máxima.")`.
- `tipo` gravado com `TextoUtil.capitalizar`. Antes de gravar, procurar um tipo já existente na
  igreja cujo `normalizarParaComparacao` bata e, se houver, **reusar a grafia existente**.
- `criadoPor` no insert, `atualizadoPor` no update — ambos do usuário do JWT.

- [ ] **Step 3: Endpoint de sugestões**

```java
@Query("""
    SELECT e.tipo FROM Evento e
     WHERE e.igreja.id = :igrejaId AND e.tipo IS NOT NULL
     GROUP BY e.tipo
     ORDER BY COUNT(e) DESC, e.tipo ASC
    """)
List<String> tiposUsadosPorFrequencia(@Param("igrejaId") UUID igrejaId);
```

O service devolve os da igreja primeiro e completa com as sementes ainda não usadas:

```java
private static final List<String> SEMENTES =
        List.of("Culto", "Conferência", "Retiro", "Ensaio", "Reunião");
```

A ordem é o que faz o campo parecer que aprende: o que a igreja mais usa sobe, o que o sistema
chutou e ninguém usou desce.

- [ ] **Step 4: Testes**

```java
@Test
void tipo_e_gravado_capitalizado() {
    UUID id = service.criar(requestComTipo("  culto   de   jovens "), igrejaId, usuarioId).id();
    assertThat(service.buscar(id, igrejaId).tipo()).isEqualTo("Culto de Jovens");
}

@Test
void tipo_equivalente_reusa_a_grafia_ja_existente() {
    service.criar(requestComTipo("Vigília"), igrejaId, usuarioId);
    UUID id = service.criar(requestComTipo("vigilia"), igrejaId, usuarioId).id();
    // "vigilia" (sem acento) não pode criar um segundo tipo — o filtro ficaria com dois.
    assertThat(service.buscar(id, igrejaId).tipo()).isEqualTo("Vigília");
}

@Test
void tipos_distintos_NAO_sao_colapsados() {
    service.criar(requestComTipo("Culto"), igrejaId, usuarioId);
    UUID id = service.criar(requestComTipo("Cultinho"), igrejaId, usuarioId).id();
    assertThat(service.buscar(id, igrejaId).tipo()).isEqualTo("Cultinho");
}

@Test
void sugestoes_trazem_os_da_igreja_por_frequencia_antes_das_sementes() {
    service.criar(requestComTipo("Vigília"), igrejaId, usuarioId);
    service.criar(requestComTipo("Vigília"), igrejaId, usuarioId);
    service.criar(requestComTipo("Retiro"), igrejaId, usuarioId);

    assertThat(service.tiposSugeridos(igrejaId)).startsWith("Vigília", "Retiro");
}

@Test
void local_de_outra_igreja_e_recusado() {
    assertThatThrownBy(() -> service.criar(requestComLocal(localDeOutraIgreja), igrejaId, usuarioId))
            .isInstanceOf(BusinessException.class);
}

@Test
void local_id_e_local_texto_juntos_sao_recusados_com_mensagem_clara() {
    assertThatThrownBy(() -> service.criar(requestComAmbos(), igrejaId, usuarioId))
            .hasFieldOrPropertyWithValue("codigo", "LOCAL_AMBIGUO");
}

@Test
void arquivar_a_pessoa_responsavel_nao_apaga_o_evento() {
    UUID eventoId = service.criar(requestComResponsavel(pessoaId), igrejaId, usuarioId).id();
    pessoaService.arquivar(pessoaId, igrejaId);
    assertThat(service.buscar(eventoId, igrejaId)).isNotNull();
}
```

- [ ] **Step 5: Rodar e commitar**

```bash
git commit -m "feat(evento): tipo com sugestoes, responsavel, local estruturado e auditoria"
```

---

## Task 6: Impacto retroativo ao apertar restrição

**Files:**
- Modify: `EventoService.java`, `EventoController.java`, `InscricaoService.java`
- Create: `evento/DTOs/ImpactoRestricaoResponse.java`
- Test: `src/test/java/com/domus/api/modules/evento/ImpactoRestricaoTest.java`

**Interfaces:**
- Produces: `POST /eventos/{id}/impacto-restricao` (body = `EventoRequest`) →
  `ImpactoRestricaoResponse(List<InscritoImpactado> afetados)`;
  `PUT /eventos/{id}?cancelarNaoElegiveis=true`.

⚠️ **Mudança de comportamento existente:** `removerInscritosNaoElegiveis` **deixa de ser
chamado automaticamente** ao ligar `exclusivoMembros`. Passa a rodar só com
`cancelarNaoElegiveis=true` explícito.

Motivo (spec, decisão 6): cancelar sozinho apagaria as exceções que o próprio admin criou com o
"inscrever mesmo assim".

⚠️ `cancelarInscricoesEmEventosExclusivos(pessoaId)` — quando a **pessoa** deixa de ser membro —
**continua automático**. Ali a mudança partiu da pessoa, e não há exceção deliberada a preservar.

- [ ] **Step 1: Teste primeiro**

```java
@Test
void apertar_a_faixa_NAO_cancela_ninguem_sozinho() {
    inscrever(eventoJovens, pessoaDe34, comConfirmacao());
    atualizar(eventoJovens, faixaDe(18, 25), /* cancelarNaoElegiveis */ false);

    assertThat(inscricaoRepository.countConfirmadas(eventoJovens)).isEqualTo(1);
}

@Test
void previa_lista_quem_ficaria_de_fora_sem_alterar_nada() {
    inscrever(eventoJovens, pessoaDe34, comConfirmacao());
    var impacto = service.calcularImpacto(eventoJovens, faixaDe(18, 25), igrejaId);

    assertThat(impacto.afetados()).extracting("nome").containsExactly("Maria Souza");
    assertThat(inscricaoRepository.countConfirmadas(eventoJovens)).isEqualTo(1);
}

@Test
void com_a_escolha_explicita_cancela() {
    inscrever(eventoJovens, pessoaDe34, comConfirmacao());
    atualizar(eventoJovens, faixaDe(18, 25), /* cancelarNaoElegiveis */ true);

    assertThat(inscricaoRepository.countConfirmadas(eventoJovens)).isZero();
}

@Test
void ligar_exclusivoMembros_tambem_deixou_de_cancelar_sozinho() {
    // Mudança deliberada de comportamento — antes cancelava em silêncio.
    inscrever(eventoAberto, congregante, semConfirmacao());
    atualizar(eventoAberto, exclusivoMembros(true), /* cancelarNaoElegiveis */ false);

    assertThat(inscricaoRepository.countConfirmadas(eventoAberto)).isEqualTo(1);
}

@Test
void pessoa_que_deixa_de_ser_membro_CONTINUA_sendo_cancelada() {
    inscrever(eventoExclusivo, membro, semConfirmacao());
    pessoaService.mudarVinculo(membro, Vinculo.CONGREGANTE);

    assertThat(inscricaoRepository.countConfirmadas(eventoExclusivo)).isZero();
}
```

- [ ] **Step 2: Implementar**

`calcularImpacto` roda a elegibilidade **das regras novas** contra cada inscrito confirmado e
devolve quem falharia — sem gravar nada.

- [ ] **Step 3: Rodar e commitar**

```bash
git commit -m "feat(evento): apertar restricao avisa em vez de cancelar em silencio"
```

---

## Task 7: `pessoa.sexo` no cadastro

**Files:**
- Modify: `PessoaRequestDTO.java`, `PessoaResponse.java`, `PessoaService.java`,
  `PessoaDocument.java`
- Modify: `frontend/src/components/module/pessoas/PessoaForm.tsx`, `lib/validators.ts`,
  `types/pessoa.types.ts`
- Modify: `backend/api/scripts/seed-dev.sql`
- Test: `src/test/java/com/domus/api/modules/pessoa/PessoaSexoTest.java`

- [ ] **Step 1:** Campo `Sexo sexo` nos DTOs e no service. **Nulável** — as pessoas já
  cadastradas não têm valor e inventar um seria inventar dado sobre gente real.

- [ ] **Step 2:** No `PessoaForm`, um `<StatusCards>` de duas opções (Homem / Mulher) na seção
  "Informações pessoais", logo após estado civil. Componente já existe no projeto.

- [ ] **Step 3:** No `seed-dev.sql`, distribuir `sexo` entre as 82 pessoas da demo — senão a
  restrição por sexo não tem como ser demonstrada.

- [ ] **Step 4:** Teste: pessoa salva sem `sexo` continua válida; pessoa com `sexo` volta com ele.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(pessoa): campo sexo (HOMEM|MULHER) para restricao de evento"
```

---

## Task 8: Front — formulário de evento em duas colunas

**Files:**
- Modify: `EventoForm.tsx`, `EventoForm.module.css`, `useEventoForm.ts`, `validators.ts`
- Create: `components/common/InputComSugestoes/InputComSugestoes.tsx` (+ CSS)
- Create: `components/module/eventos/SeletorLocal.tsx`
- Create: `hooks/evento/useLocaisEvento.ts`, `hooks/evento/useTiposEvento.ts`

- [ ] **Step 1: `<InputComSugestoes>`**

Genérico: recebe `sugestoes: string[]`, `value`, `onChange`. Renderiza chips clicáveis **mais**
o input livre. Digitar nunca é bloqueado — os chips são atalho.

Espelhe o visual de `MinisterioInput.tsx`, mas **as sugestões vêm por prop**, não de uma
constante interna. É essa diferença que permite reusar no tipo e no local ad-hoc.

- [ ] **Step 2: `<SeletorLocal>`**

`<select>` com os locais cadastrados (exibindo `— cap. N` quando houver) mais a opção
`— outro local —`, que troca o select por um `<InputComSugestoes>` de texto livre.

Ao escolher um local com capacidade, preencher **Vagas** — mas **só se o campo estiver vazio**.
Sugestão que sobrescreve escolha do usuário é armadilha.

- [ ] **Step 3: Layout**

Reusar as classes `colunas` / `colunaEsquerda` / `colunaDireita` do `PessoaForm.module.css`.

Esquerda: título, tipo, descrição, data/horário, local.
Direita: banner (`<UploadFoto>`), responsável, e a seção de inscrições.

- [ ] **Step 4: Mobile**

`@media (max-width: 900px)` → uma coluna, na ordem acima. Verificar no viewport de celular que
não há rolagem horizontal. **Parte da entrega, não etapa separada.**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(evento): formulario em duas colunas com local, tipo e responsavel"
```

---

## Task 9: Front — bloco "Para quem é" e confirmação retroativa

**Files:**
- Create: `components/module/eventos/BlocoParaQuemE.tsx` (+ CSS)
- Create: `components/module/eventos/ModalImpactoRestricao.tsx` (+ CSS)
- Modify: `EventoForm.tsx`, `useEventoForm.ts`, `validators.ts`

- [ ] **Step 1: `<BlocoParaQuemE>`**

Rádio "Todos" / "Faixa específica", **recolhido em "Todos" por padrão** — um evento comum não
deve pagar o preço visual de uma feature que a maioria dos eventos não usa.

Ao escolher faixa: chips de recorte que **preenchem** min/max, e os dois campos numéricos
editáveis ao lado. Os recortes e seus padrões:

```ts
export const RECORTES_ETARIOS = [
  { nome: 'Kids',         idadeMin: 0,  idadeMax: 11 },
  { nome: 'Adolescentes', idadeMin: 12, idadeMax: 17 },
  { nome: 'Jovens',       idadeMin: 18, idadeMax: 29 },
  { nome: 'Adultos',      idadeMin: 30, idadeMax: 59 },
  { nome: '3ª idade',     idadeMin: 60, idadeMax: null },
] as const
```

Mais os seletores de estado civil e sexo, e o toggle de "só membros" que **já existe** (mover
para cá, não duplicar).

- [ ] **Step 2: Zod**

`idadeMin <= idadeMax` com `.refine`, mensagem `"A idade mínima não pode ser maior que a máxima."`

- [ ] **Step 3: `<ModalImpactoRestricao>`**

Ao salvar um evento com restrição ligada/apertada e inscritos existentes, chamar
`POST /eventos/{id}/impacto-restricao`. Se vier alguém, abrir o modal listando nome e o motivo,
com **"Manter todos"** e **"Cancelar os N"**.

⚠️ Não usar `window.confirm` — convenção do projeto.

- [ ] **Step 4: Mobile + commit**

```bash
git commit -m "feat(evento): bloco Para quem e com recortes e aviso de impacto retroativo"
```

---

## Task 10: Front — inscrição com impedimentos

**Files:**
- Modify: `BotaoConfirmarPresenca.tsx`, `ModalInscreverPessoas.tsx`
- Create: `hooks/inscricao/useElegibilidade.ts`
- Modify: `lib/endpoints.ts`, `lib/cacheInvalidacao.ts`

- [ ] **Step 1:** `useElegibilidade(eventoId)` consulta `GET /eventos/{id}/elegibilidade`.

- [ ] **Step 2:** Botão de inscrição **visível e desabilitado**, com o motivo ao lado —
  **nunca escondido**. Botão que some deixa a pessoa achando que o sistema quebrou; botão
  desabilitado com "este evento é para 18 a 29 anos" ensina a regra.

- [ ] **Step 3:** No `ModalInscreverPessoas`, quem está fora aparece com selo de aviso. Ao
  confirmar, se houver impedimento contornável, mostrar a confirmação e enviar `confirmado=true`.

⚠️ O botão desabilitado é **conveniência**. A defesa é o 422 do backend — a tela nunca decide.

- [ ] **Step 4:** Invalidação: `['elegibilidade', eventoId]` ao inscrever/cancelar. Lembrar que
  a invalidação é **por prefixo** — `['eventos']` não cobre `['evento', id]`.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(inscricao): impedimentos na tela e inscrever mesmo assim para gestores"
```

---

## Task 11: Front — selo, filtros, detalhe e tela de locais

**Files:**
- Modify: `EventoCard.tsx`, `app/(app)/eventos/page.tsx`, `DrawerDetalheEvento.tsx`,
  `ModalEventoResumo.tsx`
- Create: `app/(app)/eventos/locais/page.tsx` (+ CSS)

- [ ] **Step 1:** Selo do recorte no card (`Jovens`, `Kids`), ao lado do selo de situação.

- [ ] **Step 2:** Filtros por tipo e por recorte na lista, reusando `<PainelFiltros>`.

- [ ] **Step 3:** No detalhe: responsável, endereço do local (com "endereço da igreja" quando
  herdado) e "criado por / atualizado por".

- [ ] **Step 4:** Tela de locais: lista com nome, capacidade e endereço; criar, editar e
  arquivar. Reusar `<ModalConfirmacaoCritica>` no arquivamento e `<EstadoVazio>` na lista vazia.

- [ ] **Step 5: Mobile** — tabela de locais vira **cards**; header empilha. Convenção do projeto.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(evento): selo de recorte, filtros, detalhe enriquecido e tela de locais"
```

---

## Task 12: Documentação e seeds

**Files:**
- Modify: `backend/api/CLAUDE.md` (diagrama ER → V3), `docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`
- Modify: `backend/api/scripts/seed-dev.sql`, `seed-prod.sql`

- [ ] **Step 1:** Atualizar o diagrama ER no `CLAUDE.md` com `LOCAL_EVENTO`, as colunas novas de
  `EVENTO` e `pessoa.sexo`. **O diagrama é documentação viva** — a fonte da verdade são as
  migrations, mas ele desatualizado engana quem o lê.

- [ ] **Step 2:** No BACKLOG, marcar a Spec B e a elegibilidade como concluídas; anotar o que
  ficou de fora (Specs C, D, E; capacidade impondo limite; lista de espera).

- [ ] **Step 3:** No `seed-dev.sql`: 3–4 locais por igreja (com e sem endereço próprio), tipos
  variados, responsáveis e ao menos um evento por recorte etário — a demo precisa mostrar a
  feature funcionando.

- [ ] **Step 4: Commit**

```bash
git commit -m "docs(evento): diagrama ER em V3, backlog atualizado e seeds com locais e recortes"
```

---

## Auto-revisão do plano

**Cobertura da spec:**

| Requisito | Task |
|---|---|
| Local tabela + texto livre | 1, 2, 8 |
| Capacidade sugere vagas | 2, 8 |
| Endereço herdado da igreja | 2, 11 |
| Tipo que aprende + normalização | 5, 8 |
| Responsável | 5, 8, 11 |
| Banner | 8 (`<UploadFoto>` já existe) |
| Auditoria criado/atualizado por | 5, 11 |
| Layout duas colunas | 8 |
| 4 restrições por regras independentes | 3 |
| Bloqueio com código próprio p/ dado ausente | 3 |
| Contorno por quem gerencia | 4, 10 |
| `VAGAS_ESGOTADAS` não contornável | 4 |
| Aviso retroativo | 6, 9 |
| `pessoa.sexo` | 1, 7 |
| Selo e filtros | 11 |
| Mobile | 8, 9, 11 |

**Consistência de tipos:** `Elegibilidade`, `Impedimento`, `CodigoImpedimento`,
`LocalEventoResponse` e `RECORTES_ETARIOS` são definidos na primeira aparição e referenciados
pelos mesmos nomes depois.

**Riscos que o plano marca explicitamente:**
1. `RENAME` da coluna `local` (Task 1) — `ADD COLUMN` perderia os dados em silêncio.
2. `RegraVagas` fora do `POST` (Tasks 3 e 4) — reabriria a corrida da Spec A.
3. Ordem dos matchers no `SecurityConfig` (Tasks 2 e 4) — já mordeu três vezes.
4. Auto-inscrição não contorna nem para admin (Task 4).
5. Mudança de comportamento do `exclusivoMembros` (Task 6) — deliberada e testada.
