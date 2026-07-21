# Pessoa e Vínculo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Renomear `membro` → `pessoa`, trocar `status` por `vinculo (MEMBRO|CONGREGANTE)`, renomear a role `MEMBRO` → `ACESSO_COMUM`, extrair a autorização para uma camada de capacidades, e expor a separação membro/congregante em listas, relatórios e no consolidado.

**Architecture:** A ordem é deliberada. **A camada de permissões sai primeiro**, enquanto os nomes antigos ainda estão de pé — assim o rename da role vira uma linha em vez de trinta. Depois o schema é recriado do zero numa V1 única já com os nomes finais, e o código corre atrás. O front vem por último, quando a API já é a definitiva.

**Tech Stack:** Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway, PostgreSQL (Neon), Redis, Elasticsearch; Next.js 16, TypeScript, TanStack Query, React Hook Form + Zod, CSS Modules.

**Spec:** `docs/superpowers/specs/2026-07-21-pessoa-vinculo-design.md` — leia antes de começar.

## Global Constraints

- `igreja_id` SEMPRE do JWT (`usuarioAutenticado.getIgrejaId()`), NUNCA do corpo da requisição.
- Camadas `controller → service → repository`; services retornam DTOs, nunca entidades.
- **Esconder no front não é esconder.** Restrição por perfil se faz no backend.
- **Pergunte pela capacidade, não pela identidade.** Nenhuma comparação de string de role fora da camada de permissões.
- Comentários, Javadoc e mensagens ao usuário em **português brasileiro**.
- Notificações: `notificar.sucesso/erro/aviso/info` — NUNCA `toast` do sonner. NUNCA `window.confirm`.
- Avatar: foto real quando existe, `iniciais()` como fallback.
- Mobile faz parte da entrega (375px): tabelas viram cards com `grid-template-areas`, nunca `display:none`.
- Invalidação de cache via `invalidarCache`; nunca `queryClient.invalidateQueries` em componente.
- **Sem `Co-Authored-By`** em commits ou PRs.
- Não encadeie `&& echo OK` depois de um pipe — use `$?` ou `${PIPESTATUS[0]}`.
- `mvn -q test` precisa do `.env` carregado; o `.env` tem um espaço sem aspas em `EMAIL_FROM` que quebra `source` puro — contorne sem editar o `.env`.
- Banco: Neon remoto, compartilhado. Não deixe linha de teste para trás.

---

## File Structure

**Backend — criar**
- `shared/security/Role.java` — enum dos perfis
- `shared/security/Permissoes.java` — política de autorização por capacidade
- `modules/pessoa/` — módulo inteiro (renomeado de `modules/membro/`)
- `modules/pessoa/Vinculo.java` — enum `MEMBRO | CONGREGANTE`
- `db/migration/V1__schema_inicial.sql` — schema completo, nomes finais

**Backend — apagar**
- `db/migration/V1..V16` (as 16 atuais)
- `modules/membro/StatusMembro.java`

**Frontend — criar**
- `src/lib/permissoes.ts` — mesmas capacidades do backend
- `src/app/(app)/pessoas/` — renomeada de `membros/`
- `src/components/common/PainelFiltros/` — o botão "Filtros" do protótipo

---

### Task 1: Camada de permissões (backend)

**Files:**
- Create: `src/main/java/com/domus/api/shared/security/Role.java`
- Create: `src/main/java/com/domus/api/shared/security/Permissoes.java`
- Test: `src/test/java/com/domus/api/shared/security/PermissoesTest.java`
- Modify: `modules/evento/inscricao/InscricaoService.java:300,335`
- Modify: `modules/membro/MembroController.java:68`
- Modify: `config/SecurityConfig.java` (todos os `hasRole`/`hasAnyRole`)

**Por que primeiro:** enquanto os nomes antigos estão de pé. Depois disso, renomear a role toca **um arquivo**. Fazer na ordem inversa significaria editar trinta lugares duas vezes.

**Interfaces:**
- Produces: `Role` (enum `ADMIN_IGREJA`, `LIDER`, `MEMBRO`), `Permissoes` com métodos estáticos `podeGerenciarInscricoes(String)`, `podeVerListaCompletaDeInscritos(String)`, `podeVerDadosSensiveisDePessoa(String)`, `podeGerenciarPessoas(String)`, `podeGerenciarEventos(String)`, `podeVerFinanceiro(String)`.

- [ ] **Step 1: Escrever o teste que falha**

```java
package com.domus.api.shared.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PermissoesTest {

    @Test
    void gerenciarInscricoes_valeParaAdminELider_naoParaComum() {
        assertThat(Permissoes.podeGerenciarInscricoes("ADMIN_IGREJA")).isTrue();
        assertThat(Permissoes.podeGerenciarInscricoes("LIDER")).isTrue();
        assertThat(Permissoes.podeGerenciarInscricoes("MEMBRO")).isFalse();
    }

    @Test
    void dadosSensiveisDePessoa_soAdmin() {
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("ADMIN_IGREJA")).isTrue();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("LIDER")).isFalse();
        assertThat(Permissoes.podeVerDadosSensiveisDePessoa("MEMBRO")).isFalse();
    }

    @Test
    void roleDesconhecidaOuNulaNaoRecebeNada() {
        // Fail-closed: perfil que não existe (token adulterado, role removida do banco)
        // não pode cair no ramo permissivo por acidente.
        for (String r : new String[]{null, "", "ROOT", "admin_igreja"}) {
            assertThat(Permissoes.podeGerenciarInscricoes(r)).isFalse();
            assertThat(Permissoes.podeVerDadosSensiveisDePessoa(r)).isFalse();
            assertThat(Permissoes.podeGerenciarEventos(r)).isFalse();
        }
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PermissoesTest`
Expected: FAIL — `Permissoes` não existe (erro de compilação).

- [ ] **Step 3: Implementar o enum**

```java
package com.domus.api.shared.security;

/**
 * Perfis de acesso. Existe para acabar com a string crua espalhada pelo código.
 *
 * <p>O nome fala de NÍVEL DE ACESSO, não de vínculo com a igreja — um congregante com
 * login tem acesso comum, e chamar isso de "MEMBRO" era a confusão que esta mudança elimina.
 */
public enum Role {
    ADMIN_IGREJA,
    LIDER,
    MEMBRO;

    /** Devolve null em vez de estourar: role desconhecida vira "não pode nada" (fail-closed). */
    public static Role deNomeOuNull(String nome) {
        if (nome == null || nome.isBlank()) return null;
        try {
            return Role.valueOf(nome);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
```

- [ ] **Step 4: Implementar a política**

```java
package com.domus.api.shared.security;

import java.util.EnumSet;
import java.util.Set;

/**
 * Política de autorização, num lugar só.
 *
 * <p><b>Por que existe:</b> antes disto o código perguntava a IDENTIDADE
 * ({@code "ADMIN_IGREJA".equals(role) || "LIDER".equals(role)}) quando queria saber a
 * CAPACIDADE ("pode gerenciar inscrições?"). A mesma regra reimplementada em dezenas de
 * lugares tem dois custos: renomear um perfil vira caçada, e uma divergência entre duas
 * cópias é um furo de autorização SILENCIOSO — não quebra compilação nem teste.
 *
 * <p><b>Como usar:</b> chame o método com o nome da ação. Se a pergunta que você precisa
 * fazer não está aqui, adicione um método — não compare string no seu service.
 *
 * <p>Isto NÃO substitui o {@code SecurityConfig}: lá fica a trava por rota, aqui a regra
 * fina de dentro do serviço. As duas leem os mesmos perfis.
 */
public final class Permissoes {

    private Permissoes() {}

    private static final Set<Role> GESTORES = EnumSet.of(Role.ADMIN_IGREJA, Role.LIDER);
    private static final Set<Role> SO_ADMIN = EnumSet.of(Role.ADMIN_IGREJA);

    private static boolean tem(String nomeRole, Set<Role> permitidos) {
        Role role = Role.deNomeOuNull(nomeRole);
        return role != null && permitidos.contains(role);
    }

    /** Cancelar inscrição de outra pessoa, inscrever terceiros, remover convidado alheio. */
    public static boolean podeGerenciarInscricoes(String role) { return tem(role, GESTORES); }

    /** Lista completa de inscritos — inclui telefone de convidado e quem inscreveu quem. */
    public static boolean podeVerListaCompletaDeInscritos(String role) { return tem(role, GESTORES); }

    /** Endereço e observações de uma pessoa. */
    public static boolean podeVerDadosSensiveisDePessoa(String role) { return tem(role, SO_ADMIN); }

    /** Cadastrar, editar e arquivar pessoas. */
    public static boolean podeGerenciarPessoas(String role) { return tem(role, SO_ADMIN); }

    /** Criar, editar e arquivar eventos. */
    public static boolean podeGerenciarEventos(String role) { return tem(role, GESTORES); }

    /** Movimentações, categorias, relatórios e dashboard. */
    public static boolean podeVerFinanceiro(String role) { return tem(role, SO_ADMIN); }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PermissoesTest`
Expected: PASS — 3 testes.

- [ ] **Step 6: Trocar as comparações espalhadas**

Em `InscricaoService.java`, nas duas linhas que hoje têm
`boolean ehAdmin = "ADMIN_IGREJA".equals(role) || "LIDER".equals(role);`:

```java
        boolean ehGestor = Permissoes.podeGerenciarInscricoes(role);
```

Renomeie a variável de `ehAdmin` para `ehGestor` nos usos seguintes — "admin" era impreciso, o líder também entra.

Em `MembroController.java:68`:

```java
    private boolean podeVerDadosSensiveis() {
        return Permissoes.podeVerDadosSensiveisDePessoa(usuarioAutenticado.getRole());
    }
```

Em `SecurityConfig.java`, troque os literais pelo enum. `hasAnyRole` recebe `String...`, então:

```java
    private static final String ADMIN = Role.ADMIN_IGREJA.name();
    private static final String LIDER = Role.LIDER.name();
    private static final String COMUM = Role.MEMBRO.name();
```

e use `.hasRole(ADMIN)` / `.hasAnyRole(ADMIN, LIDER, COMUM)`. **Não mude nenhuma regra nesta task** — só a forma de escrevê-la.

- [ ] **Step 7: Rodar a suíte inteira**

Run: `mvn -q test; echo "EXIT=$?"`
Expected: `EXIT=0`, 166 testes + 3 novos = 169.

- [ ] **Step 8: Verificar que não sobrou literal**

Run: `grep -rn '"ADMIN_IGREJA"\|"LIDER"\|"MEMBRO"' src/main --include=*.java | grep -v "Role.java\|SecurityConfig.java\|V2__"`
Expected: nenhuma linha. Se aparecer alguma, ela é uma regra de autorização que ficou de fora — leve para `Permissoes`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/domus/api/shared/security src/test/java/com/domus/api/shared/security \
        src/main/java/com/domus/api/config/SecurityConfig.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/main/java/com/domus/api/modules/membro/MembroController.java
git commit -m "refactor(seguranca): autorizacao por capacidade em vez de comparacao de role"
```

---

### Task 2: Camada de permissões (frontend)

**Files:**
- Create: `frontend/src/lib/permissoes.ts`
- Modify: todos os arquivos com `role === '...'` (ver lista no Step 3)

**Interfaces:**
- Produces: `podeGerenciarInscricoes(role)`, `podeVerListaCompletaDeInscritos(role)`, `podeVerDadosSensiveisDePessoa(role)`, `podeGerenciarPessoas(role)`, `podeGerenciarEventos(role)`, `podeVerFinanceiro(role)` — **mesmos nomes do backend**, de propósito: a mesma pergunta deve ter o mesmo nome dos dois lados.

- [ ] **Step 1: Criar o módulo**

```ts
import type { Role } from '@/types/usuario.types'

/**
 * As mesmas perguntas de autorização do backend (`shared/security/Permissoes.java`),
 * com os mesmos nomes de propósito: a mesma regra deve ser procurável nos dois lados.
 *
 * ⚠️ Isto NÃO é autorização — é para a interface não oferecer o que vai falhar.
 * Quem decide é o servidor, sempre. Um botão escondido aqui continua bloqueado lá.
 */

const GESTORES: Role[] = ['ADMIN_IGREJA', 'LIDER']
const SO_ADMIN: Role[] = ['ADMIN_IGREJA']

function tem(role: Role | null | undefined, permitidos: Role[]): boolean {
  return role != null && permitidos.includes(role)
}

export const podeGerenciarInscricoes = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerListaCompletaDeInscritos = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerDadosSensiveisDePessoa = (r: Role | null | undefined) => tem(r, SO_ADMIN)
export const podeGerenciarPessoas = (r: Role | null | undefined) => tem(r, SO_ADMIN)
export const podeGerenciarEventos = (r: Role | null | undefined) => tem(r, GESTORES)
export const podeVerFinanceiro = (r: Role | null | undefined) => tem(r, SO_ADMIN)
```

- [ ] **Step 2: Substituir as comparações**

Rode `grep -rn "role === '" src --include=*.tsx --include=*.ts` e troque cada uma pela capacidade correspondente. Os que existem hoje:

| Arquivo | Hoje | Vira |
|---|---|---|
| `components/module/eventos/EventoCard.tsx:31` | `role === 'ADMIN_IGREJA' \|\| role === 'LIDER'` | `podeGerenciarEventos(role)` |
| `components/module/eventos/ModalQuemVai.tsx:32` | idem | `podeGerenciarInscricoes(role)` |
| `components/layout/Sidebar.tsx:95` | `role === 'ADMIN_IGREJA'` | `podeVerConfiguracoes(role)` — adicione essa capacidade em `permissoes.ts` |
| `app/(app)/membros/cadastrar/page.tsx:14` | `role === 'ADMIN_IGREJA'` | `podeGerenciarPessoas(role)` |
| `app/(app)/eventos/[id]/inscritos/page.tsx` | `ADMIN_IGREJA \|\| LIDER` | `podeVerListaCompletaDeInscritos(role)` |
| `app/(app)/eventos/(detalhe)/DrawerDetalheEvento.tsx` | idem | `podeVerListaCompletaDeInscritos(role)` |
| `app/(app)/inicio/ModalEventoResumo.tsx` | idem | `podeGerenciarEventos(role)` |

**Não são substituições:** o array `roles: [...]` do `Sidebar.tsx` (linhas 17-22) é uma tabela de dados, não uma comparação — deixe. O `tipo: 'MEMBRO' | 'EVENTO' | ...` de `useBuscaGlobal.ts` é tipo de resultado de busca, nada a ver com perfil. Os arrays de `ModalConcederAcesso.tsx` e `ModalPermissaoUsuario.tsx` são **listas de opções para o usuário escolher** — continuam citando o valor, mas o rótulo visível será atualizado na Task 5.

- [ ] **Step 3: Verificar**

Run: `cd frontend && npx tsc --noEmit && npx eslint src; echo "EXIT=$?"`
Expected: `tsc` limpo; eslint com os **5 warnings pré-existentes** e 0 erros.

Run: `grep -rn "role === '" src --include=*.tsx --include=*.ts | grep -v permissoes.ts`
Expected: nenhuma linha.

- [ ] **Step 4: Commit**

```bash
git add frontend/src
git commit -m "refactor(front): autorizacao por capacidade em lib/permissoes"
```

---

### Task 3: Schema novo — V1 única

**Files:**
- Delete: `src/main/resources/db/migration/V1__*.sql` até `V16__*.sql` (as 16)
- Create: `src/main/resources/db/migration/V1__schema_inicial.sql`

**Interfaces:**
- Produces: schema com `pessoa` (com `vinculo`), `usuario.pessoa_id`, `movimentacao_financeira.pessoa_id`, `inscricao_evento.pessoa_id`, role `ACESSO_COMUM`, `evento.exclusivo_membros` (uma só), sem `evento.exclusivo_batizados`, sem `pessoa.batizado`.

- [ ] **Step 1: Guardar o seguro**

```bash
source /tmp/envexport.sh   # ou o equivalente que carrega DATABASE_URL/USERNAME/PASSWORD
mkdir -p ~/domus-backup-pre-pessoa
pg_dump "$DATABASE_URL_PSQL" -Fc -f ~/domus-backup-pre-pessoa/dev-$(date +%F).dump
```

Se `pg_dump` não estiver instalado, use o container: `docker run --rm postgres:16 pg_dump ...`.
**Não prossiga sem o dump.** É o único caminho de volta.

- [ ] **Step 2: Gerar o schema atual como base**

Em vez de reescrever 16 migrations de cabeça (fonte garantida de omissão), extraia o schema real:

```bash
pg_dump "$DATABASE_URL_PSQL" --schema-only --no-owner --no-privileges \
  --exclude-table=flyway_schema_history > /tmp/schema-atual.sql
```

Este arquivo é o **ponto de partida**, não o resultado — ele traz o schema com os nomes velhos.

- [ ] **Step 3: Transformar em V1**

Escreva `V1__schema_inicial.sql` a partir do dump, aplicando:

1. `membro` → `pessoa` (tabela, índices, constraints, comentários)
2. `membro_id` → `pessoa_id` em `usuario`, `movimentacao_financeira`, `inscricao_evento`
3. Nomes de constraint: `fk_usuario_membro` → `fk_usuario_pessoa` etc.
4. Em `pessoa`: remover `status` e `batizado`; adicionar
   `vinculo VARCHAR(20) NOT NULL DEFAULT 'CONGREGANTE'`
5. Em `evento`: remover `exclusivo_batizados` (o `exclusivo_membros` fica e passa a significar `vinculo = MEMBRO`)
6. No seed de `role`: `MEMBRO` → `ACESSO_COMUM`, com descrição
   `'Acesso comum: pessoas e eventos, sem gestão'`
7. Manter o trigger da hierarquia de igrejas (era a V14) — ele não muda
8. Manter todos os `CHECK`, `UNIQUE` e índices existentes

Cabeçalho obrigatório no arquivo:

```sql
-- V1: schema inicial do Domus.
--
-- Consolida as antigas V1–V16 numa migration só. Feito em 2026-07-21, junto com a
-- renomeação membro→pessoa: não havia dado real ainda, e uma migration de RENAME
-- carregaria para sempre a cicatriz do nome antigo (constraints chamadas
-- fk_usuario_membro numa tabela chamada pessoa).
--
-- pessoa.vinculo substitui o antigo status (ATIVO|INATIVO|VISITANTE) + batizado:
--   MEMBRO      = batizado, formalmente membro da igreja
--   CONGREGANTE = frequenta, não é batizado (absorveu VISITANTE)
-- "Inativo" não é estado: quem parou de frequentar é ARQUIVADO (deleted_at).
```

- [ ] **Step 4: Recriar o banco de DEV**

```bash
psql "$DATABASE_URL_PSQL" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

- [ ] **Step 5: Aplicar e conferir**

Suba a aplicação e confira o log do Flyway.
Expected: `Successfully applied 1 migration to schema "public", now at version v1`.

Depois confirme o schema:

```bash
psql "$DATABASE_URL_PSQL" -c "\d pessoa" | grep -E "vinculo|status|batizado"
```
Expected: aparece `vinculo`, **não** aparecem `status` nem `batizado`.

```bash
psql "$DATABASE_URL_PSQL" -c "SELECT nome FROM role ORDER BY nome;"
```
Expected: `ACESSO_COMUM`, `ADMIN_IGREJA`, `LIDER`.

> A aplicação **não vai subir inteira** ainda — as entidades JPA ainda falam `membro`. É esperado; a Task 4 resolve. Se o Flyway aplicou e o erro seguinte é de mapeamento de entidade, siga em frente.

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/db/migration
git commit -m "feat(db): schema inicial unico com pessoa/vinculo e ACESSO_COMUM"
```

---

### Task 4: Renomear o módulo no backend

**Files:**
- Rename: `modules/membro/` → `modules/pessoa/` (todos os arquivos)
- Create: `modules/pessoa/Vinculo.java`
- Delete: `modules/membro/StatusMembro.java`, `modules/membro/DTO/MembroDTO.java` (stub vazio, morto)
- Modify: todos os consumidores (`usuario`, `evento/inscricao`, `financeiro`, `igreja`, `inicio`, `busca`)

**Interfaces:**
- Produces: `Pessoa`, `PessoaRepository`, `PessoaService`, `PessoaController`, `Vinculo`, `PessoaResponse`, `PessoaRequestDTO`, `PessoaDocument`. Campo `Pessoa.getVinculo()`.

- [ ] **Step 1: Criar o enum**

```java
package com.domus.api.modules.pessoa;

/**
 * Relação da pessoa com a igreja.
 *
 * <p>Substitui o antigo {@code StatusMembro} e o boolean {@code batizado}, que juntos
 * permitiam o estado impossível "membro não batizado".
 *
 * <p>Não existe "inativo": quem parou de frequentar é <b>arquivado</b> (soft delete),
 * que é o mesmo mecanismo usado por todo o resto do sistema.
 */
public enum Vinculo {
    /** Batizado, formalmente membro da igreja. */
    MEMBRO,
    /** Frequenta, não é batizado. Absorveu o antigo VISITANTE. */
    CONGREGANTE
}
```

- [ ] **Step 2: Mover e renomear**

```bash
git mv src/main/java/com/domus/api/modules/membro src/main/java/com/domus/api/modules/pessoa
cd src/main/java/com/domus/api/modules/pessoa
for f in Membro*.java DTO/Membro*.java busca/Membro*.java; do
  [ -e "$f" ] && git mv "$f" "$(echo "$f" | sed 's/Membro/Pessoa/')"
done
```

- [ ] **Step 3: Ajustar o conteúdo**

Substituições, **nesta ordem** (a mais específica primeiro, senão uma engole a outra):

| De | Para |
|---|---|
| `modules.membro` | `modules.pessoa` |
| `StatusMembro` | `Vinculo` |
| `MembroResponse` | `PessoaResponse` |
| `MembroRequestDTO` | `PessoaRequestDTO` |
| `MembroRepository` | `PessoaRepository` |
| `MembroService` | `PessoaService` |
| `MembroController` | `PessoaController` |
| `MembroDocument` | `PessoaDocument` |
| `MembroSearchRepository` | `PessoaSearchRepository` |
| `BuscaMembroService` | `BuscaPessoaService` |
| `Membro` (tipo) | `Pessoa` |
| `membroId` | `pessoaId` |
| `membro_id` | `pessoa_id` |
| `getMembro()` | `getPessoa()` |
| `setMembro(` | `setPessoa(` |

Depois **leia o diff**. `StatusMembro.ATIVO` → `Vinculo.MEMBRO`? Não necessariamente:

- `IgrejaService:107` — o primeiro membro criado no cadastro da igreja é o admin. Ele é `MEMBRO`? O admin da igreja normalmente é batizado, mas o sistema não pode afirmar. **Use `Vinculo.CONGREGANTE` como padrão** e deixe a pessoa ajustar — inventar "membro" para todo admin recriaria a mentira que esta mudança elimina. Comente a escolha.
- `InscricaoService:215,392` — a regra `VISITANTE || INATIVO` vira `vinculo != Vinculo.MEMBRO`. Simplifica de dois ramos para um.

⚠️ **Não troque a palavra "membro" em texto de domínio.** Comentários e mensagens sobre *membro da igreja* (o vínculo) continuam corretos — só o **tipo** muda. Ex.: `"Este evento é exclusivo para membros da igreja."` fica como está.

- [ ] **Step 4: Compilar**

Run: `mvn -q compile; echo "EXIT=$?"`
Expected: `EXIT=0`. Erro remanescente significa consumidor esquecido — corrija e repita.

- [ ] **Step 5: Ajustar os testes**

Os testes citam `Membro`, `StatusMembro` e `membroId`. Aplique as mesmas substituições em `src/test`. **Não enfraqueça nenhuma asserção** — se um teste passava por `StatusMembro.VISITANTE`, ele agora passa por `Vinculo.CONGREGANTE` e deve continuar verificando a mesma coisa.

- [ ] **Step 6: Rodar a suíte**

Run: `mvn -q test; echo "EXIT=$?"`
Expected: `EXIT=0`, ~169 testes.

- [ ] **Step 7: Commit**

```bash
git add -A src
git commit -m "refactor(dominio): membro vira pessoa e status vira vinculo"
```

---

### Task 5: Role ACESSO_COMUM

**Files:**
- Modify: `shared/security/Role.java`
- Modify: `frontend/src/types/usuario.types.ts`
- Modify: `frontend/src/app/(app)/membros/ModalConcederAcesso.tsx` (rótulos)
- Modify: `frontend/src/app/(app)/usuarios/(editar)/ModalPermissaoUsuario.tsx` (rótulos)

**Esta task é curta de propósito** — é o retorno da Task 1. Se ela estiver grande, a camada de permissões não ficou completa.

- [ ] **Step 1: Backend — uma linha**

Em `Role.java`, `MEMBRO` vira `ACESSO_COMUM`. A V1 já semeia o nome novo (Task 3).

- [ ] **Step 2: Frontend — o tipo**

```ts
export type Role = 'ADMIN_IGREJA' | 'LIDER' | 'ACESSO_COMUM';
```

- [ ] **Step 3: Rótulos visíveis**

Em `ModalConcederAcesso.tsx` e `ModalPermissaoUsuario.tsx`, o valor vira `'ACESSO_COMUM'` e o texto ao usuário vira:

```
título:    'Acesso comum'
descrição: 'Vê pessoas e eventos, e se inscreve. Sem gestão.'
```

Em `ModalPermissaoUsuario.tsx` o `label` hoje mostra o valor cru (`'ADMIN_IGREJA'`). Troque por texto legível: `'Administrador'`, `'Líder'`, `'Acesso comum'` — o usuário não deveria ler nome de constante.

- [ ] **Step 4: Verificar que sobrou zero**

Run (backend): `grep -rn '"MEMBRO"' src/main --include=*.java`
Expected: nenhuma linha.

Run (frontend): `grep -rn "'MEMBRO'" src --include=*.ts --include=*.tsx | grep -v useBuscaGlobal | grep -v BuscaGlobal`
Expected: nenhuma linha. (As duas exceções são o **tipo de resultado de busca**, não perfil.)

- [ ] **Step 5: Suíte + build**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`
Run: `cd frontend && npx tsc --noEmit; echo "EXIT=$?"` → `EXIT=0`

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor(seguranca): role MEMBRO vira ACESSO_COMUM"
```

---

### Task 6: Colapsar o toggle do evento

**Files:**
- Modify: `modules/evento/Evento.java`, `DTOs/EventoRequest.java`, `DTOs/EventoResponse.java`, `EventoService.java`
- Modify: `modules/evento/inscricao/InscricaoService.java`
- Modify: `frontend/src/components/module/eventos/EventoForm.tsx`, `src/lib/validators.ts`, `src/types/evento.type.ts`, `src/hooks/evento/useEventoForm.ts`

- [ ] **Step 1: Ajustar o teste primeiro**

Em `InscricaoServiceTest`, os testes `eventoExclusivoDeBatizadosRecusaNaoBatizado` e `eventoExclusivoDeMembrosRecusaVisitante` viram **um só**:

```java
    @Test
    void eventoExclusivoDeMembrosRecusaCongregante() {
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        dado(e, pessoa(Vinculo.CONGREGANTE), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exclusivo para membros");
    }

    @Test
    void eventoExclusivoDeMembrosAceitaMembro() {
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        dado(e, pessoa(Vinculo.MEMBRO), 0);

        service.inscrever(eventoId, pessoaId, null, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }
```

Ajuste o helper `pessoa(...)` para receber `Vinculo` em vez de `(boolean batizado, StatusMembro status)`.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL — `setExclusivoBatizados` ainda existe / helper com assinatura antiga.

- [ ] **Step 3: Remover o campo**

Em `Evento.java`, apague `exclusivoBatizados` (a coluna já não existe na V1). Idem em `EventoRequest`, `EventoResponse` e nos dois pontos de `EventoService` que o gravavam.

Em `InscricaoService.validarElegibilidade`:

```java
    private void validarElegibilidade(Evento evento, Pessoa pessoa) {
        // Um toggle só: no modelo de vínculo, "só batizados" e "só membros" são a MESMA
        // pergunta — MEMBRO significa batizado. E não há "inativo" a barrar: quem parou
        // de frequentar está arquivado e não aparece em lista nenhuma.
        if (evento.isExclusivoMembros() && pessoa.getVinculo() != Vinculo.MEMBRO) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros da igreja.");
        }
    }
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`

- [ ] **Step 5: Frontend**

Tire `exclusivoBatizados` do tipo, do schema Zod, do payload e do formulário. **O aviso fixo continua**, agora sob o toggle único:

> Congregantes não poderão se inscrever nem ser inscritos. Apenas membros batizados.

- [ ] **Step 6: Verificar**

Run: `cd frontend && npx tsc --noEmit && npx next build; echo "EXIT=$?"` → `EXIT=0`

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(evento): um toggle de exclusividade em vez de dois"
```

---

### Task 7: Consolidado por vínculo

**Files:**
- Modify: `modules/igreja/familia/consolidado/ConsolidadoService.java`
- Modify: `modules/igreja/familia/consolidado/DTO/ConsolidadoResponse.java:16`
- Modify: a consulta de contagem no repository correspondente
- Modify: `frontend/src/app/(app)/financeiro/relatorios/` (aba Congregações)

**Interfaces:**
- Produces: `ConsolidadoResponse.Pessoas(long total, long membros, long congregantes)` — substitui `Membros(total, ativos, inativos, visitantes)`.

- [ ] **Step 1: Teste**

```java
    @Test
    void consolidadoSeparaMembrosDeCongregantes() {
        // 3 membros + 2 congregantes numa congregação
        var resultado = service.consolidar(sedeId, periodo);
        var linha = resultado.congregacoes().get(0);

        assertThat(linha.pessoas().membros()).isEqualTo(3);
        assertThat(linha.pessoas().congregantes()).isEqualTo(2);
        assertThat(linha.pessoas().total()).isEqualTo(5);
    }
```

Adapte à forma real do serviço (leia-o antes: hoje ele agrega por `StatusMembro.ordinal()`, o que **não sobrevive** à troca de enum — o `ordinal()` de um enum de 3 valores para um de 2 quebra silenciosamente se copiado).

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=*Consolidado*`

- [ ] **Step 3: Implementar**

Troque o record e a agregação. **Não use `ordinal()`** — use o próprio enum como chave (`EnumMap<Vinculo, Long>`), que não quebra quando alguém adicionar um vínculo novo.

- [ ] **Step 4: Frontend da aba Congregações**

As colunas "Ativos / Inativos / Visitantes" viram **"Membros / Congregantes"**, e o total continua. Era o pedido explícito do autor: cada congregação precisa mostrar os dois números separados.

- [ ] **Step 5: Verificar e commitar**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`
Run: `cd frontend && npx tsc --noEmit && npx next build; echo "EXIT=$?"` → `EXIT=0`

```bash
git add -A
git commit -m "feat(consolidado): contagem separada de membros e congregantes por igreja"
```

---

### Task 8: Renomear no frontend

**Files:**
- Rename: `src/app/(app)/membros/` → `src/app/(app)/pessoas/`
- Rename: `src/components/module/membros/` → `src/components/module/pessoas/`
- Rename: `src/hooks/membro/` → `src/hooks/pessoa/`, `src/services/membro.service.ts` → `pessoa.service.ts`, `src/types/membro.type.ts` → `pessoa.type.ts`, `src/lib/formats/membroFormat.ts` → `pessoaFormat.ts`
- Modify: `src/lib/endpoints.ts`, `src/lib/cacheInvalidacao.ts`, `src/components/layout/Sidebar.tsx`

- [ ] **Step 1: Mover os arquivos**

```bash
cd frontend/src
git mv "app/(app)/membros" "app/(app)/pessoas"
git mv components/module/membros components/module/pessoas
git mv hooks/membro hooks/pessoa
git mv services/membro.service.ts services/pessoa.service.ts
git mv types/membro.type.ts types/pessoa.type.ts
git mv lib/formats/membroFormat.ts lib/formats/pessoaFormat.ts
```

- [ ] **Step 2: Ajustar conteúdo**

Mesmas substituições da Task 4, adaptadas: `MembroResponse` → `PessoaResponse`, `useMembros` → `usePessoas`, `membroId` → `pessoaId`, `/membros` → `/pessoas`, `['membros']` → `['pessoas']` no `cacheInvalidacao.ts`.

⚠️ **`cacheInvalidacao.ts` tem uma armadilha conhecida:** a invalidação é por prefixo, e `['pessoas']` **não** cobre `['pessoa', id]`. Ao renomear, garanta que as duas chaves estão no mapa — foi exatamente esse bug (com evento) que fez o detalhe nunca atualizar.

- [ ] **Step 3: Rótulos visíveis**

- Sidebar: "Membros" → **"Pessoas"**
- Títulos, breadcrumbs e estados vazios: "membro" → "pessoa" onde fala do **cadastro**; onde fala do **vínculo** ("exclusivo para membros"), fica.
- Formulário: o campo de status vira **Vínculo**, com as opções "Membro" e "Congregante".
- Data de batismo: aparece **só** quando o vínculo é Membro (o campo `batizado` sumiu; quem controla agora é o vínculo).

- [ ] **Step 4: Verificar**

Run: `cd frontend && npx tsc --noEmit && npx eslint src && npx next build; echo "EXIT=$?"`
Expected: `tsc` limpo, eslint com os 5 warnings conhecidos, build ok.

Navegue em `/pessoas` e confirme que a rota antiga `/membros` dá 404 (não há redirect: não há usuário externo com link salvo).

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(front): membros vira pessoas"
```

---

### Task 9: Filtro por vínculo

**Files:**
- Create: `frontend/src/components/common/PainelFiltros/PainelFiltros.tsx` (+ CSS)
- Modify: `frontend/src/app/(app)/pessoas/page.tsx`
- Modify: `frontend/src/app/(app)/financeiro/relatorios/page.tsx`
- Modify: `modules/pessoa/PessoaController.java` + `PessoaService` + `PessoaRepository` (parâmetro `vinculo`)

**Interfaces:**
- Consumes: `Vinculo` (Task 4)
- Produces: `GET /pessoas?vinculo=MEMBRO|CONGREGANTE`; componente `<PainelFiltros>` reutilizável.

- [ ] **Step 1: Backend — filtro opcional**

Acrescente `vinculo` (nulável) à consulta paginada, no mesmo estilo do `q` atual:

```java
          AND (:vinculo IS NULL OR p.vinculo = :vinculo)
```

⚠️ **A chave do cache precisa incluir o filtro.** `CacheKeys.pessoas(...)` já recebe `q` e `pageable` e — desde a mudança de privacidade — o flag de dados sensíveis. Adicione `vinculo`: sem isso, a lista filtrada por MEMBRO seria servida a quem pediu CONGREGANTE.

Teste: filtrar por MEMBRO devolve só membros; sem filtro devolve todos.

- [ ] **Step 2: O painel de filtros**

Botão "Filtros" com ícone, ao lado da busca (padrão do protótipo enviado pelo autor), que abre um painel com as opções. Comece com **um** grupo — Vínculo (Membros / Congregantes) — mas receba os grupos por prop, para relatórios e outras telas reusarem sem duplicar o componente.

Mobile: o painel vira folha inferior (bottom sheet) em vez de popover, para não estourar a largura.

- [ ] **Step 3: Ligar na tela de pessoas**

O filtro entra na `queryKey` (senão o TanStack devolve a lista errada em cache) e na URL, para o filtro sobreviver ao refresh e ser compartilhável.

- [ ] **Step 4: Relatório financeiro**

Filtrar contribuições por vínculo de quem contribuiu. Reusar `<PainelFiltros>`.

- [ ] **Step 5: Verificar e commitar**

Run: `mvn -q test; echo "EXIT=$?"` → `EXIT=0`
Run: `cd frontend && npx tsc --noEmit && npx next build; echo "EXIT=$?"` → `EXIT=0`

```bash
git add -A
git commit -m "feat(pessoas): filtro por vinculo em listas e relatorios"
```

---

### Task 10: Elasticsearch

**Files:**
- Modify: `modules/pessoa/busca/PessoaDocument.java` (`indexName`)
- Modify: `shared/busca/BuscaController.java` (`/busca/membros` → `/busca/pessoas`)
- Modify: `modules/outbox/TipoEntidadeOutbox.java`
- Modify: `frontend/src/hooks/busca/useBuscaGlobal.ts`, `components/layout/busca/BuscaGlobal.tsx`

- [ ] **Step 1: Renomear o índice**

`@Document(indexName = "pessoas")`. O índice `membros` fica órfão — apague depois de confirmar que o novo funciona.

- [ ] **Step 2: Tipo do resultado de busca**

`TipoEntidadeOutbox.MEMBRO` → `PESSOA`, e no front `tipo: 'PESSOA' | 'EVENTO' | ...` com o rótulo visível "Pessoa".

- [ ] **Step 3: Reindexar**

Suba a aplicação e chame `POST /admin/reindexacao` (ADMIN_IGREJA).
Confirme: buscar o nome de uma pessoa conhecida na busca global devolve resultado do tipo Pessoa.

- [ ] **Step 4: Apagar o índice velho**

Só depois de o novo responder. Registre no relatório qual comando usou.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor(busca): indice pessoas substitui membros"
```

---

### Task 11: Produção

**⚠️ Esta task derruba o ambiente de produção. Só execute com o autor ciente e presente.**

- [ ] **Step 1: Dump de produção**

```bash
pg_dump "$DATABASE_URL_PROD" -Fc -f ~/domus-backup-pre-pessoa/prod-$(date +%F).dump
```
**Não prossiga sem isto.**

- [ ] **Step 2: Zerar o schema de produção**

```bash
psql "$DATABASE_URL_PROD" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
```

- [ ] **Step 3: Deploy**

Siga o procedimento de deploy do projeto (ver memória `producao-no-ar-deploy` — front e back sobem juntos; `API_INTERNAL_URL` é build-time).

- [ ] **Step 4: Conferir no ar**

- Flyway aplicou a V1 (log do container)
- `/login` responde 200
- Cadastrar uma igreja nova pelo fluxo público funciona e cria o primeiro usuário
- O usuário criado tem role `ADMIN_IGREJA`
- Criar uma pessoa, marcar vínculo Membro, e o campo de data de batismo aparecer

- [ ] **Step 5: Reindexar produção**

`POST /admin/reindexacao` no ambiente de produção.

- [ ] **Step 6: Confirmar o backup**

O workflow diário roda às 03:00 BRT. No dia seguinte, confirmar no Sentry Crons que o check-in passou — o schema mudou e o script precisa continuar funcionando.

---

### Task 12: Documentação

**Files:**
- Modify: `CLAUDE.md` (diagrama ER, convenções, roadmap)
- Modify: `docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`

- [ ] **Step 1: Diagrama ER**

`MEMBRO` vira `PESSOA` com `vinculo`; some `batizado`; `EVENTO` perde `exclusivo_batizados`; FKs viram `pessoa_id`. **Estado atual: V1** (não V17 — as migrations foram consolidadas).

- [ ] **Step 2: Convenções**

A regra "todo usuário está vinculado a exatamente um membro" vira **"todo usuário está vinculado a exatamente uma pessoa"**, e acrescente: *"`MEMBRO` é um VÍNCULO (batizado), não o cadastro. O cadastro é `pessoa`."*

Atualize a lista de perfis: `ADMIN_IGREJA`, `LIDER`, `ACESSO_COMUM`.

- [ ] **Step 3: Registrar a consolidação das migrations**

Uma nota curta explicando que V1 consolida as antigas V1–V16 em 2026-07-21, e que backups anteriores a essa data não restauram contra o código atual.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: pessoa/vinculo no diagrama ER e nas convencoes"
```

---

## Self-Review

**Cobertura da spec:** modelo `pessoa`/`vinculo` → Tasks 3, 4; nomes → Tasks 4, 5, 8; camada de permissões → Tasks 1, 2; reset das migrations → Tasks 3, 11; toggle do evento → Task 6; consolidado → Task 7; filtros → Task 9; Elasticsearch → Task 10; docs → Task 12.

**Riscos, em ordem:**

1. **Omissão, não erro.** 672 ocorrências no backend. A substituição mecânica acerta a maioria e erra onde "membro" significa **vínculo** — e aí o texto fica certo por acidente ou errado em silêncio. Toda task de rename exige **ler o diff**, não só compilar.
2. **`ordinal()` no consolidado** (Task 7). O código agrega por `StatusMembro.ordinal()` num enum de 3 valores. Copiado para um enum de 2, não quebra a compilação — **quebra a contagem**.
3. **Chaves de cache** (Tasks 8, 9). Duas armadilhas já vividas: prefixo (`['pessoas']` não cobre `['pessoa', id]`) e dimensão faltando na chave do Redis (perfil, agora também vínculo).
4. **Task 11 é destrutiva.** Sem o dump do Step 1, não há volta.
5. **A Task 5 deve ser curta.** Se o rename da role exigir tocar muitos arquivos, a Task 1 não terminou o trabalho — volte e complete antes de seguir.
