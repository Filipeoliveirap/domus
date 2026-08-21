package com.domus.api.modules.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * As rotas públicas de auth (login, Google, forgot/reset-password, registrar igreja) saíram do
 * ignoringRequestMatchers do CSRF (2026-08-20) — mitigação de login CSRF. Este teste prova que
 * a camada de CSRF agora barra POST sem token nessas rotas, e que um token válido passa dela
 * (chegando na lógica de negócio, que aí sim recusa por credencial inválida).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthCsrfConfigTest {

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redisTemplate;

    /**
     * O RateLimitFilter roda antes do CsrfFilter (2026-08-20) — requisição 403 de CSRF já
     * conta no limite de auth (10/min). Sem isso, execuções repetidas deste teste (ou de
     * outro que bata nas mesmas rotas) na mesma janela de 60s acumulam no Redis real
     * (compartilhado, sem Testcontainers) e um 429 legítimo mascara o que o teste quer provar.
     */
    @BeforeEach
    void limpaContadorDeRateLimit() {
        long minuto = Instant.now().getEpochSecond() / 60;
        Set<String> chaves = Set.of(
                "rl:auth:127.0.0.1:" + minuto,
                "rl:global:127.0.0.1:" + minuto);
        redisTemplate.delete(chaves);
    }

    @Test
    void loginSemTokenCsrfE403() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.com\",\"senha\":\"qualquer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginComTokenCsrfValidoPassaDaCamadaDeCsrf() throws Exception {
        // Credencial inexistente: o que importa aqui é NÃO ser 403 (CSRF) — a resposta de
        // negócio (401/400) prova que a requisição atravessou o CsrfFilter.
        mockMvc.perform(post("/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nao-existe@x.com\",\"senha\":\"qualquer\"}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 403) {
                        throw new AssertionError("esperava passar da camada de CSRF, mas levou 403");
                    }
                });
    }

    @Test
    void forgotPasswordSemTokenCsrfE403() throws Exception {
        mockMvc.perform(post("/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@x.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registrarIgrejaSemTokenCsrfE403() throws Exception {
        mockMvc.perform(post("/igrejas/registrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }
}
