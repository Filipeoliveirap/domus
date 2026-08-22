package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoRequest;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.evento.campopersonalizado.DTOs.CampoPersonalizadoResponse;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CampoPersonalizadoServiceTest {

    CampoPersonalizadoEventoRepository campoRepository;
    RespostaCampoPersonalizadoRepository respostaRepository;
    EventoRepository eventoRepository;
    com.domus.api.modules.evento.inscricao.InscricaoRepository inscricaoRepository;
    com.domus.api.modules.usuario.UsuarioRepository usuarioRepository;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    CampoPersonalizadoService service;

    UUID igrejaId;
    UUID eventoId;
    UUID usuarioIdAtor;

    @BeforeEach
    void setup() {
        campoRepository = mock(CampoPersonalizadoEventoRepository.class);
        respostaRepository = mock(RespostaCampoPersonalizadoRepository.class);
        eventoRepository = mock(EventoRepository.class);
        inscricaoRepository = mock(com.domus.api.modules.evento.inscricao.InscricaoRepository.class);
        usuarioRepository = mock(com.domus.api.modules.usuario.UsuarioRepository.class);
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        service = new CampoPersonalizadoService(
                campoRepository, respostaRepository, eventoRepository, inscricaoRepository,
                usuarioRepository, notificacaoService);

        igrejaId = UUID.randomUUID();
        eventoId = UUID.randomUUID();
        usuarioIdAtor = UUID.randomUUID();
    }

    private Evento evento() {
        return Evento.builder().id(eventoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .titulo("Retiro de Jovens").build();
    }

    @Test
    void salvarCriaCamposNovosQuandoIdENulo() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());
        when(campoRepository.save(any())).thenAnswer(inv -> {
            CampoPersonalizadoEvento c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        var request = new CampoPersonalizadoRequest(
                null, "Tamanho da camiseta", null, TipoCampoPersonalizado.OPCAO_UNICA,
                List.of("P", "M", "G"), true, true, 0, null);

        List<CampoPersonalizadoResponse> resultado = service.salvar(eventoId, igrejaId, List.of(request), usuarioIdAtor);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).label()).isEqualTo("Tamanho da camiseta");
        assertThat(resultado.get(0).opcoes()).containsExactly("P", "M", "G");
        verify(campoRepository).save(any());
    }

    @Test
    void salvarArquivaCampoQueSumiuDaListaEnviada() {
        var existente = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Campo antigo").tipo(TipoCampoPersonalizado.TEXTO_CURTO).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));

        service.salvar(eventoId, igrejaId, List.of(), usuarioIdAtor);

        verify(campoRepository).delete(existente);
    }

    @Test
    void salvarLancaNotFoundQuandoEventoNaoPertenceAIgreja() {
        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.salvar(eventoId, igrejaId, List.of(), usuarioIdAtor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void salvarNotificaInscritosQuandoCampoObrigatorioNovoEAdicionado() {
        UUID pessoaInscritaId = UUID.randomUUID();
        var usuarioInscrito = com.domus.api.modules.usuario.Usuario.builder().id(UUID.randomUUID()).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());
        when(campoRepository.save(any())).thenAnswer(inv -> {
            CampoPersonalizadoEvento c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(inscricaoRepository.findPessoaIdsByEventoIdAndStatus(eventoId,
                com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(pessoaInscritaId));
        when(usuarioRepository.findByPessoaId(pessoaInscritaId)).thenReturn(Optional.of(usuarioInscrito));

        var request = new CampoPersonalizadoRequest(
                null, "Restrição alimentar", null, TipoCampoPersonalizado.TEXTO_CURTO,
                null, true, true, 0, null);

        service.salvar(eventoId, igrejaId, List.of(request), usuarioIdAtor);

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.CAMPO_PERSONALIZADO_PENDENTE),
                eq(igrejaId), eq(usuarioInscrito.getId()), anyString(), anyString());
    }

    @Test
    void salvarNaoNotificaQuemEditouOFormulario() {
        var usuarioQueEditou = com.domus.api.modules.usuario.Usuario.builder().id(usuarioIdAtor).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());
        when(campoRepository.save(any())).thenAnswer(inv -> {
            CampoPersonalizadoEvento c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(inscricaoRepository.findPessoaIdsByEventoIdAndStatus(eventoId,
                com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA))
                .thenReturn(List.of(UUID.randomUUID()));
        when(usuarioRepository.findByPessoaId(any())).thenReturn(Optional.of(usuarioQueEditou));

        var request = new CampoPersonalizadoRequest(
                null, "Restrição alimentar", null, TipoCampoPersonalizado.TEXTO_CURTO,
                null, true, true, 0, null);

        service.salvar(eventoId, igrejaId, List.of(request), usuarioIdAtor);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void salvarNaoNotificaQuandoCampoContinuaObrigatorio() {
        var campoJaObrigatorio = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Restrição alimentar").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .obrigatorio(true).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoJaObrigatorio));
        when(campoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var request = new CampoPersonalizadoRequest(
                campoJaObrigatorio.getId(), "Restrição alimentar (editado)", null,
                TipoCampoPersonalizado.TEXTO_CURTO, null, true, true, 0, null);

        service.salvar(eventoId, igrejaId, List.of(request), usuarioIdAtor);

        verify(notificacaoService, never()).criar(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void respondeComTodosObrigatoriosPreenchidosSalvaAsRespostas() {
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID campoObrigatorioId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(pessoaId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        var campoObrigatorio = CampoPersonalizadoEvento.builder()
                .id(campoObrigatorioId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).obrigatorio(true).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoObrigatorio));
        when(respostaRepository.findByCampoIdAndInscricaoIdAndAcompanhanteId(campoObrigatorioId, inscricaoId, null))
                .thenReturn(Optional.empty());

        service.responder(inscricaoId, null,
                List.of(new com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest(campoObrigatorioId, "Sem lactose")),
                igrejaId, pessoaId, "ACESSO_COMUM");

        verify(respostaRepository).save(any());
    }

    @Test
    void respondeSemPreencherObrigatorioLancaExcecao() {
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID campoObrigatorioId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(pessoaId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        var campoObrigatorio = CampoPersonalizadoEvento.builder()
                .id(campoObrigatorioId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).obrigatorio(true).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoObrigatorio));

        assertThatThrownBy(() -> service.responder(inscricaoId, null, List.of(), igrejaId, pessoaId, "ACESSO_COMUM"))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);

        verify(respostaRepository, never()).save(any());
    }

    @Test
    void terceiroSemGerenciarNaoPodeResponderPorOutraPessoa() {
        UUID inscricaoId = UUID.randomUUID();
        UUID donoDaInscricaoId = UUID.randomUUID();
        UUID quemTaTentandoId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(donoDaInscricaoId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));

        assertThatThrownBy(() -> service.responder(inscricaoId, null, List.of(), igrejaId, quemTaTentandoId, "ACESSO_COMUM"))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);
    }

    @Test
    void gestorPodeResponderPorQualquerPessoa() {
        UUID inscricaoId = UUID.randomUUID();
        UUID donoDaInscricaoId = UUID.randomUUID();
        UUID gestorId = UUID.randomUUID();

        var pessoa = new com.domus.api.modules.pessoa.Pessoa();
        pessoa.setId(donoDaInscricaoId);
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());

        service.responder(inscricaoId, null, List.of(), igrejaId, gestorId, "LIDER");

        // Não lança — chegou até o fim sem exceção de autorização.
    }

    @Test
    void salvarZeraMapeamentoQuandoTipoMuda() {
        var existenteId = UUID.randomUUID();
        var existente = CampoPersonalizadoEvento.builder()
                .id(existenteId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.IDADE).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));
        when(campoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var requestComTipoDiferente = new CampoPersonalizadoRequest(
                existenteId, "Idade", null, TipoCampoPersonalizado.SIM_NAO, null, false, true, 0,
                MapeamentoCampoPersonalizado.IDADE);

        var resultado = service.salvar(eventoId, igrejaId, List.of(requestComTipoDiferente), UUID.randomUUID());

        assertThat(resultado.get(0).mapeamento()).isNull();
    }

    @Test
    void salvarMantemMapeamentoQuandoSoRotuloMuda() {
        var existenteId = UUID.randomUUID();
        var existente = CampoPersonalizadoEvento.builder()
                .id(existenteId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.IDADE).build();

        when(eventoRepository.findByIdAndIgrejaId(eventoId, igrejaId)).thenReturn(Optional.of(evento()));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(existente));
        when(campoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var requestSoRotulo = new CampoPersonalizadoRequest(
                existenteId, "Quantos anos você tem?", null, TipoCampoPersonalizado.TEXTO_CURTO,
                null, false, true, 0, MapeamentoCampoPersonalizado.IDADE);

        var resultado = service.salvar(eventoId, igrejaId, List.of(requestSoRotulo), UUID.randomUUID());

        assertThat(resultado.get(0).mapeamento()).isEqualTo(MapeamentoCampoPersonalizado.IDADE);
    }

    @Test
    void listarParaResponderOmiteCampoMapeadoQuandoPessoaJaTemODado() {
        var campoIdade = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.IDADE).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoIdade));

        Pessoa pessoaComData = Pessoa.builder().id(UUID.randomUUID())
                .dataNascimento(java.time.LocalDate.of(2000, 1, 1)).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, pessoaComData);

        assertThat(resultado).isEmpty();
    }

    @Test
    void listarParaResponderMostraCampoMapeadoQuandoPessoaNaoTemODado() {
        var campoEstadoCivil = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Estado civil").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .mapeamento(MapeamentoCampoPersonalizado.ESTADO_CIVIL).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoEstadoCivil));

        Pessoa pessoaSemEstadoCivil = Pessoa.builder().id(UUID.randomUUID()).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, pessoaSemEstadoCivil);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void listarParaResponderSempreMostraCamposMapeadosParaConvidadoSemPessoa() {
        var campoSexo = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Sexo").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .mapeamento(MapeamentoCampoPersonalizado.SEXO).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoSexo));

        var resultado = service.listarParaResponder(eventoId, igrejaId, null);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void listarParaResponderPulaEnderecoSeQualquerParteExiste() {
        var campoEndereco = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Endereço").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .mapeamento(MapeamentoCampoPersonalizado.ENDERECO).build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoEndereco));

        var enderecoSoComCidade = com.domus.api.shared.dominio.Endereco.builder().cidade("Recife").build();
        Pessoa pessoaComCidade = Pessoa.builder().id(UUID.randomUUID()).endereco(enderecoSoComCidade).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, pessoaComCidade);

        assertThat(resultado).isEmpty();
    }

    @Test
    void listarParaResponderMostraCampoNaoMapeadoSempre() {
        var campoLivre = CampoPersonalizadoEvento.builder()
                .id(UUID.randomUUID()).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Tamanho da camiseta").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .build();
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoLivre));

        Pessoa qualquerPessoa = Pessoa.builder().id(UUID.randomUUID()).build();

        var resultado = service.listarParaResponder(eventoId, igrejaId, qualquerPessoa);

        assertThat(resultado).hasSize(1);
    }

    @Test
    void respostaAutomaticaEhCriadaQuandoCampoMapeadoEPulado() {
        UUID inscricaoId = UUID.randomUUID();
        UUID pessoaId = UUID.randomUUID();
        UUID campoIdadeId = UUID.randomUUID();

        Pessoa pessoa = Pessoa.builder().id(pessoaId)
                .dataNascimento(java.time.LocalDate.now().minusYears(20)).build();
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).pessoa(pessoa).build();

        var campoIdade = CampoPersonalizadoEvento.builder()
                .id(campoIdadeId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Idade").tipo(TipoCampoPersonalizado.TEXTO_CURTO)
                .obrigatorio(true).mapeamento(MapeamentoCampoPersonalizado.IDADE).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoIdade));
        when(respostaRepository.findByCampoIdAndInscricaoIdAndAcompanhanteId(campoIdadeId, inscricaoId, null))
                .thenReturn(Optional.empty());

        service.responder(inscricaoId, null, List.of(), igrejaId, pessoaId, "ACESSO_COMUM");

        verify(respostaRepository).save(argThat(r -> r.getValor().equals("20") && r.getAcompanhante() == null));
    }

    @Test
    void responderComoConvidadoNaoExigeDonoNemGestor() {
        UUID inscricaoId = UUID.randomUUID();
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).nomeConvidado("Maria de Fora").build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId)).thenReturn(List.of());

        service.responderComoConvidado(inscricaoId, List.of(), igrejaId);

        // Não lança SEM_PERMISSAO — chegou até o fim sem exceção de autorização.
    }

    @Test
    void responderComoConvidadoAindaValidaObrigatoriedade() {
        UUID inscricaoId = UUID.randomUUID();
        UUID campoObrigatorioId = UUID.randomUUID();
        var inscricao = com.domus.api.modules.evento.inscricao.InscricaoEvento.builder()
                .id(inscricaoId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).nomeConvidado("Maria de Fora").build();
        var campoObrigatorio = CampoPersonalizadoEvento.builder()
                .id(campoObrigatorioId).igreja(new Igreja() {{ setId(igrejaId); }})
                .evento(evento()).label("Tamanho da camiseta").tipo(TipoCampoPersonalizado.OPCAO_UNICA)
                .obrigatorio(true).build();

        when(inscricaoRepository.findByIdAndIgrejaId(inscricaoId, igrejaId)).thenReturn(Optional.of(inscricao));
        when(campoRepository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(eventoId, igrejaId))
                .thenReturn(List.of(campoObrigatorio));

        assertThatThrownBy(() -> service.responderComoConvidado(inscricaoId, List.of(), igrejaId))
                .isInstanceOf(com.domus.api.shared.exception.BusinessException.class);
    }
}
