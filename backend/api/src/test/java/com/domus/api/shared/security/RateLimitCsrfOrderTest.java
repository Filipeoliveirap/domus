package com.domus.api.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * RateLimitFilter agora roda antes do CsrfFilter (SecurityConfig) — requisição barrada por
 * CSRF (403) já conta no limite, fechando o gap descrito no BACKLOG ("Rate limiting não
 * conta requisições barradas pelo CSRF"). Semeia o contador direto no Redis (em vez de
 * disparar N requisições reais) para não depender da virada do minuto da janela fixa —
 * um teste baseado em relógio de parede é inerentemente instável perto da borda.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitCsrfOrderTest {

    @Autowired MockMvc mockMvc;
    @Autowired StringRedisTemplate redisTemplate;

    private static final String ROTA = "/igrejas-vinculadas/entrar";
    private static final int LIMITE_AUTH = 10;

    @Test
    void requisicaoSemCsrfJaContaNoLimiteEEstoura429() throws Exception {
        long minutoAtual = Instant.now().getEpochSecond() / 60;
        String chave = "rl:auth:127.0.0.1:" + minutoAtual;
        // Simula que as LIMITE_AUTH requisições anteriores (barradas por CSRF) já contaram.
        redisTemplate.opsForValue().set(chave, String.valueOf(LIMITE_AUTH));
        redisTemplate.expire(chave, Duration.ofSeconds(60));

        // Se RateLimitFilter roda ANTES do CsrfFilter, esta requisição incrementa o
        // contador pra 11 (> limite) e leva 429 — não chega a ser barrada por CSRF (403).
        int status = mockMvc.perform(post(ROTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();

        assertThat(status).isEqualTo(429);
    }
}
