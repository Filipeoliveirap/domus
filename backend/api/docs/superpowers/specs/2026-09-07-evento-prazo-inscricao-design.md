# Prazo de inscrição opcional no evento — design

> Spec de feature. Segue o roadmap de produção (`CLAUDE.md`) e o padrão dos specs
> anteriores em `docs/superpowers/specs/`. Estado do schema no início: **V37**.

## Problema

Hoje a inscrição num evento fecha sozinha só quando o evento passa a `EM_ANDAMENTO`
(situação derivada de `inicioEm`/`fimEm`, checada em `InscricaoService.validarEventoAberto`).
Não há como encerrar as inscrições **antes** disso. Igrejas precisam disso pra fechar a
lista com antecedência — comprar material, organizar transporte, imprimir crachá, saber
quantos vão.

## Objetivo

Um campo **opcional** "inscrições até" no evento. Passado o prazo:

- **membro comum** não se inscreve sozinho — o botão fica desabilitado com mensagem clara;
- **convidado por link público** não se inscreve — o formulário some, com mensagem;
- **admin/líder** não são bloqueados: o botão continua ativo e um aviso explica *"O prazo
  de inscrição deste evento encerrou em DD/MM, mas você como admin/líder pode inscrever
  assim mesmo."* — vale tanto pra auto-inscrição quanto pro fluxo de "Inscrever
  alguém/pessoas";
- o cancelamento da própria inscrição continua livre **por padrão**, mas quem cria o
  evento pode travar a lista (ninguém entra nem sai depois do prazo);
- notificações in-app avisam os envolvidos (inscritos com pagamento pendente e
  responsáveis do evento).

## Fora de escopo (decisões explícitas)

- **Notificação filtrada por elegibilidade + regra do KIDS** (avisar "novo evento" só a
  quem pode participar, invertendo pra eventos de criança). É mudança de comportamento
  numa notificação que já existe (`NOVO_EVENTO`) e tem casos de borda próprios (quem é
  "adulto"? pessoa sem `data_nascimento`? evento sem restrição?) — merece brainstorm
  dedicado. As notificações **deste** spec vão pra inscritos e responsáveis, que já são o
  público certo sem filtro.
- **Prazo relativo em série** ("fecha N dias antes do início"). Série usa **data absoluta
  por ocorrência** (decisão do autor). O deslocamento relativo seria um modelo novo
  (número + unidade + cálculo), não só mais um campo propagado — fica pra depois se
  houver necessidade real.
- **Lembrete de prazo por e-mail.** Só notificação in-app nesta entrega.
- **Waitlist / reabertura de vaga** quando alguém cancela depois do prazo.

## Modelo de dados

### Migration `V38__evento_prazo_inscricao.sql`

```sql
ALTER TABLE evento
  ADD COLUMN inscricoes_ate               TIMESTAMP,                       -- nulo = sem prazo (comportamento de hoje)
  ADD COLUMN permite_cancelar_apos_prazo  BOOLEAN NOT NULL DEFAULT TRUE,   -- só tem efeito quando inscricoes_ate não é nulo
  ADD COLUMN aviso_prazo_proximo_em       TIMESTAMP,                       -- carimbo do job (dedup)
  ADD COLUMN aviso_prazo_fechado_em       TIMESTAMP;                       -- carimbo do job (dedup)

ALTER TABLE inscricao_evento
  ADD COLUMN aviso_prazo_incompleto_em    TIMESTAMP;                       -- carimbo do job (dedup)
```

Sem `CHECK` de `inscricoes_ate <= inicio_em` no banco: a validação real fica no
`EventoService` (mesma escolha de `chk_evento_localizacao_unica` → `resolverLocalizacao`).

### Entidades

`Evento` (`modules/evento/Evento.java`):

- `LocalDateTime inscricoesAte` — nullable. Mesmo tipo de `inicioEm`/`fimEm`.
- `boolean permiteCancelarAposPrazo` — default `true`.
- `LocalDateTime avisoPrazoProximoEm`, `LocalDateTime avisoPrazoFechadoEm` — nullable,
  estado do job.

`InscricaoEvento` (`modules/evento/inscricao/InscricaoEvento.java`):

- `LocalDateTime avisoPrazoIncompletoEm` — nullable, estado do job.

### Situação derivada — `SituacaoInscricao`

Novo enum em `modules/evento/inscricao/`, **calculado na hora** (não é coluna), pra front
e validações falarem a mesma língua:

| Valor | Condição |
|---|---|
| `ENCERRADA_POR_INICIO` | evento já `EM_ANDAMENTO`/`ENCERRADO` (o fechamento automático que já existe) — **checado primeiro** |
| `ENCERRADA_POR_PRAZO` | evento ainda não começou **e** `inscricoesAte != null && agora > inscricoesAte` |
| `ABERTA` | os demais casos (sem prazo, ou `agora <= inscricoesAte`, com evento não começado) |

A ordem importa: como a validação garante `inscricoesAte <= inicioEm`, um evento já
começado também está "depois do prazo" — mas o estado correto pra exibir é
`ENCERRADA_POR_INICIO`, então ele é testado antes.

Método na entidade `Evento` (`getSituacaoInscricao()`), ao lado de `getSituacao()`.
Sem dimensão de role: a situação diz só o estado do prazo. Quem pode furá-lo (admin/líder)
é decisão do serviço; o front cruza `situacaoInscricao` com a role do usuário logado pra
escolher entre "botão desabilitado" e "botão ativo + aviso".

### Propagação de série

`inscricoesAte` e `permiteCancelarAposPrazo` entram na lista de campos que "editar a
série toda" propaga pras ocorrências, respeitando `divergeDaSerie` (mesma máquina dos
demais campos de `Evento`). Os três carimbos de aviso **nunca** propagam — são estado
local de cada ocorrência.

### Validação (`EventoService`, criação e edição)

Quando `inscricoesAte != null`:

- exige `inscricoesAte <= inicioEm` — erro `PRAZO_APOS_INICIO` ("O prazo de inscrição tem
  que ser antes do início do evento.");
- exige `requerInscricao == true` — erro `PRAZO_SEM_INSCRICAO` ("Prazo de inscrição só faz
  sentido com inscrição obrigatória ligada.").

Sem regra sobre o passado: editar um evento antigo pode deixar um prazo já vencido.

## Enforcement no backend

### Guarda de inscrição

Novo método em `InscricaoService`, chamado ao lado da `validarEventoAberto` que já existe:

```java
/** Prazo de inscrição (V38). Nulo = sem prazo. Admin/líder passam (pergunta pela
 *  CAPACIDADE, não pelo nome do perfil — CLAUDE.md); o caminho público não tem role,
 *  então nunca passa. */
private void validarPrazoInscricao(Evento evento, String role) {
    if (evento.getInscricoesAte() == null) return;
    if (!LocalDateTime.now().isAfter(evento.getInscricoesAte())) return;
    if (Permissoes.podeGerenciarInscricoes(role)) return;
    throw new BusinessException("PRAZO_INSCRICAO_ENCERRADO",
        "O prazo de inscrição neste evento encerrou em "
        + formatarDataHora(evento.getInscricoesAte()) + ".");
}
```

Onde entra:

| Caminho | Método | Comportamento no prazo vencido |
|---|---|---|
| Auto-inscrição do membro logado | `inscrever` → `inscreverInterno` | comum barra; admin/líder passa |
| Reinscrição (reaproveita linha `CANCELADA`) | mesmo `inscreverInterno` | idem (é inscrição nova pra regra) |
| Lote do admin/líder | `inscreverPessoas` | passa (capacidade de gestão) |
| Convidado sem cadastro (link público) | `inscreverConvidado` | sempre barra (sem role → `podeGerenciarInscricoes(null)` é `false`) |

A guarda pergunta pela **capacidade** (`podeGerenciarInscricoes(role)`), não por qual
método chamou — então o mesmo comportamento vale se amanhã um admin usar o caminho de
auto-inscrição pra si.

Ordem de avaliação num caminho de inscrição: evento aberto → **prazo** → elegibilidade
(sexo/idade/estado civil/exclusivo-membros) → vaga. O prazo é uma porta independente,
como as restrições de elegibilidade.

### Guarda de cancelamento

O método `cancelar(inscricaoId, usuarioId, meuMembroId, role, igrejaId)` já trata os dois
casos (`souEu` via `meuMembroId`, `gestorDaMesmaIgreja` via `podeGerenciarInscricoes`).
Nova checagem:

```java
private void validarCancelamentoPermitido(Evento evento, boolean souEu, boolean ehGestor) {
    if (ehGestor) return;                                     // gestor remove pela lista mesmo depois do prazo
    if (!souEu) return;                                       // outro erro já barra esse caso
    if (evento.getInscricoesAte() == null) return;
    if (evento.isPermiteCancelarAposPrazo()) return;
    if (!LocalDateTime.now().isAfter(evento.getInscricoesAte())) return;
    throw new BusinessException("CANCELAMENTO_ENCERRADO_POR_PRAZO",
        "Depois do prazo de inscrição não dá mais pra cancelar sua inscrição neste evento. "
        + "Fale com a organização.");
}
```

A expiração automática de cobrança não paga (`CobrancaEventoExpiracaoJob`) **não** passa
por essa guarda — é o sistema cancelando, não a pessoa.

### DTOs

- `EventoResponse` / `EventoRequest`: `inscricoesAte`, `permiteCancelarAposPrazo`.
- `EventoResponse`: `situacaoInscricao` (enum como string).
- Resposta do convite público (`ConviteController.buscarPorToken` / o DTO que ele
  devolve): `situacaoInscricao`.
- Services retornam DTOs, nunca entidades (padrão do projeto).

## Job diário de notificação

Nova classe `PrazoInscricaoJob` em `modules/evento/inscricao/`.
`@Scheduled(cron = "0 30 6 * * *")` — 06:30, depois da renovação de token do Mercado Pago
(06:00). `@Transactional` por evento processado (uma falha não derruba os outros).

Config: `app.eventos.aviso-prazo-dias` (default `3`) — dias antes do prazo em que o aviso
"chegando" dispara.

Query base: eventos não deletados, `requer_inscricao = true`, `inscricoes_ate IS NOT NULL`,
`inicio_em > now()` (evento já começado → prazo irrelevante).

### As três notificações

| # | `TipoNotificacao` (novo) | Quando | Destinatário | Dedup |
|---|---|---|---|---|
| 1 | `PRAZO_INSCRICAO_INCOMPLETA` | `inscricoesAte - N dias <= agora < inscricoesAte` | cada `inscricao_evento` `AGUARDANDO_PAGAMENTO` cuja pessoa tem `usuario` | `inscricao.aviso_prazo_incompleto_em IS NULL` → carimba |
| 2 | `PRAZO_INSCRICAO_PROXIMO` | mesma janela do #1 | cada responsável do evento com `usuario` | `evento.aviso_prazo_proximo_em IS NULL` → carimba |
| 3 | `PRAZO_INSCRICAO_FECHADO` | `agora >= inscricoesAte` | cada responsável do evento com `usuario` | `evento.aviso_prazo_fechado_em IS NULL` → carimba |

(O tipo #3 é `PRAZO_INSCRICAO_FECHADO`, não `..._ENCERRADO` — esse nome já é o código do
`BusinessException` da guarda; nomes distintos evitam confusão nos logs.)

Notas:

- **#1 só existe em evento pago** — `AGUARDANDO_PAGAMENTO` só acontece com preço; evento
  gratuito nasce `CONFIRMADA`.
- **Responsável sem login** (`EventoResponsavel` com `nome_texto`, ou `pessoa` sem
  `usuario`) é pulado — notificação in-app precisa de `usuario_destinatario_id`. Sem
  e-mail nesta entrega.
- **Janela de #2 perdida** (app fora do ar nos N dias): `aviso_prazo_proximo_em` fica
  nulo pra sempre, sem problema — o #3 ainda dispara quando o prazo passa.
- **Evento sem responsável com login**: #2 e #3 simplesmente não saem (nada a fazer nesta
  entrega). Futuro possível: cair pro criador do evento (`criadoPorUsuarioId`).
- **Idempotente**: os três checam o carimbo antes de agir; rodar o job 2× no mesmo dia
  não duplica nada.

### Textos (in-app, curtos — padrão do `NotificacaoService`)

- **#1:** `O prazo de inscrição em "<evento>" encerra em <data>. Falta pagar <valor> pra garantir sua vaga.` → link pro checkout da cobrança.
- **#2:** `As inscrições de "<evento>" encerram em <data> (<N> dias).` → link pro detalhe do evento.
- **#3:** `As inscrições de "<evento>" encerraram. <X> confirmados, <Y> aguardando pagamento.` → link pra lista de inscritos.

### `TipoNotificacao`

Três valores novos no enum. `NotificacaoService` não muda — o produtor (o job) monta o
texto e resolve o destinatário, como já é o contrato.

## Frontend

### Form de evento (`EventoForm.tsx`)

Dentro do bloco `requerInscricao` (junto de vagas/preço), num `<Revelar>`:

- **"Inscrições até"** — `datetime-local` opcional. Ajuda/placeholder: *"Ex.: 15/03/2026
  23:59 — deixe vazio pra aceitar inscrições até o evento começar"*.
- **Checkbox "Permitir cancelamento após o prazo"** — marcado por padrão. Ajuda:
  *"Desmarque para travar a lista de inscritos no prazo — ninguém entra nem sai depois"*.
  Só aparece quando "Inscrições até" tem valor (`<Revelar>` aninhado).
- Validação Zod: se preenchido, `inscricoesAte <= inicioEm` (*"O prazo tem que ser antes
  do início do evento"*).
- Schema/tipos: `inscricoesAte?: string | null`, `permiteCancelarAposPrazo: boolean` no
  `EventoRequest`/`EventoResponse` + união de tipos.

### Card do evento na lista (`EventoCard` + selo de prazo)

O `SelosInscricaoCard` atual só renderiza pra quem já se inscreveu (early-return em
`if (!minha?.inscrito) return null`). Extrair o selo de prazo pra um componente próprio
(`SeloPrazoInscricao`) montado sempre que há prazo:

- `ABERTA` + prazo existe → selo neutro *"Inscrições até 15/03"*.
- `ABERTA` + faltam ≤ N dias → selo de destaque (âmbar) *"Inscrições até 15/03 · faltam 3 dias"*.
- `ENCERRADA_POR_PRAZO` → selo apagado *"Inscrições encerradas"*.
- `ENCERRADA_POR_INICIO` → nada novo (o "Você participou" existente cobre).

### Drawer de detalhe (`DrawerDetalheEvento.tsx`)

- Linha nova quando há prazo: *"Inscrições até: 15/03/2026, 23:59"* + estado.
- Quando `situacaoInscricao === 'ENCERRADA_POR_PRAZO'`, o front cruza com a role do
  usuário logado:
  - **comum:** botão "Inscrever-se" `disabled`, mensagem *"Inscrições encerradas em 15/03"*.
  - **admin/líder:** botão **ativo**, com um aviso (`<Transicao modo="fade">`) acima:
    *"O prazo de inscrição encerrou em 15/03, mas você como \<admin|líder\> pode inscrever
    assim mesmo."*
- A checagem de verdade é no back — o front só reflete. Rótulo do perfil vem de uma
  função/constante única (`CLAUDE.md`: nome do perfil aparece em um arquivo só).

### Modal "Inscrever alguém/pessoas" (`ModalInscreverAlguem` / `ModalInscreverPessoas`)

Quando o evento está `ENCERRADA_POR_PRAZO`, mostrar o mesmo aviso no topo do modal — o
fluxo continua funcionando (é gestor-only e o back deixa passar).

### Convite público (`app/convite/[token]/page.tsx`)

Quando a resposta traz `situacaoInscricao === 'ENCERRADA_POR_PRAZO'`:

- não renderiza `<FormularioConvidado>`;
- mostra *"As inscrições para este evento encerraram em 15/03."* — mesmo tratamento visual
  do "link não é mais válido" que já existe.

### Erros novos no handler de API do front

`PRAZO_INSCRICAO_ENCERRADO`, `CANCELAMENTO_ENCERRADO_POR_PRAZO`, `PRAZO_APOS_INICIO`,
`PRAZO_SEM_INSCRICAO` → mensagem amigável via `notificar()` (padrão do projeto, sem toast
do sonner, sem `window.confirm`).

### Responsividade + animação

Selo do card entra no fluxo dos selos existentes (sem layout novo). Campos do form usam
`<Revelar>`. Estado "encerradas" no convite público usa `<Transicao modo="fade">`. Nenhum
modal/drawer novo — sem bottom-sheet a fazer. Validar em viewport de iPhone **e** Android.

## Testes

Regra de ouro: o teste prova a feature. Camadas conforme `CLAUDE.md`.

### `InscricaoServiceTest` (Mockito puro — cresce com a classe)

- `recusaAutoInscricaoDeComumDepoisDoPrazo()` — membro comum, prazo vencido → `PRAZO_INSCRICAO_ENCERRADO`.
- `permiteAutoInscricaoDeAdminDepoisDoPrazo()` — admin pelo caminho de auto-inscrição passa.
- `permiteAutoInscricaoDeComumAntesDoPrazo()`.
- `loteDoGestorInscreveDepoisDoPrazo()` — `inscreverPessoas` passa (capacidade de gestão).
- `convidadoPublicoRecusadoDepoisDoPrazo()` — `inscreverConvidado`.
- `reinscricaoRecusadaDepoisDoPrazo()` — linha `CANCELADA` + prazo vencido.
- `semPrazoInscreveComoHoje()` — `inscricoesAte == null`.
- `cancelamentoBloqueadoQuandoToggleDesligadoEPrazoVencido()` — self, comum → `CANCELAMENTO_ENCERRADO_POR_PRAZO`.
- `cancelamentoLivreQuandoToggleLigado()`.
- `gestorCancelaMesmoComToggleDesligado()`.
- `expiracaoAutomaticaDeCobrancaIgnoraTravaDeCancelamento()`.

### `EventoServiceTest`

- `recusaPrazoDepoisDoInicio()` — `inscricoesAte > inicioEm` → `PRAZO_APOS_INICIO`.
- `recusaPrazoSemRequerInscricao()` → `PRAZO_SEM_INSCRICAO`.
- `edicaoDeSeriePropagaPrazoParaOcorrencias()`.
- `ocorrenciaDivergenteMantemProprioPrazo()`.

### `PrazoInscricaoJobTest` (novo — Mockito puro com mocks dos repositories)

- `criaAvisoDeIncompletaUmaVezSo()` — job roda 2×, notificação criada 1×, carimbo gravado.
- `criaAvisoDeProximoParaResponsavelComLogin()`.
- `pulaResponsavelSemLogin()`.
- `criaAvisoDeFechadoAoPassarDoPrazo()`.
- `naoAvisaEventoJaComecado()`.
- `avisoIncompletaSoParaEventoPago()` — evento gratuito, inscrição `CONFIRMADA` → nada.
- `janelaDeProximoPerdidaNaoImpedeAvisoDeFechado()`.

### Harness de controller (`@SpringBootTest`) — só o necessário

- `InscricaoControllerTest`: 1 teste ponta-a-ponta de 400 com `PRAZO_INSCRICAO_ENCERRADO`.
- Migration V38: teste de que as colunas existem e `permite_cancelar_apos_prazo` tem
  default `true`.

### Front

Sem infra de teste (dívida conhecida) → validação manual: form (campo + toggle +
validação Zod), card (3 estados do selo), drawer (comum vê botão desabilitado, admin/líder
vê aviso e consegue inscrever), modal de "Inscrever alguém/pessoas" com aviso, convite
público encerrado, mobile (iPhone + Android).

## Ordem de entrega (pedaços testáveis)

Cada pedaço = commit único **depois** de o autor testar. Não construir tudo e despejar no
fim (`CLAUDE.md`).

1. **Modelo + validação (back).** Migration V38, campos nas entidades,
   `SituacaoInscricao`, validação no `EventoService`, propagação de série. Testes:
   `EventoServiceTest` + migration.
   *Pronto quando:* criar/editar evento com prazo salva e valida; série propaga.
2. **Enforcement (back).** `validarPrazoInscricao` + `validarCancelamentoPermitido` nos
   caminhos certos, erros novos, DTOs de evento e do convite expõem os campos +
   `situacaoInscricao`. Testes: `InscricaoServiceTest` + 1 no `InscricaoControllerTest`.
   *Pronto quando:* auto-inscrição de comum barra no prazo, admin/líder e lote de gestão
   passam, público barra, cancelamento respeita o toggle.
3. **Front do fluxo principal.** Campo + toggle no `EventoForm` (Zod, `<Revelar>`), selo
   no card (3 estados), linha no drawer, botão desabilitado pra comum / aviso pra
   admin-líder, aviso no modal de "Inscrever alguém/pessoas", erros no `notificar()`.
   Mobile.
   *Pronto quando:* dá pra criar evento com prazo pela tela, o card mostra o estado
   certo, comum vê botão desabilitado fora do prazo, admin/líder vê o aviso e consegue
   inscrever.
4. **Convite público.** `ConviteController` devolve `situacaoInscricao`; a página troca o
   formulário pelo estado "inscrições encerradas". Mobile.
   *Pronto quando:* link público de evento com prazo vencido não deixa mais inscrever,
   com mensagem clara.
5. **Job de notificação.** `PrazoInscricaoJob`, 3 tipos em `TipoNotificacao`, carimbos,
   config `app.eventos.aviso-prazo-dias`. Testes: `PrazoInscricaoJobTest`.
   *Pronto quando:* rodar o job gera as 3 notificações uma vez só, pros destinatários
   certos.

## Impacto no diagrama ER (`CLAUDE.md`)

Atualizar o bloco `EVENTO` com `inscricoes_ate`, `permite_cancelar_apos_prazo`,
`aviso_prazo_proximo_em`, `aviso_prazo_fechado_em`; o bloco `INSCRICAO_EVENTO` com
`aviso_prazo_incompleto_em`; a nota de "Cadastro de evento enriquecido" mencionando o
prazo como 5ª porta de inscrição. Bump do estado do schema para **V38**.
