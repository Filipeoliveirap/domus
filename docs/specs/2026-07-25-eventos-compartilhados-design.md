# Eventos compartilhados entre igrejas vinculadas — design

> Brainstorm de 2026-07-25. Independente dos três specs de célula/visitantes/capacidades
> extra (mesma data, especs diferentes). Reaproveita o modelo de "família de igrejas"
> (sede/congregações, `igreja.igreja_mae_id`) já existente — não inventa hierarquia
> nova.

## Motivação

Igrejas com congregações fazem eventos comuns o tempo todo (conferência conjunta,
retiro, encontro de jovens) — hoje cada evento é isolado dentro de uma igreja
(`igreja_id`), sem jeito de uma pessoa de uma congregação ver ou se inscrever num
evento criado pela sede (ou por outra congregação da família), e vice-versa.

## Risco desta feature

Isso mexe na garantia mais sensível do sistema — o isolamento por `igreja_id`. Ao
contrário de módulos novos isolados (Ministério, Célula, Visitantes), aqui a mudança é
**abrir uma exceção controlada** nesse isolamento, não construir algo novo por cima
dele. Um erro aqui vaza dado entre igrejas (já aconteceu um quase-vazamento parecido no
recurso de igrejas vinculadas, ver memória do projeto). **Reaproveitar o mecanismo já
existente e testado de "família de igrejas"** (usado hoje no financeiro consolidado) é
obrigatório — não reinventar resolução de família aqui.

## Modelo de dados

Um campo novo em `evento`:

```sql
ALTER TABLE evento ADD COLUMN restrito_propria_igreja BOOLEAN NOT NULL DEFAULT false;
```

- `false` (padrão): evento visível e inscrevível por **toda a família** de igrejas —
  qualquer igreja da família pode criar um evento compartilhado, visível às demais
  (não é hierárquico: sede compartilha com congregações e vice-versa, e entre
  congregações irmãs também).
- `true`: evento visível/inscrevível só pela própria igreja — comportamento de hoje.

Nenhuma outra coluna nova. Vagas, elegibilidade (`idade_min`/`idade_max`,
`restricao_estado_civil`, `restricao_sexo`, `exclusivo_membros`) continuam lendo só os
campos que já existem em `pessoa`, sem qualquer distinção por igreja — ver seção
"Elegibilidade e vagas" abaixo.

### UI do toggle

O campo "Apenas minha igreja" só aparece no formulário de evento se a igreja **tem
família** (tem `igreja_mae_id` preenchido, ou tem alguma igreja filha) — uma igreja
independente nem vê a opção, já que não tem com quem compartilhar.

## Autorização

- **Gerenciar o evento** (editar, arquivar, alterar o toggle de restrição, inscrever
  outros, controlar presença, cancelar inscrição de terceiros): `podeGerenciarEventos(role)`
  **E** `usuarioAutenticado.getIgrejaId() == evento.getIgreja().getId()`. Uma pessoa com
  role alta (`ADMIN_IGREJA`/`LIDER`) em **outra** igreja da família não ganha nenhum
  poder de gestão sobre um evento que não é da própria igreja — só visualiza e se
  inscreve, igual qualquer pessoa comum.
- **Ver e se inscrever**: qualquer pessoa autenticada cuja igreja esteja na família do
  evento (a própria igreja criadora, sempre; ou qualquer igreja da família, quando
  `restrito_propria_igreja = false`) — sem exigir role nenhuma além de estar logado,
  igual eventos hoje.
- **Cancelar a própria inscrição**: sempre permitido, própria igreja ou não — não muda
  em relação a hoje.

Implementação: os métodos que já checam `podeGerenciarEventos`/`podeGerenciarInscricoes`
no `EventoController`/`InscricaoController` passam a também comparar
`usuarioAutenticado.getIgrejaId()` contra `evento.getIgreja().getId()` antes de liberar
qualquer ação de gestão — se a igreja for diferente, `AccessDeniedException` mesmo que
a role permitisse dentro da própria igreja.

## Visibilidade — listagem, detalhe e busca

- `GET /eventos` passa a agregar: eventos da própria igreja **+** eventos de outras
  igrejas da família com `restrito_propria_igreja = false`. O card mostra um badge com
  nome/sigla da igreja organizadora quando não é a própria igreja de quem está vendo.
- `GET /eventos/{id}` — mesma regra de visibilidade: pessoa de fora vê tudo que já vê
  hoje (elegibilidade, vagas, descrição), só não vê os botões de gestão.
- **Busca global (Elasticsearch)**: mesma expansão de visibilidade — evento
  compartilhado aparece na busca de quem é de outra igreja da família. O índice
  continua guardando o `igreja_id` real do evento (não duplica por igreja); a consulta
  de busca passa a filtrar por "meu `igreja_id` OU (`igreja_id` da minha família E
  `restrito_propria_igreja = false`)" em vez de só "meu `igreja_id`".

## Inscrição, vagas e elegibilidade

- **Vagas**: cota única para o evento inteiro, sem reserva por igreja — quem se
  inscrever primeiro ocupa vaga, não importa a igreja. O lock pessimista de contagem
  de vagas já existente não muda (continua contando todos os inscritos confirmados do
  evento, independente de qual igreja a pessoa é).
- **Elegibilidade** (`idade_min`/`idade_max`, `restricao_sexo`,
  `restricao_estado_civil`, `exclusivo_membros`): avalia os dados da própria pessoa
  (`data_nascimento`, `sexo`, `estado_civil`, `vinculo`) — **não** distingue por
  igreja. Um membro batizado (`vinculo = MEMBRO`) de uma congregação passa no filtro
  "exclusivo membros" de um evento criado pela sede, e vice-versa: o critério é sobre a
  pessoa (é batizada?), não sobre a igreja dela. **Nenhuma mudança na lógica de
  elegibilidade em si** — ela já lê só campos de `pessoa`.
- **`POST /eventos/{id}/inscricoes`**: a checagem de visibilidade
  (`restrito_propria_igreja` + família) entra ANTES da elegibilidade normal — pessoa de
  fora da família tentando se inscrever num evento restrito recebe 403/404 (mesmo
  tratamento de "não vê o recurso"), antes mesmo de chegar nas regras de idade/sexo/etc.

## Lista de inscritos / participantes — rastreio

Cada linha da lista de inscritos e do relatório de presença por evento ganha o
nome/sigla da igreja da pessoa (join com `pessoa.igreja_id → igreja`) — **sempre
presente no dado retornado pela API**; o front só destaca a coluna/badge quando o
evento é compartilhado (`restrito_propria_igreja = false`) ou quando há de fato mais de
uma igreja distinta entre os inscritos. Isso resolve o problema de confusão que motivou
o spec: numa lista com gente de igrejas diferentes, fica claro de onde é cada um.

## Fora de escopo

- **Responsável/gerente específico por evento** (autorização por recurso, no estilo de
  Ministério/Célula — "fulano é responsável por este evento X"). Hoje é só o gate de
  role + mesma igreja; delegar gestão a alguém específico (inclusive de outra igreja)
  fica pra uma entrega futura, se fizer sentido.
- **Reserva de vagas por igreja** (cota separada por igreja dentro do mesmo evento) —
  a cota é sempre única.
- **Cobrança/pagamento** — `preco` continua só informativo (decisão já tomada no
  roadmap, Fase 6, para eventos em geral).
- **Quebra do relatório geral de eventos por igreja** (o relatório de engajamento já
  existente, `EventoRelatorioService`, não ganha uma dimensão "por igreja" neste spec —
  só a lista de inscritos e o relatório de presença por evento específico ganham o
  rastreio). Fica pra uma entrega separada, se o uso real pedir — mesmo padrão que já
  existe hoje pro financeiro consolidado (`VisaoGeralCongregacoes`), reaproveitável
  quando essa entrega acontecer.
