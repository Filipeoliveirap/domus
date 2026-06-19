package com.domus.api.modules.membro;


import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "membro")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Membro {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "telefone", nullable = false, length = 11)
    private String telefone;

    @Column(name = "data_nascimento",  nullable = false)
    private LocalDate dataNascimento;

    @Column(name = "endereco", length = 500)
    private String enderaco;

    @Column(name = "status",  nullable = false)
    private String status;

    @Column(name = "observacoes")
    private String observacoes;


    @Column(name = "deleted_at", nullable = true)
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
