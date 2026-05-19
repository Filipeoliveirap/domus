package com.domus.api.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LoginAttemptService {

    private static final int MAX_TENTATIVAS = 5;

    private static final int MINUTOS_BLOQUEIO = 15;

    private final ConcurrentHashMap<String, TentativaLogin> tentativas = new ConcurrentHashMap<>();

    private static class TentativaLogin {
        int contador;
        LocalDateTime bloqueadoAte;

        TentativaLogin() {
            this.contador = 0;
            this.bloqueadoAte = null;
        }
    }

    public void registrarFalha(String email) {
        TentativaLogin tentativa = tentativas.getOrDefault(email, new TentativaLogin());
        tentativa.contador++;

        if (tentativa.contador >= MAX_TENTATIVAS) {
            tentativa.bloqueadoAte = LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEIO);
            log.warn("Email bloqueado por {} minutos após {} tentativas. email={}",
                    MINUTOS_BLOQUEIO, MAX_TENTATIVAS, email);
        }

        tentativas.put(email, tentativa);
        log.debug("Tentativa de login falha registrada. email={}, contador={}",
                email, tentativa.contador);
    }

    public void registrarSucesso(String email) {
        tentativas.remove(email);
        log.debug("Tentativas resetadas após login bem-sucedido. email={}", email);
    }

    public boolean estaBloqueado(String email) {
        TentativaLogin tentativa = tentativas.get(email);

        if (tentativa == null || tentativa.bloqueadoAte == null) {
            return false;
        }

        if (LocalDateTime.now().isAfter(tentativa.bloqueadoAte)) {
            tentativas.remove(email);
            log.debug("Bloqueio expirado, email desbloqueado automaticamente. email={}", email);
            return false;
        }

        return true;
    }

    public long minutosRestantes(String email) {
        TentativaLogin tentativa = tentativas.get(email);
        if (tentativa == null || tentativa.bloqueadoAte == null) return 0;

        return java.time.Duration.between(
                LocalDateTime.now(),
                tentativa.bloqueadoAte
        ).toMinutes() + 1;
    }
}