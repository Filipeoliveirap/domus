# Relatório de eventos (presença + engajamento) — design

**Data:** 2026-07-23
**Natureza:** feature de valor pra igreja.
**Fase do roadmap:** prioridade nova, decidida em reunião com a igreja (ver memória
`prioridades-gestao-celula-ministerios`), à frente do restante da Fase 2/3.

## Problema

Hoje o sistema sabe quem se **inscreveu** num evento (`inscricao_evento` +
`acompanhante_inscricao`), mas não sabe quem **realmente foi**. A igreja quer dois relatórios:

1. **Individual**, por evento: quantos se inscreveram vs quantos compareceram, separando
   pessoa cadastrada de convidado, e que fatia da igreja aquele evento alcançou.
2. **Geral**, agregando vários eventos: tendência de comparecimento, evento mais popular,
   participantes únicos, comparação entre eventos do mesmo tipo.

O obstáculo do relatório individual é que o sistema não tem como saber, por conta própria,
quem apareceu de fato — só quem se inscreveu. É preciso um jeito de "dar baixa" na presença.

## Decisão 1 — presença é opt-in por evento, não obrigatória

Nem todo evento quer o trabalho de fazer lista de chamada (um culto de domingo com 300
pessoas não vai marcar presença uma por uma). Então:

- `evento.controla_presenca BOOLEAN DEFAULT false` — toggle no formulário do evento,
  ao lado de `requer_inscricao`.
- **Só pode ser `true` se `requer_inscricao` também for `true`**
  (`CHECK (NOT controla_presenca OR requer_inscricao)`): controlar presença exige uma lista
  prévia de quem esperar — sem inscrição, não há quem chamar.
- Quando `controla_presenca=false`, o evento continua tendo relatório de **inscritos**, só
  não tem a seção de comparecimento.

## Decisão 2 — presença granular: inscrito E cada acompanhante

Acompanhante ocupa vaga e é uma pessoa que esteve lá — por isso a presença é marcada por
pessoa física, não por inscrição:

- `inscricao_evento.compareceu BOOLEAN DEFAULT false`
- `acompanhante_inscricao.compareceu BOOLEAN DEFAULT false`

Nenhuma tabela nova — só três colunas. A escala de dado de uma igreja (centenas de eventos e
pessoas, não milhões) não justifica pré-calcular nada; os relatórios são consultas agregadas
rodadas na hora (ver Decisão 4).

## Decisão 3 — marcar presença: lote + exceção, nunca só um dos dois

O fluxo real é "quase todo mundo veio, menos fulano": um botão "marcar todos vieram" que
marca `compareceu=true` em todos os inscritos `CONFIRMADA` do evento e em todos os
acompanhantes deles, e depois cada linha da lista tem um checkbox individual pra corrigir
exceção (quem não veio, ou quem chegou depois de um "marcar todos" e precisa ser incluído).

Endpoints:

- `POST /eventos/{id}/presenca/marcar-todos`
- `PATCH /eventos/{id}/presenca/inscricoes/{inscricaoId}` — `{ compareceu: boolean }`
- `PATCH /eventos/{id}/presenca/acompanhantes/{acompanhanteId}` — `{ compareceu: boolean }`

Todos retornam 409 se `evento.controla_presenca=false` (não existe presença pra marcar).
Permissão: reaproveita `podeGerenciarInscricoes` — a mesma capacidade de quem já gerencia
inscrição do evento hoje, sem role nova.

## Decisão 4 — duas bases de cálculo, nunca misturadas em silêncio

Como comparecimento só existe quando `controla_presenca=true`, mas inscrição existe sempre,
cada métrica do relatório geral declara explicitamente qual base usa:

- **Inscritos confirmados** (pessoa + convidados): disponível em **qualquer** evento.
  Usada por "evento mais popular" (funciona mesmo em evento sem controle de presença).
- **Comparecimento real**: só existe em eventos com `controla_presenca=true`. Usada por
  "comparecimento médio", "participantes únicos" e no gráfico de tendência — que por isso
  só enxergam a fatia de eventos que ativaram o controle. Um mês sem nenhum evento assim
  aparece como "sem dado", nunca como zero (zero mentiria: parece que ninguém foi, quando
  na verdade ninguém *controlou*).
- **Variação (evento anterior do mesmo tipo / média geral do filtro)**: usa comparecimento
  real quando os dois eventos comparados têm `controla_presenca=true`; cai para inscritos
  confirmados quando não. Qual base foi usada aparece **explícito** num tooltip/click — nunca
  implícito, porque comparar 40 inscritos com 40 presentes de eventos diferentes sem avisar
  seria comparar coisas diferentes como se fossem iguais.

"Evento anterior do mesmo tipo" é o evento mais recente da mesma igreja com o mesmo `tipo`
(texto livre) e `inicio_em` anterior ao evento atual.

## Relatório individual (modal do evento)

Na lista de inscritos (já existente no modal de detalhe), abaixo dela, quando
`controla_presenca=true`:

```
Presença Total       — X de Y inscritos, círculo de % (compareceram ÷ inscritos)
Composição            — "Pessoas da Igreja" vs "Convidados" (compareceram, cada um)
Impacto Global         — % da base total de pessoas ativas da igreja que compareceu
                         (só pessoas cadastradas — convidado não entra nesse denominador,
                          porque a base é "pessoas da igreja", não "gente que apareceu")
```

"Pessoas da Igreja" / "Convidados" (não "Membros"/"Visitantes"): o domínio já usa
`vinculo` (`MEMBRO`/`CONGREGANTE`) pra outra coisa — se o relatório dissesse "Membros"
pareceria filtrar por batismo, quando na verdade é só "tem cadastro" vs "não tem".

Quando `controla_presenca=false`: a seção de comparecimento some inteira (não aparece vazia
ou zerada); só o bloco de inscritos (pessoas vs convidados) continua.

`GET /eventos/{id}/relatorio`:
```
inscritos: { pessoas: N, convidados: N }
compareceram: { pessoas: N, convidados: N } | null
percentualIgreja: number | null
```

## Relatório geral (página)

**Filtros** (combináveis, todos opcionais): Período (intervalo de datas), Recorte Etário
(`recorte_etario`), Tipo (`tipo`).

**Cards de resumo:**
- Total de eventos no filtro.
- Comparecimento médio (base: comparecimento real).
- Participantes únicos — pessoas distintas que compareceram de fato (base: comparecimento
  real).
- Evento mais popular — maior total de inscritos confirmados, pessoa+convidado (base:
  inscritos).

**Gráfico de tendência (6 meses):** comparecimento médio mensal (base: comparecimento real).

**Lista "Últimos Eventos":** por evento, total de participantes + duas variações
(evento anterior do mesmo tipo; média geral do filtro), cada uma com a base usada visível
no tooltip/click.

`GET /eventos/relatorio-geral?inicio=&fim=&recorteEtario=&tipo=`:
```
resumo: {
  totalEventos: number,
  comparecimentoMedio: number | null,
  participantesUnicos: number | null
}
eventoMaisPopular: { eventoId, titulo, totalInscritos } | null
tendencia: [ { mes: "2026-07", comparecimentoMedio: number | null } ]
ultimosEventos: [{
  eventoId, titulo, data, totalParticipantes,
  variacaoEventoAnterior: { percentual: number, base: "COMPARECIMENTO" | "INSCRITOS" } | null,
  variacaoMediaGeral:     { percentual: number, base: "COMPARECIMENTO" | "INSCRITOS" }
}]
```

## Fora de escopo desta entrega

- Onde exatamente a página de relatório geral mora na navegação (embaixo dos cards de
  Eventos vs. aba própria) — decisão de layout, resolvida no protótipo/implementação do
  front, não trava o back.
- Exportar dados (botão presente no protótipo, sem requisito funcional definido ainda).
- **Bug à parte, anotado, não faz parte deste spec:** o toggle `requer_inscricao` hoje
  também esconde no front os campos de faixa/recorte etário, que deveriam poder existir
  como rótulo informativo mesmo sem inscrição formal. Fix de front independente, a ser
  feito depois.
