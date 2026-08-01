# Balancete Anual — Plano de Implementação

> **Para quem for executar:** use superpowers:subagent-driven-development (recomendado) ou
> superpowers:executing-plans para executar este plano tarefa por tarefa. Os passos usam
> checkbox (`- [ ]`) para acompanhamento.

**Objetivo:** entregar dois endpoints (`GET /relatorios/balancete-anual` e
`GET /relatorios/balancete-anual/congregacoes`) que retornam o balancete anual — matriz
categoria × mês, com saldo de abertura (movimentações de anos anteriores) e saldo
acumulado — e a tela de front que consome esses endpoints.

**Arquitetura:** novo módulo `com.domus.api.modules.financeiro.balancete` (controller →
service → repository), seguindo o mesmo padrão de `RelatorioController`/`RelatorioService`
e reaproveitando `FamiliaIgrejaService`/`Permissoes` como `ConsolidadoController` já faz.
Sem migration nova — o índice `idx_movimentacao_igreja_data (igreja_id, data_movimentacao)`
já existente cobre as queries de agregação. No front, uma tela nova
(`financeiro/relatorios/balancete`), linkada a partir da tela de relatórios existente.

**Tech Stack:** Spring Boot (JPA + query nativa Postgres), Next.js/TanStack Query
(padrão já usado no módulo de relatórios).

## Global Constraints

- Isolamento por `igreja_id` sempre resolvido do JWT (`UsuarioAutenticado`), nunca do
  corpo/query da requisição.
- Services retornam DTOs (`record`), nunca entidades JPA.
- Soft delete (`deleted_at`) respeitado — categoria arquivada só aparece se teve
  movimentação no ano pedido.
- Teste de service = Mockito puro, sem contexto Spring (regra padrão do projeto).
- Teste de repository = `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)`
  porque a query é nativa com `GROUP BY` (não trivial) — roda contra o Neon de testes,
  precisa do `.env` exportado.
- Responsividade obrigatória no front: tabela vira card por mês no mobile.
- Não commitar antes de o autor testar cada pedaço.

---

### Task 1: DTOs do balancete (igreja própria)

**Files:**
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/LinhaCategoriaDTO.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/BalanceteResponseDTO.java`

**Interfaces:**
- Produces: `LinhaCategoriaDTO(UUID categoriaId, String nomeCategoria, boolean arquivada, List<BigDecimal> valoresPorMes, BigDecimal totalAno)`
  — `categoriaId` é `null` só quando a linha vier do endpoint consolidado (Task 6), onde
  uma linha pode juntar categorias de igrejas diferentes.
- Produces: `BalanceteResponseDTO(int ano, BigDecimal saldoAbertura, List<LinhaCategoriaDTO> entradas, List<LinhaCategoriaDTO> saidas, List<BigDecimal> subtotalEntradasPorMes, List<BigDecimal> subtotalSaidasPorMes, List<BigDecimal> saldoDoMes, List<BigDecimal> saldoAcumulado)`
  — as 4 listas de `BigDecimal` sempre têm exatamente 12 posições (jan..dez).

Não há teste isolado para DTOs (são `record`s sem lógica) — são cobertos pelos testes do
service na Task 3.

- [ ] **Step 1: Criar `LinhaCategoriaDTO`**

```java
package com.domus.api.modules.financeiro.balancete.DTOs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LinhaCategoriaDTO(
        UUID categoriaId,
        String nomeCategoria,
        boolean arquivada,
        List<BigDecimal> valoresPorMes,
        BigDecimal totalAno
) {}
```

- [ ] **Step 2: Criar `BalanceteResponseDTO`**

```java
package com.domus.api.modules.financeiro.balancete.DTOs;

import java.math.BigDecimal;
import java.util.List;

public record BalanceteResponseDTO(
        int ano,
        BigDecimal saldoAbertura,
        List<LinhaCategoriaDTO> entradas,
        List<LinhaCategoriaDTO> saidas,
        List<BigDecimal> subtotalEntradasPorMes,
        List<BigDecimal> subtotalSaidasPorMes,
        List<BigDecimal> saldoDoMes,
        List<BigDecimal> saldoAcumulado
) {}
```

- [ ] **Step 3: Compilar para garantir que não há erro de sintaxe**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/LinhaCategoriaDTO.java src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/BalanceteResponseDTO.java
git commit -m "feat: DTOs do balancete anual"
```

---

### Task 2: Projections + BalanceteRepository (query nativa) com teste `@DataJpaTest`

**Files:**
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteProjections.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteRepository.java`
- Test: `src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteRepositoryTest.java`

**Interfaces:**
- Consumes: nada de tasks anteriores (é a base).
- Produces:
  - `BalanceteProjections.LinhaMensalAgregada` com `getCategoriaId()`, `getNomeCategoria()`,
    `getArquivada()` (Boolean), `getTipo()` (String, `"ENTRADA"`/`"SAIDA"`), `getMes()`
    (Integer, 1-12), `getTotal()` (BigDecimal).
  - `BalanceteRepository.agregarPorCategoriaEMes(UUID igrejaId, int ano)` →
    `List<LinhaMensalAgregada>`.
  - `BalanceteRepository.saldoAntesDe(UUID igrejaId, LocalDate inicioAno)` → `BigDecimal`
    (nunca `null` — `COALESCE` no SQL).

- [ ] **Step 1: Criar `BalanceteProjections`**

```java
package com.domus.api.modules.financeiro.balancete;

import java.math.BigDecimal;
import java.util.UUID;

public interface BalanceteProjections {

    interface LinhaMensalAgregada {
        UUID getCategoriaId();
        String getNomeCategoria();
        Boolean getArquivada();
        String getTipo();
        Integer getMes();
        BigDecimal getTotal();
    }
}
```

- [ ] **Step 2: Criar `BalanceteRepository` com as duas queries nativas**

```java
package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface BalanceteRepository extends JpaRepository<MovimentacaoFinanceira, UUID> {

    @Query(value = """
        SELECT c.id AS categoriaId,
               c.nome AS nomeCategoria,
               (c.deleted_at IS NOT NULL) AS arquivada,
               m.tipo AS tipo,
               EXTRACT(MONTH FROM m.data_movimentacao)::int AS mes,
               SUM(m.valor) AS total
        FROM movimentacao_financeira m
        JOIN categoria_financeira c ON c.id = m.categoria_id
        WHERE m.igreja_id = :igrejaId
          AND m.deleted_at IS NULL
          AND EXTRACT(YEAR FROM m.data_movimentacao) = :ano
        GROUP BY c.id, c.nome, c.deleted_at, m.tipo, EXTRACT(MONTH FROM m.data_movimentacao)
        """, nativeQuery = true)
    List<BalanceteProjections.LinhaMensalAgregada> agregarPorCategoriaEMes(
            @Param("igrejaId") UUID igrejaId, @Param("ano") int ano);

    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0)
        FROM movimentacao_financeira m
        WHERE m.igreja_id = :igrejaId
          AND m.deleted_at IS NULL
          AND m.data_movimentacao < :inicioAno
        """, nativeQuery = true)
    BigDecimal saldoAntesDe(@Param("igrejaId") UUID igrejaId, @Param("inicioAno") LocalDate inicioAno);
}
```

`c.deleted_at`/`c.nome` só existem no JOIN (não passam pelo `@SQLRestriction` da entidade
`CategoriaFinanceira`, porque a query é nativa e lê a tabela diretamente) — é assim que
uma categoria arquivada com movimento no ano consegue aparecer no resultado.

- [ ] **Step 3: Escrever o teste `@DataJpaTest` (roda contra o Neon de testes)**

```java
package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BalanceteRepositoryTest {

    @Autowired BalanceteRepository balanceteRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;
    Usuario usuario;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Balancete " + UUID.randomUUID())
                .emailContato("balancete-" + UUID.randomUUID() + "@teste.com")
                .build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja)
                .nome("Tesoureiro Teste")
                .vinculo(Vinculo.MEMBRO)
                .build());

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();

        usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja)
                .pessoa(pessoa)
                .role(role)
                .ativo(true)
                .build());

        entityManager.flush();
    }

    private CategoriaFinanceira categoria(String nome, TipoCategoria tipo) {
        return categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome(nome).tipo(tipo).build());
    }

    private void movimentacao(CategoriaFinanceira categoria, TipoMovimentacao tipo,
            BigDecimal valor, LocalDate data) {
        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(tipo).valor(valor).dataMovimentacao(data).build());
    }

    @Test
    void agregaPorCategoriaEMesDentroDoAno() {
        CategoriaFinanceira dizimos = categoria("Dizimos Teste " + UUID.randomUUID(), TipoCategoria.ENTRADA);
        movimentacao(dizimos, TipoMovimentacao.ENTRADA, new BigDecimal("100.00"), LocalDate.of(2026, 1, 10));
        movimentacao(dizimos, TipoMovimentacao.ENTRADA, new BigDecimal("50.00"), LocalDate.of(2026, 1, 20));
        movimentacao(dizimos, TipoMovimentacao.ENTRADA, new BigDecimal("30.00"), LocalDate.of(2025, 12, 31));
        entityManager.flush();
        entityManager.clear();

        List<BalanceteProjections.LinhaMensalAgregada> linhas =
                balanceteRepository.agregarPorCategoriaEMes(igreja.getId(), 2026);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getMes()).isEqualTo(1);
        assertThat(linhas.get(0).getTotal()).isEqualByComparingTo("150.00");
        assertThat(linhas.get(0).getArquivada()).isFalse();
    }

    @Test
    void categoriaArquivadaComMovimentoNoAnoApareceNaAgregacao() {
        CategoriaFinanceira categoria = categoria("Doacao Especial " + UUID.randomUUID(), TipoCategoria.ENTRADA);
        movimentacao(categoria, TipoMovimentacao.ENTRADA, new BigDecimal("200.00"), LocalDate.of(2026, 3, 5));
        categoriaRepository.delete(categoria); // soft delete via @SQLDelete
        entityManager.flush();
        entityManager.clear();

        List<BalanceteProjections.LinhaMensalAgregada> linhas =
                balanceteRepository.agregarPorCategoriaEMes(igreja.getId(), 2026);

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).getArquivada()).isTrue();
        assertThat(linhas.get(0).getTotal()).isEqualByComparingTo("200.00");
    }

    @Test
    void saldoAntesDeSomaSoMovimentacoesAnterioresAoAno() {
        CategoriaFinanceira c = categoria("Ofertas Teste " + UUID.randomUUID(), TipoCategoria.AMBOS);
        movimentacao(c, TipoMovimentacao.ENTRADA, new BigDecimal("1000.00"), LocalDate.of(2025, 6, 1));
        movimentacao(c, TipoMovimentacao.SAIDA, new BigDecimal("300.00"), LocalDate.of(2025, 12, 31));
        movimentacao(c, TipoMovimentacao.ENTRADA, new BigDecimal("500.00"), LocalDate.of(2026, 1, 1)); // não entra
        entityManager.flush();
        entityManager.clear();

        BigDecimal saldo = balanceteRepository.saldoAntesDe(igreja.getId(), LocalDate.of(2026, 1, 1));

        assertThat(saldo).isEqualByComparingTo("700.00");
    }

    @Test
    void saldoAntesDeSemMovimentacaoRetornaZeroNuncaNull() {
        BigDecimal saldo = balanceteRepository.saldoAntesDe(igreja.getId(), LocalDate.of(2026, 1, 1));
        assertThat(saldo).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
```

- [ ] **Step 4: Rodar o teste (precisa do `.env` exportado)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=BalanceteRepositoryTest`
Expected: BUILD SUCCESS, 4 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteProjections.java src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteRepository.java src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteRepositoryTest.java
git commit -m "feat: query de agregacao do balancete anual"
```

---

### Task 3: BalanceteService (igreja própria) com testes Mockito puro

**Files:**
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteService.java`
- Test: `src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteServiceTest.java`

**Interfaces:**
- Consumes: `BalanceteRepository.agregarPorCategoriaEMes(UUID, int)`,
  `BalanceteRepository.saldoAntesDe(UUID, LocalDate)` (Task 2);
  `CategoriaFinanceiraRepository.buscarTodasPorIgreja(UUID)` (já existe no projeto,
  retorna só categorias ativas por causa do `@SQLRestriction`).
- Produces: `BalanceteService.gerar(UUID igrejaId, int ano)` → `BalanceteResponseDTO`
  (Task 1). Esse método é reaproveitado pela Task 6 (visão por igreja dentro do
  consolidado).

- [ ] **Step 1: Escrever os testes primeiro**

```java
package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalanceteServiceTest {

    BalanceteRepository repository;
    CategoriaFinanceiraRepository categoriaRepository;
    BalanceteService service;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(BalanceteRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        service = new BalanceteService(repository, categoriaRepository);
        when(repository.saldoAntesDe(eq(igrejaId), any())).thenReturn(BigDecimal.ZERO);
    }

    private CategoriaFinanceira categoriaAtiva(UUID id, String nome, TipoCategoria tipo) {
        return CategoriaFinanceira.builder().id(id).nome(nome).tipo(tipo).build();
    }

    private BalanceteProjections.LinhaMensalAgregada linha(UUID categoriaId, String nome,
            boolean arquivada, String tipo, int mes, BigDecimal total) {
        BalanceteProjections.LinhaMensalAgregada l = mock(BalanceteProjections.LinhaMensalAgregada.class);
        when(l.getCategoriaId()).thenReturn(categoriaId);
        when(l.getNomeCategoria()).thenReturn(nome);
        when(l.getArquivada()).thenReturn(arquivada);
        when(l.getTipo()).thenReturn(tipo);
        when(l.getMes()).thenReturn(mes);
        when(l.getTotal()).thenReturn(total);
        return l;
    }

    @Test
    void categoriaAtivaSemMovimentoNoAnoApareceZerada() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Dizimos", TipoCategoria.ENTRADA)));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of());

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.entradas().get(0).arquivada()).isFalse();
        assertThat(resultado.entradas().get(0).totalAno()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultado.entradas().get(0).valoresPorMes()).hasSize(12);
        assertThat(resultado.entradas().get(0).valoresPorMes()).allMatch(v -> v.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void categoriaArquivadaSemMovimentoNoAnoNaoAparece() {
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of());

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).isEmpty();
        assertThat(resultado.saidas()).isEmpty();
    }

    @Test
    void categoriaArquivadaComMovimentoApareceMarcada() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(
                linha(categoriaId, "Doacao Especial", true, "ENTRADA", 3, new BigDecimal("200.00"))));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.entradas().get(0).arquivada()).isTrue();
        assertThat(resultado.entradas().get(0).valoresPorMes().get(2)).isEqualByComparingTo("200.00"); // março = índice 2
        assertThat(resultado.entradas().get(0).totalAno()).isEqualByComparingTo("200.00");
    }

    @Test
    void categoriaAmbosApareceEmEntradasESaidasSeparadamente() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Ofertas", TipoCategoria.AMBOS)));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(
                linha(categoriaId, "Ofertas", false, "ENTRADA", 1, new BigDecimal("100.00")),
                linha(categoriaId, "Ofertas", false, "SAIDA", 1, new BigDecimal("40.00"))));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.saidas()).hasSize(1);
        assertThat(resultado.entradas().get(0).valoresPorMes().get(0)).isEqualByComparingTo("100.00");
        assertThat(resultado.saidas().get(0).valoresPorMes().get(0)).isEqualByComparingTo("40.00");
    }

    @Test
    void saldoAcumuladoSomaAberturaMaisSaldoCorridoDoAno() {
        UUID categoriaId = UUID.randomUUID();
        when(repository.saldoAntesDe(eq(igrejaId), eq(LocalDate.of(2026, 1, 1))))
                .thenReturn(new BigDecimal("1000.00"));
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Dizimos", TipoCategoria.ENTRADA)));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(
                linha(categoriaId, "Dizimos", false, "ENTRADA", 1, new BigDecimal("300.00")),
                linha(categoriaId, "Dizimos", false, "ENTRADA", 2, new BigDecimal("200.00"))));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        // jan: 1000 + 300 = 1300 | fev: 1300 + 200 = 1500
        assertThat(resultado.saldoAcumulado().get(0)).isEqualByComparingTo("1300.00");
        assertThat(resultado.saldoAcumulado().get(1)).isEqualByComparingTo("1500.00");
        assertThat(resultado.saldoAcumulado().get(11)).isEqualByComparingTo("1500.00"); // sem movimento depois, mantém
    }
}
```

- [ ] **Step 2: Rodar e ver falhar (classe ainda não existe)**

Run: `mvn -q test -Dtest=BalanceteServiceTest`
Expected: FAIL (compilation error — `BalanceteService` não existe)

- [ ] **Step 3: Implementar `BalanceteService`**

```java
package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.LinhaCategoriaDTO;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BalanceteService {

    private final BalanceteRepository repository;
    private final CategoriaFinanceiraRepository categoriaRepository;

    @Transactional(readOnly = true)
    public BalanceteResponseDTO gerar(UUID igrejaId, int ano) {
        BigDecimal saldoAbertura = repository.saldoAntesDe(igrejaId, LocalDate.of(ano, 1, 1));
        List<BalanceteProjections.LinhaMensalAgregada> linhas = repository.agregarPorCategoriaEMes(igrejaId, ano);

        // categoriaId -> tipo ("ENTRADA"/"SAIDA") -> array de 12 posições
        Map<UUID, Map<String, BigDecimal[]>> valoresPorCategoria = new LinkedHashMap<>();
        Map<UUID, String> nomesPorCategoria = new HashMap<>();

        for (var l : linhas) {
            valoresPorCategoria
                    .computeIfAbsent(l.getCategoriaId(), k -> new HashMap<>())
                    .computeIfAbsent(l.getTipo(), k -> arrayZerado())[l.getMes() - 1] = l.getTotal();
            nomesPorCategoria.put(l.getCategoriaId(), l.getNomeCategoria());
        }

        List<CategoriaFinanceira> ativas = categoriaRepository.buscarTodasPorIgreja(igrejaId);
        Set<UUID> idsAtivas = new HashSet<>();
        for (CategoriaFinanceira c : ativas) idsAtivas.add(c.getId());

        List<LinhaCategoriaDTO> entradas = new ArrayList<>();
        List<LinhaCategoriaDTO> saidas = new ArrayList<>();

        for (CategoriaFinanceira c : ativas) {
            if (c.getTipo() == TipoCategoria.ENTRADA || c.getTipo() == TipoCategoria.AMBOS) {
                entradas.add(montarLinha(c.getId(), c.getNome(), false, valoresPorCategoria, "ENTRADA"));
            }
            if (c.getTipo() == TipoCategoria.SAIDA || c.getTipo() == TipoCategoria.AMBOS) {
                saidas.add(montarLinha(c.getId(), c.getNome(), false, valoresPorCategoria, "SAIDA"));
            }
        }

        for (UUID categoriaId : valoresPorCategoria.keySet()) {
            if (idsAtivas.contains(categoriaId)) continue; // já tratada acima
            Map<String, BigDecimal[]> porTipo = valoresPorCategoria.get(categoriaId);
            String nome = nomesPorCategoria.get(categoriaId);
            if (porTipo.containsKey("ENTRADA")) {
                entradas.add(montarLinha(categoriaId, nome, true, valoresPorCategoria, "ENTRADA"));
            }
            if (porTipo.containsKey("SAIDA")) {
                saidas.add(montarLinha(categoriaId, nome, true, valoresPorCategoria, "SAIDA"));
            }
        }

        List<BigDecimal> subtotalEntradas = somarPorMes(entradas);
        List<BigDecimal> subtotalSaidas = somarPorMes(saidas);
        List<BigDecimal> saldoDoMes = new ArrayList<>();
        List<BigDecimal> saldoAcumulado = new ArrayList<>();
        BigDecimal acumulado = saldoAbertura;
        for (int i = 0; i < 12; i++) {
            BigDecimal saldo = subtotalEntradas.get(i).subtract(subtotalSaidas.get(i));
            saldoDoMes.add(saldo);
            acumulado = acumulado.add(saldo);
            saldoAcumulado.add(acumulado);
        }

        return new BalanceteResponseDTO(ano, saldoAbertura, entradas, saidas,
                subtotalEntradas, subtotalSaidas, saldoDoMes, saldoAcumulado);
    }

    private LinhaCategoriaDTO montarLinha(UUID categoriaId, String nome, boolean arquivada,
            Map<UUID, Map<String, BigDecimal[]>> valoresPorCategoria, String tipo) {
        BigDecimal[] arr = valoresPorCategoria
                .getOrDefault(categoriaId, Map.of())
                .getOrDefault(tipo, arrayZerado());
        List<BigDecimal> valores = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : arr) {
            valores.add(v);
            total = total.add(v);
        }
        return new LinhaCategoriaDTO(categoriaId, nome, arquivada, valores, total);
    }

    private List<BigDecimal> somarPorMes(List<LinhaCategoriaDTO> linhas) {
        BigDecimal[] soma = arrayZerado();
        for (LinhaCategoriaDTO l : linhas) {
            for (int i = 0; i < 12; i++) {
                soma[i] = soma[i].add(l.valoresPorMes().get(i));
            }
        }
        return new ArrayList<>(List.of(soma));
    }

    private BigDecimal[] arrayZerado() {
        BigDecimal[] arr = new BigDecimal[12];
        Arrays.fill(arr, BigDecimal.ZERO);
        return arr;
    }
}
```

- [ ] **Step 4: Rodar os testes e ver passar**

Run: `mvn -q test -Dtest=BalanceteServiceTest`
Expected: BUILD SUCCESS, 5 testes passando.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteService.java src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteServiceTest.java
git commit -m "feat: BalanceteService (montagem da matriz categoria x mes)"
```

---

### Task 4: BalanceteController (endpoint da igreja própria)

**Files:**
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteController.java`

**Interfaces:**
- Consumes: `BalanceteService.gerar(UUID, int)` (Task 3), `UsuarioAutenticado`,
  `Permissoes.podeVerFinanceiro(String, Set<String>)` — ambos já existem no projeto
  (`com.domus.api.shared.security`).
- Produces: `GET /relatorios/balancete-anual?ano=2026` → `BalanceteResponseDTO`, 200 OK.
  403 (`AccessDeniedException`) se o usuário não tiver `podeVerFinanceiro`.

Sem `@WebMvcTest`/`MockMvc` — segue a dívida técnica já documentada no `CLAUDE.md`
("ordem de `requestMatchers` não é coberta por teste unitário"); a validação é manual
via curl.

- [ ] **Step 1: Implementar o controller**

```java
package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class BalanceteController {

    private final BalanceteService service;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/balancete-anual")
    public BalanceteResponseDTO balanceteAnual(@RequestParam int ano) {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(), usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException("Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
        return service.gerar(usuarioAutenticado.getIgrejaId(), ano);
    }
}
```

- [ ] **Step 2: Rodar a aplicação localmente e validar manualmente com curl**

Run: `mvn -q spring-boot:run` (em outro terminal, após login e pegar o cookie de sessão)
```bash
curl -i --cookie "domus_access=<token>" "http://localhost:8080/relatorios/balancete-anual?ano=2026"
```
Expected: 200 com JSON no formato de `BalanceteResponseDTO`; sem `ano` → 400 (parâmetro
obrigatório ausente); usuário sem `podeVerFinanceiro` → 403.

- [ ] **Step 3: Compilar**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteController.java
git commit -m "feat: endpoint GET /relatorios/balancete-anual"
```

---

### Task 5: Consolidado — DTOs + repository para família

**Files:**
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/BalanceteFamiliaResponseDTO.java`
- Create: `src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/BalanceteIgrejaDTO.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteProjections.java`
  (adicionar `LinhaMensalConsolidada`)
- Modify: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteRepository.java`
  (adicionar as duas queries de família)
- Test: `src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteRepositoryConsolidadoTest.java`

**Interfaces:**
- Produces: `BalanceteFamiliaResponseDTO(List<BalanceteIgrejaDTO> porIgreja, BalanceteResponseDTO consolidado)`;
  `BalanceteIgrejaDTO(UUID igrejaId, String nomeIgreja, boolean ehSede, BalanceteResponseDTO balancete)`;
  `BalanceteRepository.agregarConsolidadoPorCategoriaEMes(List<UUID> igrejaIds, int ano)` →
  `List<LinhaMensalConsolidada>` (com `getChave()`, `getNomeExibicao()`, `getTipo()`,
  `getMes()`, `getTotal()`); `BalanceteRepository.saldoAntesDeVariasIgrejas(List<UUID> igrejaIds, LocalDate inicioAno)` → `BigDecimal`.

- [ ] **Step 1: Criar os dois novos DTOs**

```java
package com.domus.api.modules.financeiro.balancete.DTOs;

import java.util.List;

public record BalanceteFamiliaResponseDTO(
        List<BalanceteIgrejaDTO> porIgreja,
        BalanceteResponseDTO consolidado
) {}
```

```java
package com.domus.api.modules.financeiro.balancete.DTOs;

import java.util.UUID;

public record BalanceteIgrejaDTO(
        UUID igrejaId,
        String nomeIgreja,
        boolean ehSede,
        BalanceteResponseDTO balancete
) {}
```

- [ ] **Step 2: Adicionar `LinhaMensalConsolidada` em `BalanceteProjections`**

```java
    interface LinhaMensalConsolidada {
        String getChave();          // unaccent(lower(nome)) — casa categorias entre igrejas
        String getNomeExibicao();
        String getTipo();
        Integer getMes();
        BigDecimal getTotal();
    }
```

- [ ] **Step 3: Adicionar as duas queries de família em `BalanceteRepository`**

```java
    @Query(value = """
        SELECT unaccent(lower(c.nome)) AS chave,
               MIN(c.nome) AS nomeExibicao,
               m.tipo AS tipo,
               EXTRACT(MONTH FROM m.data_movimentacao)::int AS mes,
               SUM(m.valor) AS total
        FROM movimentacao_financeira m
        JOIN categoria_financeira c ON c.id = m.categoria_id
        WHERE m.igreja_id IN (:igrejaIds)
          AND m.deleted_at IS NULL
          AND EXTRACT(YEAR FROM m.data_movimentacao) = :ano
        GROUP BY unaccent(lower(c.nome)), m.tipo, EXTRACT(MONTH FROM m.data_movimentacao)
        """, nativeQuery = true)
    List<BalanceteProjections.LinhaMensalConsolidada> agregarConsolidadoPorCategoriaEMes(
            @Param("igrejaIds") List<UUID> igrejaIds, @Param("ano") int ano);

    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE -m.valor END), 0)
        FROM movimentacao_financeira m
        WHERE m.igreja_id IN (:igrejaIds)
          AND m.deleted_at IS NULL
          AND m.data_movimentacao < :inicioAno
        """, nativeQuery = true)
    BigDecimal saldoAntesDeVariasIgrejas(@Param("igrejaIds") List<UUID> igrejaIds,
            @Param("inicioAno") LocalDate inicioAno);
```

- [ ] **Step 4: Escrever o teste `@DataJpaTest` do consolidado**

```java
package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BalanceteRepositoryConsolidadoTest {

    @Autowired BalanceteRepository balanceteRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EntityManager entityManager;

    Igreja sede;
    Igreja congregacao;
    Usuario usuarioSede;
    Usuario usuarioCongregacao;

    @BeforeEach
    void setup() {
        sede = igrejaRepository.save(Igreja.builder()
                .nome("Sede Teste Consolidado " + UUID.randomUUID())
                .emailContato("sede-" + UUID.randomUUID() + "@teste.com")
                .build());
        congregacao = igrejaRepository.save(Igreja.builder()
                .nome("Congregacao Teste Consolidado " + UUID.randomUUID())
                .emailContato("congregacao-" + UUID.randomUUID() + "@teste.com")
                .igrejaMae(sede)
                .build());

        Role role = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        usuarioSede = usuarioRepository.save(Usuario.builder().igreja(sede)
                .pessoa(pessoaRepository.save(Pessoa.builder().igreja(sede).nome("Tesoureiro Sede").vinculo(Vinculo.MEMBRO).build()))
                .role(role).ativo(true).build());
        usuarioCongregacao = usuarioRepository.save(Usuario.builder().igreja(congregacao)
                .pessoa(pessoaRepository.save(Pessoa.builder().igreja(congregacao).nome("Tesoureiro Congregacao").vinculo(Vinculo.MEMBRO).build()))
                .role(role).ativo(true).build());
        entityManager.flush();
    }

    @Test
    void casaCategoriasDeNomeIgualEntreIgrejasDiferentesIgnorandoAcentoECaixa() {
        CategoriaFinanceira dizimosSede = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(sede).nome("Dízimos").tipo(TipoCategoria.ENTRADA).build());
        CategoriaFinanceira dizimosCongregacao = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(congregacao).nome("dizimos").tipo(TipoCategoria.ENTRADA).build());

        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(sede).categoria(dizimosSede).criadoPor(usuarioSede)
                .tipo(TipoMovimentacao.ENTRADA).valor(new BigDecimal("100.00"))
                .dataMovimentacao(LocalDate.of(2026, 1, 5)).build());
        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(congregacao).categoria(dizimosCongregacao).criadoPor(usuarioCongregacao)
                .tipo(TipoMovimentacao.ENTRADA).valor(new BigDecimal("50.00"))
                .dataMovimentacao(LocalDate.of(2026, 1, 10)).build());
        entityManager.flush();
        entityManager.clear();

        List<BalanceteProjections.LinhaMensalConsolidada> linhas =
                balanceteRepository.agregarConsolidadoPorCategoriaEMes(
                        List.of(sede.getId(), congregacao.getId()), 2026);

        assertThat(linhas).hasSize(1); // uma única linha, casada por nome normalizado
        assertThat(linhas.get(0).getTotal()).isEqualByComparingTo("150.00");
    }
}
```

- [ ] **Step 5: Rodar os testes**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=BalanceteRepositoryConsolidadoTest`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/BalanceteFamiliaResponseDTO.java src/main/java/com/domus/api/modules/financeiro/balancete/DTOs/BalanceteIgrejaDTO.java src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteProjections.java src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteRepository.java src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteRepositoryConsolidadoTest.java
git commit -m "feat: query de consolidado do balancete casando categoria por nome normalizado"
```

---

### Task 6: Consolidado — service + controller + testes de permissão

**Files:**
- Modify: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteService.java`
  (adicionar `gerarFamilia`)
- Modify: `src/main/java/com/domus/api/modules/financeiro/balancete/BalanceteController.java`
  (adicionar endpoint `/congregacoes`)
- Modify: `src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteServiceTest.java`
  (testes do `gerarFamilia`)

**Interfaces:**
- Consumes: `FamiliaIgrejaService.idsDaFamilia(UUID)`, `FamiliaIgrejaService.ehFilha(UUID)`
  (já existem, `com.domus.api.modules.igreja.familia`); `IgrejaRepository.findAllById(...)`
  (herdado de `JpaRepository`); `CategoriaFinanceiraRepository.buscarTodasPorIgreja(UUID)`.
- Produces: `BalanceteService.gerarFamilia(UUID igrejaSedeId, int ano)` →
  `BalanceteFamiliaResponseDTO`; `GET /relatorios/balancete-anual/congregacoes?ano=2026`.

- [ ] **Step 1: Adicionar os testes do `gerarFamilia` em `BalanceteServiceTest`**

```java
    @Test
    void categoriaConsolidadaSoMarcaArquivadaSeNaoAtivaEmNenhumaIgrejaDaFamilia() {
        UUID sedeId = UUID.randomUUID();
        UUID congregacaoId = UUID.randomUUID();
        UUID igrejaMaeParaChamada = sedeId;

        when(familiaIgrejaService.idsDaFamilia(sedeId)).thenReturn(List.of(sedeId, congregacaoId));
        when(igrejaRepository.findAllById(List.of(sedeId, congregacaoId))).thenReturn(List.of(
                Igreja.builder().id(sedeId).nome("Sede").build(),
                Igreja.builder().id(congregacaoId).nome("Congregacao").igrejaMae(Igreja.builder().id(sedeId).build()).build()
        ));
        when(categoriaRepository.buscarTodasPorIgreja(sedeId)).thenReturn(List.of());
        when(categoriaRepository.buscarTodasPorIgreja(congregacaoId)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(sedeId, 2026)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(congregacaoId, 2026)).thenReturn(List.of());
        when(repository.saldoAntesDe(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.saldoAntesDeVariasIgrejas(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.agregarConsolidadoPorCategoriaEMes(List.of(sedeId, congregacaoId), 2026))
                .thenReturn(List.of());

        BalanceteFamiliaResponseDTO resultado = service.gerarFamilia(igrejaMaeParaChamada, 2026);

        assertThat(resultado.porIgreja()).hasSize(2);
        assertThat(resultado.porIgreja().get(0).ehSede()).isTrue();
        assertThat(resultado.porIgreja().get(1).ehSede()).isFalse();
        assertThat(resultado.consolidado().entradas()).isEmpty();
    }
```

Adicionar os campos/mocks novos no `@BeforeEach`:

```java
    FamiliaIgrejaService familiaIgrejaService;
    IgrejaRepository igrejaRepository;

    @BeforeEach
    void setup() {
        repository = mock(BalanceteRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        igrejaRepository = mock(IgrejaRepository.class);
        service = new BalanceteService(repository, categoriaRepository, familiaIgrejaService, igrejaRepository);
        when(repository.saldoAntesDe(eq(igrejaId), any())).thenReturn(BigDecimal.ZERO);
    }
```

(Isso muda o construtor de `BalanceteService` — ajustar os testes já existentes da Task 3
para passar os dois mocks novos também, mesmo que não usados neles.)

- [ ] **Step 2: Rodar e ver falhar (compilação, construtor mudou)**

Run: `mvn -q test -Dtest=BalanceteServiceTest`
Expected: FAIL (erro de compilação — `gerarFamilia` e construtor de 4 argumentos ainda
não existem)

- [ ] **Step 3: Implementar `gerarFamilia` em `BalanceteService`**

```java
@Service
@RequiredArgsConstructor
public class BalanceteService {

    private final BalanceteRepository repository;
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final FamiliaIgrejaService familiaIgrejaService;
    private final IgrejaRepository igrejaRepository;

    // ... gerar(...) e helpers existentes da Task 3, sem alteração ...

    @Transactional(readOnly = true)
    public BalanceteFamiliaResponseDTO gerarFamilia(UUID igrejaSedeId, int ano) {
        List<UUID> idsFamilia = familiaIgrejaService.idsDaFamilia(igrejaSedeId);
        List<Igreja> igrejas = igrejaRepository.findAllById(idsFamilia);

        List<BalanceteIgrejaDTO> porIgreja = igrejas.stream()
                .map(igreja -> new BalanceteIgrejaDTO(
                        igreja.getId(),
                        igreja.getNome(),
                        igreja.getIgrejaMae() == null,
                        gerar(igreja.getId(), ano)))
                .toList();

        return new BalanceteFamiliaResponseDTO(porIgreja, gerarConsolidado(idsFamilia, ano));
    }

    private BalanceteResponseDTO gerarConsolidado(List<UUID> igrejaIds, int ano) {
        BigDecimal saldoAbertura = repository.saldoAntesDeVariasIgrejas(igrejaIds, LocalDate.of(ano, 1, 1));
        List<BalanceteProjections.LinhaMensalConsolidada> linhas =
                repository.agregarConsolidadoPorCategoriaEMes(igrejaIds, ano);

        Set<String> nomesAtivosNormalizados = new HashSet<>();
        for (UUID igrejaId : igrejaIds) {
            for (CategoriaFinanceira c : categoriaRepository.buscarTodasPorIgreja(igrejaId)) {
                nomesAtivosNormalizados.add(normalizar(c.getNome()));
            }
        }

        Map<String, Map<String, BigDecimal[]>> valoresPorChave = new LinkedHashMap<>();
        Map<String, String> nomesPorChave = new HashMap<>();
        for (var l : linhas) {
            valoresPorChave
                    .computeIfAbsent(l.getChave(), k -> new HashMap<>())
                    .computeIfAbsent(l.getTipo(), k -> arrayZerado())[l.getMes() - 1] = l.getTotal();
            nomesPorChave.put(l.getChave(), l.getNomeExibicao());
        }

        List<LinhaCategoriaDTO> entradas = new ArrayList<>();
        List<LinhaCategoriaDTO> saidas = new ArrayList<>();
        for (String chave : valoresPorChave.keySet()) {
            boolean arquivada = !nomesAtivosNormalizados.contains(chave);
            Map<String, BigDecimal[]> porTipo = valoresPorChave.get(chave);
            String nome = nomesPorChave.get(chave);
            if (porTipo.containsKey("ENTRADA")) {
                entradas.add(montarLinhaConsolidada(nome, arquivada, porTipo.get("ENTRADA")));
            }
            if (porTipo.containsKey("SAIDA")) {
                saidas.add(montarLinhaConsolidada(nome, arquivada, porTipo.get("SAIDA")));
            }
        }

        List<BigDecimal> subtotalEntradas = somarPorMes(entradas);
        List<BigDecimal> subtotalSaidas = somarPorMes(saidas);
        List<BigDecimal> saldoDoMes = new ArrayList<>();
        List<BigDecimal> saldoAcumulado = new ArrayList<>();
        BigDecimal acumulado = saldoAbertura;
        for (int i = 0; i < 12; i++) {
            BigDecimal saldo = subtotalEntradas.get(i).subtract(subtotalSaidas.get(i));
            saldoDoMes.add(saldo);
            acumulado = acumulado.add(saldo);
            saldoAcumulado.add(acumulado);
        }

        return new BalanceteResponseDTO(ano, saldoAbertura, entradas, saidas,
                subtotalEntradas, subtotalSaidas, saldoDoMes, saldoAcumulado);
    }

    private LinhaCategoriaDTO montarLinhaConsolidada(String nome, boolean arquivada, BigDecimal[] valores) {
        List<BigDecimal> lista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : valores) {
            lista.add(v);
            total = total.add(v);
        }
        return new LinhaCategoriaDTO(null, nome, arquivada, lista, total);
    }

    private String normalizar(String nome) {
        return java.text.Normalizer.normalize(nome, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
    }
}
```

`categoriaId` fica `null` nas linhas consolidadas (documentado na Task 1) — não faz
sentido apontar pra uma única categoria quando a linha soma N igrejas.

- [ ] **Step 4: Adicionar o endpoint no controller**

```java
@RestController
@RequestMapping("/relatorios")
@RequiredArgsConstructor
public class BalanceteController {

    private final BalanceteService service;
    private final UsuarioAutenticado usuarioAutenticado;
    private final FamiliaIgrejaService familiaIgrejaService;

    private void exigirFinanceiro() {
        if (!Permissoes.podeVerFinanceiro(usuarioAutenticado.getRole(), usuarioAutenticado.getCapacidadesExtras())) {
            throw new AccessDeniedException("Só um administrador ou tesoureiro pode acessar o financeiro.");
        }
    }

    @GetMapping("/balancete-anual")
    public BalanceteResponseDTO balanceteAnual(@RequestParam int ano) {
        exigirFinanceiro();
        return service.gerar(usuarioAutenticado.getIgrejaId(), ano);
    }

    @GetMapping("/balancete-anual/congregacoes")
    public BalanceteFamiliaResponseDTO balanceteFamilia(@RequestParam int ano) {
        exigirFinanceiro();
        if (familiaIgrejaService.ehFilha(usuarioAutenticado.getIgrejaId())) {
            throw new AccessDeniedException("O balancete da família só é visível pela igreja sede.");
        }
        return service.gerarFamilia(usuarioAutenticado.getIgrejaId(), ano);
    }
}
```

- [ ] **Step 5: Rodar os testes do service e ver passar**

Run: `mvn -q test -Dtest=BalanceteServiceTest`
Expected: BUILD SUCCESS, 6 testes passando.

- [ ] **Step 6: Compilar tudo e validar manualmente com curl**

Run: `mvn -q -o compile`
```bash
# como congregação (deve dar 403):
curl -i --cookie "domus_access=<token-congregacao>" "http://localhost:8080/relatorios/balancete-anual/congregacoes?ano=2026"
# como sede admin/tesoureiro (deve dar 200):
curl -i --cookie "domus_access=<token-sede>" "http://localhost:8080/relatorios/balancete-anual/congregacoes?ano=2026"
```
Expected: 403 pra congregação, 200 com `porIgreja` + `consolidado` pra sede.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/balancete/ src/test/java/com/domus/api/modules/financeiro/balancete/BalanceteServiceTest.java
git commit -m "feat: endpoint GET /relatorios/balancete-anual/congregacoes (consolidado da familia)"
```

---

### Task 7: Frontend — types, service, hooks

**Files:**
- Create: `frontend/src/types/financeiro/balancete.type.ts`
- Create: `frontend/src/services/financeiro/balancete.service.ts`
- Create: `frontend/src/hooks/financeiro/balancete/useBalanceteAnual.ts`
- Create: `frontend/src/hooks/financeiro/balancete/useBalanceteFamilia.ts`
- Modify: `frontend/src/lib/endpoints.ts` (adicionar `balanceteAnual`/`balanceteFamilia`
  dentro do bloco `relatorios`)

**Interfaces:**
- Produces: `LinhaCategoria`, `Balancete`, `BalanceteIgreja`, `BalanceteFamilia` (types);
  `balanceteService.anual(ano: number, igrejaId?: string): Promise<Balancete>`,
  `balanceteService.familia(ano: number): Promise<BalanceteFamilia>`;
  `useBalanceteAnual(ano: number, enabled?: boolean, igrejaId?: string)`,
  `useBalanceteFamilia(ano: number, enabled?: boolean)` — hooks usados pela Task 8.

- [ ] **Step 1: Adicionar os endpoints em `endpoints.ts`**

```ts
  relatorios: {
    resumo: '/relatorios/resumo',
    porCategoria: '/relatorios/por-categoria',
    evolucaoMensal: '/relatorios/evolucao-mensal',
    maiorLancamento: '/relatorios/maior-lancamento',
    porContribuinte: '/relatorios/por-contribuinte',
    congregacoes: '/relatorios/congregacoes',
    balanceteAnual: '/relatorios/balancete-anual',
    balanceteFamilia: '/relatorios/balancete-anual/congregacoes',
  },
```

- [ ] **Step 2: Criar `balancete.type.ts`**

```ts
export interface LinhaCategoria {
  categoriaId: string | null
  nomeCategoria: string
  arquivada: boolean
  valoresPorMes: string[]  // 12 posições, jan..dez
  totalAno: string
}

export interface Balancete {
  ano: number
  saldoAbertura: string
  entradas: LinhaCategoria[]
  saidas: LinhaCategoria[]
  subtotalEntradasPorMes: string[]
  subtotalSaidasPorMes: string[]
  saldoDoMes: string[]
  saldoAcumulado: string[]
}

export interface BalanceteIgreja {
  igrejaId: string
  nomeIgreja: string
  ehSede: boolean
  balancete: Balancete
}

export interface BalanceteFamilia {
  porIgreja: BalanceteIgreja[]
  consolidado: Balancete
}
```

- [ ] **Step 3: Criar `balancete.service.ts`**

```ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { Balancete, BalanceteFamilia } from '@/types/financeiro/balancete.type'

export const balanceteService = {
  anual: async (ano: number, igrejaId?: string): Promise<Balancete> => {
    const { data } = await api.get(Endpoints.relatorios.balanceteAnual, {
      params: { ano, ...(igrejaId ? { igrejaId } : {}) },
    })
    return data
  },

  familia: async (ano: number): Promise<BalanceteFamilia> => {
    const { data } = await api.get(Endpoints.relatorios.balanceteFamilia, { params: { ano } })
    return data
  },
}
```

(O endpoint próprio `/relatorios/balancete-anual` não tem `igrejaId` como parâmetro no
back — Task 4 escopa sempre pela igreja do JWT. O `igrejaId` no service fica documentado
aqui mas não é enviado; remove-lo simplifica — ver Step 3 revisado abaixo.)

- [ ] **Step 3 (revisado): simplificar `balancete.service.ts` sem `igrejaId`**

```ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'
import type { Balancete, BalanceteFamilia } from '@/types/financeiro/balancete.type'

export const balanceteService = {
  anual: async (ano: number): Promise<Balancete> => {
    const { data } = await api.get(Endpoints.relatorios.balanceteAnual, { params: { ano } })
    return data
  },

  familia: async (ano: number): Promise<BalanceteFamilia> => {
    const { data } = await api.get(Endpoints.relatorios.balanceteFamilia, { params: { ano } })
    return data
  },
}
```

- [ ] **Step 4: Criar os hooks**

```ts
// frontend/src/hooks/financeiro/balancete/useBalanceteAnual.ts
import { useQuery } from '@tanstack/react-query'
import { balanceteService } from '@/services/financeiro/balancete.service'

export function useBalanceteAnual(ano: number, enabled = true) {
  return useQuery({
    queryKey: ['relatorios', 'balancete-anual', ano],
    queryFn: () => balanceteService.anual(ano),
    enabled,
  })
}
```

```ts
// frontend/src/hooks/financeiro/balancete/useBalanceteFamilia.ts
import { useQuery } from '@tanstack/react-query'
import { balanceteService } from '@/services/financeiro/balancete.service'

export function useBalanceteFamilia(ano: number, enabled = true) {
  return useQuery({
    queryKey: ['relatorios', 'balancete-anual', 'familia', ano],
    queryFn: () => balanceteService.familia(ano),
    enabled,
  })
}
```

- [ ] **Step 5: Rodar o type-check do front**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos relacionados a `balancete.type.ts`/`balancete.service.ts`.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/types/financeiro/balancete.type.ts frontend/src/services/financeiro/balancete.service.ts frontend/src/hooks/financeiro/balancete/ frontend/src/lib/endpoints.ts
git commit -m "feat: types/service/hooks do balancete anual no front"
```

---

### Task 8: Frontend — tela do balancete anual

**Files:**
- Create: `frontend/src/app/(app)/financeiro/relatorios/balancete/page.tsx`
- Create: `frontend/src/app/(app)/financeiro/relatorios/balancete/BalanceteTabela.tsx`
- Create: `frontend/src/app/(app)/financeiro/relatorios/balancete/BalanceteTabela.module.css`
- Create: `frontend/src/app/(app)/financeiro/relatorios/balancete/BalanceteCardsMes.tsx`
  (visão mobile — card por mês)
- Create: `frontend/src/app/(app)/financeiro/relatorios/balancete/BalanceteCardsMes.module.css`
- Modify: `frontend/src/app/(app)/financeiro/relatorios/page.tsx` (link "Ver balancete
  anual" perto do `GraficoEvolucao`)

**Interfaces:**
- Consumes: `useBalanceteAnual`, `useBalanceteFamilia` (Task 7); `useVinculoStatus` (já
  existe, usado em `page.tsx` pra saber `ehMae`); `podeVerFinanceiro` (já existe em
  `@/lib/permissoes`); `AcessoRestrito` (já existe em `@/components/common`).

- [ ] **Step 1: Criar `BalanceteTabela.tsx` (visão desktop)**

```tsx
'use client'

import type { Balancete, LinhaCategoria } from '@/types/financeiro/balancete.type'
import styles from './BalanceteTabela.module.css'

const MESES = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez']

function formatarValor(valor: string) {
  return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

function LinhaCategoriaRow({ linha }: { linha: LinhaCategoria }) {
  const zerada = Number(linha.totalAno) === 0
  return (
    <tr className={zerada ? styles.linhaZerada : undefined}>
      <td>
        {linha.nomeCategoria}
        {linha.arquivada && <span className={styles.seloArquivada}>Arquivada</span>}
      </td>
      {linha.valoresPorMes.map((v, i) => (
        <td key={i}>{formatarValor(v)}</td>
      ))}
      <td className={styles.total}>{formatarValor(linha.totalAno)}</td>
    </tr>
  )
}

export function BalanceteTabela({ balancete }: { balancete: Balancete }) {
  return (
    <div className={styles.wrapper}>
      <table className={styles.tabela}>
        <thead>
          <tr>
            <th>Categoria</th>
            {MESES.map((m) => (
              <th key={m}>{m}</th>
            ))}
            <th>Total</th>
          </tr>
        </thead>
        <tbody>
          <tr className={styles.secao}>
            <td colSpan={14}>Entradas</td>
          </tr>
          {balancete.entradas.map((l) => (
            <LinhaCategoriaRow key={l.categoriaId ?? l.nomeCategoria} linha={l} />
          ))}
          <tr className={styles.subtotal}>
            <td>Subtotal Entradas</td>
            {balancete.subtotalEntradasPorMes.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>

          <tr className={styles.secao}>
            <td colSpan={14}>Saídas</td>
          </tr>
          {balancete.saidas.map((l) => (
            <LinhaCategoriaRow key={l.categoriaId ?? l.nomeCategoria} linha={l} />
          ))}
          <tr className={styles.subtotal}>
            <td>Subtotal Saídas</td>
            {balancete.subtotalSaidasPorMes.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>

          <tr className={styles.saldoMes}>
            <td>Saldo do Mês</td>
            {balancete.saldoDoMes.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>
          <tr className={styles.saldoAcumulado}>
            <td>Saldo Acumulado</td>
            {balancete.saldoAcumulado.map((v, i) => (
              <td key={i}>{formatarValor(v)}</td>
            ))}
            <td />
          </tr>
        </tbody>
      </table>
    </div>
  )
}
```

- [ ] **Step 2: CSS base de `BalanceteTabela.module.css`**

```css
.wrapper {
  overflow-x: auto;
}

.tabela {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}

.tabela th,
.tabela td {
  padding: 0.5rem 0.75rem;
  text-align: right;
  white-space: nowrap;
}

.tabela th:first-child,
.tabela td:first-child {
  text-align: left;
}

.secao td {
  font-weight: 700;
  background: var(--cor-fundo-secao, #f3f4f6);
}

.linhaZerada {
  color: var(--cor-texto-muted, #9ca3af);
}

.seloArquivada {
  margin-left: 0.5rem;
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  border-radius: 999px;
  background: var(--cor-fundo-badge, #e5e7eb);
  color: var(--cor-texto-badge, #4b5563);
}

.subtotal td {
  font-weight: 600;
  border-top: 1px solid var(--cor-borda, #e5e7eb);
}

.saldoMes td {
  font-weight: 600;
}

.saldoAcumulado td {
  font-weight: 700;
  background: var(--cor-destaque, #dbeafe);
}

.total {
  font-weight: 600;
}
```

- [ ] **Step 3: Criar `BalanceteCardsMes.tsx` (visão mobile — card por mês)**

```tsx
'use client'

import type { Balancete } from '@/types/financeiro/balancete.type'
import styles from './BalanceteCardsMes.module.css'

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro']

function formatarValor(valor: string) {
  return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
}

export function BalanceteCardsMes({ balancete }: { balancete: Balancete }) {
  return (
    <div className={styles.lista}>
      {MESES.map((nomeMes, i) => (
        <div key={nomeMes} className={styles.card}>
          <h3>{nomeMes}</h3>
          <div className={styles.linha}>
            <span>Total Entradas</span>
            <strong className={styles.entrada}>{formatarValor(balancete.subtotalEntradasPorMes[i])}</strong>
          </div>
          <div className={styles.linha}>
            <span>Total Saídas</span>
            <strong className={styles.saida}>{formatarValor(balancete.subtotalSaidasPorMes[i])}</strong>
          </div>
          <div className={styles.linha}>
            <span>Saldo do Mês</span>
            <strong>{formatarValor(balancete.saldoDoMes[i])}</strong>
          </div>
          <div className={styles.saldoAcumulado}>
            <span>Saldo Acumulado</span>
            <strong>{formatarValor(balancete.saldoAcumulado[i])}</strong>
          </div>
        </div>
      ))}
    </div>
  )
}
```

- [ ] **Step 4: CSS de `BalanceteCardsMes.module.css` (mobile-first, esconde acima de md)**

```css
.lista {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

@media (min-width: 768px) {
  .lista {
    display: none;
  }
}

.card {
  border: 1px solid var(--cor-borda, #e5e7eb);
  border-radius: 0.5rem;
  padding: 1rem;
}

.card h3 {
  margin: 0 0 0.5rem;
  font-size: 0.95rem;
}

.linha {
  display: flex;
  justify-content: space-between;
  padding: 0.25rem 0;
}

.entrada {
  color: var(--cor-entrada, #15803d);
}

.saida {
  color: var(--cor-saida, #b91c1c);
}

.saldoAcumulado {
  display: flex;
  justify-content: space-between;
  margin-top: 0.5rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--cor-borda, #e5e7eb);
  font-weight: 700;
}
```

- [ ] **Step 5: Criar `page.tsx` da tela de balancete**

```tsx
'use client'

import { useState } from 'react'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { useBalanceteAnual } from '@/hooks/financeiro/balancete/useBalanceteAnual'
import { useBalanceteFamilia } from '@/hooks/financeiro/balancete/useBalanceteFamilia'
import { BalanceteTabela } from './BalanceteTabela'
import { BalanceteCardsMes } from './BalanceteCardsMes'
import styles from './balancete.module.css'

type Aba = 'MINHA_IGREJA' | 'CONSOLIDADO' | 'POR_CONGREGACAO'

export default function BalanceteAnualPage() {
  const [ano, setAno] = useState(new Date().getFullYear())
  const [aba, setAba] = useState<Aba>('MINHA_IGREJA')

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)

  const vinculo = useVinculoStatus(autorizado)
  const ehSede = vinculo.data?.estado === 'MAE'

  const balancetePropria = useBalanceteAnual(ano, autorizado && aba === 'MINHA_IGREJA')
  const balanceteFamilia = useBalanceteFamilia(ano, autorizado && ehSede && aba !== 'MINHA_IGREJA')

  if (!hidratado) return null
  if (!autorizado) return <AcessoRestrito mensagem="Só um administrador ou tesoureiro pode ver o balancete anual." />

  return (
    <div className={styles.pagina}>
      <header className={styles.header}>
        <h1>Balancete Anual</h1>
        <div className={styles.seletorAno}>
          <button onClick={() => setAno((a) => a - 1)}>‹</button>
          <strong>{ano}</strong>
          <button onClick={() => setAno((a) => a + 1)}>›</button>
        </div>
      </header>

      {ehSede && (
        <div role="tablist" className={styles.abas}>
          <button role="tab" aria-selected={aba === 'MINHA_IGREJA'} className={aba === 'MINHA_IGREJA' ? styles.abaAtiva : styles.aba} onClick={() => setAba('MINHA_IGREJA')}>
            Minha Igreja
          </button>
          <button role="tab" aria-selected={aba === 'CONSOLIDADO'} className={aba === 'CONSOLIDADO' ? styles.abaAtiva : styles.aba} onClick={() => setAba('CONSOLIDADO')}>
            Consolidado
          </button>
          <button role="tab" aria-selected={aba === 'POR_CONGREGACAO'} className={aba === 'POR_CONGREGACAO' ? styles.abaAtiva : styles.aba} onClick={() => setAba('POR_CONGREGACAO')}>
            Por Congregação
          </button>
        </div>
      )}

      {aba === 'MINHA_IGREJA' && balancetePropria.data && (
        <>
          <BalanceteTabela balancete={balancetePropria.data} />
          <BalanceteCardsMes balancete={balancetePropria.data} />
        </>
      )}

      {aba === 'CONSOLIDADO' && balanceteFamilia.data && (
        <>
          <BalanceteTabela balancete={balanceteFamilia.data.consolidado} />
          <BalanceteCardsMes balancete={balanceteFamilia.data.consolidado} />
        </>
      )}

      {aba === 'POR_CONGREGACAO' && balanceteFamilia.data && (
        <div className={styles.listaIgrejas}>
          {balanceteFamilia.data.porIgreja.map((item) => (
            <section key={item.igrejaId} className={styles.blocoIgreja}>
              <h2>{item.nomeIgreja}{item.ehSede ? ' (Sede)' : ''}</h2>
              <BalanceteTabela balancete={item.balancete} />
              <BalanceteCardsMes balancete={item.balancete} />
            </section>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 6: CSS de `balancete.module.css`**

```css
.pagina {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.seletorAno {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.abas {
  display: flex;
  gap: 0.5rem;
  border-bottom: 1px solid var(--cor-borda, #e5e7eb);
  overflow-x: auto;
}

.aba,
.abaAtiva {
  padding: 0.5rem 1rem;
  border: none;
  background: none;
  cursor: pointer;
  white-space: nowrap;
}

.abaAtiva {
  border-bottom: 2px solid var(--cor-primaria, #2563eb);
  font-weight: 600;
}

.listaIgrejas {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.blocoIgreja h2 {
  margin-bottom: 0.5rem;
}
```

Nota de responsividade: `BalanceteTabela` (desktop) e `BalanceteCardsMes` (mobile) são
renderizados juntos e alternam via CSS (`BalanceteCardsMes.module.css` já esconde a lista
acima de `768px`) — falta esconder a tabela abaixo de `768px`. Adicionar em
`BalanceteTabela.module.css`:

```css
@media (max-width: 767px) {
  .wrapper {
    display: none;
  }
}
```

- [ ] **Step 7: Adicionar o link a partir da tela de relatórios existente**

Em `frontend/src/app/(app)/financeiro/relatorios/page.tsx`, perto de `<GraficoEvolucao ... />`,
adicionar:

```tsx
import Link from 'next/link'
// ...
<Link href="/financeiro/relatorios/balancete" className={styles.linkBalancete}>
  Ver balancete anual →
</Link>
```

E em `relatorios.module.css`:

```css
.linkBalancete {
  display: inline-block;
  margin-top: 0.5rem;
  color: var(--cor-primaria, #2563eb);
  font-weight: 600;
}
```

- [ ] **Step 8: Rodar o dev server e validar manualmente no navegador**

Run: `cd frontend && npm run dev`
Testar: acessar `/financeiro/relatorios/balancete` logado como admin de uma igreja
independente (só vê "Minha Igreja", sem abas), depois como admin/tesoureiro da sede de
uma família (vê as 3 abas), e depois como congregação (mesmo se tentar acessar direto a
URL com abas de família, o hook `useBalanceteFamilia` não deve disparar — `ehSede` falso
mantém `enabled: false`). Validar em viewport de celular (DevTools, 375px): tabela some,
cards por mês aparecem.

- [ ] **Step 9: Rodar o type-check do front**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros.

- [ ] **Step 10: Commit**

```bash
git add frontend/src/app/\(app\)/financeiro/relatorios/balancete/ frontend/src/app/\(app\)/financeiro/relatorios/page.tsx frontend/src/app/\(app\)/financeiro/relatorios/relatorios.module.css
git commit -m "feat: tela de balancete anual no front (abas, tabela e cards mobile)"
```

---

## Self-review (feito ao escrever este plano)

- **Cobertura do spec:** endpoints (Tasks 4, 6), saldo de abertura (Task 2/3), matriz
  categoria×mês (Task 2/3), categoria zerada/arquivada (Task 3), categoria AMBOS em duas
  linhas (Task 3), consolidado casando por nome normalizado (Task 5/6), permissões
  (Task 4/6), frontend com abas + mobile (Task 7/8), export desabilitado — **não incluído
  como task** porque o spec marcou exportação como fora de escopo; o botão "Exportar"
  também ficou fora do desenho do front deste plano por ser cosmético e não bloquear a
  entrega funcional — se o autor quiser o placeholder visual, é um ajuste pequeno na
  Task 8.
- **Sem migration:** confirmado que os índices existentes cobrem a query — nenhuma task
  de migration foi criada.
- **Consistência de tipos:** `BalanceteService` muda de construtor de 2 para 4 argumentos
  entre Task 3 e Task 6 — a Task 6 já avisa explicitamente para atualizar o
  `@BeforeEach` da Task 3 (evita o teste antigo quebrar por engano de assinatura).
