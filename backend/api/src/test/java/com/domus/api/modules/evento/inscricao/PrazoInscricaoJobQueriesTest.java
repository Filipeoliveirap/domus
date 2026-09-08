package com.domus.api.modules.evento.inscricao;

import static org.assertj.core.api.Assertions.assertThat;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.jdbc.Sql;

/**
 * Cobre as queries JPQL do job de prazo de inscrição contra Postgres real (Testcontainers).
 * A lógica do processador é testada em {@link PrazoInscricaoJobTest} (Mockito puro); aqui
 * provamos que cada condição do WHERE inclui/exclui as linhas certas.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PrazoInscricaoJobQueriesTest implements PostgresTestContainerSupport {

    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired jakarta.persistence.EntityManager entityManager;

    static final UUID IGREJA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID EVENTO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID PESSOA = UUID.fromString("33333333-3333-3333-3333-333333333333");

    static final String IGREJA_SQL =
        "INSERT INTO igreja (id, nome, email) VALUES "
            + "('11111111-1111-1111-1111-111111111111', 'Igreja Teste', 'igreja@teste.com')";
    static final String PESSOA_SQL =
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES "
            + "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Fulano', 'fulano@teste.com')";

    // --- buscarComPrazoAtivoParaJob -------------------------------------------------

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao, inscricoes_ate) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true, now() + interval '2 days')"
    })
    void retornaEventoComPrazoAtivo() {
        List<Evento> achados = eventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime.now());

        assertThat(achados).extracting(Evento::getId).containsExactly(EVENTO);
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao, inscricoes_ate) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true, NULL)"
    })
    void excluiEventoSemPrazo() {
        assertThat(eventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime.now())).isEmpty();
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao, inscricoes_ate) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', false, now() + interval '2 days')"
    })
    void excluiEventoQueNaoExigeInscricao() {
        assertThat(eventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime.now())).isEmpty();
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao, inscricoes_ate) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() - interval '1 day', true, now() + interval '2 days')"
    })
    void excluiEventoJaComecado() {
        assertThat(eventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime.now())).isEmpty();
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao, inscricoes_ate, deleted_at) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true, now() + interval '2 days', now())"
    })
    void excluiEventoArquivado() {
        assertThat(eventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime.now())).isEmpty();
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        PESSOA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao, inscricoes_ate) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true, now() + interval '2 days')",
        "INSERT INTO evento_responsavel (id, igreja_id, evento_id, pessoa_id) VALUES "
            + "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', "
            + "'22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333')"
    })
    void eventoRetornadoTemResponsaveisAcessiveis() {
        List<Evento> achados = eventoRepository.buscarComPrazoAtivoParaJob(LocalDateTime.now());

        assertThat(achados).hasSize(1);
        // transação do @DataJpaTest ainda aberta — a relação LAZY carrega dentro do teste
        assertThat(achados.get(0).getResponsaveis()).hasSize(1);
        assertThat(achados.get(0).getResponsaveis().get(0).getPessoa().getId()).isEqualTo(PESSOA);
    }

    // --- buscarAguardandoPagamentoSemAvisoDePrazo ---------------------------------

    @Test
    @Sql(statements = {
        IGREJA_SQL, PESSOA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status, aviso_prazo_incompleto_em) VALUES "
            + "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', "
            + "'22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO', NULL)"
    })
    void retornaAguardandoPagamentoSemCarimbo() {
        List<InscricaoEvento> achados = inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(EVENTO);

        assertThat(achados).extracting(i -> i.getId())
            .containsExactly(UUID.fromString("55555555-5555-5555-5555-555555555555"));
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL, PESSOA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES "
            + "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', "
            + "'22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA')"
    })
    void excluiConfirmada() {
        assertThat(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(EVENTO)).isEmpty();
    }

    @Test
    @Sql(statements = {
        IGREJA_SQL, PESSOA_SQL,
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status, aviso_prazo_incompleto_em) VALUES "
            + "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', "
            + "'22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO', now())"
    })
    void excluiAguardandoPagamentoJaCarimbada() {
        // cerne do dedup do job: quem já foi avisado tem o carimbo preenchido e sai da fila
        assertThat(inscricaoRepository.buscarAguardandoPagamentoSemAvisoDePrazo(EVENTO)).isEmpty();
    }

    // --- contarPorEventoIdEStatus ------------------------------------------------

    @Test
    @Sql(statements = {
        IGREJA_SQL,
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES "
            + "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'A', 'a@teste.com'), "
            + "('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111111', 'B', 'b@teste.com'), "
            + "('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111111', 'C', 'c@teste.com')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, requer_inscricao) VALUES "
            + "('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'Congresso', "
            + "now() + interval '30 days', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES "
            + "('55555555-5555-5555-5555-555555555551', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA'), "
            + "('55555555-5555-5555-5555-555555555552', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333334', 'CONFIRMADA'), "
            + "('55555555-5555-5555-5555-555555555553', '11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333335', 'AGUARDANDO_PAGAMENTO')"
    })
    void contaPorStatus() {
        assertThat(inscricaoRepository.contarPorEventoIdEStatus(EVENTO, StatusInscricao.CONFIRMADA)).isEqualTo(2);
        assertThat(inscricaoRepository.contarPorEventoIdEStatus(EVENTO, StatusInscricao.AGUARDANDO_PAGAMENTO)).isEqualTo(1);
    }
}
