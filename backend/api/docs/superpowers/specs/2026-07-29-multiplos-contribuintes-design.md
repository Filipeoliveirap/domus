# Múltiplos contribuintes por movimentação financeira — Design

> Pedido confirmado pelo autor em 2026-07-22 (ver backlog). Brainstorm fechado em
> 2026-07-29.

## Contexto e motivação

Hoje `MovimentacaoFinanceira` tem no máximo **um** `pessoa_id` opcional (o "atribuinte" —
contribuinte numa entrada, beneficiário numa saída). Na prática, muitas movimentações são
feitas por mais de uma pessoa ao mesmo tempo: oferta em conjunto, ajuda a um membro
dividida entre dois doadores, cesta básica custeada por duas famílias, oferta pra
missionário arrecadada entre vários. Hoje isso ou vira uma pessoa só recebendo o crédito,
ou uma movimentação por pessoa (perdendo a noção de "isso foi uma coisa só, dividida").

A regra de ouro do modelo: **cada contribuinte tem seu próprio valor explícito (rateio)**,
nunca o valor cheio repetido por pessoa — porque a soma dos relatórios tem que continuar
batendo. Se dois contribuintes de uma entrada de R$100 aparecessem cada um com R$100, a
soma agregada do período dobraria silenciosamente.

## Modelo de dados

Nova tabela `movimentacao_contribuinte` (N-para-N entre movimentação e pessoa):

```sql
CREATE TABLE movimentacao_contribuinte (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  movimentacao_id UUID NOT NULL REFERENCES movimentacao_financeira(id) ON DELETE CASCADE,
  pessoa_id       UUID NOT NULL REFERENCES pessoa(id),
  valor           NUMERIC(15,2) NOT NULL CHECK (valor > 0),
  UNIQUE (movimentacao_id, pessoa_id)
);
```

- `pessoa_id` sai da coluna de `movimentacao_financeira`. Vira **sempre** uma lista, mesmo
  quando só tem 1 contribuinte — nada de dois caminhos de código coexistindo pro caso
  comum (1 pessoa) e pro caso raro (N pessoas).
- Lista vazia = movimentação sem contribuinte identificado, o "anônimo" de hoje. Mesmo
  comportamento, só que representado por zero linhas em vez de `pessoa_id IS NULL`.
- `ON DELETE CASCADE`: a linha de contribuinte não tem vida própria fora da movimentação.
- Vale pros dois tipos (ENTRADA e SAÍDA) — uma saída também pode ser dividida entre duas
  pessoas (ex.: reembolso de viagem a dois, ajuda partilhada entre duas famílias).

### Migração (V15)

```sql
INSERT INTO movimentacao_contribuinte (movimentacao_id, pessoa_id, valor)
SELECT id, pessoa_id, valor
FROM movimentacao_financeira
WHERE pessoa_id IS NOT NULL;

ALTER TABLE movimentacao_financeira DROP COLUMN pessoa_id;
```

Migra **inclusive linhas já arquivadas** (`deleted_at IS NOT NULL`) — é histórico, não pode
se perder mesmo que hoje esteja fora da visão padrão.

## API — request/response

```java
public record ContribuinteDTO(
    @NotNull UUID pessoaId,
    @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal valor
) {}

public record MovimentacaoRequestDTO(
    TipoMovimentacao tipo,
    BigDecimal valor,                     // continua o total da movimentação
    UUID categoriaId,
    LocalDate dataMovimentacao,
    List<ContribuinteDTO> contribuintes,  // substitui pessoaId; pode vir vazia
    String descricao
) {}
```

**Validação no service** (`MovimentacaoFinanceiraService`), lançando `BusinessException`:
- Se `contribuintes` não vier vazia, `sum(contribuintes.valor)` deve ser **exatamente**
  igual a `valor` — código `VALOR_CONTRIBUINTES_DIVERGENTE`. Sem essa trava, o relatório
  geral e o relatório por contribuinte divergem silenciosamente.
- Nenhuma pessoa pode se repetir na lista de contribuintes de uma mesma movimentação —
  código `CONTRIBUINTE_DUPLICADO` (a `UNIQUE` do banco já barraria no INSERT, mas a
  mensagem de validação do service é mais clara que uma exceção de constraint).

`MovimentacaoResponse` troca `pessoaId`/`pessoaNome` por:

```java
public record ContribuinteResponse(UUID pessoaId, String pessoaNome, BigDecimal valor) {}
// dentro de MovimentacaoResponse:
List<ContribuinteResponse> contribuintes
```

## Relatório novo — por contribuinte

`GET /relatorios/por-contribuinte` — mesmo padrão de `/relatorios/por-categoria` (mesma
autorização `exigirFinanceiro()`, mesmo escopo de família via `FamiliaIgrejaService`):

```java
public record ContribuinteBreakdownResponse(
    UUID pessoaId, String pessoaNome, TipoMovimentacao tipo,
    BigDecimal total, BigDecimal percentual
) {}
```

Soma `movimentacao_contribuinte.valor` agrupado por pessoa + tipo no período, com o mesmo
cálculo de percentual (fatia sobre o total do tipo) que `por-categoria` já faz.

## Filtro por vínculo (MEMBRO/CONGREGANTE) nos relatórios existentes

Hoje `resumo`, `por-categoria`, `evolucao-mensal` e `maior-lancamento` filtram por
`?vinculo=` usando `LEFT JOIN m.pessoa p ... p.vinculo = :vinculo` — fazia sentido quando
só existia 1 pessoa por movimentação. Com múltiplos contribuintes de vínculos diferentes
numa mesma movimentação, a regra muda para **fatiar por contribuinte**:

- **`vinculo` ausente** (comportamento padrão, "todos"): soma o **valor total da
  movimentação**, sem fatiar — é o total de sempre, sem mudança de comportamento pra quem
  não usa o filtro.
- **`vinculo=MEMBRO`**: soma só a fatia dos contribuintes que são MEMBRO daquela
  movimentação (via `movimentacao_contribuinte` + `pessoa.vinculo`), não o valor cheio.
- **`vinculo=CONGREGANTE`**: idem, só a fatia dos contribuintes CONGREGANTE.

Implementação: os 4 métodos de `RelatorioRepository` ganham um segundo caminho de query
(usado só quando `vinculo != null`) que agrega por `movimentacao_contribuinte.valor`
filtrado por `pessoa.vinculo`, em vez de `m.valor`. O caminho sem filtro (a maioria do
uso hoje) continua exatamente como está — nenhuma mudança de comportamento pra quem não
filtra por vínculo.

Movimentações sem nenhum contribuinte (lista vazia) nunca aparecem quando `vinculo` está
preenchido — mesmo comportamento de hoje (equivalente ao `LEFT JOIN` com `pessoa IS NULL`
não batendo o filtro).

## Filtro na listagem de movimentações

`GET /movimentacoes` ganha `@RequestParam(required = false) UUID pessoaId` — filtra
movimentações onde a pessoa aparece como um dos contribuintes (via `EXISTS` contra
`movimentacao_contribuinte`). Combina com os filtros que já existem (tipo, categoria,
data, busca textual `q`).

## Busca (Elasticsearch)

`MovimentacaoDocument.pessoaNome` (campo de texto único) vira `pessoaNomes` (lista de
texto) — pra continuar encontrando a movimentação buscando pelo nome de qualquer um dos
contribuintes. Documentos existentes no índice precisam de reindexação em massa (já
existe `POST /admin/reindexacao` pra isso — ver memória `reindexacao-es-endpoint-admin`).

## Frontend

- **Formulário:** o campo único "Atribuinte" vira uma lista de linhas (seletor de pessoa +
  valor), com botão "+ Adicionar contribuinte". Quando a lista tem exatamente 2 linhas,
  aparece o atalho **"Dividir 50/50"**, que preenche os dois valores automaticamente
  (sobra de centavo ímpar fica com o primeiro). Soma corrente exibida ao lado do valor
  total da movimentação; botão "Salvar" bloqueado se a soma não bater — replica no front
  a mesma trava do backend, pra feedback imediato sem round-trip.
- **Listagem:** a coluna que hoje mostra 1 nome de pessoa passa a mostrar "Nome" (1
  contribuinte) ou "Nome +N" (múltiplos, expansível).
- **Detalhe da movimentação:** lista completa de contribuinte + valor de cada um.
- **Filtro:** mesmo seletor de pessoa de sempre na barra de filtros, agora batendo contra
  o novo parâmetro `pessoaId` do backend.
- **Novo bloco de relatório "Por contribuinte":** mesmo padrão visual do bloco "Por
  categoria" já existente, alimentado pelo novo endpoint.
- Responsividade mobile obrigatória (linhas de contribuinte empilham em telas estreitas,
  como já é padrão no projeto).

## Fora de escopo desta entrega

- Percentual/proporção como alternativa a valor absoluto no rateio (ex.: "30% pra fulano,
  70% pra beltrano") — o valor explícito em R$ já cobre o caso confirmado; se um dia
  precisar de percentual, é uma conversão simples na entrada do formulário, não uma
  mudança de modelo.
- Contribuinte "pessoa externa" (sem cadastro na igreja) — hoje `pessoa_id` já exige uma
  pessoa cadastrada; múltiplos contribuintes segue a mesma regra.
