# Evento com múltiplos responsáveis

> Spec de design. Data: 2026-09-02. Back: Java 21 / Spring Boot / Flyway / Postgres.
> Front: Next.js 16 (App Router), TypeScript, CSS Modules, React Hook Form + Zod, TanStack Query.

## Problema

Hoje um evento tem **um** responsável (`evento.responsavel_pessoa_id`, FK única, +
`responsavel_texto` para quando a pessoa é excluída por LGPD). Eventos reais têm mais de
um organizador (ex.: o casal que cuida do retiro, os dois líderes de um congresso). Não dá
pra representar isso — o segundo responsável fica de fora.

## Objetivo

Um evento pode ter **zero, um ou vários** responsáveis. Sem limite de quantidade.

## Não-objetivos

- Papéis diferentes entre responsáveis (todos são "responsável", sem hierarquia).
- Responsável entrar na busca global / Elasticsearch (não entra hoje, continua fora).
- Responsável obrigatório (continua opcional).

## Decisões (tomadas no brainstorm)

- **Tabela de junção** `evento_responsavel`, no mesmo padrão de `movimentacao_contribuinte`
  (V15): `pessoa_id` **XOR** `nome_texto`. Quando um responsável é excluído
  definitivamente (ou arquivado — soft delete não dispara FK e o proxy LAZY estoura), a
  linha vira `pessoa_id = NULL, nome_texto = <nome>` e aparece como "Pessoa removida do
  sistema", igual acontece hoje com o responsável único.
- **UI**: busca de pessoa (igual hoje) + lista de **chips removíveis** abaixo. Sem teto.
- Contrato `EventoResponse.responsavel` (singular, `PessoaResumo`) vira `responsaveis`
  (lista de `PessoaResumo`). **Breaking**, mas todos os consumidores estão neste repo
  (drawer de detalhe, form). O resumo do início (`ModalEventoResumo`) não mostra
  responsável hoje — não muda.

## Estado atual relevante (investigado)

- `Evento`: `@ManyToOne(LAZY) Pessoa responsavel` (`responsavel_pessoa_id`) +
  `String responsavelTexto` (`responsavel_texto`).
- `EventoRequest.responsavelPessoaId` (UUID, posição 9 do record).
- `EventoResponse.responsavel` = `PessoaResumo` (montado por
  `PessoaResumo.dePessoa(e.getResponsavel(), e.getResponsavelTexto())` — `p != null` →
  `{id, nome}`; senão `textoFallback != null` → `{id: null, nome: texto}`).
- `EventoService`:
  - `resolverResponsavel(UUID, igrejaId)` → `Pessoa` ou `null`
    (`pessoaRepository.findByIdAndIgrejaId`, `ResourceNotFoundException` se não achar).
  - `cadastrarEvento` seta `.responsavel(...)` no builder; depois `notificarNovoResponsavel`.
  - `atualizarEvento`: guarda `responsavelIdAntigo`, seta novo, e se
    `!Objects.equals(responsavelIdAntigo, responsavelIdNovo)` chama `notificarNovoResponsavel`.
  - `notificarNovoResponsavel(evento, igrejaId, usuarioIdAtor)`: se `evento.getResponsavel()`
    não-nulo, acha o `Usuario` da pessoa e notifica **"Você foi definido como responsável
    pelo evento \"X\"."** — pula quando o ator é o próprio.
  - Séries: ao editar uma série, as ocorrências AGENDADAS recebem
    `ocorrencia.setResponsavel(editado.getResponsavel())` (linha ~416).
- `EventoRepository.desvincularResponsavel(pessoaId, nome)` — `UPDATE evento SET
  responsavel_texto = :nome, responsavel_pessoa_id = NULL WHERE responsavel_pessoa_id =
  :pessoaId`. Chamado de `PessoaService.arquivarMembro` (linha 298) **e**
  `PessoaService` no purge definitivo (linha 361).
- `EventoDocument` — sem responsável. Nada muda no ES.
- Front:
  - `SeletorResponsavel.tsx` — busca (debounce 300ms, `usePessoas`), 1 resultado clicável
    vira chip; `onChange(pessoaId | undefined, nome | undefined)`.
  - `EventoForm`: `watch('responsavelPessoaId')`, prop `responsavelNomeInicial` (string),
    `<SeletorResponsavel valor={...} nomeInicial={...} onChange={id => setValue(...)} />`.
  - `useEventoForm`: default `responsavelPessoaId: undefined`; reidrata
    `responsavelPessoaId: eventoInicial.responsavel?.id`; payload
    `responsavelPessoaId: data.responsavelPessoaId || null`; expõe
    `responsavelNomeInicial = eventoInicial?.responsavel?.nome`.
  - `validators.ts`: `responsavelPessoaId: opcional(z.string())`.
  - `evento.type.ts`: `EventoResponse.responsavel: EventoPessoaResumo | null`;
    `EventoRequest.responsavelPessoaId?: string | null`.
  - `DrawerDetalheEvento.tsx` (~linha 192): `{evento.responsavel && (<div>… {evento.responsavel.nome} …</div>)}`.
- Última migration: **V36**. Próxima: **V37**.

## Arquitetura

### 1. Schema — `V37__evento_multiplos_responsaveis.sql`

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

-- Uma pessoa não pode ser responsável do mesmo evento duas vezes.
CREATE UNIQUE INDEX uq_evento_responsavel_evento_pessoa
    ON evento_responsavel (evento_id, pessoa_id)
    WHERE pessoa_id IS NOT NULL;

CREATE INDEX idx_evento_responsavel_evento ON evento_responsavel (evento_id);
CREATE INDEX idx_evento_responsavel_pessoa ON evento_responsavel (pessoa_id);

-- Migra o responsável único (pessoa OU texto) de cada evento para a tabela nova.
INSERT INTO evento_responsavel (igreja_id, evento_id, pessoa_id, nome_texto)
SELECT igreja_id, id, responsavel_pessoa_id, responsavel_texto
FROM evento
WHERE responsavel_pessoa_id IS NOT NULL OR responsavel_texto IS NOT NULL;

ALTER TABLE evento
    DROP COLUMN responsavel_pessoa_id,
    DROP COLUMN responsavel_texto;
```

### 2. Backend — entidade

Nova entidade `EventoResponsavel` (`modules/evento/EventoResponsavel.java`):

```java
@Entity @Table(name = "evento_responsavel")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventoResponsavel {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @Column(name = "nome_texto")
    private String nomeTexto;
}
```

`Evento`:

```java
// Substitui `responsavel` + `responsavelTexto`.
@OneToMany(mappedBy = "evento", cascade = CascadeType.ALL, orphanRemoval = true)
@Builder.Default
private List<EventoResponsavel> responsaveis = new ArrayList<>();
```

### 3. Backend — DTO

**`EventoRequest`**: `UUID responsavelPessoaId` → `List<UUID> responsavelPessoaIds`
(mesma posição no record). `null` e lista vazia = sem responsável.

**`EventoResponse`**: `PessoaResumo responsavel` → `List<PessoaResumo> responsaveis`.
Montagem em `from`:

```java
List<PessoaResumo> responsaveis = e.getResponsaveis().stream()
        .map(r -> PessoaResumo.dePessoa(r.getPessoa(), r.getNomeTexto()))
        .filter(java.util.Objects::nonNull)
        .toList();
```

`PessoaResumo.dePessoa` **não muda** — já cobre pessoa e texto-fallback.

### 4. Backend — serviço

`EventoService`:

- `resolverResponsavel` → **`resolverResponsaveis(List<UUID> ids, UUID igrejaId, Igreja igreja, Evento evento)`**:
  para cada id, `pessoaRepository.findByIdAndIgrejaId` (`ResourceNotFoundException` se
  faltar), monta `EventoResponsavel` (pessoa preenchida, `nomeTexto` null) apontando pro
  `evento` e `igreja`. Dedup de ids repetidos. Retorna `List<EventoResponsavel>`.

- **`cadastrarEvento`**: monta o evento, depois
  `evento.getResponsaveis().addAll(resolverResponsaveis(data.responsavelPessoaIds(), igrejaId, igreja, evento))`
  antes do `save` (cascade grava as linhas). Depois `notificarResponsaveis(salvo, novos, usuarioId)`
  onde `novos` = todos (evento novo).

- **`atualizarEvento`**: `sincronizarResponsaveis(evento, data.responsavelPessoaIds(), igrejaId, igreja)`:
  - `idsAntigos` = pessoas atuais (só as com `pessoa != null`).
  - `idsNovos` = `data.responsavelPessoaIds()` dedup.
  - Remove de `evento.getResponsaveis()` as linhas cuja pessoa saiu (orphanRemoval apaga).
  - Adiciona `EventoResponsavel` novo pra cada id em `idsNovos - idsAntigos`.
  - Retorna a lista dos **adicionados agora** (pra notificar só eles).
  - Linhas com `pessoa == null` (texto-fallback, LGPD) ficam intocadas — o form nunca as
    manda de volta, mas também nunca as remove.

- **`notificarResponsaveis(evento, adicionados, usuarioIdAtor)`**: para cada
  `EventoResponsavel` adicionado com `pessoa != null`, acha o `Usuario` e notifica
  **"Você foi definido como responsável pelo evento \"X\"."** (texto igual ao de hoje),
  pulando o ator. Substitui `notificarNovoResponsavel`.

- **Séries** (edição de série propaga pras ocorrências AGENDADAS, ~linha 416): em vez de
  `ocorrencia.setResponsavel(editado.getResponsavel())`, **espelha a lista**: limpa
  `ocorrencia.getResponsaveis()` e recria `EventoResponsavel` a partir de
  `editado.getResponsaveis()` (copiando `pessoa`/`nomeTexto`, apontando pra `ocorrencia`).
  Notificação em massa das ocorrências fica como está (não notifica responsável por
  ocorrência — só o evento editado direto).

- **Detecção "mudou responsável" pra notificar** (`atualizarEvento`): não precisa mais do
  `Objects.equals` de id único — `sincronizarResponsaveis` já devolve exatamente quem
  entrou. Notifica essa lista.

### 5. Backend — repositório / LGPD

Novo `EventoResponsavelRepository`:

```java
@Modifying(clearAutomatically = true)
@Query(value = """
    UPDATE evento_responsavel
       SET pessoa_id = NULL, nome_texto = :nome
     WHERE pessoa_id = :pessoaId
    """, nativeQuery = true)
int desvincularPessoa(@Param("pessoaId") UUID pessoaId, @Param("nome") String nome);
```

`EventoRepository.desvincularResponsavel` **removido**; as 2 chamadas em `PessoaService`
(arquivar linha 298, purge linha 361) passam a chamar
`eventoResponsavelRepository.desvincularPessoa(pessoaId, nome)`.

> Cuidado com o UNIQUE parcial: `SET pessoa_id = NULL` em várias linhas do mesmo evento não
> viola (o índice é `WHERE pessoa_id IS NOT NULL`). Se a mesma pessoa fosse responsável de
> N eventos, cada linha vira uma linha de texto independente — ok.

### 6. Frontend

**`SeletorResponsavel.tsx`** — de único pra múltiplo:

- Props: `valores: { id: string; nome: string }[]` (em vez de `valor` + `nomeInicial`);
  `onChange: (lista: { id: string; nome: string }[]) => void`.
- Estado interno: a busca continua igual. Ao clicar num resultado,
  `onChange([...valores, { id: p.id, nome: p.nome }])` e limpa a busca. Resultado já
  escolhido não aparece / aparece desabilitado.
- Renderiza a lista de chips (cada um com X que chama `onChange(valores.filter(...))`).
  Reusa o visual do chip atual. `<Transicao modo="escala">` por chip que entra.
- Label: "RESPONSÁVEIS (opcional)".

**`EventoForm.tsx`**: `watch('responsavelPessoaIds')` → array; monta
`valores` cruzando os ids com `responsaveisIniciais` (prop nova, `{id,nome}[]`) +
o que o próprio seletor já sabe (mantém um mapa id→nome local no seletor). `onChange` →
`setValue('responsavelPessoaIds', lista.map(v => v.id), { shouldDirty: true })`.

> Nome pra exibir: o seletor guarda `{id,nome}` internamente conforme a pessoa escolhe; na
> edição, `responsaveisIniciais` (vindo de `eventoInicial.responsaveis`) alimenta os nomes
> dos que já estavam. Ids sem nome conhecido (não deve acontecer) mostram "Responsável".

**`useEventoForm.ts`**:
- default: `responsavelPessoaIds: []`.
- reidrata: `responsavelPessoaIds: (eventoInicial.responsaveis ?? []).filter(r => r.id).map(r => r.id!)`
  (ignora os texto-fallback com `id: null` — não são editáveis).
- payload: `responsavelPessoaIds: data.responsavelPessoaIds ?? []`.
- expõe `responsaveisIniciais = (eventoInicial?.responsaveis ?? []).filter(r => r.id) as {id:string;nome:string}[]`.

**`validators.ts`**: `responsavelPessoaIds: z.array(z.string()).default([])`.

**`evento.type.ts`**:
- `EventoResponse.responsavel` → `responsaveis: EventoPessoaResumo[]`.
- `EventoRequest.responsavelPessoaId?` → `responsavelPessoaIds?: string[]`.

**`DrawerDetalheEvento.tsx`** (~linha 192):
```tsx
{evento.responsaveis.length > 0 && (
  <div className={styles.infoItem}>
    <span className={styles.infoIcone}><UserCircle size={20} /></span>
    <div>
      <p className={styles.infoLabel}>{evento.responsaveis.length > 1 ? 'Responsáveis' : 'Responsável'}</p>
      <p className={styles.infoValor}>{evento.responsaveis.map(r => r.nome).join(', ')}</p>
    </div>
  </div>
)}
```

## Fluxo ponta a ponta (adicionar 2º responsável a um evento existente)

1. Editar evento → seção "Para quem é" (ou onde o `SeletorResponsavel` está) → já mostra o
   chip do responsável atual (de `responsaveisIniciais`).
2. Buscar a 2ª pessoa → clicar → 2º chip aparece. `responsavelPessoaIds` = `[id1, id2]`.
3. Salvar → `PUT /eventos/{id}` com `responsavelPessoaIds: [id1, id2]`.
4. `EventoService.atualizarEvento` → `sincronizarResponsaveis`: `id1` já existe (mantém),
   `id2` novo → cria `EventoResponsavel`. Devolve `[linha de id2]`.
5. `notificarResponsaveis` → notifica só a pessoa `id2` ("Você foi definido como
   responsável…"), a menos que seja o próprio ator.
6. `EventoResponse.responsaveis` = `[{id1,nome1}, {id2,nome2}]`.
7. Drawer mostra "Responsáveis: Nome 1, Nome 2".

## Erros e validação

| Caso | Comportamento |
|---|---|
| `responsavelPessoaIds` com id de outra igreja | `ResourceNotFoundException` (404), igual hoje |
| id repetido na lista | Dedup silencioso no serviço |
| lista vazia / `null` | Evento sem responsável (válido) |
| pessoa arquivada durante a edição | `desvincularPessoa` já converteu pra texto; o form manda só os ids restantes; o texto-fallback fica |
| remover o único responsável | `sincronizarResponsaveis` apaga a linha (orphanRemoval); nenhuma notificação |

## Testes

**Back (`EventoServiceTest`, Mockito puro):**
- `cadastrarEvento` com 2 ids → grava 2 `EventoResponsavel`, notifica os 2 (menos o ator).
- `atualizarEvento` adiciona 1 → notifica só o novo; mantém o antigo.
- `atualizarEvento` remove 1 → linha some, sem notificação.
- id duplicado na lista → grava 1 só.
- id de outra igreja → `ResourceNotFoundException`.
- `EventoResponse.responsaveis` inclui o texto-fallback (`pessoa == null, nomeTexto = "X"`).

**Back (`@DataJpaTest` + `PostgresTestContainerSupport`):**
- V37 aplicou: `evento` não tem mais `responsavel_pessoa_id`/`responsavel_texto`;
  `evento_responsavel` existe; um evento seedado com responsável antes… (a migração roda
  sozinha; testar que a tabela existe e o CHECK/UNIQUE funcionam).
- `EventoResponsavelRepository.desvincularPessoa` seta `pessoa_id` null e grava `nome_texto`.

**Front:** sem harness — checklist manual: adicionar/remover chips, salvar, reabrir na
edição (chips certos), remover todos, responsável arquivado aparece como texto no drawer,
mobile (chips quebram linha sem overflow).

## Sequência de implementação (pedaços testáveis)

1. **Back — schema + entidade + repositório + LGPD** (V37, `EventoResponsavel`,
   `Evento.responsaveis`, `EventoResponsavelRepository`, trocar as 2 chamadas em
   `PessoaService`, remover `EventoRepository.desvincularResponsavel`). Compila, migração
   testada, `PessoaServiceTest` (se existir) verde.
2. **Back — DTO + serviço + response** (`EventoRequest.responsavelPessoaIds`,
   `resolverResponsaveis`/`sincronizarResponsaveis`/`notificarResponsaveis`, séries,
   `EventoResponse.responsaveis`) + testes de serviço. Sem front.
3. **Front — tipos + validators + `useEventoForm`** (contrato novo, sem quebrar build).
4. **Front — `SeletorResponsavel` múltiplo + `EventoForm` + `DrawerDetalheEvento`**.
5. **Verificação** — suíte back, `tsc`/`build` front, teste manual, atualizar diagrama ER
   no `CLAUDE.md` (remove `responsavel_pessoa_id`/`responsavel_texto` de `EVENTO`, adiciona
   `EVENTO_RESPONSAVEL`, estado V37), PR.
