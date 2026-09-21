# Rótulo self-service por igreja — Design

> Item 8 do `docs/BACKLOG-PRE-VENDA.md`. Cada igreja pode renomear os termos
> "Ministério", "Congregação" (hoje rotulado "Unidade" na tela) e "Célula" pra
> algo que faça sentido na cultura dela (ex.: "Departamento", "Rede",
> "Pequeno Grupo", "GC"). É rótulo de tela — o domínio, os tipos e os nomes
> internos de código continuam com o termo técnico atual.

## Contexto

Hoje o único mecanismo de rótulo configurável é `frontend/src/lib/rotulosMinisterio.ts`
— duas constantes soltas (`ROTULO_MINISTERIO`/`ROTULO_MINISTERIO_PLURAL`, hardcoded
"Rede"/"Redes"), sem gênero gramatical. "Congregação"→"Unidade" é rótulo de tela sem
nenhum ponto único — está solto em pelo menos 4 arquivos. "Célula" não tem nenhum
mecanismo de rótulo, é hardcoded em toda a superfície do módulo (tipos, hooks, toasts,
sidebar, busca global).

**Escopo desta entrega:** os três módulos (Ministério, Congregação, Célula) ficam
configuráveis. A configuração é **só front** — não precisa refletir em texto gerado
no backend (notificações, e-mails continuam com o termo técnico fixo). A tela de edição
(self-service de verdade, não só o mecanismo) entra nesta mesma entrega, como uma nova
seção em `/configuracoes/igreja`.

## Arquitetura

Três pares nome+gênero novos como colunas em `igreja` (nuláveis — `NULL` = usa o
padrão atual do sistema). O front consome via um hook central `useRotulos()`, que lê
do `/auth/me` (já carregado uma vez por sessão, todo componente já depende dele) e
resolve pro padrão quando a igreja não customizou. Um helper de concordância central
(`concordar(rotulo, forma)`) substitui toda concordância hoje hardcoded em textos como
"Nova Rede"/"Novo Departamento".

Por que colunas em `igreja` e não uma tabela separada: é o padrão usado por sistemas
multi-tenant maduros (Salesforce, Notion, Linear) pra terminologia customizável —
vive junto do registro do tenant, porque é baixo volume (3 pares de string, sem
histórico, sem lifecycle próprio) e sempre lido junto do resto dos dados da igreja.
Tabela separada só compensaria com dezenas de configs ou necessidade de versionamento
— não é o caso.

## Modelo de dados

Migration nova `V25__rotulos_customizados_igreja.sql`, adicionando em `igreja`:

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

Todas nuláveis, sem default no banco — o valor-padrão (Ministério/Ministérios-masc,
Unidade/Unidades-fem, Célula/Células-fem) vive só no código do front, não duplicado
no schema. `_genero` é `VARCHAR` com `CHECK`, não teve necessidade de tabela de enum
separada (mesmo padrão já usado em outras colunas de enum textual do projeto).

Um trio (`_singular`, `_plural`, `_genero`) é atômico: ou os três estão preenchidos,
ou os três são `NULL` (validado no backend, não no banco — evitar `CHECK` cruzando
3 colunas por simplicidade).

## Backend

- `Igreja.java`: 9 campos novos (`ministerioNomeSingular`, ..., `GeneroGramatical`
  enum `MASCULINO`/`FEMININO` reaproveitado nos 3 blocos).
- `GeneroGramatical.java` (novo enum, no módulo `igreja`).
- DTO de atualização da igreja (o mesmo já usado hoje por `PUT /igreja`) ganha um
  objeto aninhado opcional `RotulosRequest { ministerio, congregacao, celula }`, cada
  bloco com `{ nomeSingular, nomePlural, genero }`, todos opcionais — enviar `null`
  no bloco reseta pro padrão (limpa as 3 colunas daquele módulo).
- Validação no service: se qualquer um dos 3 campos de um bloco vier preenchido, os
  outros dois do mesmo bloco são obrigatórios (400 se só parcialmente preenchido).
- `/auth/me` (endpoint de sessão) ganha o mesmo objeto `rotulos` no response, com
  `null` por bloco quando a igreja não customizou aquele módulo — o front decide o
  fallback, o backend não resolve o padrão.

## Frontend

- `frontend/src/lib/rotulos/concordancia.ts` (novo): mapa de formas por gênero —
  `novo/nova`, `nenhum/nenhuma`, `o/a`, `um/uma` — cresce sob demanda (YAGNI; começa
  só com as formas realmente usadas nos textos hoje existentes).
- `frontend/src/lib/rotulos/useRotulos.ts` (novo): lê o objeto `rotulos` já presente
  no contexto de auth/sessão (o mesmo que hoje fornece `role`/`igrejaId`), aplica
  fallback pros 3 módulos, devolve `{ ministerio, congregacao, celula }`, cada um
  `{ singular, plural, genero }`.
- `frontend/src/lib/rotulosMinisterio.ts` é **deletado**. Os ~10 arquivos que hoje
  importam `ROTULO_MINISTERIO`/`ROTULO_MINISTERIO_PLURAL` passam a chamar
  `useRotulos().ministerio.singular`/`.plural`.
- Congregação: as strings soltas hoje em `igrejas-vinculadas/page.tsx` e
  `financeiro/relatorios/**` (4 arquivos, listados na exploração) passam a usar
  `useRotulos().congregacao`.
- Célula: maior superfície — não existe hook hoje. Pontos de rótulo visível ao
  usuário (Sidebar, BuscaGlobal, toasts do `useCelulaForm`, títulos de página) passam
  a usar `useRotulos().celula`. **Nomes técnicos de código continuam "Célula/celula"**
  (rotas, tipos, `CelulaResponse`, `podeGerenciarCelulas`) — só o texto visível muda,
  mesmo padrão já usado em Congregação→Unidade (registrado em memória do projeto).

## Tela de configuração

Nova seção "Nomenclatura" em `/configuracoes/igreja` (só `ADMIN_IGREJA`, mesmo padrão
de permissão da tela hoje). Três blocos — Ministério, Congregação, Célula — cada um
com campo Singular, campo Plural e um seletor Masculino/Feminino, mais uma área de
**preview ao vivo** mostrando como o rótulo aparece no menu lateral (ex.: mockup
gerado no Stitch: sidebar de exemplo atualizando em tempo real ao digitar). Preview
é genuinamente reativo ao `onChange`, nunca campo `disabled` — regra de UX do projeto.

Salvar chama o mesmo `PUT /igreja` já usado pra editar dados da igreja, estendido com
o objeto `rotulos`. Um botão "Restaurar padrão" por bloco limpa os 3 campos daquele
módulo (envia os 3 como `null`).

Cache: `invalidarCache(qc, 'igreja')` já existe e invalida `['igreja']`, mas
`useRotulos()` lê do objeto de sessão/`/auth/me`, não de uma query `['igreja']` —
precisa também invalidar/refazer o fetch de `/auth/me` (ou a query que o alimenta)
depois de salvar, senão o rótulo customizado só aparece após reload.

## Testes

- Backend: `IgrejaServiceTest` (Mockito puro, sem contexto Spring) cobrindo salvar
  os 9 campos novos, validação de trio parcial (400), e retorno correto (`null` por
  bloco não customizado) — é passthrough de DTO, sem regra de negócio complexa.
- Frontend: sem suíte automatizada, validação manual no navegador — dívida técnica já
  conhecida do projeto (documentada no `BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`).

## Fora de escopo

- Rótulo refletido em texto gerado no backend (notificações, e-mails) — continuam
  com o termo técnico fixo ("Rede"/"Ministério" hardcoded em `MinisterioService`,
  `BuscaMinisterioService`).
- Pluralização automática — singular e plural são sempre digitados pela igreja.
- Qualquer módulo além dos três (Ministério, Congregação, Célula).
