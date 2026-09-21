# Fluxo de pagamento de evento — rota dedicada, inscrição em lote e correção de status

> Evolui `2026-08-23-cobranca-evento-pago-design.md` (que fechou a arquitetura de
> cobrança/split). Esta spec trata da **experiência** do fluxo — hoje embutido em modais —
> e corrige um gap de estado descoberto no brainstorm: a inscrição confirma antes do
> pagamento. Brainstorm em 2026-08-26, junto com protótipos visuais gerados no Claude
> Design (projeto "Redesign fluxo pagamento Domus").

## Contexto

O checkout de evento pago (Payment Brick, PIX, "Divisão de pagamento") hoje vive dentro de
modais/drawers do evento — `BotaoConfirmarPresenca` (auto-inscrição) e
`ModalInscreverPessoas`/`ModalInscreverAlguem` (lote do admin/líder). Isso tem dois
problemas: (1) o checkout embutido não comunica "você está num processo de várias etapas"
— não tem stepper, o contexto de evento se perde dentro do modal; (2) o fluxo de lote
seleciona todo mundo via checkbox e só decide pagamento no final, quando o ideal é decidir
pessoa por pessoa, no momento de adicionar. No meio da investigação, achamos um terceiro
problema, mais sério: `InscricaoEvento` nasce `CONFIRMADA` mesmo em evento pago, antes de
qualquer pagamento acontecer — a pessoa aparece como inscrita confirmada em listas e
contagens antes de pagar.

## Decisões já fechadas no brainstorm

- Rota nova é só para evento **pago** — evento gratuito continua com "Se inscrever"
  instantâneo, sem navegação.
- Não é aba nova do navegador — é uma rota própria do Next.js (SPA), com o contexto do
  evento (título, data, foto) visível ao lado do stepper.
- Vale pros três casos: auto-inscrição (autenticado), lote do gestor (autenticado), e
  auto-inscrição de pessoa de fora via `/convite/{token}` (sem sessão).
- O botão que hoje diz "Confirmar presença" passa a dizer **"Se inscrever"** nos dois
  casos (grátis e pago) — hoje inconsistente entre os dois fluxos.
- A reserva de vaga e a proteção contra race condition (lock pessimista em `Evento`,
  contagem por `CobrancaEvento` PAGA/PENDENTE-não-expirada) **já estão corretas** — não
  precisam mudar. O gap é só o status da `InscricaoEvento`.

## Arquitetura da rota de checkout

Rota nova: `/eventos/{eventoId}/pagamento/{cobrancaId}` (aceita `?token={conviteToken}`
opcional, para o caso de convite público sem sessão — mesmo padrão de autorização que
`/cobranca/{token}` já usa hoje, mas escopado à cobrança em vez de token único).

Layout: cabeçalho fixo com contexto do evento (foto, título, data) + stepper (`Resumo →
Pagamento → Confirmação`) + conteúdo da etapa atual. Funciona autenticado (redireciona
sozinho pro conteúdo certo pela sessão) ou público (via query `token`).

A cobrança e a inscrição (`AGUARDANDO_PAGAMENTO`) já existem quando a rota é aberta — são
criadas no clique de "Se inscrever"/"Pagar agora", antes da navegação. A rota nunca cria
cobrança, só consome uma que já existe.

## Correção no backend: novo status `AGUARDANDO_PAGAMENTO`

- Migration `V4`: novo valor no enum `StatusInscricao` — `AGUARDANDO_PAGAMENTO`, além de
  `CONFIRMADA`/`CANCELADA`.
- `InscricaoService.inscreverInterno`: para evento pago, a inscrição nasce
  `AGUARDANDO_PAGAMENTO` em vez de `CONFIRMADA` (linhas ~142/151 do arquivo atual).
- O webhook do Mercado Pago (que já atualiza `CobrancaEvento.status = PAGO`) passa a
  também atualizar a `InscricaoEvento` vinculada para `CONFIRMADA` na mesma transação.
- Se a cobrança expira ou é cancelada, a inscrição correspondente cancela junto (mesmo
  gatilho/job que já existe para expirar `CobrancaEvento` — só precisa espelhar na
  inscrição).
- `contarOcupadas`/`validarVaga` (reserva de vaga) **não mudam** — já contam certo, por
  cobrança, não por status de inscrição.
- `listarInscritos`/`listarParticipantes`/`vagasRestantes` do admin já filtram por
  `CONFIRMADA` — com a mudança de status, param de mostrar quem ainda não pagou,
  automaticamente, sem precisar tocar nessas queries.
- Telas que hoje tratam "inscrito" como só `CONFIRMADA` precisam de um estado visual pra
  `AGUARDANDO_PAGAMENTO` (ex.: "Aguardando pagamento" em vez de "Inscrito") — vale para
  `MinhaInscricaoResponse`/`BotaoConfirmarPresenca` (a própria pessoa vê que está com
  pagamento pendente, não simplesmente "não inscrita").

## Fluxo individual (auto-inscrição, `BotaoConfirmarPresenca`)

- Texto do botão: **"Se inscrever"**, evento pago ou grátis.
- Evento grátis: comportamento atual, instantâneo, sem navegação.
- Evento pago: clique cria inscrição (`AGUARDANDO_PAGAMENTO`) + cobrança (titular sempre
  "paga agora", regra já fechada na spec anterior) e navega direto para
  `/eventos/{id}/pagamento/{cobrancaId}`. Sem card "Divisão de pagamento" — não faz
  sentido para uma pessoa só, que já é sempre "paga agora".
- Ao concluir o pagamento (webhook confirma), a rota mostra a etapa "Confirmação" e a
  pessoa pode voltar para o evento.

## Fluxo em lote (`ModalInscreverPessoas` / `ModalInscreverAlguem`)

Vale para as três abas — "Pessoas da igreja", "Visitantes" e "Pessoa de fora" — quando o
evento é pago:

- Remove o checkbox de seleção múltipla. A busca/lista continua igual; cada linha ganha
  ação direta.
- Clicar numa pessoa expande inline (sem navegar) duas opções: **"Pagar inscrição de
  [Nome]"** e **"Enviar link pra [Nome] pagar"**.
- **Se o evento tem campos adicionais** (`CampoPersonalizadoEvento`): antes dessas duas
  opções, abre um passo para preencher os campos daquela pessoa. O gestor escolhe entre
  preencher ele mesmo ali, ou **compartilhar** (reaproveita `ModalCompartilharConvite`,
  o mesmo botão/fluxo de "compartilhar evento" já existente) para a pessoa preencher
  sozinha depois. Campo obrigatório não preenchido (nem ali, nem depois via
  compartilhamento) impede a inscrição de confirmar.
  - Gestor preenche ali → segue para a escolha "pagar agora / enviar link" da mesma
    pessoa, normalmente.
  - Gestor compartilha → a pessoa recebe o link de convite (que já resolve campos e
    pagamento sozinha, ver seção seguinte); o gestor não faz mais nada por essa pessoa
    agora, só volta pra lista — mesmo comportamento de "enviar link" abaixo.
- **Enviar link:** cria inscrição (`AGUARDANDO_PAGAMENTO`) + cobrança com link, abre
  `ModalCompartilharCobranca` (copiar/WhatsApp) por cima da lista. Ao fechar, volta pra
  lista, pronta para a próxima pessoa. Nenhuma navegação de rota. Se o compartilhamento
  for via WhatsApp (sai do app), ao voltar ao Domus retoma exatamente onde parou.
- **Pagar agora:** cria inscrição + cobrança e navega para
  `/eventos/{id}/pagamento/{cobrancaId}` — sai do modal. Ao concluir, navega para a tela
  de eventos (não volta ao modal — essa ação encerra a sessão de adicionar pessoas).
- O card `EscolhaPagamentoPorPessoa` ("Divisão de pagamento", que hoje resolve todo mundo
  de uma vez no final) deixa de existir nesse formato — a decisão passa a ser por pessoa,
  no momento de adicionar.

## Convite público (`/convite/{token}`) ganha suporte a pagamento

Gap real encontrado: hoje essa página mostra o preço do evento mas não cobra nada. Ao
confirmar inscrição num evento pago, em vez de ir direto para "sucesso": cria inscrição
(`AGUARDANDO_PAGAMENTO`) + cobrança e redireciona para
`/eventos/{id}/pagamento/{cobrancaId}?token={conviteToken}` — mesma rota de checkout,
autorizada pelo token do convite (sem sessão), no mesmo padrão que `/cobranca/{token}` já
usa. Cobre também o caso de "compartilhar pra pessoa preencher" do fluxo em lote — a
pessoa de fora resolve campos adicionais e pagamento nessa mesma jornada, sem intervenção
do gestor.

## Referência visual

Protótipos aprovados no Claude Design (`Fluxo Pagamento Evento.dc.html`, projeto
"Redesign fluxo pagamento Domus") — tema claro, tipografia/hierarquia inspirada em
dashboard estilo shadcn, cards com leve elevação. Usar como referência de acabamento
visual (não de fluxo — o fluxo mudou nesta spec em relação ao que foi prototipado).

## Fora de escopo

- Comissão do Domus sobre evento pago (decisão já registrada como fora de escopo na spec
  anterior).
- Lotes de preço / preço variável por pessoa.
- Reembolso/estorno pelo Domus (segue processo manual via Mercado Pago).
- Tema escuro — os protótipos foram gerados em tema claro deliberadamente; dark mode fica
  para quando o restante do app for refatorado.
