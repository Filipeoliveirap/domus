package com.domus.api.config;

import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Scrub de headers sensíveis antes do envio ao Sentry (LGPD). */
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
