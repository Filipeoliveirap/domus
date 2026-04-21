package com.domus.api.shared.security;

import com.domus.api.modules.usuario.Usuario;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UsuarioAutenticado {

    public Usuario get() {
        return (Usuario) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
    }

    public UUID getIgrejaId() {
        return get().getIgreja().getId();
    }

    public UUID getUsuarioId() {
        return get().getId();
    }
}