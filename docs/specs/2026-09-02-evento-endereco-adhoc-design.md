# Fluxo de local do evento: 3 caminhos claros + endereço ad-hoc estruturado

> Spec de design. Data: 2026-09-02. Back: Java 21 / Spring Boot / Flyway / Postgres.
> Front: Next.js 16 (App Router), TypeScript, CSS Modules, React Hook Form + Zod.

## Problema

Na tela de cadastrar/editar evento, a seção **Local** confunde quem nunca usou:

- É um `<select>` "Selecione um local" que aparece **vazio** quando a igreja ainda não
  cadastrou nenhum `LocalEvento` — só mostra a opção críptica "— outro local —".
- Ninguém explica o que é um "local cadastrado" nem por que teria um.
- Não há caminho para cadastrar um local sem sair do formulário e perder o que já foi
  preenchido do evento.
- Quem quer só **digitar um endereço estruturado para aquele evento** (um retiro numa
  chácara com endereço específico, que nunca mais vai acontecer ali) não tem opção: ou
  cadastra um `LocalEvento` permanente (lixo na lista), ou digita um texto solto sem
  estrutura ("Chácara do João") que não ajuda ninguém a chegar lá.

Uma primeira leva de melhoria (rename "Locais" → "Endereços", modal de cadastro inline
com auto-seleção, controle segmentado de 2 opções) já está implementada no branch
`feat/evento-endereco-fluxo` mas **não commitada** — esta spec a incorpora e expande.

## Objetivo

1. Três caminhos para "onde o evento acontece", visíveis de uma vez, sem jargão:
   **endereço cadastrado** (reutilizável), **digitar simples** (texto livre ad-hoc),
   **endereço completo** (estruturado, ad-hoc — não vira cadastro).
2. Cadastrar um endereço reutilizável sem sair do formulário; ao salvar, já fica
   selecionado no evento em edição (mesmo antes de o evento ser salvo).
3. Botão "usar o endereço da igreja" que preenche os campos de endereço — tanto no
   endereço completo ad-hoc quanto no modal de cadastro de endereço.
4. Tirar do modal de cadastro o texto "deixe em branco para herdar o endereço da
   igreja"; a herança continua no backend, mas o caminho explícito passa a ser o botão.

## Não-objetivos

- Endereço completo ad-hoc **não** vira `LocalEvento` nem entra na lista de endereços.
- Sem migração dos `local_texto` já existentes — continuam válidos como "digitar simples".
- Sem geocoding / mapa / autocomplete de endereço além do ViaCEP por CEP (já existe).
- Localização do evento continua **opcional** (as três formas vazias = evento sem local).

## Estado atual relevante (investigado)

- `Evento`: `@ManyToOne LocalEvento local` **XOR** `String localTexto`. Regra em
  `EventoService.resolverLocal(...)` + CHECK `local_id IS NULL OR local_texto IS NULL`.
- `Endereco` (`shared/dominio/Endereco.java`) é `@Embeddable` de **7 colunas**
  (`cep, logradouro, numero, complemento, bairro, cidade, uf`), tudo nulável, já
  incorporado por `Igreja` (`@Embedded Endereco endereco`), Pessoa e Visitante.
- `LocalEvento` **não** usa o embeddable — guarda endereço em 2 colunas compactas
  (`cep_logradouro_numero`, `complemento_bairro_cidade_uf`). Inconsistência herdada; esta
  spec **não** a resolve, só convive com ela.
- `EventoResponse.LocalInfo { UUID id, String nome, String endereco, boolean enderecoHerdado }`
  — `LocalInfo.from(Evento)` já cobre os casos `local != null` e `localTexto != null`.
- `LocalEventoResponse` tem `formatarEnderecoDaIgreja(Endereco)` — reaproveitável.
- Front: `useMinhaIgreja()` traz a igreja com `endereco`. `useBuscaCep()`
  (`hooks/pessoa/useBuscaCep.ts`) faz ViaCEP e devolve `{cep, logradouro, bairro, cidade, uf}`.
- `EnderecoDTO` (`modules/pessoa/DTO/EnderecoDTO.java`) — DTO de request/response de
  endereço estruturado, reaproveitável.
- Última migration: **V35**. Próxima: **V36**.

## Arquitetura

### 1. Modelo de dados — `V36__evento_endereco_adhoc.sql`

```sql
ALTER TABLE evento
  ADD COLUMN cep          VARCHAR(9),
  ADD COLUMN logradouro   VARCHAR(255),
  ADD COLUMN numero       VARCHAR(20),
  ADD COLUMN complemento  VARCHAR(255),
  ADD COLUMN bairro       VARCHAR(255),
  ADD COLUMN cidade       VARCHAR(255),
  ADD COLUMN uf           CHAR(2);
```

`Evento` ganha `@Embedded Endereco enderecoLocal` (sem `@AttributeOverride` — as 7
colunas nascem com os nomes padrão do embeddable, iguais aos de `igreja`).

Localização do evento passa a ter **três formas mutuamente exclusivas**:

| forma | campos preenchidos |
|---|---|
| endereço cadastrado | `local_id` |
| digitar simples | `local_texto` |
| endereço completo ad-hoc | qualquer coluna de `enderecoLocal` |
| sem local | nenhum |

**Regra "endereço ad-hoc presente"** = `cep`, `logradouro` ou `cidade` não-nulo (os 3
campos que caracterizam um endereço digitado; complemento/número/bairro sozinhos não
contam). Definido num helper `Endereco.estaPreenchido()` (novo, no embeddable) para não
espalhar a heurística.

**CHECK no banco** (rede de segurança, não a validação primária):

```sql
ALTER TABLE evento ADD CONSTRAINT evento_local_unico CHECK (
  (CASE WHEN local_id IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN local_texto IS NOT NULL THEN 1 ELSE 0 END)
  + (CASE WHEN cep IS NOT NULL OR logradouro IS NOT NULL OR cidade IS NOT NULL THEN 1 ELSE 0 END)
) <= 1
);
```

(Substitui o CHECK atual `local_id IS NULL OR local_texto IS NULL`.)

### 2. Backend

**`EventoRequest`** ganha `EnderecoDTO enderecoLocal` (nulável). Os campos `localId` e
`localTexto` continuam.

**`EventoService.resolverLocal` → `resolverLocalizacao`:** recebe o request, conta quantas
das três formas vieram preenchidas; se > 1, lança
`BusinessException("Escolha só uma forma de definir o local: um endereço cadastrado, um
texto simples, ou um endereço completo.")`. Devolve um `record Localizacao(LocalEvento
local, String localTexto, Endereco enderecoLocal)` — no máximo um campo não-nulo. Em
`criar`/`atualizar`, seta os três no `Evento` a partir desse record (os outros ficam null,
inclusive limpando um `enderecoLocal` antigo na edição).

`localTexto` continua passando por `TextoUtil.capitalizar`. `enderecoLocal` é gravado como
veio (o front já normaliza via ViaCEP + máscara).

**`EventoResponse.LocalInfo.from(Evento)`:** novo ramo — se `e.getEnderecoLocal()` está
preenchido, `new LocalInfo(null, formatarEndereco(e.getEnderecoLocal()),
formatarEndereco(...), false)`. Extrair o formatador de `Endereco` para um util
compartilhado (`EnderecoFormatter.emLinhaUnica(Endereco)` em `shared/dominio/`) e fazer
`LocalEventoResponse.formatarEnderecoDaIgreja` delegar a ele (não duplicar).

**Busca (Elasticsearch):** `EventoDocument.local` hoje vem de `local.nome` ou
`localTexto`. Passa a cair no endereço ad-hoc formatado também — ponto único em
`EventoDocument.from` / onde o campo é montado. `reindexarPorLocal` não muda (só afeta
eventos com `local_id`).

**`Evento.getLocalExibicao()`** (usado em notificações de "mudou de local"): idem, inclui
o endereço ad-hoc formatado.

### 3. Frontend — `SeletorLocal`

Controle **segmentado de 3 opções** (lado a lado no desktop, empilha no mobile — mesmo
CSS `.segmentado` de `SecaoNomenclatura`, com `flex-wrap` / `flex-direction: column` no
breakpoint):

```
[ Endereço cadastrado ] [ Digitar simples ] [ Endereço completo ]
```

Modo inicial deduzido dos dados: tem `localId` → cadastrado; tem `localTexto` → simples;
tem `enderecoLocal` preenchido → completo; senão → cadastrado.

- **Endereço cadastrado** (já implementado, sem mudança):
  - com endereços: `SelectMenu` + botão "＋ Novo endereço" → abre `ModalLocalForm` inline,
    `onCriado` seleciona o novo e sugere a capacidade.
  - sem nenhum: card "Nenhum endereço cadastrado ainda…" + botão "Cadastrar endereço".
- **Digitar simples** (já implementado, sem mudança): `InputComSugestoes`, label
  "ONDE VAI SER", placeholder "Ex: Chácara do João, Praça da Matriz".
- **Endereço completo** (novo): campos `CEP` (com `useBuscaCep` no blur / 8 dígitos),
  `Logradouro`, `Número`, `Complemento`, `Bairro`, `Cidade`, `UF`. Acima dos campos, se
  `useMinhaIgreja().data?.endereco` existe, botão **"Usar o endereço da igreja"** que
  copia os 7 campos. Layout: grid que colapsa para 1 coluna no mobile (padrão do projeto).

O componente vira controlado por `props.modo` derivado + 3 setters no pai
(`EventoForm`): `onChangeLocalId`, `onChangeLocalTexto`, `onChangeEnderecoLocal`. Trocar
de modo **sempre limpa as outras duas formas** (invariante do XOR no cliente).

**`EventoForm`**: `watch('enderecoLocal')`, passa/recebe o objeto; inclui
`enderecoLocal` no payload de submit.

**`lib/validators`** (schema do evento): `enderecoLocal` opcional
(`z.object({ cep, logradouro, numero, complemento, bairro, cidade, uf }).partial().optional()`),
`.superRefine` para garantir no máximo uma das três formas e, quando `modo === 'completo'`,
exigir pelo menos `cidade` (senão o endereço não serve pra nada). Mensagens em pt-BR.

### 4. `ModalLocalForm`

- Remove o `<p className={styles.subtitle}>` ("Deixe o endereço em branco…").
- Adiciona botão **"Usar o endereço da igreja"** (visível quando `useMinhaIgreja` tem
  endereço) que preenche `cepLogradouroNumero` e `complementoBairroCidadeUf` a partir do
  `Endereco` da igreja, via um formatador `enderecoIgrejaParaCamposCompactos(endereco)`
  (front, `lib/formats/`): linha 1 = `"{cep}, {logradouro}, {numero}"`, linha 2 =
  `"{complemento} - {bairro} - {cidade}/{uf}"` (partes vazias omitidas).
- A herança backend (LocalEvento com endereço null → herda o da igreja) **continua** — só
  deixa de ser anunciada. `ModalDetalheLocal` já mostra "Usa o endereço da igreja — não
  tem um próprio" quando é o caso, o que basta.

### 5. Exibição

`LocalInfo` já carrega `{ nome, endereco }`; o endereço ad-hoc formatado entra por
`nome` (linha principal) com `endereco` repetido. Confirmar na implementação o render em:
`DrawerDetalheEvento`, `ModalEventoResumo` (início), card da lista de eventos,
`RelatorioEventoResponse` se expõe local. Nenhuma mudança estrutural esperada — só
garantir que o texto formatado aparece.

## Fluxo ponta a ponta (endereço completo ad-hoc)

1. Usuário em `/eventos/cadastrar`, seção Local → clica "Endereço completo".
2. `SeletorLocal` limpa `localId`/`localTexto`, mostra os 7 campos.
3. (Opcional) clica "Usar o endereço da igreja" → `useMinhaIgreja` → campos preenchidos.
4. Ajusta o que precisa; digita CEP → `useBuscaCep` completa logradouro/bairro/cidade/UF.
5. Submete o evento → payload inclui `enderecoLocal: { cep, logradouro, ... }`, sem
   `localId`/`localTexto`.
6. `EventoController` → `EventoService.criar` → `resolverLocalizacao` valida (só 1 forma)
   → `evento.setEnderecoLocal(endereco)`, `setLocal(null)`, `setLocalTexto(null)`.
7. Outbox → `EventoDocument.local` = endereço formatado; busca acha o evento por ele.
8. `EventoResponse.LocalInfo` = `{ id: null, nome: "Rua X, 123 - Centro - Recife/PE", ... }`.
9. Drawer de detalhe / resumo mostram esse texto.

## Erros e validação

| Caso | Comportamento |
|---|---|
| 2+ formas no request | `BusinessException` 400, mensagem única e clara (front não deixa chegar aqui — é rede de segurança) |
| modo "completo" com só complemento/número | Zod barra no front ("informe ao menos a cidade"); back aceita (trata como "sem local" se nem cidade/cep/logradouro vier) |
| CEP inexistente no ViaCEP | `useBuscaCep` já devolve `null` silenciosamente; usuário preenche na mão |
| igreja sem endereço | botão "Usar o endereço da igreja" não aparece |
| editar evento que tinha `localTexto`, trocar p/ "completo" | `resolverLocalizacao` limpa `localTexto`, grava `enderecoLocal` |

## Testes

**Back (`EventoServiceTest`, Mockito puro):**
- `recusaLocalCadastradoJuntoComEnderecoAdHoc()`
- `recusaTextoSimplesJuntoComEnderecoAdHoc()`
- `aceitaSomenteEnderecoAdHoc_gravaELimpaOsOutros()`
- `aceitaEventoSemLocalNenhum()`
- `editarDeTextoParaEnderecoAdHoc_limpaLocalTexto()`
- `LocalInfo` do response formata o endereço ad-hoc em linha única.
- `Endereco.estaPreenchido()` — cobre cep/logradouro/cidade isolados e complemento/número sozinhos.

**Back (`@DataJpaTest` ou migration test):** V36 aplica; CHECK recusa 2 formas simultâneas.

**Front:** sem harness (validação manual no navegador, incluindo mobile) — checklist:
estado vazio, criar-e-autoselecionar, alternar entre os 3 modos sem vazar dado, "usar
endereço da igreja" nos 2 lugares, editar evento existente em cada um dos 3 estados,
grid de endereço colapsando no mobile.

## Sequência de implementação (pedaços testáveis)

1. **Back — modelo + serviço + response** (migration V36, `Endereco.estaPreenchido`,
   `EnderecoFormatter`, `Evento.enderecoLocal`, `EventoRequest`, `resolverLocalizacao`,
   `LocalInfo.from`, `EventoDocument`/`getLocalExibicao`) + testes. Sem front.
2. **Front — endereço completo ad-hoc no `SeletorLocal`** (segmentado 3-way, campos,
   ViaCEP, validators) + `EventoForm` payload.
3. **Front — botão "usar endereço da igreja"** nos 2 lugares + formatadores + remover o
   texto de herança do `ModalLocalForm`.
4. **Commit único** do fluxo inteiro (pedaços A+B da leva anterior + 1–3), depois do
   autor testar cada pedaço.
