package com.domus.api.modules.igreja.exclusao.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgendarExclusaoRequest(
        @NotBlank @Size(max = 255) String nomeConfirmacao,
        @Size(max = 255) String senha,
        @Size(max = 4096) String googleIdToken
) {}
