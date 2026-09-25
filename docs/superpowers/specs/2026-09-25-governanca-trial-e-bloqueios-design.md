# Spec de Design: Governança de Trial, Bloqueios Herdados & Notificações

> **Status:** Aprovado em 2026-09-25  
> **Objetivo:** Estabelecer regras estritas de cobrança e bloqueio entre matriz/filhas, notificação diária por e-mail de expiração de trial e UI diferenciada no login por role (`ADMIN_IGREJA` vs demais roles).

---

## 1. Regras de Negócio e Governança de Tenant

1. **Ciclo de Vida do Trial (Plano Escolhido + 14 Dias Grátis)**:
   - Toda igreja recém-cadastrada escolhe seu plano (`BASICO`, `PRO`, `PRO_PLUS`, `ENTERPRISE`).
   - Gravado: `statusAssinatura = TRIAL`, `trialExpiraEm = agora + 14 dias`.
   - Cobrança recorrente inicia exatamente à **00:00h do 15º dia**.
   - Durante os 14 dias, o cliente pode usar o plano escolhido sem cobrança.

2. **Job de Notificação Diária de Trial (`NotificacaoTrialJob`)**:
   - Executado via `@Scheduled` diário às 08h.
   - Busca igrejas em `TRIAL` com expiração em exatos 7, 3 e 1 dia (calculado até 23:59:59).
   - Envia e-mail para o e-mail de contato da igreja e todos os `ADMIN_IGREJA` ativos.
   - **Textos**:
     - 7 e 3 dias: *"Seu plano grátis de 14 dias acaba em {dias} dias. Você tem até o dia {data} para usar gratuitamente, após esse período será descontado automaticamente da conta."*
     - 1 dia: *"Seu plano grátis de 14 dias acaba amanhã. Você tem até o dia {data}..."*

3. **Herança de Bloqueio em Família de Igrejas (Matriz → Filhas)**:
   - Se a Igreja Mãe estiver **Suspenso** (`statusTenant = SUSPENSO`) ou **Cancelado** (`statusAssinatura = CANCELADA`), todas as congregações filhas herdam o bloqueio imediatamente.

4. **Matriz de Respostas de Login (`/auth/login`)**:

| Cenário | Role Tentando Logar | Resposta / Comportamento |
|---|---|---|
| **Matriz Cancelada** | `ADMIN_IGREJA` (Matriz) | HTTP 402 `ASSINATURA_CANCELADA` → Redireciona para `/assinatura-cancelada` (UI clara com botão de checkout para reativar). |
| **Matriz Cancelada** | Outras roles (Matriz ou Filhas) | HTTP 403 / 402 → Erro na tela de login: *"A conta da igreja {nome_igreja} foi cancelada."* |
| **Matriz Suspensa** | `ADMIN_IGREJA` (Matriz) | HTTP 403 `TENANT_SUSPENSO` → Erro na tela de login: *"Conta suspensa. Entre em contato com o suporte do Domus."* |
| **Matriz Suspensa** | `ADMIN_IGREJA` (Filha) | HTTP 403 `TENANT_SUSPENSO` → Erro na tela de login: *"A conta da família de igrejas foi suspensa. Entre em contato com a igreja contratante do plano."* |
| **Matriz Suspensa** | Outras roles | HTTP 403 `TENANT_SUSPENSO` → Erro na tela de login: *"A conta da igreja {nome_igreja} foi suspensa."* |

---

## 2. Trava de Congregações Filhas (`IgrejaService`)

- Ao registrar congregação filha em `IgrejaService.registrarCongregacao`:
  - Carrega plano da matriz.
  - Se `countByIgrejaMaeId(maeId) >= plano.getLimiteCongregacoes()`, lança `PlanoLimiteExcedidoException` (HTTP 402) com mensagem: *"Sua igreja atingiu o limite de {N} congregações do plano {Nome}."*
