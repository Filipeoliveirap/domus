package com.domus.api.modules.usuario;

import java.util.UUID;

/** Só os campos do {@link Usuario} que o principal autenticado (SecurityFilter) realmente
 *  usa em todo o projeto: id, igreja.id, pessoa.id, role.nome/id e ativo. Nada mais é lido
 *  a partir do principal — ver regra em UsuarioAutenticado e a nota de "principal desanexado". */
public record PrincipalCache(UUID id, UUID igrejaId, UUID pessoaId, UUID roleId, String roleNome, boolean ativo) {

    public static PrincipalCache de(Usuario usuario) {
        return new PrincipalCache(
                usuario.getId(),
                usuario.getIgreja().getId(),
                usuario.getPessoa().getId(),
                usuario.getRole().getId(),
                usuario.getRole().getNome(),
                usuario.isAtivo()
        );
    }
}
