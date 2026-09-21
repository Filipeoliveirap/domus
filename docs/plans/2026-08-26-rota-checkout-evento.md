# Rota de checkout dedicada (Plano 2/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar a rota própria `/eventos/{eventoId}/pagamento/{cobrancaId}` (Next.js), com
contexto do evento + stepper, que os planos seguintes (fluxo individual, lote, convite
público) vão navegar para ela em vez de embutir o checkout num modal.

**Architecture:** Backend ganha um endpoint novo, público (mesma garantia de posse por
UUID que já vale para `/cobrancas/{id}/pagar` e `/cobrancas/{id}/status` — ver javadoc de
`CobrancaController`), que devolve o contexto necessário pra montar o cabeçalho da página
(título e data do evento) a partir só do `cobrancaId`. O front ganha uma página cliente
(`'use client'`) nova, sem layout autenticado — mesmo padrão de `/cobranca/[token]` — com
um componente `StepperPagamento` reutilizável (2 estados: `pagamento` e `confirmado`) e o
`PaymentBrickCheckout` já existente dentro dele.

**Tech Stack:** Backend: Java 21/Spring Boot, sem migration (endpoint novo, sem mudança de
schema). Front: Next.js App Router, TypeScript, CSS Modules, TanStack Query.

**Spec:** `docs/superpowers/specs/2026-08-26-fluxo-pagamento-evento-ux-design.md` (seção
"Arquitetura da rota de checkout").

## Global Constraints

- Backend: Mockito puro pro `@SpringBootTest` do controller? Não — `CobrancaController` já
  é testado via `@SpringBootTest`+`@AutoConfigureMockMvc`+`@Sql` (não Mockito), porque usa
  repositories direto sem camada de serviço para estes 3 endpoints. Seguir esse padrão.
- Frontend: sem framework de teste (Jest/Vitest/Playwright não configurados neste projeto —
  dívida conhecida). Validação é manual no navegador (Task 5).
- CSS Modules com as variáveis já usadas em `CobrancaPublica.module.css`
  (`--color-bg-page`, `--color-bg-white`, `--color-text-dark`, `--color-text-muted`,
  `--color-primary`, `--radius-lg`, etc.) — não inventar variável nova sem checar se já
  existe.
- Responsividade obrigatória (regra do projeto) — a página já nasce mobile-first (largura
  máx. ~480px, igual `/cobranca/[token]`), então isso já vem de graça; só confirmar no
  Task 5.

---

### Task 1: Endpoint `GET /cobrancas/id/{id}` — contexto de checkout por id

**Files:**
- Create: `src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/CobrancaCheckoutDTO.java`
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java`

**Interfaces:**
- Consumes: `CobrancaEventoRepository.findById(UUID): Optional<CobrancaEvento>` (já
  existe); `EventoRepository.findById(UUID): Optional<Evento>` (já existe);
  `PessoaRepository.findById(UUID)`/`AcompanhanteRepository.findById(UUID)` (já existem,
  mesmo padrão do método `buscar` já presente no controller).
- Produces: `GET /cobrancas/id/{id}` → `CobrancaCheckoutDTO` (200) ou erro 404
  (`ResourceNotFoundException`, já mapeado pelo `GlobalExceptionHandler` existente).

**Por que rota separada (`/cobrancas/id/{id}`) em vez de reaproveitar `/cobrancas/{token}`:**
o `{token}` do endpoint existente é resolvido contra `tokenLinkPublico` (não é o `id`) —
reaproveitar o mesmo padrão de path pra um lookup por `id` colidiria com essa rota e exigiria
lógica ambígua (tentar como token, senão como id). Path fixo `/id/` evita a ambiguidade.

- [ ] **Step 1: Escrever o teste que falha**

Adicionar em `CobrancaControllerTest.java`, depois do teste `retornaDadosDaCobrancaParaTokenValido`:

```java
    @Test
    void retorna404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/cobrancas/id/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('21111111-1111-1111-1111-111111111111', 'Igreja Teste 2', 'igreja2@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('23333333-3333-3333-3333-333333333333', '21111111-1111-1111-1111-111111111111', 'Ciclana', 'ciclana@teste.com')",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('27777777-7777-7777-7777-777777777777', '21111111-1111-1111-1111-111111111111', 'Salão 2')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('25555555-5555-5555-5555-555555555555', '21111111-1111-1111-1111-111111111111', " +
            "'Congresso Anual', '2026-09-10 19:00:00', '27777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('26666666-6666-6666-6666-666666666666', '21111111-1111-1111-1111-111111111111', " +
            "'25555555-5555-5555-5555-555555555555', '23333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void retornaContextoDaCobrancaParaIdValido() throws Exception {
        UUID igrejaId = UUID.fromString("21111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("25555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("26666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("23333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("23333333-3333-3333-3333-333333333333");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(
            igrejaId, eventoId, inscricaoId, pessoaId, null,
            new BigDecimal("75.00"), Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, null));

        mockMvc.perform(get("/cobrancas/id/" + cobranca.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(cobranca.getId().toString())))
            .andExpect(jsonPath("$.eventoId", is(eventoId.toString())))
            .andExpect(jsonPath("$.tituloEvento", is("Congresso Anual")))
            .andExpect(jsonPath("$.nomePagador", is("Ciclana")))
            .andExpect(jsonPath("$.valor", is(75.00)))
            .andExpect(jsonPath("$.status", is("PENDENTE")));
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=CobrancaControllerTest#retorna404ParaIdInexistente+retornaContextoDaCobrancaParaIdValido
```

Expected: FAIL — endpoint `/cobrancas/id/{id}` ainda não existe (404 genérico do Spring,
não o 404 esperado do `ResourceNotFoundException`; e o segundo teste falha por completo).

- [ ] **Step 3: Implementar**

Criar `CobrancaCheckoutDTO.java`:

```java
package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO da rota `GET /cobrancas/id/{id}` — pública pela mesma garantia de posse por UUID
 * que já vale para `/cobrancas/{id}/pagar` e `/cobrancas/{id}/status` (ver javadoc de
 * {@code CobrancaController}). Usada pela página de checkout (`/eventos/{eventoId}/
 * pagamento/{cobrancaId}`) para montar o cabeçalho com contexto do evento — por isso
 * carrega {@code eventoId}/{@code inicioEmEvento} além do que {@link CobrancaPublicaDTO}
 * já tinha.
 */
public record CobrancaCheckoutDTO(
    UUID id,
    UUID eventoId,
    String tituloEvento,
    LocalDateTime inicioEmEvento,
    String nomePagador,
    BigDecimal valor,
    String status,
    Instant expiraEm
) {}
```

Em `CobrancaController.java`, adicionar o endpoint novo logo antes de `buscar`:

```java
    @GetMapping("/id/{id}")
    public CobrancaCheckoutDTO buscarPorId(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));

        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));

        String nomePagador;
        if (cobranca.getPessoaId() != null) {
            nomePagador = pessoaRepository.findById(cobranca.getPessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        } else {
            nomePagador = acompanhanteRepository.findById(cobranca.getAcompanhanteId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        }

        return new CobrancaCheckoutDTO(
            cobranca.getId(),
            evento.getId(),
            evento.getTitulo(),
            evento.getInicioEm(),
            nomePagador,
            cobranca.getValor(),
            cobranca.getStatus().name(),
            cobranca.getExpiraEm()
        );
    }
```

E adicionar o import no topo do arquivo:

```java
import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaCheckoutDTO;
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=CobrancaControllerTest
```

Expected: PASS em todos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/CobrancaCheckoutDTO.java \
        src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java
git commit -m "feat(pagamento): endpoint GET /cobrancas/id/{id} com contexto de checkout"
```

---

### Task 2: Service e hook do front para o contexto de checkout

**Files:**
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/services/cobranca.service.ts`
- Create: `frontend/src/hooks/cobranca/useCobrancaCheckout.ts`

**Interfaces:**
- Consumes: `GET /cobrancas/id/{id}` (Task 1).
- Produces: `cobrancaService.buscarPorId(id: string): Promise<CobrancaCheckout>`;
  `useCobrancaCheckout(cobrancaId: string): UseQueryResult<CobrancaCheckout>`.

- [ ] **Step 1: Adicionar o endpoint**

Em `frontend/src/lib/endpoints.ts`, dentro do bloco `cobrancas: { ... }`, logo após
`BUSCAR_POR_TOKEN`:

```typescript
    BUSCAR_POR_ID: (id: string) => `/cobrancas/id/${id}`,
```

- [ ] **Step 2: Adicionar o tipo e o método no service**

Em `frontend/src/services/cobranca.service.ts`, adicionar o tipo logo após `CobrancaPublica`:

```typescript
/** Espelha `CobrancaCheckoutDTO` (backend) — usado pela página de checkout dedicada
 *  (`/eventos/{eventoId}/pagamento/{cobrancaId}`), que precisa do contexto do evento
 *  (título, data) além do que `CobrancaPublica` já tinha. */
export interface CobrancaCheckout {
  id: string
  eventoId: string
  tituloEvento: string
  inicioEmEvento: string
  nomePagador: string
  valor: number
  status: StatusCobranca
  expiraEm: string
}
```

E o método, dentro do objeto `cobrancaService`, logo após `buscarPorToken`:

```typescript
  buscarPorId: (id: string): Promise<CobrancaCheckout> =>
    api.get<CobrancaCheckout>(Endpoints.cobrancas.BUSCAR_POR_ID(id)).then((res) => res.data),
```

- [ ] **Step 3: Criar o hook**

Criar `frontend/src/hooks/cobranca/useCobrancaCheckout.ts`:

```typescript
import { useQuery } from '@tanstack/react-query'
import { cobrancaService } from '@/services/cobranca.service'

/** Página de checkout dedicada (`/eventos/{eventoId}/pagamento/{cobrancaId}`) — pública
 *  pela mesma garantia de posse por UUID que o resto do módulo de cobrança (ver
 *  `useCobrancaPublica`), por isso `retry: false`. */
export function useCobrancaCheckout(cobrancaId: string) {
  return useQuery({
    queryKey: ['cobranca-checkout', cobrancaId],
    queryFn: () => cobrancaService.buscarPorId(cobrancaId),
    retry: false,
  })
}
```

- [ ] **Step 4: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros novos relacionados a estes 3 arquivos.

- [ ] **Step 5: Commit**

```bash
cd frontend
git add src/lib/endpoints.ts src/services/cobranca.service.ts src/hooks/cobranca/useCobrancaCheckout.ts
git commit -m "feat(pagamento): service e hook para contexto de checkout por cobrancaId"
```

---

### Task 3: Componente `StepperPagamento`

**Files:**
- Create: `frontend/src/components/module/pagamento/StepperPagamento.tsx`
- Create: `frontend/src/components/module/pagamento/StepperPagamento.module.css`

**Interfaces:**
- Produces: `<StepperPagamento etapaAtual="pagamento" | "confirmado" />` — componente puro,
  sem estado próprio, sem dependência de dados externos.

**Por que só 2 etapas visuais (não 3) nesta entrega:** o card "Divisão de pagamento" só
existe no fluxo em lote do gestor (Plano 4) — na auto-inscrição individual (Plano 3, que
consome este componente primeiro) o titular é sempre "paga agora", sem etapa de escolha.
O componente aceita uma 3ª etapa (`'resumo'`) desde já para não precisar ser redesenhado no
Plano 4, mas só o Plano 4 vai efetivamente passar por ela.

- [ ] **Step 1: Criar o componente**

```tsx
'use client'

import { Check } from 'lucide-react'
import styles from './StepperPagamento.module.css'

export type EtapaPagamento = 'resumo' | 'pagamento' | 'confirmado'

const ETAPAS: { chave: EtapaPagamento; rotulo: string }[] = [
  { chave: 'resumo', rotulo: 'Resumo' },
  { chave: 'pagamento', rotulo: 'Pagamento' },
  { chave: 'confirmado', rotulo: 'Confirmação' },
]

interface Props {
  etapaAtual: EtapaPagamento
  /** Etapas visíveis — por padrão as 3, mas a auto-inscrição individual (sempre "paga
   *  agora", sem card de divisão) pula "resumo" passando só ['pagamento', 'confirmado']. */
  etapasVisiveis?: EtapaPagamento[]
}

export function StepperPagamento({ etapaAtual, etapasVisiveis = ['pagamento', 'confirmado'] }: Props) {
  const etapas = ETAPAS.filter((e) => etapasVisiveis.includes(e.chave))
  const indiceAtual = etapas.findIndex((e) => e.chave === etapaAtual)

  return (
    <ol className={styles.stepper} aria-label="Progresso do pagamento">
      {etapas.map((etapa, indice) => {
        const concluida = indice < indiceAtual
        const ativa = indice === indiceAtual
        return (
          <li
            key={etapa.chave}
            className={[styles.etapa, ativa ? styles.ativa : '', concluida ? styles.concluida : ''].join(' ')}
          >
            <span className={styles.bolinha} aria-hidden="true">
              {concluida ? <Check size={14} /> : indice + 1}
            </span>
            <span className={styles.rotulo}>{etapa.rotulo}</span>
          </li>
        )
      })}
    </ol>
  )
}
```

- [ ] **Step 2: Criar o CSS module**

```css
.stepper {
  display: flex;
  align-items: center;
  list-style: none;
  padding: 0;
  margin: 0;
  gap: 8px;
}

.etapa {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-muted);
  font-size: var(--font-size-xs, 12px);
  min-width: 0;
}

.etapa:not(:last-child)::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border, #e2e8f0);
  margin: 0 8px;
  min-width: 16px;
}

.bolinha {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
  background: var(--color-bg-page);
  border: 1px solid var(--color-border, #e2e8f0);
  font-weight: var(--font-weight-semibold);
}

.ativa .bolinha {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: var(--color-bg-white);
}

.concluida .bolinha {
  background: var(--color-success, #16a34a);
  border-color: var(--color-success, #16a34a);
  color: var(--color-bg-white);
}

.ativa .rotulo {
  color: var(--color-text-dark);
  font-weight: var(--font-weight-semibold);
}

.rotulo {
  white-space: nowrap;
}

@media (max-width: 480px) {
  .rotulo {
    display: none;
  }

  .etapa:not(:last-child)::after {
    margin: 0 4px;
  }
}
```

- [ ] **Step 3: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
cd frontend
git add src/components/module/pagamento/StepperPagamento.tsx src/components/module/pagamento/StepperPagamento.module.css
git commit -m "feat(pagamento): componente StepperPagamento"
```

---

### Task 4: Página `/eventos/[eventoId]/pagamento/[cobrancaId]`

**Files:**
- Create: `frontend/src/app/eventos/[eventoId]/pagamento/[cobrancaId]/page.tsx`
- Create: `frontend/src/app/eventos/[eventoId]/pagamento/[cobrancaId]/PagamentoEvento.module.css`

**Interfaces:**
- Consumes: `useCobrancaCheckout(cobrancaId)` (Task 2);
  `<StepperPagamento etapaAtual etapasVisiveis />` (Task 3);
  `<PaymentBrickCheckout cobrancaId valor emailPagador onPagamentoCriado />` (já existe,
  `frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx`).
- Produces: rota Next.js `/eventos/{eventoId}/pagamento/{cobrancaId}` — nenhum outro
  código consome esta página diretamente (é navegação de URL, feita pelos Planos 3-5).

- [ ] **Step 1: Criar a página**

```tsx
'use client'

import { use, useState } from 'react'
import Link from 'next/link'
import { CalendarDays, CheckCircle2 } from 'lucide-react'
import { useCobrancaCheckout } from '@/hooks/cobranca/useCobrancaCheckout'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { StepperPagamento } from '@/components/module/pagamento/StepperPagamento'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './PagamentoEvento.module.css'

function formatarDataEvento(iso: string): string {
  return new Date(iso).toLocaleDateString('pt-BR', {
    day: '2-digit', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

export default function PagamentoEventoPage({
  params,
}: {
  params: Promise<{ eventoId: string; cobrancaId: string }>
}) {
  const { eventoId, cobrancaId } = use(params)
  const { data: cobranca, isLoading, isError } = useCobrancaCheckout(cobrancaId)
  const [confirmado, setConfirmado] = useState(false)

  if (isLoading) {
    return <div className={styles.pagina}><p className={styles.estado}>Carregando…</p></div>
  }

  if (isError || !cobranca) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <h1>Cobrança não encontrada</h1>
          <p>Este link de pagamento não existe ou não está mais disponível.</p>
        </div>
      </div>
    )
  }

  if (cobranca.status !== 'PENDENTE' && !confirmado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.card}>
          <h1>Pagamento não disponível</h1>
          <p>Esta cobrança já foi paga, cancelada ou expirou.</p>
          <Link href={`/eventos/${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.topo}>
        <span className={styles.topoIcone}><CalendarDays size={18} /></span>
        <div className={styles.topoTextos}>
          <span className={styles.topoTitulo}>{cobranca.tituloEvento}</span>
          <span className={styles.topoData}>{formatarDataEvento(cobranca.inicioEmEvento)}</span>
        </div>
      </header>

      <div className={styles.conteudo}>
        <StepperPagamento etapaAtual={confirmado ? 'confirmado' : 'pagamento'} />

        {confirmado ? (
          <div className={styles.card}>
            <CheckCircle2 size={40} className={styles.iconeSucesso} aria-hidden="true" />
            <h1>Pagamento em processamento</h1>
            <p>Assim que o Mercado Pago confirmar, sua inscrição fica garantida. Isso costuma levar só alguns instantes.</p>
            <Link href={`/eventos/${eventoId}`} className={styles.voltarLink}>Voltar para o evento</Link>
          </div>
        ) : (
          <>
            <div className={styles.card}>
              <p className={styles.saudacao}>{cobranca.nomePagador}, sua parte:</p>
              <p className={styles.valor}>{formatarMoeda(cobranca.valor)}</p>
            </div>

            <PaymentBrickCheckout
              cobrancaId={cobranca.id}
              valor={cobranca.valor}
              onPagamentoCriado={() => setConfirmado(true)}
            />

            <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
          </>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Criar o CSS module**

```css
.pagina {
  min-height: 100vh;
  background: var(--color-bg-page);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-bottom: 48px;
}

.topo {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 24px;
}

.topoIcone {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: var(--radius-full);
  background: var(--color-primary-light);
  color: var(--color-primary);
}

.topoTextos {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topoTitulo {
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topoData {
  font-size: var(--font-size-xs, 12px);
  color: var(--color-text-muted);
}

.conteudo {
  width: 100%;
  max-width: 480px;
  padding: 0 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.card {
  padding: 24px 20px;
  background: var(--color-bg-white);
  border-radius: var(--radius-lg);
  text-align: center;
}

.card h1 {
  font-size: var(--font-size-lg, 18px);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
  margin-bottom: 8px;
}

.card p {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.iconeSucesso {
  color: var(--color-success, #16a34a);
  margin-bottom: 8px;
}

.saudacao {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.valor {
  font-size: var(--font-size-xl, 28px);
  font-weight: var(--font-weight-semibold);
  color: var(--color-text-dark);
  margin-top: 4px;
}

.seguranca {
  font-size: var(--font-size-xs, 12px);
  color: var(--color-text-muted);
  text-align: center;
}

.estado {
  padding: 24px 0;
  text-align: center;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.voltarLink {
  display: inline-block;
  margin-top: 12px;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

@media (max-width: 480px) {
  .conteudo {
    padding: 0 12px;
  }
}
```

- [ ] **Step 3: Checar que o projeto compila**

```bash
cd frontend && npx tsc --noEmit
```

Expected: sem erros.

- [ ] **Step 4: Commit**

```bash
cd frontend
git add "src/app/eventos/[eventoId]/pagamento/[cobrancaId]/page.tsx" \
        "src/app/eventos/[eventoId]/pagamento/[cobrancaId]/PagamentoEvento.module.css"
git commit -m "feat(pagamento): rota dedicada de checkout /eventos/{id}/pagamento/{cobrancaId}"
```

---

### Task 5: Verificação manual no navegador

**Files:** nenhum arquivo novo — só verificação (use a skill `run` se disponível pra subir o app).

- [ ] **Step 1: Gerar uma cobrança pendente de teste**

Com a API e o front locais rodando, criar um evento pago e se inscrever (via UI normal ou
`POST /eventos/{id}/inscrever`) para obter um `cobrancaId` real pendente.

- [ ] **Step 2: Abrir a rota nova diretamente**

Navegar para `http://localhost:3000/eventos/{eventoId}/pagamento/{cobrancaId}` com esse
id. Conferir:
- Cabeçalho mostra título e data do evento.
- Stepper aparece com "Pagamento" ativo.
- Card de valor + `PaymentBrickCheckout` carregam.
- Testar em viewport mobile (~375px) — layout não deve estourar horizontalmente (regra do
  projeto: responsividade obrigatória).

- [ ] **Step 3: Testar os estados de erro**

- Abrir com um `cobrancaId` aleatório (`UUID.randomUUID()`) — deve mostrar "Cobrança não
  encontrada".
- Se possível, testar com uma cobrança já `PAGO`/`EXPIRADO` no banco — deve mostrar
  "Pagamento não disponível".

Não é um teste automatizado (dívida conhecida do projeto: sem Jest/Vitest/Playwright) —
é a validação manual que a convenção do projeto pede pra mudança de UI.
