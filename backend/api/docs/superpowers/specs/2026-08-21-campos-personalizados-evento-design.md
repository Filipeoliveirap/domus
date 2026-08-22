# Campos personalizados de evento — design

> Item 7 do `docs/BACKLOG-PRE-VENDA.md` (Spec D), dividido em duas specs no brainstorm de
> 2026-08-21. Esta é a **Spec 1 — campos personalizados**. A Spec 2 (formulário público de
> inscrição, sem login) fica pra depois, como item novo próprio.

## Motivação

Alguns eventos precisam de dado que não existe em `Pessoa` nem faz sentido virar coluna
permanente (tamanho de camiseta, restrição alimentar, "como soube do evento"). Hoje não tem
onde guardar isso — vira anotação solta fora do sistema. O objetivo é deixar o admin/líder
montar um formulário extra por evento (rótulo, tipo de campo, obrigatório ou não) e as
respostas ficarem vinculadas à inscrição, visíveis na lista de inscritos.

## Escopo desta spec

Cobre **só o uso interno**: quem responde é sempre uma `Pessoa` já identificada no sistema —
escolhida pelo admin/líder ao inscrever (inclusive em lote) ou a própria pessoa se
auto-inscrevendo logada. Não existe ambiguidade de identidade aqui, e os campos personalizados
nunca duplicam dado que já more em `Pessoa` (são pergunta nova, específica do evento).

**Fica pra Spec 2** (não desenhado aqui, só citado pra não perder o fio): formulário público
sem login, campos fixos de identidade (nome/e-mail/telefone/data de nascimento) pra quem não
tem cadastro, e a lógica de "essa pessoa já é conhecida do sistema? não pergunta de novo" —
essa última reaproveitando o mesmo padrão que o módulo de Célula já usa pra visitante
(perguntar se é visitante cadastrado ou alguém de fora). A Spec 2 também vai precisar decidir
com cuidado como buscar um cadastro existente sem vazar lista de nomes/telefones pra qualquer
um com o link.

## Princípio de design

Mesma separação de responsabilidade que `EventoSerie`/`Evento` já usa: a **definição** do
campo (o quê se pergunta) vive numa tabela; a **resposta** (o que cada um respondeu) vive em
outra. Cada resposta guarda o valor como **texto snapshot**, nunca uma referência a uma opção
específica — assim, editar ou apagar uma opção depois nunca quebra resposta antiga (decisão
explícita do brainstorm: editar campo é livre, sem trava, mesmo com gente já tendo respondido).

## Modelo de dados (migration `V23__campo_personalizado_evento.sql`)

```sql
CREATE TABLE campo_personalizado_evento (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id           UUID NOT NULL REFERENCES igreja(id),
    evento_id           UUID NOT NULL REFERENCES evento(id),
    label               VARCHAR(120) NOT NULL,
    placeholder         VARCHAR(160),                 -- texto de ajuda, opcional
    tipo                VARCHAR(20) NOT NULL,          -- TEXTO_CURTO | OPCAO_UNICA | MULTIPLA_ESCOLHA | SIM_NAO
    opcoes              TEXT,                          -- uma opção por linha; só p/ OPCAO_UNICA e MULTIPLA_ESCOLHA
    obrigatorio         BOOLEAN NOT NULL DEFAULT FALSE,
    visivel_ao_publico  BOOLEAN NOT NULL DEFAULT TRUE,  -- groundwork pra Spec 2; sem efeito nenhum aqui
    ordem               INTEGER NOT NULL DEFAULT 0,
    deleted_at          TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE INDEX idx_campo_personalizado_evento ON campo_personalizado_evento (evento_id);

CREATE TABLE resposta_campo_personalizado (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campo_id        UUID NOT NULL REFERENCES campo_personalizado_evento(id),
    inscricao_id    UUID NOT NULL REFERENCES inscricao_evento(id),
    acompanhante_id UUID REFERENCES acompanhante_inscricao(id),  -- NULL = resposta do titular
    valor           TEXT NOT NULL,   -- snapshot; múltipla escolha serializa como lista separada por " | "
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP
);

CREATE INDEX idx_resposta_campo_inscricao ON resposta_campo_personalizado (inscricao_id);

-- UNIQUE simples não serve aqui: no Postgres, NULL nunca é igual a NULL numa constraint
-- UNIQUE, então duas respostas do titular (acompanhante_id sempre NULL) não seriam
-- bloqueadas. Dois índices únicos parciais resolvem: um pro titular (acompanhante_id nulo),
-- outro por acompanhante (nunca nulo).
CREATE UNIQUE INDEX idx_resposta_titular_unica
    ON resposta_campo_personalizado (campo_id, inscricao_id)
    WHERE acompanhante_id IS NULL;

CREATE UNIQUE INDEX idx_resposta_acompanhante_unica
    ON resposta_campo_personalizado (campo_id, acompanhante_id)
    WHERE acompanhante_id IS NOT NULL;
```

- `igreja_id` em `campo_personalizado_evento` é isolamento multi-tenant direto (nunca via
  join com `evento`), mesmo padrão do resto do schema — sempre extraído do JWT.
- `opcoes` como texto livre (uma por linha) em vez de tabela `campo_opcao` separada: evita FK
  que a decisão "editar livre, sem trava" já descartou, e casa com volume baixo (não são
  centenas de opções por campo).
- Os dois índices únicos parciais garantem uma resposta só por campo por pessoa (titular ou
  acompanhante específico) — responder de novo é `UPDATE`, não `INSERT`.
- Sem soft delete em `resposta_campo_personalizado`: resposta não se arquiva sozinha, só some
  junto com a inscrição/acompanhante (cascade natural — ver abaixo).
- `ON DELETE`: como `Evento`, `InscricaoEvento` e `AcompanhanteInscricao` usam soft delete
  (nunca `DELETE` de verdade em uso normal), não precisamos de `ON DELETE CASCADE` aqui pro
  dia a dia. Exclusão definitiva (LGPD) já limpa registros filhos manualmente nos services
  existentes — este módulo entra na mesma limpeza.

## Enum `TipoCampoPersonalizado`

```java
public enum TipoCampoPersonalizado {
    TEXTO_CURTO,
    OPCAO_UNICA,
    MULTIPLA_ESCOLHA,
    SIM_NAO
}
```

`opcoes` é obrigatório (não-vazio) quando `tipo` é `OPCAO_UNICA` ou `MULTIPLA_ESCOLHA`;
ignorado (deve vir `null`) nos outros dois. Validação no `@AssertTrue` do request DTO, mesmo
padrão de `RecorrenciaRequest`.

## Backend

- **`CampoPersonalizadoEvento`** (entidade) + **`CampoPersonalizadoEventoRepository`** —
  `findByEventoIdAndIgrejaIdOrderByOrdem`, soft delete via `@SQLDelete`/`@SQLRestriction`
  (padrão do projeto).
- **`RespostaCampoPersonalizado`** (entidade) + **`RespostaCampoPersonalizadoRepository`** —
  `findByInscricaoId` (traz titular + todos os acompanhantes de uma vez, filtra em memória por
  `acompanhanteId`).
- **`CampoPersonalizadoService`**:
  - `listar(eventoId, igrejaId)` — retorna a lista ordenada, pra tela de config e pra tela de
    resposta.
  - `salvar(eventoId, igrejaId, List<CampoPersonalizadoRequest>)` — substitui a lista inteira
    (diff simples: soft-deleta o que sumiu, atualiza o que tem id, cria o que não tem). Mais
    simples que PATCH campo a campo e casa com "editar livre".
  - `responder(inscricaoId, acompanhanteId ou null, List<RespostaRequest>, igrejaId, usuarioId)`
    — valida que todo campo `obrigatorio` do evento tem resposta não-vazia; upsert em
    `resposta_campo_personalizado` (via os índices únicos parciais acima).
  - `respostasPorInscricao(inscricaoId, igrejaId)` — pra lista de inscritos/relatório.
- **Autorização** (`Permissoes`, mesmo padrão de `podeGerenciarEventos`):
  - Configurar campos (`salvar`): só quem gerencia o evento (ADMIN/LÍDER).
  - Responder: o dono da inscrição (pessoa logada = `inscricao.pessoa`) **ou** quem gerencia o
    evento. Nunca um terceiro qualquer.
- **Endpoints** (`CampoPersonalizadoController`, novo):
  - `GET /eventos/{eventoId}/campos-personalizados` — lista (qualquer autenticado que veja o
    evento; front decide o que fazer com isso).
  - `PUT /eventos/{eventoId}/campos-personalizados` — substitui a lista (ADMIN/LÍDER).
  - `GET /inscricoes/{inscricaoId}/respostas` — respostas do titular + acompanhantes.
  - `PUT /inscricoes/{inscricaoId}/respostas` — responde pelo titular (`acompanhanteId` no
    body indica se é resposta de um acompanhante específico).
- **Não mexe em `InscricaoService.inscrever`/`inscreverPessoas`**: a obrigatoriedade nunca é
  checada lá — é por isso que o lote nunca trava. Campo obrigatório sem resposta fica como
  pendência visível (ver Frontend), nunca bloqueia a inscrição em si.

## Frontend

- **Configuração** — dentro de `EventoForm.tsx`, seção "Inscrições" (onde já mora "Requer
  inscrição prévia"), bloco novo "Campos personalizados": lista editável (rótulo, tipo,
  placeholder, opções quando aplicável, obrigatório, "visível ao público" desabilitado/cinza
  por enquanto — sem efeito, só groundwork visual) com um painel de prévia ao vivo ao lado,
  mostrando como o campo vai aparecer pra quem responde.
- **Responder** — a tela de auto-inscrição e "Minha inscrição" ganham os campos pendentes como
  parte do formulário; admin/líder também enxerga e pode preencher em nome de outra pessoa
  (reaproveita a mesma tela/endpoint, trocando quem é o ator).
- **Validação** — Zod espelha a obrigatoriedade que o backend já valida (`.refine()` por
  campo obrigatório sem resposta), mesmo padrão dos campos de recorrência.
- **Lista de inscritos** (`DrawerDetalheEvento` / relatório) ganha as respostas por pessoa,
  inclusive indicando quando alguém ainda não respondeu um campo obrigatório (pendência visual,
  não bloqueio).

## Fora do escopo desta entrega

- Formulário público sem login, campos fixos de identidade, e a lógica de reaproveitar
  cadastro existente — tudo isso é a Spec 2, brainstormada à parte.
- Builder visual arrastar-e-soltar — reordenar é por número (`ordem`), sem drag-and-drop.
- Campo reutilizável entre eventos (template) — cada campo pertence a um evento só; se dois
  eventos querem "tamanho de camiseta", cada um cadastra o seu.
- Texto longo (textarea) como tipo de campo — só texto curto, opção única, múltipla escolha e
  sim/não nesta entrega.

## Decisões já tomadas (não rediscutir)

- Editar/apagar campo (inclusive opções) depois de já ter resposta: **livre, sem trava**.
  Resposta antiga guarda snapshot em texto, nunca quebra.
- Campo obrigatório nunca bloqueia inscrição em lote — só fica pendente, preenchível depois
  pela própria pessoa ou por quem gerencia.
- Campos personalizados valem tanto pro titular quanto pra cada acompanhante.
- `visivel_ao_publico` entra no schema já nesta spec (groundwork), mas sem nenhum efeito até a
  Spec 2 existir.
