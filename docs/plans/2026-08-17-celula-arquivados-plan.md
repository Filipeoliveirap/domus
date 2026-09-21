# Célula: Arquivados + Exclusão Definitiva — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Primeiro pedaço da feature "arquivados + exclusão definitiva" (Fase 3):
módulo Célula ganha listagem de arquivadas, restaurar, e o botão da listagem normal
passa a mostrar "Excluir" (hard delete direto) ou "Arquivar" (soft delete) dependendo
de ter membro vinculado ou não.

**Architecture:** Backend: dois endpoints novos (`GET /celulas/arquivados`,
`POST /celulas/{id}/restaurar`) reusando o `DELETE /celulas/{id}/definitivo` que já
existe; `CelulaResponse` ganha `temVinculo`. Front: rota-irmã `/celulas/arquivados`
sob um `layout.tsx` com abas (mesmo padrão de `financeiro/layout.tsx`).

**Tech Stack:** Spring Boot 21 / JPA / Postgres (backend), Next.js App Router / React
Query / CSS Modules (frontend).

**Spec:** `docs/superpowers/specs/2026-08-17-arquivados-exclusao-definitiva-design.md`

## Global Constraints

- Célula não tem dado pessoal — sem anonimização, sem tabela `eliminacao_lgpd` (isso
  é só pro pedaço de Pessoa/Usuário, mais adiante).
- `Celula` usa `@SQLDelete`/`@SQLRestriction("deleted_at IS NULL")` — toda query
  derivada/JPQL já filtra os arquivados automaticamente. Pra alcançar os arquivados
  (listar ou restaurar), é preciso SQL nativo, mesmo padrão que `hardDeleteById` já usa.
- `DELETE /celulas/{id}/definitivo` já existe (`CelulaService.excluirDefinitivo`) e já
  bloqueia com `ConflitoNegocioException("CELULA_COM_MEMBROS", ...)` quando há membro
  — reusar sem alterar.
- "Tem vínculo" pra Célula = `membroRepository.existsByCelulaId(id)`.
- `ModalConfirmacaoCritica` (`@/components/common/ModalConfirmacaoCritica`) só entra
  onde a ação é irreversível — arquivar continua sem modal (como hoje); excluir
  definitivamente usa o modal.
- Testes: Mockito puro pra `CelulaServiceTest` (padrão do arquivo, sem Spring).

---

## Task 1: CelulaRepository — consultas nativas pra arquivadas e restaurar

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/celula/CelulaRepository.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/celula/CelulaRepositoryTest.java` (criar)

**Interfaces:**
- Produces: `CelulaRepository.findArquivadasPorIgreja(UUID igrejaId): List<Celula>`,
  `CelulaRepository.restaurarPorId(UUID id): void`

- [ ] **Step 1: Escrever o teste `@DataJpaTest` (precisa do Postgres real — `@SQLRestriction` só existe no Hibernate, não é simulável com mock)**

```java
package com.domus.api.modules.celula;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@ActiveProfiles("test")
class CelulaRepositoryTest {

    @Autowired CelulaRepository celulaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void findArquivadasPorIgrejaTraSoAsArquivadas() {
        Igreja igreja = igrejaRepository.save(Igreja.builder().nome("Igreja Teste").build());

        Celula ativa = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Ativa " + UUID.randomUUID()).build());
        Celula arquivada = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Arquivada " + UUID.randomUUID()).build());
        celulaRepository.delete(arquivada); // soft delete via @SQLDelete
        entityManager.flush();
        entityManager.clear();

        List<Celula> arquivadas = celulaRepository.findArquivadasPorIgreja(igreja.getId());

        assertThat(arquivadas).extracting(Celula::getId).containsExactly(arquivada.getId());
        assertThat(arquivadas).extracting(Celula::getId).doesNotContain(ativa.getId());
    }

    @Test
    void restaurarPorIdTiraDoArquivo() {
        Igreja igreja = igrejaRepository.save(Igreja.builder().nome("Igreja Teste").build());
        Celula celula = celulaRepository.save(
                Celula.builder().igreja(igreja).nome("Vai e volta " + UUID.randomUUID()).build());
        UUID id = celula.getId();
        celulaRepository.delete(celula);
        entityManager.flush();
        entityManager.clear();

        celulaRepository.restaurarPorId(id);
        entityManager.flush();
        entityManager.clear();

        assertThat(celulaRepository.findById(id)).isPresent();
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha (método não existe ainda)**

```bash
cd backend/api
set -a; source .env >/dev/null 2>&1; set +a
mvn -o test -Dtest=CelulaRepositoryTest
```
Esperado: erro de compilação — `findArquivadasPorIgreja`/`restaurarPorId` não existem.

- [ ] **Step 3: Adicionar os dois métodos ao repositório**

```java
package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaRepository extends JpaRepository<Celula, UUID> {

    List<Celula> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    Optional<Celula> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM celula WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    /** @SQLRestriction esconde arquivados de qualquer find derivado/JPQL — precisa de SQL nativo. */
    @Query(value = """
        SELECT * FROM celula
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY nome ASC
        """, nativeQuery = true)
    List<Celula> findArquivadasPorIgreja(@Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = "UPDATE celula SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
    void restaurarPorId(@Param("id") UUID id);
}
```

- [ ] **Step 4: Rodar de novo e confirmar que passa**

```bash
mvn -o test -Dtest=CelulaRepositoryTest
```
Esperado: PASS, 2 testes.

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/celula/CelulaRepository.java \
        backend/api/src/test/java/com/domus/api/modules/celula/CelulaRepositoryTest.java
git commit -m "feat(celula): consultas nativas pra listar arquivadas e restaurar"
```

---

## Task 2: CelulaService — listarArquivadas, restaurar, temVinculo no DTO

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/celula/CelulaService.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/celula/DTOs/CelulaResponse.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/celula/CelulaServiceTest.java`

**Interfaces:**
- Consumes: `CelulaRepository.findArquivadasPorIgreja(UUID): List<Celula>`,
  `CelulaRepository.restaurarPorId(UUID): void` (Task 1);
  `CelulaMembroRepository.existsByCelulaId(UUID id): boolean` (já existe)
- Produces: `CelulaService.listarArquivadas(UUID igrejaId): List<CelulaResponse>`,
  `CelulaService.restaurar(UUID id, UUID igrejaId): void`;
  `CelulaResponse.temVinculo(): boolean`

- [ ] **Step 1: Escrever os testes que faltam (Mockito puro, mesmo padrão do arquivo)**

Adicionar ao final de `CelulaServiceTest.java` (antes do último `}`):

```java
    @Test
    void listarArquivadasRetornaSoAsArquivadas() {
        Celula arquivada = celula();
        when(celulaRepository.findArquivadasPorIgreja(igrejaId)).thenReturn(List.of(arquivada));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        List<CelulaResponse> response = service.listarArquivadas(igrejaId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).nome()).isEqualTo("Célula Bethânia");
        verify(celulaRepository, never()).findByIgrejaIdOrderByNomeAsc(any());
    }

    @Test
    void restaurarTiraDoArquivoEReindexaNaBusca() {
        service.restaurar(celulaId, igrejaId);

        verify(celulaRepository).restaurarPorId(celulaId);
        verify(outboxRegistrador).registrar(
                com.domus.api.modules.outbox.TipoEntidadeOutbox.CELULA,
                com.domus.api.modules.outbox.TipoEventoOutbox.CRIADO,
                celulaId, igrejaId);
    }

    @Test
    void listarMarcaTemVinculoQuandoTemMembro() {
        Celula c = celula();
        when(celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId)).thenReturn(List.of(c));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId))
                .thenReturn(List.of(CelulaMembro.builder().build()));

        List<CelulaResponse> response = service.listar(igrejaId, null);

        assertThat(response.get(0).temVinculo()).isTrue();
    }

    @Test
    void listarMarcaSemVinculoQuandoVazia() {
        Celula c = celula();
        when(celulaRepository.findByIgrejaIdOrderByNomeAsc(igrejaId)).thenReturn(List.of(c));
        when(membroRepository.findByCelulaIdOrderByPapelAsc(celulaId)).thenReturn(List.of());

        List<CelulaResponse> response = service.listar(igrejaId, null);

        assertThat(response.get(0).temVinculo()).isFalse();
    }
```

- [ ] **Step 2: Rodar e confirmar que falha**

```bash
mvn -o test -Dtest=CelulaServiceTest
```
Esperado: erro de compilação — `listarArquivadas`/`restaurar`/`temVinculo` não existem.

- [ ] **Step 3: Adicionar `temVinculo` ao `CelulaResponse`**

Arquivo completo (`CelulaResponse.java`):

```java
package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.DiaSemana;
import com.domus.api.modules.celula.PapelCelula;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CelulaResponse(
        UUID id,
        String nome,
        DiaSemana diaSemana,
        LocalTime horario,
        List<String> lideres,
        int totalMembros,
        UUID fotoId,
        boolean souLiderDestaCelula,
        boolean temVinculo
) {
    public static CelulaResponse from(Celula celula) {
        return new CelulaResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), List.of(), 0,
                celula.getFoto() != null ? celula.getFoto().getId() : null, false, false);
    }

    public static CelulaResponse comResumo(Celula celula, List<CelulaMembro> membros, UUID pessoaLogadaId) {
        List<String> lideres = membros.stream()
                .filter(m -> m.getPapel() == PapelCelula.LIDER)
                .map(m -> m.getPessoa() != null ? m.getPessoa().getNome() : m.getVisitante().getNome())
                .toList();
        boolean souLider = pessoaLogadaId != null && membros.stream()
                .anyMatch(m -> m.getPapel() == PapelCelula.LIDER
                        && m.getPessoa() != null && pessoaLogadaId.equals(m.getPessoa().getId()));
        return new CelulaResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), lideres, membros.size(),
                celula.getFoto() != null ? celula.getFoto().getId() : null, souLider,
                !membros.isEmpty());
    }
}
```

- [ ] **Step 4: Adicionar `listarArquivadas` e `restaurar` ao `CelulaService`**

Adicionar logo após o método `listar` (que já existe):

```java
    @Transactional(readOnly = true)
    public List<CelulaResponse> listarArquivadas(UUID igrejaId) {
        return celulaRepository.findArquivadasPorIgreja(igrejaId).stream()
                .map(c -> CelulaResponse.comResumo(c, membrosAtivosDe(c.getId()), null))
                .toList();
    }

    @Transactional
    public void restaurar(UUID id, UUID igrejaId) {
        celulaRepository.restaurarPorId(id);
        outboxRegistrador.registrar(TipoEntidadeOutbox.CELULA, TipoEventoOutbox.CRIADO, id, igrejaId);
    }
```

(Usa `TipoEventoOutbox.CRIADO` — o registro some do índice quando arquivado (`REMOVIDO`,
já registrado em `excluir`) e precisa "aparecer de novo" quando restaurado, o mesmo
efeito de uma criação nova do ponto de vista da busca.)

- [ ] **Step 5: Rodar de novo e confirmar que passa**

```bash
mvn -o test -Dtest=CelulaServiceTest
```
Esperado: PASS, todos os testes (os novos + os que já existiam).

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/celula/CelulaService.java \
        backend/api/src/main/java/com/domus/api/modules/celula/DTOs/CelulaResponse.java \
        backend/api/src/test/java/com/domus/api/modules/celula/CelulaServiceTest.java
git commit -m "feat(celula): listarArquivadas, restaurar e temVinculo no DTO"
```

---

## Task 3: CelulaController — endpoints novos

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/celula/CelulaController.java`

**Interfaces:**
- Consumes: `CelulaService.listarArquivadas(UUID)`, `CelulaService.restaurar(UUID, UUID)` (Task 2)
- Produces: `GET /celulas/arquivados`, `POST /celulas/{id}/restaurar`

- [ ] **Step 1: Adicionar os dois endpoints** (logo abaixo de `excluirDefinitivo`)

```java
    @GetMapping("/arquivados")
    public ResponseEntity<List<CelulaResponse>> arquivados() {
        exigirAdmin();
        return ResponseEntity.ok(celulaService.listarArquivadas(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/{id}/restaurar")
    public ResponseEntity<Void> restaurar(@PathVariable UUID id) {
        exigirAdmin();
        celulaService.restaurar(id, usuarioAutenticado.getIgrejaId());
        return ResponseEntity.noContent().build();
    }
```

- [ ] **Step 2: Compilar**

```bash
cd backend/api
mvn -q -o compile
```
Esperado: sem erro.

- [ ] **Step 3: Testar manualmente com curl** (backend rodando local, sessão logada
  via `.dev-tools/reindexar.sh` já validado — reusar o cookie jar que ele gera)

```bash
curl -s -b .dev-tools/cookies.txt -c .dev-tools/cookies.txt \
  http://localhost:3000/api/celulas/arquivados
```
Esperado: `[]` ou lista de células arquivadas, HTTP 200.

- [ ] **Step 4: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/celula/CelulaController.java
git commit -m "feat(celula): endpoints GET /arquivados e POST /restaurar"
```

---

## Task 4: Front — tipos, endpoints e service

**Files:**
- Modify: `frontend/src/types/celula.type.ts`
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: `frontend/src/services/celula.service.ts`

**Interfaces:**
- Produces: `celulaService.listarArquivadas(): Promise<CelulaResponse[]>`,
  `celulaService.restaurar(id: string): Promise<void>`,
  `celulaService.excluirDefinitivo(id: string): Promise<void>`

- [ ] **Step 1: `temVinculo` no tipo `CelulaResponse`**

Em `frontend/src/types/celula.type.ts`, adicionar o campo:

```typescript
export interface CelulaResponse {
  id: string
  nome: string
  fotoId: string | null
  diaSemana: DiaSemana | null
  horario: string | null
  lideres: string[]
  totalMembros: number
  souLiderDestaCelula: boolean
  temVinculo: boolean
}
```

- [ ] **Step 2: Endpoints novos**

Em `frontend/src/lib/endpoints.ts`, dentro do bloco `celulas: { ... }`:

```typescript
  celulas: {
    LISTAR: '/celulas',
    CRIAR: '/celulas',
    BY_ID: (id: string) => `/celulas/${id}`,
    ARQUIVADOS: '/celulas/arquivados',
    RESTAURAR: (id: string) => `/celulas/${id}/restaurar`,
    DEFINITIVO: (id: string) => `/celulas/${id}/definitivo`,
    MEMBROS: (id: string) => `/celulas/${id}/membros`,
    MEMBRO: (id: string, membroId: string) => `/celulas/${id}/membros/${membroId}`,
    PAPEL: (id: string, membroId: string) => `/celulas/${id}/membros/${membroId}/papel`,
    CONVERTER: (id: string, visitanteId: string) => `/celulas/${id}/converter/${visitanteId}`,
  },
```

- [ ] **Step 3: Funções novas no service**

Em `frontend/src/services/celula.service.ts`, adicionar dentro do objeto `celulaService`:

```typescript
  listarArquivadas: (): Promise<CelulaResponse[]> =>
    api.get<CelulaResponse[]>(Endpoints.celulas.ARQUIVADOS).then(res => res.data),

  restaurar: (id: string): Promise<void> =>
    api.post(Endpoints.celulas.RESTAURAR(id)).then(() => undefined),

  excluirDefinitivo: (id: string): Promise<void> =>
    api.delete(Endpoints.celulas.DEFINITIVO(id)).then(() => undefined),
```

- [ ] **Step 4: Typecheck**

```bash
cd frontend
npx tsc --noEmit
```
Esperado: sem erro.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/types/celula.type.ts frontend/src/lib/endpoints.ts \
        frontend/src/services/celula.service.ts
git commit -m "feat(celula): tipos e chamadas pra arquivados/restaurar/definitivo"
```

---

## Task 5: Front — hooks

**Files:**
- Create: `frontend/src/hooks/celula/useCelulasArquivadas.ts`
- Create: `frontend/src/hooks/celula/useRestaurarCelula.ts`
- Create: `frontend/src/hooks/celula/useExcluirCelulaDefinitivamente.ts`

**Interfaces:**
- Consumes: `celulaService.listarArquivadas/restaurar/excluirDefinitivo` (Task 4)
- Produces: `useCelulasArquivadas(): UseQueryResult<CelulaResponse[]>`,
  `useRestaurarCelula(): { restaurar: (id: string, nome: string) => Promise<void>, isLoading: boolean }`,
  `useExcluirCelulaDefinitivamente(celula: CelulaResponse, onClose: () => void): { confirmar: () => void, isLoading: boolean, erroGeral: string | null }`

- [ ] **Step 1: `useCelulasArquivadas` (query, mesmo padrão de `useCelulas`)**

```typescript
import { useQuery } from '@tanstack/react-query'
import { celulaService } from '@/services/celula.service'

export function useCelulasArquivadas() {
  return useQuery({
    queryKey: ['celulas-arquivadas'],
    queryFn: () => celulaService.listarArquivadas(),
  })
}
```

- [ ] **Step 2: `useRestaurarCelula` (mutação simples, sem modal — ação reversível não precisa de confirmação pesada, só notificação)**

```typescript
import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import type { ApiError } from '@/types/api.types'

export function useRestaurarCelula() {
  const queryClient = useQueryClient()
  const [isLoading, setIsLoading] = useState(false)

  async function restaurar(id: string, nome: string) {
    setIsLoading(true)
    try {
      await celulaService.restaurar(id)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${nome} foi restaurada.`)
    } catch (error: unknown) {
      const mensagem = axios.isAxiosError<ApiError>(error)
        ? error.response?.data?.message ?? 'Erro ao restaurar. Tente novamente.'
        : 'Erro ao restaurar. Tente novamente.'
      notificar.erro(mensagem)
    } finally {
      setIsLoading(false)
    }
  }

  return { restaurar, isLoading }
}
```

- [ ] **Step 3: `useExcluirCelulaDefinitivamente` (mesmo formato de `useArquivarLocalEvento`, pra usar com `ModalConfirmacaoCritica`)**

```typescript
import { useState } from 'react'
import axios from 'axios'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { celulaService } from '@/services/celula.service'
import type { CelulaResponse } from '@/types/celula.type'
import type { ApiError } from '@/types/api.types'

export function useExcluirCelulaDefinitivamente(celula: CelulaResponse, onClose: () => void) {
  const queryClient = useQueryClient()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const confirmar = async () => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      await celulaService.excluirDefinitivo(celula.id)
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${celula.nome} foi excluída definitivamente.`)
      onClose()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao excluir. Tente novamente.')
      } else {
        setErroGeral('Erro ao excluir. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { confirmar, isLoading, erroGeral }
}
```

- [ ] **Step 4: Typecheck**

```bash
cd frontend
npx tsc --noEmit
```
Esperado: sem erro.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/celula/useCelulasArquivadas.ts \
        frontend/src/hooks/celula/useRestaurarCelula.ts \
        frontend/src/hooks/celula/useExcluirCelulaDefinitivamente.ts
git commit -m "feat(celula): hooks de arquivados, restaurar e excluir definitivo"
```

---

## Task 6: Front — layout com abas (Ativas | Arquivadas)

**Files:**
- Create: `frontend/src/app/(app)/celulas/layout.tsx`
- Create: `frontend/src/app/(app)/celulas/celulas.module.css`
- Modify: `frontend/src/app/(app)/celulas/page.tsx` (remove o breadcrumb inline — passa a vir do layout)

**Interfaces:**
- Nenhuma — puramente estrutural (rota compartilha layout).

- [ ] **Step 1: Criar `celulas.module.css`** (cópia do padrão de `financeiro/financeiro.module.css`,
  com `overflow-x: auto` a mais nas abas — mobile-safe, regra do projeto)

```css
.moduloWrapper {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.breadcrumb {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: center;
  gap: 6px;
  font-size: var(--font-size-sm);
}
.breadcrumbLink {
  display: inline-flex;
  align-items: center;
  color: var(--color-text-muted);
  transition: color var(--transition-fast);
}
.breadcrumbLink:hover { color: var(--color-text-primary); }
.breadcrumbSep { flex-shrink: 0; color: var(--color-text-muted); opacity: 0.6; }
.breadcrumbAtual {
  display: inline-flex;
  align-items: center;
  color: var(--color-text-secondary);
  font-weight: var(--font-weight-medium);
}

.abas {
  display: flex;
  gap: 32px;
  border-bottom: 1px solid var(--color-border);
  overflow-x: auto;
}
.aba {
  position: relative;
  padding: 0 2px 14px;
  font-size: var(--font-size-base);
  font-weight: var(--font-weight-medium);
  color: var(--color-text-muted);
  white-space: nowrap;
  transition: color var(--transition-fast);
}
.aba:hover { color: var(--color-primary); }
.abaAtiva {
  color: var(--color-primary);
  font-weight: var(--font-weight-semibold);
}
.abaAtiva::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--color-primary);
  border-radius: 2px 2px 0 0;
}

.conteudo { width: 100%; }
```

- [ ] **Step 2: Criar `layout.tsx`** (mesmo padrão de `financeiro/layout.tsx`)

```tsx
'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import styles from './celulas.module.css'

const ABAS = [
  { href: '/celulas', label: 'Ativas' },
  { href: '/celulas/arquivados', label: 'Arquivadas' },
]

export default function CelulasLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()

  return (
    <div className={styles.moduloWrapper}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Células</span>
      </nav>

      <div className={styles.abas}>
        {ABAS.map((aba) => {
          // rota-filha /celulas/[id] não deve marcar a aba "Ativas" como se fosse
          // /celulas/arquivados — comparação exata, não startsWith.
          const ativa = pathname === aba.href
          return (
            <Link
              key={aba.href}
              href={aba.href}
              className={`${styles.aba} ${ativa ? styles.abaAtiva : ''}`}
            >
              {aba.label}
            </Link>
          )
        })}
      </div>

      <div className={styles.conteudo}>{children}</div>
    </div>
  )
}
```

Nota: como `pathname === aba.href` é exato, a rota `/celulas/[id]` (detalhe de uma
célula) não vai casar com nenhuma aba — nenhuma fica marcada como ativa quando o
usuário está dentro do detalhe de uma célula. Isso é aceitável (a página de detalhe já
tem seu próprio breadcrumb "Células > Nome da Célula").

- [ ] **Step 3: Remover o breadcrumb duplicado de `page.tsx`**

Em `frontend/src/app/(app)/celulas/page.tsx`, remover o import de `ChevronRight` (se
não for mais usado em outro lugar do arquivo — conferir) e o bloco:

```tsx
      <nav className={styles.breadcrumb}>
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Células</span>
      </nav>
```

(Esse bloco fica só no `layout.tsx` agora. O `<div className={styles.pagina}>` que
envolve a página continua — só o `<nav>` de breadcrumb sai.)

- [ ] **Step 4: Rodar o front local e conferir visualmente**

```bash
cd frontend
npm run dev
```
Abrir `http://localhost:3000/celulas` — checar que aparecem as abas "Ativas" |
"Arquivadas" logo abaixo do breadcrumb, sem breadcrumb duplicado, e que "Ativas" fica
destacada.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/\(app\)/celulas/layout.tsx \
        frontend/src/app/\(app\)/celulas/celulas.module.css \
        frontend/src/app/\(app\)/celulas/page.tsx
git commit -m "feat(celula): layout com abas Ativas/Arquivadas"
```

---

## Task 7: Front — página `/celulas/arquivados`

**Files:**
- Create: `frontend/src/app/(app)/celulas/arquivados/page.tsx`

**Interfaces:**
- Consumes: `useCelulasArquivadas()`, `useRestaurarCelula()`,
  `useExcluirCelulaDefinitivamente(celula, onClose)` (Task 5);
  `ModalConfirmacaoCritica` (`@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica`);
  `EstadoVazio`, `EstadoErro`, `Skeleton` (já existentes, mesmos usados em `page.tsx`)

- [ ] **Step 1: Escrever a página**

```tsx
'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, Grid3X3 } from 'lucide-react'
import { useCelulasArquivadas } from '@/hooks/celula/useCelulasArquivadas'
import { useRestaurarCelula } from '@/hooks/celula/useRestaurarCelula'
import { useExcluirCelulaDefinitivamente } from '@/hooks/celula/useExcluirCelulaDefinitivamente'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarCelulas } from '@/lib/permissoes'
import { rotuloDiaSemana, formatarHorario } from '@/lib/formats/celulaFormat'
import type { CelulaResponse } from '@/types/celula.type'
import styles from './arquivados.module.css'

export default function CelulasArquivadasPage() {
  const { data: celulas, isLoading, isError, refetch } = useCelulasArquivadas()
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarCelulas(role)
  const { restaurar, isLoading: restaurando } = useRestaurarCelula()
  const [excluindo, setExcluindo] = useState<CelulaResponse | null>(null)

  if (!podeGerenciar) {
    return <EstadoErro titulo="Sem acesso" mensagem="Só administradores veem células arquivadas." />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map(i => <Skeleton key={i} width="100%" height="72px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!celulas || celulas.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhuma célula arquivada" mensagem="Células arquivadas aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {celulas.map((c) => (
          <div key={c.id} className={styles.linha}>
            <div className={styles.info}>
              <div className={styles.icone}><Grid3X3 size={18} /></div>
              <div>
                <p className={styles.nome}>{c.nome}</p>
                {(c.diaSemana || c.horario) && (
                  <p className={styles.detalhe}>
                    {[rotuloDiaSemana(c.diaSemana), formatarHorario(c.horario)].filter(Boolean).join(', ')}
                  </p>
                )}
              </div>
            </div>
            <div className={styles.acoes}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(c.id, c.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button
                className={styles.botaoExcluir}
                disabled={c.temVinculo}
                title={c.temVinculo ? 'Tem membro vinculado — remova todos antes de excluir.' : undefined}
                onClick={() => setExcluindo(c)}
              >
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalConfirmacaoCriticaWrapper celula={excluindo} onClose={() => setExcluindo(null)} />
      )}
    </>
  )
}

function ModalConfirmacaoCriticaWrapper({ celula, onClose }: { celula: CelulaResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirCelulaDefinitivamente(celula, onClose)
  return (
    <ModalConfirmacaoCritica
      titulo="Excluir célula definitivamente?"
      mensagem={
        <>
          Isso vai apagar <strong>{celula.nome}</strong> de vez. Não tem como desfazer.
        </>
      }
      consequencias={[
        { tipo: 'perde', texto: 'A célula deixa de existir em qualquer lugar do sistema' },
      ]}
      palavraConfirmacao={celula.nome}
      textoConfirmar="Excluir definitivamente"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
```

- [ ] **Step 2: Criar `arquivados.module.css`**

```css
.lista {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.linha {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  flex-wrap: wrap;
}

.info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.icone {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-full);
  background: var(--color-surface-muted);
  color: var(--color-text-muted);
  flex-shrink: 0;
}

.nome {
  font-weight: var(--font-weight-medium);
  color: var(--color-text-primary);
}
.detalhe {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.acoes {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.botaoRestaurar, .botaoExcluir {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  border: 1px solid var(--color-border);
  background: transparent;
  cursor: pointer;
  transition: background var(--transition-fast);
}
.botaoRestaurar:hover:not(:disabled) { background: var(--color-surface-muted); }
.botaoRestaurar:disabled { opacity: 0.5; cursor: not-allowed; }

.botaoExcluir {
  color: var(--color-danger);
  border-color: var(--color-danger-border, var(--color-danger));
}
.botaoExcluir:hover:not(:disabled) { background: var(--color-danger-bg, rgba(220, 38, 38, 0.08)); }
.botaoExcluir:disabled { opacity: 0.4; cursor: not-allowed; }

@media (max-width: 640px) {
  .linha { flex-direction: column; align-items: stretch; }
  .acoes { justify-content: flex-end; }
}
```

(As variáveis `--color-danger`/`--color-danger-bg`/`--color-danger-border` seguem o
tema já usado em outros botões destrutivos do projeto — se algum nome não existir no
tema global, ajustar pro nome real usado em `MenuAcoes.module.css` ou equivalente,
que já estiliza `perigo: true`.)

- [ ] **Step 3: Rodar o front e testar visualmente**

```bash
cd frontend
npm run dev
```
Arquivar uma célula de teste (aba "Ativas" → menu → Arquivar), depois abrir a aba
"Arquivadas" e conferir: aparece na lista, "Restaurar" funciona (volta pra "Ativas"),
"Excluir definitivamente" fica desabilitado se a célula tinha membro, habilitado se
não tinha.

- [ ] **Step 4: Typecheck**

```bash
npx tsc --noEmit
```
Esperado: sem erro.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/\(app\)/celulas/arquivados/
git commit -m "feat(celula): página de arquivados com restaurar e excluir definitivo"
```

---

## Task 8: Front — botão dinâmico Excluir/Arquivar na listagem normal

**Files:**
- Modify: `frontend/src/app/(app)/celulas/page.tsx`

**Interfaces:**
- Consumes: `celula.temVinculo` (Task 4), `useExcluirCelulaDefinitivamente` (Task 5),
  `ModalConfirmacaoCritica`

- [ ] **Step 1: Trocar o item de menu "Arquivar" por lógica condicional**

Em `page.tsx`, dentro do `.map(c => ...)` que monta `acoes`, trocar:

```tsx
              ...(podeGerenciar ? [{ label: 'Arquivar', icone: Archive, onClick: () => handleToggleArquivar(c.id), perigo: true, separadorAntes: true }] : []),
```

por:

```tsx
              ...(podeGerenciar ? [
                c.temVinculo
                  ? { label: 'Arquivar', icone: Archive, onClick: () => handleToggleArquivar(c.id), perigo: true, separadorAntes: true }
                  : { label: 'Excluir', icone: Trash2, onClick: () => setExcluindoDefinitivo(c), perigo: true, separadorAntes: true }
              ] : []),
```

- [ ] **Step 2: Importar `Trash2` e o hook/modal, adicionar estado e o modal no JSX**

No topo do arquivo, no import de `lucide-react`, adicionar `Trash2`:
```tsx
import { ChevronRight, Plus, Pencil, Archive, Grid3X3, Crown, X, Trash2 } from 'lucide-react'
```

Adicionar os imports novos:
```tsx
import { useExcluirCelulaDefinitivamente } from '@/hooks/celula/useExcluirCelulaDefinitivamente'
import { ModalConfirmacaoCritica } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
```

Adicionar o estado, junto dos outros `useState` já existentes:
```tsx
  const [excluindoDefinitivo, setExcluindoDefinitivo] = useState<CelulaResponse | null>(null)
```

Adicionar o modal no fim do JSX, ao lado do modal de foto (`{fotoVisualizando && (...)}`):
```tsx
      {excluindoDefinitivo && (
        <ModalExcluirDefinitivo celula={excluindoDefinitivo} onClose={() => setExcluindoDefinitivo(null)} />
      )}
```

E o componente auxiliar, no fim do arquivo (fora do `CelulasPage`):
```tsx
function ModalExcluirDefinitivo({ celula, onClose }: { celula: CelulaResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirCelulaDefinitivamente(celula, onClose)
  return (
    <ModalConfirmacaoCritica
      titulo="Excluir célula definitivamente?"
      mensagem={<>Isso vai apagar <strong>{celula.nome}</strong> de vez. Não tem como desfazer.</>}
      consequencias={[{ tipo: 'perde', texto: 'A célula deixa de existir em qualquer lugar do sistema' }]}
      palavraConfirmacao={celula.nome}
      textoConfirmar="Excluir definitivamente"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
```

(Esse componente é idêntico ao `ModalConfirmacaoCriticaWrapper` da Task 7 — dá pra
extrair pra um arquivo compartilhado `ModalExcluirCelulaDefinitivamente.tsx` se
preferir DRY sobre duplicação; deixado duplicado aqui de propósito, pra cada task
ficar independente e não criar acoplamento entre as duas páginas antes de saber se o
padrão se repete nos próximos módulos.)

- [ ] **Step 2: Typecheck**

```bash
cd frontend
npx tsc --noEmit
```
Esperado: sem erro.

- [ ] **Step 3: Testar visualmente**

```bash
npm run dev
```
Criar uma célula de teste sem membro → botão do menu deve dizer "Excluir" e, ao
confirmar (digitando o nome), a célula some de vez (não aparece em "Arquivadas").
Criar outra com um membro vinculado → botão deve dizer "Arquivar" e continuar indo
pra aba "Arquivadas" como hoje.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/\(app\)/celulas/page.tsx
git commit -m "feat(celula): botão Excluir/Arquivar dinâmico conforme vínculo"
```

---

## Self-Review (já aplicado ao escrever o plano)

- **Cobertura da spec** (pro pedaço Célula): regra "tem vínculo" ✅ (Task 2/8),
  listar arquivados ✅ (Task 1/2/3/7), restaurar ✅ (Task 1/2/3/5/7), botão
  dinâmico Excluir/Arquivar ✅ (Task 8), aba de rota ✅ (Task 6), reindexação na
  busca ✅ (outbox `CRIADO` ao restaurar, `REMOVIDO` já existia ao excluir/arquivar).
  Anonimização e `eliminacao_lgpd`: fora de escopo deste pedaço, como já combinado.
- **Placeholders:** nenhum "TBD"/"implementar depois" — toda etapa tem código real.
- **Consistência de tipos:** `CelulaResponse.temVinculo` usado igual em back (Task 2)
  e front (Task 4/7/8); `celulaService.restaurar`/`excluirDefinitivo` com as mesmas
  assinaturas nos hooks que os consomem.
