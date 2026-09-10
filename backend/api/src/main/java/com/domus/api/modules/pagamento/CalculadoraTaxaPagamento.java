package com.domus.api.modules.pagamento;

import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Gross-up: dado quanto a igreja quer RECEBER (valorAlvo), calcula quanto COBRAR do
 * pagador pra que, depois de o Mercado Pago descontar a taxa, sobre exatamente o alvo.
 *
 * <p>valorACobrar = valorAlvo / (1 - taxaEfetiva). Divisão, não multiplicação: o MP cobra
 * a % sobre o valor cobrado, não sobre o alvo. Arredonda pra CIMA — a igreja nunca recebe
 * menos que o alvo.</p>
 *
 * <p>taxaEfetiva vem da taxa negociada da igreja (ContaPagamentoIgreja), campo a campo,
 * caindo no default do back (TaxaPagamentoProperties) pra cada campo não informado.</p>
 */
@Service
public class CalculadoraTaxaPagamento {

    private static final int MAX_PARCELAS = 12;

    private final ContaPagamentoIgrejaRepository contaRepository;
    private final TaxaPagamentoProperties padrao;

    public CalculadoraTaxaPagamento(ContaPagamentoIgrejaRepository contaRepository,
                                     TaxaPagamentoProperties padrao) {
        this.contaRepository = contaRepository;
        this.padrao = padrao;
    }

    public BigDecimal valorACobrar(UUID igrejaId, BigDecimal valorAlvo, MeioPagamento meio, int parcelas) {
        if (valorAlvo == null || valorAlvo.signum() <= 0) {
            throw new BusinessException("VALOR_ALVO_INVALIDO", "O valor da inscrição deve ser maior que zero.");
        }
        if (parcelas < 1 || parcelas > MAX_PARCELAS) {
            throw new BusinessException("PARCELAS_INVALIDAS", "Número de parcelas inválido.");
        }
        if (meio == MeioPagamento.PIX && parcelas > 1) {
            throw new BusinessException("PIX_NAO_PARCELA", "Pix não pode ser parcelado.");
        }

        BigDecimal taxaPercent = taxaEfetivaPercent(igrejaId, meio, parcelas);
        BigDecimal fator = BigDecimal.ONE.subtract(taxaPercent.movePointLeft(2)); // (1 - taxa/100)
        return valorAlvo.divide(fator, MathContext.DECIMAL128).setScale(2, RoundingMode.CEILING);
    }

    public BigDecimal taxaEmReais(BigDecimal valorAlvo, BigDecimal valorACobrar) {
        return valorACobrar.subtract(valorAlvo);
    }

    private BigDecimal taxaEfetivaPercent(UUID igrejaId, MeioPagamento meio, int parcelas) {
        ContaPagamentoIgreja conta = contaRepository.findByIgrejaId(igrejaId).orElse(null);
        if (meio == MeioPagamento.PIX) {
            return valorOu(conta == null ? null : conta.getTaxaPixPercent(), padrao.pixPercent());
        }
        BigDecimal avista = valorOu(conta == null ? null : conta.getTaxaCartaoAvistaPercent(),
            padrao.cartaoAvistaPercent());
        BigDecimal adicional = valorOu(conta == null ? null : conta.getTaxaCartaoParcelaAdicionalPercent(),
            padrao.cartaoParcelaAdicionalPercent());
        return avista.add(adicional.multiply(BigDecimal.valueOf(parcelas - 1L)));
    }

    private static BigDecimal valorOu(BigDecimal override, BigDecimal fallback) {
        return override != null ? override : fallback;
    }
}
