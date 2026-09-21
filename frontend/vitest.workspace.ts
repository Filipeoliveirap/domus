import { defineWorkspace } from "vitest/config";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";
import path from "node:path";

const here = path.dirname(fileURLToPath(import.meta.url));

// Setup de teste do front do Domus.
//
// 3 camadas:
//   - unit       : funções puras, hooks com lógica, validação Zod. SEM DOM.
//                  Arquivos: src/lib/**/__tests__/*.spec.ts
//   - component  : jsdom + @testing-library/react. Render + evento + RHF+Zod.
//                  Arquivos: src/lib/**/__tests__/*.test.tsx,
//                            src/app/**/__tests__/*.test.tsx,
//                            src/components/**/__tests__/*.test.tsx
//   - e2e        : Playwright separado, em e2e/. Não roda aqui.
//
// `vitest.workspace.ts` (e não `vitest.config.ts` com `projects: []`) é o
// formato do Vitest 2.1 pra declarar múltiplos projetos num arquivo só.
// Cada entry tem seu próprio `include` + `environment` + `setupFiles` e o
// Vitest despacha pelo caminho do arquivo.
//
// Como rodar:
//   npm run test                 # unit + component juntos (sem --project)
//   npm run test:unit            # só *.spec.ts
//   npm run test:component       # só *.test.tsx
//   npm run test -- src/lib/foo  # escopo manual
//   npm run test:e2e             # Playwright separado
export default defineWorkspace([
  {
    plugins: [react()],
    resolve: {
      alias: {
        "@": path.resolve(here, "src"),
      },
    },
    test: {
      name: "unit",
      include: ["src/lib/**/__tests__/*.spec.ts"],
      environment: "node",
      setupFiles: [],
    },
  },
  {
    plugins: [react()],
    resolve: {
      alias: {
        "@": path.resolve(here, "src"),
      },
    },
    test: {
      name: "component",
      include: [
        "src/lib/**/__tests__/*.test.tsx",
        "src/app/**/__tests__/*.test.tsx",
        "src/components/**/__tests__/*.test.tsx",
      ],
      exclude: ["node_modules/", ".next/", "e2e/**"],
      environment: "jsdom",
      setupFiles: ["./vitest.setup.ts"],
    },
  },
]);
