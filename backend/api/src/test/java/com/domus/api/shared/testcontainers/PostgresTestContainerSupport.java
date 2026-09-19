package com.domus.api.shared.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Implemente esta interface em vez de apontar pro Neon de testes via `.env`. O container é
 *  um campo estático de interface: sobe uma vez só por execução do `mvn test` (compartilhado
 *  por todas as classes que implementam), não uma vez por classe — o Surefire deste projeto
 *  roda tudo numa JVM só (sem forkCount customizado), então isso funciona sem configuração
 *  extra. Migrations do Flyway (inclusive `unaccent` e os triggers em plpgsql) rodam sozinhas
 *  contra o banco novo, porque `spring.flyway.enabled=true` já aponta pro classpath. */
public interface PostgresTestContainerSupport {

    PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("security.jwt.secret", () -> "chave-secreta-teste-nao-usar-em-producao-12345678");
        registry.add("security.jwt.expiration-ms", () -> "600000");
        registry.add("security.jwt.refresh-expiration-ms", () -> "604800000");
        registry.add("google.client-id", () -> "google-client-id-teste-nao-usar-em-producao");
        registry.add("app.pagamento.encryption-key", () -> "dGVzdGUtY2hhdmUtY3JpcHRvZ3JhZmlhLTI1NmItYSE=");
        // Credenciais fictícias de Mercado Pago: nenhuma chamada real sai dos testes
        // (MercadoPagoApi é mockado nos caminhos que exigem rede; os demais falham
        // antes de chegar lá). Sem estes placeholders o context nem sobe.
        registry.add("app.pagamento.mercadopago.client-id", () -> "mp-client-id-teste");
        registry.add("app.pagamento.mercadopago.client-secret", () -> "mp-client-secret-teste");
        registry.add("app.pagamento.mercadopago.redirect-uri", () -> "http://localhost:3000/callback");
        registry.add("app.pagamento.mercadopago.webhook-secret", () -> "mp-webhook-secret-teste");

        // O Surefire deste projeto roda todas as classes @SpringBootTest numa JVM só, e o
        // RateLimitFilter conta no MESMO Redis pra todas — sem isto, a suíte inteira soma
        // requisições até estourar o limite GLOBAL de produção (100/min) e testes que
        // passam isolados começam a falhar com 429 quando o número de classes de harness
        // cresce (achado em teste, 2026-09-15, ao adicionar a 8ª classe do harness). Só o
        // global sobe — o tier de auth fica no default: RateLimitCsrfOrderTest depende do
        // valor real (10/min) pra provar o próprio comportamento do limitador.
        registry.add("app.ratelimit.global-por-minuto", () -> "1000000");
    }
}
