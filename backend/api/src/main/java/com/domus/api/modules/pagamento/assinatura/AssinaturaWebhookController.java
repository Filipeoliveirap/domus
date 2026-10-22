package com.domus.api.modules.pagamento.assinatura;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/mercadopago/assinatura")
public class AssinaturaWebhookController {

    private final AssinaturaWebhookService webhookService;

    public AssinaturaWebhookController(AssinaturaWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping
    public ResponseEntity<Void> receberWebhook(@RequestBody Map<String, Object> payload) {
        String type = (String) payload.get("type");
        String action = (String) payload.get("action");

        boolean ehAssinatura = "subscription_preapproval".equals(type)
                || (action != null && action.startsWith("subscription_preapproval"));

        if (ehAssinatura) {
            Map<?, ?> data = (Map<?, ?>) payload.get("data");
            if (data != null) {
                String id = (String) data.get("id");
                String status = (String) data.get("status");
                if (id != null) {
                    webhookService.processarWebhookAssinatura(id, status);
                }
            }
        }
        return ResponseEntity.ok().build();
    }
}
