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

    /** Antepõe {@code prefixo} a {@code nome}, sem duplicar quando o nome já começa com esse
     *  prefixo. Ex.: prefixo "igreja" + nome "Igreja Batista Central" → "Igreja Batista Central"
     *  (não "igreja Igreja Batista Central"); prefixo "célula" + nome "Jovens" → "célula Jovens".
     *  A comparação ignora acento e caixa. Usado em texto de notificação onde o nome digitado
     *  pela pessoa pode ou não repetir o substantivo que vem antes dele na frase. */
    public static String prefixarSemDuplicar(String prefixo, String nome) {
        if (nome == null || nome.isBlank()) return prefixo;
        String limpo = nome.trim().replaceAll("\\s+", " ");
        String normNome = normalizarParaComparacao(limpo);
        String normPrefixo = normalizarParaComparacao(prefixo);
        if (normNome != null && normPrefixo != null
                && (normNome.equals(normPrefixo) || normNome.startsWith(normPrefixo + " "))) {
            return limpo;
        }
        return prefixo + " " + limpo;
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
