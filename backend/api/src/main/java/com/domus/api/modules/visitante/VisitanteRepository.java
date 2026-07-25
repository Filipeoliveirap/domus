package com.domus.api.modules.visitante;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VisitanteRepository extends JpaRepository<Visitante, UUID> {

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<Visitante> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT v FROM Visitante v
        WHERE v.igreja.id = :igrejaId
          AND (
            :q IS NULL
            OR LOWER(v.nome) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))
            OR v.telefone LIKE CONCAT('%', CAST(:q AS string), '%')
          )
          AND (:contato IS NULL OR v.contatoRealizado = :contato)
          AND (:visita IS NULL OR v.visitaRealizada = :visita)
          AND (:acompanhamento IS NULL OR v.acompanhamentoFeito = :acompanhamento)
          AND v.convertidoPessoaId IS NULL
          AND NOT EXISTS (
            SELECT cm FROM CelulaMembro cm WHERE cm.visitante.id = v.id
          )
        """)
    Page<Visitante> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                    @Param("q") String q,
                                    @Param("contato") Boolean contatoRealizado,
                                    @Param("visita") Boolean visitaRealizada,
                                    @Param("acompanhamento") Boolean acompanhamentoFeito,
                                    Pageable pageable);

    @Query("SELECT COUNT(cm) > 0 FROM CelulaMembro cm WHERE cm.visitante.id = :visitanteId")
    boolean existeCelulaMembroAtivo(@Param("visitanteId") UUID visitanteId);
}
