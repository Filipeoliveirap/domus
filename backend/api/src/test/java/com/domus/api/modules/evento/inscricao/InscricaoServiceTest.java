package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.regras.RegraEstadoCivil;
import com.domus.api.modules.evento.elegibilidade.regras.RegraFaixaEtaria;
import com.domus.api.modules.evento.elegibilidade.regras.RegraSexo;
import com.domus.api.modules.evento.elegibilidade.regras.RegraVinculo;
import com.domus.api.modules.evento.inscricao.DTOs.AcompanhanteRequest;
import com.domus.api.modules.evento.inscricao.DTOs.InscritoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ListaInscritosResponse;
import com.domus.api.modules.evento.inscricao.DTOs.MinhaInscricaoResponse;
import com.domus.api.modules.evento.inscricao.DTOs.ParticipanteResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.shared.exception.ResourceNotFoundException;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import com.domus.api.modules.evento.inscricao.DTOs.RegistranteResumo;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.modules.visitante.Visitante;
import com.domus.api.modules.visitante.VisitanteRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class InscricaoServiceTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    PessoaRepository membroRepository;
    UsuarioRepository usuarioRepository;
    VisitanteRepository visitanteRepository;
    FamiliaIgrejaService familiaIgrejaService;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository campoPersonalizadoRepository;
    com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository respostaCampoPersonalizadoRepository;
    com.domus.api.modules.pagamento.cobranca.CobrancaEventoService cobrancaEventoService;
    com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository cobrancaEventoRepository;
    com.domus.api.modules.pagamento.MercadoPagoClient mercadoPagoClient;
    com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository contaPagamentoIgrejaRepository;
    com.domus.api.shared.email.EmailService emailService;
    InscricaoService service;

    UUID igrejaId = UUID.randomUUID();
    UUID eventoId = UUID.randomUUID();
    UUID pessoaId = UUID.randomUUID();
    UUID usuarioId = UUID.randomUUID();
    UUID inscricaoId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        acompanhanteRepository = mock(AcompanhanteRepository.class);
        membroRepository = mock(PessoaRepository.class);
        usuarioRepository = mock(UsuarioRepository.class);
        visitanteRepository = mock(VisitanteRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        when(familiaIgrejaService.idsDaFamiliaCompleta(any())).thenReturn(Set.of(igrejaId));
        // Real (não mock): as regras precisam rodar de verdade para os testes de elegibilidade
        // fazerem sentido (ex.: exclusivoMembros/Congregante via RegraVinculo).
        ElegibilidadeService elegibilidadeService = new ElegibilidadeService(java.util.List.of(
                new RegraFaixaEtaria(), new RegraVinculo(),
                new RegraEstadoCivil(), new RegraSexo()));
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        campoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository.class);
        respostaCampoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.RespostaCampoPersonalizadoRepository.class);
        cobrancaEventoService = mock(com.domus.api.modules.pagamento.cobranca.CobrancaEventoService.class);
        cobrancaEventoRepository = mock(com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository.class);
        mercadoPagoClient = mock(com.domus.api.modules.pagamento.MercadoPagoClient.class);
        contaPagamentoIgrejaRepository = mock(com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository.class);
        // Por padrão a igreja TEM conta conectada — os testes de evento pago já existentes
        // não são sobre esta regra (Important 9); cada teste que quer provar a recusa
        // sobrescreve com Optional.empty() explicitamente.
        when(contaPagamentoIgrejaRepository.findByIgrejaId(any()))
                .thenReturn(Optional.of(mock(com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja.class)));
        emailService = mock(com.domus.api.shared.email.EmailService.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, membroRepository, usuarioRepository, visitanteRepository,
                elegibilidadeService, familiaIgrejaService, notificacaoService,
                campoPersonalizadoRepository, respostaCampoPersonalizadoRepository,
                cobrancaEventoService, cobrancaEventoRepository, mercadoPagoClient,
                contaPagamentoIgrejaRepository, emailService);
    }

    private Igreja igreja() {
        Igreja i = new Igreja();
        i.setId(igrejaId);
        return i;
    }

    private Evento evento(Integer vagas) {
        // requerInscricao=true por padrão: o foco da maioria destes testes é vaga,
        // elegibilidade e cancelamento, não o toggle em si (que tem teste próprio abaixo).
        return Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Retiro").inicioEm(LocalDateTime.now().plusDays(10))
                .vagas(vagas).requerInscricao(true)
                .build();
    }

    private Pessoa membro(Vinculo vinculo) {
        return Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria")
                .vinculo(vinculo)
                .build();
    }

    private com.domus.api.modules.pagamento.cobranca.CobrancaEvento cobrancaPagaComId(String mpPaymentId) {
        com.domus.api.modules.pagamento.cobranca.CobrancaEvento c =
                new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                        igrejaId, eventoId, inscricaoId, pessoaId, null,
                        new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600),
                        usuarioId, "token-" + UUID.randomUUID());
        c.marcarComoPago(mpPaymentId);
        return c;
    }

    private com.domus.api.modules.pagamento.cobranca.CobrancaEvento cobrancaPendente() {
        return new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId, null,
                new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, "token-" + UUID.randomUUID());
    }

    private Evento eventoDeOutraIgreja(UUID outraIgrejaId, boolean restritoPropriaIgreja) {
        Igreja outraIgreja = new Igreja();
        outraIgreja.setId(outraIgrejaId);
        return Evento.builder()
                .id(eventoId).igreja(outraIgreja)
                .titulo("Retiro Compartilhado").inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true).restritoPropriaIgreja(restritoPropriaIgreja)
                .build();
    }

    private InscricaoEvento inscricaoConfirmada(UUID igrejaOrganizadoraId, UUID daPessoaId) {
        Igreja igrejaOrganizadora = new Igreja();
        igrejaOrganizadora.setId(igrejaOrganizadoraId);
        return InscricaoEvento.builder()
                .id(inscricaoId).igreja(igrejaOrganizadora).evento(evento(10))
                .pessoa(Pessoa.builder().id(daPessoaId).igreja(igreja()).nome("Maria")
                        .vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.CONFIRMADA).build();
    }

    private Pessoa pessoaComIgreja(UUID pessoaUUID, String igrejanome, String sigla) {
        Igreja igrejaCustom = new Igreja();
        igrejaCustom.setId(UUID.randomUUID());
        igrejaCustom.setNome(igrejanome);
        igrejaCustom.setSigla(sigla);
        return Pessoa.builder()
                .id(pessoaUUID).igreja(igrejaCustom).nome("João")
                .vinculo(Vinculo.MEMBRO)
                .build();
    }

    private void dado(Evento e, Pessoa m, long ocupadas) {
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));
        when(membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(m));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId))
                .thenReturn(Optional.empty());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(ocupadas);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void inscreveQuandoHaVaga() {
        dado(evento(10), membro(Vinculo.MEMBRO), 3);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void eventoPagoCriaCobrancaDoTitularComoEuPagoAgora() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(50));
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(cobrancaEventoService.criarParaTitular(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(cobrancaEventoService).criarParaTitular(eq(igrejaId), eq(eventoId), any(),
                eq(pessoaId), eq(java.math.BigDecimal.valueOf(50)), any());
    }

    @Test
    void eventoPagoCriaInscricaoComoAguardandoPagamentoNaoComoConfirmada() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(50));
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(cobrancaEventoService.criarParaTitular(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        ArgumentCaptor<InscricaoEvento> captor = ArgumentCaptor.forClass(InscricaoEvento.class);
        verify(inscricaoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void eventoGratuitoContinuaCriandoInscricaoComoConfirmada() {
        dado(evento(10), membro(Vinculo.MEMBRO), 0);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        ArgumentCaptor<InscricaoEvento> captor = ArgumentCaptor.forClass(InscricaoEvento.class);
        verify(inscricaoRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void minhaInscricaoDevolveCobrancaPendenteQuandoAguardandoPagamento() {
        InscricaoEvento inscricaoAguardando = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId))
                .thenReturn(Optional.of(inscricaoAguardando));
        var cobranca = mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class);
        when(cobranca.getId()).thenReturn(UUID.randomUUID());
        when(cobranca.getPessoaId()).thenReturn(pessoaId);
        when(cobranca.getStatus()).thenReturn(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId)).thenReturn(List.of(cobranca));

        MinhaInscricaoResponse resposta = service.minhaInscricao(eventoId, pessoaId);

        assertThat(resposta.inscrito()).isFalse();
        assertThat(resposta.cobrancaPendenteId()).isNotNull();
    }

    @Test
    void inscreverPessoasDevolveInscricaoIdDeCadaPessoa() {
        Evento evento = evento(10);
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.listarPessoaIdsJaInscritos(any(), any())).thenReturn(List.of());
        // dado() faz save() ecoar o mesmo objeto — sem id, porque @GeneratedValue só
        // atua com Hibernate de verdade. Aqui o id importa (é o que o teste prova), então
        // sobrescreve com um id gerado.
        when(inscricaoRepository.save(any())).thenAnswer(inv -> {
            InscricaoEvento i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });

        var resultado = service.inscreverPessoas(eventoId, List.of(pessoaId), Set.of(),
                null, pessoaId, "ADMIN_IGREJA", false, igrejaId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).inscricaoId()).isNotNull();
        assertThat(resultado.get(0).pessoaId()).isEqualTo(pessoaId);
    }

    @Test
    void eventoPagoSemIgrejaComContaConectadaRecusaInscricaoAntesDeCriarQualquerCoisa() {
        // Important 9 (revisão final de branch): antes desta correção, essa inscrição era
        // criada com sucesso e só falhava depois, na hora de /pagar. Prova que agora falha
        // ANTES, sem criar nem InscricaoEvento nem CobrancaEvento nenhuma.
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(50));
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(contaPagamentoIgrejaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "IGREJA_SEM_CONTA_PAGAMENTO");

        verify(inscricaoRepository, never()).save(any());
        verify(cobrancaEventoService, never())
                .criarParaTitular(any(), any(), any(), any(), any(), any());
    }

    @Test
    void eventoGratuitoNaoCriaCobranca() {
        dado(evento(10), membro(Vinculo.MEMBRO), 0);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(cobrancaEventoService, never())
                .criarParaTitular(any(), any(), any(), any(), any(), any());
    }

    @Test
    void contagemDeVagaEmEventoPagoUsaCobrancaEmVezDeInscricaoDireta() {
        Evento evento = evento(1);
        evento.setPreco(java.math.BigDecimal.valueOf(50));
        // contarPessoasConfirmadas devolveria 0 (sem ocupação) — se o service ainda usasse
        // essa contagem para evento pago, a inscrição passaria; a vaga real está ocupada
        // por uma cobrança PAGA/PENDENTE de outra pessoa, refletida só na cobrança.
        dado(evento, membro(Vinculo.MEMBRO), 0);
        when(cobrancaEventoRepository.contarPessoasComVagaReservada(eq(eventoId), any()))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");

        verify(cobrancaEventoService, never())
                .criarParaTitular(any(), any(), any(), any(), any(), any());
    }

    @Test
    void inscreverNotificaOResponsavelDoEvento() {
        UUID pessoaIdResponsavel = UUID.randomUUID();
        UUID usuarioIdResponsavel = UUID.randomUUID();
        Pessoa responsavel = Pessoa.builder().id(pessoaIdResponsavel).build();
        Evento evento = evento(10);
        evento.setResponsavel(responsavel);
        dado(evento, membro(Vinculo.MEMBRO), 3);
        when(usuarioRepository.findByPessoaId(pessoaIdResponsavel))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioIdResponsavel).build()));

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.INSCRICAO_EVENTO_RESPONSAVEL),
                eq(igrejaId), eq(usuarioIdResponsavel), anyString(), eq("/eventos/" + eventoId + "/inscritos"));
    }

    @Test
    void inscreverNaoNotificaQuandoResponsavelInscreveASiMesmo() {
        Pessoa responsavelQueTambemSeInscreve = membro(Vinculo.MEMBRO);
        Evento evento = evento(10);
        evento.setResponsavel(responsavelQueTambemSeInscreve);
        dado(evento, responsavelQueTambemSeInscreve, 3);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void inscreverNaoNotificaQuandoResponsavelInscreveOutraPessoa() {
        UUID pessoaIdResponsavel = UUID.randomUUID();
        UUID usuarioIdResponsavel = UUID.randomUUID();
        Pessoa responsavel = Pessoa.builder().id(pessoaIdResponsavel).build();
        Evento evento = evento(10);
        evento.setResponsavel(responsavel);
        dado(evento, membro(Vinculo.MEMBRO), 3);
        when(usuarioRepository.findByPessoaId(pessoaIdResponsavel))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioIdResponsavel).build()));

        // O próprio responsável inscreve outra pessoa (não a si mesmo) — inscritoPorOuNull == usuarioIdResponsavel.
        service.inscrever(eventoId, pessoaId, usuarioIdResponsavel, pessoaId, null, false, igrejaId);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void inscreverEmLoteNotificaPendenciaQuandoEventoTemCampoObrigatorio() {
        UUID usuarioIdAdmin = UUID.randomUUID();
        UUID usuarioIdInscrito = UUID.randomUUID();
        dado(evento(10), membro(Vinculo.MEMBRO), 3);
        when(usuarioRepository.findByPessoaId(pessoaId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioIdInscrito).build()));
        var campoObrigatorio = com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).obrigatorio(true)
                .tipo(com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado.TEXTO_CURTO).build();
        when(campoPersonalizadoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(java.util.List.of(campoObrigatorio));

        // Admin (usuarioIdAdmin) inscreve outra pessoa (inscritoPorOuNull != null).
        service.inscrever(eventoId, pessoaId, usuarioIdAdmin, pessoaId, null, false, igrejaId);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.CAMPO_PERSONALIZADO_PENDENTE),
                eq(igrejaId), eq(usuarioIdInscrito), anyString(), anyString());
    }

    @Test
    void autoInscricaoNaoNotificaPendenciaMesmoComCampoObrigatorio() {
        dado(evento(10), membro(Vinculo.MEMBRO), 3);
        var campoObrigatorio = com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).obrigatorio(true)
                .tipo(com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado.TEXTO_CURTO).build();
        when(campoPersonalizadoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(java.util.List.of(campoObrigatorio));

        // Auto-inscrição: inscritoPorOuNull == null — o front já abre o modal na hora.
        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(notificacaoService, never()).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.CAMPO_PERSONALIZADO_PENDENTE),
                any(), any(), anyString(), anyString());
    }

    @Test
    void recusaQuandoVagasEsgotadas() {
        dado(evento(5), membro(Vinculo.MEMBRO), 5);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");
    }

    @Test
    void vagasNulasSignificamSemLimite() {
        dado(evento(null), membro(Vinculo.MEMBRO), 9999);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    // eventoExclusivoDeBatizadosRecusaNaoBatizado removido: exclusivoBatizados/batizado saíram
    // do modelo na Task 3/4 (coluna não existe mais no banco); a checagem correspondente na
    // Task 6 vai tirar o campo do contrato da API.

    @Test
    void eventoExclusivoDeMembrosRecusaCongregante() {
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        dado(e, membro(Vinculo.CONGREGANTE), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void autoInscricaoFuncionaMesmoQuandoEventoNaoRequerInscricao() {
        // B1: requerInscricao passou a significar só "organiza vagas/convidados/terceiros" —
        // a auto-inscrição ("eu vou") funciona em QUALQUER evento, inclusive casual.
        Evento e = evento(10);
        e.setRequerInscricao(false);
        dado(e, membro(Vinculo.MEMBRO), 0);

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    // eventoExclusivoDeMembrosRecusaInativo removido: StatusMembro.INATIVO/VISITANTE eram dois
    // ramos da mesma regra (ver eventoExclusivoDeMembrosRecusaCongregante); Vinculo tem só
    // MEMBRO|CONGREGANTE, então os dois testes colapsam em um só, sem perder cobertura.

    @Test
    void inscreverPessoasRecusaQuandoEventoNaoOrganizaInscricaoDeTerceiros() {
        // Diferente da auto-inscrição: inscrever OUTRA pessoa continua exigindo requerInscricao.
        Evento e = evento(10);
        e.setRequerInscricao(false);
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.inscreverPessoas(
                eventoId, java.util.List.of(pessoaId), usuarioId, UUID.randomUUID(),
                "ADMIN_IGREJA", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não organiza inscrição de outras pessoas");
    }

    @Test
    void inscreverPessoasNomeiaQuantosJaEstavamInscritos() {
        Evento e = evento(10);
        UUID outroMembroId = UUID.randomUUID();
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));
        when(inscricaoRepository.listarPessoaIdsJaInscritos(eventoId,
                java.util.List.of(pessoaId, outroMembroId)))
                .thenReturn(java.util.List.of(pessoaId, outroMembroId));

        assertThatThrownBy(() -> service.inscreverPessoas(
                eventoId, java.util.List.of(pessoaId, outroMembroId), usuarioId, UUID.randomUUID(),
                "ADMIN_IGREJA", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 membros já estão inscritos");
    }

    @Test
    void recusaEventoJaEncerrado() {
        Evento e = evento(10);
        // ENCERRADO: começou e terminou há dias (não basta inicioEm no passado — precisa
        // também passar do fim/fim-do-dia, senão vira EM_ANDAMENTO em vez de ENCERRADO).
        e.setInicioEm(LocalDateTime.now().minusDays(2));
        e.setFimEm(LocalDateTime.now().minusDays(1));
        dado(e, membro(Vinculo.MEMBRO), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aconteceu");
    }

    @Test
    void recusaInscricaoEmEventoEmAndamentoComMensagemPropria() {
        // B3: EM_ANDAMENTO precisa de mensagem DIFERENTE de ENCERRADO — "já começou" é
        // factualmente diferente de "já aconteceu" para quem está vendo o evento rolar agora.
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusHours(1));
        e.setFimEm(LocalDateTime.now().plusHours(1));
        dado(e, membro(Vinculo.MEMBRO), 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já começou")
                .hasMessageNotContaining("já aconteceu");
    }

    @Test
    void inscreverPessoasRecusaQuandoEventoEmAndamento() {
        // B3: a validação de "evento aberto" tem que valer também para inscreverPessoas, não
        // só para a auto-inscrição — checada ANTES do laço, então nem chega a olhar pessoaIds.
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusHours(1));
        e.setFimEm(LocalDateTime.now().plusHours(1));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.inscreverPessoas(
                eventoId, java.util.List.of(pessoaId), usuarioId, UUID.randomUUID(),
                "ADMIN_IGREJA", false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já começou");
    }

    @Test
    void liderDeOutraIgrejaDaFamiliaConsegueInscreverMembroDaPropriaIgrejaEmEventoCompartilhado() {
        UUID outraIgrejaId = UUID.randomUUID();
        Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(outraIgrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, outraIgrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(compartilhado));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, outraIgrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(compartilhado));
        when(membroRepository.findByIdAndIgrejaId(pessoaId, outraIgrejaId))
                .thenReturn(Optional.of(membro(Vinculo.MEMBRO)));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.inscreverPessoas(eventoId, java.util.List.of(pessoaId), usuarioId, UUID.randomUUID(),
                "LIDER", false, outraIgrejaId);

        verify(inscricaoRepository).save(any());
    }

    @Test
    void adicionarAcompanhanteRecusaQuandoEventoEncerrado() {
        // B3: mesma validação também protege a porta dos convidados.
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusDays(2));
        e.setFimEm(LocalDateTime.now().minusDays(1));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("João", null), usuarioId, pessoaId,
                "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aconteceu");
    }

    @Test
    void convidadoDuplicadoPorTelefoneNoMesmoEventoEhRecusado() {
        // B1: mesmo telefone formatado de jeito diferente ("(11) 99999-8888" vs "11999998888")
        // conta como o mesmo convidado — comparação é por dígitos, não pelo texto exato.
        Evento e = evento(10);
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        AcompanhanteInscricao existente = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(minha)
                .nome("Carlos").telefone("(11) 99999-8888").build();
        when(acompanhanteRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of(existente));

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("Carlos Outro Nome", "11999998888"),
                usuarioId, pessoaId, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está inscrito neste evento");
    }

    @Test
    void convidadoDuplicadoPorNomeQuandoNaoHaTelefoneEmNenhumLado() {
        // B1: telefone é opcional; sem ele, cai para nome normalizado (sem acento, sem case).
        Evento e = evento(10);
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        AcompanhanteInscricao existente = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(minha)
                .nome("José da Silva").telefone(null).build();
        when(acompanhanteRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of(existente));

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("  jose   DA silva ", null),
                usuarioId, pessoaId, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está inscrito neste evento");
    }

    @Test
    void convidadoComMesmoNomeMasTelefoneDiferenteNaoEhBloqueado() {
        // Confirma que não virou bloqueio global por nome: telefones diferentes e
        // informados nos dois lados vencem a comparação — nomes iguais não bastam sozinhos.
        Evento e = evento(10);
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        AcompanhanteInscricao existente = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(minha)
                .nome("João").telefone("11911112222").build();
        when(acompanhanteRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of(existente));
        when(acompanhanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId))).thenReturn(Optional.of(e));

        service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("João", "11933334444"), usuarioId, pessoaId,
                "ACESSO_COMUM", igrejaId);

        verify(acompanhanteRepository).save(any(AcompanhanteInscricao.class));
    }

    @Test
    void recusaInscricaoDuplicada() {
        dado(evento(10), membro(Vinculo.MEMBRO), 0);
        InscricaoEvento existente = InscricaoEvento.builder()
                .id(UUID.randomUUID()).status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId))
                .thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está inscrit");
    }

    @Test
    void reinscricaoReaproveitaLinhaCancelada() {
        dado(evento(10), membro(Vinculo.MEMBRO), 0);
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .id(UUID.randomUUID()).status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId))
                .thenReturn(Optional.of(cancelada));

        service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId);

        assertThat(cancelada.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(inscricaoRepository).save(cancelada);
    }

    @Test
    void convidadoRecusadoSeEventoDeixouDeAceitarInscricao() {
        // Cenário real: a inscrição nasceu quando o evento aceitava, e o admin desligou o
        // toggle depois. Convidado ocupa vaga igual, então não pode entrar pela porta dos fundos.
        Evento e = evento(10);
        e.setRequerInscricao(false);
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("João", null), usuarioId, pessoaId,
                "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não permite convidados");
    }

    @Test
    void acompanhanteOcupaVaga() {
        Evento e = evento(2);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId))).thenReturn(Optional.of(e));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e).pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(2L);

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                minha.getId(), new AcompanhanteRequest("João", null), usuarioId, pessoaId,
                "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");
    }

    @Test
    void adicionarAcompanhanteEmEventoPagoCriaCobrancaParaTerceiroPagandoAgora() {
        Evento e = evento(10);
        e.setPreco(java.math.BigDecimal.valueOf(50));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId))).thenReturn(Optional.of(e));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e).pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(acompanhanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cobrancaEventoService.criarParaTerceiro(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        service.adicionarAcompanhante(minha.getId(), new AcompanhanteRequest("João", null, false),
                usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(cobrancaEventoService).criarParaTerceiro(eq(igrejaId), eq(eventoId), eq(minha.getId()),
                isNull(), any(), eq(java.math.BigDecimal.valueOf(50)), eq(usuarioId), eq(false));
    }

    @Test
    void adicionarAcompanhanteComEscolhaDeLinkGeraCobrancaComLink() {
        Evento e = evento(10);
        e.setPreco(java.math.BigDecimal.valueOf(50));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId))).thenReturn(Optional.of(e));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e).pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(acompanhanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cobrancaEventoService.criarParaTerceiro(any(), any(), any(), any(), any(), any(), any(), anyBoolean()))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        service.adicionarAcompanhante(minha.getId(), new AcompanhanteRequest("João", null, true),
                usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(cobrancaEventoService).criarParaTerceiro(eq(igrejaId), eq(eventoId), eq(minha.getId()),
                isNull(), any(), eq(java.math.BigDecimal.valueOf(50)), eq(usuarioId), eq(true));
    }

    @Test
    void adicionarAcompanhanteEmEventoGratuitoNaoCriaCobranca() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha.getEvento()));
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(acompanhanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adicionarAcompanhante(minha.getId(), new AcompanhanteRequest("João", null),
                usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(cobrancaEventoService, never())
                .criarParaTerceiro(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void donoDaInscricaoPodeRemoverSeuAcompanhante() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(minha).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        service.removerAcompanhante(acompanhante.getId(), pessoaId, "ACESSO_COMUM", igrejaId);

        verify(acompanhanteRepository).delete(acompanhante);
    }

    @Test
    void terceiroNaoPodeRemoverAcompanhanteDeInscricaoAlheia() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(usuarioId)
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(outra).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        UUID membroDoTerceiro = UUID.randomUUID();

        assertThatThrownBy(() -> service.removerAcompanhante(
                acompanhante.getId(), membroDoTerceiro, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class);
        verify(acompanhanteRepository, never()).delete(any());
    }

    @Test
    void regressaoFuroDeAutoInscricaoNaoLiberaTerceiroARemoverAcompanhante() {
        // Bug real de um rascunho anterior: comparar com inscritoPorUsuarioId e tratar
        // NULL como "sou eu" liberava geral, pois toda auto-inscrição tem esse campo NULL.
        // Este teste trava especificamente esse caso.
        InscricaoEvento autoInscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(null) // auto-inscrição: campo NULL, como na maioria dos casos
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(autoInscricao).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        UUID membroDeOutraPessoa = UUID.randomUUID();

        assertThatThrownBy(() -> service.removerAcompanhante(
                acompanhante.getId(), membroDeOutraPessoa, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class);
        verify(acompanhanteRepository, never()).delete(any());
    }

    @Test
    void adminPodeRemoverAcompanhanteDeInscricaoDeQualquerUm() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(outra).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        service.removerAcompanhante(acompanhante.getId(), UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        verify(acompanhanteRepository).delete(acompanhante);
    }

    @Test
    void acompanhanteDeInscricaoDeOutraIgrejaEhTratadoComoNaoEncontrado() {
        Igreja outraIgreja = new Igreja();
        outraIgreja.setId(UUID.randomUUID());
        InscricaoEvento inscricaoDeOutraIgreja = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(outraIgreja).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(inscricaoDeOutraIgreja).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() -> service.removerAcompanhante(
                acompanhante.getId(), pessoaId, "ADMIN_IGREJA", igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
        verify(acompanhanteRepository, never()).delete(any());
    }

    @Test
    void cancelar_quemInscreveuOutraPessoaNaoPodeCancelarPorEla() {
        // Este teste é sobre CANCELAR (não sobre removerAcompanhante): ter sido quem
        // inscreveu (inscritoPorUsuarioId) não dá direito de cancelar a inscrição de outra pessoa.
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(usuarioId)      // fui EU quem inscrevi
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(outra.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(outra));

        // sou MEMBRO, o membro da inscrição não sou eu
        assertThatThrownBy(() -> service.cancelar(
                outra.getId(), usuarioId, UUID.randomUUID(), "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode cancelar");
    }

    @Test
    void oProprioInscritoPodeCancelar() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));

        service.cancelar(minha.getId(), usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelarLevaOsConvidadosJunto() {
        // Decisão de produto: o convidado NÃO volta numa reinscrição. Quem cancelou porque
        // o convidado desistiu não pode vê-lo reaparecer sozinho, ocupando vaga de novo.
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        minha.getAcompanhantes().add(
                AcompanhanteInscricao.builder().id(UUID.randomUUID())
                        .inscricao(minha).nome("Convidado").build());
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));

        service.cancelar(minha.getId(), usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        assertThat(minha.getAcompanhantes()).isEmpty();
    }

    @Test
    void cancelarApagaAsRespostasDeCamposPersonalizados() {
        // A linha de inscrição é reaproveitada numa reinscrição (UNIQUE evento+pessoa) — sem
        // apagar a resposta velha, reinscrever pareceria "já respondido" sem a pessoa ter
        // respondido nada desta vez.
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));

        service.cancelar(minha.getId(), usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(respostaCampoPersonalizadoRepository).deleteByInscricaoId(minha.getId());
    }

    @Test
    void cancelarInscricaoComCobrancaPagaAcionaEstorno() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(mercadoPagoClient).estornar(igrejaId, "mp-payment-1");
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelamentoComReembolsoEnviaEmailAvisandoQueSeraReembolsado() {
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        var assuntoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), assuntoCaptor.capture(), corpoCaptor.capture());
        assertThat(assuntoCaptor.getValue()).contains("cancelada");
        assertThat(corpoCaptor.getValue()).contains("reembolsado");
    }

    @Test
    void cancelamentoDeCobrancaPendenteNaoEnviaEmailPorqueNaoHouveCobranca() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPendente()));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(emailService, never()).enviar(any(), any(), any());
    }

    @Test
    void cancelarInscricaoComCobrancaPendenteNaoAcionaEstorno() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPendente()));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(mercadoPagoClient, never()).estornar(any(), any());
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void falhaNoEstornoNaoDeixaInscricaoComoCancelada() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));
        doThrow(new IllegalStateException("Mercado Pago fora do ar"))
                .when(mercadoPagoClient).estornar(any(), any());

        assertThatThrownBy(() -> service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FALHA_ESTORNO");

        verify(inscricaoRepository, never()).save(argThat(i -> i.getStatus() == StatusInscricao.CANCELADA));
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void falhaNoEstornoDeUmaCobrancaPagaNaoCancelaOutraCobrancaPendenteDaMesmaInscricao() {
        // Important 7 (revisão final de branch): antes da correção, a cobrança PENDENTE era
        // marcada CANCELADO na 1ª passada do loop, e só depois o loop chegava na cobrança
        // PAGA cujo estorno falha — mutação parcial que, em cancelamento em lote, seria
        // persistida mesmo a inscrição continuando CONFIRMADA. Prova aqui: depois da falha,
        // a cobrança PENDENTE continua PENDENTE (nenhuma mutação de status aconteceu).
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        var cobrancaPendente = cobrancaPendente();
        var cobrancaPaga = cobrancaPagaComId("mp-payment-falha");
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPendente, cobrancaPaga));
        doThrow(new IllegalStateException("Mercado Pago fora do ar"))
                .when(mercadoPagoClient).estornar(any(), eq("mp-payment-falha"));

        assertThatThrownBy(() -> service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FALHA_ESTORNO");

        assertThat(cobrancaPendente.getStatus())
                .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE);
        assertThat(cobrancaPaga.getStatus())
                .isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO);
        verify(cobrancaEventoRepository, never()).saveAll(any());
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void adminPodeCancelarInscricaoDeQualquerUm() {
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(outra.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(outra));

        service.cancelar(outra.getId(), usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        assertThat(outra.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelarInscricoesEmEventosAbertosPorPessoaContinuaProcessandoAposFalhaDeEstornoDeUmaInscricao() {
        Evento agendado1 = evento(10);
        Evento agendado2 = evento(10);
        InscricaoEvento comCobrancaPaga = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(agendado1)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento semCobranca = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(agendado2)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByPessoaIdAndStatus(pessoaId, StatusInscricao.CONFIRMADA))
                .thenReturn(java.util.List.of(comCobrancaPaga, semCobranca));
        when(cobrancaEventoRepository.findByInscricaoId(comCobrancaPaga.getId()))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-falha-3")));
        when(cobrancaEventoRepository.findByInscricaoId(semCobranca.getId()))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("Mercado Pago fora do ar"))
                .when(mercadoPagoClient).estornar(any(), eq("mp-payment-falha-3"));

        service.cancelarInscricoesEmEventosAbertosPorPessoa(pessoaId);

        assertThat(comCobrancaPaga.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(semCobranca.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void listaTrazTotalDePessoasEVagasRestantes() {
        Evento e = evento(10);
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(inscricaoRepository.listarIdsPaginadoPorEvento(eq(eventoId), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        when(inscricaoRepository.listarComDetalhesPorIds(any())).thenReturn(java.util.List.of());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(4L);

        ListaInscritosResponse r = service.listarInscritos(eventoId, igrejaId, null, org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(r.totalPessoas()).isEqualTo(4);
        assertThat(r.vagas()).isEqualTo(10);
        assertThat(r.vagasRestantes()).isEqualTo(6);
    }

    @Test
    void vagasRestantesEhNuloQuandoNaoHaLimite() {
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId))
                .thenReturn(Optional.of(evento(null)));
        when(inscricaoRepository.listarIdsPaginadoPorEvento(eq(eventoId), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));
        when(inscricaoRepository.listarComDetalhesPorIds(any())).thenReturn(java.util.List.of());
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(50L);

        assertThat(service.listarInscritos(eventoId, igrejaId, null, org.springframework.data.domain.PageRequest.of(0, 20)).vagasRestantes()).isNull();
    }

    @Test
    void listaTrazNomeDeQuemInscreveuQuandoNaoFoiAutoInscricao() {
        Evento e = evento(10);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(usuarioId)
                .status(StatusInscricao.CONFIRMADA).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(inscricaoRepository.listarIdsPaginadoPorEvento(eq(eventoId), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(inscricao.getId())));
        when(inscricaoRepository.listarComDetalhesPorIds(any())).thenReturn(java.util.List.of(inscricao));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);
        when(usuarioRepository.buscarRegistrantes(java.util.List.of(usuarioId)))
                .thenReturn(java.util.List.of(new RegistranteResumo(usuarioId, "João Líder", null)));

        var inscritos = service.listarInscritos(eventoId, igrejaId, null, org.springframework.data.domain.PageRequest.of(0, 20)).inscritos().getContent();

        assertThat(inscritos).hasSize(1);
        assertThat(inscritos.get(0).inscritoPorUsuarioId()).isEqualTo(usuarioId);
        assertThat(inscritos.get(0).inscritoPorNome()).isEqualTo("João Líder");
        assertThat(inscritos.get(0).inscritoPorFotoId()).isNull();
    }

    @Test
    void listaNaoTrazNomeDeQuemInscreveuQuandoFoiAutoInscricao() {
        Evento e = evento(10);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(null) // auto-inscrição
                .status(StatusInscricao.CONFIRMADA).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(inscricaoRepository.listarIdsPaginadoPorEvento(eq(eventoId), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(inscricao.getId())));
        when(inscricaoRepository.listarComDetalhesPorIds(any())).thenReturn(java.util.List.of(inscricao));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        var inscritos = service.listarInscritos(eventoId, igrejaId, null, org.springframework.data.domain.PageRequest.of(0, 20)).inscritos().getContent();

        assertThat(inscritos.get(0).inscritoPorUsuarioId()).isNull();
        assertThat(inscritos.get(0).inscritoPorNome()).isNull();
        // auto-inscrição nem tem id pra resolver: a query em lote não deve nem ser chamada.
        verify(usuarioRepository, never()).buscarRegistrantes(any());
    }

    @Test
    void listaTrataRegistranteArquivadoSemQuebrarALinha() {
        // A conta (ou o membro por trás dela) de quem inscreveu foi arquivada depois: o
        // @SQLRestriction do Usuario/Pessoa faz a busca em lote não trazer esse id de volta.
        // A inscrição continua aparecendo, só sem o nome de quem a fez.
        Evento e = evento(10);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(usuarioId)
                .status(StatusInscricao.CONFIRMADA).build();
        when(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(eventoId, igrejaId)).thenReturn(Optional.of(e));
        when(inscricaoRepository.listarIdsPaginadoPorEvento(eq(eventoId), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(inscricao.getId())));
        when(inscricaoRepository.listarComDetalhesPorIds(any())).thenReturn(java.util.List.of(inscricao));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);
        when(usuarioRepository.buscarRegistrantes(java.util.List.of(usuarioId)))
                .thenReturn(java.util.List.of()); // arquivado: não volta na busca

        var inscritos = service.listarInscritos(eventoId, igrejaId, null, org.springframework.data.domain.PageRequest.of(0, 20)).inscritos().getContent();

        assertThat(inscritos).hasSize(1);
        assertThat(inscritos.get(0).inscritoPorUsuarioId()).isEqualTo(usuarioId);
        assertThat(inscritos.get(0).inscritoPorNome()).isNull();
        assertThat(inscritos.get(0).inscritoPorFotoId()).isNull();
    }

    @Test
    void listarParticipantesTrazFormaReduzidaSemDadosAdministrativos() {
        Evento e = evento(10);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .inscritoPorUsuarioId(usuarioId)
                .status(StatusInscricao.CONFIRMADA).build();
        inscricao.getAcompanhantes().add(
                com.domus.api.modules.evento.inscricao.AcompanhanteInscricao.builder()
                        .id(UUID.randomUUID()).inscricao(inscricao)
                        .nome("Convidado").telefone("11999998888").build());
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of(inscricao));

        var participantes = service.listarParticipantes(eventoId, igrejaId);

        assertThat(participantes).hasSize(1);
        var p = participantes.get(0);
        assertThat(p.nome()).isEqualTo("Maria");
        assertThat(p.convidados()).containsExactly("Convidado");
        // reduzido de propósito: sem telefone, sem "quem inscreveu", sem data — o record
        // ParticipanteResponse nem tem esses campos, então não há como vazá-los por acidente.
    }

    @Test
    void listarParticipantesSoTrazConfirmadas() {
        // listarPorEvento já filtra CONFIRMADA na query; este teste garante que o service
        // não reintroduz canceladas ao montar a resposta.
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento(10)));
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of());

        assertThat(service.listarParticipantes(eventoId, igrejaId)).isEmpty();
    }

    @Test
    void removerInscritosNaoElegiveisNaoCancelaNinguemQuandoTodosSaoElegiveis() {
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of());

        int removidos = service.removerInscritosNaoElegiveis(eventoId);

        assertThat(removidos).isEqualTo(0);
    }

    @Test
    void removerInscritosNaoElegiveisCancelaCongreganteQuandoExclusivoMembros() {
        // Task 6: o método agora lê a configuração ATUAL do evento (via inscricao.getEvento()),
        // não recebe mais o booleano como parâmetro — só roda com escolha explícita do admin.
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        InscricaoEvento visitante = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento ativo = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.listarPorEvento(eventoId))
                .thenReturn(java.util.List.of(visitante, ativo));

        int removidos = service.removerInscritosNaoElegiveis(eventoId);

        assertThat(removidos).isEqualTo(1);
        assertThat(visitante.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        assertThat(ativo.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void removerInscritosNaoElegiveisPreservaAExcecaoDeliberada() {
        // Task 6, achado da revisão da Task 4: o motorista CONGREGANTE inscrito de propósito
        // ("inscrever mesmo assim") não pode ser cancelado por uma edição futura do evento —
        // a marca inscritoPorExcecao=true é justamente o registro dessa decisão.
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        InscricaoEvento motorista = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA)
                .inscritoPorExcecao(true)
                .build();
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of(motorista));

        int removidos = service.removerInscritosNaoElegiveis(eventoId);

        assertThat(removidos).isEqualTo(0);
        assertThat(motorista.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void removerInscritosNaoElegiveisContinuaProcessandoAposFalhaDeEstornoDeUmaPessoa() {
        // Decisão do autor (fix round 1): falha de estorno de UMA pessoa no lote não pode
        // travar as demais — cada item é processado isoladamente.
        Evento e = evento(10);
        e.setExclusivoMembros(true);
        InscricaoEvento congreganteComCobrancaPaga = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento congreganteSemCobranca = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.listarPorEvento(eventoId))
                .thenReturn(java.util.List.of(congreganteComCobrancaPaga, congreganteSemCobranca));
        when(cobrancaEventoRepository.findByInscricaoId(congreganteComCobrancaPaga.getId()))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-falha")));
        when(cobrancaEventoRepository.findByInscricaoId(congreganteSemCobranca.getId()))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("Mercado Pago fora do ar"))
                .when(mercadoPagoClient).estornar(any(), eq("mp-payment-falha"));

        int removidos = service.removerInscritosNaoElegiveis(eventoId);

        assertThat(removidos).isEqualTo(1);
        assertThat(congreganteComCobrancaPaga.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(congreganteSemCobranca.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelarInscricoesEmEventosExclusivosCancelaSoOsExclusivosDaPessoa() {
        // A pessoa deixou de ser MEMBRO: deve perder a vaga no evento exclusivo, mas
        // manter a inscrição num evento comum (que a query já filtra fora).
        Evento exclusivo = evento(10);
        exclusivo.setExclusivoMembros(true);
        InscricaoEvento inscricaoExclusiva = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(exclusivo)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao convidado = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(inscricaoExclusiva).nome("Convidado").build();
        inscricaoExclusiva.getAcompanhantes().add(convidado);

        when(inscricaoRepository.findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
                pessoaId, StatusInscricao.CONFIRMADA))
                .thenReturn(java.util.List.of(inscricaoExclusiva));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int canceladas = service.cancelarInscricoesEmEventosExclusivos(pessoaId);

        assertThat(canceladas).isEqualTo(1);
        assertThat(inscricaoExclusiva.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        // convidado vai junto — mesma regra de cancelarInterno reusada aqui.
        assertThat(inscricaoExclusiva.getAcompanhantes()).isEmpty();
    }

    @Test
    void cancelarInscricoesEmEventosExclusivosNaoCancelaNadaQuandoNaoHaInscricaoExclusiva() {
        when(inscricaoRepository.findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
                pessoaId, StatusInscricao.CONFIRMADA))
                .thenReturn(java.util.List.of());

        int canceladas = service.cancelarInscricoesEmEventosExclusivos(pessoaId);

        assertThat(canceladas).isEqualTo(0);
        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void cancelarInscricoesEmEventosExclusivosContinuaProcessandoAposFalhaDeEstornoDeUmaInscricao() {
        Evento exclusivo1 = evento(10);
        exclusivo1.setExclusivoMembros(true);
        Evento exclusivo2 = evento(10);
        exclusivo2.setExclusivoMembros(true);
        InscricaoEvento comCobrancaPaga = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(exclusivo1)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento semCobranca = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(exclusivo2)
                .pessoa(membro(Vinculo.CONGREGANTE))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
                pessoaId, StatusInscricao.CONFIRMADA))
                .thenReturn(java.util.List.of(comCobrancaPaga, semCobranca));
        when(cobrancaEventoRepository.findByInscricaoId(comCobrancaPaga.getId()))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-falha-2")));
        when(cobrancaEventoRepository.findByInscricaoId(semCobranca.getId()))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("Mercado Pago fora do ar"))
                .when(mercadoPagoClient).estornar(any(), eq("mp-payment-falha-2"));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int canceladas = service.cancelarInscricoesEmEventosExclusivos(pessoaId);

        assertThat(canceladas).isEqualTo(1);
        assertThat(comCobrancaPaga.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(semCobranca.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelarRecusaQuandoEventoEncerrado() {
        // A2: cancelar reescreveria histórico de presença de um evento que já aconteceu.
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusDays(2));
        e.setFimEm(LocalDateTime.now().minusDays(1));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(minha.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));

        assertThatThrownBy(() -> service.cancelar(minha.getId(), usuarioId, pessoaId, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aconteceu");
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void cancelarRecusaMesmoParaAdminQuandoEventoEmAndamento() {
        // A2: a trava vale para TODO MUNDO, admin incluso — ver Javadoc de cancelar().
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusHours(1));
        e.setFimEm(LocalDateTime.now().plusHours(1));
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(outra.getId(), Set.of(igrejaId)))
                .thenReturn(Optional.of(outra));

        assertThatThrownBy(() -> service.cancelar(
                outra.getId(), usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já começou");
        assertThat(outra.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    @Test
    void removerAcompanhanteRecusaQuandoEventoEncerrado() {
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusDays(2));
        e.setFimEm(LocalDateTime.now().minusDays(1));
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(minha).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() -> service.removerAcompanhante(acompanhante.getId(), pessoaId, "ACESSO_COMUM", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já aconteceu");
        verify(acompanhanteRepository, never()).delete(any());
    }

    @Test
    void removerAcompanhanteRecusaMesmoParaAdminQuandoEventoEmAndamento() {
        Evento e = evento(10);
        e.setInicioEm(LocalDateTime.now().minusHours(1));
        e.setFimEm(LocalDateTime.now().plusHours(1));
        InscricaoEvento outra = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(outra).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));

        assertThatThrownBy(() -> service.removerAcompanhante(
                acompanhante.getId(), UUID.randomUUID(), "ADMIN_IGREJA", igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já começou");
        verify(acompanhanteRepository, never()).delete(any());
    }

    @Test
    void listarParticipantesDeEventoDeOutraIgrejaEh404() {
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listarParticipantes(eventoId, igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.ResourceNotFoundException.class);
    }

    @Test
    void listarParticipantesFuncionaParaEventoCompartilhadoDeOutraIgreja() {
        UUID outraIgrejaId = UUID.randomUUID();
        Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(compartilhado));
        when(inscricaoRepository.listarPorEvento(eventoId)).thenReturn(java.util.List.of());

        var resposta = service.listarParticipantes(eventoId, igrejaId);

        assertThat(resposta).isEmpty();
    }

    @Test
    void pessoaDeOutraIgrejaDaFamiliaConseguSeInscreverEmEventoCompartilhado() {
        UUID outraIgrejaId = UUID.randomUUID();
        Evento compartilhado = eventoDeOutraIgreja(outraIgrejaId, false);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(compartilhado));
        when(membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(membro(Vinculo.MEMBRO)));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MinhaInscricaoResponse response = service.inscrever(
                eventoId, pessoaId, null, pessoaId, "ACESSO_COMUM", false, igrejaId);

        assertThat(response).isNotNull();
        verify(inscricaoRepository).save(any());
    }

    @Test
    void pessoaDeIgrejaForaDaFamiliaNaoConseguSeInscrever() {
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inscrever(
                eventoId, pessoaId, null, pessoaId, "ACESSO_COMUM", false, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void cancelarPropriaInscricaoFuncionaMesmoEmEventoDeOutraIgrejaDaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        InscricaoEvento inscricao = inscricaoConfirmada(outraIgrejaId, pessoaId);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricao));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(inscricaoRepository).save(argThat(i -> i.getStatus() == StatusInscricao.CANCELADA));
    }

    @Test
    void gestorDeOutraIgrejaDaFamiliaNaoPodeCancelarInscricaoDeTerceiro() {
        UUID outraIgrejaId = UUID.randomUUID();
        InscricaoEvento inscricao = inscricaoConfirmada(igrejaId, UUID.randomUUID());
        when(familiaIgrejaService.idsDaFamiliaCompleta(outraIgrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.cancelar(
                inscricaoId, usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", outraIgrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não pode cancelar");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void gestorDaMesmaIgrejaAindaPodeCancelarInscricaoDeTerceiroEmEventoCompartilhado() {
        UUID outraIgrejaId = UUID.randomUUID();
        InscricaoEvento inscricao = inscricaoConfirmada(igrejaId, UUID.randomUUID());
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricao));

        service.cancelar(inscricaoId, usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        assertThat(inscricao.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void gestorDeOutraIgrejaDaFamiliaNaoPodeRemoverAcompanhanteDeTerceiro() {
        UUID outraIgrejaId = UUID.randomUUID();
        InscricaoEvento inscricaoDeTerceiro = inscricaoConfirmada(igrejaId, UUID.randomUUID());
        AcompanhanteInscricao acompanhante = AcompanhanteInscricao.builder()
                .id(UUID.randomUUID()).inscricao(inscricaoDeTerceiro).nome("João").build();
        when(acompanhanteRepository.findById(acompanhante.getId()))
                .thenReturn(Optional.of(acompanhante));
        when(familiaIgrejaService.idsDaFamiliaCompleta(outraIgrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));

        assertThatThrownBy(() -> service.removerAcompanhante(
                acompanhante.getId(), UUID.randomUUID(), "LIDER", outraIgrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("própria inscrição");

        verify(acompanhanteRepository, never()).delete(any());
    }

    @Test
    void inscritoResponseTrazIgrejaDaPessoa() {
        Pessoa pessoaDeOutraIgreja = pessoaComIgreja(UUID.randomUUID(), "Congregação Norte", "CN");
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).pessoa(pessoaDeOutraIgreja)
                .acompanhantes(new ArrayList<>()).createdAt(java.time.LocalDateTime.now())
                .build();

        InscritoResponse response = InscritoResponse.from(inscricao, pessoaDeOutraIgreja, null, null);

        assertThat(response.igrejaDaPessoa().nome()).isEqualTo("Congregação Norte");
    }

    @Test
    void participanteResponseTrazIgrejaDaPessoa() {
        Pessoa pessoaDeOutraIgreja = pessoaComIgreja(UUID.randomUUID(), "Congregação Sul", "CS");
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).pessoa(pessoaDeOutraIgreja)
                .acompanhantes(new ArrayList<>()).createdAt(java.time.LocalDateTime.now())
                .build();

        ParticipanteResponse response = ParticipanteResponse.from(inscricao, pessoaDeOutraIgreja, null);

        assertThat(response.igrejaDaPessoa().nome()).isEqualTo("Congregação Sul");
    }

    @Test
    void donoDaInscricaoConsegueAdicionarAcompanhanteMesmoEmEventoDeOutraIgrejaDaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        InscricaoEvento inscricao = inscricaoConfirmada(outraIgrejaId, pessoaId);
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricao));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricao.getEvento()));
        when(acompanhanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adicionarAcompanhante(inscricaoId, new AcompanhanteRequest("João", null),
                usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(acompanhanteRepository).save(any(AcompanhanteInscricao.class));
    }

    @Test
    void gestorDeOutraIgrejaDaFamiliaNaoPodeAdicionarAcompanhanteDeTerceiro() {
        UUID outraIgrejaId = UUID.randomUUID();
        InscricaoEvento inscricaoDeTerceiro = inscricaoConfirmada(igrejaId, UUID.randomUUID());
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(inscricaoDeTerceiro));
        when(familiaIgrejaService.idsDaFamiliaCompleta(outraIgrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));

        assertThatThrownBy(() -> service.adicionarAcompanhante(
                inscricaoId, new AcompanhanteRequest("João", null), usuarioId, UUID.randomUUID(),
                "LIDER", outraIgrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("própria inscrição");

        verify(acompanhanteRepository, never()).save(any());
    }

    @Test
    void gestorDaMesmaIgrejaAindaPodeAdicionarAcompanhanteDeTerceiro() {
        InscricaoEvento inscricaoDeTerceiro = inscricaoConfirmada(igrejaId, UUID.randomUUID());
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(inscricaoDeTerceiro));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(inscricaoDeTerceiro.getEvento()));
        when(acompanhanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.adicionarAcompanhante(inscricaoId, new AcompanhanteRequest("João", null),
                usuarioId, UUID.randomUUID(), "ADMIN_IGREJA", igrejaId);

        verify(acompanhanteRepository).save(any(AcompanhanteInscricao.class));
    }

    @Test
    void gestorDeOutraIgrejaDaFamiliaNaoContornaElegibilidadeAoSeAutoInscrever() {
        UUID outraIgrejaId = UUID.randomUUID();
        Igreja sedeA = new Igreja();
        sedeA.setId(igrejaId);
        Evento eventoDaSede = Evento.builder()
                .id(eventoId).igreja(sedeA)
                .titulo("Retiro de Jovens").inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true).idadeMin(12).idadeMax(17)
                .build();
        Pessoa gestorAdulto = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Pastor da Congregação B")
                .vinculo(Vinculo.MEMBRO)
                .dataNascimento(LocalDateTime.now().minusYears(40).toLocalDate())
                .build();
        when(familiaIgrejaService.idsDaFamiliaCompleta(outraIgrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, outraIgrejaId, Set.of(igrejaId, outraIgrejaId)))
                .thenReturn(Optional.of(eventoDaSede));
        when(membroRepository.findByIdAndIgrejaId(pessoaId, outraIgrejaId)).thenReturn(Optional.of(gestorAdulto));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inscrever(
                eventoId, pessoaId, null, pessoaId, "ADMIN_IGREJA", true, outraIgrejaId))
                .isInstanceOf(com.domus.api.modules.evento.elegibilidade.NaoElegivelException.class);

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void gestorDaMesmaIgrejaAindaContornaElegibilidadeAoSeAutoInscrever() {
        Evento eventoRestrito = Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Retiro de Jovens").inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true).idadeMin(12).idadeMax(17)
                .build();
        Pessoa gestorAdulto = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Líder")
                .vinculo(Vinculo.MEMBRO)
                .dataNascimento(LocalDateTime.now().minusYears(40).toLocalDate())
                .build();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(eventoRestrito));
        when(membroRepository.findByIdAndIgrejaId(pessoaId, igrejaId)).thenReturn(Optional.of(gestorAdulto));
        when(inscricaoRepository.findByEventoIdAndPessoaId(eventoId, pessoaId)).thenReturn(Optional.empty());
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.inscrever(eventoId, pessoaId, null, pessoaId, "ADMIN_IGREJA", true, igrejaId);

        verify(inscricaoRepository).save(any(InscricaoEvento.class));
    }

    @Test
    void inscreverConvidadoCriaInscricaoComPessoaNulaENomeConvidadoPreenchido() {
        UUID convidadoPorId = UUID.randomUUID();
        Pessoa convidante = Pessoa.builder().id(convidadoPorId).build();
        Evento evento = evento(null); // sem limite de vagas
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(membroRepository.findByIdAndIgrejaId(convidadoPorId, igrejaId)).thenReturn(Optional.of(convidante));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria de Fora",
                "11999998888", null, convidadoPorId, null, null, false).inscricao();

        assertThat(salva.getPessoa()).isNull();
        assertThat(salva.getNomeConvidado()).isEqualTo("Maria de Fora");
        assertThat(salva.getTelefoneConvidado()).isEqualTo("11999998888");
        assertThat(salva.isConvidadoSemCadastro()).isTrue();
        verify(inscricaoRepository).save(any());
    }

    @Test
    void inscreverConvidadoEmEventoPagoCriaComoAguardandoPagamento() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(80));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> {
            InscricaoEvento i = inv.getArgument(0);
            if (i.getId() == null) i.setId(UUID.randomUUID());
            return i;
        });
        when(cobrancaEventoService.criarParaTerceiro(eq(igrejaId), eq(eventoId), any(),
                isNull(), isNull(), eq(java.math.BigDecimal.valueOf(80)), any(), eq(false)))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        var resultado = service.inscreverConvidado(eventoId, igrejaId, "Fulano", "11999999999",
                "fulano@teste.com", null, usuarioId, null, false);

        assertThat(resultado.inscricao().getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        verify(cobrancaEventoService).criarParaTerceiro(eq(igrejaId), eq(eventoId), any(),
                isNull(), isNull(), eq(java.math.BigDecimal.valueOf(80)), any(), eq(false));
    }

    @Test
    void inscreverConvidadoEmEventoPagoSemEmailRecusaAntesDeCriarQualquerCoisa() {
        // E-mail é a única forma de mandar o comprovante de pagamento pra quem não tem
        // cadastro — sem ele, evento pago recusa antes de criar inscrição ou cobrança.
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(80));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Fulano",
                "11999999999", null, null, usuarioId, null, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "EMAIL_OBRIGATORIO");

        verify(inscricaoRepository, never()).save(any());
        verify(cobrancaEventoService, never())
                .criarParaTerceiro(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void inscreverConvidadoEmEventoPagoSemContaConectadaRecusaAntesDeCriarQualquerCoisa() {
        Evento evento = evento(10);
        evento.setPreco(java.math.BigDecimal.valueOf(80));
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(contaPagamentoIgrejaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Fulano",
                "11999999999", null, null, usuarioId, null, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "IGREJA_SEM_CONTA_PAGAMENTO");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoEmEventoGratuitoContinuaCriandoComoConfirmadaSemCobranca() {
        Evento evento = evento(10);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.inscreverConvidado(eventoId, igrejaId, "Fulano", "11999999999",
                null, null, usuarioId, null, false);

        assertThat(resultado.inscricao().getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(resultado.cobranca()).isNull();
        verify(cobrancaEventoService, never())
                .criarParaTerceiro(any(), any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void inscreverConvidadoRecusaQuandoVagasEsgotadas() {
        Evento evento = evento(1);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, null, null, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("esgotadas");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoRecusaQuandoEventoExclusivoMembros() {
        Evento evento = evento(null);
        evento.setExclusivoMembros(true);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, null, null, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exclusivo");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoNaoChecaElegibilidadeMesmoComRestricaoDeIdade() {
        Evento eventoComRestricao = Evento.builder()
                .id(eventoId).igreja(igreja())
                .titulo("Evento Kids").inicioEm(LocalDateTime.now().plusDays(10))
                .requerInscricao(true).idadeMin(0).idadeMax(12)
                .build();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(eventoComRestricao));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Não lança NaoElegivelException mesmo sem nenhum dado de idade — prova que
        // inscreverConvidado nunca chama ElegibilidadeService.
        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, null, null, null, false).inscricao();

        assertThat(salva.isConvidadoSemCadastro()).isTrue();
    }

    @Test
    void inscreverConvidadoGravaConvidadoPorPessoaIdQuandoInformado() {
        UUID convidadoPorId = UUID.randomUUID();
        Pessoa convidante = Pessoa.builder().id(convidadoPorId).build();
        Evento evento = evento(null);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(membroRepository.findByIdAndIgrejaId(convidadoPorId, igrejaId)).thenReturn(Optional.of(convidante));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, convidadoPorId, null, null, false).inscricao();

        assertThat(salva.getConvidadoPor()).isEqualTo(convidante);
    }

    @Test
    void inscreverConvidadoRecusaDuplicadoPorTelefoneMesmoSemPessoa() {
        // Busca o mesmo visitante/pessoa de fora duas vezes no modal (ou no convite público)
        // não pode criar duas inscrições avulsas ocupando duas vagas pra mesma pessoa.
        Evento evento = evento(null);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        InscricaoEvento jaInscrito = InscricaoEvento.builder()
                .igreja(igreja()).evento(evento).pessoa(null)
                .nomeConvidado("Maria de Fora").telefoneConvidado("(11) 99999-8888")
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.listarConvidadosSemCadastroPorEvento(eventoId)).thenReturn(List.of(jaInscrito));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria de Fora", "11999998888", null, null, null, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Você já está inscrito");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoRecusaDuplicadoComMensagemDiferenteQuandoInscritoPorOutraPessoa() {
        // Mesma duplicidade do teste acima, mas via modal do admin (inscritoPorUsuarioId
        // informado) — a mensagem fala da pessoa, não de "você", porque quem lê o erro é
        // quem está cadastrando, não quem já está inscrito.
        Evento evento = evento(null);
        UUID usuarioId = UUID.randomUUID();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        InscricaoEvento jaInscrito = InscricaoEvento.builder()
                .igreja(igreja()).evento(evento).pessoa(null)
                .nomeConvidado("Maria de Fora").telefoneConvidado("(11) 99999-8888")
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.listarConvidadosSemCadastroPorEvento(eventoId)).thenReturn(List.of(jaInscrito));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria de Fora", "11999998888", null, null, usuarioId, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Essa pessoa já está inscrita");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoGravaInscritoPorUsuarioIdQuandoInformado() {
        // "Inscrito por" (quem apertou o botão) é diferente de "convidado por" (quem trouxe):
        // no modal presencial os dois coincidem, mas o campo é gravado à parte pra a lista de
        // inscritos poder mostrar os dois corretamente.
        Evento evento = evento(null);
        UUID usuarioId = UUID.randomUUID();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, null, usuarioId, null, false).inscricao();

        assertThat(salva.getInscritoPorUsuarioId()).isEqualTo(usuarioId);
    }

    @Test
    void inscreverConvidadoGravaVisitanteQuandoInformado() {
        Evento evento = evento(null);
        UUID visitanteId = UUID.randomUUID();
        Visitante visitante = Visitante.builder().id(visitanteId).igreja(igreja()).nome("Maria").build();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(visitanteRepository.findByIdAndIgrejaId(visitanteId, igrejaId)).thenReturn(Optional.of(visitante));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, null, null, visitanteId, false).inscricao();

        assertThat(salva.getVisitante()).isEqualTo(visitante);
    }

    @Test
    void inscreverConvidadoRecusaDuplicadoPorVisitanteIdMesmoComNomeDiferente() {
        // Checagem exata por id — mais confiável que comparar nome/telefone (apelido, telefone
        // desatualizado etc.), e é exatamente o caso que motivou o vínculo Visitante→Inscrição.
        Evento evento = evento(null);
        UUID visitanteId = UUID.randomUUID();
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        Visitante visitante = Visitante.builder().id(visitanteId).igreja(igreja()).nome("Maria").build();
        InscricaoEvento jaInscrito = InscricaoEvento.builder()
                .igreja(igreja()).evento(evento).pessoa(null)
                .nomeConvidado("Maria").visitante(visitante)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.listarConvidadosSemCadastroPorEvento(eventoId)).thenReturn(List.of(jaInscrito));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria Apelido", null, null, null, null, visitanteId, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já está inscrit");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoAceitaConvidadoPorNulo() {
        Evento evento = evento(null);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(0L);
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, null, null, null, null, false).inscricao();

        assertThat(salva.getConvidadoPor()).isNull();
        verify(membroRepository, never()).findByIdAndIgrejaId(any(), any());
    }
}
