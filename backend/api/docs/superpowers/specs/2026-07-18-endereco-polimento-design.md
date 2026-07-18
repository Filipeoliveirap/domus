# Design — Polimento do formulário de endereço (Projeto A)

- **Data:** 2026-07-18
- **Fase:** 2 — extensão da feature de endereço estruturado (2026-07-18)
- **Status:** design aprovado
- **Relacionado:** `2026-07-18-endereco-estruturado-design.md`. Mergeia junto com aquela feature.

## Problema

O form de endereço funciona, mas falta polimento e — o mais importante — **padronização do
bairro**. Alguns CEPs (ex.: cidades pequenas) não devolvem o bairro, então a pessoa digita na
mão; sem padronização, "Centro"/"centro"/"Bairro Centro" viram valores diferentes, e o filtro
por bairro (previsto no BACKLOG) acharia lixo. Padronizar **na entrada** é o momento certo,
mesmo com o filtro adiado — o dado está sendo digitado agora.

## Escopo (3 itens)

### 1. Máscara do CEP

- `formatarCep()` em `frontend/src/lib/masks.ts` (espelha `formatarTelefone`): pega os dígitos,
  corta em 8, insere o traço → `01001-000`. Máximo **9 caracteres** (8 dígitos + traço).
- O campo **exibe mascarado**; o **valor gravado no banco são os 8 dígitos limpos** (tira o
  traço no envio). Na edição, os 8 dígitos voltam formatados pra tela.
- Zod: se o CEP for preenchido, valida o formato mascarado `^\d{5}-\d{3}$`.
- O `useBuscaCep` já faz `replace(/\D/g)`, então lida com o valor mascarado sem mudança.

### 2. UF como dropdown

- Troca o input livre por um `<select>` (componente `Select` já usado no form) com os **27
  estados** (value = sigla, ex.: `SP`). O CEP, ao preencher, seleciona a UF sozinho.

### 3. Bairro padronizado — duas camadas

- **Camada 1 — normalizar ao salvar (backend).** No mapeamento `EnderecoDTO → Endereco`
  (`MembroService.paraEndereco`), aplicar `trim` + **Title Case** no **bairro** e na **cidade**
  (os dois campos que a pessoa digita e que o filtro futuro usará). `"centro "` → `"Centro"`.
  Um helper `normalizar(String)`: trim, colapsa espaços, primeira letra de cada palavra
  maiúscula. Nulo/vazio → nulo.
- **Camada 2 — sugerir bairros já usados.** Endpoint `GET /membros/bairros` que devolve os
  bairros **distintos** já cadastrados na igreja (escopado pelo `igreja_id` do JWT, ordenado,
  ignorando nulos). No front, o input de bairro ganha um `<datalist>` nativo populado com essa
  lista — conforme a pessoa digita, o navegador sugere os existentes. Reuso → convergência.

Por que as duas: a normalização garante o piso (nunca "centro"/"Centro" separados); a
sugestão faz convergir para o mesmo nome ("Centro" vs "Bairro Centro").

## Backend

- **`MembroService.paraEndereco`:** aplicar `normalizar()` em `bairro` e `cidade`. Novo método
  privado `normalizar(String)` (trim + colapsa espaços + Title Case; nulo/vazio → nulo).
- **`MembroRepository`:** `@Query("SELECT DISTINCT m.endereco.bairro FROM Membro m WHERE
  m.igreja.id = :igrejaId AND m.endereco.bairro IS NOT NULL ORDER BY m.endereco.bairro")
  List<String> bairrosDistintos(UUID igrejaId)`.
- **`MembroService.listarBairros(UUID igrejaId)`** → chama o repo.
- **`MembroController`:** `GET /membros/bairros` → `List<String>`, `igrejaId` do
  `UsuarioAutenticado` (nunca do request), autorização igual às outras rotas GET de membro.

## Frontend

- **`masks.ts`:** `formatarCep(valor: string): string`.
- **`validators.ts`:** `cep` opcional com regex `^\d{5}-\d{3}$` (mascarado).
- **`MembroForm.tsx`:**
  - CEP: `maxLength={9}`, `onChange` aplica `formatarCep` via `setValue`; mantém o `onBlur` que
    dispara o `useBuscaCep`.
  - UF: `Select` com os 27 estados.
  - Bairro: `<input list="bairros-datalist">` + `<datalist id="bairros-datalist">` populado
    pelo hook.
- **`useBairros()`** (novo hook): `GET /membros/bairros` via TanStack Query (o mesmo padrão dos
  outros selects), `staleTime` curto.
- **Envio:** no `onSubmit` dos hooks de membro, tirar o traço do `cep` antes de mandar
  (`cep: data.endereco.cep?.replace(/\D/g, '')`), e reaproveitar o `formatarCep` nos
  `defaultValues`/`reset` da edição para exibir mascarado.

## Testes

- **Back:** `normalizar()` (`"  centro "` → `"Centro"`, `"BAIRRO CENTRO"` → `"Bairro Centro"`,
  nulo → nulo) e o mapeamento aplicando-o (Mockito). O endpoint `/membros/bairros` — teste do
  service delegando ao repo.
- **Front (manual, sem runner):** CEP formata com traço e trava em 9; UF lista os estados; ao
  digitar bairro, sugestões aparecem; salvar normaliza (conferir no banco/edição).

## Fora de escopo

- **Busca reversa por rua** (ViaCEP UF+cidade+rua → lista) — para quando "não sei o CEP" virar
  demanda real. Discutido e adiado.
- **Normalizar `logradouro`** — nomes de rua variam demais; normalizar traria mais ruído que
  ordem. Fica de fora.
