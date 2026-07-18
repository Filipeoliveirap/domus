package com.domus.api.shared.util;

import java.util.Set;

public final class TextoUtil {

    private TextoUtil() {}

    // Conectores que ficam em minúsculo no meio de nomes próprios/títulos (PT-BR).
    private static final Set<String> CONECTORES = Set.of("de", "da", "do", "das", "dos", "e");

    /**
     * Capitaliza para exibição: Title Case por palavra, mantendo os conectores em minúsculo
     * (exceto quando são a primeira palavra). Ex.: "joão da silva" -> "João da Silva".
     * Colapsa espaços e apara. Retorna null se o texto for nulo ou vazio.
     */
    public static String capitalizar(String texto) {
        if (texto == null) return null;
        String limpo = texto.trim().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) return null;

        String[] palavras = limpo.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder(limpo.length());
        for (int i = 0; i < palavras.length; i++) {
            String p = palavras[i];
            if (i > 0 && CONECTORES.contains(p)) {
                sb.append(p);
            } else {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
            if (i < palavras.length - 1) sb.append(' ');
        }
        return sb.toString();
    }
}
