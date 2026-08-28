# Pagamento para convidado sem cadastro — Frontend (Plano 4b.2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Abas "Visitantes"/"Pessoa de fora" (`ModalInscreverAlguem`) e o convite
público (`/convite/{token}`) ganham o mesmo fluxo de pagamento que "Pessoas da igreja"
(Plano 4) já tem, usando o backend do Plano 4b.1.

**Architecture:** Remove primeiro o caminho de criação de acompanhante, morto (nenhuma
tela viva o alcança). Em `ModalInscreverAlguem`, quando o evento é pago, o botão único
"Inscrever" vira dois — "Pagar inscrição de {nome}" / "Enviar link pra {nome} pagar" —
que chamam `useCriarConvidado` com `gerarLink` explícito; sucesso navega pro checkout
(Plano 2) ou abre `ModalCompartilharCobranca` e limpa o formulário pra próxima pessoa.
Em `/convite/{token}`, desfaz o bloqueio do Plano 5 e `FormularioConvidado` passa a
navegar pro checkout quando a resposta trouxer `cobrancaId`.

**Tech Stack:** Next.js/TypeScript/CSS Modules/TanStack Query.

**Spec:** `docs/superpowers/specs/2026-08-26-pagamento-convidado-sem-cadastro-design.md`
(seções 1, 4 e 5). **Depende do Plano 4b.1 (backend) já implementado** — os tipos e
comportamento abaixo assumem `ConvidadoResponse` com `cobrancaId`/`tokenLinkPublico` e
`CriarConvidadoRequest` com `gerarLink`.

## Global Constraints

- Sem framework de teste de frontend — validação é `npx tsc --noEmit` + `npx next
  build` + verificação manual no navegador.
- Não tocar em `ModalInscreverPessoas.tsx` (aba "Pessoas da igreja", já pronta desde o
  Plano 4).

---

### Task 1: Apagar o caminho morto de criar acompanhante

**Files:**
- Delete: `frontend/src/components/module/eventos/ModalConvidado.tsx`
- Delete: `frontend/src/components/module/eventos/ModalConvidado.module.css`
- Delete: `frontend/src/components/module/eventos/ModalConfirmarPagamento.tsx`
- Delete: `frontend/src/hooks/inscricao/useAdicionarConvidado.ts`

- [ ] **Step 1: Confirmar que não há outro uso**

```bash
cd frontend
grep -rln "ModalConvidado\|ModalConfirmarPagamento\|useAdicionarConvidado" src --include="*.tsx" --include="*.ts"
```

Expected: só os quatro arquivos acima (e um comentário em `useCriarConvidado.ts`
mencionando `useAdicionarConvidado` por nome, sem importar) — se aparecer qualquer
outro arquivo importando algum dos quatro, PARAR e reavaliar antes de apagar.

- [ ] **Step 2: Apagar**

```bash
git rm frontend/src/components/module/eventos/ModalConvidado.tsx \
       frontend/src/components/module/eventos/ModalConvidado.module.css \
       frontend/src/components/module/eventos/ModalConfirmarPagamento.tsx \
       frontend/src/hooks/inscricao/useAdicionarConvidado.ts
```

- [ ] **Step 3: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(eventos): remove caminho morto de criar acompanhante (ModalConvidado)"
```

---

### Task 2: Tipos do front acompanham o backend

**Files:**
- Modify: `frontend/src/types/inscricao.type.ts`

**Interfaces:**
- Consumes: `ConvidadoResponse`/`CriarConvidadoRequest` do backend (Plano 4b.1, Task 4).

- [ ] **Step 1: Atualizar `CriarConvidadoRequest`**

Em `frontend/src/types/inscricao.type.ts`, trocar:

```typescript
export interface CriarConvidadoRequest {
  nome: string
  telefone?: string
  /** Preenchido só quando o admin selecionou um Visitante existente na busca (aba "Visitantes"). */
  visitanteId?: string
  respostas?: RespostaRequest[]
}
```

por:

```typescript
export interface CriarConvidadoRequest {
  nome: string
  telefone?: string
  /** Preenchido só quando o admin selecionou um Visitante existente na busca (aba "Visitantes"). */
  visitanteId?: string
  respostas?: RespostaRequest[]
  /** Plano 4b — evento pago: false = quem preencheu paga agora; true = gera link pra
   *  pessoa pagar sozinha depois. Sem efeito em evento gratuito. */
  gerarLink?: boolean
}
```

- [ ] **Step 2: Atualizar `ConvidadoResponse`**

Trocar:

```typescript
export interface ConvidadoResponse {
  inscricaoId: string
  nome: string
  telefone: string | null
}
```

por:

```typescript
export interface ConvidadoResponse {
  inscricaoId: string
  nome: string
  telefone: string | null
  /** Plano 4b — presente só em evento pago. */
  cobrancaId: string | null
  tokenLinkPublico: string | null
}
```

- [ ] **Step 3: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros novos (os campos são opcionais/nulos, nenhum chamador quebra por
enquanto — as Tasks seguintes é que passam a usá-los).

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/types/inscricao.type.ts
git commit -m "feat(inscricao): tipos de convidado acompanham pagamento (gerarLink, cobrancaId)"
```

---

### Task 3: `ModalInscreverAlguem` — pagamento nas abas Visitantes/Pessoa de fora

**Files:**
- Modify: `frontend/src/components/module/eventos/ModalInscreverAlguem.tsx`
- Modify: `frontend/src/components/module/eventos/ModalInscreverAlguem.module.css`

**Interfaces:**
- Consumes: `useCriarConvidado(eventoId).mutate(data, opts)` (já existe, `data` agora
  aceita `gerarLink`, resposta tem `cobrancaId`/`tokenLinkPublico` — Task 2);
  `ModalCompartilharCobranca` (já existe, mesmo componente que o Plano 4 usa em
  `ModalInscreverPessoas`, props `{ nomePessoa, tituloEvento, valor, token, onClose }`);
  rota `/eventos/{eventoId}/pagamento/{cobrancaId}` (Plano 2).
- Produces: nenhuma mudança de `Props` pública.

- [ ] **Step 1: Substituir o conteúdo do arquivo**

Substituir todo o conteúdo de `frontend/src/components/module/eventos/ModalInscreverAlguem.tsx` por:

```tsx
'use client'

import { useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'
import { X, Share2 } from 'lucide-react'
import { ModalInscreverPessoas } from './ModalInscreverPessoas'
import { ModalCompartilharConvite } from './ModalCompartilharConvite'
import { ModalCompartilharCobranca } from './ModalCompartilharCobranca'
import { useVisitantesBuscaLeve } from '@/hooks/visitante/useVisitantesBuscaLeve'
import { useCriarConvidado } from '@/hooks/inscricao/useCriarConvidado'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useDebounce } from '@/hooks/useDebounce'
import { formatarTelefone } from '@/lib/masks'
import type { RespostaRequest } from '@/types/campoPersonalizado.type'
import styles from './ModalInscreverAlguem.module.css'
import painelStyles from './ModalInscreverPessoas.module.css'

type Aba = 'pessoas' | 'visitantes' | 'fora'

interface Props {
  eventoId: string
  tituloEvento: string
  exclusivoMembros: boolean
  /** Evento pago habilita a escolha de pagamento nas abas Visitantes/Pessoa de fora,
   *  além de "Pessoas da igreja" (ModalInscreverPessoas). */
  preco?: number | null
  onClose: () => void
}

export function ModalInscreverAlguem({ eventoId, tituloEvento, exclusivoMembros, preco, onClose }: Props) {
  const router = useRouter()
  const [aba, setAba] = useState<Aba>('pessoas')

  const [buscaVisitante, setBuscaVisitante] = useState('')
  const buscaDebounced = useDebounce(buscaVisitante, 300)
  const { data: visitantes = [] } = useVisitantesBuscaLeve(buscaDebounced)
  const [visitanteSelecionadoId, setVisitanteSelecionadoId] = useState<string | null>(null)

  // Quem já está inscrito neste evento precisa aparecer bloqueado na busca — mesmo padrão
  // da aba "Pessoas da igreja" (ver ModalInscreverPessoas), agora possível pra visitantes
  // graças ao vínculo visitante_id na inscrição.
  const { data: participantes = [] } = useParticipantes(eventoId)
  const visitantesJaInscritos = useMemo(
    () => new Set(participantes.map((p) => p.visitanteId).filter((id): id is string => id !== null)),
    [participantes],
  )

  const [nome, setNome] = useState('')
  const [telefone, setTelefone] = useState('')
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouConfirmar, setTentouConfirmar] = useState(false)

  const { data: campos = [] } = useCamposPersonalizados(eventoId)
  const criarConvidado = useCriarConvidado(eventoId)

  const [compartilharAberto, setCompartilharAberto] = useState(false)
  // Plano 4b: link de cobrança gerado pra um convidado (evento pago, "enviar link").
  const [compartilhandoCobranca, setCompartilhandoCobranca] = useState<{ nome: string; token: string } | null>(null)

  const isPending = criarConvidado.isPending

  function limparFormulario() {
    setNome('')
    setTelefone('')
    setVisitanteSelecionadoId(null)
    setBuscaVisitante('')
    setCamposValores({})
    setTentouConfirmar(false)
  }

  /** Troca de aba limpa nome/telefone/campos — sem isso, selecionar um visitante e depois
   *  ir pra "Pessoa de fora" deixava os dados dele preenchidos lá, como se já tivessem sido
   *  digitados pra outra pessoa. */
  function trocarAba(novaAba: Aba) {
    setAba(novaAba)
    limparFormulario()
  }

  function selecionarVisitante(id: string) {
    if (visitantesJaInscritos.has(id)) return
    const v = visitantes.find((x) => x.id === id)
    if (!v) return
    setVisitanteSelecionadoId(id)
    setNome(v.nome)
    setTelefone(v.telefone ?? '')
  }

  function montarRespostas(): RespostaRequest[] {
    return campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
  }

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function telefoneValido(): boolean {
    const digitos = telefone.replace(/\D/g, '')
    return digitos.length === 10 || digitos.length === 11
  }

  /** Evento gratuito: sempre chamado sem gerarLink (irrelevante). Evento pago: chamado
   *  duas vezes possíveis, uma por botão ("Pagar inscrição"/"Enviar link"). */
  function confirmar(gerarLink: boolean) {
    setTentouConfirmar(true)
    if (!nome.trim() || !telefoneValido() || camposObrigatoriosPendentes()) return

    const visitanteId = aba === 'visitantes' ? visitanteSelecionadoId ?? undefined : undefined
    const nomeConfirmado = nome.trim()
    criarConvidado.mutate(
      { nome: nomeConfirmado, telefone: telefone.replace(/\D/g, ''), visitanteId, respostas: montarRespostas(), gerarLink },
      {
        onSuccess: (resposta) => {
          if (!resposta.cobrancaId) {
            // Evento gratuito — fluxo antigo, sem escolha de pagamento.
            onClose()
            return
          }
          if (gerarLink) {
            setCompartilhandoCobranca({ nome: nomeConfirmado, token: resposta.tokenLinkPublico! })
            limparFormulario()
          } else {
            router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaId}`)
          }
        },
      },
    )
  }

  if (compartilhandoCobranca) {
    return (
      <ModalCompartilharCobranca
        nomePessoa={compartilhandoCobranca.nome}
        tituloEvento={tituloEvento}
        valor={preco ?? 0}
        token={compartilhandoCobranca.token}
        onClose={() => setCompartilhandoCobranca(null)}
      />
    )
  }

  return (
    <>
    <div className={painelStyles.overlay} onMouseDown={() => !isPending && onClose()}>
      <div
        className={painelStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-inscrever-alguem"
      >
        <div className={painelStyles.header}>
          <div>
            <h2 className={painelStyles.titulo} id="titulo-inscrever-alguem">Inscrever alguém</h2>
            <p className={painelStyles.subtitulo}>{tituloEvento}</p>
          </div>
          <button
            type="button"
            className={painelStyles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={isPending}
          >
            <X size={20} />
          </button>
        </div>

        <div className={styles.abas}>
          <button type="button" className={aba === 'pessoas' ? styles.abaAtiva : styles.aba} onClick={() => trocarAba('pessoas')}>
            Pessoas da igreja
          </button>
          <button type="button" className={aba === 'visitantes' ? styles.abaAtiva : styles.aba} onClick={() => trocarAba('visitantes')}>
            Visitantes
          </button>
          <button type="button" className={aba === 'fora' ? styles.abaAtiva : styles.aba} onClick={() => trocarAba('fora')}>
            Pessoa de fora
          </button>
        </div>

        {aba === 'pessoas' && (
          <ModalInscreverPessoas
            eventoId={eventoId}
            tituloEvento={tituloEvento}
            exclusivoMembros={exclusivoMembros}
            preco={preco}
            onClose={onClose}
            embutido
          />
        )}

        {(aba === 'visitantes' || aba === 'fora') && (
          <>
            <div className={styles.conteudoAba}>
              {aba === 'visitantes' && (
                <>
                  <p className={styles.avisoCamposExtra}>
                    Busque alguém que já está cadastrado como visitante na igreja, ou alguém que
                    está numa célula.
                  </p>
                  <div className={styles.buscaContainer}>
                    <input
                      type="text"
                      className={styles.buscaInput}
                      placeholder="Nome de um visitante já conhecido pela igreja…"
                      value={buscaVisitante}
                      onChange={(e) => { setBuscaVisitante(e.target.value); setVisitanteSelecionadoId(null) }}
                    />
                    {visitantes.length > 0 && !visitanteSelecionadoId && (
                      <div className={styles.listaVisitantes}>
                        {visitantes.map((v) => {
                          const bloqueado = visitantesJaInscritos.has(v.id)
                          return (
                            <button
                              key={v.id}
                              type="button"
                              className={`${styles.linhaVisitante} ${bloqueado ? styles.linhaVisitanteBloqueada : ''}`}
                              onClick={() => selecionarVisitante(v.id)}
                              disabled={bloqueado}
                            >
                              {v.nome}{v.telefone ? ` — ${v.telefone}` : ''}
                              {bloqueado && <span className={styles.avisoBloqueado}>Já inscrito neste evento</span>}
                            </button>
                          )
                        })}
                      </div>
                    )}
                  </div>
                  {visitanteSelecionadoId && (
                    <p className={styles.selecionado}>Selecionado: {nome}</p>
                  )}
                </>
              )}

              <label className={styles.campo}>
                <span>Nome*</span>
                <input
                  type="text"
                  placeholder="Ex.: Maria Souza"
                  value={nome}
                  onChange={(e) => { setNome(e.target.value); if (aba === 'visitantes') setVisitanteSelecionadoId(null) }}
                />
                {tentouConfirmar && !nome.trim() && <span className={styles.avisoErro}>O nome é obrigatório.</span>}
              </label>

              <label className={styles.campo}>
                <span>Telefone*</span>
                <input
                  type="text"
                  placeholder="(00) 00000-0000"
                  inputMode="numeric"
                  value={telefone}
                  onChange={(e) => setTelefone(formatarTelefone(e.target.value))}
                />
                {tentouConfirmar && !telefone.trim() && (
                  <span className={styles.avisoErro}>O telefone é obrigatório.</span>
                )}
                {tentouConfirmar && telefone.trim() && !telefoneValido() && (
                  <span className={styles.avisoErro}>Telefone inválido. Digite um número válido com DDD.</span>
                )}
              </label>

              {campos.length > 0 && (
                <p className={styles.avisoCamposExtra}>
                  Este evento também pede as informações abaixo.
                </p>
              )}

              {campos.map((campo) => (
                <label key={campo.id} className={styles.campo}>
                  <span>{campo.label}{campo.obrigatorio ? '*' : ''}</span>
                  {campo.tipo === 'OPCAO_UNICA' || campo.tipo === 'SIM_NAO' ? (
                    <select
                      value={camposValores[campo.id] ?? ''}
                      onChange={(e) => setCamposValores((v) => ({ ...v, [campo.id]: e.target.value }))}
                    >
                      <option value="">Selecione…</option>
                      {(campo.tipo === 'SIM_NAO' ? ['Sim', 'Não'] : campo.opcoes).map((op) => (
                        <option key={op} value={op}>{op}</option>
                      ))}
                    </select>
                  ) : campo.tipo === 'MULTIPLA_ESCOLHA' ? (
                    <div className={styles.listaVisitantes}>
                      {campo.opcoes.map((op) => {
                        const selecionadas = (camposValores[campo.id] ?? '').split(' | ').filter(Boolean)
                        const marcado = selecionadas.includes(op)
                        return (
                          <label key={op} className={painelStyles.linha}>
                            <input
                              type="checkbox"
                              checked={marcado}
                              onChange={() => {
                                const novas = marcado ? selecionadas.filter((s) => s !== op) : [...selecionadas, op]
                                setCamposValores((v) => ({ ...v, [campo.id]: novas.join(' | ') }))
                              }}
                            />
                            {op}
                          </label>
                        )
                      })}
                    </div>
                  ) : (
                    <input
                      type="text"
                      placeholder={campo.placeholder ?? ''}
                      value={camposValores[campo.id] ?? ''}
                      onChange={(e) => setCamposValores((v) => ({ ...v, [campo.id]: e.target.value }))}
                    />
                  )}
                  {tentouConfirmar && campo.obrigatorio && !(camposValores[campo.id]?.trim()) && (
                    <span className={styles.avisoErro}>Essa pergunta é obrigatória.</span>
                  )}
                </label>
              ))}
            </div>

            {aba === 'fora' && (
              <button type="button" className={styles.btnLinkCompartilhar} onClick={() => setCompartilharAberto(true)}>
                <Share2 size={14} aria-hidden="true" />
                Ou compartilhe com quem você quer levar
              </button>
            )}

            <div className={styles.footer}>
              <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={isPending}>
                Cancelar
              </button>
              {preco ? (
                <div className={styles.acoesPagamentoConvidado}>
                  <button type="button" className={styles.btnConfirmar} onClick={() => confirmar(false)} disabled={isPending}>
                    {isPending ? 'Inscrevendo…' : `Pagar inscrição${nome.trim() ? ` de ${nome.trim()}` : ''}`}
                  </button>
                  <button type="button" className={styles.btnEnviarLink} onClick={() => confirmar(true)} disabled={isPending}>
                    Enviar link pra pagar
                  </button>
                </div>
              ) : (
                <button type="button" className={styles.btnConfirmar} onClick={() => confirmar(false)} disabled={isPending}>
                  {isPending ? 'Inscrevendo…' : 'Inscrever'}
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>

    {compartilharAberto && (
      <ModalCompartilharConvite eventoId={eventoId} onClose={() => setCompartilharAberto(false)} />
    )}
    </>
  )
}
```

- [ ] **Step 2: Adicionar o estilo das duas ações de pagamento**

No fim de `frontend/src/components/module/eventos/ModalInscreverAlguem.module.css`, adicionar:

```css
/* ---- Plano 4b: pagamento nas abas Visitantes/Pessoa de fora ---- */

.acoesPagamentoConvidado {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.btnEnviarLink {
  padding: 10px 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-white);
  color: var(--color-text-dark);
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-sm);
  transition: background-color var(--transition-fast);
}

.btnEnviarLink:hover:not(:disabled) {
  background: var(--color-bg-page);
}

.btnEnviarLink:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
```

- [ ] **Step 3: Checar compilação e build**

```bash
cd frontend
npx tsc --noEmit
npx next build
```

Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/components/module/eventos/ModalInscreverAlguem.tsx \
        src/components/module/eventos/ModalInscreverAlguem.module.css
git commit -m "feat(eventos): pagamento nas abas Visitantes/Pessoa de fora (ModalInscreverAlguem)"
```

---

### Task 4: Convite público desbloqueia e paga o caminho sem conta

**Files:**
- Modify: `frontend/src/app/convite/[token]/page.tsx`
- Modify: `frontend/src/app/convite/[token]/FormularioConvidado.tsx`

**Interfaces:**
- Consumes: `conviteService.entrar(token, dados): Promise<ConvidadoResponse>` (já
  existe, resposta agora tem `cobrancaId`/`tokenLinkPublico` — Task 2, Plano 4b.1).

- [ ] **Step 1: Reverter o bloqueio do Plano 5 em `page.tsx`**

Trocar:

```tsx
            {convite.preco === null ? (
              <button type="button" className={styles.btnSemConta} onClick={() => setEtapa('formulario')}>
                Continuar sem conta
              </button>
            ) : (
              <p className={styles.aviso}>
                Este evento é pago — para pagar sua inscrição, entre com sua conta.
              </p>
            )}
```

por:

```tsx
            <button type="button" className={styles.btnSemConta} onClick={() => setEtapa('formulario')}>
              Continuar sem conta
            </button>
```

- [ ] **Step 2: `FormularioConvidado` navega pro checkout quando o evento é pago**

Em `FormularioConvidado.tsx`, adicionar o import:

```typescript
import { useRouter } from 'next/navigation'
```

E trocar:

```typescript
export function FormularioConvidado({ token, campos, onSucesso }: Props) {
  const entrar = useEntrarComoConvidado(token)
```

por:

```typescript
export function FormularioConvidado({ token, campos, onSucesso }: Props) {
  const router = useRouter()
  const entrar = useEntrarComoConvidado(token)
```

E trocar:

```typescript
    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    entrar.mutate(
      { nome: nome.trim(), telefone: telefone.replace(/\D/g, ''), respostas },
      { onSuccess: onSucesso },
    )
```

por:

```typescript
    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))
    entrar.mutate(
      { nome: nome.trim(), telefone: telefone.replace(/\D/g, ''), respostas },
      {
        onSuccess: (resposta) => {
          if (resposta.cobrancaId) {
            router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaId}`)
          } else {
            onSucesso()
          }
        },
      },
    )
```

**`ConvidadoResponse` (Task 2) não tem `eventoId`** — `FormularioConvidado` precisa
receber isso como prop nova, vindo de `convite.eventoId` (`ConvitePublicoResponse` já
tem esse campo, confirmado). Trocar a interface `Props`:

```typescript
interface Props {
  token: string
  eventoId: string
  campos: CampoPersonalizadoResponse[]
  onSucesso: () => void
}

export function FormularioConvidado({ token, eventoId, campos, onSucesso }: Props) {
```

E usar essa `eventoId` (da prop, não de `resposta`) no `router.push` do Step 2 acima:
`router.push(\`/eventos/${eventoId}/pagamento/${resposta.cobrancaId}\`)`.

Em `page.tsx`, trocar a linha que renderiza o componente:

```tsx
          <FormularioConvidado token={token} campos={convite.campos} onSucesso={() => setEtapa('sucesso')} />
```

por:

```tsx
          <FormularioConvidado token={token} eventoId={convite.eventoId} campos={convite.campos} onSucesso={() => setEtapa('sucesso')} />
```

- [ ] **Step 3: Checar compilação e build**

```bash
cd frontend
npx tsc --noEmit
npx next build
```

Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
cd frontend
git add "src/app/convite/[token]/page.tsx" "src/app/convite/[token]/FormularioConvidado.tsx"
git commit -m "feat(convite): desbloqueia e paga inscricao sem conta em evento pago"
```

---

### Task 5: Verificação manual no navegador

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Aba "Visitantes"/"Pessoa de fora", evento pago**

Abrir "Inscrever alguém" num evento pago, preencher nome/telefone numa das duas abas,
clicar "Pagar inscrição" — deve navegar pro checkout (Plano 2). Repetir clicando
"Enviar link" — deve abrir `ModalCompartilharCobranca` e, ao fechar, o formulário
volta limpo, pronto pra próxima pessoa.

- [ ] **Step 2: Evento gratuito (regressão)**

Repetir nas duas abas com evento grátis — continua um botão só "Inscrever", fechando o
modal ao confirmar, sem nenhuma tela de pagamento.

- [ ] **Step 3: Convite público, sem conta, evento pago**

Abrir o link de convite numa aba anônima, clicar "Continuar sem conta" (deve voltar a
aparecer), preencher o formulário — ao confirmar, deve navegar pro checkout.

- [ ] **Step 4: Convite público, sem conta, evento grátis (regressão)**

Repetir com evento grátis — continua indo direto pra tela "Inscrição confirmada!".

Não é um teste automatizado (dívida conhecida do projeto) — é a validação manual que a
convenção do projeto pede pra mudança de UI.
