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
    }
}
