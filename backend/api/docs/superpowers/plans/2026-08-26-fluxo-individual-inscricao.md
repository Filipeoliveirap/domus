# Fluxo individual de inscrição (Plano 3/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `BotaoConfirmarPresenca` passa a dizer "Se inscrever" nos dois casos (grátis e
pago) e, em evento pago, navega direto para a rota de checkout dedicada (Plano 2) em vez
de abrir o Payment Brick embutido no drawer.

**Architecture:** Remove o mini state-machine `etapaPagamento` (`'escolha' | 'checkout'`)
e os componentes `EscolhaPagamentoPorPessoa`/`PaymentBrickCheckout` deste arquivo — o
titular sempre "paga agora" (regra já fechada), então não existe card de divisão pra uma
pessoa só; o clique já chama `inscrever.mutate` e, se vier `cobrancaPendenteId`, navega
pra `/eventos/{eventoId}/pagamento/{cobrancaId}`. Um novo estado (baseado em dado do
servidor, não em state local) mostra "Pagamento pendente — continuar" quando
`useMinhaInscricao` devolve `inscrito: false` mas `cobrancaPendenteId` preenchido — isso
sobrevive a reload de página, porque `minhaInscricao` (Plano 1, Task 3) já preserva a
cobrança pendente pra inscrição `AGUARDANDO_PAGAMENTO`.

**Tech Stack:** Next.js App Router, TypeScript, CSS Modules, TanStack Query.

**Spec:** `docs/superpowers/specs/2026-08-26-fluxo-pagamento-evento-ux-design.md` (seção
"Fluxo individual (auto-inscrição, `BotaoConfirmarPresenca`)").

## Global Constraints

- Sem framework de teste de frontend (dívida conhecida do projeto) — validação é
  `npx tsc --noEmit` + verificação manual no navegador.
- Não alterar `EscolhaPagamentoPorPessoa`, `PaymentBrickCheckout` nem
  `ModalInscreverPessoas`/`ModalInscreverAlguem` neste plano — eles continuam em uso no
  fluxo em lote (Plano 4). Este plano só edita `BotaoConfirmarPresenca.tsx`/`.module.css`.
- Manter toda a lógica de elegibilidade, "Eu vou" e cancelamento intocada — só o caminho
  de evento pago muda.

---

### Task 1: Reescrever `BotaoConfirmarPresenca` — navega pra rota de checkout, texto unificado

**Files:**
- Modify: `frontend/src/components/module/eventos/BotaoConfirmarPresenca.tsx`
- Modify: `frontend/src/components/module/eventos/BotaoConfirmarPresenca.module.css`

**Interfaces:**
- Consumes: `useMinhaInscricao(eventoId)` (já existe, campo `cobrancaPendenteId`
  preservado em `AGUARDANDO_PAGAMENTO` pelo Plano 1); `useInscrever(eventoId, silencioso,
  opcoes)` (já existe, resposta tem `cobrancaPendenteId: string | null`);
  `useContaPagamento()` (já existe); rota `/eventos/{eventoId}/pagamento/{cobrancaId}`
  (Plano 2).
- Produces: nenhuma interface nova — o componente continua com a mesma `Props` pública
  (`eventoId`, `inicioEm`, `vagasRestantes`, `requerInscricao`, `situacao`, `preco`,
  `onInscritoComSucesso`), consumida sem mudança por quem já o usa (drawer de evento).

- [ ] **Step 1: Substituir o conteúdo do arquivo**

Substituir todo o conteúdo de `frontend/src/components/module/eventos/BotaoConfirmarPresenca.tsx` por:

```tsx
'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CheckCircle2, XCircle, ThumbsUp, AlertTriangle, Clock } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useElegibilidade } from '@/hooks/inscricao/useElegibilidade'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import type { SituacaoEvento } from '@/types/evento.type'
import type { Impedimento } from '@/types/inscricao.type'
import styles from './BotaoConfirmarPresenca.module.css'

interface Props {
  eventoId: string
  inicioEm: string
  vagasRestantes: number | null
  requerInscricao: boolean
  situacao: SituacaoEvento
  preco?: number | null
  /** Chamado só quando a inscrição exige confirmação prévia (requerInscricao) e deu certo,
   *  SEM pagamento pendente — evento pago com sucesso navega pra rota de checkout em vez
   *  de chamar isto (o drawer não teria o que abrir; a pessoa já saiu da tela). */
  onInscritoComSucesso?: () => void
}

export function BotaoConfirmarPresenca({
  eventoId, inicioEm, vagasRestantes, requerInscricao, situacao, preco, onInscritoComSucesso,
}: Props) {
  const router = useRouter()
  const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false)
  const [semConta, setSemConta] = useState(false)
  // 422 contornável: gestor quebrando recorte de elegibilidade
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)

  const role = useAuthStore((s) => s.role)
  // Gestor ignora restrições com confirmação extra
  const ehGestor = podeGerenciarInscricoes(role)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  // Status da conta MP da própria igreja — só importa quando o evento é pago.
  const { data: contaPagamento } = useContaPagamento()
  // Modo "Eu vou": sem toast, feedback é o próprio botão. Evento pago também silencia o
  // toast genérico ("Inscrição confirmada!" seria enganoso — o pagamento ainda não foi
  // feito; a rota de checkout mostra o próprio feedback quando o pagamento é aprovado).
  const inscrever = useInscrever(eventoId, !requerInscricao || !!preco, {
    onContornavel: ehGestor ? (imps) => setImpedimentosParaConfirmar(imps) : undefined,
  })
  const cancelar = useCancelarInscricao(!requerInscricao)

  // Gestor vê o motivo, mas o botão segue ativo (422 abre confirmação)
  const { data: elegibilidade } = useElegibilidade(eventoId)
  const impedimentoPreview = !elegibilidade?.apto ? elegibilidade?.impedimentos[0]?.mensagem : undefined
  const impedimento = ehGestor ? undefined : impedimentoPreview

  const eventoEncerrado = new Date(inicioEm) < new Date()
  const semVagas = vagasRestantes !== null && vagasRestantes <= 0
  const inscricaoBloqueadaPelaSituacao = situacao !== 'AGENDADO'

  function aoConfirmarMesmoAssim() {
    inscrever.mutate({ confirmado: true }, {
      onSuccess: (resposta) => {
        setImpedimentosParaConfirmar(null)
        if (resposta.cobrancaPendenteId) {
          router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaPendenteId}`)
        } else {
          onInscritoComSucesso?.()
        }
      },
    })
  }

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscrever.isPending}
      onConfirmar={aoConfirmarMesmoAssim}
      onClose={() => setImpedimentosParaConfirmar(null)}
      mensagem={
        <>
          <p>Você não atende a todos os requisitos deste evento:</p>
          <ul>
            {impedimentosParaConfirmar.map((imp) => (
              <li key={imp.codigo}>{imp.mensagem}</li>
            ))}
          </ul>
        </>
      }
    />
  )

  if (isLoading) {
    return (
      <button type="button" className={styles.botao} disabled>
        Carregando…
      </button>
    )
  }

  // Modo "Eu vou": alterna direto, sem diálogo de confirmação
  if (!requerInscricao) {
    const marcado = !!minha?.inscrito

    if (!marcado && inscricaoBloqueadaPelaSituacao) return null

    // Fora de AGENDADO: backend recusa cancelar
    if (marcado && !podeCancelarInscricao(situacao)) {
      return (
        <span className={styles.participou}>
          <CheckCircle2 size={15} aria-hidden="true" />
          Você participou deste evento
        </span>
      )
    }

    const pendente = inscrever.isPending || cancelar.isPending
    // Cancelamento não esbarra em elegibilidade
    const bloqueadoPorImpedimento = !marcado && !!impedimento

    function aoClicarEuVou() {
      if (marcado) {
        if (!minha?.id) return
        cancelar.mutate(minha.id)
      } else {
        inscrever.mutate({})
      }
    }

    return (
      <span className={styles.euVouWrap}>
        <button
          type="button"
          className={`${styles.euVou} ${marcado ? styles.euVouAtivo : ''}`}
          onClick={aoClicarEuVou}
          disabled={pendente || bloqueadoPorImpedimento}
          aria-pressed={marcado}
        >
          <ThumbsUp size={15} className={styles.icone} aria-hidden="true" />
          {marcado ? 'Você vai' : 'Eu vou'}
        </button>
        {bloqueadoPorImpedimento && (
          <span className={styles.motivo}>
            <AlertTriangle size={13} aria-hidden="true" />
            {impedimento}
          </span>
        )}
        {modalContorno}
      </span>
    )
  }

  // Pagamento em aberto: inscrição existe como AGUARDANDO_PAGAMENTO. Vem de dado do
  // servidor (não de state local), então sobrevive a reload/fechar e reabrir o drawer —
  // ao contrário do antigo `etapaPagamento`, que se perdia ao desmontar o componente.
  if (!minha?.inscrito && minha?.cobrancaPendenteId) {
    return (
      <Link href={`/eventos/${eventoId}/pagamento/${minha.cobrancaPendenteId}`} className={styles.pagamentoPendente}>
        <Clock size={16} aria-hidden="true" />
        <span>Pagamento pendente — continuar</span>
      </Link>
    )
  }

  if (minha?.inscrito) {
    const podeCancelar = podeCancelarInscricao(situacao)

    return (
      <div className={styles.inscrito}>
        <div className={styles.inscritoStatus}>
          <CheckCircle2 size={18} aria-hidden="true" />
          <div className={styles.inscritoTexto}>
            <strong>Inscrito</strong>
            <span>{podeCancelar ? 'Tudo certo pra você!' : 'Você participou deste evento'}</span>
          </div>
        </div>

        {podeCancelar && (
          <button
            type="button"
            className={styles.cancelarLink}
            onClick={() => setConfirmandoCancelamento(true)}
          >
            <XCircle size={14} aria-hidden="true" />
            Cancelar inscrição
          </button>
        )}

        {confirmandoCancelamento && (
          <ConfirmarCancelamentoInscricao
            nome=""
            proprio
            quantidadeConvidados={minha.acompanhantes.length}
            isLoading={cancelar.isPending}
            onConfirmar={() => {
              if (!minha.id) return
              cancelar.mutate(minha.id, {
                onSuccess: () => setConfirmandoCancelamento(false),
              })
            }}
            onClose={() => setConfirmandoCancelamento(false)}
          />
        )}
      </div>
    )
  }

  if (inscricaoBloqueadaPelaSituacao || eventoEncerrado) {
    return null
  }

  if (semVagas) {
    return (
      <button type="button" className={styles.botao} disabled>
        Vagas esgotadas
      </button>
    )
  }

  return (
    <>
      <button
        type="button"
        className={styles.botao}
        disabled={inscrever.isPending || !!impedimento}
        onClick={() => {
          if (!preco) {
            inscrever.mutate({}, { onSuccess: onInscritoComSucesso })
            return
          }
          // Sem conta MP conectada, a rota de checkout nem carregaria (não há pra quem
          // receber) — aviso com atalho em vez de navegar pra uma tela que ia falhar.
          if (!contaPagamento?.conectada) {
            setSemConta(true)
            return
          }
          inscrever.mutate({}, {
            onSuccess: (resposta) => {
              if (resposta.cobrancaPendenteId) {
                router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaPendenteId}`)
              } else {
                // Não deveria acontecer (evento tem preço), mas não trava a pessoa numa tela morta.
                onInscritoComSucesso?.()
              }
            },
          })
        }}
      >
        <CheckCircle2 size={18} aria-hidden="true" />
        {inscrever.isPending ? 'Inscrevendo…' : 'Se inscrever'}
      </button>

      {impedimento && (
        <span className={styles.motivo}>
          <AlertTriangle size={14} aria-hidden="true" />
          {impedimento}
        </span>
      )}

      {semConta && preco && (
        <div className={styles.avisoSemConta}>
          <AlertTriangle size={16} aria-hidden="true" />
          <span>
            Este evento é pago, mas a igreja ainda não conectou uma conta para receber
            pagamentos.{' '}
            {ehGestor ? (
              <Link href="/configuracoes/igreja">Conectar agora</Link>
            ) : (
              'Fale com a secretaria da igreja.'
            )}
          </span>
          <button type="button" className={styles.cancelarLink} onClick={() => setSemConta(false)}>
            Fechar
          </button>
        </div>
      )}

      {modalContorno}
    </>
  )
}
```

- [ ] **Step 2: Adicionar o estilo do estado "pagamento pendente"**

No fim de `frontend/src/components/module/eventos/BotaoConfirmarPresenca.module.css`, adicionar:

```css
/* ---- Plano 3: retomar pagamento pendente (AGUARDANDO_PAGAMENTO) ---- */

.pagamentoPendente {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  padding: 12px 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-warning, #d97706);
  background: var(--color-bg-page);
  color: var(--color-warning, #d97706);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  transition: opacity var(--transition-fast);
}

.pagamentoPendente:hover {
  opacity: 0.85;
}
```

- [ ] **Step 3: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros. Se o compilador reclamar de `resposta.cobrancaPendenteId` não
existir no tipo de retorno de `inscrever.mutate`, checar `frontend/src/services/inscricao.service.ts`
— o tipo devolvido por `inscricoesService.inscrever` precisa expor esse campo (ele já
existe hoje, usado em `EscolhaPagamentoPorPessoa`'s caller dentro de
`ModalInscreverPessoas.tsx`, então já deve bater).

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/components/module/eventos/BotaoConfirmarPresenca.tsx \
        src/components/module/eventos/BotaoConfirmarPresenca.module.css
git commit -m "feat(eventos): auto-inscricao em evento pago navega pra rota de checkout dedicada"
```

---

### Task 2: Verificação manual no navegador

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Evento grátis**

Abrir um evento grátis com `requerInscricao=true`, clicar em "Se inscrever" (texto novo,
sem navegação) — deve confirmar na hora, igual antes.

- [ ] **Step 2: Evento pago — igreja sem conta conectada**

Abrir um evento pago numa igreja sem `ContaPagamentoIgreja`, clicar "Se inscrever" — deve
mostrar o aviso "igreja ainda não conectou uma conta", sem navegar.

- [ ] **Step 3: Evento pago — fluxo completo**

Com a igreja conectada (ou usando dados de teste inseridos direto no banco, como no Plano
2), clicar "Se inscrever" num evento pago — deve navegar pra
`/eventos/{id}/pagamento/{cobrancaId}` (a rota do Plano 2), sem mostrar toast de
"Inscrição confirmada!".

- [ ] **Step 4: Retomar pagamento pendente**

Com uma inscrição `AGUARDANDO_PAGAMENTO` já existente (não pagar o Brick, só recarregar a
página do evento), o botão deve aparecer como "Pagamento pendente — continuar" em vez de
"Se inscrever" de novo, e o link deve levar pra mesma cobrança.

Não é um teste automatizado (dívida conhecida do projeto: sem Jest/Vitest/Playwright) —
é a validação manual que a convenção do projeto pede pra mudança de UI.
