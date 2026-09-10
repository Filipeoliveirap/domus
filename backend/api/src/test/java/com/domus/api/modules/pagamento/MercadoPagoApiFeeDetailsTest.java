package com.domus.api.modules.pagamento;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MercadoPagoApiFeeDetailsTest {

    ObjectMapper mapper = new ObjectMapper();

    @Test
    void pagamentoAprovadoExtraiBrutoTaxaLiquido() throws Exception {
        String json = """
            {
              "external_reference": "abc-123",
              "status": "approved",
              "transaction_amount": 110.49,
              "fee_details": [
                { "type": "mercadopago_fee", "amount": 10.49 }
              ],
              "transaction_details": { "net_received_amount": 100.00 }
            }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);

        assertThat(info.status()).isEqualTo("approved");
        assertThat(info.valorBruto()).isEqualByComparingTo("110.49");
        assertThat(info.taxaMercadoPago()).isEqualByComparingTo("10.49");
        assertThat(info.valorLiquido()).isEqualByComparingTo("100.00");
    }

    @Test
    void pagamentoPendenteSemFeeDetailsDeixaCamposNulos() throws Exception {
        String json = """
            { "external_reference": "abc-123", "status": "pending" }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);

        assertThat(info.status()).isEqualTo("pending");
        assertThat(info.valorBruto()).isNull();
        assertThat(info.taxaMercadoPago()).isNull();
        assertThat(info.valorLiquido()).isNull();
    }

    @Test
    void pendenteComTransactionAmountMasSemTransactionDetails_deixaTresCamposNulos() throws Exception {
        String json = """
            { "external_reference": "abc-123", "status": "pending", "transaction_amount": 50.00 }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);

        assertThat(info.status()).isEqualTo("pending");
        assertThat(info.valorBruto()).isNull();
        assertThat(info.taxaMercadoPago()).isNull();
        assertThat(info.valorLiquido()).isNull();
    }

    @Test
    void taxaEhBrutoMenosLiquidoNaoSomaDeFeeDetails() throws Exception {
        String json = """
            {
              "external_reference": "x", "status": "approved",
              "transaction_amount": 100.00,
              "fee_details": [
                { "type": "mercadopago_fee", "amount": 3.00 },
                { "type": "mercadopago_fee", "amount": 1.50 },
                { "type": "financing_fee", "amount": 9.99 }
              ],
              "transaction_details": { "net_received_amount": 95.50 }
            }
            """;
        var resp = mapper.readValue(json, MercadoPagoApi.RespostaPagamentoMercadoPago.class);
        var info = MercadoPagoApi.InformacoesPagamento.de(resp);
        // Decisão fixada no Step 2 / pelo controller: taxaMercadoPago = bruto - liquido
        // (transaction_amount - net_received_amount), NÃO a soma de fee_details.
        // fee_details é parseado só para log/diagnóstico.
        assertThat(info.taxaMercadoPago()).isEqualByComparingTo("4.50"); // 100.00 - 95.50
        assertThat(info.valorLiquido()).isEqualByComparingTo("95.50");
    }
}
