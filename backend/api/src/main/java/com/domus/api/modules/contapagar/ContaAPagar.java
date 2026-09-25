package com.domus.api.modules.contapagar;

import com.domus.api.modules.anexo.Anexo;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Conta a pagar (fixa ou recorrente). */
@Entity
@Table(name = "conta_a_pagar")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@SQLDelete(sql = "UPDATE conta_a_pagar SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ContaAPagar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaFinanceira categoria;

    /** Beneficiario: pessoa cadastrada (XOR com texto). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beneficiario_pessoa_id")
    private com.domus.api.modules.pessoa.Pessoa beneficiarioPessoa;

    @Column(name = "beneficiario_texto", length = 120)
    private String beneficiarioTexto;

    @Column(length = 255)
    private String descricao;

    private LocalDate competencia;

    @Column(name = "linha_digitavel", length = 80)
    private String linhaDigitavel;

    @Column(name = "documento_numero", length = 40)
    private String documentoNumero;

    @Column(name = "cnpj_beneficiario", length = 20)
    private String cnpjBeneficiario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "vencimento", nullable = false)
    private LocalDate vencimento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusConta status = StatusConta.EM_ABERTO;

    @Column(name = "valor_pago", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal valorPago = BigDecimal.ZERO;

    @Column(name = "pago_em")
    private LocalDate pagoEm;

    /** Self-FK: a geradora aponta pra si mesma. Ocorrências materiais apontam pra geradora. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "serie_id")
    private ContaAPagar serie;

    @Column(name = "diverge_da_serie", nullable = false)
    @Builder.Default
    private Boolean divergeDaSerie = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "recorrencia_frequencia", length = 20)
    private RecorrenciaFrequencia recorrenciaFrequencia;

    @Column(name = "recorrencia_ate")
    private LocalDate recorrenciaAte;

    @Column(name = "recorrencia_vezes")
    private Integer recorrenciaVezes;

    @Column(name = "recorrencia_dia_ancora")
    private Integer recorrenciaDiaAncora;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anexo_id")
    private Anexo anexo;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPorUsuario;

    @Column(name = "criado_por_texto", length = 255)
    private String criadoPorTexto;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder.Default
    @OneToMany(mappedBy = "conta", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PagamentoConta> pagamentos = new ArrayList<>();

    /** True se esta conta é a geradora da série (serie_id == id). */
    public boolean ehGeradora() {
        return this.serie != null && this.serie.getId().equals(this.id);
    }

    /** True se é ocorrência materializada (não é geradora). */
    public boolean ehOcorrencia() {
        return this.serie != null && !ehGeradora();
    }

    /** Descrição do beneficiário cobrindo os dois casos (pessoa ou texto). */
    public String getDescricaoBeneficiario() {
        if (beneficiarioPessoa != null) return beneficiarioPessoa.getNome();
        return beneficiarioTexto;
    }

    /** Valor total de pagamentos não-estornados. */
    public BigDecimal getValorPagoAcumulado() {
        return pagamentos.stream()
                .filter(p -> !p.getEstornado())
                .map(PagamentoConta::getValorPago)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
