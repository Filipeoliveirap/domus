# Compartilhamento de Postagens e Avisos entre Igrejas da Rede — Design

> Spec para compartilhamento de postagens do Feed e Mural de Avisos com as demais igrejas da família (rede de congregações/sede).

## Contexto & Motivação

Igrejas vinculadas em uma família (sede e congregações) desejam compartilhar comunicados, devocionais, testemunhos e avisos no feed da comunidade com toda a rede de igrejas vinculadas. 
A funcionalidade segue o mesmo conceito já adotado no módulo de Eventos (`docs/specs/2026-07-28-eventos-compartilhados-design.md`), permitindo que a postagem seja visível a todos os membros das igrejas da família.

## Modelo de Dados (Flyway Migration V48)

Adição do campo de restrição na tabela `postagem`:

```sql
ALTER TABLE postagem ADD COLUMN restrito_propria_igreja BOOLEAN NOT NULL DEFAULT true;

-- Garantir que postagens já existentes em igrejas com família permaneçam restritas à própria igreja
UPDATE postagem p
SET restrito_propria_igreja = true
WHERE EXISTS (
    SELECT 1 FROM igreja i
    WHERE i.id = p.igreja_id
      AND (i.igreja_mae_id IS NOT NULL OR EXISTS (SELECT 1 FROM igreja f WHERE f.igreja_mae_id = i.id))
);
```

- `restrito_propria_igreja = true` (padrão): visível apenas pelos membros da própria igreja criadora.
- `restrito_propria_igreja = false`: visível por todas as igrejas da família (sede + congregações irmãs + filhas).

## Arquitetura & Padrões de Projeto (Strategy + Specification)

Para garantir alta coesão e baixo acoplamento para futuras expansões de funcionalidades da rede:

1. **`EspecificacaoEscopoRede` (Specification Pattern)**:
   - Encapsula as regras de filtro SQL/JPQL para recursos compartilháveis com a rede (`WHERE igreja_id = :minhaIgreja OR (igreja_id IN :restoDaFamilia AND restrito_propria_igreja = false)`).
2. **`EstrategiaVisibilidadeRede` (Strategy Pattern)**:
   - Reutiliza `FamiliaIgrejaService.idsDaFamiliaCompleta(igrejaId)` para determinar se a igreja possui família e resolver os IDs dos membros da rede.
3. **`Postagem` (Entity)**:
   - Adiciona atributo `boolean restritoPropriaIgreja` com getter/setter.

## Regras de Negócio & Autorização

### Visibilidade & Leitura
- **`GET /postagens/mural` & `GET /postagens/feed`**:
  - Retorna postagens da própria igreja (`restrito_propria_igreja` true ou false) E postagens de outras igrejas da família onde `restrito_propria_igreja = false`.
- **`PostagemResponse`**:
  - `igrejaAutor`: `{ id, nome, sigla }`
  - `restritoPropriaIgreja`: `boolean`
  - `podeEditar`: `boolean` (calculado no backend)
  - `podeDeletar`: `boolean` (calculado no backend)

### Reações (Curtidas) e Comentários
- **Curtir / Comentar**: Qualquer usuário autenticado da família completa pode curtir e comentar em um post compartilhado.
- **`ComentarioResponse`**:
  - `igrejaAutor`: `{ id, nome, sigla }` (para permitir a exibição do badge de igreja no comentário).

### Gestão (Edição e Exclusão)
- **Editar Post**: Apenas o autor original OU `ADMIN_IGREJA`/`LIDER` da **igreja criadora do post**.
- **Excluir Post**: Apenas o autor original OU `ADMIN_IGREJA`/`LIDER` da **igreja criadora do post**.
- **Excluir Comentário**: Apenas o autor do comentário, autor do post, OU `ADMIN_IGREJA`/`LIDER` da **igreja criadora do post**.

## Frontend & UX Self-Service

1. **Botão Toggle de Compartilhamento**:
   - Localizado ao lado do botão de anexar imagem na caixa de criação de post e aviso.
   - Visível apenas se a igreja tem família (`ehMae` ou `ehFilha`).
   - Rótulo dinâmico via `useRotulos()` e `concordar()` (ex: *"Compartilhar com as demais {rotulos.congregacao.plural}"*).
2. **Badges no Feed e nos Comentários**:
   - Exibe badge com sigla/nome da igreja ao lado do autor no card do post e nos comentários apenas quando `post.igrejaAutor.id !== minhaIgreja.id`.
