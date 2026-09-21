# Estudo de provedor de pagamento

> Resultado do item 1 do `BACKLOG-PRE-VENDA.md`, e da Fase 6 do `CLAUDE.md` (pedida desde a
> Fase 1, nunca feita). **Não é build — é decisão.** Pesquisado em 2026-08-20 (spike, ver
> memória do brainstorm da sessão). Trava os itens 2 (cobrança de assinatura do Domus) e 3
> (cobrança de evento pago) do backlog de pré-venda.

## Os dois casos de uso

O Domus precisa de um provedor que sirva aos dois ao mesmo tempo — são requisitos diferentes:

- **(a) Cobrança recorrente da assinatura do Domus.** A igreja paga o Domus todo mês.
  Cobrança B2B, sem split (o dinheiro vai só pro Domus).
- **(b) Cobrança de evento pago.** A pessoa física paga a igreja por uma inscrição. Precisa de
  **split** (o dinheiro tem que cair na conta da igreja, não na do Domus) e de **PIX** como
  método — é o que o brasileiro comum espera, principalmente numa audiência menos
  acostumada a checkout online (fiel de igreja pagando inscrição de evento, não um
  desenvolvedor comprando SaaS).

## Comparativo

| Provedor | PIX (taxa) | Split nativo | Assinatura recorrente | Observação |
|---|---|---|---|---|
| **Stripe** | ❌ não suportado pra conta configurada no Brasil (só disponível em contas fora do Brasil, cobrando cliente brasileiro) | Sim (Stripe Connect) | Sim | **Descartado** — PIX é essencial pro caso (b) e não funciona pra uma conta brasileira |
| **Mercado Pago** | 0,99% (0,49% pra CNPJ faturando acima de R$15.000/mês), recebimento imediato | Sim (Checkout Pro/Transparente) | Sim (produto "Assinaturas") | Marca mais reconhecida do brasileiro comum — reduz abandono de checkout numa audiência pouco acostumada a pagar online |
| **Pagar.me** (Grupo Stone) | 1,19% | Sim, inclusive PIX com múltiplos recebedores | Sim | O mais robusto pra marketplace de verdade; taxa de PIX mais alta dos três viáveis |
| **Asaas** | ~R$ 1,99 fixo por transação (grátis nas 100 primeiras/mês) | Sim (split de pagamentos) | Sim — é o produto mais forte deles, feito especificamente pra SaaS cobrar assinante | Taxa **fixa** (não percentual) é pior em valores altos, melhor em valores baixos |

Taxas de cartão não entraram na comparação porque o caso de uso prioritário nos dois cenários
é PIX — cartão fica como método secundário a decidir na implementação, não critério de
escolha do provedor.

## Recomendação

**Mercado Pago, um único provedor pros dois casos de uso.**

1. **PIX é o método esperado** por quem paga inscrição de evento — público leigo, muitas
   vezes pouco acostumado a checkout online. O Mercado Pago é a marca mais reconhecida do
   Brasil nesse cenário, o que reduz abandono de checkout — mais do que a diferença de
   centavos entre as taxas.
2. **PIX mais barato** dos três provedores viáveis (Stripe descartado): 0,99%, ou 0,49% em
   volume — mais barato que Pagar.me (1,19%) e, pra valores de inscrição típicos (não muito
   baixos), mais barato que a taxa fixa do Asaas.
3. **Split nativo** cobre a cobrança de evento (caso b: dinheiro cai direto na conta da
   igreja); o produto **"Assinaturas"** cobre a cobrança do Domus à igreja (caso a).
4. **Um provedor só** = um webhook a manter, uma conciliação, um conjunto de credenciais —
   menos superfície de manutenção do que juntar dois provedores diferentes pros dois casos.

### Alternativa considerada e descartada por ora

**Asaas só pro caso (a)** (cobrança B2B da assinatura do Domus), mantendo Mercado Pago pro
caso (b) (split de evento). Justificativa: Asaas é literalmente o produto deles — feito pra
SaaS cobrar assinante recorrente, com dunning e emissão de fatura prontos. Descartado por
ora porque usar dois provedores dobra a complexidade de integração (dois webhooks, duas
conciliações, duas credenciais) sem ganho claro nesse estágio. **Reavaliar só se o produto de
assinatura do Mercado Pago se mostrar limitado na prática** ao implementar o item 2 do
backlog de pré-venda — não é uma porta fechada, é uma escolha de simplicidade pro primeiro
lançamento.

### Stripe — por que foi descartado sem entrar na comparação de taxas

PIX não é suportado por contas Stripe configuradas no Brasil — só funciona em contas
internacionais cobrando cliente brasileiro, o que criaria uma camada extra de complexidade
(entidade fora do Brasil, câmbio, compliance) irrelevante pro estágio atual do Domus. Ponto
eliminatório, não uma questão de taxa.

## O que fica pra quando for implementar (itens 2 e 3 do backlog de pré-venda)

- Confirmar taxas exatas e condições vigentes no painel do Mercado Pago no momento da
  implementação — taxas de provedor de pagamento mudam com frequência, os números acima são
  o retrato de 2026-08-20.
- Decidir prazo de recebimento (na hora vs. D+14/D+30) pro caso (a) — afeta fluxo de caixa do
  Domus.
- Desenhar o que acontece quando a assinatura falha/atrasa (dunning) — anotado como pendência
  aberta no item 2 do `BACKLOG-PRE-VENDA.md`.
- Reembolso de inscrição de evento cancelada (caso b) — como o Mercado Pago trata estorno com
  split já aplicado (a parte que ficou com a igreja precisa ser devolvida por ela, ou o
  provedor reverte a operação inteira?). Confirmar na documentação do Checkout
  Pro/Transparente antes de desenhar o fluxo de cancelamento.

## Fontes consultadas (2026-08-20)

- [Pix Asaas: saiba sobre cobrança, recebimento e taxas](https://blog.asaas.com/pix-asaas/)
- [Qual API oferece split de pagamentos? Melhores opções](https://blog.asaas.com/qual-api-oferece-split-de-pagamentos/)
- [Automatize a divisão de recebíveis com o split de pagamentos do Asaas](https://materiais.asaas.com/split-de-pagamentos)
- [Split de Pagamento: O que é, Como Funciona e Gateways [2026]](https://yav.com.br/blog/split-pagamento-ecommerce/)
- [Stripe Connect](https://stripe.com/connect)
- [Receba pagamentos por Pix | Stripe](https://stripe.com/br/payment-method/pix)
- [Como habilitar o Pix como forma de pagamento no Brasil? — Stripe](https://support.stripe.com/questions/how-to-enable-pix-as-a-payment-method-in-brazil)
- [Split de pagamento: gerencie fornecedores no marketplace — Mercado Pago](https://www.mercadopago.com.br/blog/split-pagamento-complexo-marketplace)
- [Assinaturas: conheça a solução de pagamento recorrente do Mercado Pago](https://www.mercadopago.com.br/blog/assinaturas-conheca-nova-solucao-de-pagamento-recorrente-do-mercado-pago)
- [Taxas Mercado Pago 2026: O Guia Completo Para Vendedores](https://sellsync.ai/pt/blog/taxa-mercado-pago-2026-guia-completo/)
- [Conte com o Pix no Checkout Transparente do Mercado Pago](https://www.mercadopago.com.br/blog/o-pix-chegou-ao-checkout-transparente-do-mercado-pago)
- [PIX | Saiba mais sobre esse meio de pagamento — Central de Ajuda Pagar.me/Stone](https://pagarme.helpjuice.com/pt_BR/p1-meios-de-pagamento/pix-saiba-mais-sobre-esse-meio-de-pagamento)
- [Pix — Documentação Pagar.me](https://docs.pagar.me/docs/pix-1)
- [Gateways de Pagamento no Brasil: Comparativo 2026](https://fwctecnologia.com/blog/post/gateways-pagamento-brasil-comparativo)
