package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.visitante.Visitante;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
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

    /** Obrigatório no front quando o evento é pago (usado pra mandar o comprovante de
     *  pagamento) — opcional em evento gratuito. Nulável aqui só por causa de inscrições
     *  antigas, criadas antes deste campo existir. */
    @Column(name = "email_convidado", length = 255)
    private String emailConvidado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "convidado_por_pessoa_id")
    private Pessoa convidadoPor;

    /** Preenchido só quando o convidado veio da busca de Visitante existente (aba
     *  "Visitantes" do modal) — NULL pra "Pessoa de fora" (nunca teve Visitante) e pro
     *  convite público (quem preenche não escolhe um Visitante, digita os próprios dados). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visitante_id")
    private Visitante visitante;

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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean estaConfirmada() {
        return status == StatusInscricao.CONFIRMADA;
    }

    public boolean estaAguardandoPagamento() {
        return status == StatusInscricao.AGUARDANDO_PAGAMENTO;
    }

    /** {@code true} = inscrição de gente sem cadastro no sistema. */
    public boolean isConvidadoSemCadastro() {
        return pessoa == null && nomeConvidado != null;
    }
}
