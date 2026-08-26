package com.domus.api.modules.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
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
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111111', 'Igreja Teste', 'igreja@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Fulano', 'fulano@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', " +
            "'Retiro', now(), '77777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA')",
        "INSERT INTO acompanhante_inscricao (id, inscricao_id, nome) VALUES " +
            "('88888888-8888-8888-8888-888888888888', '66666666-6666-6666-6666-666666666666', 'Acompanhante Expirada')",
        "INSERT INTO acompanhante_inscricao (id, inscricao_id, nome) VALUES " +
            "('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '66666666-6666-6666-6666-666666666666', 'Acompanhante Paga')",
        "INSERT INTO acompanhante_inscricao (id, inscricao_id, nome) VALUES " +
            "('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '66666666-6666-6666-6666-666666666666', 'Acompanhante Cancelada')"
    })
    void contaSoPagosEPendentesNaoExpiradasComTentativaDePagamentoEmAndamento() {
        // PENDENTE não-expirada, COM tentativa de pagamento em andamento (mpPaymentId
        // gravado por CobrancaController.pagar) -> conta. Sem isso, a vaga só é reservada
        // quando a pessoa realmente envia o pagamento (cartão ou Pix) — não só por ter
        // clicado "Se inscrever" e ainda estar decidindo/navegando no checkout.
        var cobrancaComTentativa = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.TEN, Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, null);
        cobrancaComTentativa.registrarTentativaPagamento("mp-payment-em-andamento");
        entityManager.persist(cobrancaComTentativa);

        // PENDENTE expirada (por passagem de prazo, sem transição explícita de status) -> não conta
        var cobrancaExpirada = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null,
            UUID.fromString("88888888-8888-8888-8888-888888888888"), BigDecimal.TEN,
            Instant.now().minus(1, ChronoUnit.HOURS), usuarioId, "token-x");
        entityManager.persist(cobrancaExpirada);

        // PAGO (marcado explicitamente) -> conta, mesmo que expiraEm já tenha passado
        var cobrancaPaga = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null,
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), BigDecimal.TEN,
            Instant.now().minus(1, ChronoUnit.HOURS), usuarioId, "token-paga");
        cobrancaPaga.marcarComoPago("mp-payment-123");
        entityManager.persist(cobrancaPaga);

        // CANCELADO (marcado explicitamente), mesmo com expiraEm no futuro -> não conta
        var cobrancaCancelada = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null,
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), BigDecimal.TEN,
            Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, "token-cancelada");
        cobrancaCancelada.marcarComoCancelado();
        entityManager.persist(cobrancaCancelada);

        entityManager.flush();
        entityManager.clear();

        long total = repository.contarPessoasComVagaReservada(eventoId, Instant.now());

        assertThat(total).isEqualTo(2); // PENDENTE com tentativa em andamento + PAGO; expirada e cancelada não contam
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111111', 'Igreja Teste', 'igreja@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Fulano', 'fulano@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', " +
            "'Retiro', now(), '77777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA')"
    })
    void naoContaPendenteSemTentativaDePagamentoEmAndamento() {
        // A pessoa clicou "Se inscrever" mas ainda não enviou nenhum pagamento (nem
        // escolheu cartão/Pix) — não deve segurar a vaga. Só conta a partir do momento
        // que CobrancaController.pagar() grava mpPaymentId.
        entityManager.persist(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.TEN, Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, null));
        entityManager.flush();
        entityManager.clear();

        long total = repository.contarPessoasComVagaReservada(eventoId, Instant.now());

        assertThat(total).isEqualTo(0);
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111111', 'Igreja Teste', 'igreja@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Fulano', 'fulano@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', " +
            "'Retiro', now(), '77777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA')",
        "INSERT INTO acompanhante_inscricao (id, inscricao_id, nome) VALUES " +
            "('99999999-9999-9999-9999-999999999999', '66666666-6666-6666-6666-666666666666', 'Acompanhante Token')"
    })
    void buscaPorTokenLinkPublico() {
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null,
            UUID.fromString("99999999-9999-9999-9999-999999999999"),
            BigDecimal.TEN, Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, "token-unico-123");
        entityManager.persist(cobranca);
        entityManager.flush();
        entityManager.clear();

        var encontrada = repository.findByTokenLinkPublico("token-unico-123");

        assertThat(encontrada).isPresent();
    }
}
