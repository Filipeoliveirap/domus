package com.domus.api.shared.security;

/**
 * Perfis de acesso. Existe para acabar com a string crua espalhada pelo código.
 *
 * <p>O nome fala de NÍVEL DE ACESSO, não de vínculo com a igreja — um congregante com
 * login tem acesso comum, e chamar esse perfil pelo nome do vínculo era a confusão que
 * esta mudança elimina.
 */
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
