# Plano de Implementação: Governança de Trial, Bloqueios Herdados & E-mails

> **Status:** Aprovado em 2026-09-25  
> **Spec Relacionada:** `docs/superpowers/specs/2026-09-25-governanca-trial-e-bloqueios-design.md`

---

## Task 1: Job de E-mails Diários de Expiração do Trial (Backend)
- **O que fazer:**
  - Criar `NotificacaoTrialJob.java` com `@Scheduled(cron = "0 0 8 * * *")`.
  - Buscar igrejas em `TRIAL` com expiração em 7, 3 e 1 dia.
  - Enviar e-mail de aviso para contato da igreja e admins com texto formatado ("acaba em 7 dias", "acaba amanhã").
- **Verificação:** Teste unitário do job validando a seleção de igrejas e formato das mensagens.

---

## Task 2: Herança de Bloqueio Matriz → Filha & Respostas no Login (Backend)
- **O que fazer:**
  - Atualizar `SecurityFilter.java` e `AuthService.java`:
    - Verificar `statusTenant` e `statusAssinatura` da Igreja e da Igreja Mãe (se for filha).
    - Retornar códigos e mensagens diferenciados conforme a role (`ADMIN_IGREJA` vs outras) e o tipo de igreja (Matriz vs Filha).
- **Verificação:** Testes unitários em `AuthServiceTest` e `SecurityFilterTest` cobrindo a matriz completa de respostas de login.

---

## Task 3: Trava de Limite de Congregações no Backend
- **O que fazer:**
  - Atualizar `IgrejaService.registrarCongregacao`: checar `limiteCongregacoes` do plano da igreja mãe antes de permitir a criação da filha. Lançar `PlanoLimiteExcedidoException` (402).
- **Verificação:** Teste unitário no `IgrejaServiceTest`.

---

## Task 4: UI de Assinatura Cancelada & Feedback no Login (Frontend)
- **O que fazer:**
  - Criar página `/assinatura-cancelada` para `ADMIN_IGREJA` (card no tema do app com aviso e botão de checkout de reativação).
  - Atualizar formulário de login (`/login`) para exibir as mensagens específicas retornadas pela API (sem toast, diretamente no box de erro).
- **Verificação:** Testes unitários com Vitest validando o login e o redirecionamento.
