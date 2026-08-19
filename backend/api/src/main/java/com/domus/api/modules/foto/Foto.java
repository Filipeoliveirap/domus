package com.domus.api.modules.foto;

import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Metadado da foto — os bytes vivem no R2. */
@Entity
@Table(name = "foto")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Foto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    /** Prefixo no bucket. As três versões vivem sob ele: `{chave}/original`, `/display.jpg`, `/thumb.jpg`. */
    @Column(nullable = false, unique = true)
    private String chave;

    /** Tipo do ORIGINAL. As versões derivadas são sempre JPEG. */
    @Column(nullable = false, length = 50)
    private String tipo;

    /** Tamanho do original, para acompanhar consumo do bucket. */
    @Column(nullable = false)
    private long bytes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
