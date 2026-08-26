# Fluxo em lote — Pessoas da igreja (Plano 4/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Na aba "Pessoas da igreja" (`ModalInscreverPessoas`), evento pago troca a
seleção múltipla por checkbox por um fluxo de **uma pessoa por vez**: clicar numa pessoa
abre — se o evento tiver campos adicionais, um passo pra preenchê-los — e depois a
escolha "pagar agora" (navega pra rota de checkout do Plano 2) ou "enviar link" (mesmo
padrão já existente, `ModalCompartilharCobranca`).

**Architecture:** Backend ganha um campo (`inscricaoId`) na resposta de
`POST /eventos/{id}/inscricoes/pessoas` — sem ele o front não tem como chamar
`PUT /inscricoes/{id}/respostas` (endpoint que **já existe e já funciona**, hoje só usado
pelo convite público) depois de criar a inscrição. Fora esse campo, nenhuma mudança de
backend é necessária — a arquitetura de duas chamadas (criar inscrição → anexar
respostas) já está pronta. No front, evento **gratuito** mantém a lista com checkbox e
confirmação em lote exatamente como está hoje (nada muda nesse caminho); evento **pago**
passa a usar uma lista sem checkbox onde clicar numa linha abre um painel para aquela
pessoa só, reaproveitando `CamposExtrasForm` (já existe, controlado, usado hoje só no
convidado sem cadastro) e `ModalCompartilharCobranca` (já existe). O card
`EscolhaPagamentoPorPessoa` (usado só neste arquivo) é removido — a decisão "pagar
agora"/"enviar link" que ele fazia em lote agora é por pessoa, inline.

**Tech Stack:** Java 21/Spring Boot (backend), Next.js/TypeScript/CSS Modules/TanStack
Query (frontend).

**Spec:** `docs/superpowers/specs/2026-08-26-fluxo-pagamento-evento-ux-design.md` (seções
"Fluxo em lote" e "Campos adicionais no fluxo de adicionar pessoa").

## Escopo desta entrega (decidido no brainstorm)

- Só a aba **"Pessoas da igreja"**. As abas "Visitantes"/"Pessoa de fora"
  (`ModalInscreverAlguem`) ficam para o Plano 4b — hoje `inscreverConvidado` não cria
  `CobrancaEvento` nenhuma (gap real, sem suporte a pagamento pra convidado sem cadastro);
  corrigir isso é trabalho de arquitetura à parte (migration no `CHECK` de
  `cobranca_evento`, extensão de `CobrancaEventoService`), decidido para não entrar aqui.
- Quando o evento tem campos adicionais, esta entrega oferece só **"preencher agora"**
  (o gestor preenche ali mesmo) — a opção "compartilhar pra pessoa preencher" (Seção 6 da
  spec) faz sentido pleno pra convidado sem cadastro (via `/convite/{token}`, que o Plano
  4b/5 vai equipar com pagamento), mas não tem um destino claro pra uma pessoa **já
  cadastrada** na igreja (ela já pode se auto-inscrever pelo próprio evento). Fica
  registrado aqui, não escondido: se isso for necessário depois, é uma decisão de UX à
  parte, não uma omissão.

## Global Constraints

- Backend: Mockito puro (Estilo A, `mock()` manual em `@BeforeEach`) — mesmo padrão de
  `InscricaoServiceTest`.
- Frontend: sem framework de teste — validação é `npx tsc --noEmit` + `npx next build` +
  verificação manual no navegador.
- Não tocar em `ModalInscreverAlguem.tsx` neste plano (Plano 4b cobre as abas
  Visitantes/Pessoa de fora).
- `EscolhaPagamentoPorPessoa.tsx`/`.module.css` são apagados por completo (só eram usados
  aqui; ninguém mais referencia — checado via grep antes de escrever este plano).

---

### Task 1: Backend — `inscricaoId` na resposta de `inscreverPessoas`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/PessoaInscritaComCobranca.java` — caminho correto é `evento/inscricao/DTOs`, não `pagamento/cobranca/DTOs` (ver Interfaces abaixo).
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java:274-284`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Produces: `PessoaInscritaComCobranca(UUID pessoaId, UUID inscricaoId, UUID cobrancaId, String tokenLinkPublico)`
  — `inscricaoId` é o campo novo, inserido como 2º parâmetro (logo depois de `pessoaId`,
  antes de `cobrancaId`, que pode ser nulo em evento gratuito — `inscricaoId` nunca é
  nulo, então fica antes na ordem por convenção de "obrigatório antes de opcional").

- [ ] **Step 1: Escrever o teste que falha**

Adicionar em `InscricaoServiceTest.java` (perto dos outros testes de `inscreverPessoas` —
buscar por esse nome de método no arquivo para achar a vizinhança certa):

```java
    @Test
    void inscreverPessoasDevolveInscricaoIdDeCadaPessoa() {
        Evento evento = evento(10);
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(inscricaoRepository.listarPessoaIdsJaInscritos(any(), any())).thenReturn(List.of());

        var resultado = service.inscreverPessoas(eventoId, List.of(pessoaId), null,
                null, pessoaId, "ADMIN_IGREJA", false, igrejaId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).inscricaoId()).isNotNull();
        assertThat(resultado.get(0).pessoaId()).isEqualTo(pessoaId);
    }
```

Se o arquivo ainda não tiver um `import static org.mockito.ArgumentMatchers.any;`
compatível com essa chamada (`any()` sem tipo explícito em `listarPessoaIdsJaInscritos`),
usar a forma já usada em outros testes do arquivo (`any(), any()` já aparece em outros
mocks — copiar o padrão local em vez de inventar um novo).

- [ ] **Step 2: Rodar o teste e confirmar que falha**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=InscricaoServiceTest#inscreverPessoasDevolveInscricaoIdDeCadaPessoa
```

Expected: FAIL — `resultado.get(0).inscricaoId()` não existe (erro de compilação até o
Step 3 rodar).

- [ ] **Step 3: Implementar**

Mover/criar `PessoaInscritaComCobranca.java` no pacote correto
(`com.domus.api.modules.evento.inscricao.DTOs`, onde já vive hoje — a linha "Files" acima
existe só pra deixar claro que **não** é o pacote de pagamento, apesar do nome). Conteúdo:

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import java.util.UUID;

/**
 * Item da resposta de {@code POST /eventos/{id}/inscricoes/pessoas} — o que o front
 * precisa por pessoa inscrita num evento pago pra decidir o próximo passo:
 * {@code inscricaoId} (Plano 4) permite anexar respostas de campos personalizados via
 * {@code PUT /inscricoes/{id}/respostas} depois de criar; {@code cobrancaId} não nulo +
 * {@code tokenLinkPublico} nulo → navegar pra rota de checkout (paga agora);
 * {@code cobrancaId} + {@code tokenLinkPublico} não nulos → abrir
 * {@code ModalCompartilharCobranca} (a pessoa recebe um link pra pagar sozinha depois);
 * os dois nulos → evento gratuito, nada a fazer.
 */
public record PessoaInscritaComCobranca(
        UUID pessoaId,
        UUID inscricaoId,
        UUID cobrancaId,
        String tokenLinkPublico
) {}
```

Em `InscricaoService.java`, dentro do laço de `inscreverPessoas` (linhas ~274-284),
trocar:

```java
            resultado.add(new PessoaInscritaComCobranca(
                    pessoaId,
                    r.cobranca() != null ? r.cobranca().getId() : null,
                    r.cobranca() != null ? r.cobranca().getTokenLinkPublico() : null));
```

por:

```java
            resultado.add(new PessoaInscritaComCobranca(
                    pessoaId,
                    r.inscricao().getId(),
                    r.cobranca() != null ? r.cobranca().getId() : null,
                    r.cobranca() != null ? r.cobranca().getTokenLinkPublico() : null));
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=InscricaoServiceTest
```

Expected: PASS em todos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/DTOs/PessoaInscritaComCobranca.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(inscricao): inscreverPessoas devolve inscricaoId de cada pessoa"
```

---

### Task 2: Frontend — tipo e uso de `inscricaoId` no service

**Files:**
- Modify: `frontend/src/types/inscricao.type.ts`

**Interfaces:**
- Consumes: `PessoaInscritaComCobranca` (Task 1).
- Produces: nenhuma interface nova — só alinha o tipo TS com o DTO do backend.

- [ ] **Step 1: Adicionar o campo ao tipo**

Em `frontend/src/types/inscricao.type.ts`, trocar:

```typescript
export interface PessoaInscritaComCobranca {
  pessoaId: string
  cobrancaId: string | null
  tokenLinkPublico: string | null
}
```

por:

```typescript
export interface PessoaInscritaComCobranca {
  pessoaId: string
  inscricaoId: string
  cobrancaId: string | null
  tokenLinkPublico: string | null
}
```

- [ ] **Step 2: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros novos (`inscricaoId` ainda não é lido por ninguém — só o tipo mudou).

- [ ] **Step 3: Commit**

```bash
cd frontend
git add src/types/inscricao.type.ts
git commit -m "feat(inscricao): tipo PessoaInscritaComCobranca ganha inscricaoId"
```

---

### Task 3: Apagar `EscolhaPagamentoPorPessoa` (fica sem uso após a Task 4)

**Files:**
- Delete: `frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.tsx`
- Delete: `frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.module.css`

- [ ] **Step 1: Confirmar que não há outro uso**

```bash
cd frontend && grep -rn "EscolhaPagamentoPorPessoa" src --include="*.tsx" --include="*.ts"
```

Expected: só aparece dentro do próprio `ModalInscreverPessoas.tsx` (que a Task 4 vai
reescrever, removendo essa referência) — se aparecer em qualquer outro arquivo além
desses dois e do `ModalInscreverPessoas.tsx`, PARAR e reavaliar antes de apagar.

- [ ] **Step 2: Apagar os dois arquivos**

```bash
git rm frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.tsx \
       frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.module.css
```

(Executar isso DEPOIS da Task 4 remover as importações — se rodado antes, o build quebra
até a Task 4 terminar. Ordem sugerida: fazer a Task 4 primeiro, deixando este `git rm`
como parte do commit da Task 4, ou rodar as duas em sequência sem commitar a Task 4 até
apagar estes arquivos. Este plano lista como Task 3 por clareza de leitura, mas execute-a
**logo após** a Task 4, antes do commit final da Task 4.)

---

### Task 4: Reescrever `ModalInscreverPessoas` — fluxo pago pessoa a pessoa

**Files:**
- Modify: `frontend/src/components/module/eventos/ModalInscreverPessoas.tsx`
- Modify: `frontend/src/components/module/eventos/ModalInscreverPessoas.module.css`

**Interfaces:**
- Consumes: `useCamposPersonalizados(eventoId)` (já existe);
  `CamposExtrasForm` (já existe, `frontend/src/components/module/eventos/CamposExtrasForm.tsx`,
  props `{ campos, valores, onChange, tentouEnviar }`);
  `useResponderCampos()` (já existe, retorna `{ responder(inscricaoId, dados,
  acompanhanteId?), isLoading, erro }`); `useInscreverPessoas` (já existe, resposta agora
  tem `inscricaoId` por item — Task 1/2); `ModalCompartilharCobranca` (já existe, props
  `{ nomePessoa, tituloEvento, valor, token, onClose }`); rota
  `/eventos/{eventoId}/pagamento/{cobrancaId}` (Plano 2).
- Produces: `ModalInscreverPessoas` mantém a mesma `Props` pública (nenhuma mudança de
  assinatura) — quem já o usa (`ModalInscreverAlguem`, a tela do evento) não muda.

**Comportamento por evento:**
- **Gratuito** (`preco` nulo/undefined): idêntico a hoje — lista com checkbox, seleção
  múltipla, confirmar em lote.
- **Pago:** primeiro checa `contaPagamento?.conectada` (se não, mostra o aviso "sem
  conta", igual hoje). Se conectada: lista **sem checkbox** — clicar numa pessoa abre um
  painel só para ela (campos adicionais, se houver, com "preencher agora"; depois "Pagar
  inscrição de {nome}" ou "Enviar link pra {nome} pagar"). "Enviar link" volta pra lista
  ao fechar `ModalCompartilharCobranca`; "Pagar" navega pra rota de checkout.

- [ ] **Step 1: Substituir o conteúdo do arquivo**

Substituir todo o conteúdo de `frontend/src/components/module/eventos/ModalInscreverPessoas.tsx` por:

```tsx
'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import Image from 'next/image'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { Search, X, Check, AlertTriangle, ArrowLeft } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useParticipantes } from '@/hooks/inscricao/useParticipantes'
import { useInscreverPessoas } from '@/hooks/inscricao/useInscreverPessoas'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import { useDebounce } from '@/hooks/useDebounce'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { iniciais, rotuloVinculo } from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { CamposExtrasForm } from './CamposExtrasForm'
import { ModalCompartilharCobranca } from './ModalCompartilharCobranca'
import type { PessoaResponse } from '@/types/pessoa.type'
import type { Impedimento } from '@/types/inscricao.type'
import styles from './ModalInscreverPessoas.module.css'

interface Props {
  eventoId: string
  tituloEvento: string
  /** Evento exclusivo para membros: só quem tem vínculo MEMBRO pode ser inscrito. */
  exclusivoMembros: boolean
  /** Evento pago habilita o fluxo pessoa a pessoa antes de confirmar. `null`/`undefined`
   *  = evento gratuito, fluxo antigo (seleção múltipla) sem mudança. */
  preco?: number | null
  onClose: () => void
  /** Usado dentro de ModalInscreverAlguem (aba "Pessoas da igreja") — sem overlay nem
   *  cabeçalho próprios, porque o modal pai já mostra os dois. */
  embutido?: boolean
}

function jaInscrita(p: PessoaResponse, jaInscritos: Set<string>): boolean {
  return jaInscritos.has(p.id)
}

// Não bloqueia: EXCLUSIVO_MEMBROS é contornável pelo backend para quem gerencia.
function avisoElegibilidade(p: PessoaResponse, exclusivoMembros: boolean): string | null {
  if (exclusivoMembros && p.vinculo !== 'MEMBRO') {
    return 'Congregante — evento exclusivo para membros'
  }
  return null
}

export function ModalInscreverPessoas({
  eventoId, tituloEvento, exclusivoMembros, preco, onClose, embutido = false,
}: Props) {
  const router = useRouter()
  const [busca, setBusca] = useState('')
  // Evento gratuito: seleção múltipla por checkbox (Map pra guardar o nome no momento da
  // seleção — a lista de busca pode mudar de página/termo depois).
  const [selecionados, setSelecionados] = useState<Map<string, string>>(new Map())
  // Evento pago: uma pessoa por vez.
  const [pessoaClicada, setPessoaClicada] = useState<{ id: string; nome: string } | null>(null)
  // Guarda qual ação foi tentada, pra o retry de "inscrever mesmo assim" (422 contornável)
  // refazer a MESMA escolha — sem isto, o retry sempre viraria "pagar agora".
  const [acaoPendente, setAcaoPendente] = useState<'pagar' | 'link' | null>(null)
  const [camposValores, setCamposValores] = useState<Record<string, string>>({})
  const [tentouConfirmarCampos, setTentouConfirmarCampos] = useState(false)
  const [compartilhando, setCompartilhando] = useState<{ nome: string; token: string } | null>(null)
  // Impedimentos contornáveis devolvidos pelo 422 — abre a confirmação "inscrever mesmo
  // assim" só para quem gerencia. `null` = confirmação fechada.
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const role = useAuthStore((s) => s.role)
  const ehGestor = podeGerenciarInscricoes(role)

  const buscaDebounced = useDebounce(busca, 300)
  const { data, isLoading } = usePessoas({ q: buscaDebounced, page: 0, size: 30 })
  const pessoas = data?.content ?? []

  // Quem já está inscrito precisa aparecer desabilitado. `useParticipantes` é a lista
  // reduzida que QUALQUER pessoa autenticada pode chamar — a completa (`useListaInscritos`)
  // é restrita a ADMIN/LÍDER e devolveria 401 para uma pessoa comum abrindo este modal.
  const { data: participantes = [] } = useParticipantes(eventoId)
  const jaInscritos = useMemo(
    () => new Set(participantes.map((p) => p.pessoaId).filter((id): id is string => id !== null)),
    [participantes],
  )

  // Só importa quando o evento é pago.
  const { data: contaPagamento } = useContaPagamento()
  const { data: campos = [] } = useCamposPersonalizados(eventoId)
  const { responder: responderCampos } = useResponderCampos()

  const inscreverPessoas = useInscreverPessoas(eventoId, {
    onContornavel: ehGestor
      ? (impedimentos) => setImpedimentosParaConfirmar(impedimentos)
      : undefined,
  })

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !inscreverPessoas.isPending && !pessoaClicada && !compartilhando) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, inscreverPessoas.isPending, pessoaClicada, compartilhando])

  function alternarSelecao(p: PessoaResponse) {
    setSelecionados((atual) => {
      const novo = new Map(atual)
      if (novo.has(p.id)) novo.delete(p.id)
      else novo.set(p.id, p.nome)
      return novo
    })
  }

  function camposObrigatoriosPendentes(): boolean {
    return campos.some((c) => c.obrigatorio && !(camposValores[c.id]?.trim()))
  }

  function voltarParaLista() {
    setPessoaClicada(null)
    setCamposValores({})
    setTentouConfirmarCampos(false)
    setAcaoPendente(null)
  }

  /** Núcleo do fluxo pago pessoa-a-pessoa: cria a inscrição (com ou sem link), anexa
   *  respostas de campos adicionais se houver, e decide o próximo passo. */
  function confirmarPessoa(gerarLink: boolean, confirmado = false) {
    if (!pessoaClicada) return
    if (!confirmado && camposObrigatoriosPendentes()) {
      setTentouConfirmarCampos(true)
      return
    }
    setAcaoPendente(gerarLink ? 'link' : 'pagar')

    const respostas = campos.map((c) => ({ campoId: c.id, valor: camposValores[c.id] ?? '' }))

    inscreverPessoas.mutate(
      { pessoaIds: [pessoaClicada.id], pessoasParaLink: gerarLink ? [pessoaClicada.id] : undefined, confirmado },
      {
        onSuccess: async (lista) => {
          setImpedimentosParaConfirmar(null)
          const item = lista[0]
          if (campos.length > 0 && item) {
            await responderCampos(item.inscricaoId, respostas)
          }
          if (!item?.cobrancaId) {
            // Não deveria acontecer (evento tem preço), mas não trava o gestor numa tela morta.
            voltarParaLista()
            return
          }
          if (gerarLink) {
            setCompartilhando({ nome: pessoaClicada.nome, token: item.tokenLinkPublico! })
            voltarParaLista()
          } else {
            router.push(`/eventos/${eventoId}/pagamento/${item.cobrancaId}`)
          }
        },
        onError: () => setImpedimentosParaConfirmar(null),
      },
    )
  }

  function aoConfirmarSelecaoGratuita() {
    const pessoaIds = Array.from(selecionados.keys())
    inscreverPessoas.mutate({ pessoaIds }, {
      onSuccess: () => {
        setImpedimentosParaConfirmar(null)
        onClose()
      },
      onError: () => setImpedimentosParaConfirmar(null),
    })
  }

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscreverPessoas.isPending}
      onConfirmar={() => {
        if (pessoaClicada) {
          confirmarPessoa(acaoPendente === 'link', true)
        } else {
          inscreverPessoas.mutate({ pessoaIds: Array.from(selecionados.keys()), confirmado: true }, {
            onSuccess: () => { setImpedimentosParaConfirmar(null); onClose() },
          })
        }
      }}
      onClose={() => setImpedimentosParaConfirmar(null)}
      mensagem={
        <>
          <p>
            {(pessoaClicada ? 1 : selecionados.size) === 1
              ? 'Esta pessoa não atende'
              : 'Uma ou mais pessoas selecionadas não atendem'}
            {' '}a todos os requisitos deste evento:
          </p>
          <ul className={styles.listaImpedimentos}>
            {impedimentosParaConfirmar.map((imp) => (
              <li key={imp.codigo}>{imp.mensagem}</li>
            ))}
          </ul>
        </>
      }
    />
  )

  // ---- Evento pago: sem conta MP conectada ----
  if (preco && !contaPagamento?.conectada) {
    const conteudo = (
      <div className={styles.lista}>
        <p className={styles.estado}>
          Este evento é pago, mas a igreja ainda não conectou uma conta para receber
          pagamentos.{' '}
          {ehGestor ? <Link href="/configuracoes/igreja">Conectar agora</Link> : 'Fale com a secretaria da igreja.'}
        </p>
      </div>
    )
    return embutido ? conteudo : (
      <div className={styles.overlay} onMouseDown={() => onClose()}>
        <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
          <div className={styles.header}>
            <div>
              <h2 className={styles.titulo}>Inscrever pessoas</h2>
              <p className={styles.subtitulo}>{tituloEvento}</p>
            </div>
            <button type="button" className={styles.btnFechar} onClick={onClose} aria-label="Fechar"><X size={20} /></button>
          </div>
          {conteudo}
        </div>
      </div>
    )
  }

  // ---- Evento pago: compartilhando o link de uma pessoa ----
  if (preco && compartilhando) {
    return (
      <ModalCompartilharCobranca
        nomePessoa={compartilhando.nome}
        tituloEvento={tituloEvento}
        valor={preco}
        token={compartilhando.token}
        onClose={() => setCompartilhando(null)}
      />
    )
  }

  // ---- Evento pago: painel de uma pessoa (campos adicionais + escolha de pagamento) ----
  if (preco && pessoaClicada) {
    const conteudo = (
      <div className={styles.lista}>
        <button type="button" className={styles.botaoVoltar} onClick={voltarParaLista}>
          <ArrowLeft size={16} aria-hidden="true" />
          Voltar
        </button>

        <div className={styles.painelPessoa}>
          <h3 className={styles.painelTitulo}>Inscrever {pessoaClicada.nome}</h3>

          {campos.length > 0 && (
            <CamposExtrasForm
              campos={campos}
              valores={camposValores}
              onChange={(campoId, valor) => setCamposValores((v) => ({ ...v, [campoId]: valor }))}
              tentouEnviar={tentouConfirmarCampos}
            />
          )}

          <p className={styles.painelValor}>{formatarMoeda(preco)}</p>

          <div className={styles.acoesPagamento}>
            <button
              type="button"
              className={styles.botaoPagar}
              disabled={inscreverPessoas.isPending}
              onClick={() => confirmarPessoa(false)}
            >
              {inscreverPessoas.isPending ? 'Inscrevendo…' : `Pagar inscrição de ${pessoaClicada.nome}`}
            </button>
            <button
              type="button"
              className={styles.botaoLink}
              disabled={inscreverPessoas.isPending}
              onClick={() => confirmarPessoa(true)}
            >
              Enviar link pra {pessoaClicada.nome} pagar
            </button>
          </div>
        </div>
      </div>
    )

    return (
      <>
        {embutido ? conteudo : (
          <div className={styles.overlay} onMouseDown={() => !inscreverPessoas.isPending && onClose()}>
            <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
              <div className={styles.header}>
                <div>
                  <h2 className={styles.titulo}>Inscrever pessoas</h2>
                  <p className={styles.subtitulo}>{tituloEvento}</p>
                </div>
                <button type="button" className={styles.btnFechar} onClick={onClose} aria-label="Fechar" disabled={inscreverPessoas.isPending}><X size={20} /></button>
              </div>
              {conteudo}
            </div>
          </div>
        )}
        {modalContorno}
      </>
    )
  }

  // ---- Lista: sem checkbox se pago, com checkbox se gratuito ----
  const listaConteudo = (
    <div className={styles.lista}>
      {isLoading ? (
        <p className={styles.estado}>Carregando pessoas…</p>
      ) : pessoas.length === 0 ? (
        <p className={styles.estado}>Nenhuma pessoa encontrada.</p>
      ) : (
        pessoas.map((p) => {
          const bloqueado = jaInscrita(p, jaInscritos)
          const aviso = !bloqueado ? avisoElegibilidade(p, exclusivoMembros) : null
          const marcado = !preco && selecionados.has(p.id)
          return (
            <label
              key={p.id}
              className={[
                styles.linha,
                marcado ? styles.linhaSelecionada : '',
                bloqueado ? styles.linhaBloqueada : '',
              ].join(' ')}
              onClick={(e) => {
                if (preco && !bloqueado) {
                  e.preventDefault()
                  setPessoaClicada({ id: p.id, nome: p.nome })
                }
              }}
            >
              {!preco && (
                <input
                  type="checkbox"
                  className={styles.checkbox}
                  checked={marcado}
                  disabled={bloqueado}
                  onChange={() => alternarSelecao(p)}
                />
              )}
              <span className={styles.avatar}>
                {urlFoto(p.fotoId, 'THUMB') ? (
                  <Image src={urlFoto(p.fotoId, 'THUMB')!} alt="" width={36} height={36} unoptimized className={styles.avatarFoto} />
                ) : (
                  iniciais(p.nome)
                )}
              </span>
              <span className={styles.info}>
                <span className={styles.nome}>{p.nome}</span>
                <span className={styles.detalhe}>
                  {bloqueado ? 'Já inscrita neste evento' : (aviso ?? rotuloVinculo(p.vinculo))}
                </span>
              </span>
              {aviso && !bloqueado && (
                <AlertTriangle size={15} className={styles.avisoIcone} aria-label="Pode não ser elegível para este evento" />
              )}
              {marcado && <Check size={16} className={styles.checkIcone} aria-hidden="true" />}
            </label>
          )
        })
      )}
    </div>
  )

  const conteudo = (
    <>
      {!embutido && (
        <div className={styles.header}>
          <div>
            <h2 className={styles.titulo} id="titulo-inscrever-pessoas">Inscrever pessoas</h2>
            <p className={styles.subtitulo}>{tituloEvento}</p>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={inscreverPessoas.isPending}
          >
            <X size={20} />
          </button>
        </div>
      )}

      <div className={styles.buscaWrap}>
        <Search size={16} className={styles.buscaIcone} />
        <input
          ref={inputRef}
          type="text"
          className={styles.buscaInput}
          placeholder="Buscar por nome…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      </div>

      {listaConteudo}

      {!preco && (
        <div className={styles.footer}>
          <span className={styles.contador}>
            {selecionados.size} selecionado{selecionados.size === 1 ? '' : 's'}
          </span>
          <div className={styles.footerAcoes}>
            <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={inscreverPessoas.isPending}>
              Cancelar
            </button>
            <button
              type="button"
              className={styles.btnConfirmar}
              onClick={aoConfirmarSelecaoGratuita}
              disabled={selecionados.size === 0 || inscreverPessoas.isPending}
            >
              {inscreverPessoas.isPending ? 'Inscrevendo…' : 'Inscrever'}
            </button>
          </div>
        </div>
      )}
    </>
  )

  return (
    <>
      {embutido ? conteudo : (
        <div className={styles.overlay} onMouseDown={() => !inscreverPessoas.isPending && onClose()}>
          <div
            className={styles.modal}
            onMouseDown={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="titulo-inscrever-pessoas"
          >
            {conteudo}
          </div>
        </div>
      )}
      {modalContorno}
    </>
  )
}
```

- [ ] **Step 2: Adicionar os estilos novos**

No fim de `frontend/src/components/module/eventos/ModalInscreverPessoas.module.css`, adicionar:

```css
/* ---- Plano 4: painel de uma pessoa (evento pago) ---- */

.botaoVoltar {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  align-self: flex-start;
  margin-bottom: 8px;
  color: var(--color-text-muted);
  font-size: var(--font-size-sm);
  transition: color var(--transition-fast);
}

.botaoVoltar:hover {
  color: var(--color-primary);
}

.painelPessoa {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.painelTitulo {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
}

.painelValor {
  font-size: var(--font-size-lg, 18px);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
}

.acoesPagamento {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.botaoPagar {
  padding: 12px 16px;
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, var(--color-primary) 0%, #2563eb 100%);
  color: #fff;
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
  transition: opacity var(--transition-fast);
}

.botaoPagar:hover:not(:disabled) {
  opacity: 0.9;
}

.botaoLink {
  padding: 12px 16px;
  border-radius: var(--radius-md);
  border: 1px solid var(--color-border);
  background: var(--color-bg-white);
  color: var(--color-text-dark);
  font-weight: var(--font-weight-medium);
  font-size: var(--font-size-sm);
  transition: background-color var(--transition-fast);
}

.botaoLink:hover:not(:disabled) {
  background: var(--color-bg-page);
}

.botaoPagar:disabled,
.botaoLink:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
```

- [ ] **Step 3: Apagar `EscolhaPagamentoPorPessoa` (Task 3) e checar compilação**

```bash
cd frontend
git rm src/components/module/eventos/EscolhaPagamentoPorPessoa.tsx \
       src/components/module/eventos/EscolhaPagamentoPorPessoa.module.css
npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 4: Build do Next**

```bash
cd frontend && npx next build
```

Expected: build limpo, sem warning de import não resolvido.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/components/module/eventos/ModalInscreverPessoas.tsx \
        src/components/module/eventos/ModalInscreverPessoas.module.css
git commit -m "feat(eventos): fluxo pessoa-a-pessoa pra inscrever pessoas da igreja em evento pago"
```

---

### Task 5: Verificação manual no navegador

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Evento grátis**

Abrir "Inscrever pessoas" num evento grátis — checkbox e seleção múltipla continuam
idênticos a antes.

- [ ] **Step 2: Evento pago sem campos adicionais**

Abrir "Inscrever pessoas" num evento pago (sem campos adicionais configurados) — lista
sem checkbox; clicar numa pessoa abre o painel direto com as duas opções de pagamento
(sem seção de campos). "Enviar link" volta pra lista com o modal de compartilhar; "Pagar"
navega pra rota de checkout (Plano 2).

- [ ] **Step 3: Evento pago com campos adicionais obrigatórios**

Configurar um campo obrigatório no evento, repetir o clique numa pessoa — o painel mostra
o campo antes dos botões de pagamento; tentar confirmar sem preencher mostra o erro "Essa
pergunta é obrigatória" (via `CamposExtrasForm`) e não deixa avançar; preenchendo e
clicando "Pagar"/"Enviar link", checar (via `GET /inscricoes/{id}/respostas` ou a tela de
inscritos) que a resposta foi salva.

- [ ] **Step 4: Elegibilidade contornável**

Clicar numa pessoa que não atende a elegibilidade do evento (ex.: exclusivo membros) — a
confirmação "Inscrever mesmo assim?" deve abrir; confirmando, o fluxo de pagamento
continua normalmente para essa pessoa.

Não é um teste automatizado (dívida conhecida do projeto) — é a validação manual que a
convenção do projeto pede pra mudança de UI.
