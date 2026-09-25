# Implementation Plan: Cobrança de Assinatura SaaS do Domus (Planos, Checkout, Código de Convite & Feature Flags Desacopladas)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform Domus into a complete multi-tenant SaaS with public landing page, self-service onboarding (14-day trial with upfront credit card authorization via Mercado Pago Subscriptions API), daughter church onboarding via invite codes, member limits, and a highly decoupled feature-flagging architecture (`FeaturePlan` + `@RequerFeature` + `<FeatureGuard>`) allowing easy price, limit, and feature reconfiguration.

**Architecture:** 
- Backend: Spring Boot 3.3 + JPA + Flyway migration (`V48__cobranca_assinatura_saas.sql`). `FeaturePlan` enum lists features. `PlanoAssinatura` holds `Set<FeaturePlan>`, limits, and monthly price. `@RequerFeature(FeaturePlan.X)` annotation and `FeaturePlanInterceptor` guard controllers without hardcoding plan names. `PessoaService` enforces member limits. `MercadoPagoSubscriptionService` handles `/preapproval` with 14-day trial start date.
- Frontend: Next.js App Router. `usePlanoFeatures()` hook and `<FeatureGuard>` component. Public `/` Landing Page, `/planos` comparison table, `/cadastro` wizard with Mercado Pago Card Token SDK, `/cadastro/congregacao` free invite-code onboarding, and `/configuracoes/assinatura` management.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, Flyway, Mercado Pago Subscriptions API (`/preapproval`), Next.js 14 (App Router), TypeScript, React Hook Form, Zod, Tailwind CSS.

**Spec:** `docs/specs/2026-09-24-cobranca-assinatura-saas-design.md`

## Global Constraints
- High cohesion and loose coupling: controllers and services must check `temFeature(FeaturePlan)` rather than comparing plan names like `BASICO`.
- All backend entities must maintain multi-tenant isolation (`igreja_id`).
- Credit card details must never touch backend servers directly; frontend must tokenize card numbers via Mercado Pago SDK.
- Orthographic correctness for prt-BR in UI strings and emails.

## Review Focus
- **Feature flag decoupling**: Adding/removing a feature from a plan in `PlanoAssinatura` must update access control without requiring changes in controllers or frontend pages.
- **Member limit enforcement**: Attempting to create a 61st active person in a Básico church must throw `PlanoLimitePessoasExcedidoException`.
- **Daughter church invite code**: Daughter churches registering with a valid invite code must create a linked church (`matriz_id`) with status `ATIVA` without requiring credit card or charging.
- **Webhook idempotency**: Duplicate webhook notifications from Mercado Pago must not produce duplicate emails or corrupt status.

---

### Task 1: Database Migration, Core Enums (`FeaturePlan`, `PlanoAssinatura`) & Decoupled Access Model

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V48__cobranca_assinatura_saas.sql`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/FeaturePlan.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/PlanoAssinatura.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/StatusAssinatura.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteCongregacao.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteRepository.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/Igreja.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/igreja/PlanoAssinaturaTest.java`

**Interfaces:**
- Consumes: Database schema V47
- Produces: `FeaturePlan`, `PlanoAssinatura` with `Set<FeaturePlan>`, `StatusAssinatura`, `CodigoConviteCongregacao`, updated `Igreja` entity

- [ ] **Step 1: Write the failing test**
Create `backend/api/src/test/java/com/domus/api/modules/igreja/PlanoAssinaturaTest.java`:
```java
package com.domus.api.modules.igreja;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlanoAssinaturaTest {

    @Test
    void deveVerificarFeaturesDoPlanoBasicoEPro() {
        assertThat(PlanoAssinatura.BASICO.temFeature(FeaturePlan.FEED_SOCIAL)).isFalse();
        assertThat(PlanoAssinatura.BASICO.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isFalse();

        assertThat(PlanoAssinatura.PRO.temFeature(FeaturePlan.FEED_SOCIAL)).isTrue();
        assertThat(PlanoAssinatura.PRO.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=PlanoAssinaturaTest`
Expected: FAIL with compilation errors.

- [ ] **Step 3: Implement minimal code**
1. Create `FeaturePlan.java`:
```java
package com.domus.api.modules.igreja;

public enum FeaturePlan {
    FEED_SOCIAL("Mural e Feed Social da Comunidade"),
    CONTAS_A_PAGAR("Gestão e Lembretes de Contas a Pagar"),
    CHECKOUT_EVENTO("Cobrança de Eventos Pagos via PIX/Cartão"),
    RELATORIOS_AVANCADOS("Relatórios Avançados e Balancete Anual"),
    CAMPOS_PERSONALIZADOS("Campos Personalizados em Eventos");

    private final String descricao;

    FeaturePlan(String descricao) { this.descricao = descricao; }
    public String getDescricao() { return descricao; }
}
```

2. Create `PlanoAssinatura.java`:
```java
package com.domus.api.modules.igreja;

import java.math.BigDecimal;
import java.util.Set;

public enum PlanoAssinatura {
    BASICO("Básico", 60, 0, new BigDecimal("79.00"), Set.of()),
    PRO("Pro", 300, 3, new BigDecimal("179.00"), Set.of(FeaturePlan.values())),
    PRO_PLUS("Pro+", 800, 5, new BigDecimal("299.00"), Set.of(FeaturePlan.values())),
    ENTERPRISE("Enterprise", 99999, 9999, new BigDecimal("499.00"), Set.of(FeaturePlan.values()));

    private final String nomeExibicao;
    private final int limitePessoas;
    private final int limiteCongregacoes;
    private final BigDecimal valorMensal;
    private final Set<FeaturePlan> featuresHabilitadas;

    PlanoAssinatura(String nomeExibicao, int limitePessoas, int limiteCongregacoes, BigDecimal valorMensal, Set<FeaturePlan> featuresHabilitadas) {
        this.nomeExibicao = nomeExibicao;
        this.limitePessoas = limitePessoas;
        this.limiteCongregacoes = limiteCongregacoes;
        this.valorMensal = valorMensal;
        this.featuresHabilitadas = featuresHabilitadas;
    }

    public boolean temFeature(FeaturePlan feature) {
        return featuresHabilitadas.contains(feature);
    }

    public String getNomeExibicao() { return nomeExibicao; }
    public int getLimitePessoas() { return limitePessoas; }
    public int getLimiteCongregacoes() { return limiteCongregacoes; }
    public BigDecimal getValorMensal() { return valorMensal; }
    public Set<FeaturePlan> getFeaturesHabilitadas() { return featuresHabilitadas; }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=PlanoAssinaturaTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): adiciona FeaturePlan e PlanoAssinatura desacoplados"
```

---

### Task 2: Decoupled Backend Annotation `@RequerFeature` and Interceptor

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/config/RequerFeature.java`
- Create: `backend/api/src/main/java/com/domus/api/config/FeaturePlanInterceptor.java`
- Test: `backend/api/src/test/java/com/domus/api/config/FeaturePlanInterceptorTest.java`

**Interfaces:**
- Consumes: `@RequerFeature`, `FeaturePlan`, JWT authentication, `Igreja.plano`
- Produces: HTTP 403 / 402 with upgrade guidance when accessing locked features

- [ ] **Step 1: Write the failing test**
Create `backend/api/src/test/java/com/domus/api/config/FeaturePlanInterceptorTest.java`:
```java
package com.domus.api.config;

import com.domus.api.modules.igreja.FeaturePlan;
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
class FeaturePlanInterceptorTest {

    @InjectMocks
    private FeaturePlanInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    void deveBloquearAcessoQuandoPlanoNaoPossuirAFeature() throws Exception {
        Igreja igreja = new Igreja();
        igreja.setPlano(PlanoAssinatura.BASICO);

        boolean liberado = interceptor.validarFeature(response, igreja, FeaturePlan.FEED_SOCIAL);

        verify(response).setStatus(403);
        assertThat(liberado).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**
Run: `cd backend/api && ./mvnw test -Dtest=FeaturePlanInterceptorTest`
Expected: FAIL compilation error.

- [ ] **Step 3: Implement minimal code**
1. Create `RequerFeature.java`:
```java
package com.domus.api.config;

import com.domus.api.modules.igreja.FeaturePlan;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerFeature {
    FeaturePlan value();
}
```

2. Create `FeaturePlanInterceptor.java`:
```java
package com.domus.api.config;

import com.domus.api.modules.igreja.FeaturePlan;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class FeaturePlanInterceptor {

    public boolean validarFeature(HttpServletResponse response, Igreja igreja, FeaturePlan feature) throws IOException {
        PlanoAssinatura plano = igreja.getPlano() != null ? igreja.getPlano() : PlanoAssinatura.BASICO;

        if (!plano.temFeature(feature)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(String.format("{\"error\": \"A funcionalidade '%s' não está incluída no seu plano atual (%s). Faça um upgrade para liberar.\", \"feature\": \"%s\"}",
                feature.getDescricao(), plano.getNomeExibicao(), feature.name()));
            return false;
        }
        return true;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**
Run: `cd backend/api && ./mvnw test -Dtest=FeaturePlanInterceptorTest`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): adiciona anotação @RequerFeature e interceptor desacoplado"
```

---

### Task 3: Daughter Church Invite Code Service

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/igreja/CodigoConviteService.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/igreja/IgrejaController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/igreja/CodigoConviteServiceTest.java`

- [ ] **Step 1: Write failing unit test for invite code generation and validation**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement CodigoConviteService**
- [ ] **Step 4: Run test to verify pass**
- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): adiciona serviço de geração e uso de código de convite para congregações"
```

---

### Task 4: Mercado Pago Subscription & Webhooks

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/assinatura/MercadoPagoSubscriptionService.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/assinatura/AssinaturaWebhookController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/assinatura/MercadoPagoSubscriptionServiceTest.java`

- [ ] **Step 1: Write failing test**
- [ ] **Step 2: Run test to verify failure**
- [ ] **Step 3: Implement preapproval subscription logic (start_date at D+14) and webhook listener**
- [ ] **Step 4: Run test to verify pass**
- [ ] **Step 5: Commit**
```bash
git add backend/api/src/
git commit -m "feat(saas): integra Mercado Pago Subscriptions e processamento de webhooks"
```

---

### Task 5: Frontend Decoupled `usePlanoFeatures` Hook & `<FeatureGuard>` Component

**Files:**
- Create: `frontend/src/hooks/usePlanoFeatures.ts`
- Create: `frontend/src/components/auth/FeatureGuard.tsx`
- Test: `frontend/src/components/auth/FeatureGuard.test.tsx`

- [ ] **Step 1: Write failing component test for FeatureGuard**
Create `frontend/src/components/auth/FeatureGuard.test.tsx`:
```tsx
import { render, screen } from '@testing-library/react';
import FeatureGuard from './FeatureGuard';

describe('FeatureGuard', () => {
    it('deve ocultar conteúdo quando a feature não for permitida', () => {
        render(
            <FeatureGuard feature="FEED_SOCIAL" featuresHabilitadas={[]}>
                <div>Conteúdo Privado</div>
            </FeatureGuard>
        );
        expect(screen.queryByText('Conteúdo Privado')).not.toBeInTheDocument();
    });
});
```

- [ ] **Step 2: Run test to verify failure**
Run: `cd frontend && npm test -- components/auth/FeatureGuard.test.tsx`

- [ ] **Step 3: Implement minimal code**
Create `frontend/src/components/auth/FeatureGuard.tsx`:
```tsx
import React from 'react';

interface FeatureGuardProps {
    feature: string;
    featuresHabilitadas?: string[];
    fallback?: React.ReactNode;
    children: React.ReactNode;
}

export default function FeatureGuard({ feature, featuresHabilitadas = [], fallback = null, children }: FeatureGuardProps) {
    const liberada = featuresHabilitadas.includes(feature);
    if (!liberada) {
        return <>{fallback}</>;
    }
    return <>{children}</>;
}
```

- [ ] **Step 4: Run test to verify pass**
Run: `cd frontend && npm test -- components/auth/FeatureGuard.test.tsx`
Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add frontend/src/
git commit -m "feat(saas): adiciona hook usePlanoFeatures e componente FeatureGuard"
```

---

### Task 6: Frontend Landing Page, Pricing Table & Onboarding Wizard

**Files:**
- Create: `frontend/src/app/(public)/page.tsx`
- Create: `frontend/src/app/(public)/planos/page.tsx`
- Create: `frontend/src/app/(public)/cadastro/page.tsx`
- Create: `frontend/src/app/(public)/cadastro/congregacao/page.tsx`
- Test: `frontend/src/app/(public)/planos/page.test.tsx`

- [ ] **Step 1: Write failing frontend tests for pages**
- [ ] **Step 2: Run tests to verify failure**
- [ ] **Step 3: Implement Landing Page, Pricing Table, Checkout Wizard & Daughter Church Invite Onboarding**
- [ ] **Step 4: Run tests to verify pass**
- [ ] **Step 5: Commit**
```bash
git add frontend/src/
git commit -m "feat(saas): adiciona Landing Page, tabela de preços e onboarding via código de convite"
```
