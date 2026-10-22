package com.domus.api.modules.usuario;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.PlanoAssinatura;
import com.domus.api.modules.igreja.StatusAssinatura;
import com.domus.api.modules.igreja.StatusTenant;

import java.util.UUID;

/** Só os campos do {@link Usuario} que o principal autenticado (SecurityFilter) realmente
 *  usa em todo o projeto: id, igreja.id, pessoa.id, role.nome/id e ativo. Nada mais é lido
 *  a partir do principal — ver regra em UsuarioAutenticado e a nota de "principal desanexado". */
public record PrincipalCache(
        UUID id,
        UUID igrejaId,
        UUID pessoaId,
        UUID roleId,
        String roleNome,
        boolean ativo,
        StatusTenant statusTenant,
        StatusAssinatura statusAssinatura,
        UUID igrejaMaeId,
        String motivoSuspensao,
        PlanoAssinatura plano
) {

    public PrincipalCache(UUID id, UUID igrejaId, UUID pessoaId, UUID roleId, String roleNome, boolean ativo) {
        this(id, igrejaId, pessoaId, roleId, roleNome, ativo, StatusTenant.ATIVO, StatusAssinatura.ATIVA, null, null, null);
    }

    public static PrincipalCache de(Usuario usuario) {
        Igreja ig = usuario.getIgreja();
        return new PrincipalCache(
                usuario.getId(),
                ig != null ? ig.getId() : null,
                usuario.getPessoa().getId(),
                usuario.getRole().getId(),
                usuario.getRole().getNome(),
                usuario.isAtivo(),
                ig != null ? ig.getStatusTenant() : StatusTenant.ATIVO,
                ig != null ? ig.getStatusAssinatura() : StatusAssinatura.ATIVA,
                ig != null && ig.getIgrejaMae() != null ? ig.getIgrejaMae().getId() : null,
                ig != null ? ig.getMotivoSuspensao() : null,
                ig != null ? ig.getPlano() : null
        );
    }
}
