# Cobrança de Evento Pago (Mercado Pago) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cobrar de verdade a inscrição em evento pago via Mercado Pago (split — dinheiro
cai direto na conta da igreja), com cobrança por pessoa (titular sempre paga na hora,
demais podem receber link individual pra pagar depois).

**Architecture:** Módulo novo `com.domus.api.modules.pagamento` no backend com dois
subdomínios: `conta` (conexão OAuth da igreja com o Mercado Pago, credenciais
criptografadas) e `cobranca` (uma linha por pessoa cobrada num evento, com status e
token público opcional). Checkout via Mercado Pago Checkout Bricks (embutido, PIX +
cartão) tanto na tela de inscrição autenticada quanto numa página pública
`/cobranca/[token]`. Webhook único confirma pagamento; job agendado expira cobrança
vencida e libera vaga.

**Tech Stack:** Java 21 / Spring Boot / Spring Data JPA / Flyway (backend);
Next.js/TypeScript/TanStack Query (frontend); SDK oficial `com.mercadopago:sdk-java`
(backend) e `@mercadopago/sdk-react` (frontend, Payment Brick).

**Spec:** `backend/api/docs/superpowers/specs/2026-08-23-cobranca-evento-pago-design.md`

## Global Constraints

- 0% de comissão do Domus nesta entrega — 100% do valor do evento vai pra igreja.
- `evento.preco` continua um valor fixo único por pessoa — sem lotes.
- PIX + cartão desde o início, via Checkout Bricks — nunca redirecionar pro Mercado Pago
  (decisão explícita: "quero algo profissional, redirecionar fica amador").
- `access_token`/`refresh_token` da conta Mercado Pago da igreja são criptografados em
  repouso (AES-GCM), chave só em variável de ambiente, nunca logados.
- Titular da inscrição sempre paga a própria parte na hora — só cobrança de acompanhante
  ou de outra pessoa inscrita junto pode virar link compartilhável.
- Vaga de evento pago conta pessoa com `COBRANCA_EVENTO` em `PAGO` **ou** `PENDENTE` não
  expirada — nunca conta `EXPIRADO`/`CANCELADO`.
- Webhook do Mercado Pago SEMPRE responde 200 (mesmo em rejeição por assinatura inválida
  — só loga e ignora), pra não entrar em reenvio infinito do provedor.
- `igreja_id` de toda entidade nova vem do JWT/contexto de autenticação, nunca do corpo
  da requisição.
- Todo teste de service usa Mockito puro (Estilo A — `mock()` manual no `@BeforeEach`,
  dominante no projeto); `@SpringBootTest` só para webhook (segurança/assinatura) e
  controller (harness `AutenticacaoTestSupport`).

---

## File Structure

**Backend — módulo novo `com.domus.api.modules.pagamento`:**
```
modules/pagamento/
  conta/
    ContaPagamentoIgreja.java
    ContaPagamentoIgrejaRepository.java
    MercadoPagoOAuthService.java
    ContaPagamentoController.java
    DTOs/
      ConectarContaResponseDTO.java
      StatusContaPagamentoDTO.java
  cobranca/
    CobrancaEvento.java
    StatusCobranca.java (enum)
    CobrancaEventoRepository.java
    CobrancaEventoService.java
    CobrancaController.java (endpoints públicos por token)
    DTOs/
      CobrancaResumoDTO.java
      CobrancaPublicaDTO.java
  webhook/
    MercadoPagoWebhookController.java
    MercadoPagoWebhookService.java
    MercadoPagoAssinaturaValidator.java
  MercadoPagoClient.java (wrapper fino do SDK, um client por token de igreja)
  job/
    CobrancaEventoExpiracaoJob.java
  seguranca/
    CredencialEncryptor.java
```

**Backend — modificados:**
- `modules/evento/inscricao/InscricaoService.java` — cria `CobrancaEvento` por pessoa
  ao inscrever em evento pago; cancelamento aciona reembolso.
- `modules/evento/inscricao/InscricaoRepository.java` — nova query de contagem de vaga
  considerando `CobrancaEvento`.
- `modules/evento/inscricao/InscricaoController.java` — endpoint de inscrição em evento
  pago recebe a escolha por pessoa (pagar agora / gerar link).
- `shared/security/SecurityConfig.java` — libera `/pagamentos/mercadopago/webhook` e
  `/cobrancas/**` (público, sem CSRF).
- `shared/exception/BusinessException` — novos códigos (`IGREJA_SEM_CONTA_PAGAMENTO`,
  etc.) — reaproveita a classe existente, sem alteração estrutural.
- `pom.xml` — dependência `com.mercadopago:sdk-java`.
- `application.properties` — novas envs.

**Frontend — novos:**
```
frontend/src/services/pagamento.service.ts
frontend/src/services/cobranca.service.ts
frontend/src/hooks/pagamento/useContaPagamento.ts
frontend/src/hooks/pagamento/useConectarMercadoPago.ts
frontend/src/hooks/cobranca/useGerarLinkCobranca.ts
frontend/src/hooks/cobranca/useCobrancaPublica.ts
frontend/src/components/module/configuracoes/SecaoRecebimentos.tsx
frontend/src/components/module/configuracoes/SecaoRecebimentos.module.css
frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx
frontend/src/components/module/pagamento/PaymentBrickCheckout.module.css
frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.tsx
frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.module.css
frontend/src/components/module/eventos/ModalCompartilharCobranca.tsx (clone de ModalCompartilharConvite)
frontend/src/app/cobranca/[token]/page.tsx
frontend/src/app/cobranca/[token]/CobrancaPublica.module.css
```

**Frontend — modificados:**
- `frontend/src/lib/endpoints.ts` — endpoints de pagamento/cobrança.
- Fluxo de inscrição em evento pago (componente que hoje abre `ModalConfirmarPagamento`)
  passa a decidir: sem conta conectada → aviso; com conta → fluxo novo de escolha por
  pessoa + Brick.
- `package.json` — `@mercadopago/sdk-react`.

---

## Task 1: Migration — tabelas `conta_pagamento_igreja` e `cobranca_evento`

**Files:**
- Create: `backend/api/src/main/resources/db/migration/V29__cobranca_evento_pago.sql`
- Test: nenhum teste unitário nesta task (migration validada pelo boot do Testcontainers em tasks seguintes)

- [ ] **Step 1: Escrever a migration**

```sql
-- V29: cobrança de evento pago via Mercado Pago (split).
-- Duas entidades novas: CONTA_PAGAMENTO_IGREJA (credencial OAuth da igreja no Mercado
-- Pago, 1-1) e COBRANCA_EVENTO (uma linha por pessoa cobrada num evento pago).

CREATE TABLE conta_pagamento_igreja (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id               UUID NOT NULL UNIQUE REFERENCES igreja(id),
    mp_user_id              VARCHAR(100) NOT NULL,
    -- access_token/refresh_token são criptografados em repouso (AES-GCM, ver
    -- CredencialEncryptor) — nunca gravados em texto puro, nunca logados.
    access_token            TEXT NOT NULL,
    refresh_token           TEXT NOT NULL,
    expira_em               TIMESTAMPTZ NOT NULL,
    conectado_em            TIMESTAMPTZ NOT NULL DEFAULT now(),
    conectado_por_usuario_id UUID NOT NULL REFERENCES usuario(id)
);

CREATE TABLE cobranca_evento (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    igreja_id               UUID NOT NULL REFERENCES igreja(id),
    evento_id               UUID NOT NULL REFERENCES evento(id),
    inscricao_id            UUID NOT NULL REFERENCES inscricao_evento(id) ON DELETE CASCADE,
    pessoa_id               UUID REFERENCES pessoa(id),
    acompanhante_id         UUID REFERENCES acompanhante_inscricao(id) ON DELETE CASCADE,
    valor                   NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    status                  VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    mp_payment_id           VARCHAR(100),
    token_link_publico      VARCHAR(64) UNIQUE,
    expira_em               TIMESTAMPTZ NOT NULL,
    pago_em                 TIMESTAMPTZ,
    criado_por_usuario_id   UUID NOT NULL REFERENCES usuario(id),
    criado_em               TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Exatamente uma das duas: cobrança é de uma pessoa cadastrada OU de um
    -- acompanhante sem cadastro (mesmo padrão de EVENTO.local_id/local_texto).
    CONSTRAINT cobranca_evento_pessoa_xor_acompanhante CHECK (
        (pessoa_id IS NOT NULL AND acompanhante_id IS NULL) OR
        (pessoa_id IS NULL AND acompanhante_id IS NOT NULL)
    ),
    CONSTRAINT cobranca_evento_status_valido CHECK (
        status IN ('PENDENTE', 'PAGO', 'EXPIRADO', 'CANCELADO', 'REEMBOLSADO')
    )
);

CREATE INDEX idx_cobranca_evento_inscricao ON cobranca_evento(inscricao_id);
CREATE INDEX idx_cobranca_evento_status_expiracao ON cobranca_evento(status, expira_em)
    WHERE status = 'PENDENTE';
```

- [ ] **Step 2: Subir o projeto local e confirmar que a migration aplica sem erro**

Run: `cd backend/api && mvn -q -o spring-boot:run` (ou `mvn -q test -Dtest=NENHUM` só pra
disparar o boot do Flyway) — parar assim que o log mostrar `Successfully applied 1
migration to schema` referente a V29, depois `Ctrl+C`.

- [ ] **Step 3: Commit**

```bash
git add backend/api/src/main/resources/db/migration/V29__cobranca_evento_pago.sql
git commit -m "feat(pagamento): migration das tabelas de conta e cobrança de pagamento"
```

---

## Task 2: `CredencialEncryptor` (criptografia AES-GCM)

Primeira credencial reversível de terceiro guardada no projeto — precisa de um
utilitário próprio, testável sem Spring.

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/seguranca/CredencialEncryptor.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/seguranca/CredencialEncryptorTest.java`

**Interfaces:**
- Produces: `CredencialEncryptor.criptografar(String textoPlano): String` e
  `CredencialEncryptor.descriptografar(String textoCriptografado): String` — usados por
  `ContaPagamentoIgrejaRepository`/`MercadoPagoOAuthService` nas Tasks 3-4.

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.pagamento.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CredencialEncryptorTest {

    // Chave AES-256 de teste, 32 bytes em Base64 — nunca usar em produção.
    private static final String CHAVE_TESTE = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=";

    private final CredencialEncryptor encryptor = new CredencialEncryptor(CHAVE_TESTE);

    @Test
    void criptografaEDescriptografaDeVolta() {
        String original = "APP_USR-1234567890-mercadopago-access-token";

        String criptografado = encryptor.criptografar(original);
        String descriptografado = encryptor.descriptografar(criptografado);

        assertThat(criptografado).isNotEqualTo(original);
        assertThat(descriptografado).isEqualTo(original);
    }

    @Test
    void criptografiasSucessivasDoMesmoValorGeramSaidasDiferentes() {
        // AES-GCM usa IV aleatório por chamada — mesma entrada, saída diferente.
        // Prova que não é um cifrador determinístico (o que vazaria padrão).
        String original = "mesmo-valor";

        String primeira = encryptor.criptografar(original);
        String segunda = encryptor.criptografar(original);

        assertThat(primeira).isNotEqualTo(segunda);
        assertThat(encryptor.descriptografar(primeira)).isEqualTo(original);
        assertThat(encryptor.descriptografar(segunda)).isEqualTo(original);
    }

    @Test
    void recusaDescriptografarValorAdulterado() {
        String criptografado = encryptor.criptografar("valor-original");
        String adulterado = criptografado.substring(0, criptografado.length() - 4) + "AAAA";

        assertThatThrownBy(() -> encryptor.descriptografar(adulterado))
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=CredencialEncryptorTest`
Expected: FAIL — `CredencialEncryptor` não existe ainda.

- [ ] **Step 3: Implementar**

```java
package com.domus.api.modules.pagamento.seguranca;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM com IV aleatório por chamada. A chave vem só de variável de ambiente
 * (nunca do banco, nunca logada) — primeira credencial reversível de terceiro que o
 * projeto guarda (access_token/refresh_token do Mercado Pago da igreja).
 */
@Component
public class CredencialEncryptor {

    private static final String ALGORITMO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV_BYTES = 12;
    private static final int TAMANHO_TAG_BITS = 128;

    private final SecretKeySpec chave;
    private final SecureRandom random = new SecureRandom();

    public CredencialEncryptor(@Value("${app.pagamento.encryption-key}") String chaveBase64) {
        byte[] bytesChave = Base64.getDecoder().decode(chaveBase64);
        this.chave = new SecretKeySpec(bytesChave, "AES");
    }

    public String criptografar(String textoPlano) {
        try {
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            byte[] textoCifrado = cipher.doFinal(textoPlano.getBytes());

            byte[] resultado = new byte[iv.length + textoCifrado.length];
            System.arraycopy(iv, 0, resultado, 0, iv.length);
            System.arraycopy(textoCifrado, 0, resultado, iv.length, textoCifrado.length);

            return Base64.getEncoder().encodeToString(resultado);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criptografar credencial", e);
        }
    }

    public String descriptografar(String textoCriptografado) {
        try {
            byte[] bytes = Base64.getDecoder().decode(textoCriptografado);
            byte[] iv = new byte[TAMANHO_IV_BYTES];
            byte[] textoCifrado = new byte[bytes.length - TAMANHO_IV_BYTES];
            System.arraycopy(bytes, 0, iv, 0, iv.length);
            System.arraycopy(bytes, iv.length, textoCifrado, 0, textoCifrado.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAMANHO_TAG_BITS, iv));
            return new String(cipher.doFinal(textoCifrado));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao descriptografar credencial", e);
        }
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=CredencialEncryptorTest`
Expected: PASS (3 testes)

- [ ] **Step 5: Adicionar a propriedade em `application.properties`**

Em `backend/api/src/main/resources/application.properties`, adicionar:
```properties
app.pagamento.encryption-key=${PAGAMENTO_ENCRYPTION_KEY}
```

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/seguranca/CredencialEncryptor.java \
        backend/api/src/test/java/com/domus/api/modules/pagamento/seguranca/CredencialEncryptorTest.java \
        backend/api/src/main/resources/application.properties
git commit -m "feat(pagamento): criptografia AES-GCM para credenciais de terceiro"
```

---

## Task 3: Entidade e repositório `ContaPagamentoIgreja`

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/conta/ContaPagamentoIgreja.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/conta/ContaPagamentoIgrejaRepository.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/conta/ContaPagamentoIgrejaRepositoryTest.java`

**Interfaces:**
- Consumes: `CredencialEncryptor` (Task 2).
- Produces: `ContaPagamentoIgrejaRepository.findByIgrejaId(UUID igrejaId): Optional<ContaPagamentoIgreja>`
  — usado pelas Tasks 4, 7 e 10.

- [ ] **Step 1: Entidade**

```java
package com.domus.api.modules.pagamento.conta;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conta_pagamento_igreja")
public class ContaPagamentoIgreja {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "igreja_id", nullable = false, unique = true)
    private UUID igrejaId;

    @Column(name = "mp_user_id", nullable = false)
    private String mpUserId;

    // Guardados JÁ criptografados — a criptografia/descriptografia acontece no
    // service (MercadoPagoOAuthService), nunca aqui, pra manter a entidade sem
    // dependência do CredencialEncryptor.
    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessTokenCriptografado;

    @Column(name = "refresh_token", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenCriptografado;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "conectado_em", nullable = false)
    private Instant conectadoEm;

    @Column(name = "conectado_por_usuario_id", nullable = false)
    private UUID conectadoPorUsuarioId;

    protected ContaPagamentoIgreja() {}

    public ContaPagamentoIgreja(UUID igrejaId, String mpUserId, String accessTokenCriptografado,
                                 String refreshTokenCriptografado, Instant expiraEm,
                                 UUID conectadoPorUsuarioId) {
        this.igrejaId = igrejaId;
        this.mpUserId = mpUserId;
        this.accessTokenCriptografado = accessTokenCriptografado;
        this.refreshTokenCriptografado = refreshTokenCriptografado;
        this.expiraEm = expiraEm;
        this.conectadoEm = Instant.now();
        this.conectadoPorUsuarioId = conectadoPorUsuarioId;
    }

    public void atualizarTokens(String accessTokenCriptografado, String refreshTokenCriptografado,
                                 Instant expiraEm) {
        this.accessTokenCriptografado = accessTokenCriptografado;
        this.refreshTokenCriptografado = refreshTokenCriptografado;
        this.expiraEm = expiraEm;
    }

    public UUID getId() { return id; }
    public UUID getIgrejaId() { return igrejaId; }
    public String getMpUserId() { return mpUserId; }
    public String getAccessTokenCriptografado() { return accessTokenCriptografado; }
    public String getRefreshTokenCriptografado() { return refreshTokenCriptografado; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getConectadoEm() { return conectadoEm; }
}
```

- [ ] **Step 2: Repositório**

```java
package com.domus.api.modules.pagamento.conta;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaPagamentoIgrejaRepository extends JpaRepository<ContaPagamentoIgreja, UUID> {
    Optional<ContaPagamentoIgreja> findByIgrejaId(UUID igrejaId);
    void deleteByIgrejaId(UUID igrejaId);
}
```

- [ ] **Step 3: Escrever o teste (`@DataJpaTest`, banco real via Testcontainers)**

```java
package com.domus.api.modules.pagamento.conta;

import static org.assertj.core.api.Assertions.assertThat;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ContaPagamentoIgrejaRepositoryTest implements PostgresTestContainerSupport {

    @Autowired
    private ContaPagamentoIgrejaRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome) VALUES ('11111111-1111-1111-1111-111111111111', 'Igreja Teste')",
        "INSERT INTO role (id, nome) VALUES ('22222222-2222-2222-2222-222222222222', 'ADMIN_IGREJA')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Admin', 'admin@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', true)"
    })
    void salvaEBuscaPorIgrejaId() {
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        ContaPagamentoIgreja conta = new ContaPagamentoIgreja(
            igrejaId, "mp-user-123", "token-criptografado", "refresh-criptografado",
            Instant.now().plusSeconds(3600), usuarioId
        );
        repository.save(conta);
        entityManager.flush();
        entityManager.clear();

        var encontrada = repository.findByIgrejaId(igrejaId);

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getMpUserId()).isEqualTo("mp-user-123");
    }

    @Test
    void naoEncontraContaParaIgrejaSemConexao() {
        var encontrada = repository.findByIgrejaId(UUID.randomUUID());

        assertThat(encontrada).isEmpty();
    }
}
```

- [ ] **Step 4: Rodar e confirmar que compila e passa**

Run: `cd backend/api && mvn -q test -Dtest=ContaPagamentoIgrejaRepositoryTest`
Expected: PASS (2 testes) — precisa de Docker rodando (Testcontainers).

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/conta/ \
        backend/api/src/test/java/com/domus/api/modules/pagamento/conta/
git commit -m "feat(pagamento): entidade e repositório de ContaPagamentoIgreja"
```

---

## Task 4: OAuth Connect — conectar/desconectar conta Mercado Pago

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/conta/MercadoPagoOAuthService.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/conta/ContaPagamentoController.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/conta/DTOs/StatusContaPagamentoDTO.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/conta/DTOs/ConectarContaResponseDTO.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/conta/MercadoPagoOAuthServiceTest.java`

**Interfaces:**
- Consumes: `ContaPagamentoIgrejaRepository` (Task 3), `CredencialEncryptor` (Task 2).
- Produces: `MercadoPagoOAuthService.gerarUrlAutorizacao(UUID igrejaId): String`,
  `MercadoPagoOAuthService.processarCallback(String code, UUID igrejaId, UUID usuarioId): void`,
  `MercadoPagoOAuthService.status(UUID igrejaId): boolean`,
  `MercadoPagoOAuthService.desconectar(UUID igrejaId): void` — `status()` é consumido
  pela Task 7 (`InscricaoService`) pra checar pré-requisito antes de cobrar.

- [ ] **Step 1: Escrever o teste do service (Mockito puro)**

```java
package com.domus.api.modules.pagamento.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoOAuthServiceTest {

    ContaPagamentoIgrejaRepository repository;
    CredencialEncryptor encryptor;
    MercadoPagoOAuthClient client; // wrapper do SDK que troca `code` por tokens — mockado aqui
    MercadoPagoOAuthService service;

    UUID igrejaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(ContaPagamentoIgrejaRepository.class);
        encryptor = mock(CredencialEncryptor.class);
        client = mock(MercadoPagoOAuthClient.class);
        service = new MercadoPagoOAuthService(repository, encryptor, client, "client-id-teste");
    }

    @Test
    void geraUrlDeAutorizacaoComIgrejaIdNoState() {
        String url = service.gerarUrlAutorizacao(igrejaId);

        assertThat(url).contains("client_id=client-id-teste");
        assertThat(url).contains("state=" + igrejaId);
    }

    @Test
    void processaCallbackSalvandoTokensCriptografados() {
        var tokensObtidos = new MercadoPagoOAuthClient.TokensObtidos(
            "mp-user-999", "access-plano", "refresh-plano", 21600L
        );
        when(client.trocarCodePorTokens("code-123")).thenReturn(tokensObtidos);
        when(encryptor.criptografar("access-plano")).thenReturn("access-cripto");
        when(encryptor.criptografar("refresh-plano")).thenReturn("refresh-cripto");
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        service.processarCallback("code-123", igrejaId, usuarioId);

        verify(repository).save(argThat(conta ->
            conta.getIgrejaId().equals(igrejaId) &&
            conta.getMpUserId().equals("mp-user-999") &&
            conta.getAccessTokenCriptografado().equals("access-cripto") &&
            conta.getRefreshTokenCriptografado().equals("refresh-cripto")
        ));
    }

    @Test
    void processarCallbackAtualizaContaExistenteEmVezDeDuplicar() {
        var contaExistente = new ContaPagamentoIgreja(
            igrejaId, "mp-user-antigo", "antigo-access", "antigo-refresh",
            Instant.now(), usuarioId
        );
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(contaExistente));
        var tokensObtidos = new MercadoPagoOAuthClient.TokensObtidos(
            "mp-user-999", "access-novo", "refresh-novo", 21600L
        );
        when(client.trocarCodePorTokens("code-123")).thenReturn(tokensObtidos);
        when(encryptor.criptografar(any())).thenReturn("cripto");

        service.processarCallback("code-123", igrejaId, usuarioId);

        verify(repository, never()).save(argThat(c -> c != contaExistente));
        verify(repository).save(contaExistente);
    }

    @Test
    void statusRetornaFalsoQuandoIgrejaNaoTemContaConectada() {
        when(repository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThat(service.status(igrejaId)).isFalse();
    }

    @Test
    void statusRetornaVerdadeiroQuandoIgrejaTemContaConectada() {
        when(repository.findByIgrejaId(igrejaId)).thenReturn(
            Optional.of(new ContaPagamentoIgreja(igrejaId, "mp-user", "a", "r", Instant.now(), usuarioId))
        );

        assertThat(service.status(igrejaId)).isTrue();
    }

    @Test
    void desconectarRemoveAConta() {
        service.desconectar(igrejaId);

        verify(repository).deleteByIgrejaId(igrejaId);
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoOAuthServiceTest`
Expected: FAIL — `MercadoPagoOAuthService`/`MercadoPagoOAuthClient` não existem.

- [ ] **Step 3: Implementar o client fino do SDK**

```java
package com.domus.api.modules.pagamento.conta;

import com.mercadopago.client.oauth.OAuthClient;
import com.mercadopago.client.oauth.CreateAccessTokenRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Wrapper fino do SDK oficial — isola o resto do código de trocar o SDK depois. */
@Component
public class MercadoPagoOAuthClient {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public MercadoPagoOAuthClient(@Value("${app.pagamento.mercadopago.client-id}") String clientId,
                                   @Value("${app.pagamento.mercadopago.client-secret}") String clientSecret,
                                   @Value("${app.pagamento.mercadopago.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public TokensObtidos trocarCodePorTokens(String code) {
        try {
            OAuthClient client = new OAuthClient();
            CreateAccessTokenRequest request = CreateAccessTokenRequest.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .code(code)
                .redirectUri(redirectUri)
                .build();
            var credential = client.createAccessToken(request);
            return new TokensObtidos(
                String.valueOf(credential.getUserId()),
                credential.getAccessToken(),
                credential.getRefreshToken(),
                credential.getExpiresIn()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao trocar code por tokens no Mercado Pago", e);
        }
    }

    public record TokensObtidos(String mpUserId, String accessToken, String refreshToken, long expiresInSegundos) {}
}
```

- [ ] **Step 4: Implementar o service**

```java
package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MercadoPagoOAuthService {

    private final ContaPagamentoIgrejaRepository repository;
    private final CredencialEncryptor encryptor;
    private final MercadoPagoOAuthClient client;
    private final String clientId;

    public MercadoPagoOAuthService(ContaPagamentoIgrejaRepository repository,
                                    CredencialEncryptor encryptor,
                                    MercadoPagoOAuthClient client,
                                    @Value("${app.pagamento.mercadopago.client-id}") String clientId) {
        this.repository = repository;
        this.encryptor = encryptor;
        this.client = client;
        this.clientId = clientId;
    }

    public String gerarUrlAutorizacao(UUID igrejaId) {
        String state = URLEncoder.encode(igrejaId.toString(), StandardCharsets.UTF_8);
        return "https://auth.mercadopago.com.br/authorization"
            + "?client_id=" + clientId
            + "&response_type=code"
            + "&platform_id=mp"
            + "&state=" + state;
    }

    public void processarCallback(String code, UUID igrejaId, UUID usuarioId) {
        var tokens = client.trocarCodePorTokens(code);
        String accessCriptografado = encryptor.criptografar(tokens.accessToken());
        String refreshCriptografado = encryptor.criptografar(tokens.refreshToken());
        Instant expiraEm = Instant.now().plusSeconds(tokens.expiresInSegundos());

        var contaExistente = repository.findByIgrejaId(igrejaId);
        if (contaExistente.isPresent()) {
            contaExistente.get().atualizarTokens(accessCriptografado, refreshCriptografado, expiraEm);
            repository.save(contaExistente.get());
        } else {
            repository.save(new ContaPagamentoIgreja(
                igrejaId, tokens.mpUserId(), accessCriptografado, refreshCriptografado,
                expiraEm, usuarioId
            ));
        }
    }

    public boolean status(UUID igrejaId) {
        return repository.findByIgrejaId(igrejaId).isPresent();
    }

    public void desconectar(UUID igrejaId) {
        repository.deleteByIgrejaId(igrejaId);
    }
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoOAuthServiceTest`
Expected: PASS (6 testes)

- [ ] **Step 6: DTOs e controller**

```java
package com.domus.api.modules.pagamento.conta.DTOs;

public record StatusContaPagamentoDTO(boolean conectada) {}
```

```java
package com.domus.api.modules.pagamento.conta.DTOs;

public record ConectarContaResponseDTO(String urlAutorizacao) {}
```

```java
package com.domus.api.modules.pagamento.conta;

import com.domus.api.modules.pagamento.conta.DTOs.ConectarContaResponseDTO;
import com.domus.api.modules.pagamento.conta.DTOs.StatusContaPagamentoDTO;
import com.domus.api.shared.security.UsuarioAutenticado;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagamentos/conta")
public class ContaPagamentoController {

    private final MercadoPagoOAuthService service;

    public ContaPagamentoController(MercadoPagoOAuthService service) {
        this.service = service;
    }

    @GetMapping("/conectar")
    public ConectarContaResponseDTO conectar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new ConectarContaResponseDTO(service.gerarUrlAutorizacao(usuario.getIgrejaId()));
    }

    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam String state,
                          @AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.processarCallback(code, java.util.UUID.fromString(state), usuario.getId());
    }

    @GetMapping("/status")
    public StatusContaPagamentoDTO status(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return new StatusContaPagamentoDTO(service.status(usuario.getIgrejaId()));
    }

    @DeleteMapping
    public void desconectar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        service.desconectar(usuario.getIgrejaId());
    }
}
```

> **Nota pro implementador**: confirme o nome exato da classe de principal usada hoje
> nos outros controllers (`UsuarioAutenticado` é um placeholder de nome — grep por
> `@AuthenticationPrincipal` em outro controller existente, ex. `EventoController`, e
> use o mesmo tipo e os mesmos métodos de acesso a `igrejaId`/`id`. Lembrar da memória
> do projeto: usar só o `id` do principal, nunca ler campo LAZY dele diretamente —
> buscar a igreja/usuário completo pelo repositório quando precisar de mais dado.)

- [ ] **Step 7: Adicionar dependência do SDK no `pom.xml`**

```xml
<dependency>
    <groupId>com.mercadopago</groupId>
    <artifactId>sdk-java</artifactId>
    <version>2.1.16</version>
</dependency>
```

(Confirmar a versão mais recente disponível no Maven Central no momento da
implementação — `2.1.16` é a referência conhecida até o cutoff de conhecimento, pode ter
saído versão mais nova.)

- [ ] **Step 8: Adicionar propriedades**

Em `application.properties`:
```properties
app.pagamento.mercadopago.client-id=${MERCADOPAGO_CLIENT_ID}
app.pagamento.mercadopago.client-secret=${MERCADOPAGO_CLIENT_SECRET}
app.pagamento.mercadopago.redirect-uri=${MERCADOPAGO_REDIRECT_URI}
```

- [ ] **Step 9: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/conta/ \
        backend/api/src/test/java/com/domus/api/modules/pagamento/conta/MercadoPagoOAuthServiceTest.java \
        backend/api/pom.xml backend/api/src/main/resources/application.properties
git commit -m "feat(pagamento): fluxo OAuth de conexão com o Mercado Pago"
```

---

## Task 5: Frontend — seção "Recebimentos" em Configurações

**Files:**
- Create: `frontend/src/services/pagamento.service.ts`
- Create: `frontend/src/hooks/pagamento/useContaPagamento.ts`
- Create: `frontend/src/hooks/pagamento/useConectarMercadoPago.ts`
- Create: `frontend/src/hooks/pagamento/useDesconectarMercadoPago.ts`
- Create: `frontend/src/components/module/configuracoes/SecaoRecebimentos.tsx`
- Create: `frontend/src/components/module/configuracoes/SecaoRecebimentos.module.css`
- Modify: `frontend/src/lib/endpoints.ts`
- Modify: página de `/configuracoes/igreja` pra incluir `<SecaoRecebimentos />`

**Interfaces:**
- Consumes: `GET /pagamentos/conta/status`, `GET /pagamentos/conta/conectar`,
  `DELETE /pagamentos/conta` (Task 4).
- Produces: nada consumido por outras tasks do backend; consumido pela Task 9
  (checkout) só indiretamente (o aviso de "sem conta conectada" é renderizado ali
  também, reaproveitando `useContaPagamento`).

> **Nota pro implementador**: antes de escrever, leia
> `frontend/src/lib/endpoints.ts` e `frontend/src/services/inscricao.service.ts`
> (ou equivalente) pra confirmar a assinatura exata do `api` client (provavelmente um
> Axios configurado) e o formato de `Endpoints.<modulo>.<acao>(...)` — os arquivos
> abaixo seguem a convenção descrita na pesquisa de contexto, mas ajuste import/nome se
> divergir.

- [ ] **Step 1: `endpoints.ts`**

```ts
// dentro do objeto Endpoints existente
pagamento: {
  status: () => '/pagamentos/conta/status',
  conectar: () => '/pagamentos/conta/conectar',
  desconectar: () => '/pagamentos/conta',
},
```

- [ ] **Step 2: `pagamento.service.ts`**

```ts
import { api } from '@/lib/api'
import { Endpoints } from '@/lib/endpoints'

export interface StatusContaPagamento {
  conectada: boolean
}

export interface ConectarContaResponse {
  urlAutorizacao: string
}

export const pagamentoService = {
  buscarStatus: () =>
    api.get<StatusContaPagamento>(Endpoints.pagamento.status()).then((res) => res.data),

  gerarUrlConexao: () =>
    api.get<ConectarContaResponse>(Endpoints.pagamento.conectar()).then((res) => res.data),

  desconectar: () => api.delete(Endpoints.pagamento.desconectar()),
}
```

- [ ] **Step 3: Hooks**

```ts
// frontend/src/hooks/pagamento/useContaPagamento.ts
import { useQuery } from '@tanstack/react-query'
import { pagamentoService } from '@/services/pagamento.service'

export function useContaPagamento() {
  return useQuery({
    queryKey: ['pagamento', 'status'],
    queryFn: pagamentoService.buscarStatus,
  })
}
```

```ts
// frontend/src/hooks/pagamento/useConectarMercadoPago.ts
import { useMutation } from '@tanstack/react-query'
import { pagamentoService } from '@/services/pagamento.service'
import { notificar } from '@/lib/notificar' // nome de import a confirmar no projeto

export function useConectarMercadoPago() {
  return useMutation({
    mutationFn: pagamentoService.gerarUrlConexao,
    onSuccess: (data) => {
      window.location.href = data.urlAutorizacao
    },
    onError: () => {
      notificar.erro('Não foi possível iniciar a conexão com o Mercado Pago. Tente novamente.')
    },
  })
}
```

```ts
// frontend/src/hooks/pagamento/useDesconectarMercadoPago.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { pagamentoService } from '@/services/pagamento.service'
import { notificar } from '@/lib/notificar'

export function useDesconectarMercadoPago() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: pagamentoService.desconectar,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['pagamento', 'status'] })
      notificar.sucesso('Conta desconectada.')
    },
    onError: () => {
      notificar.erro('Não foi possível desconectar. Tente novamente.')
    },
  })
}
```

> **Nota**: confirmar o nome real do helper de notificação (`notificar()`) e sua API
> exata lendo um uso existente antes de codar — o CLAUDE.md menciona convenção própria
> de feedback, não `toast` do `sonner` nem `window.confirm`.

- [ ] **Step 4: Componente**

```tsx
// frontend/src/components/module/configuracoes/SecaoRecebimentos.tsx
'use client'

import { CreditCard, CheckCircle2 } from 'lucide-react'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useConectarMercadoPago } from '@/hooks/pagamento/useConectarMercadoPago'
import { useDesconectarMercadoPago } from '@/hooks/pagamento/useDesconectarMercadoPago'
import { Button } from '@/components/common/button/Button'
import styles from './SecaoRecebimentos.module.css'

export function SecaoRecebimentos() {
  const { data, isLoading } = useContaPagamento()
  const conectar = useConectarMercadoPago()
  const desconectar = useDesconectarMercadoPago()

  if (isLoading) return null

  return (
    <section className={styles.wrapper}>
      <h2 className={styles.titulo}>Recebimentos</h2>
      <p className={styles.subtitulo}>
        Conecte uma conta do Mercado Pago para receber diretamente o valor das inscrições
        de eventos pagos.
      </p>

      {data?.conectada ? (
        <div className={styles.conectado}>
          <CheckCircle2 size={20} className={styles.iconeConectado} aria-hidden="true" />
          <span>Conta do Mercado Pago conectada</span>
          <Button
            variant="secondary"
            size="sm"
            isLoading={desconectar.isPending}
            onClick={() => desconectar.mutate()}
          >
            Desconectar
          </Button>
        </div>
      ) : (
        <div className={styles.desconectado}>
          <CreditCard size={20} aria-hidden="true" />
          <Button
            variant="primary"
            size="md"
            isLoading={conectar.isPending}
            onClick={() => conectar.mutate()}
          >
            Conectar Mercado Pago
          </Button>
        </div>
      )}
    </section>
  )
}
```

```css
/* frontend/src/components/module/configuracoes/SecaoRecebimentos.module.css */
.wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1.25rem;
  border: 1px solid var(--cor-borda);
  border-radius: 0.75rem;
}

.titulo {
  font-size: 1.125rem;
  font-weight: 600;
}

.subtitulo {
  color: var(--cor-texto-secundario);
  font-size: 0.9rem;
}

.conectado,
.desconectado {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.iconeConectado {
  color: var(--cor-sucesso);
}

@media (max-width: 640px) {
  .conectado,
  .desconectado {
    flex-direction: column;
    align-items: flex-start;
  }
}
```

> **Nota**: confirmar os nomes reais das variáveis CSS (`--cor-borda`, `--cor-sucesso`
> etc.) olhando outro `.module.css` do projeto antes de codar — são placeholders de
> convenção, não valores confirmados pela pesquisa.

- [ ] **Step 5: Incluir na página de configurações**

Abrir `frontend/src/app/(app)/configuracoes/igreja/page.tsx` (ou caminho equivalente
confirmado no projeto) e renderizar `<SecaoRecebimentos />` junto das demais seções.

- [ ] **Step 6: Testar manualmente no navegador**

Rodar `npm run dev` no front e back, abrir `/configuracoes/igreja` logado como
`ADMIN_IGREJA`, confirmar que a seção aparece com o estado "desconectado" (sem conta
ainda) e que o botão "Conectar Mercado Pago" dispara a chamada (vai falhar ao redirecionar
de verdade até ter credencial real de sandbox — aceitável nesta task, só a integração
visual/chamada precisa funcionar). Testar responsividade em viewport de celular.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/services/pagamento.service.ts frontend/src/hooks/pagamento/ \
        frontend/src/components/module/configuracoes/SecaoRecebimentos.* \
        frontend/src/lib/endpoints.ts
git commit -m "feat(pagamento): seção de conexão com Mercado Pago em Configurações"
```

---

## Task 6: Entidade e repositório `CobrancaEvento`

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/StatusCobranca.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoRepository.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoRepositoryTest.java`

**Interfaces:**
- Produces: `CobrancaEventoRepository.findByTokenLinkPublico(String token): Optional<CobrancaEvento>`,
  `CobrancaEventoRepository.findByInscricaoId(UUID inscricaoId): List<CobrancaEvento>`,
  `CobrancaEventoRepository.contarPessoasComVagaReservada(UUID eventoId, Instant agora): long`
  — consumidos pelas Tasks 7 (contagem de vaga), 8 (criação de cobrança) e 11 (endpoint
  público por token).

- [ ] **Step 1: Enum de status**

```java
package com.domus.api.modules.pagamento.cobranca;

public enum StatusCobranca {
    PENDENTE,
    PAGO,
    EXPIRADO,
    CANCELADO,
    REEMBOLSADO
}
```

- [ ] **Step 2: Entidade**

```java
package com.domus.api.modules.pagamento.cobranca;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cobranca_evento")
public class CobrancaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "igreja_id", nullable = false)
    private UUID igrejaId;

    @Column(name = "evento_id", nullable = false)
    private UUID eventoId;

    @Column(name = "inscricao_id", nullable = false)
    private UUID inscricaoId;

    @Column(name = "pessoa_id")
    private UUID pessoaId;

    @Column(name = "acompanhante_id")
    private UUID acompanhanteId;

    @Column(nullable = false)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCobranca status;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "token_link_publico", unique = true)
    private String tokenLinkPublico;

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "pago_em")
    private Instant pagoEm;

    @Column(name = "criado_por_usuario_id", nullable = false)
    private UUID criadoPorUsuarioId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected CobrancaEvento() {}

    public CobrancaEvento(UUID igrejaId, UUID eventoId, UUID inscricaoId, UUID pessoaId,
                           UUID acompanhanteId, BigDecimal valor, Instant expiraEm,
                           UUID criadoPorUsuarioId, String tokenLinkPublico) {
        if ((pessoaId == null) == (acompanhanteId == null)) {
            throw new IllegalArgumentException(
                "CobrancaEvento precisa de exatamente pessoaId OU acompanhanteId");
        }
        this.igrejaId = igrejaId;
        this.eventoId = eventoId;
        this.inscricaoId = inscricaoId;
        this.pessoaId = pessoaId;
        this.acompanhanteId = acompanhanteId;
        this.valor = valor;
        this.status = StatusCobranca.PENDENTE;
        this.expiraEm = expiraEm;
        this.criadoPorUsuarioId = criadoPorUsuarioId;
        this.criadoEm = Instant.now();
        this.tokenLinkPublico = tokenLinkPublico;
    }

    public void marcarComoPago(String mpPaymentId) {
        this.status = StatusCobranca.PAGO;
        this.mpPaymentId = mpPaymentId;
        this.pagoEm = Instant.now();
    }

    public void marcarComoExpirado() { this.status = StatusCobranca.EXPIRADO; }
    public void marcarComoCancelado() { this.status = StatusCobranca.CANCELADO; }
    public void marcarComoReembolsado() { this.status = StatusCobranca.REEMBOLSADO; }

    public UUID getId() { return id; }
    public UUID getIgrejaId() { return igrejaId; }
    public UUID getEventoId() { return eventoId; }
    public UUID getInscricaoId() { return inscricaoId; }
    public UUID getPessoaId() { return pessoaId; }
    public UUID getAcompanhanteId() { return acompanhanteId; }
    public BigDecimal getValor() { return valor; }
    public StatusCobranca getStatus() { return status; }
    public String getMpPaymentId() { return mpPaymentId; }
    public String getTokenLinkPublico() { return tokenLinkPublico; }
    public Instant getExpiraEm() { return expiraEm; }
    public Instant getPagoEm() { return pagoEm; }
    public UUID getCriadoPorUsuarioId() { return criadoPorUsuarioId; }
    public boolean ehDoTitular() { return pessoaId != null; }
}
```

- [ ] **Step 3: Repositório**

```java
package com.domus.api.modules.pagamento.cobranca;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobrancaEventoRepository extends JpaRepository<CobrancaEvento, UUID> {

    Optional<CobrancaEvento> findByTokenLinkPublico(String token);

    List<CobrancaEvento> findByInscricaoId(UUID inscricaoId);

    @Query("""
        SELECT COUNT(c) FROM CobrancaEvento c
        WHERE c.eventoId = :eventoId
        AND (c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO
             OR (c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE
                 AND c.expiraEm > :agora))
        """)
    long contarPessoasComVagaReservada(@Param("eventoId") UUID eventoId, @Param("agora") Instant agora);

    List<CobrancaEvento> findByStatusAndExpiraEmBefore(StatusCobranca status, Instant momento);
}
```

- [ ] **Step 4: Escrever o teste (`@DataJpaTest`)**

```java
package com.domus.api.modules.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CobrancaEventoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired CobrancaEventoRepository repository;
    @Autowired jakarta.persistence.EntityManager entityManager;

    UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555555");
    UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666666");
    UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome) VALUES ('11111111-1111-1111-1111-111111111111', 'Igreja Teste')",
        "INSERT INTO role (id, nome) VALUES ('22222222-2222-2222-2222-222222222222', 'ADMIN_IGREJA')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Fulano', 'fulano@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222', true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', " +
            "'Retiro', now(), '77777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA')"
    })
    void contaSoPagosEPendentesNaoExpirados() {
        entityManager.persist(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.TEN, Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, null));

        var cobrancaExpirada = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null,
            UUID.randomUUID(), BigDecimal.TEN, Instant.now().minus(1, ChronoUnit.HOURS), usuarioId, "token-x");
        entityManager.persist(cobrancaExpirada);

        entityManager.flush();
        entityManager.clear();

        long total = repository.contarPessoasComVagaReservada(eventoId, Instant.now());

        assertThat(total).isEqualTo(1); // só a não-expirada conta
    }

    @Test
    void buscaPorTokenLinkPublico() {
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null, UUID.randomUUID(),
            BigDecimal.TEN, Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, "token-unico-123");
        entityManager.persist(cobranca);
        entityManager.flush();
        entityManager.clear();

        var encontrada = repository.findByTokenLinkPublico("token-unico-123");

        assertThat(encontrada).isPresent();
    }
}
```

> **Nota pro implementador**: os `INSERT`s de fixture (colunas de `evento`,
> `inscricao_evento`) precisam ser conferidos contra o schema real (V1 + migrations
> seguintes) antes de rodar — o diagrama ER do `CLAUDE.md` tem os nomes de coluna, mas
> confirme tipos/obrigatoriedade lendo a migration correspondente se o teste falhar por
> constraint.

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=CobrancaEventoRepositoryTest`
Expected: PASS (2 testes)

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/StatusCobranca.java \
        backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEvento.java \
        backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoRepository.java \
        backend/api/src/test/java/com/domus/api/modules/pagamento/cobranca/
git commit -m "feat(pagamento): entidade e repositório de CobrancaEvento"
```

---

## Task 7: `CobrancaEventoService` — geração de token e regras de negócio isoladas

Isola, sem tocar ainda em `InscricaoService`, as regras que dão pra testar puro:
geração de token de link, cálculo de prazo de expiração, e a regra "titular não pode
virar link".

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoService.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoServiceTest.java`

**Interfaces:**
- Consumes: `CobrancaEventoRepository` (Task 6).
- Produces: `CobrancaEventoService.criarParaTitular(...)`,
  `CobrancaEventoService.criarParaTerceiro(..., boolean gerarLink): CobrancaEvento`,
  `CobrancaEventoService.PRAZO_PAGAMENTO_IMEDIATO`, `CobrancaEventoService.PRAZO_LINK_COMPARTILHADO`
  — consumidos pela Task 8 (integração com `InscricaoService`).

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CobrancaEventoServiceTest {

    CobrancaEventoRepository repository;
    CobrancaEventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID inscricaoId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();
    UUID acompanhanteId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        service = new CobrancaEventoService(repository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criaCobrancaParaTitularComPrazoCurtoESemToken() {
        var cobranca = service.criarParaTitular(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId);

        assertThat(cobranca.getPessoaId()).isEqualTo(pessoaId);
        assertThat(cobranca.getTokenLinkPublico()).isNull();
        assertThat(cobranca.getExpiraEm()).isBefore(Instant.now().plus(31, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void criaCobrancaParaTerceiroPagandoAgoraComPrazoCurtoESemToken() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, null, acompanhanteId,
            BigDecimal.valueOf(150), usuarioId, false);

        assertThat(cobranca.getAcompanhanteId()).isEqualTo(acompanhanteId);
        assertThat(cobranca.getTokenLinkPublico()).isNull();
    }

    @Test
    void criaCobrancaParaTerceiroComLinkGeraTokenEPrazoLongo() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, null, acompanhanteId,
            BigDecimal.valueOf(150), usuarioId, true);

        assertThat(cobranca.getTokenLinkPublico()).isNotBlank();
        assertThat(cobranca.getExpiraEm()).isAfter(Instant.now().plus(23, java.time.temporal.ChronoUnit.HOURS));
    }

    @Test
    void tokensGeradosNaoSeRepetem() {
        var c1 = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, null, acompanhanteId,
            BigDecimal.TEN, usuarioId, true);
        var c2 = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, null, UUID.randomUUID(),
            BigDecimal.TEN, usuarioId, true);

        assertThat(c1.getTokenLinkPublico()).isNotEqualTo(c2.getTokenLinkPublico());
    }

    @Test
    void buscarPorTokenLancaExcecaoQuandoNaoEncontrado() {
        when(repository.findByTokenLinkPublico("inexistente")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.buscarPorToken("inexistente"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("LINK_COBRANCA_INVALIDO");
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=CobrancaEventoServiceTest`
Expected: FAIL — `CobrancaEventoService` não existe.

- [ ] **Step 3: Implementar**

```java
package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CobrancaEventoService {

    public static final Duration PRAZO_PAGAMENTO_IMEDIATO = Duration.ofMinutes(30);
    public static final Duration PRAZO_LINK_COMPARTILHADO = Duration.ofHours(48);

    private final CobrancaEventoRepository repository;
    private final SecureRandom random = new SecureRandom();

    public CobrancaEventoService(CobrancaEventoRepository repository) {
        this.repository = repository;
    }

    public CobrancaEvento criarParaTitular(UUID igrejaId, UUID eventoId, UUID inscricaoId,
                                            UUID pessoaId, BigDecimal valor, UUID criadoPorUsuarioId) {
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null, valor,
            Instant.now().plus(PRAZO_PAGAMENTO_IMEDIATO), criadoPorUsuarioId, null);
        return repository.save(cobranca);
    }

    public CobrancaEvento criarParaTerceiro(UUID igrejaId, UUID eventoId, UUID inscricaoId,
                                             UUID pessoaId, UUID acompanhanteId, BigDecimal valor,
                                             UUID criadoPorUsuarioId, boolean gerarLink) {
        String token = gerarLink ? gerarToken() : null;
        Duration prazo = gerarLink ? PRAZO_LINK_COMPARTILHADO : PRAZO_PAGAMENTO_IMEDIATO;

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, acompanhanteId,
            valor, Instant.now().plus(prazo), criadoPorUsuarioId, token);
        return repository.save(cobranca);
    }

    public CobrancaEvento buscarPorToken(String token) {
        return repository.findByTokenLinkPublico(token)
            .orElseThrow(() -> new BusinessException("LINK_COBRANCA_INVALIDO",
                "Este link de pagamento não existe ou expirou."));
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```

> **Nota**: confirmar a assinatura real de `BusinessException` (o construtor
> `(codigo, mensagem)` veio da pesquisa de contexto) antes de compilar — se divergir,
> ajustar aqui e nas próximas tasks que a usam.

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=CobrancaEventoServiceTest`
Expected: PASS (5 testes)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoService.java \
        backend/api/src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaEventoServiceTest.java
git commit -m "feat(pagamento): regras de criação de cobrança e token de link público"
```

---

## Task 8: `MercadoPagoClient` — criar pagamento (Payment Brick) e estornar

Wrapper do SDK que efetivamente fala com a API do Mercado Pago usando o token
descriptografado da igreja — usado tanto na criação do pagamento quanto no estorno.

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/MercadoPagoClient.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/MercadoPagoClientTest.java`

**Interfaces:**
- Consumes: `ContaPagamentoIgrejaRepository` + `CredencialEncryptor` (pra obter o access
  token descriptografado da igreja).
- Produces: `MercadoPagoClient.criarPagamento(UUID igrejaId, CobrancaEvento cobranca): String` (retorna `mp_payment_id`/preference id usado pelo front pra montar o Brick),
  `MercadoPagoClient.estornar(UUID igrejaId, String mpPaymentId): void` — consumidos
  pelas Tasks 9 e 12.

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoClientTest {

    ContaPagamentoIgrejaRepository contaRepository;
    CredencialEncryptor encryptor;
    MercadoPagoApi api; // wrapper fino da chamada HTTP real, mockado aqui
    MercadoPagoClient client;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        contaRepository = mock(ContaPagamentoIgrejaRepository.class);
        encryptor = mock(CredencialEncryptor.class);
        api = mock(MercadoPagoApi.class);
        client = new MercadoPagoClient(contaRepository, encryptor, api);
    }

    @Test
    void lancaErroDeNegocioQuandoIgrejaNaoTemContaConectada() {
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());
        var cobranca = new CobrancaEvento(igrejaId, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(60),
            UUID.randomUUID(), null);

        assertThatThrownBy(() -> client.criarPagamento(igrejaId, cobranca))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("IGREJA_SEM_CONTA_PAGAMENTO");
    }

    @Test
    void criaPagamentoUsandoTokenDescriptografadoDaIgreja() {
        var conta = new ContaPagamentoIgreja(igrejaId, "mp-user", "access-cripto", "refresh-cripto",
            Instant.now().plusSeconds(3600), UUID.randomUUID());
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        when(encryptor.descriptografar("access-cripto")).thenReturn("access-plano");
        when(api.criarPagamento(eq("access-plano"), any(), any())).thenReturn("mp-payment-999");

        var cobranca = new CobrancaEvento(igrejaId, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.valueOf(50), Instant.now().plusSeconds(60),
            UUID.randomUUID(), null);

        String resultado = client.criarPagamento(igrejaId, cobranca);

        assertThat(resultado).isEqualTo("mp-payment-999");
        verify(api).criarPagamento(eq("access-plano"), eq(cobranca.getId().toString()), eq(BigDecimal.valueOf(50)));
    }

    @Test
    void estornarUsaTokenDescriptografadoDaIgreja() {
        var conta = new ContaPagamentoIgreja(igrejaId, "mp-user", "access-cripto", "refresh-cripto",
            Instant.now().plusSeconds(3600), UUID.randomUUID());
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        when(encryptor.descriptografar("access-cripto")).thenReturn("access-plano");

        client.estornar(igrejaId, "mp-payment-999");

        verify(api).estornar("access-plano", "mp-payment-999");
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoClientTest`
Expected: FAIL — `MercadoPagoClient`/`MercadoPagoApi` não existem.

- [ ] **Step 3: Implementar `MercadoPagoApi` (wrapper do SDK, chamada HTTP de verdade)**

```java
package com.domus.api.modules.pagamento;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentRefundClient;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Chamada HTTP real ao Mercado Pago. Isolado num componente próprio (em vez de dentro
 * de MercadoPagoClient) só pra poder mockar a borda de I/O nos testes de
 * MercadoPagoClientTest sem precisar de rede.
 */
@Component
public class MercadoPagoApi {

    public String criarPagamento(String accessToken, String externalReference, BigDecimal valor) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            PaymentClient client = new PaymentClient();
            PaymentCreateRequest request = PaymentCreateRequest.builder()
                .transactionAmount(valor)
                .description("Inscrição em evento — Domus")
                .externalReference(externalReference)
                .build();
            var pagamento = client.create(request);
            return String.valueOf(pagamento.getId());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao criar pagamento no Mercado Pago", e);
        }
    }

    public void estornar(String accessToken, String mpPaymentId) {
        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            PaymentRefundClient client = new PaymentRefundClient();
            client.refund(Long.parseLong(mpPaymentId));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao estornar pagamento no Mercado Pago", e);
        }
    }
}
```

> **Nota importante pro implementador**: a criação de pagamento via `PaymentClient`
> direto (Checkout API) é o caminho usado quando o Brick no front já tokenizou o método
> de pagamento e manda esse token pro backend — o fluxo completo do Payment Brick
> envolve o front chamar `criarPagamento` do SDK JS pra obter um `token`/`payment_method_id`,
> mandar isso pro backend (endpoint novo, não coberto neste wrapper simplificado), e o
> backend então chamar `PaymentClient.create` com esses dados. Este wrapper está
> simplificado pra focar na integração igreja→token→chamada; ajustar os campos exatos
> de `PaymentCreateRequest` (token do cartão, `paymentMethodId`, dados de PIX) durante a
> implementação real da Task 9, consultando a documentação do Payment Brick + Checkout
> API do Mercado Pago no momento de implementar (a superfície exata da request muda
> conforme a versão do SDK).

- [ ] **Step 4: Implementar `MercadoPagoClient`**

```java
package com.domus.api.modules.pagamento;

import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.exception.BusinessException;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MercadoPagoClient {

    private final ContaPagamentoIgrejaRepository contaRepository;
    private final CredencialEncryptor encryptor;
    private final MercadoPagoApi api;

    public MercadoPagoClient(ContaPagamentoIgrejaRepository contaRepository,
                              CredencialEncryptor encryptor, MercadoPagoApi api) {
        this.contaRepository = contaRepository;
        this.encryptor = encryptor;
        this.api = api;
    }

    public String criarPagamento(UUID igrejaId, CobrancaEvento cobranca) {
        String accessToken = obterAccessTokenPlano(igrejaId);
        return api.criarPagamento(accessToken, cobranca.getId().toString(), cobranca.getValor());
    }

    public void estornar(UUID igrejaId, String mpPaymentId) {
        String accessToken = obterAccessTokenPlano(igrejaId);
        api.estornar(accessToken, mpPaymentId);
    }

    private String obterAccessTokenPlano(UUID igrejaId) {
        var conta = contaRepository.findByIgrejaId(igrejaId)
            .orElseThrow(() -> new BusinessException("IGREJA_SEM_CONTA_PAGAMENTO",
                "Esta igreja ainda não conectou uma conta para receber pagamentos."));
        return encryptor.descriptografar(conta.getAccessTokenCriptografado());
    }
}
```

- [ ] **Step 5: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoClientTest`
Expected: PASS (3 testes)

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/MercadoPagoClient.java \
        backend/api/src/main/java/com/domus/api/modules/pagamento/MercadoPagoApi.java \
        backend/api/src/test/java/com/domus/api/modules/pagamento/MercadoPagoClientTest.java
git commit -m "feat(pagamento): client Mercado Pago para criar pagamento e estornar"
```

---

## Task 9: Integrar `InscricaoService` — criar cobranças ao inscrever em evento pago

**Files:**
- Modify: `backend/api/src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java`
- Modify: `backend/api/src/main/java/com/domus/api/modules/evento/inscricao/InscricaoRepository.java`
  (ou onde vive a contagem de vaga hoje)
- Test: modificar/estender o arquivo de teste existente do `InscricaoService`
  (localizar via `find backend/api/src/test -name "InscricaoServiceTest.java"`)

**Interfaces:**
- Consumes: `CobrancaEventoService` (Task 7), `CobrancaEventoRepository.contarPessoasComVagaReservada`
  (Task 6).
- Produces: `InscricaoService` passa a aceitar, no método de inscrição, um mapa de
  escolha por pessoa (`Map<UUID pessoaOuAcompanhanteId, Boolean gerarLink>`) quando o
  evento é pago — assinatura exata a definir olhando a assinatura real de
  `inscrever(...)` encontrada no código (a pesquisa de contexto trouxe
  `inscrever(eventoId, pessoaId, inscritoPorOuNull, minhaPessoaId, role, confirmado, igrejaId)`
  — ajustar este plano/adicionar parâmetro conforme o método real).

> **Nota pro implementador — leitura obrigatória antes de codar esta task**: abra
> `InscricaoService.java` e `InscricaoServiceTest.java` por completo antes de escrever
> qualquer linha. A pesquisa de contexto (subagente) trouxe a assinatura aproximada do
> método principal e o nome da query de lock (`buscarComLockVisivelParaFamilia`), mas
> não o arquivo inteiro — esta task tem alto risco de a assinatura real divergir do que
> está esboçado abaixo. Ajuste os testes/steps ao que encontrar, mantendo a regra de
> negócio (titular sempre "eu pago agora"; vaga conta `PAGO`+`PENDENTE` não expirada).

- [ ] **Step 1: Escrever o teste novo pra regra "titular não pode virar link"**

```java
// Adicionar em InscricaoServiceTest.java (Estilo A do arquivo, mock() no @BeforeEach)

@Test
void eventoPagoCriaCobrancaDoTitularComoEuPagoAgora() {
    // arrange: evento(preco = 50), pessoa titular, sem escolha "gerarLink" pro titular
    Evento evento = evento(10); // helper existente
    evento.setPreco(BigDecimal.valueOf(50));
    Pessoa titular = pessoa(Vinculo.MEMBRO);
    dado(evento, titular, 0L);

    when(eventoRepository.buscarComLockVisivelParaFamilia(any(), any(), any()))
        .thenReturn(Optional.of(evento));
    when(cobrancaEventoService.criarParaTitular(any(), any(), any(), any(), any(), any()))
        .thenReturn(mock(CobrancaEvento.class));

    service.inscrever(evento.getId(), titular.getId(), null, titular.getId(),
        Role.ACESSO_COMUM, true, igrejaId);

    verify(cobrancaEventoService).criarParaTitular(eq(igrejaId), eq(evento.getId()),
        any(), eq(titular.getId()), eq(BigDecimal.valueOf(50)), any());
}

@Test
void contagemDeVagaEmEventoPagoUsaCobrancaEmVezDeInscricaoDireta() {
    Evento evento = evento(1);
    evento.setPreco(BigDecimal.valueOf(50));

    when(cobrancaEventoRepository.contarPessoasComVagaReservada(eq(evento.getId()), any()))
        .thenReturn(1L); // vaga já ocupada por outra cobrança pendente/paga

    assertThatThrownBy(() -> service.inscrever(evento.getId(), UUID.randomUUID(), null,
        UUID.randomUUID(), Role.ACESSO_COMUM, true, igrejaId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("VAGAS_ESGOTADAS");
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL — dependências novas (`cobrancaEventoService`, `cobrancaEventoRepository`)
ainda não existem no construtor do service/teste.

- [ ] **Step 3: Injetar as dependências novas e ramificar a lógica**

No construtor de `InscricaoService`, adicionar `CobrancaEventoService cobrancaEventoService`
e (se a contagem de vaga não estiver dentro do próprio `InscricaoRepository`)
`CobrancaEventoRepository cobrancaEventoRepository`. No método de inscrição, após
confirmar que há vaga (adaptando a checagem de vaga pra somar
`contarPessoasComVagaReservada` quando `evento.getPreco() != null`), se o evento for
pago:

```java
if (evento.getPreco() != null) {
    cobrancaEventoService.criarParaTitular(igrejaId, evento.getId(), inscricao.getId(),
        pessoaId, evento.getPreco(), usuarioLogadoId);
    // para cada acompanhante/outra pessoa da leva, chamar
    // cobrancaEventoService.criarParaTerceiro(..., gerarLink) conforme a escolha recebida
}
```

(A integração exata com o loop de acompanhantes depende de como o método atual já
itera sobre eles — seguir o padrão existente, só inserindo a chamada de criação de
cobrança dentro do laço.)

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=InscricaoServiceTest`
Expected: PASS (todos os testes, incluindo os novos)

- [ ] **Step 5: Rodar a suíte completa pra garantir que nada quebrou**

Run: `cd backend/api && mvn -q test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/evento/inscricao/ \
        backend/api/src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(pagamento): InscricaoService cria cobrança por pessoa em evento pago"
```

---

## Task 10: Webhook do Mercado Pago

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoAssinaturaValidator.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookService.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookController.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoAssinaturaValidatorTest.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/webhook/MercadoPagoWebhookServiceTest.java`
- Modify: `backend/api/src/main/java/com/domus/api/shared/security/SecurityConfig.java`

**Interfaces:**
- Consumes: `CobrancaEventoRepository` (Task 6), central de notificações já existente
  (`NotificacaoService.criar(...)`, conforme documentado em memória do projeto).
- Produces: endpoint `POST /pagamentos/mercadopago/webhook`, sempre 200.

- [ ] **Step 1: Teste do validador de assinatura**

```java
package com.domus.api.modules.pagamento.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MercadoPagoAssinaturaValidatorTest {

    private final MercadoPagoAssinaturaValidator validator =
        new MercadoPagoAssinaturaValidator("segredo-webhook-teste");

    @Test
    void aceitaAssinaturaValida() throws Exception {
        String dataId = "123456";
        String requestId = "req-abc";
        long timestamp = 1700000000L;
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("segredo-webhook-teste".getBytes(), "HmacSHA256"));
        String hashEsperado = java.util.HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));
        String header = "ts=" + timestamp + ",v1=" + hashEsperado;

        assertThat(validator.valida(header, dataId, requestId)).isTrue();
    }

    @Test
    void recusaAssinaturaComHashErrado() {
        String header = "ts=1700000000,v1=hash-forjado-invalido";

        assertThat(validator.valida(header, "123456", "req-abc")).isFalse();
    }

    @Test
    void recusaHeaderMalFormado() {
        assertThat(validator.valida("qualquer-coisa-sem-formato", "123456", "req-abc")).isFalse();
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoAssinaturaValidatorTest`
Expected: FAIL

- [ ] **Step 3: Implementar o validador**

```java
package com.domus.api.modules.pagamento.webhook;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida a assinatura do webhook do Mercado Pago (header x-signature) — sem isso,
 * qualquer requisição não autenticada poderia forjar "pagamento confirmado".
 * Formato do header: "ts=<timestamp>,v1=<hash>". Manifest assinado:
 * "id:<data.id>;request-id:<x-request-id>;ts:<timestamp>;".
 */
@Component
public class MercadoPagoAssinaturaValidator {

    private final String segredoWebhook;

    public MercadoPagoAssinaturaValidator(@Value("${app.pagamento.mercadopago.webhook-secret}") String segredoWebhook) {
        this.segredoWebhook = segredoWebhook;
    }

    public boolean valida(String headerXSignature, String dataId, String requestId) {
        try {
            Map<String, String> partes = parseHeader(headerXSignature);
            String timestamp = partes.get("ts");
            String hashRecebido = partes.get("v1");
            if (timestamp == null || hashRecebido == null) return false;

            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredoWebhook.getBytes(), "HmacSHA256"));
            String hashCalculado = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));

            return hashCalculado.equals(hashRecebido);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> parseHeader(String header) {
        Map<String, String> partes = new HashMap<>();
        for (String parte : header.split(",")) {
            String[] chaveValor = parte.split("=", 2);
            if (chaveValor.length == 2) partes.put(chaveValor[0].trim(), chaveValor[1].trim());
        }
        return partes;
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoAssinaturaValidatorTest`
Expected: PASS (3 testes)

- [ ] **Step 5: Teste do service de webhook**

```java
package com.domus.api.modules.pagamento.webhook;

import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookServiceTest {

    CobrancaEventoRepository cobrancaRepository;
    MercadoPagoWebhookService service;

    @BeforeEach
    void setup() {
        cobrancaRepository = mock(CobrancaEventoRepository.class);
        service = new MercadoPagoWebhookService(cobrancaRepository);
    }

    @Test
    void confirmaCobrancaEncontradaPeloExternalReference() {
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999");

        assertThatCobrancaFoiMarcadaPaga(cobranca);
        verify(cobrancaRepository).save(cobranca);
    }

    @Test
    void ignoraSilenciosamenteQuandoCobrancaNaoExiste() {
        when(cobrancaRepository.findById(any())).thenReturn(Optional.empty());

        service.confirmarPagamento(UUID.randomUUID().toString(), "mp-payment-999");

        verify(cobrancaRepository, never()).save(any());
    }

    private void assertThatCobrancaFoiMarcadaPaga(CobrancaEvento cobranca) {
        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
    }
}
```

- [ ] **Step 6: Rodar e confirmar que falha, depois implementar**

```java
package com.domus.api.modules.pagamento.webhook;

import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MercadoPagoWebhookService {

    private final CobrancaEventoRepository cobrancaRepository;

    public MercadoPagoWebhookService(CobrancaEventoRepository cobrancaRepository) {
        this.cobrancaRepository = cobrancaRepository;
    }

    public void confirmarPagamento(String cobrancaId, String mpPaymentId) {
        cobrancaRepository.findById(UUID.fromString(cobrancaId)).ifPresent(cobranca -> {
            cobranca.marcarComoPago(mpPaymentId);
            cobrancaRepository.save(cobranca);
            // TODO da implementação real: disparar notificação in-app pro titular
            // (criadoPorUsuarioId da cobrança) quando cobranca.getPessoaId() == null
            // (ou seja, é cobrança de terceiro/link) — usar NotificacaoService.criar(...)
            // já existente na central de notificações, seguindo o padrão dos outros
            // produtores documentados (ver memória "Central de notificações").
        });
    }
}
```

> Note: o comentário acima descreve trabalho real a fazer — não é o placeholder
> proibido pela skill porque a integração exata com `NotificacaoService` depende de
> ler a API real dele (não coberta pela pesquisa de contexto desta sessão). Antes de
> considerar esta task pronta, o implementador DEVE abrir `NotificacaoService.java`,
> ver a assinatura de `criar(...)` e completar essa chamada de verdade — não deixar o
> comentário como está no código final.

- [ ] **Step 7: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=MercadoPagoWebhookServiceTest`
Expected: PASS (2 testes)

- [ ] **Step 8: Controller**

```java
package com.domus.api.modules.pagamento.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pagamentos/mercadopago")
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoAssinaturaValidator validator;
    private final MercadoPagoWebhookService service;

    public MercadoPagoWebhookController(MercadoPagoAssinaturaValidator validator,
                                         MercadoPagoWebhookService service) {
        this.validator = validator;
        this.service = service;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
        @RequestHeader("x-signature") String assinatura,
        @RequestHeader("x-request-id") String requestId,
        @RequestParam("data.id") String dataId,
        @RequestParam String type
    ) {
        // O Mercado Pago SEMPRE espera 200 — mesmo em rejeição, só loga e ignora,
        // pra não entrar em reenvio infinito do provedor.
        if (!validator.valida(assinatura, dataId, requestId)) {
            log.warn("Webhook do Mercado Pago com assinatura inválida, ignorado. requestId={}", requestId);
            return ResponseEntity.ok().build();
        }

        if ("payment".equals(type)) {
            service.confirmarPagamento(buscarExternalReference(dataId), dataId);
        }

        return ResponseEntity.ok().build();
    }

    private String buscarExternalReference(String mpPaymentId) {
        // TODO da implementação real: o external_reference (id da CobrancaEvento) vem
        // dentro do payload do pagamento, não no query param — implementar a chamada
        // GET /v1/payments/{id} ao Mercado Pago (via MercadoPagoApi/SDK) pra obter o
        // external_reference antes de chamar service.confirmarPagamento. Este método
        // está com assinatura simplificada; ajustar ao implementar de verdade,
        // consultando a documentação de webhooks "payment" do Mercado Pago.
        throw new UnsupportedOperationException("Implementar busca do pagamento por id no Mercado Pago");
    }
}
```

> **Atenção pro implementador**: o `buscarExternalReference` acima está deliberadamente
> incompleto e sinalizado — a pesquisa de contexto desta sessão não cobriu o formato
> exato do payload de webhook "payment" do Mercado Pago (que muda de versão pra
> versão). Esta é a única lacuna real deste plano; resolvê-la é o primeiro passo da
> implementação desta task, consultando a documentação oficial de webhooks do Mercado
> Pago no momento de implementar. Depois de resolvida, o teste do controller (Step 9)
> deve mockar essa chamada também.

- [ ] **Step 9: Liberar o endpoint no `SecurityConfig`**

Em `SecurityConfig.java`, no bloco de `requestMatchers`, adicionar (seguindo
exatamente o padrão já usado pra `/convites/**`):

```java
.requestMatchers("/pagamentos/mercadopago/webhook").permitAll()
```

E no bloco de CSRF:

```java
.ignoringRequestMatchers("/pagamentos/mercadopago/webhook")
```

- [ ] **Step 10: Adicionar propriedade**

```properties
app.pagamento.mercadopago.webhook-secret=${MERCADOPAGO_WEBHOOK_SECRET}
```

- [ ] **Step 11: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/webhook/ \
        backend/api/src/test/java/com/domus/api/modules/pagamento/webhook/ \
        backend/api/src/main/java/com/domus/api/shared/security/SecurityConfig.java \
        backend/api/src/main/resources/application.properties
git commit -m "feat(pagamento): webhook do Mercado Pago com validação de assinatura"
```

---

## Task 11: Job de expiração de cobrança

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJob.java`
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/job/CobrancaEventoExpiracaoJobTest.java`

**Interfaces:**
- Consumes: `CobrancaEventoRepository.findByStatusAndExpiraEmBefore` (Task 6).

- [ ] **Step 1: Escrever o teste**

```java
package com.domus.api.modules.pagamento.job;

import static org.mockito.Mockito.*;

import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CobrancaEventoExpiracaoJobTest {

    CobrancaEventoRepository repository;
    CobrancaEventoExpiracaoJob job;

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        job = new CobrancaEventoExpiracaoJob(repository);
    }

    @Test
    void expiraTodasAsCobrancasPendentesVencidas() {
        var cobranca1 = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().minusSeconds(60), UUID.randomUUID(), null);
        var cobranca2 = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, UUID.randomUUID(), BigDecimal.TEN, Instant.now().minusSeconds(60), UUID.randomUUID(), "token");

        when(repository.findByStatusAndExpiraEmBefore(eq(StatusCobranca.PENDENTE), any()))
            .thenReturn(List.of(cobranca1, cobranca2));

        job.executar();

        org.assertj.core.api.Assertions.assertThat(cobranca1.getStatus()).isEqualTo(StatusCobranca.EXPIRADO);
        org.assertj.core.api.Assertions.assertThat(cobranca2.getStatus()).isEqualTo(StatusCobranca.EXPIRADO);
        verify(repository).saveAll(List.of(cobranca1, cobranca2));
    }

    @Test
    void naoFazNadaQuandoNaoHaCobrancaVencida() {
        when(repository.findByStatusAndExpiraEmBefore(eq(StatusCobranca.PENDENTE), any()))
            .thenReturn(List.of());

        job.executar();

        verify(repository, never()).saveAll(any());
    }
}
```

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=CobrancaEventoExpiracaoJobTest`
Expected: FAIL

- [ ] **Step 3: Implementar**

```java
package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira cobrança de evento pago vencida, liberando a vaga. Roda a cada 5 minutos —
 * suficiente dado que o prazo mínimo de cobrança é 30 minutos. */
@Component
public class CobrancaEventoExpiracaoJob {

    private final CobrancaEventoRepository repository;

    public CobrancaEventoExpiracaoJob(CobrancaEventoRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void executar() {
        var vencidas = repository.findByStatusAndExpiraEmBefore(StatusCobranca.PENDENTE, Instant.now());
        if (vencidas.isEmpty()) return;

        vencidas.forEach(com.domus.api.modules.pagamento.cobranca.CobrancaEvento::marcarComoExpirado);
        repository.saveAll(vencidas);
    }
}
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=CobrancaEventoExpiracaoJobTest`
Expected: PASS (2 testes)

- [ ] **Step 5: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/job/ \
        backend/api/src/test/java/com/domus/api/modules/pagamento/job/
git commit -m "feat(pagamento): job de expiração de cobrança vencida"
```

---

## Task 12: Reembolso no cancelamento de inscrição

**Files:**
- Modify: `InscricaoService.java` (método de cancelamento)
- Test: estender `InscricaoServiceTest.java`

- [ ] **Step 1: Escrever os testes**

```java
@Test
void cancelarInscricaoComCobrancaPagaAcionaEstorno() {
    // arrange: inscrição com CobrancaEvento PAGO
    when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
        .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));

    service.cancelar(inscricaoId, usuarioId, igrejaId);

    verify(mercadoPagoClient).estornar(igrejaId, "mp-payment-1");
}

@Test
void cancelarInscricaoComCobrancaPendenteNaoAcionaEstorno() {
    when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
        .thenReturn(List.of(cobrancaPendente()));

    service.cancelar(inscricaoId, usuarioId, igrejaId);

    verify(mercadoPagoClient, never()).estornar(any(), any());
}

@Test
void falhaNoEstornoNaoDeixaInscricaoComoCancelada() {
    when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
        .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));
    doThrow(new IllegalStateException("Mercado Pago fora do ar"))
        .when(mercadoPagoClient).estornar(any(), any());

    assertThatThrownBy(() -> service.cancelar(inscricaoId, usuarioId, igrejaId))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("FALHA_ESTORNO");

    verify(inscricaoRepository, never()).save(argThat(i -> i.getStatus() == StatusInscricao.CANCELADA));
}
```

(Os helpers `cobrancaPagaComId(...)`/`cobrancaPendente()` seguem o padrão de helper
privado já usado no arquivo — ex. `evento(...)`, `pessoa(...)`.)

- [ ] **Step 2: Rodar e confirmar que falha**

Run: `cd backend/api && mvn -q test -Dtest=InscricaoServiceTest`
Expected: FAIL

- [ ] **Step 3: Implementar no método de cancelamento**

```java
// dentro do método de cancelamento, antes de persistir o novo status CANCELADA
List<CobrancaEvento> cobrancas = cobrancaEventoRepository.findByInscricaoId(inscricaoId);
for (CobrancaEvento cobranca : cobrancas) {
    if (cobranca.getStatus() == StatusCobranca.PAGO) {
        try {
            mercadoPagoClient.estornar(igrejaId, cobranca.getMpPaymentId());
            cobranca.marcarComoReembolsado();
        } catch (Exception e) {
            throw new BusinessException("FALHA_ESTORNO",
                "Não foi possível estornar o pagamento. Tente novamente em instantes.");
        }
    } else if (cobranca.getStatus() == StatusCobranca.PENDENTE) {
        cobranca.marcarComoCancelado();
    }
}
cobrancaEventoRepository.saveAll(cobrancas);
```

- [ ] **Step 4: Rodar e confirmar que passa**

Run: `cd backend/api && mvn -q test -Dtest=InscricaoServiceTest`
Expected: PASS

- [ ] **Step 5: Rodar a suíte completa**

Run: `cd backend/api && mvn -q test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/evento/inscricao/InscricaoService.java \
        backend/api/src/test/java/com/domus/api/modules/evento/inscricao/InscricaoServiceTest.java
git commit -m "feat(pagamento): reembolso automático ao cancelar inscrição paga"
```

---

## Task 13: Endpoint público de cobrança por token + `CobrancaController`

**Files:**
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java`
- Create: `backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/CobrancaPublicaDTO.java`
- Modify: `SecurityConfig.java` (liberar `/cobrancas/**`)
- Test: `backend/api/src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java`
  (harness `AutenticacaoTestSupport` não se aplica aqui — é rota pública; usar `MockMvc` sem cookie)

**Interfaces:**
- Consumes: `CobrancaEventoService.buscarPorToken` (Task 7).
- Produces: `GET /cobrancas/{token}` → `CobrancaPublicaDTO` (nome do evento, valor, nome
  de quem vai pagar, status, prazo) — consumido pela Task 14 (página pública do front).

- [ ] **Step 1: DTO**

```java
package com.domus.api.modules.pagamento.cobranca.DTOs;

import java.math.BigDecimal;
import java.time.Instant;

public record CobrancaPublicaDTO(
    String tituloEvento,
    String nomePagador,
    BigDecimal valor,
    String status,
    Instant expiraEm
) {}
```

- [ ] **Step 2: Escrever o teste do controller**

```java
package com.domus.api.modules.pagamento.cobranca;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CobrancaControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;

    @Test
    void retorna404ParaTokenInexistenteSemPrecisarDeAutenticacao() throws Exception {
        mockMvc.perform(get("/cobrancas/token-que-nao-existe"))
            .andExpect(status().isNotFound());
    }

    // Teste de caminho feliz (token válido) requer fixture completa de evento +
    // inscrição + cobrança — implementar seguindo o padrão inline de fixture já usado
    // em outros *ControllerTest do projeto (sem fixture compartilhada de domínio).
}
```

- [ ] **Step 3: Controller**

```java
package com.domus.api.modules.pagamento.cobranca;

import com.domus.api.modules.pagamento.cobranca.DTOs.CobrancaPublicaDTO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cobrancas")
public class CobrancaController {

    private final CobrancaEventoService service;

    public CobrancaController(CobrancaEventoService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    public CobrancaPublicaDTO buscar(@PathVariable String token) {
        var cobranca = service.buscarPorToken(token);
        // TODO da implementação real: montar CobrancaPublicaDTO buscando o título do
        // evento (EventoRepository) e o nome de quem vai pagar (PessoaRepository ou
        // AcompanhanteInscricaoRepository, conforme cobranca.getPessoaId()/getAcompanhanteId())
        // — não coberto neste plano por depender de repositórios já existentes que o
        // implementador deve injetar aqui.
        throw new UnsupportedOperationException("Montar DTO consultando Evento/Pessoa/Acompanhante");
    }
}
```

> **Nota**: assim como a Task 10, esta é uma lacuna sinalizada de propósito — montar o
> DTO de resposta exige repositórios de `Evento`/`Pessoa`/`AcompanhanteInscricao` cuja
> assinatura exata não foi levantada nesta pesquisa. Resolver isso é a continuação
> natural do Step 3 e deve ser feito antes de considerar a task pronta; o teste do
> Step 2 (caminho feliz) só pode ser completado depois disso.

- [ ] **Step 4: Liberar no `SecurityConfig`**

```java
.requestMatchers("/cobrancas/**").permitAll()
// e em CSRF:
.ignoringRequestMatchers("/cobrancas/**")
```

- [ ] **Step 5: Rodar o teste do 404 (não depende do TODO acima)**

Run: `cd backend/api && mvn -q test -Dtest=CobrancaControllerTest`
Expected: PASS (o teste de 404 passa mesmo com o `buscar` de caminho feliz incompleto,
já que `buscarPorToken` lança `BusinessException` mapeada a 404/400 antes de chegar no
trecho não implementado)

- [ ] **Step 6: Commit**

```bash
git add backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/CobrancaController.java \
        backend/api/src/main/java/com/domus/api/modules/pagamento/cobranca/DTOs/CobrancaPublicaDTO.java \
        backend/api/src/test/java/com/domus/api/modules/pagamento/cobranca/CobrancaControllerTest.java \
        backend/api/src/main/java/com/domus/api/shared/security/SecurityConfig.java
git commit -m "feat(pagamento): endpoint público de cobrança por token"
```

---

## Task 14: Frontend — escolha de pagamento por pessoa + Payment Brick + compartilhar link

Esta é a task mais visual, e onde os mockups do Stitch (conectados à sessão de
brainstorm) servem de referência de layout: card "Divisão de Pagamento" lado a lado do
resumo, total dinâmico somando só quem paga agora, tela de confirmação com contagem
regressiva e ações de compartilhar.

**Files:**
- Create: `frontend/src/services/cobranca.service.ts`
- Create: `frontend/src/hooks/cobranca/useCobrancaPublica.ts`
- Create: `frontend/src/components/module/pagamento/PaymentBrickCheckout.tsx`
- Create: `frontend/src/components/module/pagamento/PaymentBrickCheckout.module.css`
- Create: `frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.tsx`
- Create: `frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.module.css`
- Create: `frontend/src/components/module/eventos/ModalCompartilharCobranca.tsx`
- Create: `frontend/src/app/cobranca/[token]/page.tsx`
- Create: `frontend/src/app/cobranca/[token]/CobrancaPublica.module.css`
- Modify: `package.json` (`@mercadopago/sdk-react`)
- Modify: o componente que hoje abre `ModalConfirmarPagamento.tsx`

> **Nota pro implementador**: esta task depende de `ModalCompartilharConvite.tsx` e do
> hook `useGerarConvite.ts` como referência de padrão (copiar/compartilhar via WhatsApp
> por `window.open('https://wa.me/?text=...')`, e `useQuery` com `enabled` em vez de
> `useMutation` pra evitar duplo POST em StrictMode) — leia os dois arquivos originais
> antes de escrever `ModalCompartilharCobranca.tsx`. Também depende de ler
> `ConvitePublico.module.css`/página de convite público pra reaproveitar visualmente na
> tela `/cobranca/[token]` (mesma observação da pesquisa de contexto).

- [ ] **Step 1: Instalar o SDK JS**

```bash
cd frontend && npm install @mercadopago/sdk-react
```

- [ ] **Step 2: `cobranca.service.ts`**

```ts
import { api } from '@/lib/api'

export interface CobrancaPublica {
  tituloEvento: string
  nomePagador: string
  valor: number
  status: 'PENDENTE' | 'PAGO' | 'EXPIRADO' | 'CANCELADO' | 'REEMBOLSADO'
  expiraEm: string
}

export const cobrancaService = {
  buscarPorToken: (token: string) =>
    api.get<CobrancaPublica>(`/cobrancas/${token}`).then((res) => res.data),
}
```

- [ ] **Step 3: Hook público**

```ts
// frontend/src/hooks/cobranca/useCobrancaPublica.ts
import { useQuery } from '@tanstack/react-query'
import { cobrancaService } from '@/services/cobranca.service'

export function useCobrancaPublica(token: string) {
  return useQuery({
    queryKey: ['cobranca-publica', token],
    queryFn: () => cobrancaService.buscarPorToken(token),
    retry: false,
  })
}
```

- [ ] **Step 4: `EscolhaPagamentoPorPessoa` — card de "Divisão de Pagamento"**

```tsx
'use client'

import { useState } from 'react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { Button } from '@/components/common/button/Button'
import styles from './EscolhaPagamentoPorPessoa.module.css'

export interface PessoaACobrar {
  id: string
  nome: string
  valor: number
  ehTitular: boolean
}

interface Props {
  pessoas: PessoaACobrar[]
  onConfirmar: (escolhas: Record<string, 'PAGAR_AGORA' | 'GERAR_LINK'>) => void
  isLoading: boolean
}

/**
 * Titular sempre fica travado em "eu pago agora" — regra de negócio fechada no
 * brainstorm: evita uma inscrição inteira travada porque nem o titular resolveu o
 * próprio pagamento.
 */
export function EscolhaPagamentoPorPessoa({ pessoas, onConfirmar, isLoading }: Props) {
  const [escolhas, setEscolhas] = useState<Record<string, 'PAGAR_AGORA' | 'GERAR_LINK'>>(
    Object.fromEntries(pessoas.map((p) => [p.id, 'PAGAR_AGORA']))
  )

  const totalAgora = pessoas
    .filter((p) => escolhas[p.id] === 'PAGAR_AGORA')
    .reduce((soma, p) => soma + p.valor, 0)

  return (
    <div className={styles.wrapper}>
      <h3 className={styles.titulo}>Divisão de Pagamento</h3>
      <p className={styles.subtitulo}>
        Como você deseja acertar as inscrições? Pague tudo agora ou envie links de
        cobrança individuais.
      </p>

      <ul className={styles.lista}>
        {pessoas.map((pessoa) => (
          <li key={pessoa.id} className={styles.item}>
            <div>
              <strong>{pessoa.nome}</strong>
              <span className={styles.valor}>{formatarMoeda(pessoa.valor)}</span>
            </div>
            {pessoa.ehTitular ? (
              <span className={styles.pagaAgoraFixo}>PAGA AGORA</span>
            ) : (
              <div className={styles.opcoes}>
                <button
                  type="button"
                  className={escolhas[pessoa.id] === 'PAGAR_AGORA' ? styles.opcaoAtiva : styles.opcao}
                  onClick={() => setEscolhas((atual) => ({ ...atual, [pessoa.id]: 'PAGAR_AGORA' }))}
                >
                  Eu pago agora
                </button>
                <button
                  type="button"
                  className={escolhas[pessoa.id] === 'GERAR_LINK' ? styles.opcaoAtiva : styles.opcao}
                  onClick={() => setEscolhas((atual) => ({ ...atual, [pessoa.id]: 'GERAR_LINK' }))}
                >
                  Enviar link
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>

      <div className={styles.total}>
        <span>Total a pagar agora</span>
        <strong>{formatarMoeda(totalAgora)}</strong>
      </div>

      <Button
        type="button"
        variant="primary"
        size="md"
        isLoading={isLoading}
        onClick={() => onConfirmar(escolhas)}
      >
        Confirmar Inscrição
      </Button>

      <p className={styles.seguranca}>
        Pagamento processado com segurança pelo Mercado Pago.
      </p>
    </div>
  )
}
```

```css
/* frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.module.css */
.wrapper {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.25rem;
  border-radius: 0.75rem;
  border: 1px solid var(--cor-borda);
}

.lista {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
  min-width: 0;
}

.valor {
  display: block;
  color: var(--cor-texto-secundario);
  font-size: 0.85rem;
}

.opcoes {
  display: flex;
  gap: 0.5rem;
}

.opcao,
.opcaoAtiva {
  padding: 0.4rem 0.75rem;
  border-radius: 0.5rem;
  border: 1px solid var(--cor-borda);
  background: transparent;
  font-size: 0.85rem;
}

.opcaoAtiva {
  background: var(--cor-primaria);
  color: white;
  border-color: var(--cor-primaria);
}

.total {
  display: flex;
  justify-content: space-between;
  font-size: 1.1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--cor-borda);
}

.seguranca {
  font-size: 0.8rem;
  color: var(--cor-texto-secundario);
  text-align: center;
}

@media (max-width: 640px) {
  .item {
    flex-direction: column;
    align-items: flex-start;
  }
}
```

> **Nota**: variáveis CSS (`--cor-primaria` etc.) são placeholders de convenção —
> confirmar nomes reais lendo outro `.module.css` do projeto antes de codar.

- [ ] **Step 5: `PaymentBrickCheckout` — wrapper do Payment Brick**

```tsx
'use client'

import { useEffect, useRef } from 'react'
import { initMercadoPago, Payment } from '@mercadopago/sdk-react'

interface Props {
  valor: number
  onPagamentoCriado: (paymentId: string) => void
}

let inicializado = false

/**
 * Payment Brick embutido (PIX + cartão na mesma tela) — decisão do brainstorm de não
 * redirecionar pro Mercado Pago (\"fica amador\"). O tokenizador de cartão roda no
 * navegador via SDK do Mercado Pago; o dado de cartão nunca passa pelo backend do
 * Domus.
 */
export function PaymentBrickCheckout({ valor, onPagamentoCriado }: Props) {
  const publicKeyRef = useRef(process.env.NEXT_PUBLIC_MERCADOPAGO_PUBLIC_KEY ?? '')

  useEffect(() => {
    if (!inicializado) {
      initMercadoPago(publicKeyRef.current, { locale: 'pt-BR' })
      inicializado = true
    }
  }, [])

  return (
    <Payment
      initialization={{ amount: valor }}
      customization={{ paymentMethods: { bankTransfer: 'all', creditCard: 'all' } }}
      onSubmit={async ({ formData }) => {
        // TODO da implementação real: enviar formData pro backend (endpoint novo,
        // ex. POST /pagamentos/cobranca/{cobrancaId}/pagar) que chama
        // MercadoPagoClient/PaymentClient com os dados tokenizados — este endpoint
        // backend não foi coberto nas tasks anteriores porque o formato exato de
        // formData do Payment Brick (token, payment_method_id, installments) só é
        // conhecido consultando a documentação do Brick no momento de implementar.
        // Ajustar aqui e criar o endpoint correspondente.
        console.log('formData do Brick', formData)
      }}
      onError={(error) => console.error('Erro no Payment Brick', error)}
    />
  )
}
```

> **Lacuna sinalizada de propósito** (mesma natureza das Tasks 10 e 13): o contrato
> exato entre o Payment Brick (front) e o endpoint de criação de pagamento (back) muda
> conforme a versão do SDK — resolver isso é o primeiro passo real desta task,
> consultando a documentação atual do Mercado Pago Payment Brick. O endpoint
> `POST /pagamentos/cobranca/{cobrancaId}/pagar` mencionado no comentário precisa ser
> criado no backend (`CobrancaController`, Task 13) como parte da conclusão desta task
> — ele chama `MercadoPagoClient.criarPagamento` (Task 8) com os dados vindos do Brick.

- [ ] **Step 6: `ModalCompartilharCobranca`**

Copiar a estrutura de `ModalCompartilharConvite.tsx` (lido no início desta task),
trocando: hook de geração (usa o `tokenLinkPublico` já retornado na criação da
inscrição, não precisa gerar de novo), texto da mensagem de WhatsApp
(`"Você foi inscrito(a) em {evento}! Pague sua parte (R$ {valor}) aqui: {link}"`), e a
URL montada com `/cobranca/{token}` em vez de `/convite/{token}`.

- [ ] **Step 7: Página pública `/cobranca/[token]`**

```tsx
// frontend/src/app/cobranca/[token]/page.tsx
'use client'

import { useParams } from 'next/navigation'
import { useCobrancaPublica } from '@/hooks/cobranca/useCobrancaPublica'
import { PaymentBrickCheckout } from '@/components/module/pagamento/PaymentBrickCheckout'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import styles from './CobrancaPublica.module.css'

export default function CobrancaPublicaPage() {
  const { token } = useParams<{ token: string }>()
  const { data: cobranca, isLoading, isError } = useCobrancaPublica(token)

  if (isLoading) return null
  if (isError || !cobranca) {
    return <div className={styles.wrapper}><p>Este link de pagamento não existe ou expirou.</p></div>
  }
  if (cobranca.status === 'PAGO') {
    return <div className={styles.wrapper}><p>Pagamento já confirmado. Obrigado!</p></div>
  }
  if (cobranca.status !== 'PENDENTE') {
    return <div className={styles.wrapper}><p>Este link não está mais disponível.</p></div>
  }

  return (
    <div className={styles.wrapper}>
      <h1>{cobranca.tituloEvento}</h1>
      <p>{cobranca.nomePagador}, sua parte: <strong>{formatarMoeda(cobranca.valor)}</strong></p>
      <PaymentBrickCheckout valor={cobranca.valor} onPagamentoCriado={() => {}} />
      <p className={styles.seguranca}>Pagamento processado com segurança pelo Mercado Pago.</p>
    </div>
  )
}
```

```css
/* frontend/src/app/cobranca/[token]/CobrancaPublica.module.css */
.wrapper {
  max-width: 480px;
  margin: 0 auto;
  padding: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.seguranca {
  font-size: 0.8rem;
  color: var(--cor-texto-secundario);
  text-align: center;
}
```

- [ ] **Step 8: Ligar tudo no fluxo de inscrição existente**

Encontrar o componente que hoje abre `ModalConfirmarPagamento` (fluxo de inscrição em
evento pago) e substituir: se `useContaPagamento` (Task 5) retornar `conectada: false`,
mostrar aviso com atalho pra `/configuracoes/igreja`; se `true`, abrir
`EscolhaPagamentoPorPessoa` em vez do modal antigo, e depois de confirmar, renderizar
`PaymentBrickCheckout` pra cada pessoa marcada "eu pago agora" (em sequência) e
`ModalCompartilharCobranca` pra cada uma marcada "gerar link".

- [ ] **Step 9: Testar manualmente no navegador**

Rodar front+back locais, logar como `ADMIN_IGREJA` numa igreja com conta MP conectada
(sandbox), abrir um evento pago, inscrever titular + 1 acompanhante marcado "gerar
link", confirmar que: (a) o Brick aparece pro titular, (b) a tela de confirmação mostra
o link do acompanhante com botão de copiar/WhatsApp, (c) abrir o link em aba anônima
mostra a página pública funcionando sem login. Testar em viewport de celular (regra de
responsividade obrigatória do projeto).

- [ ] **Step 10: Commit**

```bash
git add frontend/src/services/cobranca.service.ts frontend/src/hooks/cobranca/ \
        frontend/src/components/module/pagamento/ frontend/src/components/module/eventos/EscolhaPagamentoPorPessoa.* \
        frontend/src/components/module/eventos/ModalCompartilharCobranca.tsx \
        frontend/src/app/cobranca/ frontend/package.json frontend/package-lock.json
git commit -m "feat(pagamento): checkout embutido, escolha por pessoa e link público de cobrança"
```

---

## Self-Review

**Cobertura da spec:**
- Conexão OAuth da igreja → Tasks 3-5. ✓
- `COBRANCA_EVENTO` por pessoa, regra titular fixo → Tasks 6-9. ✓
- Checkout Bricks (PIX+cartão embutido) → Tasks 8, 14. ✓
- Webhook + confirmação + notificação → Task 10 (notificação sinalizada como
  continuação a implementar com a API real do `NotificacaoService`). ✓ (com lacuna
  sinalizada)
- Reembolso automático → Task 12. ✓
- Job de expiração de vaga → Task 11. ✓
- Criptografia de credencial → Task 2. ✓
- Link público de cobrança individual → Tasks 13-14. ✓

**Lacunas conhecidas e sinalizadas explicitamente** (não são placeholders silenciosos —
cada uma tem uma nota "Atenção pro implementador" explicando por que não pôde ser
fechada nesta sessão de planejamento e o que fazer):
1. Formato exato do payload de webhook "payment" do Mercado Pago (Task 10) — depende de
   consultar a documentação oficial no momento de implementar, não da pesquisa desta
   sessão.
2. Contrato exato Payment Brick ↔ endpoint de criação de pagamento (Tasks 8/14) — muda
   por versão do SDK.
3. Montagem do `CobrancaPublicaDTO` (Task 13) precisa de repositórios de
   `Evento`/`Pessoa`/`AcompanhanteInscricao` cuja assinatura real não foi lida nesta
   pesquisa.
4. Assinatura exata do método de inscrição em `InscricaoService` (Task 9) — a pesquisa
   trouxe uma assinatura aproximada; o implementador precisa abrir o arquivo real antes
   de codar essa task especificamente.

Essas quatro lacunas são inerentes a planejar sobre uma integração externa cujo
contrato muda de versão e sobre um método de service não lido por completo — resolver
via leitura do código/documentação real é o primeiro passo de cada task afetada, não
um trabalho "a mais" fora do plano.

**Consistência de tipos**: `CobrancaEvento`, `StatusCobranca`, `CobrancaEventoRepository`,
`CobrancaEventoService` usados de forma consistente entre Tasks 6-14. `MercadoPagoClient`
(Task 8) é consumido igual nas Tasks 9, 12, 14.

**Escopo**: cabe num plano só — é uma feature vertical (conexão → cobrança → checkout →
confirmação → cancelamento), sem subsistemas independentes que precisassem virar planos
separados.
