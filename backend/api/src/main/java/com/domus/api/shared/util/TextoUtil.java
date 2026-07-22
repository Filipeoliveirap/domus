package com.domus.api.shared.util;

import java.text.Normalizer;
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

    /**
     * Deixa só os dígitos de um telefone (remove DDI, parênteses, traço, espaço). Usado para
     * comparar telefones que chegam formatados de jeitos diferentes (ex.: "(11) 99999-8888" e
     * "11999998888" devem contar como o mesmo número). Retorna {@code null} se a entrada for
     * nula ou não sobrar nenhum dígito.
     */
    public static String somenteDigitos(String texto) {
        if (texto == null) return null;
        String digitos = texto.replaceAll("\\D", "");
        return digitos.isEmpty() ? null : digitos;
    }

    /**
     * Normaliza um nome para COMPARAÇÃO (não para exibição): apara, colapsa espaços, remove
     * acentuação e vira minúsculo. Ex.: "José  DA Silva" e "jose da silva" comparam iguais.
     * Retorna {@code null} se a entrada for nula ou vazia.
     */
    public static String normalizarParaComparacao(String texto) {
        if (texto == null) return null;
        String limpo = texto.trim().replaceAll("\\s+", " ").toLowerCase();
        if (limpo.isEmpty()) return null;
        String semAcento = Normalizer.normalize(limpo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento;
    }
}
