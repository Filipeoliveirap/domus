package com.domus.api.modules.pagamento.webhook;

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

            String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + timestamp + ";";

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredoWebhook.getBytes(), "HmacSHA256"));
            String hashCalculado = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes()));

            return hashCalculado.equals(hashRecebido);
        } catch (Exception e) {
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
