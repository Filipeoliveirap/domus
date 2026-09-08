package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.regras.RegraEstadoCivil;
import com.domus.api.modules.evento.elegibilidade.regras.RegraFaixaEtaria;
import com.domus.api.modules.evento.elegibilidade.regras.RegraSexo;
import com.domus.api.modules.evento.elegibilidade.regras.RegraVinculo;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Task 5 (V38): a guarda de prazo de inscrição ({@code inscricoes_ate}) barra o membro
 *  comum e o link público depois do prazo; admin/líder passam (capacidade, não identidade). */
class InscricaoPrazoTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    PessoaRepository membroRepository;
    UsuarioRepository usuarioRepository;
    VisitanteRepository visitanteRepository;
    ElegibilidadeService elegibilidadeService;
    FamiliaIgrejaService familiaIgrejaService;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository campoPersonalizadoRepository;
    com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository respostaCampoPersonalizadoRepository;
    com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository cobrancaEventoRepository;
    com.domus.api.modules.pagamento.MercadoPagoClient mercadoPagoClient;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID adminUsuarioId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        membroRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        visitanteRepository = mock(VisitanteRepository.class);
        elegibilidadeService = new ElegibilidadeService(List.of(
                new RegraFaixaEtaria(), new RegraVinculo(),
                new RegraEstadoCivil(), new RegraSexo()));
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        when(familiaIgrejaService.idsDaFamiliaCompleta(any())).thenReturn(java.util.Set.of(igrejaId));
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        campoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository.class);
        respostaCampoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository.class);
        cobrancaEventoRepository = mock(com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository.class);
        mercadoPagoClient = mock(com.domus.api.modules.pagamento.MercadoPagoClient.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                membroRepository, usuarioRepository, visitanteRepository,
                elegibilidadeService, familiaIgrejaService, notificacaoService,
                campoPersonalizadoRepository, respostaCampoPersonalizadoRepository,
                mock(com.domus.api.modules.pagamento.cobranca.CobrancaEventoService.class),
                cobrancaEventoRepository,
                mercadoPagoClient,
                contaPagamentoIgrejaRepositoryComContaConectada(),
                mock(com.domus.api.shared.email.EmailService.class),
                mock(com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService.class));
    }

    private static com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository
            contaPagamentoIgrejaRepositoryComContaConectada() {
        var repo = mock(com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository.class);
        when(repo.findByIgrejaId(any())).thenReturn(java.util.Optional.of(
                mock(com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja.class)));
        return repo;
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private void dado(Evento e, Pessoa pessoa, long ocupadas) {
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, java.util.Set.of(igrejaId))).thenReturn(Optional.of(e));
        when(membroRepository.findByIdAndIgrejaId(pessoa.getId(), igrejaId)).thenReturn(Optional.of(pessoa));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoa.getId())).thenReturn(Optional.empty());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(ocupadas);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private Evento eventoComPrazo(LocalDateTime inscricoesAte, boolean permiteCancelar) {
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Congresso").inicioEm(LocalDateTime.now().plusDays(20))
                .requerInscricao(true)
                .inscricoesAte(inscricoesAte)
                .politicaCancelamentoAposPrazo(permiteCancelar
                        ? com.domus.api.modules.evento.PoliticaCancelamentoAposPrazo.PERMITIDO_COM_REEMBOLSO
                        : com.domus.api.modules.evento.PoliticaCancelamentoAposPrazo.NAO_PERMITIDO)
                .build();
    }

    private Evento eventoComPrazo(LocalDateTime inscricoesAte,
            com.domus.api.modules.evento.PoliticaCancelamentoAposPrazo politica) {
        var e = eventoComPrazo(inscricoesAte, true);
        e.setPoliticaCancelamentoAposPrazo(politica);
        return e;
    }

    private Pessoa pessoaComum() {
        return Pessoa.builder()
                .id(UUID.randomUUID()).igreja(igreja()).nome("Fulano").email("f@e.com")
                .vinculo(com.domus.api.modules.pessoa.Vinculo.MEMBRO)
                .build();
    }

    @Test
    void recusaAutoInscricaoDeComumDepoisDoPrazo() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        assertThatThrownBy(() -> service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo("PRAZO_INSCRICAO_ENCERRADO");
        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void permiteAutoInscricaoDeAdminDepoisDoPrazo() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ADMIN_IGREJA", false, igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void permiteAutoInscricaoDeComumAntesDoPrazo() {
        var evento = eventoComPrazo(LocalDateTime.now().plusDays(5), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void semPrazoInscreveComoHoje() {
        var evento = eventoComPrazo(null, true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void reinscricaoRecusadaDepoisDoPrazoParaComum() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        dado(evento, pessoa, 0);
        var cancelada = InscricaoEvento.builder()
                .igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoa.getId()))
                .thenReturn(java.util.Optional.of(cancelada));
        assertThatThrownBy(() -> service.inscrever(eventoId, pessoa.getId(), null, pessoa.getId(),
                "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo("PRAZO_INSCRICAO_ENCERRADO");
        verify(inscricaoRepository, never()).save(any());
    }

    // --- Task 6: guarda de cancelamento ---

    private InscricaoEvento inscricaoDe(Evento evento, Pessoa pessoa) {
        var inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento).pessoa(pessoa)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(eq(inscricao.getId()), any()))
                .thenReturn(Optional.of(inscricao));
        return inscricao;
    }

    @Test
    void cancelamentoBloqueadoQuandoToggleDesligadoEPrazoVencidoParaComum() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), false);
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        assertThatThrownBy(() -> service.cancelar(inscricao.getId(), UUID.randomUUID(),
                pessoa.getId(), "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .extracting("codigo").isEqualTo("CANCELAMENTO_ENCERRADO_POR_PRAZO");
        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void cancelamentoLivreQuandoToggleLigado() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), true);
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        service.cancelar(inscricao.getId(), UUID.randomUUID(), pessoa.getId(),
                "ACESSO_COMUM", igrejaId);
        verify(inscricaoRepository).save(any());
    }

    @Test
    void gestorCancelaMesmoComToggleDesligado() {
        var evento = eventoComPrazo(LocalDateTime.now().minusHours(1), false);
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        service.cancelar(inscricao.getId(), UUID.randomUUID(), UUID.randomUUID(),
                "ADMIN_IGREJA", igrejaId);
        verify(inscricaoRepository).save(any());
    }

    // --- Adendo 2026-09-07: cancelamento após o prazo em evento pago não estorna ---

    private Evento eventoPagoComPrazo(LocalDateTime inscricoesAte, boolean permiteCancelar) {
        var e = eventoComPrazo(inscricoesAte, permiteCancelar);
        e.setPreco(new java.math.BigDecimal("50.00"));
        return e;
    }

    private Evento eventoPagoSemReembolsoAposPrazo(LocalDateTime inscricoesAte) {
        var e = eventoComPrazo(inscricoesAte,
                com.domus.api.modules.evento.PoliticaCancelamentoAposPrazo.PERMITIDO_SEM_REEMBOLSO);
        e.setPreco(new java.math.BigDecimal("50.00"));
        return e;
    }

    private com.domus.api.modules.pagamento.cobranca.CobrancaEvento cobranca(
            UUID inscricaoId, com.domus.api.modules.pagamento.cobranca.StatusCobranca status) {
        var c = mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class);
        when(c.getInscricaoId()).thenReturn(inscricaoId);
        when(c.getStatus()).thenReturn(status);
        when(c.getMpPaymentId()).thenReturn("mp-1");
        when(c.valorRestanteParaEstornar()).thenReturn(new java.math.BigDecimal("50.00"));
        return c;
    }

    @Test
    void cancelamentoAntesDoPrazoEstornaNormalmente() {
        var evento = eventoPagoComPrazo(LocalDateTime.now().plusDays(5), true);
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        var pago = cobranca(inscricao.getId(), com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
        when(cobrancaEventoRepository.findByInscricaoId(inscricao.getId())).thenReturn(List.of(pago));

        service.cancelar(inscricao.getId(), UUID.randomUUID(), pessoa.getId(), "ACESSO_COMUM", igrejaId);

        verify(mercadoPagoClient).estornarParcial(eq(igrejaId), eq("mp-1"), any());
        verify(pago).registrarEstorno(any());
    }

    @Test
    void cancelamentoDepoisDoPrazoNaoEstorna() {
        var evento = eventoPagoSemReembolsoAposPrazo(LocalDateTime.now().minusHours(1));
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        var pago = cobranca(inscricao.getId(), com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
        when(cobrancaEventoRepository.findByInscricaoId(inscricao.getId())).thenReturn(List.of(pago));

        service.cancelar(inscricao.getId(), UUID.randomUUID(), pessoa.getId(), "ACESSO_COMUM", igrejaId);

        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
        verify(pago, never()).registrarEstorno(any());
        verify(pago, never()).marcarComoCancelado();
        org.assertj.core.api.Assertions.assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelamentoDepoisDoPrazoCancelaCobrancaPendente() {
        var evento = eventoPagoSemReembolsoAposPrazo(LocalDateTime.now().minusHours(1));
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        inscricao.setStatus(StatusInscricao.AGUARDANDO_PAGAMENTO);
        var pendente = cobranca(inscricao.getId(), com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        when(cobrancaEventoRepository.findByInscricaoId(inscricao.getId())).thenReturn(List.of(pendente));

        service.cancelar(inscricao.getId(), UUID.randomUUID(), pessoa.getId(), "ACESSO_COMUM", igrejaId);

        verify(pendente).marcarComoCancelado();
        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
        org.assertj.core.api.Assertions.assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void gestorCancelaDepoisDoPrazoTambemNaoEstorna() {
        var evento = eventoPagoSemReembolsoAposPrazo(LocalDateTime.now().minusHours(1));
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        var pago = cobranca(inscricao.getId(), com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
        when(cobrancaEventoRepository.findByInscricaoId(inscricao.getId())).thenReturn(List.of(pago));

        service.cancelar(inscricao.getId(), UUID.randomUUID(), UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
        verify(pago, never()).registrarEstorno(any());
    }

    @Test
    void cancelamentoDepoisDoPrazoComPoliticaDeReembolsoEstornaNormalmente() {
        var evento = eventoPagoComPrazo(LocalDateTime.now().minusHours(1), true); // PERMITIDO_COM_REEMBOLSO
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        var pago = cobranca(inscricao.getId(), com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
        when(cobrancaEventoRepository.findByInscricaoId(inscricao.getId())).thenReturn(List.of(pago));

        service.cancelar(inscricao.getId(), UUID.randomUUID(), pessoa.getId(), "ACESSO_COMUM", igrejaId);

        verify(mercadoPagoClient).estornarParcial(eq(igrejaId), eq("mp-1"), any());
        verify(pago).registrarEstorno(any());
    }

    @Test
    void gestorCancelaDepoisDoPrazoComReembolsoEstorna() {
        var evento = eventoPagoComPrazo(LocalDateTime.now().minusHours(1), true); // PERMITIDO_COM_REEMBOLSO
        var pessoa = pessoaComum();
        var inscricao = inscricaoDe(evento, pessoa);
        var pago = cobranca(inscricao.getId(), com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
        when(cobrancaEventoRepository.findByInscricaoId(inscricao.getId())).thenReturn(List.of(pago));

        service.cancelar(inscricao.getId(), UUID.randomUUID(), UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        verify(mercadoPagoClient).estornarParcial(eq(igrejaId), eq("mp-1"), any());
        verify(pago).registrarEstorno(any());
    }
}
