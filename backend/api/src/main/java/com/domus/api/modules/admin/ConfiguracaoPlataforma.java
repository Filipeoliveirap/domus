package com.domus.api.modules.admin;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "configuracao_plataforma")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class ConfiguracaoPlataforma {

    @Id
    @Column(name = "chave", length = 100, nullable = false)
    private String chave;

    @Column(name = "valor_criptografado", columnDefinition = "TEXT")
    private String valorCriptografado;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
