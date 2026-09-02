package com.domus.api.shared.dominio;

/** Formata um {@link Endereco} numa linha só para exibição (drawer de evento, busca, notificação). */
public final class EnderecoFormatter {

    private EnderecoFormatter() {}

    public static String emLinhaUnica(Endereco e) {
        if (e == null || !e.estaPreenchido()) return null;

        StringBuilder linha = new StringBuilder();
        acrescentar(linha, e.getLogradouro(), "");
        acrescentar(linha, e.getNumero(), ", ");
        acrescentar(linha, e.getComplemento(), ", ");

        StringBuilder bairroCidade = new StringBuilder();
        acrescentar(bairroCidade, e.getBairro(), "");
        String cidadeUf = e.getCidade();
        if (temTexto(cidadeUf) && temTexto(e.getUf())) cidadeUf = cidadeUf + "/" + e.getUf();
        acrescentar(bairroCidade, cidadeUf, ", ");

        if (bairroCidade.length() > 0) {
            if (linha.length() > 0) linha.append(" - ");
            linha.append(bairroCidade);
        }
        if (temTexto(e.getCep())) linha.append(" (").append(e.getCep().trim()).append(")");
        return linha.toString();
    }

    private static void acrescentar(StringBuilder sb, String parte, String separador) {
        if (!temTexto(parte)) return;
        if (sb.length() > 0) sb.append(separador);
        sb.append(parte.trim());
    }

    private static boolean temTexto(String s) {
        return s != null && !s.isBlank();
    }
}
