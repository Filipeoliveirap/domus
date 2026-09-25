# Spec de Design: Cobrança de Assinatura SaaS do Domus (Planos, Checkout & Webhooks)

> **Status:** Proposta aprovada no brainstorm em 2026-09-24  
> **Escopo:** Landing page pública, cadastro self-service com checkout no trial (14 dias grátis), integração com Mercado Pago Subscriptions API, notificações por e-mail e travamento por plano.

---

## 1. Visão Geral e Objetivos

O Domus passa a operar como um SaaS multi-tenant completo. Qualquer igreja pode se cadastrar publicamente através da Landing Page, escolher um plano, informar os dados de pagamento (cartão de crédito) e iniciar 14 dias de teste gratuito sem cobrança imediata. Após os 14 dias, o Mercado Pago cobra automaticamente a mensalidade do plano selecionado.

### Princípios do Modelo de Negócio
1. **Cobrança por capacidade organizacional (Congregações)**: Não cobra por número de membros ou visitantes (uso ilimitado em todos os planos).
2. **Cartão no cadastro com 14 dias trial**: Checkout obrigatório no cadastro, porém cobrança agendada para D+14. Se cancelar até o dia 13, nada é cobrado.
3. **Todas as funcionalidades liberadas**: Todos os planos acessam o sistema completo.

---

## 2. Faixas de Planos

| Plano | Congregações Suportadas | Valor Mensal |
|---|---|---|
| **Básico** | 1 Igreja (0 congregações vinculadas) | **R$ 129,00 / mês** |
| **Pro** | Até 3 Congregações vinculadas | **R$ 249,00 / mês** |
| **Pro+** | Até 5 Congregações vinculadas | **R$ 369,00 / mês** |
| **Enterprise** | Congregações Ilimitadas | **R$ 549,00 / mês** |

---

## 3. Fluxo de Onboarding e Checkout (UX)

```
[ Landing Page (/) / Planos (/planos) ]
                 │
                 ▼ (Clique em "Testar 14 dias grátis" no plano X)
[ /cadastro?plano=X ]
  - Dados da Igreja (Nome, CNPJ opcional)
  - Dados do Admin (Nome, E-mail, Telefone, Senha)
                 │
                 ▼ (Avançar para Pagamento)
[ Form de Cartão de Crédito (Mercado Pago Subscriptions API) ]
  - Número, Validade, CVV, Nome Impresso, CPF do Titular
  - Transação pré-aprovada com início de cobrança em D+14
                 │
                 ▼ (Confirmação)
[ Token JWT gerado + E-mail de Boas-vindas enviado + Redirecionamento para Dashboard ]
```

---

## 4. Alterações no Modelo de Dados (Backend)

### 4.1 Enum `PlanoAssinatura`
- `BASICO` (limiteCongregacoes: 0)
- `PRO` (limiteCongregacoes: 3)
- `PRO_PLUS` (limiteCongregacoes: 5)
- `ENTERPRISE` (limiteCongregacoes: 9999)

### 4.2 Enum `StatusAssinatura`
- `TRIAL`: Dentro dos 14 dias grátis, cartão validado/pré-aprovado.
- `ATIVA`: Cobrança mensal processada e em dia.
- `PAUSADA`: Cobrança falhou no cartão (dunning) ou assinatura suspensa.
- `CANCELADA`: Assinatura cancelada pelo cliente durante ou após o trial.

### 4.3 Campos em `igreja`
```sql
ALTER TABLE igreja ADD COLUMN plano VARCHAR(30) DEFAULT 'BASICO';
ALTER TABLE igreja ADD COLUMN status_assinatura VARCHAR(30) DEFAULT 'TRIAL';
ALTER TABLE igreja ADD COLUMN trial_expira_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE igreja ADD COLUMN mp_preapproval_id VARCHAR(100); -- Subscription ID no MP
ALTER TABLE igreja ADD COLUMN mp_payer_id VARCHAR(100);
```

---

## 5. Integração Mercado Pago (Subscriptions / Preapproval)

1. **Criação da Assinatura (`/preapproval`)**:
   - `payer_email`: E-mail do administrador.
   - `back_url`: URL de retorno do Domus.
   - `reason`: "Assinatura Domus - Plano " + Plano.
   - `auto_recurring`:
     - `frequency`: 1
     - `frequency_type`: "months"
     - `transaction_amount`: Preço do plano
     - `currency_id`: "BRL"
     - `start_date`: NOW + 14 dias (ISO 8601)
   - `card_token_id`: Token de cartão gerado via MercadoPago SDK no Frontend.

2. **Webhooks (`/api/webhooks/mercadopago/assinatura`)**:
   - Escuta eventos `subscription_preapproval` e `authorized_payment`.
   - Atualiza `status_assinatura` na `Igreja`:
     - `authorized`: `ATIVA`
     - `paused` / `cancelled`: `PAUSADA` / `CANCELADA`

---

## 6. Notificações por E-mail (`EmailService`)

1. **E-mail de Boas-Vindas & Trial**: Disparado no momento da conclusão do cadastro com aviso da data de início da cobrança.
2. **E-mail de Lembrete de Cobrança (Dia 11)**: Disparado 3 dias antes do término do trial ("Seu trial vence em 3 dias").
3. **E-mail de Erro de Cobrança / Recusa de Cartão**: Avisa que o pagamento falhou e solicita atualização do cartão.
4. **E-mail de Cancelamento**: Confirmação do cancelamento da assinatura.

---

## 7. Trava de Limites e Segurança

1. **Validação de Congregações (`IgrejaService`)**:
   - Ao adicionar igreja vinculada (congregação), verifica `count(igrejas_vinculadas) < plano.limiteCongregacoes`.
   - Lança exceção `PlanoLimiteExcedidoException` se atingir o limite.

2. **Filtro de Segurança (`SecurityFilter` / `AssinaturaFilter`)**:
   - Se `status_assinatura` for `PAUSADA` ou `CANCELADA` (ou `TRIAL` expirado sem preapproval ativo), bloqueia requisições de alteração (POST/PUT/DELETE), redirecionando o front para a tela de regularização de assinatura.

---

## 8. Arquivos Impactados

- **Backend**:
  - `Igreja.java`, `PlanoAssinatura.java`, `StatusAssinatura.java`
  - `V48__cobranca_assinatura_saas.sql`
  - `MercadoPagoSubscriptionService.java`
  - `AssinaturaWebhookController.java`
  - `EmailService.java` (novos templates HTML)
  - `IgrejaService.java` (trava de congregações)

- **Frontend**:
  - `app/(public)/page.tsx` (Landing Page)
  - `app/(public)/planos/page.tsx` (Tabela comparativa)
  - `app/(public)/cadastro/page.tsx` (Wizard de Cadastro + Form Cartão)
  - `app/(app)/configuracoes/assinatura/page.tsx` (Gestão do plano, troca e cancelamento)
