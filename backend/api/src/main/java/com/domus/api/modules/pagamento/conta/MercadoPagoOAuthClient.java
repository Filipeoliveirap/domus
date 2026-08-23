package com.domus.api.modules.pagamento.conta;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Wrapper fino do fluxo OAuth do Mercado Pago — isola o resto do código do detalhe de
 * como o "code" vira tokens.
 *
 * <p><b>Por que HTTP direto, e não {@code com.mercadopago.client.oauth.OauthClient} do SDK
 * oficial?</b> Na versão 2.1.16 do SDK, {@code OauthClient.createCredential(code, redirectUri)}
 * NÃO envia {@code client_id} no corpo da requisição (só {@code client_secret}, tirado da
 * configuração global {@code MercadoPagoConfig.setAccessToken(...)}) — o troca-code-por-token
 * fica quebrado para o fluxo "conectar conta de terceiro" que este service implementa (a
 * `CreateOauthCredentialRequest` do SDK tem o campo `clientId`, mas o próprio `OauthClient`
 * nunca o preenche). A resposta também não expõe `user_id` como campo tipado. Por isso este
 * client fala direto com {@code POST /oauth/token} da API do Mercado Pago via
 * {@link RestClient}, do jeito que a documentação oficial de OAuth descreve. O SDK continua
 * sendo usado (dependência no pom.xml) para os recursos de cobrança nas próximas tasks —
 * aqui, no OAuth, ele não serve.
 */
@Component
public class MercadoPagoOAuthClient {

    private static final String TOKEN_URL = "https://api.mercadopago.com/oauth/token";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient restClient;

    public MercadoPagoOAuthClient(@Value("${app.pagamento.mercadopago.client-id}") String clientId,
                                   @Value("${app.pagamento.mercadopago.client-secret}") String clientSecret,
                                   @Value("${app.pagamento.mercadopago.redirect-uri}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.restClient = RestClient.create();
    }

    public TokensObtidos trocarCodePorTokens(String code) {
        try {
            Map<String, String> corpo = new LinkedHashMap<>();
            corpo.put("grant_type", "authorization_code");
            corpo.put("client_id", clientId);
            corpo.put("client_secret", clientSecret);
            corpo.put("code", code);
            corpo.put("redirect_uri", redirectUri);

            RespostaTokenMercadoPago resposta = restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .body(corpo)
                .retrieve()
                .body(RespostaTokenMercadoPago.class);

            if (resposta == null) {
                throw new IllegalStateException("Resposta vazia do Mercado Pago ao trocar code por tokens");
            }

            return new TokensObtidos(
                String.valueOf(resposta.userId()),
                resposta.accessToken(),
                resposta.refreshToken(),
                resposta.expiresIn()
            );
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao trocar code por tokens no Mercado Pago", e);
        }
    }

    public record TokensObtidos(String mpUserId, String accessToken, String refreshToken, long expiresInSegundos) {}

    private record RespostaTokenMercadoPago(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("expires_in") long expiresIn
    ) {}
}
