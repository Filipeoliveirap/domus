package com.domus.api.modules.evento;

import com.domus.api.modules.evento.local.LocalEvento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.EstadoCivil;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.Sexo;
import com.domus.api.modules.usuario.Usuario;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_id")
    private LocalEvento local;

    @Column(name = "local_texto")
    private String localTexto;

    @Column(name = "tipo", length = 80)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_pessoa_id")
    private Pessoa responsavel;

    @Column(name = "responsavel_texto")
    private String responsavelTexto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    @Column(name = "criado_por_texto")
    private String criadoPorTexto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    @Column(name = "atualizado_por_texto")
    private String atualizadoPorTexto;

    @Column(name = "recorte_etario", length = 40)
    private String recorteEtario;

    @Column(name = "idade_min") private Integer idadeMin;
    @Column(name = "idade_max") private Integer idadeMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "restricao_estado_civil", length = 20)
    private EstadoCivil restricaoEstadoCivil;

    @Enumerated(EnumType.STRING)
    @Column(name = "restricao_sexo", length = 10)
    private Sexo restricaoSexo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "foto_id")
    private com.domus.api.modules.foto.Foto foto;

    @Column(name = "vagas")
    private Integer vagas;

    @Column(name = "preco", precision = 10, scale = 2)
    private java.math.BigDecimal preco;

    @Column(name = "exclusivo_membros", nullable = false)
    @Builder.Default
    private boolean exclusivoMembros = false;

    @Column(name = "requer_inscricao", nullable = false)
    @Builder.Default
    private boolean requerInscricao = false;

    @Column(name = "controla_presenca", nullable = false)
    @Builder.Default
    private boolean controlaPresenca = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SituacaoEvento getSituacao() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime fimEfetivo = fimEm != null
                ? fimEm
                : inicioEm.toLocalDate().atTime(23, 59, 59);

        if (agora.isBefore(inicioEm)) {
            return SituacaoEvento.AGENDADO;
        }
        if (agora.isAfter(fimEfetivo)) {
            return SituacaoEvento.ENCERRADO;
        }
        return SituacaoEvento.EM_ANDAMENTO;
    }

    public String getLocalExibicao() {
        if (local != null) return local.getNome();
        return localTexto;
    }
}
