package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
class TaxaPagamentoPropertiesTest implements PostgresTestContainerSupport {

    @Autowired TaxaPagamentoProperties props;

    @Test
    void carregaValoresPadraoDoApplicationProperties() {
        assertThat(props.pixPercent()).isEqualByComparingTo(new BigDecimal("0.99"));
        assertThat(props.cartaoAvistaPercent()).isEqualByComparingTo(new BigDecimal("4.49"));
        assertThat(props.cartaoParcelaAdicionalPercent()).isEqualByComparingTo(new BigDecimal("2.50"));
    }
}
