package com.domus.api.modules.pagamento.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MercadoPagoAssinaturaValidatorTest {

    private final MercadoPagoAssinaturaValidator validator =
        new MercadoPagoAssinaturaValidator("segredo-webhook-teste");

    @Test
    void aceitaAssinaturaValida() {
        // Important 11b (revisão final de branch): o timestamp precisa ser ATUAL agora que
        // existe checagem de frescor — antes desta correção, um timestamp fixo qualquer
        // (ex.: de 2023) bastava porque não havia janela de validade.
        String dataId = "123456";
        String requestId = "req-abc";
        long timestamp = java.time.Instant.now().getEpochSecond();
        String header = headerValidoPara(dataId, requestId, timestamp);

        assertThat(validator.valida(header, dataId, requestId)).isTrue();
    }

    @Test
    void recusaAssinaturaComHashErrado() {
        long timestamp = java.time.Instant.now().getEpochSecond();
        String header = "ts=" + timestamp + ",v1=hash-forjado-invalido";

        assertThat(validator.valida(header, "123456", "req-abc")).isFalse();
    }

    @Test
    void recusaHeaderMalFormado() {
        assertThat(validator.valida("qualquer-coisa-sem-formato", "123456", "req-abc")).isFalse();
    }

    @Test
    void recusaAssinaturaComHashCorretoMasTimestampAntigo() {
        // Important 11b: hash matematicamente correto pra aquele timestamp, mas o
        // timestamp em si é de 1 hora atrás — replay de uma notificação capturada.
        String dataId = "123456";
        String requestId = "req-abc";
        long timestampAntigo = java.time.Instant.now().minusSeconds(3600).getEpochSecond();
        String header = headerValidoPara(dataId, requestId, timestampAntigo);

        assertThat(validator.valida(header, dataId, requestId)).isFalse();
    }

    private String headerValidoPara(String dataId, String requestId, long timestamp) {
        try {
            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec("segredo-webhook-teste".getBytes(), "HmacSHA256"));
            String hash = java.util.HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));
            return "ts=" + timestamp + ",v1=" + hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
