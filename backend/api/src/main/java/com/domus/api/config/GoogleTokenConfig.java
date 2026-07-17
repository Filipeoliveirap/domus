package com.domus.api.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * Configura o verificador de ID tokens do Google.
 *
 * O verificador confere a assinatura do token contra as chaves públicas do Google
 * (baixadas e cacheadas por ele), a validade, o emissor e o audience (aud) — que precisa
 * ser o NOSSO Client ID. Assim garantimos que o token foi emitido pelo Google PARA o Domus.
 */
@Configuration
public class GoogleTokenConfig {

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(
            @Value("${google.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }
}
