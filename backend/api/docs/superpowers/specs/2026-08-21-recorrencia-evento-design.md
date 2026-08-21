# Recorrência de evento — design

> Item 6 do `docs/BACKLOG-PRE-VENDA.md`. Brainstorm com o autor em 2026-08-21.

## Motivação

Cadastrar o culto toda semana na mão é um dos primeiros atritos que uma igreja de fora notaria
comparando com um concorrente. `Evento` hoje não tem nenhum conceito de recorrência — cada
linha é um evento isolado. O objetivo é cadastrar uma vez ("toda quinta às 19h") e o sistema
manter as ocorrências futuras aparecendo sozinhas, com o mesmo poder de edição que qualquer
calendário maduro (Google Calendar) já resolveu: editar/cancelar **só um dia**, **este dia e os
seguintes**, ou **a série inteira**.

## Princípio de design

A regra de recorrência e o conteúdo visível do evento (título, local, vagas, restrições...)
vivem em lugares diferentes, cada um com uma única razão pra mudar:

- **`EventoSerie`** — só a regra ("quando"). Nunca guarda título/local/etc. Se guardasse,
  toda edição de conteúdo teria que decidir se escreve na série ou na ocorrência — duas fontes
  de verdade pro mesmo dado.
- **`Evento`** (já existe) — todo o conteúdo, como hoje. Ganha só `serie_id` (nulável) e
  `diverge_da_serie` (boolean). Evento avulso continua exatamente igual, sem tocar nele.

Cada ocorrência de uma série é um `Evento` de verdade, materializado com antecedência — não um
cálculo virtual na hora de listar. Isso significa que inscrição, presença, notificação, busca
(Elasticsearch via outbox) e o cache de listagem continuam funcionando **sem nenhuma mudança**
neles: pra esses módulos, uma ocorrência de série é indistinguível de um evento avulso.

## Modelo de dados (migration `V22__evento_serie.sql`)

```sql
CREATE TABLE evento_serie (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id                   UUID NOT NULL REFERENCES igreja(id),
    frequencia                  VARCHAR(10) NOT NULL,   -- DIARIA | SEMANAL | MENSAL
    intervalo                   INTEGER NOT NULL DEFAULT 1 CHECK (intervalo > 0),
    dias_semana                 VARCHAR(80),            -- CSV de DiaSemana, só p/ SEMANAL
    tipo_recorrencia_mensal     VARCHAR(20),             -- DIA_FIXO | DIA_DA_SEMANA, só p/ MENSAL
    data_fim                    DATE,                    -- mutuamente exclusivo c/ numero_ocorrencias
    numero_ocorrencias          INTEGER CHECK (numero_ocorrencias > 0),
    ativa                       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_por_usuario_id       UUID REFERENCES usuario(id),
    created_at                  TIMESTAMP NOT NULL DEFAULT NOW(),

    CHECK (data_fim IS NULL OR numero_ocorrencias IS NULL)
);

ALTER TABLE evento ADD COLUMN serie_id UUID REFERENCES evento_serie(id);
ALTER TABLE evento ADD COLUMN diverge_da_serie BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_evento_serie ON evento (serie_id);
```

- `dias_semana` como CSV de `DiaSemana` (reusa o enum que a Célula já usa — mesmo tratamento
  de domínio: nunca string solta, sempre o enum existente).
- `data_fim` / `numero_ocorrencias` ambos `NULL` = série sem fim (o CHECK só impede os dois
  preenchidos ao mesmo tempo, igual ao par `local_id`/`local_texto` de `Evento`).
- Sem soft delete em `EventoSerie`: "apagar a série" na prática é `ativa = false` (o job para de
  materializar) + arquivar as ocorrências futuras via `arquivarEvento` já existente — a série em
  si não é um registro de negócio auditável por conta própria, é metadado da regra.

## Materialização (job diário)

Mesmo padrão de `ExclusaoIgrejaJob`/`LimpezaFotosJob` (`@Scheduled(cron = "0 0 5 * * *")`,
depois do backup e antes do expediente). Por igreja, por série `ativa = true`:

1. Calcula as datas da regra dentro de uma janela de **60 dias à frente** (ou até `data_fim`/
   `numero_ocorrencias`, o que vier primeiro).
2. Pra cada data: se já existe um `Evento` com aquele `serie_id` + aquela data (**inclusive
   soft-deletado** — um feriado cancelado não pode ressuscitar no próximo dia de rodagem do
   job), pula.
3. Se não existe: clona os campos do **`Evento` mais recente daquela série com
   `diverge_da_serie = false`** (nunca de uma ocorrência editada "só este dia" — senão a
   exceção pontual de uma semana vazaria pra sempre nas seguintes), recalcula só
   `inicioEm`/`fimEm` pra nova data, salva.

A primeira ocorrência nasce de forma síncrona junto com a série (mesma request que cria a
série), não espera o job — usuário vê o primeiro evento aparecer na hora.

**Cálculo de datas por frequência:**
- `DIARIA`: soma `intervalo` dias a partir da última ocorrência.
- `SEMANAL`: próxima data cujo dia da semana está em `dias_semana`, respeitando `intervalo`
  como "pula N semanas inteiras" quando aplicável (ex.: quinzenal).
- `MENSAL` + `DIA_FIXO`: mesmo dia-do-mês da primeira ocorrência, a cada `intervalo` meses.
- `MENSAL` + `DIA_DA_SEMANA`: mesma posição (1ª, 2ª, 3ª, última) do mesmo dia da semana da
  primeira ocorrência, a cada `intervalo` meses (ex.: "toda 1ª terça").

## Edição — os 3 escopos

Endpoint `PUT /eventos/{id}` ganha parâmetro `escopo` (`ESTA` | `ESTA_E_SEGUINTES` | `SERIE`),
default `ESTA` quando ausente (evento avulso nunca precisa dele — request continua igual).

- **`ESTA`**: `EventoService.atualizarEvento` de sempre, sem mudança nenhuma, só passa a
  marcar `diverge_da_serie = true` quando o evento pertence a uma série.
- **`SERIE`**: atualiza os campos de **todas** as ocorrências `AGENDADO` daquela série (passado
  já é bloqueado hoje por `EventoService` — `EM_ANDAMENTO`/`ENCERRADO` recusam edição, regra
  existente cobre isso de graça) e limpa `diverge_da_serie` de todas (edição de série sempre
  vence uma divergência antiga — mais previsível que o comportamento real do Google Calendar,
  que é inconsistente nisso). Se a regra em si mudou (frequência/dias/intervalo/fim), atualiza
  `EventoSerie` também.
- **`ESTA_E_SEGUINTES`**: encerra a série atual (`data_fim` = véspera desta ocorrência,
  **`numero_ocorrencias` limpo** — o CHECK de exclusão mútua exige isso, independente de como a
  série antiga terminava originalmente), cria uma `EventoSerie` nova clonada com a regra
  editada, repontam pra ela essa ocorrência e todas as `AGENDADO` futuras (`serie_id` trocado,
  campos atualizados, `diverge_da_serie` limpo). O job passa a materializar a série nova daqui
  pra frente; a antiga já não gera mais nada além do que já existe até a véspera.

## Cancelamento

`DELETE /eventos/{id}` ganha o mesmo parâmetro `escopo`. `ESTA` reusa `arquivarEvento` como já
existe hoje (soft-delete daquele `Evento` só — feriado). `SERIE` arquiva em lote todas as
`AGENDADO` daquela série e marca `EventoSerie.ativa = false`. `ESTA_E_SEGUINTES` arquiva a
partir desta data e marca `ativa = false` na série (sem criar uma segunda série pra um
cancelamento — só faz sentido dividir a série num edit que a mantém viva).

## Notificação

`TipoNotificacao.NOVO_EVENTO` (já existe) dispara em toda ocorrência materializada — inclusive
as que o job cria sozinho toda semana pra sempre, por decisão explícita do autor. O texto muda
por origem, dentro do mesmo produtor:

- **Evento avulso** (`serie_id == null`): texto atual, sem mudança —
  `"Novo evento: \"{título}\". Dá uma olhada!"`.
- **Ocorrência de série** (`serie_id != null`): tom de lembrete/convite, não de anúncio —
  `"{título} é {dia da semana}, {data} às {hora}. Vem participar!"` (ex.: `"Culto é quinta,
  21/08 às 19h. Vem participar!"`). Vale igual pra diária/semanal/mensal — o texto só descreve
  quando é, não precisa de frase por frequência.

## Frontend

- **Criar**: toggle **"Repetir"** no formulário de evento existente. Ligado, abre: frequência
  (diária/semanal/mensal), "a cada N" (dias/semanas/meses), dias da semana (chips, mesmo padrão
  visual do seletor de `DiaSemana` que a Célula já usa) quando semanal, e pro mensal um radio
  "todo dia {N}" vs "toda {1ª/2ª/3ª/última} {dia da semana}". Fim: "nunca" / "em {data}" /
  "depois de {N} ocorrências".
- **Editar/cancelar** um evento com `serie_id`: ao salvar ou arquivar, modal pergunta o escopo
  — "Só este" / "Este e os seguintes" / "Toda a série". Evento avulso (`serie_id == null`)
  salva/arquiva direto como hoje, sem esse passo a mais.
- `EventoRequest` ganha campo opcional `recorrencia` (frequência, intervalo, dias, tipo
  mensal, fim) — `null` = evento avulso, formato de request existente não muda pra quem já
  cadastra assim.

## Fora do escopo desta entrega

- Inscrição por série (decidido: inscrição continua por ocorrência — cada `Evento` materializado
  tem sua própria lista de inscritos, `InscricaoService` não muda nada).
- Visualização em calendário (grade mensal) — a lista de eventos continua sendo lista; cada
  ocorrência aparece como uma linha normal, só com o rótulo "faz parte de uma série".
- Preferência de notificação por tipo/frequência (silenciar o lembrete de série específica) —
  fica pro mesmo backlog de preferências por usuário já registrado na Central de Notificações.
- Editar/mover só a data de UMA ocorrência sem editar mais nada ("arrastar no calendário") —
  cabe dentro do escopo `ESTA` já desenhado, não precisa de fluxo separado.

## Decisões já tomadas (não rediscutir)

- Recorrência livre (diária/semanal/mensal com intervalo), não só semanal.
- Materialização em janela móvel (60 dias), não tudo de uma vez nem 100% virtual.
- 3 escopos de edição (só esta / esta e as seguintes / toda a série), com divisão real de série
  pra suportar mudança de regra "daqui pra frente".
- Recorrência é uma opção dentro do formulário de evento existente, não uma tela nova.
- Notificação de "novo evento" dispara em toda ocorrência materializada, texto diferente pra
  série (lembrete/convite) vs. avulso (anúncio).
