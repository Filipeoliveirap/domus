# Mural Oficial e Feed da Comunidade — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reestruturar a tela de início (`/inicio`), criando o Mural Oficial de Avisos (carrossel de avisos institucionais) e o Feed da Comunidade (estilo X/Twitter para devocionais, testemunhos, orações e resumos), integrando curtidas, comentários, notificações in-app e suporte mobile refinado.

**Architecture:** Módulo Spring Boot (`com.domus.api.modules.postagem`) com entidades JPA (`Postagem`, `CurtidaPostagem`, `ComentarioPostagem`), controllers REST multi-tenant (`igreja_id` JWT) e integração com `NotificacaoService`. No frontend Next.js (`/inicio`), visualização responsiva desktop 2-colunas e mobile em carrossel + chips/drawers com componentes TanStack Query, RHF+Zod e CSS Modules com animações padrão (`<Colapsavel>`, `<Transicao>`, `useFecharAnimado`).

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL (Flyway V45), React / Next.js 15, TypeScript, TanStack Query v5, CSS Modules, Lucide Icons, Vitest, RTL, JUnit 5, AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-22-mural-feed-design.md`

## Global Constraints
- Isolamento multi-tenant obrigatório via `igreja_id` extraído exclusivamente do JWT.
- Permissão `oficial=true` restrita aos perfis `ADMIN_IGREJA` e `LIDER`.
- Interações de curtir/comentar permitidas a qualquer perfil ativo (`ADMIN_IGREJA`, `LIDER`, `MEMBRO`).
- Soft delete (`deleted_at`) em postagens e comentários.
- Manter o padrão visual e comportamental dos Próximos Eventos, Versículo e Aniversariantes existentes.
- Respeitar diretrizes de motion de `docs/design-guidelines.md` (`<Colapsavel>`, `<Transicao>`, `useFecharAnimado`, `:active scale(0.95)`).

## Review Focus
1. Tentar criar postagem oficial (`oficial=true`) com usuário `MEMBRO` -> Deve retornar `403 Forbidden`.
2. Tentar excluir comentário de terceiro como `MEMBRO` comum (não sendo o autor do post nem ADMIN) -> Deve retornar `403 Forbidden`.
3. Tentar acessar postagens de outra igreja trocando ID -> Deve ser bloqueado pelo filtro de `igreja_id` do JWT.
4. Animação de fechar modais/drawers no iOS Safari < 17.4 -> Deve rodar classe `.saindo` via `@keyframes` do `useFecharAnimado`.
5. Postagem sem foto/versículo -> Deve renderizar corretamente sem quebrar o layout do feed.

---

### Task 1: Migration Flyway V45 (Tabelas `postagem`, `curtida_postagem`, `comentario_postagem`)

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V45__criar_tabelas_mural_e_feed.sql`

**Interfaces:**
- Produces: Esquema relacional no Postgres para armazenar postagens, curtidas e comentários com suporte multi-tenant e índices.

- [ ] **Step 1: Criar o arquivo de migração SQL**
```sql
CREATE TABLE postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL REFERENCES igreja(id),
    autor_pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    tipo VARCHAR(30) NOT NULL,
    oficial BOOLEAN NOT NULL DEFAULT FALSE,
    titulo VARCHAR(150),
    conteudo TEXT NOT NULL,
    foto_id UUID REFERENCES foto(id),
    versiculo_ref VARCHAR(100),
    fixado BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_postagem_igreja_criado ON postagem(igreja_id, criado_em DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_postagem_oficial ON postagem(igreja_id, oficial) WHERE deleted_at IS NULL;

CREATE TABLE curtida_postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postagem_id UUID NOT NULL REFERENCES postagem(id) ON DELETE CASCADE,
    pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    tipo_reacao VARCHAR(20) NOT NULL DEFAULT 'AMEM',
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_curtida_postagem_pessoa UNIQUE (postagem_id, pessoa_id)
);

CREATE INDEX idx_curtida_postagem ON curtida_postagem(postagem_id);

CREATE TABLE comentario_postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postagem_id UUID NOT NULL REFERENCES postagem(id) ON DELETE CASCADE,
    autor_pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    conteudo TEXT NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_comentario_postagem ON comentario_postagem(postagem_id, criado_em ASC) WHERE deleted_at IS NULL;
```

- [ ] **Step 2: Validar sintaxe da migration SQL via Flyway/Maven**
Run: `cd backend/api && ./mvnw test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add backend/api/src/main/resources/db/migration/V45__criar_tabelas_mural_e_feed.sql
git commit -m "db(migration): adiciona V45 para tabelas de postagem, curtida e comentario"
```

---

### Task 2: Backend Entities, Enums e Repositories

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/TipoPostagem.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/TipoReacao.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/Postagem.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/CurtidaPostagem.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/ComentarioPostagem.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/PostagemRepository.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/CurtidaPostagemRepository.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/ComentarioPostagemRepository.java`

**Interfaces:**
- Produces: Modelo ORM JPA e Spring Data Repositories com consultas filtradas por `igrejaId` e `deletedAt IS NULL`.

- [ ] **Step 1: Criar Enums `TipoPostagem` e `TipoReacao`**
```java
package com.domus.api.modules.postagem;

public enum TipoPostagem {
    MURAL_AVISO,
    DEVOCIONAL,
    PEDIDO_ORACAO,
    TESTEMUNHO,
    RESUMO_CULTO,
    GERAL
}
```
```java
package com.domus.api.modules.postagem;

public enum TipoReacao {
    AMEM,
    CORACAO,
    ORANDO
}
```

- [ ] **Step 2: Criar Entidades JPA (`Postagem`, `CurtidaPostagem`, `ComentarioPostagem`)**
Implementar com Lombok (`@Getter`, `@Setter`, `@NoArgsConstructor`, `@AllArgsConstructor`), `@Entity`, `@Table`, `@ManyToOne` para `igreja`, `pessoa`, `foto` e soft delete.

- [ ] **Step 3: Criar Repositories Spring Data JPA**
```java
package com.domus.api.modules.postagem;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostagemRepository extends JpaRepository<Postagem, UUID> {
    
    @Query("SELECT p FROM Postagem p WHERE p.igreja.id = :igrejaId AND p.oficial = true AND p.deletedAt IS NULL ORDER BY p.fixado DESC, p.criadoEm DESC")
    List<Postagem> findMuralAvisos(@Param("igrejaId") UUID igrejaId);

    @Query("SELECT p FROM Postagem p WHERE p.igreja.id = :igrejaId AND p.deletedAt IS NULL AND (:tipo IS NULL OR p.tipo = :tipo) ORDER BY p.criadoEm DESC")
    Page<Postagem> findFeed(@Param("igrejaId") UUID igrejaId, @Param("tipo") TipoPostagem tipo, Pageable pageable);

    Optional<Postagem> findByIdAndIgrejaIdAndDeletedAtIsNull(UUID id, UUID igrejaId);
}
```

- [ ] **Step 4: Compilar o backend para verificar mapeamentos JPA**
Run: `cd backend/api && ./mvnw test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/main/java/com/domus/api/modules/postagem/
git commit -m "feat(backend): cria entidades JPA e repositories para postagens, curtidas e comentarios"
```

---

### Task 3: Backend DTOs e Service (`PostagemService`)

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/dto/CriarPostagemRequest.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/dto/CriarComentarioRequest.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/dto/CurtidaRequest.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/dto/PostagemResponse.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/dto/ComentarioResponse.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/PostagemService.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/postagem/PostagemServiceTest.java`

**Interfaces:**
- Consumes: Repositories e `NotificacaoService`.
- Produces: Lógica de negócio para postar, curtir, comentar, validar permissão de post oficial e disparar notificações in-app.

- [ ] **Step 1: Escrever teste unitário `PostagemServiceTest` que falha**
```java
package com.domus.api.modules.postagem;

import com.domus.api.shared.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PostagemServiceTest {

    @Mock
    private PostagemRepository postagemRepository;

    @InjectMocks
    private PostagemService postagemService;

    @Test
    @DisplayName("membro_comunidade_nao_pode_criar_postagem_oficial")
    void membroComunidadeNaoPodeCriarPostagemOficial() {
        UUID igrejaId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        
        assertThatThrownBy(() -> postagemService.criarPostagem(igrejaId, pessoaId, "MEMBRO", null))
                .isInstanceOf(RegraNegocioException.class);
    }
}
```

- [ ] **Step 2: Executar o teste e garantir que falha**
Run: `cd backend/api && ./mvnw test -Dtest=PostagemServiceTest`
Expected: FAIL (classe/métodos ainda não existem)

- [ ] **Step 3: Implementar os DTOs e a classe `PostagemService`**
Criar DTOs com validações `@NotBlank`, `@NotNull` e construir o service com regras de permissão (`ADMIN_IGREJA` ou `LIDER` para `oficial=true`) e integrações.

- [ ] **Step 4: Executar o teste e garantir que passa**
Run: `cd backend/api && ./mvnw test -Dtest=PostagemServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/main/java/com/domus/api/modules/postagem/ backend/api/src/test/java/com/domus/api/modules/postagem/
git commit -m "feat(backend): implementa DTOs e PostagemService com testes unitarios"
```

---

### Task 4: Backend REST Controller (`PostagemController`) e Teste de Integração

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/postagem/PostagemController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/postagem/PostagemControllerTest.java`

**Interfaces:**
- Consumes: `PostagemService` e `UsuarioAutenticado`.
- Produces: Endpoints HTTP `/postagens/mural`, `/postagens/feed`, `/postagens`, `/postagens/{id}/curtir`, `/postagens/{id}/comentarios`.

- [ ] **Step 1: Criar o teste `PostagemControllerTest`**
Verificar se as rotas filtram por tenant e chamam o service adequadamente com `MockMvc`.

- [ ] **Step 2: Executar o teste para falhar**
Run: `cd backend/api && ./mvnw test -Dtest=PostagemControllerTest`
Expected: FAIL

- [ ] **Step 3: Implementar `PostagemController.java`**
```java
package com.domus.api.modules.postagem;

import com.domus.api.modules.postagem.dto.*;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/postagens")
@RequiredArgsConstructor
public class PostagemController {

    private final PostagemService postagemService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/mural")
    public List<PostagemResponse> listarMural() {
        return postagemService.listarMural(usuarioAutenticado.getIgrejaId());
    }

    @GetMapping("/feed")
    public Page<PostagemResponse> listarFeed(
            @RequestParam(required = false) TipoPostagem tipo,
            Pageable pageable) {
        return postagemService.listarFeed(usuarioAutenticado.getIgrejaId(), tipo, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostagemResponse criar(@RequestBody @Valid CriarPostagemRequest request) {
        return postagemService.criarPostagem(
                usuarioAutenticado.getIgrejaId(),
                usuarioAutenticado.getPessoaId(),
                usuarioAutenticado.getPerfil(),
                request
        );
    }

    @PostMapping("/{id}/curtir")
    public PostagemResponse curtir(
            @PathVariable UUID id,
            @RequestBody @Valid CurtidaRequest request) {
        return postagemService.alternarCurtida(usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), id, request.tipo());
    }

    @PostMapping("/{id}/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public ComentarioResponse comentar(
            @PathVariable UUID id,
            @RequestBody @Valid CriarComentarioRequest request) {
        return postagemService.comentar(usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), id, request.conteudo());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletar(@PathVariable UUID id) {
        postagemService.deletarPostagem(usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getPessoaId(), usuarioAutenticado.getPerfil(), id);
    }
}
```

- [ ] **Step 4: Executar o teste controller para verificar sucesso**
Run: `cd backend/api && ./mvnw test -Dtest=PostagemControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/main/java/com/domus/api/modules/postagem/PostagemController.java backend/api/src/test/java/com/domus/api/modules/postagem/PostagemControllerTest.java
git commit -m "feat(backend): adiciona PostagemController com testes de integracao"
```

---

### Task 5: Frontend Types, Endpoints e Service

**Files:**
- Create: `frontend/src/types/postagem.type.ts`
- Modify: `frontend/src/lib/endpoints.ts`
- Create: `frontend/src/services/postagem.service.ts`

**Interfaces:**
- Produces: Tipagem TypeScript e métodos Axios para consumo das APIs `/postagens`.

- [ ] **Step 1: Criar `frontend/src/types/postagem.type.ts`**
```ts
export type TipoPostagem = 'MURAL_AVISO' | 'DEVOCIONAL' | 'PEDIDO_ORACAO' | 'TESTEMUNHO' | 'RESUMO_CULTO' | 'GERAL'
export type TipoReacao = 'AMEM' | 'CORACAO' | 'ORANDO'

export interface AutorPostagem {
  id: string
  nome: string
  fotoId: string | null
  cargo?: string | null
}

export interface Comentario {
  id: string
  autor: AutorPostagem
  conteudo: string
  criadoEm: string
}

export interface Postagem {
  id: string
  autor: AutorPostagem
  tipo: TipoPostagem
  oficial: boolean
  titulo?: string | null
  conteudo: string
  fotoId?: string | null
  versiculoRef?: string | null
  fixado: boolean
  criadoEm: string
  totalCurtidas: number
  totalComentarios: number
  minhaReacao: TipoReacao | null
  comentariosRecentes?: Comentario[]
}
```

- [ ] **Step 2: Atualizar `frontend/src/lib/endpoints.ts`**
Adicionar rotas sob `postagens`: `MURAL`, `FEED`, `CRIAR`, `CURTIR(id)`, `COMENTAR(id)`.

- [ ] **Step 3: Criar `frontend/src/services/postagem.service.ts`**
Implementar chamadas `api.get`, `api.post`, `api.delete`.

- [ ] **Step 4: Validar compilação do TypeScript no Frontend**
Run: `cd frontend && npm run test:unit`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add frontend/src/types/postagem.type.ts frontend/src/lib/endpoints.ts frontend/src/services/postagem.service.ts
git commit -m "feat(frontend): adiciona tipos TypeScript e postagem.service"
```

---

### Task 6: Frontend Custom Hooks (`useMuralAvisos`, `useFeedPostagens`, etc.)

**Files:**
- Create: `frontend/src/hooks/postagem/useMuralAvisos.ts`
- Create: `frontend/src/hooks/postagem/useFeedPostagens.ts`
- Create: `frontend/src/hooks/postagem/useCriarPostagem.ts`
- Create: `frontend/src/hooks/postagem/useCurtirPostagem.ts`
- Create: `frontend/src/hooks/postagem/useComentarPostagem.ts`

**Interfaces:**
- Consumes: `postagemService` e TanStack Query (`useQuery`, `useMutation`, `queryClient.invalidateQueries`).
- Produces: Custom hooks prontos para consumo nos componentes do feed e mural.

- [ ] **Step 1: Criar hooks de query e mutação**
Implementar `useMuralAvisos` (queryKey `['mural-avisos']`) e `useFeedPostagens` (queryKey `['feed-postagens', tipo]`) com otimização e revalidação.

- [ ] **Step 2: Validar compilação**
Run: `cd frontend && npm run test:unit`
Expected: PASS

- [ ] **Step 3: Commit**
```bash
git add frontend/src/hooks/postagem/
git commit -m "feat(frontend): cria hooks TanStack Query para mural e feed"
```

---

### Task 7: Componentes Frontend — Carrossel de Mural de Avisos e Modal Novo Aviso

**Files:**
- Create: `frontend/src/app/(app)/inicio/MuralAvisosCarrossel.tsx`
- Create: `frontend/src/app/(app)/inicio/MuralAvisosCarrossel.module.css`
- Create: `frontend/src/app/(app)/inicio/ModalNovoAviso.tsx`
- Create: `frontend/src/app/(app)/inicio/ModalNovoAviso.module.css`

**Interfaces:**
- Consumes: `useMuralAvisos`, `useAuthStore` (`perfil`), `<Transicao>`, `useFecharAnimado`.
- Produces: Carrossel do topo com navegação por setas (desktop) e scroll horizontal (mobile), além do modal de criar aviso oficial para admins/líderes.

- [ ] **Step 1: Criar `MuralAvisosCarrossel.module.css` e `MuralAvisosCarrossel.tsx`**
Implementar o layout responsivo com suporte a `.card-interativo`, acento lateral de cor, navegação por botões e rolagem touch.

- [ ] **Step 2: Criar `ModalNovoAviso.tsx` com animação de saída `useFecharAnimado`**
Garantir suporte a bottom-sheet no mobile e formulário com RHF.

- [ ] **Step 3: Testar renderização no Vitest**
Run: `cd frontend && npm run test:component`
Expected: PASS

- [ ] **Step 4: Commit**
```bash
git add frontend/src/app/\(app\)/inicio/MuralAvisosCarrossel* frontend/src/app/\(app\)/inicio/ModalNovoAviso*
git commit -m "feat(frontend): cria componente de carrossel de mural e modal de novo aviso"
```

---

### Task 8: Componentes Frontend — Caixa de Criar Postagem, Item do Feed e Lista

**Files:**
- Create: `frontend/src/app/(app)/inicio/CaixaCriarPostagem.tsx`
- Create: `frontend/src/app/(app)/inicio/CaixaCriarPostagem.module.css`
- Create: `frontend/src/app/(app)/inicio/PostItem.tsx`
- Create: `frontend/src/app/(app)/inicio/PostItem.module.css`
- Create: `frontend/src/app/(app)/inicio/FeedComunidade.tsx`

**Interfaces:**
- Consumes: `useFeedPostagens`, `useCriarPostagem`, `useCurtirPostagem`, `useComentarPostagem`, `<Colapsavel>`, `<Transicao>`.
- Produces: Caixa de criação estilo X (Twitter), abas de filtro e lista de postagens com reações e seção de comentários expansível.

- [ ] **Step 1: Criar `CaixaCriarPostagem.tsx`**
Formulário com seletor de tags/categorias (`Devocional`, `Pedido de Oração`, etc.), upload de foto opcional e expansão suave com `<Colapsavel>`.

- [ ] **Step 2: Criar `PostItem.tsx`**
Card de postagem com botão de reação ("Amém"/Coração), contador de curtidas, visualização de foto, versículo destacado e seção de comentários retrátil.

- [ ] **Step 3: Criar `FeedComunidade.tsx`**
Lista paginada com abas de filtro e estados de carregamento via `Skeleton` e `EstadoVazio`.

- [ ] **Step 4: Executar testes de componente**
Run: `cd frontend && npm run test:component`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add frontend/src/app/\(app\)/inicio/CaixaCriarPostagem* frontend/src/app/\(app\)/inicio/PostItem* frontend/src/app/\(app\)/inicio/FeedComunidade*
git commit -m "feat(frontend): adiciona caixa de criar postagem estilo X e feed da comunidade"
```

---

### Task 9: Componentes Mobile — Chips e Drawers de Versículo e Aniversariantes

**Files:**
- Create: `frontend/src/app/(app)/inicio/ChipAtalhosMobile.tsx`
- Create: `frontend/src/app/(app)/inicio/DrawerVersiculoMobile.tsx`
- Create: `frontend/src/app/(app)/inicio/inicioMobile.module.css`

**Interfaces:**
- Produces: Barra de atalhos e drawers em bottom-sheet para esconder o Versículo do Dia e Aniversariantes no mobile sem poluição visual.

- [ ] **Step 1: Criar `ChipAtalhosMobile.tsx`**
Barra horizontal de chips exibindo atalhos rápidos para Versículo e Aniversariantes (com contagem ex.: "🎉 3 hoje").

- [ ] **Step 2: Criar `DrawerVersiculoMobile.tsx`**
Bottom-sheet animado via `useFecharAnimado` e `.grabber` exibindo o versículo completo ao tocar no chip.

- [ ] **Step 3: Testar compilação e estilos**
Run: `cd frontend && npm run test:component`
Expected: PASS

- [ ] **Step 4: Commit**
```bash
git add frontend/src/app/\(app\)/inicio/ChipAtalhosMobile* frontend/src/app/\(app\)/inicio/DrawerVersiculoMobile* frontend/src/app/\(app\)/inicio/inicioMobile.module.css
git commit -m "feat(frontend): cria atalhos de chips e drawer mobile para versiculo e aniversariantes"
```

---

### Task 10: Integração Final da Tela de Início (`/inicio/page.tsx`) e Testes E2E

**Files:**
- Modify: `frontend/src/app/(app)/inicio/page.tsx`
- Modify: `frontend/src/app/(app)/inicio/inicio.module.css`
- Test: `frontend/e2e/inicio-feed.spec.ts`

**Interfaces:**
- Consumes: `MuralAvisosCarrossel`, `CaixaCriarPostagem`, `FeedComunidade`, `ChipAtalhosMobile`, mantendo a integração existente com `ModalEventoResumo` e `ModalAniversariantes`.

- [ ] **Step 1: Atualizar `frontend/src/app/(app)/inicio/page.tsx`**
Reorganizar a página com o grid 2-colunas no desktop (Mural no topo, Feed na esquerda, Versículo/Eventos/Aniversariantes na direita) e visual otimizado no mobile.

- [ ] **Step 2: Criar teste Playwright E2E em `frontend/e2e/inicio-feed.spec.ts`**
```ts
import { test, expect } from '@playwright/test'

test.describe('Tela de Início e Feed da Comunidade', () => {
  test('deve carregar o mural de avisos e o feed de postagens', async ({ page }) => {
    await page.goto('/inicio')
    await expect(page.getByRole('heading', { name: /Mural Oficial/i })).toBeVisible()
    await expect(page.getByPlaceholder(/O que está em seu coração hoje/i)).toBeVisible()
  })
})
```

- [ ] **Step 3: Executar a suíte completa de testes no frontend**
Run: `cd frontend && npm run test`
Expected: PASS (unit e component)

- [ ] **Step 4: Executar suíte de testes no backend**
Run: `cd backend/api && ./mvnw test`
Expected: BUILD SUCCESS & PASS ALL TESTS

- [ ] **Step 5: Commit final da funcionalidade**
```bash
git add frontend/src/app/\(app\)/inicio/ frontend/e2e/inicio-feed.spec.ts
git commit -m "feat(inicio): integra mural oficial e feed da comunidade na tela de inicio"
```
