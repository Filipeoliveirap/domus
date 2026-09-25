# SaaS Subscription Limits, Daughter Church Onboarding & UI Feedback Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full SaaS active people limit enforcement, public daughter church registration with invite code validation and visual feedback (handling expired, already used, and limit exceeded cases), official Domus branding, and E2E Playwright tests.

**Architecture:** Backend enforces limit checks in `PessoaService` and `CodigoConviteService`. `CadastroCongregacaoController` exposes `POST /igrejas/registrar-congregacao` and `GET /convites/congregacao/{codigo}`. Frontend connects Landing Page ("Resgatar Convite") and `/cadastro/congregacao` using the Stitch SaaS UI design, official Domus logo (`/images/logo.png`), and distinct error feedback screens.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Next.js 15, TypeScript, Vitest, Playwright E2E.

**Spec:** `docs/superpowers/specs/2026-09-25-saas-limit-trava-e2e-design.md`

## Global Constraints
- `limitePessoas`: BASICO (60), PRO (300), PRO_PLUS (800), ENTERPRISE (99999).
- `limiteCongregacoes`: BASICO (0), PRO (3), PRO_PLUS (5), ENTERPRISE (9999).
- Error Code on limit exceeded: `LIMITE_PESSOAS_EXCEDIDO` (HTTP 402 Payment Required).
- Invite Code format: `DOMUS-XXXXXX` (6 unambiguous chars).

## Review Focus
1. `GET /convites/congregacao/{codigo}` must distinguish code states: `VALIDO`, `EXPIRADO`, `JA_UTILIZADO`, `MATRIZ_LIMITE_EXCEDIDO`.
2. `/cadastro/congregacao` must render dedicated UI feedback for each error state:
   - Expired / Not Found -> `TimerOff` badge with instructions to request a new code.
   - Already Used -> Custom feedback message informing the code was already redeemed.
   - Matriz Limit Exceeded -> Card advising to contact `{nomeMatriz}` + CTA link to `/planos`.
3. Landing Page (`/`) must feature a **"Resgatar Convite de Filial"** CTA button that opens the code input flow.
4. Logo throughout the invite flow must use the official Domus asset `/images/logo.png`.

---

### Task 1: Backend Invite Code Validation Endpoint (`GET /convites/congregacao/{codigo}`)

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteService.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/CadastroCongregacaoController.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/dto/ConsultaConviteResponse.java`
- Modify: `backend/api/src/main/java/com/domus/api/config/SecurityConfig.java`
- Modify: `backend/api/src/test/java/com/domus/api/modules/igreja/CadastroCongregacaoControllerTest.java`

**Interfaces:**
- Consumes: `CodigoConviteService.consultarCodigo(codigo)`.
- Produces: `GET /convites/congregacao/{codigo}` -> `ConsultaConviteResponse(estado, matrizNome, matrizPastor, planoNome, limiteCongregacoes, vagasRestantes, logoFotoId)`.

- [ ] **Step 1: Write failing test for `consultarCodigo` endpoint in `CadastroCongregacaoControllerTest.java`**
```java
@Test
void consultarCodigo_valido_retornaDadosDaMatriz() throws Exception {
    var respCodigo = codigoConviteService.gerarCodigo(matriz);

    mockMvc.perform(get("/convites/congregacao/" + respCodigo.codigo()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.estado").value("VALIDO"))
            .andExpect(jsonPath("$.matrizNome").value(matriz.getNome()));
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && mvn -q test -Dtest=CadastroCongregacaoControllerTest`
Expected: FAIL (endpoint not implemented).

- [ ] **Step 3: Implement `consultarCodigo` method and endpoint**
Add `ConsultaConviteResponse` DTO and `GET /convites/congregacao/{codigo}` in `CadastroCongregacaoController`.

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && mvn -q test -Dtest=CadastroCongregacaoControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit Task 1**
```bash
git add backend/api/src/main/java/com/domus/api/modules/igreja/ backend/api/src/main/java/com/domus/api/config/SecurityConfig.java backend/api/src/test/java/com/domus/api/modules/igreja/CadastroCongregacaoControllerTest.java
git commit -m "feat(saas): adiciona endpoint publico GET /convites/congregacao/{codigo} para validacao de convite"
```

---

### Task 2: Frontend Invite Resolution & Distinct Error UI Feedback

**Files:**
- Modify: `frontend/src/services/plano.service.ts`
- Modify: `frontend/src/app/(public)/cadastro/congregacao/page.tsx`
- Modify: `frontend/src/app/(public)/page.tsx`

**Interfaces:**
- Consumes: `consultarConvite(codigo)` API service.
- Produces: UI with distinct feedback states (`VALIDO`, `EXPIRADO`, `JA_UTILIZADO`, `MATRIZ_LIMITE_EXCEDIDO`).

- [ ] **Step 1: Add `consultarConvite` function in `plano.service.ts`**
```ts
export interface ConsultaConviteResult {
  estado: 'VALIDO' | 'EXPIRADO' | 'JA_UTILIZADO' | 'MATRIZ_LIMITE_EXCEDIDO';
  matrizNome?: string;
  matrizPastor?: string;
  planoNome?: string;
  limiteCongregacoes?: number;
  vagasRestantes?: number;
  logoFotoId?: string | null;
}

export async function consultarConvite(codigo: string): Promise<ConsultaConviteResult> {
  const response = await fetch(`/api/convites/congregacao/${encodeURIComponent(codigo)}`);
  if (!response.ok) {
    return { estado: 'EXPIRADO' };
  }
  return response.json();
}
```

- [ ] **Step 2: Update `/cadastro/congregacao/page.tsx` with Stitch SaaS UI layout and error states**
Render official `/images/logo.png`, Matriz details card when valid, `TimerOff` component when expired, already used alert, and Matriz limit exceeded card linking to `/planos`.

- [ ] **Step 3: Add "Resgatar Convite de Filial" CTA on Landing Page (`src/app/(public)/page.tsx`)**
Add button linking directly to `/cadastro/congregacao`.

- [ ] **Step 4: Check TypeScript and Unit tests**
Run: `cd frontend && npx tsc --noEmit && npx vitest run`
Expected: 0 errors.

- [ ] **Step 5: Commit Task 2**
```bash
git add frontend/src/services/plano.service.ts frontend/src/app/\(public\)/cadastro/congregacao/page.tsx frontend/src/app/\(public\)/page.tsx
git commit -m "feat(saas): atualiza cadastro de congregacao com design SaaS, logo oficial e feedbacks de erro"
```

---

### Task 3: Playwright E2E Test Suite Update (`saas-assinatura-limites.spec.ts`)

**Files:**
- Modify: `frontend/e2e/saas-assinatura-limites.spec.ts`

**Interfaces:**
- Consumes: Playwright browser test runner.
- Produces: Verified E2E scenarios for plans page, invite code redemption, and error feedback states.

- [ ] **Step 1: Update `saas-assinatura-limites.spec.ts` with Landing Page CTA and error state checks**
```ts
test('navega da landing page para resgate de convite', async ({ page }) => {
  await page.goto('/');
  await page.click('text=Resgatar Convite de Filial');
  await expect(page).toHaveURL(/\/cadastro\/congregacao/);
});
```

- [ ] **Step 2: Run E2E tests**
Run: `cd frontend && npm run test:e2e`
Expected: PASS.

- [ ] **Step 3: Commit Task 3**
```bash
git add frontend/e2e/saas-assinatura-limites.spec.ts
git commit -m "test(e2e): atualiza suíte E2E para fluxo de resgate de convite e landing page"
```

---

### Task 4: Final Verification & Test Suite Execution

- [ ] **Step 1: Run backend and frontend test suites**
Run: `cd backend/api && mvn -q test && cd ../../frontend && npx tsc --noEmit && npx vitest run`
Expected: ALL TESTS PASS.
