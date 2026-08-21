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
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InscricaoServiceTest {

    EventoRepository eventoRepository;
    InscricaoRepository inscricaoRepository;
    AcompanhanteRepository acompanhanteRepository;
    PessoaRepository membroRepository;
    UsuarioRepository usuarioRepository;
    FamiliaIgrejaService familiaIgrejaService;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository campoPersonalizadoRepository;
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
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        when(familiaIgrejaService.idsDaFamiliaCompleta(any())).thenReturn(Set.of(igrejaId));
        // Real (não mock): as regras precisam rodar de verdade para os testes de elegibilidade
        // fazerem sentido (ex.: exclusivoMembros/Congregante via RegraVinculo).
        ElegibilidadeService elegibilidadeService = new ElegibilidadeService(java.util.List.of(
                new RegraFaixaEtaria(), new RegraVinculo(),
                new RegraEstadoCivil(), new RegraSexo()));
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        campoPersonalizadoRepository = mock(com.domus.api.modules.evento.campopersonalizado.CampoPersonalizadoEventoRepository.class);
        service = new InscricaoService(eventoRepository, inscricaoRepository,
                acompanhanteRepository, membroRepository, usuarioRepository, elegibilidadeService,
                familiaIgrejaService, notificacaoService, campoPersonalizadoRepository);
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

        InscritoResponse response = InscritoResponse.from(inscricao, pessoaDeOutraIgreja, null);

        assertThat(response.igrejaDaPessoa().nome()).isEqualTo("Congregação Norte");
    }

    @Test
    void participanteResponseTrazIgrejaDaPessoa() {
        Pessoa pessoaDeOutraIgreja = pessoaComIgreja(UUID.randomUUID(), "Congregação Sul", "CS");
        InscricaoEvento inscricao = InscricaoEvento.builder()
                .id(UUID.randomUUID()).pessoa(pessoaDeOutraIgreja)
                .acompanhantes(new ArrayList<>()).createdAt(java.time.LocalDateTime.now())
                .build();

        ParticipanteResponse response = ParticipanteResponse.from(inscricao, pessoaDeOutraIgreja);

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
}
