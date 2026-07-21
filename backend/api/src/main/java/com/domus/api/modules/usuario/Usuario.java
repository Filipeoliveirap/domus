package com.domus.api.modules.usuario;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "usuario")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@SQLDelete(sql = "UPDATE usuario SET delete_at = NOW(), ativo = false WHERE id = ?")
@SQLRestriction("delete_at IS NULL")
public class Usuario implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "igreja_id", nullable = false)
    private Igreja igreja;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pessoa_id", nullable = false)
    private Pessoa pessoa;

    @Column(name = "senha_hash", length = 255)
    private String senhaHash;

    @Column(name = "google_sub", length = 255, unique = true)
    private String googleSub;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "ultimo_login_em")
    private LocalDateTime ultimoLoginEm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "delete_at")
    private LocalDateTime deleteAt;

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.getNome()));
    }

    public void registrarLogin() {
        this.ultimoLoginEm = LocalDateTime.now();
    }

    /** Volta a conta ao estado "convite pendente" (nunca logou). Usado na reativação. */
    public void marcarConvitePendente() {
        this.ultimoLoginEm = null;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return pessoa.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }


    public String getNome() {
        return pessoa.getNome();
    }

    public String getEmail() {
        return pessoa.getEmail();
    }
}
