package com.domus.api.modules.anexo;

import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Arquivo genérico armazenado no R2 (anexos de conta, comprovantes, NFes). */
@Entity
@Table(name = "anexo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Anexo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(nullable = false, unique = true)
    private String chave;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(nullable = false)
    private Long bytes;

    @Column(name = "nome_original", nullable = false)
    private String nomeOriginal;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
