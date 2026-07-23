package com.domus.api.modules.evento.elegibilidade;

/**
 * @param contornavel quem gerencia pode inscrever assim mesmo (equipe, preletor, motorista).
 *                    VAGAS_ESGOTADAS NUNCA é contornável: vaga que não existe não vira
 *                    exceção administrativa.
 */
public record Impedimento(String codigo, String mensagem, boolean contornavel) {}
