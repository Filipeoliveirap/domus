# Compartilhamento de Postagens e Avisos entre Igrejas da Rede — Plano de Implementação

> Plano passo a passo para implementar o compartilhamento de posts e avisos do mural entre igrejas da rede (família), com padrões de projeto reutilizáveis e badges no frontend.

## 1. Backend (Java 21 / Spring Boot / PostgreSQL)

- [ ] **Task 1: Migration Flyway V48**
  - Criar `backend/api/src/main/resources/db/migration/V48__postagem_restrito_propria_igreja.sql`.
  - Adicionar coluna `restrito_propria_igreja BOOLEAN NOT NULL DEFAULT true` na tabela `postagem`.
  - Atualizar postagens existentes em igrejas que têm família para `restrito_propria_igreja = true`.

- [ ] **Task 2: Atualização da Entidade `Postagem`**
  - Adicionar atributo `private boolean restritoPropriaIgreja;` na entidade `Postagem.java` com builder e defaults.

- [ ] **Task 3: DTOs `CriarPostagemRequest`, `PostagemResponse` e `ComentarioResponse`**
  - Em `CriarPostagemRequest`: adicionar campo opcional `Boolean restritoPropriaIgreja` (se nulo ou não enviado, assume true).
  - Em `PostagemResponse`: adicionar record `IgrejaInfoResponse(UUID id, String nome, String sigla)` e incluir `igrejaAutor`, `restritoPropriaIgreja`, `podeEditar`, `podeDeletar`.
  - Em `ComentarioResponse`: incluir `igrejaAutor` (id, nome, sigla).

- [ ] **Task 4: Atualizar `PostagemRepository` e Consultas JPQL**
  - Alterar `findFeed`: filtrar por `p.igreja.id = :minhaIgreja OR (p.igreja.id IN :restoDaFamilia AND p.restritoPropriaIgreja = false)`.
  - Alterar `findMuralAvisos`: filtrar com a mesma cláusula da família.

- [ ] **Task 5: Atualizar `PostagemService` e Regras de Negócio / Autorização**
  - Integrar `FamiliaIgrejaService.idsDaFamiliaCompleta(igrejaId)` para compor os IDs da família nas consultas de feed e mural.
  - Validar autorização em `atualizarPostagem`, `deletarPostagem`, `deletarComentario`, `alternarCurtida` e `comentar` considerando a igreja do post e da pessoa.

- [ ] **Task 6: Testes Automatizados no Backend**
  - Criar/atualizar `PostagemServiceTest` testando:
    - Post compartilhado de outra igreja da família aparece no feed/mural.
    - Post restrito de outra igreja da família NÃO aparece.
    - Usuário de outra igreja curte e comenta post compartilhado.
    - Usuário de outra igreja tenta editar/deletar post de terceiros e recebe erro de permissão.

## 2. Frontend (Next.js / TypeScript / React)

- [ ] **Task 7: Atualizar Tipos TypeScript em `frontend/src/types/postagem.type.ts`**
  - Adicionar `restritoPropriaIgreja?: boolean`, `igrejaAutor?: { id: string; nome: string; sigla: string | null }`, `podeEditar?: boolean`, `podeDeletar?: boolean`.

- [ ] **Task 8: Adicionar Toggle de Compartilhamento na `CaixaCriarPostagem` e `ModalCriarAviso`**
  - Verificar se a igreja do usuário possui família (`ehMae` ou `ehFilha`).
  - Adicionar botão/toggle de compartilhamento ao lado do botão de anexar foto.
  - Usar `useRotulos()` e `concordar()` para montar o texto do toggle.

- [ ] **Task 9: Exibição de Badges de Igreja no Card de Post e Comentários**
  - Em `PostagemCard.tsx` e `ItemComentario.tsx`: exibir badge com sigla/nome da igreja caso `igrejaAutor.id !== minhaIgrejaId`.

- [ ] **Task 10: Testes Manuais e Validação de Fluxo de Ponta a Ponta**
  - Criar post restrito e compartilhado entre duas igrejas vinculadas na mesma família.
  - Confirmar visibilidade, reações, comentários e badges no feed e no mural.
