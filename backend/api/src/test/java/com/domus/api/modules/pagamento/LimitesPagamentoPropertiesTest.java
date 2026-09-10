package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

@SpringBootTest
class LimitesPagamentoPropertiesTest implements PostgresTestContainerSupport {

    @Autowired LimitesPagamentoProperties props;

    @Test
    void carregaValoresPadraoDoApplicationProperties() {
        assertThat(props.cartaoValorMinimo()).isEqualByComparingTo(new BigDecimal("1.00"));
        assertThat(props.parcelaValorMinimo()).isEqualByComparingTo(new BigDecimal("5.00"));
    }
}
