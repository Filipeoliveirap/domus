package com.domus.api.modules.visitante;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "visitante")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Visitante {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @Column(nullable = false, length = 255)
    private String nome;

    @Column(length = 20)
    private String telefone;

    @Embedded
    private Endereco endereco;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sexo sexo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_civil", length = 20)
    private EstadoCivil estadoCivil;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "tem_filhos", nullable = false)
    @Builder.Default
    private Boolean temFilhos = false;

    @Column(name = "quantidade_filhos")
    private Integer quantidadeFilhos;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "contato_realizado", nullable = false)
    @Builder.Default
    private Boolean contatoRealizado = false;

    @Column(name = "visita_realizada", nullable = false)
    @Builder.Default
    private Boolean visitaRealizada = false;

    @Column(name = "acompanhamento_feito", nullable = false)
    @Builder.Default
    private Boolean acompanhamentoFeito = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "entrou_em_celula_em")
    private LocalDateTime entrouEmCelulaEm;

    @Column(name = "convertido_pessoa_id")
    private UUID convertidoPessoaId;
}
