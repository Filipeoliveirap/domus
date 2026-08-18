package com.domus.api.modules.igreja.familia;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Alfabeto exclui {@code 0/O} e {@code 1/I/L} (confundem quando ditado/lido no papel).
 * {@link SecureRandom} e não {@code Random}: o código é uma credencial.
 */
@Component
public class CodigoVinculoGenerator {

    private static final String ALFABETO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int TAMANHO = 8;

    private final SecureRandom random = new SecureRandom();

    /** Devolve já formatado com o hífen, como será exibido e armazenado. */
    public String gerar() {
        StringBuilder sb = new StringBuilder(TAMANHO + 1);
        for (int i = 0; i < TAMANHO; i++) {
            if (i == TAMANHO / 2) {
                sb.append('-');
            }
            sb.append(ALFABETO.charAt(random.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }

    /** Aceita minúsculas, com/sem hífen, com espaços; devolve o formato canônico ou {@code null} se inválido. */
    public String normalizar(String digitado) {
        if (digitado == null) {
            return null;
        }
        String limpo = digitado.toUpperCase().replaceAll("[^0-9A-Z]", "");
        if (limpo.length() != TAMANHO) {
            return null;
        }
        for (char c : limpo.toCharArray()) {
            if (ALFABETO.indexOf(c) < 0) {
                return null;
            }
        }
        return limpo.substring(0, TAMANHO / 2) + "-" + limpo.substring(TAMANHO / 2);
    }
}
