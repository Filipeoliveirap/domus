package com.domus.api.modules.igreja.familia;

import com.domus.api.modules.igreja.CodigoConviteCongregacao;
import com.domus.api.modules.igreja.CodigoConviteRepository;
import com.domus.api.modules.igreja.CodigoConviteService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.igreja.dto.GerarCodigoConviteResponse;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import com.domus.api.modules.igreja.familia.DTO.VinculoDTOs.EstadoVinculo;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.AcessoNegadoException;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VinculoServiceTest {

    IgrejaRepository igrejaRepository;
    FamiliaIgrejaService familiaService;
    CodigoVinculoGenerator gerador;
    UsuarioRepository usuarioRepository;
    com.domus.api.modules.notificacao.NotificacaoService notificacaoService;
    CodigoConviteService codigoConviteService;
    CodigoConviteRepository codigoConviteRepository;
    com.domus.api.modules.usuario.PrincipalCacheService principalCacheService;
    VinculoService service;

    Igreja mae;
    Igreja filha;
    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        igrejaRepository = mock(IgrejaRepository.class);
        familiaService = mock(FamiliaIgrejaService.class);
        gerador = new CodigoVinculoGenerator();
        usuarioRepository = mock(UsuarioRepository.class);
        codigoConviteService = mock(CodigoConviteService.class);
        codigoConviteRepository = mock(CodigoConviteRepository.class);
        principalCacheService = mock(com.domus.api.modules.usuario.PrincipalCacheService.class);
        Usuario quemVinculou = new Usuario();
        quemVinculou.setId(usuarioId);
        when(usuarioRepository.getReferenceById(usuarioId)).thenReturn(quemVinculou);
        notificacaoService = mock(com.domus.api.modules.notificacao.NotificacaoService.class);
        service = new VinculoService(
                igrejaRepository, familiaService, gerador, usuarioRepository,
                notificacaoService, codigoConviteService, codigoConviteRepository, principalCacheService);

        mae = igreja("Igreja Sede");
        mae.setPlano(PlanoAssinatura.PRO);
        filha = igreja("Congregação A");
        mae.setCodigoVinculo("XK4P-2M7Q");

        when(codigoConviteService.gerarCodigo(any())).thenReturn(new GerarCodigoConviteResponse("DOMUS-K7M9P2"));
        when(igrejaRepository.findByCodigoVinculo("XK4P-2M7Q")).thenReturn(Optional.of(mae));
        when(igrejaRepository.findByCodigoVinculo(argThat(c -> !"XK4P-2M7Q".equals(c))))
                .thenReturn(Optional.empty());
        when(familiaService.filhasDe(any())).thenReturn(List.of());
    }

    private Igreja igreja(String nome) {
        Igreja i = new Igreja();
        i.setId(UUID.randomUUID());
        i.setNome(nome);
        when(igrejaRepository.findById(i.getId())).thenReturn(Optional.of(i));
        // entrarNaFamilia trava as duas linhas antes de validar (defesa contra ciclo).
        when(igrejaRepository.buscarComLock(i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    // ---------- entrar na família: as recusas ----------

    @Test
    void codigoInexistenteEhRecusado() {
        assertThatThrownBy(() -> service.entrarNaFamilia(filha.getId(), usuarioId, "AAAA-BBBB"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido");
    }

    @Test
    void autoVinculoEhRecusado() {
        assertThatThrownBy(() -> service.entrarNaFamilia(mae.getId(), usuarioId, "XK4P-2M7Q"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ela mesma");
    }

    @Test
    void donaDoCodigoQueJaTemMaeEhRecusada() {
        Igreja avo = igreja("Outra Sede");
        mae.setIgrejaMae(avo); // a dona do código virou filha

        assertThatThrownBy(() -> service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q"))
                .isInstanceOf(BusinessException.class)
                // Mensagem achatada de propósito: distinguir "código não existe" de "a dona
                // já é congregação" transformaria o endpoint num oráculo sobre terceiros.
                .hasMessageContaining("inválido");
    }

    @Test
    void quemJaTemFilhasNaoViraCongregacao() {
        when(familiaService.ehMae(filha.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já tem congregações");
    }

    @Test
    void quemJaTemMaeNaoEntraEmOutraFamilia() {
        filha.setIgrejaMae(igreja("Sede Antiga"));

        assertThatThrownBy(() -> service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("já faz parte");
    }

    @Test
    void vinculoValidoGravaAMaeELimpaOCodigoDaFilha() {
        filha.setCodigoVinculo("ZZZZ-ZZZZ");

        service.entrarNaFamilia(filha.getId(), usuarioId, "xk4p2m7q"); // minúsculo e sem hífen: deve normalizar

        assertThat(filha.getIgrejaMae()).isSameAs(mae);
        assertThat(filha.getCodigoVinculo()).isNull();
        // Auditoria do vínculo: quando e por quem.
        assertThat(filha.getVinculadoEm()).isNotNull();
        assertThat(filha.getVinculadoPor().getId()).isEqualTo(usuarioId);
        verify(igrejaRepository).save(filha);
    }

    @Test
    void entrarNaFamiliaNotificaAdminsDaSede() {
        UUID usuarioIdAdminSede = UUID.randomUUID();
        Usuario adminSede = new Usuario();
        adminSede.setId(usuarioIdAdminSede);
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(mae.getId(), "ADMIN_IGREJA"))
                .thenReturn(List.of(adminSede));

        service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q");

        verify(notificacaoService).criar(
                eq(com.domus.api.modules.notificacao.TipoNotificacao.PEDIDO_VINCULO_FAMILIA), eq(mae.getId()),
                eq(usuarioIdAdminSede), anyString(), eq("/configuracoes/igrejas-vinculadas"));
    }

    @Test
    void notificacaoDeVinculoUsaGrupoDeUnidadesPorPadrao() {
        Usuario adminSede = new Usuario();
        adminSede.setId(UUID.randomUUID());
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(mae.getId(), "ADMIN_IGREJA"))
                .thenReturn(List.of(adminSede));

        service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q");

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService).criar(any(), any(), any(), texto.capture(), any());
        assertThat(texto.getValue())
                .isEqualTo("Congregação A pediu pra entrar no seu grupo de unidades.")
                .doesNotContain("família");
    }

    @Test
    void notificacaoDeVinculoSegueORotuloCustomizadoDaSede() {
        mae.setCongregacaoNomePlural("Congregações");
        Usuario adminSede = new Usuario();
        adminSede.setId(UUID.randomUUID());
        when(usuarioRepository.findByIgrejaIdAndRole_NomeAndAtivoTrue(mae.getId(), "ADMIN_IGREJA"))
                .thenReturn(List.of(adminSede));

        service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q");

        ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
        verify(notificacaoService).criar(any(), any(), any(), texto.capture(), any());
        assertThat(texto.getValue()).isEqualTo("Congregação A pediu pra entrar no seu grupo de congregações.");
    }

    @Test
    void travaAsDuasIgrejasAntesDeValidar() {
        service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q");

        // Sem o lock, duas requisições simultâneas criariam A.mae=B E B.mae=A — um ciclo que
        // faz pertenceAFamilia() aprovar nos DOIS sentidos e vaza financeiro entre as igrejas.
        verify(igrejaRepository).buscarComLock(filha.getId());
        verify(igrejaRepository).buscarComLock(mae.getId());
    }

    @Test
    void codigoRotacionadoEntreALeituraEOLockEhRecusado() {
        // Simula a corrida: quando a linha é travada e relida, o código já é outro.
        when(igrejaRepository.buscarComLock(mae.getId())).thenAnswer(inv -> {
            mae.setCodigoVinculo("OUTR-OCOD");
            return Optional.of(mae);
        });

        assertThatThrownBy(() -> service.entrarNaFamilia(filha.getId(), usuarioId, "XK4P-2M7Q"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido");

        assertThat(filha.getIgrejaMae()).isNull();
    }

    // ---------- gerar / rotacionar ----------

    @Test
    void rotacionarTrocaOCodigoAnterior() {
        when(codigoConviteService.gerarCodigo(any())).thenReturn(new GerarCodigoConviteResponse("DOMUS-NOVO12"));
        String antigo = mae.getCodigoVinculo();
        String novo = service.gerarCodigo(mae.getId());

        assertThat(novo).isNotEqualTo(antigo);
        assertThat(mae.getCodigoVinculo()).isEqualTo(novo);
        assertThat(mae.getCodigoGeradoEm()).isNotNull();
    }

    @Test
    void gerarCodigoNoPlanoSemLimiteLancaExcecao() {
        when(codigoConviteService.gerarCodigo(any()))
                .thenThrow(new PlanoLimiteExcedidoException("O plano Básico permite no máximo 0 congregações."));

        assertThatThrownBy(() -> service.gerarCodigo(mae.getId()))
                .isInstanceOf(PlanoLimiteExcedidoException.class);
    }

    @Test
    void entrarNaFamiliaComCodigoDomusVinculaEAtivaAssinatura() {
        mae.setPlano(PlanoAssinatura.PRO);
        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setCodigo("DOMUS-K7M9P2");
        convite.setMatriz(mae);
        when(codigoConviteRepository.findByCodigo("DOMUS-K7M9P2")).thenReturn(Optional.of(convite));

        service.entrarNaFamilia(filha.getId(), usuarioId, "domus-k7m9p2");

        assertThat(filha.getIgrejaMae()).isSameAs(mae);
        assertThat(filha.getStatusAssinatura()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(filha.getPlano()).isEqualTo(PlanoAssinatura.PRO);
        assertThat(convite.getUsadoEm()).isNotNull();
        assertThat(convite.getIgrejaFilha()).isSameAs(filha);
        verify(codigoConviteRepository).save(convite);
    }

    @Test
    void entrarNaFamiliaComLimiteAtingidoLancaExcecao() {
        mae.setPlano(PlanoAssinatura.PRO);
        when(igrejaRepository.countByIgrejaMaeId(mae.getId())).thenReturn(2L);
        CodigoConviteCongregacao convite = new CodigoConviteCongregacao();
        convite.setCodigo("DOMUS-K7M9P2");
        convite.setMatriz(mae);
        when(codigoConviteRepository.findByCodigo("DOMUS-K7M9P2")).thenReturn(Optional.of(convite));

        assertThatThrownBy(() -> service.entrarNaFamilia(filha.getId(), usuarioId, "DOMUS-K7M9P2"))
                .isInstanceOf(PlanoLimiteExcedidoException.class)
                .hasMessageContaining("limite de congregações");
    }

    @Test
    void congregacaoNaoGeraCodigo() {
        filha.setIgrejaMae(mae);

        assertThatThrownBy(() -> service.gerarCodigo(filha.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("congregação");
    }

    @Test
    void gerarCodigoNaoTornaAIgrejaMae() {
        when(familiaService.ehMae(mae.getId())).thenReturn(false);
        service.gerarCodigo(mae.getId());

        assertThat(service.status(mae.getId()).estado()).isEqualTo(EstadoVinculo.INDEPENDENTE);
    }

    // ---------- desvincular pelos dois lados ----------

    @Test
    void maeDesvinculaAPropriaCongregacao() {
        filha.setIgrejaMae(mae);

        service.desvincularCongregacao(mae.getId(), filha.getId());

        assertThat(filha.getIgrejaMae()).isNull();
        // Metadados morrem junto — senão sobrariam mentindo sobre um vínculo que não existe.
        assertThat(filha.getVinculadoEm()).isNull();
        assertThat(filha.getVinculadoPor()).isNull();
        assertThat(filha.getStatusAssinatura()).isEqualTo(StatusAssinatura.PAUSADA);
        assertThat(filha.getPlano()).isNull();
        assertThat(filha.getMotivoSuspensao()).isEqualTo("DESVINCULADA_MATRIZ");
        verify(igrejaRepository).save(filha);
    }

    @Test
    void maeNaoDesvinculaCongregacaoDeOutraFamilia() {
        Igreja outraMae = igreja("Sede Estranha");
        filha.setIgrejaMae(outraMae);

        assertThatThrownBy(() -> service.desvincularCongregacao(mae.getId(), filha.getId()))
                .isInstanceOf(AcessoNegadoException.class);

        assertThat(filha.getIgrejaMae()).isSameAs(outraMae);
    }

    @Test
    void filhaSaiDaFamiliaPorContaPropria() {
        filha.setIgrejaMae(mae);

        service.sairDaFamilia(filha.getId());

        assertThat(filha.getIgrejaMae()).isNull();
        assertThat(filha.getVinculadoEm()).isNull();
        assertThat(filha.getVinculadoPor()).isNull();
    }

    @Test
    void semVinculoNaoTemComoSair() {
        assertThatThrownBy(() -> service.sairDaFamilia(filha.getId()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não faz parte");
    }

    // ---------- status ----------

    @Test
    void statusDaMaeListaAsCongregacoes() {
        when(familiaService.filhasDe(mae.getId())).thenReturn(List.of(filha));

        var status = service.status(mae.getId());

        assertThat(status.estado()).isEqualTo(EstadoVinculo.MAE);
        assertThat(status.congregacoes()).hasSize(1);
        assertThat(status.congregacoes().get(0).nome()).isEqualTo("Congregação A");
        assertThat(status.codigoVinculo()).isEqualTo("XK4P-2M7Q");
    }

    @Test
    void statusDaFilhaMostraAMaeENaoOCodigo() {
        filha.setIgrejaMae(mae);

        var status = service.status(filha.getId());

        assertThat(status.estado()).isEqualTo(EstadoVinculo.FILHA);
        assertThat(status.mae().nome()).isEqualTo("Igreja Sede");
        assertThat(status.codigoVinculo()).isNull();
        assertThat(status.congregacoes()).isEmpty();
    }

    // ---------- o gerador ----------

    @Test
    void codigoSaiNoFormatoExibidoESemSimbolosAmbiguos() {
        for (int i = 0; i < 200; i++) {
            String codigo = gerador.gerar();
            assertThat(codigo).matches("DOMUS-[2-9A-HJ-NP-Z]{6}");
            String sufixo = codigo.substring(6);
            assertThat(sufixo).doesNotContain("0").doesNotContain("1")
                    .doesNotContain("O").doesNotContain("I");
        }
    }

    @Test
    void normalizarAceitaOQueOUsuarioRealmenteDigita() {
        assertThat(gerador.normalizar("domus-k7m9p2")).isEqualTo("DOMUS-K7M9P2");
        assertThat(gerador.normalizar("DOMUSK7M9P2")).isEqualTo("DOMUS-K7M9P2");
        assertThat(gerador.normalizar("https://domusigreja.com.br/convite/congregacao?codigo=DOMUS-K7M9P2")).isEqualTo("DOMUS-K7M9P2");
        assertThat(gerador.normalizar("xk4p-2m7q")).isEqualTo("XK4P-2M7Q");
        assertThat(gerador.normalizar("XK4P2M7Q")).isEqualTo("XK4P-2M7Q");
        assertThat(gerador.normalizar(" xk4p 2m7q ")).isEqualTo("XK4P-2M7Q");
        assertThat(gerador.normalizar("XK4P-2M7")).isNull();      // curto demais
        assertThat(gerador.normalizar("XK4P-2M7O")).isNull();     // símbolo ambíguo
        assertThat(gerador.normalizar(null)).isNull();
    }
}
