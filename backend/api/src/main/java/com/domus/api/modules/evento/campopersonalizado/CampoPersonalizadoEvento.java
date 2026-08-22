package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "campo_personalizado_evento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE campo_personalizado_evento SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class CampoPersonalizadoEvento {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evento_id", nullable = false)
    private Evento evento;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 160)
    private String placeholder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCampoPersonalizado tipo;

    /** Uma opção por linha; só usado quando tipo é OPCAO_UNICA ou MULTIPLA_ESCOLHA. */
    @Column(columnDefinition = "TEXT")
    private String opcoes;

    @Column(nullable = false)
    @Builder.Default
    private boolean obrigatorio = false;

    /** Groundwork pra Spec 2 (formulário público) — sem efeito nenhum nesta spec. */
    @Column(name = "visivel_ao_publico", nullable = false)
    @Builder.Default
    private boolean visivelAoPublico = true;

    @Column(nullable = false)
    @Builder.Default
    private int ordem = 0;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public List<String> getOpcoesComoLista() {
        if (opcoes == null || opcoes.isBlank()) return List.of();
        List<String> resultado = new ArrayList<>();
        for (String linha : opcoes.split("\n")) {
            String limpa = linha.trim();
            if (!limpa.isEmpty()) resultado.add(limpa);
        }
        return resultado;
    }

    public void setOpcoesComoLista(List<String> lista) {
        this.opcoes = (lista == null || lista.isEmpty()) ? null : String.join("\n", lista);
    }
}
