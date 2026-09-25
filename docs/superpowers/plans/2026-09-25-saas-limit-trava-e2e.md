# SaaS Subscription Limits, Daughter Church Onboarding, Upgrade Modal & E2E Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement backend active people limit enforcement (`limitePessoas`), daughter church registration via invite code bypassing payment checkout, a reusable animated `<ModalUpgradePlano />`, and an E2E Playwright test suite.

**Architecture:** Active people limit is validated inside `PessoaService.criar` against `Igreja.getPlano().getLimitePessoas()`. Congregation registration uses `CodigoConviteService` with `POST /api/igrejas/registrar-congregacao` (public) and `POST /api/igrejas-vinculadas/codigo-convite` (protected). Frontend uses `<ModalUpgradePlano />` with `useFecharAnimado` and `@starting-style` for seamless mobile bottom-sheet and desktop modal transitions.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Next.js 15, TypeScript, Vitest, Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-09-25-saas-limit-trava-e2e-design.md`

## Global Constraints
- `limitePessoas`: BASICO (60), PRO (300), PRO_PLUS (800), ENTERPRISE (99999).
- `limiteCongregacoes`: BASICO (0), PRO (3), PRO_PLUS (5), ENTERPRISE (9999).
- Error Code on limit exceeded: `LIMITE_PESSOAS_EXCEDIDO` with HTTP status `402 Payment Required`.
- Invite Code format: `DOMUS-XXXXXX` (6 unambiguous chars).

## Review Focus
1. `PessoaService.criar` must count active people (`deleted_at IS NULL`) per church before saving a new `Pessoa`.
2. `CadastroCongregacaoService` must validate that the host church (`matriz`) has not reached its `limiteCongregacoes` limit before creating a daughter church.
3. Daughter church registration must set `status_assinatura = ATIVA` and `igreja_mae_id = matriz.id` without requiring credit card payment.
4. `ModalUpgradePlano` must follow Domus UI standards (`useFecharAnimado`, `@starting-style`, bottom-sheet in mobile with `.grabber`).
5. Playwright E2E tests must pass on Chromium and WebKit browsers.

---

### Task 1: Backend `PessoaService` Active People Limit Enforcement

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/pessoa/PessoaService.java`
- Modify: `backend/api/src/main/java/com/domus/api/shared/exception/GlobalExceptionHandler.java`
- Modify: `backend/api/src/test/java/com/domus/api/modules/pessoa/PessoaServiceTest.java`

**Interfaces:**
- Consumes: `PessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)` and `Igreja.getPlano().getLimitePessoas()`.
- Produces: `PlanoLimiteExcedidoException("LIMITE_PESSOAS_EXCEDIDO", ...)` when `totalActivePessoas >= limitePessoas`.

- [ ] **Step 1: Write the failing test in `PessoaServiceTest.java`**
```java
@Test
@DisplayName("deve_recusar_cadastro_pessoa_quando_atingir_limite_do_plano")
void deveRecusarCadastroPessoaQuandoAtingirLimiteDoPlano() {
    UUID igrejaId = UUID.randomUUID();
    UUID autorPessoaId = UUID.randomUUID();

    Igreja igreja = new Igreja();
    igreja.setId(igrejaId);
    igreja.setPlano(PlanoAssinatura.BASICO); // limite 60

    when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
    when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(60L);

    PessoaRequestDTO request = new PessoaRequestDTO("Nova Pessoa", "email@teste.com", null, null, null, Vinculo.MEMBRO, null, null, null, null, null);

    assertThatThrownBy(() -> pessoaService.criar(igrejaId, autorPessoaId, request))
            .isInstanceOf(PlanoLimiteExcedidoException.class)
            .hasMessageContaining("limite de 60 pessoas");
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && mvn -q test -Dtest=PessoaServiceTest`
Expected: FAIL (no limit check in `PessoaService.criar`).

- [ ] **Step 3: Implement active people limit check in `PessoaService.java`**
```java
long totalAtivas = membroRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId);
if (totalAtivas >= igreja.getPlano().getLimitePessoas()) {
    throw new PlanoLimiteExcedidoException(
        "LIMITE_PESSOAS_EXCEDIDO",
        String.format("Sua igreja atingiu o limite de %d pessoas do plano %s.",
            igreja.getPlano().getLimitePessoas(), igreja.getPlano().getNomeExibicao())
    );
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && mvn -q test -Dtest=PessoaServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit Task 1**
```bash
git add backend/api/src/main/java/com/domus/api/modules/pessoa/PessoaService.java backend/api/src/main/java/com/domus/api/shared/exception/GlobalExceptionHandler.java backend/api/src/test/java/com/domus/api/modules/pessoa/PessoaServiceTest.java
git commit -m "feat(saas): adiciona trava de limite de pessoas ativas por plano no PessoaService"
```

---

### Task 2: Daughter Church Registration & Invite Code Endpoints

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CadastroCongregacaoController.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CadastroCongregacaoService.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteController.java`
- Create: `backend/api/src/test/java/com/domus/api/modules/igreja/CadastroCongregacaoControllerTest.java`

**Interfaces:**
- Consumes: `CodigoConviteService.validarEConsumirCodigo(codigo, igrejaFilha)`.
- Produces: `POST /api/igrejas/registrar-congregacao` (public) & `POST /api/igrejas-vinculadas/codigo-convite` (protected).

- [ ] **Step 1: Write the failing integration test**
```java
@Test
void registrarCongregacao_comCodigoValido_cadastraESemCheckout() throws Exception {
    // Generates invite code for matriz and tests public POST /api/igrejas/registrar-congregacao
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && mvn -q test -Dtest=CadastroCongregacaoControllerTest`
Expected: FAIL (endpoints do not exist).

- [ ] **Step 3: Implement `CadastroCongregacaoService` and Controllers**
Create `CadastroCongregacaoController` endpoint handling `POST /api/igrejas/registrar-congregacao` returning `SessaoDTO` upon registration.

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && mvn -q test -Dtest=CadastroCongregacaoControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit Task 2**
```bash
git add backend/api/src/main/java/com/domus/api/modules/igreja/ backend/api/src/test/java/com/domus/api/modules/igreja/
git commit -m "feat(saas): adiciona endpoints de registro de congregação por código de convite"
```

---

### Task 3: Frontend Reusable `<ModalUpgradePlano />` & Limit Integration

**Files:**
- Create: `frontend/src/components/common/ModalUpgradePlano/ModalUpgradePlano.tsx`
- Create: `frontend/src/components/common/ModalUpgradePlano/ModalUpgradePlano.module.css`
- Modify: `frontend/src/app/(public)/cadastro/congregacao/page.tsx`
- Modify: `frontend/src/components/auth/FeatureGuard.tsx`

**Interfaces:**
- Consumes: `ModalUpgradePlano({ aberto, aoFechar, titulo, descricao, planoAtual, planoSugerido, progressoCurrent, progressoMax })`.
- Produces: Reusable animated modal and connected congregation registration page.

- [ ] **Step 1: Create `<ModalUpgradePlano />` component with animation and responsive design**
Create component using `useFecharAnimado`, `@starting-style`, bottom-sheet layout on mobile with `.grabber`.

- [ ] **Step 2: Connect `CadastroCongregacaoPage` (`/cadastro/congregacao`) to backend API**
Update `src/app/(public)/cadastro/congregacao/page.tsx` to handle `?codigo=` from URL and call `planoService.registrarCongregacao(payload)`.

- [ ] **Step 3: Check TypeScript build and unit tests**
Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: 0 errors.

- [ ] **Step 4: Commit Task 3**
```bash
git add frontend/src/components/common/ModalUpgradePlano/ frontend/src/app/\(public\)/cadastro/congregacao/
git commit -m "feat(saas): adiciona ModalUpgradePlano animado e conecta cadastro de congregações"
```

---

### Task 4: Playwright E2E Test Suite (`saas-assinatura-limites.spec.ts`)

**Files:**
- Create: `frontend/e2e/saas-assinatura-limites.spec.ts`

**Interfaces:**
- Consumes: Playwright chromium & webkit browser fixtures.
- Produces: E2E test verification of plan list, invite code flow, and upgrade modal.

- [ ] **Step 1: Write `saas-assinatura-limites.spec.ts`**
```ts
import { test, expect } from '@playwright/test';

test.describe('SaaS Subscription Limits & Invite Flow', () => {
  test('exibe planos e valores corretamente em /planos', async ({ page }) => {
    await page.goto('/planos');
    await expect(page.getByText('Básico')).toBeVisible();
    await expect(page.getByText('Pro')).toBeVisible();
  });

  test('preenche codigo de convite automaticamente em /cadastro/congregacao?codigo=DOMUS-TEST12', async ({ page }) => {
    await page.goto('/cadastro/congregacao?codigo=DOMUS-TEST12');
    const input = page.locator('#codigoConvite');
    await expect(input).toHaveValue('DOMUS-TEST12');
  });
});
```

- [ ] **Step 2: Run Playwright E2E tests**
Run: `cd frontend && npm run test:e2e`
Expected: PASS on chromium and webkit.

- [ ] **Step 3: Commit Task 4**
```bash
git add frontend/e2e/saas-assinatura-limites.spec.ts
git commit -m "test(e2e): adiciona testes Playwright para limites de assinatura e convite de congregação"
```

---

### Task 5: Workflow Audit & Verification Run (Ultracode Orchestration)

**Files:**
- Run Workflow script via `Workflow` tool orchestrating parallel audit agents.

- [ ] **Step 1: Run Workflow security & logic review**
Audit backend multi-tenancy boundaries, Mercado Pago webhook verification, and limit checks across all layers.

- [ ] **Step 2: Verify all test suites pass**
Run: `cd backend/api && mvn -q test && cd ../../frontend && npx tsc --noEmit && npx vitest run`
Expected: PASS across all layers.
