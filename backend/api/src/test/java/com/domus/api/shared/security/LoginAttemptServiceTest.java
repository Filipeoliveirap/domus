package com.domus.api.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LoginAttemptServiceTest {

    StringRedisTemplate redis;
    ValueOperations<String, String> valueOps;
    LoginAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        service = new LoginAttemptService(redis);
    }

    @Test
    void primeiraFalha_defineValidadeDoContador() {
        when(valueOps.increment("login:attempt:a@a.com")).thenReturn(1L);

        service.registrarFalha("a@a.com");

        verify(redis).expire(eq("login:attempt:a@a.com"), any(Duration.class));
        // não bloqueia ainda: nenhuma chave de bloqueio é criada
        verify(valueOps, never()).set(startsWith("login:block:"), anyString(), any(Duration.class));
    }

    @Test
    void quintaFalha_bloqueiaEApagaContador() {
        when(valueOps.increment("login:attempt:a@a.com")).thenReturn(5L);

        service.registrarFalha("a@a.com");

        verify(valueOps).set(eq("login:block:a@a.com"), eq("1"), any(Duration.class));
        verify(redis).delete("login:attempt:a@a.com");
    }

    @Test
    void estaBloqueado_refleteExistenciaDaChaveDeBloqueio() {
        when(redis.hasKey("login:block:a@a.com")).thenReturn(true);
        assertThat(service.estaBloqueado("a@a.com")).isTrue();

        when(redis.hasKey("login:block:b@b.com")).thenReturn(false);
        assertThat(service.estaBloqueado("b@b.com")).isFalse();
    }

    @Test
    void minutosRestantes_arredondaParaCima() {
        when(redis.getExpire("login:block:a@a.com")).thenReturn(61L); // 1min1s
        assertThat(service.minutosRestantes("a@a.com")).isEqualTo(2);

        when(redis.getExpire("login:block:c@c.com")).thenReturn(-2L); // sem TTL
        assertThat(service.minutosRestantes("c@c.com")).isZero();
    }

    @Test
    void registrarSucesso_limpaContadorEBloqueio() {
        service.registrarSucesso("a@a.com");
        verify(redis).delete("login:attempt:a@a.com");
        verify(redis).delete("login:block:a@a.com");
    }
}
