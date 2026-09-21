# Setup do Codex (esteira paralela de revisão/diagnóstico)

Este documento explica o **pré-requisito** pra usar o **Codex CLI** (`codex` da
OpenAI) como esteira paralela de revisão/diagnóstico no projeto Domus. A orquestração
acontece via agent **`@dev-cox`** (em `.claude/agents/dev-cox.md`).

## O que é o codex neste projeto

O codex é um **segundo olho**, não o autor. Você (o autor humano) escreve código com
ajuda do **Claude Code** (eu), e usa o codex pra:

- **`codex:adversarial-review`** — passar o diff e pedir pra procurar bugs latentes
  antes de mergear (esteira paralela).
- **`codex:rescue`** — quando o `superpowers:systematic-debugging` chegou ao limite
  e você quer segunda opinião pra um bug difícil.
- **`codex:review`** — review geral substituto ou complementar ao `@code-reviewer`
  em tasks grandes.

**O codex não conhece o Domus.** Inclua os guardrails relevantes no brief
(`igreja_id` do JWT, `PostgresTestContainerSupport`, `vinculo`, multi-tenant por
coluna, LGPD).

## Pré-requisito único: codex instalado e autenticado

O codex CLI v0.155+ já está instalado em `~/.npm-global/bin/codex`. Falta **autenticar**.

```bash
export PATH="$HOME/.npm-global/bin:$PATH"
codex --version  # deve mostrar 0.155+
codex login status  # <<< chave do check
```

Se responder `Logged in` (ou similar com sua conta OpenAI), tudo certo. Se não:

### Opção A — Login interativo

```bash
codex login
```
Seguir o prompt (abre o navegador, login com conta OpenAI que tem acesso ao GPT-5).

### Opção B — API key (não interativo)

Se preferir não abrir navegador:

```bash
echo "$OPENAI_API_KEY" | codex login --with-api-key
```

Sendo que `$OPENAI_API_KEY` precisa estar configurado no ambiente. Salvar em
`~/.config/openai/api_key` ou similar (NÃO `.env` do projeto) — o codex pega de
variável de ambiente ou de `~/.codex/`.

## Validação rápida

```bash
export PATH="$HOME/.npm-global/bin:$PATH"
echo "console.log('ok');" > /tmp/domus-codex-test.js
codex exec --sandbox=read-only --model=gpt-5-codex \
  "Rode: node /tmp/domus-codex-test.js" 2>&1 | tail -3
```

Esperado: `ok` impresso.

## Como `@dev-cox` usa o codex

Ver `.claude/agents/dev-cox.md` pra exemplos concretos. Resumo:

```bash
# Review adversarial antes de merge (Fase 6 do WORKFLOW)
git diff develop...HEAD > /tmp/domus-diff.patch
codex exec --sandbox=read-only --model=gpt-5-codex \
  "<brief detalhado em 5 ângulos>" 2>&1

# Diagnóstico profundo (quando systematic-debugging não fechou)
codex exec --sandbox=read-only --model=gpt-5-codex \
  "<bug brief: sintoma + hypothesis + logs>" 2>&1
```

**Sandbox sempre `read-only`.** Nunca deixe o codex editar arquivos do projeto
diretamente.

## Fallback: codex não autenticado

Se `codex login status` não estiver autenticado, o `@dev-cox` pula o passo e
continua. Não bloqueia o trabalho. Adicione um lembrete ao autor:

> "Pulei o `codex:adversarial-review` porque `codex login status` não está
> autenticado. Rode `codex login` (interativo) ou
> `echo $OPENAI_API_KEY | codex login --with-api-key` para liberar essa esteira."

## Custo & responsabilidade

- O codex cobra tokens (gpt-5-codex). Para um diff típico do Domus (~500-2000 linhas),
  espere **poucos centavos a poucos reais** dependendo do tamanho. Olho no orçamento.
- O codex **não substitui** teste humano. Após adversarial review, você ainda roda a
  suíte (`mvn -q test`, `npm run test`) e o build (`npm run build`,
  `mvn -q package -DskipTests`).
- O codex **não** abre PR, **não** faz merge, **não** commita. Você decide o que
  aplicar — e como.

## Skills que orquestram o codex

- **`codex`** (plugin OpenAI) — skills `codex-cli-runtime`, `codex-result-handling`,
  `gpt-5-4-prompting`. Subagentes: `codex:rescue`, `codex:setup`.
- **`@dev-cox`** (em `.claude/agents/`) — ponto de entrada canônico.
