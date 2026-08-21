# Backlog — Pré-venda (o que falta pra vender pra outras igrejas)

> Levantado em 2026-08-20, em brainstorm com o autor. **Critério de entrada aqui:** sem isso,
> não dá pra abrir o cadastro pra igreja de fora e cobrar com segurança — seja por faltar
> funcionalidade que os concorrentes já têm, seja por faltar a própria capacidade de operar o
> negócio (ver quem pagou, revisar o que a pessoa aceitou). O que é "bom ter" mas não bloqueia
> o lançamento foi pra `BACKLOG-MELHORIAS-FUTURAS.md`.
>
> Isto complementa, não substitui, a Fase 5/6 do `CLAUDE.md` — é o detalhamento delas em
> tarefas, mais o que o brainstorm de 2026-08-20 achou que faltava no roadmap original.

---

## Ordem sugerida (por dependência, não por prioridade de negócio)

```
1. Estudo de provedor de pagamento
       │
       ├──▶ 2. Cobrança de assinatura do Domus (planos)
       └──▶ 3. Cobrança de evento pago

4. Central de notificações (in-app)           — independente, mas 5/13 dependem dela pra avisar
5. Contas a pagar (registrar/lembrar)         — usa a central (item 4) pro lembrete de vencimento
6. Recorrência de evento (Spec C)             — independente
7. Campos personalizados + formulário público — independente
8. Rótulo self-service por igreja             — independente, mas barato fazer com 9/10
9. Fluxo comercial (cadastro público + onboarding)
10. Painel admin interno (gestão de tenants)   — precisa existir ANTES do cadastro abrir de vez
11. Importação de pessoas via CSV
12. Revisão de Termos/Privacidade pra cobrança
13. Tela de início: mural + feed da comunidade — usa a central (item 4) pra avisar de curtida/comentário
```

O único encadeamento rígido é 1 → 2/3 (sem provedor escolhido, não tem como cobrar nada). O
item 4 (central de notificações) é o segundo mais "de baixo" — vale fazer cedo porque os
itens 5 e 13 já nascem esperando ter onde avisar o usuário, em vez de cada um inventar o
próprio mecanismo de aviso. Fora isso, a ordem é só "o que destrava mais coisa primeiro".

---

## 1. ~~Estudo de provedor de pagamento~~ RESOLVIDO (2026-08-20)

**Não era build — era decisão.** Repetia o que a Fase 6 do `CLAUDE.md` sempre pedia, nunca
feito antes. Ver documento completo com comparativo, recomendação e fontes:
**[`ESTUDO-PROVEDOR-PAGAMENTO.md`](./ESTUDO-PROVEDOR-PAGAMENTO.md)**.

**Decisão: Mercado Pago**, um único provedor pros dois casos de uso (cobrança de assinatura
do Domus via produto "Assinaturas", e cobrança de evento pago via split nativo). Stripe
descartado (PIX não funciona em conta configurada no Brasil). Asaas cogitado só pro caso (a),
descartado por ora pra evitar dois provedores/dois webhooks logo de saída — não é porta
fechada, ver o documento.

Itens 2 e 3 abaixo já têm por onde começar.

---

## 2. Cobrança de assinatura do Domus (planos)

Depende do item 1. Constrói:

- Modelo de assinatura recorrente por igreja (campo `plano` já existe em `Igreja`, hoje é
  texto livre sem cobrança de verdade atrás).
- Webhook do provedor pra saber quando o pagamento falha/atrasa (dunning) — decidir o que
  acontece com a conta que não paga: bloqueia leitura, bloqueia escrita, ou só avisa?
- Trial period (quantos dias grátis antes de cobrar?) e fluxo de cancelamento self-service
  (a igreja consegue cancelar sem falar com você, ou isso passa pelo painel admin do item 10?).
- Ver seção **"Rascunho de faixas de plano"** abaixo — não é decisão de código, mas trava o
  desenho de quais campos/limites o backend precisa checar.

### Rascunho de faixas de plano (proposta pra você reagir, não decisão fechada)

Padrão comum em SaaS de gestão de igreja/associação: cobrar por **tamanho** (nº de pessoas
ativas — métrica que o Domus já rastreia, `PESSOA` não arquivada) em vez de por
funcionalidade, porque funcionalidade trancada atrás de plano caro é o que mais gera
reclamação e todo mundo acaba usando o Google Forms por fora pra escapar da trava. Sugestão:

| Faixa | Até quantas pessoas ativas | O que muda |
|---|---|---|
| **Piloto/Starter** | até ~80 | Tudo liberado, preço mais baixo — igrejas pequenas são o público mais fácil de fechar primeiro e o custo de servidor por igreja pequena é baixo |
| **Growth** | até ~300 | Mesmo tudo, preço intermediário |
| **Completo** | acima de ~300 | Preço maior, ou negociado — igreja grande já é outro tipo de venda (demonstração, talvez suporte prioritário) |

**Contas a pagar (item 5) e cobrança de evento (item 3) eu deixaria liberados em TODOS os
planos** — são a diferença competitiva que faz a igreja trocar de sistema, não faz sentido
esconder atrás de plano caro logo no lançamento. Reavaliar depois de ter dado de uso real.

Números exatos (R$, limite de pessoas) são decisão sua — o formato acima (por tamanho, tudo
liberado) é a parte que eu recomendo com mais confiança.

---

## 3. Cobrança de evento pago

Depende do item 1. Hoje `evento.preco` é só informativo (Spec A, Fase 2) — a inscrição não
cobra nada de verdade. Constrói:

- Checkout de verdade na inscrição (PIX/cartão via provedor escolhido).
- **Split**: dinheiro cai na conta da igreja, não do Domus (decisão do item 1 já cobre isso).
- Reembolso ao cancelar inscrição — reaproveita `InscricaoService` (cancelamento já existe,
  falta só a perna financeira).
- Estado da inscrição precisa de um status novo (`AGUARDANDO_PAGAMENTO`?) além de
  `CONFIRMADA`/`CANCELADA` — vaga só é ocupada depois do pagamento confirmar, senão alguém
  reserva vaga sem nunca pagar.

---

## 4. ~~Central de notificações (in-app)~~ RESOLVIDO (2026-08-21)

Implementado exatamente como desenhado abaixo, com um ajuste no brainstorm: entrada na célula
notifica **todos os membros da célula, exceto quem acabou de entrar** (não só o líder, como o
texto original sugeria). `TipoNotificacao` (enum) + `NotificacaoService.criar(...)` fachada
única, chamada síncrona de cada produtor. 8 produtores ligados na v1 (mais do que os 3
originalmente listados aqui — pedido de ministério, entrada em célula, acesso concedido,
inscrição em evento com responsável, promoção a líder de célula, evento mudando/cancelado,
pedido de vínculo de família, exclusão de conta agendada perto do prazo). Sino no `TopBar`
com badge, dropdown, marcar lida/todas. Ver spec completo em
`docs/superpowers/specs/2026-08-20-central-notificacoes-design.md` e o plano de implementação
em `docs/superpowers/plans/2026-08-20-central-notificacoes.md`.

**Fechamento (2026-08-21):** entrega passou a ser instantânea via SSE (`GET
/notificacoes/stream`, `NotificacaoSseRegistry` em memória — dispensa Redis pub/sub por ora,
YAGNI de 1 instância só) em vez de só o polling de 60s (que virou rede de segurança). Achado
testando: `ResponseEntity<SseEmitter>` quebra o dispatch assíncrono do Spring — o controller
tem que retornar `SseEmitter` puro; e a rewrite genérica do Next não serve pra stream que
nunca termina, por isso existe uma rota dedicada em `app/api/notificacoes/stream/route.ts`
com `http.request` puro do Node. Também: mais produtores (responsável de evento definido,
evento novo cadastrado avisando toda a igreja, entrada/saída de célula, entrada/saída de rede
— inclusive pedido aceito —, mudança de dia/horário de célula), supressão de auto-notificação
em todo produtor onde o ator podia ser o próprio destinatário, e correção do rótulo
"ministério" → "Rede" nos textos (sem duplicar quando o nome cadastrado já começa com
"Rede"). 12 tipos de notificação no total.

Texto original do brainstorm mantido abaixo por contexto:

**Achado no brainstorm de 2026-08-20** — não existia nenhum mecanismo de notificação dentro do
produto até agora (só e-mail transacional avulso, um por feature: convite, reset de senha).
Sem uma central única, cada feature nova (contas a pagar vencendo, comentário no mural,
inscrição confirmada) reinventaria o próprio jeito de avisar — e o usuário nunca teria um
lugar só pra ver tudo que aconteceu.

- **Entidade nova**: `NOTIFICACAO` (`igreja_id`, `pessoa_id` ou `usuario_id` — destinatário,
  `tipo`, `texto`, `link` opcional pra onde clicar leva, `lida` boolean, `criada_em`). Isolamento
  multi-tenant igual a tudo mais (`igreja_id` sempre do JWT).
- **Sino de notificação** na `TopBar` (mesmo lugar de qualquer app com esse padrão): contador
  de não-lidas, dropdown com a lista, marcar uma ou todas como lida.
- **Quem gera notificação** (v1, produtores conhecidos já no backlog):
  - Contas a pagar vencendo (item 5).
  - Curtida/comentário no mural (item 13).
  - Acesso concedido / convite aceito (reaproveita o que já dispara e-mail — passa a também
    gerar notificação in-app, não só e-mail).
- **Decisão de desenho**: notificação in-app é **sempre gerada junto** com o evento de negócio
  (ex.: `ContaAPagarService` grava a `NOTIFICACAO` na mesma transação que marca vencimento
  próximo) — não um sistema de eventos assíncrono separado. Mais simples, e o volume de uma
  igreja não justifica fila de mensageria própria por enquanto.
- **E-mail e notificação in-app não são a mesma coisa**: e-mail é pra avisos que precisam
  alcançar quem não está com o Domus aberto (convite, reset de senha); notificação in-app é
  pra "aconteceu algo, vê quando abrir". Alguns eventos (ex.: convite) fazem sentido nos dois
  canais; outros (ex.: curtida) só fazem sentido in-app — decidir caso a caso ao construir
  cada produtor, não regra geral única.

**Fora desta entrega:** push notification (exige PWA/app, ver `BACKLOG-MELHORIAS-FUTURAS.md`)
e preferências de notificação por usuário (silenciar um tipo específico) — v1 é tudo ligado,
sem configuração.

---

## 5. Contas a pagar (registrar e lembrar — sem executar pagamento)

**Escopo decidido em 2026-08-20:** só ledger + lembrete. Domus **não** manda dinheiro pro
fornecedor nessa entrega — isso vira item em `BACKLOG-MELHORIAS-FUTURAS.md` ("contas a pagar
executando o pagamento").

- Cadastro de conta a pagar: fornecedor (texto livre, sem cadastro próprio — mesmo tratamento
  de `tipo` do evento, YAGNI de tabela nova), valor, vencimento, categoria (reusa
  `categoria_financeira`, tipo `SAIDA`).
- Lembrete de vencimento — usa a central de notificações (item 4) e/ou e-mail (reusa
  `EmailService`).
- Marcar como paga → vira `movimentacao_financeira` do tipo `SAIDA` automaticamente (não
  duplica lançamento manual).
- Lista de contas a pagar em aberto/pagas/atrasadas — tela nova, parecida com
  `/financeiro/movimentacoes`.

---

## 6. Recorrência de evento (Spec C)

Já estava na Spec C do roadmap (`docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`, seção "Módulo de
eventos"). Confirmado como essencial agora (2026-08-20) — antes estava "esperando uso real",
mas pra vender pra igreja de fora, cadastrar o culto toda semana na mão é um dos primeiros
atritos que uma igreja notaria comparando com concorrente.

- Cadastrar o culto **uma vez** em vez de recriar toda semana.
- Pergunta de desenho que já estava anotada: editar um culto muda **aquele dia** ou **a série
  inteira**? Cancelar um feriado é uma **exceção** da série, não edição.
- Referência: qualquer calendário maduro (Google Calendar) já resolveu essa UX — não
  reinventar do zero, copiar o modelo mental (série vs. só esta ocorrência).

---

## 7. Campos personalizados + formulário público (Spec D, com escopo maior)

Já estava na Spec D do roadmap, mas o escopo cresceu no brainstorm de 2026-08-20: além de
"pergunta extra no cadastro de inscrição" (ex.: tamanho de camiseta), agora inclui **inscrição
de gente de fora sem login**, via link — funciona como um Google Forms embutido no evento.

Duas capacidades, construídas juntas (são a mesma spec, o público-sem-login é só um modo do
formulário):

- **Campos personalizados no evento**: admin/líder marca "este evento precisa de dado extra"
  e monta o formulário (tipo de campo, obrigatório ou não, texto/opção/checkbox). Respostas
  ficam vinculadas à inscrição, visíveis em relatório.
- **Link público de inscrição**: evento com esse modo ligado gera uma URL sem autenticação
  (`/inscricao-publica/{token}` ou parecido) — pessoa de fora preenche nome/e-mail/telefone +
  os campos personalizados, sem precisar virar `pessoa` cadastrada na igreja. Decisão de
  desenho: a inscrição pública vira um registro à parte (não uma `Pessoa`) ou cria uma `Pessoa`
  com `vinculo=CONGREGANTE` automaticamente? Precisa decidir antes de desenhar o schema —
  afeta se essa pessoa aparece depois nas listagens normais da igreja.
- Vagas/lotação do formulário público valem a mesma regra de vagas do evento normal (lock
  pessimista já existe, reusa).
- Link **não pode vazar dado de outra igreja** — token por evento, escopado por `igreja_id`
  como tudo mais no sistema.

**Fora desta entrega, fica pra depois (não pediu, YAGNI):** builder visual arrastar-e-soltar
de formulário — a v1 é uma lista de campos com tipo fixo (texto curto, texto longo, opção
única, múltipla escolha, sim/não), não um editor livre de layout.

---

## 8. Rótulo self-service por igreja

Já era item conhecido no backlog antigo (`rotulo-ministerio-self-service-fase5` na memória,
`Rótulo do módulo "Ministério" deveria ser self-service` no `BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`)
— o autor não lembrou dele no brainstorm de 2026-08-20, mas segue essencial: cada igreja
cliente vai chamar as coisas diferente.

- **Ministério** → hoje hardcoded "Rede" no front. Vira config por igreja (`igreja.rotulo_ministerio`
  + gênero, pro texto concordar certo — "Nova Rede" vs. "Novo Departamento").
- **Congregação** → hoje hardcoded "Unidade". Mesmo padrão, mesma tabela de config,
  provavelmente a mesma tela de onboarding pergunta os dois de uma vez ("como sua igreja
  chama isso: Ministério/Departamento/Rede? E isso: Congregação/Unidade/Campus?").
- **Célula** também pode entrar no mesmo mecanismo (a spec de células já cogitava isso).

Desenhar a config genérica UMA vez (nome do módulo + gênero, por igreja) e aplicar nos três
lugares, em vez de três mecanismos separados.

---

## 9. Fluxo comercial (cadastro público + onboarding)

Itens 1 e 2 da Fase 5 do `CLAUDE.md`, sem novidade — só copiado aqui pra ficar tudo num lugar
só:

- **Expor o cadastro publicamente.** Hoje é uso interno/piloto (sem self-service real). O
  cadastro via Google já existe (Fase 1) — falta só tirar a trava que impede gente de fora.
- **Polir o onboarding pós-cadastro**: tela de boas-vindas + próximos passos (continuar
  cadastro da igreja, cadastrar a primeira pessoa, ir pro painel). Primeira impressão de quem
  nunca usou o Domus.
- **Aviso de acesso por e-mail**: quando o admin concede acesso a alguém (`concederAcesso`,
  já existe o e-mail de convite), reforçar que reusa o mecanismo já pronto — não é feature
  nova de verdade, só confirmar que está ligado no fluxo self-service também.
- Precisa decidir junto: cadastro público entra **direto** com trial, ou tem uma etapa de
  "fila de espera"/aprovação manual sua no início (útil pra não escalar suporte antes de estar
  pronto)? Ver painel admin (item 10) — se ele existir, dá pra aprovar/suspender manualmente
  sem travar o cadastro em si.

---

## 10. Painel admin interno (gestão de tenants)

**Não existe nada disso hoje** — achado no brainstorm de 2026-08-20, nem estava no roadmap.
Sem isso, "vender" significa operar o negócio inteiro direto no banco de dados.

- Lista de todas as igrejas clientes: nome, plano, status de pagamento, data de cadastro,
  quantas pessoas/usuários ativos.
- Suspender/reativar uma igreja (bloquear login de todo mundo daquela igreja — provavelmente
  um campo `igreja.suspensa` checado no `SecurityFilter`, parecido com o `usuario.ativo`).
- Ver detalhe de uma igreja (drill-down: quem são os usuários, últimos logins) — útil pra
  suporte ("fulano não consegue entrar, o que houve com a conta dele").
- Acesso restrito a um perfil novo, **fora** do `ADMIN_IGREJA`/`LIDER`/`ACESSO_COMUM` de hoje
  (esses são por igreja; isso aqui é cross-tenant, só você e quem mais operar o Domus).
  Decisão de desenho: usuário desse painel é uma tabela separada (`usuario_domus`?) ou um
  campo/flag no `usuario` normal com uma igreja "interna" reservada? A primeira opção isola
  melhor (não mistura o modelo de autorização por igreja com autorização cross-tenant).

---

## 11. Importação de pessoas via CSV

Sem isso, toda igreja que migra de planilha/outro sistema digita cadastro por cadastro — alto
atrito de troca, provável motivo de perder venda pra concorrente que já tem isso.

- Upload de CSV (ou colar dados) na tela de Pessoas, com mapeamento de coluna (nome, e-mail,
  telefone, data de nascimento, vínculo — nem toda planilha vai ter as colunas na mesma
  ordem/nome que o Domus usa).
- Validação linha a linha (mesma validação do cadastro manual — `@Valid` já existe nos DTOs,
  reusa) com relatório do que deu certo/errado, não tudo-ou-nada.
- E-mail duplicado (`pessoa.email` é único) precisa de uma decisão de UX: pula a linha, ou
  deixa a pessoa escolher se sobrescreve?
- Fora de escopo desta entrega: importação de outros módulos (eventos, financeiro histórico)
  — só pessoa é o que toda igreja migrando realmente precisa primeiro.

---

## 12. Revisão de Termos de Uso / Política de Privacidade pra cobrança

Os documentos atuais (`termo_aceite`, Fase 3) foram escritos pro **piloto gratuito**. Cobrar
de estranhos exige cobrir:

- Modelo de cobrança, cancelamento, reembolso (o que acontece se cancelar no meio do mês
  pago?).
- SLA/disponibilidade — o que o Domus promete (ou explicitamente não promete) de uptime.
- Ponto legal: vale revisão com advogado antes de cobrar de terceiros — isso não é trabalho
  de engenharia, é anotação pra você não esquecer.
- Reaproveita o mecanismo de reaceite já existente (versionamento de `termo_aceite`) — quando
  o texto mudar, usuário existente já é forçado a reaceitar (`ModalReaceitarTermos`).

---

## 13. Tela de início: mural de avisos + feed da comunidade

Feature nova de domínio — não existia nada parecido antes. Decisões já tomadas no brainstorm
de 2026-08-20: só ADMIN/LÍDER posta; qualquer pessoa da igreja pode curtir/comentar.

- **Mural de avisos**: lista simples, informativo (ex.: "reunião de célula essa semana",
  "não haverá culto no dia X") — mais parecido com aviso de mural físico que posto de rede
  social. Provavelmente não precisa de curtir/comentar, só leitura.
- **Feed estilo "postagem com foto e texto"**: devocional, testemunho, foto de um evento que
  aconteceu, com texto curto — curtir e comentar habilitados (reusa padrão de `<UploadFoto>`
  pra imagem).
- Entidades novas: `POSTAGEM` (igreja_id, autor, texto, foto opcional, tipo? AVISO/POSTAGEM),
  `CURTIDA` (postagem_id, pessoa_id — únique pra não curtir duas vezes), `COMENTARIO`
  (postagem_id, pessoa_id, texto).
- Curtida e comentário disparam notificação in-app pro autor da postagem, via central de
  notificações (item 4) — é o primeiro produtor "social" dela, os outros dois (item 5, convite)
  são administrativos.
- **Precisa entrar no Elasticsearch/outbox?** — mesma pergunta que todo módulo novo já
  levanta no backlog (ver `busca-global-pendencia-novos-modulos` na memória). Provavelmente
  não faz sentido buscar postagem na busca global — decidir e documentar, não deixar
  esquecido.
- Apagar o próprio comentário: sim. Apagar comentário de terceiro: só quem postou o mural
  original ou ADMIN — mesma régua de moderação de "é o meu conteúdo ou eu administro o
  espaço".
- **Fora desta entrega** (fica pro `BACKLOG-MELHORIAS-FUTURAS.md`): qualquer pessoa poder
  postar (não só admin/líder), notificação **push**/e-mail de nova postagem (a in-app da
  central já cobre curtida/comentário — push é canal à parte), denúncia de conteúdo — tudo
  dependente de moderação mais pesada, melhor com uso real primeiro.

---

## Coisas que ficaram de fora deste levantamento de propósito

Perguntadas e descartadas no brainstorm de 2026-08-20 (não por esquecimento — por decisão):

- **Programação do evento + equipe servindo (Spec E)** — confirmado como feature futura, não
  essencial pra vender.
- **Contas a pagar executando pagamento de verdade** — confirmado v1 é só ledger/lembrete.
- **Push notification** — a central de notificações (item 4) é só in-app na v1; push exige
  PWA/app, fica pra `BACKLOG-MELHORIAS-FUTURAS.md`.
