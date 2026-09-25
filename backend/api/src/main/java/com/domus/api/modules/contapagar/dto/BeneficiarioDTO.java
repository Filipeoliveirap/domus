package com.domus.api.modules.contapagar.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Beneficiário de uma conta: pessoa cadastrada (XOR com texto). */
public record BeneficiarioDTO(
    UUID pessoaId,
    @Size(max = 120) String texto
) {}
