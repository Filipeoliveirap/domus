package com.domus.api.shared.security;

/** Nível de acesso, não vínculo com a igreja — um congregante com login tem ACESSO_COMUM. */
public enum Perfil {
    ADMIN_IGREJA,
    LIDER,
    ACESSO_COMUM;

    /** Devolve null em vez de estourar: role desconhecida vira "não pode nada" (fail-closed). */
    public static Perfil deNomeOuNull(String nome) {
        if (nome == null || nome.isBlank()) return null;
        try {
            return Perfil.valueOf(nome);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
