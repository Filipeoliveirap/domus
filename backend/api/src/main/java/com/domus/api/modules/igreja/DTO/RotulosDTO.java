package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.GeneroGramatical;
import com.domus.api.modules.igreja.Igreja;

/** Nulo por campo = a igreja não customizou aquele módulo; o front resolve o padrão. */
public record RotulosDTO(
        String ministerioSingular, String ministerioPlural, GeneroGramatical ministerioGenero,
        String congregacaoSingular, String congregacaoPlural, GeneroGramatical congregacaoGenero,
        String celulaSingular, String celulaPlural, GeneroGramatical celulaGenero) {

    public static RotulosDTO from(Igreja igreja) {
        return new RotulosDTO(
                igreja.getMinisterioNomeSingular(), igreja.getMinisterioNomePlural(), igreja.getMinisterioGenero(),
                igreja.getCongregacaoNomeSingular(), igreja.getCongregacaoNomePlural(), igreja.getCongregacaoGenero(),
                igreja.getCelulaNomeSingular(), igreja.getCelulaNomePlural(), igreja.getCelulaGenero());
    }
}
