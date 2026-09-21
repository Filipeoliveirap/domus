# Início e Dashboard — Design

**Data:** 2026-07-18
**Fase:** 4 (dashboard) + tela inicial

Duas telas distintas, hoje inexistentes (nav aponta para elas):
- **`/inicio`** — tela inicial de **todas as roles**. Vibe comunidade/acolhimento.
- **`/dashboard`** — painel de **ADMIN_IGREJA**. Vibe gestão/financeiro.

Regra geral: só usar dados que existem na modelagem (membro, evento, movimentação).
Nada de contas a pagar, metas ou gráficos complexos. Ambas responsivas (mobile obrigatório).

## Início (`/inicio`)

Conteúdo (mesmo para todas as roles):
1. **Saudação** — "Olá, {primeiro nome}" + subtítulo curto. Primeiro nome = primeira palavra do nome.
2. **Versículo do dia** — lista **curada local de 60 versículos** (frontend). Escolha determinística
   pela data (ex.: índice = dia-do-ano % 60), então roda por dia e é estável no mesmo dia.
   Sem API externa (sem dependência/latência/CSP).
3. **Aniversariantes do mês** — membros com `data_nascimento` no mês atual (nome + dia).
   Ordenado por dia. Estado vazio: "Nenhum aniversariante este mês."
4. **Próximos eventos** — lista dos próximos eventos (título, data/hora, local). Sem grade de
   calendário. Estado vazio: "Nenhum evento próximo."

### Backend — `GET /inicio`
Retorna `{ aniversariantesMes: [{ id, nome, dia }], proximosEventos: [{ id, titulo, inicio, local }] }`.
- Aniversariantes: `membro` da igreja, não arquivado, `EXTRACT(MONTH FROM data_nascimento) = mês atual`,
  ordenado por `EXTRACT(DAY ...)`.
- Próximos eventos: `evento` da igreja, `inicio >= agora`, ordenado asc, limite 5.

## Dashboard (`/dashboard`) — só ADMIN

4 cards de número + 2 listas (baseado no protótipo do usuário, mapeado ao que temos):

**Cards:**
1. **Total de membros** (+ novos no mês).
2. **Eventos este mês** (+ quantos nesta semana).
3. **Entradas do mês** (R$) — soma das movimentações de ENTRADA no mês.
4. **Saldo do mês** — entradas − saídas do mês (verde se ≥ 0, vermelho se < 0).

**Listas:**
5. **Movimentações recentes** — últimos ~5 lançamentos (descrição, categoria, data, valor com sinal/cor).
6. **Próximos eventos** — lateral (título, data, local).

Descartados do protótipo (fora da modelagem): "contas a pagar", "% da meta", gráfico de barras.

### Backend — `GET /dashboard` (ADMIN)
Retorna:
```
{
  membros: { total, novosMes },
  eventos: { mes, semana },
  financeiro: { entradasMes, saidasMes, saldoMes },
  movimentacoesRecentes: [{ id, descricao, categoriaNome, tipo, valor, dataMovimentacao }],
  proximosEventos: [{ id, titulo, inicio, local }]
}
```
- `novosMes`: membros criados no mês corrente.
- `eventos.mes`/`semana`: eventos com `inicio` no mês / na semana corrente.
- `financeiro`: soma por tipo das movimentações do mês.
- `movimentacoesRecentes`: últimas 5 por data desc.

## Frontend
- Reusar componentes de estado (EstadoVazio, EstadoErro, skeletons) e o padrão de cards/listas.
- Hooks react-query (`useInicio`, `useDashboard`).
- `/dashboard` protegido com `AcessoRestrito` para não-admin (padrão dos relatórios).
- Responsivo: cards em grid que colapsa para 1 coluna no mobile; listas viram cards; tipografia por tokens.

## Fora de escopo
- Notificações (sino), gráficos, metas, contas a pagar, calendário em grade.

## Testes
- Back: queries de agregação (aniversariantes por mês, contadores, somas) — testes de repositório/serviço.
- Front: verificação manual (sem runner).

## Ordem
Spec → implementar **Início** (back + front) → deploy → **Dashboard** (back + front) → deploy.
