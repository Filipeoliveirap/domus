package com.domus.api.modules.pagamento.cobranca.DTOs;

import com.domus.api.modules.pagamento.MeioPagamento;
import java.math.BigDecimal;
import java.util.List;

/**
 * Opções que a tela de escolha de método do checkout renderiza. `valorEvento` é o alvo
 * (o que a igreja quer receber); cada opção traz o total já com a taxa do MP embutida
 * (gross-up) pra aquele meio/parcela.
 */
public record OpcoesPagamentoResponse(BigDecimal valorEvento, List<OpcaoPagamento> opcoes) {

    public record OpcaoPagamento(MeioPagamento meio, int parcelas,
                                 BigDecimal valorTotal, BigDecimal valorParcela, BigDecimal taxa) {}
}
