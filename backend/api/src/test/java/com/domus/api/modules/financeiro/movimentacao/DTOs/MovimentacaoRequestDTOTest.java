package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * contribuintes não tinha @Valid — o @NotNull de ContribuinteDTO.valor/pessoaId nunca era
 * checado (Bean Validation só cascateia pra dentro de listas com @Valid no campo), e um
 * valor nulo estourava NullPointerException em MovimentacaoFinanceiraService.validarContribuintes
 * (BigDecimal::add) em vez de dar 400 limpo.
 */
class MovimentacaoRequestDTOTest {

    private MovimentacaoRequestDTO base(List<ContribuinteDTO> contribuintes) {
        return new MovimentacaoRequestDTO(
                TipoMovimentacao.ENTRADA, BigDecimal.TEN, UUID.randomUUID(),
                LocalDate.now(), contribuintes, null);
    }

    @Test
    void contribuinteValido_naoGeraViolacao() {
        var contribuintes = List.of(new ContribuinteDTO(UUID.randomUUID(), BigDecimal.TEN));
        assertThat(VALIDATOR.validate(base(contribuintes))).isEmpty();
    }

    @Test
    void contribuinteComValorNulo_recusaComViolacao() {
        var contribuintes = List.of(new ContribuinteDTO(UUID.randomUUID(), null));
        assertThat(VALIDATOR.validate(base(contribuintes))).isNotEmpty();
    }

    @Test
    void contribuinteComPessoaIdNulo_recusaComViolacao() {
        var contribuintes = List.of(new ContribuinteDTO(null, BigDecimal.TEN));
        assertThat(VALIDATOR.validate(base(contribuintes))).isNotEmpty();
    }

    @Test
    void listaComMaisDe200Contribuintes_recusaComViolacao() {
        var contribuintes = java.util.stream.IntStream.range(0, 201)
                .mapToObj(i -> new ContribuinteDTO(UUID.randomUUID(), BigDecimal.ONE))
                .toList();
        assertThat(VALIDATOR.validate(base(contribuintes))).isNotEmpty();
    }
}
