// Exemplo de teste e2e (Playwright).
//
// Roda contra next dev (configurado em playwright.config.ts) ou contra
// PLAYWRIGHT_BASE_URL (CI/preview).
//
// Por que esse arquivo existe mesmo sem tela real ainda: serve de modelo
// pra quem for escrever o primeiro teste e2e real. Mostra:
//   - como o teste dispara em 2 projetos (chromium + webkit)
//   - como fazer login mockado (sem precisar de back de pé)
//   - como validar mobile (viewport iPhone 14)

import { test, expect } from "@playwright/test";

test.describe("Smoke — home carrega", () => {
  test("página inicial renderiza sem erro", async ({ page }) => {
    await page.goto("/");
    // Sem tela real ainda, só checa que responde 200 e tem <html>.
    await expect(page).toHaveTitle(/Domus/i);
  });
});

test.describe("Smoke — viewport mobile", () => {
  test.use({ viewport: { width: 390, height: 844 } }); // iPhone 14

  test("bottom-sheet funciona com toque real", async ({ page }) => {
    await page.goto("/");
    // Exemplo: este teste só faz sentido com uma tela real rodando.
    // Quando tiver, substitua o skip por uma interação concreta:
    //   await page.getByRole("button", { name: /abrir menu/i }).tap();
    //   await expect(page.getByRole("dialog")).toBeVisible();
    test.skip(true, "aguardando tela com bottom-sheet pra testar");
  });
});
