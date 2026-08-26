# Inscrição `AGUARDANDO_PAGAMENTO` (Plano 1/5) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir o backend para que `InscricaoEvento` de evento pago só vire `CONFIRMADA`
quando o pagamento for confirmado pelo webhook — hoje ela confirma na hora, antes de
qualquer pagamento acontecer.

**Architecture:** Novo valor `AGUARDANDO_PAGAMENTO` no enum `StatusInscricao` (sem
migration — a coluna `inscricao_evento.status` é `VARCHAR(20)` sem `CHECK` constraint).
Evento pago passa a criar a inscrição nesse status; o webhook do Mercado Pago (que já
confirma `CobrancaEvento`) passa a confirmar a `InscricaoEvento` vinculada na mesma
chamada; o job de expiração (que já expira `CobrancaEvento` vencida) passa a cancelar a
`InscricaoEvento` vinculada. A reserva de vaga (`contarOcupadas`/`validarVaga`, já
baseada em `CobrancaEvento`) e o cancelamento manual (`cancelarInscricao`, que já seta
`CANCELADA` incondicionalmente) não mudam — já estão corretos.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Mockito puro (sem contexto Spring).

**Spec:** `docs/superpowers/specs/2026-08-26-fluxo-pagamento-evento-ux-design.md` (seção
"Correção no backend: novo status `AGUARDANDO_PAGAMENTO`").

## Global Constraints

- Mockito puro (`mock()` manual em `@BeforeEach`, Estilo A) — é o padrão dominante nos
  arquivos tocados por este plano (`InscricaoServiceTest`, `MercadoPagoWebhookServiceTest`,
  `CobrancaEventoExpiracaoJobTest`).
- AssertJ (`assertThat`/`assertThatThrownBy`) para as novas assertions.
- Nomenclatura de teste: método em `snake_case`/português descrevendo o cenário.
- Rodar `mvn -q test -Dtest=NomeDaClasse` depois de cada task; rodar `mvn -q test`
  completo antes do commit final (Task 6).
- Não alterar teste existente para fazer passar — se um teste hoje assume `CONFIRMADA`
  para evento pago e quebrar, ele está desatualizado pela mudança de regra: atualizar a
  asserção para o novo status correto, nunca enfraquecer a checagem.

---

### Task 1: Novo status `AGUARDANDO_PAGAMENTO` no enum e helper na entidade

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/StatusInscricao.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoEventoTest.java` (novo arquivo)

**Interfaces:**
- Produces: `StatusInscricao.AGUARDANDO_PAGAMENTO` (novo valor do enum);
  `InscricaoEvento.estaAguardandoPagamento(): boolean`.

- [ ] **Step 1: Escrever o teste que falha**

Criar `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoEventoTest.java`:

```java
package com.domus.api.modules.evento.inscricao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InscricaoEventoTest {

    @Test
    void estaAguardandoPagamentoEhVerdadeiroSoNesseStatus() {
        InscricaoEvento aguardando = InscricaoEvento.builder()
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        InscricaoEvento confirmada = InscricaoEvento.builder()
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .status(StatusInscricao.CANCELADA).build();

        assertThat(aguardando.estaAguardandoPagamento()).isTrue();
        assertThat(confirmada.estaAguardandoPagamento()).isFalse();
        assertThat(cancelada.estaAguardandoPagamento()).isFalse();
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=InscricaoEventoTest`
Expected: FAIL — compilação quebra (`AGUARDANDO_PAGAMENTO`/`estaAguardandoPagamento` não existem).

- [ ] **Step 3: Implementar**

Em `StatusInscricao.java`, adicionar o valor novo:

```java
package com.domus.api.modules.evento.inscricao;

public enum StatusInscricao {
    AGUARDANDO_PAGAMENTO,
    CONFIRMADA,
    CANCELADA
}
```

Em `InscricaoEvento.java`, logo abaixo de `estaConfirmada()`:

```java
    public boolean estaAguardandoPagamento() {
        return status == StatusInscricao.AGUARDANDO_PAGAMENTO;
    }
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=InscricaoEventoTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/StatusInscricao.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoEvento.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoEventoTest.java
git commit -m "feat(inscricao): adiciona status AGUARDANDO_PAGAMENTO"
```

---

### Task 2: Evento pago cria a inscrição como `AGUARDANDO_PAGAMENTO`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java:139-156`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `StatusInscricao.AGUARDANDO_PAGAMENTO` (Task 1).
- Produces: nenhuma interface nova — comportamento interno de `inscreverInterno`.

- [ ] **Step 1: Escrever o teste que falha**

Adicionar em `InscricaoServiceTest.java`, logo depois de `eventoPagoCriaCobrancaDoTitularComoEuPagoAgora`:

```java
    @Test
    void eventoPagoCriaInscricaoComoAguardandoPagamentoNaoComoConfirmada() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(50));
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(cobrancaEventoService.criarParaTitular(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        ArgumentCaptor<InscricaoEvento> captor = ArgumentCaptor.forClass(InscricaoEvento.class);
        verify(inscricaoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void eventoGratuitoContinuaCriandoInscricaoComoConfirmada() {
        dado(evento(10), membro(Vinculo.MEMBRO), 0);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        ArgumentCaptor<InscricaoEvento> captor = ArgumentCaptor.forClass(InscricaoEvento.class);
        verify(inscricaoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }
```

Adicionar o import no topo do arquivo, junto dos outros `import static`/`import org.mockito.*`:

```java
import org.mockito.ArgumentCaptor;
```

- [ ] **Step 2: Rodar os testes e confirmar que falham**

Run: `mvn -q test -Dtest=InscricaoServiceTest#eventoPagoCriaInscricaoComoAguardandoPagamentoNaoComoConfirmada`
Expected: FAIL — `captor.getValue().getStatus()` é `CONFIRMADA`, esperado `AGUARDANDO_PAGAMENTO`.

- [ ] **Step 3: Implementar**

Em `InscricaoService.java`, substituir o bloco (linhas ~139-156):

```java
        validarVaga(evento, 1);

        if (inscricao != null) {
            inscricao.setStatus(StatusInscricao.CONFIRMADA);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
            inscricao.setInscritoPorExcecao(porExcecao);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .pessoa(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(StatusInscricao.CONFIRMADA)
                    .inscritoPorExcecao(porExcecao)
                    .build();
        }
```

por:

```java
        validarVaga(evento, 1);

        // Evento pago: a inscrição nasce AGUARDANDO_PAGAMENTO — só vira CONFIRMADA quando
        // o webhook do Mercado Pago confirmar o pagamento (MercadoPagoWebhookService). A
        // reserva de vaga não depende deste status (ver contarOcupadas, baseada em
        // CobrancaEvento) — este status é só o que aparece pra quem lista inscritos.
        StatusInscricao statusInicial = evento.getPreco() != null
                ? StatusInscricao.AGUARDANDO_PAGAMENTO
                : StatusInscricao.CONFIRMADA;

        if (inscricao != null) {
            inscricao.setStatus(statusInicial);
            inscricao.setInscritoPorUsuarioId(inscritoPorOuNull);
            inscricao.setInscritoPorExcecao(porExcecao);
        } else {
            inscricao = InscricaoEvento.builder()
                    .igreja(evento.getIgreja())
                    .evento(evento)
                    .pessoa(membro)
                    .inscritoPorUsuarioId(inscritoPorOuNull)
                    .status(statusInicial)
                    .inscritoPorExcecao(porExcecao)
                    .build();
        }
```

Também atualizar o comentário logo abaixo (linhas ~158-163 do arquivo original,
"Evento pago: titular sempre..."), que hoje diz explicitamente que a inscrição confirma
na hora "pago ou não" — isso deixou de ser verdade:

```java
        // Evento pago: titular sempre "eu pago agora" (nunca vira link, diferente do
        // acompanhante em adicionarAcompanhante) — vaga fica reservada via CobrancaEvento
        // (ver validarVaga), não pela InscricaoEvento (que agora só confirma quando o
        // pagamento é aprovado, ver statusInicial acima). Quem assina como "criado por":
        // quem inscreveu (admin/líder em lote) ou, na auto-inscrição (inscritoPorOuNull
        // nulo), o próprio usuário do titular — sempre existe, pois só se auto-inscreve
        // quem está logado.
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: PASS em todos — inclusive os dois testes novos. Se algum teste antigo do
arquivo quebrar por assumir `CONFIRMADA` num evento pago (ex.: algum teste de listagem
que crie a inscrição via `service.inscrever` direto em vez de builder), ajustar a
asserção desse teste para `AGUARDANDO_PAGAMENTO` — é o comportamento novo correto, não
um teste a preservar como estava.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "fix(inscricao): evento pago nasce AGUARDANDO_PAGAMENTO, nao CONFIRMADA"
```

---

### Task 3: `minhaInscricao` continua devolvendo a cobrança pendente quando `AGUARDANDO_PAGAMENTO`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java:278-284`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `InscricaoEvento.estaAguardandoPagamento()` (Task 1).
- Produces: nenhuma mudança de assinatura — `minhaInscricao(UUID, UUID): MinhaInscricaoResponse` continua igual.

**Por que este task existe:** `minhaInscricao` hoje filtra por `InscricaoEvento::estaConfirmada`
antes de montar a resposta — com a Task 2, uma inscrição recém-criada em evento pago passa
a ter `status = AGUARDANDO_PAGAMENTO`, então esse filtro passaria a devolver
`naoInscrito()` e o front perderia o `cobrancaPendenteId` logo após criar a inscrição
(quebra o cenário já documentado no comentário de `cobrancaPendenteDoTitular`: "se a
pessoa recarregar a página antes de pagar").

- [ ] **Step 1: Escrever o teste que falha**

Adicionar em `InscricaoServiceTest.java`:

```java
    @Test
    void minhaInscricaoDevolveCobrancaPendenteQuandoAguardandoPagamento() {
        InscricaoEvento inscricaoAguardando = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId))
                .thenReturn(Optional.of(inscricaoAguardando));
        var cobranca = cobrancaPendente();
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId)).thenReturn(List.of(cobranca));

        MinhaInscricaoResponse resposta = service.minhaInscricao(eventoId, pessoaId);

        assertThat(resposta.inscrito()).isFalse();
        assertThat(resposta.cobrancaPendenteId()).isNotNull();
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=InscricaoServiceTest#minhaInscricaoDevolveCobrancaPendenteQuandoAguardandoPagamento`
Expected: FAIL — `resposta` é o singleton `naoInscrito()` (`cobrancaPendenteId` nulo),
porque o filtro atual só passa `CONFIRMADA`.

- [ ] **Step 3: Implementar**

Em `InscricaoService.java`, substituir:

```java
    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID pessoaId) {
        return inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(InscricaoEvento::estaConfirmada)
                .map(i -> MinhaInscricaoResponse.from(i, cobrancaPendenteDoTitular(i.getId(), pessoaId)))
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }
```

por:

```java
    @Transactional(readOnly = true)
    public MinhaInscricaoResponse minhaInscricao(UUID eventoId, UUID pessoaId) {
        return inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)
                .filter(i -> i.estaConfirmada() || i.estaAguardandoPagamento())
                .map(i -> MinhaInscricaoResponse.from(i, cobrancaPendenteDoTitular(i.getId(), pessoaId)))
                .orElseGet(MinhaInscricaoResponse::naoInscrito);
    }
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=InscricaoServiceTest`
Expected: PASS em todos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "fix(inscricao): minhaInscricao mantem cobranca pendente visivel em AGUARDANDO_PAGAMENTO"
```

---

### Task 4: Webhook confirma a `InscricaoEvento` junto com a `CobrancaEvento`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java`

**Interfaces:**
- Consumes: `InscricaoRepository.findById(UUID): Optional<InscricaoEvento>` (já existe,
  via `JpaRepository`); `InscricaoRepository.save(InscricaoEvento)` (já existe);
  `InscricaoEvento.setStatus(StatusInscricao)` (já existe, `@Setter` do Lombok);
  `CobrancaEvento.getInscricaoId(): UUID` (já existe).
- Produces: `MercadoPagoWebhookService(CobrancaEventoRepository, InscricaoRepository, NotificacaoService)`
  — construtor com um parâmetro novo (`InscricaoRepository`), na 2ª posição.

- [ ] **Step 1: Escrever o teste que falha**

Em `MercadoPagoWebhookServiceTest.java`, adicionar o import e o mock novo, e um teste:

```java
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
```

No `@BeforeEach`, adicionar o mock e passar no construtor (ajustar a linha existente):

```java
    InscricaoRepository inscricaoRepository;

    @BeforeEach
    void setup() {
        cobrancaRepository = mock(CobrancaEventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        service = new MercadoPagoWebhookService(cobrancaRepository, inscricaoRepository, notificacaoService);
    }
```

Novo teste, logo após `confirmaCobrancaEncontradaPeloExternalReferenceQuandoStatusEhAprovado`:

```java
    @Test
    void confirmaInscricaoVinculadaQuandoPagamentoAprovado() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), inscricaoId,
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(inscricaoRepository).save(inscricao);
    }
```

Adicionar `import static org.assertj.core.api.Assertions.assertThat;` no topo, junto dos
outros imports.

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=MercadoPagoWebhookServiceTest`
Expected: FAIL — compilação quebra (construtor com 2 parâmetros não existe mais/mock
`inscricaoRepository` não é usado por `service`), depois de ajustar a compilação, o
teste novo falha porque `inscricao.getStatus()` continua `AGUARDANDO_PAGAMENTO`.

- [ ] **Step 3: Implementar**

Em `MercadoPagoWebhookService.java`, adicionar o import e o campo/injeção:

```java
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
```

```java
    private final CobrancaEventoRepository cobrancaRepository;
    private final InscricaoRepository inscricaoRepository;
    private final NotificacaoService notificacaoService;

    public MercadoPagoWebhookService(CobrancaEventoRepository cobrancaRepository,
                                      InscricaoRepository inscricaoRepository,
                                      NotificacaoService notificacaoService) {
        this.cobrancaRepository = cobrancaRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.notificacaoService = notificacaoService;
    }
```

E no corpo de `confirmarPagamento`, dentro do `.ifPresent(cobranca -> { ... })` do caminho
aprovado, logo depois de `cobrancaRepository.save(cobranca);`:

```java
        cobrancaRepository.findById(UUID.fromString(cobrancaId)).ifPresent(cobranca -> {
            cobranca.marcarComoPago(mpPaymentId);
            cobrancaRepository.save(cobranca);

            // A inscrição só confirma quando o pagamento é aprovado de verdade — ver
            // InscricaoService.inscreverInterno, que a cria como AGUARDANDO_PAGAMENTO.
            inscricaoRepository.findById(cobranca.getInscricaoId()).ifPresent(inscricao -> {
                inscricao.setStatus(StatusInscricao.CONFIRMADA);
                inscricaoRepository.save(inscricao);
            });

            if (!cobranca.ehDoTitular()) {
                notificacaoService.criar(
                    TipoNotificacao.COBRANCA_EVENTO_PAGA,
                    cobranca.getIgrejaId(),
                    cobranca.getCriadoPorUsuarioId(),
                    "O pagamento foi confirmado.",
                    "/eventos/" + cobranca.getEventoId() + "/inscritos");
            }
        });
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=MercadoPagoWebhookServiceTest`
Expected: PASS em todos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java \
        src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java
git commit -m "fix(pagamento): webhook confirma InscricaoEvento junto com CobrancaEvento"
```

---

### Task 5: Job de expiração cancela a `InscricaoEvento` vinculada

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJob.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJobTest.java`

**Interfaces:**
- Consumes: `InscricaoRepository.findAllById(Iterable<UUID>): List<InscricaoEvento>` (já
  existe, via `JpaRepository`); `InscricaoRepository.saveAll(Iterable<InscricaoEvento>)`
  (já existe); `InscricaoEvento.estaAguardandoPagamento()` (Task 1).
- Produces: `CobrancaEventoExpiracaoJob(CobrancaEventoRepository, InscricaoRepository)` —
  construtor com um parâmetro novo, na 2ª posição.

**Por que checar `estaAguardandoPagamento()` antes de cancelar:** uma `CobrancaEvento`
PENDENTE só expira antes de ser paga — nesse ponto a `InscricaoEvento` vinculada só pode
estar `AGUARDANDO_PAGAMENTO` (nunca `CONFIRMADA`, que só acontece via webhook aprovado, e
nunca `CANCELADA`, que já teria cancelado a cobrança junto via `estornarCobrancasDaInscricao`).
A checagem é defensiva, não motivo de teste tardio: evita sobrescrever um status que já
mudou por outro caminho entre o `findByStatusAndExpiraEmBefore` e a execução do job.

- [ ] **Step 1: Escrever o teste que falha**

Em `CobrancaEventoExpiracaoJobTest.java`, adicionar imports e mock:

```java
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import static org.assertj.core.api.Assertions.assertThat;
```

No `@BeforeEach`:

```java
    InscricaoRepository inscricaoRepository;

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        job = new CobrancaEventoExpiracaoJob(repository, inscricaoRepository);
    }
```

Novo teste:

```java
    @Test
    void cancelaInscricaoVinculadaQuandoCobrancaExpira() {
        UUID inscricaoId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), inscricaoId,
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().minusSeconds(60), UUID.randomUUID(), null);
        when(repository.findByStatusAndExpiraEmBefore(eq(StatusCobranca.PENDENTE), any()))
            .thenReturn(List.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findAllById(List.of(inscricaoId))).thenReturn(List.of(inscricao));

        job.executar();

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        verify(inscricaoRepository).saveAll(List.of(inscricao));
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=CobrancaEventoExpiracaoJobTest`
Expected: FAIL — compilação quebra (construtor com 2 parâmetros), depois de ajustar,
`inscricaoRepository.saveAll` nunca é chamado.

- [ ] **Step 3: Implementar**

Em `CobrancaEventoExpiracaoJob.java`:

```java
package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira cobrança de evento pago vencida, liberando a vaga, e cancela a inscrição
 * vinculada (que nasceu AGUARDANDO_PAGAMENTO — ninguém pagou a tempo). Roda a cada 5
 * minutos — suficiente dado que o prazo mínimo de cobrança é 30 minutos. */
@Component
public class CobrancaEventoExpiracaoJob {

    private final CobrancaEventoRepository repository;
    private final InscricaoRepository inscricaoRepository;

    public CobrancaEventoExpiracaoJob(CobrancaEventoRepository repository,
                                       InscricaoRepository inscricaoRepository) {
        this.repository = repository;
        this.inscricaoRepository = inscricaoRepository;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void executar() {
        var vencidas = repository.findByStatusAndExpiraEmBefore(StatusCobranca.PENDENTE, Instant.now());
        if (vencidas.isEmpty()) return;

        vencidas.forEach(CobrancaEvento::marcarComoExpirado);
        repository.saveAll(vencidas);

        var inscricaoIds = vencidas.stream().map(CobrancaEvento::getInscricaoId).toList();
        var inscricoes = inscricaoRepository.findAllById(inscricaoIds).stream()
                .filter(InscricaoEvento::estaAguardandoPagamento)
                .toList();
        if (inscricoes.isEmpty()) return;

        inscricoes.forEach(i -> i.setStatus(StatusInscricao.CANCELADA));
        inscricaoRepository.saveAll(inscricoes);
    }
}
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=CobrancaEventoExpiracaoJobTest`
Expected: PASS em todos, inclusive o teste antigo `naoFazNadaQuandoNaoHaCobrancaVencida`
(que continua sem tocar em `inscricaoRepository`, pois retorna cedo quando `vencidas`
está vazia).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJob.java \
        src/test/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJobTest.java
git commit -m "fix(pagamento): job de expiracao cancela InscricaoEvento vinculada"
```

---

### Task 6: Suíte completa e checagem manual dos endpoints tocados

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Rodar a suíte completa**

Run: `mvn -q test`
Expected: PASS em todos os módulos (Docker precisa estar rodando, para os testes
`@DataJpaTest`/`@SpringBootTest` que sobem Postgres via Testcontainers).

- [ ] **Step 2: Checar manualmente com curl (ambiente local) o fluxo ponta a ponta**

Com a API local rodando (`docker-compose up` + app):
1. Criar evento pago, se inscrever (`POST /eventos/{id}/inscrever`) — checar que
   `GET /eventos/{id}/inscritos` (admin) NÃO lista essa pessoa ainda.
2. `GET /eventos/{id}/minha-inscricao` — checar que `inscrito: false` e
   `cobrancaPendenteId` preenchido.
3. Simular o webhook aprovado (`POST /webhooks/mercadopago` com payload de teste,
   `status: approved`) — checar que `GET /eventos/{id}/inscritos` agora lista a pessoa.

Não é um teste automatizado (a ordem de `requestMatchers`/webhook externo não é coberta
por unitário, por convenção do projeto) — é a validação manual que a convenção de teste
do projeto pede para este tipo de mudança.

- [ ] **Step 3: Commit final (se algum ajuste tiver sido necessário no passo 1)**

```bash
git add -A
git commit -m "test(inscricao): ajustes finais apos rodar suite completa"
```

(Pular este commit se a suíte já passou de primeira, sem nenhum ajuste.)
