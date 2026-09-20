// Helpers compartilhados de teste de componente.
//
// O mais comum no Domus: testar componente que consome TanStack Query.
// Sem QueryClientProvider, qualquer `useQuery` joga erro. Em vez de colar
// o wrapper em cada teste, usa-se `renderizarComQuery(<MeuComponente />)`.

import type { ReactElement, PropsWithChildren } from "react";
import { render, type RenderOptions } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

// QueryClient novo por teste — não vaza cache entre cenários.
// `gcTime: 0` + `retry: false` é o padrão pra teste: não queremos
// retry nem cache morto deixando teste lento.
function criarQueryClientTeste() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
        staleTime: 0,
      },
      mutations: {
        retry: false,
      },
    },
  });
}

function Providers({ children }: PropsWithChildren) {
  const client = criarQueryClientTeste();
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

/**
 * Substitui `render` do RTL: embrulha em QueryClientProvider com config de teste.
 * Use pra qualquer componente que consuma TanStack Query.
 */
export function renderizarComQuery(
  ui: ReactElement,
  options?: Omit<RenderOptions, "wrapper">,
) {
  return render(ui, { wrapper: Providers, ...options });
}

// Reexporta tudo do RTL pra conveniência — `import { screen, fireEvent } from "@/test/test-utils"`.
export * from "@testing-library/react";
export { default as userEvent } from "@testing-library/user-event";
