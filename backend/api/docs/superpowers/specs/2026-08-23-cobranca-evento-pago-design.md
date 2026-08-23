# Cobrança de evento pago (Mercado Pago) — design

> Item 3 do `BACKLOG-PRE-VENDA.md`, depende do item 1 (`ESTUDO-PROVEDOR-PAGAMENTO.md`,
> resolvido: Mercado Pago). Brainstorm em 2026-08-23. Substitui o `ModalConfirmarPagamento`
> atual (que só avisa "combine com a igreja", sem cobrar de verdade).

## Contexto

Hoje `evento.preco` é puramente informativo — a inscrição confirma na hora, sem nenhuma
cobrança de verdade acontecer. Isso bloqueia a igreja de vender evento pago pelo Domus (tem
que cobrar por fora, PIX manual/dinheiro, sem controle). Esta feature fecha esse ciclo:
checkout de verdade, dinheiro caindo direto na conta da igreja via split, e — decisão que
ampliou o escopo no brainstorm — cobrança **por pessoa**, com opção de cada pessoa inscrita
pagar a própria parte por um link, não só quem inscreveu pagando por todo mundo.

**Decisões de negócio já fechadas** (não é escopo desta spec renegociar):
- Provedor: Mercado Pago, um único provedor pros dois casos de uso (evento pago e, no
  futuro, assinatura do Domus — ver `ESTUDO-PROVEDOR-PAGAMENTO.md`).
- **0% de comissão do Domus** sobre evento pago nesta entrega — 100% do valor vai pra
  igreja. Monetização por evento pago fica para reavaliar depois de uso real; a
  comissão como conceito pode ser adicionada depois sem redesenhar o split (ver seção
  "Fora de escopo").
- Sem lotes de preço — `evento.preco` continua um valor fixo único por pessoa.
- PIX + cartão desde a v1, via Checkout Bricks (embutido, não redireciona pro Mercado
  Pago) — decisão explícita de não parecer amador redirecionando o usuário pra fora do
  app.

## Arquitetura geral

```
Igreja conecta conta MP (OAuth Connect)
        │
        ▼
  CONTA_PAGAMENTO_IGREJA (access_token/refresh_token criptografados)
        │
        ▼
Pessoa se inscreve em evento pago
        │
        ├─ titular: paga a própria parte agora (Payment Brick embutido)
        └─ outras pessoas inscritas junto (acompanhante ou pessoa cadastrada):
           escolha individual "eu pago agora" ou "gerar link"
                    │
                    ├─ "eu pago agora" → mesmo Brick, no mesmo fluxo
                    └─ "gerar link" → COBRANCA_EVENTO com token público
                                       → tela /cobranca/[token] (sem login)
                                       → mesmo Brick, mas standalone

        Cada cobrança individual → Mercado Pago (via token da igreja, split)
                    │
                    ▼
        Webhook único do marketplace → identifica cobrança por external_reference
                    │
                    ▼
        COBRANCA_EVENTO.status = PAGO → libera vaga definitiva, notifica titular
```

## Componentes

### 1. Conexão da igreja com o Mercado Pago

Nova seção em `/configuracoes/igreja` (dentro de Dados da Igreja ou aba própria
"Recebimentos"). Fluxo OAuth do Mercado Pago Connect: botão "Conectar Mercado Pago" abre
a tela de autorização do MP; ao voltar, o backend troca o `code` pelo par
access_token/refresh_token e grava.

**Entidade nova — `CONTA_PAGAMENTO_IGREJA`**
| Campo | Tipo | Nota |
|---|---|---|
| `id` | uuid PK | |
| `igreja_id` | uuid FK, UK | 1-1 com igreja |
| `mp_user_id` | varchar | id da conta MP conectada |
| `access_token` | varchar | **criptografado** (ver seção Segurança) |
| `refresh_token` | varchar | **criptografado** |
| `expira_em` | timestamp | access_token do MP expira; renovado via refresh antes de cada uso próximo do vencimento |
| `conectado_em` | timestamp | |
| `conectado_por_usuario_id` | uuid FK | auditoria, padrão já usado em `movimentacao_financeira`/`evento` |

Desconectar apaga a linha (hard delete — não é dado de domínio da igreja, é uma
credencial; não faz sentido soft delete de um token revogado). Reconectar cria uma nova.

**Checagem de pré-requisito**: qualquer tentativa de cobrar (criar `COBRANCA_EVENTO`)
sem `CONTA_PAGAMENTO_IGREJA` existente falha com erro de negócio (`IgrejaSemContaPagamento`
ou similar) — no front, vira aviso "Esta igreja ainda não conectou uma conta para receber
pagamentos" com atalho pra `/configuracoes/igreja`. Essa checagem também bloqueia
*publicar/salvar* um evento como pago sem conta conectada, pra não deixar o organizador
descobrir o problema só quando o primeiro inscrito tentar pagar.

### 2. Modelo de cobrança por pessoa

**Entidade nova — `COBRANCA_EVENTO`**
| Campo | Tipo | Nota |
|---|---|---|
| `id` | uuid PK | |
| `igreja_id` | uuid FK | isolamento multi-tenant |
| `evento_id` | uuid FK | |
| `inscricao_id` | uuid FK | a inscrição "guarda-chuva" a que essa cobrança pertence |
| `pessoa_id` | uuid FK, nulável | XOR com `acompanhante_id` |
| `acompanhante_id` | uuid FK, nulável | XOR com `pessoa_id` — CHECK garante exatamente um preenchido, mesmo padrão de `EVENTO.local_id`/`local_texto` |
| `valor` | numeric | snapshot de `evento.preco` no momento da inscrição (preço pode mudar depois, cobrança não) |
| `status` | varchar | `PENDENTE`\|`PAGO`\|`EXPIRADO`\|`CANCELADO`\|`REEMBOLSADO` |
| `mp_payment_id` | varchar, nulável | preenchido quando o pagamento é criado no MP |
| `token_link_publico` | varchar, UK, nulável | só preenchido quando vira link compartilhável |
| `expira_em` | timestamp | curto (15-30min) se "eu pago agora"; longo (24-48h) se link compartilhado |
| `pago_em` | timestamp, nulável | |
| `criado_por_usuario_id` | uuid FK | quem inscreveu/gerou a cobrança |

**Regra de negócio fixada no brainstorm**: o titular da inscrição sempre paga a própria
parte na hora (`"eu pago agora"` não é opcional pra ele) — só cobranças de acompanhante ou
de outra pessoa cadastrada que ele esteja inscrevendo podem virar link. Isso evita o
estado de uma inscrição inteira travada porque nem o próprio titular resolveu o
pagamento.

**Contagem de vagas** (mudança em relação ao evento grátis, onde vaga = inscrição
confirmada + acompanhantes contados direto): pra evento pago, uma pessoa (titular,
acompanhante ou outra pessoa inscrita) ocupa vaga quando a `COBRANCA_EVENTO` dela está
`PAGO` **ou** `PENDENTE` com `expira_em` no futuro. Isso precisa entrar na mesma consulta
de contagem de vagas que já existe (lock pessimista, Fase 2) — a vaga fica "reservada"
durante a janela de pagamento e libera se expirar sem pagar.

**Job de expiração** — `CobrancaEventoExpiracaoJob` (mesmo padrão do
`EventoSerieMaterializacaoJob`, um `@Scheduled` rodando a cada poucos minutos): busca
`COBRANCA_EVENTO` em `PENDENTE` com `expira_em` no passado, marca `EXPIRADO`, libera a
vaga. Frequência a decidir na implementação (ex.: a cada 5 minutos é suficiente dado que
o prazo mínimo é 15min).

### 3. Checkout — Mercado Pago Checkout Bricks

Payment Brick embutido no fluxo de inscrição (autenticado) e na tela pública
`/cobranca/[token]` (sem login — mesmo padrão de `/convite/[token]`). PIX e cartão na
mesma tela; dado de cartão tokenizado no navegador via SDK do Mercado Pago (não passa
pelo backend do Domus — fora do escopo de PCI compliance do Domus).

Fluxo de inscrição num evento pago:
1. Pessoa inicia a inscrição (self ou inscrevendo outras pessoas/acompanhantes).
2. Pra cada pessoa da leva (exceto o titular, que é fixo em "eu pago agora"), escolhe
   "eu pago agora" ou "gerar link".
3. Confirma → cria uma `INSCRICAO_EVENTO` + uma `COBRANCA_EVENTO` por pessoa.
4. Quem ficou marcado "eu pago agora" (titular + quem mais for marcado assim) segue pro
   Payment Brick, pagando cada cobrança (podem ser pagamentos separados no Mercado Pago,
   um por pessoa, já que cada um cai como uma transação própria — não dá pra somar num
   pagamento único e depois splitar por pessoa dentro do MP sem complicar a conciliação).
5. Quem ficou "gerar link" recebe, na tela de confirmação da inscrição, o link
   individual pra compartilhar (copiar / abrir WhatsApp — mesmo padrão de
   `ModalCompartilharConvite`).

### 4. Webhook e confirmação

`POST /pagamentos/mercadopago/webhook` — endpoint único (o Mercado Pago Connect manda
notificação de qualquer conta conectada pra essa URL única do marketplace). Identifica a
`COBRANCA_EVENTO` pelo `external_reference` (setado como o `id` da cobrança ao criar o
pagamento). Valida a assinatura do webhook (header `x-signature`, HMAC com a chave
secreta do webhook do Domus) antes de processar qualquer coisa — sem isso, qualquer um
poderia forjar confirmação de pagamento.

Ao confirmar `PAGO`:
- Se a cobrança é do titular: `INSCRICAO_EVENTO` fica/mantém `CONFIRMADA` (mesmo
  comportamento de evento grátis).
- Se é de acompanhante/outra pessoa: não muda o status da inscrição-guarda-chuva, só a
  vaga daquela pessoa deixa de depender da janela de expiração.
- Dispara notificação in-app (central de notificações já existente) pro titular quando
  uma cobrança que ele gerou como link é paga.

### 5. Cancelamento e reembolso

- Cancelar inscrição/remover acompanhante com `COBRANCA_EVENTO` em `PAGO` → chama
  estorno via API do Mercado Pago (token da conta da igreja) → marca `REEMBOLSADO`.
- Cancelar cobrança ainda `PENDENTE` (nunca paga) → `CANCELADO` direto, sem chamar API,
  libera a vaga imediatamente (não espera expirar).
- Falha ao chamar a API de estorno (ex.: Mercado Pago fora do ar) precisa de tratamento
  explícito — decisão de implementação: não deixar a inscrição virar `CANCELADA` no
  Domus se o estorno falhou (senão a igreja fica com o dinheiro e o Domus registra como
  se tivesse devolvido). Reter em estado intermediário e permitir retry, ou falhar a
  operação inteira e pedir pra tentar de novo — decidir na hora de implementar essa
  parte, com teste cobrindo o caso de falha do provedor.

### 6. Segurança — criptografia de credencial de terceiro

Primeira vez que o projeto guarda uma credencial reversível de terceiro (diferente de
senha, que é hash bcrypt irreversível). `access_token`/`refresh_token` da
`CONTA_PAGAMENTO_IGREJA` precisam de criptografia simétrica em repouso — chave em
variável de ambiente (nunca no banco, seguindo a mesma régua de "nunca imprimir
segredo" já em vigor no projeto), nunca logada nem exposta em nenhum DTO. Mecanismo
concreto (lib, algoritmo) é decisão de implementação, não desta spec — mas a
obrigatoriedade da criptografia é decisão de design fechada aqui.

## Fluxo de dados ponta a ponta (exemplo: titular inscreve 2 pessoas)

1. Titular abre inscrição no evento pago, adiciona 2 acompanhantes.
2. Marca: titular = "eu pago agora" (fixo), acompanhante A = "eu pago agora", acompanhante
   B = "gerar link".
3. `POST /eventos/{id}/inscricoes` cria 1 `INSCRICAO_EVENTO` + 3 `COBRANCA_EVENTO`
   (titular, acompanhante A com expiração curta, acompanhante B com expiração longa e
   `token_link_publico` gerado).
4. Front mostra Payment Brick pra cobrar titular + acompanhante A na sequência (ou um
   Brick por vez).
5. Front mostra tela de confirmação com botão de compartilhar o link do acompanhante B.
6. Webhook confirma pagamento do titular e de A → `COBRANCA_EVENTO` de ambos vira `PAGO`.
7. Acompanhante B (ou quem receber o link) abre `/cobranca/{token}`, paga via Brick
   standalone → webhook confirma → `COBRANCA_EVENTO` de B vira `PAGO` → notificação
   in-app avisa o titular.
8. Se B nunca pagar, `CobrancaEventoExpiracaoJob` expira a cobrança dele após 24-48h,
   liberando a vaga dele (a inscrição em si, se o titular já pagou a própria parte,
   permanece `CONFIRMADA` — só a vaga de B some).

## Testes

Segue o padrão de teste do projeto (`AGENTS`/`CLAUDE.md` — Mockito puro pra regra de
negócio, `@SpringBootTest` só onde há FK/webhook/segurança real). Cenários que a regra de
negócio precisa provar, na implementação:

- Titular não pode marcar a própria cobrança como "gerar link" (regra de negócio, testável
  sem Spring).
- Vaga conta pessoa com `COBRANCA_EVENTO` `PENDENTE` não expirada, mas não conta a
  `EXPIRADA` nem `CANCELADA`.
- Webhook com assinatura inválida é rejeitado sem alterar nenhuma cobrança.
- Webhook confirmando cobrança de acompanhante não muda o status da `INSCRICAO_EVENTO`.
- Cancelamento com cobrança `PAGO` dispara chamada de estorno; com `PENDENTE` não dispara.
- Falha no estorno não deixa a inscrição como `CANCELADA` sem o dinheiro ter voltado
  (cenário de erro do provedor).
- Tentar criar cobrança pra igreja sem `CONTA_PAGAMENTO_IGREJA` falha com o erro de
  negócio esperado.

## Fora de escopo (fica como próxima feature / backlog)

- **Comissão do Domus sobre evento pago** — 0% nesta entrega; se vier a existir, é um
  campo `percentual_comissao` na configuração do marketplace, aplicado no momento de
  criar o pagamento no Mercado Pago (split de 2 partes) — não exige redesenho do
  `COBRANCA_EVENTO`.
- **Lotes de preço por período** (1º lote, 2º lote) — `evento.preco` continua único.
- **Cobrança de assinatura do Domus** (item 2 do `BACKLOG-PRE-VENDA.md`) — depende de
  decisões de negócio próprias (faixas de plano, trial, dunning), provedor já é o
  mesmo (Mercado Pago, produto "Assinaturas"), mas é item separado.
- **Cartão salvo / cobrança recorrente por pessoa** — cada evento é uma cobrança nova.
