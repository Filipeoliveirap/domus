package com.domus.api.modules.financeiro.categoria;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoriaFinanceiraRepository extends JpaRepository<CategoriaFinanceira, UUID> {

    Optional<CategoriaFinanceira> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT c FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
          AND (:q IS NULL OR LOWER(c.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY c.nome ASC
    """)
    Page<CategoriaFinanceira> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                              @Param("q") String q,
                                              Pageable pageable);
    @Query("""
        SELECT c FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
        ORDER BY c.nome ASC
    """)
    List<CategoriaFinanceira> buscarTodasPorIgreja(@Param("igrejaId") UUID igrejaId);

    @Query("""
        SELECT COUNT(c) > 0 FROM CategoriaFinanceira c
        WHERE c.igreja.id = :igrejaId
          AND LOWER(TRIM(c.nome)) = LOWER(TRIM(:nome))
          AND c.id <> :idIgnorar
    """)
    boolean existeComNome(@Param("igrejaId") UUID igrejaId,
                          @Param("nome") String nome,
                          @Param("idIgnorar") UUID idIgnorar);
}