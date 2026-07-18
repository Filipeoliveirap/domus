package com.domus.api.modules.membro;

import com.domus.api.modules.usuario.Usuario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MembroRepository extends JpaRepository<Membro, UUID> {

    @Query("""
        SELECT m FROM Membro m
        WHERE m.igreja.id = :igrejaId
          AND (
            :q IS NULL
            OR LOWER(m.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR LOWER(m.email) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR m.telefone LIKE CONCAT('%', CAST(:q AS string), '%')
          )
        """)
    Page<Membro> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                 @Param("q") String q,
                                 Pageable pageable);

    Optional<Membro> findByIdAndIgrejaId(UUID id, UUID igrejaId);
    boolean existsByEmail(String email);

    /** Bairros distintos já cadastrados na igreja — alimenta a sugestão (datalist) do form. */
    @Query("""
        SELECT DISTINCT m.endereco.bairro FROM Membro m
        WHERE m.igreja.id = :igrejaId AND m.endereco.bairro IS NOT NULL
        ORDER BY m.endereco.bairro
        """)
    java.util.List<String> bairrosDistintos(@Param("igrejaId") UUID igrejaId);

    @Query(value = """
    SELECT COUNT(*) > 0 FROM membro
    WHERE LOWER(email) = LOWER(:email)
    """, nativeQuery = true)
    boolean existsByEmailIncluindoArquivados(@Param("email") String email);
}