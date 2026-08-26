package com.domus.api.modules.pagamento.webhook;

import static org.mockito.Mockito.*;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.notificacao.NotificacaoService;
import com.domus.api.modules.notificacao.TipoNotificacao;
import com.domus.api.modules.pagamento.cobranca.CobrancaEvento;
import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.shared.email.EmailService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MercadoPagoWebhookServiceTest {

    CobrancaEventoRepository cobrancaRepository;
    InscricaoRepository inscricaoRepository;
    NotificacaoService notificacaoService;
    EventoRepository eventoRepository;
    PessoaRepository pessoaRepository;
    EmailService emailService;
    MercadoPagoWebhookService service;

    @BeforeEach
    void setup() {
        cobrancaRepository = mock(CobrancaEventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        notificacaoService = mock(NotificacaoService.class);
        eventoRepository = mock(EventoRepository.class);
        pessoaRepository = mock(PessoaRepository.class);
        emailService = mock(EmailService.class);
        service = new MercadoPagoWebhookService(cobrancaRepository, inscricaoRepository, notificacaoService,
                eventoRepository, pessoaRepository, emailService);
    }

    private Igreja igreja(UUID id, String nome) {
        Igreja i = new Igreja();
        i.setId(id);
        i.setNome(nome);
        return i;
    }

    private Evento evento(UUID id, Igreja igreja) {
        return Evento.builder().id(id).igreja(igreja).titulo("Retiro")
                .inicioEm(LocalDateTime.now().plusDays(5)).requerInscricao(true).build();
    }

    @Test
    void confirmaInscricaoVinculadaQuandoPagamentoAprovado() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), inscricaoId,
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(inscricaoRepository).save(inscricao);
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
    void naoTentaNotificarQuandoCriadoPorUsuarioIdEhNulo() {
        // Plano 4b — convidado sem cadastro via convite público (inscritoPorUsuarioId=null):
        // a cobrança não tem criadoPorUsuarioId nenhum pra notificar. ehDoTitular() é false
        // (pessoaId/acompanhanteId ambos nulos), então sem esta guarda o notificacaoService
        // seria chamado com destinatarioId=null.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            null, null, BigDecimal.TEN, Instant.now().plusSeconds(600), null, null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verifyNoInteractions(notificacaoService);
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
        // Ajustado na fix wave de 2026-08-25: "rejected" é status TERMINAL não aprovado, então
        // agora BUSCA a cobrança e limpa o mpPaymentId (ver liberaMpPaymentIdQuandoStatusEhRecusado
        // abaixo) — mas continua sem marcar como PAGO nem notificar. A asserção antiga
        // (never().findById) provava um comportamento que o próprio bug desta fix wave corrige;
        // a asserção de "nunca fica PAGO" e "nunca notifica" continua de pé.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "rejected");

        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        verifyNoInteractions(notificacaoService);
    }

    @Test
    void liberaMpPaymentIdQuandoStatusEhRecusado() {
        // Fix wave (2026-08-25): cobrança com mpPaymentId de uma tentativa anterior (gravado
        // por CobrancaController.pagar assim que o pagamento é criado no MP) tem o campo
        // limpo quando o webhook chega com "rejected" — liberando uma nova tentativa de
        // pagamento (outro cartão, PIX) sem cair em COBRANCA_JA_EM_PROCESSAMENTO.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        cobranca.registrarTentativaPagamento("mp-payment-tentativa-1");
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-tentativa-1", "rejected");

        org.assertj.core.api.Assertions.assertThat(cobranca.getMpPaymentId()).isNull();
        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        verify(cobrancaRepository).save(cobranca);
    }

    @Test
    void liberaMpPaymentIdQuandoStatusEhCancelado() {
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        cobranca.registrarTentativaPagamento("mp-payment-tentativa-1");
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-tentativa-1", "cancelled");

        org.assertj.core.api.Assertions.assertThat(cobranca.getMpPaymentId()).isNull();
        verify(cobrancaRepository).save(cobranca);
    }

    @Test
    void naoLiberaMpPaymentIdQuandoStatusEhPendente() {
        // Status não-terminal (pending/in_process) ainda pode virar approved depois — não
        // libera retry, senão criaria pagamento duplicado pro mesmo PIX pendente.
        UUID cobrancaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(), null, BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        cobranca.registrarTentativaPagamento("mp-payment-tentativa-1");
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-tentativa-1", "pending");

        org.assertj.core.api.Assertions.assertThat(cobranca.getMpPaymentId())
            .isEqualTo("mp-payment-tentativa-1");
        verify(cobrancaRepository, never()).save(any());
        verify(cobrancaRepository, never()).findById(any());
    }

    @Test
    void enviaEmailDeConfirmacaoQuandoPessoaCadastradaTemEmail() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));
        Igreja igrejaDaPessoa = igreja(igrejaId, "Igreja Batista");
        Pessoa pessoa = Pessoa.builder().id(pessoaId).igreja(igrejaDaPessoa)
                .nome("Maria").email("maria@teste.com").build();
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa));
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento(eventoId, igrejaDaPessoa)));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verify(emailService).enviar(eq("maria@teste.com"), contains("Retiro"), anyString());
    }

    @Test
    void naoEnviaEmailQuandoCobrancaEhDeAcompanhanteSemEmail() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(UUID.randomUUID(), UUID.randomUUID(), inscricaoId,
            null, UUID.randomUUID(), BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verifyNoInteractions(emailService);
    }

    @Test
    void enviaEmailDeConfirmacaoQuandoConvidadoSemCadastroTemEmail() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, null, null,
            BigDecimal.TEN, Instant.now().plusSeconds(600), null, null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO)
                .nomeConvidado("Fulano de Fora").emailConvidado("fulano@teste.com").build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));
        Igreja igrejaOrganizadora = igreja(igrejaId, "Igreja Batista");
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento(eventoId, igrejaOrganizadora)));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verify(emailService).enviar(eq("fulano@teste.com"), contains("Retiro"), anyString());
    }

    @Test
    void emailMencionaIgrejaOrganizadoraQuandoEventoEhDeOutraIgreja() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID igrejaDoEventoId = UUID.randomUUID();
        UUID igrejaDaPessoaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(igrejaDoEventoId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));
        Pessoa pessoa = Pessoa.builder().id(pessoaId).igreja(igreja(igrejaDaPessoaId, "Congregação Filha"))
                .nome("Maria").email("maria@teste.com").build();
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa));
        Igreja igrejaOrganizadora = igreja(igrejaDoEventoId, "Igreja Sede");
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento(eventoId, igrejaOrganizadora)));

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        verify(emailService).enviar(eq("maria@teste.com"), anyString(), contains("Igreja Sede"));
    }

    @Test
    void naoQuebraConfirmacaoQuandoEnvioDeEmailFalha() {
        UUID cobrancaId = UUID.randomUUID();
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID igrejaId = UUID.randomUUID();
        var cobranca = new CobrancaEvento(igrejaId, eventoId, inscricaoId, pessoaId, null,
            BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);
        when(cobrancaRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(inscricaoId).status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(inscricao));
        Igreja igrejaDaPessoa = igreja(igrejaId, "Igreja Batista");
        Pessoa pessoa = Pessoa.builder().id(pessoaId).igreja(igrejaDaPessoa)
                .nome("Maria").email("maria@teste.com").build();
        when(pessoaRepository.findById(pessoaId)).thenReturn(Optional.of(pessoa));
        when(eventoRepository.findById(eventoId)).thenReturn(Optional.of(evento(eventoId, igrejaDaPessoa)));
        doThrow(new RuntimeException("Falha no provedor de e-mail"))
                .when(emailService).enviar(anyString(), anyString(), anyString());

        service.confirmarPagamento(cobrancaId.toString(), "mp-payment-999", "approved");

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(inscricaoRepository).save(inscricao);
    }

    private void assertThatCobrancaFoiMarcadaPaga(CobrancaEvento cobranca) {
        org.assertj.core.api.Assertions.assertThat(cobranca.getStatus())
            .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
    }
}
