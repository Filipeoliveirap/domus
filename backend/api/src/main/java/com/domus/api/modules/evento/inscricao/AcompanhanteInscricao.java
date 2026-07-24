package com.domus.api.modules.evento.inscricao;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "acompanhante_inscricao")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AcompanhanteInscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inscricao_id", nullable = false)
    private InscricaoEvento inscricao;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "telefone", length = 20)
    private String telefone;

    /**
     * Presença marcada para ESTE convidado especificamente — acompanhante ocupa vaga e
     * esteve lá igual ao inscrito, por isso a presença é granular por pessoa física.
     */
    @Column(name = "compareceu", nullable = false)
    @Builder.Default
    private boolean compareceu = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
