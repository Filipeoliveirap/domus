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

    /**
     * Local cadastrado. Mutuamente exclusivo com {@link #localTexto} (CHECK no banco).
     *
     * <p>N+1 na listagem: {@code EventoRepository.buscarPorIgreja} é nativa (o ORDER BY por
     * situação usa CASE WHEN + date_trunc que JPQL não tem), então não dá pra usar JOIN FETCH /
     * @EntityGraph nela. Resolvido com {@code @BatchSize} na CLASSE {@link LocalEvento} (batch
     * fetch é por tipo de entidade carregada, não pode ir no campo @ManyToOne) — uma página de
     * 20 eventos com local vira 1 {@code SELECT ... WHERE id IN (...)} em vez de 20 SELECTs.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_id")
    private LocalEvento local;

    /** Local ad-hoc ("chácara do João"). Era a coluna `local` até a V3. */
    @Column(name = "local_texto")
    private String localTexto;

    /** Texto normalizado por TextoUtil.capitalizar. NULL = sem tipo. */
    @Column(name = "tipo", length = 80)
    private String tipo;

    /** Mesmo raciocínio de N+1 do {@link #local}: {@code @BatchSize} está na classe {@link Pessoa}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_pessoa_id")
    private Pessoa responsavel;

    /**
     * Nome do responsável no momento em que a pessoa foi arquivada (V4). Mesmo raciocínio de
     * {@link #localTexto}: como {@link Pessoa} usa soft delete, o {@code ON DELETE SET NULL}
     * de {@code responsavel_pessoa_id} nunca dispara — {@code PessoaService.arquivarMembro}
     * zera a FK via UPDATE nativo e copia o nome para cá, preservando a informação mesmo sem
     * o vínculo. {@code null} enquanto o responsável ainda existe (o nome vem de {@link #responsavel}).
     */
    @Column(name = "responsavel_texto")
    private String responsavelTexto;

    /** Mesmo raciocínio de N+1 do {@link #local}: {@code @BatchSize} está na classe {@link Usuario}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPor;

    /** Mesmo raciocínio do {@link #responsavelTexto}, para quando o USUÁRIO que criou é arquivado. */
    @Column(name = "criado_por_texto")
    private String criadoPorTexto;

    /** Mesmo raciocínio de N+1 do {@link #local}: {@code @BatchSize} está na classe {@link Usuario}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    /** Mesmo raciocínio do {@link #responsavelTexto}, para quando o USUÁRIO que atualizou é arquivado. */
    @Column(name = "atualizado_por_texto")
    private String atualizadoPorTexto;

    /** Nome do recorte (Kids, Jovens...). Alimenta selo e filtro; NÃO valida nada. */
    @Column(name = "recorte_etario", length = 40)
    private String recorteEtario;

    /** Quem valida é este par. NULL = sem restrição daquele lado. */
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

    /** NULL = sem limite de vagas. */
    @Column(name = "vagas")
    private Integer vagas;

    /** NULL = gratuito. Informativo: o Domus registra a inscrição, não o pagamento. */
    @Column(name = "preco", precision = 10, scale = 2)
    private java.math.BigDecimal preco;

    @Column(name = "exclusivo_membros", nullable = false)
    @Builder.Default
    private boolean exclusivoMembros = false;

    /** Só evento marcado mostra o botão "Confirmar presença" e a lista de inscritos. */
    @Column(name = "requer_inscricao", nullable = false)
    @Builder.Default
    private boolean requerInscricao = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Situação do evento AGORA, derivada de {@code inicioEm}/{@code fimEm} — nunca armazenada
     * (ver {@link SituacaoEvento}). Mora na entidade porque é uma regra sobre os próprios
     * campos do evento, sem depender de nada externo (inscrições, igreja etc.).
     *
     * <p>Sem {@code fimEm} declarado, o evento é considerado em andamento até o fim do PRÓPRIO
     * dia de início, e encerrado a partir do dia seguinte — não dá pra saber quando um evento
     * sem horário de término "acaba", mas também não faz sentido continuar "em andamento" para
     * sempre.
     */
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

    /** O local a exibir: o nome do cadastrado, ou o texto ad-hoc, ou null. */
    public String getLocalExibicao() {
        if (local != null) return local.getNome();
        return localTexto;
    }
}