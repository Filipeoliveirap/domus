package com.domus.api.modules.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioDomusAdminRepository extends JpaRepository<UsuarioDomusAdmin, UUID> {
    Optional<UsuarioDomusAdmin> findByEmail(String email);
    boolean existsByEmail(String email);
}
