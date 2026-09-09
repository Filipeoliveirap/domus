package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.domus.api.modules.pagamento.conta.ContaPagamentoIgreja;
import com.domus.api.modules.pagamento.conta.ContaPagamentoIgrejaRepository;
import com.domus.api.shared.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CalculadoraTaxaPagamentoTest {

    UUID igrejaId = UUID.randomUUID();
    ContaPagamentoIgrejaRepository contaRepository;
    CalculadoraTaxaPagamento calculadora;

    @BeforeEach
    void setup() {
        contaRepository = mock(ContaPagamentoIgrejaRepository.class);
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.empty()); // sem override
        var props = new TaxaPagamentoProperties(
            new BigDecimal("0.99"), new BigDecimal("4.49"), new BigDecimal("2.50"));
        calculadora = new CalculadoraTaxaPagamento(contaRepository, props);
    }

    @Test
    void pixUsaTaxaPixEArredondaParaCima() {
        // 100 / (1 - 0.0099) = 100 / 0.9901 = 101.0000 -> 101.00
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.PIX, 1);
        assertThat(cobrar).isEqualByComparingTo("101.00");
    }

    @Test
    void cartaoAvistaUsaTaxaAvista() {
        // 100 / (1 - 0.0449) = 100 / 0.9551 = 104.7011... -> CEILING -> 104.71
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 1);
        assertThat(cobrar).isEqualByComparingTo("104.71");
    }

    @Test
    void cartaoParceladoSomaAdicionalPorParcelaExtra() {
        // 3x: taxa = 4.49 + 2*2.50 = 9.49%. 100 / 0.9051 = 110.4850... -> 110.49
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 3);
        assertThat(cobrar).isEqualByComparingTo("110.49");
    }

    @Test
    void taxaNegociadaDaIgrejaTemPrecedenciaSobreConfig() {
        var conta = mock(ContaPagamentoIgreja.class);
        when(conta.getTaxaCartaoAvistaPercent()).thenReturn(new BigDecimal("3.00"));
        when(conta.getTaxaPixPercent()).thenReturn(null);
        when(conta.getTaxaCartaoParcelaAdicionalPercent()).thenReturn(null);
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        // 100 / (1 - 0.03) = 103.0927... -> 103.10
        BigDecimal cobrar = calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 1);
        assertThat(cobrar).isEqualByComparingTo("103.10");
    }

    @Test
    void overrideParcialCaiNoDefaultParaOCampoNaoInformado() {
        var conta = mock(ContaPagamentoIgreja.class);
        when(conta.getTaxaPixPercent()).thenReturn(new BigDecimal("0.50"));
        when(conta.getTaxaCartaoAvistaPercent()).thenReturn(null); // cai no default 4.49
        when(conta.getTaxaCartaoParcelaAdicionalPercent()).thenReturn(null);
        when(contaRepository.findByIgrejaId(igrejaId)).thenReturn(Optional.of(conta));
        assertThat(calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.PIX, 1))
            .isEqualByComparingTo("100.51"); // 100 / 0.995 = 100.5025 -> 100.51
        assertThat(calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 1))
            .isEqualByComparingTo("104.71");
    }

    @Test
    void pixComMaisDeUmaParcelaEhRecusado() {
        assertThatThrownBy(() -> calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.PIX, 2))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Pix");
    }

    @Test
    void parcelasMenorQueUmEhRecusado() {
        assertThatThrownBy(() -> calculadora.valorACobrar(igrejaId, new BigDecimal("100.00"), MeioPagamento.CARTAO, 0))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void valorAlvoZeroOuNegativoEhRecusado() {
        assertThatThrownBy(() -> calculadora.valorACobrar(igrejaId, BigDecimal.ZERO, MeioPagamento.PIX, 1))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void taxaEmReaisEhADiferenca() {
        assertThat(calculadora.taxaEmReais(new BigDecimal("100.00"), new BigDecimal("104.71")))
            .isEqualByComparingTo("4.71");
    }
}
