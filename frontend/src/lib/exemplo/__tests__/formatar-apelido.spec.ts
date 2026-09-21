// Exemplo de teste de unidade puro. Sem React, sem DOM, sem jsdom.
// Roda em milissegundos — feedback instantâneo em watch mode.
//
// Onde colocar lógica testável no Domus:
//   - src/lib/<dominio>/normalizadores.ts (CPF, telefone, datas)
//   - src/lib/<dominio>/schemas.ts (Zod — sempre vale testar)
//   - src/lib/<dominio>/regras.ts (regras puras que viram hook depois)

import { describe, it, expect } from "vitest";
import { formatarApelido } from "@/lib/exemplo/formatar-apelido";

describe("formatarApelido", () => {
  it("remove_espacos_nas_pontas", () => {
    expect(formatarApelido("  Gago  ")).toBe("Gago");
  });

  it("colapsa_espacos_multiplos_no_meio", () => {
    expect(formatarApelido("O   Gago")).toBe("O Gago");
  });

  it("devolve_vazio_quando_entrada_vazia", () => {
    expect(formatarApelido("")).toBe("");
    expect(formatarApelido("   ")).toBe("");
  });

  it("trunca_em_60_caracteres", () => {
    const longo = "a".repeat(80);
    expect(formatarApelido(longo)).toHaveLength(60);
  });

  it("preserva_acentos_e_emoji", () => {
    expect(formatarApelido("São João 😀")).toBe("São João 😀");
  });
});
