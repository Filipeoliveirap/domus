package com.domus.api.modules.evento.DTOs;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

class EventoRequestTest {

    private EventoRequest base(String titulo, String descricao, String localTexto, String tipo, String recorteEtario) {
        return new EventoRequest(
                titulo, descricao, LocalDateTime.now().plusDays(1), null,
                null, localTexto, tipo, null, recorteEtario,
                null, null, null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }

    private EventoRequest baseComPagamento(java.math.BigDecimal preco, Boolean aceitaCartao, Integer maxParcelas) {
        return new EventoRequest(
                "Culto", "descrição curta", LocalDateTime.now().plusDays(1), null,
                null, "Salão", "Culto", null, "Jovens",
                null, null, null, null,
                null, preco, aceitaCartao, maxParcelas, null, null, null,
                null, null, null, null, null, null, null);
    }

    @Test
    void camposDentroDoLimite_naoGeraViolacao() {
        Set<?> violacoes = VALIDATOR.validate(base("Culto", "descrição curta", "Salão", "Culto", "Jovens"));
        assertThat(violacoes).isEmpty();
    }

    @Test
    void descricaoAcimaDe5000_recusaComViolacao() {
        Set<?> violacoes = VALIDATOR.validate(base("Culto", "a".repeat(5001), null, null, null));
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void tituloAcimaDe255_recusaComViolacao() {
        Set<?> violacoes = VALIDATOR.validate(base("a".repeat(256), null, null, null, null));
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void localTextoAcimaDe255_recusaComViolacao() {
        Set<?> violacoes = VALIDATOR.validate(base("Culto", null, "a".repeat(256), null, null));
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void tipoAcimaDe80_recusaComViolacao() {
        Set<?> violacoes = VALIDATOR.validate(base("Culto", null, null, "a".repeat(81), null));
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void recorteEtarioAcimaDe40_recusaComViolacao() {
        Set<?> violacoes = VALIDATOR.validate(base("Culto", null, null, null, "a".repeat(41)));
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void maxParcelasAcimaDe12_recusaComViolacao() {
        var req = baseComPagamento(new java.math.BigDecimal("100.00"), true, 13);
        assertThat(VALIDATOR.validate(req)).isNotEmpty();
    }

    @Test
    void maxParcelasZero_recusaComViolacao() {
        var req = baseComPagamento(new java.math.BigDecimal("100.00"), true, 0);
        assertThat(VALIDATOR.validate(req)).isNotEmpty();
    }

    @Test
    void pagamentoValido_naoGeraViolacao() {
        var req = baseComPagamento(new java.math.BigDecimal("100.00"), true, 6);
        assertThat(VALIDATOR.validate(req)).isEmpty();
    }
}
