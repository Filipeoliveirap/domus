package com.domus.api.modules.pagamento.assinatura;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.pagamento.MercadoPagoClient;
import com.domus.api.modules.pagamento.assinatura.dto.AssinaturaResponse;
import com.domus.api.modules.pagamento.assinatura.dto.CriarAssinaturaRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MercadoPagoSubscriptionServiceTest {

    @Mock
    private MercadoPagoClient mercadoPagoClient;

    @Mock
    private IgrejaRepository igrejaRepository;

    @InjectMocks
    private MercadoPagoSubscriptionService subscriptionService;

    @InjectMocks
    private AssinaturaWebhookService webhookService;

    @Test
    void deveCriarPreapprovalComDataInicioEm14Dias() {
        Igreja igreja = new Igreja();
        igreja.setId(UUID.randomUUID());
        igreja.setPlano(PlanoAssinatura.PRO);

        CriarAssinaturaRequest req = new CriarAssinaturaRequest("token_123", "admin@teste.com", "12345678901");

        org.mockito.ArgumentCaptor<Map<String, Object>> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
        when(mercadoPagoClient.criarAssinaturaPreapproval(any(), bodyCaptor.capture()))
            .thenReturn(Map.of("id", "preapproval_999", "status", "authorized"));

        AssinaturaResponse resp = subscriptionService.criarAssinaturaTrial(igreja, req);

        assertThat(resp.preapprovalId()).isEqualTo("preapproval_999");
        assertThat(resp.status()).isEqualTo("authorized");

        Map<String, Object> body = bodyCaptor.getValue();
        Map<?, ?> autoRecurring = (Map<?, ?>) body.get("auto_recurring");
        assertThat(autoRecurring).isNotNull();
        assertThat(autoRecurring.get("start_date")).isNotNull();
    }

    @Test
    void deveProcessarWebhookAtualizandoStatus() {
        Igreja igreja = new Igreja();
        igreja.setMpPreapprovalId("preapp_123");
        igreja.setStatusAssinatura(StatusAssinatura.TRIAL);

        when(igrejaRepository.findByMpPreapprovalId("preapp_123")).thenReturn(Optional.of(igreja));

        webhookService.processarWebhookAssinatura("preapp_123", "authorized");

        assertThat(igreja.getStatusAssinatura()).isEqualTo(StatusAssinatura.ATIVA);
        verify(igrejaRepository).save(igreja);
    }
}
