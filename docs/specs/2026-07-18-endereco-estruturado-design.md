# Design — Endereço estruturado do membro

- **Data:** 2026-07-18
- **Fase:** 2 (funcionalidades de valor para a igreja)
- **Status:** design aprovado, pendente plano de implementação

## Problema

O `membro.endereco` é hoje um `VARCHAR(500)` de **texto livre** — cada pessoa digita de um
jeito, e não dá para filtrar por bairro/cidade nem organizar. O roadmap decide trocar por
**colunas estruturadas na própria tabela `membro`** (não tabela nova — é 1-para-1, e habilita
filtro por bairro sem JOIN), com **auto-preenchimento por CEP via ViaCEP** no cadastro.

**Contexto que simplifica tudo:** a produção (Neon Frankfurt) subiu **zerada** hoje. Os ~2073
membros com endereço antigo estão só no **dev**, e são **dado de teste descartável**. Logo,
**não há migração de dado** — a mudança é só de estrutura. (Migrar texto livre para campos
estruturados seria impreciso e trabalhoso; felizmente não é preciso.)

## Escopo

**Opção A — só o núcleo** (armazenar estruturado + form com ViaCEP). O **filtro por
bairro/cidade fica como follow-up.**

Por quê: o filtro só é útil quando há **dado para filtrar**, e não há nenhum ainda (prod
zerado). Construí-lo agora seria otimizar para um cenário inexistente e adivinhar como as
igrejas filtram. Primeiro elas cadastram (já estruturado), depois se observa a necessidade
real. As colunas já existindo, o filtro é barato de adicionar depois.

**Fora de escopo (follow-up):** filtro por bairro/cidade (query + UI); indexar bairro/cidade
no Elasticsearch para busca global.

## Modelo de dados

Migration `V11` que **troca** a estrutura na tabela `membro`:

```sql
ALTER TABLE membro
  DROP COLUMN endereco,
  ADD COLUMN cep         VARCHAR(9),
  ADD COLUMN logradouro  VARCHAR(255),
  ADD COLUMN numero      VARCHAR(20),
  ADD COLUMN complemento VARCHAR(255),
  ADD COLUMN bairro      VARCHAR(255),
  ADD COLUMN cidade      VARCHAR(255),
  ADD COLUMN uf          CHAR(2);
```

Decisões:
- **Todas nuláveis.** Um membro pode ter endereço parcial ou nenhum (visitante, cadastro
  rápido). Forçar travaria o cadastro. Quem filtrar por bairro depois filtra só quem tem.
- **`numero` é texto** (`VARCHAR(20)`) — cobre "123A", "s/n", "45 fundos". `INT` quebraria.
- **`cep` guarda 8 dígitos limpos** (o front tira a máscara antes de enviar); `VARCHAR(9)`
  dá folga. Formatação é na exibição.
- **`uf` é `CHAR(2)`** — sigla do estado.

**A migração é destrutiva de propósito.** O `DROP COLUMN endereco` não perde nada em produção
(vazia) e descarta o dado de teste do dev (aprovado como descartável). Como o prod já rodou o
Flyway até a V10, esta V11 será a **primeira migration que ele aplica sozinho** no próximo
deploy — bônus: exercita o pipeline de migração em produção.

## Backend

**`Endereco` como `@Embeddable`, não 7 campos soltos no `Membro`.** Agrupa os 7 num objeto de
valor coeso:

```java
@Embeddable
public class Endereco {
    private String cep;
    private String logradouro;
    private String numero;
    private String complemento;
    private String bairro;
    private String cidade;
    private String uf;
}
```

O `Membro` embute com `@Embedded private Endereco endereco;`. No banco continua sendo as
**mesmas 7 colunas na tabela `membro`** (o `@Embeddable` achata na tabela dona — sem JOIN,
como o roadmap quer). Ganho: endereço vira **um conceito só**, testável e movível em bloco, e
o `Membro` (já com ~12 campos) não incha.

**DTOs espelham com um `EnderecoDTO` aninhado.** `MembroRequestDTO` e `MembroResponse` trocam
o `String endereco` por `EnderecoDTO endereco`. O contrato JSON passa a ser:

```json
{ "nome": "...", "telefone": "...", "endereco": { "cep": "...", "logradouro": "...", "numero": "...", "complemento": "...", "bairro": "...", "cidade": "...", "uf": "..." } }
```

`MembroService` mapeia `EnderecoDTO ↔ Endereco` nos dois sentidos (um método privado pequeno).

**Elasticsearch — mudança obrigatória (não opcional).** O `MembroDocument` hoje indexa
`endereco` lendo `membro.getEndereco()`. Como esse campo **deixa de existir**, essa linha
quebraria — então o `endereco` **sai do documento**. Coerente com a Opção A, **não** se
adiciona `bairro`/`cidade` ao índice agora. A busca global deixa de considerar endereço — que
hoje quase não ajuda (ninguém busca membro por logradouro).

**Validação:** tudo nulável. A única regra defensiva no back é `@Size(max=2)` no `uf` (o
`CHAR(2)` já garante no banco; o `@Size` dá a mensagem amigável). O resto entra como vier — a
validação de formato fica no front (Zod), onde o roadmap a coloca.

## Frontend + ViaCEP

O `MembroForm` troca o campo único por um **bloco de 7 campos** agrupados, com o CEP no topo.

**Fluxo do auto-preenchimento:**
1. Digita o CEP (máscara `12345-678`).
2. Ao completar **8 dígitos**, chama a ViaCEP: `https://viacep.com.br/ws/{cep}/json/`.
3. A resposta (`{ logradouro, bairro, localidade, uf }`) preenche `logradouro`, `bairro`,
   `cidade` (vem em `localidade`) e `uf`.
4. A pessoa completa `numero` e `complemento` (a ViaCEP não tem).

A ViaCEP é **pública, grátis, sem chave e com CORS liberado** — a chamada sai **direto do
navegador**, sem proxy no backend (seria código a mais sem ganho).

**⚠️ CSP — quebraria em silêncio sem este ajuste.** O `next.config.ts` tem
`connect-src 'self' https://accounts.google.com https://*.sentry.io`. Chamar a ViaCEP seria
**bloqueado** pela CSP (o navegador barra, sem erro óbvio). A feature **precisa adicionar
`https://viacep.com.br` ao `connect-src`** — mesmo padrão do Google e do Sentry.

**Tratamento de erro — tudo não-bloqueante** (o CEP é conveniência, não obrigação):
- **CEP não encontrado** (ViaCEP devolve `{ erro: true }`) → não preenche, aviso discreto
  ("CEP não encontrado, preencha manualmente"), a pessoa digita.
- **ViaCEP fora do ar / erro de rede** → não trava, a pessoa preenche na mão.
- **CEP incompleto** → nem chama (só dispara nos 8 dígitos).

Regra de ouro: **a pessoa sempre consegue cadastrar o endereço na mão.** A ViaCEP acelera,
nunca impede.

**Validação (Zod):** todos opcionais. Se o CEP for preenchido, valida 8 dígitos; `uf` no
máximo 2 letras. Nada além — validação leve, sem travar cadastro.

## Testes

- **Back:** o mapeamento `EnderecoDTO ↔ Endereco` e o `MembroService` (Mockito puro).
- **Front:** o hook do auto-preenchimento com a **ViaCEP mockada**, cobrindo os três caminhos
  (achou / `{erro:true}` / erro de rede) — provando que **nenhum trava** o form.

## Critério de pronto

- Cadastrar/editar membro grava as 7 colunas estruturadas; o `endereco` texto não existe mais.
- Digitar um CEP válido preenche logradouro/bairro/cidade/uf sozinho.
- CEP inválido, inexistente ou ViaCEP fora do ar **não travam** o cadastro.
- A migration V11 aplica limpa (prod vazia; dev perde o dado de teste).
- Testes do mapeamento e do auto-preenchimento passando.

## Fora de escopo (BACKLOG)

- **Filtro por bairro/cidade** (query no back + UI no front) — quando houver dado real e a
  necessidade for observada. As colunas já existirão.
- **Indexar bairro/cidade no Elasticsearch** para busca global — só se a busca por
  localização virar demanda.
- **Validar CEP contra a base dos Correios** / normalizar UF por lista fechada — YAGNI.
