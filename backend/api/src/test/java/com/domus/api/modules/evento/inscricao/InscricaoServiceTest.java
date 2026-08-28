package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.elegibilidade.ElegibilidadeService;
import com.domus.api.modules.evento.elegibilidade.regras.RegraEstadoCivil;
import com.domus.api.modules.evento.elegibilidade.regras.RegraFaixaEtaria;
import com.domus.api.modules.evento.elegibilidade.regras.RegraSexo;
import com.domus.api.modules.evento.elegibilidade.regras.RegraVinculo;
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
    com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService movimentacaoAutomaticaService;
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
        movimentacaoAutomaticaService = mock(com.domus.api.modules.financeiro.movimentacao.MovimentacaoAutomaticaService.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                membroRepository, usuarioRepository, visitanteRepository,
                elegibilidadeService, familiaIgrejaService, notificacaoService,
                campoPersonalizadoRepository, respostaCampoPersonalizadoRepository,
                cobrancaEventoService, cobrancaEventoRepository, mercadoPagoClient,
                contaPagamentoIgrejaRepository, emailService, movimentacaoAutomaticaService);
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
        // E-mail obrigatório pra se inscrever em qualquer evento (2026-08-27) — presente
        // por padrão aqui pra não confundir com EMAIL_OBRIGATORIO os testes que não são
        // sobre isso; os que testam a regra em si constroem a Pessoa sem e-mail à parte.
        return Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(vinculo)
                .build();
    }

    private com.domus.api.modules.pagamento.cobranca.CobrancaEvento cobrancaPagaComId(String mpPaymentId) {
        com.domus.api.modules.pagamento.cobranca.CobrancaEvento c =
                new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                        igrejaId, eventoId, inscricaoId, pessoaId,
                        new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600),
                        usuarioId, "token-" + UUID.randomUUID());
        c.marcarComoPago(mpPaymentId);
        return c;
    }

    private com.domus.api.modules.pagamento.cobranca.CobrancaEvento cobrancaPendente() {
        return new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
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
    void inscreverRecusaPessoaSemEmailMesmoEmEventoGratuito() {
        // Decisão do autor (2026-08-27): e-mail obrigatório pra se inscrever em qualquer
        // evento — sem ele, não daria pra avisar se o evento gratuito virar pago depois.
        Pessoa semEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Sem Email")
                .vinculo(Vinculo.MEMBRO).build();
        dado(evento(10), semEmail, 0);

        assertThatThrownBy(() -> service.inscrever(eventoId, pessoaId, null, pessoaId, null, false, igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "EMAIL_OBRIGATORIO");

        verify(inscricaoRepository, never()).save(any());
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
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));

        assertThatThrownBy(() -> service.inscreverConvidado(
                eventoId, igrejaId, "João", null, null, pessoaId, usuarioId, null, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não permite convidados");
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

        verify(mercadoPagoClient).estornarParcial(igrejaId, "mp-payment-1", new java.math.BigDecimal("50.00"));
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    // Achado ao vivo (2026-08-27): cancelar uma inscrição que já tinha recebido um estorno
    // PARCIAL antes (reajuste de preço pra baixo) tentava estornar o valor CHEIO de novo —
    // o Mercado Pago recusa por falta de saldo, e a pessoa nunca conseguia cancelar.
    @Test
    void cancelarInscricaoComEstornoParcialAnteriorSoEstornaORestante() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        var cobrancaComEstornoParcial = cobrancaPagaComId("mp-payment-1"); // valor = 50.00
        cobrancaComEstornoParcial.registrarEstorno(new java.math.BigDecimal("20.00")); // já devolveu 20 antes
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaComEstornoParcial));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        // Só o RESTANTE (50 - 20 = 30), nunca o valor cheio de novo.
        verify(mercadoPagoClient).estornarParcial(igrejaId, "mp-payment-1", new java.math.BigDecimal("30.00"));
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        assertThat(cobrancaComEstornoParcial.getStatus()).isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.REEMBOLSADO);
    }

    @Test
    void cancelarInscricaoJaTotalmenteEstornadaAntesNaoTentaEstornarDeNovo() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        var cobrancaJaTotalmenteEstornada = cobrancaPagaComId("mp-payment-1"); // valor = 50.00
        cobrancaJaTotalmenteEstornada.registrarEstorno(new java.math.BigDecimal("50.00"));
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaJaTotalmenteEstornada));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void cancelamentoComReembolsoRegistraSaidaNoFinanceiro() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verify(movimentacaoAutomaticaService).registrarSaidaDeEvento(
            eq(igrejaId), eq(new java.math.BigDecimal("50.00")),
            org.mockito.ArgumentMatchers.contains("Maria"), eq(pessoaId), eq("Maria"));
    }

    @Test
    void cancelamentoDeCobrancaPendenteNaoRegistraNadaNoFinanceiro() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.buscarVisivelParaFamilia(inscricaoId, Set.of(igrejaId)))
                .thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPendente()));

        service.cancelar(inscricaoId, usuarioId, pessoaId, "ACESSO_COMUM", igrejaId);

        verifyNoInteractions(movimentacaoAutomaticaService);
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

        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
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
                .when(mercadoPagoClient).estornarParcial(any(), any(), any());

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
                .when(mercadoPagoClient).estornarParcial(any(), eq("mp-payment-falha"), any());

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
    void calcularImpactoEventoVirarGratuitoSomaPessoasComPagamentoEValorEAguardando() {
        InscricaoEvento paga = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        UUID inscricaoAguardandoId = UUID.randomUUID();
        InscricaoEvento aguardando = InscricaoEvento.builder()
                .id(inscricaoAguardandoId).igreja(igreja()).evento(evento(10))
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("João").vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(paga, aguardando));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoAguardandoId))
                .thenReturn(List.of(cobrancaPendente()));

        var impacto = service.calcularImpactoEventoVirarGratuito(eventoId);

        assertThat(impacto.tipo()).isEqualTo(com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.PAGO_PARA_GRATUITO);
        assertThat(impacto.pessoasComPagamentoPago()).isEqualTo(1);
        assertThat(impacto.valorTotalAEstornar()).isEqualByComparingTo("50.00");
        assertThat(impacto.pessoasAguardandoPagamento()).isEqualTo(1);
        // Prévia pura — nada de mutação nem chamada externa.
        verifyNoInteractions(mercadoPagoClient, movimentacaoAutomaticaService, emailService);
        assertThat(paga.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(aguardando.getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
    }

    @Test
    void calcularImpactoEventoVirarGratuitoDevolveSemImpactoQuandoNaoHaConfirmadoNemAguardando() {
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of());

        var impacto = service.calcularImpactoEventoVirarGratuito(eventoId);

        assertThat(impacto.tipo()).isEqualTo(com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.SEM_IMPACTO);
        assertThat(impacto.pessoasComPagamentoPago()).isZero();
        assertThat(impacto.pessoasAguardandoPagamento()).isZero();
    }

    @Test
    void calcularImpactoEventoVirarPagoContaConfirmadosEMultiplicaPeloPrecoNovo() {
        InscricaoEvento confirmada1 = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento confirmada2 = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("João").vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento aguardando = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Ana").vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(confirmada1, confirmada2, aguardando));

        var impacto = service.calcularImpactoEventoVirarPago(eventoId, new java.math.BigDecimal("30.00"));

        assertThat(impacto.tipo()).isEqualTo(com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.GRATUITO_PARA_PAGO);
        assertThat(impacto.pessoasSeraoCobradas()).isEqualTo(2);
        assertThat(impacto.valorTotalACobrar()).isEqualByComparingTo("60.00");
        verifyNoInteractions(cobrancaEventoService);
    }

    @Test
    void calcularImpactoEventoVirarPagoDevolveSemImpactoQuandoNaoHaConfirmado() {
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of());

        var impacto = service.calcularImpactoEventoVirarPago(eventoId, new java.math.BigDecimal("30.00"));

        assertThat(impacto.tipo()).isEqualTo(com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.SEM_IMPACTO);
    }

    @Test
    void eventoVirouPagoCriaCobrancaEConfirmaComoAguardandoPagamento() {
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        var cobrancaCriada = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("30.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, "token-gerado");
        when(cobrancaEventoService.criarParaTerceiro(
                igrejaId, eventoId, inscricaoId, pessoaId, new java.math.BigDecimal("30.00"), usuarioId, true))
                .thenReturn(cobrancaCriada);
        UUID usuarioDaPessoaId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoaId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioDaPessoaId).build()));

        int processadas = service.aplicarEventoVirouPago(eventoId, new java.math.BigDecimal("30.00"), usuarioId);

        assertThat(processadas).isEqualTo(1);
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        verify(cobrancaEventoService).criarParaTerceiro(
                igrejaId, eventoId, inscricaoId, pessoaId, new java.math.BigDecimal("30.00"), usuarioId, true);

        var assuntoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), assuntoCaptor.capture(), corpoCaptor.capture());
        assertThat(assuntoCaptor.getValue()).contains("pago");
        assertThat(corpoCaptor.getValue()).contains("token-gerado");

        // Além do e-mail, notifica dentro do próprio Domus quem tem conta (achado da
        // sessão: alguns fluxos só mandavam e-mail, sem notificação in-app).
        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_VIROU_PAGO),
                eq(igrejaId), eq(usuarioDaPessoaId), any(), any());
    }

    @Test
    void eventoVirouPagoIgnoraInscricoesNaoConfirmadas() {
        InscricaoEvento aguardando = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(evento(10))
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("João").vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(aguardando, cancelada));

        int processadas = service.aplicarEventoVirouPago(eventoId, new java.math.BigDecimal("30.00"), usuarioId);

        assertThat(processadas).isZero();
        verifyNoInteractions(cobrancaEventoService);
        assertThat(aguardando.getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        assertThat(cancelada.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
    }

    @Test
    void eventoVirouPagoLancaErroSemContaDePagamentoConectada() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(contaPagamentoIgrejaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aplicarEventoVirouPago(eventoId, new java.math.BigDecimal("30.00"), usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "IGREJA_SEM_CONTA_PAGAMENTO");
        verifyNoInteractions(cobrancaEventoService);
    }

    @Test
    void eventoVirouGratuitoEstornaCobrancaPagaEMantemInscricaoConfirmada() {
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));
        UUID usuarioDaPessoaId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoaId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioDaPessoaId).build()));

        int processadas = service.aplicarEventoVirouGratuito(eventoId);

        assertThat(processadas).isEqualTo(1);
        verify(mercadoPagoClient).estornarParcial(igrejaId, "mp-payment-1", new java.math.BigDecimal("50.00"));
        // Diferente do cancelamento: a inscrição continua CONFIRMADA, nunca vira CANCELADA —
        // o evento é que ficou gratuito, ninguém perdeu a vaga.
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(movimentacaoAutomaticaService).registrarSaidaDeEvento(
                eq(igrejaId), eq(new java.math.BigDecimal("50.00")),
                org.mockito.ArgumentMatchers.contains("Maria"), eq(pessoaId), eq("Maria"));

        var assuntoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), assuntoCaptor.capture(), corpoCaptor.capture());
        assertThat(assuntoCaptor.getValue()).contains("gratuito");
        assertThat(corpoCaptor.getValue()).contains("reembolsado");

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.EVENTO_VIROU_GRATUITO),
                eq(igrejaId), eq(usuarioDaPessoaId), any(), any());
    }

    @Test
    void eventoVirouGratuitoConfirmaInscricaoQueEstavaAguardandoPagamentoSemRegistrarFinanceiro() {
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPendente()));

        int processadas = service.aplicarEventoVirouGratuito(eventoId);

        assertThat(processadas).isEqualTo(1);
        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        // Cobrança PENDENTE cancelada nunca chegou a debitar ninguém — sem lançamento no financeiro.
        verifyNoInteractions(movimentacaoAutomaticaService);

        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), any(), corpoCaptor.capture());
        assertThat(corpoCaptor.getValue()).doesNotContain("reembolsado");
        assertThat(corpoCaptor.getValue()).contains("não precisa mais pagar");
    }

    @Test
    void eventoVirouGratuitoIgnoraInscricoesJaCanceladas() {
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CANCELADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(cancelada));

        int processadas = service.aplicarEventoVirouGratuito(eventoId);

        assertThat(processadas).isZero();
        verifyNoInteractions(mercadoPagoClient, cobrancaEventoRepository);
    }

    @Test
    void eventoVirouGratuitoContinuaProcessandoAposFalhaDeEstornoDeUmaInscricao() {
        UUID inscricaoComFalhaId = UUID.randomUUID();
        InscricaoEvento comFalha = InscricaoEvento.builder()
                .id(inscricaoComFalhaId).igreja(igreja()).evento(evento(10))
                .pessoa(Pessoa.builder().id(UUID.randomUUID()).igreja(igreja()).nome("Falha").vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento comSucesso = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(comFalha, comSucesso));

        var cobrancaComFalha = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoComFalhaId, comFalha.getPessoa().getId(),
                new java.math.BigDecimal("30.00"), java.time.Instant.now().plusSeconds(3600), usuarioId, null);
        cobrancaComFalha.marcarComoPago("mp-payment-falha");
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoComFalhaId))
                .thenReturn(List.of(cobrancaComFalha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-ok")));
        doThrow(new IllegalStateException("Mercado Pago fora do ar"))
                .when(mercadoPagoClient).estornarParcial(any(), eq("mp-payment-falha"), any());

        int processadas = service.aplicarEventoVirouGratuito(eventoId);

        assertThat(processadas).isEqualTo(1);
        assertThat(comFalha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(comSucesso.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(mercadoPagoClient).estornarParcial(igrejaId, "mp-payment-ok", new java.math.BigDecimal("50.00"));
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
                .when(mercadoPagoClient).estornarParcial(any(), eq("mp-payment-falha-3"), any());

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
        // Cada convidado agora é sua própria InscricaoEvento (ligada por convidadoPor) —
        // a lista traz as duas linhas, cada uma reduzida (sem telefone, sem "quem inscreveu",
        // sem data — o record ParticipanteResponse nem tem esses campos).
        Evento e = evento(10);
        Pessoa titular = membro(Vinculo.MEMBRO);
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .pessoa(titular)
                .inscritoPorUsuarioId(usuarioId)
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento convidado = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja()).evento(e)
                .nomeConvidado("Convidado").telefoneConvidado("11999998888")
                .convidadoPor(titular)
                .status(StatusInscricao.CONFIRMADA).build();
        when(eventoRepository.buscarVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(e));
        when(inscricaoRepository.listarPorEvento(eventoId))
                .thenReturn(java.util.List.of(inscricao, convidado));

        var participantes = service.listarParticipantes(eventoId, igrejaId);

        assertThat(participantes).hasSize(2);
        var titularResp = participantes.stream().filter(p -> p.nome().equals("Maria")).findFirst().orElseThrow();
        assertThat(titularResp.convidadoPorNome()).isNull();
        var convidadoResp = participantes.stream().filter(p -> p.nome().equals("Convidado")).findFirst().orElseThrow();
        assertThat(convidadoResp.convidadoPorNome()).isEqualTo("Maria");
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
                .when(mercadoPagoClient).estornarParcial(any(), eq("mp-payment-falha"), any());

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

        when(inscricaoRepository.findByPessoaIdAndStatusAndEventoExclusivoMembrosTrue(
                pessoaId, StatusInscricao.CONFIRMADA))
                .thenReturn(java.util.List.of(inscricaoExclusiva));
        when(inscricaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int canceladas = service.cancelarInscricoesEmEventosExclusivos(pessoaId);

        assertThat(canceladas).isEqualTo(1);
        assertThat(inscricaoExclusiva.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
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
                .when(mercadoPagoClient).estornarParcial(any(), eq("mp-payment-falha-2"), any());
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
    void inscritoResponseTrazIgrejaDaPessoa() {
        Pessoa pessoaDeOutraIgreja = pessoaComIgreja(UUID.randomUUID(), "Congregação Norte", "CN");
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).pessoa(pessoaDeOutraIgreja)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        InscritoResponse response = InscritoResponse.from(inscricao, pessoaDeOutraIgreja, null, null, false, null);

        assertThat(response.igrejaDaPessoa().nome()).isEqualTo("Congregação Norte");
    }

    @Test
    void participanteResponseTrazIgrejaDaPessoa() {
        Pessoa pessoaDeOutraIgreja = pessoaComIgreja(UUID.randomUUID(), "Congregação Sul", "CS");
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).pessoa(pessoaDeOutraIgreja)
                .createdAt(java.time.LocalDateTime.now())
                .build();

        ParticipanteResponse response = ParticipanteResponse.from(inscricao, pessoaDeOutraIgreja, null);

        assertThat(response.igrejaDaPessoa().nome()).isEqualTo("Congregação Sul");
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
                .id(pessoaId).igreja(igreja()).nome("Pastor da Congregação B").email("pastor@email.com")
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
                .id(pessoaId).igreja(igreja()).nome("Líder").email("lider@email.com")
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
                "11999998888", "maria@fora.com", convidadoPorId, null, null, false).inscricao();

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
                isNull(), eq(java.math.BigDecimal.valueOf(80)), any(), eq(false)))
                .thenReturn(mock(com.domus.api.modules.pagamento.cobranca.CobrancaEvento.class));

        var resultado = service.inscreverConvidado(eventoId, igrejaId, "Fulano", "11999999999",
                "fulano@teste.com", null, usuarioId, null, false);

        assertThat(resultado.inscricao().getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        verify(cobrancaEventoService).criarParaTerceiro(eq(igrejaId), eq(eventoId), any(),
                isNull(), eq(java.math.BigDecimal.valueOf(80)), any(), eq(false));
    }

    @Test
    void inscreverConvidadoEmEventoPagoSemEmailRecusaAntesDeCriarQualquerCoisa() {
        // Em evento pago, e-mail é a única forma de mandar o comprovante de pagamento pra
        // quem não tem cadastro — recusa antes de criar inscrição ou cobrança.
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
                .criarParaTerceiro(any(), any(), any(), any(), any(), any(), anyBoolean());
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
                "fulano@teste.com", null, usuarioId, null, false);

        assertThat(resultado.inscricao().getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        assertThat(resultado.cobranca()).isNull();
        verify(cobrancaEventoService, never())
                .criarParaTerceiro(any(), any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void inscreverConvidadoRecusaSemEmailMesmoEmEventoGratuito() {
        // Decisão do autor (2026-08-27): e-mail deixou de ser exigido só em evento pago —
        // sem ele, não daria pra avisar o convidado se o evento gratuito virar pago depois
        // (ver EventoService.aplicarEventoVirouPago).
        Evento evento = evento(10);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Fulano",
                "11999999999", null, null, usuarioId, null, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "EMAIL_OBRIGATORIO");

        verify(inscricaoRepository, never()).save(any());
    }

    @Test
    void inscreverConvidadoRecusaQuandoVagasEsgotadas() {
        Evento evento = evento(1);
        when(eventoRepository.buscarComLockVisivelParaFamilia(eventoId, igrejaId, Set.of(igrejaId)))
                .thenReturn(Optional.of(evento));
        when(inscricaoRepository.contarPessoasConfirmadas(eventoId)).thenReturn(1L);

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria", null, "maria@teste.com", null, null, null, false))
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
        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, "maria@teste.com", null, null, null, false).inscricao();

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

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, "maria@teste.com", convidadoPorId, null, null, false).inscricao();

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

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria de Fora", "11999998888", "maria@fora.com", null, null, null, false))
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

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria de Fora", "11999998888", "maria@fora.com", null, usuarioId, null, false))
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

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, "maria@teste.com", null, usuarioId, null, false).inscricao();

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

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, "maria@teste.com", null, null, visitanteId, false).inscricao();

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

        assertThatThrownBy(() -> service.inscreverConvidado(eventoId, igrejaId, "Maria Apelido", null, "maria@teste.com", null, null, visitanteId, false))
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

        InscricaoEvento salva = service.inscreverConvidado(eventoId, igrejaId, "Maria", null, "maria@teste.com", null, null, null, false).inscricao();

        assertThat(salva.getConvidadoPor()).isNull();
        verify(membroRepository, never()).findByIdAndIgrejaId(any(), any());
    }

    // ---- enviarLembretePagamento (2026-08-27) ----

    @Test
    void enviarLembretePagamentoUsaLinkPublicoQuandoCobrancaTemToken() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId)).thenReturn(List.of(cobrancaPendente()));
        UUID usuarioDaPessoaId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoaId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioDaPessoaId).build()));

        service.enviarLembretePagamento(inscricaoId, igrejaId, "ADMIN_IGREJA");

        var assuntoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), assuntoCaptor.capture(), corpoCaptor.capture());
        assertThat(assuntoCaptor.getValue()).contains("Lembrete");
        assertThat(assuntoCaptor.getValue()).doesNotContainIgnoringCase("cobrança");
        assertThat(corpoCaptor.getValue()).contains("/cobranca/");
        assertThat(corpoCaptor.getValue()).doesNotContainIgnoringCase("cobrar");
        assertThat(corpoCaptor.getValue()).contains("/pagamento/").contains("/cancelar");

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.LEMBRETE_PAGAMENTO_PENDENTE),
                eq(igrejaId), eq(usuarioDaPessoaId), any(), any());
    }

    @Test
    void enviarLembretePagamentoUsaLinkAutenticadoQuandoCobrancaEhDoTitular() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        var cobrancaSemToken = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, null);
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId)).thenReturn(List.of(cobrancaSemToken));

        service.enviarLembretePagamento(inscricaoId, igrejaId, "LIDER");

        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), any(), corpoCaptor.capture());
        assertThat(corpoCaptor.getValue()).contains("/eventos/" + eventoId + "/pagamento/" + cobrancaSemToken.getId());
    }

    @Test
    void enviarLembretePagamentoRecusaQuandoInscricaoNaoEstaAguardandoPagamento() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(minha));

        assertThatThrownBy(() -> service.enviarLembretePagamento(inscricaoId, igrejaId, "ADMIN_IGREJA"))
                .hasFieldOrPropertyWithValue("codigo", "INSCRICAO_NAO_AGUARDA_PAGAMENTO");
        verifyNoInteractions(emailService);
    }

    @Test
    void enviarLembretePagamentoRecusaParaQuemNaoGerenciaInscricoes() {
        assertThatThrownBy(() -> service.enviarLembretePagamento(inscricaoId, igrejaId, "ACESSO_COMUM"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(emailService, inscricaoRepository);
    }

    @Test
    void enviarLembretePagamentoRecusaSemEmailCadastrado() {
        Pessoa semEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email(null)
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(semEmail)
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId)).thenReturn(List.of(cobrancaPendente()));

        assertThatThrownBy(() -> service.enviarLembretePagamento(inscricaoId, igrejaId, "ADMIN_IGREJA"))
                .hasFieldOrPropertyWithValue("codigo", "SEM_EMAIL_PARA_LEMBRETE");
    }

    // ---- cancelarPorCobranca (2026-08-27) — "Cancelar inscrição" do e-mail de lembrete ----

    @Test
    void cancelarPorCobrancaCancelaInscricaoAguardandoPagamento() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        var cobrancaId = UUID.randomUUID();
        var cobranca = cobrancaPendente();
        when(cobrancaEventoRepository.findById(cobrancaId)).thenReturn(Optional.of(cobranca));
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId)).thenReturn(List.of(cobranca));

        service.cancelarPorCobranca(cobrancaId);

        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CANCELADA);
        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
    }

    @Test
    void cancelarPorCobrancaRecusaQuandoInscricaoNaoEstaMaisAguardandoPagamento() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        var cobrancaId = UUID.randomUUID();
        when(cobrancaEventoRepository.findById(cobrancaId)).thenReturn(Optional.of(cobrancaPendente()));
        when(inscricaoRepository.findById(inscricaoId)).thenReturn(Optional.of(minha));

        assertThatThrownBy(() -> service.cancelarPorCobranca(cobrancaId))
                .hasFieldOrPropertyWithValue("codigo", "INSCRICAO_NAO_AGUARDA_PAGAMENTO");
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
    }

    // ---- aplicarMudancaValorPago / calcularImpactoMudancaValorPago (2026-08-27) ----
    // Evento continua pago, só o valor muda — terceira direção além do toggle
    // gratuito<->pago (ver aplicarEventoVirouPago/aplicarEventoVirouGratuito acima).

    @Test
    void calcularImpactoMudancaValorPagoDetectaAumentoParaQuemJaPagou() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByEventoId(eventoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));

        var impacto = service.calcularImpactoMudancaValorPago(
                eventoId, new java.math.BigDecimal("50.00"), new java.math.BigDecimal("80.00"));

        assertThat(impacto.tipo()).isEqualTo(
                com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.VALOR_AUMENTOU);
        assertThat(impacto.pessoasSeraoCobradas()).isEqualTo(1);
        assertThat(impacto.valorTotalACobrar()).isEqualByComparingTo("30.00");
    }

    @Test
    void calcularImpactoMudancaValorPagoDetectaReducaoParaQuemJaPagou() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        // Pagou 80 de verdade (não os 50 fixos de cobrancaPagaComId) — o preço caiu de
        // 80 pra 50, então o que importa é o que a pessoa REALMENTE pagou.
        var cobrancaPagou80 = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("80.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, null);
        cobrancaPagou80.marcarComoPago("mp-payment-1");
        when(cobrancaEventoRepository.findByEventoId(eventoId)).thenReturn(List.of(cobrancaPagou80));

        var impacto = service.calcularImpactoMudancaValorPago(
                eventoId, new java.math.BigDecimal("80.00"), new java.math.BigDecimal("50.00"));

        assertThat(impacto.tipo()).isEqualTo(
                com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.VALOR_DIMINUIU);
        assertThat(impacto.pessoasComPagamentoPago()).isEqualTo(1);
        assertThat(impacto.valorTotalAEstornar()).isEqualByComparingTo("30.00");
    }

    @Test
    void aplicarMudancaValorPagoGeraCobrancaDeComplementoEVoltaParaAguardandoPagamento() {
        // Decisão do usuário (2026-08-27): tratar exatamente como aplicarEventoVirouPago —
        // mesma pendência, mesma tag "Pagamento pendente", mesmo lembrete/cancelamento.
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByEventoId(eventoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));
        var complemento = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("30.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, "token-complemento");
        when(cobrancaEventoService.criarParaTerceiro(
                igrejaId, eventoId, inscricaoId, pessoaId, new java.math.BigDecimal("30.00"), usuarioId, true))
                .thenReturn(complemento);
        UUID usuarioDaPessoaId = UUID.randomUUID();
        when(usuarioRepository.findByPessoaId(pessoaId))
                .thenReturn(Optional.of(com.domus.api.modules.usuario.Usuario.builder().id(usuarioDaPessoaId).build()));

        int processadas = service.aplicarMudancaValorPago(
                eventoId, new java.math.BigDecimal("50.00"), new java.math.BigDecimal("80.00"), usuarioId);

        assertThat(processadas).isEqualTo(1);
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        verify(inscricaoRepository).save(minha);
        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());

        var assuntoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), assuntoCaptor.capture(), corpoCaptor.capture());
        assertThat(corpoCaptor.getValue()).contains("token-complemento");
        assertThat(corpoCaptor.getValue()).contains("cancelar"); // mesmo botão do lembrete comum

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.COMPLEMENTO_PAGAMENTO_PENDENTE),
                eq(igrejaId), eq(usuarioDaPessoaId), any(), any());
    }

    @Test
    void aplicarMudancaValorPagoEstornaParcialSemMudarStatusQuandoValorBaixa() {
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        // Pagou 80 de verdade (não os 50 fixos de cobrancaPagaComId) — o preço caiu de
        // 80 pra 50, então o que importa é o que a pessoa REALMENTE pagou.
        var cobrancaPagou80 = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("80.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, null);
        cobrancaPagou80.marcarComoPago("mp-payment-1");
        when(cobrancaEventoRepository.findByEventoId(eventoId)).thenReturn(List.of(cobrancaPagou80));

        int processadas = service.aplicarMudancaValorPago(
                eventoId, new java.math.BigDecimal("80.00"), new java.math.BigDecimal("50.00"), usuarioId);

        assertThat(processadas).isEqualTo(1);
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(mercadoPagoClient).estornarParcial(igrejaId, "mp-payment-1", new java.math.BigDecimal("30.00"));
        verify(cobrancaEventoService, never()).criarParaTerceiro(any(), any(), any(), any(), any(), any(), anyBoolean());
        verify(movimentacaoAutomaticaService).registrarSaidaDeEvento(
                eq(igrejaId), eq(new java.math.BigDecimal("30.00")),
                org.mockito.ArgumentMatchers.contains("Maria"), eq(pessoaId), eq("Maria"));
        verify(emailService).enviar(eq("maria@email.com"), any(), any());
    }

    @Test
    void aplicarMudancaValorPagoSoAtualizaValorDaCobrancaPendenteDeQuemAindaNaoPagou() {
        InscricaoEvento aguardando = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(aguardando));
        var cobrancaPendente = cobrancaPendente();
        when(cobrancaEventoRepository.findByEventoId(eventoId)).thenReturn(List.of(cobrancaPendente));

        int processadas = service.aplicarMudancaValorPago(
                eventoId, new java.math.BigDecimal("50.00"), new java.math.BigDecimal("80.00"), usuarioId);

        assertThat(processadas).isEqualTo(1);
        assertThat(aguardando.getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
        assertThat(cobrancaPendente.getValor()).isEqualByComparingTo("80.00");
        verify(cobrancaEventoRepository).save(cobrancaPendente);
        verifyNoInteractions(emailService, mercadoPagoClient, cobrancaEventoService);
    }

    @Test
    void aplicarMudancaValorPagoLancaErroSemContaDePagamentoConectadaQuandoAumentaEHaConfirmado() {
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByEventoId(eventoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-1")));
        when(contaPagamentoIgrejaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.aplicarMudancaValorPago(
                eventoId, new java.math.BigDecimal("50.00"), new java.math.BigDecimal("80.00"), usuarioId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("codigo", "IGREJA_SEM_CONTA_PAGAMENTO");
        verifyNoInteractions(cobrancaEventoService);
    }

    // Achado ao vivo (2026-08-27): um segundo reajuste, com um complemento do primeiro
    // ainda pendente, cobrava o valor CHEIO do novo preço em vez de só a diferença
    // restante — porque a diferença era calculada a partir do preço antigo do EVENTO, não
    // do que a pessoa já tinha pago de verdade.
    @Test
    void aplicarMudancaValorPagoAtualizaComplementoJaAbertoEmVezDeCobrarValorCheioNoSegundoReajuste() {
        // Já pagou 50 (valor original) e tem um complemento de 15 aberto, pendente, do
        // primeiro reajuste (50 -> 65). Preço sobe de novo, agora 65 -> 90 — falta pagar
        // 40 (90 - 50 já pago), não 90 (o valor cheio do preço novo).
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        var pagoOriginal = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600), usuarioId, null);
        pagoOriginal.marcarComoPago("mp-payment-original");
        var complementoAbertoDoPrimeiroReajuste = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("15.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, "token-complemento-1");
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByEventoId(eventoId))
                .thenReturn(List.of(pagoOriginal, complementoAbertoDoPrimeiroReajuste));

        int processadas = service.aplicarMudancaValorPago(
                eventoId, new java.math.BigDecimal("65.00"), new java.math.BigDecimal("90.00"), usuarioId);

        assertThat(processadas).isEqualTo(1);
        // Reaproveita a cobrança do primeiro complemento — nunca cria uma segunda.
        assertThat(complementoAbertoDoPrimeiroReajuste.getValor()).isEqualByComparingTo("40.00");
        verify(cobrancaEventoRepository).save(complementoAbertoDoPrimeiroReajuste);
        verify(cobrancaEventoService, never()).criarParaTerceiro(any(), any(), any(), any(), any(), any(), anyBoolean());
        // Já estava AGUARDANDO_PAGAMENTO desde o primeiro reajuste — continua assim.
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.AGUARDANDO_PAGAMENTO);
    }

    // Achado ao vivo (2026-08-27): um reajuste que zera a diferença exatamente (a pessoa já
    // pagou exatamente o novo preço, contando o que tinha de complemento pendente) não
    // resolvia nada — a cobrança pendente do complemento ficava aberta com um valor que já
    // não era mais devido, e o status continuava travado em AGUARDANDO_PAGAMENTO.
    @Test
    void aplicarMudancaValorPagoResolveComplementoQuandoNovoPrecoZeraADiferenca() {
        // Pagou 50 (original) e tem 15 de complemento pendente (do primeiro reajuste,
        // 50 -> 65). O preço agora CAI de 65 pra 50 — a diferença zera: não deve nem
        // estornar (não pagou os 15 ainda) nem deixar nada pendente.
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        var pagoOriginal = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600), usuarioId, null);
        pagoOriginal.marcarComoPago("mp-payment-original");
        var complementoPendente = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("15.00"), java.time.Instant.now().plusSeconds(3600),
                usuarioId, "token-complemento");
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(minha));
        when(cobrancaEventoRepository.findByEventoId(eventoId))
                .thenReturn(List.of(pagoOriginal, complementoPendente));

        int processadas = service.aplicarMudancaValorPago(
                eventoId, new java.math.BigDecimal("65.00"), new java.math.BigDecimal("50.00"), usuarioId);

        assertThat(processadas).isEqualTo(1);
        assertThat(complementoPendente.getStatus()).isEqualTo(com.domus.api.modules.pagamento.cobranca.StatusCobranca.CANCELADO);
        assertThat(minha.getStatus()).isEqualTo(StatusInscricao.CONFIRMADA);
        verify(mercadoPagoClient, never()).estornarParcial(any(), any(), any());
    }

    // Achado ao vivo (2026-08-27): quando pessoas diferentes já tinham histórico de preço
    // diferente (reajustes anteriores), um novo reajuste pode fazer algumas deverem mais e
    // outras precisarem de estorno AO MESMO TEMPO — a prévia tinha que mostrar as duas
    // direções, não só uma escondendo a outra.
    @Test
    void calcularImpactoMudancaValorPagoDevolveVoltaMistoQuandoAsDuasDirecoesCoexistem() {
        UUID inscricaoIdB = UUID.randomUUID();
        UUID pessoaIdB = UUID.randomUUID();
        InscricaoEvento pessoaA = InscricaoEvento.builder() // pagou 50, vai dever mais
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(membro(Vinculo.MEMBRO))
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento pessoaB = InscricaoEvento.builder() // pagou 100 (reajuste anterior), vai ser estornada
                .id(inscricaoIdB).igreja(igreja()).evento(evento(10))
                .pessoa(Pessoa.builder().id(pessoaIdB).igreja(igreja()).nome("João").vinculo(Vinculo.MEMBRO).build())
                .status(StatusInscricao.CONFIRMADA).build();
        var cobrancaA = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoId, pessoaId,
                new java.math.BigDecimal("50.00"), java.time.Instant.now().plusSeconds(3600), usuarioId, null);
        cobrancaA.marcarComoPago("mp-payment-a");
        var cobrancaB = new com.domus.api.modules.pagamento.cobranca.CobrancaEvento(
                igrejaId, eventoId, inscricaoIdB, pessoaIdB,
                new java.math.BigDecimal("100.00"), java.time.Instant.now().plusSeconds(3600), usuarioId, null);
        cobrancaB.marcarComoPago("mp-payment-b");
        when(inscricaoRepository.findByEventoId(eventoId)).thenReturn(List.of(pessoaA, pessoaB));
        when(cobrancaEventoRepository.findByEventoId(eventoId)).thenReturn(List.of(cobrancaA, cobrancaB));

        // Preço novo = 80: A (pagou 50) deve mais 30; B (pagou 100) recebe 20 de volta.
        var impacto = service.calcularImpactoMudancaValorPago(
                eventoId, new java.math.BigDecimal("50.00"), new java.math.BigDecimal("80.00"));

        assertThat(impacto.tipo()).isEqualTo(
                com.domus.api.modules.evento.DTOs.ImpactoMudancaPrecoResponse.VALOR_MISTO);
        assertThat(impacto.pessoasSeraoCobradas()).isEqualTo(1);
        assertThat(impacto.valorTotalACobrar()).isEqualByComparingTo("30.00");
        assertThat(impacto.pessoasComPagamentoPago()).isEqualTo(1);
        assertThat(impacto.valorTotalAEstornar()).isEqualByComparingTo("20.00");
    }

    // Achado ao vivo (2026-08-27): o texto do lembrete não avisava que a pessoa já tinha
    // pago o valor original — parecia que ela nunca tinha pago nada, mesmo só faltando o
    // complemento de um reajuste.
    @Test
    void enviarLembretePagamentoUsaTextoDeComplementoQuandoJaPagouAlgo() {
        Pessoa pessoaComEmail = Pessoa.builder()
                .id(pessoaId).igreja(igreja()).nome("Maria").email("maria@email.com")
                .vinculo(Vinculo.MEMBRO).build();
        InscricaoEvento minha = InscricaoEvento.builder()
                .id(inscricaoId).igreja(igreja()).evento(evento(10))
                .pessoa(pessoaComEmail)
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(minha));
        when(cobrancaEventoRepository.findByInscricaoId(inscricaoId))
                .thenReturn(List.of(cobrancaPagaComId("mp-payment-original"), cobrancaPendente()));

        service.enviarLembretePagamento(inscricaoId, igrejaId, "ADMIN_IGREJA");

        var corpoCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailService).enviar(eq("maria@email.com"), any(), corpoCaptor.capture());
        assertThat(corpoCaptor.getValue()).contains("já pagou o valor original");
        assertThat(corpoCaptor.getValue()).contains("Falta complementar");
    }
}
