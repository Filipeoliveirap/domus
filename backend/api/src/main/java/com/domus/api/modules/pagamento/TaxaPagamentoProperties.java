package com.domus.api.modules.pagamento;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tabela de taxa PADRÃO do Mercado Pago, em pontos percentuais (4.49 = 4,49%). Usada pelo
 * gross-up quando a igreja não informou taxa negociada própria em ContaPagamentoIgreja.
 * Se o MP mudar a tabela pública, ajustar aqui e redeployar.
 */
@ConfigurationProperties(prefix = "pagamento.taxa")
public record TaxaPagamentoProperties(
        BigDecimal pixPercent,
        BigDecimal cartaoAvistaPercent,
        BigDecimal cartaoParcelaAdicionalPercent
) {}
