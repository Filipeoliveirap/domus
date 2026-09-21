# Pagamento para convidado sem cadastro — Backend (Plano 4b.1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `InscricaoService.inscreverConvidado` (Visitante/Pessoa de fora e convite
público) passa a criar `CobrancaEvento` em evento pago, em vez de bloquear com
`CONVIDADO_NAO_PODE_EM_EVENTO_PAGO`.

**Architecture:** Migration relaxa o `CHECK` de `cobranca_evento` (aceita
`pessoa_id`/`acompanhante_id` os dois nulos = convidado sem cadastro, resolvido só por
`inscricao_id`) e torna `criado_por_usuario_id` nullable (auto-registro anônimo via
convite não tem usuário). `inscreverConvidado` passa a espelhar `inscreverInterno`:
`AGUARDANDO_PAGAMENTO` + `CobrancaEvento` via `criarParaTerceiro` (já aceita
pessoaId/acompanhanteId nulos depois da migration, sem precisar de método novo).
`ConvidadoResponse` ganha `cobrancaId`/`tokenLinkPublico`, igual
`PessoaInscritaComCobranca` já tem. `CobrancaController` resolve nome do pagador via
`InscricaoEvento` quando não há nem pessoa nem acompanhante.

**Tech Stack:** Java 21/Spring Boot, Flyway, Mockito puro + `@SpringBootTest`/`@DataJpaTest`.

**Spec:** `docs/superpowers/specs/2026-08-26-pagamento-convidado-sem-cadastro-design.md`
(seções 2 e 3).

## Global Constraints

- Mockito puro (Estilo A) pros testes de `InscricaoService`; `@SpringBootTest`+`@Sql`
  reais pros de `CobrancaController` — mesmo padrão dos planos anteriores.
- Não mexer em `AcompanhanteInscricao`/leitura de acompanhante — só o caminho de
  convidado sem cadastro.
- `criarParaTerceiro` (já existe) é reaproveitado como está — nenhum método novo em
  `CobrancaEventoService`.

---

### Task 1: Migration — relaxar `CHECK` e `criado_por_usuario_id`

**Files:**
- Create: `src/main/resources/db/migration/V30__cobranca_convidado_sem_cadastro.sql`

- [ ] **Step 1: Escrever a migration**

```sql
-- V30: cobrança de evento pago para convidado sem cadastro (Visitante/Pessoa de fora,
-- convite público) — resolvida só por inscricao_id, sem pessoa_id nem acompanhante_id.

ALTER TABLE cobranca_evento DROP CONSTRAINT cobranca_evento_pessoa_xor_acompanhante;
ALTER TABLE cobranca_evento ADD CONSTRAINT cobranca_evento_pessoa_xor_acompanhante CHECK (
    (pessoa_id IS NOT NULL AND acompanhante_id IS NULL) OR
    (pessoa_id IS NULL AND acompanhante_id IS NOT NULL) OR
    (pessoa_id IS NULL AND acompanhante_id IS NULL)
);

-- NULL = auto-registro anônimo via /convite/{token} (sem sessão, sem usuário nenhum) —
-- mesmo padrão semântico que inscricao_evento.inscrito_por_usuario_id já usa.
ALTER TABLE cobranca_evento ALTER COLUMN criado_por_usuario_id DROP NOT NULL;
```

- [ ] **Step 2: Rodar a suíte pra confirmar que o Flyway aplica a migration sem erro**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=CobrancaEventoRepositoryTest
```

Expected: PASS (a suíte sobe o schema do zero via Testcontainers, incluindo a migration nova).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V30__cobranca_convidado_sem_cadastro.sql
git commit -m "feat(pagamento): migration para cobranca de convidado sem cadastro"
```

---

### Task 2: `CobrancaEvento` — relaxar a validação do construtor

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoTest.java` (novo arquivo, se não existir; checar antes)

**Interfaces:**
- Produces: `new CobrancaEvento(igrejaId, eventoId, inscricaoId, null, null, valor,
  expiraEm, criadoPorUsuarioIdOuNull, tokenOuNull)` passa a ser uma chamada válida
  (hoje lança `IllegalArgumentException`).

- [ ] **Step 1: Checar se já existe teste de entidade pra este construtor**

```bash
find src/test -iname "CobrancaEventoTest*"
```

Se existir, adicionar o teste novo nele; se não, criar o arquivo do Step 2.

- [ ] **Step 2: Escrever o teste que falha**

```java
package com.domus.api.modules.pagamento.cobranca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CobrancaEventoTest {

    @Test
    void aceitaPessoaIdEAcompanhanteIdOsDoisNulosParaConvidadoSemCadastro() {
        assertThatCode(() -> new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                BigDecimal.TEN, Instant.now().plusSeconds(600), null, null))
            .doesNotThrowAnyException();
    }

    @Test
    void recusaPessoaIdEAcompanhanteIdOsDoisPreenchidos() {
        assertThatThrownBy(() -> new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 3: Rodar o teste e confirmar que falha**

```bash
mvn -o test -Dtest=CobrancaEventoTest
```

Expected: FAIL em `aceitaPessoaIdEAcompanhanteIdOsDoisNulosParaConvidadoSemCadastro`
(construtor atual lança `IllegalArgumentException` pros dois nulos).

- [ ] **Step 4: Implementar**

Em `CobrancaEvento.java`, trocar:

```java
        if ((pessoaId == null) == (acompanhanteId == null)) {
            throw new IllegalArgumentException(
                "CobrancaEvento precisa de exatamente pessoaId OU acompanhanteId");
        }
```

por:

```java
        // Os dois nulos = convidado sem cadastro (resolvido só por inscricaoId, ver
        // CobrancaController) — só os dois PREENCHIDOS ao mesmo tempo é inválido.
        if (pessoaId != null && acompanhanteId != null) {
            throw new IllegalArgumentException(
                "CobrancaEvento não pode ter pessoaId e acompanhanteId ao mesmo tempo");
        }
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

```bash
mvn -o test -Dtest=CobrancaEventoTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoTest.java
git commit -m "feat(pagamento): CobrancaEvento aceita pessoaId e acompanhanteId nulos (convidado sem cadastro)"
```

---

### Task 3: `inscreverConvidado` cria cobrança em evento pago

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java`

**Interfaces:**
- Consumes: `CobrancaEventoService.criarParaTerceiro(igrejaId, eventoId, inscricaoId,
  null, null, valor, criadoPorUsuarioIdOuNull, gerarLink)` (já existe, Task 2 libera
  passar os dois nulos).
- Produces: `InscricaoService.inscreverConvidado(eventoId, igrejaId, nome, telefone,
  convidadoPorPessoaId, inscritoPorUsuarioId, visitanteId, gerarLink):
  ResultadoConvidado` — **mudança de assinatura** (parâmetro `gerarLink` novo) e de
  tipo de retorno (novo record `ResultadoConvidado(InscricaoEvento inscricao,
  CobrancaEvento cobrancaOuNull)`, no lugar de `InscricaoEvento` puro). As duas
  chamadoras (`InscricaoController.criarConvidado`, `ConviteController.entrar`) e o
  `ConvidadoResponse.from` mudam na Task 4 pra bater com essa assinatura nova — os dois
  lados desta mudança fazem parte da mesma Task, senão o projeto não compila entre uma
  e outra.

- [ ] **Step 1: Escrever os testes que falham**

Adicionar em `InscricaoServiceTest.java` (perto dos outros testes de convidado —
buscar `inscreverConvidado` no arquivo pra achar a vizinhança; se não houver nenhum
teste desse método ainda, adicionar no fim do arquivo, antes do `}` final):

```java
    @Test
    void inscreverConvidadoEmEventoPagoCriaComoAguardandoPagamento() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(80));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> {
            InscricaoEvento i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
        when(cobrancaEventoService.criarParaTerceiro(eq(igrejaId), eq(eventoId), any(),
                isNull(), isNull(), eq(java.math.BigDecimal.valueOf(80)), any(), eq(false)))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        var resultado = service.inscreverConvidado(eventoId, igrejaId, "Fulano", "11999999999",
                null, usuarioId, null, false);

        assertThat(resultado.inscricao().getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        verify(cobrancaEventoService).criarParaTerceiro(eq(igrejaId), eq(eventoId), any(),
                isNull(), isNull(), eq(java.math.BigDecimal.valueOf(80)), any(), eq(false));
    }

    @Test
    void inscreverConvidadoEmEventoPagoSemContaConectadaRecusaAntesDeCriarQualquerCoisa() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(80));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(contaPagamentoIgrejaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Fulano",
                "11999999999", null, usuarioId, null, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "IGREJA_SEM_CONTA_PAGAMENTO");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoEmEventoGratuitoContinuaCriandoComoConfirmadaSemCobranca() {
        Evento evento = evento(10);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.inscreverConvidado(eventoId, igrejaId, "Fulano", "11999999999",
                null, usuarioId, null, false);

        assertThat(resultado.inscricao().getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(resultado.cobranca()).isNull();
        verify(cobrancaEventoService, never())
                .criarParaTerceiro(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }
```

Se `isNull`/`anyBoolean` não estiverem já importados no topo do arquivo (checar os
`import static org.mockito.ArgumentMatchers.*` existentes), adicionar:

```java
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
```

(um dos dois já pode existir — só adicionar o que faltar).

- [ ] **Step 2: Rodar os testes e confirmar que falham**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=InscricaoServiceTest#inscreverConvidadoEmEventoPagoCriaComoAguardandoPagamento+inscreverConvidadoEmEventoPagoSemContaConectadaRecusaAntesDeCriarQualquerCoisa+inscreverConvidadoEmEventoGratuitoContinuaCriandoComoConfirmadaSemCobranca
```

Expected: FAIL — compilação quebra (`inscreverConvidado` ainda não tem o parâmetro
`gerarLink`, nem devolve `.inscricao()`/`.cobranca()`).

- [ ] **Step 3: Implementar**

Em `InscricaoService.java`, trocar o corpo de `inscreverConvidado` (a assinatura e o
bloco de bloqueio de evento pago, comentário incluso — este é exatamente o comentário
que a spec desta feature referencia):

```java
    @Transactional
    public InscricaoEvento inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
                                               String telefone, UUID convidadoPorPessoaId,
                                               UUID inscritoPorUsuarioId, UUID visitanteId) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        validarOrganizaInscricao(evento, "Este evento não permite convidados.");
        if (evento.isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        // Convidado de topo não tem Pessoa nem é um acompanhante — CobrancaEvento exige um
        // dos dois (ver construtor), então não há como gerar cobrança pra ele hoje. Sem essa
        // trava, evento pago poderia ser "furado" inscrevendo qualquer um por aqui, sem pagar
        // nada, e essa vaga ficaria invisível pra contarPessoasComVagaReservada (ver
        // contarOcupadas) — overbooking real. Bloqueia até existir uma forma de cobrar
        // convidado sem cadastro (fora do escopo desta task).
        if (evento.getPreco() != null) {
            throw new BusinessException("CONVIDADO_NAO_PODE_EM_EVENTO_PAGO",
                    "Este evento é pago — inscreva com uma pessoa cadastrada para gerar a cobrança.");
        }
        validarEventoAberto(evento);
        validarConvidadoTopoNaoDuplicado(eventoId, nome, telefone, visitanteId, inscritoPorUsuarioId);
        validarVaga(evento, 1);

        Pessoa convidadoPor = convidadoPorPessoaId == null ? null
                : membroRepository.findByIdAndIgrejaId(convidadoPorPessoaId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        Visitante visitante = visitanteId == null ? null
                : visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));

        InscricaoEvento inscricao = InscricaoEvento.builder()
                .igreja(evento.getIgreja())
                .evento(evento)
                .pessoa(null)
                .nomeConvidado(TextoUtil.capitalizar(nome))
                .telefoneConvidado(telefone)
                .convidadoPor(convidadoPor)
                .visitante(visitante)
                .inscritoPorUsuarioId(inscritoPorUsuarioId)
                .status(StatusInscricao.CONFIRMADA)
                .build();

        InscricaoEvento salva = inscricaoRepository.save(inscricao);
        log.info("Convidado inscrito. evento_id={}, convidado_por_pessoa_id={}, inscrito_por_usuario_id={}, igreja_id={}",
                eventoId, convidadoPorPessoaId, inscritoPorUsuarioId, igrejaId);
        return salva;
    }
```

por:

```java
    /** Devolvido por {@link #inscreverConvidado} — {@code cobranca} é nulo em evento
     *  gratuito, ou tem valor em evento pago (mesmo padrão de {@code ResultadoInscricao},
     *  usado por {@link #inscreverInterno}). */
    public record ResultadoConvidado(InscricaoEvento inscricao, CobrancaEvento cobranca) {}

    @Transactional
    public ResultadoConvidado inscreverConvidado(UUID eventoId, UUID igrejaId, String nome,
                                               String telefone, UUID convidadoPorPessoaId,
                                               UUID inscritoPorUsuarioId, UUID visitanteId,
                                               boolean gerarLink) {
        var idsFamilia = familiaIgrejaService.idsDaFamiliaCompleta(igrejaId);
        Evento evento = eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, idsFamilia)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));

        validarOrganizaInscricao(evento, "Este evento não permite convidados.");
        if (evento.isExclusivoMembros()) {
            throw new BusinessException("EXCLUSIVO_MEMBROS",
                    "Este evento é exclusivo para membros — não é possível levar convidados.");
        }
        if (evento.getPreco() != null) {
            validarContaPagamentoConectada(igrejaId);
        }
        validarEventoAberto(evento);
        validarConvidadoTopoNaoDuplicado(eventoId, nome, telefone, visitanteId, inscritoPorUsuarioId);
        validarVaga(evento, 1);

        Pessoa convidadoPor = convidadoPorPessoaId == null ? null
                : membroRepository.findByIdAndIgrejaId(convidadoPorPessoaId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Pessoa não encontrada."));

        Visitante visitante = visitanteId == null ? null
                : visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)
                        .orElseThrow(() -> new ResourceNotFoundException("Visitante não encontrado."));

        // Evento pago: a inscrição nasce AGUARDANDO_PAGAMENTO, mesmo padrão de
        // inscreverInterno (ver Plano 1) — só confirma quando o webhook aprovar.
        StatusInscricao statusInicial = evento.getPreco() != null
                ? StatusInscricao.AGUARDANDO_PAGAMENTO
                : StatusInscricao.CONFIRMADA;

        InscricaoEvento inscricao = InscricaoEvento.builder()
                .igreja(evento.getIgreja())
                .evento(evento)
                .pessoa(null)
                .nomeConvidado(TextoUtil.capitalizar(nome))
                .telefoneConvidado(telefone)
                .convidadoPor(convidadoPor)
                .visitante(visitante)
                .inscritoPorUsuarioId(inscritoPorUsuarioId)
                .status(statusInicial)
                .build();

        InscricaoEvento salva = inscricaoRepository.save(inscricao);

        // pessoaId/acompanhanteId nulos = convidado sem cadastro (Plano 4b) — resolvido só
        // por inscricaoId (ver CobrancaController). criadoPorUsuarioId pode ser nulo aqui
        // (auto-registro anônimo via /convite/{token}, ver migration V30).
        CobrancaEvento cobranca = null;
        if (evento.getPreco() != null) {
            cobranca = cobrancaEventoService.criarParaTerceiro(igrejaId, eventoId, salva.getId(),
                    null, null, evento.getPreco(), inscritoPorUsuarioId, gerarLink);
        }

        log.info("Convidado inscrito. evento_id={}, convidado_por_pessoa_id={}, inscrito_por_usuario_id={}, igreja_id={}",
                eventoId, convidadoPorPessoaId, inscritoPorUsuarioId, igrejaId);
        return new ResultadoConvidado(salva, cobranca);
    }
```

- [ ] **Step 4: Rodar os testes e confirmar que passam**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=InscricaoServiceTest
```

Expected: PASS em todos. **Este passo vai quebrar a compilação até a Task 4 estar
pronta** (os dois controllers que chamam `inscreverConvidado` ainda esperam a
assinatura antiga) — normal, é o mesmo arquivo mudando junto com quem o usa; a
verificação de verdade só fecha depois da Task 4.

- [ ] **Step 5: Commit** (fazer junto com o commit da Task 4 — ver nota no fim dela)

---

### Task 4: Controllers e `ConvidadoResponse` acompanham a assinatura nova

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java`
- Modify: `src/main/java/com/domus/api/modules/evento/convite/ConviteController.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ConvidadoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/DTOs/CriarConvidadoRequest.java`
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoControllerTest.java` (se cobrir `criarConvidado`; checar antes)

**Interfaces:**
- Consumes: `InscricaoService.ResultadoConvidado` (Task 3).
- Produces: `ConvidadoResponse(UUID inscricaoId, String nome, String telefone, UUID
  cobrancaId, String tokenLinkPublico)`; `CriarConvidadoRequest` ganha `boolean
  gerarLink` (default `false` — Jackson resolve isso sozinho pra primitivo ausente no
  JSON).

- [ ] **Step 1: Atualizar `ConvidadoResponse`**

```java
package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import java.util.UUID;

public record ConvidadoResponse(
        UUID inscricaoId,
        String nome,
        String telefone,
        UUID cobrancaId,
        String tokenLinkPublico
) {
    public static ConvidadoResponse from(InscricaoEvento i, CobrancaEvento cobrancaOuNull) {
        return new ConvidadoResponse(
                i.getId(), i.getNomeConvidado(), i.getTelefoneConvidado(),
                cobrancaOuNull != null ? cobrancaOuNull.getId() : null,
                cobrancaOuNull != null ? cobrancaOuNull.getTokenLinkPublico() : null
        );
    }
}
```

- [ ] **Step 2: Atualizar `CriarConvidadoRequest`**

Adicionar o campo (sem anotação de validação — `boolean` primitivo nunca é nulo):

```java
public record CriarConvidadoRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String nome,
        @NotBlank(message = "O telefone é obrigatório.")
        @Pattern(regexp = "^\\d{10,11}$", message = "O telefone deve conter 10 ou 11 dígitos numéricos.")
        String telefone,
        UUID visitanteId,
        @Valid
        List<RespostaRequest> respostas,
        /** Task 4b — evento pago: false = quem está preenchendo paga agora; true = gera
         *  link pra pessoa pagar sozinha depois (mesmo padrão de
         *  InscreverPessoasRequest.pessoasParaLink, mas por ser um convidado só de cada
         *  vez aqui, é um boolean, não uma lista). */
        boolean gerarLink
) {}
```

- [ ] **Step 3: Atualizar `InscricaoController.criarConvidado`**

```java
    @PostMapping("/eventos/{eventoId}/inscricoes/convidados")
    public ResponseEntity<ConvidadoResponse> criarConvidado(
            @PathVariable UUID eventoId,
            @Valid @RequestBody CriarConvidadoRequest data) {
        var usuario = usuarioAutenticado.get();
        var resultado = inscricaoService.inscreverConvidado(
                eventoId, usuario.getIgreja().getId(), data.nome(), data.telefone(),
                usuario.getPessoa().getId(), usuario.getId(), data.visitanteId(), data.gerarLink());
        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responder(resultado.inscricao().getId(), null, data.respostas(),
                    usuario.getIgreja().getId(), usuario.getPessoa().getId(), usuario.getRole().getNome());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConvidadoResponse.from(resultado.inscricao(), resultado.cobranca()));
    }
```

- [ ] **Step 4: Atualizar `ConviteController.entrar`**

```java
    @PostMapping("/convites/{token}/entrar")
    public ResponseEntity<ConvidadoResponse> entrar(@PathVariable String token,
                                                      @Valid @RequestBody EntrarConviteRequest data) {
        ConviteResolvido resolvido = conviteService.resolver(token);
        var evento = resolvido.evento();

        // gerarLink sempre false: é a própria pessoa se auto-inscrevendo, sempre "paga
        // agora" (mesma regra do titular em inscreverInterno).
        var resultado = inscricaoService.inscreverConvidado(
                evento.getId(), evento.getIgreja().getId(), data.nome(), data.telefone(),
                resolvido.convidante().getId(), null, null, false);

        if (data.respostas() != null && !data.respostas().isEmpty()) {
            campoPersonalizadoService.responderComoConvidado(
                    resultado.inscricao().getId(), data.respostas(), evento.getIgreja().getId());
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ConvidadoResponse.from(resultado.inscricao(), resultado.cobranca()));
    }
```

- [ ] **Step 5: Rodar a suíte completa**

```bash
set -a && source .env && set +a
mvn -o test
```

Expected: PASS em tudo — isso fecha a compilação que a Task 3 deixou pendente.
Se algum teste de `InscricaoControllerTest`/`ConviteControllerTest` já existente
quebrar por causa da nova resposta (campos a mais em `ConvidadoResponse`), é porque
usa `jsonPath` estrito demais numa asserção de igualdade de objeto inteiro — ajustar
pra checar só os campos relevantes do teste, sem remover cobertura nenhuma.

- [ ] **Step 6: Commit (junto com a Task 3)**

```bash
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoController.java \
        src/main/java/com/domus/api/modules/evento/convite/ConviteController.java \
        src/main/java/com/domus/api/modules/evento/inscricao/DTOs/ConvidadoResponse.java \
        src/main/java/com/domus/api/modules/evento/inscricao/DTOs/CriarConvidadoRequest.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(inscricao): inscreverConvidado cria cobranca em evento pago"
```

---

### Task 5: `CobrancaController` resolve nome do pagador via `InscricaoEvento`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java`

**Interfaces:**
- Consumes: `InscricaoRepository.findById(UUID): Optional<InscricaoEvento>` (já existe,
  `JpaRepository`).
- Produces: nenhuma interface nova — `buscarPorId` continua devolvendo
  `CobrancaCheckoutDTO`, agora também pra cobrança de convidado sem cadastro.

- [ ] **Step 1: Escrever o teste que falha**

Adicionar em `CobrancaControllerTest.java`, seguindo o mesmo padrão de
`retornaContextoDaCobrancaParaIdValido` já existente no arquivo, mas com a
`inscricao_evento` do fixture criada com `pessoa_id = NULL` e
`nome_convidado`/`telefone_convidado` preenchidos, e a `CobrancaEvento` com
`pessoaId`/`acompanhanteId` os dois nulos:

```java
    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('21111111-1111-1111-1111-111111111112', 'Igreja Teste Convidado', 'igrejaconv@teste.com')",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('27777777-7777-7777-7777-777777777778', '21111111-1111-1111-1111-111111111112', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('25555555-5555-5555-5555-555555555556', '21111111-1111-1111-1111-111111111112', " +
            "'Evento Com Convidado', '2026-09-11 19:00:00', '27777777-7777-7777-7777-777777777778', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, nome_convidado, telefone_convidado, status) VALUES " +
            "('26666666-6666-6666-6666-666666666667', '21111111-1111-1111-1111-111111111112', " +
            "'25555555-5555-5555-5555-555555555556', 'Convidado Sem Cadastro', '11988887777', 'AGUARDANDO_PAGAMENTO')"
    })
    void retornaContextoDaCobrancaDeConvidadoSemCadastro() throws Exception {
        UUID igrejaId = UUID.fromString("21111111-1111-1111-1111-111111111112");
        UUID eventoId = UUID.fromString("25555555-5555-5555-5555-555555555556");
        UUID inscricaoId = UUID.fromString("26666666-6666-6666-6666-666666666667");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(
            igrejaId, eventoId, inscricaoId, null, null,
            new BigDecimal("40.00"), Instant.now().plus(1, ChronoUnit.HOURS), null, null));

        mockMvc.perform(get("/cobrancas/id/" + cobranca.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nomePagador", is("Convidado Sem Cadastro")))
            .andExpect(jsonPath("$.emailPagador").value(org.hamcrest.Matchers.nullValue()));
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=CobrancaControllerTest#retornaContextoDaCobrancaDeConvidadoSemCadastro
```

Expected: FAIL — `buscarPorId` hoje lança `ResourceNotFoundException` na busca por
`pessoaRepository.findById(cobranca.getPessoaId())` com `pessoaId` nulo (ou erro
equivalente), porque não trata o caso "os dois nulos".

- [ ] **Step 3: Implementar**

Adicionar `InscricaoRepository` ao construtor de `CobrancaController` e usar no
`buscarPorId`:

```java
    private final CobrancaEventoService service;
    private final CobrancaEventoRepository cobrancaRepository;
    private final EventoRepository eventoRepository;
    private final com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository;
    private final PessoaRepository pessoaRepository;
    private final AcompanhanteRepository acompanhanteRepository;
    private final MercadoPagoClient mercadoPagoClient;

    public CobrancaController(CobrancaEventoService service,
                               CobrancaEventoRepository cobrancaRepository,
                               EventoRepository eventoRepository,
                               com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository,
                               PessoaRepository pessoaRepository,
                               AcompanhanteRepository acompanhanteRepository,
                               MercadoPagoClient mercadoPagoClient) {
        this.service = service;
        this.cobrancaRepository = cobrancaRepository;
        this.eventoRepository = eventoRepository;
        this.inscricaoRepository = inscricaoRepository;
        this.pessoaRepository = pessoaRepository;
        this.acompanhanteRepository = acompanhanteRepository;
        this.mercadoPagoClient = mercadoPagoClient;
    }
```

Em `buscarPorId`, trocar o bloco `String nomePagador; ...` por:

```java
        String nomePagador;
        String emailPagador = null;
        if (cobranca.getPessoaId() != null) {
            var pessoa = pessoaRepository.findById(cobranca.getPessoaId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."));
            nomePagador = pessoa.getNome();
            emailPagador = pessoa.getEmail();
        } else if (cobranca.getAcompanhanteId() != null) {
            nomePagador = acompanhanteRepository.findById(cobranca.getAcompanhanteId())
                .orElseThrow(() -> new ResourceNotFoundException("Pagador da cobrança não encontrado."))
                .getNome();
        } else {
            // Convidado sem cadastro (Plano 4b) — nem pessoa nem acompanhante, resolvido
            // só pela InscricaoEvento (nomeConvidado). Sem e-mail: não existe onde buscar
            // um pra convidado sem cadastro.
            nomePagador = inscricaoRepository.findById(cobranca.getInscricaoId())
                .orElseThrow(() -> new ResourceNotFoundException("Inscrição da cobrança não encontrada."))
                .getNomeConvidado();
        }
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=CobrancaControllerTest
```

Expected: PASS em todos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java
git commit -m "feat(pagamento): CobrancaController resolve nome de convidado sem cadastro"
```

---

### Task 6: Webhook não quebra quando `criadoPorUsuarioId` é nulo

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java`

**Interfaces:** nenhuma mudança de assinatura — só uma checagem a mais antes de notificar.

- [ ] **Step 1: Escrever o teste que falha**

```java
    @Test
    void naoTentaNotificarQuandoCriadoPorUsuarioIdEhNulo() {
        // Convidado sem cadastro se auto-inscrevendo via /convite/{token} (Plano 4b) —
        // criadoPorUsuarioId nulo, ninguém pra notificar.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, null, BigDecimal.TEN, Instant.now().plusSeconds(600), null, null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verifyNoInteractions(notificacaoService);
    }
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=MercadoPagoWebhookServiceTest#naoTentaNotificarQuandoCriadoPorUsuarioIdEhNulo
```

Expected: FAIL — hoje `ehDoTitular()` é falso (pessoaId nulo) e o código tenta chamar
`notificacaoService.criar(..., null, ...)` incondicionalmente.

- [ ] **Step 3: Implementar**

Em `MercadoPagoWebhookService.java`, trocar:

```java
            if (!cobranca.ehDoTitular()) {
                notificacaoService.criar(
                    TipoNotificacao.COBRANCA_EVENTO_PAGA,
                    cobranca.getIgrejaId(),
                    cobranca.getCriadoPorUsuarioId(),
                    "O pagamento foi confirmado.",
                    "/eventos/" + cobranca.getEventoId() + "/inscritos");
            }
```

por:

```java
            // Convidado sem cadastro se auto-inscrevendo (Plano 4b) tem
            // criadoPorUsuarioId nulo — ninguém pra notificar nesse caso.
            if (!cobranca.ehDoTitular() && cobranca.getCriadoPorUsuarioId() != null) {
                notificacaoService.criar(
                    TipoNotificacao.COBRANCA_EVENTO_PAGA,
                    cobranca.getIgrejaId(),
                    cobranca.getCriadoPorUsuarioId(),
                    "O pagamento foi confirmado.",
                    "/eventos/" + cobranca.getEventoId() + "/inscritos");
            }
```

- [ ] **Step 4: Rodar o teste e confirmar que passa**

```bash
set -a && source .env && set +a
mvn -o test -Dtest=MercadoPagoWebhookServiceTest
```

Expected: PASS em todos.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java \
        src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java
git commit -m "fix(pagamento): webhook nao tenta notificar quando criadoPorUsuarioId e nulo"
```

---

### Task 7: Suíte completa

**Files:** nenhum arquivo novo — só verificação.

- [ ] **Step 1: Rodar a suíte completa**

```bash
set -a && source .env && set +a
mvn -o test
```

Expected: PASS em tudo, sem regressão nos planos anteriores (Plano 1-5).
