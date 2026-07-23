package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "inscricao_evento")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InscricaoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    /** NULL = auto-inscrição. Preenchido = alguém inscreveu esta pessoa. */
    @Column(name = "inscrito_por_usuario_id")
    private UUID inscritoPorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusInscricao status = StatusInscricao.CONFIRMADA;

    /**
     * V5 — marca DURÁVEL de que esta inscrição só existe porque quem gerencia contornou
     * deliberadamente um impedimento ("inscrever mesmo assim": o líder de 34 anos no retiro de
     * jovens, o preletor, o motorista). Sem isto, qualquer edição futura do evento apagaria a
     * exceção em silêncio (ver {@link InscricaoService#removerInscritosNaoElegiveis} e Task 6).
     *
     * <p>Gravada em {@link InscricaoService#inscrever} exatamente quando a elegibilidade foi
     * contornada; {@code false} (default) para toda inscrição que era legítima sob a regra
     * vigente no momento em que foi feita.
     */
    @Column(name = "inscrito_por_excecao", nullable = false)
    @Builder.Default
    private boolean inscritoPorExcecao = false;

    @OneToMany(mappedBy = "inscricao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AcompanhanteInscricao> acompanhantes = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean estaConfirmada() {
        return status == StatusInscricao.CONFIRMADA;
    }
}
