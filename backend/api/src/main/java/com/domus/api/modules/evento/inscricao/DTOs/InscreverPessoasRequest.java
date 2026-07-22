package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record InscreverPessoasRequest(
        @NotEmpty(message = "Selecione ao menos um membro.")
        List<UUID> pessoaIds
) {}
