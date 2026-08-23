package com.domus.api.modules.pagamento.conta;

import static org.assertj.core.api.Assertions.assertThat;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111111', 'Igreja Teste', 'igreja@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Admin', 'admin@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)"
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
