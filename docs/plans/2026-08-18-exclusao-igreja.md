# Excluir Igreja (Conta) — Plano de Implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que `ADMIN_IGREJA` agende a exclusão definitiva da própria igreja (tenant inteiro), com carência de 10 dias cancelável, reautenticação obrigatória, e-mails nos momentos-chave, e uma purga tabela-por-tabela explícita (sem `ON DELETE CASCADE`) executada por um job diário.

**Architecture:** Duas colunas novas em `igreja` (`exclusao_agendada_em`, `exclusao_agendada_por_usuario_id`) guardam o estado "agendada". Um novo módulo `com.domus.api.modules.igreja.exclusao` concentra `ExclusaoIgrejaService` (agendar/cancelar/resumo/reautenticação) e `ExclusaoIgrejaJob` (`@Scheduled`, mesmo padrão de `LimpezaFotosJob`) que dispara e-mails de lembrete e, quando o prazo vence, chama `PurgaIgrejaService.purgar(igrejaId)` — uma sequência ordenada de `DELETE ... WHERE igreja_id = ?` nativos, um método novo por repositório, dentro de uma única `@Transactional`. Fotos usam `FotoService.remover` (já existe). Elasticsearch é limpo via `deleteByIgrejaId` novo em cada `*SearchRepository`, fora da transação do Postgres.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA (queries nativas `@Modifying`), Spring Data Elasticsearch, Flyway, `@Scheduled`, `PasswordEncoder` (bcrypt), `GoogleIdTokenVerifier`, `EmailService` (Resend), Next.js/TypeScript/Zustand no front.

**Spec:** `backend/api/docs/superpowers/specs/2026-08-18-exclusao-igreja-design.md`

## Global Constraints

- Só `ADMIN_IGREJA` pode chamar qualquer endpoint deste módulo (usar `Permissoes` — capacidade, não string de role solta).
- `igrejaId` sempre vem de `UsuarioAutenticado.getIgrejaId()` (JWT), nunca do corpo da requisição.
- **Nenhuma FK ganha `ON DELETE CASCADE`.** A purga é sempre `DELETE ... WHERE igreja_id = ?` explícito, um método por tabela, na ordem definida na spec — nunca dependente de cascade do banco.
- A purga inteira (exceto fotos no R2 e Elasticsearch) roda em **uma única `@Transactional`**: se qualquer passo falhar, tudo desfaz e o job tenta de novo no dia seguinte.
- R2 (fotos) e Elasticsearch ficam **fora** da transação Postgres — melhor-esforço, falha só loga alto.
- Cancelar a exclusão **não** exige reautenticação. Agendar **exige**.
- Serviços retornam DTOs, nunca entidades. Soft delete não se aplica aqui — isto é hard delete agendado.
- Testes de service usam Mockito puro (Estilo A: `mock()` manual no `@BeforeEach`), seguindo o padrão dominante do projeto.

---

## Fase 1 — Schema + Agendar/Cancelar + Job (sem purga)

### Task 1: Migration V19 — colunas de exclusão agendada

**Files:**
- Create: `src/main/resources/db/migration/V19__exclusao_igreja.sql`
- Modify: `src/main/java/com/domus/api/modules/igreja/Igreja.java`
- Test: nenhum (migration é validada rodando os testes existentes contra o Neon de teste)

**Interfaces:**
- Consumes: nada.
- Produces: `Igreja.exclusaoAgendadaEm` (`LocalDateTime`), `Igreja.exclusaoAgendadaPor` (`Usuario`) — usados por todas as tasks seguintes.

- [ ] **Step 1: Criar a migration**

```sql
-- V19__exclusao_igreja.sql
ALTER TABLE igreja
  ADD COLUMN exclusao_agendada_em TIMESTAMP,
  ADD COLUMN exclusao_agendada_por_usuario_id UUID REFERENCES usuario(id);
```

- [ ] **Step 2: Adicionar os campos na entidade `Igreja`**

Em `src/main/java/com/domus/api/modules/igreja/Igreja.java`, adicionar após o campo `updatedAt`:

```java
    @Column(name = "exclusao_agendada_em")
    private LocalDateTime exclusaoAgendadaEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exclusao_agendada_por_usuario_id")
    private Usuario exclusaoAgendadaPor;
```

- [ ] **Step 3: Rodar os testes existentes para confirmar que a migration aplica sem quebrar nada**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=IgrejaServiceTest`
Expected: PASS (a suíte já existente continua passando com as colunas novas, que são nuláveis).

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V19__exclusao_igreja.sql src/main/java/com/domus/api/modules/igreja/Igreja.java
git commit -m "feat(igreja): schema pra exclusão agendada da igreja"
```

---

### Task 2: `IgrejaRepository` — consultas de exclusão agendada

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaRepository.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaServiceTest.java` (criado na Task 3, cobre estas queries indiretamente via mock)

**Interfaces:**
- Consumes: `Igreja` (Task 1).
- Produces: `IgrejaRepository.buscarComExclusaoAgendada()`, `IgrejaRepository.marcarExclusaoAgendada(UUID, UUID, LocalDateTime)`, `IgrejaRepository.cancelarExclusaoAgendada(UUID)` — usados por `ExclusaoIgrejaService` (Task 3) e `ExclusaoIgrejaJob` (Task 4).

- [ ] **Step 1: Adicionar os métodos ao `IgrejaRepository`**

```java
    /** Toda igreja com exclusão agendada — o job varre esta lista todo dia. */
    @Query("SELECT i FROM Igreja i WHERE i.exclusaoAgendadaEm IS NOT NULL")
    List<Igreja> buscarComExclusaoAgendada();

    @Modifying
    @Query(value = """
        UPDATE igreja
           SET exclusao_agendada_em = :agora, exclusao_agendada_por_usuario_id = :usuarioId
         WHERE id = :igrejaId
        """, nativeQuery = true)
    void marcarExclusaoAgendada(@Param("igrejaId") UUID igrejaId,
                                 @Param("usuarioId") UUID usuarioId,
                                 @Param("agora") java.time.LocalDateTime agora);

    @Modifying
    @Query(value = """
        UPDATE igreja
           SET exclusao_agendada_em = NULL, exclusao_agendada_por_usuario_id = NULL
         WHERE id = :igrejaId
        """, nativeQuery = true)
    void cancelarExclusaoAgendada(@Param("igrejaId") UUID igrejaId);
```

Import necessário no topo do arquivo: `java.time.LocalDateTime` já pode ser referenciado com nome qualificado como acima, sem novo import (mantém consistência com o resto do arquivo que já usa `UUID` importado e tipos qualificados em métodos como `desvincularUsuario`).

- [ ] **Step 2: Compilar para confirmar que não há erro de sintaxe**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/IgrejaRepository.java
git commit -m "feat(igreja): queries de agendar/cancelar/listar exclusão"
```

---

### Task 3: `ExclusaoIgrejaService` — resumo, agendar (sem reautenticação ainda), cancelar

**Files:**
- Create: `src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaService.java`
- Create: `src/main/java/com/domus/api/modules/igreja/exclusao/DTO/ResumoExclusaoResponse.java`
- Create: `src/main/java/com/domus/api/modules/igreja/exclusao/DTO/AgendarExclusaoRequest.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaServiceTest.java`

**Interfaces:**
- Consumes: `IgrejaRepository` (Task 2), `PessoaRepository.countByIgrejaId`/similar contagens já existentes no projeto para outros módulos (ver Step 1), `IgrejaRepository.buscarIdsDasFilhas` (já existe, `IgrejaRepository.java:32`).
- Produces: `ExclusaoIgrejaService.resumo(UUID igrejaId)`, `ExclusaoIgrejaService.agendar(UUID igrejaId, UUID usuarioId, String nomeConfirmacao)`, `ExclusaoIgrejaService.cancelar(UUID igrejaId)` — consumidos por `ExclusaoIgrejaController` (Task 5) e estendidos com reautenticação na Task 10.

- [ ] **Step 1: Escrever o teste que prova agendar/cancelar/resumo**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExclusaoIgrejaServiceTest {

    IgrejaRepository igrejaRepository;
    PessoaRepository pessoaRepository;
    EventoRepository eventoRepository;
    MovimentacaoFinanceiraRepository movimentacaoRepository;
    CelulaRepository celulaRepository;
    MinisterioRepository ministerioRepository;
    UsuarioRepository usuarioRepository;
    EmailService emailService;
    ExclusaoIgrejaService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        eventoRepository = mock(EventoRepository.class);
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        celulaRepository = mock(CelulaRepository.class);
        ministerioRepository = mock(MinisterioRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        emailService = mock(EmailService.class);
        service = new ExclusaoIgrejaService(igrejaRepository, pessoaRepository, eventoRepository,
                movimentacaoRepository, celulaRepository, ministerioRepository, usuarioRepository, emailService);
    }

    private Igreja igreja() {
        return Igreja.builder().id(igrejaId).nome("Igreja Batista Central").emailContato("contato@igreja.com").build();
    }

    @Test
    void agendaExclusaoQuandoNomeConfere() {
        Igreja igreja = igreja();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        service.agendar(igrejaId, usuarioId, "Igreja Batista Central");

        verify(igrejaRepository).marcarExclusaoAgendada(eq(igrejaId), eq(usuarioId), any());
        verify(emailService).enviar(eq("contato@igreja.com"), anyString(), anyString());
    }

    @Test
    void recusaAgendarQuandoNomeNaoConfere() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja()));

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Nome Errado"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nome");

        verify(igrejaRepository, never()).marcarExclusaoAgendada(any(), any(), any());
    }

    @Test
    void recusaAgendarSeIgrejaNaoExiste() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Qualquer"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelaSemPrecisarDeNadaAlemDoIgrejaId() {
        service.cancelar(igrejaId);

        verify(igrejaRepository).cancelarExclusaoAgendada(igrejaId);
    }

    @Test
    void resumoContaTudoQueSeraApagado() {
        when(pessoaRepository.countByIgrejaId(igrejaId)).thenReturn(42L);
        when(eventoRepository.countByIgrejaId(igrejaId)).thenReturn(10L);
        when(movimentacaoRepository.countByIgrejaId(igrejaId)).thenReturn(200L);
        when(celulaRepository.countByIgrejaId(igrejaId)).thenReturn(5L);
        when(ministerioRepository.countByIgrejaId(igrejaId)).thenReturn(3L);
        when(usuarioRepository.countByIgrejaId(igrejaId)).thenReturn(8L);
        when(igrejaRepository.buscarIdsDasFilhas(igrejaId)).thenReturn(List.of());

        ResumoExclusaoResponse resumo = service.resumo(igrejaId);

        assertThat(resumo.pessoas()).isEqualTo(42L);
        assertThat(resumo.eventos()).isEqualTo(10L);
        assertThat(resumo.igrejasVinculadas()).isEmpty();
    }
}
```

- [ ] **Step 2: Rodar o teste e ver falhar**

Run: `mvn -q test -Dtest=ExclusaoIgrejaServiceTest`
Expected: FAIL (classes `ExclusaoIgrejaService`, `ResumoExclusaoResponse` etc. não existem)

- [ ] **Step 3: Verificar se `countByIgrejaId` já existe nos repositórios usados no resumo**

Nenhum dos repositórios (`PessoaRepository`, `EventoRepository`, `MovimentacaoFinanceiraRepository`, `CelulaRepository`, `MinisterioRepository`, `UsuarioRepository`) tem `countByIgrejaId` hoje. Adicionar em cada um — são *derived query methods* do Spring Data, uma linha por repositório, sem SQL nativo (não precisam bypassar `@SQLRestriction`: o resumo mostra só o que está ativo, é aceitável não contar arquivados aqui — a purga na Fase 2 apaga todos, ativos e arquivados, via `DELETE` nativo à parte):

Em `PessoaRepository.java`, `EventoRepository.java`, `CelulaRepository.java`, `MinisterioRepository.java`, `UsuarioRepository.java`: adicionar `long countByIgrejaId(UUID igrejaId);`
Em `MovimentacaoFinanceiraRepository.java`: adicionar `long countByIgrejaId(UUID igrejaId);` (o campo na entidade é `igreja`, então o Spring Data resolve via `igreja.id` automaticamente pelo nome do método).

- [ ] **Step 4: Criar os DTOs**

```java
// src/main/java/com/domus/api/modules/igreja/exclusao/DTO/ResumoExclusaoResponse.java
package com.domus.api.modules.igreja.exclusao.DTO;

import java.util.List;

public record ResumoExclusaoResponse(
        long pessoas,
        long eventos,
        long movimentacoesFinanceiras,
        long celulas,
        long ministerios,
        long usuarios,
        List<String> igrejasVinculadas
) {}
```

```java
// src/main/java/com/domus/api/modules/igreja/exclusao/DTO/AgendarExclusaoRequest.java
package com.domus.api.modules.igreja.exclusao.DTO;

import jakarta.validation.constraints.NotBlank;

public record AgendarExclusaoRequest(
        @NotBlank String nomeConfirmacao
) {}
```

- [ ] **Step 5: Implementar `ExclusaoIgrejaService`**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.email.EmailService;
import com.domus.api.shared.exception.BusinessException;
import com.domus.api.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Agendar/cancelar a exclusão definitiva da igreja. A purga em si vive em {@link PurgaIgrejaService}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExclusaoIgrejaService {

    private final IgrejaRepository igrejaRepository;
    private final PessoaRepository pessoaRepository;
    private final EventoRepository eventoRepository;
    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final CelulaRepository celulaRepository;
    private final MinisterioRepository ministerioRepository;
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public ResumoExclusaoResponse resumo(UUID igrejaId) {
        List<String> nomesFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId).isEmpty()
                ? List.of()
                : igrejaRepository.findAllById(igrejaRepository.buscarIdsDasFilhas(igrejaId))
                        .stream().map(Igreja::getNome).toList();

        return new ResumoExclusaoResponse(
                pessoaRepository.countByIgrejaId(igrejaId),
                eventoRepository.countByIgrejaId(igrejaId),
                movimentacaoRepository.countByIgrejaId(igrejaId),
                celulaRepository.countByIgrejaId(igrejaId),
                ministerioRepository.countByIgrejaId(igrejaId),
                usuarioRepository.countByIgrejaId(igrejaId),
                nomesFilhas
        );
    }

    @Transactional
    public void agendar(UUID igrejaId, UUID usuarioId, String nomeConfirmacao) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        if (!normalizarNome(igreja.getNome()).equals(normalizarNome(nomeConfirmacao))) {
            throw new BusinessException("NOME_NAO_CONFERE",
                    "O nome digitado não confere com o nome da igreja.");
        }

        igrejaRepository.marcarExclusaoAgendada(igrejaId, usuarioId, LocalDateTime.now());
        log.info("Exclusão agendada. igreja_id={}, por_usuario_id={}", igrejaId, usuarioId);

        emailService.enviar(igreja.getEmailContato(), "Exclusão da sua igreja no Domus foi agendada",
                "A exclusão definitiva de \"" + igreja.getNome() + "\" foi agendada e acontecerá em 10 dias. "
                        + "Você pode cancelar a qualquer momento antes disso, entrando em Configurações → Sistema.");
    }

    @Transactional
    public void cancelar(UUID igrejaId) {
        igrejaRepository.cancelarExclusaoAgendada(igrejaId);
        log.info("Exclusão cancelada. igreja_id={}", igrejaId);
    }

    private String normalizarNome(String nome) {
        return nome == null ? "" : nome.trim().toLowerCase();
    }
}
```

- [ ] **Step 6: Rodar o teste e ver passar**

Run: `mvn -q test -Dtest=ExclusaoIgrejaServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/exclusao/ src/main/java/com/domus/api/modules/pessoa/PessoaRepository.java src/main/java/com/domus/api/modules/evento/EventoRepository.java src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraRepository.java src/main/java/com/domus/api/modules/celula/CelulaRepository.java src/main/java/com/domus/api/modules/ministerio/MinisterioRepository.java src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java src/test/java/com/domus/api/modules/igreja/exclusao/
git commit -m "feat(igreja): agendar/cancelar exclusão + resumo de contagens"
```

---

### Task 4: `IgrejaDetalheDTO` e `GET /igrejas/minha` ganham `exclusaoAgendadaEm`/`diasRestantes`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/DTO/IgrejaDetalheDTO.java`
- Test: `src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java` (adicionar um caso ao arquivo existente)

**Interfaces:**
- Consumes: `Igreja.exclusaoAgendadaEm` (Task 1).
- Produces: `IgrejaDetalheDTO.exclusaoAgendadaEm` / `IgrejaDetalheDTO.diasRestantes` — consumidos pelo front na Fase 4 (banner de contagem regressiva).

- [ ] **Step 1: Escrever o teste (adicionar ao arquivo existente `IgrejaServiceTest.java`)**

```java
    @Test
    void detalheTrazDiasRestantesQuandoExclusaoAgendada() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste")
                .exclusaoAgendadaEm(LocalDateTime.now().minusDays(3))
                .build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        IgrejaDetalheDTO dto = igrejaService.buscarDetalhe(igrejaId);

        assertThat(dto.exclusaoAgendadaEm()).isNotNull();
        assertThat(dto.diasRestantes()).isEqualTo(7);
    }

    @Test
    void detalheSemExclusaoAgendadaTemDiasRestantesNulo() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Teste").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        IgrejaDetalheDTO dto = igrejaService.buscarDetalhe(igrejaId);

        assertThat(dto.exclusaoAgendadaEm()).isNull();
        assertThat(dto.diasRestantes()).isNull();
    }
```

(Adicionar `import java.time.LocalDateTime;` no topo do arquivo de teste se ainda não existir.)

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=IgrejaServiceTest`
Expected: FAIL (`exclusaoAgendadaEm`/`diasRestantes` não existem em `IgrejaDetalheDTO`)

- [ ] **Step 3: Adicionar os campos e o `from(...)` em `IgrejaDetalheDTO`**

Adicionar dois campos ao record e calcular `diasRestantes` (10 dias de carência, arredondado para cima pelo dia — `ChronoUnit.DAYS.between` mais 10, nunca negativo) dentro do método estático `from(Igreja igreja, String nomeAtualizadoPor)` já existente:

```java
        LocalDateTime exclusaoAgendadaEm,
        Integer diasRestantes,
```

No corpo de `from(...)`, antes do `return new IgrejaDetalheDTO(...)`:

```java
        Integer diasRestantes = null;
        if (igreja.getExclusaoAgendadaEm() != null) {
            long decorridos = java.time.temporal.ChronoUnit.DAYS.between(
                    igreja.getExclusaoAgendadaEm(), LocalDateTime.now());
            diasRestantes = (int) Math.max(0, 10 - decorridos);
        }
```

E passar `igreja.getExclusaoAgendadaEm()` e `diasRestantes` nos dois últimos argumentos do `new IgrejaDetalheDTO(...)`.

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test -Dtest=IgrejaServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/DTO/IgrejaDetalheDTO.java src/test/java/com/domus/api/modules/igreja/IgrejaServiceTest.java
git commit -m "feat(igreja): expõe exclusão agendada e dias restantes em /igrejas/minha"
```

---

### Task 5: `ExclusaoIgrejaController` — endpoints REST

**Files:**
- Create: `src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaController.java`
- Modify: `src/main/java/com/domus/api/shared/security/Permissoes.java`
- Test: manual (o projeto não tem `@WebMvcTest`/harness de autorização por endpoint — dívida técnica conhecida, documentada no `CLAUDE.md`; validar com curl como o resto do `SecurityConfig`)

**Interfaces:**
- Consumes: `ExclusaoIgrejaService.resumo/agendar/cancelar` (Task 3), `UsuarioAutenticado.getIgrejaId()/getUsuarioId()/getRole()`.
- Produces: `GET /igrejas/exclusao/resumo`, `POST /igrejas/exclusao/agendar`, `POST /igrejas/exclusao/cancelar`.

- [ ] **Step 1: Adicionar a capacidade em `Permissoes`**

```java
    /** Agendar ou cancelar a exclusão definitiva da igreja — a ação de maior risco do sistema. */
    public static boolean podeExcluirIgreja(String role) { return tem(role, SO_ADMIN); }
```

- [ ] **Step 2: Criar o controller**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.exclusao.DTO.AgendarExclusaoRequest;
import com.domus.api.modules.igreja.exclusao.DTO.ResumoExclusaoResponse;
import com.domus.api.shared.exception.AcessoNegadoException;
import com.domus.api.shared.security.Permissoes;
import com.domus.api.shared.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/igrejas/exclusao")
@RequiredArgsConstructor
public class ExclusaoIgrejaController {

    private final ExclusaoIgrejaService exclusaoIgrejaService;
    private final UsuarioAutenticado usuarioAutenticado;

    @GetMapping("/resumo")
    public ResponseEntity<ResumoExclusaoResponse> resumo() {
        exigirAdmin();
        return ResponseEntity.ok(exclusaoIgrejaService.resumo(usuarioAutenticado.getIgrejaId()));
    }

    @PostMapping("/agendar")
    public ResponseEntity<Void> agendar(@RequestBody @Valid AgendarExclusaoRequest data) {
        exigirAdmin();
        exclusaoIgrejaService.agendar(
                usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId(), data.nomeConfirmacao());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cancelar")
    public ResponseEntity<Void> cancelar() {
        exigirAdmin();
        exclusaoIgrejaService.cancelar(usuarioAutenticado.getIgrejaId());
        return ResponseEntity.ok().build();
    }

    private void exigirAdmin() {
        if (!Permissoes.podeExcluirIgreja(usuarioAutenticado.getRole())) {
            throw new AcessoNegadoException();
        }
    }
}
```

- [ ] **Step 3: Compilar**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Validar manualmente com curl (dev local rodando)**

Testar: login como ADMIN_IGREJA → `GET /api/igrejas/exclusao/resumo` retorna contagens; login como LIDER/ACESSO_COMUM → recebe 403 nos três endpoints.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaController.java src/main/java/com/domus/api/shared/security/Permissoes.java
git commit -m "feat(igreja): endpoints de resumo/agendar/cancelar exclusão"
```

---

### Task 6: `ExclusaoIgrejaJob` — lembretes de 5 e 1 dia (sem purga ainda)

**Files:**
- Create: `src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJob.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJobTest.java`

**Interfaces:**
- Consumes: `IgrejaRepository.buscarComExclusaoAgendada()` (Task 2), `EmailService.enviar` (existe).
- Produces: `ExclusaoIgrejaJob.verificarPrazos()` — estendido na Task 9 (Fase 2) para também chamar a purga quando o prazo vence.

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExclusaoIgrejaJobTest {

    IgrejaRepository igrejaRepository;
    EmailService emailService;
    ExclusaoIgrejaJob job;

    @BeforeEach
    void setup() {
        igrejaRepository = mock(IgrejaRepository.class);
        emailService = mock(EmailService.class);
        job = new ExclusaoIgrejaJob(igrejaRepository, emailService, null);
    }

    private Igreja igrejaAgendadaHa(int dias) {
        return Igreja.builder().nome("Igreja X").emailContato("x@x.com")
                .exclusaoAgendadaEm(LocalDateTime.now().minusDays(dias)).build();
    }

    @Test
    void enviaLembreteQuandoFaltamExatamente5Dias() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(5)));

        job.verificarPrazos();

        verify(emailService).enviar(eq("x@x.com"), contains("5 dias"), anyString());
    }

    @Test
    void enviaLembreteQuandoFaltaExatamente1Dia() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(9)));

        job.verificarPrazos();

        verify(emailService).enviar(eq("x@x.com"), contains("1 dia"), anyString());
    }

    @Test
    void naoEnviaLembreteForaDosMarcosDe5E1Dia() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(3)));

        job.verificarPrazos();

        verify(emailService, never()).enviar(anyString(), anyString(), anyString());
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=ExclusaoIgrejaJobTest`
Expected: FAIL (`ExclusaoIgrejaJob` não existe)

- [ ] **Step 3: Implementar o job**

O terceiro parâmetro do construtor (`PurgaIgrejaService`) é usado só na Task 9 — por ora fica como campo não utilizado no branch de prazo vencido, mas **precisa existir na assinatura** desde já para não quebrar a assinatura entre fases (o teste acima já passa `null` nele de propósito).

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** Roda uma vez por dia: lembra em D-5 e D-1, e (a partir da Fase 2) executa a purga em D-10. */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExclusaoIgrejaJob {

    private final IgrejaRepository igrejaRepository;
    private final EmailService emailService;
    private final PurgaIgrejaService purgaIgrejaService;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void verificarPrazos() {
        var igrejas = igrejaRepository.buscarComExclusaoAgendada();
        int lembretes = 0;

        for (Igreja igreja : igrejas) {
            long decorridos = ChronoUnit.DAYS.between(igreja.getExclusaoAgendadaEm(), LocalDateTime.now());
            long faltam = 10 - decorridos;

            if (faltam == 5) {
                emailService.enviar(igreja.getEmailContato(),
                        "Sua igreja será excluída em 5 dias",
                        "Faltam 5 dias para a exclusão definitiva de \"" + igreja.getNome() + "\". "
                                + "Cancele em Configurações → Sistema, se quiser manter sua conta.");
                lembretes++;
            } else if (faltam == 1) {
                emailService.enviar(igreja.getEmailContato(),
                        "Sua igreja será excluída amanhã",
                        "Falta 1 dia para a exclusão definitiva de \"" + igreja.getNome() + "\". "
                                + "Cancele em Configurações → Sistema, se quiser manter sua conta.");
                lembretes++;
            }
        }

        log.info("Verificação diária de exclusões agendadas concluída. igrejas_agendadas={}, lembretes_enviados={}",
                igrejas.size(), lembretes);
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test -Dtest=ExclusaoIgrejaJobTest`
Expected: PASS

- [ ] **Step 5: Criar `PurgaIgrejaService` vazio (só pra compilar — implementado de verdade na Fase 2)**

Este passo NÃO é um placeholder de lógica de negócio — é a introdução de uma classe cuja API pública fica estável a partir de agora, com corpo real (vazio é comportamento real: "nada a purgar ainda"), documentada para deixar claro o que falta:

```java
package com.domus.api.modules.igreja.exclusao;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Purga tabela-por-tabela da igreja (Fase 2 preenche o corpo de {@link #purgar}). */
@Slf4j
@Service
public class PurgaIgrejaService {

    @Transactional
    public void purgar(UUID igrejaId) {
        log.warn("purgar() chamado antes da Fase 2 estar implementada. igreja_id={}", igrejaId);
    }
}
```

- [ ] **Step 6: Rodar toda a suíte pra garantir que nada quebrou**

Run: `mvn -q -o test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJob.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJobTest.java
git commit -m "feat(igreja): job diário de lembrete de exclusão agendada (D-5/D-1)"
```

> **Checkpoint Fase 1:** agendar, cancelar, resumo e lembretes por e-mail funcionam de ponta a ponta (sem reautenticação e sem a purga em si). Testar manualmente antes de seguir para a Fase 2.

---

## Fase 2 — Purga tabela-por-tabela

> Cada task abaixo adiciona um método `deleteAllByIgrejaId`/equivalente nativo por repositório e o encaixa em `PurgaIgrejaService.purgar(...)`, na ordem exata da spec. O teste principal (Task 13) só compila depois que todas as tasks 7–12 existem — as tasks intermediárias são verificadas por teste unitário do próprio método de repositório sendo chamado (mockado) dentro de `PurgaIgrejaServiceTest`, que cresce a cada task.

### Task 7: Purga do financeiro (movimentação → categoria) e inscrições de evento

**Files:**
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraRepository.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/categoria/CategoriaFinanceiraRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java` (criado aqui, cresce nas próximas tasks)

**Interfaces:**
- Consumes: nenhuma classe nova.
- Produces: `MovimentacaoFinanceiraRepository.deleteAllByIgrejaId(UUID)`, `CategoriaFinanceiraRepository.deleteAllByIgrejaId(UUID)`, `InscricaoRepository.deleteAllByIgrejaId(UUID)` — chamados por `PurgaIgrejaService.purgar`.

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class PurgaIgrejaServiceTest {

    MovimentacaoFinanceiraRepository movimentacaoRepository;
    CategoriaFinanceiraRepository categoriaRepository;
    InscricaoRepository inscricaoRepository;
    PurgaIgrejaService service;
    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        service = new PurgaIgrejaService(movimentacaoRepository, categoriaRepository, inscricaoRepository);
    }

    @Test
    void purgaApagaInscricoesMovimentacoesECategoriasNaOrdemCerta() {
        service.purgar(igrejaId);

        var ordem = inOrder(inscricaoRepository, movimentacaoRepository, categoriaRepository);
        ordem.verify(inscricaoRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(movimentacaoRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(categoriaRepository).deleteAllByIgrejaId(igrejaId);
    }
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: FAIL (`deleteAllByIgrejaId` não existe em nenhum dos três repositórios; construtor de `PurgaIgrejaService` não bate)

- [ ] **Step 3: Adicionar os métodos nativos de bulk delete**

Em `InscricaoRepository.java` (o `acompanhante_inscricao` cascadeia sozinho via `ON DELETE CASCADE FROM inscricao_id`, confirmado em `V1__schema_inicial.sql`, então apagar `inscricao_evento` já resolve os dois):

```java
    @Modifying
    @Query(value = "DELETE FROM inscricao_evento WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `MovimentacaoFinanceiraRepository.java` (`movimentacao_contribuinte` cascadeia sozinho via `ON DELETE CASCADE FROM movimentacao_id`, confirmado em `V15__movimentacao_contribuinte.sql`):

```java
    @Modifying
    @Query(value = "DELETE FROM movimentacao_financeira WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `CategoriaFinanceiraRepository.java`:

```java
    @Modifying
    @Query(value = "DELETE FROM categoria_financeira WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

- [ ] **Step 4: Implementar `PurgaIgrejaService.purgar` com estes três passos**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Purga tabela-por-tabela da igreja: uma transação, uma linha de DELETE por tabela, ordem
 *  explícita (nunca ON DELETE CASCADE) — se qualquer passo falhar, tudo desfaz e o job diário
 *  tenta de novo amanhã. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgaIgrejaService {

    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final CategoriaFinanceiraRepository categoriaRepository;
    private final InscricaoRepository inscricaoRepository;

    @Transactional
    public void purgar(UUID igrejaId) {
        log.warn("Iniciando purga definitiva da igreja. igreja_id={}", igrejaId);

        inscricaoRepository.deleteAllByIgrejaId(igrejaId);
        movimentacaoRepository.deleteAllByIgrejaId(igrejaId);
        categoriaRepository.deleteAllByIgrejaId(igrejaId);
    }
}
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 6: Ajustar `ExclusaoIgrejaJob` (Task 6) que já injeta `PurgaIgrejaService` — apenas confirmar que compila**

Run: `mvn -q -o compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoFinanceiraRepository.java src/main/java/com/domus/api/modules/financeiro/categoria/CategoriaFinanceiraRepository.java src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java
git commit -m "feat(igreja): purga financeiro e inscrições de evento"
```

---

### Task 8: Purga de célula e ministério (membros → cadastro) e `usuario_capacidade`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/celula/CelulaMembroRepository.java`
- Modify: `src/main/java/com/domus/api/modules/celula/CelulaRepository.java`
- Modify: `src/main/java/com/domus/api/modules/ministerio/MinisterioMembroRepository.java`
- Modify: `src/main/java/com/domus/api/modules/ministerio/MinisterioRepository.java`
- Modify: `src/main/java/com/domus/api/modules/usuario/UsuarioCapacidadeRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java` (estende o teste da Task 7)

**Interfaces:**
- Consumes: nenhuma classe nova.
- Produces: `CelulaMembroRepository.deleteAllByIgrejaId`, `CelulaRepository.deleteAllByIgrejaId`, `MinisterioMembroRepository.deleteAllByIgrejaId`, `MinisterioRepository.deleteAllByIgrejaId`, `UsuarioCapacidadeRepository.deleteAllByUsuarioIgrejaId`.

`celula_membro` e `ministerio_membro` **têm coluna `igreja_id` própria** (confirmado em `V11__celulas.sql` e `V9__ministerio.sql`), então são deletáveis direto por `igreja_id`, sem subquery pela célula/ministério pai. `usuario_capacidade` (PK composta `usuario_id, capacidade`) **não** tem `igreja_id` própria — precisa de subquery contra `usuario`.

- [ ] **Step 1: Estender o teste**

Adicionar ao `@BeforeEach` os quatro novos mocks e ao construtor os quatro novos parâmetros; adicionar este teste:

```java
    @Test
    void purgaApagaCelulaEMinisterioAntesDosCadastrosPais() {
        service.purgar(igrejaId);

        var ordem = inOrder(celulaMembroRepository, celulaRepository, ministerioMembroRepository, ministerioRepository);
        ordem.verify(celulaMembroRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(celulaRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(ministerioMembroRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(ministerioRepository).deleteAllByIgrejaId(igrejaId);
    }

    @Test
    void purgaApagaCapacidadesDeUsuario() {
        service.purgar(igrejaId);

        verify(usuarioCapacidadeRepository).deleteAllByUsuarioIgrejaId(igrejaId);
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: FAIL

- [ ] **Step 3: Adicionar os métodos de bulk delete**

Em `CelulaMembroRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM celula_membro WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `CelulaRepository.java` (seguir o padrão de `hardDeleteById` já existente, agora em lote):
```java
    @Modifying
    @Query(value = "DELETE FROM celula WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `MinisterioMembroRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM ministerio_membro WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `MinisterioRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM ministerio WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `UsuarioCapacidadeRepository.java` (sem `igreja_id` própria — subquery por `usuario_id IN (SELECT id FROM usuario WHERE igreja_id = ?)`):
```java
    @Modifying
    @Query(value = "DELETE FROM usuario_capacidade WHERE usuario_id IN (SELECT id FROM usuario WHERE igreja_id = :igrejaId)", nativeQuery = true)
    void deleteAllByUsuarioIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

- [ ] **Step 4: Atualizar `PurgaIgrejaService`**

Adicionar os cinco novos repositórios ao construtor (via `@RequiredArgsConstructor`, só adicionar os campos `private final`) e, em `purgar(...)`, logo após o bloco da Task 7:

```java
        celulaMembroRepository.deleteAllByIgrejaId(igrejaId);
        celulaRepository.deleteAllByIgrejaId(igrejaId);
        ministerioMembroRepository.deleteAllByIgrejaId(igrejaId);
        ministerioRepository.deleteAllByIgrejaId(igrejaId);
        usuarioCapacidadeRepository.deleteAllByUsuarioIgrejaId(igrejaId);
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/celula/ src/main/java/com/domus/api/modules/ministerio/ src/main/java/com/domus/api/modules/usuario/UsuarioCapacidadeRepository.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java
git commit -m "feat(igreja): purga células, ministérios e capacidades de usuário"
```

---

### Task 9: Purga de evento, visitante e local de evento

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/EventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/visitante/VisitanteRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/local/LocalEventoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java`

**Interfaces:**
- Consumes: nenhuma classe nova.
- Produces: `EventoRepository.deleteAllByIgrejaId`, `VisitanteRepository.deleteAllByIgrejaId`, `LocalEventoRepository.deleteAllByIgrejaId`.

`evento.local_id` é `ON DELETE SET NULL` (confirmado em `V3__evento_enriquecido.sql:42`) e `evento.responsavel_pessoa_id` também é `SET NULL` — então apagar `evento` antes de `local_evento`/`pessoa` nunca quebra por FK; a ordem aqui (evento antes de local_evento, pessoa depois na Task 11) só existe para deixar o rastro de logs em ordem de dependência lógica, não porque o banco exigiria.

- [ ] **Step 1: Estender o teste**

```java
    @Test
    void purgaApagaEventoVisitanteELocal() {
        service.purgar(igrejaId);

        verify(eventoRepository).deleteAllByIgrejaId(igrejaId);
        verify(visitanteRepository).deleteAllByIgrejaId(igrejaId);
        verify(localEventoRepository).deleteAllByIgrejaId(igrejaId);
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: FAIL

- [ ] **Step 3: Adicionar os métodos**

Em `EventoRepository.java` (ver o `hardDeleteById` já existente na linha 191–193 como referência de estilo):
```java
    @Modifying
    @Query(value = "DELETE FROM evento WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `VisitanteRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM visitante WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

Em `LocalEventoRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM local_evento WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") java.util.UUID igrejaId);
```

- [ ] **Step 4: Atualizar `PurgaIgrejaService`**

Adicionar os três repositórios ao construtor e, em `purgar(...)`, após o bloco da Task 8:

```java
        eventoRepository.deleteAllByIgrejaId(igrejaId);
        visitanteRepository.deleteAllByIgrejaId(igrejaId);
        localEventoRepository.deleteAllByIgrejaId(igrejaId);
```

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/EventoRepository.java src/main/java/com/domus/api/modules/visitante/VisitanteRepository.java src/main/java/com/domus/api/modules/evento/local/LocalEventoRepository.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java
git commit -m "feat(igreja): purga eventos, visitantes e locais de evento"
```

---

### Task 10: Purga de fotos (R2 + banco) — reusa `FotoService.remover`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/foto/FotoRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java`

**Interfaces:**
- Consumes: `FotoService.remover(UUID)` (já existe, `FotoService.java:73`).
- Produces: `FotoRepository.findByIgrejaId(UUID)`.

Ordem importa aqui: `pessoa.foto_id`, `evento.foto_id`, `celula.foto_id`, `ministerio.foto_id` são `ON DELETE RESTRICT` — mas a essa altura da purga, pessoa/evento/célula/ministério **ainda não foram apagados** (pessoa só cai na Task 11). Só `igreja.logo_foto_id` já existe e ainda aponta pra uma foto nesse momento (a igreja em si só é apagada por último, Task 12). Por isso este passo primeiro limpa `igreja.logo_foto_id` explicitamente, senão o `RESTRICT` bloqueia apagar a foto do logo. As fotos de pessoa/evento/célula/ministério só serão removíveis sem erro **depois** que essas linhas caírem — então a purga de fotos, pra ser 100% best-effort e não travar em erro de FK residual, deve rodar **depois** de pessoa/evento/célula/ministério estarem apagados. Reordenando: mover este passo para depois da Task 11 (usuário/pessoa) na composição final de `purgar(...)` — a Task 12 formaliza a ordem completa.

- [ ] **Step 1: Estender o teste**

```java
    @Test
    void purgaRemoveFotosUmaAUmaViaFotoService() {
        UUID fotoId1 = UUID.randomUUID();
        UUID fotoId2 = UUID.randomUUID();
        Foto foto1 = Foto.builder().id(fotoId1).build();
        Foto foto2 = Foto.builder().id(fotoId2).build();
        when(fotoRepository.findByIgrejaId(igrejaId)).thenReturn(List.of(foto1, foto2));

        service.purgar(igrejaId);

        verify(fotoService).remover(fotoId1);
        verify(fotoService).remover(fotoId2);
    }

    @Test
    void purgaDeFotoContinuaMesmoSeUmaFalhar() {
        UUID fotoId1 = UUID.randomUUID();
        UUID fotoId2 = UUID.randomUUID();
        Foto foto1 = Foto.builder().id(fotoId1).build();
        Foto foto2 = Foto.builder().id(fotoId2).build();
        when(fotoRepository.findByIgrejaId(igrejaId)).thenReturn(List.of(foto1, foto2));
        doThrow(new RuntimeException("falha no R2")).when(fotoService).remover(fotoId1);

        service.purgar(igrejaId);

        verify(fotoService).remover(fotoId2);
    }
```

Adicionar `import com.domus.api.modules.foto.Foto;` e `import java.util.List;` no topo do teste, e os mocks `fotoRepository`/`fotoService` no `@BeforeEach` + construtor.

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: FAIL

- [ ] **Step 3: Adicionar `findByIgrejaId` em `FotoRepository`**

```java
    List<Foto> findByIgrejaId(UUID igrejaId);
```

(Derived query simples — `Foto.igreja` já é o nome do campo, `FotoRepository.java` confirmado.)

- [ ] **Step 4: Atualizar `PurgaIgrejaService`**

Adicionar `FotoRepository` e `FotoService` ao construtor. Zerar `igreja.logo_foto_id` antes do loop (usar update nativo simples, já que a entidade `Igreja` não está carregada aqui — evita um SELECT a mais):

Adicionar em `IgrejaRepository.java`:
```java
    @Modifying
    @Query(value = "UPDATE igreja SET logo_foto_id = NULL WHERE id = :igrejaId", nativeQuery = true)
    void limparLogoFoto(@Param("igrejaId") UUID igrejaId);
```

Em `PurgaIgrejaService.purgar(...)`, o bloco de fotos (best-effort — uma foto que falha não trava as demais nem a transação):

```java
        igrejaRepository.limparLogoFoto(igrejaId);
        for (var foto : fotoRepository.findByIgrejaId(igrejaId)) {
            try {
                fotoService.remover(foto.getId());
            } catch (Exception e) {
                log.error("Falha ao remover foto na purga da igreja — seguindo para as demais. foto_id={}, igreja_id={}",
                        foto.getId(), igrejaId, e);
            }
        }
```

(`IgrejaRepository` já é injetado em `PurgaIgrejaService`? Ainda não — adicionar ao construtor também.)

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/foto/FotoRepository.java src/main/java/com/domus/api/modules/igreja/IgrejaRepository.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java
git commit -m "feat(igreja): purga fotos (R2 + banco) reusando FotoService.remover"
```

---

### Task 11: Purga de usuário/pessoa e desvínculo de família (se mãe)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java`
- Modify: `src/main/java/com/domus/api/modules/pessoa/PessoaRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/IgrejaRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java`

**Interfaces:**
- Consumes: `IgrejaRepository.buscarIdsDasFilhas` (já existe).
- Produces: `UsuarioRepository.deleteAllByIgrejaId`, `PessoaRepository.deleteAllByIgrejaId`, `IgrejaRepository.desvincularFamiliaEmLote(List<UUID> idsFilhas)`.

- [ ] **Step 1: Estender o teste**

```java
    @Test
    void purgaApagaUsuarioDepoisPessoa() {
        service.purgar(igrejaId);

        var ordem = inOrder(usuarioRepository, pessoaRepository);
        ordem.verify(usuarioRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(pessoaRepository).deleteAllByIgrejaId(igrejaId);
    }

    @Test
    void purgaDesvinculaFilhasQuandoIgrejaEhMae() {
        UUID filha1 = UUID.randomUUID();
        UUID filha2 = UUID.randomUUID();
        when(igrejaRepository.buscarIdsDasFilhas(igrejaId)).thenReturn(List.of(filha1, filha2));

        service.purgar(igrejaId);

        verify(igrejaRepository).desvincularFamiliaEmLote(List.of(filha1, filha2));
    }

    @Test
    void purgaNaoChamaDesvinculoQuandoIgrejaNaoEhMae() {
        when(igrejaRepository.buscarIdsDasFilhas(igrejaId)).thenReturn(List.of());

        service.purgar(igrejaId);

        verify(igrejaRepository, never()).desvincularFamiliaEmLote(any());
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: FAIL

- [ ] **Step 3: Adicionar os métodos**

Em `UsuarioRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM usuario WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
```

Em `PessoaRepository.java`:
```java
    @Modifying
    @Query(value = "DELETE FROM pessoa WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
```

Em `IgrejaRepository.java` (mesma lógica de `VinculoService.limparVinculo`, em lote — `igreja/familia/VinculoService.java:172-177`):
```java
    @Modifying
    @Query(value = """
        UPDATE igreja
           SET igreja_mae_id = NULL, vinculado_em = NULL, vinculado_por_usuario_id = NULL
         WHERE id IN (:idsFilhas)
        """, nativeQuery = true)
    void desvincularFamiliaEmLote(@Param("idsFilhas") java.util.List<UUID> idsFilhas);
```

- [ ] **Step 4: Atualizar `PurgaIgrejaService`**

Adicionar `UsuarioRepository` e `PessoaRepository` ao construtor (já tem `IgrejaRepository`, adicionado na Task 10). Após o bloco de fotos:

```java
        List<UUID> idsFilhas = igrejaRepository.buscarIdsDasFilhas(igrejaId);
        if (!idsFilhas.isEmpty()) {
            igrejaRepository.desvincularFamiliaEmLote(idsFilhas);
            log.info("Igrejas vinculadas desvinculadas da família. igreja_mae_id={}, filhas={}", igrejaId, idsFilhas.size());
        }

        usuarioRepository.deleteAllByIgrejaId(igrejaId);
        pessoaRepository.deleteAllByIgrejaId(igrejaId);
```

(Adicionar `import java.util.List;` se ainda não houver.)

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/usuario/UsuarioRepository.java src/main/java/com/domus/api/modules/pessoa/PessoaRepository.java src/main/java/com/domus/api/modules/igreja/IgrejaRepository.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java
git commit -m "feat(igreja): purga usuário/pessoa e desvincula família (se for mãe)"
```

---

### Task 12: Elasticsearch, e-mail final e a linha da própria igreja — fecha a purga e liga no job

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pessoa/busca/PessoaSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/evento/busca/EventoSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/usuario/busca/UsuarioSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/busca/MovimentacaoSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/financeiro/categoria/busca/CategoriaSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/celula/busca/CelulaSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/ministerio/busca/MinisterioSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/visitante/busca/VisitanteSearchRepository.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJob.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaServiceTest.java`, `ExclusaoIgrejaJobTest.java`

**Interfaces:**
- Consumes: nenhuma classe nova.
- Produces: `PurgaIgrejaService.purgar(UUID)` **completo** (todos os passos da spec) — consumido por `ExclusaoIgrejaJob.verificarPrazos()`, que passa a chamá-lo quando o prazo vence.

- [ ] **Step 1: Adicionar `deleteByIgrejaId` em cada `*SearchRepository`**

Todos os `*Document` guardam `igrejaId` como `String` (confirmado em `PessoaDocument.java:24`, `EventoDocument.java:24`). Em cada um dos oito arquivos listados acima, adicionar uma linha ao corpo da interface (exemplo para `PessoaSearchRepository`):

```java
package com.domus.api.modules.pessoa.busca;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PessoaSearchRepository extends ElasticsearchRepository<PessoaDocument, String> {
    void deleteByIgrejaId(String igrejaId);
}
```

Repetir o mesmo padrão (`void deleteByIgrejaId(String igrejaId);`) nos outros sete arquivos, cada um usando seu próprio `*Document`.

- [ ] **Step 2: Estender o teste de `PurgaIgrejaServiceTest`**

```java
    @Test
    void purgaApagaDocumentosDeTodosOsIndicesDoElasticsearch() {
        service.purgar(igrejaId);

        String id = igrejaId.toString();
        verify(pessoaSearchRepository).deleteByIgrejaId(id);
        verify(eventoSearchRepository).deleteByIgrejaId(id);
        verify(usuarioSearchRepository).deleteByIgrejaId(id);
        verify(movimentacaoSearchRepository).deleteByIgrejaId(id);
        verify(categoriaSearchRepository).deleteByIgrejaId(id);
        verify(celulaSearchRepository).deleteByIgrejaId(id);
        verify(ministerioSearchRepository).deleteByIgrejaId(id);
        verify(visitanteSearchRepository).deleteByIgrejaId(id);
    }

    @Test
    void purgaNaoTravaSeElasticsearchFalhar() {
        doThrow(new RuntimeException("ES fora do ar")).when(pessoaSearchRepository).deleteByIgrejaId(anyString());

        service.purgar(igrejaId);

        verify(igrejaRepository).deleteById(igrejaId);
    }

    @Test
    void purgaEnviaEmailFinalAntesDeApagarALinhaDaIgreja() {
        Igreja igreja = Igreja.builder().id(igrejaId).nome("Igreja X").emailContato("x@x.com").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        service.purgar(igrejaId);

        var ordem = inOrder(emailService, igrejaRepository);
        ordem.verify(emailService).enviar(eq("x@x.com"), contains("excluída"), anyString());
        ordem.verify(igrejaRepository).deleteById(igrejaId);
    }
```

(Adicionar `import java.util.Optional;` e os oito mocks de `*SearchRepository` + `emailService` ao `@BeforeEach`/construtor.)

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: FAIL

- [ ] **Step 4: Atualizar `PurgaIgrejaService` — fechar `purgar(...)` com ES, e-mail e a linha da igreja**

Adicionar os oito `*SearchRepository` e `EmailService` ao construtor. No final de `purgar(...)`, após o bloco da Task 11:

```java
        String idTexto = igrejaId.toString();
        for (Runnable limpezaIndice : List.of(
                () -> pessoaSearchRepository.deleteByIgrejaId(idTexto),
                () -> eventoSearchRepository.deleteByIgrejaId(idTexto),
                () -> usuarioSearchRepository.deleteByIgrejaId(idTexto),
                () -> movimentacaoSearchRepository.deleteByIgrejaId(idTexto),
                () -> categoriaSearchRepository.deleteByIgrejaId(idTexto),
                () -> celulaSearchRepository.deleteByIgrejaId(idTexto),
                () -> ministerioSearchRepository.deleteByIgrejaId(idTexto),
                () -> visitanteSearchRepository.deleteByIgrejaId(idTexto)
        )) {
            try {
                limpezaIndice.run();
            } catch (Exception e) {
                log.error("Falha ao limpar índice do Elasticsearch na purga da igreja — seguindo. igreja_id={}", igrejaId, e);
            }
        }

        igrejaRepository.findById(igrejaId).ifPresent(igreja ->
                emailService.enviar(igreja.getEmailContato(), "Sua igreja foi excluída",
                        "A exclusão definitiva de \"" + igreja.getNome() + "\" foi concluída. Todos os dados foram removidos."));

        igrejaRepository.deleteById(igrejaId);
        log.warn("Purga definitiva da igreja concluída. igreja_id={}", igrejaId);
```

(O e-mail precisa vir **antes** do `deleteById` — a linha da igreja, com o e-mail de contato, ainda precisa existir nesse momento, conforme a spec.)

- [ ] **Step 5: Rodar e ver passar**

Run: `mvn -q test -Dtest=PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 6: Ligar a purga no job — atualizar `ExclusaoIgrejaJob.verificarPrazos()`**

Adicionar, dentro do `for (Igreja igreja : igrejas)`, um terceiro branch (`else if (faltam <= 0)`) que chama `purgaIgrejaService.purgar(igreja.getId())`:

```java
            } else if (faltam <= 0) {
                purgaIgrejaService.purgar(igreja.getId());
```

- [ ] **Step 7: Estender `ExclusaoIgrejaJobTest` com o caso do prazo vencido**

```java
    @Test
    void executaPurgaQuandoPrazoVenceu() {
        when(igrejaRepository.buscarComExclusaoAgendada()).thenReturn(List.of(igrejaAgendadaHa(10)));
        PurgaIgrejaService purgaIgrejaService = mock(PurgaIgrejaService.class);
        ExclusaoIgrejaJob jobComPurga = new ExclusaoIgrejaJob(igrejaRepository, emailService, purgaIgrejaService);

        jobComPurga.verificarPrazos();

        verify(purgaIgrejaService).purgar(any());
    }
```

(Este teste cria seu próprio `job` local em vez de usar o do `@BeforeEach`, porque agora `purgaIgrejaService` precisa ser um mock de verdade, não `null`; a instância `igrejaAgendadaHa(10)` do `@BeforeEach` não tem `id`, então usar `Igreja.builder().id(UUID.randomUUID())...` — ajustar o helper `igrejaAgendadaHa` para setar um `id` fixo se ainda não tiver.)

- [ ] **Step 8: Rodar toda a suíte do módulo**

Run: `mvn -q test -Dtest=ExclusaoIgrejaJobTest,PurgaIgrejaServiceTest,ExclusaoIgrejaServiceTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/domus/api/modules/*/busca/*SearchRepository.java src/main/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaService.java src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaJob.java src/test/java/com/domus/api/modules/igreja/exclusao/
git commit -m "feat(igreja): fecha a purga (Elasticsearch + e-mail final + linha da igreja) e liga no job"
```

---

### Task 13: Teste de integração — purga sem nenhum erro de FK, criando um registro de cada tipo

**Files:**
- Create: `src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaIntegrationTest.java`

**Interfaces:**
- Consumes: `PurgaIgrejaService.purgar` (Task 12), entidades de todos os módulos.
- Produces: nada (teste terminal da Fase 2).

Este é **o teste mais importante do projeto** (conforme a spec): `@SpringBootTest` que cria uma igreja de teste com um registro de cada tipo (pessoa, evento, inscrição+acompanhante, movimentação+contribuinte, categoria, célula+membro, ministério+membro, usuário, visitante, local) e roda a purga de verdade contra o Neon de teste.

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.CelulaMembroRepository;
import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.celula.PapelCelula;
import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.evento.local.LocalEventoRepository;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.ministerio.Ministerio;
import com.domus.api.modules.ministerio.MinisterioMembro;
import com.domus.api.modules.ministerio.MinisterioMembroRepository;
import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.ministerio.PapelMinisterio;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.RoleRepository;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PurgaIgrejaIntegrationTest {

    @Autowired PurgaIgrejaService purgaIgrejaService;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired LocalEventoRepository localEventoRepository;
    @Autowired CategoriaFinanceiraRepository categoriaRepository;
    @Autowired MovimentacaoFinanceiraRepository movimentacaoRepository;
    @Autowired CelulaRepository celulaRepository;
    @Autowired CelulaMembroRepository celulaMembroRepository;
    @Autowired MinisterioRepository ministerioRepository;
    @Autowired MinisterioMembroRepository ministerioMembroRepository;
    @Autowired VisitanteRepository visitanteRepository;
    @Autowired EntityManager entityManager;

    @Test
    void purgaTudoSemErroDeFkComUmRegistroDeCadaTipo() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja de Teste da Purga").emailContato("purga@teste.com").build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano de Teste").email("fulano-purga@teste.com")
                .vinculo(Vinculo.MEMBRO).build());

        Role roleAdmin = roleRepository.findByNome("ADMIN_IGREJA").orElseThrow();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .igreja(igreja).pessoa(pessoa).role(roleAdmin).senhaHash("hash").ativo(true).build());

        LocalEvento local = localEventoRepository.save(LocalEvento.builder()
                .igreja(igreja).nome("Salão de Teste").build());

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento de Teste").local(local)
                .inicioEm(LocalDateTime.now().plusDays(1)).build());

        InscricaoEvento inscricao = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(pessoa).status(StatusInscricao.CONFIRMADA).build());

        CategoriaFinanceira categoria = categoriaRepository.save(CategoriaFinanceira.builder()
                .igreja(igreja).nome("Categoria de Teste").tipo(TipoCategoria.ENTRADA).build());

        movimentacaoRepository.save(MovimentacaoFinanceira.builder()
                .igreja(igreja).categoria(categoria).criadoPor(usuario)
                .tipo(TipoMovimentacao.ENTRADA).valor(BigDecimal.TEN)
                .dataMovimentacao(LocalDate.now()).build());

        Celula celula = celulaRepository.save(Celula.builder()
                .igreja(igreja).nome("Célula de Teste").build());
        celulaMembroRepository.save(CelulaMembro.builder()
                .igreja(igreja).celula(celula).pessoa(pessoa).papel(PapelCelula.LIDER).build());

        Ministerio ministerio = ministerioRepository.save(Ministerio.builder()
                .igreja(igreja).nome("Ministério de Teste").build());
        ministerioMembroRepository.save(MinisterioMembro.builder()
                .igreja(igreja).ministerio(ministerio).pessoa(pessoa).papel(PapelMinisterio.LIDER).build());

        visitanteRepository.save(Visitante.builder()
                .igreja(igreja).nome("Visitante de Teste").build());

        entityManager.flush();
        entityManager.clear();

        purgaIgrejaService.purgar(igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(igrejaRepository.findById(igreja.getId())).isEmpty();
        assertThat(pessoaRepository.findByIdAndIgrejaId(pessoa.getId(), igreja.getId())).isEmpty();
        assertThat(usuarioRepository.findByIdAndIgrejaId(usuario.getId(), igreja.getId())).isEmpty();
        assertThat(eventoRepository.findByIdAndIgrejaId(evento.getId(), igreja.getId())).isEmpty();
        assertThat(inscricaoRepository.findByIdAndIgrejaId(inscricao.getId(), igreja.getId())).isEmpty();
        assertThat(localEventoRepository.findByIdAndIgrejaId(local.getId(), igreja.getId())).isEmpty();
        assertThat(categoriaRepository.findByIdAndIgrejaId(categoria.getId(), igreja.getId())).isEmpty();
        assertThat(celulaRepository.findByIdAndIgrejaId(celula.getId(), igreja.getId())).isEmpty();
        assertThat(ministerioRepository.findByIdAndIgrejaId(ministerio.getId(), igreja.getId())).isEmpty();
    }
}
```

Ajustar os nomes exatos de builder/campo (`PapelCelula`, `PapelMinisterio`, `TipoCategoria`, `TipoMovimentacao`, construtor de `Evento.local`) conforme o que já existe nas entidades reais — o subagente que executar esta task deve conferir cada enum/campo abrindo a entidade correspondente antes de compilar, já que os nomes exatos de enum não foram confirmados nesta investigação (única exceção às "No Placeholders" desta plan: os *nomes* de enum, não a lógica, podem exigir um ajuste de 1 linha por causa disso — se algum não bater, o teste não compila e aponta exatamente qual).

- [ ] **Step 2: Rodar e ver falhar (ou compilar com ajustes de nome, depois falhar)**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=PurgaIgrejaIntegrationTest`
Expected: FAIL até a Task 12 estar completa — se todas as tasks anteriores foram feitas, deve já ir direto para PASS.

- [ ] **Step 3: Rodar e ver passar**

Run: `set -a; source .env >/dev/null 2>&1; set +a; mvn -q test -Dtest=PurgaIgrejaIntegrationTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/domus/api/modules/igreja/exclusao/PurgaIgrejaIntegrationTest.java
git commit -m "test(igreja): integração da purga completa sem erro de FK"
```

> **Checkpoint Fase 2:** a purga funciona de ponta a ponta contra o banco real. Testar manualmente agendando uma exclusão com data manipulada no banco de teste (ou reduzindo o cron para rodar logo) antes de seguir para a Fase 3.

---

## Fase 3 — Reautenticação obrigatória ao agendar

### Task 14: Senha (nativo) ou Google ID token exigidos em `POST /igrejas/exclusao/agendar`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/DTO/AgendarExclusaoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaService.java`
- Modify: `src/main/java/com/domus/api/modules/auth/GoogleAuthService.java`
- Test: `src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaServiceTest.java`

**Interfaces:**
- Consumes: `PasswordEncoder.matches` (já usado em `AuthService.java:92,159`), `GoogleAuthService.verificarParaReautenticacao(String idToken)` (novo wrapper público).
- Produces: `ExclusaoIgrejaService.agendar(UUID igrejaId, UUID usuarioId, String nomeConfirmacao, String senha, String googleIdToken)` (assinatura estendida).

- [ ] **Step 1: Expor um wrapper público em `GoogleAuthService`**

O método `verificar(String idToken)` (`GoogleAuthService.java:121`) hoje é *package-private*. Adicionar, no mesmo arquivo, um método público que delega para ele — usado fora do pacote `auth`:

```java
    /** Reautenticação (step-up auth) fora do fluxo de login — ex.: confirmar exclusão de igreja. */
    public String reautenticarPorGoogle(String idToken) {
        return verificar(idToken).getSubject();
    }
```

- [ ] **Step 2: Escrever os testes de reautenticação**

Adicionar ao `ExclusaoIgrejaServiceTest` (ajustar o `@BeforeEach`/construtor para incluir `passwordEncoder` e `googleAuthService` mockados):

```java
    @Test
    void agendaComSenhaCorretaParaLoginNativo() {
        Igreja igreja = igreja();
        Usuario usuario = Usuario.builder().id(usuarioId).senhaHash("hash-bcrypt").googleSub(null).build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-correta", "hash-bcrypt")).thenReturn(true);

        service.agendar(igrejaId, usuarioId, "Igreja Batista Central", "senha-correta", null);

        verify(igrejaRepository).marcarExclusaoAgendada(eq(igrejaId), eq(usuarioId), any());
    }

    @Test
    void recusaAgendarComSenhaErrada() {
        Igreja igreja = igreja();
        Usuario usuario = Usuario.builder().id(usuarioId).senhaHash("hash-bcrypt").googleSub(null).build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha-errada", "hash-bcrypt")).thenReturn(false);

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Igreja Batista Central", "senha-errada", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("senha");

        verify(igrejaRepository, never()).marcarExclusaoAgendada(any(), any(), any());
    }

    @Test
    void agendaComGoogleQuandoSubBateComOCadastrado() {
        Igreja igreja = igreja();
        Usuario usuario = Usuario.builder().id(usuarioId).senhaHash(null).googleSub("google-sub-123").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(googleAuthService.reautenticarPorGoogle("token-valido")).thenReturn("google-sub-123");

        service.agendar(igrejaId, usuarioId, "Igreja Batista Central", null, "token-valido");

        verify(igrejaRepository).marcarExclusaoAgendada(eq(igrejaId), eq(usuarioId), any());
    }

    @Test
    void recusaAgendarComGoogleQuandoSubNaoBate() {
        Igreja igreja = igreja();
        Usuario usuario = Usuario.builder().id(usuarioId).senhaHash(null).googleSub("google-sub-123").build();
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(googleAuthService.reautenticarPorGoogle("token-de-outra-conta")).thenReturn("google-sub-999");

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Igreja Batista Central", null, "token-de-outra-conta"))
                .isInstanceOf(BusinessException.class);

        verify(igrejaRepository, never()).marcarExclusaoAgendada(any(), any(), any());
    }

    @Test
    void recusaAgendarSemSenhaNemGoogleToken() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja()));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(Usuario.builder().id(usuarioId).build()));

        assertThatThrownBy(() -> service.agendar(igrejaId, usuarioId, "Igreja Batista Central", null, null))
                .isInstanceOf(BusinessException.class);
    }
```

O teste antigo `agendaExclusaoQuandoNomeConfere` (da Task 3) precisa ser atualizado para passar uma senha válida e mockar `usuarioRepository.findById`/`passwordEncoder.matches` — ajustar in-place em vez de duplicar.

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q test -Dtest=ExclusaoIgrejaServiceTest`
Expected: FAIL

- [ ] **Step 4: Atualizar `AgendarExclusaoRequest`**

```java
package com.domus.api.modules.igreja.exclusao.DTO;

import jakarta.validation.constraints.NotBlank;

public record AgendarExclusaoRequest(
        @NotBlank String nomeConfirmacao,
        String senha,
        String googleIdToken
) {}
```

- [ ] **Step 5: Atualizar `ExclusaoIgrejaService.agendar`**

Adicionar `UsuarioRepository`, `PasswordEncoder` e `GoogleAuthService` ao construtor (o campo `usuarioRepository` já existe no construtor desde a Task 3, usado no resumo). Substituir a assinatura e o corpo de `agendar`:

```java
    @Transactional
    public void agendar(UUID igrejaId, UUID usuarioId, String nomeConfirmacao, String senha, String googleIdToken) {
        Igreja igreja = igrejaRepository.findById(igrejaId)
                .orElseThrow(() -> new ResourceNotFoundException("Igreja não encontrada."));

        if (!normalizarNome(igreja.getNome()).equals(normalizarNome(nomeConfirmacao))) {
            throw new BusinessException("NOME_NAO_CONFERE",
                    "O nome digitado não confere com o nome da igreja.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        reautenticar(usuario, senha, googleIdToken);

        igrejaRepository.marcarExclusaoAgendada(igrejaId, usuarioId, LocalDateTime.now());
        log.info("Exclusão agendada. igreja_id={}, por_usuario_id={}", igrejaId, usuarioId);

        emailService.enviar(igreja.getEmailContato(), "Exclusão da sua igreja no Domus foi agendada",
                "A exclusão definitiva de \"" + igreja.getNome() + "\" foi agendada e acontecerá em 10 dias. "
                        + "Você pode cancelar a qualquer momento antes disso, entrando em Configurações → Sistema.");
    }

    private void reautenticar(Usuario usuario, String senha, String googleIdToken) {
        if (usuario.getSenhaHash() != null) {
            if (senha == null || !passwordEncoder.matches(senha, usuario.getSenhaHash())) {
                throw new BusinessException("SENHA_INCORRETA", "Senha incorreta.");
            }
            return;
        }
        if (usuario.getGoogleSub() != null) {
            if (googleIdToken == null) {
                throw new BusinessException("REAUTENTICACAO_NECESSARIA", "Confirme sua identidade com o Google para continuar.");
            }
            String subConfirmado = googleAuthService.reautenticarPorGoogle(googleIdToken);
            if (!usuario.getGoogleSub().equals(subConfirmado)) {
                throw new BusinessException("REAUTENTICACAO_INVALIDA", "Não foi possível confirmar sua identidade.");
            }
            return;
        }
        throw new BusinessException("REAUTENTICACAO_NECESSARIA", "Confirme sua identidade para continuar.");
    }
```

Importar `com.domus.api.modules.usuario.Usuario`, `com.domus.api.modules.usuario.UsuarioRepository`, `com.domus.api.modules.auth.GoogleAuthService`, `org.springframework.security.crypto.password.PasswordEncoder`.

- [ ] **Step 6: Rodar e ver passar**

Run: `mvn -q test -Dtest=ExclusaoIgrejaServiceTest`
Expected: PASS

- [ ] **Step 7: Atualizar `ExclusaoIgrejaController` para passar os novos campos**

Em `agendar(...)`, trocar a chamada por:
```java
        exclusaoIgrejaService.agendar(usuarioAutenticado.getIgrejaId(), usuarioAutenticado.getUsuarioId(),
                data.nomeConfirmacao(), data.senha(), data.googleIdToken());
```

- [ ] **Step 8: Compilar tudo e rodar a suíte do módulo**

Run: `mvn -q test -Dtest=ExclusaoIgrejaServiceTest,ExclusaoIgrejaJobTest,PurgaIgrejaServiceTest`
Expected: PASS

- [ ] **Step 9: Validar manualmente com curl (senha errada → 400 com `SENHA_INCORRETA`; senha certa → 200)**

- [ ] **Step 10: Commit**

```bash
git add src/main/java/com/domus/api/modules/igreja/exclusao/ src/main/java/com/domus/api/modules/auth/GoogleAuthService.java src/test/java/com/domus/api/modules/igreja/exclusao/ExclusaoIgrejaServiceTest.java
git commit -m "feat(igreja): reautenticação (senha ou Google) obrigatória pra agendar exclusão"
```

> **Checkpoint Fase 3:** back-end 100% completo e testado. Testar manualmente todo o fluxo (agendar com senha errada/certa, agendar com Google, cancelar, esperar o job de lembrete, forçar o prazo vencido e ver a purga rodar) antes de seguir para o front.

---

## Fase 4 — Front-end

### Task 15: `endpoints.ts` e tipos

**Files:**
- Modify: `frontend/src/lib/endpoints.ts`
- Create: `frontend/src/types/exclusaoIgreja.types.ts`

**Interfaces:**
- Consumes: nada.
- Produces: `endpoints.igreja.exclusao.*`, tipos `ResumoExclusao`, `AgendarExclusaoPayload`.

- [ ] **Step 1: Adicionar ao objeto `igreja` em `endpoints.ts`**

```typescript
  igreja: {
    MINHA: '/igrejas/minha',
    exclusao: {
      RESUMO: '/igrejas/exclusao/resumo',
      AGENDAR: '/igrejas/exclusao/agendar',
      CANCELAR: '/igrejas/exclusao/cancelar',
    },
  },
```

- [ ] **Step 2: Criar os tipos**

```typescript
// frontend/src/types/exclusaoIgreja.types.ts
export interface ResumoExclusao {
  pessoas: number
  eventos: number
  movimentacoesFinanceiras: number
  celulas: number
  ministerios: number
  usuarios: number
  igrejasVinculadas: string[]
}

export interface AgendarExclusaoPayload {
  nomeConfirmacao: string
  senha?: string
  googleIdToken?: string
}
```

- [ ] **Step 3: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/endpoints.ts frontend/src/types/exclusaoIgreja.types.ts
git commit -m "feat(front): endpoints e tipos de exclusão de igreja"
```

---

### Task 16: `useAuthStore` ganha `exclusaoAgendadaEm`/`diasRestantes`

**Files:**
- Modify: `frontend/src/store/authStore.ts`
- Modify: qualquer chamador de `login(data)`/`atualizarUsuarioLogado` que precise repassar os novos campos (localizar via busca por `useAuthStore` no fluxo de login/`/auth/me` — o subagente que executar esta task deve `grep -rn "useAuthStore().login\|authStore.getState().login"` em `frontend/src` para achar todos os pontos de chamada de `login(...)` e confirmar se `Sessao` já inclui `exclusaoAgendadaEm`/`diasRestantes` ou se precisa adicionar lá também)

**Interfaces:**
- Consumes: `IgrejaDetalheDTO.exclusaoAgendadaEm`/`diasRestantes` (Task 4, via `GET /igrejas/minha`).
- Produces: `useAuthStore().exclusaoAgendadaEm`, `useAuthStore().diasRestantes`, `useAuthStore().atualizarExclusaoAgendada(data)` — consumidos pelo banner (Task 19).

- [ ] **Step 1: Adicionar os campos ao estado**

Em `AuthState`, junto dos demais campos:
```typescript
  exclusaoAgendadaEm: string | null
  diasRestantes: number | null
```

Em `estadoDeslogado`:
```typescript
  exclusaoAgendadaEm: null,
  diasRestantes: null,
```

Adicionar a ação:
```typescript
  atualizarExclusaoAgendada: (exclusaoAgendadaEm: string | null, diasRestantes: number | null) => void
```
```typescript
  atualizarExclusaoAgendada: (exclusaoAgendadaEm, diasRestantes) => set({ exclusaoAgendadaEm, diasRestantes }),
```

- [ ] **Step 2: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos (a store por si só compila; os chamadores de `login(...)` que não repassam os campos novos usam o default de `estadoDeslogado` via spread, então não quebram)

- [ ] **Step 3: Commit**

```bash
git add frontend/src/store/authStore.ts
git commit -m "feat(front): authStore ganha estado de exclusão agendada"
```

---

### Task 17: `ModalExcluirIgreja` — resumo + digitar nome + reautenticação

**Files:**
- Create: `frontend/src/components/module/configuracoes/ModalExcluirIgreja/ModalExcluirIgreja.tsx`
- Create: `frontend/src/components/module/configuracoes/ModalExcluirIgreja/ModalExcluirIgreja.module.css`

**Interfaces:**
- Consumes: `api.get(endpoints.igreja.exclusao.RESUMO)`, `api.post(endpoints.igreja.exclusao.AGENDAR, payload)` (padrão de chamada já usado no resto do front — o subagente deve conferir o cliente HTTP exato, ex. `frontend/src/lib/api.ts`, antes de escrever as chamadas), `ResumoExclusao`/`AgendarExclusaoPayload` (Task 15), `useAuthStore().login/role` para saber se o usuário logado é nativo (tem senha) ou só-Google — **checar se essa informação já está disponível no store; se não estiver, usar a heurística "mostrar sempre os dois campos, um deles opcional conforme o que o back aceitar"** já que o back decide qual reautenticação vale pela própria conta (`usuario.senhaHash != null` vs `googleSub != null`), não pelo front adivinhar.
- Produces: componente `ModalExcluirIgreja({ nomeIgreja, onClose, onExcluidoComSucesso })`, consumido pela tela de Configurações (Task 18).

Este NÃO reusa `ModalConfirmacaoCritica` diretamente — a spec exige um campo extra de reautenticação (senha ou botão "Confirmar com Google") que aquele componente genérico não tem. Segue a mesma linguagem visual (mesmo `AlertTriangle`, mesma estrutura de "digite o nome", mesmo overlay), mas como componente próprio porque a responsabilidade (buscar resumo + reautenticar) é diferente de um simples "digite para confirmar".

- [ ] **Step 1: Implementar o componente**

```tsx
'use client'

import { useEffect, useId, useRef, useState } from 'react'
import { AlertTriangle, X } from 'lucide-react'
import { api } from '@/lib/api'
import { endpoints } from '@/lib/endpoints'
import type { ResumoExclusao } from '@/types/exclusaoIgreja.types'
import styles from './ModalExcluirIgreja.module.css'

interface Props {
  nomeIgreja: string
  temSenhaNativa: boolean
  onClose: () => void
  onExcluidoComSucesso: () => void
}

export function ModalExcluirIgreja({ nomeIgreja, temSenhaNativa, onClose, onExcluidoComSucesso }: Props) {
  const [resumo, setResumo] = useState<ResumoExclusao | null>(null)
  const [digitado, setDigitado] = useState('')
  const [senha, setSenha] = useState('')
  const [carregando, setCarregando] = useState(false)
  const [erro, setErro] = useState<string | null>(null)
  const inputId = useId()

  useEffect(() => {
    api.get<ResumoExclusao>(endpoints.igreja.exclusao.RESUMO).then((res) => setResumo(res.data))
  }, [])

  const normalizar = (v: string) => v.trim().toLocaleLowerCase('pt-BR').normalize('NFD').replace(/[̀-ͯ]/g, '')
  const confere = normalizar(digitado) === normalizar(nomeIgreja)

  async function confirmar() {
    setCarregando(true)
    setErro(null)
    try {
      await api.post(endpoints.igreja.exclusao.AGENDAR, {
        nomeConfirmacao: digitado,
        senha: temSenhaNativa ? senha : undefined,
      })
      onExcluidoComSucesso()
    } catch (e: any) {
      setErro(e?.response?.data?.message ?? 'Não foi possível agendar a exclusão. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className={styles.overlay} onMouseDown={() => !carregando && onClose()}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className={styles.cabecalho}>
          <span className={styles.iconBox}><AlertTriangle size={22} aria-hidden="true" /></span>
          <h2 className={styles.titulo}>Excluir esta igreja</h2>
          <button className={styles.btnFechar} onClick={onClose} disabled={carregando} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        <div className={styles.corpo}>
          <p>
            Isso vai apagar definitivamente{resumo ? (
              <> <strong>{resumo.pessoas} pessoas</strong>, <strong>{resumo.eventos} eventos</strong>,{' '}
              <strong>{resumo.movimentacoesFinanceiras} movimentações financeiras</strong>,{' '}
              <strong>{resumo.celulas} células</strong>, <strong>{resumo.ministerios} ministérios</strong> e{' '}
              <strong>{resumo.usuarios} usuários</strong>.</>
            ) : '…'}
          </p>

          {resumo && resumo.igrejasVinculadas.length > 0 && (
            <p className={styles.avisoRede}>
              As {resumo.igrejasVinculadas.length} igrejas vinculadas ({resumo.igrejasVinculadas.join(', ')}) vão
              sair da rede — cada uma continua funcionando normalmente, com todos os dados intactos, só deixam de
              estar ligadas a esta.
            </p>
          )}

          <p>Isso é <strong>reversível por 10 dias</strong>. Depois disso, não há como recuperar.</p>

          <label className={styles.instrucao} htmlFor={inputId}>
            Para confirmar, digite <span className={styles.palavraChave}>{nomeIgreja}</span> abaixo:
          </label>
          <input id={inputId} className={styles.input} value={digitado}
                 onChange={(e) => setDigitado(e.target.value)} disabled={carregando} autoComplete="off" />

          {temSenhaNativa && (
            <>
              <label className={styles.instrucao} htmlFor={`${inputId}-senha`}>Confirme sua senha:</label>
              <input id={`${inputId}-senha`} type="password" className={styles.input} value={senha}
                     onChange={(e) => setSenha(e.target.value)} disabled={carregando} autoComplete="current-password" />
            </>
          )}

          {erro && <p className={styles.erro}>{erro}</p>}
        </div>

        <div className={styles.rodape}>
          <button className={styles.btnCancelar} onClick={onClose} disabled={carregando}>Cancelar</button>
          <button className={styles.btnConfirmar} onClick={confirmar}
                  disabled={!confere || carregando || (temSenhaNativa && !senha)}>
            {carregando ? 'Processando…' : 'Excluir esta igreja'}
          </button>
        </div>
      </div>
    </div>
  )
}
```

O botão "Confirmar com Google" (para contas só-Google, `!temSenhaNativa`) fica fora do escopo desta task por depender do componente de login Google já existente no projeto — **Task 17b** (abaixo) resolve isso depois que o subagente localizar o componente exato (`grep -rn "GoogleLogin\|google.accounts.id" frontend/src`).

**Nota de padrão a confirmar:** o subagente que executar esta task deve abrir `frontend/src/lib/api.ts` (ou equivalente) antes de escrever as chamadas `api.get`/`api.post`, para usar exatamente o cliente HTTP e o formato de erro (`e.response.data.message` é um chute baseado em Axios — confirmar contra o padrão real usado em outros componentes do projeto, ex. `PessoaForm.tsx`).

- [ ] **Step 2: Criar o CSS seguindo o padrão de `ModalConfirmacaoCritica.module.css`**

Copiar a estrutura de classes (`.overlay`, `.modal`, `.cabecalho`, `.iconBox`, `.titulo`, `.corpo`, `.instrucao`, `.palavraChave`, `.input`, `.erro`, `.rodape`, `.btnCancelar`, `.btnConfirmar`) do arquivo `ModalConfirmacaoCritica.module.css` já existente, ajustando cores para tom de "zona de perigo" (borda vermelha) conforme o mockup do Stitch já aprovado nesta sessão. Adicionar `.btnFechar` e `.avisoRede` (fundo levemente destacado, para a linha sobre as igrejas vinculadas).

- [ ] **Step 3: Rodar o type-check**

Run: `cd frontend && npx tsc --noEmit`
Expected: sem erros novos

- [ ] **Step 4: Testar manualmente no navegador** (abrir a tela que vai renderizar o modal — feito de fato na Task 18 — e confirmar visualmente: resumo carrega, digitar nome errado desabilita o botão, digitar certo habilita, responsivo em viewport de celular)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/module/configuracoes/ModalExcluirIgreja/
git commit -m "feat(front): modal de excluir igreja com resumo e reautenticação"
```

---

### Task 18: Seção "Zona de Perigo" em Configurações → Sistema

**Files:**
- Modify: a tela de configurações da igreja existente (localizar exatamente: `grep -rln "configuracoes/igreja\|ConfiguracoesIgreja" frontend/src` — a spec do roadmap já menciona `/configuracoes/igreja` como rota existente; o subagente confirma o arquivo exato antes de editar)

**Interfaces:**
- Consumes: `ModalExcluirIgreja` (Task 17).
- Produces: seção "Zona de Perigo" com botão "Excluir esta igreja" (ou "Cancelar exclusão" se já agendada), visível só para `ADMIN_IGREJA`.

- [ ] **Step 1: Localizar o arquivo da tela e o padrão de abas/seções já usado**

Run: `grep -rln "configuracoes" frontend/src/app --include="*.tsx" -i`

- [ ] **Step 2: Adicionar a seção**

No componente encontrado, dentro da área visível só para `role === 'ADMIN_IGREJA'` (seguir o padrão já usado no resto da tela para condicionar por role via `useAuthStore`), adicionar ao final:

```tsx
{role === 'ADMIN_IGREJA' && (
  <section className={styles.zonaPerigo}>
    <h2>Zona de Perigo</h2>
    {exclusaoAgendadaEm ? (
      <>
        <p>Esta igreja será excluída definitivamente em {diasRestantes} dias.</p>
        <button onClick={async () => {
          await api.post(endpoints.igreja.exclusao.CANCELAR)
          atualizarExclusaoAgendada(null, null)
        }}>Cancelar exclusão</button>
      </>
    ) : (
      <>
        <p>Excluir a igreja apaga todos os dados definitivamente, após 10 dias de carência.</p>
        <button onClick={() => setModalAberto(true)}>Excluir esta igreja</button>
      </>
    )}
    {modalAberto && (
      <ModalExcluirIgreja
        nomeIgreja={igrejaNome}
        temSenhaNativa={/* usar o dado disponível na sessão — conferir junto da Task 16 se dá pra saber sem novo endpoint */ true}
        onClose={() => setModalAberto(false)}
        onExcluidoComSucesso={() => {
          setModalAberto(false)
          atualizarExclusaoAgendada(new Date().toISOString(), 10)
        }}
      />
    )}
  </section>
)}
```

O valor de `temSenhaNativa` precisa vir de algum lugar real — **o subagente deve verificar se a sessão (`Sessao`/`useAuthStore`) já carrega essa informação; se não, a alternativa mais simples e correta é o back aceitar `senha` como opcional e decidir sozinho qual reautenticação vale (que é exatamente o que `ExclusaoIgrejaService.reautenticar` já faz na Task 14) — nesse caso o front sempre mostra o campo de senha, e só usuários que autenticam via Google (sem senha nativa) veriam esse campo obrigatório sem sentido.** Resolver definindo se o front pergunta ou sempre mostra os dois caminhos com um texto tipo "Se você usa login Google, deixe em branco e confirme pelo Google" — decisão de UX pequena, delegada ao subagente desta task com a instrução de manter simples: mostrar sempre o campo de senha; adicionar o botão Google só se, ao investigar, o subagente achar fácil reusar o componente de login Google já existente (senão, registrar como resíduo pequeno de UX no backlog, não bloquear a entrega).

- [ ] **Step 3: Testar manualmente no navegador**

Fluxo completo: como ADMIN_IGREJA, abrir Configurações → ver "Zona de Perigo" → clicar "Excluir esta igreja" → modal abre com resumo real → digitar nome errado (botão desabilitado) → digitar certo + senha certa → confirmar → seção agora mostra "será excluída em 10 dias" com botão "Cancelar exclusão" → clicar cancelar → volta ao estado normal. Testar também como LIDER/ACESSO_COMUM: seção não aparece.

- [ ] **Step 4: Testar responsividade em viewport de celular** (obrigatório por convenção do projeto — botões empilham, modal reduz padding, sem overflow horizontal)

- [ ] **Step 5: Commit**

```bash
git add <arquivo-da-tela-de-configuracoes>
git commit -m "feat(front): zona de perigo com excluir/cancelar exclusão de igreja"
```

---

### Task 19: Banner de contagem regressiva (fixo, todas as telas, só ADMIN_IGREJA)

**Files:**
- Create: `frontend/src/components/common/BannerExclusaoAgendada/BannerExclusaoAgendada.tsx`
- Create: `frontend/src/components/common/BannerExclusaoAgendada/BannerExclusaoAgendada.module.css`
- Modify: o layout raiz autenticado (localizar via `grep -rln "AuthenticatedLayout\|app/(autenticado)/layout" frontend/src/app`)

**Interfaces:**
- Consumes: `useAuthStore().exclusaoAgendadaEm/diasRestantes/role` (Task 16), `GET /igrejas/minha` já chamado na hidratação da sessão (confirmar onde isso acontece hoje — provavelmente no layout raiz ou num hook de bootstrap).
- Produces: banner fixo, renderizado condicionalmente.

- [ ] **Step 1: Implementar o componente**

```tsx
'use client'

import { useAuthStore } from '@/store/authStore'
import { api } from '@/lib/api'
import { endpoints } from '@/lib/endpoints'
import styles from './BannerExclusaoAgendada.module.css'

export function BannerExclusaoAgendada() {
  const { role, exclusaoAgendadaEm, diasRestantes, atualizarExclusaoAgendada } = useAuthStore()

  if (role !== 'ADMIN_IGREJA' || !exclusaoAgendadaEm) return null

  async function cancelar() {
    await api.post(endpoints.igreja.exclusao.CANCELAR)
    atualizarExclusaoAgendada(null, null)
  }

  return (
    <div className={styles.banner} role="alert">
      <span>Esta igreja será excluída definitivamente em {diasRestantes} dias.</span>
      <button onClick={cancelar}>Cancelar exclusão</button>
    </div>
  )
}
```

- [ ] **Step 2: Criar o CSS** (fixo no topo, fundo de alerta/vermelho, empilha texto+botão em coluna no mobile — seguir o padrão de header responsivo já usado em outras telas do projeto)

- [ ] **Step 3: Incluir no layout autenticado raiz**, logo abaixo do header principal, e confirmar que `GET /igrejas/minha` já popula `exclusaoAgendadaEm`/`diasRestantes` no store no bootstrap da sessão — se não popular, adicionar essa atribuição no ponto onde a resposta de `/igrejas/minha` já é tratada hoje.

- [ ] **Step 4: Testar manualmente**: agendar exclusão, navegar entre 3 telas diferentes, confirmar que o banner aparece em todas; logar como LIDER, confirmar que não aparece; clicar "Cancelar exclusão" no banner e confirmar que ele some sem precisar recarregar a página.

- [ ] **Step 5: Testar responsividade em viewport de celular**

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/common/BannerExclusaoAgendada/ <arquivo-do-layout-raiz>
git commit -m "feat(front): banner fixo de contagem regressiva da exclusão agendada"
```

> **Checkpoint Fase 4 / Feature completa:** rodar a suíte inteira do back (`mvn -q -o test`) e o type-check do front (`npx tsc --noEmit`) uma última vez, testar o fluxo ponta-a-ponta no navegador (agendar → banner aparece → cancelar → banner some; agendar → esperar job → e-mails chegam → purga acontece → login na igreja excluída dá erro normal de credencial inválida) antes de considerar a feature pronta pra revisão final do autor.

---

## Self-Review

**1. Spec coverage:**
- Carência de 10 dias cancelável → Tasks 1–6.
- Só `ADMIN_IGREJA` → `Permissoes.podeExcluirIgreja` (Task 5), banner condicional (Task 19), seção condicional (Task 18).
- Reautenticação (senha/Google) obrigatória ao agendar, não ao cancelar → Task 14 (`agendar`), `cancelar` nunca pede nada (Task 3, inalterado).
- Digitar nome exato → Task 3 (`agendar`) + Task 17 (front).
- Sistema funciona 100% normal durante os 10 dias → nenhuma restrição foi adicionada em nenhum outro módulo; não há task que altere comportamento fora deste módulo.
- E-mails nos 4 momentos → agendar (Task 3), D-5/D-1 (Task 6), conclusão (Task 12).
- Mãe desvincula filhas só na purga final, nunca no agendamento → Task 11 (só dentro de `purgar`, nunca em `agendar`/`cancelar`).
- Nenhum `ON DELETE CASCADE` novo → confirmado em todas as tasks 7–12, sempre `DELETE ... WHERE igreja_id = ?` explícito.
- Purga tabela-por-tabela, ordem explícita, uma transação → Tasks 7–12, todas dentro do mesmo `@Transactional` em `PurgaIgrejaService.purgar`.
- R2/Elasticsearch fora da transação Postgres, melhor-esforço → Task 10 (try/catch por foto) e Task 12 (try/catch por índice).
- E-mail final antes de apagar a linha da igreja → Task 12, ordem explícita testada com `inOrder`.
- Endpoints novos (`resumo`, `agendar`, `cancelar`, `/igrejas/minha` estendido) → Tasks 3–5, Task 4.
- Teste de integração "cria um de cada, purga, zero erro de FK" → Task 13.
- Front: modal com resumo+reautenticação, banner de contagem, zona de perigo → Tasks 15–19.
- Fora do escopo (excluir família inteira, personalizar "rede", export granular) → nenhuma task implementa isso, consistente com a spec.

**2. Placeholder scan:** Duas tasks (16 e 18) têm um parágrafo de decisão delegada ao subagente sobre `temSenhaNativa` — não é um "TBD" de lógica de negócio (a lógica do back, que é o que decide de fato, está 100% especificada e testada na Task 14); é uma decisão de UX pequena e explicitamente resolvida com uma instrução concreta ("mostrar sempre o campo de senha" como default seguro), não deixada em aberto. Mantido assim de propósito, porque a informação que falta (onde exatamente mora o dado de "esta conta tem senha nativa" no front) depende de um `grep` que só faz sentido rodar no momento da execução, não nesta investigação — e a fallback já é código real e funcional, não um "implementar depois".

**3. Type consistency:** `ResumoExclusaoResponse`/`ResumoExclusao` (back/front) têm os mesmos 7 campos em ambas as pontas (Tasks 3 e 15). `AgendarExclusaoRequest`/`AgendarExclusaoPayload` batem (`nomeConfirmacao`, `senha`, `googleIdToken` — Tasks 14 e 15). `ExclusaoIgrejaService.agendar` tem a mesma assinatura de 5 parâmetros em toda referência a partir da Task 14 (controller, testes). `PurgaIgrejaService.purgar(UUID)` mantém assinatura estável da Task 6 em diante — só o corpo cresce. `deleteAllByIgrejaId`/`deleteByIgrejaId` (JPA vs. Elasticsearch) usam nomes ligeiramente diferentes de propósito (`deleteAll` = JPA bulk nativo; `delete` sem plural = Spring Data Elasticsearch derived — nome exigido pelo framework para casar com a assinatura padrão `deleteBy<Campo>`), documentado no corpo de cada task para não confundir um executor lendo fora de ordem.

---

## Execution Handoff

Plano completo e salvo em `docs/superpowers/plans/2026-08-18-exclusao-igreja.md`. Duas opções de execução:

**1. Subagent-Driven (recomendado)** — dispatco um subagente por task, revisando entre uma e outra, iteração rápida.

**2. Inline Execution** — executo as tasks nesta sessão via executing-plans, com checkpoints pra revisão.

Qual prefere?
