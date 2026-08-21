package com.domus.api.shared.util;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** Valida DTOs de request via Bean Validation puro (sem contexto Spring) — rápido, prova as anotações. */
public final class ValidacaoTestSupport {

    public static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private ValidacaoTestSupport() {}
}
