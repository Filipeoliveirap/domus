package com.domus.api.modules.igreja;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PlanoAssinaturaTest {

    @Test
    @DisplayName("Plano BASICO deve custar R$ 49,00 com limite de 50 pessoas, 0 congregacoes e todas as features liberadas")
    void planoBasicoValores() {
        PlanoAssinatura plano = PlanoAssinatura.BASICO;

        assertThat(plano.getValorMensal()).isEqualByComparingTo(new BigDecimal("49.00"));
        assertThat(plano.getLimitePessoas()).isEqualTo(50);
        assertThat(plano.getLimiteCongregacoes()).isEqualTo(0);
        assertThat(plano.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.CHECKOUT_EVENTO)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.FEED_SOCIAL)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.RELATORIOS_AVANCADOS)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.CAMPOS_PERSONALIZADOS)).isTrue();
    }

    @Test
    @DisplayName("Plano PRO deve custar R$ 129,00 com limite de 200 pessoas, 2 congregacoes e features liberadas")
    void planoProValores() {
        PlanoAssinatura plano = PlanoAssinatura.PRO;

        assertThat(plano.getValorMensal()).isEqualByComparingTo(new BigDecimal("129.00"));
        assertThat(plano.getLimitePessoas()).isEqualTo(200);
        assertThat(plano.getLimiteCongregacoes()).isEqualTo(2);
        assertThat(plano.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.CHECKOUT_EVENTO)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.FEED_SOCIAL)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.RELATORIOS_AVANCADOS)).isTrue();
        assertThat(plano.temFeature(FeaturePlan.CAMPOS_PERSONALIZADOS)).isTrue();
    }

    @Test
    @DisplayName("Plano PRO_PLUS deve custar R$ 249,00 com limite de 600 pessoas e 5 congregacoes")
    void planoProPlusValores() {
        PlanoAssinatura plano = PlanoAssinatura.PRO_PLUS;

        assertThat(plano.getValorMensal()).isEqualByComparingTo(new BigDecimal("249.00"));
        assertThat(plano.getLimitePessoas()).isEqualTo(600);
        assertThat(plano.getLimiteCongregacoes()).isEqualTo(5);
        assertThat(plano.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isTrue();
    }

    @Test
    @DisplayName("Plano ENTERPRISE deve custar R$ 449,00 com limites ilimitados")
    void planoEnterpriseValores() {
        PlanoAssinatura plano = PlanoAssinatura.ENTERPRISE;

        assertThat(plano.getValorMensal()).isEqualByComparingTo(new BigDecimal("449.00"));
        assertThat(plano.getLimitePessoas()).isEqualTo(99999);
        assertThat(plano.getLimiteCongregacoes()).isEqualTo(9999);
        assertThat(plano.temFeature(FeaturePlan.CONTAS_A_PAGAR)).isTrue();
    }
}
