# Meio de pagamento, parcelamento e taxa por evento — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deixar cada evento pago escolher quais meios de pagamento aceita (Pix sempre, cartão opt-in) e o teto de parcelas, com a taxa do Mercado Pago repassada ao pagador via gross-up e registrada separada no financeiro da igreja.

**Architecture:** A igreja cadastra o valor que quer **receber** (`evento.preco` muda de significado). Um service novo (`CalculadoraTaxaPagamento`) calcula quanto cobrar do pagador por meio/parcela (`valorAlvo / (1 − taxaEfetiva)`, arredondado pra cima). O checkout ganha uma tela de escolha de método antes do Payment Brick; o `POST /pagar` recalcula o valor no back (nunca confia no front). Na confirmação, o `MovimentacaoAutomaticaService` passa a lançar dois registros: ENTRADA do bruto em "Eventos" e SAÍDA da taxa real (de `fee_details` do MP) em "Taxas de pagamento".

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, PostgreSQL (Testcontainers nos testes), JUnit 5 + AssertJ + Mockito; Next.js 16, TypeScript, TanStack Query, React Hook Form + Zod, CSS Modules; SDK `@mercadopago/sdk-react` (Payment Brick).

**Spec:** `docs/superpowers/specs/2026-09-09-meio-pagamento-parcelamento-taxa-evento-design.md`

## Global Constraints

- **Camadas:** `controller → service → repository`. Services retornam DTO, nunca entidade.
- **`igreja_id` sempre do JWT**, nunca do corpo. (Rotas de `/cobrancas/**` são públicas por posse do UUID — não introduzir `igreja_id` de request nelas.)
- **Isolamento de tenant** em toda query de domínio.
- **Programar para capacidade, não identidade:** nada de `role == 'ADMIN'` solto; usar `Permissoes.*`. Literais de domínio em `enum` (back) / união de tipos (front).
- **Migrations são a fonte da verdade do schema** — atualizar o diagrama ER do `CLAUDE.md` (estado atual: V39 → passa a V40).
- **Teste comprova a regra, incluindo o caminho que recusa.** Mockito puro é a regra (90% do projeto); `@SpringBootTest + @AutoConfigureMockMvc + AutenticacaoTestSupport` para controller; `@DataJpaTest` só para query JPQL não trivial. Testcontainers sobe o Postgres — precisa de Docker rodando.
- **Nunca alterar teste só pra passar.** Nunca reportar "passou" sem rodar `mvn -q test -Dtest=...` e ver o resultado.
- **Não commitar antes de o autor testar** o pedaço. Um commit coerente por task.
- **Front:** responsividade obrigatória (tabelas→cards, headers empilham, grid→1 coluna, `min-width: 0`); movimento é entrega, não enfeite (`<Colapsavel>` para toggle mostra/esconde, `<Transicao>`/`<Revelar>` para bloco que surge). Rótulo de campo sempre com exemplo no `placeholder`. Sem teste automatizado de front — validação manual no viewport de iPhone **e** Android.
- **Moeda:** `BigDecimal`, `RoundingMode.CEILING` para o valor a cobrar (a igreja nunca recebe menos que o alvo), 2 casas.
- **Percentuais** guardados em pontos percentuais (`4.49` = 4,49%), `NUMERIC(5,2)`.
- Sem coautoria em commits além da linha de atribuição já configurada na sessão.

---

## File Structure

**Backend — criar:**
- `src/main/resources/db/migration/V40__meio_pagamento_e_taxa_evento.sql`
- `src/main/java/com/domus/api/modules/pagamento/MeioPagamento.java` — enum `PIX`, `CARTAO`
- `src/main/java/com/domus/api/modules/pagamento/TaxaPagamentoProperties.java` — `@ConfigurationProperties("pagamento.taxa")`
- `src/main/java/com/domus/api/modules/pagamento/CalculadoraTaxaPagamento.java` — cálculo do gross-up
- `src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/OpcoesPagamentoResponse.java`
- `src/main/java/com/domus/api/modules/pagamento/DTOs/SimularPagamentoRequest.java`
- `src/test/java/com/domus/api/modules/pagamento/CalculadoraTaxaPagamentoTest.java`
- `src/test/java/com/domus/api/modules/pagamento/cobranca/OpcoesPagamentoControllerTest.java`
- `src/test/java/com/domus/api/modules/pagamento/MercadoPagoApiFeeDetailsTest.java`
- `src/test/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoAutomaticaServiceTest.java` (se não existir; senão estender)

**Backend — modificar:**
- `modules/evento/Evento.java` — `pagamentoAceitaCartao`, `pagamentoMaxParcelas`
- `modules/evento/DTOs/EventoRequest.java`, `modules/evento/DTOs/EventoResponse.java`
- `modules/evento/EventoService.java` — criar/atualizar + `simularPagamento`
- `modules/pagamento/conta/ContaPagamentoIgreja.java` — 3 campos de taxa negociada + getters + setter
- `modules/pagamento/conta/ContaPagamentoIgrejaRepository.java` — nada (já tem `findByIgrejaId`)
- `modules/pagamento/MercadoPagoApi.java` — `InformacoesPagamento` + parse `fee_details`
- `modules/pagamento/MercadoPagoClient.java` — `criarPagamentoComToken` recebe `valorACobrar`; `buscarInformacoesPagamento*` já delega
- `modules/pagamento/cobranca/CobrancaEvento.java` — `valorCobrado`; `valorRestanteParaEstornar` usa `valorCobrado`
- `modules/pagamento/cobranca/DTOs/PagarCobrancaRequest.java` — `meio`, `parcelas`
- `modules/pagamento/cobranca/CobrancaController.java` — `pagar` recalcula; novo `GET /{id}/opcoes-pagamento`
- `modules/pagamento/cobranca/CobrancaEventoService.java` — `montarOpcoesPagamento`
- `modules/pagamento/webhook/MercadoPagoWebhookService.java` — `confirmarPagamento` recebe fee info; `registrarNoFinanceiro`
- `modules/pagamento/webhook/MercadoPagoWebhookController.java` — passa `InformacoesPagamento` inteiro
- `modules/pagamento/PagamentoPollingService.java` — idem
- `modules/financeiro/movimentacao/MovimentacaoAutomaticaService.java` — dois lançamentos + categoria "Taxas de pagamento"
- `modules/evento/inscricao/InscricaoService.java` — `registrarEstornoNoFinanceiro` dois lançamentos; `aplicarMudancaValorPago` gross ratio
- `src/main/resources/application.properties` — `pagamento.taxa.*`
- `EventoController.java` — endpoint `POST /eventos/simular-pagamento`
- `CLAUDE.md` — diagrama ER (V40)

**Frontend — criar:**
- `src/components/module/pagamento/EscolhaMeioPagamento.tsx` + `.module.css`
- `src/components/module/eventos/ResumoTaxaPagamento.tsx` + `.module.css` (resumo e-commerce no form)

**Frontend — modificar:**
- `src/types/api.types.ts` — `MeioPagamento`, `OpcaoPagamento`, `OpcoesPagamentoResponse`, campos de `Evento`
- `src/lib/endpoints.ts` — `cobrancas.OPCOES_PAGAMENTO`, `eventos.SIMULAR_PAGAMENTO`
- `src/services/cobranca.service.ts` — `opcoesPagamento`; `PagarCobrancaRequest` ganha `meio`/`parcelas`
- `src/services/evento.service.ts` (ou onde vive) — `simularPagamento`
- `src/components/module/pagamento/PaymentBrickCheckout.tsx` — props `meio`/`parcelas`/`valorTotal`
- `src/app/eventos/[id]/pagamento/[cobrancaId]/page.tsx` — insere `EscolhaMeioPagamento`
- `src/app/cobranca/[token]/page.tsx` — idem
- `src/hooks/evento/useEventoForm.ts` — campos `pagamentoAceitaCartao`, `pagamentoMaxParcelas`
- `src/components/module/eventos/EventoForm.tsx` — toggle cartão + select parcelas + `<ResumoTaxaPagamento>`
- `src/components/module/eventos/DrawerDetalheEvento.tsx` (e card/página de inscrição) — nota discreta

---

## Task 1: Migration V40 + enum `MeioPagamento` + `TaxaPagamentoProperties`

**Files:**
- Create: `src/main/resources/db/migration/V40__meio_pagamento_e_taxa_evento.sql`
- Create: `src/main/java/com/domus/api/modules/pagamento/MeioPagamento.java`
- Create: `src/main/java/com/domus/api/modules/pagamento/TaxaPagamentoProperties.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/main/java/com/domus/api/ApiApplication.java` (ou a classe `@SpringBootApplication`) — `@ConfigurationPropertiesScan` se ainda não existir
- Test: `src/test/java/com/domus/api/modules/pagamento/TaxaPagamentoPropertiesTest.java`

**Interfaces:**
- Produces:
  - `enum MeioPagamento { PIX, CARTAO }`
  - `record TaxaPagamentoProperties(BigDecimal pixPercent, BigDecimal cartaoAvistaPercent, BigDecimal cartaoParcelaAdicionalPercent)`
  - Colunas: `evento.pagamento_aceita_cartao BOOLEAN NOT NULL DEFAULT FALSE`, `evento.pagamento_max_parcelas SMALLINT NOT NULL DEFAULT 1`; `cobranca_evento.valor_cobrado NUMERIC(10,2)`; `conta_pagamento_igreja.taxa_pix_percent / taxa_cartao_avista_percent / taxa_cartao_parcela_adicional_percent NUMERIC(5,2)` (nuláveis)

- [ ] **Step 1: Escrever a migration**

Create `V40__meio_pagamento_e_taxa_evento.sql`:

```sql
-- Meio de pagamento, parcelamento e taxa por evento.
-- Ver docs/superpowers/specs/2026-09-09-meio-pagamento-parcelamento-taxa-evento-design.md

ALTER TABLE evento
    ADD COLUMN pagamento_aceita_cartao BOOLEAN  NOT NULL DEFAULT FALSE,
    ADD COLUMN pagamento_max_parcelas  SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE evento
    ADD CONSTRAINT chk_evento_max_parcelas CHECK (pagamento_max_parcelas BETWEEN 1 AND 12);

-- Valor efetivamente cobrado do pagador (alvo + taxa, com gross-up). NULL até a 1ª
-- tentativa de pagamento. `valor` continua sendo o alvo (o que a igreja quer receber).
ALTER TABLE cobranca_evento
    ADD COLUMN valor_cobrado NUMERIC(10,2);

-- Taxas negociadas da igreja com o Mercado Pago (plano personalizado). NULL = usa o
-- default da config do back (pagamento.taxa.*).
ALTER TABLE conta_pagamento_igreja
    ADD COLUMN taxa_pix_percent                      NUMERIC(5,2),
    ADD COLUMN taxa_cartao_avista_percent            NUMERIC(5,2),
    ADD COLUMN taxa_cartao_parcela_adicional_percent NUMERIC(5,2);
```

- [ ] **Step 2: Criar o enum**

`MeioPagamento.java`:

```java
package com.domus.api.modules.pagamento;

/** Meio de pagamento escolhido pelo pagador no checkout. PIX nunca parcela. */
public enum MeioPagamento {
    PIX,
    CARTAO
}
```

- [ ] **Step 3: Criar o `TaxaPagamentoProperties`**

`TaxaPagamentoProperties.java`:

```java
package com.domus.api.modules.pagamento;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tabela de taxa PADRÃO do Mercado Pago, em pontos percentuais (4.49 = 4,49%). Usada pelo
 * gross-up quando a igreja não informou taxa negociada própria em ContaPagamentoIgreja.
 * Se o MP mudar a tabela pública, ajustar aqui e redeployar.
 */
@ConfigurationProperties(prefix = "pagamento.taxa")
public record TaxaPagamentoProperties(
        BigDecimal pixPercent,
        BigDecimal cartaoAvistaPercent,
        BigDecimal cartaoParcelaAdicionalPercent
) {}
```

- [ ] **Step 4: Registrar no `application.properties`**

Adicionar ao bloco de pagamento (perto de `app.pagamento.*`):

```properties
# Taxa padrao do Mercado Pago (pontos percentuais) para o gross-up de evento pago.
pagamento.taxa.pix-percent=0.99
pagamento.taxa.cartao-avista-percent=4.49
pagamento.taxa.cartao-parcela-adicional-percent=2.50
```

- [ ] **Step 5: Garantir o binding do `@ConfigurationProperties`**

Verificar na classe `@SpringBootApplication` se há `@ConfigurationPropertiesScan`. Se **não** houver, adicionar. Se o projeto usa `@EnableConfigurationProperties` explícito noutro lugar, seguir esse padrão e registrar `TaxaPagamentoProperties.class`.

Run: `grep -rn "ConfigurationPropertiesScan\|EnableConfigurationProperties" src/main/java`

- [ ] **Step 6: Escrever o teste do properties**

`TaxaPagamentoPropertiesTest.java`:

```java
package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
class TaxaPagamentoPropertiesTest implements PostgresTestContainerSupport {

    @Autowired TaxaPagamentoProperties props;

    @Test
    void carregaValoresPadraoDoApplicationProperties() {
        assertThat(props.pixPercent()).isEqualByComparingTo(new BigDecimal("0.99"));
        assertThat(props.cartaoAvistaPercent()).isEqualByComparingTo(new BigDecimal("4.49"));
        assertThat(props.cartaoParcelaAdicionalPercent()).isEqualByComparingTo(new BigDecimal("2.50"));
    }
}
```

- [ ] **Step 7: Rodar a suíte de migration + o teste novo**

Run: `mvn -q test -Dtest=MigracaoV3Test,TaxaPagamentoPropertiesTest`
Expected: PASS. Flyway aplica V40 sozinho contra o Postgres do Testcontainers.

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V40__meio_pagamento_e_taxa_evento.sql \
        src/main/java/com/domus/api/modules/pagamento/MeioPagamento.java \
        src/main/java/com/domus/api/modules/pagamento/TaxaPagamentoProperties.java \
        src/main/resources/application.properties \
        src/test/java/com/domus/api/modules/pagamento/TaxaPagamentoPropertiesTest.java
git commit -m "feat(pagamento): schema V40 + enum MeioPagamento + config de taxa padrao"
```

---

## Task 2: `CalculadoraTaxaPagamento` — gross-up

**Files:**
- Create: `src/main/java/com/domus/api/modules/pagamento/CalculadoraTaxaPagamento.java`
- Modify: `src/main/java/com/domus/api/modules/pagamento/conta/ContaPagamentoIgreja.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/CalculadoraTaxaPagamentoTest.java`

**Interfaces:**
- Consumes: `TaxaPagamentoProperties` (Task 1), `MeioPagamento` (Task 1), `ContaPagamentoIgrejaRepository.findByIgrejaId(UUID)` (existente, retorna `Optional<ContaPagamentoIgreja>`).
- Produces:
  - `ContaPagamentoIgreja` ganha getters `getTaxaPixPercent()`, `getTaxaCartaoAvistaPercent()`, `getTaxaCartaoParcelaAdicionalPercent()` (`BigDecimal`, nuláveis) e um `atualizarTaxasNegociadas(BigDecimal pix, BigDecimal cartaoAvista, BigDecimal parcelaAdicional)`.
  - `CalculadoraTaxaPagamento.valorACobrar(UUID igrejaId, BigDecimal valorAlvo, MeioPagamento meio, int parcelas) -> BigDecimal` (2 casas, `CEILING`).
  - `CalculadoraTaxaPagamento.taxaEmReais(BigDecimal valorAlvo, BigDecimal valorACobrar) -> BigDecimal` = `valorACobrar - valorAlvo`.

- [ ] **Step 1: Adicionar os campos de taxa na `ContaPagamentoIgreja`**

Adicionar as 3 colunas mapeadas + getters + método de atualização:

```java
    @Column(name = "taxa_pix_percent")
    private BigDecimal taxaPixPercent;

    @Column(name = "taxa_cartao_avista_percent")
    private BigDecimal taxaCartaoAvistaPercent;

    @Column(name = "taxa_cartao_parcela_adicional_percent")
    private BigDecimal taxaCartaoParcelaAdicionalPercent;

    // ... nos getters existentes:
    public BigDecimal getTaxaPixPercent() { return taxaPixPercent; }
    public BigDecimal getTaxaCartaoAvistaPercent() { return taxaCartaoAvistaPercent; }
    public BigDecimal getTaxaCartaoParcelaAdicionalPercent() { return taxaCartaoParcelaAdicionalPercent; }

    /** Taxas negociadas da igreja com o MP. Qualquer uma nula = usa o default do back. */
    public void atualizarTaxasNegociadas(BigDecimal pix, BigDecimal cartaoAvista, BigDecimal parcelaAdicional) {
        this.taxaPixPercent = pix;
        this.taxaCartaoAvistaPercent = cartaoAvista;
        this.taxaCartaoParcelaAdicionalPercent = parcelaAdicional;
    }
```

Import `java.math.BigDecimal` já está no arquivo? Verificar; se não, adicionar.

- [ ] **Step 2: Escrever os testes primeiro**

`CalculadoraTaxaPagamentoTest.java`:

```java
package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculadoraTaxaPagamentoTest {

    UUID igrejaId = UUID.randomUUID();
    ContaPagamentoIgrejaRepository contaRepository;
    CalculadoraTaxaPagamento calculadora;

    @BeforeEach
    void setup() {
        contaRepository = mock(ContaPagamentoIgrejaRepository.class);
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty()); // sem override
        var props = new TaxaPagamentoProperties(
            new BigDecimal("0.99"), new BigDecimal("4.49"), new BigDecimal("2.50"));
        calculadora = new CalculadoraTaxaPagamento(contaRepository, props);
    }

    @Test
    void pixUsaTaxaPixEArredondaParaCima() {
        // 100 / (1 - 0.0099) = 100 / 0.9901 = 101.0000 -> 101.00
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.PIX, 1);
        assertThat(cobrar).isEqualByComparingTo("101.00");
    }

    @Test
    void cartaoAvistaUsaTaxaAvista() {
        // 100 / (1 - 0.0449) = 100 / 0.9551 = 104.7011... -> CEILING -> 104.71
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 1);
        assertThat(cobrar).isEqualByComparingTo("104.71");
    }

    @Test
    void cartaoParceladoSomaAdicionalPorParcelaExtra() {
        // 3x: taxa = 4.49 + 2*2.50 = 9.49%. 100 / 0.9051 = 110.4850... -> 110.49
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 3);
        assertThat(cobrar).isEqualByComparingTo("110.49");
    }

    @Test
    void taxaNegociadaDaIgrejaTemPrecedenciaSobreConfig() {
        var conta = mock(ContaPagamentoIgreja.class);
        when(conta.getTaxaCartaoAvistaPercent()).thenReturn(new BigDecimal("3.00"));
        when(conta.getTaxaPixPercent()).thenReturn(null);
        when(conta.getTaxaCartaoParcelaAdicionalPercent()).thenReturn(null);
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        // 100 / (1 - 0.03) = 103.0927... -> 103.10
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 1);
        assertThat(cobrar).isEqualByComparingTo("103.10");
    }

    @Test
    void overrideParcialCaiNoDefaultParaOCampoNaoInformado() {
        var conta = mock(ContaPagamentoIgreja.class);
        when(conta.getTaxaPixPercent()).thenReturn(new BigDecimal("0.50"));
        when(conta.getTaxaCartaoAvistaPercent()).thenReturn(null); // cai no default 4.49
        when(conta.getTaxaCartaoParcelaAdicionalPercent()).thenReturn(null);
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        assertThat(calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.PIX, 1))
            .isEqualByComparingTo("100.51"); // 100 / 0.995 = 100.5025 -> 100.51
        assertThat(calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 1))
            .isEqualByComparingTo("104.71");
    }

    @Test
    void pixComMaisDeUmaParcelaEhRecusado() {
        assertThatThrownBy(() -> calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.PIX, 2))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Pix");
    }

    @Test
    void parcelasMenorQueUmEhRecusado() {
        assertThatThrownBy(() -> calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 0))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void valorAlvoZeroOuNegativoEhRecusado() {
        assertThatThrownBy(() -> calculadora.valorACobrar(igrejaId, BigDecimal.ZERO, MeioPagamento.PIX, 1))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void taxaEmReaisEhADiferenca() {
        assertThat(calculadora.taxaEmReais(new BigDecimal("100.00"), new BigDecimal("104.71")))
            .isEqualByComparingTo("4.71");
    }
}
```

- [ ] **Step 2b: Rodar e ver falhar**

Run: `mvn -q test -Dtest=CalculadoraTaxaPagamentoTest`
Expected: FAIL — `CalculadoraTaxaPagamento` não existe.

- [ ] **Step 3: Implementar a `CalculadoraTaxaPagamento`**

```java
package com.domus.api.modules.pagamento;

import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Gross-up: dado quanto a igreja quer RECEBER (valorAlvo), calcula quanto COBRAR do
 * pagador pra que, depois de o Mercado Pago descontar a taxa, sobre exatamente o alvo.
 *
 * <p>valorACobrar = valorAlvo / (1 - taxaEfetiva). Divisão, não multiplicação: o MP cobra
 * a % sobre o valor cobrado, não sobre o alvo. Arredonda pra CIMA — a igreja nunca recebe
 * menos que o alvo.</p>
 *
 * <p>taxaEfetiva vem da taxa negociada da igreja (ContaPagamentoIgreja), campo a campo,
 * caindo no default do back (TaxaPagamentoProperties) pra cada campo não informado.</p>
 */
@Service
public class CalculadoraTaxaPagamento {

    private static final int MAX_PARCELAS = 12;

    private final ContaPagamentoIgrejaRepository contaRepository;
    private final TaxaPagamentoProperties padrao;

    public CalculadoraTaxaPagamento(ContaPagamentoIgrejaRepository contaRepository,
                                     TaxaPagamentoProperties padrao) {
        this.contaRepository = contaRepository;
        this.padrao = padrao;
    }

    public BigDecimal valorACobrar(UUID igrejaId, BigDecimal valorAlvo, MeioPagamento meio, int parcelas) {
        if (valorAlvo == null || valorAlvo.signum() <= 0) {
            throw new BusinessException("VALOR_ALVO_INVALIDO", "O valor da inscrição deve ser maior que zero.");
        }
        if (parcelas < 1 || parcelas > MAX_PARCELAS) {
            throw new BusinessException("PARCELAS_INVALIDAS", "Número de parcelas inválido.");
        }
        if (meio == MeioPagamento.PIX && parcelas > 1) {
            throw new BusinessException("PIX_NAO_PARCELA", "Pix não pode ser parcelado.");
        }

        BigDecimal taxaPercent = taxaEfetivaPercent(igrejaId, meio, parcelas);
        BigDecimal fator = BigDecimal.ONE.subtract(taxaPercent.movePointLeft(2)); // (1 - taxa/100)
        return valorAlvo.divide(fator, MathContext.DECIMAL64).setScale(2, RoundingMode.CEILING);
    }

    public BigDecimal taxaEmReais(BigDecimal valorAlvo, BigDecimal valorACobrar) {
        return valorACobrar.subtract(valorAlvo);
    }

    private BigDecimal taxaEfetivaPercent(UUID igrejaId, MeioPagamento meio, int parcelas) {
        ContaPagamentoIgreja conta = contaRepository.findByIgrejaId(igrejaId).orElse(null);
        if (meio == MeioPagamento.PIX) {
            return valorOu(conta == null ? null : conta.getTaxaPixPercent(), padrao.pixPercent());
        }
        BigDecimal avista = valorOu(conta == null ? null : conta.getTaxaCartaoAvistaPercent(),
            padrao.cartaoAvistaPercent());
        BigDecimal adicional = valorOu(conta == null ? null : conta.getTaxaCartaoParcelaAdicionalPercent(),
            padrao.cartaoParcelaAdicionalPercent());
        return avista.add(adicional.multiply(BigDecimal.valueOf(parcelas - 1L)));
    }

    private static BigDecimal valorOu(BigDecimal override, BigDecimal fallback) {
        return override != null ? override : fallback;
    }
}
```

- [ ] **Step 4: Rodar e ver passar**

Run: `mvn -q test -Dtest=CalculadoraTaxaPagamentoTest`
Expected: PASS (10 testes).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/CalculadoraTaxaPagamento.java \
        src/main/java/com/domus/api/modules/pagamento/conta/ContaPagamentoIgreja.java \
        src/test/java/com/domus/api/modules/pagamento/CalculadoraTaxaPagamentoTest.java
git commit -m "feat(pagamento): CalculadoraTaxaPagamento (gross-up da taxa do Mercado Pago)"
```

---

## Task 3: `evento` ganha config de pagamento

**Files:**
- Modify: `src/main/java/com/domus/api/modules/evento/Evento.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/evento/DTOs/EventoResponse.java`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java`
- Test: `src/test/java/com/domus/api/modules/evento/DTOs/EventoRequestTest.java` (existente — estender)
- Test: `src/test/java/com/domus/api/modules/evento/EventoServiceTest.java` (existente — estender)

**Interfaces:**
- Consumes: nada de tasks anteriores (o enum `MeioPagamento` não entra aqui).
- Produces:
  - `Evento.isPagamentoAceitaCartao()` / `setPagamentoAceitaCartao(boolean)`, `Evento.getPagamentoMaxParcelas()` / `setPagamentoMaxParcelas(int)`.
  - `EventoRequest` ganha `Boolean pagamentoAceitaCartao`, `Integer pagamentoMaxParcelas`.
  - `EventoResponse` ganha `boolean pagamentoAceitaCartao`, `int pagamentoMaxParcelas`.

- [ ] **Step 1: Adicionar os campos na entidade**

Em `Evento.java`, perto de `preco`:

```java
    /** Só relevante em evento pago. Pix é sempre aceito; cartão é opt-in. */
    @Column(name = "pagamento_aceita_cartao", nullable = false)
    @Builder.Default
    private boolean pagamentoAceitaCartao = false;

    /** Só relevante quando pagamentoAceitaCartao. 1 = só à vista. Cap em 12 (CHECK no banco). */
    @Column(name = "pagamento_max_parcelas", nullable = false)
    @Builder.Default
    private int pagamentoMaxParcelas = 1;
```

(`@Getter/@Setter` da classe geram os acessores — `isPagamentoAceitaCartao()` / `getPagamentoMaxParcelas()`.)

- [ ] **Step 2: Estender o teste de validação do request**

Em `EventoRequestTest.java`, ajustar o helper `base(...)` para os 2 parâmetros novos do record (ver Step 4) e adicionar:

```java
    @Test
    void maxParcelasAcimaDe12_recusaComViolacao() {
        var req = baseComPagamento(new java.math.BigDecimal("100.00"), true, 13);
        assertThat(VALIDATOR.validate(req)).isNotEmpty();
    }

    @Test
    void maxParcelasZero_recusaComViolacao() {
        var req = baseComPagamento(new java.math.BigDecimal("100.00"), true, 0);
        assertThat(VALIDATOR.validate(req)).isNotEmpty();
    }

    @Test
    void pagamentoValido_naoGeraViolacao() {
        var req = baseComPagamento(new java.math.BigDecimal("100.00"), true, 6);
        assertThat(VALIDATOR.validate(req)).isEmpty();
    }
```

`baseComPagamento` é um helper novo que monta um `EventoRequest` completo com `preco`, `pagamentoAceitaCartao`, `pagamentoMaxParcelas` preenchidos e o resto nos defaults (mesmo estilo do `base` atual).

- [ ] **Step 3: Rodar e ver falhar**

Run: `mvn -q test -Dtest=EventoRequestTest`
Expected: FAIL — compilação (campos não existem no record).

- [ ] **Step 4: Adicionar os campos no `EventoRequest`**

No record, junto de `preco`:

```java
        /** Só usado em evento pago. null tratado como false. */
        Boolean pagamentoAceitaCartao,

        /** Só usado quando pagamentoAceitaCartao. null tratado como 1. */
        @jakarta.validation.constraints.Min(value = 1, message = "O parcelamento deve ser de pelo menos 1x.")
        @jakarta.validation.constraints.Max(value = 12, message = "O parcelamento máximo é 12x.")
        Integer pagamentoMaxParcelas,
```

Atualizar o construtor do helper `base(...)` no `EventoRequestTest` para passar `null, null` nas duas novas posições.

- [ ] **Step 5: Adicionar no `EventoResponse` + mapper**

Em `EventoResponse.java` adicionar `boolean pagamentoAceitaCartao` e `int pagamentoMaxParcelas` na posição análoga a `preco`. Localizar o(s) ponto(s) de mapeamento `Evento -> EventoResponse` (`grep -n "new EventoResponse\|EventoResponse.de\|\.preco(" src/main/java`) e passar `evento.isPagamentoAceitaCartao()`, `evento.getPagamentoMaxParcelas()`.

- [ ] **Step 6: Persistir no `EventoService` (criar + atualizar)**

Em `EventoService.java`, achar onde `preco`/`vagas` são setados no `criarEvento` e no `atualizarEvento` (`grep -n "setPreco\|\.preco()\|setVagas" src/main/java/com/domus/api/modules/evento/EventoService.java`). Adicionar, no mesmo lugar, com a regra: **só valem quando o evento é pago; se cartão não é aceito, força `maxParcelas = 1`.**

```java
    private void aplicarConfigPagamento(Evento evento, EventoRequest req) {
        boolean pago = evento.getPreco() != null;
        boolean aceitaCartao = pago && Boolean.TRUE.equals(req.pagamentoAceitaCartao());
        evento.setPagamentoAceitaCartao(aceitaCartao);
        int maxParcelas = aceitaCartao && req.pagamentoMaxParcelas() != null
            ? req.pagamentoMaxParcelas() : 1;
        evento.setPagamentoMaxParcelas(maxParcelas);
    }
```

Chamar `aplicarConfigPagamento(evento, request)` depois de `preco` já ter sido resolvido, no criar e no atualizar.

- [ ] **Step 7: Teste no `EventoServiceTest`**

Adicionar (seguindo o estilo de mocks do arquivo):

```java
    @Test
    void eventoGratuitoIgnoraConfigDePagamento() {
        // request com preco null mas pagamentoAceitaCartao=true, maxParcelas=6
        Evento salvo = criarEventoComRequest(reqGratuitoComCartao());
        assertThat(salvo.isPagamentoAceitaCartao()).isFalse();
        assertThat(salvo.getPagamentoMaxParcelas()).isEqualTo(1);
    }

    @Test
    void eventoPagoSemCartaoForcaParcelaUnica() {
        Evento salvo = criarEventoComRequest(reqPago(new BigDecimal("50"), false, 6));
        assertThat(salvo.isPagamentoAceitaCartao()).isFalse();
        assertThat(salvo.getPagamentoMaxParcelas()).isEqualTo(1);
    }

    @Test
    void eventoPagoComCartaoGuardaOTeto() {
        Evento salvo = criarEventoComRequest(reqPago(new BigDecimal("50"), true, 6));
        assertThat(salvo.isPagamentoAceitaCartao()).isTrue();
        assertThat(salvo.getPagamentoMaxParcelas()).isEqualTo(6);
    }
```

(Adaptar os nomes dos helpers ao que o `EventoServiceTest` já usa para montar request + capturar o `Evento` salvo — provavelmente `verify(eventoRepository).save(captor.capture())`.)

- [ ] **Step 8: Rodar**

Run: `mvn -q test -Dtest=EventoRequestTest,EventoServiceTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/domus/api/modules/evento/
git commit -m "feat(evento): config de meio de pagamento e teto de parcelas por evento"
```

---

## Task 4: `MercadoPagoApi` lê `fee_details`

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/MercadoPagoApi.java`
- Test: `src/test/java/com/domus/api/modules/pagamento/MercadoPagoApiFeeDetailsTest.java`

**Interfaces:**
- Produces: `InformacoesPagamento` passa a ter `(String externalReference, String status, BigDecimal valorBruto, BigDecimal taxaMercadoPago, BigDecimal valorLiquido)`. Os 3 novos vêm `null` quando o pagamento ainda não tem `fee_details` (pending).

- [ ] **Step 1: Escrever o teste de parse**

O `buscarInformacoesPagamento` usa `RestClient` real. Para testar o parse isolado, extrair a conversão do JSON num método/record testável. Adicionar teste que desserializa um JSON de exemplo do MP (aprovado, com `fee_details`) e um sem `fee_details` (pending), via o `ObjectMapper` do Jackson diretamente sobre o record interno `RespostaPagamentoMercadoPago` (tornar o record package-private para o teste, ou expor um método estático `InformacoesPagamento.de(RespostaPagamentoMercadoPago)`).

`MercadoPagoApiFeeDetailsTest.java`:

```java
package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MercadoPagoApiFeeDetailsTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void pagamentoAprovadoExtraiBrutoTaxaLiquido() throws Exception {
        String json = """
            {
              "external_reference": "abc-123",
              "status": "approved",
              "transaction_amount": 110.49,
              "fee_details": [
                { "type": "mercadopago_fee", "amount": 10.49 }
              ],
              "transaction_details": { "net_received_amount": 100.00 }
            }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);

        assertThat(info.status()).isEqualTo("approved");
        assertThat(info.valorBruto()).isEqualByComparingTo("110.49");
        assertThat(info.taxaMercadoPago()).isEqualByComparingTo("10.49");
        assertThat(info.valorLiquido()).isEqualByComparingTo("100.00");
    }

    @Test
    void pagamentoPendenteSemFeeDetailsDeixaCamposNulos() throws Exception {
        String json = """
            { "external_reference": "abc-123", "status": "pending" }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);

        assertThat(info.status()).isEqualTo("pending");
        assertThat(info.valorBruto()).isNull();
        assertThat(info.taxaMercadoPago()).isNull();
        assertThat(info.valorLiquido()).isNull();
    }

    @Test
    void somaTodasAsLinhasDeMercadopagoFee() throws Exception {
        String json = """
            {
              "external_reference": "x", "status": "approved",
              "transaction_amount": 100.00,
              "fee_details": [
                { "type": "mercadopago_fee", "amount": 3.00 },
                { "type": "mercadopago_fee", "amount": 1.50 },
                { "type": "financing_fee", "amount": 9.99 }
              ],
              "transaction_details": { "net_received_amount": 95.50 }
            }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);
        // Soma TODAS as fee_details (o que o pagador pagou a mais coberto pelo gross-up
        // inclui a financing_fee do parcelamento). Usa net_received_amount como fonte
        // da verdade do liquido, e a taxa = bruto - liquido.
        assertThat(info.taxaMercadoPago()).isEqualByComparingTo("4.50"); // ver decisao no Step 2
        assertThat(info.valorLiquido()).isEqualByComparingTo("95.50");
    }
```

> **Decisão a fixar no Step 2** (questão aberta 2 da spec): `taxaMercadoPago` = **`transaction_amount − net_received_amount`** (fonte da verdade do que sumiu), não a soma de `fee_details`. `fee_details` fica só para log/diagnóstico. Ajustar a asserção do 3º teste para `taxaMercadoPago == 4.50` (100.00 − 95.50).

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=MercadoPagoApiFeeDetailsTest`
Expected: FAIL — `RespostaPagamentoMercadoPago` é `private`, `InformacoesPagamento` não tem os campos nem `de(...)`.

- [ ] **Step 3: Enriquecer o record de resposta**

Em `MercadoPagoApi.java`, tornar `RespostaPagamentoMercadoPago` package-private (`record RespostaPagamentoMercadoPago(...)`) e adicionar os campos:

```java
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaPagamentoMercadoPago(
        @JsonProperty("external_reference") String externalReference,
        String status,
        @JsonProperty("status_detail") String statusDetail,
        @JsonProperty("point_of_interaction") PontoDeInteracao pointOfInteraction,
        @JsonProperty("date_of_expiration") String dateOfExpiration,
        @JsonProperty("transaction_amount") BigDecimal transactionAmount,
        @JsonProperty("fee_details") java.util.List<FeeDetail> feeDetails,
        @JsonProperty("transaction_details") TransactionDetails transactionDetails
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FeeDetail(String type, BigDecimal amount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TransactionDetails(@JsonProperty("net_received_amount") BigDecimal netReceivedAmount) {}
```

- [ ] **Step 4: Enriquecer `InformacoesPagamento` + `de(...)`**

```java
    /**
     * (externalReference, status) + os valores financeiros do pagamento quando aprovado.
     * valorBruto/taxaMercadoPago/valorLiquido vêm null enquanto o pagamento não confirmou
     * (sem transaction_details). taxaMercadoPago = bruto - liquido (o que o MP reteve).
     */
    public record InformacoesPagamento(String externalReference, String status,
                                       BigDecimal valorBruto, BigDecimal taxaMercadoPago,
                                       BigDecimal valorLiquido) {

        static InformacoesPagamento de(RespostaPagamentoMercadoPago r) {
            BigDecimal bruto = r.transactionAmount();
            BigDecimal liquido = r.transactionDetails() != null
                ? r.transactionDetails().netReceivedAmount() : null;
            BigDecimal taxa = (bruto != null && liquido != null) ? bruto.subtract(liquido) : null;
            return new InformacoesPagamento(r.externalReference(), r.status(), bruto, taxa, liquido);
        }
    }
```

- [ ] **Step 5: Usar `de(...)` no `buscarInformacoesPagamento`**

Trocar a linha `return new InformacoesPagamento(pagamento.externalReference(), pagamento.status());` por `return InformacoesPagamento.de(pagamento);`. Corrigir os outros 2 pontos que constroem `InformacoesPagamento` (o de webhook e o de qr — `grep -n "new InformacoesPagamento"`), se existirem, para `de(...)` também.

- [ ] **Step 6: Rodar**

Run: `mvn -q test -Dtest=MercadoPagoApiFeeDetailsTest`
Expected: PASS (3 testes). Rodar também `mvn -q test -Dtest=MercadoPagoWebhookServiceTest,PagamentoPollingServiceTest` (se existirem) pra garantir que a mudança de assinatura do record não quebrou nada.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/MercadoPagoApi.java \
        src/test/java/com/domus/api/modules/pagamento/MercadoPagoApiFeeDetailsTest.java
git commit -m "feat(pagamento): InformacoesPagamento expoe bruto, taxa e liquido do fee_details"
```

---

## Task 5: `GET /cobrancas/{id}/opcoes-pagamento` + `POST /eventos/simular-pagamento`

**Files:**
- Create: `src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/OpcoesPagamentoResponse.java`
- Create: `src/main/java/com/domus/api/modules/pagamento/DTOs/SimularPagamentoRequest.java`
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoService.java` — `montarOpcoes(...)`
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java` — `GET /{id}/opcoes-pagamento`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoController.java` — `POST /eventos/simular-pagamento`
- Modify: `src/main/java/com/domus/api/modules/evento/EventoService.java` — `simularPagamento(...)`
- Modify: `SecurityConfig` — liberar `GET /cobrancas/*/opcoes-pagamento` (já coberto por `permitAll` de `/cobrancas/**`? conferir; `/eventos/simular-pagamento` fica autenticado)
- Test: `src/test/java/com/domus/api/modules/pagamento/cobranca/OpcoesPagamentoControllerTest.java`

**Interfaces:**
- Consumes: `CalculadoraTaxaPagamento.valorACobrar(...)` (Task 2), `MeioPagamento` (Task 1), `Evento.isPagamentoAceitaCartao()/getPagamentoMaxParcelas()/getPreco()` (Task 3).
- Produces:
  - `record OpcaoPagamento(MeioPagamento meio, int parcelas, BigDecimal valorTotal, BigDecimal valorParcela, BigDecimal taxa)`
  - `record OpcoesPagamentoResponse(BigDecimal valorEvento, List<OpcaoPagamento> opcoes)`
  - `record SimularPagamentoRequest(@NotNull @Positive BigDecimal preco, Boolean aceitaCartao, Integer maxParcelas)`
  - `CobrancaEventoService.montarOpcoes(BigDecimal valorEvento, boolean aceitaCartao, int maxParcelas, UUID igrejaId) -> OpcoesPagamentoResponse`
  - `GET /cobrancas/{id}/opcoes-pagamento -> OpcoesPagamentoResponse`
  - `POST /eventos/simular-pagamento -> OpcoesPagamentoResponse`

- [ ] **Step 1: Criar os DTOs**

`OpcoesPagamentoResponse.java`:

```java
package com.domus.api.modules.pagamento.cobranca.DTOs;

import com.domus.api.modules.pagamento.MeioPagamento;
import java.math.BigDecimal;
import java.util.List;

/**
 * Opções que a tela de escolha de método do checkout renderiza. `valorEvento` é o alvo
 * (o que a igreja quer receber); cada opção traz o total já com a taxa do MP embutida
 * (gross-up) pra aquele meio/parcela.
 */
public record OpcoesPagamentoResponse(BigDecimal valorEvento, List<OpcaoPagamento> opcoes) {

    public record OpcaoPagamento(MeioPagamento meio, int parcelas,
                                 BigDecimal valorTotal, BigDecimal valorParcela, BigDecimal taxa) {}
}
```

`SimularPagamentoRequest.java`:

```java
package com.domus.api.modules.pagamento.DTOs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Corpo de POST /eventos/simular-pagamento — o form de evento pede as opções antes de salvar. */
public record SimularPagamentoRequest(
    @NotNull(message = "O valor é obrigatório.")
    @Positive(message = "O valor deve ser maior que zero.")
    BigDecimal preco,
    Boolean aceitaCartao,
    Integer maxParcelas
) {}
```

- [ ] **Step 2: `montarOpcoes` no `CobrancaEventoService`**

```java
    private final CalculadoraTaxaPagamento calculadora; // injetar no construtor

    public OpcoesPagamentoResponse montarOpcoes(BigDecimal valorEvento, boolean aceitaCartao,
                                                int maxParcelas, UUID igrejaId) {
        List<OpcoesPagamentoResponse.OpcaoPagamento> opcoes = new ArrayList<>();

        opcoes.add(opcao(igrejaId, valorEvento, MeioPagamento.PIX, 1));

        if (aceitaCartao) {
            int teto = Math.max(1, Math.min(maxParcelas, 12));
            for (int p = 1; p <= teto; p++) {
                opcoes.add(opcao(igrejaId, valorEvento, MeioPagamento.CARTAO, p));
            }
        }
        return new OpcoesPagamentoResponse(valorEvento, opcoes);
    }

    private OpcoesPagamentoResponse.OpcaoPagamento opcao(UUID igrejaId, BigDecimal alvo,
                                                         MeioPagamento meio, int parcelas) {
        BigDecimal total = calculadora.valorACobrar(igrejaId, alvo, meio, parcelas);
        BigDecimal parcela = total.divide(BigDecimal.valueOf(parcelas), 2, RoundingMode.HALF_UP);
        return new OpcoesPagamentoResponse.OpcaoPagamento(
            meio, parcelas, total, parcela, calculadora.taxaEmReais(alvo, total));
    }
```

- [ ] **Step 3: Endpoint no `CobrancaController`**

```java
    /**
     * Opções de pagamento (Pix + faixas de cartão) pra a tela de escolha de método do
     * checkout. Sem autenticação, mesmo motivo do resto da classe (posse do UUID da
     * cobrança). O valor de cada opção é recalculado no back — o front só renderiza.
     */
    @GetMapping("/{id}/opcoes-pagamento")
    public OpcoesPagamentoResponse opcoesPagamento(@PathVariable UUID id) {
        var cobranca = cobrancaRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Cobrança não encontrada."));
        var evento = eventoRepository.findById(cobranca.getEventoId())
            .orElseThrow(() -> new ResourceNotFoundException("Evento da cobrança não encontrado."));
        return service.montarOpcoes(cobranca.getValor(), evento.isPagamentoAceitaCartao(),
            evento.getPagamentoMaxParcelas(), cobranca.getIgrejaId());
    }
```

Conferir em `SecurityConfig` que `/cobrancas/**` está `permitAll` (o Javadoc da classe diz que sim — a rota nova cai no mesmo matcher). Se houver matcher específico por sub-path, adicionar `GET /cobrancas/*/opcoes-pagamento`.

- [ ] **Step 4: Endpoint de simulação no `EventoController` + service**

`EventoService.simularPagamento`:

```java
    public OpcoesPagamentoResponse simularPagamento(UUID igrejaId, SimularPagamentoRequest req) {
        boolean aceitaCartao = Boolean.TRUE.equals(req.aceitaCartao());
        int maxParcelas = aceitaCartao && req.maxParcelas() != null ? req.maxParcelas() : 1;
        return cobrancaEventoService.montarOpcoes(req.preco(), aceitaCartao, maxParcelas, igrejaId);
    }
```

(Injetar `CobrancaEventoService` no `EventoService` — se criar ciclo de dependência, mover `montarOpcoes` + `CalculadoraTaxaPagamento` para um `OpcoesPagamentoService` próprio em `modules/pagamento` que os dois consomem. Preferir isso se o ciclo aparecer.)

`EventoController`:

```java
    @PostMapping("/simular-pagamento")
    public OpcoesPagamentoResponse simularPagamento(@Valid @RequestBody SimularPagamentoRequest req) {
        return eventoService.simularPagamento(usuarioAutenticado.getIgrejaId(), req);
    }
```

- [ ] **Step 5: Teste de controller**

`OpcoesPagamentoControllerTest.java` — `@SpringBootTest @AutoConfigureMockMvc @Transactional`, `AutenticacaoTestSupport` (fixtures inline de Igreja/Evento/Cobranca):

```java
    @Test
    void eventoSoPix_devolveApenasOpcaoPix() throws Exception {
        // evento pago R$ 100, pagamentoAceitaCartao=false; cobranca PENDENTE
        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valorEvento").value(100.00))
            .andExpect(jsonPath("$.opcoes.length()").value(1))
            .andExpect(jsonPath("$.opcoes[0].meio").value("PIX"))
            .andExpect(jsonPath("$.opcoes[0].valorTotal").value(101.00));
    }

    @Test
    void eventoComCartaoAte3x_devolvePixMais3FaixasDeCartao() throws Exception {
        // evento pago R$ 100, pagamentoAceitaCartao=true, pagamentoMaxParcelas=3
        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opcoes.length()").value(4)) // PIX + 1x + 2x + 3x
            .andExpect(jsonPath("$.opcoes[1].meio").value("CARTAO"))
            .andExpect(jsonPath("$.opcoes[1].parcelas").value(1))
            .andExpect(jsonPath("$.opcoes[3].parcelas").value(3));
    }

    @Test
    void semAutenticacao_endpointResponde() throws Exception {
        // rota publica por posse do UUID — nao exige cookie
        mockMvc.perform(get("/cobrancas/{id}/opcoes-pagamento", cobrancaId))
            .andExpect(status().isOk());
    }

    @Test
    void simularPagamento_exigeAutenticacao() throws Exception {
        mockMvc.perform(post("/eventos/simular-pagamento")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preco\":100.00,\"aceitaCartao\":true,\"maxParcelas\":6}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void simularPagamento_autenticado_devolveOpcoes() throws Exception {
        mockMvc.perform(autenticado(post("/eventos/simular-pagamento"), usuarioAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"preco\":100.00,\"aceitaCartao\":true,\"maxParcelas\":6}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.opcoes.length()").value(7)); // PIX + 1x..6x
    }
```

- [ ] **Step 6: Rodar**

Run: `mvn -q test -Dtest=OpcoesPagamentoControllerTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/ \
        src/main/java/com/domus/api/modules/evento/EventoController.java \
        src/main/java/com/domus/api/modules/evento/EventoService.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/OpcoesPagamentoControllerTest.java
git commit -m "feat(pagamento): endpoints de opcoes de pagamento (checkout) e simulacao (cadastro)"
```

---

## Task 6: `POST /cobrancas/{id}/pagar` recalcula o valor no back

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/PagarCobrancaRequest.java`
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java` — `registrarValorCobrado`
- Modify: `src/main/java/com/domus/api/modules/pagamento/MercadoPagoClient.java` — `criarPagamentoComToken` recebe `valorACobrar`
- Modify: `src/main/java/com/domus/api/modules/pagamento/MercadoPagoApi.java` — `criarPagamentoTokenizado` já recebe `valor`; nada
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java` — `pagar`
- Test: `src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java` (existente — estender)

**Interfaces:**
- Consumes: `CalculadoraTaxaPagamento` (Task 2), `MeioPagamento` (Task 1).
- Produces:
  - `PagarCobrancaRequest` ganha `@NotNull MeioPagamento meio`, `@NotNull @Min(1) @Max(12) Integer parcelas`.
  - `CobrancaEvento.registrarValorCobrado(BigDecimal)` + `getValorCobrado()`.
  - `MercadoPagoClient.criarPagamentoComToken(UUID igrejaId, CobrancaEvento cobranca, BigDecimal valorACobrar, String token, String paymentMethodId, Integer installments, String payerEmail, String issuerId)`.

- [ ] **Step 1: Estender `PagarCobrancaRequest`**

```java
    @jakarta.validation.constraints.NotNull(message = "meio de pagamento é obrigatório")
    com.domus.api.modules.pagamento.MeioPagamento meio,

    @jakarta.validation.constraints.NotNull(message = "número de parcelas é obrigatório")
    @jakarta.validation.constraints.Min(value = 1, message = "número de parcelas inválido")
    @jakarta.validation.constraints.Max(value = 12, message = "número de parcelas inválido")
    Integer parcelas
```

(`installments` continua existindo — vem do Brick; `parcelas` é a fonte da verdade validada.)

- [ ] **Step 2: `CobrancaEvento.registrarValorCobrado` + getter**

```java
    @Column(name = "valor_cobrado")
    private BigDecimal valorCobrado;

    /** Valor efetivamente cobrado do pagador (alvo + taxa, com gross-up), gravado no
     *  POST /pagar. `valor` continua sendo o alvo. */
    public void registrarValorCobrado(BigDecimal valorCobrado) { this.valorCobrado = valorCobrado; }

    public BigDecimal getValorCobrado() { return valorCobrado; }
```

- [ ] **Step 3: `MercadoPagoClient.criarPagamentoComToken` recebe o valor**

Trocar a assinatura para receber `BigDecimal valorACobrar` e repassar pro `api.criarPagamentoTokenizado(accessToken, cobranca.getId().toString(), valorACobrar, token, ...)` em vez de `cobranca.getValor()`.

- [ ] **Step 4: Escrever os testes primeiro**

Em `CobrancaControllerTest.java`:

```java
    @Test
    void pagar_recalculaValorNoBack_ignorandoQualquerValorDoFront() throws Exception {
        // evento pago R$ 100, cobranca PENDENTE. Paga CARTAO 1x.
        // MercadoPagoClient é mockado — captura o valorACobrar passado.
        mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"tok","paymentMethodId":"visa","installments":1,
                     "payerEmail":"p@x.com","issuerId":"1","meio":"CARTAO","parcelas":1}
                    """))
            .andExpect(status().isOk());

        ArgumentCaptor<BigDecimal> valor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mercadoPagoClient).criarPagamentoComToken(any(), any(), valor.capture(),
            any(), any(), any(), any(), any());
        assertThat(valor.getValue()).isEqualByComparingTo("104.71");

        var cobranca = cobrancaRepository.findById(cobrancaId).orElseThrow();
        assertThat(cobranca.getValorCobrado()).isEqualByComparingTo("104.71");
        assertThat(cobranca.getValor()).isEqualByComparingTo("100.00"); // alvo intacto
    }

    @Test
    void pagar_pixComParcelasMaiorQue1_recusa() throws Exception {
        mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"paymentMethodId":"pix","payerEmail":"p@x.com","meio":"PIX","parcelas":2}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("PIX_NAO_PARCELA"));
    }

    @Test
    void pagar_cartaoEmEventoQueSoAceitaPix_recusa() throws Exception {
        // cobrancaId2 pertence a evento com pagamentoAceitaCartao=false
        mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaIdSoPix)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"t","paymentMethodId":"visa","installments":1,
                     "payerEmail":"p@x.com","meio":"CARTAO","parcelas":1}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("CARTAO_NAO_ACEITO"));
    }

    @Test
    void pagar_parcelasAcimaDoTetoDoEvento_recusa() throws Exception {
        // evento com pagamentoMaxParcelas=3
        mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"token":"t","paymentMethodId":"visa","installments":6,
                     "payerEmail":"p@x.com","meio":"CARTAO","parcelas":6}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("PARCELAS_ACIMA_DO_TETO"));
    }

    @Test
    void pagar_semMeio_recusaComoValidacao() throws Exception {
        mockMvc.perform(post("/cobrancas/{id}/pagar", cobrancaId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"pix\",\"payerEmail\":\"p@x.com\",\"parcelas\":1}"))
            .andExpect(status().isBadRequest());
    }
```

- [ ] **Step 5: Rodar e ver falhar**

Run: `mvn -q test -Dtest=CobrancaControllerTest`
Expected: FAIL.

- [ ] **Step 6: Implementar o recálculo no `CobrancaController.pagar`**

Depois das checagens de estado (`COBRANCA_NAO_PENDENTE` etc.) e do lock do evento, antes de chamar o MP:

```java
        if (request.meio() == MeioPagamento.CARTAO && !evento.isPagamentoAceitaCartao()) {
            throw new BusinessException("CARTAO_NAO_ACEITO",
                "Este evento aceita apenas Pix.");
        }
        if (request.meio() == MeioPagamento.CARTAO && request.parcelas() > evento.getPagamentoMaxParcelas()) {
            throw new BusinessException("PARCELAS_ACIMA_DO_TETO",
                "Este evento aceita no máximo " + evento.getPagamentoMaxParcelas() + "x.");
        }

        BigDecimal valorACobrar = calculadoraTaxaPagamento.valorACobrar(
            cobranca.getIgrejaId(), cobranca.getValor(), request.meio(), request.parcelas());
        cobranca.registrarValorCobrado(valorACobrar);

        var resultado = mercadoPagoClient.criarPagamentoComToken(
            cobranca.getIgrejaId(), cobranca, valorACobrar,
            request.token(), request.paymentMethodId(), request.installments(),
            request.payerEmail(), request.issuerId());
```

(`PIX_NAO_PARCELA` e `PARCELAS_INVALIDAS` já são lançados de dentro de `CalculadoraTaxaPagamento.valorACobrar` — não duplicar aqui.)

Injetar `CalculadoraTaxaPagamento` no construtor do controller.

- [ ] **Step 7: Rodar**

Run: `mvn -q test -Dtest=CobrancaControllerTest`
Expected: PASS. Rodar também `CalculadoraTaxaPagamentoTest` de novo (garantir que as mensagens batem com o `hasMessageContaining` dos testes).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/ \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java
git commit -m "feat(pagamento): POST /pagar recalcula o valor com gross-up por meio/parcela"
```

---

## Task 7: `MovimentacaoAutomaticaService` — dois lançamentos (bruto + taxa)

**Files:**
- Modify: `src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoAutomaticaService.java`
- Modify: `src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java` — `confirmarPagamento` recebe `InformacoesPagamento`; `registrarNoFinanceiro`
- Modify: `src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookController.java` — passa `informacoes` inteiro
- Modify: `src/main/java/com/domus/api/modules/pagamento/PagamentoPollingService.java` — passa `info` inteiro
- Test: `src/test/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoAutomaticaServiceTest.java`

**Interfaces:**
- Consumes: `InformacoesPagamento(externalReference, status, valorBruto, taxaMercadoPago, valorLiquido)` (Task 4).
- Produces:
  - `MovimentacaoAutomaticaService.registrarEntradaDeEvento(UUID igrejaId, BigDecimal valorBruto, BigDecimal taxaMp, String descricao, UUID pessoaId, String nomePagador)` — assinatura nova (ganha `taxaMp` na 2ª posição).
  - `MovimentacaoAutomaticaService.registrarSaidaDeEvento(UUID igrejaId, BigDecimal valorBruto, BigDecimal taxaDevolvida, String descricao, UUID pessoaId, String nomePagador)`.
  - `MercadoPagoWebhookService.confirmarPagamento(String cobrancaId, String mpPaymentId, InformacoesPagamento info)` — assinatura muda de `(…, String status)` para `(…, InformacoesPagamento info)`.
  - Categoria "Taxas de pagamento" (`TipoCategoria.SAIDA`), auto-criada; `NOMES_CATEGORIA_TAXA_ACEITOS = Set.of("taxa de pagamento", "taxas de pagamento")`.

- [ ] **Step 1: Escrever os testes primeiro**

`MovimentacaoAutomaticaServiceTest.java` (Mockito puro, estilo A — `mock()` no `@BeforeEach`):

```java
    @Test
    void pagamentoConfirmado_registraEntradaBrutaEmEventosESaidaDeTaxa() {
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("110.49"), new BigDecimal("10.49"),
            "Pagamento de inscrição — Acampamento (João)", pessoaId, "João");

        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());
        var salvos = mov.getAllValues();

        var entrada = salvos.stream().filter(m -> m.getTipo() == TipoMovimentacao.ENTRADA).findFirst().orElseThrow();
        assertThat(entrada.getValor()).isEqualByComparingTo("110.49");
        assertThat(entrada.getCategoria().getNome()).isEqualTo("Eventos");
        assertThat(entrada.getContribuintes()).hasSize(1);

        var saida = salvos.stream().filter(m -> m.getTipo() == TipoMovimentacao.SAIDA).findFirst().orElseThrow();
        assertThat(saida.getValor()).isEqualByComparingTo("10.49");
        assertThat(saida.getCategoria().getNome()).isEqualTo("Taxas de pagamento");
        assertThat(saida.getContribuintes()).isEmpty();
    }

    @Test
    void taxaZeroOuNula_naoRegistraSaidaDeTaxa() {
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("100.00"), null,
            "desc", pessoaId, "João");
        verify(movimentacaoRepository, times(1)).save(any()); // só a entrada
    }

    @Test
    void primeiraTaxa_criaCategoriaTaxasDePagamentoENotifica() {
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), argThat(s -> s.contains("taxas de pagamento"))))
            .thenReturn(List.of());
        // ... categoria "Eventos" existe
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("104.70"), new BigDecimal("4.70"),
            "desc", pessoaId, "João");

        ArgumentCaptor<CategoriaFinanceira> cat = ArgumentCaptor.forClass(CategoriaFinanceira.class);
        verify(categoriaRepository, atLeastOnce()).save(cat.capture());
        assertThat(cat.getAllValues()).anySatisfy(c -> {
            assertThat(c.getNome()).isEqualTo("Taxas de pagamento");
            assertThat(c.getTipo()).isEqualTo(TipoCategoria.SAIDA);
        });
        verify(notificacaoService, atLeastOnce()).criar(eq(TipoNotificacao.CATEGORIA_FINANCEIRA_AUTO_CRIADA),
            eq(igrejaId), any(), contains("Taxas de pagamento"), any());
    }

    @Test
    void categoriaTaxaJaExiste_toleraVariacaoDeNome() {
        var existente = CategoriaFinanceira.builder().nome("Taxa de Pagamento").tipo(TipoCategoria.SAIDA).build();
        when(categoriaRepository.buscarPorIgrejaENomeNormalizado(eq(igrejaId), argThat(s -> s.contains("taxa de pagamento"))))
            .thenReturn(List.of(existente));
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("104.70"), new BigDecimal("4.70"),
            "desc", pessoaId, "João");
        // não cria outra categoria de taxa
        verify(categoriaRepository, never()).save(argThat(c -> "Taxas de pagamento".equals(c.getNome())));
    }

    @Test
    void estorno_registraSaidaBrutaEmEventosEDevolucaoDeTaxa() {
        service.registrarSaidaDeEvento(igrejaId, new BigDecimal("110.49"), new BigDecimal("10.49"),
            "Reembolso — Acampamento (João)", pessoaId, "João");

        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());
        var saidaEventos = mov.getAllValues().stream()
            .filter(m -> m.getCategoria().getNome().equals("Eventos")).findFirst().orElseThrow();
        assertThat(saidaEventos.getTipo()).isEqualTo(TipoMovimentacao.SAIDA);
        assertThat(saidaEventos.getValor()).isEqualByComparingTo("110.49");
        var entradaTaxa = mov.getAllValues().stream()
            .filter(m -> m.getCategoria().getNome().equals("Taxas de pagamento")).findFirst().orElseThrow();
        assertThat(entradaTaxa.getTipo()).isEqualTo(TipoMovimentacao.ENTRADA);
        assertThat(entradaTaxa.getValor()).isEqualByComparingTo("10.49");
    }

    @Test
    void estornoSemTaxaDevolvida_soRegistraSaidaEmEventos() {
        service.registrarSaidaDeEvento(igrejaId, new BigDecimal("110.49"), BigDecimal.ZERO,
            "Reembolso", pessoaId, "João");
        verify(movimentacaoRepository, times(1)).save(any());
    }

    @Test
    void contribuinteSemCadastro_usaNomeExternoNaEntrada() {
        service.registrarEntradaDeEvento(igrejaId, new BigDecimal("101.00"), new BigDecimal("1.00"),
            "desc", null, "Convidado Zé");
        ArgumentCaptor<MovimentacaoFinanceira> mov = ArgumentCaptor.forClass(MovimentacaoFinanceira.class);
        verify(movimentacaoRepository, times(2)).save(mov.capture());
        var entrada = mov.getAllValues().stream()
            .filter(m -> m.getTipo() == TipoMovimentacao.ENTRADA).findFirst().orElseThrow();
        assertThat(entrada.getContribuintes().get(0).getNomeExterno()).isEqualTo("Convidado Zé");
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=MovimentacaoAutomaticaServiceTest`
Expected: FAIL — assinatura antiga (`registrarEntradaDeEvento` sem `taxaMp`).

- [ ] **Step 3: Refatorar `MovimentacaoAutomaticaService`**

- Adicionar constantes:

```java
    private static final String NOME_CATEGORIA_TAXA = "Taxas de pagamento";
    private static final Set<String> NOMES_CATEGORIA_TAXA_ACEITOS = Set.of("taxa de pagamento", "taxas de pagamento");
```

- Novas assinaturas (mantendo os métodos antigos removidos — todos os chamadores serão atualizados nas tasks 7/8):

```java
    @Transactional
    public void registrarEntradaDeEvento(UUID igrejaId, BigDecimal valorBruto, BigDecimal taxaMp,
                                          String descricao, UUID pessoaId, String nomePagador) {
        registrar(igrejaId, TipoMovimentacao.ENTRADA, valorBruto, descricao, pessoaId, nomePagador,
            buscarOuCriarCategoriaEventos(igrejaId));
        registrarTaxa(igrejaId, TipoMovimentacao.SAIDA, taxaMp, "Taxa Mercado Pago — " + semPrefixo(descricao));
    }

    @Transactional
    public void registrarSaidaDeEvento(UUID igrejaId, BigDecimal valorBruto, BigDecimal taxaDevolvida,
                                        String descricao, UUID pessoaId, String nomePagador) {
        registrar(igrejaId, TipoMovimentacao.SAIDA, valorBruto, descricao, pessoaId, nomePagador,
            buscarOuCriarCategoriaEventos(igrejaId));
        registrarTaxa(igrejaId, TipoMovimentacao.ENTRADA, taxaDevolvida,
            "Devolução de taxa — " + semPrefixo(descricao));
    }

    private void registrarTaxa(UUID igrejaId, TipoMovimentacao tipo, BigDecimal valor, String descricao) {
        if (valor == null || valor.signum() <= 0) return;
        CategoriaFinanceira categoria = buscarOuCriarCategoriaTaxa(igrejaId);
        MovimentacaoFinanceira mov = MovimentacaoFinanceira.builder()
            .igreja(igrejaRepository.getReferenceById(igrejaId))
            .categoria(categoria)
            .criadoPorTexto("Sistema (taxa de pagamento)")
            .tipo(tipo)
            .valor(valor)
            .dataMovimentacao(LocalDate.now())
            .descricao(descricao)
            .build();
        movimentacaoRepository.save(mov);
        outboxRegistrador.registrar(TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.CRIADO, mov.getId(), igrejaId);
        cacheEvictor.evictPorIgreja("movimentacoes", igrejaId);
    }
```

- Extrair `registrar(...)` para aceitar a categoria já resolvida (parâmetro novo), e generalizar `buscarOuCriarCategoria...` num método parametrizado `buscarOuCriarCategoria(igrejaId, nomePadrao, nomesAceitos, tipo)`, reusado pelos dois (`buscarOuCriarCategoriaEventos` continua chamando com `AMBOS`, `buscarOuCriarCategoriaTaxa` com `SAIDA`). Manter `notificarCategoriaCriada` recebendo o nome da categoria criada.
- `semPrefixo(descricao)`: helper que tira o prefixo `"Pagamento de inscrição — "` / `"Reembolso — "` pra sobrar `"<evento> (<pagador>)"` na descrição da taxa. Se não casar nenhum prefixo, usar a descrição inteira.

- [ ] **Step 4: Threading da fee info até o `registrarNoFinanceiro`**

Em `MercadoPagoWebhookService.confirmarPagamento`: trocar o parâmetro `String status` por `InformacoesPagamento info`; usar `info.status()` onde hoje usa `status`; passar `info` adiante a `registrarNoFinanceiro`.

```java
    private void registrarNoFinanceiro(CobrancaEvento cobranca, InscricaoEvento inscricao, InformacoesPagamento info) {
        try {
            Evento evento = eventoRepository.findById(cobranca.getEventoId()).orElse(null);
            if (evento == null) return;
            String nomePagador = resolverNomePagador(cobranca, inscricao);
            BigDecimal bruto = info.valorBruto() != null ? info.valorBruto()
                : (cobranca.getValorCobrado() != null ? cobranca.getValorCobrado() : cobranca.getValor());
            BigDecimal taxa = info.taxaMercadoPago() != null ? info.taxaMercadoPago() : BigDecimal.ZERO;
            movimentacaoAutomaticaService.registrarEntradaDeEvento(
                cobranca.getIgrejaId(), bruto, taxa,
                "Pagamento de inscrição — " + evento.getTitulo() + " (" + nomePagador + ")",
                cobranca.getPessoaId(), nomePagador);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar movimentação financeira do pagamento. cobrancaId={}", cobranca.getId(), e);
        }
    }
```

- Atualizar os 2 chamadores de `confirmarPagamento`:
  - `MercadoPagoWebhookController` (linha ~87): `service.confirmarPagamento(informacoes.externalReference(), dataId, informacoes)`.
  - `PagamentoPollingService` (linhas ~81 e ~102): `webhookService.confirmarPagamento(cobrancaId, mpPaymentId, info)`.
  - `CobrancaController.status` chama `pagamentoPollingService.reconferirAgora(...)` — esse método internamente chama `confirmarPagamento`; ajustar lá dentro também (`grep -n "confirmarPagamento" PagamentoPollingService.java`).

- [ ] **Step 5: Rodar**

Run: `mvn -q test -Dtest=MovimentacaoAutomaticaServiceTest,MercadoPagoWebhookServiceTest,PagamentoPollingServiceTest`
Expected: PASS. Ajustar os testes de webhook/polling existentes que passavam `status` string — agora montam um `InformacoesPagamento`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoAutomaticaService.java \
        src/main/java/com/domus/api/modules/pagamento/webhook/ \
        src/main/java/com/domus/api/modules/pagamento/PagamentoPollingService.java \
        src/test/java/com/domus/api/modules/financeiro/movimentacao/MovimentacaoAutomaticaServiceTest.java
git commit -m "feat(financeiro): pagamento de evento registra bruto + taxa do MP separados"
```

---

## Task 8: Estorno usa o valor bruto

**Files:**
- Modify: `src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java` — `valorRestanteParaEstornar`
- Modify: `src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java` — `registrarEstornoNoFinanceiro`, `aplicarMudancaValorPago`
- Test: `src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoTest.java` (criar se não existir)
- Test: `src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java` (existente — estender)

**Interfaces:**
- Consumes: `CobrancaEvento.getValorCobrado()` (Task 6), `MovimentacaoAutomaticaService.registrarSaidaDeEvento(igrejaId, bruto, taxaDevolvida, desc, pessoaId, nome)` (Task 7).
- Produces: `valorRestanteParaEstornar()` passa a devolver `(valorCobrado != null ? valorCobrado : valor).subtract(valorEstornado)`.

- [ ] **Step 1: Teste de `CobrancaEvento`**

```java
    @Test
    void valorRestanteParaEstornar_usaValorCobradoQuandoPresente() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        c.registrarValorCobrado(new BigDecimal("110.49"));
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("110.49");
    }

    @Test
    void valorRestanteParaEstornar_caiNoAlvoQuandoNuncaFoiCobrado() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("100.00");
    }

    @Test
    void valorRestanteParaEstornar_descontaEstornoParcialJaFeito() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        c.registrarValorCobrado(new BigDecimal("110.49"));
        c.registrarEstorno(new BigDecimal("10.49"));
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("100.00");
    }
```

- [ ] **Step 2: Rodar e ver falhar**

Run: `mvn -q test -Dtest=CobrancaEventoTest`
Expected: FAIL.

- [ ] **Step 3: Ajustar `valorRestanteParaEstornar`**

```java
    public BigDecimal valorRestanteParaEstornar() {
        BigDecimal base = valorCobrado != null ? valorCobrado : valor;
        BigDecimal restante = base.subtract(this.valorEstornado);
        return restante.signum() > 0 ? restante : BigDecimal.ZERO;
    }
```

E `registrarEstorno`: a checada `this.valorEstornado.compareTo(this.valor)` passa a comparar com a mesma base (`valorCobrado != null ? valorCobrado : valor`).

- [ ] **Step 4: `registrarEstornoNoFinanceiro` — dois lançamentos**

Em `InscricaoService.registrarEstornoNoFinanceiro(inscricao, valorReembolsado)`: o `valorReembolsado` já é o bruto (vem de `valorRestanteParaEstornar`). A taxa devolvida pelo MP: o `estornarParcial` do MP devolve proporcionalmente a taxa em estorno total dentro da janela. Buscar essa info do resultado do estorno **se disponível**; senão passar `BigDecimal.ZERO` (conservador — a igreja comeu a taxa). Para esta task, passar `BigDecimal.ZERO` como `taxaDevolvida` e deixar um `// TODO` referenciando a questão aberta 2 da spec **NÃO** — em vez disso: consultar `mercadoPagoClient.buscarInformacoesPagamento(igrejaId, mpPaymentId)` após o estorno e usar `bruto - liquido` do refund se o MP expuser; se o record não trouxer, `ZERO`. Manter simples:

```java
    private void registrarEstornoNoFinanceiro(InscricaoEvento inscricao, BigDecimal valorBrutoReembolsado,
                                              BigDecimal taxaDevolvida) {
        try {
            String nomePagador = inscricao.getPessoa() != null
                ? inscricao.getPessoa().getNome() : inscricao.getNomeConvidado();
            movimentacaoAutomaticaService.registrarSaidaDeEvento(
                inscricao.getIgreja().getId(), valorBrutoReembolsado, taxaDevolvida,
                "Reembolso — " + inscricao.getEvento().getTitulo() + " (" + nomePagador + ")",
                inscricao.getPessoa() != null ? inscricao.getPessoa().getId() : null, nomePagador);
        } catch (RuntimeException e) {
            log.error("Falha ao registrar estorno na movimentação financeira. inscricaoId={}", inscricao.getId(), e);
        }
    }
```

Atualizar os chamadores (`grep -n "registrarEstornoNoFinanceiro" InscricaoService.java` — há 3: `estornarCobrancasDaInscricao`, `aplicarEventoVirouGratuito`, `tentarEstornoNovamente`). Todos passam `taxaDevolvida = BigDecimal.ZERO` por ora (decisão consciente: estorno total dentro da janela devolve a taxa, mas rastrear isso com precisão exige ler o refund do MP — fora do escopo desta task; a SAÍDA bruta já deixa o financeiro correto quanto ao dinheiro que saiu). Documentar em uma linha no Javadoc de `registrarEstornoNoFinanceiro`.

- [ ] **Step 5: `aplicarMudancaValorPago` — gross-up proporcional**

Em `aplicarMudancaValorPago(eventoId, precoAntigo, precoNovo, ...)`: para cobranças **PAGAS**, o estorno parcial hoje é `precoAntigo - precoNovo` (alvo). Passar a usar a razão de gross-up da própria cobrança:

```java
    // valorCobrado / valor = fator de gross-up efetivo daquela compra. Aplica o mesmo
    // fator ao novo alvo pra achar o novo bruto e estornar só a diferença de bruto.
    BigDecimal fator = cobranca.getValorCobrado() != null && cobranca.getValor().signum() > 0
        ? cobranca.getValorCobrado().divide(cobranca.getValor(), MathContext.DECIMAL64)
        : BigDecimal.ONE;
    BigDecimal novoBruto = precoNovo.multiply(fator).setScale(2, RoundingMode.CEILING);
    BigDecimal diferencaBruto = cobranca.getValorCobrado() != null
        ? cobranca.getValorCobrado().subtract(novoBruto)
        : precoAntigo.subtract(precoNovo);
    // estorna diferencaBruto (limitado a valorRestanteParaEstornar), atualiza cobranca.valor = precoNovo
```

Manter a atualização de `cobranca.atualizarValor(precoNovo)` (o alvo). Para cobranças **PENDENTES**, só `atualizarValor(precoNovo)` como hoje (o `valorCobrado` ainda é null; será calculado no `/pagar`).

- [ ] **Step 6: Estender `InscricaoServiceTest`**

```java
    @Test
    void cancelamento_estornaOValorBrutoCobrado_naoOAlvo() {
        // cobranca PAGA: valor=100, valorCobrado=110.49
        // ... cancelar inscrição
        verify(mercadoPagoClient).estornarParcial(any(), any(), argThat(v -> v.compareTo(new BigDecimal("110.49")) == 0));
    }

    @Test
    void cancelamento_registraSaidaBrutaNoFinanceiro() {
        // ...
        verify(movimentacaoAutomaticaService).registrarSaidaDeEvento(
            any(), argThat(v -> v.compareTo(new BigDecimal("110.49")) == 0), any(), any(), any(), any());
    }
```

- [ ] **Step 7: Rodar**

Run: `mvn -q test -Dtest=CobrancaEventoTest,InscricaoServiceTest`
Expected: PASS. Rodar a suíte inteira de pagamento: `mvn -q test -Dtest='*Cobranca*,*Pagamento*,*Inscricao*,*MovimentacaoAutomatica*'`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java \
        src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoTest.java \
        src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(pagamento): estorno opera sobre o valor bruto cobrado (com taxa)"
```

---

## Task 9: Front — tipos, endpoints, services

**Files:**
- Modify: `src/types/api.types.ts`
- Modify: `src/lib/endpoints.ts`
- Modify: `src/services/cobranca.service.ts`
- Modify: `src/services/evento.service.ts` (ou o arquivo de service de evento — `grep -rl "eventos" src/services`)

**Interfaces:**
- Produces (TypeScript):
  - `type MeioPagamento = 'PIX' | 'CARTAO'`
  - `interface OpcaoPagamento { meio: MeioPagamento; parcelas: number; valorTotal: number; valorParcela: number; taxa: number }`
  - `interface OpcoesPagamentoResponse { valorEvento: number; opcoes: OpcaoPagamento[] }`
  - `Evento` ganha `pagamentoAceitaCartao: boolean`, `pagamentoMaxParcelas: number`
  - `cobrancaService.opcoesPagamento(cobrancaId: string): Promise<OpcoesPagamentoResponse>`
  - `eventoService.simularPagamento(body: { preco: number; aceitaCartao: boolean; maxParcelas: number }): Promise<OpcoesPagamentoResponse>`
  - `PagarCobrancaRequest` ganha `meio: MeioPagamento`, `parcelas: number`

- [ ] **Step 1: Tipos**

Em `api.types.ts` adicionar os tipos acima; adicionar os 2 campos em `Evento`.

- [ ] **Step 2: Endpoints**

```ts
  cobrancas: {
    // ...
    OPCOES_PAGAMENTO: (id: string) => `/cobrancas/${id}/opcoes-pagamento`,
  },
  eventos: {
    // ...
    SIMULAR_PAGAMENTO: () => `/eventos/simular-pagamento`,
  },
```

- [ ] **Step 3: `cobrancaService.opcoesPagamento` + `PagarCobrancaRequest`**

```ts
export interface PagarCobrancaRequest {
  token: string | null
  paymentMethodId: string
  installments: number | null
  payerEmail: string
  issuerId: string | null
  meio: MeioPagamento
  parcelas: number
}

  opcoesPagamento: (cobrancaId: string): Promise<OpcoesPagamentoResponse> =>
    api.get<OpcoesPagamentoResponse>(Endpoints.cobrancas.OPCOES_PAGAMENTO(cobrancaId)).then((r) => r.data),
```

- [ ] **Step 4: `eventoService.simularPagamento`**

```ts
  simularPagamento: (body: { preco: number; aceitaCartao: boolean; maxParcelas: number }): Promise<OpcoesPagamentoResponse> =>
    api.post<OpcoesPagamentoResponse>(Endpoints.eventos.SIMULAR_PAGAMENTO(), body).then((r) => r.data),
```

- [ ] **Step 5: Typecheck**

Run: `cd frontend && npx tsc --noEmit`
Expected: erros só onde `PaymentBrickCheckout` monta o `pagar` sem `meio`/`parcelas` — resolvidos na Task 11.

- [ ] **Step 6: Commit**

```bash
cd frontend && git add src/types/api.types.ts src/lib/endpoints.ts src/services/
git commit -m "feat(front): tipos e services de opcoes de pagamento e simulacao"
```

---

## Task 10: Front — `<ResumoTaxaPagamento>` no formulário de evento

**Files:**
- Create: `src/components/module/eventos/ResumoTaxaPagamento.tsx` + `.module.css`
- Modify: `src/hooks/evento/useEventoForm.ts`
- Modify: `src/components/module/eventos/EventoForm.tsx`

**Interfaces:**
- Consumes: `eventoService.simularPagamento` (Task 9), `<Colapsavel>` (`components/common/Transicao/`).
- Produces: campos no schema/estado do form: `pagamentoAceitaCartao: boolean`, `pagamentoMaxParcelas: number`; enviados no payload de criar/editar evento.

- [ ] **Step 1: `useEventoForm` — schema + defaults + payload**

- No `eventoSchema` (Zod), adicionar `pagamentoAceitaCartao: z.boolean().default(false)` e `pagamentoMaxParcelas: z.number().int().min(1).max(12).default(1)`.
- `defaultValues`: `pagamentoAceitaCartao: false`, `pagamentoMaxParcelas: 1`.
- Ao popular de `eventoInicial` (edição): `pagamentoAceitaCartao: eventoInicial.pagamentoAceitaCartao ?? false`, `pagamentoMaxParcelas: eventoInicial.pagamentoMaxParcelas ?? 1`.
- No mapeamento pro payload (perto de `preco:`): só manda os campos quando `requerInscricao && tipoInscricao === 'PAGO'`; senão `pagamentoAceitaCartao: false, pagamentoMaxParcelas: 1`.

- [ ] **Step 2: `<ResumoTaxaPagamento>`**

```tsx
'use client'
import { useEffect, useState } from 'react'
import { eventoService } from '@/services/evento.service'
import type { OpcoesPagamentoResponse } from '@/types/api.types'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './ResumoTaxaPagamento.module.css'

interface Props {
  preco: number | undefined
  aceitaCartao: boolean
  maxParcelas: number
}

/** Resumo estilo e-commerce mostrado ao GESTOR no cadastro de evento pago: quanto o
 *  pagador vai pagar em cada meio (a taxa do Mercado Pago é repassada via gross-up). */
export function ResumoTaxaPagamento({ preco, aceitaCartao, maxParcelas }: Props) {
  const [dados, setDados] = useState<OpcoesPagamentoResponse | null>(null)
  const [erro, setErro] = useState(false)

  useEffect(() => {
    if (!preco || preco <= 0) { setDados(null); return }
    let vivo = true
    const t = setTimeout(() => {
      eventoService.simularPagamento({ preco, aceitaCartao, maxParcelas })
        .then((r) => { if (vivo) { setDados(r); setErro(false) } })
        .catch(() => { if (vivo) setErro(true) })
    }, 400) // debounce enquanto digita o preço
    return () => { vivo = false; clearTimeout(t) }
  }, [preco, aceitaCartao, maxParcelas])

  if (!preco || preco <= 0) return null
  if (erro) return <p className={styles.erro}>Não foi possível calcular os valores agora.</p>
  if (!dados) return <p className={styles.carregando}>Calculando…</p>

  const pix = dados.opcoes.find((o) => o.meio === 'PIX')
  const cartao = dados.opcoes.filter((o) => o.meio === 'CARTAO')

  return (
    <div className={styles.painel}>
      <p className={styles.titulo}>O pagador vai pagar</p>
      <ul className={styles.linhas}>
        {pix && (
          <li><span>Pix</span><span>{formatarMoeda(pix.valorTotal)}</span></li>
        )}
        {cartao.map((o) => (
          <li key={o.parcelas}>
            <span>{o.parcelas === 1 ? 'Cartão à vista' : `Cartão em ${o.parcelas}x`}</span>
            <span>
              {formatarMoeda(o.valorTotal)}
              {o.parcelas > 1 && <em className={styles.parcela}> ({formatarMoeda(o.valorParcela)}/mês)</em>}
            </span>
          </li>
        ))}
      </ul>
      <p className={styles.rodape}>A igreja recebe {formatarMoeda(dados.valorEvento)} em qualquer opção.</p>
    </div>
  )
}
```

CSS: usar `.card-painel` (classe global) como base do `.painel` — é bloco informativo, não clicável.

- [ ] **Step 3: Ligar no `EventoForm`**

Na seção de evento pago (onde hoje está o campo `preco`):
- Label do preço: "Quanto a igreja quer receber por inscrição" com `placeholder="R$ 100,00"`.
- Abaixo, um `<Colapsavel aberto={requerInscricao && tipoInscricao === 'PAGO'}>` contendo:
  - toggle "Aceitar cartão de crédito" (controla `pagamentoAceitaCartao`)
  - `<Colapsavel aberto={pagamentoAceitaCartao}>` com o `<select>` "Parcelar em até" (1x…12x → `pagamentoMaxParcelas`)
  - `<ResumoTaxaPagamento preco={precoNumerico} aceitaCartao={pagamentoAceitaCartao} maxParcelas={pagamentoMaxParcelas} />`
- Responsivo: grid colapsa pra 1 coluna no mobile; `min-width: 0` na cadeia.

- [ ] **Step 4: Validação manual**

Run: `cd frontend && npm run dev`
- Criar evento pago, valor R$ 100, sem cartão → resumo mostra só "Pix R$ 101,00" e "A igreja recebe R$ 100,00".
- Ligar cartão, teto 6x → resumo mostra Pix + 1x…6x com valores crescentes.
- Digitar o preço aos poucos → sem "pipoco", debounce segura.
- Viewport iPhone e Android: sem overflow horizontal, grid em 1 coluna.
- Evento gratuito → seção some.

- [ ] **Step 5: Commit**

```bash
cd frontend && git add src/components/module/eventos/ResumoTaxaPagamento.tsx \
  src/components/module/eventos/ResumoTaxaPagamento.module.css \
  src/hooks/evento/useEventoForm.ts src/components/module/eventos/EventoForm.tsx
git commit -m "feat(front): config de pagamento e resumo de taxa no formulario de evento"
```

---

## Task 11: Front — `<EscolhaMeioPagamento>` + `PaymentBrickCheckout` parametrizado

**Files:**
- Create: `src/components/module/pagamento/EscolhaMeioPagamento.tsx` + `.module.css`
- Modify: `src/components/module/pagamento/PaymentBrickCheckout.tsx`
- Modify: `src/app/eventos/[id]/pagamento/[cobrancaId]/page.tsx`
- Modify: `src/app/cobranca/[token]/page.tsx`

**Interfaces:**
- Consumes: `cobrancaService.opcoesPagamento` (Task 9), `OpcaoPagamento` (Task 9).
- Produces:
  - `<EscolhaMeioPagamento cobrancaId valorEvento onEscolher={(o: OpcaoPagamento) => void} />`
  - `PaymentBrickCheckout` props novas: `meio: MeioPagamento`, `parcelas: number`, `valorTotal: number` (substitui/soma ao `valor` atual — passa a ser o total da opção escolhida). `customization` restringe ao `meio`; `pagar(...)` manda `meio` e `parcelas`.

- [ ] **Step 1: `<EscolhaMeioPagamento>`**

```tsx
'use client'
import { useQuery } from '@tanstack/react-query'
import { cobrancaService } from '@/services/cobranca.service'
import type { OpcaoPagamento } from '@/types/api.types'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { Loader } from '@/components/common/Loader/Loader'
import styles from './EscolhaMeioPagamento.module.css'

interface Props {
  cobrancaId: string
  onEscolher: (opcao: OpcaoPagamento) => void
}

export function EscolhaMeioPagamento({ cobrancaId, onEscolher }: Props) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['opcoes-pagamento', cobrancaId],
    queryFn: () => cobrancaService.opcoesPagamento(cobrancaId),
    staleTime: 5 * 60_000,
  })

  if (isLoading) return <div className={styles.centro}><Loader variant="circular" size="lg" /></div>
  if (isError || !data) return <p className={styles.erro}>Não foi possível carregar as formas de pagamento.</p>

  const pix = data.opcoes.filter((o) => o.meio === 'PIX')
  const cartao = data.opcoes.filter((o) => o.meio === 'CARTAO')

  return (
    <div className={styles.wrapper}>
      <p className={styles.valorEvento}>Valor da inscrição: <strong>{formatarMoeda(data.valorEvento)}</strong></p>
      <p className={styles.aviso}>O total já inclui a taxa de pagamento, que varia conforme a forma escolhida.</p>

      <div className={styles.grupo}>
        <span className={styles.grupoTitulo}>Pix</span>
        {pix.map((o) => (
          <button key="pix" type="button" className={styles.opcao} onClick={() => onEscolher(o)}>
            <span>Pix</span>
            <span className={styles.total}>{formatarMoeda(o.valorTotal)}</span>
          </button>
        ))}
      </div>

      {cartao.length > 0 && (
        <div className={styles.grupo}>
          <span className={styles.grupoTitulo}>Cartão de crédito</span>
          {cartao.map((o) => (
            <button key={o.parcelas} type="button" className={styles.opcao} onClick={() => onEscolher(o)}>
              <span>{o.parcelas === 1 ? 'À vista' : `${o.parcelas}x de ${formatarMoeda(o.valorParcela)}`}</span>
              <span className={styles.total}>{formatarMoeda(o.valorTotal)}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
```

CSS: botões grandes, alvo de toque ≥44px, `:active { transform: scale(0.98) }` com `@media (prefers-reduced-motion: reduce)` zerando; responsivo (largura total no mobile).

- [ ] **Step 2: `PaymentBrickCheckout` — props novas**

- `Props`: adicionar `meio: MeioPagamento`, `parcelas: number`, `valorTotal: number`. Manter `valor` como está? Não — renomear o uso interno: `initialization = useMemo(() => ({ amount: valorTotal }), [valorTotal])`.
- `customization`:

```tsx
const customization = useMemo(() => (
  meio === 'PIX'
    ? { paymentMethods: { bankTransfer: 'all' as const } }
    : { paymentMethods: { creditCard: 'all' as const, maxInstallments: parcelas, minInstallments: parcelas } }
), [meio, parcelas])
```

(Travar `min`/`maxInstallments` no valor escolhido — a pessoa já escolheu as parcelas na tela anterior.)
- No `cobrancaService.pagar(...)`: adicionar `meio, parcelas` ao corpo.

- [ ] **Step 3: Ligar na página `eventos/[id]/pagamento/[cobrancaId]/page.tsx`**

No branch `{!resultadoEfetivo && !indisponivel && (...)}`: substituir o bloco atual (`<div card valor>` + `<PaymentBrickCheckout>`) por um estado local `const [opcao, setOpcao] = useState<OpcaoPagamento | null>(null)`:
- `opcao === null` → `<EscolhaMeioPagamento cobrancaId={cobranca.id} onEscolher={setOpcao} />`
- `opcao !== null` → cabeçalho com "voltar" (`setOpcao(null)`) + resumo da opção escolhida + `<PaymentBrickCheckout key={cobranca.id + opcao.meio + opcao.parcelas} cobrancaId={cobranca.id} meio={opcao.meio} parcelas={opcao.parcelas} valorTotal={opcao.valorTotal} expiraEm={cobranca.expiraEm} onPagamentoCriado={...} onCobrancaIndisponivel={setIndisponivel} />`

- [ ] **Step 4: Ligar na página `cobranca/[token]/page.tsx`**

Mesmo padrão. (Ver o arquivo — provavelmente tem uma estrutura mais enxuta que a de `[cobrancaId]`.)

- [ ] **Step 5: Typecheck + validação manual**

Run: `cd frontend && npx tsc --noEmit` → limpo.
Run: `npm run dev` — com um evento pago de teste (sandbox MP):
- Fluxo titular: inscrever → tela de escolha → escolher Pix → Brick só Pix, valor = total Pix → pagar → QR.
- Voltar, escolher Cartão 3x → Brick trava em 3x, valor = total 3x.
- Fluxo link público (`/cobranca/[token]`): mesma tela de escolha.
- Recusa: cartão de teste recusado → volta pro Brick (não pro seletor).
- Viewport mobile.

- [ ] **Step 6: Commit**

```bash
cd frontend && git add src/components/module/pagamento/ \
  "src/app/eventos/[id]/pagamento/[cobrancaId]/page.tsx" "src/app/cobranca/[token]/page.tsx"
git commit -m "feat(front): tela de escolha de meio/parcelas antes do Payment Brick"
```

---

## Task 12: Front — nota de taxa na página do evento + diagrama ER

**Files:**
- Modify: `src/components/module/eventos/DrawerDetalheEvento.tsx`
- Modify: componente do card de evento e/ou página de inscrição que mostra o preço (`grep -rl "preco" src/components/module/eventos src/app/eventos`)
- Modify: `backend/api/CLAUDE.md` — diagrama ER

**Interfaces:** nenhuma nova.

- [ ] **Step 1: Nota discreta onde o preço do evento aparece**

Onde o preço do evento pago é exibido (drawer de detalhe, card, tela de inscrição), abaixo do valor:

```tsx
{evento.preco != null && (
  <p className={styles.notaTaxa}>
    Taxas de pagamento são aplicadas no checkout, conforme a forma escolhida.
  </p>
)}
```

Texto pequeno, cor secundária. Não repetir em todo lugar — só onde a pessoa decide se inscrever.

- [ ] **Step 2: Atualizar o diagrama ER do `CLAUDE.md`**

No bloco `EVENTO { ... }` adicionar:

```
        boolean   pagamento_aceita_cartao "V40 - Pix sempre aceito em evento pago; cartão opt-in"
        smallint  pagamento_max_parcelas "V40 - 1..12, CHECK; teto de parcelas do cartão"
```

No bloco `COBRANCA_EVENTO { ... }`:

```
        numeric   valor_cobrado "V40 - alvo + taxa (gross-up); NULL até a 1ª tentativa. `valor` continua sendo o alvo"
```

No bloco `CONTA_PAGAMENTO_IGREJA { ... }`:

```
        numeric   taxa_pix_percent "V40 - taxa negociada da igreja; NULL = usa default do back"
        numeric   taxa_cartao_avista_percent "V40"
        numeric   taxa_cartao_parcela_adicional_percent "V40"
```

Atualizar "Estado atual: **V39**" → **V40** e a nota de texto sobre pagamento de evento (V29-V32) mencionando que a taxa agora é repassada ao pagador (gross-up) e registrada como SAÍDA em "Taxas de pagamento".

- [ ] **Step 3: Validação manual**

`npm run dev` — abrir detalhe de um evento pago, ver a nota; evento gratuito não mostra.

- [ ] **Step 4: Commit**

```bash
cd frontend && git add src/components/module/eventos/ src/app/eventos/
git commit -m "feat(front): nota de taxa de pagamento no detalhe do evento pago"
cd ../backend/api && git add CLAUDE.md
git commit -m "docs: diagrama ER V40 (config de pagamento e taxa por evento)"
```

---

## Task 13: Verificação de ponta a ponta + suíte completa

**Files:** nenhum (só verificação).

- [ ] **Step 1: Suíte de back completa**

Run: `cd backend/api && mvn -q test`
Expected: BUILD SUCCESS. Se algum teste antigo de webhook/polling/inscrição quebrou pela mudança de assinatura de `confirmarPagamento`/`registrarEntradaDeEvento`/`registrarEstornoNoFinanceiro`, corrigir o **teste** para a assinatura nova (não enfraquecer asserção) e re-rodar.

- [ ] **Step 2: Typecheck + build de front**

Run: `cd frontend && npx tsc --noEmit && npm run build`
Expected: sem erro.

- [ ] **Step 3: Teste manual do fluxo pago (sandbox Mercado Pago)**

Com credenciais de teste do MP configuradas:
1. Criar evento pago R$ 10, cartão até 3x.
2. Conferir o resumo no cadastro.
3. Inscrever-se; na tela de escolha, pagar com cartão de teste aprovado 2x.
4. Conferir no financeiro: ENTRADA em "Eventos" = valor total cobrado; SAÍDA em "Taxas de pagamento" = diferença; categoria "Taxas de pagamento" criada + notificação.
5. Cancelar a inscrição; conferir SAÍDA bruta em "Eventos".
6. Repetir o fluxo pagando com Pix (cartão de teste "CONT" pra pending, ou Pix de teste).
7. Link compartilhável: gerar, abrir em aba anônima, pagar — mesma tela de escolha.

- [ ] **Step 4: Conferir as questões abertas da spec**

- **`net_received_amount` vs `bruto − Σ fee_details`:** no passo 3 acima, logar os dois no `MercadoPagoApi` e confirmar que `taxaMercadoPago = bruto − liquido` bate com o que o painel do MP mostra. Se divergir, ajustar (a fonte da verdade é `bruto − net_received_amount`).
- **Arredondamento de parcela do MP:** conferir se a soma das parcelas que o MP cobra bate com o `valorTotal` enviado. Se o MP arredondar diferente, o `valorTotal` (o que mandamos em `transactionAmount`) manda; o `valorParcela` do display é aproximado — deixar claro no texto ("2x de R$ X").

- [ ] **Step 5: Avisar o autor**

Não commitar mais nada. Apresentar o resumo do que foi entregue, os pontos das questões abertas com o que foi observado no sandbox, e esperar o autor testar antes de qualquer merge.

---

## Self-Review

**1. Spec coverage:**
- Repassar taxa pro pagador / gross-up → Task 2 (`CalculadoraTaxaPagamento`).
- Página do evento mostra preço-base + nota → Task 12.
- Checkout mostra preço + taxa → Task 11 (`EscolhaMeioPagamento`).
- Resumo e-commerce no cadastro → Task 10 (`ResumoTaxaPagamento`).
- Parcelamento sem juros, custo no preço → Task 2 (adicional por parcela) + Task 5 (faixas nas opções).
- Schema V40 (evento, conta_pagamento_igreja, cobranca_evento) → Task 1.
- Config de taxa padrão + override por igreja → Task 1 (config) + Task 2 (override, precedência campo a campo).
- `GET /cobrancas/{id}/opcoes-pagamento` → Task 5.
- `POST /eventos/simular-pagamento` (questão aberta a) → Task 5.
- `POST /pagar` recalcula no back + `valor_cobrado` → Task 6.
- Validações `PIX_NAO_PARCELA` / `CARTAO_NAO_ACEITO` / `PARCELAS_ACIMA_DO_TETO` → Task 2 + Task 6.
- `MercadoPagoApi` lê `fee_details` → Task 4.
- `MovimentacaoAutomaticaService` dois lançamentos + categoria "Taxas de pagamento" → Task 7.
- Estorno espelha + usa bruto → Task 8.
- `aplicarMudancaValorPago` com gross-up → Task 8 Step 5.
- Front: `EventoForm`, tela de checkout, `PaymentBrickCheckout`, nota no evento → Tasks 10–12.
- Testes (Calculadora, MovimentacaoAutomatica, CobrancaController, MercadoPagoApi, EventoService) → Tasks 2, 3, 5, 6, 7, 8.
- Diagrama ER V40 → Task 12.
- Fora de escopo (aprender taxa real; parcelamento com juros) → não implementado, correto.
- Sem compat retroativa → nenhuma migração de dado em V40, correto.

**2. Placeholder scan:** Task 8 Step 4 tinha um "TODO" — reescrito para decisão explícita (passar `BigDecimal.ZERO` como `taxaDevolvida`, documentado). Sem outros placeholders; todo passo de código tem bloco de código.

**3. Type consistency:**
- `MeioPagamento { PIX, CARTAO }` — consistente em Tasks 1, 2, 5, 6, 9, 11.
- `registrarEntradaDeEvento(igrejaId, valorBruto, taxaMp, descricao, pessoaId, nomePagador)` — Task 7 define, Task 7 Step 4 usa igual.
- `registrarSaidaDeEvento(igrejaId, valorBruto, taxaDevolvida, descricao, pessoaId, nomePagador)` — Task 7 define, Task 8 usa igual.
- `confirmarPagamento(String cobrancaId, String mpPaymentId, InformacoesPagamento info)` — Task 7 define, todos os chamadores listados no mesmo step.
- `InformacoesPagamento(externalReference, status, valorBruto, taxaMercadoPago, valorLiquido)` — Task 4 define, Task 7 consome os nomes exatos.
- `CalculadoraTaxaPagamento.valorACobrar(UUID, BigDecimal, MeioPagamento, int)` — Task 2 define, Tasks 5 e 6 chamam igual.
- `OpcoesPagamentoResponse(valorEvento, opcoes)` / `OpcaoPagamento(meio, parcelas, valorTotal, valorParcela, taxa)` — Task 5 (back) e Task 9 (front) espelhados.
- `criarPagamentoComToken(igrejaId, cobranca, valorACobrar, token, paymentMethodId, installments, payerEmail, issuerId)` — Task 6 define e usa.
- `cobranca.registrarValorCobrado(BigDecimal)` / `getValorCobrado()` — Task 6 define, Tasks 7 e 8 consomem.
- `valorRestanteParaEstornar()` — Task 8 redefine a implementação, mantém a assinatura.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-09-09-meio-pagamento-parcelamento-taxa-evento.md`. Two execution options:**

**1. Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints.

**Which approach?**
