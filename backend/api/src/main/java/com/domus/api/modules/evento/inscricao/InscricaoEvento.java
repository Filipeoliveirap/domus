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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    @Column(name = "nome_convidado", length = 255)
    private String nomeConvidado;

    @Column(name = "telefone_convidado", length = 20)
    private String telefoneConvidado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convidado_por_pessoa_id")
    private Pessoa convidadoPor;

    @Column(name = "inscrito_por_usuario_id")
    private UUID inscritoPorUsuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private StatusInscricao status = StatusInscricao.CONFIRMADA;

    @Column(name = "inscrito_por_excecao", nullable = false)
    @Builder.Default
    private boolean inscritoPorExcecao = false;

    @Column(name = "compareceu", nullable = false)
    @Builder.Default
    private boolean compareceu = false;

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

    /** {@code true} = inscrição de gente sem cadastro no sistema (modelo desta spec — nunca
     *  confundir com {@link #getAcompanhantes()}, que é o modelo antigo aninhado). */
    public boolean isConvidadoSemCadastro() {
        return pessoa == null && nomeConvidado != null;
    }
}
