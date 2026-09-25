package com.domus.api.modules.contapagar.dto;

import java.util.UUID;

/** Dados do beneficiário em uma resposta. */
public record BeneficiarioResponse(
    UUID pessoaId,
    String pessoaNome,
    String texto,
    Boolean pessoaRemovida
) {}
