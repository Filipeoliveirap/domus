package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.igreja.exception.PlanoLimiteExcedidoException;
import com.domus.api.modules.pagamento.assinatura.dto.ResumoAssinaturaResponse;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.usuario.UsuarioRepository;
import com.domus.api.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssinaturaServiceTest {

    @Mock
    private IgrejaRepository igrejaRepository;

    @Mock
    private PessoaRepository pessoaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private MercadoPagoSubscriptionService mpSubscriptionService;

    @Mock
    private com.domus.api.modules.usuario.PrincipalCacheService principalCacheService;

    @InjectMocks
    private AssinaturaService assinaturaService;

    private UUID igrejaId;
    private Igreja igreja;

    @BeforeEach
    void setUp() {
        igrejaId = UUID.randomUUID();
        igreja = Igreja.builder()
            .id(igrejaId)
            .nome("Igreja Central")
            .plano(PlanoAssinatura.PRO)
            .statusAssinatura(StatusAssinatura.TRIAL)
            .trialExpiraEm(LocalDateTime.now().plusDays(10))
            .mpPreapprovalId("preapp_123")
            .build();
    }

    @Test
    @DisplayName("obterResumo deve retornar metricas calculadas corretamente")
    void obterResumoCalculaMetricas() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(100L);
        when(igrejaRepository.countByIgrejaMaeId(igrejaId)).thenReturn(1L);

        ResumoAssinaturaResponse resumo = assinaturaService.obterResumo(igrejaId);

        assertThat(resumo.plano()).isEqualTo(PlanoAssinatura.PRO);
        assertThat(resumo.valorMensal()).isEqualByComparingTo(new BigDecimal("129.00"));
        assertThat(resumo.totalPessoas()).isEqualTo(100L);
        assertThat(resumo.limitePessoas()).isEqualTo(200);
        assertThat(resumo.percentualPessoas()).isEqualTo(50);
        assertThat(resumo.totalCongregacoes()).isEqualTo(1L);
        assertThat(resumo.limiteCongregacoes()).isEqualTo(2);
        assertThat(resumo.percentualCongregacoes()).isEqualTo(50);
        assertThat(resumo.diasRestantesTrial()).isGreaterThanOrEqualTo(9L);
    }

    @Test
    @DisplayName("alterarPlano deve efetuar upgrade com sucesso quando dentro dos limites")
    void alterarPlanoPermiteUpgrade() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(150L);
        when(igrejaRepository.countByIgrejaMaeId(igrejaId)).thenReturn(2L);

        assinaturaService.alterarPlano(igrejaId, PlanoAssinatura.PRO_PLUS);

        verify(mpSubscriptionService).alterarPlano("preapp_123", PlanoAssinatura.PRO_PLUS);
        verify(igrejaRepository).save(igreja);
        assertThat(igreja.getPlano()).isEqualTo(PlanoAssinatura.PRO_PLUS);
    }

    @Test
    @DisplayName("alterarPlano deve bloquear downgrade se total de pessoas exceder o limite do novo plano")
    void alterarPlanoBloqueiaDowngradePorExcessoDePessoas() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(80L); // basico permite 50

        assertThatThrownBy(() -> assinaturaService.alterarPlano(igrejaId, PlanoAssinatura.BASICO))
            .isInstanceOf(PlanoLimiteExcedidoException.class)
            .hasMessageContaining("a igreja possui 80 pessoas ativas (limite do plano: 50)");

        verify(mpSubscriptionService, never()).alterarPlano(any(), any());
        verify(igrejaRepository, never()).save(any());
    }

    @Test
    @DisplayName("alterarPlano deve bloquear downgrade se total de congregacoes filhas exceder o novo plano")
    void alterarPlanoBloqueiaDowngradePorExcessoDeCongregacoes() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(30L);
        when(igrejaRepository.countByIgrejaMaeId(igrejaId)).thenReturn(2L); // basico permite 0

        assertThatThrownBy(() -> assinaturaService.alterarPlano(igrejaId, PlanoAssinatura.BASICO))
            .isInstanceOf(PlanoLimiteExcedidoException.class)
            .hasMessageContaining("a igreja possui 2 congregações filhas (limite do plano: 0)");

        verify(mpSubscriptionService, never()).alterarPlano(any(), any());
    }

    @Test
    @DisplayName("cancelarAssinatura deve cancelar no MP e marcar status como CANCELADA")
    void cancelarAssinaturaSucesso() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        assinaturaService.cancelarAssinatura(igrejaId);

        verify(mpSubscriptionService).cancelarAssinatura("preapp_123");
        verify(igrejaRepository).save(igreja);
        assertThat(igreja.getStatusAssinatura()).isEqualTo(StatusAssinatura.CANCELADA);
    }

    @Test
    @DisplayName("obterResumo para igreja filial deve herdar plano da matriz e trazer nome da matriz")
    void obterResumoParaFilialHerdaPlanoDaMatriz() {
        Igreja matriz = Igreja.builder()
                .id(UUID.randomUUID())
                .nome("Igreja Sede")
                .plano(PlanoAssinatura.PRO_PLUS)
                .statusAssinatura(StatusAssinatura.ATIVA)
                .build();
        igreja.setIgrejaMae(matriz);

        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(40L);
        when(igrejaRepository.countByIgrejaMaeId(matriz.getId())).thenReturn(3L);

        ResumoAssinaturaResponse resumo = assinaturaService.obterResumo(igrejaId);

        assertThat(resumo.ehMatriz()).isFalse();
        assertThat(resumo.nomeMatriz()).isEqualTo("Igreja Sede");
        assertThat(resumo.plano()).isEqualTo(PlanoAssinatura.PRO_PLUS);
        assertThat(resumo.limitePessoas()).isEqualTo(PlanoAssinatura.PRO_PLUS.getLimitePessoas());
        assertThat(resumo.totalCongregacoes()).isEqualTo(3L);
        assertThat(resumo.limiteCongregacoes()).isEqualTo(PlanoAssinatura.PRO_PLUS.getLimiteCongregacoes());
    }

    @Test
    @DisplayName("alterarPlano deve ser bloqueado quando igreja for filial")
    void alterarPlanoBloqueadoParaFilial() {
        Igreja matriz = Igreja.builder().id(UUID.randomUUID()).nome("Sede").build();
        igreja.setIgrejaMae(matriz);
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        assertThatThrownBy(() -> assinaturaService.alterarPlano(igrejaId, PlanoAssinatura.ENTERPRISE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gestão da assinatura é de responsabilidade da matriz");
    }

    @Test
    @DisplayName("cancelarAssinatura deve ser bloqueado quando igreja for filial")
    void cancelarAssinaturaBloqueadoParaFilial() {
        Igreja matriz = Igreja.builder().id(UUID.randomUUID()).nome("Sede").build();
        igreja.setIgrejaMae(matriz);
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));

        assertThatThrownBy(() -> assinaturaService.cancelarAssinatura(igrejaId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("gestão da assinatura é de responsabilidade da matriz");
    }

    @Test
    @DisplayName("contratarPlano ativa assinatura imediatamente e remove motivo de suspensao")
    void contratarPlanoAtivaAssinatura() {
        igreja.setStatusAssinatura(StatusAssinatura.PAUSADA);
        igreja.setMotivoSuspensao("DESVINCULADA_MATRIZ");
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(20L);
        when(igrejaRepository.countByIgrejaMaeId(igrejaId)).thenReturn(0L);

        assinaturaService.contratarPlano(igrejaId, PlanoAssinatura.PRO, null);

        assertThat(igreja.getPlano()).isEqualTo(PlanoAssinatura.PRO);
        assertThat(igreja.getStatusAssinatura()).isEqualTo(StatusAssinatura.ATIVA);
        assertThat(igreja.getMotivoSuspensao()).isNull();
        verify(igrejaRepository).save(igreja);
    }

    @Test
    @DisplayName("contratarPlano deve bloquear se total de pessoas exceder o limite do plano")
    void contratarPlanoBloqueiaExcessoDePessoas() {
        when(igrejaRepository.findById(igrejaId)).thenReturn(Optional.of(igreja));
        when(pessoaRepository.countByIgrejaIdAndDeletedAtIsNull(igrejaId)).thenReturn(100L); // basico permite 50

        assertThatThrownBy(() -> assinaturaService.contratarPlano(igrejaId, PlanoAssinatura.BASICO, null))
                .isInstanceOf(PlanoLimiteExcedidoException.class)
                .hasMessageContaining("a igreja possui 100 pessoas ativas (limite do plano: 50)");
    }
}
