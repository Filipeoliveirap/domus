package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.StatusAssinatura;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssinaturaWebhookServiceTest {

    @Mock
    private IgrejaRepository igrejaRepository;

    @Mock
    private MercadoPagoSubscriptionService mpSubscriptionService;

    @InjectMocks
    private AssinaturaWebhookService webhookService;

    @Test
    @DisplayName("deve consultar status na API do Mercado Pago quando status vier nulo no webhook")
    void deveConsultarStatusNaApiDoMpQuandoStatusVierNulo() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = Igreja.builder()
            .id(matrizId)
            .statusAssinatura(StatusAssinatura.PAUSADA)
            .mpPreapprovalId("preapp_123")
            .build();

        when(mpSubscriptionService.consultarStatus("preapp_123")).thenReturn("authorized");
        when(igrejaRepository.findByMpPreapprovalId("preapp_123")).thenReturn(Optional.of(matriz));
        when(igrejaRepository.findByIgrejaMaeId(matrizId)).thenReturn(List.of());

        webhookService.processarWebhookAssinatura("preapp_123", null);

        assertThat(matriz.getStatusAssinatura()).isEqualTo(StatusAssinatura.ATIVA);
        verify(mpSubscriptionService).consultarStatus("preapp_123");
        verify(igrejaRepository).save(matriz);
    }

    @Test
    @DisplayName("deve ignorar webhook se status for nulo e consulta ao Mercado Pago nao retornar status")
    void deveIgnorarQuandoStatusContinuarNuloAposConsulta() {
        when(mpSubscriptionService.consultarStatus("preapp_123")).thenReturn(null);

        webhookService.processarWebhookAssinatura("preapp_123", null);

        verify(mpSubscriptionService).consultarStatus("preapp_123");
        verifyNoInteractions(igrejaRepository);
    }

    @Test
    @DisplayName("deve cascatear status PAUSADA da matriz para congregacoes filhas")
    void deveCascatearStatusPausadaParaFilhas() {
        UUID matrizId = UUID.randomUUID();
        Igreja matriz = Igreja.builder()
            .id(matrizId)
            .statusAssinatura(StatusAssinatura.ATIVA)
            .mpPreapprovalId("preapp_123")
            .build();

        Igreja filha1 = Igreja.builder().id(UUID.randomUUID()).statusAssinatura(StatusAssinatura.ATIVA).build();
        Igreja filha2 = Igreja.builder().id(UUID.randomUUID()).statusAssinatura(StatusAssinatura.ATIVA).build();

        when(igrejaRepository.findByMpPreapprovalId("preapp_123")).thenReturn(Optional.of(matriz));
        when(igrejaRepository.findByIgrejaMaeId(matrizId)).thenReturn(List.of(filha1, filha2));

        webhookService.processarWebhookAssinatura("preapp_123", "paused");

        assertThat(matriz.getStatusAssinatura()).isEqualTo(StatusAssinatura.PAUSADA);
        assertThat(filha1.getStatusAssinatura()).isEqualTo(StatusAssinatura.PAUSADA);
        assertThat(filha2.getStatusAssinatura()).isEqualTo(StatusAssinatura.PAUSADA);

        verify(igrejaRepository).save(matriz);
        verify(igrejaRepository).save(filha1);
        verify(igrejaRepository).save(filha2);
    }
}
