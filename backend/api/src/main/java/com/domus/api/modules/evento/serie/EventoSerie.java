package com.domus.api.modules.evento.serie;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "evento_serie")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EventoSerie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FrequenciaRecorrencia frequencia;

    @Column(nullable = false)
    @Builder.Default
    private int intervalo = 1;

    /** CSV de {@code DiaSemana.name()} — só preenchido quando {@code frequencia == SEMANAL}. */
    @Column(name = "dias_semana", length = 80)
    private String diasSemana;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_recorrencia_mensal", length = 20)
    private TipoRecorrenciaMensal tipoRecorrenciaMensal;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    @Column(name = "numero_ocorrencias")
    private Integer numeroOcorrencias;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativa = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private java.time.LocalDateTime createdAt;

    public java.util.Set<com.domus.api.modules.celula.DiaSemana> getDiasSemanaComoSet() {
        if (diasSemana == null || diasSemana.isBlank()) return java.util.Set.of();
        java.util.Set<com.domus.api.modules.celula.DiaSemana> resultado = new java.util.HashSet<>();
        for (String parte : diasSemana.split(",")) {
            resultado.add(com.domus.api.modules.celula.DiaSemana.valueOf(parte.trim()));
        }
        return resultado;
    }
}
