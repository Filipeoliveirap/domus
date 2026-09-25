# Spec de Design: Painel Admin Interno v1 (Gestão de Tenants)

> **Status:** Aprovado em 2026-09-25  
> **Objetivo:** Permitir ao operador/dono do Domus monitorar e administrar todas as igrejas (tenants), gerenciar status/planos, realizar suporte via impersonação e acompanhar métricas consolidadas (MRR, total de pessoas, status de clientes).

---

## 1. Visão Geral e Arquitetura de Isolamento

1. **Modelo de Autorização Isolado**:
   - SuperAdmins usam entidade exclusiva `UsuarioDomusAdmin` (`usuario_domus_admin`), totalmente separada de `Usuario` (que pertence a uma `Igreja`).
   - Autenticação via rotas exclusivas `/api/admin/auth/login`. Token JWT gerado contém claim `role: "ROLE_DOMUS_ADMIN"` e nenhuma informação de `igreja_id`.

2. **Gestão de Estado de Tenants (`Igreja`)**:
   - Entidade `Igreja` possui:
     - `status_tenant`: Enum `StatusTenant` (`ATIVO`, `SUSPENSO`). Default `ATIVO`.
     - `motivo_suspensao`: String opcional (ex: "Conta suspensa. Entre em contato com o suporte").
     - `plano`: Enum `PlanoAssinatura` (`BASICO`, `PRO`, `PRO_PLUS`, `ENTERPRISE`).
     - `status_assinatura`: Enum `StatusAssinatura` (`TRIAL`, `ATIVA`, `PAUSADA`, `CANCELADA`).

3. **Bloqueio Global de Tenant Suspenso**:
   - `SecurityFilter` intercepta chamadas tenant-scoped (`/api/v1/**`). Se a `Igreja` associada ao JWT do usuário estiver com `statusTenant == SUSPENSO`, retorna HTTP 403 Forbidden com o motivo da suspensão.
   - `/auth/login` valida o status da igreja e retorna erro específico informando que a conta está suspensa.

4. **Recurso de Impersonação (Suporte Técnico)**:
   - SuperAdmin chama `POST /api/admin/tenants/{id}/impersonate`.
   - Backend gera um JWT comum de tenant válido por 15 minutos para a igreja especificada com role `ADMIN_IGREJA` e claim `impersonated_by_admin_id`.
   - O front-end armazena o token de impersonação temporariamente para navegar no app como o admin da igreja.

---

## 2. API Endpoints (`/api/admin/**`)

| Método | Endpoint | Descrição |
|---|---|---|
| `POST` | `/api/admin/auth/login` | Autenticação do SuperAdmin. Retorna JWT de admin. |
| `GET` | `/api/admin/dashboard` | Retorna métricas globais (MRR, total igrejas por status, total de pessoas, alertas de limite). |
| `GET` | `/api/admin/tenants` | Lista paginada de igrejas com filtros (busca por nome/cnpj, status, plano). |
| `GET` | `/api/admin/tenants/{id}` | Detalhes completos da igreja (dados, administradores, consumo de limites). |
| `PATCH` | `/api/admin/tenants/{id}/status` | Altera status (`ATIVO`/`SUSPENSO`) e motivo da suspensão. |
| `PATCH` | `/api/admin/tenants/{id}/plano` | Override manual do plano (`PlanoAssinatura`) e status de assinatura (`StatusAssinatura`). |
| `POST` | `/api/admin/tenants/{id}/impersonate` | Gera JWT temporário de acesso à igreja para suporte. |

---

## 3. Estrutura do Frontend (`Next.js`)

1. **Rota Dedicada `/admin`**:
   - Grupo de páginas em `app/admin/(auth)/login` e `app/admin/(dashboard)/...`.
   - `AdminAuthContext` e `useAdminAuth()` isolados para gerenciar `domus_admin_token` (localStorage/Cookie).
   - Middleware de rota para proteger `/admin/*`.

2. **Telas do Painel**:
   - `/admin/login`: Login exclusivo do SuperAdmin.
   - `/admin/dashboard`: Cards de KPI (MRR, Igrejas Ativas, Pessoas Totais) + Tabela de Igrejas Próximas ao Limite.
   - `/admin/tenants`: Tabela de tenants com ações rápidas (Ver Detalhes, Alterar Plano, Suspender/Reativar, Impersonar).
   - `/admin/tenants/[id]`: Visão 360 do tenant (dados cadastrais, administradores, histórico de assinatura).

---

## 4. Bootstrapping Inicial

- `AdminDataInitializer` (`CommandLineRunner`):
  - Verifica se a tabela `usuario_domus_admin` está vazia.
  - Se vazia, lê `DOMUS_ADMIN_EMAIL` e `DOMUS_ADMIN_PASSWORD` das variáveis de ambiente e cria o usuário inicial com senha criptografada via `PasswordEncoder`.
