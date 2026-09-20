// Setup global de testes de componente. Roda antes de cada arquivo `.test.tsx`.
//
// O que vai aqui:
//   - matchers do @testing-library/jest-dom (toBeInTheDocument, toHaveTextContent...)
//   - polyfills de browser que o jsdom não traz por padrão (matchMedia, ResizeObserver)
//   - cleanup automático do DOM entre testes (RTL já faz, mas explícito)
//
// Nomes de teste em snake_case PT (mesma convenção do back). Exemplo:
//   describe("PessoaCard", () => {
//     it("mostra_nome_principal_quando_apelido_esta_vazio", () => { ... });
//   });

import "@testing-library/jest-dom/vitest";
import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";

// jsdom não tem matchMedia — componentes que usam media query quebram sem isso.
if (typeof window !== "undefined" && !window.matchMedia) {
  Object.defineProperty(window, "matchMedia", {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    }),
  });
}

// jsdom não tem ResizeObserver — quem usa recharts (gráficos) precisa disso.
if (typeof window !== "undefined" && !("ResizeObserver" in window)) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  // @ts-expect-error — polyfill intencional em runtime de teste
  window.ResizeObserver = ResizeObserverStub;
}

// Limpa o DOM entre testes pra evitar falso-positivo de "achou elemento que sobrou".
afterEach(() => {
  cleanup();
});
