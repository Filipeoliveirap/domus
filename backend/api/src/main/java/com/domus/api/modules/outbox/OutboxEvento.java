package com.domus.api.modules.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_entidade", nullable = false, length = 30)
    private TipoEntidadeOutbox tipoEntidade;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_evento", nullable = false, length = 20)
    private TipoEventoOutbox tipoEvento;

    @Column(name = "entidade_id", nullable = false)
    private UUID entidadeId;

    @Column(name = "igreja_id", nullable = false)
    private UUID igrejaId;

    @Column(nullable = false)
    private boolean processado;

    @Column(nullable = false)
    private int tentativas;

    @Column(columnDefinition = "TEXT")
    private String erro;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processado_at")
    private LocalDateTime processadoAt;
}
