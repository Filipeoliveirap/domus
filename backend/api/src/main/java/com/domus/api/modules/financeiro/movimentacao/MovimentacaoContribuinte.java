package com.domus.api.modules.financeiro.movimentacao;

import com.domus.api.modules.pessoa.Pessoa;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "movimentacao_contribuinte")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentacaoContribuinte {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movimentacao_id", nullable = false)
    private MovimentacaoFinanceira movimentacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pessoa_id")
    private Pessoa pessoa;

    /** Contribuinte/beneficiário sem cadastro na igreja (ex.: doação de visitante avulso) —
     *  exatamente um entre {@code pessoa}/{@code nomeExterno} (CHECK no banco, V32). */
    @Column(name = "nome_externo")
    private String nomeExterno;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;
}
