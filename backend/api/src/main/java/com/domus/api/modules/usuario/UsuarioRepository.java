package com.domus.api.modules.usuario;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<UserDetails> findByEmail(String username);
    Boolean existsByEmail(String email);
    Boolean existsByIgrejaIdAndEmail(UUID igrejaId, String email);
}
