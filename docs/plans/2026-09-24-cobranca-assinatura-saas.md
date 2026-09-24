# Implementation Plan: Cobrança de Assinatura SaaS do Domus (Planos, Checkout, Código de Convite & Trava de Recursos)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform Domus into a complete multi-tenant SaaS with public landing page, self-service onboarding (14-day trial with upfront credit card authorization via Mercado Pago Subscriptions API), congregation onboarding via invite codes, member limits, feature locking by tier, automatic lifecycle webhooks, e-mail notifications, and subscription enforcement.

**Architecture:** 
- Backend: Spring Boot 3.3 + JPA + Flyway migration (`V48__cobranca_assinatura_saas.sql`). `Igreja` entity gains `plano`, `statusAssinatura`, `trialExpiraEm`, `mpPreapprovalId`. New `CodigoConviteCongregacao` entity for daughter church onboarding. `MercadoPagoSubscriptionService` manages preapproval creation and webhooks. `PessoaService` enforces member limit (e.g. 60 for Básico). `PlanoResourceInterceptor` locks advanced features (Feed, Contas a pagar, Event payment) on Básico.
- Frontend: Next.js App Router. Public `/` Landing Page, `/planos` comparison table, `/cadastro` wizard integrating Mercado Pago Card Token SDK, `/cadastro/congregacao` (free invite-code onboarding), and `/configuracoes/assinatura` management screen.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, Flyway, Mercado Pago Subscriptions API (`/preapproval`), Next.js 14 (App Router), TypeScript, React Hook Form, Zod, Tailwind CSS.

**Spec:** `docs/specs/2026-09-24-cobranca-assinatura-saas-design.md`

## Global Constraints
- All backend entities must maintain multi-tenant isolation (`igreja_id`).
- Mercado Pago API requests must use existing credentials configuration from `MercadoPagoClient`.
- Credit card details must never touch backend servers directly; frontend must tokenize card numbers via Mercado Pago SDK.
- Orthographic correctness for prt-BR in UI strings and emails.

## Review Focus
- **Member limit enforcement**: Attempting to create a 61st active person in a Básico church must throw `PlanoLimitePessoasExcedidoException`.
- **Feature locking on Básico**: Requesting POST on `/api/postagens` or `/api/contas-a-pagar` for a Básico church must return HTTP 403 / 402 with clear upgrade message.
- **Daughter church invite code**: Daughter churches registering with valid invite code must create linked church (`matriz_id`) with status `ATIVA` without requiring credit card or charging.
- **Webhook idempotency**: Duplicate webhook notifications from Mercado Pago must not produce duplicate emails or corrupt status.

---

### Task 1: Database Migration, Core Domain Enums & Invite Code Entity

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V48__cobranca_assinatura_saas.sql`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/PlanoAssinatura.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/StatusAssinatura.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteCongregacao.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteRepository.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/Igreja.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/igreja/IgrejaEntityTest.java`

**Interfaces:**
- Consumes: Database schema V47
- Produces: `PlanoAssinatura`, `StatusAssinatura`, `CodigoConviteCongregacao`, updated `Igreja` entity attributes

- [ ] **Step 1: Write the failing test**
Create `backend/api/src/test/java/com/domus/api/modules/igreja/IgrejaEntityTest.java`:
```java
package com.domus.api.modules.igreja;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class IgrejaEntityTest {

    @Test
    void deveInicializarIgrejaComPlanoBasicoETrial() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste");
        igreja.setPlano(PlanoAssinatura.BASICO);
        igreja.setStatusAssinatura(StatusAssinatura.TRIAL);

        assertThat(igreja.getPlano()).isEqualTo(PlanoAssinatura.BASICO);
        assertThat(igreja.getPlano().getLimitePessoas()).isEqualTo(60);
        assertThat(igreja.getPlano().getLimiteCongregacoes()).isEqualTo(0);
        assertThat(igreja.getPlano().isBloqueiaAvancados()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=IgrejaEntityTest`
Expected: FAIL with compilation errors.

- [ ] **Step 3: Implement minimal code**
1. Create `PlanoAssinatura.java`:
```java
package com.domus.api.modules.igreja;

import java.math.BigDecimal;

public enum PlanoAssinatura {
    BASICO("Básico", 60, 0, new BigDecimal("79.00"), true),
    PRO("Pro", 300, 3, new BigDecimal("179.00"), false),
    PRO_PLUS("Pro+", 800, 5, new BigDecimal("299.00"), false),
    ENTERPRISE("Enterprise", 99999, 9999, new BigDecimal("499.00"), false);

    private final String nomeExibicao;
    private final int limitePessoas;
    private final int limiteCongregacoes;
    private final BigDecimal valorMensal;
    private final boolean bloqueiaAvancados;

    PlanoAssinatura(String nomeExibicao, int limitePessoas, int limiteCongregacoes, BigDecimal valorMensal, boolean bloqueiaAvancados) {
        this.nomeExibicao = nomeExibicao;
        this.limitePessoas = limitePessoas;
        this.limiteCongregacoes = limiteCongregacoes;
        this.valorMensal = valorMensal;
        this.bloqueiaAvancados = bloqueiaAvancados;
    }

    public String getNomeExibicao() { return nomeExibicao; }
    public int getLimitePessoas() { return limitePessoas; }
    public int getLimiteCongregacoes() { return limiteCongregacoes; }
    public BigDecimal getValorMensal() { return valorMensal; }
    public boolean isBloqueiaAvancados() { return bloqueiaAvancados; }
}
```

2. Create `V48__cobranca_assinatura_saas.sql`:
```sql
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS status_assinatura VARCHAR(30) DEFAULT 'TRIAL';
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS trial_expira_em TIMESTAMP WITH TIME ZONE;
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS mp_preapproval_id VARCHAR(100);
ALTER TABLE igreja ADD COLUMN IF NOT EXISTS mp_payer_id VARCHAR(100);

CREATE TABLE IF NOT EXISTS codigo_convite_congregacao (
    id BIGSERIAL PRIMARY KEY,
    matriz_id BIGINT NOT NULL REFERENCES igreja(id),
    codigo VARCHAR(20) NOT NULL UNIQUE,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    usado_em TIMESTAMP WITH TIME ZONE,
    igreja_filha_id BIGINT REFERENCES igreja(id)
);
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=IgrejaEntityTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): adiciona migration V48 e entidades do modelo SaaS"
```

---

### Task 2: Member and Congregation Limit Enforcement in Services

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pessoa/exception/PlanoLimitePessoasExcedidoException.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/pessoa/PessoaService.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/IgrejaService.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pessoa/PessoaServiceLimiteTest.java`

**Interfaces:**
- Consumes: `PessoaRepository`, `Igreja.plano`
- Produces: Server-side validation of active members count vs plan limit

- [ ] **Step 1: Write the failing test**
Create `backend/api/src/test/java/com/domus/api/modules/pessoa/PessoaServiceLimiteTest.java`:
```java
package com.domus.api.modules.pessoa;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.pessoa.exception.PlanoLimitePessoasExcedidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PessoaServiceLimiteTest {

    @Mock
    private PessoaRepository pessoaRepository;

    @InjectMocks
    private PessoaService pessoaService;

    @Test
    void deveLancarExcecaoAoExceder60PessoasNoPlanoBasico() {
        Igreja igreja = new Igreja();
        igreja.setId(1L);
        igreja.setPlano(PlanoAssinatura.BASICO);

        when(pessoaRepository.countByIgrejaIdAndArquivadoFalse(1L)).thenReturn(60L);

        assertThatThrownBy(() -> pessoaService.validarLimitePessoas(igreja))
            .isInstanceOf(PlanoLimitePessoasExcedidoException.class)
            .hasMessageContaining("O plano Básico permite cadastrar até 60 pessoas");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=PessoaServiceLimiteTest`
Expected: FAIL with compilation errors.

- [ ] **Step 3: Implement minimal code**
1. Create `PlanoLimitePessoasExcedidoException.java`:
```java
package com.domus.api.modules.pessoa.exception;

public class PlanoLimitePessoasExcedidoException extends RuntimeException {
    public PlanoLimitePessoasExcedidoException(String message) {
        super(message);
    }
}
```

2. Add validation in `PessoaService.java`:
```java
    public void validarLimitePessoas(Igreja igreja) {
        PlanoAssinatura plano = igreja.getPlano() != null ? igreja.getPlano() : PlanoAssinatura.BASICO;
        long ativas = pessoaRepository.countByIgrejaIdAndArquivadoFalse(igreja.getId());
        if (ativas >= plano.getLimitePessoas()) {
            throw new PlanoLimitePessoasExcedidoException(
                String.format("O plano %s permite cadastrar até %d pessoas ativas. Faça um upgrade para continuar cadastrando.",
                    plano.getNomeExibicao(), plano.getLimitePessoas())
            );
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=PessoaServiceLimiteTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): valida limite de pessoas ativas por plano em PessoaService"
```

---

### Task 3: Daughter Church Invite Code Service & Endpoint

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteService.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/dto/GerarCodigoConviteResponse.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/dto/CadastroCongregacaoRequest.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/IgrejaController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/igreja/CodigoConviteServiceTest.java`

**Interfaces:**
- Consumes: `IgrejaRepository`, `CodigoConviteRepository`
- Produces: Invite code generation for Matriz & free onboarding endpoint for daughter churches

- [ ] **Step 1: Write the failing test**
Create `backend/api/src/test/java/com/domus/api/modules/igreja/CodigoConviteServiceTest.java`:
```java
package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodigoConviteServiceTest {

    @Mock
    private CodigoConviteRepository codigoConviteRepository;

    @Mock
    private IgrejaRepository igrejaRepository;

    @InjectMocks
    private CodigoConviteService codigoConviteService;

    @Test
    void deveGerarCodigoConviteValidoParaMatriz() {
        Igreja matriz = new Igreja();
        matriz.setId(10L);
        matriz.setPlano(PlanoAssinatura.PRO);

        when(igrejaRepository.countByMatrizId(10L)).thenReturn(1L); // 1 usada de 3 disponíveis
        when(codigoConviteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GerarCodigoConviteResponse resp = codigoConviteService.gerarCodigo(matriz);
        assertThat(resp.codigo()).startsWith("DOMUS-");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=CodigoConviteServiceTest`
Expected: FAIL with compilation errors.

- [ ] **Step 3: Implement minimal code**
Create `CodigoConviteService.java`:
```java
package com.domus.api.modules.igreja;

import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CodigoConviteService {

    private final CodigoConviteRepository codigoConviteRepository;
    private final IgrejaRepository igrejaRepository;

    public CodigoConviteService(CodigoConviteRepository codigoConviteRepository, IgrejaRepository igrejaRepository) {
        this.codigoConviteRepository = codigoConviteRepository;
        this.igrejaRepository = igrejaRepository;
    }

    @Transactional
    public GerarCodigoConviteResponse gerarCodigo(Igreja matriz) {
        long congregacoesAtuais = igrejaRepository.countByMatrizId(matriz.getId());
        if (congregacoesAtuais >= matriz.getPlano().getLimiteCongregacoes()) {
            throw new PlanoLimiteExcedidoException("Limite de congregações vinculadas atingido para o plano " + matriz.getPlano().getNomeExibicao());
        }

        String codigo = "DOMUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setMatriz(matriz);
        convite.setCodigo(codigo);

        codigoConviteRepository.save(convite);
        return new GerarCodigoConviteResponse(codigo);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=CodigoConviteServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): adiciona CodigoConviteService para cadastro gratuito de congregações filhas"
```

---

### Task 4: Feature Locking Interceptor for Básico Plan

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/config/PlanoFeatureInterceptor.java`
- Test: `backend/api/src/test/java/com/domus/api/config/PlanoFeatureInterceptorTest.java`

**Interfaces:**
- Consumes: HTTP request path, `Igreja.plano`
- Produces: HTTP 403 Forbidden for advanced routes (Feed social, Contas a pagar) on Básico plan

- [ ] **Step 1: Write the failing test**
Create `backend/api/src/test/java/com/domus/api/config/PlanoFeatureInterceptorTest.java`:
```java
package com.domus.api.config;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanoFeatureInterceptorTest {

    @InjectMocks
    private PlanoFeatureInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    void deveBloquearAcessoAoFeedNoPlanoBasico() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/postagens");

        Igreja igreja = new Igreja();
        igreja.setPlano(PlanoAssinatura.BASICO);

        boolean result = interceptor.validarAcessoRecurso(request, response, igreja);

        verify(response).setStatus(403);
        verify(response).getWriter();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=PlanoFeatureInterceptorTest`
Expected: FAIL compilation error.

- [ ] **Step 3: Implement minimal code**
Create `PlanoFeatureInterceptor.java`:
```java
package com.domus.api.config;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PlanoFeatureInterceptor {

    public boolean validarAcessoRecurso(HttpServletRequest request, HttpServletResponse response, Igreja igreja) throws IOException {
        String uri = request.getRequestURI();
        PlanoAssinatura plano = igreja.getPlano() != null ? igreja.getPlano() : PlanoAssinatura.BASICO;

        if (plano.isBloqueiaAvancados() && (uri.startsWith("/api/postagens") || uri.startsWith("/api/contas-a-pagar"))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\": \"Este recurso não está disponível no plano Básico. Atualize para o plano Pro para liberar.\"}");
            return false;
        }
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=PlanoFeatureInterceptorTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): adiciona trava de funcionalidades avançadas para o plano Básico"
```

---

### Task 5: Mercado Pago Preapproval & Webhook Services

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/assinatura/MercadoPagoSubscriptionService.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/assinatura/AssinaturaWebhookController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/assinatura/MercadoPagoSubscriptionServiceTest.java`

- [ ] **Step 1: Write the failing test**
Unit tests for Mercado Pago preapproval creation with start_date at D+14 and webhook status updates.

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=MercadoPagoSubscriptionServiceTest`

- [ ] **Step 3: Implement minimal code**
Implement preapproval creation with 14-day start_date and webhook payload parser.

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=MercadoPagoSubscriptionServiceTest`

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): integra Mercado Pago Subscriptions API e processamento de webhooks"
```

---

### Task 6: Frontend Landing Page & Pricing Matrix

**Files:**
- Create: `frontend/src/app/(public)/page.tsx`
- Create: `frontend/src/app/(public)/planos/page.tsx`
- Create: `frontend/src/components/landing/TabelaPlanos.tsx`
- Test: `frontend/src/app/(public)/planos/page.test.tsx`

- [ ] **Step 1: Write failing frontend component test**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement TabelaPlanos with R$ 79, R$ 179, R$ 299, R$ 499 tiers**
- [ ] **Step 4: Run test to verify pass**
- [ ] **Step 5: Commit**
```bash
git add frontend/src/
git commit -m "feat(saas): cria Landing Page e tabela comparativa de planos com R$ 79 base"
```

---

### Task 7: Frontend Daughter Church Onboarding via Invite Code (`/cadastro/congregacao`)

**Files:**
- Create: `frontend/src/app/(public)/cadastro/congregacao/page.tsx`
- Test: `frontend/src/app/(public)/cadastro/congregacao/page.test.tsx`

- [ ] **Step 1: Write failing frontend test for invite code onboarding**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement free onboarding form validating `?codigo=DOMUS-XXXX`**
- [ ] **Step 4: Run test to verify pass**
- [ ] **Step 5: Commit**
```bash
git add frontend/src/
git commit -m "feat(saas): cria tela de cadastro de congregações filhas via código de convite sem cobrança"
```
