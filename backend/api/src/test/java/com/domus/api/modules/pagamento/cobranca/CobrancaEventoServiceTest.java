package com.domus.api.modules.pagamento.cobranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.domus.api.modules.evento.Evento;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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

    /** Evento sem prazo de inscrição próprio e com início distante — não interfere no
     *  teto de 48h do link, comportamento equivalente ao anterior à mudança. */
    private Evento evento(LocalDateTime inicioEm, LocalDateTime inscricoesAte) {
        return Evento.builder().id(eventoId).inicioEm(inicioEm).inscricoesAte(inscricoesAte).build();
    }

    private Evento eventoDistante() {
        return evento(LocalDateTime.now().plusDays(30), null);
    }

    @BeforeEach
    void setup() {
        repository = mock(CobrancaEventoRepository.class);
        service = new CobrancaEventoService(repository,
            mock(com.domus.api.modules.pagamento.CalculadoraTaxaPagamento.class),
            new com.domus.api.modules.pagamento.LimitesPagamentoProperties(
                new BigDecimal("1.00"), new BigDecimal("5.00")));
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
        var cobranca = service.criarParaTerceiro(igrejaId, eventoDistante(), inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId, false);

        assertThat(cobranca.getPessoaId()).isEqualTo(pessoaId);
        assertThat(cobranca.getTokenLinkPublico()).isNull();
    }

    @Test
    void criaCobrancaParaTerceiroComLinkGeraTokenEPrazoLongo() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoDistante(), inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId, true);

        assertThat(cobranca.getTokenLinkPublico()).isNotBlank();
        assertThat(cobranca.getExpiraEm()).isAfter(Instant.now().plus(23, java.time.temporal.ChronoUnit.HOURS));
    }

    @Test
    void linkNaoSobrevoaOPrazoDeInscricaoQuandoMaisCurtoQue48Horas() {
        LocalDateTime prazoEmSeisHoras = LocalDateTime.now().plusHours(6);
        var evento = evento(LocalDateTime.now().plusDays(30), prazoEmSeisHoras);

        var cobranca = service.criarParaTerceiro(igrejaId, evento, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId, true);

        assertThat(cobranca.getExpiraEm())
            .isAfter(Instant.now().plus(5, java.time.temporal.ChronoUnit.HOURS))
            .isBefore(Instant.now().plus(7, java.time.temporal.ChronoUnit.HOURS));
    }

    @Test
    void linkUsaInicioDoEventoComoTetoQuandoNaoHaPrazoDeInscricaoProprio() {
        LocalDateTime inicioEmDezHoras = LocalDateTime.now().plusHours(10);
        var evento = evento(inicioEmDezHoras, null);

        var cobranca = service.criarParaTerceiro(igrejaId, evento, inscricaoId, pessoaId,
            BigDecimal.valueOf(150), usuarioId, true);

        assertThat(cobranca.getExpiraEm())
            .isAfter(Instant.now().plus(9, java.time.temporal.ChronoUnit.HOURS))
            .isBefore(Instant.now().plus(11, java.time.temporal.ChronoUnit.HOURS));
    }

    @Test
    void tokensGeradosNaoSeRepetem() {
        var c1 = service.criarParaTerceiro(igrejaId, eventoDistante(), inscricaoId, pessoaId,
            BigDecimal.TEN, usuarioId, true);
        var c2 = service.criarParaTerceiro(igrejaId, eventoDistante(), inscricaoId, UUID.randomUUID(),
            BigDecimal.TEN, usuarioId, true);

        assertThat(c1.getTokenLinkPublico()).isNotEqualTo(c2.getTokenLinkPublico());
    }

    @Test
    void criaCobrancaParaTerceiroConvidadoSemCadastroComPessoaIdNulo() {
        var cobranca = service.criarParaTerceiro(igrejaId, eventoDistante(), inscricaoId, null,
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
