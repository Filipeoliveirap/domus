package com.domus.api.modules.pagamento.cobranca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CobrancaEventoTest {

    private final UUID igrejaId = UUID.randomUUID();
    private final UUID eventoId = UUID.randomUUID();
    private final UUID inscricaoId = UUID.randomUUID();
    private final UUID pessoaId = UUID.randomUUID();
    private final UUID usuarioId = UUID.randomUUID();

    // ---- Task 8: estorno opera sobre o valor BRUTO cobrado (alvo + taxa), não o alvo ----

    @Test
    void valorRestanteParaEstornar_usaValorCobradoQuandoPresente() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        c.registrarValorCobrado(new BigDecimal("110.49"));
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("110.49");
    }

    @Test
    void valorRestanteParaEstornar_caiNoAlvoQuandoNuncaFoiCobrado() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("100.00");
    }

    @Test
    void valorRestanteParaEstornar_descontaEstornoParcialJaFeito() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        c.registrarValorCobrado(new BigDecimal("110.49"));
        c.registrarEstorno(new BigDecimal("10.49"));
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("100.00");
    }

    @Test
    void registrarEstorno_marcaReembolsadoContraOBrutoCobradoNaoOAlvo() {
        var c = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId,
                new BigDecimal("100.00"), Instant.now().plusSeconds(3600), usuarioId, null);
        c.marcarComoPago("mp-1");
        c.registrarValorCobrado(new BigDecimal("110.49"));

        // devolver só o alvo (100) NÃO zera a cobrança — ainda falta a taxa
        c.registrarEstorno(new BigDecimal("100.00"));
        assertThat(c.getStatus()).isEqualTo(StatusCobranca.PAGO);

        // devolver o restante (a taxa) fecha como REEMBOLSADO
        c.registrarEstorno(new BigDecimal("10.49"));
        assertThat(c.getStatus()).isEqualTo(StatusCobranca.REEMBOLSADO);
        assertThat(c.valorRestanteParaEstornar()).isEqualByComparingTo("0");
    }

    @Test
    void aceitaPessoaIdNuloParaConvidadoSemCadastro() {
        // Convidado sem cadastro (V30): pessoaId nulo, resolvido só por inscricaoId
        // (ver CobrancaController) — cada "acompanhante" virou sua própria InscricaoEvento,
        // então CobrancaEvento não tem mais um segundo campo de pagador pra conflitar.
        assertThatCode(() -> new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                BigDecimal.TEN, Instant.now().plusSeconds(600), null, null))
            .doesNotThrowAnyException();
    }

    @Test
    void aceitaPessoaIdPreenchidaParaPagadorComCadastro() {
        var cobranca = new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);

        assertThat(cobranca.getPessoaId()).isNotNull();
    }
}
