# Múltiplos contribuintes por movimentação financeira — Plano de implementação

> Spec: `docs/superpowers/specs/2026-07-29-multiplos-contribuintes-design.md`.
> Executar em pedaços testáveis (convenção do projeto): entregar cada task, esperar o
> autor testar, só então seguir pra próxima.

## Global Constraints

- Migration nova é `V15` (última hoje é `V14__evento_restrito_propria_igreja.sql`).
- `sum(contribuintes.valor)` deve ser **exatamente** igual a `valor` quando a lista não
  for vazia — código de erro `VALOR_CONTRIBUINTES_DIVERGENTE`.
- Pessoa duplicada na lista de contribuintes — código de erro `CONTRIBUINTE_DUPLICADO`.
- `movimentacao_contribuinte.pessoa_id` é `NOT NULL` (contribuinte sempre é uma pessoa
  cadastrada); lista vazia = anônimo (equivalente ao `pessoa_id IS NULL` de hoje).
- `ON DELETE CASCADE` de `movimentacao_id` — a linha de contribuinte não sobrevive à
  movimentação.
- Vale pros dois tipos (ENTRADA e SAÍDA).

---

### Task 1: Migração + entidade + repositório de contribuinte

**Files:**
- Create: `src/main/resources/db/migration/V15__movimentacao_contribuinte.sql`
- Create: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoContribuinte.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoContribuinteRepository.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceira.java`

**Interfaces:**
- Produz: `MovimentacaoContribuinte { id, movimentacao (ManyToOne), pessoa (ManyToOne), valor }`.
- Produz: `MovimentacaoFinanceira.contribuintes` (`@OneToMany(mappedBy="movimentacao", cascade=ALL, orphanRemoval=true)`) substitui o campo `pessoa` removido.

- [ ] **Passo 1: migração SQL**

```sql
CREATE TABLE movimentacao_contribuinte (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  movimentacao_id UUID NOT NULL REFERENCES movimentacao_financeira(id) ON DELETE CASCADE,
  pessoa_id       UUID NOT NULL REFERENCES pessoa(id),
  valor           NUMERIC(15,2) NOT NULL CHECK (valor > 0),
  UNIQUE (movimentacao_id, pessoa_id)
);

INSERT INTO movimentacao_contribuinte (movimentacao_id, pessoa_id, valor)
SELECT id, pessoa_id, valor
FROM movimentacao_financeira
WHERE pessoa_id IS NOT NULL;

ALTER TABLE movimentacao_financeira DROP COLUMN pessoa_id;
```

- [ ] **Passo 2: entidade `MovimentacaoContribuinte`**

```java
package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.modules.pessoa.Pessoa;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "movimentacao_contribuinte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoContribuinte {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movimentacao_id", nullable = false)
    private MovimentacaoFinanceira movimentacao;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;
}
```

- [ ] **Passo 3: repositório**

```java
package com.domus.api.modules.financeiro.movimentacao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MovimentacaoContribuinteRepository extends JpaRepository<MovimentacaoContribuinte, UUID> {
}
```

(Sem query methods próprios ainda — as queries agregadas ficam no `RelatorioRepository`
e `MovimentacaoFinanceiraRepository`, tasks seguintes.)

- [ ] **Passo 4: atualizar `MovimentacaoFinanceira`**

Remover:
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "pessoa_id")
private Pessoa pessoa;
```

Adicionar:
```java
@Builder.Default
@OneToMany(mappedBy = "movimentacao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<MovimentacaoContribuinte> contribuintes = new ArrayList<>();
```
(imports `java.util.ArrayList`, `java.util.List`, `jakarta.persistence.OneToMany`, `jakarta.persistence.CascadeType`)

- [ ] **Passo 5: rodar `mvn -q -o test -Dtest=MovimentacaoFinanceiraServiceTest`**

Vai falhar a compilar (o service ainda usa `.pessoa(...)` no builder) — confirma que o
passo 2 da Task 2 é obrigatório antes de fechar; não commitar ainda.

---

### Task 2: DTOs + validação de rateio + service (CRUD)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/DTOs/MovimentacaoRequestDTO.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/DTOs/MovimentacaoResponse.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/movimentacao/DTOs/ContribuinteDTO.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/movimentacao/DTOs/ContribuinteResponse.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraService.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraRepository.java` (fetch join da lista nova)
- Test: `src/test/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraServiceTest.java`

**Interfaces:**
- Consome: `MovimentacaoContribuinte`, `MovimentacaoContribuinteRepository` (Task 1).
- Produz: `ContribuinteDTO(pessoaId, valor)`, `ContribuinteResponse(pessoaId, pessoaNome, valor)`.

- [ ] **Passo 1: `ContribuinteDTO`**

```java
package com.domus.api.modules.financeiro.movimentacao.DTOs;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ContribuinteDTO(
        @NotNull(message = "A pessoa é obrigatória")
        UUID pessoaId,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "Valor inválido")
        BigDecimal valor
) {}
```

- [ ] **Passo 2: `ContribuinteResponse`**

```java
package com.domus.api.modules.financeiro.movimentacao.DTOs;

import java.math.BigDecimal;
import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record ContribuinteResponse(
        UUID pessoaId,
        String pessoaNome,
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal valor
) {}
```

- [ ] **Passo 3: `MovimentacaoRequestDTO`** — trocar `UUID pessoaId` por:

```java
import java.util.List;

// ...
@NotNull(message = "A lista de contribuintes é obrigatória (pode ser vazia)")
List<ContribuinteDTO> contribuintes,
```

- [ ] **Passo 4: `MovimentacaoResponse`** — trocar `pessoaId`/`pessoaNome` por
  `List<ContribuinteResponse> contribuintes`, e no `de(...)`:

```java
public static MovimentacaoResponse de(MovimentacaoFinanceira m) {
    return new MovimentacaoResponse(
            m.getId(),
            m.getTipo(),
            m.getValor(),
            m.getDataMovimentacao(),
            m.getDescricao(),
            m.getCategoria().getId(),
            m.getCategoria().getNome(),
            m.getContribuintes().stream()
                    .map(c -> new ContribuinteResponse(c.getPessoa().getId(), c.getPessoa().getNome(), c.getValor()))
                    .toList(),
            m.getCriadoPor().getNome(),
            m.getAtualizadoPor() != null ? m.getAtualizadoPor().getNome() : null
    );
}
```

- [ ] **Passo 5: testes de validação (escrever antes do código, TDD)** — em
  `MovimentacaoFinanceiraServiceTest.java`:

```java
@Test
void cadastrarRecusaQuandoSomaDosContribuintesDivergeDoValor() {
    var contribuintes = List.of(
            new ContribuinteDTO(pessoaId1, new BigDecimal("30.00")),
            new ContribuinteDTO(pessoaId2, new BigDecimal("30.00"))
    );
    var dto = new MovimentacaoRequestDTO(TipoMovimentacao.ENTRADA, new BigDecimal("100.00"),
            categoriaId, LocalDate.now(), contribuintes, null);

    assertThatThrownBy(() -> service.cadastrar(dto, igrejaId, usuarioId))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("VALOR_CONTRIBUINTES_DIVERGENTE".substring(0, 0)); // ver nota abaixo
    verify(repository, never()).save(any());
}

@Test
void cadastrarRecusaContribuinteDuplicado() {
    var contribuintes = List.of(
            new ContribuinteDTO(pessoaId1, new BigDecimal("50.00")),
            new ContribuinteDTO(pessoaId1, new BigDecimal("50.00"))
    );
    var dto = new MovimentacaoRequestDTO(TipoMovimentacao.ENTRADA, new BigDecimal("100.00"),
            categoriaId, LocalDate.now(), contribuintes, null);

    assertThatThrownBy(() -> service.cadastrar(dto, igrejaId, usuarioId))
            .isInstanceOf(BusinessException.class);
    verify(repository, never()).save(any());
}

@Test
void cadastrarAceitaListaVaziaDeContribuintes() {
    var dto = new MovimentacaoRequestDTO(TipoMovimentacao.ENTRADA, new BigDecimal("100.00"),
            categoriaId, LocalDate.now(), List.of(), null);

    service.cadastrar(dto, igrejaId, usuarioId);

    verify(repository).save(any());
}

@Test
void cadastrarAceitaSomaExataDosContribuintes() {
    var contribuintes = List.of(
            new ContribuinteDTO(pessoaId1, new BigDecimal("40.00")),
            new ContribuinteDTO(pessoaId2, new BigDecimal("60.00"))
    );
    var dto = new MovimentacaoRequestDTO(TipoMovimentacao.ENTRADA, new BigDecimal("100.00"),
            categoriaId, LocalDate.now(), contribuintes, null);

    service.cadastrar(dto, igrejaId, usuarioId);

    verify(repository).save(any());
}
```

Nota sobre a asserção de mensagem: usar
`.hasMessageContaining("não bate")` ou o texto real que você escrever na exceção — o
importante é o `isInstanceOf(BusinessException.class)`, ajustar a string exata ao
implementar (evitar copiar literal `.substring(0,0)`, isso foi só placeholder de
raciocínio — escrever a mensagem real na Task, não este trecho).

- [ ] **Passo 6: implementar a validação no service**

```java
private void validarContribuintes(BigDecimal valorTotal, List<ContribuinteDTO> contribuintes) {
    if (contribuintes.isEmpty()) return;

    Set<UUID> pessoas = new HashSet<>();
    for (ContribuinteDTO c : contribuintes) {
        if (!pessoas.add(c.pessoaId())) {
            throw new BusinessException("CONTRIBUINTE_DUPLICADO",
                    "A mesma pessoa não pode aparecer duas vezes na lista de contribuintes.");
        }
    }

    BigDecimal soma = contribuintes.stream()
            .map(ContribuinteDTO::valor)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (soma.compareTo(valorTotal) != 0) {
        throw new BusinessException("VALOR_CONTRIBUINTES_DIVERGENTE",
                "A soma dos contribuintes (" + soma + ") não bate com o valor total da movimentação (" + valorTotal + ").");
    }
}

private List<MovimentacaoContribuinte> resolverContribuintes(
        MovimentacaoFinanceira mov, List<ContribuinteDTO> dtos, UUID igrejaId) {
    return dtos.stream()
            .map(c -> MovimentacaoContribuinte.builder()
                    .movimentacao(mov)
                    .pessoa(resolverMembro(c.pessoaId(), igrejaId))
                    .valor(c.valor())
                    .build())
            .toList();
}
```

Chamar `validarContribuintes(dto.valor(), dto.contribuintes())` no início de `cadastrar` e
`atualizar` (antes de montar a entidade). No `cadastrar`, depois de `mov` já ter `id`
(salvar a movimentação primeiro, sem contribuintes, depois popular
`mov.getContribuintes().addAll(resolverContribuintes(mov, dto.contribuintes(), igrejaId))`
e salvar de novo — ou, mais simples, montar a lista com `mov` já referenciado no builder
antes do primeiro `save`, já que é a mesma instância em memória, e deixar o
`cascade = ALL` propagar). No `atualizar`: `mov.getContribuintes().clear()` seguido de
`mov.getContribuintes().addAll(...)` — o `orphanRemoval = true` cuida do DELETE das linhas
antigas.

- [ ] **Passo 7: `MovimentacaoFinanceiraRepository`** — trocar
  `LEFT JOIN FETCH m.pessoa` por `LEFT JOIN FETCH m.contribuintes ct LEFT JOIN FETCH ct.pessoa`
  nos dois métodos (`buscarPorIdComRelacoes`, `buscarComFiltros`). Remover
  `buscarIdsPorMembro` deste arquivo (query antiga baseada em `m.pessoa.id`) — vai ser
  refeita na Task 4 usando a tabela nova.

- [ ] **Passo 8: rodar `mvn -q -o test -Dtest=MovimentacaoFinanceiraServiceTest`**

Esperado: todos os testes passam, incluindo os 4 novos.

- [ ] **Passo 9: commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/movimentacao src/main/resources/db/migration/V15__movimentacao_contribuinte.sql src/test/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraServiceTest.java
git commit -m "feat: multiplos contribuintes por movimentacao (rateio explicito)"
```

**Parar aqui e esperar o autor testar** (CRUD de movimentação com 0, 1 e N contribuintes,
via curl ou Postman, antes de seguir pra Task 3) — a spec e o CLAUDE.md exigem isso.

---

### Task 3: Relatório "por contribuinte" + filtro por vínculo fatiado

**Files:**
- Modify: `src/main/java/com/domus/api/modules/financeiro/relatorio/RelatorioProjections.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/relatorio/RelatorioRepository.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/relatorio/RelatorioService.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/relatorio/RelatorioController.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/relatorio/DTOs/ContribuinteBreakdownResponse.java`
- Test: `src/test/java/com/domus/api/modules/financeiro/relatorio/RelatorioServiceTest.java`

**Interfaces:**
- Produz: `GET /relatorios/por-contribuinte` análogo a `/por-categoria`.
- Produz: os 4 métodos existentes de `RelatorioRepository` ganham 2ª query (usada quando
  `vinculo != null`) somando `movimentacao_contribuinte.valor`, não `m.valor`.

- [ ] **Passo 1: `ContribuinteBreakdownResponse`**

```java
package com.domus.api.modules.financeiro.relatorio.DTOs;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import java.math.BigDecimal;
import java.util.UUID;

public record ContribuinteBreakdownResponse(
        UUID pessoaId, String pessoaNome, TipoMovimentacao tipo,
        BigDecimal total, BigDecimal percentual
) {}
```

- [ ] **Passo 2: projeção nova em `RelatorioProjections`**

```java
interface ContribuinteAgregado {
    UUID getPessoaId();
    String getPessoaNome();
    TipoMovimentacao getTipo();
    BigDecimal getTotal();
}
```

- [ ] **Passo 3: query nova em `RelatorioRepository`**

```java
@Query("""
SELECT
    p.id AS pessoaId,
    p.nome AS pessoaNome,
    m.tipo AS tipo,
    SUM(ct.valor) AS total
FROM MovimentacaoContribuinte ct
JOIN ct.movimentacao m
JOIN ct.pessoa p
WHERE m.igreja.id = :igrejaId
  AND m.dataMovimentacao >= :dataInicio
  AND m.dataMovimentacao <= :dataFim
GROUP BY p.id, p.nome, m.tipo
ORDER BY SUM(ct.valor) DESC
""")
List<RelatorioProjections.ContribuinteAgregada> agregarPorContribuinte(@Param("igrejaId") UUID igrejaId,
                                                                       @Param("dataInicio") LocalDate dataInicio,
                                                                       @Param("dataFim") LocalDate dataFim);
```

(Import `MovimentacaoContribuinte` no topo do arquivo.)

- [ ] **Passo 4: `RelatorioService.porContribuinte(...)`** — mesmo padrão de
  `porCategoria`:

```java
@Transactional(readOnly = true)
public List<ContribuinteBreakdownResponse> porContribuinte(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim) {
    var agregados = repository.agregarPorContribuinte(igrejaId, dataInicio, dataFim);

    BigDecimal totalEntradas = agregados.stream()
            .filter(a -> a.getTipo() == TipoMovimentacao.ENTRADA)
            .map(RelatorioProjections.ContribuinteAgregada::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalSaidas = agregados.stream()
            .filter(a -> a.getTipo() == TipoMovimentacao.SAIDA)
            .map(RelatorioProjections.ContribuinteAgregada::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);

    return agregados.stream().map(a -> {
        BigDecimal base = a.getTipo() == TipoMovimentacao.ENTRADA ? totalEntradas : totalSaidas;
        BigDecimal pct = base.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : a.getTotal().multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP);
        return new ContribuinteBreakdownResponse(a.getPessoaId(), a.getPessoaNome(), a.getTipo(), a.getTotal(), pct);
    }).toList();
}
```

- [ ] **Passo 5: endpoint no `RelatorioController`**

```java
@GetMapping("/por-contribuinte")
public List<ContribuinteBreakdownResponse> porContribuinte(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
        @RequestParam(required = false) UUID igrejaId) {
    exigirFinanceiro();
    return service.porContribuinte(escopo(igrejaId), dataInicio, dataFim);
}
```

- [ ] **Passo 6: fatiar por vínculo nos 4 relatórios existentes**

Cada um dos 4 métodos (`agregarResumo`, `agregarPorCategoria`, `agregarEvolucaoMensal`,
`buscarMaiorLancamento`) ganha uma 2ª query JPQL (sufixo `PorVinculo`) que usa
`MovimentacaoContribuinte` + `pessoa.vinculo` em vez de `m.valor` direto. Exemplo pro
resumo:

```java
@Query("""
SELECT
    COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN ct.valor ELSE 0 END), 0) AS totalEntradas,
    COALESCE(SUM(CASE WHEN m.tipo = 'SAIDA' THEN ct.valor ELSE 0 END), 0) AS totalSaidas,
    COUNT(DISTINCT m.id) AS quantidade
FROM MovimentacaoContribuinte ct
JOIN ct.movimentacao m
JOIN ct.pessoa p
WHERE m.igreja.id = :igrejaId
  AND m.dataMovimentacao >= :dataInicio
  AND m.dataMovimentacao <= :dataFim
  AND p.vinculo = :vinculo
""")
RelatorioProjections.ResumoAgregado agregarResumoPorVinculo(@Param("igrejaId") UUID igrejaId,
                                                            @Param("dataInicio") LocalDate dataInicio,
                                                            @Param("dataFim") LocalDate dataFim,
                                                            @Param("vinculo") Vinculo vinculo);
```

No `RelatorioService`, cada método passa a escolher a query certa:

```java
var atual = vinculo == null
        ? repository.agregarResumo(igrejaId, dataInicio, dataFim)
        : repository.agregarResumoPorVinculo(igrejaId, dataInicio, dataFim, vinculo);
```

(mesma troca nos outros 3: `porCategoria`, `evolucaoMensal`, `maiorLancamento` — cada
query "PorVinculo" segue o mesmo molde: troca `m.valor`/`SUM(m.valor)` por `ct.valor`/
`SUM(ct.valor)`, junta em `MovimentacaoContribuinte` em vez de `LEFT JOIN m.pessoa`, e
filtra `p.vinculo = :vinculo` com `JOIN` normal, não `LEFT JOIN` — sem contribuinte não
bate o filtro, igual hoje.) Remover o parâmetro `vinculo` das 4 queries antigas (elas só
rodam quando `vinculo == null`, então não precisam mais do `LEFT JOIN m.pessoa` nem do
`(:vinculo IS NULL OR p.vinculo = :vinculo)`).

- [ ] **Passo 7: testes** — em `RelatorioServiceTest.java`, adicionar cenário: 1
  movimentação de R$100 com 2 contribuintes (1 MEMBRO com R$60, 1 CONGREGANTE com R$40);
  `resumoPorPeriodo(..., vinculo=MEMBRO)` deve retornar `totalEntradas = 60.00`, não
  `100.00`.

- [ ] **Passo 8: rodar `mvn -q -o test -Dtest=RelatorioServiceTest`**

- [ ] **Passo 9: commit**

**Parar e esperar o autor testar** (relatório novo + filtro por vínculo com movimentação
de contribuintes mistos) antes da Task 4.

---

### Task 4: Filtro de listagem por pessoa + busca (Elasticsearch)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraController.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraService.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraRepository.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/busca/MovimentacaoDocument.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/busca/BuscaMovimentacaoService.java`

**Interfaces:**
- Produz: `GET /movimentacoes?pessoaId=...`.
- Produz: `MovimentacaoDocument.pessoaNomes: List<String>` substitui `pessoaNome`.

- [ ] **Passo 1: `MovimentacaoFinanceiraRepository.buscarComFiltros`** — acrescentar
  parâmetro `UUID pessoaId` e condição:

```java
AND (:pessoaId IS NULL OR EXISTS (
    SELECT 1 FROM MovimentacaoContribuinte ct2
    WHERE ct2.movimentacao = m AND ct2.pessoa.id = :pessoaId
))
```

- [ ] **Passo 2: repor `buscarIdsPorMembro`** (removido na Task 2), agora via tabela nova:

```java
@Query("""
SELECT DISTINCT ct.movimentacao.id FROM MovimentacaoContribuinte ct
WHERE ct.pessoa.id = :pessoaId AND ct.movimentacao.igreja.id = :igrejaId
""")
List<UUID> buscarIdsPorMembro(@Param("pessoaId") UUID pessoaId, @Param("igrejaId") UUID igrejaId);
```

- [ ] **Passo 3: `MovimentacaoFinanceiraService.listar`** — acrescentar parâmetro
  `UUID pessoaId`, incluir no cálculo de `semFiltro` e repassar pro repositório.

- [ ] **Passo 4: `MovimentacaoFinanceiraController.listar`** — acrescentar
  `@RequestParam(required = false) UUID pessoaId` e repassar.

- [ ] **Passo 5: `MovimentacaoDocument`** — trocar `pessoaNome` por:

```java
@Field(type = FieldType.Text, analyzer = "domus_index", searchAnalyzer = "domus_search")
private List<String> pessoaNomes;
```

No `de(...)`:
```java
doc.setPessoaNomes(mov.getContribuintes().stream()
        .map(c -> c.getPessoa().getNome())
        .toList());
```

- [ ] **Passo 6: `BuscaMovimentacaoService`** — trocar `"pessoaNome"` por
  `"pessoaNomes"` nos dois `.fields(...)`.

- [ ] **Passo 7: testes** — atualizar/estender `MovimentacaoFinanceiraServiceTest` com
  `listarFiltraPorPessoaContribuinte`.

- [ ] **Passo 8: rodar suíte completa** `mvn -q -o test -Dtest=MovimentacaoFinanceiraServiceTest`

- [ ] **Passo 9: commit**

**Parar e esperar o autor testar** (filtro por pessoa na listagem; busca por nome de
contribuinte — esta última precisa do `POST /admin/reindexacao` rodado manualmente uma
vez, avisar o autor disso) antes da Task 5.

---

### Task 5: Frontend

**Files:**
- Modify: `frontend/src/types/financeiro/movimentacao.type.ts`
- Modify: `frontend/src/services/financeiro/movimentacao.service.ts`
- Modify: `frontend/src/lib/validators` (schema Zod da movimentação — localizar arquivo exato ao abrir a task)
- Modify: `frontend/src/components/module/movimentacoes/MovimentacaoForm.tsx`
- Create: `frontend/src/components/module/movimentacoes/ListaContribuintes.tsx`
- Modify: `frontend/src/hooks/financeiro/movimentacao/useMovimentacaoForm.ts`
- Modify: listagem de movimentações (coluna de pessoa) — localizar arquivo exato ao abrir
- Modify: `frontend/src/app/(app)/financeiro/movimentacoes/(detalhe)/DrawerDetalheMovimentacao.tsx`
- Create: bloco de relatório "Por contribuinte" (mesmo padrão do bloco "Por categoria" já
  existente — localizar componente exato ao abrir a task)

**Interfaces:**
- Consome: `GET /relatorios/por-contribuinte`, `GET /movimentacoes?pessoaId=`, novo
  formato de `contribuintes` no request/response de movimentação (Tasks 2-4).

- [ ] **Passo 1:** `movimentacao.type.ts` — trocar `pessoaId`/`pessoaNome` por:

```typescript
export interface ContribuinteResponse {
  pessoaId: string
  pessoaNome: string
  valor: string
}

export interface ContribuinteRequest {
  pessoaId: string
  valor: string
}

// em MovimentacaoResponse: contribuintes: ContribuinteResponse[]
// em MovimentacaoRequest: contribuintes: ContribuinteRequest[]
// em MovimentacaoFiltros: pessoaId?: string
```

- [ ] **Passo 2:** `movimentacao.service.ts` — em `listar`, acrescentar
  `if (filtros.pessoaId) params.pessoaId = filtros.pessoaId`.

- [ ] **Passo 3:** `MovimentacaoForm.tsx` — substituir o bloco único de `SelecaoPessoa`
  (linhas 170-181 hoje) por um componente novo `ListaContribuintes` que:
  - Renderiza N linhas de `SelecaoPessoa` + input de valor (reaproveitar
    `formatarValorDigitado`/máscara de moeda já usada no campo de valor total).
  - Botão "+ Adicionar contribuinte".
  - Quando a lista tem exatamente 2 linhas, mostra botão "Dividir 50/50" que preenche
    `valor / 2` em cada uma (arredondado, sobra de centavo pro primeiro:
    `Math.floor(centavosTotal / 2)` na primeira, `centavosTotal - floor` na segunda).
  - Mostra soma corrente vs. valor total da movimentação; se divergir, mensagem de erro
    inline (mesmo texto de erro do backend) e desabilita o botão salvar (reusar a mesma
    condição que já desabilita `btnSalvar` em `isFormIncomplete`/`valorInvalido`).
- [ ] **Passo 4:** `useMovimentacaoForm.ts` e o schema Zod — ajustar pra validar
  `contribuintes: z.array(z.object({ pessoaId: z.string(), valor: z.string() }))` com
  `.refine` conferindo soma == valor total (mesma regra do backend, replicada).
- [ ] **Passo 5:** listagem — coluna de pessoa mostra `contribuintes[0]?.pessoaNome` +
  `` (+${contribuintes.length - 1}) `` quando `length > 1`; tooltip ou expandir ao clicar
  mostrando a lista completa.
- [ ] **Passo 6:** `DrawerDetalheMovimentacao.tsx` — trocar o bloco que mostra 1
  pessoa por uma lista de `contribuinte.pessoaNome — R$ valor`.
- [ ] **Passo 7:** filtro da listagem — mesmo `SelecaoPessoa` de sempre, ligado ao novo
  `filtros.pessoaId`.
- [ ] **Passo 8:** novo bloco "Por contribuinte" no dashboard/relatórios financeiros,
  mesmo componente visual do bloco "Por categoria" já existente, trocando a chamada de
  serviço pro novo endpoint.
- [ ] **Passo 9:** testar manualmente no navegador em viewport mobile (linhas de
  contribuinte empilham) — CLAUDE.md exige responsividade em toda feature de front antes
  de considerar pronto.
- [ ] **Passo 10: commit**

**Parar e esperar o autor testar tudo end-to-end** antes de considerar a feature
completa.
