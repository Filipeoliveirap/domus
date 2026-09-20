import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));

// Setup de teste do front do Domus.
//
// 3 camadas:
//   - unit       : funções puras, hooks com lógica, validação Zod. SEM DOM.
//   - component  : jsdom + @testing-library/react. Render + evento + RHF+Zod.
//   - e2e        : Playwright separado, em e2e/. Não roda aqui.
//
// Por que separar unit de component: testes de função pura são milissegundos,
// rodam em watch sem subir jsdom. Quando você está mexendo num transformador
// ou num schema Zod, é o tipo de feedback que você quer. Teste de componente
// é mais lento (jsdom + RTL) e roda sob demanda.
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": path.resolve(here, "src"),
    },
  },
  test: {
    // Watch exclui Playwright — fica só Vitest. Roda `npm run test:e2e` pra e2e.
    projects: [
      {
        test: {
          name: "unit",
          include: ["src/**/*.{test,spec}.ts"],
          exclude: ["src/**/*.{test,spec}.tsx", "e2e/**", "node_modules/**"],
          environment: "node",
        },
      },
      {
        test: {
          name: "component",
          include: ["src/**/*.{test,spec}.tsx"],
          exclude: ["e2e/**", "node_modules/**"],
          environment: "jsdom",
          setupFiles: ["./vitest.setup.ts"],
        },
      },
    ],
    // Cobertura via v8 (rápido, sem transformação). Não falha por cobertura
    // baixa — quem decide é o code-reviewer. Só pra ver o que escapou.
    coverage: {
      provider: "v8",
      reporter: ["text", "html"],
      exclude: [
        "node_modules/",
        ".next/",
        "**/*.test.{ts,tsx}",
        "**/*.spec.{ts,tsx}",
        "**/__tests__/**",
        "src/test/**",
        "e2e/**",
      ],
    },
  },
});
