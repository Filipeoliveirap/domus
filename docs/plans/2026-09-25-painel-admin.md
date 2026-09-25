# Plano de Implementação: Painel Admin Interno v1 (Gestão de Tenants)

> **Status:** Criado em 2026-09-25  
> **Spec Relacionada:** `docs/specs/2026-09-25-painel-admin-design.md`

---

## Task 1: Migration Flyway & Entidade Admin & Status do Tenant (Backend)
- **O que fazer:**
  - Criar migration `V49__painel_admin_tenants.sql`:
    - Criar tabela `usuario_domus_admin` (`id`, `nome`, `email`, `senha_hash`, `ativo`, `created_at`, `updated_at`).
    - Alterar tabela `igreja`: adicionar `status_tenant` (VARCHAR 20, DEFAULT 'ATIVO') e `motivo_suspensao` (VARCHAR 255).
  - Criar enum `StatusTenant` (`ATIVO`, `SUSPENSO`).
  - Atualizar entidade `Igreja.java` com novos campos.
  - Criar entidade `UsuarioDomusAdmin.java` e `UsuarioDomusAdminRepository.java`.
- **Verificação:** Teste unitário e de integração do repository de admin e atualização da `Igreja`.

---

## Task 2: Autenticação de Admin & Seed Inicial (Backend)
- **O que fazer:**
  - Criar DTOs: `AdminLoginRequestDTO`, `AdminLoginResponseDTO`.
  - Criar `AdminJwtService` para assinar e validar tokens JWT com claim `role: ROLE_DOMUS_ADMIN`.
  - Criar `AdminAuthService` e `AdminAuthController` (`POST /api/admin/auth/login`).
  - Criar `AdminDataInitializer` (`CommandLineRunner`) para seed inicial lendo `DOMUS_ADMIN_EMAIL` e `DOMUS_ADMIN_PASSWORD`.
  - Atualizar `SecurityConfig.java` para permitir `/api/admin/auth/login` e proteger `/api/admin/**` com `ROLE_DOMUS_ADMIN`.
- **Verificação:** Testar login de admin e verificar rejeição de acesso com token incorreto ou de usuário comum.

---

## Task 3: Bloqueio de Tenant Suspenso & Validação no Login (Backend)
- **O que fazer:**
  - Atualizar `SecurityFilter.java`: se token for de usuário comum, carregar `Igreja` (com cache simples se aplicável) e verificar `statusTenant`. Se `SUSPENSO`, rejeitar com 403 Forbidden.
  - Atualizar `AuthService.java` / `UsuarioController.java`: ao tentar logar em igreja suspensa, retornar erro amigável contendo o motivo da suspensão ("Conta suspensa. Entre em contato com o suporte").
- **Verificação:** Testes unitários no `SecurityFilter` e `AuthService` com tenant ativo vs suspenso.

---

## Task 4: Endpoints de Gestão de Tenants, Impersonação & Dashboard (Backend)
- **O que fazer:**
  - Criar `AdminTenantService` e `AdminTenantController`:
    - `GET /api/admin/tenants`: Lista paginada com busca.
    - `GET /api/admin/tenants/{id}`: Detalhes do tenant.
    - `PATCH /api/admin/tenants/{id}/status`: Alterar status e motivo da suspensão.
    - `PATCH /api/admin/tenants/{id}/plano`: Override de plano e status de assinatura.
    - `POST /api/admin/tenants/{id}/impersonate`: Gerar JWT de tenant temporário (15m) com claim `impersonated_by_admin_id`.
  - Criar `AdminDashboardService` e `AdminDashboardController` (`GET /api/admin/dashboard`):
    - Calcular MRR, contagem de igrejas por status, total de pessoas ativas e lista de igrejas perto do limite (>90%).
- **Verificação:** Testes de integração dos controllers de admin.

---

## Task 5: Estrutura do Frontend Admin & Contexto de Autenticação (Frontend)
- **O que fazer:**
  - Criar `services/adminAuthService.ts` e `services/adminTenantService.ts`.
  - Criar `contexts/AdminAuthContext.tsx` e hook `useAdminAuth()`.
  - Criar tela de login `/admin/login`.
  - Proteger rotas `/admin/*` via Middleware/Guard.
- **Verificação:** Efetuar login no frontend admin e validar persistência e expiração da sessão.

---

## Task 6: Interface do Dashboard Admin & Lista de Tenants (Frontend)
- **O que fazer:**
  - Criar página `/admin/dashboard`:
    - Stat tiles de MRR, Igrejas Ativas, Total Pessoas.
    - Tabela de Alertas de Limite de Pessoas.
  - Criar página `/admin/tenants`:
    - Tabela de igrejas com busca e paginação.
    - Modais para: Alterar Plano/Status, Suspender/Reativar Tenant, Iniciar Impersonação.
  - Criar página `/admin/tenants/[id]` (Detalhes do Tenant).
- **Verificação:** Validar fluxo completo de visualização, alteração de plano, suspensão e impersonação.
