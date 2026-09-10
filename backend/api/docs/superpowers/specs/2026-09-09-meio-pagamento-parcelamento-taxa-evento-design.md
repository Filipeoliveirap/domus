# Meio de pagamento, parcelamento e taxa por evento — design

> Status: aprovado no brainstorm (2026-09-09). Back + front + schema (V40).
> Resolve dois itens do backlog que andam juntos:
> - "Escolha de meio de pagamento + parcelamento por evento, considerando a taxa do Mercado Pago"
> - "Taxa do Mercado Pago não aparece separada no financeiro"

## Problema

Hoje o evento pago tem só `preco` (`BigDecimal`, `null` = gratuito). O checkout
(`PaymentBrickCheckout.tsx`) libera **tudo** igual pra qualquer evento —
`customization = { paymentMethods: { bankTransfer: 'all', creditCard: 'all' } }` —
Pix, cartão e todas as parcelas, sem nenhuma configuração por evento.

Duas consequências:

1. **A igreja não escolhe o que aceitar.** Um evento pequeno que só deveria
   receber Pix aceita cartão parcelado em 12x do mesmo jeito.
2. **A taxa do Mercado Pago some.** O MP desconta por transação (~0,99% no Pix,
   ~4,49% no cartão à vista, mais por parcela) antes de o dinheiro cair na conta
   da igreja. O `MovimentacaoAutomaticaService` registra o valor **bruto** como
   entrada na categoria "Eventos" — então o financeiro da igreja mostra um número
   que nunca caiu na conta de verdade, e o custo de aceitar pagamento digital não
   aparece em lugar nenhum.

## Decisão de produto central: repassar a taxa pro pagador

A igreja cadastra **quanto quer receber** (líquido alvo). O pagador cobre a taxa
do Mercado Pago por cima. Assim a igreja recebe o valor exato que pediu, em
qualquer meio de pagamento.

- É a norma no domínio de eventos (Sympla, Eventbrite fazem assim).
- A alternativa (igreja absorve, embute no preço) foi descartada: a igreja
  costuma vender ingresso a preço de custo, sem margem pra comer 4–5%.
- **Página do evento:** mostra o preço-base (o alvo — ex.: R$ 100), com nota
  discreta "taxas de pagamento aplicadas no checkout".
- **Checkout:** mostra `preço + taxa = total`, variando pela escolha de
  método/parcelas do pagador.
- **Cadastro do evento:** resumo estilo e-commerce pro gestor conferir quanto o
  pagador vai pagar em cada opção antes de salvar.

### Gross-up (embutir a taxa "por dentro")

O valor a cobrar **não** é `alvo × (1 + taxa%)` — isso erra por baixo, porque o
MP cobra a % sobre o valor cobrado, não sobre o alvo. A conta certa:

```
valorACobrar = valorAlvo / (1 − taxaEfetiva)
```

Exemplo: alvo R$ 100, taxa 4,49% → `100 / (1 − 0,0449) = 100 / 0,9551 = 104,7011…`
→ **R$ 104,71** (arredondado pra cima ao centavo — a igreja nunca recebe menos que
o alvo). O MP tira ~4,49% de 104,71 ≈ R$ 4,71. A igreja recebe **R$ 100,00** cravados.

Todos os exemplos de valor nesta spec usam a tabela de config padrão: Pix 0,99%,
cartão à vista 4,49%, adicional por parcela 2,50%. Faixas de cartão:

| Parcelas | Taxa efetiva | Total (alvo R$ 100) | Taxa em R$ |
|---|---|---|---|
| Pix | 0,99% | R$ 101,00 | R$ 1,00 |
| 1x | 4,49% | R$ 104,71 | R$ 4,71 |
| 2x | 6,99% | R$ 107,52 | R$ 7,52 |
| 3x | 9,49% | R$ 110,49 | R$ 10,49 |
| 6x | 16,99% | R$ 120,47 | R$ 20,47 |

Arredondamento: sempre **pra cima** ao centavo (a igreja nunca recebe menos que
o alvo).

### Parcelamento: sem juros pro pagador, custo embutido no preço

O pagador vê "6x de R$ 19,67 sem juros". O custo que o MP cobra da igreja por
parcelar (~2–3% por parcela extra) entra no `valorACobrar` via gross-up — então
o **total** em 6x é maior que à vista (ex.: R$ 118,00 vs R$ 104,71). A igreja
recebe o alvo cravado em qualquer faixa. O resumo no cadastro mostra o total real
de cada faixa pro gestor escolher o teto com consciência.

Parcelamento **com juros na fatura do pagador** ficou fora de escopo (mecanismo
separado, config na conta MP, ganho marginal).

## Não-objetivos

- Não muda a confirmação assíncrona de pagamento (webhook + poll ativo
  idempotentes) — só o que cada um **registra** no financeiro.
- Não muda o fluxo de troca pago↔gratuito com gente inscrita
  (`InscricaoService.aplicarEventoVirouGratuito` etc.) além de repassar os
  valores novos.
- Não implementa "aprender a taxa real do `fee_details` pra corrigir o gross-up
  dos próximos pagamentos" — fica no backlog.
- Sem compatibilidade retroativa: a feature de evento pago existe mas a igreja
  piloto ainda não usou. A mudança de significado de `evento.preco` (passa a ser
  líquido alvo) vale só pros próximos eventos; eventos pagos antigos, se
  houver, não são migrados.
- Sem teste automatizado de front (não existe infra). Validação manual.

---

## Arquitetura

### 1. Schema — migration `V40__meio_pagamento_e_taxa_evento.sql`

**`evento`:**

```sql
ALTER TABLE evento
    ADD COLUMN pagamento_aceita_cartao BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN pagamento_max_parcelas  SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_max_parcelas CHECK (pagamento_max_parcelas BETWEEN 1 AND 12);
```

- Pix é sempre aceito em evento pago (não precisa de flag). Cartão é opt-in.
- `pagamento_max_parcelas = 1` → só à vista. Teto que o gestor escolhe, cap em 12.
- Sem efeito quando o evento é gratuito (`preco IS NULL`) — os campos ficam nos
  defaults e são ignorados.
- `evento.preco` **não muda de tipo nem de coluna** — muda de *significado* (era
  "valor que o pagador paga", passa a ser "valor que a igreja quer receber").
  Documentado no diagrama ER do `CLAUDE.md`.

**`conta_pagamento_igreja`** (taxas negociadas com o MP, opcionais):

```sql
ALTER TABLE conta_pagamento_igreja
    ADD COLUMN taxa_pix_percent                     NUMERIC(5,2),
    ADD COLUMN taxa_cartao_avista_percent           NUMERIC(5,2),
    ADD COLUMN taxa_cartao_parcela_adicional_percent NUMERIC(5,2);
```

- Todas nuláveis. `NULL` = usa o default da config do back.
- Preenchidas só se a igreja negociou plano personalizado com o MP (contas de
  volume alto pagam menos). O MP **não** expõe API pra ler as próprias taxas —
  não dá pra setar automático ao conectar a conta.
- Percentual em pontos percentuais (`4.49` = 4,49%), não fração.

**`cobranca_evento`** (reconciliação):

```sql
ALTER TABLE cobranca_evento
    ADD COLUMN valor_cobrado NUMERIC(10,2);
```

- `valor` continua sendo o **alvo** (o que a igreja quer receber).
- `valor_cobrado` é preenchido no `POST /cobrancas/{id}/pagar`, com o valor
  efetivamente cobrado do pagador (alvo + taxa, já com gross-up e parcela).
  `NULL` até a primeira tentativa de pagamento.

### 2. Config de taxa padrão — `application.properties`

```properties
pagamento.taxa.pix-percent=0.99
pagamento.taxa.cartao-avista-percent=4.49
pagamento.taxa.cartao-parcela-adicional-percent=2.50
```

- Ligados a um `@ConfigurationProperties` record `TaxaPagamentoProperties`
  (`modules/pagamento`).
- Valores padrão do Mercado Pago em set/2026. Se o MP mudar a tabela pública,
  ajusta aqui e redeploya.
- Por ambiente, como o resto dos `pagamento.*`.

### 3. `CalculadoraTaxaPagamento` — service novo (`modules/pagamento`)

Uma responsabilidade: dado o alvo e a escolha de meio/parcelas, quanto cobrar.

```java
public BigDecimal valorACobrar(UUID igrejaId, BigDecimal valorAlvo,
                               MeioPagamento meio, int parcelas)
```

- `MeioPagamento` — enum novo: `PIX`, `CARTAO`.
- Resolve a taxa efetiva:
  - `PIX` → `taxaPix` (override da igreja, senão config).
  - `CARTAO` → `taxaCartaoAvista + (parcelas − 1) × taxaCartaoParcelaAdicional`.
- `valorACobrar = valorAlvo / (1 − taxaEfetiva / 100)`, `RoundingMode.CEILING`
  a 2 casas.
- Validações: `parcelas >= 1`; `parcelas == 1` obrigatório quando `meio == PIX`;
  `valorAlvo > 0`.
- Depende só do `ContaPagamentoIgrejaRepository` (pra ler o override) e do
  `TaxaPagamentoProperties`. Testável isolado com Mockito puro.

### 4. `GET /cobrancas/{id}/opcoes-pagamento` — opções pro checkout

Monta a lista que a tela de escolha de método renderiza. Não recebe nada além
do id da cobrança (o resto vem do evento).

Resposta (`OpcoesPagamentoResponse`):

```jsonc
{
  "valorEvento": 100.00,           // o alvo, pra mostrar "valor do evento"
  "opcoes": [
    { "meio": "PIX",    "parcelas": 1, "valorTotal": 101.00, "valorParcela": 101.00, "taxa": 1.00 },
    { "meio": "CARTAO", "parcelas": 1, "valorTotal": 104.71, "valorParcela": 104.71, "taxa": 4.71 },
    { "meio": "CARTAO", "parcelas": 2, "valorTotal": 107.52, "valorParcela": 53.76,  "taxa": 7.52 }
    // ... até evento.pagamentoMaxParcelas
  ]
}
```

- `CARTAO` só entra se `evento.pagamentoAceitaCartao`.
- `valorParcela` = `valorTotal / parcelas`, `RoundingMode.HALF_UP` (só display; o
  MP faz o próprio arredondamento de parcela).
- `taxa` = `valorTotal − valorEvento` (o que o pagador paga a mais).
- Acessível no fluxo logado **e** no fluxo público (link compartilhável) — mesma
  regra de acesso do `GET /cobrancas/{id}` que já existe pros dois.

### 5. `POST /cobrancas/{id}/pagar` — recalcula o valor no back

`PagarCobrancaRequest` ganha dois campos:

```java
@NotNull MeioPagamento meio,
@NotNull @Min(1) @Max(12) Integer parcelas
```

- O back **ignora** qualquer valor vindo do front e recalcula com
  `CalculadoraTaxaPagamento.valorACobrar(...)`. Esse valor:
  - vira o `transactionAmount` em `MercadoPagoClient.criarPagamentoComToken`
    (hoje passa `cobranca.getValor()` cru);
  - é persistido em `cobranca.valorCobrado`.
- `installments` que já vem do Brick é **ignorado no servidor** (implementação
  endureceu além desta spec): o número de parcelas mandado pro Mercado Pago é
  sempre o `parcelas` validado contra `evento.pagamentoMaxParcelas`, nunca o
  `installments` cru do request. Sem isso dava pra pedir gross-up de 1x (barato,
  passa no teto) e `installments: 12` no mesmo request. O código de erro
  `PARCELAS_DIVERGENTES` previsto aqui não chegou a existir — não é mais
  necessário.
- `meio == PIX` com `parcelas > 1` → 400 (`PIX_NAO_PARCELA`).
- `meio == CARTAO` num evento com `pagamentoAceitaCartao == false` → 400
  (`CARTAO_NAO_ACEITO`).

### 6. `MercadoPagoApi` — parsear `fee_details`

`InformacoesPagamento` (hoje `record (externalReference, status)`) ganha:

```java
BigDecimal valorBruto,    // transaction_amount
BigDecimal taxaMercadoPago, // transaction_amount − net_received_amount
BigDecimal valorLiquido   // transaction_details.net_received_amount
```

- **Decisão fechada na implementação (resolve a questão aberta 2):**
  `taxaMercadoPago = transaction_amount − net_received_amount`, **não** a soma de
  `fee_details[].amount`. Motivo: `fee_details` não inclui a `financing_fee` do
  parcelamento, então somar só as linhas `mercadopago_fee` subestimaria o que o
  MP de fato reteve. `fee_details` é parseado só pra log/diagnóstico.
- Sem `transaction_details` (pagamento ainda `pending`), os **três** campos
  ficam `null` — inclusive `valorBruto`.
- `RespostaPagamentoMercadoPago` (record interno) ganha os campos
  `transaction_amount`, `fee_details` (lista de `{ type, amount }`) e
  `transaction_details.net_received_amount`.
- `buscarInformacoesPagamento` continua sendo `RestClient` direto (o SDK oficial
  não anexa o header de auth no GET — bug já documentado na classe).
- Se `fee_details` vier vazio (pagamento ainda `pending`, ou Pix não confirmado),
  os três campos ficam `null` — o chamador (webhook/poll) só registra o
  financeiro quando `status == approved`, aí os campos existem.

### 7. `MovimentacaoAutomaticaService` — dois lançamentos

Onde hoje `registrarEntradaDeEvento` faz **uma** ENTRADA bruta, passa a fazer
**dois** lançamentos ligados à mesma inscrição:

| Lançamento | Tipo | Categoria | Valor | Contribuinte |
|---|---|---|---|---|
| `Inscrição — <evento> (<pagador>)` | ENTRADA | Eventos | `valorBruto` | pagador (`pessoa_id` ou `nome_externo`) |
| `Taxa Mercado Pago — <evento>` | SAÍDA | Taxas de pagamento | `taxaMercadoPago` | — (sem contribuinte) |

- Assinatura nova:
  `registrarEntradaDeEvento(igrejaId, valorBruto, taxaMp, descricao, pessoaId, nomePagador, referenciaInscricaoId)`.
- **"Taxas de pagamento"**: categoria nova, auto-criada na 1ª vez (mesmo padrão
  de "Eventos" — `buscarOuCriarCategoria...`), `TipoCategoria.SAIDA`,
  `NOMES_ACEITOS = Set.of("taxa de pagamento", "taxas de pagamento")`. Notifica
  ADMIN_IGREJA + TESOUREIRO igual à categoria "Eventos".
- Os dois lançamentos carregam a mesma referência de inscrição, pra rastrear que
  vieram do mesmo pagamento.

**Estorno** (`registrarSaidaDeEvento`, chamado de `InscricaoService`):

- SAÍDA de `valorBruto` em "Eventos" (espelha a entrada).
- Se o MP devolveu a taxa junto (estorno rápido — `fee_details` do refund traz o
  valor devolvido), ENTRADA de `taxaDevolvida` em "Taxas de pagamento" zerando o
  custo. Se não devolveu, não registra nada do lado da taxa (a igreja comeu a
  taxa daquele pagamento — que é o comportamento real do MP).
- `MercadoPagoApi.estornarParcial` / o parse do estorno passam a expor o valor de
  taxa devolvido.

### 8. Front

**`EventoForm` / `useEventoForm`** — quando `tipoInscricao === 'PAGO'`:

- Campo **"Quanto a igreja quer receber por inscrição"** (label com exemplo no
  `placeholder`, ex.: `R$ 100,00`) — é o `preco`.
- Toggle **"Aceitar cartão de crédito"** dentro de `<Colapsavel>` — off por
  padrão. Off = só Pix.
- Com o toggle on: select **"Parcelar em até"** (1x a 12x).
- **Resumo e-commerce** (bloco `.card-painel`, aparece quando há `preco`):
  chama `GET` de simulação (endpoint 4, ou um `/eventos/simular-pagamento` com os
  valores do form ainda não salvo — ver questão aberta abaixo) e mostra:

  ```
  O pagador vai pagar:
    Pix ............... R$ 101,00
    Cartão à vista .... R$ 104,71
    em 6x ............. R$ 118,00  (R$ 19,67 / mês)
  A igreja recebe R$ 100,00 em qualquer opção.
  ```

- Responsivo (colapsa pra 1 coluna no mobile) + animação (`<Colapsavel>` nos
  blocos condicionais). Segue os padrões de `mobile-responsivo-obrigatorio` e
  `animacao-suavidade-padrao-front`.

**Tela de checkout nova** — `EscolhaMeioPagamento` (antes do `PaymentBrickCheckout`):

- Chama `GET /cobrancas/{id}/opcoes-pagamento`.
- Lista as opções com o valor de cada uma; deixa claro `valor do evento + taxa`.
- Ao escolher, monta o `PaymentBrickCheckout` com `meio`, `parcelas` e
  `valorTotal` fixos, e `customization.paymentMethods` restrito ao meio escolhido
  (`{ bankTransfer: 'all' }` **ou** `{ creditCard: 'all', maxInstallments: N }`).
- `PaymentBrickCheckout` passa `meio` e `parcelas` no corpo do
  `cobrancaService.pagar(...)`.
- Botão "voltar" pra trocar de opção antes de pagar.

**Página / drawer do evento** (`DrawerDetalheEvento`, card de evento, página de
inscrição):

- Mostra `evento.preco` (o alvo) como "Valor da inscrição".
- Nota discreta: "Taxas de pagamento são aplicadas no checkout, conforme o meio
  escolhido."

**Tipos** (`api.types.ts`): `MeioPagamento`, `OpcoesPagamentoResponse`, campos
novos em `Evento` (`pagamentoAceitaCartao`, `pagamentoMaxParcelas`).

---

## Fluxo de ponta a ponta

1. **Cadastro.** Gestor cria evento pago: define R$ 100 de alvo, liga cartão,
   teto 6x. O form chama a simulação e mostra o resumo. Salva → `evento.preco =
   100`, `pagamento_aceita_cartao = true`, `pagamento_max_parcelas = 6`.
2. **Inscrição.** Pessoa se inscreve → nasce `INSCRICAO_EVENTO` `AGUARDANDO_PAGAMENTO`
   + `COBRANCA_EVENTO` com `valor = 100` (alvo), `valor_cobrado = null`.
3. **Checkout — escolha.** `GET /cobrancas/{id}/opcoes-pagamento` → tela mostra
   "Pix R$ 101,00 · Cartão à vista R$ 104,71 · 2x R$ 107,52 · 3x R$ 110,49 · …".
   Pessoa escolhe 3x.
4. **Checkout — Brick.** `PaymentBrickCheckout` monta com `amount = 110.49`,
   cartão only, `maxInstallments = 6`. Pessoa preenche o cartão, 3x.
5. **`POST /cobrancas/{id}/pagar`** `{ token, paymentMethodId, installments: 3,
   meio: CARTAO, parcelas: 3, payerEmail }`. Back recalcula (não confia no front):
   `100 / (1 − (4,49 + 2×2,50)/100) = 100 / 0,9051 = R$ 110,49` (arredondado pra
   cima). Persiste `valor_cobrado = 110,49`, cobra esse valor no MP.
6. **Confirmação** (webhook ou poll). `status == approved`, `transaction_amount`
   R$ 110,49, `fee_details` soma ~R$ 10,49, `net_received_amount ≈ R$ 100,00`.
7. **Financeiro.**
   - ENTRADA R$ 110,49 em "Eventos", contribuinte = pagador.
   - SAÍDA `transaction_amount − net_received_amount` (≈ R$ 10,49) em "Taxas de
     pagamento".
   - Líquido no período: ≈ R$ 100,00.
8. **Cancelamento** (se houver). SAÍDA R$ 110,49 em "Eventos"; se o MP devolveu a
   taxa, ENTRADA do valor devolvido em "Taxas de pagamento".

---

## Testes

| Alvo | Ferramenta | Cobre |
|---|---|---|
| `CalculadoraTaxaPagamentoTest` | Mockito puro | gross-up Pix/cartão; arredondamento pra cima; `(parcelas−1)×adicional`; override da igreja vs config; recusa `parcelas>1` no Pix; recusa `parcelas<1`; `valorAlvo<=0` |
| `MovimentacaoAutomaticaServiceTest` | Mockito puro | dois lançamentos (ENTRADA bruto + SAÍDA taxa); categoria "Taxas de pagamento" auto-criada + notificação; tolera nome já existente (singular/plural); estorno espelhado com e sem taxa devolvida; contribuinte na entrada (pessoa e nome_externo), ausente na saída de taxa |
| `CobrancaControllerTest` | `@SpringBootTest` + `AutenticacaoTestSupport` | valor recalculado no back, ignora valor do front; 400 em `PIX_NAO_PARCELA` / `CARTAO_NAO_ACEITO` / `PARCELAS_DIVERGENTES`; `@Valid` em `meio`/`parcelas` ausentes; opções acessíveis no fluxo público |
| `MercadoPagoApiTest` (ou o de webhook) | Mockito / stub de resposta | parse de `fee_details`, `net_received_amount`, `transaction_amount`; `fee_details` vazio → campos `null` |
| `EventoServiceTest` / `EventoRequestTest` | Mockito / validator | `pagamento_max_parcelas` entre 1 e 12; cartão off zera parcelas; campos ignorados quando gratuito |

Regra de ouro do projeto: cada regra de negócio nova vem com o teste que a
prova, incluindo o caminho que **recusa**.

---

## Ordem de implementação (pedaços testáveis)

Feature grande — entregar em pedaços, testar cada um antes do próximo (regra
`feature-grande-em-pedacos-testaveis`):

1. **Schema V40 + `TaxaPagamentoProperties` + `CalculadoraTaxaPagamento`** (com
   testes). Nada visível ainda; base de tudo.
2. **`evento` ganha os campos** — entity, `EventoRequest`, `EventoService`,
   DTO de resposta, `EventoForm` (campos + toggle + select, sem o resumo ainda).
   Testável: criar/editar evento pago com config de pagamento.
3. **`GET /cobrancas/{id}/opcoes-pagamento` + endpoint de simulação pro form** +
   o resumo e-commerce no `EventoForm`.
4. **`MercadoPagoApi` lê `fee_details`** + `InformacoesPagamento` novo (sem mexer
   no financeiro ainda — só expõe os campos).
5. **`POST /cobrancas/{id}/pagar` recalcula** + `PagarCobrancaRequest` novo +
   `valor_cobrado` + tela `EscolhaMeioPagamento` + `PaymentBrickCheckout`
   parametrizado. Testável: pagar com Pix e com cartão parcelado, conferir o
   valor cobrado.
6. **`MovimentacaoAutomaticaService` — dois lançamentos** + categoria "Taxas de
   pagamento" + estorno. Testável: pagar → conferir ENTRADA + SAÍDA; cancelar →
   conferir o espelho.
7. **Fluxo público (link compartilhável)** herda a tela de escolha e o
   recálculo. Testável: pagar por link com cartão.
8. **Página/drawer do evento** — nota discreta + garantir que o preço exibido é o
   alvo.

---

## Questões abertas (resolver na implementação)

- **Simulação no cadastro de evento ainda não salvo.** O resumo e-commerce
  precisa do cálculo antes de o evento existir. Opções: (a) endpoint
  `POST /eventos/simular-pagamento` que recebe `{ preco, aceitaCartao,
  maxParcelas }` e devolve as opções; (b) calcular no front com as taxas vindas
  de `GET /pagamento/taxas` (config + override da igreja). (a) mantém a fórmula
  num lugar só (back) — preferência inicial.
- **`net_received_amount` vs `transaction_amount − Σ fee_details`.** Confirmar ao
  vivo (sandbox) que os dois batem; se houver divergência (imposto retido,
  etc.), decidir qual é a fonte da verdade pro líquido. A SAÍDA de taxa deve ser
  exatamente `transaction_amount − net_received_amount` pra o líquido fechar.
- **Arredondamento de parcela do MP.** O `valorParcela` que mostramos é
  estimativa; o MP pode arredondar diferente. Conferir se a soma das parcelas do
  MP bate com o `valorTotal` que cobramos — se não, o `valorTotal` (o que a gente
  manda em `transactionAmount`) manda, e o display de parcela é aproximado.
