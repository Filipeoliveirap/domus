package com.domus.api.modules.evento.elegibilidade;

/** @param contornavel VAGAS_ESGOTADAS nunca é contornável — vaga que não existe não vira exceção administrativa. */
public record Impedimento(String codigo, String mensagem, boolean contornavel) {}
