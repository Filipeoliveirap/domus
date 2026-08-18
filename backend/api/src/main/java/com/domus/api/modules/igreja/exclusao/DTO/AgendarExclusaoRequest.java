package com.domus.api.modules.igreja.exclusao.DTO;

import jakarta.validation.constraints.NotBlank;

public record AgendarExclusaoRequest(
        @NotBlank String nomeConfirmacao,
        String senha,
        String googleIdToken
) {}
