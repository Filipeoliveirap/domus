package com.domus.api.modules.evento;

import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "evento")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE evento SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Evento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "inicio_em", nullable = false)
    private LocalDateTime inicioEm;

    @Column(name = "fim_em")
    private LocalDateTime fimEm;

    private String local;

    private String foto;

    /** NULL = sem limite de vagas. */
    @Column(name = "vagas")
    private Integer vagas;

    /** NULL = gratuito. Informativo: o Domus registra a inscrição, não o pagamento. */
    @Column(name = "preco", precision = 10, scale = 2)
    private java.math.BigDecimal preco;

    @Column(name = "exclusivo_membros", nullable = false)
    @Builder.Default
    private boolean exclusivoMembros = false;

    @Column(name = "exclusivo_batizados", nullable = false)
    @Builder.Default
    private boolean exclusivoBatizados = false;

    /** Só evento marcado mostra o botão "Confirmar presença" e a lista de inscritos. */
    @Column(name = "requer_inscricao", nullable = false)
    @Builder.Default
    private boolean requerInscricao = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}