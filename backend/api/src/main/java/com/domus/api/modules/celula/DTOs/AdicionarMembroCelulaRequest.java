package com.domus.api.modules.celula.DTOs;

import java.util.UUID;

public record AdicionarMembroCelulaRequest(
        UUID pessoaId,
        UUID visitanteId
) {}
