package com.domus.api.modules.pagamento;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mínimos que o Mercado Pago (Brasil) impõe pra pagamento com cartão: um total mínimo
 * (~R$ 1,00) e um valor mínimo POR PARCELA (~R$ 5,00). Servem pra não oferecer no checkout
 * (nem no form de evento) uma faixa de parcela que o MP recusaria — o que travaria o Brick,
 * que fica preso ao número de parcelas escolhido ({@code minInstallments == maxInstallments}).
 * Se o MP mudar os limites, ajustar aqui e redeployar.
 */
@ConfigurationProperties(prefix = "pagamento.limite")
public record LimitesPagamentoProperties(
        BigDecimal cartaoValorMinimo,
        BigDecimal parcelaValorMinimo
) {}
