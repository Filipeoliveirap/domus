package com.domus.api.modules.igreja;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PlanoAssinaturaTest {

    @Test
    void deveVerificarFeaturesDoPlanoBasicoEPro() {
        assertThat(PlanoAssinatura.BASICO.temFeature(FeaturePlan.FEED_SOCIAL)).isFalse();
        assertThat(PlanoAssinatura.BASICO.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isFalse();
        assertThat(PlanoAssinatura.BASICO.temFeature(FeaturePlan.CHECKOUT_EVENTO)).isFalse();

        assertThat(PlanoAssinatura.PRO.temFeature(FeaturePlan.FEED_SOCIAL)).isTrue();
        assertThat(PlanoAssinatura.PRO.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isTrue();
        assertThat(PlanoAssinatura.PRO.temFeature(FeaturePlan.CHECKOUT_EVENTO)).isTrue();
    }

    @Test
    void deveVerificarLimitesDePessoasECongregacoes() {
        assertThat(PlanoAssinatura.BASICO.getLimitePessoas()).isEqualTo(60);
        assertThat(PlanoAssinatura.BASICO.getLimiteCongregacoes()).isEqualTo(0);

        assertThat(PlanoAssinatura.PRO.getLimitePessoas()).isEqualTo(300);
        assertThat(PlanoAssinatura.PRO.getLimiteCongregacoes()).isEqualTo(3);

        assertThat(PlanoAssinatura.PRO_PLUS.getLimitePessoas()).isEqualTo(800);
        assertThat(PlanoAssinatura.PRO_PLUS.getLimiteCongregacoes()).isEqualTo(5);

        assertThat(PlanoAssinatura.ENTERPRISE.getLimitePessoas()).isEqualTo(99999);
        assertThat(PlanoAssinatura.ENTERPRISE.getLimiteCongregacoes()).isEqualTo(9999);
    }
}
