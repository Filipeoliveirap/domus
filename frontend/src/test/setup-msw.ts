// MSW (Mock Service Worker) — mocka a API nos testes de componente.
//
// Por que mockar a API em vez de subir um back de teste? Velocidade + isolamento.
// Você testa o componente, não a integração. Pra integração, tem teste de
// back (que já tem Mockito + Testcontainers).
//
// Como usar:
//   1. Importe `server` no setup file OU direto no teste.
//   2. Defina os handlers no próprio arquivo de teste (escopo local) ou em
//      `src/test/handlers/` se for reusar.
//   3. Os handlers ficam ativos enquanto o servidor roda.
//
// Exemplo:
//   import { http, HttpResponse } from "msw";
//   import { server } from "@/test/setup-msw";
//
//   it("mostra lista de pessoas", async () => {
//     server.use(
//       http.get("/api/pessoas", () =>
//         HttpResponse.json([{ id: "1", nome: "Maria" }])
//       )
//     );
//     render(<ListaPessoas />);
//     expect(await screen.findByText("Maria")).toBeInTheDocument();
//   });

import { setupServer } from "msw/node";

// Server de mock. Não tem handlers globais — cada teste adiciona os seus.
// Por quê vazio? Handlers globais mascaram o que o teste realmente está
// pedindo. Explícito por teste = leitura clara.
export const server = setupServer();
