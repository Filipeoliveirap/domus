package com.domus.api.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Anti-força-bruta por conta. Conta falhas de login por e-mail no Redis (sobrevive a
 * reinícios) e bloqueia após {@link #MAX_TENTATIVAS} erros. Duas chaves:
 * {@code login:attempt:<email>} (contador) e {@code login:block:<email>} (marca com TTL
 * para desbloqueio automático).
 */
@Slf4j
@Service
public class LoginAttemptService {

    private static final int MAX_TENTATIVAS = 5;
    private static final Duration BLOQUEIO = Duration.ofMinutes(15);

    private static final String PREFIXO_TENTATIVA = "login:attempt:";
    private static final String PREFIXO_BLOQUEIO = "login:block:";

    private final StringRedisTemplate redisTemplate;

    public LoginAttemptService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void registrarFalha(String email) {
        String chave = chaveTentativa(email);
        Long contador = redisTemplate.opsForValue().increment(chave);
        if (contador != null && contador == 1L) {
            // Primeira falha da janela: define validade para as falhas expirarem sozinhas.
            redisTemplate.expire(chave, BLOQUEIO);
        }

        if (contador != null && contador >= MAX_TENTATIVAS) {
            redisTemplate.opsForValue().set(chaveBloqueio(email), "1", BLOQUEIO);
            redisTemplate.delete(chave);
            log.warn("Email bloqueado por {} min após {} tentativas. email={}",
                    BLOQUEIO.toMinutes(), MAX_TENTATIVAS, email);
        }

        log.debug("Tentativa de login falha registrada. email={}, contador={}", email, contador);
    }

    public void registrarSucesso(String email) {
        redisTemplate.delete(chaveTentativa(email));
        redisTemplate.delete(chaveBloqueio(email));
        log.debug("Tentativas resetadas após login bem-sucedido. email={}", email);
    }

    public boolean estaBloqueado(String email) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(chaveBloqueio(email)));
    }

    public long minutosRestantes(String email) {
        Long segundos = redisTemplate.getExpire(chaveBloqueio(email));
        if (segundos == null || segundos <= 0) return 0;
        // Arredonda para cima: 1s restante ainda é "1 minuto".
        return (segundos + 59) / 60;
    }

    private String chaveTentativa(String email) {
        return PREFIXO_TENTATIVA + email;
    }

    private String chaveBloqueio(String email) {
        return PREFIXO_BLOQUEIO + email;
    }
}
