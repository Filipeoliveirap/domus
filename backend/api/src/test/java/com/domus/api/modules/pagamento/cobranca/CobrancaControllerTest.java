package com.domus.api.modules.pagamento.cobranca;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
