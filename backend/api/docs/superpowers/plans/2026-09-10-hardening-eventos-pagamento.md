# Hardening do módulo eventos + pagamento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fechar as brechas Críticas + Importantes selecionadas da auditoria (`backend/api/docs/AUDITORIA-MODULO-EVENTOS-PAGAMENTO.md`) antes de seguir pro próximo item do escopo comercial — cada correção nasce com o teste do cenário de falha.

**Architecture:** Correções pontuais em fluxos que já existem. Nenhuma feature nova, nenhum schema novo. As decisões de produto já foram tomadas no brainstorm (ver cada task). Ordem: primeiro as de dinheiro/concorrência no backend (C1–C3), depois a coordenação back+front (C4/I1), depois consistência de valor e de contagem (I2, I3), depois os ajustes baratos (I6, I7, I9, I11, I13).

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA (lock pessimista via `@Lock`), Flyway, PostgreSQL (Testcontainers nos testes), JUnit 5 + AssertJ + Mockito; Next.js/TypeScript/CSS Modules no front.

**Spec:** `backend/api/docs/AUDITORIA-MODULO-EVENTOS-PAGAMENTO.md` (o levantamento) + as decisões registradas nas tasks abaixo.

## Global Constraints

- **`igreja_id` sempre do JWT / da cobrança**, nunca do corpo. Rotas `/cobrancas/**` e `/convites/**` são públicas por posse de UUID — não introduzir `igreja_id` de request nelas.
- **Camadas** `controller → service → repository`; services retornam DTO.
- **Mockito puro é a regra** (90% do projeto); `@SpringBootTest + @AutoConfigureMockMvc + AutenticacaoTestSupport` para controller; `@DataJpaTest` só para query JPQL nova. Testcontainers sobe o Postgres — precisa de Docker. **`@SpringBootTest` precisa do `.env` carregado:** `set -a && . ./.env && set +a && mvn -q test -Dtest=...` (senão ~121 erros de `JWT_SECRET`).
- **Nunca enfraquecer teste pra passar.** Nunca reportar "passou" sem rodar `mvn` e ver o resultado. **Não commitar antes do autor testar** o pedaço — um commit coerente por task.
- **Não mockar tipos de domínio** (`Evento`, `InscricaoEvento`, `CobrancaEvento`, `Pessoa`). Builders / `new`.
- **Programar por capacidade** (`Permissoes.podeGerenciarInscricoes(role)` / `podeGerenciarEventos`), nunca `role.equals("ADMIN_IGREJA")`.
- **Front:** CSS Modules, tokens reais de `src/styles/tokens.css` **sem fallback de hex**, `@media (prefers-reduced-motion: reduce)` onde houver transform, responsividade obrigatória. Sem infra de teste de front — validação manual.
- **Estado atual do schema: V40.** Este plano **não** adiciona migration.
- **BigDecimal** para dinheiro; `RoundingMode.CEILING` para valor que a igreja não pode perder (o resto HALF_UP).
- Atribuição de commit: terminar com
  `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` / `Claude-Session: https://claude.ai/code/session_016T8PzW1CTUoA4uASNAB28e`.

---

## File Structure

**Backend — modificar:**
- `modules/pagamento/job/CobrancaEventoExpiracaoJob.java` — C1: não cancelar quem já pagou algo
- `modules/evento/inscricao/InscricaoService.java` — C1 (`aplicarMudancaValorPago` não flipa pra AGUARDANDO_PAGAMENTO no aumento), I7, I9, I13, I3 (rota das contagens)
- `modules/evento/inscricao/InscricaoRepository.java` / `modules/pagamento/cobranca/CobrancaEventoRepository.java` — I3: query de reserva reusável; C1: query "tem cobrança PAGO"
- `modules/pagamento/webhook/MercadoPagoWebhookService.java` — C2 (`@Transactional` + lock), I2 (valor no comprovante)
- `modules/pagamento/cobranca/CobrancaController.java` — C3 (`reiniciar` reconfere), I1 (`pagar` valida situação do evento, não o prazo)
- `modules/pagamento/MercadoPagoApi.java` — I6: falha vira `BusinessException`
- `modules/evento/EventoService.java` — I11 (`validarPrazoInscricao` recusa passado no cadastro), I3 (`atualizarEvento` usa contagem de reserva)
- `modules/evento/convite/ConviteController.java` — I3: `vagasRestantes` do convite público usa reserva
- `modules/evento/inscricao/InscricaoController.java` + `EventoController.java` — C1: endpoint de "recriar complemento e lembrar"
- `modules/evento/inscricao/DTOs/InscritoResponse.java` — C1: `pagamentoParcial` resolve mesmo com complemento EXPIRADO
- `shared/exception/` — reusar `BusinessException` (não criar exception nova sem necessidade)

**Frontend — modificar:**
- `src/components/module/eventos/BotaoConfirmarPresenca.tsx` — C4: o `return` do prazo não barra quem tem `cobrancaPendenteId`; I3: `vagasRestantes` vem certo do back (nenhuma mudança de lógica, só garantir que o valor do back é o de reserva)
- `src/components/module/eventos/PagamentoPendenteBadge.tsx` — C1: botão "lembrar" quando o complemento expirou
- `src/services/` + `src/hooks/` — C1: chamar o endpoint novo de "recriar complemento"

**Testes novos:**
- `src/test/java/.../pagamento/job/CobrancaEventoExpiracaoJobTest.java` (não existe)
- casos novos em `InscricaoServiceTest`, `CobrancaControllerTest`, `MercadoPagoWebhookServiceTest`, `EventoServiceTest`, `EventoRequestTest`, `MercadoPagoApiFeeDetailsTest`/novo

---

## Task 1: C1 — complemento não pago não cancela quem já pagou o original

**Decisão (brainstorm):** no aumento de preço, a inscrição **fica CONFIRMADA** (hoje vira `AGUARDANDO_PAGAMENTO` — decisão de 2026-08-27 revista). A pendência do complemento é sinalizada pela tag "Falta complementar", derivada do estado das cobranças (tem PAGO **e** tem complemento PENDENTE/EXPIRADO). O `CobrancaEventoExpiracaoJob` não cancela quem já pagou algo. O gestor decide: manter / lembrar / contato / remover (remover = `cancelar`, que já estorna).

**Files:**
- Modify: `modules/pagamento/job/CobrancaEventoExpiracaoJob.java`
- Modify: `modules/evento/inscricao/InscricaoService.java` (`aplicarMudancaValorPago`, ~linha 1636–1665)
- Modify: `modules/evento/inscricao/InscricaoRepository.java` (nova query) ou `CobrancaEventoRepository.java`
- Modify: `modules/evento/inscricao/DTOs/InscritoResponse.java` + o ponto que resolve `pagamentoParcial` em lote (grep `pagamentoParcial` em `InscricaoService`)
- Test: `src/test/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJobTest.java` (criar)
- Test: `InscricaoServiceTest` (casos novos)

**Interfaces:**
- Produces: `CobrancaEventoRepository.existePagaParaInscricao(UUID inscricaoId) -> boolean` (JPQL: `select count(c) > 0 from CobrancaEvento c where c.inscricaoId = :id and c.status = 'PAGO'`).

- [ ] **Step 1: Teste do job — cenário C1**

`CobrancaEventoExpiracaoJobTest.java` — `@DataJpaTest @AutoConfigureTestDatabase(replace = NONE)` implementando `PostgresTestContainerSupport`, ou `@SpringBootTest` se precisar do contexto. Monta fixtures inline (Igreja, Evento pago, Pessoa, InscricaoEvento, CobrancaEvento):

```java
@Test
void complementoVencidoNaoCancelaInscricaoDeQuemJaPagouOOriginal() {
    // inscrição CONFIRMADA + cobrança A PAGO (R$100) + cobrança B PENDENTE vencida (R$30, complemento)
    // ... salvar tudo, expiraEm de B no passado
    job.executar();

    var b = cobrancaRepository.findById(bId).orElseThrow();
    assertThat(b.getStatus()).isEqualTo(StatusCobranca.EXPIRADO);      // complemento expira
    var inscricao = inscricaoRepository.findById(inscricaoId).orElseThrow();
    assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA); // NÃO cancela
}

@Test
void cobrancaUnicaVencidaSemPagamentoNenhumCancelaComoAntes() {
    // inscrição AGUARDANDO_PAGAMENTO + 1 cobrança PENDENTE vencida, nenhuma PAGO
    job.executar();
    var inscricao = inscricaoRepository.findById(inscricaoId).orElseThrow();
    assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA); // comportamento antigo
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=CobrancaEventoExpiracaoJobTest`
Expected: FAIL — o job hoje cancela pelo filtro `estaAguardandoPagamento()` sem olhar se já pagou.

- [ ] **Step 3: Query "tem cobrança paga"**

Em `CobrancaEventoRepository.java`:

```java
@Query("select (count(c) > 0) from CobrancaEvento c where c.inscricaoId = :inscricaoId and c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO")
boolean existePagaParaInscricao(@Param("inscricaoId") UUID inscricaoId);
```

- [ ] **Step 4: Job só cancela quem nunca pagou**

Em `CobrancaEventoExpiracaoJob.executar()`, ao montar a lista de inscrições a cancelar, adicionar o filtro:

```java
var inscricoes = inscricaoRepository.findAllById(inscricaoIds).stream()
        .filter(InscricaoEvento::estaAguardandoPagamento)
        .filter(i -> !cobrancaRepository.existePagaParaInscricao(i.getId())) // C1: quem já pagou algo NÃO é cancelado
        .toList();
```

(Injetar `CobrancaEventoRepository` no job — ele já tem o `repository` de cobrança; usar esse.)

Comentário curto explicando: complemento de reajuste vencido não tira a vaga de quem já pagou o original; o gestor resolve pela lista de inscritos.

- [ ] **Step 5: `aplicarMudancaValorPago` — aumento mantém CONFIRMADA**

Em `InscricaoService.aplicarMudancaValorPago`, no branch de criação do complemento (~linha 1654), **remover** o `inscricao.setStatus(StatusInscricao.AGUARDANDO_PAGAMENTO)` (e o `inscricaoRepository.save(inscricao)` que só existia pra isso). A inscrição continua CONFIRMADA; só a cobrança de complemento é criada. Ajustar o comentário de 2026-08-27 (linha ~1648) para a decisão nova. **Não** mexer no branch de `cobrancaPendenteOpt.isPresent()` (atualiza cobrança existente) além de remover o mesmo `setStatus` se houver.

- [ ] **Step 6: `pagamentoParcial` sobrevive ao complemento EXPIRADO**

Achar onde `pagamentoParcial` é resolvido em lote (grep `pagamentoParcial` em `InscricaoService.java` — provável método que monta `listarInscritos`). Hoje a condição provavelmente é `AGUARDANDO_PAGAMENTO && temComplementoPendente`. Trocar para: **tem cobrança PAGO E tem cobrança de complemento PENDENTE-ou-EXPIRADO com valor > 0** (independente do status da inscrição, que agora é CONFIRMADA). O DTO `InscritoResponse` linha ~98 (`i.getStatus() == AGUARDANDO_PAGAMENTO && pagamentoParcial`) vira só `pagamentoParcial` (o `pagamentoParcial` já carrega a semântica completa).

- [ ] **Step 7: Testes de `InscricaoServiceTest` (aumento de preço)**

```java
@Test
void aumentoDePreco_mantemInscricaoConfirmada_criaComplemento() {
    // cobrança PAGA R$100, inscrição CONFIRMADA. Reajuste pra R$130.
    aplicarMudancaValorPago(eventoId, new BigDecimal("100"), new BigDecimal("130"), usuarioId);
    verify(cobrancaEventoService).criarParaTerceiro(any(), any(), any(), any(),
        argThat(v -> v.compareTo(new BigDecimal("30.00")) == 0), any(), eq(true));
    // status NÃO muda:
    assertThat(inscricaoCapturada().getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
}

@Test
void listaInscritos_mostraFaltaComplementar_mesmoComComplementoExpirado() {
    // PAGO R$100 + complemento EXPIRADO R$30 → pagamentoParcial == true
}
```

- [ ] **Step 8: Rodar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=CobrancaEventoExpiracaoJobTest,InscricaoServiceTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJob.java \
        src/main/java/com/domus/api/modules/evento/inscricao/ \
        src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoRepository.java \
        src/test/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJobTest.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "fix(evento): complemento de reajuste vencido nao cancela quem ja pagou o original"
```

---

## Task 2: C1 (parte 2) — botão "lembrar" recria o complemento se expirou

**Files:**
- Modify: `modules/evento/inscricao/InscricaoService.java` — `enviarLembretePagamento` (ou um método novo `lembrarComplemento`) recria a cobrança quando ela está EXPIRADO
- Modify: `modules/evento/inscricao/InscricaoController.java` — endpoint (ou reusar o de lembrete existente)
- Modify front: `PagamentoPendenteBadge.tsx` + service/hook
- Test: `InscricaoServiceTest`

**Interfaces:**
- Consumes: `cobrancaEventoService.criarParaTerceiro(igrejaId, eventoId, inscricaoId, pessoaId, valor, usuarioId, true)` (existe).
- Produces: comportamento — chamar o lembrete quando o complemento está EXPIRADO cria uma nova `CobrancaEvento` PENDENTE (mesmo `novoValorDevido = preco - valorJaPago`) e manda o e-mail apontando pra ela.

- [ ] **Step 1: Teste**

```java
@Test
void lembrarComplementoExpirado_criaCobrancaNovaEEnviaEmail() {
    // inscrição CONFIRMADA, PAGO R$100, complemento EXPIRADO R$30, evento agora R$130
    inscricaoService.enviarLembretePagamento(inscricaoId, igrejaId, role);
    // criou uma cobrança nova de R$30 PENDENTE
    verify(cobrancaEventoService).criarParaTerceiro(any(), any(), eq(inscricaoId), any(),
        argThat(v -> v.compareTo(new BigDecimal("30.00")) == 0), any(), eq(true));
    verify(emailService).enviar(any(), contains("Lembrete"), contains("complementar"));
}

@Test
void lembrarComplementoAindaPendente_naoDuplicaCobranca() {
    // complemento PENDENTE não vencido → só reenvia o e-mail, não cria outra
    inscricaoService.enviarLembretePagamento(inscricaoId, igrejaId, role);
    verify(cobrancaEventoService, never()).criarParaTerceiro(any(), any(), any(), any(), any(), any(), anyBoolean());
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

Em `enviarLembretePagamento`, ao resolver a `cobranca` do lembrete: se a única cobrança pendente/relevante da inscrição está `EXPIRADO` e ainda há saldo devido (`preco - valorJaPago > 0`), criar uma nova via `criarParaTerceiro` e usar essa no e-mail. Se já há uma PENDENTE não vencida, usar essa (sem criar). Manter o `try/catch` (ver Task 8/I7 — na verdade fazer o I7 já aqui: envolver o `emailService.enviar` em try/catch + log, como os outros 4 envios).

- [ ] **Step 4: Front — botão "lembrar" no badge**

`PagamentoPendenteBadge.tsx`: quando `pagamentoParcial` (tag "Falta complementar"), adicionar um botão/link "Lembrar" ao lado, que chama o endpoint de lembrete (`POST /eventos/{eventoId}/inscricoes/{inscricaoId}/lembrar-pagamento` ou o que já existe — grep no service). Feedback de sucesso via `notificar`. Estilo consistente com o resto do badge; alvo ≥44px no mobile.

- [ ] **Step 5: Rodar back + typecheck front**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=InscricaoServiceTest` → PASS.
Run: `cd ../../frontend && npx tsc --noEmit` → limpo.

- [ ] **Step 6: Commit** (não commitar o front sozinho — junto neste commit, mas avisar o autor pra testar o botão no navegador antes)

```bash
git add src/main/java/com/domus/api/modules/evento/ src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "fix(evento): botao lembrar recria a cobranca de complemento quando ela expirou"
# front vai junto depois do teste manual do autor
```

---

## Task 3: C2 — confirmação de pagamento transacional e serializada

**Files:**
- Modify: `modules/pagamento/webhook/MercadoPagoWebhookService.java` — `confirmarPagamento`
- Modify: `modules/pagamento/cobranca/CobrancaEventoRepository.java` — já tem `buscarComLock`
- Test: `MercadoPagoWebhookServiceTest`

**Interfaces:**
- Consumes: `CobrancaEventoRepository.buscarComLock(UUID)` (existe, `@Lock(PESSIMISTIC_WRITE)`).

- [ ] **Step 1: Teste de dupla confirmação**

Em `MercadoPagoWebhookServiceTest` (Mockito puro):

```java
@Test
void segundaConfirmacaoDoMesmoPagamento_naoDuplicaFinanceiroNemEmail() {
    // cobrança PENDENTE. Primeira chamada confirma.
    service.confirmarPagamento(cobrancaId, "mp-1", info("approved"));
    // Segunda chamada (poll chegou logo depois do webhook) — cobrança agora PAGO
    when(cobrancaRepository.buscarComLock(uuid)).thenReturn(Optional.of(cobrancaJaPaga));
    service.confirmarPagamento(cobrancaId, "mp-1", info("approved"));

    verify(movimentacaoAutomaticaService, times(1)).registrarEntradaDeEvento(any(), any(), any(), any(), any(), any());
    verify(emailService, times(1)).enviar(any(), any(), any());
}
```

(O teste de concorrência real — duas threads — não roda bem em Mockito; o que provamos aqui é que a 2ª chamada, vendo PAGO, sai cedo. O lock pessimista é o que serializa em produção; documentar isso no teste.)

- [ ] **Step 2: Rodar e ver falhar (ou não)**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=MercadoPagoWebhookServiceTest`
Expected: o 2º caso já pode passar (a guarda de status existe) — mas o teste vai exigir trocar `findById` por `buscarComLock` no mock. Se passar de primeira, o valor está em tornar explícito o lock; seguir pro Step 3 mesmo assim.

- [ ] **Step 3: `@Transactional` + lock**

```java
@org.springframework.transaction.annotation.Transactional
public void confirmarPagamento(String cobrancaId, String mpPaymentId, InformacoesPagamento info) {
    String status = info.status();
    var cobranca = cobrancaRepository.buscarComLock(UUID.fromString(cobrancaId)).orElse(null); // era findById
    // ... resto igual
```

Agora o método inteiro é uma transação: cobrança + inscrição + e-mail + financeiro num átomo. O `buscarComLock` serializa webhook vs poll — a 2ª thread bloqueia até a 1ª commitar, aí lê `status == PAGO` e sai na guarda da linha ~119.

> **Nota sobre o e-mail:** `emailService.enviar` dentro de `@Transactional` — se o Resend falhar, hoje já é `try/catch` em `enviarEmailConfirmacao`? Verificar; se não for, envolver (mesma linha do I7). O e-mail nunca pode dar rollback no financeiro.

- [ ] **Step 4: Ajustar os testes existentes**

Os testes que mockavam `cobrancaRepository.findById(...)` pra este fluxo passam a mockar `buscarComLock(...)`. Plumbing, sem enfraquecer asserção.

- [ ] **Step 5: Rodar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=MercadoPagoWebhookServiceTest,PagamentoPollingServiceTest,MercadoPagoWebhookControllerTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/webhook/ src/test/java/com/domus/api/modules/pagamento/
git commit -m "fix(pagamento): confirmarPagamento transacional + lock pessimista (webhook x poll)"
```

---

## Task 4: C3 — `reiniciar` reconfere o status antes de liberar

**Files:**
- Modify: `modules/pagamento/cobranca/CobrancaController.java` — `reiniciar` (~322–334)
- Test: `CobrancaControllerTest`

**Interfaces:**
- Consumes: `mercadoPagoClient.buscarInformacoesPagamento(igrejaId, mpPaymentId) -> InformacoesPagamento`, `webhookService.confirmarPagamento(...)`, `cobranca.liberarParaNovaTentativa()`.

- [ ] **Step 1: Testes**

```java
@Test
void reiniciar_quandoPagamentoJaAprovado_confirmaEmVezDeLiberar() {
    // cobrança PENDENTE com mpPaymentId; MP diz "approved"
    when(mercadoPagoClient.buscarInformacoesPagamento(any(), eq("mp-1"))).thenReturn(info("approved"));
    mockMvc.perform(post("/cobrancas/{id}/reiniciar", cobrancaId)).andExpect(status().isOk());
    verify(webhookService).confirmarPagamento(eq(cobrancaId.toString()), eq("mp-1"), any());
    var c = cobrancaRepository.findById(cobrancaId).orElseThrow();
    assertThat(c.getMpPaymentId()).isEqualTo("mp-1"); // NÃO foi limpo
}

@Test
void reiniciar_quandoPagamentoAindaPendente_liberaNormalmente() {
    when(mercadoPagoClient.buscarInformacoesPagamento(any(), any())).thenReturn(info("pending"));
    // cancelarPagamento no MP tem sucesso
    mockMvc.perform(post("/cobrancas/{id}/reiniciar", cobrancaId)).andExpect(status().isOk());
    var c = cobrancaRepository.findById(cobrancaId).orElseThrow();
    assertThat(c.getMpPaymentId()).isNull(); // liberado
}

@Test
void reiniciar_quandoCancelamentoNoMpFalha_naoLibera_e_devolveErro() {
    when(mercadoPagoClient.buscarInformacoesPagamento(any(), any())).thenReturn(info("pending"));
    doThrow(new BusinessException("MP_INDISPONIVEL", "x"))
        .when(mercadoPagoClient).cancelarPagamento(any(), any());
    mockMvc.perform(post("/cobrancas/{id}/reiniciar", cobrancaId)).andExpect(status().isBadRequest());
    var c = cobrancaRepository.findById(cobrancaId).orElseThrow();
    assertThat(c.getMpPaymentId()).isNotNull(); // preservado
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=CobrancaControllerTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

```java
@PostMapping("/{id}/reiniciar")
@Transactional
public void reiniciar(@PathVariable UUID id) {
    var cobranca = cobrancaRepository.buscarComLock(id)
        .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
    if (cobranca.getStatus() != StatusCobranca.PENDENTE || cobranca.getMpPaymentId() == null) {
        return;
    }
    // Reconfere no MP: se o pagamento já aprovou na janela entre o poll e o clique,
    // confirmar em vez de descartar (senão vira pagamento órfão / risco de pagar 2x).
    var info = mercadoPagoClient.buscarInformacoesPagamento(cobranca.getIgrejaId(), cobranca.getMpPaymentId());
    if ("approved".equals(info.status())) {
        webhookService.confirmarPagamento(id.toString(), cobranca.getMpPaymentId(), info);
        return;
    }
    // Só libera se o cancelamento no MP der certo — se `cancelarPagamento` lançar, propaga (4xx via I6).
    mercadoPagoClient.cancelarPagamento(cobranca.getIgrejaId(), cobranca.getMpPaymentId());
    cobranca.liberarParaNovaTentativa();
    cobrancaRepository.save(cobranca);
}
```

(Injetar `MercadoPagoWebhookService` no `CobrancaController` se ainda não estiver — provavelmente não está; adicionar ao construtor.)

- [ ] **Step 4: Rodar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=CobrancaControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java
git commit -m "fix(pagamento): reiniciar reconfere status no MP antes de descartar o mpPaymentId"
```

---

## Task 5: C4 + I1 — pós-prazo deixa terminar de pagar

**Decisão:** quem tem inscrição pendente **já existente** pode terminar de pagar mesmo com `inscricoes_ate` no passado. Inscrição **nova** pós-prazo continua barrada. O `/pagar` passa a recusar evento `ENCERRADO` (situação), mas **não** o prazo de inscrição.

**Files:**
- Modify front: `src/components/module/eventos/BotaoConfirmarPresenca.tsx` (~linha 234)
- Modify: `modules/pagamento/cobranca/CobrancaController.java` — `pagar` valida `SituacaoEvento`
- Test: `CobrancaControllerTest`

- [ ] **Step 1: Front — o `return` do prazo não barra quem tem cobrança pendente**

`BotaoConfirmarPresenca.tsx` linha ~234:

```tsx
if (encerradoPorPrazo && !podeFurarPrazo && !minha?.inscrito && !minha?.cobrancaPendenteId) {
  return (
    <button type="button" className={styles.botao} disabled>
      Inscrições encerradas em {dataPrazo}
    </button>
  )
}
```

Com `cobrancaPendenteId` preenchido, cai no `cenaAcao === 'pendente'` (linha ~316) e renderiza o bloco "Pagamento pendente — continuar" + cancelar. Nenhuma outra mudança no front.

- [ ] **Step 2: Confirmar que `MinhaInscricaoResponse.cobrancaPendenteId` vem preenchido pós-prazo**

Grep quem monta `MinhaInscricaoResponse.from(i, cobrancaPendenteId)` — confirmar que o `cobrancaPendenteId` é "a `CobrancaEvento` PENDENTE não vencida da inscrição" sem nenhum filtro por prazo do evento. Se houver filtro por prazo, removê-lo. (Provavelmente não há — a cobrança tem prazo próprio.)

- [ ] **Step 3: Teste do `/pagar` — evento encerrado**

```java
@Test
void pagar_eventoJaEncerrado_recusa() {
    // evento com inicioEm/fimEm no passado, cobrança PENDENTE não vencida
    mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId)
            .contentType(APPLICATION_JSON).content(corpoPix()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("EVENTO_ENCERRADO")));
}

@Test
void pagar_eventoAgendadoComPrazoDeInscricaoVencido_permite() {
    // evento AGENDADO (início no futuro), inscricoesAte no passado, inscrição pendente existente
    mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId)
            .contentType(APPLICATION_JSON).content(corpoPix()))
        .andExpect(status().isOk());
}
```

- [ ] **Step 4: Implementar no `CobrancaController.pagar`**

Depois de carregar `evento` (linha ~180), antes de chamar o MP:

```java
if (evento.getSituacao() == SituacaoEvento.ENCERRADO) {
    throw new BusinessException("EVENTO_ENCERRADO",
        "Este evento já terminou. Fale com a igreja para resolver o pagamento.");
}
```

**Não** checar `inscricoesAte` aqui — a decisão é deixar a inscrição existente concluir.

- [ ] **Step 5: Rodar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=CobrancaControllerTest`
Run: `cd ../../frontend && npx tsc --noEmit && npm run build`
Expected: PASS / limpo.

- [ ] **Step 6: Commit** (front + back juntos; avisar o autor pra testar o cenário no navegador: inscrever, deixar o prazo passar, conseguir pagar)

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java
git commit -m "fix(evento): pos-prazo deixa terminar de pagar inscricao existente; /pagar recusa evento encerrado"
# o BotaoConfirmarPresenca.tsx vai no mesmo commit depois do teste manual
```

---

## Task 6: I2 — comprovante com o valor efetivamente cobrado

**Files:**
- Modify: `modules/pagamento/webhook/MercadoPagoWebhookService.java` (~linha 251, corpo do e-mail de confirmação)
- Test: `MercadoPagoWebhookServiceTest`

- [ ] **Step 1: Teste**

```java
@Test
void emailDeConfirmacao_usaValorCobradoNaoOAlvo() {
    // cobrança: valor (alvo) = 100.00, valorCobrado = 111.73
    service.confirmarPagamento(cobrancaId, "mp-1", info("approved"));
    ArgumentCaptor<String> corpo = ArgumentCaptor.forClass(String.class);
    verify(emailService).enviar(any(), any(), corpo.capture());
    assertThat(corpo.getValue()).contains("111,73").doesNotContain("100,00");
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=MercadoPagoWebhookServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implementar**

No ponto que formata "Valor pago" no e-mail de confirmação: trocar `cobranca.getValor()` por `cobranca.getValorCobrado() != null ? cobranca.getValorCobrado() : cobranca.getValor()`. Se o valor bruto do `info` estiver disponível e não-nulo (`info.valorBruto()`), preferir ele (é o que o MP de fato cobrou). Só o **comprovante** — os e-mails de "falta pagar X" (lembrete, virou-pago, complemento) ficam no `valor`/`novoValorDevido` (a tela de checkout já mostra a taxa; ver a nota da auditoria I2).

- [ ] **Step 4: Rodar + Commit**

```bash
set -a && . ./.env && set +a && mvn -q test -Dtest=MercadoPagoWebhookServiceTest
git add src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java
git commit -m "fix(pagamento): comprovante de pagamento anuncia o valor cobrado (com taxa), nao o alvo"
```

---

## Task 7: I3 — contagem de vaga única (reserva)

**Decisão:** "reservada" (cobrança PAGO + PENDENTE não-vencida, para evento pago; confirmadas para gratuito) vira a fonte em todos os pontos.

**Files:**
- Modify: `modules/evento/inscricao/InscricaoService.java` — expor `contarOcupadas` como público recebendo `eventoId` (ou uma variante); rotear `listarInscritos` (~linha 1060) por ela
- Modify: `modules/evento/EventoService.java:247` — redução de vagas usa a contagem de reserva
- Modify: `modules/evento/convite/ConviteController.java:66` — `vagasRestantes` do convite usa reserva
- Test: `InscricaoServiceTest`, `EventoServiceTest`, e um caso em `ConviteControllerTest` se existir

**Interfaces:**
- Produces: `InscricaoService.contarOcupadas(UUID eventoId) -> long` (público; carrega o `Evento` e delega pra lógica que já existe em `contarOcupadas(Evento)`; para evento pago = `cobrancaEventoRepository.contarPessoasComVagaReservada(eventoId, Instant.now())`, para gratuito = `contarPessoasConfirmadas`).

- [ ] **Step 1: Testes**

```java
@Test
void listaInscritos_vagasRestantes_contaQuemEstaPagando() {
    // evento pago, 10 vagas, 3 CONFIRMADA + 4 com cobrança PENDENTE não vencida
    var lista = inscricaoService.listarInscritos(eventoId, ...);
    assertThat(lista.vagasRestantes()).isEqualTo(3); // 10 - 7, não 10 - 3
}

@Test
void atualizarEvento_naoDeixaReduzirVagasAbaixoDoReservado() {
    // 6 pessoas reservando (pagas + pagando). Admin tenta baixar vagas pra 4.
    assertThatThrownBy(() -> eventoService.atualizarEvento(eventoId, requestComVagas(4), igrejaId, usuarioId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("vagas");
}
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `set -a && . ./.env && set +a && mvn -q test -Dtest=InscricaoServiceTest,EventoServiceTest`
Expected: FAIL.

- [ ] **Step 3: Expor `contarOcupadas(eventoId)`**

```java
public long contarOcupadas(UUID eventoId) {
    var evento = eventoRepository.findById(eventoId)
        .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    return contarOcupadas(evento); // método privado que já existe (linha 459)
}
```

- [ ] **Step 4: Rotear os call sites**

- `InscricaoService.java:1060` (`listarInscritos` monta `vagasRestantes`): `contarPessoasConfirmadas(eventoId)` → `contarOcupadas(evento)` (o `evento` já está em escopo ali).
- `EventoService.java:247` (`atualizarEvento`): `inscricaoService.contarPessoasConfirmadas(evento.getId())` → `inscricaoService.contarOcupadas(evento.getId())`. A mensagem de erro de "vagas abaixo do ocupado" já existe? Se não, adicionar o guard: `if (novasVagas != null && novasVagas < contarOcupadas) throw new BusinessException("VAGAS_ABAIXO_DO_OCUPADO", "Já há N pessoas com vaga garantida ou em pagamento.")`.
- `ConviteController.java:66`: `inscricaoService.contarPessoasConfirmadas(evento.getId())` → `inscricaoService.contarOcupadas(evento.getId())`.

**Não mexer** em `contarPessoasConfirmadas` em si (ainda usado por `contarOcupadas` no ramo gratuito e talvez por relatório de presença — grep antes de remover). Só trocar quem chama para efeito de "vaga".

- [ ] **Step 5: Front**

`BotaoConfirmarPresenca.tsx:132` usa `vagasRestantes` que vem do back — se o valor do back já é o de reserva (via `listarInscritos` / o endpoint que alimenta o botão), o front não muda. Confirmar de qual endpoint o `vagasRestantes` do botão vem e garantir que ele passou a usar `contarOcupadas`. Se vier de um endpoint diferente, rotear esse também.

- [ ] **Step 6: Rodar + Commit**

```bash
set -a && . ./.env && set +a && mvn -q test -Dtest=InscricaoServiceTest,EventoServiceTest,ConviteControllerTest
git add src/main/java/com/domus/api/modules/evento/ src/test/java/com/domus/api/modules/evento/
git commit -m "fix(evento): contagem de vaga unica (reserva) em lista, convite publico e reducao de vagas"
```

---

## Task 8: I6 + I7 — falhas do MP viram 4xx; lembrete com try/catch

**Files:**
- Modify: `modules/pagamento/MercadoPagoApi.java` — os `catch (Exception e)` (linhas 94, 173, 247, 311, 341, 371)
- Modify: `modules/evento/inscricao/InscricaoService.java:1925` — `enviarLembretePagamento` (se ainda não coberto pela Task 2)
- Test: um teste de `MercadoPagoApi` (ou do controller) provando o 4xx; `InscricaoServiceTest`

- [ ] **Step 1: Testes**

```java
// no teste do controller que já mocka MercadoPagoApi/Client:
@Test
void pagar_quandoMercadoPagoIndisponivel_devolve4xxNao500() {
    when(mercadoPagoApi.criarPagamentoTokenizado(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(new BusinessException("MP_INDISPONIVEL", "..."));
    mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId).contentType(APPLICATION_JSON).content(corpoCartao()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", is("MP_INDISPONIVEL")));
}
```

E um teste unitário de `MercadoPagoApi` (com `RestClient` mockado ou stub) que força uma `RestClientException` e verifica que a exceção propagada é `BusinessException`, não `IllegalStateException`.

- [ ] **Step 2: Rodar e ver falhar**

- [ ] **Step 3: `MercadoPagoApi` — `BusinessException` em vez de `IllegalStateException`**

Em cada `catch (Exception e)`, trocar o `throw new IllegalStateException("Falha ao ...", e)` por:

```java
    log.error("Falha ao <ação> no Mercado Pago", e);   // manter o log existente
    throw new BusinessException("MP_INDISPONIVEL",
        "Não foi possível falar com o Mercado Pago agora. Tente de novo em instantes.");
```

Manter a `IllegalStateException` só onde é "resposta vazia/inesperada" e sinaliza bug nosso (linhas 235, 301) — essas podem continuar 500, ou virar `MP_RESPOSTA_INVALIDA` `BusinessException` também (preferir `BusinessException`, é mais amigável). **Não** engolir a recusa de cartão (que hoje volta 200 com `status: rejected`) — essa lógica não passa por esses catches, confirmar.

- [ ] **Step 4: I7 — `enviarLembretePagamento` try/catch**

Se a Task 2 já não envolveu: em `InscricaoService.java:1925`, envolver `emailService.enviar(...)` em `try { ... } catch (RuntimeException e) { log.error("Falha ao enviar lembrete de pagamento. inscricaoId={}", inscricaoId, e); }` — igual a `enviarEmailCancelamento` (linha ~868). A notificação in-app (linha ~1927) deve rodar mesmo se o e-mail falhar — mover pra fora do try, ou depois dele.

- [ ] **Step 5: Rodar full + Commit**

```bash
set -a && . ./.env && set +a && mvn -q test
git add src/main/java/com/domus/api/modules/pagamento/MercadoPagoApi.java src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java src/test/
git commit -m "fix(pagamento): falha do Mercado Pago vira 4xx; lembrete de pagamento nao quebra transacao"
```

---

## Task 9: I9 — `marcarTodosPresentes` só mexe em CONFIRMADA

**Files:**
- Modify: `modules/evento/inscricao/InscricaoService.java:1156-1200` (`marcarTodosPresentes` + `desmarcarTodosPresentes`)
- Test: `InscricaoServiceTest`

- [ ] **Step 1: Teste**

```java
@Test
void marcarTodosPresentes_ignoraCanceladasEPendentes() {
    // 3 CONFIRMADA, 1 CANCELADA, 1 AGUARDANDO_PAGAMENTO
    int marcados = inscricaoService.marcarTodosPresentes(eventoId, igrejaId, "ADMIN_IGREJA");
    assertThat(marcados).isEqualTo(3);
    assertThat(inscricaoCancelada().isCompareceu()).isFalse();
}
```

- [ ] **Step 2: Rodar e ver falhar**

- [ ] **Step 3: Implementar** — no loop de `marcarTodosPresentes` e `desmarcarTodosPresentes`:

```java
for (InscricaoEvento inscricao : inscricoes) {
    if (inscricao.getStatus() != StatusInscricao.CONFIRMADA) continue;
    inscricao.setCompareceu(true);  // ou false no desmarcar
    marcados++;
    inscricaoRepository.save(inscricao);
}
```

- [ ] **Step 4: Rodar + Commit**

```bash
set -a && . ./.env && set +a && mvn -q test -Dtest=InscricaoServiceTest
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "fix(evento): marcar/desmarcar todos presentes ignora inscricao nao confirmada"
```

---

## Task 10: I11 — cadastro recusa prazo de inscrição no passado

**Files:**
- Modify: `modules/evento/EventoService.java` — `validarPrazoInscricao` (~linha 697); precisa distinguir criar de editar
- Test: `EventoServiceTest`

- [ ] **Step 1: Teste**

```java
@Test
void criarEvento_comInscricoesAteNoPassado_recusa() {
    var req = requestPago(...); // inscricoesAte = LocalDateTime.now().minusDays(1), inicioEm no futuro
    assertThatThrownBy(() -> eventoService.criarEvento(req, igrejaId, usuarioId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("passado");
}

@Test
void editarEvento_podeFecharInscricoesColocandoPrazoNoPassado() {
    // edição de evento existente com inscricoesAte no passado NÃO lança
    eventoService.atualizarEvento(eventoId, requestComPrazoPassado(), igrejaId, usuarioId);
    // não lança
}
```

- [ ] **Step 2: Rodar e ver falhar**

- [ ] **Step 3: Implementar**

`validarPrazoInscricao(EventoRequest data)` vira `validarPrazoInscricao(EventoRequest data, boolean criacao)`:

```java
private void validarPrazoInscricao(EventoRequest data, boolean criacao) {
    if (data.inscricoesAte() == null) return;
    if (data.inscricoesAte().isAfter(data.inicioEm())) {
        throw new BusinessException("PRAZO_INSCRICAO_APOS_INICIO", "O prazo de inscrição não pode ser depois do início do evento.");
    }
    if (criacao && data.inscricoesAte().isBefore(LocalDateTime.now())) {
        throw new BusinessException("PRAZO_INSCRICAO_NO_PASSADO",
            "O prazo de inscrição já passou. Escolha uma data futura ou deixe em branco.");
    }
}
```

Chamador em `criarEvento` (linha 122) passa `true`; em `atualizarEvento` (linha 203) passa `false`.

- [ ] **Step 4: Rodar + Commit**

```bash
set -a && . ./.env && set +a && mvn -q test -Dtest=EventoServiceTest,EventoRequestTest
git add src/main/java/com/domus/api/modules/evento/EventoService.java src/test/java/com/domus/api/modules/evento/EventoServiceTest.java
git commit -m "fix(evento): cadastro recusa prazo de inscricao no passado (edicao continua permitindo)"
```

---

## Task 11: I13 — prévia de "virar gratuito" usa o valor real do estorno

**Files:**
- Modify: `modules/evento/inscricao/InscricaoService.java` — `calcularImpactoEventoVirarGratuito` (~linha 1277-1282) e `calcularImpactoEventoVirarPago` (~1307)
- Test: `InscricaoServiceTest`

- [ ] **Step 1: Teste**

```java
@Test
void previaVirarGratuito_usaValorRestanteParaEstornar_naoOAlvo() {
    // cobrança PAGA: valor=100, valorCobrado=111.73, valorEstornado=0
    var impacto = inscricaoService.calcularImpactoEventoVirarGratuito(eventoId, igrejaId);
    assertThat(impacto.valorTotalAEstornar()).isEqualByComparingTo("111.73");
}

@Test
void previaVirarGratuito_descontaOQueJaFoiEstornado() {
    // valorCobrado=111.73, valorEstornado=111.73 (já 100% estornado) → não conta
    var impacto = inscricaoService.calcularImpactoEventoVirarGratuito(eventoId, igrejaId);
    assertThat(impacto.valorTotalAEstornar()).isEqualByComparingTo("0");
    assertThat(impacto.pessoasComPagamentoPago()).isZero();
}
```

- [ ] **Step 2: Rodar e ver falhar**

- [ ] **Step 3: Implementar**

Em `calcularImpactoEventoVirarGratuito`, no loop:

```java
for (CobrancaEvento cobranca : cobrancaEventoRepository.findByInscricaoId(inscricao.getId())) {
    if (cobranca.getStatus() == StatusCobranca.PAGO) {
        BigDecimal restante = cobranca.valorRestanteParaEstornar(); // bruto - já estornado
        if (restante.signum() > 0) {
            pessoasComPagamentoPago++;
            valorTotalAEstornar = valorTotalAEstornar.add(restante);
        }
    }
}
```

Em `calcularImpactoEventoVirarPago` (linha ~1307): onde multiplica `precoNovo × pessoas` sem gross-up — usar `CalculadoraTaxaPagamento` pra estimar o bruto por pessoa (mesmo cálculo que `montarOpcoes` faz pro 1x cartão / Pix), ou pelo menos anotar no DTO que é estimativa. Preferir o cálculo real: `calculadora.valorACobrar(igrejaId, precoNovo, MeioPagamento.PIX, 1)` como piso e deixar claro na prévia que "cartão sai mais caro". (Se ficar complexo, escopo mínimo: só corrigir o `virarGratuito`, que é o bug claro, e anotar o `virarPago` como dívida — decisão do implementador com o revisor.)

- [ ] **Step 4: Rodar + Commit**

```bash
set -a && . ./.env && set +a && mvn -q test -Dtest=InscricaoServiceTest
git add src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "fix(evento): previa de virar gratuito usa o valor real a estornar (bruto - ja estornado)"
```

---

## Task 12: Verificação de ponta a ponta

- [ ] **Step 1: Suíte completa**

Run: `set -a && . ./.env && set +a && mvn -q test`
Expected: BUILD SUCCESS, 0 falhas. Se algum teste antigo quebrar por mudança de assinatura (`confirmarPagamento` lock, `contarPessoasConfirmadas` → `contarOcupadas`, `validarPrazoInscricao` +arg), corrigir o **teste** pra assinatura nova sem enfraquecer asserção.

- [ ] **Step 2: Front**

Run: `cd ../../frontend && npx tsc --noEmit && npm run build`
Expected: sem erro.

- [ ] **Step 3: Teste manual do autor (roteiro)**

1. **C1:** evento pago R$ 100, inscrever + pagar. Admin sobe pra R$ 130 → a inscrição continua "Inscrito" com tag "Falta complementar" + botão "Lembrar". Esperar o complemento expirar (ou forçar via banco) → a inscrição **continua** lá, tag e botão continuam. Clicar "Lembrar" → chega e-mail com link novo que funciona.
2. **C4:** inscrever num evento com `inscricoes_ate` amanhã, fechar o checkout sem pagar. Mudar o `inscricoes_ate` pra ontem (ou esperar). Reabrir o evento → deve aparecer "Pagamento pendente — continuar", e o pagamento deve concluir.
3. **I2:** pagar um evento e conferir que o e-mail de comprovante mostra o valor com a taxa (não o valor base).
4. **I3:** evento pago com 2 vagas. Inscrever-se e ficar na tela de pagamento (não pagar). Noutro navegador, abrir o evento → deve mostrar 1 vaga (não 2), e se inscrever a 2ª pessoa deixando pendente → a 3ª não consegue nem clicar "Se inscrever".
5. **I11:** criar evento com prazo de inscrição ontem → erro claro no formulário.

- [ ] **Step 4: Não commitar mais nada. Apresentar o resumo e esperar o autor testar antes de qualquer merge.**

---

## Self-Review

**1. Cobertura da auditoria:**
- C1 → Task 1 + Task 2 (job não cancela + botão lembrar recria).
- C2 → Task 3 (`@Transactional` + `buscarComLock`).
- C3 → Task 4 (reconfere status).
- C4 → Task 5 (front `return` + `cobrancaPendenteId`).
- I1 → Task 5 (`/pagar` valida `ENCERRADO`, não o prazo).
- I2 → Task 6 (comprovante usa `valorCobrado`).
- I3 → Task 7 (contagem de reserva única).
- I6 → Task 8 (`BusinessException`).
- I7 → Task 8 (try/catch no lembrete).
- I9 → Task 9 (`marcarTodosPresentes` filtra CONFIRMADA).
- I11 → Task 10 (prazo no passado no cadastro).
- I13 → Task 11 (prévia usa `valorRestanteParaEstornar`).
- **Fora deste plano (backlog):** I4 (estorno em lote perde chamada bem-sucedida), I5 (série + preço), I8 (`@Min/@Max` na taxa negociada — vai junto com a UI de taxa negociada), I10 (`cancelarPorCobranca` fura política), I12 (`aplicarMudancaValorPago` só estorna da última cobrança), M1–M8. Anotar no BACKLOG que ficaram.

**2. Placeholder scan:** Task 11 Step 3 deixa uma decisão ao implementador (corrigir `virarPago` ou só anotar) — é uma decisão real de escopo com o revisor, não um placeholder de "implementar depois"; o caminho mínimo (só `virarGratuito`) está explícito. Sem outros.

**3. Consistência de tipos/assinaturas:**
- `confirmarPagamento(String, String, InformacoesPagamento)` — assinatura não muda; só o `findById` interno vira `buscarComLock`. Task 3 e Task 4 (que chama `confirmarPagamento` de dentro do `reiniciar`) consistentes.
- `contarOcupadas(UUID eventoId)` — Task 7 define público; os call sites de Task 7 usam esse nome.
- `validarPrazoInscricao(EventoRequest, boolean)` — Task 10 muda a assinatura; os dois chamadores no mesmo step.
- `CobrancaEventoRepository.existePagaParaInscricao(UUID)` — Task 1 define, Task 1 usa.
- `BusinessException` — reusado em todas (I6, I11, C3, C4, I1), código de erro string em cada `throw`.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-09-10-hardening-eventos-pagamento.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
