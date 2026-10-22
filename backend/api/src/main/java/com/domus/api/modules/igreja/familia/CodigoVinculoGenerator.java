package com.domus.api.modules.igreja.familia;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Alfabeto exclui {@code 0/O} e {@code 1/I/L} (confundem quando ditado/lido no papel).
 * {@link SecureRandom} e não {@code Random}: o código é uma credencial.
 */
@Component
public class CodigoVinculoGenerator {

    private static final String ALFABETO_UNAMBIGUO = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final int TAMANHO_ALEATORIO = 6;
    private static final int TAMANHO_LEGADO = 8;

    private final SecureRandom random = new SecureRandom();

    /** Devolve no padrão DOMUS-XXXXXX com hífen. */
    public String gerar() {
        StringBuilder sb = new StringBuilder("DOMUS-");
        for (int i = 0; i < TAMANHO_ALEATORIO; i++) {
            sb.append(ALFABETO_UNAMBIGUO.charAt(random.nextInt(ALFABETO_UNAMBIGUO.length())));
        }
        return sb.toString();
    }

    /**
     * Aceita:
     * - DOMUS-XXXXXX (com ou sem hífen, minúsculas ou maiúsculas)
     * - Links contendo ?codigo=...
     * - Formato legado de 8 caracteres (XK4P-2M7Q)
     */
    public String normalizar(String digitado) {
        if (digitado == null || digitado.isBlank()) {
            return null;
        }

        String valor = digitado.trim();
        // Se colar link completo, extrai o valor de codigo=
        if (valor.contains("codigo=")) {
            int inicio = valor.indexOf("codigo=") + 7;
            int fim = valor.indexOf('&', inicio);
            valor = (fim != -1) ? valor.substring(inicio, fim) : valor.substring(inicio);
        }

        String limpo = valor.toUpperCase().replaceAll("[^0-9A-Z]", "");

        // Padrão DOMUS (DOMUS + 6 caracteres = 11 chars limpos)
        if (limpo.startsWith("DOMUS") && limpo.length() == 5 + TAMANHO_ALEATORIO) {
            String sufixo = limpo.substring(5);
            for (char c : sufixo.toCharArray()) {
                if (ALFABETO_UNAMBIGUO.indexOf(c) < 0) {
                    return null;
                }
            }
            return "DOMUS-" + sufixo;
        }

        // Padrão Legado (8 caracteres)
        if (limpo.length() == TAMANHO_LEGADO) {
            for (char c : limpo.toCharArray()) {
                if (ALFABETO_UNAMBIGUO.indexOf(c) < 0) {
                    return null;
                }
            }
            return limpo.substring(0, TAMANHO_LEGADO / 2) + "-" + limpo.substring(TAMANHO_LEGADO / 2);
        }

        return null;
    }
}
