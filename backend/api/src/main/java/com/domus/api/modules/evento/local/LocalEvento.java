package com.domus.api.modules.evento.local;

import com.domus.api.modules.igreja.Igreja;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "local_evento")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE local_evento SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@org.hibernate.annotations.BatchSize(size = 25)
public class LocalEvento {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(nullable = false, length = 150)
    private String nome;

    private Integer capacidade;

    @Column(name = "cep_logradouro_numero")
    private String cepLogradouroNumero;

    @Column(name = "complemento_bairro_cidade_uf")
    private String complementoBairroCidadeUf;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Endereço próprio? Se não, quem exibe deve cair no da igreja. */
    public boolean temEnderecoProprio() {
        return cepLogradouroNumero != null && !cepLogradouroNumero.isBlank();
    }
}
