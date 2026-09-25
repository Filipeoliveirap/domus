# Design Spec — Mural Oficial e Feed da Comunidade (Tela de Início)

**Data:** 2026-09-22  
**Status:** Aprovado para Planejamento  
**Módulo:** Início (`/inicio`) / Comunidade  

---

## 1. Visão Geral e Objetivos

O objetivo desta especificação é reestruturar a tela de início (`/inicio`) do Domus, evoluindo a experiência atual para integrar:
1. **Mural Oficial de Avisos:** Espaço institucional de destaque (carrossel horizontal) para comunicados pastorais, escalas e avisos gerais publicados exclusivamente por Administradores e Líderes.
2. **Feed da Comunidade:** Feed no formato de microblog/rede social (estilo X/Twitter) para compartilhamento de devocionais, testemunhos, resumos de cultos e pedidos de oração, permitindo interações (curtidas "Amém"/Coração/Orando e comentários) por qualquer membro da igreja.
3. **Seções Existentes Integradas:** Preservação rigorosa dos **Próximos Eventos** (no formato atual do Domus com selo de inscrito e modal de detalhes), do **Versículo do Dia** e dos **Aniversariantes do Mês**.

---

## 2. Layout, Estrutura Visual e Responsividade (Mobile-First)

### 2.1. Layout Desktop (Telas Largas ≥ 1024px)
- **Topo (Full Width):**
  - **Mural Oficial & Quadro de Avisos:** Cabeçalho com ícone `campaign`, badge de avisos ativos e navegação por setas (`<` / `>`). Grid/Carrossel de cards de avisos oficiais com barra lateral colorida por tipo (`bg-primary`, `bg-secondary`, etc.).
- **Grid de 2 Colunas (proporção ~65% / ~35%):**
  - **Coluna Esquerda (Feed Principal):**
    1. **Caixa de Nova Postagem (Estilo X / Microblog):** Avatar do usuário, identificação, textarea com redimensionamento natural, seletor de categorias/tags (`Devocional`, `Resumo do Culto`, `Pedido de Oração`, `Testemunho`, `Aviso`), botões de mídia (upload de foto, adicionar versículo, marcar como pedido de oração) e botão "Publicar".
    2. **Filtros do Feed:** Abas horizontais (`Tudo`, `Devocionais`, `Resumos da Semana`, `Pedidos de Oração`, `Avisos`) com botão de filtro secundário.
    3. **Fluxo do Feed:** Lista vertical de postagens interativas com contador de reações, botão "Amém", formulário de comentário inline e respostas da comunidade.
  - **Coluna Direita (Painel Lateral Fixo):**
    1. **Versículo do Dia:** Card elegante com aspas e citação bíblica (mantendo a funcionalidade atual).
    2. **Próximos Eventos:** Mantém a trilha de cards interativos do Domus (`.card-interativo`, data em badge, hora, local, selo `SeloInscritoCard` e modal de detalhes `ModalEventoResumo`).
    3. **Aniversariantes do Mês:** Card compacto com os 4 primeiros do mês e botão para abrir o `ModalAniversariantes`.

### 2.2. Layout Mobile (Telas Pequenas < 1024px)
No mobile, o layout prioriza rolagens horizontais fluidas e acesso rápido sem poluição vertical:
1. **Topo (Mural Oficial):** Carrossel horizontal arrastável (`overflow-x-auto hide-scrollbar snap-x`), exibindo 1 card de aviso por vez com padding lateral.
2. **Atalhos / Chips Flutuantes (Acesso Rápido a Versículo e Aniversariantes):**
   - Barramento de chips horizontais abaixo do topo:
     - `<ChipVersiculo>`: Ao tocar, abre o Versículo em um bottom-sheet/drawer animado.
     - `<ChipAniversariantes>`: Badge informando ex.: *"🎉 3 aniversariantes hoje"*; ao tocar, abre o `ModalAniversariantes` existente.
3. **Caixa de Nova Postagem:** Apresentada de forma compacta (textarea de 1 linha que expande ao foco com `<Colapsavel>`).
4. **Próximos Eventos:** Trilha horizontal arrastável (`overflow-x-auto hide-scrollbar`), mantendo os cards interativos atuais em formato compacto.
5. **Feed da Comunidade:** Feed principal rolável na vertical.

---

## 3. Diretrizes de UX, Motion e Animações (Alinhado com `docs/design-guidelines.md`)

Para garantir que a experiência seja fluida, responsiva e consistente com o restante da aplicação, o desenvolvimento no front-end deve obrigatoriamente seguir:

1. **Entrada e Transição de Elementos:**
   - Uso de `<Transicao modo="subir">` ou `<Transicao modo="fade">` para carregamento de postagens e filtros do feed, evitando que conteúdos apareçam secos.
   - Expandir/recolher a caixa de postagem e seções de comentários utilizando o `<Colapsavel aberto={bool}>` (`components/common/Transicao/`).
2. **Saída Animada de Modais e Bottom-Sheets:**
   - Modais de avisos e drawers mobile devem utilizar o hook `useFecharAnimado(onClose, 220)` junto com a classe CSS `.saindo` (garantindo compatibilidade com iOS Safari < 17.4 via `@keyframes`).
   - No mobile (`@media (max-width: 767px)`), modais viram **bottom-sheets** colados ao rodapé com indicador `.grabber` e trava de scroll de fundo (`document.body.style.overflow = 'hidden'`).
3. **Micro-feedback de Toque:**
   - Todos os botões de reação ("Amém", "Curtir", "Orando", "Responder", botões de filtro e envio) devem ter feedback de toque `:active { transform: scale(0.95); transition: transform 0.12s; }`, respeitando `@media (prefers-reduced-motion: reduce)`.
4. **Cards Interativos:**
   - Cards de evento e avisos clicáveis utilizam a classe `.card-interativo` com acento de cor via token de categoria (`--cat-eventos`), mantendo o padrão visual e de toque estabelecido.

---

## 4. Modelo de Dados (Postgres & Flyway Migration)

Nova migration Flyway `VXX__criar_tabelas_mural_e_feed.sql`:

```sql
-- Tabela Principal de Postagens (Mural Oficial e Feed)
CREATE TABLE postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id UUID NOT NULL REFERENCES igreja(id),
    autor_pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    tipo VARCHAR(30) NOT NULL, -- MURAL_AVISO, DEVOCIONAL, PEDIDO_ORACAO, TESTEMUNHO, RESUMO_CULTO, GERAL
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

-- Curtidas e Reações nas Postagens
CREATE TABLE curtida_postagem (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    postagem_id UUID NOT NULL REFERENCES postagem(id) ON DELETE CASCADE,
    pessoa_id UUID NOT NULL REFERENCES pessoa(id),
    tipo_reacao VARCHAR(20) NOT NULL DEFAULT 'AMEM', -- AMEM, CORACAO, ORANDO
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_curtida_postagem_pessoa UNIQUE (postagem_id, pessoa_id)
);

CREATE INDEX idx_curtida_postagem ON curtida_postagem(postagem_id);

-- Comentários nas Postagens
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

---

## 5. Arquitetura do Backend (Spring Boot)

### 5.1. Módulo: `com.domus.api.modules.postagem`
- **`PostagemController.java`**:
  - `GET /postagens/mural` — Lista avisos oficiais fixados/recentes.
  - `GET /postagens/feed` — Lista feed paginado com filtros por `tipo`.
  - `POST /postagens` — Criar nova postagem (valida `oficial=true` apenas para ADMIN/LÍDER).
  - `DELETE /postagens/{id}` — Soft delete de postagem (apenas autor ou ADMIN).
  - `POST /postagens/{id}/curtir` — Alterna ou adiciona reação (Amém/Coração/Orando).
  - `POST /postagens/{id}/comentarios` — Adiciona comentário.
  - `DELETE /postagens/{id}/comentarios/{comentarioId}` — Remove comentário (autor do comentário, autor do post ou ADMIN).
- **Regra Multi-tenant:** `igreja_id` extraído obrigatoriamente do `UsuarioAutenticado` (JWT).

### 5.2. Integração com a Central de Notificações
- Ao criar uma `curtida_postagem` ou `comentario_postagem`, o `PostagemService` dispara um evento/serviço para a Central de Notificações (`NotificacaoService`), gerando notificação in-app para o `autor_pessoa_id` da postagem (exceção se o autor reagir ao próprio post).

---

## 6. Arquitetura do Frontend (Next.js & TanStack Query)

- **Diretório:** `frontend/src/app/(app)/inicio/`
  - `page.tsx` — Página principal reestruturada.
  - `MuralAvisosCarrossel.tsx` — Componente do topo (Desktop & Mobile scroll).
  - `CaixaCriarPostagem.tsx` — Formulário interativo estilo X.
  - `FeedComunidade.tsx` — Lista de posts com filtros, curtidas e comentários.
  - `PostItem.tsx` — Card individual de postagem.
  - `ModalNovoAviso.tsx` — Modal para ADMIN/LÍDER publicar aviso oficial.
- **Hooks:**
  - `useMuralAvisos()` — Query para avisos oficiais.
  - `useFeedPostagens(tipoFilter)` — Query paginada do feed da comunidade.
  - `useCriarPostagem()`, `useCurtirPostagem()`, `useComentarPostagem()`.

---

## 7. Estratégia de Testes

1. **Backend (JUnit 5 + Mockito + AssertJ):**
   - Unit: `PostagemServiceTest` testando criação de post oficial por membro comum (deve falhar), soft delete por autor vs não-autor, e envio de notificação in-app.
   - Controller: `PostagemControllerTest` validando status HTTP e isolamento multi-tenant.
2. **Frontend (Vitest + RTL + MSW / Playwright E2E):**
   - Component: Renderização do feed, alternância de reagir/curtir com feedback instantâneo (optimistic updates ou invalidation), e exibição de modal de comentários.
   - E2E (Chromium & WebKit iOS): Testar navegação mobile, rolagem do carrossel do mural e drawer de versículo/aniversariantes.

---

## 8. Decisão de Spec Self-Review

- [x] Nomes de tabelas e colunas em português e padrão snake_case.
- [x] Regra multi-tenant rigorosa via `igreja_id`.
- [x] UX mobile com soluções adequadas para 3 colunas (carrossel + chips + drawers).
- [x] Padrões de animação (`<Colapsavel>`, `<Transicao>`, `useFecharAnimado`) devidamente especificados.
