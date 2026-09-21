# Balancete anual — Design

> Brainstorm fechado em 2026-07-31.

## Contexto e motivação

O módulo de relatório financeiro (`RelatorioController`/`RelatorioService`) hoje tem
`/resumo`, `/por-categoria`, `/evolucao-mensal`, `/maior-lancamento` e `/por-contribuinte`,
todos operando sobre um período arbitrário. Nenhum deles é o relatório clássico de
prestação de contas de tesouraria: uma tabela ano inteiro, categoria por categoria, mês a
mês, com **saldo acumulado** (o saldo carregado de um mês pro outro, incluindo anos
anteriores) — o "balancete anual".

Pedido do autor: já nasce com o dado retornado pronto para consumo (não a geração do
arquivo em si — exportação em PDF/Excel fica para depois, fora de escopo desta entrega) e
com visão consolidada entre igreja sede e congregações, já que a família de igrejas
(`FamiliaIgrejaService`) e o consolidado financeiro (`ConsolidadoService`) já existem no
projeto.

## Endpoints

Segue o padrão já existente no projeto: relatório da própria igreja separado do
relatório de família (como `RelatorioController` vs. `ConsolidadoController`), porque o
formato de resposta dos dois é bem diferente.

- `GET /relatorios/balancete-anual?ano=2026`
  Balancete da igreja do usuário logado. Escopo pelo JWT (`igreja_id`), como os demais
  relatórios. `ano` é obrigatório — sem valor default, para forçar escolha explícita e
  evitar confusão sobre "qual ano estou vendo".
- `GET /relatorios/balancete-anual/congregacoes?ano=2026`
  Só a sede. Retorna o balancete de cada igreja da família **e** um bloco consolidado
  (soma de todas). Mesma checagem do `ConsolidadoController`: `Permissoes.podeVerFinanceiro`
  (`SO_ADMIN` ou capacidade `TESOUREIRO`) + `FamiliaIgrejaService.ehFilha(igrejaId)` lança
  `AccessDeniedException` se a igreja solicitante for congregação (só a sede vê o
  consolidado da família).

Meses futuros dentro do ano corrente (ex.: pedir `ano=2026` em julho/2026) aparecem
normalmente na resposta, só que zerados — não são omitidos.

## Cálculo

### Saldo de abertura

Soma de todas as movimentações (`ENTRADA` soma, `SAIDA` subtrai) com
`data_movimentacao < 01/01/{ano}`. Uma query agregada, calculada antes de montar a
matriz — é o "saldo trazido" dos anos anteriores, indispensável para o balancete bater
com a realidade financeira da igreja (nenhum ano começa do zero).

### Matriz categoria × mês

Uma query agrupando por `categoria_id`, `tipo` e `EXTRACT(MONTH FROM data_movimentacao)`
dentro do ano pedido, reaproveitando o padrão já usado em `evolucao-mensal`.

### Quais categorias entram

- Categoria **ativa** da igreja: sempre entra, mesmo com todos os meses zerados. Decisão
  deliberada — categoria ausente confundiria o usuário ("cadê a categoria X?"); categoria
  zerada comunica "não teve nada aqui".
- Categoria **arquivada** (`deleted_at` preenchido): só entra se teve pelo menos uma
  movimentação no ano pedido — senão some. A linha vem com um campo `arquivada: true` no
  DTO, para o front mostrar um selo "Categoria arquivada" ao lado do nome (comunica por
  que uma categoria que não está mais em uso ainda aparece no relatório).

### Entradas vs. saídas na mesma categoria

Categoria do tipo `AMBOS` (pode receber lançamento de entrada ou de saída) aparece em
**duas linhas**, uma na seção "Entradas" e outra na seção "Saídas" — nunca numa linha só
misturando os dois. Motivo: misturar entrada e saída na mesma célula perde a direção do
dinheiro e quebra a conferência com o extrato bancário (um balancete contábil sempre
separa por direção, não por categoria isolada).

### Saldo do mês e saldo acumulado

- `Saldo do mês` = soma de entradas do mês − soma de saídas do mês.
- `Saldo acumulado` = saldo de abertura + soma corrida dos saldos mensais desde janeiro
  até o mês da coluna (inclusive).

## Consolidado entre igrejas

Cada igreja tem suas próprias linhas de `categoria_financeira` (isoladas por
`igreja_id`) — sede e congregação podem ter "Dízimos" cadastrado como registros
diferentes, com IDs diferentes. Para casar essas categorias na visão consolidada, usa-se
`unaccent(lower(nome))`: a mesma normalização que já garante a unicidade de
`categoria_financeira.nome` por igreja hoje. Não há necessidade de busca aproximada
(Elasticsearch): categorias já são nomes curtos e normalizados na criação, então
comparação exata pós-normalização é suficiente e evita depender de um sistema externo
(com atraso de sincronização via outbox) para uma operação simples de agrupamento no
banco.

Categoria que só existe numa das igrejas aparece sozinha na linha consolidada, com as
colunas das igrejas que não a têm cadastrada permanecendo zeradas.

### Resposta do endpoint de congregações

```java
public record BalanceteFamiliaResponseDTO(
    List<BalanceteIgrejaDTO> porIgreja,   // mesma estrutura do endpoint próprio, uma por igreja
    BalanceteResponseDTO consolidado       // categorias casadas por nome normalizado
) {}

public record BalanceteIgrejaDTO(
    UUID igrejaId,
    String nomeIgreja,
    boolean ehSede,
    BalanceteResponseDTO balancete
) {}
```

## Estrutura da resposta (balancete de uma igreja)

```java
public record BalanceteResponseDTO(
    int ano,
    BigDecimal saldoAbertura,
    List<LinhaCategoriaDTO> entradas,
    List<LinhaCategoriaDTO> saidas,
    List<BigDecimal> subtotalEntradasPorMes,  // 12 posições
    List<BigDecimal> subtotalSaidasPorMes,    // 12 posições
    List<BigDecimal> saldoDoMes,              // 12 posições
    List<BigDecimal> saldoAcumulado           // 12 posições
) {}

public record LinhaCategoriaDTO(
    UUID categoriaId,
    String nomeCategoria,
    boolean arquivada,
    List<BigDecimal> valoresPorMes,  // 12 posições, jan..dez
    BigDecimal totalAno
) {}
```

## Permissões

- `GET /relatorios/balancete-anual`: qualquer role que já pode ver financeiro
  (`Permissoes.podeVerFinanceiro`) — igual aos outros relatórios existentes.
- `GET /relatorios/balancete-anual/congregacoes`: `podeVerFinanceiro` **e** não ser
  congregação (`ehFilha` bloqueia com `AccessDeniedException`).

## Frontend

Nova aba/tela em `financeiro/relatorios`, reaproveitando os componentes e serviços já
existentes no módulo (`relatorio.service.ts`, `relatorio.type.ts`).

- Seletor de ano (`‹ 2026 ›`).
- Abas "Minha Igreja" / "Consolidado" / "Por Congregação" — as duas últimas só
  renderizadas se o usuário tiver permissão de consolidado (mesma checagem de hoje usada
  para mostrar o link do consolidado existente).
- Tabela: seções "Entradas"/"Saídas" com suas categorias, subtotal de cada seção, linha
  de saldo do mês, linha de saldo acumulado destacada (é o número mais importante do
  relatório).
- Categoria arquivada com selo "Arquivada" ao lado do nome.
- Categoria zerada com texto visualmente mais claro (não removida da lista).
- Botão "Exportar" desabilitado com tooltip "Em breve" — não gera arquivo nesta entrega.
- Mobile: tabela larga vira card por mês (padrão já usado no projeto — tabelas viram
  cards no mobile), cada card com total de entradas, total de saídas, saldo do mês e
  saldo acumulado daquele mês.

## Testes

Mockito puro no service novo (`BalanceteService`, separado de `RelatorioService` para não
inchar um arquivo que já cobre 5 relatórios diferentes — uma razão para mudar por
classe):

- Saldo de abertura soma corretamente movimentações anteriores ao ano pedido.
- Categoria ativa sem movimento no ano aparece zerada.
- Categoria arquivada sem movimento no ano não aparece.
- Categoria arquivada com movimento no ano aparece com `arquivada: true`.
- Categoria `AMBOS` aparece em `entradas` e `saidas` separadamente, cada uma só com o
  valor do respectivo tipo.
- Saldo acumulado de dezembro bate com saldo de abertura + soma de todos os saldos
  mensais do ano.
- Consolidado casa categorias de nomes iguais (ignorando acento/caixa) entre igrejas
  diferentes, somando os valores.
- Congregação recebe `AccessDeniedException` ao chamar `/balancete-anual/congregacoes`.
- Sede recebe a lista `porIgreja` + `consolidado` corretamente.

## Fora de escopo desta entrega

- Geração de arquivo (PDF/Excel) — endpoint entrega dado estruturado, exportação fica
  para depois.
- Ajuste do saldo de abertura por edição retroativa de movimentação de anos já fechados
  (nenhuma trava de "ano fechado" existe hoje no projeto; fora de escopo).
