package com.domus.api.modules.pagamento.cobranca;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CobrancaControllerTest implements PostgresTestContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired CobrancaEventoRepository cobrancaEventoRepository;

    @Test
    void retorna400ParaTokenInexistenteSemPrecisarDeAutenticacao() throws Exception {
        // buscarPorToken lança BusinessException (código LINK_COBRANCA_INVALIDO), que o
        // GlobalExceptionHandler mapeia pra 400 — não 404 (ver BusinessException handler).
        mockMvc.perform(get("/cobrancas/token-que-nao-existe"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("LINK_COBRANCA_INVALIDO")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111111', 'Igreja Teste', 'igreja@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'Fulano de Tal', 'fulano@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', " +
            "'33333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777777', '11111111-1111-1111-1111-111111111111', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', " +
            "'Retiro de Jovens', now(), '77777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555555', '33333333-3333-3333-3333-333333333333', 'CONFIRMADA')"
    })
    void retornaDadosDaCobrancaParaTokenValido() throws Exception {
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, "token-valido-123");
        cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(get("/cobrancas/token-valido-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tituloEvento", is("Retiro de Jovens")))
            .andExpect(jsonPath("$.nomePagador", is("Fulano de Tal")))
            .andExpect(jsonPath("$.valor", is(150)))
            .andExpect(jsonPath("$.status", is("PENDENTE")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111112', 'Igreja Teste 2', 'igreja2@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333334', '11111111-1111-1111-1111-111111111112', 'Titular Da Inscricao', 'titular@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444445', '11111111-1111-1111-1111-111111111112', " +
            "'33333333-3333-3333-3333-333333333334', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777778', '11111111-1111-1111-1111-111111111112', 'Salão 2')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555556', '11111111-1111-1111-1111-111111111112', " +
            "'Acampamento', now(), '77777777-7777-7777-7777-777777777778', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666667', '11111111-1111-1111-1111-111111111112', " +
            "'55555555-5555-5555-5555-555555555556', '33333333-3333-3333-3333-333333333334', 'CONFIRMADA')",
        "INSERT INTO acompanhante_inscricao (id, inscricao_id, nome) VALUES " +
            "('99999999-9999-9999-9999-999999999998', '66666666-6666-6666-6666-666666666667', 'Convidado Sem Cadastro')"
    })
    void retornaNomeDoAcompanhanteQuandoCobrancaEhDeTerceiroSemCadastro() throws Exception {
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111112");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555556");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666667");
        UUID acompanhanteId = UUID.fromString("99999999-9999-9999-9999-999999999998");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444445");

        // pessoaId nulo, acompanhanteId preenchido — ramo do XOR que o controller precisa
        // resolver via AcompanhanteRepository, não via PessoaRepository.
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null, acompanhanteId,
            BigDecimal.valueOf(80), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, "token-acompanhante-456");
        cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(get("/cobrancas/token-acompanhante-456"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tituloEvento", is("Acampamento")))
            .andExpect(jsonPath("$.nomePagador", is("Convidado Sem Cadastro")))
            .andExpect(jsonPath("$.valor", is(80)))
            .andExpect(jsonPath("$.status", is("PENDENTE")));
    }

    @Test
    void retorna404AoTentarPagarCobrancaInexistenteSemPrecisarDeAutenticacao() throws Exception {
        mockMvc.perform(post("/cobrancas/" + UUID.randomUUID() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"a@a.com\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111113', 'Igreja Teste 3', 'igreja3@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333335', '11111111-1111-1111-1111-111111111113', 'Pagador Titular', 'pagador@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444446', '11111111-1111-1111-1111-111111111113', " +
            "'33333333-3333-3333-3333-333333333335', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777779', '11111111-1111-1111-1111-111111111113', 'Salão 3')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555557', '11111111-1111-1111-1111-111111111113', " +
            "'Congresso', now(), '77777777-7777-7777-7777-777777777779', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666668', '11111111-1111-1111-1111-111111111113', " +
            "'55555555-5555-5555-5555-555555555557', '33333333-3333-3333-3333-333333333335', 'CONFIRMADA')"
    })
    void recusaPagarCobrancaSemIgrejaComContaDePagamentoConectada() throws Exception {
        // Sem uma ContaPagamentoIgreja cadastrada pra essa igreja: o fluxo real (controller
        // -> MercadoPagoClient -> ContaPagamentoIgrejaRepository) recusa antes de sequer
        // tentar falar com o Mercado Pago — exercitando o endpoint de ponta a ponta sem
        // precisar mockar rede.
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111113");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555557");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666668");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333335");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444446");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"pagador@teste.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("IGREJA_SEM_CONTA_PAGAMENTO")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111114', 'Igreja Teste 4', 'igreja4@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333336', '11111111-1111-1111-1111-111111111114', 'Pagador Ja Pago', 'japago@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444447', '11111111-1111-1111-1111-111111111114', " +
            "'33333333-3333-3333-3333-333333333336', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777780', '11111111-1111-1111-1111-111111111114', 'Salão 4')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555558', '11111111-1111-1111-1111-111111111114', " +
            "'Conferência', now(), '77777777-7777-7777-7777-777777777780', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666669', '11111111-1111-1111-1111-111111111114', " +
            "'55555555-5555-5555-5555-555555555558', '33333333-3333-3333-3333-333333333336', 'CONFIRMADA')"
    })
    void recusaPagarCobrancaQueJaNaoEstaPendente() throws Exception {
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111114");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555558");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666669");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333336");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444447");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca.marcarComoPago("mp-payment-ja-existente");
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"japago@teste.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("COBRANCA_NAO_PENDENTE")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111116', 'Igreja Teste 6', 'igreja6@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333338', '11111111-1111-1111-1111-111111111116', 'Pagador Duplicado', 'duplicado@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444449', '11111111-1111-1111-1111-111111111116', " +
            "'33333333-3333-3333-3333-333333333338', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777782', '11111111-1111-1111-1111-111111111116', 'Salão 6')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555560', '11111111-1111-1111-1111-111111111116', " +
            "'Culto Especial', now(), '77777777-7777-7777-7777-777777777782', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666671', '11111111-1111-1111-1111-111111111116', " +
            "'55555555-5555-5555-5555-555555555560', '33333333-3333-3333-3333-333333333338', 'CONFIRMADA')"
    })
    void recusaSegundaTentativaDePagamentoQuandoJaExisteMpPaymentIdRegistrado() throws Exception {
        // Critical 5 (revisão final de branch): cobrança PENDENTE, mas já com mpPaymentId
        // registrado (1ª tentativa criou o pagamento no Mercado Pago, webhook ainda não
        // confirmou) — a 2ª tentativa de pagar precisa ser recusada, sem chamar o Mercado
        // Pago de novo (evita cobrança duplicada real do pagador).
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111116");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555560");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666671");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333338");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444449");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca.registrarTentativaPagamento("mp-payment-1a-tentativa");
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"duplicado@teste.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("COBRANCA_JA_EM_PROCESSAMENTO")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111115', 'Igreja Teste 5', 'igreja5@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333337', '11111111-1111-1111-1111-111111111115', 'Pagador Atrasado', 'atrasado@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444448', '11111111-1111-1111-1111-111111111115', " +
            "'33333333-3333-3333-3333-333333333337', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777781', '11111111-1111-1111-1111-111111111115', 'Salão 5')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555559', '11111111-1111-1111-1111-111111111115', " +
            "'Vigília', now(), '77777777-7777-7777-7777-777777777781', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666670', '11111111-1111-1111-1111-111111111115', " +
            "'55555555-5555-5555-5555-555555555559', '33333333-3333-3333-3333-333333333337', 'CONFIRMADA')"
    })
    void recusaPagarCobrancaComPrazoExpirado() throws Exception {
        // Diferente de COBRANCA_NAO_PENDENTE: aqui o status no banco AINDA é PENDENTE (o
        // job de expiração, Task 11, roda periodicamente e não necessariamente já passou)
        // — o endpoint precisa recusar pelo prazo mesmo antes do job rodar.
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111115");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555559");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666670");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333337");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444448");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.valueOf(150), Instant.now().minus(1, ChronoUnit.HOURS), usuarioId, null);
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"atrasado@teste.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("COBRANCA_EXPIRADA")));
    }

    @Test
    void retorna404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/cobrancas/id/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('21111111-1111-1111-1111-111111111111', 'Igreja Teste 2', 'igreja2b@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('23333333-3333-3333-3333-333333333333', '21111111-1111-1111-1111-111111111111', 'Ciclana', 'ciclana@teste.com')",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('27777777-7777-7777-7777-777777777777', '21111111-1111-1111-1111-111111111111', 'Salão 2')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('25555555-5555-5555-5555-555555555555', '21111111-1111-1111-1111-111111111111', " +
            "'Congresso Anual', '2026-09-10 19:00:00', '27777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('26666666-6666-6666-6666-666666666666', '21111111-1111-1111-1111-111111111111', " +
            "'25555555-5555-5555-5555-555555555555', '23333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void retornaContextoDaCobrancaParaIdValido() throws Exception {
        UUID igrejaId = UUID.fromString("21111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("25555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("26666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("23333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("23333333-3333-3333-3333-333333333333");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(
            igrejaId, eventoId, inscricaoId, pessoaId, null,
            new BigDecimal("75.00"), Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, null));

        mockMvc.perform(get("/cobrancas/id/" + cobranca.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(cobranca.getId().toString())))
            .andExpect(jsonPath("$.eventoId", is(eventoId.toString())))
            .andExpect(jsonPath("$.tituloEvento", is("Congresso Anual")))
            .andExpect(jsonPath("$.nomePagador", is("Ciclana")))
            .andExpect(jsonPath("$.valor", is(75.00)))
            .andExpect(jsonPath("$.status", is("PENDENTE")));
    }
}
