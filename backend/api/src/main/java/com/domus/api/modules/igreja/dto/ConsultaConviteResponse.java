package com.domus.api.modules.igreja.dto;

import java.util.UUID;

public record ConsultaConviteResponse(
        String estado,
        String matrizNome,
        String matrizPastor,
        String planoNome,
        Integer limiteCongregacoes,
        Long congregacoesAtuais,
        UUID logoFotoId
) {
    public static ConsultaConviteResponse valido(String matrizNome, String matrizPastor, String planoNome, Integer limiteCongregacoes, Long congregacoesAtuais, UUID logoFotoId) {
        return new ConsultaConviteResponse("VALIDO", matrizNome, matrizPastor, planoNome, limiteCongregacoes, congregacoesAtuais, logoFotoId);
    }

    public static ConsultaConviteResponse expirado() {
        return new ConsultaConviteResponse("EXPIRADO", null, null, null, null, null, null);
    }

    public static ConsultaConviteResponse jaUtilizado(String matrizNome) {
        return new ConsultaConviteResponse("JA_UTILIZADO", matrizNome, null, null, null, null, null);
    }

    public static ConsultaConviteResponse limiteExcedido(String matrizNome, String planoNome) {
        return new ConsultaConviteResponse("MATRIZ_LIMITE_EXCEDIDO", matrizNome, null, planoNome, null, null, null);
    }
}
