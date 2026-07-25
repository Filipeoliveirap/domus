package com.domus.api.modules.usuario;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "usuario_capacidade")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(UsuarioCapacidadeId.class)
public class UsuarioCapacidade {

    @Id
    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Id
    @Column(nullable = false, length = 20)
    private String capacidade;

    @Column(name = "concedido_por_usuario_id")
    private UUID concedidoPorUsuarioId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
