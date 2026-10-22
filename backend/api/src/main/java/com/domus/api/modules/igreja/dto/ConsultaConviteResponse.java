package com.domus.api.modules.igreja.dto;

import java.util.UUID;

public record ConsultaConviteResponse(
        String estado,
        String matrizNome,
        String matrizPastor,
        String planoNome,
        Integer limiteCongregacoes,
        Long congregacoesAtuais,
        UUID logoFotoId,
        String rotuloCongregacaoSingular,
        String rotuloCongregacaoPlural
) {
    public static ConsultaConviteResponse valido(String matrizNome, String matrizPastor, String planoNome, Integer limiteCongregacoes, Long congregacoesAtuais, UUID logoFotoId, String rotuloSingular, String rotuloPlural) {
        return new ConsultaConviteResponse("VALIDO", matrizNome, matrizPastor, planoNome, limiteCongregacoes, congregacoesAtuais, logoFotoId, rotuloSingular, rotuloPlural);
    }

    public static ConsultaConviteResponse valido(String matrizNome, String matrizPastor, String planoNome, Integer limiteCongregacoes, Long congregacoesAtuais, UUID logoFotoId) {
        return valido(matrizNome, matrizPastor, planoNome, limiteCongregacoes, congregacoesAtuais, logoFotoId, "Congregação", "Congregações");
    }

    public static ConsultaConviteResponse expirado() {
        return new ConsultaConviteResponse("EXPIRADO", null, null, null, null, null, null, null, null);
    }

    public static ConsultaConviteResponse jaUtilizado(String matrizNome) {
        return new ConsultaConviteResponse("JA_UTILIZADO", matrizNome, null, null, null, null, null, null, null);
    }

    public static ConsultaConviteResponse limiteExcedido(String matrizNome, String planoNome, Integer limite, Long atuais, String rotuloSingular, String rotuloPlural) {
        return new ConsultaConviteResponse("MATRIZ_LIMITE_EXCEDIDO", matrizNome, null, planoNome, limite, atuais, null, rotuloSingular, rotuloPlural);
    }

    public static ConsultaConviteResponse limiteExcedido(String matrizNome, String planoNome) {
        return limiteExcedido(matrizNome, planoNome, null, null, "Congregação", "Congregações");
    }
}
