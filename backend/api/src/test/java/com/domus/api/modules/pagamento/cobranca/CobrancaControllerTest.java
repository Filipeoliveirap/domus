package com.domus.api.modules.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.domus.api.modules.pagamento.MercadoPagoApi;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.modules.pagamento.seguranca.CredencialEncryptor;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
    @Autowired ContaPagamentoIgrejaRepository contaPagamentoIgrejaRepository;
    @Autowired CredencialEncryptor credencialEncryptor;

    // Wrapper fino da chamada HTTP real ao Mercado Pago — mockado só pro caminho feliz do
    // recálculo de valor (os demais testes falham antes de chegar aqui). O MercadoPagoClient
    // continua REAL: os testes de "sem conta conectada" seguem exercitando o fluxo de ponta
    // a ponta.
    @MockitoBean MercadoPagoApi mercadoPagoApi;

    // O poll de confirmação (@Async) não asserta nada aqui e roda em paralelo ao webhook —
    // mockar não perde cobertura e evita que ele rode após o rollback do @Transactional
    // (logs de warning + ~30s de stall no Surefire).
    @MockitoBean com.domus.api.modules.pagamento.PagamentoPollingService pagamentoPollingService;

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

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
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
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, nome_convidado, telefone_convidado, status) VALUES " +
            "('66666666-6666-6666-6666-666666666667', '11111111-1111-1111-1111-111111111112', " +
            "'55555555-5555-5555-5555-555555555556', 'Convidado Sem Cadastro', '11988887777', 'AGUARDANDO_PAGAMENTO')"
    })
    void retornaNomeDoConvidadoQuandoCobrancaEhDeTerceiroSemCadastro() throws Exception {
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111112");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555556");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666667");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444445");

        // pessoaId nulo — convidado sem cadastro (Plano 4b), resolvido só via
        // InscricaoEvento (nomeConvidado), não mais via AcompanhanteRepository.
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null,
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
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"a@a.com\",\"meio\":\"PIX\",\"parcelas\":1}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void recusaPagarSemMeioDePagamento() throws Exception {
        // @Valid dispara antes do corpo do controller — nem chega a procurar a cobrança.
        mockMvc.perform(post("/cobrancas/" + UUID.randomUUID() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"installments\":1,\"payerEmail\":\"a@a.com\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.campos.paymentMethodId").exists());
    }

    @Test
    void recusaPagarComEmailDoPagadorInvalido() throws Exception {
        mockMvc.perform(post("/cobrancas/" + UUID.randomUUID() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"nao-e-email\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.campos.payerEmail").exists());
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

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"pagador@teste.com\",\"meio\":\"PIX\",\"parcelas\":1}"))
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

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca.marcarComoPago("mp-payment-ja-existente");
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"japago@teste.com\",\"meio\":\"PIX\",\"parcelas\":1}"))
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

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca.registrarTentativaPagamento("mp-payment-1a-tentativa");
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"duplicado@teste.com\",\"meio\":\"PIX\",\"parcelas\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("COBRANCA_JA_EM_PROCESSAMENTO")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111117', 'Igreja Teste 7', 'igreja7@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333339', '11111111-1111-1111-1111-111111111117', 'Ocupante Da Vaga', 'ocupante@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333340', '11111111-1111-1111-1111-111111111117', 'Tentando Pagar Depois', 'depois@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444450', '11111111-1111-1111-1111-111111111117', " +
            "'33333333-3333-3333-3333-333333333339', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777783', '11111111-1111-1111-1111-111111111117', 'Salão 7')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, vagas) VALUES " +
            "('55555555-5555-5555-5555-555555555561', '11111111-1111-1111-1111-111111111117', " +
            "'Evento Com Vaga Unica', now(), '77777777-7777-7777-7777-777777777783', true, 1)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666672', '11111111-1111-1111-1111-111111111117', " +
            "'55555555-5555-5555-5555-555555555561', '33333333-3333-3333-3333-333333333339', 'AGUARDANDO_PAGAMENTO')",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666673', '11111111-1111-1111-1111-111111111117', " +
            "'55555555-5555-5555-5555-555555555561', '33333333-3333-3333-3333-333333333340', 'AGUARDANDO_PAGAMENTO')"
    })
    void recusaPagarQuandoVagaJaFoiOcupadaPorOutraTentativaEmAndamento() throws Exception {
        // Duas pessoas clicaram "Se inscrever" pro mesmo evento de 1 vaga (nenhuma segura
        // a vaga só por isso, ver CobrancaEventoRepository) — a primeira já enviou o
        // pagamento (mpPaymentId gravado, ocupa a vaga); a segunda tenta pagar agora e
        // precisa ser recusada, sem sequer chamar o Mercado Pago.
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111117");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555561");

        var cobrancaOcupante = new CobrancaEvento(igrejaId, eventoId,
            UUID.fromString("66666666-6666-6666-6666-666666666672"),
            UUID.fromString("33333333-3333-3333-3333-333333333339"),
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS),
            UUID.fromString("44444444-4444-4444-4444-444444444450"), null);
        cobrancaOcupante.registrarTentativaPagamento("mp-payment-ocupante");
        cobrancaEventoRepository.save(cobrancaOcupante);

        var cobrancaTardia = new CobrancaEvento(igrejaId, eventoId,
            UUID.fromString("66666666-6666-6666-6666-666666666673"),
            UUID.fromString("33333333-3333-3333-3333-333333333340"),
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS),
            UUID.fromString("44444444-4444-4444-4444-444444444450"), null);
        cobrancaTardia = cobrancaEventoRepository.save(cobrancaTardia);

        mockMvc.perform(post("/cobrancas/" + cobrancaTardia.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"depois@teste.com\",\"meio\":\"PIX\",\"parcelas\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("VAGAS_ESGOTADAS")));
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

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), Instant.now().minus(1, ChronoUnit.HOURS), usuarioId, null);
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1,\"payerEmail\":\"atrasado@teste.com\",\"meio\":\"PIX\",\"parcelas\":1}"))
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
            "('11111111-1111-1111-1111-111111111118', 'Igreja Teste 8', 'igreja8@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333341', '11111111-1111-1111-1111-111111111118', 'Sem Tentativa De Pagamento', 'semtentativa@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444451', '11111111-1111-1111-1111-111111111118', " +
            "'33333333-3333-3333-3333-333333333341', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777784', '11111111-1111-1111-1111-111111111118', 'Salão 8')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555562', '11111111-1111-1111-1111-111111111118', " +
            "'Retiro Sem Pagamento Iniciado', now(), '77777777-7777-7777-7777-777777777784', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666674', '11111111-1111-1111-1111-111111111118', " +
            "'55555555-5555-5555-5555-555555555562', '33333333-3333-3333-3333-333333333341', 'AGUARDANDO_PAGAMENTO')"
    })
    void retorna404ParaPixSemNenhumaTentativaDePagamento() throws Exception {
        // Sem mpPaymentId gravado (ninguém clicou "pagar" ainda) — o front só chama este
        // endpoint quando `pagamentoEmAndamento` já é true, mas o backend responde honesto
        // em vez de devolver um objeto com tudo nulo.
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111118");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555562");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666674");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333341");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444451");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(get("/cobrancas/" + cobranca.getId() + "/pix"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('11111111-1111-1111-1111-111111111119', 'Igreja Teste 9', 'igreja9@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333342', '11111111-1111-1111-1111-111111111119', 'Com Pagamento Em Andamento', 'comandamento@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444452', '11111111-1111-1111-1111-111111111119', " +
            "'33333333-3333-3333-3333-333333333342', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('77777777-7777-7777-7777-777777777785', '11111111-1111-1111-1111-111111111119', 'Salão 9')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('55555555-5555-5555-5555-555555555563', '11111111-1111-1111-1111-111111111119', " +
            "'Retiro Sem Conta Conectada', now(), '77777777-7777-7777-7777-777777777785', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('66666666-6666-6666-6666-666666666675', '11111111-1111-1111-1111-111111111119', " +
            "'55555555-5555-5555-5555-555555555563', '33333333-3333-3333-3333-333333333342', 'AGUARDANDO_PAGAMENTO')"
    })
    void recusaPixQuandoIgrejaNaoTemContaDePagamentoConectada() throws Exception {
        // Cobrança com mpPaymentId já gravado, mas sem ContaPagamentoIgreja pra essa igreja
        // — exercita o endpoint de ponta a ponta (controller -> MercadoPagoClient) sem
        // precisar mockar a chamada real ao Mercado Pago.
        UUID igrejaId = UUID.fromString("11111111-1111-1111-1111-111111111119");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555563");
        UUID inscricaoId = UUID.fromString("66666666-6666-6666-6666-666666666675");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333342");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444452");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobranca.registrarTentativaPagamento("mp-payment-sem-conta");
        cobranca = cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(get("/cobrancas/" + cobranca.getId() + "/pix"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("IGREJA_SEM_CONTA_PAGAMENTO")));
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
            igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("75.00"), Instant.now().plus(1, ChronoUnit.HOURS), usuarioId, null));

        mockMvc.perform(get("/cobrancas/id/" + cobranca.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(cobranca.getId().toString())))
            .andExpect(jsonPath("$.eventoId", is(eventoId.toString())))
            .andExpect(jsonPath("$.tituloEvento", is("Congresso Anual")))
            // Segurança (2026-08-26): e-mail nunca sai nesta rota pública, mesma regra de
            // CobrancaPublicaDTO — só rota autenticada exporia esse dado.
            .andExpect(jsonPath("$.emailPagador").doesNotExist())
            .andExpect(jsonPath("$.nomePagador", is("Ciclana")))
            .andExpect(jsonPath("$.valor", is(75.00)))
            .andExpect(jsonPath("$.status", is("PENDENTE")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('21111111-1111-1111-1111-111111111112', 'Igreja Teste Convidado', 'igrejaconv@teste.com')",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('27777777-7777-7777-7777-777777777778', '21111111-1111-1111-1111-111111111112', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('25555555-5555-5555-5555-555555555556', '21111111-1111-1111-1111-111111111112', " +
            "'Evento Com Convidado', '2026-09-11 19:00:00', '27777777-7777-7777-7777-777777777778', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, nome_convidado, telefone_convidado, status) VALUES " +
            "('26666666-6666-6666-6666-666666666667', '21111111-1111-1111-1111-111111111112', " +
            "'25555555-5555-5555-5555-555555555556', 'Convidado Sem Cadastro', '11988887777', 'AGUARDANDO_PAGAMENTO')"
    })
    void retornaContextoDaCobrancaDeConvidadoSemCadastro() throws Exception {
        UUID igrejaId = UUID.fromString("21111111-1111-1111-1111-111111111112");
        UUID eventoId = UUID.fromString("25555555-5555-5555-5555-555555555556");
        UUID inscricaoId = UUID.fromString("26666666-6666-6666-6666-666666666667");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(
            igrejaId, eventoId, inscricaoId, null,
            new BigDecimal("40.00"), Instant.now().plus(1, ChronoUnit.HOURS), null, null));

        mockMvc.perform(get("/cobrancas/id/" + cobranca.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nomePagador", is("Convidado Sem Cadastro")))
            .andExpect(jsonPath("$.emailPagador").doesNotExist());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('41111111-1111-1111-1111-111111111111', 'Igreja Teste Status', 'igrejastatus@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('43333333-3333-3333-3333-333333333333', '41111111-1111-1111-1111-111111111111', 'Beltrano', 'beltrano@teste.com')",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('47777777-7777-7777-7777-777777777777', '41111111-1111-1111-1111-111111111111', 'Salão Status')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('45555555-5555-5555-5555-555555555555', '41111111-1111-1111-1111-111111111111', " +
            "'Seminário', '2026-09-20 19:00:00', '47777777-7777-7777-7777-777777777777', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('46666666-6666-6666-6666-666666666666', '41111111-1111-1111-1111-111111111111', " +
            "'45555555-5555-5555-5555-555555555555', '43333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void statusReconfereNoMercadoPagoMasNaoQuebraQuandoAContaNaoEstaConectada() throws Exception {
        // Cobrança PENDENTE com tentativa de pagamento já registrada (mpPaymentId != null):
        // o /status agora dispara uma reconferência síncrona no Mercado Pago. Sem conta MP
        // conectada nesta igreja, essa reconferência falha lá dentro — e tem que ser
        // engolida (reconferirAgora captura), devolvendo o status do banco em vez de 500.
        UUID igrejaId = UUID.fromString("41111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("45555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("46666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("43333333-3333-3333-3333-333333333333");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("90.00"), Instant.now().plus(1, ChronoUnit.HOURS), pessoaId, null);
        cobranca.registrarTentativaPagamento("mp-payment-abc");
        cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(get("/cobrancas/" + cobranca.getId() + "/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PENDENTE")));
    }

    @Test
    void retorna404AoReiniciarCobrancaInexistenteSemPrecisarDeAutenticacao() throws Exception {
        mockMvc.perform(post("/cobrancas/" + UUID.randomUUID() + "/reiniciar"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('31111111-1111-1111-1111-111111111112', 'Igreja Teste Reiniciar', 'igrejareiniciar@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('33333333-3333-3333-3333-333333333336', '31111111-1111-1111-1111-111111111112', 'Titular Reiniciar', 'titular-reiniciar@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('44444444-4444-4444-4444-444444444447', '31111111-1111-1111-1111-111111111112', " +
            "'33333333-3333-3333-3333-333333333336', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('37777777-7777-7777-7777-777777777778', '31111111-1111-1111-1111-111111111112', 'Salão')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao) VALUES " +
            "('35555555-5555-5555-5555-555555555556', '31111111-1111-1111-1111-111111111112', " +
            "'Retiro Reiniciar', now(), '37777777-7777-7777-7777-777777777778', true)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('36666666-6666-6666-6666-666666666667', '31111111-1111-1111-1111-111111111112', " +
            "'35555555-5555-5555-5555-555555555556', '33333333-3333-3333-3333-333333333336', 'AGUARDANDO_PAGAMENTO')"
    })
    void reiniciarNaoFazNadaQuandoNaoHaTentativaEmAndamento() throws Exception {
        // Sem mpPaymentId (nenhuma tentativa de pagamento em voo) — não deveria nem tentar
        // falar com o Mercado Pago, só confirmar que não havia nada a liberar.
        UUID igrejaId = UUID.fromString("31111111-1111-1111-1111-111111111112");
        UUID eventoId = UUID.fromString("35555555-5555-5555-5555-555555555556");
        UUID inscricaoId = UUID.fromString("36666666-6666-6666-6666-666666666667");
        UUID pessoaId = UUID.fromString("33333333-3333-3333-3333-333333333336");
        UUID usuarioId = UUID.fromString("44444444-4444-4444-4444-444444444447");

        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(50), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null);
        cobrancaEventoRepository.save(cobranca);

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/reiniciar"))
            .andExpect(status().isOk());

        var atualizada = cobrancaEventoRepository.findById(cobranca.getId()).orElseThrow();
        assertThat(atualizada.getStatus()).isEqualTo(StatusCobranca.PENDENTE);
        assertThat(atualizada.getMpPaymentId()).isNull();
    }

    // ---------------------------------------------------------------------------------
    // Task 6: POST /cobrancas/{id}/pagar recalcula o valor no back (gross-up por meio/parcela)
    // ---------------------------------------------------------------------------------

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('51111111-1111-1111-1111-111111111111', 'Igreja Recalculo', 'recalculo@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('53333333-3333-3333-3333-333333333333', '51111111-1111-1111-1111-111111111111', 'Pagador Recalculo', 'pagrecalculo@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('54444444-4444-4444-4444-444444444444', '51111111-1111-1111-1111-111111111111', " +
            "'53333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('57777777-7777-7777-7777-777777777777', '51111111-1111-1111-1111-111111111111', 'Salão Recalculo')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco, pagamento_aceita_cartao, pagamento_max_parcelas) VALUES " +
            "('55555555-5555-5555-5555-555555555565', '51111111-1111-1111-1111-111111111111', " +
            "'Evento Pago Cartao', now(), '57777777-7777-7777-7777-777777777777', true, 100.00, true, 12)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('56666666-6666-6666-6666-666666666666', '51111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555565', '53333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_recalculaValorNoBack_ignorandoQualquerValorDoFront() throws Exception {
        UUID igrejaId = UUID.fromString("51111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555565");
        UUID inscricaoId = UUID.fromString("56666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("53333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("54444444-4444-4444-4444-444444444444");

        contaPagamentoIgrejaRepository.save(new ContaPagamentoIgreja(
            igrejaId, "mp-user-recalculo",
            credencialEncryptor.criptografar("access-token-fake"),
            credencialEncryptor.criptografar("refresh-token-fake"),
            Instant.now().plus(30, ChronoUnit.DAYS), usuarioId));

        when(mercadoPagoApi.criarPagamentoTokenizado(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MercadoPagoApi.ResultadoPagamento("mp-payment-recalculo", "approved", null, null, null, null));

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":1," +
                    "\"payerEmail\":\"p@x.com\",\"issuerId\":\"1\",\"meio\":\"CARTAO\",\"parcelas\":1}"))
            .andExpect(status().isOk());

        // 100 / (1 - 0.0449) = 104.7011... -> CEILING -> 104.71.
        // O nº de parcelas mandado ao MP é o `parcelas` validado (1), não o `installments`.
        ArgumentCaptor<BigDecimal> valor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(mercadoPagoApi).criarPagamentoTokenizado(any(), any(), valor.capture(),
            any(), any(), eq(1), any(), any());
        assertThat(valor.getValue()).isEqualByComparingTo("104.71");

        var atualizada = cobrancaEventoRepository.findById(cobranca.getId()).orElseThrow();
        assertThat(atualizada.getValorCobrado()).isEqualByComparingTo("104.71");
        assertThat(atualizada.getValor()).isEqualByComparingTo("100.00"); // alvo intacto
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('55111111-1111-1111-1111-111111111111', 'Igreja Installments', 'installments@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('55333333-3333-3333-3333-333333333333', '55111111-1111-1111-1111-111111111111', 'Pagador Installments', 'paginst@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('55444444-4444-4444-4444-444444444444', '55111111-1111-1111-1111-111111111111', " +
            "'55333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('55777777-7777-7777-7777-777777777777', '55111111-1111-1111-1111-111111111111', 'Salão Installments')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco, pagamento_aceita_cartao, pagamento_max_parcelas) VALUES " +
            "('55555555-5555-5555-5555-555555555575', '55111111-1111-1111-1111-111111111111', " +
            "'Evento Installments', now(), '55777777-7777-7777-7777-777777777777', true, 100.00, true, 12)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('55666666-6666-6666-6666-666666666666', '55111111-1111-1111-1111-111111111111', " +
            "'55555555-5555-5555-5555-555555555575', '55333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_ignoraInstallmentsDoFront_usaParcelasValidado() throws Exception {
        UUID igrejaId = UUID.fromString("55111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("55555555-5555-5555-5555-555555555575");
        UUID inscricaoId = UUID.fromString("55666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("55333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("55444444-4444-4444-4444-444444444444");

        contaPagamentoIgrejaRepository.save(new ContaPagamentoIgreja(
            igrejaId, "mp-user-installments",
            credencialEncryptor.criptografar("access-token-fake"),
            credencialEncryptor.criptografar("refresh-token-fake"),
            Instant.now().plus(30, ChronoUnit.DAYS), usuarioId));

        when(mercadoPagoApi.criarPagamentoTokenizado(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new MercadoPagoApi.ResultadoPagamento("mp-payment-inst", "approved", null, null, null, null));

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        // parcelas=1 (barato, passa no teto) mas installments=12 no corpo — o servidor
        // ignora installments e manda parcelas=1 pro Mercado Pago.
        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"tok\",\"paymentMethodId\":\"visa\",\"installments\":12," +
                    "\"payerEmail\":\"p@x.com\",\"issuerId\":\"1\",\"meio\":\"CARTAO\",\"parcelas\":1}"))
            .andExpect(status().isOk());

        verify(mercadoPagoApi).criarPagamentoTokenizado(any(), any(), any(),
            any(), any(), eq(1), any(), any());
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('52111111-1111-1111-1111-111111111111', 'Igreja Pix Parcela', 'pixparcela@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('52333333-3333-3333-3333-333333333333', '52111111-1111-1111-1111-111111111111', 'Pagador Pix', 'pagpix@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('52444444-4444-4444-4444-444444444444', '52111111-1111-1111-1111-111111111111', " +
            "'52333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('52777777-7777-7777-7777-777777777777', '52111111-1111-1111-1111-111111111111', 'Salão Pix')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco) VALUES " +
            "('52555555-5555-5555-5555-555555555555', '52111111-1111-1111-1111-111111111111', " +
            "'Evento Pix', now(), '52777777-7777-7777-7777-777777777777', true, 100.00)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('52666666-6666-6666-6666-666666666666', '52111111-1111-1111-1111-111111111111', " +
            "'52555555-5555-5555-5555-555555555555', '52333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_pixComParcelasMaiorQue1_recusa() throws Exception {
        UUID igrejaId = UUID.fromString("52111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("52555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("52666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("52333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("52444444-4444-4444-4444-444444444444");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"pix\",\"payerEmail\":\"p@x.com\",\"meio\":\"PIX\",\"parcelas\":2}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("PIX_NAO_PARCELA")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('53111111-1111-1111-1111-111111111111', 'Igreja So Pix', 'sopix@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('53433333-3333-3333-3333-333333333333', '53111111-1111-1111-1111-111111111111', 'Pagador So Pix', 'pagsopix@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('53444444-4444-4444-4444-444444444444', '53111111-1111-1111-1111-111111111111', " +
            "'53433333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('53777777-7777-7777-7777-777777777777', '53111111-1111-1111-1111-111111111111', 'Salão So Pix')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco, pagamento_aceita_cartao) VALUES " +
            "('53555555-5555-5555-5555-555555555555', '53111111-1111-1111-1111-111111111111', " +
            "'Evento So Pix', now(), '53777777-7777-7777-7777-777777777777', true, 100.00, false)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('53666666-6666-6666-6666-666666666666', '53111111-1111-1111-1111-111111111111', " +
            "'53555555-5555-5555-5555-555555555555', '53433333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_cartaoEmEventoQueSoAceitaPix_recusa() throws Exception {
        UUID igrejaId = UUID.fromString("53111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("53555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("53666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("53433333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("53444444-4444-4444-4444-444444444444");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"t\",\"paymentMethodId\":\"visa\",\"installments\":1," +
                    "\"payerEmail\":\"p@x.com\",\"meio\":\"CARTAO\",\"parcelas\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("CARTAO_NAO_ACEITO")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('54111111-1111-1111-1111-111111111111', 'Igreja Teto', 'teto@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('54333333-3333-3333-3333-333333333333', '54111111-1111-1111-1111-111111111111', 'Pagador Teto', 'pagteto@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('54544444-4444-4444-4444-444444444444', '54111111-1111-1111-1111-111111111111', " +
            "'54333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('54777777-7777-7777-7777-777777777777', '54111111-1111-1111-1111-111111111111', 'Salão Teto')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco, pagamento_aceita_cartao, pagamento_max_parcelas) VALUES " +
            "('54555555-5555-5555-5555-555555555555', '54111111-1111-1111-1111-111111111111', " +
            "'Evento Teto 3x', now(), '54777777-7777-7777-7777-777777777777', true, 100.00, true, 3)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('54666666-6666-6666-6666-666666666666', '54111111-1111-1111-1111-111111111111', " +
            "'54555555-5555-5555-5555-555555555555', '54333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_parcelasAcimaDoTetoDoEvento_recusa() throws Exception {
        UUID igrejaId = UUID.fromString("54111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("54555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("54666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("54333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("54544444-4444-4444-4444-444444444444");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("100.00"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"t\",\"paymentMethodId\":\"visa\",\"installments\":6," +
                    "\"payerEmail\":\"p@x.com\",\"meio\":\"CARTAO\",\"parcelas\":6}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("PARCELAS_ACIMA_DO_TETO")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('5a111111-1111-1111-1111-111111111111', 'Igreja Inviavel', 'inviavel@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('5a333333-3333-3333-3333-333333333333', '5a111111-1111-1111-1111-111111111111', 'Pagador Inviavel', 'paginviavel@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('5a444444-4444-4444-4444-444444444444', '5a111111-1111-1111-1111-111111111111', " +
            "'5a333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('5a777777-7777-7777-7777-777777777777', '5a111111-1111-1111-1111-111111111111', 'Salão Inviavel')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco, pagamento_aceita_cartao, pagamento_max_parcelas) VALUES " +
            "('5a555555-5555-5555-5555-555555555555', '5a111111-1111-1111-1111-111111111111', " +
            "'Evento Barato 6x', now(), '5a777777-7777-7777-7777-777777777777', true, 12.00, true, 6)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('5a666666-6666-6666-6666-666666666666', '5a111111-1111-1111-1111-111111111111', " +
            "'5a555555-5555-5555-5555-555555555555', '5a333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_parcelasInviaveisParaOValor_recusa() throws Exception {
        // Evento de R$ 12,00 aceita até 6x (passa no teto), mas 6x daria ~R$ 2,41/parcela,
        // abaixo do mínimo do Mercado Pago (R$ 5,00). Requisição forjada (a UI não oferece)
        // tem que ser recusada antes de chamar o MP.
        UUID igrejaId = UUID.fromString("5a111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("5a555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("5a666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("5a333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("5a444444-4444-4444-4444-444444444444");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("12.00"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"t\",\"paymentMethodId\":\"visa\",\"installments\":6," +
                    "\"payerEmail\":\"p@x.com\",\"meio\":\"CARTAO\",\"parcelas\":6}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("PARCELAS_INVIAVEIS_PARA_VALOR")));
    }

    @Test
    @Sql(statements = {
        "INSERT INTO igreja (id, nome, email) VALUES " +
            "('5b111111-1111-1111-1111-111111111111', 'Igreja Minimo Cartao', 'minimocartao@teste.com')",
        "INSERT INTO pessoa (id, igreja_id, nome, email) VALUES " +
            "('5b333333-3333-3333-3333-333333333333', '5b111111-1111-1111-1111-111111111111', 'Pagador Minimo', 'pagminimo@teste.com')",
        "INSERT INTO usuario (id, igreja_id, pessoa_id, role_id, ativo) VALUES " +
            "('5b444444-4444-4444-4444-444444444444', '5b111111-1111-1111-1111-111111111111', " +
            "'5b333333-3333-3333-3333-333333333333', (SELECT id FROM role WHERE nome = 'ADMIN_IGREJA'), true)",
        "INSERT INTO local_evento (id, igreja_id, nome) VALUES " +
            "('5b777777-7777-7777-7777-777777777777', '5b111111-1111-1111-1111-111111111111', 'Salão Minimo')",
        "INSERT INTO evento (id, igreja_id, titulo, inicio_em, local_id, requer_inscricao, preco, pagamento_aceita_cartao, pagamento_max_parcelas) VALUES " +
            "('5b555555-5555-5555-5555-555555555555', '5b111111-1111-1111-1111-111111111111', " +
            "'Evento Centavos', now(), '5b777777-7777-7777-7777-777777777777', true, 0.50, true, 1)",
        "INSERT INTO inscricao_evento (id, igreja_id, evento_id, pessoa_id, status) VALUES " +
            "('5b666666-6666-6666-6666-666666666666', '5b111111-1111-1111-1111-111111111111', " +
            "'5b555555-5555-5555-5555-555555555555', '5b333333-3333-3333-3333-333333333333', 'AGUARDANDO_PAGAMENTO')"
    })
    void pagar_totalAbaixoDoMinimoDeCartao_recusa() throws Exception {
        // R$ 0,50: mesmo 1x, o total com gross-up (~R$ 0,53) fica abaixo do mínimo de
        // cartão do MP (R$ 1,00) — cartão não é opção pra esse valor.
        UUID igrejaId = UUID.fromString("5b111111-1111-1111-1111-111111111111");
        UUID eventoId = UUID.fromString("5b555555-5555-5555-5555-555555555555");
        UUID inscricaoId = UUID.fromString("5b666666-6666-6666-6666-666666666666");
        UUID pessoaId = UUID.fromString("5b333333-3333-3333-3333-333333333333");
        UUID usuarioId = UUID.fromString("5b444444-4444-4444-4444-444444444444");

        var cobranca = cobrancaEventoRepository.save(new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
            new BigDecimal("0.50"), Instant.now().plus(1, ChronoUnit.DAYS), usuarioId, null));

        mockMvc.perform(post("/cobrancas/" + cobranca.getId() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"t\",\"paymentMethodId\":\"visa\",\"installments\":1," +
                    "\"payerEmail\":\"p@x.com\",\"meio\":\"CARTAO\",\"parcelas\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("CARTAO_VALOR_MINIMO")));
    }

    @Test
    void pagar_semMeio_recusaComoValidacao() throws Exception {
        mockMvc.perform(post("/cobrancas/" + UUID.randomUUID() + "/pagar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentMethodId\":\"pix\",\"payerEmail\":\"p@x.com\",\"parcelas\":1}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.campos.meio").exists());
    }
}
