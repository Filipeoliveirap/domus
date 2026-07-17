package com.domus.api.config;

import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Endurecimento do Sentry para não vazar dado sensível (LGPD). O starter registra
 * automaticamente beans do tipo {@link SentryOptions.BeforeSendCallback}, então este
 * callback roda em todo evento antes de ser enviado, limpando o que for sensível.
 */
@Configuration
public class SentryConfig {

    @Bean
    public SentryOptions.BeforeSendCallback beforeSendCallback() {
        return (event, hint) -> {
            if (event.getRequest() != null && event.getRequest().getHeaders() != null) {
                var headers = event.getRequest().getHeaders();
                // Credenciais/sessão nunca vão para um serviço de terceiro.
                headers.remove("Authorization");
                headers.remove("Cookie");
                headers.remove("Set-Cookie");
            }
            return event;
        };
    }
}
