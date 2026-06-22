package com.domus.api.modules.usuario;

import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<UserDetails> findByEmail(String username);
    Boolean existsByEmail(String email);
    Boolean existsByIgrejaIdAndEmail(UUID igrejaId, String email);
    Optional<Usuario> findByIdAndIgrejaId(UUID id, UUID igrejaId);
    long countByIgrejaIdAndRole_NomeAndAtivoTrue(UUID igrejaId, String roleNome);

    @Query("""
    SELECT u FROM Usuario u
    WHERE u.igreja.id = :igrejaId
      AND ( :q IS NULL
            OR LOWER(u.nome)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR LOWER(u.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) )
    """)
    Page<Usuario> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                  @Param("q") String q,
                                  Pageable pageable);
}
