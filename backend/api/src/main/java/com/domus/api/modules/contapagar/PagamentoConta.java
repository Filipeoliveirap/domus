package com.domus.api.modules.contapagar;

import com.domus.api.modules.anexo.Anexo;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.usuario.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Um pagamento (parcela) de uma conta a pagar. */
@Entity
@Table(name = "pagamento_conta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoConta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_id", nullable = false)
    private ContaAPagar conta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movimentacao_id", nullable = false)
    private MovimentacaoFinanceira movimentacao;

    @Column(name = "valor_pago", nullable = false, precision = 15, scale = 2)
    private BigDecimal valorPago;

    @Column(name = "juros_acrescimos", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal jurosAcrescimos = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal desconto = BigDecimal.ZERO;

    @Column(name = "pago_em", nullable = false)
    private LocalDate pagoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private FormaPagamento forma;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anexo_id")
    private Anexo anexo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean estornado = false;

    @Column(name = "estornado_em")
    private LocalDateTime estornadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estornado_por_usuario_id")
    private Usuario estornadoPorUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_usuario_id")
    private Usuario criadoPorUsuario;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Valor líquido: o que efetivamente saiu da conta bancária. */
    public BigDecimal getValorLiquido() {
        return valorPago.add(jurosAcrescimos).subtract(desconto);
    }
}
