# Spec de Design: Cobrança de Assinatura SaaS do Domus (Planos, Checkout, Código de Convite & Feature Flags Desacopladas)

> **Status:** Proposta aprovada em 2026-09-24  
> **Arquitetura:** Altamente desacoplada (Low Coupling / High Cohesion). O catálogo de planos, preços, limites e permissões de funcionalidades vive em enums/configurações centralizadas (`FeaturePlan`), permitindo alterar preços, limites e redistribuir funcionalidades entre planos alterando apenas a definição central, sem impactar regras de negócio, controllers ou telas.

---

## 1. Visão Geral e Arquitetura de Desacoplamento

1. **Catálogo de Features (`FeaturePlan`)**: Cada funcionalidade avançada é representada por uma constante enum (ex: `FEED_SOCIAL`, `CONTAS_A_PAGAR`, `CHECKOUT_EVENTO`, `RELATORIOS_AVANCADOS`, `CAMPOS_PERSONALIZADOS`).
2. **Matriz de Permissões no Plano**: Cada `PlanoAssinatura` possui um `Set<FeaturePlan>` de funcionalidades permitidas, um limite de pessoas ativas e um limite de congregações filhas.
3. **Verificação Desacoplada**:
   - Backend: `igreja.getPlano().temFeature(FeaturePlan.FEED_SOCIAL)`
   - Frontend: `hasFeature('FEED_SOCIAL')` via hook `usePlanoFeatures()`
4. **Fluxo de Código de Convite**: A Matriz assina o plano; a igreja filha cadastra-se em `/cadastro/congregacao?codigo=DOMUS-XXXX` sem cobrança individual.

---

## 2. Definidor de Funcionalidades (`FeaturePlan`)

```java
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

---

## 3. Matriz de Configuração dos Planos (`PlanoAssinatura`)

| Plano | Valor Mensal | Pessoas Ativas | Congregações Filhas | Features Habilitadas |
|---|---|---|---|---|
| **Básico** | R$ 79,00 | 60 | 0 | Nenhuma feature avançada (Apenas essencial) |
| **Pro** | R$ 179,00 | 300 | 3 | TODAS (`FEED_SOCIAL`, `CONTAS_A_PAGAR`, `CHECKOUT_EVENTO`, `RELATORIOS_AVANCADOS`, `CAMPOS_PERSONALIZADOS`) |
| **Pro+** | R$ 299,00 | 800 | 5 | TODAS |
| **Enterprise** | R$ 499,00 | Ilimitado (99999) | Ilimitado (9999) | TODAS |

---

## 4. Estrutura do Backend (`Spring Boot`)

1. **`PlanoAssinatura.java`**:
```java
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

    public boolean temFeature(FeaturePlan feature) {
        return featuresHabilitadas.contains(feature);
    }
}
```

2. **Anotação Personalizada `@RequerFeature`**:
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequerFeature {
    FeaturePlan value();
}
```

3. **Interceptor / Aspecto (`FeaturePlanInterceptor`)**:
- Intercepta métodos/controllers anotados com `@RequerFeature(FeaturePlan.CONTAS_A_PAGAR)`.
- Se a igreja do token JWT não possuir a feature, lança `FeatureNaoPermitidaException` (HTTP 403 / 402).

---

## 5. Estrutura do Frontend (`Next.js`)

1. **Hook `usePlanoFeatures()`**:
   - Lê as features permitidas enviadas pelo endpoint `/auth/me` ou `/igrejas/minha`.
   - Exemplo: `const { temFeature, limitePessoasExcedido } = usePlanoFeatures();`

2. **Componente `<FeatureGuard>`**:
```tsx
<FeatureGuard feature="FEED_SOCIAL" fallback={<UpgradeBanner />}>
    <FeedComunidade />
</FeatureGuard>
```

---

## 6. Arquivos Impactados

- **Backend**:
  - `FeaturePlan.java`, `PlanoAssinatura.java`, `RequerFeature.java`, `FeaturePlanInterceptor.java`
  - `Igreja.java`, `CodigoConviteCongregacao.java`, `V48__cobranca_assinatura_saas.sql`
  - `MercadoPagoSubscriptionService.java`, `AssinaturaWebhookController.java`

- **Frontend**:
  - `hooks/usePlanoFeatures.ts`, `components/auth/FeatureGuard.tsx`
  - `app/(public)/planos/page.tsx`, `app/(public)/cadastro/page.tsx`
  - `app/(public)/cadastro/congregacao/page.tsx`
  - `app/(app)/configuracoes/assinatura/page.tsx`
