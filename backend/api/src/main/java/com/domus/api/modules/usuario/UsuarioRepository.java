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

    @Query("SELECT u FROM Usuario u WHERE u.membro.email = :email")
    Optional<Usuario> findByEmail(@Param("email") String email);
    Optional<Usuario> findByIdAndIgrejaId(UUID id, UUID igrejaId);
    long countByIgrejaIdAndRole_NomeAndAtivoTrue(UUID igrejaId, String roleNome);

    @Query("""
    SELECT u FROM Usuario u
    WHERE u.igreja.id = :igrejaId
      AND ( :q IS NULL
            OR LOWER(u.membro.nome)  LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR LOWER(u.membro.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')) )
    ORDER BY u.membro.nome ASC
    """)
    Page<Usuario> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                  @Param("q") String q,
                                  Pageable pageable);

    @Query(value = """
        SELECT u.* FROM usuario u
        JOIN membro m ON m.id = u.membro_id
        WHERE m.email = :email
        """, nativeQuery = true)
    Optional<Usuario> findByEmailIncluindoArquivados(@Param("email") String email);
    Optional<Usuario> findByMembroId(UUID membroId);

    @Query(value = """
    SELECT u.* FROM usuario u
    WHERE u.membro_id = :membroId
    """, nativeQuery = true)
    Optional<Usuario> findByMembroIdIncluindoArquivados(@Param("membroId") UUID membroId);
}
