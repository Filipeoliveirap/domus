# Endereço estruturado do membro — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Trocar o `membro.endereco` (texto livre) por 7 colunas estruturadas, com auto-preenchimento por CEP via ViaCEP no cadastro.

**Architecture:** No back, um `@Embeddable Endereco` agrupa as 7 colunas na própria tabela `membro` (sem JOIN); os DTOs ganham um `EnderecoDTO` aninhado. No front, o `MembroForm` troca o textarea por 7 campos e um hook chama a ViaCEP ao completar o CEP, preenchendo o resto.

**Tech Stack:** Java 21/Spring Boot, Flyway, JPA `@Embeddable`; Next.js 16, React Hook Form + Zod, fetch para a ViaCEP.

**Spec:** `backend/api/docs/superpowers/specs/2026-07-18-endereco-estruturado-design.md`

## Global Constraints

- Repositório único em `/home/jos-filipe-oliveira-pereira/Documents/domus`. Branch de trabalho `producao`, PR pra `main` com **merge commit**.
- **Sem trailer `Co-Authored-By`** em commits.
- `./mvnw` está quebrado — usar o `mvn` do sistema, de `backend/api`, com as envs do `.env` exportadas (`set -a && . <(sed 's/^\(EMAIL_FROM\)=\(.*\)$/\1="\2"/' ./.env) && set +a`).
- Testes de back: **Mockito puro**, sem `@SpringBootTest`.
- **Front NÃO tem runner de teste** — verificação é **manual no navegador** (documentada). Montar vitest fica no BACKLOG.
- **Migração LIMPA, destrutiva de propósito:** `DROP COLUMN endereco`. Prod está vazio; o dev perde dado de teste (aprovado como descartável). Sem migrar texto.
- **Tudo nulável.** Nenhum campo de endereço é obrigatório.
- `numero` é **texto** (`VARCHAR(20)`), não inteiro. `cep` guarda **8 dígitos limpos** (front tira a máscara). `uf` é `CHAR(2)`.
- Contrato JSON: `endereco` vira **objeto aninhado** (`{ cep, logradouro, numero, complemento, bairro, cidade, uf }`).
- **CSP:** o `next.config.ts` precisa liberar `https://viacep.com.br` no `connect-src`, senão a chamada é bloqueada em silêncio.

---

### Task 1: Migration V11 + `Endereco` embeddable no `Membro`

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V11__endereco_estruturado.sql`
- Create: `backend/api/src/main/java/com/domus/api/modules/membro/Endereco.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/membro/Membro.java:46-47`

**Interfaces:**
- Produces: `Endereco` (POJO `@Embeddable` com getters/setters e `@Builder`) e `Membro.getEndereco()`/`setEndereco()`/`.endereco(...)` (builder) agora retornando/aceitando `Endereco` em vez de `String`.

- [ ] **Step 1: Criar a migration**

Create `backend/api/src/main/resources/db/migration/V11__endereco_estruturado.sql`:

```sql
-- Troca o endereco de texto livre por colunas estruturadas.
-- Destrutivo de propósito: prod está vazio; o dado de dev é descartável (não se migra texto).
ALTER TABLE membro
  DROP COLUMN endereco,
  ADD COLUMN cep         VARCHAR(9),
  ADD COLUMN logradouro  VARCHAR(255),
  ADD COLUMN numero      VARCHAR(20),
  ADD COLUMN complemento VARCHAR(255),
  ADD COLUMN bairro      VARCHAR(255),
  ADD COLUMN cidade      VARCHAR(255),
  ADD COLUMN uf          CHAR(2);
```

- [ ] **Step 2: Criar o `Endereco` embeddable**

Create `backend/api/src/main/java/com/domus/api/modules/membro/Endereco.java`:

```java
package com.domus.api.modules.membro;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Endereço do membro como objeto de valor. É {@code @Embeddable}: as 7 colunas vivem na
 * própria tabela {@code membro} (sem JOIN), mas o código trata endereço como um conceito só.
 * Tudo nulável — um membro pode ter endereço parcial ou nenhum.
 */
@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Endereco {

    @Column(name = "cep", length = 9)
    private String cep;

    @Column(name = "logradouro", length = 255)
    private String logradouro;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "complemento", length = 255)
    private String complemento;

    @Column(name = "bairro", length = 255)
    private String bairro;

    @Column(name = "cidade", length = 255)
    private String cidade;

    @Column(name = "uf", length = 2)
    private String uf;
}
```

- [ ] **Step 3: Trocar o campo no `Membro`**

Em `backend/api/src/main/java/com/domus/api/modules/membro/Membro.java`, substituir as linhas 46-47:

```java
    @Column(name = "endereco", length = 500)
    private String endereco;
```

por:

```java
    @Embedded
    private Endereco endereco;
```

E adicionar o import junto dos outros de `jakarta.persistence`:

```java
import jakarta.persistence.Embedded;
```

- [ ] **Step 4: Compilar (o Membro ainda vai quebrar consumidores — esperado)**

```bash
cd backend/api && mvn -o -q compile 2>&1 | grep -E "endereco|ERROR|BUILD" | head
```
Expected: erros de compilação em `MembroService` e `MembroDocument` (esperam `String`, agora recebem `Endereco`). São corrigidos na Task 2 — este passo só confirma que o `Endereco`/`Membro` em si compilam e que os pontos a mexer são exatamente esses.

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/resources/db/migration/V11__endereco_estruturado.sql \
        backend/api/src/main/java/com/domus/api/modules/membro/Endereco.java \
        backend/api/src/main/java/com/domus/api/modules/membro/Membro.java
git commit -m "feat(membro): endereco estruturado no banco e na entidade (V11)

Troca o endereco (texto livre VARCHAR 500) por 7 colunas na tabela membro
(cep, logradouro, numero, complemento, bairro, cidade, uf), agrupadas num
@Embeddable Endereco. Migração destrutiva de propósito: prod está vazio e
o dado de dev é descartável. Consumidores (service, doc) na Task 2."
```

---

### Task 2: `EnderecoDTO` + DTOs + mapeamento no service + limpeza do ES doc

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/membro/DTO/EnderecoDTO.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/membro/DTO/MembroRequestDTO.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/membro/DTO/MembroResponse.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/membro/MembroService.java:74,122`
- Modify: `backend/api/src/main/java/com/domus/api/modules/membro/busca/MembroDocument.java:34,49`
- Modify: `backend/api/src/main/java/com/domus/api/modules/membro/busca/BuscaMembroService.java:41,49`
- Test: `backend/api/src/test/java/com/domus/api/modules/membro/EnderecoMapeamentoTest.java`

**Interfaces:**
- Consumes: `Endereco` (Task 1).
- Produces: `EnderecoDTO(String cep, logradouro, numero, complemento, bairro, cidade, uf)`; `MembroResponse.from(Membro)` mapeando o `Endereco` para `EnderecoDTO`; `MembroRequestDTO.endereco()` agora `EnderecoDTO`.

- [ ] **Step 1: Write the failing test**

Create `backend/api/src/test/java/com/domus/api/modules/membro/EnderecoMapeamentoTest.java`:

```java
package com.domus.api.modules.membro;

import com.domus.api.modules.membro.DTO.EnderecoDTO;
import com.domus.api.modules.membro.DTO.MembroResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnderecoMapeamentoTest {

    @Test
    void membroResponseFrom_mapeiaOEnderecoAninhado() {
        Endereco endereco = Endereco.builder()
                .cep("01001000").logradouro("Praça da Sé").numero("100")
                .complemento("lado ímpar").bairro("Sé").cidade("São Paulo").uf("SP")
                .build();
        Membro membro = Membro.builder().nome("Ana").endereco(endereco).build();

        MembroResponse resp = MembroResponse.from(membro);

        assertNotNull(resp.endereco());
        assertEquals("01001000", resp.endereco().cep());
        assertEquals("Praça da Sé", resp.endereco().logradouro());
        assertEquals("100", resp.endereco().numero());
        assertEquals("Sé", resp.endereco().bairro());
        assertEquals("São Paulo", resp.endereco().cidade());
        assertEquals("SP", resp.endereco().uf());
    }

    @Test
    void membroResponseFrom_toleraEnderecoNulo() {
        Membro membro = Membro.builder().nome("Bia").endereco(null).build();

        MembroResponse resp = MembroResponse.from(membro);

        assertNull(resp.endereco());
    }
}
```

- [ ] **Step 2: Run — deve falhar (não compila)**

```bash
cd backend/api && mvn -o -q test -Dtest=EnderecoMapeamentoTest 2>&1 | grep -E "cannot find symbol|EnderecoDTO|BUILD" | head
```
Expected: FAIL de compilação — `EnderecoDTO` não existe e `resp.endereco()` ainda é `String`.

- [ ] **Step 3: Criar o `EnderecoDTO`**

Create `backend/api/src/main/java/com/domus/api/modules/membro/DTO/EnderecoDTO.java`:

```java
package com.domus.api.modules.membro.DTO;

import jakarta.validation.constraints.Size;

/**
 * Endereço no contrato da API — aninhado no membro. Tudo opcional; a validação de formato
 * fica no front (Zod). A única regra defensiva no back é o tamanho da UF.
 */
public record EnderecoDTO(
        @Size(max = 9) String cep,
        @Size(max = 255) String logradouro,
        @Size(max = 20) String numero,
        @Size(max = 255) String complemento,
        @Size(max = 255) String bairro,
        @Size(max = 255) String cidade,
        @Size(max = 2, message = "UF deve ter 2 letras") String uf
) {}
```

- [ ] **Step 4: Trocar o campo no `MembroRequestDTO`**

Em `MembroRequestDTO.java`, remover o import de validação não usado se sobrar, e substituir:

```java
        @Size(max = 500, message = "O endereço deve ter no máximo 500 caracteres")
        String endereco,
```

por:

```java
        @jakarta.validation.Valid
        EnderecoDTO endereco,
```

(O `@Valid` faz o Bean Validation descer no objeto aninhado — ex.: o `@Size` do `uf`.)

- [ ] **Step 5: Trocar no `MembroResponse` (campo + `from`)**

Em `MembroResponse.java`:
- trocar `String endereco,` por `EnderecoDTO endereco,` na lista do record;
- no `from(Membro m)`, trocar `m.getEndereco()` por um mapeamento que tolera nulo:

```java
    public static MembroResponse from(Membro m) {
        return new MembroResponse(
                m.getId(), m.getNome(), m.getEmail(), m.getTelefone(),
                m.getDataNascimento(), enderecoDe(m.getEndereco()), m.getStatus(),
                m.getEstadoCivil(), m.getMinisterio(), m.getFoto(),
                m.getObservacoes(), m.getCreatedAt()
        );
    }

    private static EnderecoDTO enderecoDe(com.domus.api.modules.membro.Endereco e) {
        if (e == null) return null;
        return new EnderecoDTO(e.getCep(), e.getLogradouro(), e.getNumero(),
                e.getComplemento(), e.getBairro(), e.getCidade(), e.getUf());
    }
```

- [ ] **Step 6: Mapear no `MembroService` (criar e atualizar)**

Em `MembroService.java`, no builder do `criar` (~linha 74), trocar `.endereco(data.endereco())` por:

```java
                .endereco(paraEndereco(data.endereco()))
```

No `atualizar` (~linha 122), trocar `membro.setEndereco(data.endereco());` por:

```java
        membro.setEndereco(paraEndereco(data.endereco()));
```

E adicionar o método privado (perto do fim da classe) + o import:

```java
    private Endereco paraEndereco(EnderecoDTO dto) {
        if (dto == null) return null;
        return Endereco.builder()
                .cep(dto.cep()).logradouro(dto.logradouro()).numero(dto.numero())
                .complemento(dto.complemento()).bairro(dto.bairro())
                .cidade(dto.cidade()).uf(dto.uf())
                .build();
    }
```
Imports: `import com.domus.api.modules.membro.Endereco;` e `import com.domus.api.modules.membro.DTO.EnderecoDTO;` (se não estiverem).

- [ ] **Step 7: Remover o `endereco` do `MembroDocument`**

Em `MembroDocument.java`:
- apagar o campo (linha ~34): `private String endereco;` e sua anotação `@Field(...)` logo acima;
- apagar a linha (~49): `doc.setEndereco(membro.getEndereco());`.

O endereço sai da busca global (era string livre e quase não ajudava; indexar bairro/cidade fica no BACKLOG).

- [ ] **Step 7b: Tirar `"endereco"` da query de busca**

O `BuscaMembroService` lista `"endereco"` entre os campos pesquisados do Elasticsearch (linhas
~41 e ~49). O campo não existe mais no documento — não quebra em runtime (o ES ignora campo
inexistente), mas é referência morta. Em `BuscaMembroService.java`, remover `"endereco"` das
duas chamadas `.fields(...)`:

```java
// linha ~41
                        .fields("email^2", "ministerio")
// linha ~49
                        .fields("nome^3", "email^2", "telefone", "ministerio")
```

- [ ] **Step 8: Run — deve passar, e a suíte inteira também**

```bash
cd backend/api && set -a && . <(sed 's/^\(EMAIL_FROM\)=\(.*\)$/\1="\2"/' ./.env) && set +a && mvn -o test 2>&1 | grep -E "Tests run: [0-9]+, Fail|BUILD"
```
Expected: PASS — 45 + 2 novos = 47 testes, 0 falhas, BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/src/main/java/com/domus/api/modules/membro/
git commit -m "feat(membro): DTO de endereco aninhado + mapeamento no service

EnderecoDTO aninhado em MembroRequestDTO/MembroResponse; o service mapeia
EnderecoDTO <-> Endereco (tolera nulo). MembroDocument perde o campo
endereco e o BuscaMembroService para de listá-lo nos campos de busca
(indexar bairro/cidade fica no backlog). Testa o mapeamento e o nulo."
```

---

### Task 3: Front — CSP, types e validators

**Files:**
- Modify: `frontend/next.config.ts` (linha do `connect-src`)
- Modify: `frontend/src/types/membro.type.ts`
- Modify: `frontend/src/lib/validators.ts`

**Interfaces:**
- Produces: tipo `Endereco` no front; `MembroRequest.endereco?: Endereco` e `MembroResponse.endereco: Endereco | null`; `membroSchema` com `endereco` como objeto de 7 campos opcionais.

- [ ] **Step 1: Liberar a ViaCEP na CSP**

Em `frontend/next.config.ts`, na linha do `connect-src`:

```ts
  "connect-src 'self' https://accounts.google.com https://*.sentry.io",
```

trocar por:

```ts
  "connect-src 'self' https://accounts.google.com https://*.sentry.io https://viacep.com.br",
```

- [ ] **Step 2: Tipos do endereço**

Em `frontend/src/types/membro.type.ts`, adicionar o tipo `Endereco` (depois dos `type` do topo) e trocar o campo nas duas interfaces:

```ts
export interface Endereco {
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
}
```

Em `MembroRequest`: trocar `endereco?: string` por `endereco?: Endereco`.
Em `MembroResponse`: trocar `endereco: string | null` por `endereco: Endereco | null`.

- [ ] **Step 3: Schema do endereço no validator**

Em `frontend/src/lib/validators.ts`, trocar o bloco `endereco` (linhas ~67-69):

```ts
  endereco: opcional(
    z.string().max(500, 'O endereço deve ter no máximo 500 caracteres'),
  ),
```

por um objeto de campos opcionais (usa o mesmo helper `opcional`):

```ts
  endereco: z.object({
    cep: opcional(z.string().regex(/^\d{8}$/, 'CEP deve ter 8 dígitos')),
    logradouro: opcional(z.string().max(255)),
    numero: opcional(z.string().max(20)),
    complemento: opcional(z.string().max(255)),
    bairro: opcional(z.string().max(255)),
    cidade: opcional(z.string().max(255)),
    uf: opcional(z.string().length(2, 'UF deve ter 2 letras')),
  }).optional(),
```

- [ ] **Step 4: Typecheck**

```bash
cd frontend && rm -rf .next && npx tsc --noEmit 2>&1 | head -20; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: **vai acusar erros** em `MembroForm.tsx` e nos hooks (ainda usam `endereco` como string) — esperado, corrigidos nas Tasks 4 e 5. Confirme que os erros são **só** nesses arquivos de membro, não em outros.

- [ ] **Step 5: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/next.config.ts frontend/src/types/membro.type.ts frontend/src/lib/validators.ts
git commit -m "feat(front): tipos e schema do endereco aninhado + CSP libera ViaCEP

endereco vira objeto (cep, logradouro, numero, complemento, bairro,
cidade, uf), todos opcionais. A CSP passa a permitir viacep.com.br no
connect-src — senão a chamada do auto-preenchimento seria bloqueada em
silêncio. Form e hooks na Task 5."
```

---

### Task 4: Front — hook de auto-preenchimento da ViaCEP

**Files:**
- Create: `frontend/src/hooks/membro/useBuscaCep.ts`

**Interfaces:**
- Produces: `useBuscaCep()` → `{ buscar: (cep: string) => Promise<Endereco | null>, carregando: boolean }`. `buscar` recebe o CEP (8 dígitos), retorna os campos que a ViaCEP conhece (logradouro, bairro, cidade, uf) ou `null` se não achou / deu erro. **Nunca lança** — erro vira `null`.

- [ ] **Step 1: Criar o hook**

Create `frontend/src/hooks/membro/useBuscaCep.ts`:

```ts
import { useState } from 'react'
import type { Endereco } from '@/types/membro.type'

// Resposta da ViaCEP (só os campos que usamos). `erro: true` quando o CEP não existe.
interface ViaCepResposta {
  logradouro?: string
  bairro?: string
  localidade?: string // = cidade
  uf?: string
  erro?: boolean
}

/**
 * Auto-preenchimento por CEP via ViaCEP (pública, grátis, sem chave, com CORS).
 *
 * `buscar` NUNCA lança: CEP inexistente, ViaCEP fora do ar ou erro de rede viram `null`.
 * O CEP é conveniência — a pessoa sempre consegue preencher o endereço na mão.
 */
export function useBuscaCep() {
  const [carregando, setCarregando] = useState(false)

  async function buscar(cep: string): Promise<Endereco | null> {
    const limpo = cep.replace(/\D/g, '')
    if (limpo.length !== 8) return null

    setCarregando(true)
    try {
      const resp = await fetch(`https://viacep.com.br/ws/${limpo}/json/`)
      if (!resp.ok) return null
      const data: ViaCepResposta = await resp.json()
      if (data.erro) return null
      return {
        cep: limpo,
        logradouro: data.logradouro ?? '',
        bairro: data.bairro ?? '',
        cidade: data.localidade ?? '',
        uf: data.uf ?? '',
      }
    } catch {
      return null // rede caiu, ViaCEP fora do ar — não trava o form
    } finally {
      setCarregando(false)
    }
  }

  return { buscar, carregando }
}
```

- [ ] **Step 2: Verificação — o endpoint da ViaCEP responde o esperado**

Sem runner de teste no front, valida-se o **contrato da ViaCEP** direto (é o que o hook consome):

```bash
echo "--- CEP válido (deve trazer logradouro/bairro/localidade/uf) ---"
curl -s "https://viacep.com.br/ws/01001000/json/" | python3 -m json.tool | grep -E "logradouro|bairro|localidade|uf"
echo "--- CEP inexistente (deve trazer \"erro\": true) ---"
curl -s "https://viacep.com.br/ws/99999999/json/" | python3 -m json.tool | grep -i erro
```
Expected: o primeiro traz `"localidade": "São Paulo"`, `"uf": "SP"`, etc.; o segundo traz `"erro": true`. Confirma que os campos que o hook lê (`localidade`, `uf`, `erro`) são esses.

- [ ] **Step 3: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/hooks/membro/useBuscaCep.ts
git commit -m "feat(front): hook useBuscaCep (auto-preenchimento via ViaCEP)

buscar(cep) chama a ViaCEP e devolve logradouro/bairro/cidade/uf, ou null
se o CEP não existe, a ViaCEP cai ou a rede falha — nunca lança. O CEP é
conveniência: a pessoa sempre pode preencher na mão."
```

---

### Task 5: Front — `MembroForm` com os 7 campos e o auto-preenchimento

**Files:**
- Modify: `frontend/src/components/module/membros/MembroForm.tsx:80-83`
- Modify (se necessário): `frontend/src/hooks/membro/useMembroForm.ts`, `frontend/src/hooks/membro/useCadastrarMembro.ts` (defaultValues/submit do endereço)

**Interfaces:**
- Consumes: `useBuscaCep` (Task 4); `membroSchema`/`Endereco` (Task 3).

- [ ] **Step 1: Trocar o textarea pelos 7 campos**

Em `MembroForm.tsx`, substituir o bloco do endereço (linhas ~80-83, o `<label>ENDEREÇO</label>` + `<textarea>`) por um bloco com CEP + 6 campos, registrados no caminho aninhado `endereco.*`:

```tsx
            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="cep">CEP</label>
              <input
                id="cep"
                className={styles.input}
                placeholder="00000-000"
                inputMode="numeric"
                {...register('endereco.cep')}
                onBlur={aoSairDoCep}
              />
              {carregandoCep && <span className={styles.dica}>buscando CEP…</span>}
              {cepNaoEncontrado && (
                <span className={styles.dica}>CEP não encontrado — preencha manualmente.</span>
              )}
            </div>

            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="logradouro">LOGRADOURO</label>
              <input id="logradouro" className={styles.input} {...register('endereco.logradouro')} />
            </div>
            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="numero">NÚMERO</label>
              <input id="numero" className={styles.input} placeholder="123, s/n…" {...register('endereco.numero')} />
            </div>
            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="complemento">COMPLEMENTO</label>
              <input id="complemento" className={styles.input} {...register('endereco.complemento')} />
            </div>
            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="bairro">BAIRRO</label>
              <input id="bairro" className={styles.input} {...register('endereco.bairro')} />
            </div>
            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="cidade">CIDADE</label>
              <input id="cidade" className={styles.input} {...register('endereco.cidade')} />
            </div>
            <div className={styles.grupo}>
              <label className={styles.label} htmlFor="uf">UF</label>
              <input id="uf" className={styles.input} maxLength={2} placeholder="SP" {...register('endereco.uf')} />
            </div>
```

(Usa as classes `styles.grupo`/`styles.label`/`styles.input`/`styles.dica` no padrão dos outros campos do form; se `styles.dica` não existir, reusar `styles.erroCampo` ou adicionar uma classe simples no CSS module.)

- [ ] **Step 2: Ligar o auto-preenchimento**

No topo do componente `MembroForm`, obter `setValue` do form (React Hook Form) e o hook:

```tsx
import { useState } from 'react'
import { useBuscaCep } from '@/hooks/membro/useBuscaCep'
// ...
  const { buscar, carregando: carregandoCep } = useBuscaCep()
  const [cepNaoEncontrado, setCepNaoEncontrado] = useState(false)

  async function aoSairDoCep(e: React.FocusEvent<HTMLInputElement>) {
    setCepNaoEncontrado(false)
    const achado = await buscar(e.target.value)
    if (!achado) {
      // só sinaliza "não encontrado" se a pessoa digitou um CEP completo
      if (e.target.value.replace(/\D/g, '').length === 8) setCepNaoEncontrado(true)
      return
    }
    // preenche o que a ViaCEP sabe; numero/complemento a pessoa completa
    if (achado.logradouro) setValue('endereco.logradouro', achado.logradouro)
    if (achado.bairro) setValue('endereco.bairro', achado.bairro)
    if (achado.cidade) setValue('endereco.cidade', achado.cidade)
    if (achado.uf) setValue('endereco.uf', achado.uf)
  }
```

Garantir que `setValue` venha do mesmo `useForm`/`useMembroForm` que já provê `register` (adicionar `setValue` ao destructuring onde `register` é obtido).

- [ ] **Step 3: Ajustar defaultValues (evita `undefined` nos inputs controlados)**

Em `useMembroForm.ts` (ou onde `defaultValues` do form é montado), garantir que o endereço tem um objeto default — na criação:

```ts
      endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
```

E, na **edição** (quando carrega um membro existente), mapear `membro.endereco` (que pode ser `null`) para esse objeto, caindo em strings vazias quando nulo. Se o form usa um `reset(membro)`, adaptar para `endereco: membro.endereco ?? { cep:'', ... }`.

- [ ] **Step 4: Typecheck + build**

```bash
cd frontend && rm -rf .next && npx tsc --noEmit && echo "tsc OK" && npm run build > /tmp/build-endereco.log 2>&1; echo "build EXIT=$?"
```
Expected: `tsc OK` e build exit 0.

- [ ] **Step 5: Verificação MANUAL no navegador (o front não tem runner)**

Subir o dev (`npm run dev`, backend local de pé) e, na tela de cadastrar membro:

1. **CEP válido** (`01001000`) → sair do campo → `logradouro`, `bairro`, `cidade`, `uf` **preenchem sozinhos**; `numero`/`complemento` ficam pra digitar.
2. **CEP inexistente** (`99999999`) → aparece "CEP não encontrado — preencha manualmente"; o form **não trava**, dá pra digitar tudo na mão.
3. **CEP incompleto** (`0100`) → **não** dispara busca, sem erro.
4. **Salvar** um membro com endereço completo → recarregar/editar → os 7 campos voltam preenchidos (prova o ida-e-volta com o back).
5. (Opcional, testar o "ViaCEP fora do ar": no DevTools → Network → bloquear `viacep.com.br` → digitar CEP → o form não trava.)

- [ ] **Step 6: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add frontend/src/components/module/membros/MembroForm.tsx \
        frontend/src/hooks/membro/useMembroForm.ts \
        frontend/src/hooks/membro/useCadastrarMembro.ts
git commit -m "feat(front): MembroForm com endereco estruturado + auto-preenchimento

Troca o textarea por 7 campos (cep, logradouro, numero, complemento,
bairro, cidade, uf). Ao sair do CEP, a ViaCEP preenche
logradouro/bairro/cidade/uf; numero/complemento a pessoa completa. CEP
inexistente ou ViaCEP fora do ar não travam — preenchimento manual sempre
funciona. Verificado manualmente no navegador (front sem runner de teste)."
```

---

### Task 6: Fechar — roadmap e backlog

**Files:**
- Modify: `backend/api/CLAUDE.md`
- Modify: `backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`

- [ ] **Step 1: Marcar no roadmap**

Em `backend/api/CLAUDE.md`, no item "Endereço estruturado" da Fase 2, marcar como **FEITO** (colunas estruturadas na tabela `membro`, `@Embeddable Endereco`, DTO aninhado, ViaCEP no cadastro; migração limpa; filtro por bairro adiado).

- [ ] **Step 2: Registrar os follow-ups no BACKLOG**

Em `backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md`:

```markdown
- **Filtro por bairro/cidade de membros.** As 7 colunas de endereço já existem
  (`membro.cep/logradouro/.../uf`, feito em 2026-07-18). Falta a query no back (parâmetro no
  `GET /membros`) e a UI de filtro no front. Adiado de propósito: só é útil quando houver
  dado real cadastrado. Fazer quando as igrejas tiverem membros e a necessidade aparecer.

- **Indexar bairro/cidade no Elasticsearch.** O `MembroDocument` deixou de indexar o endereço
  (era texto livre). Se a busca global por bairro/cidade virar demanda, adicionar esses campos
  ao documento e reindexar.

- **Runner de teste no front (vitest).** O frontend não tem testes automatizados; o
  auto-preenchimento da ViaCEP foi verificado só manualmente. Montar vitest + testing-library
  permitiria testar hooks (ex.: `useBuscaCep` nos 3 caminhos) sem abrir o navegador.
```

- [ ] **Step 3: Commit**

```bash
cd /home/jos-filipe-oliveira-pereira/Documents/domus
git add backend/api/docs/BACKLOG-DIVIDA-E-PROXIMO-SCOPE.md
git commit -m "docs(backlog): follow-ups do endereço estruturado

Filtro por bairro/cidade (colunas já existem), indexar bairro/cidade no
ES, e montar runner de teste no front (o auto-preenchimento foi só
verificado manualmente)."
```

---

## Verificação final

- [ ] `mvn -o test` → 47 testes, 0 falhas
- [ ] `tsc --noEmit` limpo e `npm run build` exit 0
- [ ] No navegador: CEP válido auto-preenche; CEP inexistente/ViaCEP fora do ar não travam; salvar e reabrir mantém os 7 campos
- [ ] `git status` limpo, nenhum segredo/`.env` commitado
- [ ] Roadmap e BACKLOG atualizados
