package com.domus.api.modules.pagamento.webhook;

import static org.mockito.Mockito.*;

import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MercadoPagoWebhookServiceTest {

    CobrancaEventoRepository cobrancaRepository;
    NotificacaoService notificacaoService;
    MercadoPagoWebhookService service;

    @BeforeEach
    void setup() {
        cobrancaRepository = mock(CobrancaEventoRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        service = new MercadoPagoWebhookService(cobrancaRepository, notificacaoService);
    }

    @Test
    void confirmaCobrancaEncontradaPeloExternalReferenceQuandoStatusEhAprovado() {
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        assertThatCobrancaFoiMarcadaPaga(cobranca);
        verify(cobrancaRepository).save(cobranca);
    }

    @Test
    void ignoraSilenciosamenteQuandoCobrancaNaoExiste() {
        when(cobrancaRepository.findById(any())).thenReturn(Optional.empty());

        service.confirmarPagamento(UUID.randomUUID().toString(), "mp-payment-999", "approved");

        verify(cobrancaRepository, never()).save(any());
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void naoNotificaQuandoCobrancaEhDoProprioTitular() {
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verifyNoInteractions(notificacaoService);
    }

    @Test
    void notificaCriadorQuandoCobrancaEhDeAcompanhante() {
        UUID cobrancaId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        UUID criadoPorUsuarioId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(igrejaId, UUID.randomUUID(), UUID.randomUUID(),
            null, UUID.randomUUID(), BigDecimal.TEN, Instant.now().plusSeconds(600), criadoPorUsuarioId, null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verify(notificacaoService).criar(
            eq(TipoNotificacao.COBRANCA_EVENTO_PAGA),
            eq(igrejaId),
            eq(criadoPorUsuarioId),
            anyString(),
            anyString());
    }

    @Test
    void notificaQuemGerouOLinkQuandoCobrancaEhDeOutraPessoaCadastradaComLink() {
        // Important 6 (revisão final de branch): o discriminador antigo (pessoaId != null)
        // classificava essa cobrança (link gerado pra OUTRA pessoa cadastrada, não
        // acompanhante) como "do titular" — e por isso quem gerou o link nunca era
        // notificado. Agora o discriminador correto é tokenLinkPublico != null.
        UUID cobrancaId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        UUID criadoPorUsuarioId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(igrejaId, UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600),
            criadoPorUsuarioId, "token-abc");
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verify(notificacaoService).criar(
            eq(TipoNotificacao.COBRANCA_EVENTO_PAGA),
            eq(igrejaId),
            eq(criadoPorUsuarioId),
            anyString(),
            anyString());
    }

    @Test
    void naoConfirmaQuandoStatusEhPendente() {
        // Critical 2 (revisão final de branch): PIX ainda não pago não pode confirmar a cobrança.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "pending");

        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        verify(cobrancaRepository, never()).save(any());
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void naoConfirmaQuandoStatusEhRecusado() {
        // Critical 2 (revisão final de branch): cartão recusado não pode confirmar a cobrança.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "rejected");

        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        verify(cobrancaRepository, never()).save(any());
        verifyNoInteractions(notificacaoService);
        // Não deve nem chegar a buscar a cobrança no repositório — recusado sai antes disso.
        verify(cobrancaRepository, never()).findById(any());
    }

    private void assertThatCobrancaFoiMarcadaPaga(CobrancaEvento cobranca) {
        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
    }
}
