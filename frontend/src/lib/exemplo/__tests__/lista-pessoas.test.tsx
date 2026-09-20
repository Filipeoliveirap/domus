// Exemplo de teste de componente. Usa jsdom + RTL + MSW.
//
// Onde colocar componente teste:
//   - src/components/<componente>/__tests__/<componente>.test.tsx
//   - OU src/app/<rota>/__tests__/<rota>.test.tsx
//
// Convenção: nome do teste em snake_case PT descrevendo o cenário esperado.
// Mesmo padrão do back (CLAUDE.md "Convenções de teste").

import { describe, it, expect, beforeAll, afterAll, afterEach } from "vitest";
import { http, HttpResponse } from "msw";
import { server } from "@/test/setup-msw";
import { renderizarComQuery, screen, waitFor } from "@/test/test-utils";
import { ListaPessoasExemplo } from "@/lib/exemplo/lista-pessoas";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("ListaPessoasExemplo", () => {
  it("mostra_carregando_enquanto_api_nao_responde", () => {
    server.use(
      http.get("/api/pessoas", async () => {
        await new Promise((r) => setTimeout(r, 10_000));
        return HttpResponse.json([]);
      }),
    );

    renderizarComQuery(<ListaPessoasExemplo />);
    expect(screen.getByRole("status")).toHaveTextContent(/carregando/i);
  });

  it("mostra_lista_quando_api_retorna_pessoas", async () => {
    server.use(
      http.get("/api/pessoas", () =>
        HttpResponse.json([
          { id: "1", nome: "Maria Silva", apelido: "Ma" },
          { id: "2", nome: "João Santos" }, // sem apelido
        ]),
      ),
    );

    renderizarComQuery(<ListaPessoasExemplo />);

    expect(await screen.findByText("Maria Silva")).toBeInTheDocument();
    expect(await screen.findByText("João Santos")).toBeInTheDocument();

    // Aparece o apelido de Maria, mas não de João.
    expect(screen.getByText("Ma")).toBeInTheDocument();
  });

  it("mostra_mensagem_vazia_quando_api_retorna_array_vazio", async () => {
    server.use(http.get("/api/pessoas", () => HttpResponse.json([])));

    renderizarComQuery(<ListaPessoasExemplo />);

    expect(await screen.findByText(/nenhuma pessoa/i)).toBeInTheDocument();
  });

  it("mostra_erro_quando_api_retorna_500", async () => {
    server.use(
      http.get("/api/pessoas", () => new HttpResponse(null, { status: 500 })),
    );

    renderizarComQuery(<ListaPessoasExemplo />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/erro/i);
  });
});
