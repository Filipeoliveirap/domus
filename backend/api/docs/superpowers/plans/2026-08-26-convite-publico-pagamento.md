# Convite público ganha pagamento (Plano 5/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fecha o gap descoberto no brainstorm: `/convite/{token}` mostra o preço do
evento mas nunca cobra nada. Quem abre o convite **logado** (`EntrarLogado`) passa a
navegar pra rota de checkout (Plano 2) igual à auto-inscrição normal. Quem abre **sem
conta** (`FormularioConvidado`) fica bloqueado de se inscrever sozinho em evento pago —
não porque a decisão de produto seja essa, mas porque `inscreverConvidado` genuinamente
não tem como cobrar hoje (ver "Fora de escopo" abaixo).

**Architecture:** Nenhuma mudança de backend. `EntrarLogado` já usa `useInscrever`, o
mesmo hook que `BotaoConfirmarPresenca` usa (Planos 1-3 já deixaram esse caminho pronto
pra pagamento — cria `AGUARDANDO_PAGAMENTO` + `CobrancaEvento`, devolve
`cobrancaPendenteId`). Só falta o componente reagir a esse campo e navegar. O caminho sem
conta (`FormularioConvidado`) usa `inscreverConvidado`, que **não** cria `CobrancaEvento`
(inscrição sem cadastro não tem como ser cobrada no schema atual — mesmo gap do Plano 4b).
Em vez de deixar a pessoa se inscrever de graça num evento pago (bug real, hoje
silencioso), a tela some com o formulário de convidado quando o evento é pago e empurra
pra login.

**Tech Stack:** Next.js/TypeScript/CSS Modules (só frontend).

**Spec:** `docs/superpowers/specs/2026-08-26-fluxo-pagamento-evento-ux-design.md` (seção
"Convite público (`/convite/{token}`) ganha suporte a pagamento").

## Escopo desta entrega (decidido no brainstorm)

- **Com conta (`EntrarLogado`):** pagamento completo, reaproveitando o que os Planos 1-3
  já construíram.
- **Sem conta (`FormularioConvidado`):** em evento pago, o formulário de "continuar sem
  conta" some — só resta "Fazer login". Cobrar quem não tem cadastro fica pro Plano 4b
  (junto da unificação acompanhante↔convidado e da migration no `CHECK` de
  `cobranca_evento`), decidido explicitamente no brainstorm pra não crescer o escopo aqui.
- Sem token na URL de checkout (`?token=...}`): checado durante o Plano 2 que
  `GET /cobrancas/id/{id}` já é público (mesma garantia de posse por UUID de todo o
  módulo de cobrança) — não precisa de nada a mais pra funcionar sem sessão.

## Global Constraints

- Sem framework de teste de frontend — validação é `npx tsc --noEmit` + verificação
  manual no navegador.
- Não tocar em `inscreverConvidado`/backend nenhum — o gap de convidado sem cadastro fica
  pro Plano 4b.

---

### Task 1: `EntrarLogado` navega pra checkout quando o evento é pago

**Files:**
- Modify: `frontend/src/app/convite/[token]/EntrarLogado.tsx`

**Interfaces:**
- Consumes: `MinhaInscricaoResponse.cobrancaPendenteId` (já existe, preenchido pelo
  Plano 1 quando a inscrição nasce `AGUARDANDO_PAGAMENTO`); rota
  `/eventos/{eventoId}/pagamento/{cobrancaId}` (Plano 2).
- Produces: nenhuma interface nova — `EntrarLogado` mantém a mesma `Props`
  (`eventoId`, `nomeUsuario`, `onSucesso`).

- [ ] **Step 1: Editar o componente**

Em `EntrarLogado.tsx`, adicionar o estado novo logo depois de `tentouEnviar`:

```typescript
  const [cobrancaPendenteId, setCobrancaPendenteId] = useState<string | null>(null)
```

Substituir:

```typescript
  function aoConfirmarInscricao() {
    inscrever.mutate(undefined, {
      onSuccess: (dados) => {
        setInscricaoId(dados.id)
        if (campos.length === 0) onSucesso()
      },
    })
  }

  async function aoSalvarRespostas() {
    setTentouEnviar(true)
    if (camposObrigatoriosPendentes() || !idParaResponder) return

    const dados = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    const sucesso = await responder(idParaResponder, dados)
    if (sucesso) onSucesso()
  }
```

por:

```typescript
  function aoConfirmarInscricao() {
    inscrever.mutate(undefined, {
      onSuccess: (dados) => {
        setInscricaoId(dados.id)
        setCobrancaPendenteId(dados.cobrancaPendenteId)
        if (campos.length === 0) finalizar(dados.cobrancaPendenteId)
      },
    })
  }

  /** Evento pago (cobrancaId presente) navega pro checkout dedicado; gratuito segue pro
   *  fluxo antigo (`onSucesso`, tela estática "Inscrição confirmada!"). */
  function finalizar(cobrancaId: string | null) {
    if (cobrancaId) {
      router.push(`/eventos/${eventoId}/pagamento/${cobrancaId}`)
    } else {
      onSucesso()
    }
  }

  async function aoSalvarRespostas() {
    setTentouEnviar(true)
    if (camposObrigatoriosPendentes() || !idParaResponder) return

    const dados = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    const sucesso = await responder(idParaResponder, dados)
    if (sucesso) finalizar(cobrancaPendenteId)
  }
```

`router` já está importado e em uso no arquivo (`useRouter`), não precisa de import novo.

- [ ] **Step 2: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/app/convite/\[token\]/EntrarLogado.tsx
git commit -m "feat(convite): auto-inscricao logada em evento pago navega pro checkout"
```

---

### Task 2: Bloquear "continuar sem conta" em evento pago

**Files:**
- Modify: `frontend/src/app/convite/[token]/page.tsx`

**Interfaces:**
- Consumes: `ConvitePublicoResponse.preco` (já existe, `number | null`).
- Produces: nenhuma interface nova.

- [ ] **Step 1: Editar a página**

Em `page.tsx`, localizar o bloco `{etapa === 'escolha' && (...)}` e substituir:

```tsx
        {etapa === 'escolha' && (
          <div className={styles.escolha}>
            <Link href={`/login?next=${encodeURIComponent(`/convite/${token}?entrar=1`)}`} className={styles.btnLogin}>
              Já tenho conta — Fazer login
            </Link>
            <button type="button" className={styles.btnSemConta} onClick={() => setEtapa('formulario')}>
              Continuar sem conta
            </button>
          </div>
        )}
```

por:

```tsx
        {etapa === 'escolha' && (
          <div className={styles.escolha}>
            <Link href={`/login?next=${encodeURIComponent(`/convite/${token}?entrar=1`)}`} className={styles.btnLogin}>
              Já tenho conta — Fazer login
            </Link>
            {convite.preco === null ? (
              <button type="button" className={styles.btnSemConta} onClick={() => setEtapa('formulario')}>
                Continuar sem conta
              </button>
            ) : (
              <p className={styles.aviso}>
                Este evento é pago — para pagar sua inscrição, entre com sua conta.
              </p>
            )}
          </div>
        )}
```

- [ ] **Step 2: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/app/convite/\[token\]/page.tsx
git commit -m "fix(convite): bloqueia inscricao sem conta em evento pago (sem suporte a cobranca ainda)"
```

---

### Task 3: Verificação manual no navegador

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Build do Next**

```bash
cd frontend && npx next build
```

Expected: build limpo.

- [ ] **Step 2: Convite de evento pago, logado**

Gerar um link de convite (`ModalCompartilharConvite`) pra um evento pago, abrir numa
sessão já autenticada (ou usar `?entrar=1` depois de logar) — "Confirmar inscrição usando
seu cadastro" deve navegar pra `/eventos/{id}/pagamento/{cobrancaId}` ao concluir (direto,
se o evento não tem campos adicionais; depois de "Salvar e concluir", se tiver).

- [ ] **Step 3: Convite de evento pago, sem conta**

Abrir o mesmo link numa aba anônima (sem sessão) — na tela de escolha, "Continuar sem
conta" não deve aparecer; só "Já tenho conta — Fazer login" e o aviso de que o evento é
pago.

- [ ] **Step 4: Convite de evento grátis, sem conta (regressão)**

Repetir com um evento grátis — "Continuar sem conta" continua aparecendo normalmente,
comportamento intocado.

Não é um teste automatizado (dívida conhecida do projeto) — é a validação manual que a
convenção do projeto pede pra mudança de UI.

---

## Fora de escopo (fica pro Plano 4b)

- Cobrança de convidado sem cadastro (`inscreverConvidado`) — exige migration no `CHECK`
  de `cobranca_evento` e extensão de `CobrancaEventoService`, decidido no brainstorm como
  trabalho de arquitetura à parte, junto da unificação acompanhante↔convidado.
- Quando o Plano 4b resolver isso, a Task 2 deste plano (bloquear "continuar sem conta")
  deixa de fazer sentido e deve ser revertida/substituída pelo fluxo de pagamento real.
