package com.domus.api.modules.termos;

import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Registro de consentimento — histórico jurídico. Nunca editado nem apagado depois de criado. */
@Entity
@Table(name = "termo_aceite")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class TermoAceite {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoTermo tipo;

    @Column(name = "versao", nullable = false, length = 20)
    private String versao;

    @Column(name = "ip", length = 45)
    private String ip;

    @CreationTimestamp
    @Column(name = "aceito_em", nullable = false, updatable = false)
    private LocalDateTime aceitoEm;
}
