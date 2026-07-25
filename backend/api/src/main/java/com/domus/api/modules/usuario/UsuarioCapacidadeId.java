package com.domus.api.modules.usuario;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class UsuarioCapacidadeId implements Serializable {
    private UUID usuarioId;
    private String capacidade;

    public UsuarioCapacidadeId() {}
    public UsuarioCapacidadeId(UUID usuarioId, String capacidade) {
        this.usuarioId = usuarioId; this.capacidade = capacidade;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsuarioCapacidadeId that)) return false;
        return Objects.equals(usuarioId, that.usuarioId) && Objects.equals(capacidade, that.capacidade);
    }
    @Override public int hashCode() { return Objects.hash(usuarioId, capacidade); }
}
