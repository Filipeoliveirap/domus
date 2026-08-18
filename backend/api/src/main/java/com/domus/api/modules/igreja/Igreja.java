package com.domus.api.modules.igreja;

import com.domus.api.shared.dominio.Endereco;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "igreja")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class Igreja {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome", nullable = false, length = 255)
    private String nome;

    @Column(name = "cnpj", length = 18)
    private String cnpj;

    @Column(name = "email", nullable = false, length = 255)
    private String emailContato;

    @Column(name = "telefone", length = 50)
    private String telefoneContato;

    @Column(name = "plano", length = 50)
    private String plano;

    @Column(name = "razao_social", length = 255)
    private String razaoSocial;

    @Column(name = "denominacao", length = 255)
    private String denominacao;

    @Column(name = "sigla", length = 20)
    private String sigla;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_foto_id")
    private com.domus.api.modules.foto.Foto logoFoto;

    @Embedded
    private Endereco endereco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    @Column(name = "atualizado_por_texto")
    private String atualizadoPorTexto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_mae_id")
    private Igreja igrejaMae;

    @Column(name = "codigo_vinculo", length = 9, unique = true)
    private String codigoVinculo;

    @Column(name = "codigo_gerado_em")
    private LocalDateTime codigoGeradoEm;

    @Column(name = "vinculado_em")
    private LocalDateTime vinculadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vinculado_por_usuario_id")
    private Usuario vinculadoPor;

    @Column(name = "vinculado_por_texto")
    private String vinculadoPorTexto;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
