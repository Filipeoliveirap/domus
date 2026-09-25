package com.domus.api.config;

import com.domus.api.modules.igreja.FeaturePlan;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeaturePlanInterceptorTest {

    @InjectMocks
    private FeaturePlanInterceptor interceptor;

    @Mock
    private HttpServletResponse response;

    @Mock
    private PrintWriter printWriter;

    @Test
    void deveBloquearAcessoQuandoPlanoBasicoTentarAcessarFeedSocial() throws Exception {
        when(response.getWriter()).thenReturn(printWriter);

        Igreja igreja = new Igreja();
        igreja.setPlano(PlanoAssinatura.BASICO);

        boolean liberado = interceptor.validarFeature(response, igreja, FeaturePlan.FEED_SOCIAL);

        verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(liberado).isFalse();
    }

    @Test
    void devePermitirAcessoQuandoPlanoProAcessarFeedSocial() throws Exception {
        Igreja igreja = new Igreja();
        igreja.setPlano(PlanoAssinatura.PRO);

        boolean liberado = interceptor.validarFeature(response, igreja, FeaturePlan.FEED_SOCIAL);

        verify(response, never()).setStatus(anyInt());
        assertThat(liberado).isTrue();
    }
}
