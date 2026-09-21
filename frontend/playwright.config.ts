import { defineConfig, devices } from "@playwright/test";

// Playwright roda fluxo de usuário real — abre navegador, clica, espera response,
// valida o que aparece na tela. Onde Vitest+RTL não chega (botão que só anima
// saindo, swipe no mobile, layout real em viewport pequeno).
//
// Por que webkit: o Domus é mobile-first (CLAUDE.md) e o iOS Safari < 17.4
// não suporta @starting-style — por isso `useFecharAnimado`+`.saindo` existe.
// Não dá pra pular webkit, senão a animação de saída de modal vai quebrar
// silenciosamente em iPhone real.
//
// Como rodar:
//   npm run test:e2e              (sobe next dev automaticamente)
//   npm run test:e2e -- --headed  (vê o navegador)
//   npm run test:e2e -- --project=chromium  (só desktop)
//
// Variáveis de ambiente:
//   PLAYWRIGHT_BASE_URL — se setada, aponta pra staging/preview. Sem ela, sobe
//                         next dev local na porta 3000.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    // trace grava o passo-a-passo do teste pra debug quando falha.
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "webkit",
      // iPhone 14 viewport — pega bottom-sheet, animação de modal, fonte.
      use: { ...devices["iPhone 14"] },
    },
  ],
  // Sobe next dev antes dos testes se não houver PLAYWRIGHT_BASE_URL.
  // O dev server usa API_INTERNAL_URL=http://localhost:8080 (mesmo do dev normal).
  webServer: process.env.PLAYWRIGHT_BASE_URL
    ? undefined
    : {
        command: "npm run dev",
        url: "http://localhost:3000",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
