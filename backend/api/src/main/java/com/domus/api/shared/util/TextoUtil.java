package com.domus.api.shared.util;

import java.text.Normalizer;
import java.util.Set;

public final class TextoUtil {

    private TextoUtil() {}

    private static final Set<String> CONECTORES = Set.of("de", "da", "do", "das", "dos", "e");

    /** Title Case por palavra, mantendo conectores PT-BR em minúsculo. Ex.: "joão da silva" → "João da Silva". */
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

    /** Remove tudo que não for dígito. Usado para comparar telefones formatados de jeitos diferentes. */
    public static String somenteDigitos(String texto) {
        if (texto == null) return null;
        String digitos = texto.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /** Normaliza para comparação: apara, colapsa espaços, remove acentos e vira minúsculo. */
    public static String normalizarParaComparacao(String texto) {
        if (texto == null) return null;
        String limpo = texto.trim().replaceAll("\\s+", " ").toLowerCase();
        if (limpo.isEmpty()) return null;
        String semAcento = Normalizer.normalize(limpo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento;
    }
}
