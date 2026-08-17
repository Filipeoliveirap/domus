package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaMembroRepository extends JpaRepository<CelulaMembro, UUID> {

    List<CelulaMembro> findByCelulaIdOrderByPapelAsc(UUID celulaId);

    Optional<CelulaMembro> findByPessoaId(UUID pessoaId);

    Optional<CelulaMembro> findByVisitanteId(UUID visitanteId);

    boolean existsByCelulaId(UUID celulaId);

    boolean existsByCelulaIdAndPessoaIdAndPapel(UUID celulaId, UUID pessoaId, PapelCelula papel);

    List<CelulaMembro> findByCelulaIdAndVisitanteIdIsNotNull(UUID celulaId);

    List<CelulaMembro> findByCelulaIdAndPessoaIdIsNotNull(UUID celulaId);

    /** Pra reindexação em lote: evita N+1 consultando visitante->célula um a um. */
    @Query("SELECT cm.visitante.id, cm.celula.id FROM CelulaMembro cm WHERE cm.visitante IS NOT NULL")
    List<Object[]> buscarCelulaIdPorVisitanteId();
}
