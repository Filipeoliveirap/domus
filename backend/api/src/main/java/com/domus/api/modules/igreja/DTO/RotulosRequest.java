package com.domus.api.modules.igreja.DTO;

import com.domus.api.modules.igreja.GeneroGramatical;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/** Cada bloco é opcional; {@code null} = não mexe nesse módulo. Um bloco presente com
 *  os 3 campos nulos reseta o módulo pro padrão ("Restaurar padrão"). Trio parcialmente
 *  preenchido é rejeitado pelo service (não dá pra expressar isso em Bean Validation
 *  simples sem acoplar os 3 campos). */
public record RotulosRequest(
        @Valid Bloco ministerio,
        @Valid Bloco congregacao,
        @Valid Bloco celula) {

    public record Bloco(
            @Size(max = 40) String singular,
            @Size(max = 40) String plural,
            GeneroGramatical genero) {}
}
