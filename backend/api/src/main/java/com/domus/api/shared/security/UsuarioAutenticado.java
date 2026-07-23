package com.domus.api.shared.security;

import com.domus.api.modules.usuario.Role;
import com.domus.api.modules.usuario.Usuario;
import com.domus.api.shared.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UsuarioAutenticado {

    public Usuario get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof Usuario usuario)) {
            throw new BusinessException("Usuário não autenticado.");
        }
        return usuario;
    }

    public UUID getIgrejaId() {
        return get().getIgreja().getId();
    }

    public UUID getUsuarioId() {
        return get().getId();
    }

    public UUID getPessoaId() {
        return get().getPessoa().getId();
    }

    public String getRole() { return get().getRole().getNome();}
}