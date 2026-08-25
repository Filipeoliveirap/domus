package com.domus.api.modules.pagamento.webhook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida a assinatura do webhook do Mercado Pago (header x-signature) — sem isso,
 * qualquer requisição não autenticada poderia forjar "pagamento confirmado".
 * Formato do header: "ts=<timestamp>,v1=<hash>". Manifest assinado:
 * "id:<data.id>;request-id:<x-request-id>;ts:<timestamp>;".
 */
@Component
public class MercadoPagoAssinaturaValidator {

    /**
     * Important 11b (revisão final de branch): janela de frescor — sem isso, uma
     * notificação capturada (ex.: por um proxy comprometido, ou um log vazado) continuaria
     * válida pra sempre e poderia ser reenviada (replay) a qualquer momento.
     */
    private static final long JANELA_FRESCOR_SEGUNDOS = 5 * 60;

    private final String segredoWebhook;

    public MercadoPagoAssinaturaValidator(@Value("${app.pagamento.mercadopago.webhook-secret}") String segredoWebhook) {
        this.segredoWebhook = segredoWebhook;
    }

    public boolean valida(String headerXSignature, String dataId, String requestId) {
        try {
            Map<String, String> partes = parseHeader(headerXSignature);
            String timestamp = partes.get("ts");
            String hashRecebido = partes.get("v1");
            if (timestamp == null || hashRecebido == null) return false;

            if (!dentroDaJanelaDeFrescor(timestamp)) return false;

            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredoWebhook.getBytes(), "HmacSHA256"));
            String hashCalculado = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));

            // Important 11a: MessageDigest.isEqual é tempo-constante — .equals() de String
            // sai mais cedo no primeiro byte que diverge, o que dá a um atacante um jeito
            // de medir (por tempo de resposta) o quanto ele já acertou do hash.
            return MessageDigest.isEqual(
                hashCalculado.getBytes(StandardCharsets.UTF_8),
                hashRecebido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    /** {@code ts} do Mercado Pago é epoch em segundos. Fora da janela = possível replay. */
    private boolean dentroDaJanelaDeFrescor(String timestamp) {
        try {
            long ts = Long.parseLong(timestamp.trim());
            long agora = Instant.now().getEpochSecond();
            return Math.abs(agora - ts) <= JANELA_FRESCOR_SEGUNDOS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Map<String, String> parseHeader(String header) {
        Map<String, String> partes = new HashMap<>();
        for (String parte : header.split(",")) {
            String[] chaveValor = parte.split("=", 2);
            if (chaveValor.length == 2) partes.put(chaveValor[0].trim(), chaveValor[1].trim());
        }
        return partes;
    }
}
