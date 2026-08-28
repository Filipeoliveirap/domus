package com.domus.api.modules.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CobrancaEventoServiceTest {

    CobrancaEventoRepository repository;
    CobrancaEventoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID inscricaoId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        service = new CobrancaEventoService(repository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criaCobrancaParaTitularComPrazoCurtoESemToken() {
        var cobranca = service.criarParaTitular(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId);

        assertThat(cobranca.getPessoaId()).isEqualTo(pessoaId);
        assertThat(cobranca.getTokenLinkPublico()).isNull();
        assertThat(cobranca.getExpiraEm()).isBefore(Instant.now().plus(31, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void criaCobrancaParaTerceiroPagandoAgoraComPrazoCurtoESemToken() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId, false);

        assertThat(cobranca.getPessoaId()).isEqualTo(pessoaId);
        assertThat(cobranca.getTokenLinkPublico()).isNull();
    }

    @Test
    void criaCobrancaParaTerceiroComLinkGeraTokenEPrazoLongo() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId, true);

        assertThat(cobranca.getTokenLinkPublico()).isNotBlank();
        assertThat(cobranca.getExpiraEm()).isAfter(Instant.now().plus(23, java.time.temporal.ChronoUnit.HOURS));
    }

    @Test
    void tokensGeradosNaoSeRepetem() {
        var c1 = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, pessoaId,
            BigDecimal.TEN, usuarioId, true);
        var c2 = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, UUID.randomUUID(),
            BigDecimal.TEN, usuarioId, true);

        assertThat(c1.getTokenLinkPublico()).isNotEqualTo(c2.getTokenLinkPublico());
    }

    @Test
    void criaCobrancaParaTerceiroConvidadoSemCadastroComPessoaIdNulo() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoId, inscricaoId, null,
            BigDecimal.valueOf(150), usuarioId, false);

        assertThat(cobranca.getPessoaId()).isNull();
    }

    @Test
    void buscarPorTokenLancaExcecaoQuandoNaoEncontrado() {
        when(repository.findByTokenLinkPublico("inexistente")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.buscarPorToken("inexistente"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("codigo", "LINK_COBRANCA_INVALIDO");
    }
}
