package com.domus.api.modules.igreja.familia;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera o código de vínculo, no formato exibido {@code XK4P-2M7Q}.
 *
 * <p>O alfabeto exclui os símbolos que se confundem quando o código é <b>ditado no WhatsApp
 * ou lido num papel</b>: {@code 0/O} e {@code 1/I/L}. Sobram 31 símbolos → 31⁸ ≈ 852 bilhões
 * de combinações, espaço de sobra (o design estimava ~1 trilhão com 32).
 *
 * <p>{@link SecureRandom} e não {@code Random}: o código é uma credencial — quem adivinhar
 * pendura a igreja dele na sua família.
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

    /**
     * Normaliza o que o usuário digitou: aceita minúsculas, com ou sem hífen, com espaços.
     * Devolve no formato canônico ({@code XK4P-2M7Q}) ou {@code null} se não for um código válido.
     */
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
