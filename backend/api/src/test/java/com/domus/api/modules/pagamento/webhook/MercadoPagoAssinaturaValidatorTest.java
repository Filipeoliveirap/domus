package com.domus.api.modules.pagamento.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MercadoPagoAssinaturaValidatorTest {

    private final MercadoPagoAssinaturaValidator validator =
        new MercadoPagoAssinaturaValidator("segredo-webhook-teste");

    @Test
    void aceitaAssinaturaValida() throws Exception {
        String dataId = "123456";
        String requestId = "req-abc";
        long timestamp = 1700000000L;
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("segredo-webhook-teste".getBytes(), "HmacSHA256"));
        String hashEsperado = java.util.HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));
        String header = "ts=" + timestamp + ",v1=" + hashEsperado;

        assertThat(validator.valida(header, dataId, requestId)).isTrue();
    }

    @Test
    void recusaAssinaturaComHashErrado() {
        String header = "ts=1700000000,v1=hash-forjado-invalido";

        assertThat(validator.valida(header, "123456", "req-abc")).isFalse();
    }

    @Test
    void recusaHeaderMalFormado() {
        assertThat(validator.valida("qualquer-coisa-sem-formato", "123456", "req-abc")).isFalse();
    }
}
