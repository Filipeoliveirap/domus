package com.domus.api.modules.igreja;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "codigo_convite_congregacao")
public class CodigoConviteCongregacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matriz_id", nullable = false)
    private Igreja matriz;

    @Column(name = "codigo", nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_filha_id")
    private Igreja igrejaFilha;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Igreja getMatriz() { return matriz; }
    public void setMatriz(Igreja matriz) { this.matriz = matriz; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public LocalDateTime getCriadoEm() { return criadoEm; }
    public void setCriadoEm(LocalDateTime criadoEm) { this.criadoEm = criadoEm; }

    public LocalDateTime getUsadoEm() { return usadoEm; }
    public void setUsadoEm(LocalDateTime usadoEm) { this.usadoEm = usadoEm; }

    public Igreja getIgrejaFilha() { return igrejaFilha; }
    public void setIgrejaFilha(Igreja igrejaFilha) { this.igrejaFilha = igrejaFilha; }
}
