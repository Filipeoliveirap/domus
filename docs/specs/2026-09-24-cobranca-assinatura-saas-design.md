# Spec de Design: Cobrança de Assinatura SaaS do Domus (Planos, Checkout, Código de Convite & Trava de Recursos)

> **Status:** Proposta refinada e aprovada em 2026-09-24  
> **Escopo:** Landing page pública, cadastro self-service com checkout no trial (14 dias grátis), cadastro de congregações filhas via Código de Convite (grátis para a filha, custeado pela Matriz), integração com Mercado Pago Subscriptions API, trava de limite de membros e trava por funcionalidade/plano.

---

## 1. Visão Geral e Objetivos

O Domus opera como um SaaS multi-tenant completo com suporte a matrizes e congregações filhas:
1. **Igreja Matriz**: Realiza o cadastro público, escolhe o plano, informa o cartão de crédito e inicia o trial de 14 dias.
2. **Igreja Filha (Congregação)**: Cadastra-se gratuitamente via **Código de Convite** gerado pela Matriz. Não é cobrada individualmente — utiliza o saldo de congregações do plano da Matriz.
3. **Plano Básico ultra-acessível (R$ 79/mês)**: Focado em igrejas pequenas (~50 membros) com uso essencial (Pessoas, Células, Financeiro de entradas/saídas). Recursos avançados (Feed social, Contas a pagar, Checkout de eventos, QR Check-in) são reservados para os planos Pro, Pro+ e Enterprise.

---

## 2. Faixas de Planos, Limites e Recursos

| Recurso / Limite | **Básico** | **Pro** | **Pro+** | **Enterprise** |
|---|---|---|---|---|
| **Valor Mensal** | **R$ 79,00 / mês** | **R$ 179,00 / mês** | **R$ 299,00 / mês** | **R$ 499,00 / mês** |
| **Limite de Pessoas Ativas** | Até 60 pessoas | Até 300 pessoas | Até 800 pessoas | Ilimitado |
| **Congregações Filhas** | 0 (Apenas solo) | Até 3 filhas | Até 5 filhas | Ilimitadas |
| **Gestão de Pessoas & Células** | ✅ Liberado | ✅ Liberado | ✅ Liberado | ✅ Liberado |
| **Financeiro Entradas/Saídas** | ✅ Liberado | ✅ Liberado | ✅ Liberado | ✅ Liberado |
| **Mural & Feed Social** | ❌ Bloqueado | ✅ Liberado | ✅ Liberado | ✅ Liberado |
| **Contas a Pagar** | ❌ Bloqueado | ✅ Liberado | ✅ Liberado | ✅ Liberado |
| **Cobrança de Eventos (PIX/Cartão)** | ❌ Bloqueado | ✅ Liberado | ✅ Liberado | ✅ Liberado |
| **QR Code Check-in & Relatórios** | ❌ Bloqueado | ✅ Liberado | ✅ Liberado | ✅ Liberado |

---

## 3. Fluxo de Código de Convite para Congregações Filhas

```
[ Igreja Matriz (Plano PRO/PRO+/Enterprise) ]
                 │
                 ▼ Gera código em /configuracoes/igreja
[ Código Único: DOMUS-X7Y9 ]
                 │
                 ▼ Envia o código/link para o responsável da congregação
[ /cadastro/congregacao?codigo=DOMUS-X7Y9 ]
  - Valida se o código existe e se a Matriz ainda tem vaga de congregação
  - Formulário de dados da congregação + admin
  - SEM FORMULÁRIO DE CARTÃO / COBRANÇA
                 │
                 ▼ (Concluir Cadastro)
[ Igreja Filha criada com matriz_id = Matriz, status = ATIVA (vinculada ao plano da Matriz) ]
```

---

## 4. Alterações no Modelo de Dados (Backend)

### 4.1 Enum `PlanoAssinatura`
- `BASICO` (limitePessoas: 60, limiteCongregacoes: 0, valor: R$ 79,00, bloqueiaAvançados: true)
- `PRO` (limitePessoas: 300, limiteCongregacoes: 3, valor: R$ 179,00, bloqueiaAvançados: false)
- `PRO_PLUS` (limitePessoas: 800, limiteCongregacoes: 5, valor: R$ 299,00, bloqueiaAvançados: false)
- `ENTERPRISE` (limitePessoas: 99999, limiteCongregacoes: 9999, valor: R$ 499,00, bloqueiaAvançados: false)

### 4.2 Tabela `codigo_convite_congregacao`
```sql
CREATE TABLE codigo_convite_congregacao (
    id BIGSERIAL PRIMARY KEY,
    matriz_id BIGINT NOT NULL REFERENCES igreja(id),
    codigo VARCHAR(20) NOT NULL UNIQUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    usado_em TIMESTAMP WITH TIME ZONE,
    igreja_filha_id BIGINT REFERENCES igreja(id)
);
```

---

## 5. Trava de Limites e Recursos (Backend)

1. **Trava de Limite de Pessoas (`PessoaService`)**:
   - Antes de salvar nova `Pessoa`, verifica `count(pessoas_ativas) < plano.limitePessoas`.
   - Se atingir o limite, lança `PlanoLimitePessoasExcedidoException`.

2. **Trava de Recursos por Plano (`PlanoResourceInterceptor` / `SecurityFilter`)**:
   - Requisições para `/api/postagens`, `/api/contas-a-pagar`, `/api/eventos/{id}/checkout` verificam se `igreja.plano.bloqueiaAvançados`.
   - Retorna HTTP 403 / 402 com mensagem: `"Recurso indisponível no plano Básico. Faça o upgrade para o plano Pro."`

---

## 6. Arquivos Impactados

- **Backend**:
  - `PlanoAssinatura.java`, `StatusAssinatura.java`, `Igreja.java`
  - `CodigoConviteCongregacao.java`, `CodigoConviteRepository.java`, `CodigoConviteService.java`
  - `V48__cobranca_assinatura_saas.sql`
  - `MercadoPagoSubscriptionService.java`
  - `AssinaturaWebhookController.java`
  - `PessoaService.java` (trava de pessoas)
  - `IgrejaService.java` (código de convite)

- **Frontend**:
  - `app/(public)/page.tsx`, `app/(public)/planos/page.tsx`
  - `app/(public)/cadastro/page.tsx`
  - `app/(public)/cadastro/congregacao/page.tsx` (Cadastro via código de convite)
  - `app/(app)/configuracoes/assinatura/page.tsx`
  - `app/(app)/configuracoes/igreja/page.tsx` (Gerador de código de convite)
