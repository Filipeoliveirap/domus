package com.domus.api.modules.financeiro.categoria.DTOs;

import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CategoriaRequestDTO(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @NotNull(message = "O tipo é obrigatório")
        TipoCategoria tipo
) {}