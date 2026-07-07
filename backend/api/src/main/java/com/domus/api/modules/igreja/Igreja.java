package com.domus.api.modules.igreja;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.membro.Membro;
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Usuario> usuarios;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Membro> membros;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Evento> eventos;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CategoriaFinanceira> categorias;

    @OneToMany(mappedBy = "igreja", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MovimentacaoFinanceira> movimentacoes;
}
