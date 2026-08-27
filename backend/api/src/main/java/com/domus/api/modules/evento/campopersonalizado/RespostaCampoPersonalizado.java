package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "resposta_campo_personalizado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RespostaCampoPersonalizado {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "campo_id", nullable = false)
    private CampoPersonalizadoEvento campo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false)
    private InscricaoEvento inscricao;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String valor;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
