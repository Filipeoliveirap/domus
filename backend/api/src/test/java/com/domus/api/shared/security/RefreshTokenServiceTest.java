package com.domus.api.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    StringRedisTemplate redisTemplate;
    ValueOperations<String, String> valueOps;
    SetOperations<String, String> setOps;
    RefreshTokenService service;

    UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        service = new RefreshTokenService(redisTemplate, 604_800_000L); // 7 dias
    }

    @Test
    void revogarTodasSessoesExceto_mantemAFamiliaDoTokenAtual() {
        String tokenAtual = "token-atual";
        String familiaAtual = "familia-atual";
        String familiaOutra = "familia-outra";

        when(valueOps.get("refresh:" + tokenAtual)).thenReturn(usuarioId + "|" + familiaAtual);
        when(setOps.members("usuariofamilias:" + usuarioId))
                .thenReturn(Set.of(familiaAtual, familiaOutra));

        service.revogarTodasSessoesExceto(usuarioId, tokenAtual);

        verify(redisTemplate, never()).delete("refreshfam:" + familiaAtual);
        verify(redisTemplate).delete("refreshfam:" + familiaOutra);
    }

    @Test
    void revogarTodasSessoesExceto_tokenAtualNulo_revogaTodasMesmoAssim() {
        when(setOps.members("usuariofamilias:" + usuarioId)).thenReturn(Set.of("familia-x"));

        service.revogarTodasSessoesExceto(usuarioId, null);

        verify(redisTemplate).delete("refreshfam:familia-x");
    }
}
