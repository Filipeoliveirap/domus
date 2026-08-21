# Central de notificações — design

> Item 4 do `docs/BACKLOG-PRE-VENDA.md`. Brainstorm com o autor em 2026-08-20.

## Motivação

Não existe hoje nenhum mecanismo de notificação dentro do produto — só e-mail transacional
avulso, um por feature (convite, reset de senha). Sem uma central única, cada feature nova
(contas a pagar vencendo, comentário no mural, pedido de entrada em ministério) reinventaria
o próprio jeito de avisar, e o usuário nunca teria um lugar só pra ver tudo que aconteceu.

## Padrão de extensibilidade

Segue a mesma filosofia já documentada no `CLAUDE.md` do projeto ("estenda sem editar", "nada
de literal de domínio solto", "interface sem troca prevista é cerimônia"). Por isso, **sem
interface nova**:

- **`TipoNotificacao`** — enum, um lugar só. Adicionar um tipo de notificação novo é uma
  entrada nova no enum, nada mais.
- **`NotificacaoService.criar(TipoNotificacao tipo, UUID igrejaId, UUID usuarioDestinatarioId, String texto, String link)`**
  — método fachada único. Todo produtor (serviço que já existe) chama esse método no ponto
  onde o evento de negócio já acontece — mesmo padrão que `CacheEvictor.evictPorIgreja(...)`
  já usa hoje: chamada direta, síncrona, na mesma transação do produtor. Sem fila, sem
  `@Async`, sem event listener — consistente com o resto do projeto (só o `OutboxProcessador`
  é `@Scheduled`, e propositalmente).
- **Cada produtor monta o próprio texto/link.** O serviço que dispara já tem todo o contexto
  (nome da pessoa, nome do evento) — quem sabe montar "fulano pediu pra entrar no ministério
  X" é o `MinisterioService`, não um motor de template central adivinhando. "Uma razão pra
  mudar": o texto de uma notificação de ministério só muda se a regra de negócio de
  ministério mudar.
- **Múltiplos destinatários** (ex.: todos os membros de uma célula) não mudam a assinatura do
  método — o produtor decide a lista de destinatários e chama `criar(...)` uma vez por
  destinatário. `NotificacaoService` não sabe nem precisa saber que existe "célula".

Resultado prático: adicionar um produtor novo no futuro é **uma chamada de método** dentro do
serviço que já existe. Nunca precisa tocar em `NotificacaoService`, banco ou frontend.

## Modelo de dados (migration `V21__notificacao.sql`)

```sql
CREATE TABLE notificacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL REFERENCES igreja(id),
    usuario_destinatario_id UUID NOT NULL REFERENCES usuario(id),
    tipo VARCHAR(60) NOT NULL,
    texto VARCHAR(500) NOT NULL,
    link VARCHAR(255),
    lida BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notificacao_destinatario ON notificacao (usuario_destinatario_id, lida, created_at DESC);
```

- Destinatário é sempre `usuario` (não `pessoa`) — só quem tem login vê o app, logo só quem
  tem login pode receber notificação in-app.
- Sem soft delete: notificação não é dado de negócio que precisa de histórico auditável tipo
  `pessoa`/`evento` — expira por relevância, não por exclusão. Job de limpeza (notificações
  lidas há mais de N dias) fica como possível dívida futura, não construído na v1 (volume de
  uma igreja não justifica agora).
- `tipo` é `VARCHAR`, não FK pra tabela — reflete o enum Java (`TipoNotificacao`), mesmo
  tratamento que `usuario.role` faz diferente por ser autorização (esse aqui não é).

## Os 8 produtores da v1

| # | Evento | Onde no código | Destinatário(s) |
|---|---|---|---|
| 1 | Pedido de entrada em ministério | `MinisterioService.pedirEntrada` | Todos os líderes do ministério |
| 2 | Pessoa/visitante entra na célula | `CelulaService.adicionarMembro` | **Todos os membros da célula, exceto quem acabou de entrar** |
| 3 | Acesso concedido/convite aceito | `UsuarioService.concederAcesso` / `reativarAcesso` | O próprio usuário |
| 4 | Inscrição em evento que você é responsável | fluxo de inscrição (`InscricaoService`) | `evento.responsavelPessoaId`, se tiver usuário |
| 5 | Promovido a líder de célula | `CelulaService.atualizarPapel` (papel novo = LIDER) | A pessoa promovida |
| 6 | Evento muda data/local, ou é cancelado | `EventoService.atualizarEvento` (só se `inicioEm`/local mudou de verdade) / `arquivarEvento` | Inscritos confirmados |
| 7 | Igreja de fora pede pra entrar na família | `VinculoService.entrarNaFamilia` | Admin(s) da sede |
| 8 | Exclusão de conta agendada perto do prazo | job de exclusão de igreja existente | Admin(s) da igreja |

Notas:
- **Item 2 (mudou no brainstorm):** não é só o líder — é **todo mundo da célula** sabendo que
  chegou gente nova, com exceção de quem acabou de entrar (não faz sentido notificar a
  própria pessoa que ela mesma entrou).
- Itens 4, 5 e 8 dependem de a pessoa/admin **ter usuário** (login). Se não tiver, o produtor
  simplesmente não gera notificação pra aquele destinatário — sem erro, sem log de warning,
  é um caminho normal (nem todo `responsavelPessoaId` tem acesso ao sistema).
- Item 6 precisa comparar valor antigo vs. novo em `atualizarEvento` antes de notificar — só
  quando `inicioEm`, `localId` ou `localTexto` mudarem de verdade, senão editar a descrição
  de um evento gera notificação de "eita, mudou" sem nada relevante ter mudado.

## API

- `GET /notificacoes` — paginado (`Pageable`), mais recentes primeiro. Escopado por
  `usuarioId` do JWT — nunca lista notificação de outro usuário.
- `GET /notificacoes/contagem-nao-lidas` — endpoint leve, só o número, pro badge do sino
  (evita paginar a lista inteira só pra saber a contagem).
- `PATCH /notificacoes/{id}/lida` — marca uma como lida. 404 se o id não pertence ao usuário
  autenticado (mesmo padrão de `findByIdAndIgrejaId` que o resto do projeto usa, aqui seria
  `findByIdAndUsuarioDestinatarioId`).
- `PATCH /notificacoes/lidas` — marca todas como lidas de uma vez.

## Frontend

- Sino no `TopBar` (`lucide-react`, ícone `Bell`), ao lado da `BuscaGlobal`. Badge com a
  contagem de não lidas (esconde o número em zero, só mostra o sino "limpo").
- Dropdown ao clicar: lista das notificações (TanStack Query), item lido em cinza/mais claro,
  não lido em destaque. Botão "marcar todas como lidas" no topo do dropdown.
- Clicar numa notificação: marca como lida (`PATCH .../lida`) e navega pro `link`, se tiver.
- Atualização: **polling**, `refetchInterval` do TanStack Query (~30–60s) só pra
  `contagem-nao-lidas` (leve). A lista completa (dropdown) só busca quando o dropdown abre —
  não faz sentido manter a lista inteira sincronizada em segundo plano.
- Sem infra de WebSocket no projeto hoje — polling é o padrão mais simples que já serve pro
  volume de uma igreja; push/real-time fica em `BACKLOG-MELHORIAS-FUTURAS.md` se algum dia
  for preciso.

## Testes

Segue a convenção do projeto (`CLAUDE.md`, camada por ferramenta):

- `NotificacaoServiceTest` — Mockito puro, prova que `criar(...)` grava a notificação certa.
- Um teste por produtor, no arquivo de teste do serviço que já existe (`MinisterioServiceTest`,
  `CelulaServiceTest`, etc.) — `verify(notificacaoService).criar(...)` com os argumentos
  certos, mesmo padrão que `verify(cacheEvictor).evictPorIgreja(...)` já usa hoje. Cobre
  também o caminho "não notifica quem não tem usuário" e "não notifica quem acabou de entrar
  na célula" (item 2).
- `NotificacaoControllerTest` — `@SpringBootTest` + `AutenticacaoTestSupport`, prova que um
  usuário só vê/marca como lida a própria notificação (nunca a de outro usuário da mesma
  igreja, nem de outra igreja).

## Fora desta entrega (fica pro `BACKLOG-MELHORIAS-FUTURAS.md`)

- Push notification (exige PWA/app).
- Preferência de notificação por usuário (silenciar um tipo específico).
- Job de limpeza de notificação antiga/lida.
- Produtores além dos 8 listados (ex.: curtida/comentário no mural, contas a pagar vencendo)
  — entram quando os módulos de mural e contas a pagar (itens 5 e 13 do
  `BACKLOG-PRE-VENDA.md`) forem construídos. A central já nasce pronta pra recebê-los: é só
  mais uma chamada de `notificacaoService.criar(...)`.
