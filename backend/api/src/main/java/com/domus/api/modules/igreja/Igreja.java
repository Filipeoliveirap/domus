package com.domus.api.modules.igreja;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.pessoa.Endereco;
import com.domus.api.modules.pessoa.Pessoa;
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

    /** Par do CNPJ para documento fiscal — raramente igual ao nome popular da igreja. */
    @Column(name = "razao_social", length = 255)
    private String razaoSocial;

    @Column(name = "denominacao", length = 255)
    private String denominacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "logo_foto_id")
    private com.domus.api.modules.foto.Foto logoFoto;

    /**
     * Mesmo objeto de valor do membro — as 7 colunas vivem na própria tabela igreja.
     * Reusado de propósito: mesma estrutura, mesmo componente de front, mesmo ViaCEP.
     */
    @Embedded
    private Endereco endereco;

    /** Quem alterou os dados por último. O "quando" é o {@link #updatedAt} que já existia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "atualizado_por_usuario_id")
    private Usuario atualizadoPor;

    /**
     * A sede desta congregação. NULL = igreja independente ou mãe.
     * Regra dos 2 níveis: quem tem mãe não pode ser mãe (garantida no serviço de vínculo).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_mae_id")
    private Igreja igrejaMae;

    /**
     * Código que a mãe gera e a filha digita para entrar na família.
     * Reutilizável e rotacionável, sem expiração. NULL = nunca gerou.
     * Ter código NÃO torna a igreja mãe — mãe é quem tem pelo menos uma filha.
     */
    @Column(name = "codigo_vinculo", length = 9, unique = true)
    private String codigoVinculo;

    /** O código não expira; esta data permite a tela sugerir rotação de um código antigo. */
    @Column(name = "codigo_gerado_em")
    private LocalDateTime codigoGeradoEm;

    /** Quando esta congregação entrou na família. NULL para quem não é congregação. */
    @Column(name = "vinculado_em")
    private LocalDateTime vinculadoEm;

    /**
     * Qual admin da congregação digitou o código. O vínculo expõe o financeiro dela,
     * então "quem autorizou" é auditoria, não enfeite.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vinculado_por_usuario_id")
    private Usuario vinculadoPor;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Usuario> usuarios;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Pessoa> membros;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Evento> eventos;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CategoriaFinanceira> categorias;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovimentacaoFinanceira> movimentacoes;
}
